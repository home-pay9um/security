# 사용·확장·운영 가이드

이 문서는 이 인증 서버를 로컬에서 실행하고, 클라이언트에서 연동하며, 다른 프로젝트의 인증 기반으로 확장할 때 참고하는 실전 가이드입니다.

프로젝트 구조와 내부 데이터 흐름은 [README](../README.md)를 먼저 확인하세요.

## 1. 빠른 시작

### 사전 요구 사항

- JDK 21
- MySQL 8 이상 권장
- IntelliJ IDEA 또는 Maven Wrapper 실행 환경

### 실행

루트 디렉터리에서 실행합니다.

```powershell
.\mvnw.cmd spring-boot:run -pl security-app -am
```

테스트는 다음 명령으로 실행합니다.

```powershell
.\mvnw.cmd test
```

`security-app`이 실행 모듈입니다. `-am` 옵션은 `security-core`, `security-jwt` 의존 모듈도 함께 빌드합니다.

### IntelliJ IDEA 설정

1. 루트의 `pom.xml`을 **Open**하여 Maven 프로젝트로 불러옵니다.
2. Project SDK를 JDK 21로 지정합니다.
3. Maven 창에서 루트 프로젝트를 Reload 합니다.
4. `SecurityApplication`을 실행 구성으로 선택합니다.
5. 필요하다면 실행 구성의 Environment variables에 DB 및 JWT 설정을 지정합니다.

## 2. 설정 방법

Git에는 실제 설정 파일 대신 [`security-app/src/main/resources/application.yml.example`](../security-app/src/main/resources/application.yml.example)만 포함됩니다. 프로젝트를 clone한 뒤 아래 명령으로 로컬 전용 설정 파일을 만들고, 환경 변수 또는 로컬 값으로 DB와 JWT 설정을 채우세요.

```powershell
Copy-Item security-app/src/main/resources/application.yml.example security-app/src/main/resources/application.yml
```

`application.yml`은 `.gitignore`로 제외되어 GitHub에 올라가지 않습니다. 환경 변수를 설정하면 `application.yml`의 `${...}` 설정값에 주입됩니다.

| 환경 변수 | 설명 | 예시 |
| --- | --- | --- |
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/security` |
| `DB_USERNAME` | DB 계정 | `security_app` |
| `DB_PASSWORD` | DB 비밀번호 | 별도 관리 |
| `JWT_SECRET_KEY` | JWT HMAC 서명 키 | 32바이트 이상의 무작위 문자열 |
| `APP_SECURITY_REFRESH_TOKEN_CLEANUP_CRON` | 만료 Refresh Token 삭제 Cron | `0 0 3 * * *` |

Spring Boot의 이름 변환 규칙에 따라 `app.security.refresh-token-cleanup-cron`은 환경 변수 `APP_SECURITY_REFRESH_TOKEN_CLEANUP_CRON`으로 지정할 수 있습니다.

> GitHub 공개 저장소에 올리기 전에는 반드시 확인하세요. 이미 실제 DB 접근 정보 또는 실제 JWT 키를 커밋한 적이 있다면, 값을 먼저 회전(교체)해야 합니다. 파일을 ignore 처리해도 Git 이력에 남은 비밀값은 자동으로 사라지지 않습니다.

## 3. API 사용 흐름

기본 주소는 `http://localhost:8080`이고 인증 API의 접두사는 `/api/v1/auth`입니다.

### 회원가입

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username":"user1234",
    "password":"Password123!",
    "passwordConfirm":"Password123!",
    "email":"user1234@example.com",
    "nickname":"개발자",
    "phoneNumber":"010-1234-5678"
  }'
```

성공 응답은 `201 Created`입니다.

### 로그인

```bash
curl -X POST http://localhost:8080/api/v1/auth/signin \
  -H "Content-Type: application/json" \
  -d '{"username":"user1234","password":"Password123!"}'
```

응답의 `token.accessToken`은 보호 API 호출에 사용하고, `token.refreshToken`은 재발급 및 로그아웃에만 사용합니다.

### 보호 API 호출

새 컨트롤러를 추가한 뒤 기본 보안 정책을 그대로 사용하면 인증이 필요합니다.

```bash
curl http://localhost:8080/api/v1/example \
  -H "Authorization: Bearer {accessToken}"
```

Access Token이 아니거나, 서명·만료·형식이 올바르지 않은 토큰은 `401 Unauthorized`를 반환합니다. 권한은 있지만 접근 권한이 부족하면 `403 Forbidden`을 반환합니다.

### 토큰 재발급

Access Token이 만료되고 Refresh Token이 유효할 때만 호출합니다.

```bash
curl -X POST http://localhost:8080/api/v1/auth/reissue \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"{refreshToken}"}'
```

성공하면 새 Access Token과 새 Refresh Token이 함께 반환됩니다. 기존 Refresh Token은 즉시 폐기되므로, 클라이언트는 두 값을 함께 교체해야 합니다.

### 로그아웃

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"{refreshToken}"}'
```

성공 시 `204 No Content`입니다. 서버는 해당 Refresh Token 행을 즉시 삭제합니다. Access Token은 무상태 토큰이므로 만료 전까지는 유효합니다.

## 4. 클라이언트 연동 규칙

### 권장 토큰 처리

1. 로그인 성공 후 Access Token과 Refresh Token을 받습니다.
2. Access Token으로 일반 API를 호출합니다.
3. 보호 API가 `401`을 반환하면 재발급 요청을 **한 번만** 시도합니다.
4. 재발급 성공 시 새 토큰 쌍으로 저장 값을 교체하고, 실패했던 요청을 한 번 재시도합니다.
5. 재발급도 `401`이면 토큰을 모두 제거하고 로그인 화면으로 이동합니다.
6. 로그아웃 시 서버 `/logout` 호출 후 클라이언트 저장 값도 제거합니다.

동시에 여러 요청이 `401`을 받았을 때 각각 재발급을 시도하면, Refresh Token 회전 특성상 첫 요청 이후 나머지 요청은 실패합니다. 클라이언트에서는 재발급 요청을 하나만 실행하도록 Promise/락으로 묶어야 합니다.

현재 API는 Refresh Token을 JSON 본문으로 받습니다. 브라우저 웹 서비스로 확장할 때는 Refresh Token을 `HttpOnly`, `Secure`, `SameSite` 쿠키로 옮기고 CSRF 정책을 함께 설계하는 방식을 권장합니다.

## 5. 다른 프로젝트로 확장하기

### 모듈 책임 유지

| 모듈 | 넣어도 되는 코드 | 넣지 말아야 할 코드 |
| --- | --- | --- |
| `security-core` | 엔티티, Repository, 공통 예외, 인증 사용자 모델 | Controller, JWT 구현, UI 의존 코드 |
| `security-jwt` | 토큰 생성·파싱·검증·필터 | 도메인 비즈니스 규칙, Controller |
| `security-app` | Controller, Service, Security 설정, 실행 설정 | 다른 모듈이 재사용할 도메인 구현 |

새 도메인 기능(예: 게시글)을 만들 때 권장 위치는 다음과 같습니다.

```text
security-core/src/main/java/.../core/domain/post/
├── entity/Post.java
└── repository/PostRepository.java

security-app/src/main/java/.../
├── controller/PostController.java
├── service/PostService.java
└── dto/post/
    ├── request/
    └── response/
```

### 새 보호 API 추가 예시

`/api/v1/auth/**`만 익명 접근이 허용됩니다. 다른 경로는 기본적으로 인증이 필요하므로, `PostController`를 추가하면 Access Token 검증이 자동 적용됩니다.

공개 API를 새로 허용하려면 `application.yml`의 `app.security.permit-all-patterns`에 경로를 추가합니다. 관리자 전용 API처럼 역할 기반 정책이 필요하면 `SecurityConfig`의 요청 인가 규칙에 `hasRole("ADMIN")`을 추가합니다.

```java
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

역할 문자열은 `Role` enum의 `ROLE_ADMIN`처럼 `ROLE_` 접두사를 포함합니다. Spring Security DSL에서는 `hasRole("ADMIN")`처럼 접두사를 제외하고 작성합니다.

### Refresh Token 정책 선택

현재 구현은 **다중 기기 로그인 허용** 정책입니다. 로그인할 때마다 별도 Refresh Token이 생성됩니다.

- 로그아웃: 해당 기기의 Refresh Token만 폐기
- 재발급: 사용한 Refresh Token을 새 토큰으로 회전
- 만료: 매일 03:00 정리 작업이 만료 행을 삭제

한 사용자당 단일 기기만 허용하려면 로그인 직전에 해당 UID의 기존 Refresh Token을 모두 삭제하는 Repository 메서드를 추가하면 됩니다. 이 정책은 다른 기기까지 함께 로그아웃시킨다는 점을 제품 요구사항으로 먼저 확정해야 합니다.

## 6. 운영 체크리스트

### 배포 전

- [ ] `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`를 배포 환경의 비밀 저장소 또는 환경 변수로 설정했다.
- [ ] 공개 저장소에 실제 DB 계정, JWT 키, API 키가 남아 있지 않은지 Git 이력까지 확인했다.
- [ ] 운영 DB에서 `spring.jpa.hibernate.ddl-auto`를 `validate`로 전환하고 Flyway 또는 Liquibase로 스키마를 관리한다.
- [ ] 운영 CORS origin을 실제 프론트엔드 도메인으로 제한했다.
- [ ] HTTPS를 적용했다.
- [ ] Refresh Token 만료 기간, 정리 Cron, 세션/기기 정책을 서비스 요구사항에 맞췄다.

### 장애·문의 대응

| 증상 | 우선 확인할 항목 |
| --- | --- |
| 시작 시 DB 연결 실패 | `DB_URL`, 계정, MySQL 네트워크·권한 |
| 보호 API가 401 | Access Token인지, `Authorization: Bearer` 형식인지, 만료 여부 |
| 재발급이 401 | Refresh Token인지, 이미 재발급·로그아웃으로 폐기됐는지, 만료 여부 |
| Refresh Token 행이 증가 | 다중 기기 로그인 정책상 정상. 만료 행은 스케줄 실행 후 삭제 |
| `ObjectMapper` Bean 오류 | Spring Boot 4에서는 `tools.jackson.databind.ObjectMapper`를 사용 |

## 7. GitHub 관리 권장 사항

### 커밋 대상 구분

`.gitignore`은 아래 정책을 적용합니다.

| GitHub에 포함 | GitHub에서 제외 |
| --- | --- |
| Java 소스, Maven POM, Maven Wrapper(`mvnw`, `mvnw.cmd`, `.mvn/`) | 실제 `application.yml`, `application-*.yml`, `.env*` |
| `application.yml.example` | DB 비밀번호, JWT 서명 키, API 키 |
| `README.md`, `docs/`, 테스트 코드 | `target/`, `build/`, `node_modules/`, 로그 |
| `.gitignore`, GitHub Actions 설정 | IntelliJ/VS Code/STS 개인 설정 파일 |

첫 커밋 전에 아래 명령으로 **GitHub에 실제로 올라갈 파일만** 검토하세요.

```powershell
git status --ignored
git add README.md docs .gitignore pom.xml mvnw mvnw.cmd .mvn security-app security-core security-jwt
git diff --cached --check
git diff --cached
```

`security-app/src/main/resources/application.yml`이 staged 목록에 나타나면 즉시 `git restore --staged security-app/src/main/resources/application.yml`을 실행하고 `.gitignore` 규칙을 다시 확인하세요.

- `main`은 항상 빌드 가능한 상태로 유지합니다.
- 기능별 브랜치(`feature/token-rotation` 등)에서 작업하고 Pull Request로 병합합니다.
- PR 전 `./mvnw test`를 실행합니다.
- 코드 변경과 함께 API 계약 또는 설정이 바뀌면 `README.md`와 이 문서를 갱신합니다.
- 기능이 커지면 `.github/workflows/`에 Maven 테스트 CI를 추가합니다.
- 실제 비밀 값은 commit하지 않습니다. 이미 커밋했다면 값을 교체하고 Git 이력도 정리해야 합니다.
