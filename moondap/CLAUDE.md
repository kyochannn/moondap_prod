# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

moondap(문답) — 심리테스트 · 밸런스게임 · 에겐테토 테스트를 제공하는 Spring Boot 3.4 / Java 17
서버사이드 렌더링(Thymeleaf) 웹 서비스. MyBatis + MySQL, Cafe24 톰캣에 **WAR 로 배포**한다.

git 저장소 루트는 이 디렉터리의 **부모**(`moondap_prod/`)다. 커밋 경로는 `moondap/...` 로 시작한다.

## 명령어

```bash
./gradlew build                 # 컴파일 + 테스트 + WAR
./gradlew build -x test         # 테스트 제외 빌드
./gradlew test                  # 전체 테스트
./gradlew test --tests "com.moondap.service.BalanceGameVoteTest"          # 단일 클래스
./gradlew test --tests "*BalanceGameVoteTest.투표는*"                      # 단일 메서드
./gradlew bootRun               # 로컬 실행 (환경변수 주입 필요, 아래 참고)
./gradlew bootWar               # 배포 산출물 ROOT.war 생성 (devtools 제외)
```

로컬 실행 전 환경변수 주입:

```bash
cp .env.example .env && vi .env
set -a && source .env && set +a && ./gradlew bootRun
```

`spring.profiles.active` 를 `dev` 로 두고 실행한다. 린트/포매터 설정은 없다.

## 설정과 비밀값 (중요)

- `application*.properties` 에는 **플레이스홀더만** 두고 기본값을 주지 않는 것이 원칙이다
  (`${DB_USERNAME}`, `${DB_PASSWORD}`, `${ADMIN_SECRET_KEY}`). 환경변수가 비면 Spring 이
  `Could not resolve placeholder` 로 기동을 거부한다 — 약한 기본값으로 조용히 뜨는 것보다 낫다.
  **이 파일들에 실제 계정·키를 적어 넣지 말 것.** 자세한 운영 절차는 `docs/SECRETS.md`.
- `config/` 디렉터리는 gitignore 대상이고(`*.example` 만 추적), Spring Boot 가 실행 디렉터리의
  `./config/` 를 classpath 설정보다 **우선**해서 읽는다. 로컬에서 설정이 예상과 다르면 여기를 먼저 본다.
- `dev` 프로파일의 DB 기본값은 **로컬** MySQL 이다. 운영 DB 를 봐야 할 때만 `DB_URL` 로 덮어쓴다.
- 테스트는 `src/test/resources/application-test.properties` 의 더미 값을 쓴다. 컨텍스트를 띄우는
  테스트에는 반드시 `@ActiveProfiles("test")` 를 붙인다(개발자 머신 환경변수에 의존하지 않기 위해).
  테스트 DB 포트를 13306 으로 둔 것은 실수로 3306 에 붙는 것을 막기 위함이다.

## 아키텍처

### 레이어

`controller` → `service` → `mapper`(MyBatis 인터페이스) → `resources/com/moondap/mybatis/mapper/*.xml`

- 서비스는 대부분 클래스 하나다. `BalanceGameService`/`StandardBalanceGameService` 처럼
  인터페이스 + `Standard*` 구현 쌍을 쓰는 곳도 있다(`MdUserService` 는 빈 껍데기 인터페이스).
- DTO 는 `dto/`(응답·도메인), `dto/request/`(요청 바인딩 + Bean Validation)로 나뉜다.
- MyBatis 는 `mapUnderscoreToCamelCase=true`, `com.moondap.dto` 패키지를 타입 별칭으로 등록.
  스키마 변경은 수동이며(앱은 DDL 을 실행하지 않는다) 참고 SQL 은 `resources/SQL 쿼리 모음/`.

### 콘텐츠 통합 모델

심리테스트(`NORMAL`)와 밸런스게임(`BALANCE`)은 별개 테이블이지만, 메인/목록에서는
`MdTestUserService.getAllContentList(...)` 가 UNION 으로 합쳐 `MdContentItemDTO` 로 내려준다.
이 쿼리는 인덱스를 타지 못해 데이터가 늘수록 선형으로 느려지므로 캐시가 전제다.

### 캐시 (`config/CacheConfig`)

Caffeine 기반. TTL 이 필요해서 Spring 기본 `ConcurrentMapCacheManager` 를 쓰지 않는다.
캐시 이름은 `CacheConfig` 의 상수로만 참조한다 — `CONTENT_LIST`, `ACTIVE_CATEGORIES`,
`PARTICIPANT_COUNT`, `EGEN_STATS`.

**콘텐츠를 등록·수정·삭제하는 서비스 메서드에는 `@CacheEvict(CONTENT_LIST, allEntries = true)`
를 반드시 붙인다.** 빠뜨리면 목록에 최대 5분간 반영되지 않는다. 카테고리 변경은
`ACTIVE_CATEGORIES` 와 `CONTENT_LIST` 를 함께 비운다.

### 보안 (`config/SecurityConfig`)

- **기본 정책은 `anyRequest().authenticated()` 거부**다. 새 컨트롤러를 추가하면 명시적으로
  `permitAll()` 규칙을 넣지 않는 한 로그인 화면으로 넘어간다.
- 규칙 순서가 의미를 갖는다. 보호 규칙(`/test/manage/**` 등)을 공개 규칙(`/test/**`)보다
  **먼저** 선언해야 한다. 먼저 매칭되는 규칙이 이긴다.
- 기본 거부의 부작용(오타 URL 이 로그인으로 유도됨)은 `NotFoundAwareAuthenticationEntryPoint`
  가 보정한다 — 매핑된 핸들러가 없으면 404.
- CSRF 활성. 폼은 `th:action` 으로 hidden 토큰 자동 주입, AJAX 는 `fragment/link.html` 의
  meta → `fragment/script.html` 전역 설정 경로로 전달된다. multipart 폼의 토큰을 읽기 위해
  `MultipartFilter` 가 시큐리티 필터보다 앞에 등록돼 있다.
- 세션은 `SessionRegistry` 에 등록된다(동시 접속 제한이 아니라 관리자의 강제 만료 용도).
- 권한 판정 헬퍼는 `common/SecurityUtil` 한 곳에 모아둔다. 컨트롤러·서비스에서 직접
  `SecurityContextHolder` 를 다시 풀어쓰지 않는다.
- 댓글 삭제처럼 "작성자 본인 또는 관리자" 판정이 필요한 것은 URL 규칙이 아니라
  서비스(`canDeleteComment`)가 결정한다.

### 익명 사용자 식별 (`common/AnonymousIdentity`)

비로그인 사용자의 댓글·투표·결과 보관함은 `md_anon` 쿠키(UUID, HttpOnly, SameSite=Lax)로 묶인다.
- `current()` 는 조회만 한다. 발급하지 않는다.
- `getOrCreate(response)` 는 쓰기 시점(댓글·투표)과 **방문 집계**(`VisitLogInterceptor`)에서 부른다.
  후자 때문에 열람만 하는 방문자에게도 발급된다 — IP 만으로는 방문자 수가 두 방향으로
  틀리는데(공유 회선은 여러 명을 1명으로, 모바일 IP 변동은 1명을 여러 명으로) 비교 기준이
  없으면 얼마나 틀리는지조차 알 수 없어서 내린 결정이다. 개인정보처리방침 제2조에
  "방문자 수 집계" 목적으로 고지돼 있으므로, 발급 범위를 바꾸면 그 문구도 함께 고친다.
- 방문 집계는 **되돌아온 쿠키만** 센다. 방금 발급한 값을 세면 쿠키를 저장하지 않는
  클라이언트 한 대가 하루 수백 명으로 둔갑한다.
- `Cookie#setAttribute` 는 서블릿 6.0 API 라 운영 톰캣 10.0 에서 터진다. `Set-Cookie` 헤더를
  직접 만드는 현재 방식을 유지할 것.

### 예외 처리 (`common/handler/GlobalExceptionHandler`)

- 사용자에게 보일 문구는 `UserMessageException` 에 담아 던진다. 그 외 예외 메시지는
  일반 문구로 덮는다 — MyBatis/JDBC 메시지에 SQL 과 테이블명이 노출되기 때문.
- 없는 콘텐츠는 `ContentNotFoundException` → 404. 리다이렉트나 200 으로 처리하면 soft 404 가 되어
  삭제한 URL 이 색인에 남는다.
- 같은 핸들러가 AJAX(JSON)와 뷰 응답을 모두 처리한다. 상태 코드를 반드시 실어 보낸다.

### SEO

`SeoMetaInterceptor` 가 모든 뷰에 `seo` 모델 속성(canonical/og/robots)을 채운다. 컨트롤러가
`seo` 를 미리 넣으면 그 값이 우선하고 빈 항목만 보충된다. 질문지·결과·로그인 등 읽을거리가
없는 경로는 `NOINDEX_PATTERNS` 에 등록해 noindex 처리한다(robots.txt 로 막으면 이미 색인된
URL 을 뺄 수 없다). 절대 URL 기준은 `app.base-url` 이며 로컬에서도 운영 도메인을 유지한다.

`DomainRedirectFilter` 는 prod 프로파일에서만 동작하며 http → https, `kckoo.co.kr` → `moondap.com`
301 리다이렉트를 처리한다.

### 뷰

Thymeleaf + `thymeleaf-layout-dialect`. 공통 골격은 `templates/layout/main_layout.html`,
조각은 `templates/fragment/`(header/footer/link/script/notification)와
`templates/layout/fragments/comment_fragment.html`.
스타일은 `static/assets/scss/` 와 `static/assets/css/` 가 공존하며 **빌드 파이프라인이 없다**
(package.json 없음). 실제로 서빙되는 것은 `css/` 이므로 CSS 를 직접 수정한다.

### 파일 업로드

`common/FileService` 가 확장자가 아니라 실제 내용을 검사한다. **SVG 는 의도적으로 금지**
(스크립트를 품을 수 있고 같은 오리진에서 서빙되므로 저장형 XSS 경로가 된다).
업로드물은 `/uploads/**`, `/profile/**` 로 서빙되며 `WebConfig` 가 `nosniff` 와
`Content-Security-Policy: sandbox` 헤더를 붙인다(검증이 느슨하던 시절 파일이 디스크에 남아 있다).

## 테스트 관례

`src/test/java/com/moondap/` 아래 서비스 단위 테스트는 Mockito(`@ExtendWith(MockitoExtension.class)`)
+ AssertJ, 컨트롤러/설정 테스트는 `@SpringBootTest` + `@ActiveProfiles("test")`.
테스트 클래스와 `@DisplayName` 은 한국어로 "무엇을 보장하는가"를 적고, 클래스 javadoc 에
**그 규칙이 왜 필요한지(어떤 버그가 있었는지)** 를 남기는 것이 이 저장소의 스타일이다.

## 코드 주석 스타일

이 저장소의 주석은 "무엇을 하는가"가 아니라 **"왜 이렇게 했는가 / 다르게 하면 무엇이 깨지는가"**
를 적는다(예: `SecurityConfig` 의 규칙 순서, `AnonymousIdentity` 의 Set-Cookie 직접 생성,
`CacheConfig` 의 Caffeine 선택 이유). 새 코드도 같은 밀도로 맞춘다. 한국어로 작성한다.

## 진행 현황 (노션)

작업 단위별 현황은 노션 DB **`문답 작업 현황`** 에 둔다.
데이터 소스: `collection://36913f9c-9253-4b42-86d5-0c0e091b4cda`
(경로: `반갑습니다. 개발자 구교찬입니다. / Project List / 세상의 모든 테스트, 문답`)

속성은 `작업`(제목) · `상태`(완료/진행 중/검토 필요/예정) · `분류`(애드센스 대응/기능/운영·배포/SEO/문서)
· `Git`(커밋됨/미커밋/미추적) · `주요 파일` · `메모` · `갱신`(자동).

**갱신 규칙 — 노션 MCP 가 붙어 있을 때만 수행한다. 없으면 조용히 건너뛴다.**

- 한 덩어리의 작업을 끝내면 해당 항목의 `상태` 를 갱신한다. 항목이 없으면 새로 만든다.
- 커밋을 만들면 그 커밋에 포함된 항목의 `Git` 을 `커밋됨` 으로 바꾼다.
- `메모` 에는 진행률이 아니라 **왜 했는지 / 무엇이 깨져 있었는지 / 남은 것**을 적는다.
  커밋 메시지와 코드 주석에서 이미 하는 일과 같은 기준이다. 여기 적힌 내용이
  코드만 봐서는 안 나오는 "이 변경이 왜 있었나" 의 유일한 기록인 경우가 많다.
- 갱신은 **세션이 돌 때만** 일어난다. 훅이나 CI 가 아니라 사람이 부른 세션이 쓰는 것이라,
  노션 값은 "마지막 세션 시점의 사실"이고 작업 트리의 실제 상태와 어긋날 수 있다.
  어긋나 보이면 `git status` 가 정답이고 노션을 고친다.
