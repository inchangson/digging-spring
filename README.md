# Diggin Spring

Spring의 다양한 주제를 독립적인 코드와 테스트로 공부하는 Gradle 멀티 모듈 저장소입니다.

## 구조

```text
topics/
  authorization-server/   # SAS 확장 경계, OAuth2/OIDC, 동시성 실험
    src/main/
    src/test/
    docs/
    scripts/
    compose.yaml
    build.gradle
templates/
  topic/                  # 새 학습 주제 템플릿
notes/                    # 책·강의 메모
```

- `topics/{topic}`: 주제별 독립 Gradle 모듈. 의존성과 실행 환경은 모듈 안에서 관리합니다.
- 루트 `build.gradle`: 공통 Java toolchain, Spring Boot BOM, JUnit 설정.
- 모듈 `build.gradle`: 해당 주제에 필요한 의존성과 실행 설정.

현재 학습 내용은 [Authorization Server 학습 가이드](topics/authorization-server/README.md)에서 시작합니다.
추가할 주제 예시: IoC/DI, AOP, MVC, 트랜잭션, JPA, Security, Batch, 이벤트와 캐시.

## 실행

JDK 17이 필요하며, Gradle은 Wrapper로 실행합니다. 기존 실험 버전인 Spring Boot 3.5.15와 SAS 1.5.8을 유지합니다.
Gradle 8.14.3은 [Spring Boot 3.5의 지원 범위](https://docs.spring.io/spring-boot/3.5/system-requirements.html)에 맞췄습니다.

아래 명령은 저장소 루트에서 실행합니다. Authorization Server 테스트는 PostgreSQL을 사용하므로 Docker가 필요합니다.

```bash
./gradlew projects
docker compose -f topics/authorization-server/compose.yaml up -d --wait
./gradlew test
./gradlew :topics:authorization-server:test
./gradlew :topics:authorization-server:bootRun
./gradlew :topics:authorization-server:bootJar
```

테스트 보고서는 `topics/authorization-server/build/reports/tests/test/index.html`,
실행 JAR는 `topics/authorization-server/build/libs/sas-demo-1.0.0.jar`에 생성됩니다.
부하 실험은 `./topics/authorization-server/scripts/run-load-profile.sh`로 실행합니다.

## 새 주제 추가

```bash
cp -R templates/topic topics/transaction
```

루트 `settings.gradle`에 모듈을 등록합니다.

```groovy
include 'topics:transaction'
```

새 모듈의 README에 학습 목적을 적고, `build.gradle`에 필요한 의존성을 추가합니다.
코드는 `src/main/java`, 검증은 `src/test/java`에 둡니다.
`./gradlew :topics:transaction:test`로 해당 주제만 실행할 수 있습니다.
Java 버전은 모듈의 `java.toolchain.languageVersion`으로 재정의할 수 있습니다.
