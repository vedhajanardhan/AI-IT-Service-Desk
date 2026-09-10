# Docker Verification Runbook

## Why this is a runbook, not a "verified" checkmark

This sandbox has no Docker daemon (`docker: not found`), so I could not actually run
`docker compose up` or pull any images. What I *did* do instead - a static review, listed below -
catches a real class of bugs, but it's not the same as watching the stack actually come up. Please
run through this yourself; if anything fails, paste me the error and I'll fix it.

## What was statically verified (no Docker needed)

- `docker-compose.yml` is valid YAML (parsed with PyYAML) with the expected 5 services and volumes.
- The backend Dockerfile's final `COPY --from=build /app/target/it-service-desk.jar` matches
  `pom.xml`'s `<finalName>it-service-desk</finalName>` exactly - a very common source of "file not
  found" errors in multi-stage Docker builds, and it's correct here.
- Every environment variable the `backend` service passes in `docker-compose.yml` (`DB_HOST`, `DB_PORT`,
  `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, etc.) matches a placeholder actually read in
  `application.yml`, and vice versa - no typo'd env var names.
- The frontend's `package-lock.json` exists and is committed, so `npm install` in the Docker build
  stage will resolve the exact versions verified in Phase 8/9, not potentially-different latest ones.
- Kafka is configured in KRaft mode (no Zookeeper) with a `PLAINTEXT` internal listener
  (`kafka:19092`, used by the backend inside the Docker network) and an `EXTERNAL` listener
  (`localhost:9092`, for connecting from your host machine if you want to inspect topics directly).

## What I could not verify and you should watch for

- **`wget` inside the `eclipse-temurin:17-jre-alpine` and `nginx:1.27-alpine` images.** Both
  healthchecks use `wget`. Alpine's busybox usually includes it, but I can't pull the image here to
  confirm. If a healthcheck shows `unhealthy` immediately, this is the first thing to check
  (`docker exec <container> which wget`).
- **The `apache/kafka:3.8.0` image's KRaft auto-formatting.** I configured `KAFKA_NODE_ID`,
  `KAFKA_PROCESS_ROLES`, and `KAFKA_CONTROLLER_QUORUM_VOTERS` for a single-node KRaft broker, which
  matches the official image's documented setup, but I have not run it. If Kafka fails to start, check
  `docker compose logs kafka` first - KRaft storage formatting issues are the most common cause.
- Whether `mvn clean package` inside the backend build stage actually succeeds - this is still the
  same unverified Java code from earlier phases. If it fails here, that's the first real compile
  signal on the backend; fix any errors before worrying about anything else in this runbook.

## Step-by-step

```bash
cd it-service-desk
cp .env.example .env
# edit .env if you want a real JWT_SECRET / ANTHROPIC_API_KEY, otherwise defaults work for local testing

docker compose up --build
```

Watch the logs. In order, you should see:
1. `postgres` and `redis` report healthy within ~10s.
2. `kafka` takes longer (KRaft storage formatting on first boot) - give it 30-60s.
3. `backend` waits for all three (`depends_on: condition: service_healthy`), then runs Flyway
   migrations (`V1` through `V5` - watch for `Successfully applied 5 migrations` in the logs), then
   starts Spring Boot.
4. `frontend` builds and starts nginx.

### Confirm each service individually

```bash
# Postgres
docker compose exec postgres pg_isready -U servicedesk

# Redis
docker compose exec redis redis-cli ping        # expect PONG

# Backend health
curl http://localhost:8080/actuator/health      # expect {"status":"UP",...}

# Backend Swagger UI (visual confirmation the app started with all controllers registered)
open http://localhost:8080/swagger-ui.html

# Frontend
open http://localhost:5173
```

### The end-to-end workflow test (from the original spec)

This is the flow the spec explicitly asks for. Do it through the UI at `localhost:5173`:

1. **Register** two accounts: one `EMPLOYEE`, one `ENGINEER`.
2. **Login** as the employee, create an incident with a description containing a keyword like
   "database connection timeout" (the mock AI provider pattern-matches on this).
3. **Login** as the engineer. Open the incident, click **Run AI Analysis** - you should see a
   classification, confidence score, root cause, and a recommended remediation action within a
   couple seconds (mock provider, no API key needed).
4. Confirm **Related Knowledge Articles** shows the seeded "Troubleshooting database connection pool
   exhaustion" article.
5. Assign the incident to yourself (or another engineer account).
6. In the **Remediation** panel, request the AI-recommended action. If it needs approval, approve it.
7. Watch the workflow stepper - it should move through Execution → Health Check → Resolved within a
   few seconds (mock executor). If the mock executor's simulated ~15% failure rate happens to trigger,
   you'll instead see a retry, and eventually either a second success or - after exhausting the
   action's retry limit - an escalation. Either outcome is correct behavior, not a bug.
8. Check the **notification bell** - you should see "AI analysis completed" and "remediation completed"
   (or "failed") notifications.
9. As admin (register a third `ADMIN` account), check `/admin/analytics` - the incident you just
   created and resolved should show up in the counts.

If every step above works, the whole stack - Postgres, Redis, Kafka, the Spring Boot backend, and the
React frontend - is genuinely integrated end-to-end, not just individually plausible.
