# FPL Discord Bot — Service Level Objectives

## Purpose

This document defines the reliability contract for the FPL Discord bot: what "working" means for each feature, how it's measured, and what happens when it's not met. It exists so that reliability is a design input from day one, not something bolted on after an outage — and so every incident has a clear objective to measure against.

## Service Overview

A Discord bot serving a public FPL (Fantasy Premier League) community with four features, shipped incrementally:

1. Deadline reminders
2. Price-change alerts
3. Mini-league stats
4. Live match tracking

Each feature has a distinct load pattern and failure mode, so each gets its own SLO rather than one blanket "bot is up" target.

## Dependencies

- **FPL API (unofficial, undocumented)** — no SLA, can rate-limit or change shape without notice. Treated as an unreliable upstream by design.
- **Discord API** — third-party, generally reliable, but rate limits apply per-bot and per-guild.
- **Postgres DB** — self-hosted, under our control.
- **Kubernetes cluster** — self-hosted/managed, under our control.

## SLOs by Feature

### 1. Deadline Reminders
- **SLI:** percentage of scheduled reminders successfully delivered to Discord before the FPL deadline.
- **SLO:** 100% delivered, zero missed reminders. No error budget.
- **Rationale:** a missed reminder has an irreversible real-world consequence (a user misses a transfer). This is the one target where "mostly working" is a failure. Idempotency matters equally — a duplicate reminder is a minor annoyance, not an SLO breach, but should still be tracked.
- **Measurement:** log every scheduled reminder and every confirmed send; alert on any scheduled reminder with no confirmed send by T-0.

### 2. Price-Change Alerts
- **SLI:** percentage of actual price changes detected and alerted within 15 minutes of occurring.
- **SLO:** 95% within 15 minutes, measured weekly.
- **Rationale:** price changes happen nightly (~1:30am UK) via polling an unreliable upstream. Some missed or late detections are acceptable; total silent failure is not.
- **Error budget:** 5% of price changes per week may be missed or late before this counts as a breach.

### 3. Mini-League Stats
- **SLI:** percentage of on-demand stat queries returning a correct response within 3 seconds.
- **SLO:** 99% within 3 seconds, measured weekly.
- **Rationale:** bursty, on-demand load around the gameweek deadline. Users expect fast responses but can tolerate an occasional slow query more than a broken reminder.
- **Error budget:** 1% of queries per week may exceed 3 seconds or fail before this counts as a breach.

### 4. Live Match Tracking
- **SLI (freshness):** percentage of live score updates delivered to Discord within 60 seconds of the real-world event.
- **SLI (availability):** percentage of match minutes during which live tracking is functioning at all.
- **SLO:** 95% of updates within 60 seconds; 99.5% of match-minutes available, measured per gameweek.
- **Rationale:** this is the highest-load, highest-visibility feature — sustained traffic across 90+ minutes, often multiple concurrent matches on Saturdays. Most likely source of a real incident and the primary on-call trigger.
- **Error budget:** roughly 27 seconds of downtime per 90-minute match window before the availability SLO is breached.

### Bot Responsiveness (cross-cutting)
- **SLI:** percentage of slash commands acknowledged within 2 seconds.
- **SLO:** 99%, measured weekly, across all commands.
- **Rationale:** baseline UX expectation independent of feature; a slow bot erodes trust in every feature above it.

## Alerting

- Deadline reminder miss → immediate page (zero error budget, so any miss is an incident).
- Live match tracking availability or freshness breach mid-match → immediate page.
- Price-change or mini-league stats error budget burn rate exceeding a fast-burn threshold (e.g., consuming 10% of the weekly budget in under an hour) → page.
- Slow burn on any error budget → daily digest, not a page.

## Incident Response

- Every page gets a postmortem, regardless of duration or user impact, while the project is still building a track record.
- Postmortem format: timeline, root cause, what the SLO/alerting did or didn't catch, action items with owners (in this case, always the bot's author) and target dates.
- Postmortems are kept in the project repo under `/postmortems/`, one file per incident, named by date.

## Review Cadence

- SLOs are reviewed and adjusted after each gameweek for the first month, then monthly once targets stabilize.
- A target that's never at risk of breach is set too loose and should be tightened; a target breached most weeks is set too tight or reveals a design problem — either is a signal to revisit, not just the number, but the underlying cause.
