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
cat > .env <<'ENV'
POSTGRES_DB=moasem
POSTGRES_USER=moasem
POSTGRES_PASSWORD=<강력한_비밀번호>
GEMINI_API_KEY=<Gemini_API_키>
ENV
chmod 600 .env
```

`GEMINI_API_KEY` 는 결산 보고서의 AI 총평에 쓴다.
비워 두거나 아예 넣지 않아도 배포는 정상이며, AI 총평만 빠지고 보고서는 그대로 생성된다.

개인 계정 키 대신 **프로젝트용으로 따로 발급한 키**를 넣는다.
개인 키를 쓰면 발급자가 키를 폐기·교체할 때 서버가 같이 멈춘다.
키는 [Google AI Studio](https://aistudio.google.com/apikey)에서 발급한다.

키를 나중에 추가하거나 바꿀 때는 `.env` 를 고치고 컨테이너만 다시 올리면 된다.

```bash
cd ~/moasem
docker compose -f docker-compose.prod.yml up -d app
```

### 3.3 보안 그룹

- 22 (SSH): 배포용. GitHub Actions는 고정 IP가 아니므로 열어둬야 한다.
- 8080 (앱): 필요 범위만. 앞단에 Nginx/ALB를 둘 경우 8080은 닫고 그쪽만 연다.
- 5432 / 6379: **열지 않는다.** `docker-compose.prod.yml` 에서 `expose` 만 써서 컨테이너 네트워크 안에만 노출된다.

## 4. 배포 흐름

`main` push → 이미지 빌드/푸시 → EC2에서 `docker compose pull && up -d` →
`moasem-app` 컨테이너가 `healthy` 가 될 때까지 최대 3분 대기.
실패하면 워크플로가 앱 로그 100줄을 출력하고 실패 처리한다.

헬스 체크는 `/actuator/health` 를 사용한다.
`curl -f` 가 아닌 이유는 Spring Security 기본 설정에서 이 엔드포인트가 401을 주기 때문이다 —
401도 "앱이 응답 중"이라는 신호라 통과시키고, 연결 자체가 안 될 때만 실패로 본다.
**`SecurityConfig` 를 작성할 때 `/actuator/health` 를 `permitAll` 로 열어두면** 헬스 체크가 200을 받게 되고,
그때 compose 의 healthcheck 를 `curl -f` 로 조여도 된다.

## 5. 수동 조작

```bash
cd ~/moasem && export APP_IMAGE=ghcr.io/moa-sem/moasem-backend:latest && docker compose -f docker-compose.prod.yml logs -f app
```

특정 커밋으로 롤백:

```bash
cd ~/moasem && export APP_IMAGE=ghcr.io/moa-sem/moasem-backend:sha-<커밋해시> && docker compose -f docker-compose.prod.yml up -d app
```

## 6. RDS / ElastiCache 로 옮길 때

`docker-compose.prod.yml` 에서 `db`, `redis` 서비스와 `depends_on`, `volumes` 를 지우고
`app` 의 환경 변수만 바꾼다.

```yaml
DB_URL: jdbc:postgresql://<rds-엔드포인트>:5432/moasem
REDIS_HOST: <elasticache-엔드포인트>
```

`prod` 프로필은 이미 `DB_URL` / `REDIS_HOST` 를 환경 변수로 받으므로 애플리케이션 코드 변경은 없다.

## 7. 로컬 개발

로컬은 `docker-compose.yml` (앱을 직접 빌드, `local` 프로필) 을 그대로 쓴다.

```bash
docker compose up -d --build
```
