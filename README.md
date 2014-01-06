# Digital-ocean

Clojure interface for Digital Ocean

## Usage

### Authentication

All methods require a client-id and api-key that can be found in your Digital Ocean Account under the API tab.

```clojure
(ns myns
  (:require [digital-ocean.core]))

(def client-id "YOUR_CLIENT_ID")
(def api-key "YOUR_KEY")

(droplets client-id api-key)
```

## Droplets

All droplets for your account

```clojure
(droplets client-id api-key)
```

Get a single droplet

```clojure
(droplet client-id api-key)
```

Create a new droplet

```clojure
(new-droplet client-id api-key {:name ""})
```

## Regions

Return all regions

```clojure
(regions client-id api-key)
```

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
