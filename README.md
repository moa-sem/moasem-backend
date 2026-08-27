# 모아셈

모임·동아리 공금 운영 서비스 백엔드

## 개요

모임 구성원이 실제 지출 후 증빙을 제출하고, 모임장이 승인한 지출만 행사 예산에 반영하며,
행사 종료 시 AI가 결산 분석이 포함된 PDF 보고서를 생성하는 공금 운영 서비스입니다.

자세한 기획 내용은 `모아셈_최종_기획안_v1.0`을 참고하세요.

## 기술 스택

- Kotlin 2.2.x
- Spring Boot 4.1.x (Java 17)
- PostgreSQL / Redis
- OAuth2 (Google) + JWT
- AWS S3, EC2

## 시작하기

```bash
./gradlew bootRun
```

로컬 프로필(`local`)이 기본으로 활성화됩니다. `application.yml`에서 DB/Redis 접속 정보를 환경에 맞게 수정하세요.

## 문서

- [개발 컨벤션](./docs/CONVENTION.md) — 패키지 구조, 네이밍, Git 전략, 코드 스타일
- [배포 가이드](./docs/DEPLOYMENT.md) — CI/CD 파이프라인, GitHub Secrets, EC2 초기 설정

## 패키지 구조

```
domain/{auth,group,event,spending,report}/{controller,service,dto,converter,entity,repository}
global/{config,error,security,response,util}
```

자세한 설명은 [컨벤션 문서](./docs/CONVENTION.md#2-패키지-구조) 참고.
