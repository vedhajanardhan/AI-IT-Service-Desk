CREATE TABLE knowledge_articles (
    id              UUID PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    content         TEXT         NOT NULL,
    summary         TEXT         NOT NULL,
    category        VARCHAR(30),
    tags            VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    author_id       UUID         NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_kb_articles_status ON knowledge_articles (status);
CREATE INDEX idx_kb_articles_category ON knowledge_articles (category);

-- Seed a system author for the sample articles so this migration doesn't
-- depend on any particular admin account existing yet.
INSERT INTO users (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'kb-system@servicedesk.local',
    -- bcrypt hash of a random, unusable password - this account is never meant to log in.
    '$2a$12$C6UzMDM.H6dfI/f/IKcEeOxvi2ITf8OcbLo6zZ9BvUq8pi5xN3sYq',
    'Knowledge Base System',
    'ADMIN',
    FALSE,
    now(),
    now()
);

INSERT INTO knowledge_articles (
    id,
    title,
    content,
    summary,
    category,
    tags,
    status,
    author_id,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    title,
    content,
    summary,
    category,
    tags,
    'PUBLISHED',
    (SELECT id FROM users WHERE email = 'kb-system@servicedesk.local'),
    now(),
    now()
FROM (
    VALUES
    (
        'Diagnosing repeated HTTP 500 and 503 responses',
        'A 500 means the service accepted the request but hit an unhandled error while processing it; a 503 means the service (or a proxy in front of it) is refusing the request entirely, usually because it is overloaded, mid-restart, or a dependency it needs is unreachable. Start by checking whether the errors are isolated to one instance or spread across the fleet - one bad instance points to local resource exhaustion or a stuck thread, while a fleet-wide spike usually points to a shared dependency (database, cache, downstream API) failing. Check recent deploys first: a 500 spike that starts right after a release is almost always the release. If nothing changed, look at thread pool and connection pool saturation next - a slow downstream call can back up an entire pool and turn into 503s under load even though the code itself is fine. A safe first remediation step is a targeted service restart, which clears stuck threads and resets pool state without needing to know the root cause yet; only escalate to code-level debugging if the problem returns shortly after.',
        'How to tell 500s from 503s and where to look first: recent deploys, then pool saturation, then a safe restart.',
        'APPLICATION_ERROR',
        'http-500-503-errors,application-errors'
    ),
    (
        'Troubleshooting database connection pool exhaustion',
        'Symptoms are usually request timeouts or explicit "connection pool exhausted" errors under normal-looking traffic. The most common cause is a slow query holding a connection far longer than expected, which starves the rest of the pool - check for any query whose average duration jumped recently, not just the slowest single query. The second most common cause is a connection leak: code that acquires a connection on one path and doesn''t release it on an exception path. If you can restart the pool safely (i.e. no in-flight transactions you cannot afford to lose), that buys immediate relief while you look at slow query logs. Increasing pool size is a valid short-term mitigation but treat it as a band-aid - if the underlying query is slow, a bigger pool just delays the same problem at higher load.',
        'Slow queries and connection leaks are the two usual suspects; a pool restart buys time to find which one.',
        'DATABASE',
        'database-connection-problems,timeouts'
    ),
    (
        'Recovering from Redis connectivity failures',
        'When the application cannot reach Redis, the safe design is for it to degrade to direct database reads rather than fail outright - if you are seeing hard failures instead of degraded performance, check whether cache-aside logic is actually wrapped in a try/catch. Connectivity failures usually come from one of three places: the Redis node itself being down or over its memory limit and evicting aggressively, a network partition between the app and cache tier, or the connection pool on the app side being exhausted the same way a database pool can be. Check Redis memory usage and eviction stats before assuming it is a network issue. If keys are known to be stale or corrupted rather than the connection being down, a targeted cache invalidation is lower-risk than a full Redis restart, since it does not affect other services sharing the same cache cluster.',
        'Check memory/eviction stats first; prefer targeted invalidation over a full restart when other services share the same cache.',
        'INFRASTRUCTURE',
        'redis-connectivity,caching'
    ),
    (
        'Investigating a spike in authentication failures',
        'A sudden spike in login or token-validation failures is rarely a coincidence across many unrelated users, so start from the assumption it is systemic rather than "many people forgot their password at once." Check whether a signing-key rotation happened recently and whether all instances picked up the new key - a partial rollout means some instances validate tokens signed by the old key and others do not, producing exactly this pattern. Clock skew between the app servers and the identity provider is another common cause, since JWT validation is time-sensitive. If the identity provider itself is degraded, failures will correlate with its status page or its own error logs. A service restart clears any cached, now-invalid credentials or keys held in memory, which resolves the majority of these cases; if failures persist immediately after a restart, the cause is external (IdP outage, expired certificate) rather than local.',
        'Rule out partial key rollout and clock skew before assuming it is an external identity provider outage.',
        'AUTHENTICATION',
        'authentication-failures,security'
    ),
    (
        'What "service unavailable" actually means and how to respond',
        'Service Unavailable almost always originates from a load balancer or reverse proxy in front of the actual application, not the application itself - it means the health check for every backend instance is currently failing, or there are simply no healthy instances registered. The first thing to check is the health-check endpoint response directly against an instance, bypassing the load balancer, to see whether the app is actually up. If the app responds fine directly but the load balancer still shows it unhealthy, look at whether the health check has a stricter timeout than normal requests, or checks a dependency (like the database) that is itself degraded. If every instance is genuinely unhealthy at once, that points back to a shared dependency failure rather than anything wrong with the instances individually.',
        'Check the health endpoint directly, bypassing the load balancer, before assuming the whole service is actually down.',
        'INFRASTRUCTURE',
        'service-unavailable,http-500-503-errors'
    ),
    (
        'Responding to sustained high CPU usage',
        'Before doing anything corrective, distinguish a genuine traffic-driven spike from an inefficient-code-driven spike, because the right response is opposite in each case - restarting during a real traffic spike will not help and may cause a thundering-herd problem as connections re-establish. Compare current request rate against historical baselines for the same time of day/week first. If request volume is normal but CPU is elevated, look for a recently introduced inefficient query, a runaway background job, or a logging change that got much noisier. If traffic really has spiked, the right response is scaling out (more instances) rather than restarting existing ones. A read-only health check is the safest first step in either case, since it costs nothing and confirms which scenario you are actually in before you act.',
        'Confirm whether it is a real traffic spike or inefficient code before choosing between scaling out and restarting.',
        'PERFORMANCE',
        'high-cpu-usage,performance'
    ),
    (
        'Handling disk space exhaustion safely',
        'Disk space exhaustion is one of the few incident types where there is no safe fully-automated remediation in this system, because clearing space usually means deleting something, and guessing wrong about what is safe to delete can cause data loss. The most common cause by far is unrotated or excessively verbose logs, followed by accumulated temp files from failed jobs that never got cleaned up. Before deleting anything, identify what is actually consuming the space (a simple recursive directory size check is usually enough) rather than assuming it is logs. Once the immediate pressure is relieved, the actual fix is usually a log rotation policy or a scheduled cleanup job, not a one-time manual deletion, since without one the same incident recurs on a predictable cycle.',
        'No safe automated fix exists here by design; identify what is actually consuming space before deleting anything.',
        'INFRASTRUCTURE',
        'disk-space-issues'
    ),
    (
        'Diagnosing growing Kafka consumer lag',
        'Consumer lag growing over time means the consumer group is processing messages slower than the producer is publishing them, and the fix depends on why. If a specific partition has disproportionate lag compared to its peers, check for a "hot key" - one partition key receiving far more traffic than others, which no amount of consumer scaling fixes on its own. If lag is roughly even across partitions, check whether a downstream call the consumer makes per message (a database write, an external API call) has gotten slower, since consumer throughput is often bounded by that per-message work rather than Kafka itself. Frequent consumer group rebalances are worth checking too - a consumer that keeps dying and rejoining the group loses processing time on every rebalance. Retrying the specific stuck workflow is a reasonable first step if a single message or small batch appears to be stuck rather than the whole group falling behind.',
        'Check for a hot partition key and slow per-message downstream calls before scaling consumers blindly.',
        'INFRASTRUCTURE',
        'kafka-consumer-lag'
    )
) AS seed(title, content, summary, category, tags);