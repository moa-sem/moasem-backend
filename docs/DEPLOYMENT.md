# 배포 (CI/CD)

## 1. 파이프라인 개요

| 워크플로 | 파일 | 트리거 | 하는 일 |
|---|---|---|---|
| CI | `.github/workflows/ci.yml` | `main`/`develop` 대상 PR, `main`/`develop` push | JDK 17 + Gradle 캐시로 `./gradlew build` (테스트 포함), 테스트 리포트 아티팩트 업로드 |
| CD | `.github/workflows/cd.yml` | `main` push, 수동 실행 | Docker 이미지 빌드 → GHCR 푸시 → EC2 SSH 배포 → 헬스 체크 |

브랜치 전략(`main` ← `develop` ← `feature/*`)에 맞춰 **`develop`은 검증만, `main` 머지 시에만 배포**된다.

이미지는 `ghcr.io/moa-sem/moasem-backend` 에 `latest` 와 `sha-<커밋해시>` 두 태그로 푸시된다.
문제가 생기면 EC2에서 `sha-` 태그로 롤백할 수 있다.

## 2. GitHub Secrets

리포지토리 Settings → Secrets and variables → Actions 에 등록한다.

| 이름 | 값 | 필수 |
|---|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 | O |
| `EC2_USER` | SSH 계정명 (Ubuntu AMI는 `ubuntu`, Amazon Linux는 `ec2-user`) | O |
| `EC2_SSH_KEY` | SSH 개인키 **전문** (`-----BEGIN ... KEY-----` 줄 포함) | O |
| `EC2_PORT` | SSH 포트, 미등록 시 22 | X |

`GITHUB_TOKEN`은 GitHub가 자동 제공하므로 따로 등록하지 않는다. GHCR 푸시/풀 모두 이 토큰을 쓴다.

## 3. EC2 최초 준비 (한 번만)

### 3.1 Docker 설치

```bash
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER   # 적용하려면 재접속
```

### 3.2 배포 디렉터리와 `.env` 생성

CD는 `~/moasem` 에 `docker-compose.prod.yml` 을 전송하고, 같은 위치의 `.env` 를 읽는다.
`.env` 는 비밀값이라 리포지토리에 두지 않고 **서버에서 직접 만든다**.

```bash
mkdir -p ~/moasem && cd ~/moasem


```bash
cd ~/moasem
docker compose -f docker-compose.prod.yml up -d app
```

### 3.3 보안 그룹

**EC2 보안 그룹**

- 22 (SSH): 배포용. GitHub Actions는 고정 IP가 아니므로 열어둬야 한다.
- 8080 (앱): 필요 범위만. 앞단에 Nginx/ALB를 둘 경우 8080은 닫고 그쪽만 연다.
- 6379: **열지 않는다.** Redis는 `expose` 만 써서 컨테이너 네트워크 안에만 노출된다.

**RDS 보안 그룹**

인바운드에 5432를 열되, 소스를 **EC2의 보안 그룹**으로 지정한다.
IP로 지정하면 EC2를 재시작해 IP가 바뀔 때 연결이 끊긴다.

이 설정이 없으면 앱이 DB 연결 타임아웃으로 기동에 실패한다.
RDS는 퍼블릭 액세스를 끈 상태로 둔다.

## 4. 배포 흐름

`main` push → 이미지 빌드/푸시 → EC2에서 `docker compose pull && up -d` →
`moasem-app` 컨테이너가 `healthy` 가 될 때까지 최대 3분 대기.
실패하면 워크플로가 앱 로그 100줄을 출력하고 실패 처리한다.

헬스 체크는 `/actuator/health` 를 사용한다.
`curl -f` 가 아닌 이유는 Spring Security 기본 설정에서 이 엔드포인트가 401을 주기 때문이다 —
401도 "앱이 응답 중"이라는 신호라 통과시키고, 연결 자체가 안 될 때만 실패로 본다.
**`SecurityConfig` 를 작성할 때 `/actuator/health` 를 `permitAll` 로 열어두면** 헬스 체크가 200을 받게 되고,
그때 compose 의 healthcheck 를 `curl -f` 로 조여도 된다.

## 4.1 알려진 제약: 스키마가 자동으로 만들어지지 않는다

운영 프로파일은 `ddl-auto: validate` 다. 엔티티와 테이블이 맞는지 확인만 하고,
없는 테이블을 만들어 주지는 않는다.

따라서 **비어 있는 RDS에 처음 배포하면 앱이 기동에 실패한다.**

```
새 RDS(테이블 0개) → 앱 기동 → validate 실패 → 헬스 체크 3분 대기 → 배포 실패
```

로컬(`ddl-auto: update`)에서는 Hibernate가 테이블을 만들어 주기 때문에 이 문제가 드러나지 않는다.
운영에서 Hibernate가 스키마를 바꾸게 두면 위험하므로 `validate` 자체는 의도한 설정이고,
**테이블을 누가 만들 것인가가 아직 정해지지 않았다.**

선택지는 둘이다.

- **Flyway 도입** — `src/main/resources/db/migration` 에 SQL을 두면 기동 시 적용된다.
  스키마가 버전 관리되고 팀원 로컬에도 같은 스키마가 깔린다.
- **수동 생성** — RDS에 직접 붙어 `CREATE TABLE` 을 실행한다. 지금은 빠르지만
  엔티티가 바뀔 때마다 사람이 맞춰야 하고, 놓치면 `validate` 가 배포를 막는다.

모든 도메인의 테이블이 걸려 있어 팀 논의가 필요하다. 정해지기 전까지 배포는 성공하지 않는다.

## 5. 수동 조작

```bash
cd ~/moasem && export APP_IMAGE=ghcr.io/moa-sem/moasem-backend:latest && docker compose -f docker-compose.prod.yml logs -f app
```

특정 커밋으로 롤백:

```bash
cd ~/moasem && export APP_IMAGE=ghcr.io/moa-sem/moasem-backend:sha-<커밋해시> && docker compose -f docker-compose.prod.yml up -d app
```

## 6. ElastiCache 로 옮길 때

DB는 이미 RDS를 쓴다. Redis는 아직 EC2 안의 컨테이너다.

옮기려면 `docker-compose.prod.yml` 에서 `redis` 서비스와 `depends_on`, `redis_data` 볼륨을 지우고
`app` 의 환경 변수만 바꾼다.

```yaml
REDIS_HOST: ${REDIS_HOST:?.env에 REDIS_HOST가 없습니다}
```

`prod` 프로필은 이미 `REDIS_HOST` 를 환경 변수로 받으므로 애플리케이션 코드 변경은 없다.
DB와 마찬가지로 ElastiCache 보안 그룹에서 EC2 보안 그룹의 6379 인바운드를 허용해야 한다.

## 7. 로컬 개발

로컬은 `docker-compose.yml` (앱을 직접 빌드, `local` 프로필) 을 그대로 쓴다.

```bash
docker compose up -d --build
```
