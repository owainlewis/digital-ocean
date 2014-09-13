# Digital-ocean

Clojure interface for Digital Ocean

## NOTE

The Digital Ocean API will soon be moving to a V2 (in progress). The code here currently referes to the V1 API

## Usage

```
[digitalocean "0.1.0"]
```

### Authentication

All methods require a client-id and api-key that can be found in your Digital Ocean Account under the API tab.

```clojure
(ns myns
  (:require [digital-ocean.droplet :refer :all]))
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

Boot up a new droplet

```clojure
(new-droplet client-id api-key
  {:name "Demo"
   :size_id "66"
   :image_id "473123"
   :region_id "1"}))
```

## Regions

Return all regions

```clojure
(regions client-id api-key)
```

## License

Copyright © 2014 Owain Lewis

Distributed under the Eclipse Public License, the same as Clojure.
