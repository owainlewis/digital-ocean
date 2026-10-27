# digital-ocean

[![CI](https://github.com/owainlewis/digital-ocean/actions/workflows/ci.yml/badge.svg)](https://github.com/owainlewis/digital-ocean/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/digitalocean.svg)](https://clojars.org/digitalocean)

A small Clojure client for the [DigitalOcean API v2](https://docs.digitalocean.com/reference/api/).

The library focuses on the modern control-plane API at `https://api.digitalocean.com/v2`.
DigitalOcean V1 support was removed in `2.0.0`.

It gives you:

- simple helpers for common resources: Droplets, volumes, domains, SSH keys, images, snapshots, regions, sizes, tags, projects, VPCs, databases, load balancers, firewalls, reserved IPs, certificates, CDN endpoints, Kubernetes, App Platform, the container registry, Spaces keys and uptime checks
- `wait-for-action` to block until a Droplet or volume action finishes
- a low-level `request` function for any DigitalOcean endpoint
- automatic JSON encoding and decoding
- Clojure keyword support for API fields, including `:per-page` to `per_page`
- pagination helpers for list endpoints
- proxy support through `clj-http` request options
- useful error data through `ex-info`

## Install

Version 2 is not on Clojars yet. Build it locally with `lein install` to try it.

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

Proxy options can be passed through to `clj-http`:

```clojure
(def proxied-client
  (do/client (System/getenv "DIGITALOCEAN_TOKEN")
    {:request-options {:proxy-host "127.0.0.1"
                       :proxy-port 3128}}))
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

Wait for an action to finish:

```clojure
(->> (do/power-off-droplet client 123456)
     (do/wait-for-action client))
;; => {:id 36804636 :status "completed" :type "power_off" ...}
```

## Volumes

```clojure
(do/create-volume client {:name "data"
                          :region "lon1"
                          :size-gigabytes 100})
(do/attach-volume client "volume-id" 123456 "lon1")
(do/detach-volume client "volume-id" 123456 "lon1")
```

## Networking

```clojure
(do/load-balancers client)
(do/firewalls client)
(do/create-reserved-ip client {:region "lon1"})
(do/assign-reserved-ip client "203.0.113.10" 123456)
(do/certificates client)
(do/cdn-endpoints client)
```

## Kubernetes and App Platform

```clojure
(do/kubernetes-clusters client)
(spit "kubeconfig.yaml" (do/kubeconfig client "cluster-id"))

(do/apps client)
(do/create-app-deployment client "app-id")
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
(do/databases client)
(do/vpcs client)
(do/registry client)
(do/spaces-keys client)
(do/uptime-checks client)
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

JSON bodies are parsed into maps with keyword keys. Keys keep DigitalOcean's
snake_case spelling, for example `:domain_records`. Request keys are converted
the other way, so `:per-page` is sent as `per_page`. Non-JSON responses, such
as a kubeconfig, are returned as strings.

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

## Development

```bash
lein test
clojure -M:test
lein cljfmt check
lein check
```

Tests run against a local HTTP server. They do not call DigitalOcean. See
[CONTRIBUTING.md](CONTRIBUTING.md) and [CHANGELOG.md](CHANGELOG.md).

## License

Copyright 2014-2026 Owain Lewis

Distributed under the Eclipse Public License, the same as Clojure.
