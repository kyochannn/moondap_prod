#!/usr/bin/env bash
#
# moondap 운영 배포 — ROOT.war 빌드 후 Cafe24 톰캣에 SFTP 로 올린다.
#
# 사용법:
#   ./scripts/deploy.sh                # 빌드(테스트 포함) → 업로드 → (설정 시)재기동
#   ./scripts/deploy.sh --skip-tests   # 테스트 건너뛰고 빌드
#   ./scripts/deploy.sh --no-build     # 기존 build/libs/ROOT.war 를 그대로 올림
#   ./scripts/deploy.sh --dry-run      # 실제 전송 없이 무엇을 할지만 출력
#
# 설정은 scripts/deploy.env 에서 읽는다(scripts/deploy.env.example 참고).
#
# ── 왜 이런 구조인가 ─────────────────────────────────────────────────────────
#
# 1) 임시 파일명으로 올린 뒤 rename 한다.
#    ROOT.war 는 46MB 가 넘고, 톰캣은 webapps/ 를 감시하다가 .war 가 보이면 배포를
#    시작한다. 최종 이름으로 바로 전송하면 톰캣이 "절반만 전송된 WAR" 를 열어
#    배포에 실패하거나 깨진 상태로 뜰 수 있다. rename 은 원자적이라 이 창이 없다.
#
# 2) 배포 전 기존 WAR 를 ROOT.war.bak 으로 남긴다.
#    문제가 생겼을 때 되돌릴 수단이 서버에 있어야 한다(--rollback).
#
# 3) 재기동 시 폭파된 webapps/ROOT/ 디렉터리를 지운다.
#    톰캣은 기존 ROOT/ 가 WAR 보다 새것이면 재전개하지 않는다. 지우지 않으면
#    WAR 만 갈아끼우고 예전 코드가 그대로 서비스되는 상황이 생긴다.
#
# 4) 비밀번호를 다루지 않는다.
#    SFTP 비밀번호를 스크립트나 설정 파일에 두면 평문 자격증명이 디스크에 남는다.
#    SSH 키 인증(BatchMode)만 지원하고, 키가 없으면 시작 단계에서 멈춘다.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_FILE="$PROJECT_DIR/scripts/deploy.env"
WAR_LOCAL="$PROJECT_DIR/build/libs/ROOT.war"

# 업로드 중 이름. 톰캣이 감시하는 확장자(.war)가 아니어야 한다.
UPLOAD_NAME="ROOT.war.uploading"

SKIP_TESTS=false
DO_BUILD=true
DRY_RUN=false
ROLLBACK=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-tests) SKIP_TESTS=true ;;
    --no-build)   DO_BUILD=false ;;
    --dry-run)    DRY_RUN=true ;;
    --rollback)   ROLLBACK=true; DO_BUILD=false ;;
    -h|--help)    sed -n '3,12p' "$0"; exit 0 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
  shift
done

log()  { printf '\033[1;34m▶\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m✔\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m✘\033[0m %s\n' "$*" >&2; exit 1; }

# ── 설정 ────────────────────────────────────────────────────────────────────

[[ -f "$CONFIG_FILE" ]] || die "설정 파일이 없다: $CONFIG_FILE
  cp scripts/deploy.env.example scripts/deploy.env 로 만든 뒤 값을 채운다."

# shellcheck disable=SC1090
set -a; source "$CONFIG_FILE"; set +a

: "${SSH_HOST:?deploy.env 에 SSH_HOST 가 비어 있다}"
: "${SSH_USER:?deploy.env 에 SSH_USER 가 비어 있다}"
: "${SSH_PORT:=22}"
: "${REMOTE_WEBAPPS:?deploy.env 에 REMOTE_WEBAPPS 가 비어 있다}"
: "${RESTART_TOMCAT:=false}"
: "${CATALINA_HOME:=}"
# 배포 전용 키. 개인 기본 키(~/.ssh/id_*)와 분리해 두면 이 키의 권한만 따로
# 회수할 수 있고, 에이전트에 어떤 키가 올라와 있든 동작이 일정하다.
: "${SSH_KEY:=$HOME/.ssh/moondap_deploy_ed25519}"

TARGET="$SSH_USER@$SSH_HOST"

# BatchMode=yes — 비밀번호 프롬프트가 뜨는 대신 즉시 실패한다.
# 배포 스크립트가 입력을 기다리며 멈춰 있는 것보다 명확하게 실패하는 편이 낫다.
SSH_OPTS=(-p "$SSH_PORT" -o BatchMode=yes -o ConnectTimeout=15)
SFTP_OPTS=(-P "$SSH_PORT" -o BatchMode=yes -o ConnectTimeout=15)

# IdentitiesOnly=yes — 에이전트에 키가 여러 개 올라와 있으면 서버가 시도 횟수
# 초과로 끊어버리는 일이 있다. 지정한 키 하나만 쓴다.
if [[ -f "$SSH_KEY" ]]; then
  SSH_OPTS+=(-i "$SSH_KEY" -o IdentitiesOnly=yes)
  SFTP_OPTS+=(-i "$SSH_KEY" -o IdentitiesOnly=yes)
fi

remote_sh() { ssh "${SSH_OPTS[@]}" "$TARGET" "$@"; }

# ── 사전 점검 ───────────────────────────────────────────────────────────────

# 연결 확인은 ssh 가 아니라 sftp 로 한다. Cafe24 계정은 SFTP 만 열리고 셸은
# 막힌 경우가 흔한데, 그 계정에서 `ssh true` 는 항상 실패하므로 연결 자체를
# 잘못 판정하게 된다.
log "SFTP 연결 확인: $TARGET:$SSH_PORT"
if ! printf 'bye\n' | sftp "${SFTP_OPTS[@]}" -b - "$TARGET" >/dev/null 2>&1; then
  die "키 인증으로 접속하지 못했다.
  비밀번호 인증은 지원하지 않는다(자격증명을 디스크에 남기지 않기 위해).
  키를 등록한 뒤 다시 실행한다:
    ssh-copy-id -i $SSH_KEY -p $SSH_PORT $TARGET"
fi
ok "SFTP 연결 정상"

# 셸 명령까지 실행되는지는 따로 본다. 셸이 막혀 있으면 재기동을 자동화할 수 없다.
HAS_SHELL=true
remote_sh 'echo shell-ok' 2>/dev/null | grep -q shell-ok || HAS_SHELL=false
if [[ "$HAS_SHELL" == "true" ]]; then ok "원격 셸 사용 가능"; else warn "원격 셸 불가 — 업로드까지만 자동화된다"; fi

if [[ "$RESTART_TOMCAT" == "true" && "$HAS_SHELL" != "true" ]]; then
  die "RESTART_TOMCAT=true 인데 원격 셸 명령이 실행되지 않는다(SFTP 전용 계정으로 보인다).
  deploy.env 에서 RESTART_TOMCAT=false 로 두고, 재시작은 Cafe24 관리자 패널에서 한다."
fi

# ── 롤백 ────────────────────────────────────────────────────────────────────

if [[ "$ROLLBACK" == "true" ]]; then
  [[ "$HAS_SHELL" == "true" ]] || die "롤백은 원격 셸이 있어야 한다."
  remote_sh "test -f '$REMOTE_WEBAPPS/ROOT.war.bak'" \
    || die "서버에 ROOT.war.bak 이 없다. 롤백할 대상이 없음."
  log "직전 WAR 로 되돌리는 중"
  if [[ "$DRY_RUN" == "true" ]]; then
    ok "[dry-run] ROOT.war.bak → ROOT.war 복원 후 재기동"
    exit 0
  fi
  remote_sh "cp '$REMOTE_WEBAPPS/ROOT.war.bak' '$REMOTE_WEBAPPS/ROOT.war.restore' \
             && mv '$REMOTE_WEBAPPS/ROOT.war.restore' '$REMOTE_WEBAPPS/ROOT.war'"
  RESTART_TOMCAT=true
  DO_BUILD=false
fi

# ── 빌드 ────────────────────────────────────────────────────────────────────

if [[ "$DO_BUILD" == "true" ]]; then
  if [[ "$SKIP_TESTS" == "true" ]]; then
    warn "테스트를 건너뛴다"
    log "bootWar 빌드 중 (테스트 제외)"
    (cd "$PROJECT_DIR" && ./gradlew bootWar -x test)
  else
    log "bootWar 빌드 중 (테스트 포함)"
    (cd "$PROJECT_DIR" && ./gradlew bootWar)
  fi
  ok "빌드 완료"
fi

if [[ "$ROLLBACK" != "true" ]]; then
  [[ -f "$WAR_LOCAL" ]] || die "WAR 가 없다: $WAR_LOCAL"

  # 톰캣이 열지 못하는 깨진 WAR 를 올리는 사고를 막는다.
  unzip -t "$WAR_LOCAL" >/dev/null 2>&1 || die "WAR 가 온전한 zip 이 아니다: $WAR_LOCAL"

  # devtools 가 섞이면 운영에서 클래스로더가 재시작 루프를 돌 수 있다.
  if unzip -l "$WAR_LOCAL" 2>/dev/null | grep -q 'spring-boot-devtools'; then
    die "WAR 에 spring-boot-devtools 가 포함돼 있다. bootWar 의 exclude 설정을 확인한다."
  fi

  WAR_SIZE=$(wc -c < "$WAR_LOCAL" | tr -d ' ')
  ok "WAR 검증 완료 ($(( WAR_SIZE / 1024 / 1024 ))MB, $(date -r "$WAR_LOCAL" '+%Y-%m-%d %H:%M'))"
fi

# ── 업로드 ──────────────────────────────────────────────────────────────────

if [[ "$ROLLBACK" != "true" ]]; then
  if [[ "$DRY_RUN" == "true" ]]; then
    ok "[dry-run] $WAR_LOCAL → $TARGET:$REMOTE_WEBAPPS/$UPLOAD_NAME → ROOT.war 로 rename"
    [[ "$RESTART_TOMCAT" == "true" ]] && ok "[dry-run] 톰캣 재기동 ($CATALINA_HOME)"
    exit 0
  fi

  log "업로드 중 → $REMOTE_WEBAPPS/$UPLOAD_NAME"
  sftp "${SFTP_OPTS[@]}" -b - "$TARGET" <<SFTPCMD
cd $REMOTE_WEBAPPS
put $WAR_LOCAL $UPLOAD_NAME
bye
SFTPCMD
  ok "전송 완료"

  # 전송이 중간에 끊겼는데 sftp 가 0 을 반환하는 경우를 대비해 크기를 대조한다.
  if [[ "$HAS_SHELL" == "true" ]]; then
    REMOTE_SIZE=$(remote_sh "wc -c < '$REMOTE_WEBAPPS/$UPLOAD_NAME'" | tr -d ' ')
    [[ "$REMOTE_SIZE" == "$WAR_SIZE" ]] \
      || die "크기 불일치 — 로컬 $WAR_SIZE / 원격 $REMOTE_SIZE. 전송이 잘렸다.
  서버의 $UPLOAD_NAME 은 그대로 두었다(ROOT.war 는 건드리지 않았으므로 서비스는 무사)."
    ok "크기 대조 완료 ($REMOTE_SIZE bytes)"
  else
    warn "원격 셸이 없어 크기 대조를 건너뛴다"
  fi
fi

# ── 교체 ────────────────────────────────────────────────────────────────────

if [[ "$ROLLBACK" != "true" ]]; then
  if [[ "$HAS_SHELL" == "true" ]]; then
    log "기존 WAR 백업 후 교체"
    # 톰캣이 실행 중이어도 안전하다. mv 는 원자적이고, 톰캣은 교체된 시점의
    # 온전한 WAR 만 보게 된다.
    remote_sh "cd '$REMOTE_WEBAPPS' \
               && { test -f ROOT.war && cp -p ROOT.war ROOT.war.bak || true; } \
               && mv '$UPLOAD_NAME' ROOT.war"
  else
    log "기존 WAR 교체 (SFTP rename)"
    sftp "${SFTP_OPTS[@]}" -b - "$TARGET" <<SFTPCMD || die "rename 실패. 서버의 $UPLOAD_NAME 을 확인한다."
cd $REMOTE_WEBAPPS
-rm ROOT.war.bak
-rename ROOT.war ROOT.war.bak
rename $UPLOAD_NAME ROOT.war
bye
SFTPCMD
  fi
  ok "ROOT.war 교체 완료"
fi

# ── 재기동 ──────────────────────────────────────────────────────────────────

if [[ "$RESTART_TOMCAT" == "true" ]]; then
  [[ -n "$CATALINA_HOME" ]] || die "RESTART_TOMCAT=true 인데 CATALINA_HOME 이 비어 있다."

  log "톰캣 정지"
  remote_sh "'$CATALINA_HOME/bin/shutdown.sh' 2>&1 | tail -3" || warn "shutdown.sh 가 오류를 반환했다(이미 정지 상태일 수 있음)"

  # 포트가 풀릴 때까지 기다린다. 정지 전에 startup.sh 를 부르면 두 인스턴스가
  # 뜨거나 포트 충돌로 조용히 실패한다.
  log "프로세스 종료 대기 (최대 60초)"
  for i in $(seq 1 30); do
    if ! remote_sh "pgrep -f 'catalina.*$CATALINA_HOME' >/dev/null 2>&1"; then
      ok "정지 확인 (${i}회 확인)"
      break
    fi
    sleep 2
    if [[ $i -eq 30 ]]; then
      die "60초 안에 종료되지 않았다. 서버에서 직접 확인한다.
  WAR 는 이미 교체됐으므로, 수동으로 톰캣을 재시작하면 새 버전이 뜬다."
    fi
  done

  # 폭파된 ROOT/ 를 남겨두면 새 WAR 가 전개되지 않고 예전 코드가 계속 뜬다.
  log "이전 전개 디렉터리 제거"
  remote_sh "rm -rf '$REMOTE_WEBAPPS/ROOT'"

  log "톰캣 기동"
  remote_sh "'$CATALINA_HOME/bin/startup.sh' 2>&1 | tail -3"

  ok "기동 명령 전송 완료"
  echo
  warn "전개에는 보통 30~60초가 걸린다. 로그 확인:"
  echo "    ssh -p $SSH_PORT $TARGET \"tail -f $CATALINA_HOME/logs/catalina.out\""
else
  echo
  warn "WAR 는 올라갔지만 톰캣은 재시작하지 않았다."
  warn "Cafe24 관리자 패널에서 톰캣을 재시작해야 새 버전이 반영된다."
fi

echo
ok "배포 완료"
echo "    되돌리기: ./scripts/deploy.sh --rollback"
