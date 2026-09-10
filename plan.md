# FPL Discord Bot — Execution Plan

This plan is structured as a sequence of commits, grouped into phases. Each commit is scoped to be small enough to review on its own and should leave the project in a working state. Intended to be handed to Claude Code (or executed manually) one commit at a time, in order.

Reference docs: `docs/fpl-bot-slo.md` (SLOs), `postmortems/` (created as incidents occur).

---

## Phase 0 — Repo & Tooling Setup

**Goal:** a buildable, empty skeleton with the folder structure already agreed on.

1. `chore: initialize Gradle/Maven project structure` — set up build tool (Gradle recommended for JDA projects), Java version, base folder structure (`src/main/java/com/fplbot/...` as previously scaffolded).
2. `chore: add JDA and core dependencies` — JDA, SLF4J + Logback, dotenv/config loading library.
3. `chore: add .gitignore, README skeleton, LICENSE`
4. `docs: add SLO doc` — move the existing SLO doc into `docs/fpl-bot-slo.md`.
5. `chore: add EditorConfig and code style config` (Checkstyle or Spotless) — worth having before multiple features exist, not after.

---

## Phase 1 — Bot Skeleton & Observability Foundation

**Goal:** a bot that connects to Discord, responds to one command, and exposes metrics — before any real feature exists. Instrumentation-first is the point of this project.

6. `feat: bootstrap JDA client and connect to Discord` — minimal `Main` class, reads bot token from env/config, logs in successfully.
7. `feat: add /ping command` — first slash command, proves the command-handling pipeline works end to end.
8. `feat: add Micrometer + Prometheus registry` — wire up `/metrics` endpoint (small embedded HTTP server, e.g., via Javalin or plain `com.sun.net.httpserver`).
9. `feat: instrument /ping with latency metric` — first real metric tied to the "bot responsiveness" SLO.
10. `chore: add structured logging` — consistent log format now, since retrofitting later is painful once features multiply.
11. `test: add unit tests for command dispatch`

---

## Phase 2 — Deadline Reminders (first real feature)

**Goal:** ship the zero-error-budget feature first, since it's simplest and has the clearest pass/fail definition.

12. `feat: add Postgres connection and migration tooling (Flyway)`
13. `feat: add League/User schema migration`
14. `feat: add FPL API client for deadline data` — isolated in `fplapi/`, with a basic retry policy from the start (this dependency has no SLA).
15. `feat: add scheduler for deadline reminder job`
16. `feat: implement deadline reminder delivery + idempotency check` — must not double-send.
17. `feat: instrument reminder delivery success/failure metric` — this is the SLI backing the zero-error-budget SLO.
18. `feat: add alert rule config for missed reminders` (Prometheus alert rule, deployed in Phase 6 but defined here alongside the feature it protects).
19. `test: add tests for idempotency and missed-reminder detection`
20. `docs: log first real-world run in postmortems/ if anything breaks`

---

## Phase 3 — Price-Change Alerts

**Goal:** introduce sustained polling of an unreliable upstream and real backoff/retry logic.

21. `feat: add nightly price-check polling job`
22. `feat: implement exponential backoff + circuit breaker for FPL API client`
23. `feat: detect and store price changes`
24. `feat: send price-change alerts to subscribed channels`
25. `feat: instrument detection latency metric` — backs the "95% within 15 minutes" SLO.
26. `test: add tests for backoff behavior under simulated API failure`

---

## Phase 4 — Mini-League Stats

**Goal:** introduce real query load and caching.

27. `feat: add mini-league schema and data model`
28. `feat: add /league-stats command`
29. `feat: add caching layer for repeated stat queries` (e.g., Caffeine cache)
30. `feat: instrument query latency metric` — backs the "99% under 3 seconds" SLO.
31. `test: add load test script for burst queries around deadline time`

---

## Phase 5 — Live Match Tracking

**Goal:** the highest-stakes feature — sustained load, the primary on-call trigger.

32. `feat: add live match polling job (per-match, concurrent-safe)`
33. `feat: implement live score delta detection`
34. `feat: push live score updates to Discord channels`
35. `feat: instrument update freshness metric` — backs the "95% within 60 seconds" SLO.
36. `feat: instrument availability metric (match-minutes covered)` — backs the "99.5% match-minutes" SLO.
37. `feat: add alert rules for freshness/availability breaches mid-match`
38. `test: add load test simulating multiple concurrent matches`

---

## Phase 6 — AWS Deployment

**Goal:** move from local/dev to a real AWS deployment with monitoring wired end to end.

Two viable paths — pick based on budget vs. resume value:
- **Path A (cheaper): single EC2 instance running k3s** — lightweight Kubernetes, ~$10–15/mo, still gives you real K8s manifests and operational practice.
- **Path B (more resume-standard): EKS** — a real managed Kubernetes control plane, more expensive (~$75+/mo just for the control plane plus nodes), but the more commonly-asked-about platform in interviews.

Plan below is written path-agnostic where possible; Kubernetes manifests are the same either way, only the cluster provisioning differs.

39. `chore: containerize bot with Dockerfile` — multi-stage build, slim JRE base image.
40. `chore: add docker-compose for local dev (bot + Postgres + Prometheus + Grafana)`
41. `chore: write Terraform for AWS networking (VPC, subnets, security groups)`
42. `chore: write Terraform for cluster provisioning` — either EC2 + k3s install script, or EKS module, per chosen path.
43. `chore: write Terraform for RDS Postgres instance` — move off local Postgres to managed RDS.
44. `chore: write K8s Deployment + Service manifests for the bot`
45. `chore: write K8s ConfigMap/Secret manifests` — bot token, DB credentials (via AWS Secrets Manager integration if going the more rigorous route).
46. `chore: deploy Prometheus + Grafana to the cluster` (kube-prometheus-stack Helm chart is the standard choice).
47. `chore: import Grafana dashboards for each SLO` — one panel per SLI defined in the SLO doc.
48. `chore: configure Alertmanager routing` — page (e.g., via a free-tier PagerDuty or even a webhook to a personal phone) for the zero-budget and mid-match alerts specifically.
49. `chore: point DNS/ingress if a web-facing component is ever added` (likely unnecessary for a pure Discord bot, but note explicitly if skipped and why).
50. `docs: write deployment runbook` — how to deploy, rollback, and check cluster health manually.

---

## Phase 7 — CI/CD

**Goal:** deployments become repeatable and reviewable, not manual.

51. `chore: add GitHub Actions workflow for build + test on PR`
52. `chore: add GitHub Actions workflow to build and push Docker image on merge to main`
53. `chore: add GitHub Actions workflow to apply Terraform changes on approval` (manual approval gate for infra changes is a good practice to highlight in interviews).
54. `chore: add deployment workflow to apply new K8s manifests`

---

## Phase 8 — Reliability Validation

**Goal:** prove the SLOs actually hold under real and simulated conditions, and generate the artifacts that matter most for interviews.

55. `test: run first live gameweek end to end, monitor dashboards live`
56. `docs: write first real postmortem` (if/when something breaks — and something will)
57. `feat: chaos experiment — kill the bot pod mid-match, measure recovery time` — document in `postmortems/` even though it's self-inflicted; the process is the point.
58. `feat: chaos experiment — simulate FPL API outage, verify circuit breaker and alerting behave correctly`
59. `docs: review and adjust SLOs after first full gameweek cycle` — per the review cadence in the SLO doc.

---

## Notes for whoever executes this plan

- Ship after every phase, not just at the end — real users on Phase 2 give you real signal before Phase 5 is even built.
- Every alert defined in a feature phase (18, 25, 37) should have its actual Prometheus rule written and deployed in Phase 6, not left as a TODO — cross-reference back when doing infra work.
- Resist the urge to build Phase 6 (AWS/K8s) before Phase 1–2 are done locally with docker-compose. Get the bot working and instrumented first; infra should serve a working service, not the other way around.