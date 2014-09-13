# Digital-ocean

Clojure interface for Digital Ocean

## V1

V1 API will be depricated soon. See V1.md for information about the old V1 API.

I recommend using the V2 API going forward.

I've tried to map this API to be as RESTful and simple as possible

## V2

### Authentication

You can pass in an authentication token directly to every function.

This can be found in your digital ocean account.

### Getting started

```clojure
(ns myproject
  (:require [digitalocean.v2.core :as do]))

(defonce token "YOURAUTHTOKEN")

```

### Droplets

Get all droplets

```clojure
(do/droplets token)
```

Get a droplet by ID

```clojure
(do/get-droplet token 123)
```

Boot up a new droplet

Note that certain fields are required. See the Digital Ocean API V2 docs for all params

```clojure
(do/create-droplet token nil
  {:name "my droplet"
   :region "nyc1"
   :size "512mb"
   :image "123"
  })
```

### Domains

Get all

```clojure
(do/domains token)
```

Get one

```clojure
(do/get-domain token "fshionable.com")
```

## Images

### Keys

### Regions

### Sizes

## License

Copyright © 2014 Owain Lewis

Distributed under the Eclipse Public License, the same as Clojure.
