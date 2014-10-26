# crud

[![Build Status](https://img.shields.io/travis/cddr/crud/master.svg)](https://travis-ci.org/cddr/crud)
[![Documentation](http://img.shields.io/badge/documentation-latest-green.svg)](https://cddr.github.io/crud/uberdoc.html)

As a developer, I want to be able to describe a resource's high-level details
like data data integrity, associations, documentation etc. and have a system
that builds a corresponding API and user interface so that I can focus on tooling
for higher value activities like integration with other systems or data analysis.

## Basic Example

The following program implements a "tweet" API with the following features

 * GET/POST/PATCH/DELETE "tweets" with :created_at, :authored_by and :msg attributes
 * For POST and PATCH, ensure that :msg is <144 characters 
 * GET a list of tweets matching the specified parameters
 * GET "/api/doc" for swagger documentation for the Tweet and User resources
 * When persisting the User resource, :secret is filtered through the bcrypt algorithm

```
(ns crud.twitter
  (:require [crud :refer :all]
            [prismatic.schema :as s]))

(defresource User
  {:schema {:email s/Str
            :name s/Str
            :secret s/Str}})

(defresource Tweet
  :schema {:created-at Date
           :authored-by User
           :msg s/Str})

(defroutes tweetd
  (context "/user"
    (api-routes User))

  (context "/tweet"
    (api-routes Tweet)
    
  (context "/api/doc"
    (api-docs [User Tweet]))))
```

OK. Lets make sure we're not flagrently violating security principles by storing the
secret in plain text. The example below encrypts the :secret attribute before persisting it
to storage

```
(ns crud.twitter
  (:require [crypto.password.bcrypt :as password]
            [prismatic.schema :as s]))

(defn encrypt [attr] (fn [params]
                       (password/encrypt (attr params))))

(defresource Tweet
  :schema {:created-at Date
           :authored-by User
           :msg s/Str}
  :storage [:email :name (encrypt :secret)]}
```

Next step is to add some validation. Tweets must be 144 characters right? So we should
enforce that in our API

```
(ns crud.twitter
  (:require [crypto.password.bcrypt :as password]
            [prismatic.schema :as s]))

(defn tweetable? [msg] (<= (count msg) 144))

(defresource Tweet
  :schema {:created-at Date
           :authored-by User
           :msg s/Str}
  :validations {:msg (s/pred tweetable?)})
```

## General Thoughts

The goal here is to make simple things easy without getting in the way
of when the need to do complex stuff arises. To that end, we provide a
helper macro to define what your resources look like, and functions to
generate Ring handlers that provide the basic API features. It's easy
to wrap the ring handlers and storage primitives to provide application
specific functionality. For example, this example demonstrates ommitting
the :secret attribute from the response to a GET request.

```
(defn remove-attrs [handler spec]
  (fn [req]
    (assoc (handler req)
      :body (apply dissoc (:body response) ((:method request) spec)))))

(defroutes tweetd
  (context "/user"
    (-> (api-routes User)
        (remove-attrs {:get [:secret]}))))
```

A common theme in the system's I've worked on as a developer is integration
with other systems. Clients of a CRUD system I think fall into a few
general camps (I'd love to hear if there's more you know of).

## License

Copyright © 2014 Andy Chambers
