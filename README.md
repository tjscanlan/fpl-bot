# FPL Discord Bot

A Discord bot for Fantasy Premier League (FPL) communities — deadline reminders, price-change alerts, mini-league stats, and live match score tracking.

This is also a personal reliability-engineering project. Alongside the features, the goal is to run a small production service the way a real SRE team would: defined SLOs, real monitoring, real alerting, and real postmortems when something breaks.

## Features

- **Deadline reminders** — never miss an FPL transfer deadline.
- **Price-change alerts** — get notified when player prices rise or fall.
- **Mini-league stats** — rivalry tracking and stats for your private leagues.
- **Live match tracking** — live score updates during gameweeks.

## Why This Exists

I built this to learn site reliability engineering by doing it on a real, public-facing service rather than a tutorial project. Every feature here is designed around a specific load pattern and failure mode, and each has a defined Service Level Objective (SLO) tracked in [`docs/fpl-bot-slo.md`](./docs/fpl-bot-slo.md). When something breaks, the incident gets written up in [`postmortems/`](./postmortems/) — timeline, root cause, and action items, same as a production incident at a company would.

The FPL API this bot depends on is unofficial and undocumented, with no SLA. That's intentional: it's a realistic unreliable upstream dependency, and handling it properly (backoff, retries, circuit breaking) is part of the point.

## Tech Stack

- **Language:** Java
- **Discord integration:** [JDA](https://github.com/discord-jda/JDA)
- **Database:** Postgres (Flyway for migrations)
- **Scheduling:** cron-based jobs for reminders and polling
- **Metrics:** Micrometer → Prometheus → Grafana
- **Infrastructure:** Kubernetes on AWS, provisioned with Terraform
- **CI/CD:** GitHub Actions

## Architecture

```
Discord ⇄ JDA Bot ⇄ Postgres (league/user data)
              │
              ├── Scheduler (reminders, price polling)
              ├── FPL API client (isolated, with backoff/retry)
              └── Metrics (Micrometer) → Prometheus → Grafana / Alertmanager
```

Deployed on Kubernetes (AWS), with Prometheus and Grafana running alongside the bot for real-time SLI tracking and alerting.

## Reliability

- **SLOs:** defined per feature in [`docs/fpl-bot-slo.md`](./docs/fpl-bot-slo.md), covering delivery guarantees, freshness, latency, and availability.
- **Alerting:** Alertmanager rules tied directly to SLO error budgets — fast-burn alerts page immediately, slow-burn issues surface in a daily digest.
- **Postmortems:** every page gets a written postmortem in [`postmortems/`](./postmortems/), regardless of severity, while the project builds a track record.
- **Chaos testing:** periodic deliberate failure injection (killing pods mid-match, simulating upstream API outages) to validate that monitoring and alerting actually catch what they're supposed to.

## Project Status

This project is being built incrementally, feature by feature, with each shipped to real users before the next one starts. See [`docs/execution-plan.md`](./docs/execution-plan.md) for the full build sequence.

## Local Development

```bash
# clone and configure
git clone <repo-url>
cd fpl-bot
cp .env.example .env   # fill in Discord bot token, DB credentials

# run locally with dependencies
docker-compose up
```

## Deployment

Deployed to AWS via Terraform (cluster, networking, RDS) and Kubernetes manifests (bot, Prometheus, Grafana). See the deployment runbook in `docs/` for full steps.

## License

MIT