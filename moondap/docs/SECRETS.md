# 자격증명 관리 런북

## 1. 현재 구조

`application*.properties` 에는 **비밀값이 존재하지 않는다.** 플레이스홀더만 있고 기본값이 없다.

| 파일 | 플레이스홀더 |
|---|---|
| `application.properties` | `admin.secret-key=${ADMIN_SECRET_KEY}` |
| `application-dev.properties` | `spring.datasource.username=${DB_USERNAME}`<br>`spring.datasource.password=${DB_PASSWORD}` |
| `application-prod.properties` | 위와 동일 |

기본값(`${VAR:fallback}`)을 의도적으로 두지 않았다. 환경변수가 비어 있으면 Spring 이
`Could not resolve placeholder` 로 **기동을 거부**한다. 약한 기본 키로 조용히 뜨는 것보다
즉시 실패하는 편이 안전하다.

`StandardMdUserService` 의 `@Value("${admin.secret-key:MOONDAP_ADMIN_2026}")` 하드코딩
fallback 도 함께 제거했다. 키 값을 INFO 로그로 출력하던 코드도 삭제했고, 키 비교는
`MessageDigest.isEqual` 로 바꿔 타이밍 공격 여지를 없앴다.

---

## 2. 로컬 개발 환경

```bash
cp .env.example .env      # .env 는 gitignore 대상
vi .env                   # 실제 값 입력
set -a && source .env && set +a
./gradlew bootRun
```

IntelliJ 를 쓴다면 Run Configuration → Environment variables 에 동일한 3개를 등록한다.

---

## 3. 운영 환경 (Cafe24 톰캣 WAR 배포)

톰캣은 기동 시 `$CATALINA_HOME/bin/setenv.sh` 를 자동으로 읽는다. 이 파일은 WAR 바깥에
있으므로 배포 산출물에 비밀값이 섞이지 않는다.

```bash
# $CATALINA_HOME/bin/setenv.sh
export DB_USERNAME='...'
export DB_PASSWORD='...'
export ADMIN_SECRET_KEY='...'
export SPRING_PROFILES_ACTIVE='prod'
```

권한을 반드시 좁힌다. 톰캣 실행 계정만 읽을 수 있어야 한다.

```bash
chown tomcat:tomcat $CATALINA_HOME/bin/setenv.sh
chmod 600 $CATALINA_HOME/bin/setenv.sh
```

적용 확인:

```bash
$CATALINA_HOME/bin/shutdown.sh && $CATALINA_HOME/bin/startup.sh
tail -f $CATALINA_HOME/logs/catalina.out
# 'Could not resolve placeholder' 가 없으면 정상 주입된 것
```

---

## 4. 키 / 비밀번호 로테이션 절차

> **히스토리 정리 여부와 무관하게 로테이션은 반드시 수행해야 한다.**
> 기존 값은 이미 GitHub 에 평문으로 푸시되었다. 히스토리를 재작성해도 GitHub 은 과거 객체를
> 일정 기간 보관하며, 포크·클론·PR 캐시에는 그대로 남는다. 노출된 값은 영구적으로
> 오염된 것으로 간주한다.

### 4-1. `ADMIN_SECRET_KEY` (난이도 낮음 — 먼저 처리)

이 키는 서버에만 존재하고 DB에 저장되지 않으므로 즉시 교체 가능하다.

```bash
openssl rand -base64 32        # 새 키 생성
```

1. `setenv.sh` 의 `ADMIN_SECRET_KEY` 를 새 값으로 교체
2. 톰캣 재기동
3. `/joinSelectView` 에서 새 키로 관리자 가입이 되는지 확인

**교체 전에 반드시 확인할 것** — 기존 키가 노출된 동안 생성된 관리자 계정이 있는지:

```sql
SELECT username, nickname, role, created_at
FROM moondap.md_users
WHERE role = 'ROLE_ADMIN'
ORDER BY created_at DESC;
```

본인이 만들지 않은 `ROLE_ADMIN` 계정이 있으면 즉시 차단한다.

```sql
UPDATE moondap.md_users SET status = 'SUSPENDED' WHERE username = '<의심계정>';
```

### 4-2. `DB_PASSWORD` (무중단 적용 순서)

계정 하나를 그대로 쓰면 비번 변경과 앱 재기동 사이에 반드시 다운타임이 생긴다.
**신규 계정을 만들고 전환한 뒤 구 계정을 폐기하는 순서**가 안전하다.

```sql
-- 1) 새 계정 생성 (기존 계정은 아직 살려둔다)
CREATE USER 'moondap_app'@'localhost' IDENTIFIED BY '<새로운_강력한_비밀번호>';
GRANT SELECT, INSERT, UPDATE, DELETE ON moondap.* TO 'moondap_app'@'localhost';
FLUSH PRIVILEGES;
```

> 애플리케이션은 DDL 을 실행하지 않는다(스키마 변경은 수동). 따라서 DML 4종만 부여하면
> 충분하다. 기존 계정이 `ALL PRIVILEGES` 였다면 이번 기회에 권한을 좁히는 것이 좋다.

```bash
# 2) setenv.sh 에 새 계정 정보 반영 후 재기동
vi $CATALINA_HOME/bin/setenv.sh
$CATALINA_HOME/bin/shutdown.sh && $CATALINA_HOME/bin/startup.sh

# 3) 정상 동작 확인 (메인 페이지 조회, 로그인, 밸런스게임 투표)
tail -f $CATALINA_HOME/logs/catalina.out
```

```sql
-- 4) 새 계정으로 정상 서비스되는 것을 확인한 뒤에만 구 계정 폐기
DROP USER '<기존계정>'@'%';
```

### 4-3. DB 노출 범위 점검

`application-dev.properties` 가 `moondap.com:3306` 을 직접 가리키고 있었다. 즉 DB 포트가
외부에 열려 있을 가능성이 높다. 노출된 계정 정보와 결합하면 원격에서 직접 접속이 가능하다.

```sql
-- 원격 접속이 허용된 계정 확인. host 가 '%' 인 계정이 위험하다.
SELECT user, host FROM mysql.user;
```

DB 는 애플리케이션과 같은 서버에 있으므로(`application-prod` 는 `localhost`) 3306 을
외부에 열어둘 이유가 없다. 방화벽에서 차단하고 계정 host 를 `localhost` 로 제한한다.

---

## 5. git 히스토리 정리 (직접 실행)

> ⚠️ **되돌리기 어려운 작업이다.** 첫 재작성 커밋 이후의 모든 커밋 SHA 가 바뀌고
> 원격에 강제 푸시가 필요하다. 협업자가 있다면 재클론이 필요하다.
> 실행 전에 4번의 로테이션을 **먼저** 끝내는 것을 권장한다. 그러면 히스토리에 남은 값이
> 이미 무효화된 상태라 작업 중 압박이 없다.

### 5-0. 백업 (필수)

```bash
cd /Users/kookyochan/workSpace
git clone --mirror https://github.com/kyochannn/moondap_prod.git moondap_prod-backup.git
tar czf moondap_prod-backup-$(date +%Y%m%d).tar.gz moondap_prod-backup.git
```

### 5-1. 도구 설치

```bash
brew install git-filter-repo
```

### 5-2. 치환 규칙 작성

파일 자체를 히스토리에서 지우는 대신(`--invert-paths`), **비밀값 문자열만 치환**한다.
파일은 남으므로 과거 커밋을 체크아웃해도 빌드 구조가 깨지지 않는다.

```bash
cd /Users/kookyochan/workSpace/moondap_prod
cat > /tmp/md-replacements.txt <<'EOF'
literal:여기에_기존_DB_비밀번호==>${DB_PASSWORD}
literal:여기에_기존_DB_계정명==>${DB_USERNAME}
literal:여기에_기존_ADMIN_KEY==>${ADMIN_SECRET_KEY}
EOF
chmod 600 /tmp/md-replacements.txt
```

기존 값은 아래에서 확인할 수 있다(백업본 기준).

```bash
git show HEAD~1:moondap/src/main/resources/application-prod.properties
```

### 5-3. 재작성 실행

```bash
cd /Users/kookyochan/workSpace/moondap_prod
git filter-repo --replace-text /tmp/md-replacements.txt --force
```

### 5-4. 검증 — 푸시 전에 반드시

```bash
# 히스토리 전체에서 기존 비밀값이 사라졌는지 확인. 결과가 없어야 정상.
git grep -I '여기에_기존_DB_비밀번호' $(git rev-list --all) 2>/dev/null

# 현재 작업본이 멀쩡한지
cd moondap && ./gradlew build -x test
```

### 5-5. 강제 푸시

```bash
# filter-repo 는 안전장치로 origin 을 제거한다. 다시 등록한다.
cd /Users/kookyochan/workSpace/moondap_prod
git remote add origin https://github.com/kyochannn/moondap_prod.git
git push origin --force --all
git push origin --force --tags
```

### 5-6. 뒷정리

```bash
rm -f /tmp/md-replacements.txt /tmp/md-*.bak
```

- GitHub 웹에서 오래된 객체가 직접 SHA URL 로 계속 조회된다면 GitHub Support 에
  gc 를 요청한다.
- 저장소에 **포크가 있다면 포크에는 옛 히스토리가 그대로 남는다.** 포크는 재작성이
  불가능하므로, 4번의 로테이션이 유일한 실질적 방어선이다.

---

## 6. 재발 방지

커밋 전에 비밀값을 자동 차단하려면 pre-commit 훅을 둔다.

```bash
brew install gitleaks
cd /Users/kookyochan/workSpace/moondap_prod
cat > .git/hooks/pre-commit <<'EOF'
#!/bin/sh
gitleaks protect --staged --redact --no-banner || {
  echo "커밋에 비밀값으로 의심되는 문자열이 있습니다. 확인 후 다시 시도하세요."
  exit 1
}
EOF
chmod +x .git/hooks/pre-commit
```
