# Contributing

## Local checks

Install Java 11 or newer, Leiningen 2.11.2 or newer, the Clojure CLI and clj-kondo.

```bash
lein test
lein with-profile +clojure-1.12 test
clojure -M:test
lein cljfmt check
lein check
clj-kondo --lint src test --fail-level error
lein jar
```

Tests must not call DigitalOcean. Use the local http-kit server helpers in `test/digitalocean/v2/core_test.clj` and assert the method, path and body that reach the server.

## Pull requests

- Keep the public functions in `digitalocean.v2.core` backwards compatible after 2.0.0 is released.
- New resource helpers follow the existing patterns (`generic`, `request!`) and come with a path test.
- Add an entry to `CHANGELOG.md`.
- Never log or include API tokens in exceptions.

## Releases

1. Set a non-snapshot version in `project.clj` and date the changelog entry.
2. Merge the release commit.
3. Push a matching `vX.Y.Z` tag.
4. The protected `clojars` GitHub environment publishes after checking that the tag and project version match. It needs the `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` secrets.
