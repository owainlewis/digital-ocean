# Roadmap

Maintained by the project's agents and the owner. See `AGENTS.md` for how items move.

## Now

- Release 2.0.0 to Clojars (needs the owner to tag).
- Opt-in retry on HTTP 429 that waits until `ratelimit-reset`.

## Next

- Lazy pagination: `list-seq` returning a lazy sequence of items, for very large accounts.
- Droplet helpers for backups, monitoring alerts and one-click apps.
- Functions (serverless) namespaces and triggers.
- Optional kebab-case response keys (`:domain-records`) behind a client option.

## Later

- Generate helpers from DigitalOcean's published OpenAPI spec so new resources appear without hand-written code.

## Done

- 2026-10: load balancers, firewalls, reserved IPs, certificates, CDN, Kubernetes, App Platform, registry, Spaces keys, uptime checks, volumes, `wait-for-action`, text responses, pinned CI matrix and release workflow.
- 2026-07: v2 client rewrite, proxies, databases, v1 removed.
