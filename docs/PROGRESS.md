# Build Progress

## Phase 1 — Architecture & project structure ✅
- `backend/` (Spring Boot, Maven) and `frontend/` (React, not yet scaffolded) side by side.
- Package structure: `config, security, common, auth, user, incident, ai, remediation, knowledgebase,
  notification, analytics, audit, kafka`.

## Phase 2 — Backend foundation + authentication ✅
- `User` entity implementing `UserDetails` directly (role stored as enum: EMPLOYEE/ENGINEER/ADMIN).
- JWT access + refresh tokens (`JwtService`), stateless security filter chain, BCrypt (strength 12).
- `SecurityConfig` wires role-based endpoint rules; method-level `@PreAuthorize` is enabled for finer
  control inside services as later phases need it.
- `GlobalExceptionHandler` + typed `ApplicationException` subtypes give every error a consistent JSON
  shape and correct HTTP status.
- `CorrelationIdFilter` stamps every request with an `X-Correlation-Id` (generates one if absent) and
  puts it in the logging MDC — this is what will let you trace one incident's AI analysis + remediation
  across services in the logs later.

## Phase 3 — Incident management ✅
- `Incident`, `IncidentComment`, `IncidentEvent` entities. `IncidentEventType` enum covers the full
  lifecycle (created, assigned, AI analysis done/failed, remediation recommended/approved/rejected/
  started/succeeded/failed, health check passed/failed, escalated, resolved, reopened) — the remediation
  phase will start writing more of these event types, the plumbing already exists.
- `IncidentStateMachine`: explicit adjacency map of legal transitions, `validateTransition` throws
  `InvalidStateTransitionException` (HTTP 409) on illegal moves. Fully unit-testable without Spring context
  — see the test file, 12 cases covering happy path, escalation from any state, retry loops, reopening,
  and rejected illegal transitions.
- `IncidentSpecifications` + `JpaSpecificationExecutor` for composable search/filter (status, category,
  severity, keyword, reporter, assignee) without an explosion of repository methods.
- Role-scoped visibility: employees only ever see/query their own incidents (enforced in the service layer,
  not just the controller) — `enforceViewAccess` is reused by comments and the timeline endpoint too.
- Optimistic locking (`@Version` on `BaseEntity`) so two engineers acting on the same incident concurrently
  get a conflict instead of a silent overwrite.

## Phase 4 — Kafka + Redis ✅ (producer side only)
- Topics: `incident.created/assigned/analyzed`, `remediation.requested/started/completed/failed`,
  `incident.resolved/escalated`, plus a `servicedesk.dlt` dead-letter topic — declared as `NewTopic` beans
  so they're auto-provisioned against the broker on boot.
- `EventPublisher`: publish failures are logged, never thrown — Kafka carries side effects, so a broker
  hiccup can't break the underlying REST write (e.g. incident creation still succeeds even if the event
  fails to publish).
- `IdempotencyGuard`: Redis `SETNX`-based claim check, ready for the remediation Kafka consumers so a
  redelivered message can't trigger the same remediation twice.
- `RedisConfig`: cache manager with per-cache TTLs (knowledge base 30 min, analytics 2 min) — deliberately
  does **not** cache incidents themselves (they mutate too often and staleness there is dangerous when
  approving remediation against a stale status).
- **Not yet built**: the actual `@KafkaListener` consumers. Those belong to Phase 6 (remediation), since
  that's the module that reacts to `RemediationRequested` and orchestrates execution.

## Verification status
- **Not compiled.** This sandbox's egress allowlist doesn't include Maven Central (only npm/pip/GitHub
  registries), so `mvn` cannot download Spring Boot / Kafka / Redis client dependencies here. Everything
  was written directly against Spring Boot 3.3.x / Spring Kafka / Spring Data Redis APIs I'm confident in,
  but you should run this locally before relying on it:

  ```bash
  cd backend
  mvn clean verify
  ```

  If anything fails to compile, paste me the error and I'll fix it — that's a normal, expected step for a
  project this size, not a sign something is fundamentally wrong.

- One thing worth double-checking yourself once it compiles: the `@ConfigurationProperties` record
  (`KafkaTopicsProperties`) — Spring Boot 3.3 supports constructor-bound `@ConfigurationProperties` records,
  but do confirm your exact Spring Boot patch version behaves the same way.

## Phase 5 — AI analysis ✅
- `AiProvider` interface; `MockAiProvider` (deterministic keyword-based classification covering all 8 KB
  incident signatures, zero API key needed) and `AnthropicAiProvider` (real LLM, strict JSON-only prompt,
  WebClient timeout + exponential-backoff retry via Reactor, schema validation that rejects malformed
  output and drops any recommended action code not in the known safe-action set).
- `AiProviderFactory` picks the active one from `app.ai.provider` (mock/anthropic) - no code elsewhere
  knows which is active.
- `AiAnalysisService.analyzeIncident` never lets a provider failure propagate: it's caught, persisted as a
  FAILED `AiAnalysis` row, logged as an `AI_ANALYSIS_FAILED` incident event, and the incident simply stays
  in its current status - normal manual triage/assign/resolve still works.
- On success: incident moves to `AI_ANALYZED` (if the state machine allows it), priority is set from the
  AI's recommendation, an `IncidentAnalyzedEvent` goes to Kafka.

## Phase 6 — Safe auto-remediation ✅
- **The safety boundary**: `RemediationAction` is a fixed, admin-managed catalog (6 seeded rows -
  restart service, clear app cache, invalidate Redis cache, restart connection pool, run health check,
  retry failed workflow). `RemediationExecutor` only ever dispatches by `action.getCode()` against that
  catalog - there is no code path anywhere that accepts or runs a free-form command, from the AI or
  otherwise. `MockRemediationExecutor` simulates each action (with a ~15% simulated failure rate so
  retry/escalation paths are actually exercised); `RealRemediationExecutor` is an unimplemented stub
  documenting exactly where real infra calls plug in later.
- **Workflow**: request → `RemediationPolicyValidator` (enabled? incident in an eligible status? actor
  has the required role?) → PENDING_APPROVAL or auto-approved if low-risk → engineer approval →
  `RemediationRequestedEvent` published to Kafka → `RemediationEventListener` consumes it **asynchronously**
  (approval returns immediately over REST) → `RemediationService.runExecution` runs the action under a
  hard timeout, then a health check, then resolves the incident or retries (up to the action's
  `retryLimit`, auto-approved, no re-prompt) or escalates.
- **Idempotency**: `IdempotencyGuard` (Redis SETNX) keyed by execution ID stops a redelivered Kafka
  message from running the same attempt twice.
- **Retry/timeout/dead-letter**: `KafkaConsumerConfig` wraps the listener container with 3 retries
  (1s backoff) then routes to the `servicedesk.dlt` topic; the executor call itself is wrapped in a
  `CompletableFuture` with the action's configured timeout, independent of whatever the executor does
  internally.

## Phase 7 — Knowledge base ✅
- `KnowledgeArticle` (draft/published/archived), full CRUD + publish/unpublish/archive (ADMIN-only,
  per the role spec), search via Specifications (status/category/keyword), cached with Redis
  (`@Cacheable("knowledgeBaseArticles")`, evicted on any write).
- 8 original sample articles seeded via Flyway (`V4__knowledge_base.sql`) covering every incident
  signature the spec calls out (500/503, DB connection, Redis, auth failures, service unavailable, high
  CPU, disk space, Kafka consumer lag) - written from scratch, not copied from any vendor documentation.
- `KnowledgeRecommendationController` closes the loop on the "Knowledge Recommendation" workflow step:
  takes the latest AI analysis's tags for an incident and returns matching published articles.
- Public read-only search (`GET /api/v1/knowledge-base/public`) needs no auth, matching the "self-service
  before filing a ticket" idea; full search (all statuses) is ENGINEER/ADMIN only.

## Phase 8 — React frontend ✅ (verified — actually type-checks and builds)
Unlike the backend, this sandbox *can* reach the npm registry, so this phase was genuinely verified:
`npx tsc --noEmit` passes with zero errors and `npm run build` produces a working Vite production
bundle. That's real signal the frontend code is correct, not just "written carefully" like the backend.

- **Stack**: Vite + React 18 + TypeScript (strict mode) + Tailwind + Axios + React Router + Recharts
  (Recharts installed and ready for Phase 9's analytics charts).
- **Auth**: login/register pages, JWT stored in localStorage, axios interceptor auto-attaches the token
  and transparently refreshes on 401 (single in-flight refresh, queued correctly), route guarding via
  `ProtectedRoute` with optional role restriction.
- **Layout**: sidebar + top nav, role-filtered navigation (an EMPLOYEE never sees "Incident Queue",
  "Remediation Catalog", or the admin section; matches the role spec).
- **Core pages**: Dashboard (role-aware stats + recent incidents), Create Incident, Incident List
  (search/filter by status/category/severity + pagination), Knowledge Base browse.
- **The flagship Incident Details screen** (this got the most attention, per the spec's own emphasis):
  a `WorkflowStepper` visualizes the full pipeline (Reported → AI Diagnosis → Knowledge Recommendations →
  Suggested Remediation → Policy Validation & Approval → Execution → Health Check → Resolved/Escalated)
  computed live from incident status + latest remediation execution state, not just a static list. Beside
  it: AI diagnosis panel (confidence, root cause, recommended action, re-run button), knowledge
  recommendations, a remediation panel engineers can request/approve/reject actions from, comments
  (with internal-only notes for engineers), and the full audit timeline.
- **Remediation catalog page**: read-only view of the 6 safe actions, explicitly framed around the safety
  guarantee ("the only thing an AI recommendation or approval is ever allowed to trigger").
- **Docker**: multi-stage Dockerfile (Node build → nginx serve) with an nginx config for SPA routing.

### Honest gaps in this phase
- **Admin Analytics and User Management pages are explicit placeholders**, not faked data. They exist,
  route correctly, and are ADMIN-only - but they call out plainly that the backend doesn't have analytics
  endpoints or an admin user-management API yet (both are Phase 9/beyond work). I chose not to build UI
  against endpoints that don't exist.
- While building this I noticed the Incident Details page needed incident comments, and the backend
  didn't have a GET endpoint for them - added `IncidentService.getComments` +
  `GET /api/v1/incidents/{id}/comments` to close that gap (also respects internal-only visibility for
  employees). This is a backend addition made *during* the frontend phase, not part of the original
  Phase 3 commit - flagging so it doesn't look like it was always there.

## Phase 9 — Analytics + notifications ✅
- **Analytics**: `AnalyticsService` computes every metric the spec lists (total/open/resolved/escalated,
  by-severity, by-category, average resolution time, AI analysis success rate, remediation success/failure
  rate, auto-remediation percentage) via real repository aggregate queries - `GROUP BY` for the bucket
  breakdowns, a native Postgres query for `AVG(EXTRACT(EPOCH FROM resolved_at - created_at))` since JPQL
  has no portable timestamp-diff function, and a `date_trunc('day', ...)` native query for the
  incidents-over-time series. Everything is computed server-side, not in the frontend, per the spec.
  Cached with a 2-minute TTL (`analyticsSummary` cache) since this is a dashboard, not a live feed.
  `GET /api/v1/analytics/summary` and `/incidents-over-time` both take optional `from`/`to` and default to
  the last 30 days.
- **Notifications**: a plain DB-backed in-app notification (not email/push - no infrastructure for that
  in this project), triggered directly from the service methods that know when something happened -
  incident assigned, AI analysis completed, remediation needs approval, remediation completed/failed,
  incident escalated/resolved (both manual and auto-remediated paths). `GET /api/v1/notifications`,
  `/unread-count`, `POST /{id}/read`.
- **Frontend now consumes both for real**: the Analytics page (previously a placeholder) now renders
  actual stat cards + a line chart (incidents over time) + two bar charts (by severity, by category) via
  Recharts, all driven by the new endpoints. A notification bell in the top nav polls unread count every
  30s and shows a dropdown that marks-as-read and deep-links to the related incident. Re-verified after
  these changes: `tsc --noEmit` and `npm run build` both still pass clean.
- User Management admin page is still a placeholder - no admin user-CRUD API was in scope for this phase.

## Phase 10 — Test suite + security review ✅
- **Security review found and fixed a real IDOR gap**: `AiAnalysisController`'s history/latest endpoints
  had no ownership check, so any authenticated employee could view another employee's incident's AI
  diagnosis by guessing/knowing the incident ID (comments/timeline already had this check; AI analysis
  didn't). Fixed by adding `IncidentService.assertCanView` and calling it from both read paths, plus
  restricting the analyze-trigger endpoint to ENGINEER/ADMIN (matching the role spec - employees create
  incidents, engineers analyze them) at both the service layer and `SecurityConfig`.
- **Also fixed a real portability bug while writing tests**: `RemediationService` was calling
  `Executors.newVirtualThreadPerTaskExecutor()`, which requires Java 21+, while `pom.xml` targets Java 17
  ("Java 17+" per spec). Switched to `CompletableFuture.supplyAsync`'s no-executor overload (runs on the
  common ForkJoinPool) - correct for a short-lived, timeout-bounded task running on a Kafka consumer
  thread, and compiles on plain Java 17.
- **New unit tests** (Mockito-based, no Spring context needed - consistent with the state-machine and
  policy-validator tests from earlier phases):
  - `AuthServiceTest` - register (including duplicate-email rejection, password hashing, email
    lowercasing), login (success + bad-credentials propagation). Covers the spec's required "Login" flow.
  - `IncidentServiceTest` - create, assign (+ reject-assigning-to-an-employee), resolve
    (+ reject-resolving-a-fresh-incident via the state machine), escalate, and **unauthorized access**
    (employee viewing another employee's incident) - covers "Create incident", "Assign incident",
    "Incident resolution", "Escalation", and "Unauthorized access" from the spec's required list.
  - `AiAnalysisServiceTest` - success path, provider-failure isolation (the "AI failure must not break
    the workflow" requirement), the new employee-can't-trigger-analysis rule, and the new
    ownership-check fix. Covers "AI analysis" and reinforces "Unauthorized access".
  - `RemediationServiceTest` - request (approval-required and auto-approved paths), approve, reject,
    successful execution + resolution, failure-within-retry-limit (asserts a new attempt is created and
    dispatched), failure-exceeding-retry-limit (asserts escalation instead), and a not-yet-approved
    execution being safely skipped. Covers "Remediation recommendation", "Remediation approval",
    "Successful remediation", "Failed remediation", "Retry", and "Escalation" - the whole required list
    for that area.
  - Caught and fixed my own test-writing mistake along the way: `Incident.builder().id(...)` doesn't
    compile because `id` lives on `BaseEntity` and plain `@Builder` doesn't expose inherited fields -
    fixed by building first, then calling `.setId(...)`.
- **Not added**: full `@SpringBootTest`/`@AutoConfigureMockMvc` controller integration tests (would need
  Testcontainers spinning up real Postgres/Kafka/Redis, which needs actual test execution to get right -
  can't verify those compile or pass without a JVM+Docker environment, so I stuck to what I could reason
  about carefully: pure unit tests with real collaborators where cheap (state machine, policy validator)
  and Mockito everywhere else.

## Phase 11 — Docker end-to-end verification ⚠️ (static review only - no Docker daemon in this sandbox)
- Confirmed via `docker --version` that this sandbox has no Docker at all, so `docker compose up` could
  not actually be run here - same category of limitation as Maven Central, just for a different tool.
- Did what verification *is* possible without Docker: validated `docker-compose.yml` as real YAML,
  confirmed the backend Dockerfile's copied jar path (`it-service-desk.jar`) exactly matches `pom.xml`'s
  `<finalName>`, cross-checked every env var the compose file passes to the backend against what
  `application.yml` actually reads (no typo'd names), and confirmed `frontend/package-lock.json` is
  committed so the Docker build resolves the same verified dependency versions as Phase 8/9.
- Wrote `docs/DOCKER_VERIFICATION.md` - a step-by-step runbook for the user to actually run
  `docker compose up` and verify each service, including the full spec-required end-to-end workflow
  test (register → create incident → AI analysis → knowledge recommendations → assign → remediation
  request/approval → execution → health check → resolution/escalation → notifications → analytics).
  Also flags two specific things I could not verify and what to check if they fail (`wget` availability
  in the Alpine-based images, and the `apache/kafka` image's KRaft auto-formatting behavior).

## Phase 12 — Final integration + README ✅
- **Closed the last flagged gap**: `KnowledgeRecommendationController` now uses
  `AiAnalysisService.getLatestSuccessfulAnalysis(incidentId, requester)` (the ownership-checked overload
  from Phase 10) instead of the unchecked one - and the unchecked overload was deleted entirely rather
  than left around as a temptation for a future caller to use by mistake.
- Scanned the full codebase for leftover `TODO`/`FIXME`/placeholder markers - none found.
- Rewrote `README.md` from scratch as the final deliverable README: features, architecture (with a
  diagram and an explicit justification for the single-service-not-microservices decision), tech stack,
  service responsibilities, database design, Kafka event flow, Redis usage, AI workflow, auto-remediation
  safety model, API docs pointer, setup/env/Docker/local-dev/testing/deployment instructions, and a
  **Verification status** table right at the top - so anyone reading it sees exactly what was and wasn't
  actually run before reading a single feature claim.
- Final file count: 131 backend Java files (6 test files), 5 Flyway migrations, 31 frontend TS/TSX files.

## Closing note on verification, honestly stated

Two things in this project were genuinely verified by actually running them: the frontend's TypeScript
compiler and production build. Everything else - the Java backend's compilation, the unit tests actually
passing, and the Docker Compose stack actually starting - was written and reviewed as carefully as I
could manage, including catching and fixing two real bugs along the way (an IDOR gap and a Java-version
incompatibility), but was never executed in this environment because the tools to do so (Maven Central
access, a Docker daemon) weren't available here. The README's Verification status table and this
progress log say so plainly rather than letting a confident-sounding README imply otherwise. Run
`mvn clean verify` first thing.

## New known gaps from this round (flagging honestly rather than glossing over them)
- `KnowledgeRecommendationController` doesn't re-check that the requester is allowed to view the
  underlying incident (the ownership check that `IncidentService.enforceViewAccess` does elsewhere)
  - low risk since it only returns public KB article summaries, not incident data, but worth tightening.
  - The Anthropic provider's retry policy retries on *any* non-`AiProviderException` throwable, which
  is a bit broad (e.g. it would retry a 4xx from a malformed request body just as it retries a timeout).
  Fine for a portfolio project's demo path; a production version should distinguish 4xx from 5xx/timeout.
- Still not compiled/verified locally - same Maven Central limitation as before.
