# Digital-ocean

Clojure interface for Digital Ocean

## V1

See V1.md for information about the old V1 API

## V2

### Authentication

You can pass in an authentication token directly to every function. This can be found in your digital ocean account.

### Getting started

```clojure
(ns myproject
  (:require [digitalocean.v2.core :as do]))
```

### Droplets

Get all droplets

```clojure
(do/droplets "AUTH_TOKEN")
```

Get a droplet by ID

```clojure
(do/droplet "AUTH_TOKEN" 123)
```

Boot up a new droplet

Note that certain fields are required. See the Digital Ocean API V2 docs for all params

```clojure
(do/droplet-create "AUTH_TOKEN"
  {:name "my droplet"
   :region "nyc1"
   :size "512mb"
   :image "123"
  })
```

### Domains

```clojure
(do/domains "AUTH_TOKEN")
```


## License

Copyright © 2014 Owain Lewis

Distributed under the Eclipse Public License, the same as Clojure.
