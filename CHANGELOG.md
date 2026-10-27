# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [2.0.0] - Unreleased

### Added

- Client maps with token, endpoint, timeout, headers and `clj-http` request options such as proxies.
- `request` (full response envelope with rate limit data) and `request!` (body or `ex-info`).
- Pagination with `list-all` and `all-droplets`, `all-images`, `all-regions`, `all-sizes`, `all-volumes`, `all-ssh-keys`.
- Helpers for volumes and volume actions, load balancers, firewalls, reserved IPs, certificates, CDN endpoints, Kubernetes clusters, node pools and kubeconfig, App Platform apps and deployments, the container registry, Spaces keys, uptime checks, projects, databases and VPCs.
- `wait-for-action` polls an action until it completes, errors or times out.
- GitHub Actions CI on Java 11, 17 and 21 with Clojure 1.11 and 1.12, formatting, linting, packaging and dependency scanning. Tag-gated Clojars release workflow and weekly dependency freshness check.

### Changed

- Non-JSON responses (a kubeconfig, an HTML error page from a proxy) are returned as text instead of throwing a JSON parse error.
- Clojure 1.11 is the minimum version. Cheshire 6.2.0, clj-http 3.13.1.

### Removed

- The DigitalOcean API v1 client. DigitalOcean shut down API v1.
