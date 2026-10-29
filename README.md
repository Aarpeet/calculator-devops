# Calculator DevOps — Spring Boot REST API with a Full CI/CD Pipeline

A production-style **Calculator REST API** (Spring Boot 3, Java 17, Maven) wrapped in an automated
DevOps pipeline: **Git/GitHub → Jenkins → Docker → Docker Hub → Docker Compose (+ Nginx)**.
A push to `main` automatically builds, tests, containerises, publishes and deploys the service.

> CSE 816 — Software Production Engineering, Mini-Project (IIIT Bangalore, Term I 2026-27)

| | |
|---|---|
| **GitHub** | https://github.com/Aarpeet/calculator-devops |
| **Docker Hub** | https://hub.docker.com/r/aarpeet/calculator-api |
| **Report** | [REPORT.md](REPORT.md) |

---

## Architecture

```
 Developer ──git push──▶ GitHub ──webhook / Poll SCM──▶ Jenkins (Jenkinsfile)
                                                          │
         ┌────────────┬───────────┬─────────────┬────────┴───┬──────────────┐
         ▼            ▼           ▼             ▼            ▼              ▼
     Checkout       Build        Test      Docker Build     Push          Deploy
    (checkout   (mvn package) (mvn test +   (multi-stage  (Docker Hub   (docker compose
       scm)                    JUnit XML)    image :N +    :N + :latest)  up -d + health
                                             :latest)                     verification)
                                                                              │
                     Docker host (network: calc-net)                          ▼
              ┌─────────────────────────────────────────────────────────────────────┐
  client ───▶ │ nginx-proxy :8081 ──proxy_pass──▶ calculator-app :8082 → 8080       │
              │ (nginx:stable-alpine)             (aarpeet/calculator-api:N)        │
              │                                    healthcheck /actuator/health     │
              └─────────────────────────────────────────────────────────────────────┘
                                            Email notification (success / failure)
```

| Component | Container port | Host port | Notes |
|---|---|---|---|
| `calculator-app` (Spring Boot) | 8080 | **8082** (`APP_HOST_PORT`) | 8080 on the host is used by Jenkins |
| `nginx-proxy` (reverse proxy) | 80 | **8081** (`PROXY_HOST_PORT`) | Public entry point |
| Jenkins | — | 8080 | Native service on the same host |

## Tech stack

Java 17 · Spring Boot 3.5 (Web, Actuator) · Maven · JUnit 5 + MockMvc + AssertJ · JaCoCo ·
Docker (multi-stage) · Docker Compose · Nginx · Jenkins (declarative pipeline) · Docker Hub

## Repository layout

```
calculator-devops/
├── src/main/java/com/spe/calculator/
│   ├── CalculatorApplication.java          # Spring Boot entry point
│   ├── controller/CalculatorController.java # GET /api/v1/{add,subtract,multiply,divide}
│   ├── service/CalculatorService.java      # BigDecimal arithmetic + operand validation
│   ├── dto/CalculationResponse.java        # {"operation","a","b","result"}
│   ├── dto/ErrorResponse.java              # {"error","status","timestamp"}
│   └── exception/GlobalExceptionHandler.java  # @ControllerAdvice → HTTP 400 JSON
├── src/main/resources/
│   ├── application.properties              # ${SERVER_PORT:8080}, Actuator exposure
│   └── application-prod.properties         # prod profile (used in Compose)
├── src/test/java/com/spe/calculator/
│   ├── service/CalculatorServiceTest.java                 # unit tests
│   └── controller/CalculatorControllerIntegrationTest.java # @SpringBootTest + MockMvc
├── nginx/nginx.conf      # reverse proxy → http://calculator-app:8080
├── Dockerfile            # multi-stage: Maven/JDK builder → JRE runtime, non-root
├── .dockerignore
├── docker-compose.yml    # calculator-app + nginx-proxy on calc-net
├── Jenkinsfile           # Checkout → Build → Test → Docker Build → Push → Deploy
├── .gitignore
├── README.md
└── REPORT.md
```

## Prerequisites

| Tool | Version used | Needed for |
|---|---|---|
| JDK | 17+ (tested on 17 and 21) | local build / tests |
| Maven | 3.8+ | local build / tests |
| Docker Engine + Compose v2 | 29.x / v2+ | containers & deployment |
| Jenkins | LTS (tested on 2.580.1) | CI/CD |

---

## Build, test and run locally

```bash
mvn clean test               # 48 tests; JUnit XML → target/surefire-reports/*.xml
mvn clean package            # executable JAR → target/calculator-api.jar
java -jar target/calculator-api.jar            # http://localhost:8080
SERVER_PORT=9000 java -jar target/calculator-api.jar   # custom port
```

Coverage report (JaCoCo) is written to `target/site/jacoco/index.html` by `mvn test`.

## API reference

All endpoints are `GET` and take numeric query parameters `a` and `b` (integers, decimals,
negatives and scientific notation such as `1e3` are accepted). Results are exact (`BigDecimal`),
and numbers are rendered cleanly (`5`, not `5.0`; `0.3`, not `0.30000000000000004`).

| Operation | Endpoint | Result |
|---|---|---|
| Add | `/api/v1/add?a=&b=` | `a + b` |
| Subtract | `/api/v1/subtract?a=&b=` | `a - b` |
| Multiply | `/api/v1/multiply?a=&b=` | `a * b` |
| Divide | `/api/v1/divide?a=&b=` | `a / b` (34 significant digits for non-terminating results) |
| Health | `/actuator/health` | `UP` / `DOWN` |
| Info | `/actuator/info` | App name, Jenkins build number, Maven version and build time |

The examples below go through the Nginx proxy (`:8081`). The app is also reachable directly on `:8082`
(Compose) or `:8080` (plain `java -jar`).

### Successful requests

```bash
$ curl "http://localhost:8081/api/v1/add?a=10&b=5"
{"operation":"add","a":10,"b":5,"result":15}

$ curl "http://localhost:8081/api/v1/subtract?a=10&b=5"
{"operation":"subtract","a":10,"b":5,"result":5}

$ curl "http://localhost:8081/api/v1/multiply?a=10&b=5"
{"operation":"multiply","a":10,"b":5,"result":50}

$ curl "http://localhost:8081/api/v1/divide?a=10&b=4"
{"operation":"divide","a":10,"b":4,"result":2.5}

$ curl "http://localhost:8081/api/v1/add?a=0.1&b=0.2"
{"operation":"add","a":0.1,"b":0.2,"result":0.3}

$ curl "http://localhost:8081/actuator/health"
{"status":"UP","groups":["liveness","readiness"]}

$ curl "http://localhost:8081/actuator/info"
{"app":{"name":"calculator-api","build":"5"},"build":{"artifact":"calculator-api","name":"calculator-api","time":"2026-10-29T22:40:12.345Z","version":"1.0.0","group":"com.spe"}}
```

`app.build` is the Jenkins build number baked into the image (`local` for builds outside Jenkins), so you can
tell which pipeline run is currently deployed.

### Error cases (HTTP 400, structured JSON)

```bash
$ curl -i "http://localhost:8081/api/v1/divide?a=10&b=0"
HTTP/1.1 400
{"error":"Division by zero is not allowed","status":400,"timestamp":"2026-10-27T14:06:21.396Z"}

$ curl "http://localhost:8081/api/v1/add?a=abc&b=5"
{"error":"Invalid value 'abc' for query parameter 'a': must be a number","status":400,"timestamp":"..."}

$ curl "http://localhost:8081/api/v1/add?a=2"
{"error":"Missing or empty query parameter 'b' (usage: /api/v1/add?a=<number>&b=<number>)","status":400,"timestamp":"..."}

$ curl "http://localhost:8081/api/v1/add?a=1e999999999&b=1"
{"error":"Operand 'a' is out of range: magnitude must be between 1e-100 and 1e100","status":400,"timestamp":"..."}
```

| Cause | Exception handled by `GlobalExceptionHandler` |
|---|---|
| Missing / empty parameter | `MissingServletRequestParameterException` |
| Non-numeric value (`abc`, `NaN`) | `MethodArgumentTypeMismatchException` |
| Division by zero (`0`, `0.0`, `-0`) | `ArithmeticException` |
| Operand outside `[1e-100, 1e100]` | `IllegalArgumentException` |

---

## Docker

```bash
docker build -t aarpeet/calculator-api:latest .
docker run -d --name calculator -p 8082:8080 aarpeet/calculator-api:latest
curl "http://localhost:8082/api/v1/add?a=2&b=3"
docker rm -f calculator
```

Image highlights: multi-stage build (Maven/JDK builder → `eclipse-temurin:17-jre-alpine` runtime),
cached dependency layer (`pom.xml` copied and resolved before `src/`), non-root `appuser`,
read-only root-owned JAR, `EXPOSE 8080`, built-in `HEALTHCHECK`, exec-form `ENTRYPOINT`, and OCI labels
recording the Jenkins build number, git commit and build time:

```bash
docker inspect aarpeet/calculator-api:latest --format '{{json .Config.Labels}}'
```

## Docker Compose (full stack)

```bash
docker compose up -d --build          # build locally and start app + proxy
docker compose ps                     # both services should be (healthy)
docker compose logs -f calculator-app
docker compose down                   # stop and remove the stack

# Deploy a specific published image (what Jenkins does):
DOCKER_IMAGE=aarpeet/calculator-api:42 docker compose up -d --remove-orphans

# Use different host ports if 8081/8082 are taken:
APP_HOST_PORT=9082 PROXY_HOST_PORT=9081 docker compose up -d
```

`docker-compose.yml` defines the custom bridge network `calc-net`, a health check on
`/actuator/health` (10s interval / 5s timeout / 5 retries / 20s start period), `restart: unless-stopped`,
environment variables (`SPRING_PROFILES_ACTIVE=prod`, `SERVER_PORT=8080`), and makes Nginx wait until
the API is healthy (`depends_on: condition: service_healthy`). The project name is fixed
(`calculator-devops`) so local runs and Jenkins manage the same stack.

Both containers are hardened: read-only root filesystems (with `tmpfs` only where writes are needed),
`no-new-privileges`, CPU/memory limits (512 MB for the API, 64 MB for Nginx), rotated JSON logs
(3 × 10 MB) and a 30 s stop grace period that matches Spring's graceful shutdown.

---

## Operating the deployment

### What is running
```bash
docker compose ps
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
curl -s http://localhost:8081/actuator/info                     # app.build = Jenkins build currently live
docker image inspect "$(docker inspect -f '{{.Config.Image}}' calculator-app)" \
  --format '{{json .Config.Labels}}'                            # build number, git commit, build time
```

### Smoke-test every endpoint, directly (8082) and through the proxy (8081)
```bash
for port in 8082 8081; do
  for path in "actuator/health" "api/v1/add?a=10&b=5" "api/v1/subtract?a=10&b=5" \
              "api/v1/multiply?a=10&b=5" "api/v1/divide?a=10&b=4" \
              "api/v1/divide?a=10&b=0" "api/v1/add?a=abc&b=5" "api/v1/add?a=2"; do
    printf '%-5s %-28s ' "$port" "$path"; curl -s -w ' [%{http_code}]\n' "http://localhost:$port/$path"
  done
done
```

### Ship a change
Every push to `main` is built, tested, published and deployed with no manual steps:
```bash
git switch -c feature/<name>
# edit code and tests
mvn -q test
git commit -am "Describe the change"
git push -u origin feature/<name>      # then open and merge a PR (or push small fixes straight to main)
watch -n 2 'curl -s http://localhost:8081/actuator/info'   # app.build flips to the new build number
```
A change that breaks a test stops at the *Test* stage: no image is pushed, the running version keeps
serving, and a failure email is sent.

### Roll back to an earlier build
Each build's image stays on Docker Hub under its build number, so a rollback is a single command
(the next pipeline run deploys its own build again):
```bash
DOCKER_IMAGE=aarpeet/calculator-api:10 docker compose up -d
```

### Proxy behaviour
```bash
curl -sI http://localhost:8081/actuator/health          # security headers
seq 150 | xargs -P 75 -I{} curl -s -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8081/api/v1/add?a=1&b=2' | sort | uniq -c    # burst: excess requests get 429
docker compose stop calculator-app
curl -s 'http://localhost:8081/api/v1/add?a=1&b=2'      # JSON 503 instead of an HTML error page
docker compose start calculator-app
```

### Logs and history
```bash
docker compose logs -f --tail=50 calculator-app
git log --graph --oneline --all
```

---

## Jenkins CI/CD setup

The pipeline is defined in [`Jenkinsfile`](Jenkinsfile):
**Checkout → Build → Test → Docker Build → Push → Deploy**, plus email notifications.

### 1. One-time host preparation (Jenkins running natively on Linux)

```bash
# Let the jenkins user talk to the Docker daemon, then restart Jenkins
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins

# Sanity check: must print versions without "permission denied"
sudo -u jenkins docker version && sudo -u jenkins docker compose version && sudo -u jenkins mvn -v
```

### 2. Plugins

*Manage Jenkins → Plugins*: **Pipeline**, **Git**, **GitHub**, **JUnit**, **Credentials Binding**,
**Email Extension**, **Timestamper** (all part of the suggested plugin set).

### 3. Credentials (never hard-coded)

*Manage Jenkins → Credentials → System → Global → Add Credentials*

| Field | Value |
|---|---|
| Kind | Username with password |
| Username | `aarpeet` |
| Password | a Docker Hub **access token** (Docker Hub → Account settings → Personal access tokens, Read & Write) |
| ID | `dockerhub-creds` |

The Jenkinsfile reads it with `credentials('dockerhub-creds')` and logs in with `--password-stdin`;
the token is masked in the console log and `docker logout` always runs after the push.

### 4. Email notifications

*Manage Jenkins → System*:

- **Jenkins Location → System Admin e-mail address**: the sender address.
- **Extended E-mail Notification** (used by `emailext`) and **E-mail Notification** (fallback `mail`):
  SMTP server `smtp.gmail.com`, port `465`, *Use SSL*, credentials = Gmail address + **App Password**
  (or your institute SMTP relay). Use *Test configuration* to send a test mail.

Notifications go to `Aarpeet135@gmail.com` on every success and failure and include the job name,
build number and build URL. (The institute mailbox is hosted on Microsoft 365, which tends to quarantine
automated mail relayed through Gmail, so a Gmail inbox is used as the recipient.)

> **Debugging email:** email-ext does not print SMTP failures in normal mode, so a build log showing only
> `Sending email to: …` does not prove delivery. Enable *Extended E-mail Notification → Enable Debug Mode*
> to log the SMTP conversation; `535-5.7.8 Username and Password not accepted` means the credential holds
> the normal Gmail password instead of an App Password.

### 5. Create the pipeline job

1. *New Item* → name `calculator-devops` → **Pipeline**.
2. *Pipeline → Definition*: **Pipeline script from SCM** → SCM **Git** →
   Repository URL `https://github.com/Aarpeet/calculator-devops.git` → Branch `*/main` → Script Path `Jenkinsfile`.
3. Save and click **Build Now** once. This first run registers the `githubPush()` and `pollSCM` triggers
   declared in the Jenkinsfile.

### 6. Automatic triggering from GitHub

- **Webhook (preferred):** GitHub repo → *Settings → Webhooks → Add webhook* →
  Payload URL `https://<public-jenkins-url>/github-webhook/`, content type `application/json`, *Just the push event*.
  A Jenkins on `localhost` is not reachable from GitHub, so expose it with a tunnel. With an ngrok static
  domain (set it as the *Jenkins URL* too), run:
  ```bash
  ngrok http --url=https://<your-static-domain>.ngrok-free.dev 8080
  ```
  Expose Jenkins only with authentication enabled: anonymous users must not hold any admin rights.
  The webhook can also be created from the CLI:
  ```bash
  gh api repos/Aarpeet/calculator-devops/hooks -X POST -f name=web -F active=true -f 'events[]=push' \
    -f 'config[url]=https://<public-jenkins-url>/github-webhook/' -f 'config[content_type]=json'
  ```
- **Checking deliveries:** every push should show `200` in the webhook's *Recent Deliveries* tab, and the
  build cause in Jenkins reads *Started by GitHub push*. From the CLI:
  ```bash
  gh api repos/Aarpeet/calculator-devops/hooks --jq '.[].id'                 # webhook id
  gh api repos/Aarpeet/calculator-devops/hooks/<id>/deliveries \
    --jq '.[] | "\(.delivered_at) \(.event) \(.status_code)"'
  ```
- **Fallback:** `pollSCM('H/5 * * * *')` checks GitHub every ~5 minutes, so pushes are still deployed
  automatically even without a webhook.

### Pipeline guarantees

- **Fails fast**: a failing unit/integration test fails the *Test* stage, and JUnit results are always published.
- **Immutable tags**: each run pushes `aarpeet/calculator-api:<BUILD_NUMBER>` and `:latest`; a failed build
  never publishes an image, and any earlier tag can be redeployed for a rollback.
- **Idempotent**: `disableConcurrentBuilds()`, a fixed Compose project, `--remove-orphans`;
  re-running only replaces the `calculator-app` container, and Nginx resolves it dynamically, so the proxy keeps working.
  `nginx-proxy` is recreated only when `nginx/nginx.conf` actually changed.
- **Verified deploy**: the stage polls `:8082/actuator/health`, `:8081/api/v1/add?a=2&b=3` and
  `:8081/actuator/info` (which must report this run's build number, proving the new container is live) and
  fails, dumping `docker compose ps` and logs, if that doesn't happen within ~150s.

---

## Git workflow

- `main` holds releasable code and every push to it is deployed. Features are developed on branches and
  merged through Pull Requests; small follow-up fixes go straight to `main`.

| PR | Branch | Change |
|---|---|---|
| [#1](https://github.com/Aarpeet/calculator-devops/pull/1) | `feature/multiply-divide-validation` | multiply/divide, validation, Actuator, tests |
| [#2](https://github.com/Aarpeet/calculator-devops/pull/2) | `feature/devops-pipeline` | Dockerfile, Compose + Nginx, Jenkinsfile, docs |
| [#3](https://github.com/Aarpeet/calculator-devops/pull/3) | `feature/notification-recipient` | build notifications to a Gmail inbox |
| [#4](https://github.com/Aarpeet/calculator-devops/pull/4) | `feature/build-info` | build number on `/actuator/info` |
| [#5](https://github.com/Aarpeet/calculator-devops/pull/5) | `feature/verify-deployed-build` | Deploy stage verifies the live build number |
| [#6](https://github.com/Aarpeet/calculator-devops/pull/6) | `feature/oci-labels` | OCI image labels, `--pull` |
| [#7](https://github.com/Aarpeet/calculator-devops/pull/7) | `feature/compose-hardening` | read-only containers, limits, log rotation |

- `.gitignore` excludes `target/`, IDE files and secret material (`.env`, keys, keystores).
  No credentials live in the repository; Docker Hub access comes only from the Jenkins credentials store.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `permission denied ... /var/run/docker.sock` in Jenkins | `sudo usermod -aG docker jenkins && sudo systemctl restart jenkins` |
| `Bind for 0.0.0.0:8082 failed: port is already allocated` | Free the port or set `APP_HOST_PORT` / `PROXY_HOST_PORT` |
| `credentials('dockerhub-creds')` not found | Create the credential with exactly that ID (step 3) |
| No email received | Check SMTP settings with *Test configuration*; check spam folder |
| Pipeline not triggered on push | First run must be manual; check webhook deliveries or wait for Poll SCM |
