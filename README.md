# digital-ocean

A small Clojure client for the [DigitalOcean API v2](https://docs.digitalocean.com/reference/api/).

The library focuses on the modern control-plane API at `https://api.digitalocean.com/v2`.
DigitalOcean V1 support was removed in `2.0.0`.

It gives you:

- simple helpers for common resources like Droplets, domains, SSH keys, images, regions, sizes, tags, projects, and VPCs
- a low-level `request` function for any DigitalOcean endpoint
- automatic JSON encoding and decoding
- Clojure keyword support for API fields, including `:per-page` to `per_page`
- pagination helpers for list endpoints
- useful error data through `ex-info`

## Install

Leiningen:

```clojure
[digitalocean "2.0.0-SNAPSHOT"]
```

Clojure CLI:

```clojure
{:deps {digitalocean/digitalocean {:mvn/version "2.0.0-SNAPSHOT"}}}
```

## Authentication

Create a personal access token in DigitalOcean and keep it out of source control.

```clojure
(ns my-app
  (:require [digitalocean.v2.core :as do]))

(def client
  (do/client (System/getenv "DIGITALOCEAN_TOKEN")))
```

You can also pass a token string directly to every function:

```clojure
(do/droplets (System/getenv "DIGITALOCEAN_TOKEN"))
```

## Droplets

```clojure
;; List the first page.
(do/droplets client)

;; List with query params.
(do/droplets client {:page 1
                     :per-page 100
                     :tag-name "prod"})

;; Fetch every page.
(do/all-droplets client)

;; Get one Droplet.
(do/get-droplet client 123456)

;; Create a Droplet.
(do/create-droplet client
  {:name "web-01"
   :region "lon1"
   :size "s-1vcpu-1gb"
   :image "ubuntu-24-04-x64"
   :ssh-keys [12345]
   :tags ["web" "prod"]})

;; Run actions.
(do/reboot-droplet client 123456)
(do/power-off-droplet client 123456)
(do/snapshot-droplet client 123456 "before-upgrade")

;; Delete a Droplet.
(do/delete-droplet client 123456)
```

## Domains And Records

```clojure
(do/domains client)
(do/get-domain client "example.com")

(do/records client "example.com")
(do/create-record client "example.com"
  {:type "A"
   :name "@"
   :data "203.0.113.10"
   :ttl 3600})
```

## SSH Keys

```clojure
(do/ssh-keys client)
(do/create-key client
  {:name "laptop"
   :public-key "ssh-ed25519 AAAA..."})
```

## Other Helpers

```clojure
(do/account client)
(do/actions client)
(do/images client)
(do/regions client)
(do/sizes client)
(do/tags client)
(do/projects client)
(do/vpcs client)
```

## Any Endpoint

Use `request` when you want the full response envelope:

```clojure
(do/request client :get [:droplets 123456 :snapshots])
;; => {:ok? true
;;     :status 200
;;     :headers {...}
;;     :rate-limit {:limit 5000 :remaining 4999 :reset 1720000000}
;;     :body {:snapshots [...]}}
```

Use `request!` when you want the parsed body or an exception:

```clojure
(do/request! client :post [:tags]
  {:body {:name "prod"}})
```

For HTTP failures, `request!` and the convenience helpers throw `ex-info`.
The exception data includes `:status`, `:headers`, `:rate-limit`, `:body`, and `:error`.

## Pagination

DigitalOcean list endpoints use `page` and `per_page` query params and return
pagination links in `:links :pages`.

```clojure
(do/list-all client :droplets :droplets)
(do/list-all client [:domains "example.com" :records] :domain_records)
```

## Tests

```bash
lein test
clojure -M:test
lein check
```

Tests run against a local HTTP server. They do not call DigitalOcean.

## License

Copyright 2014-2026 Owain Lewis

Distributed under the Eclipse Public License, the same as Clojure.
