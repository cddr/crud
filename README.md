# crud

[![Build Status](https://img.shields.io/travis/cddr/crud/master.svg)](https://travis-ci.org/cddr/crud)
[![Documentation](http://img.shields.io/badge/documentation-latest-green.svg)](https://cddr.github.io/crud/uberdoc.html)
[![Stories in Ready](https://badge.waffle.io/cddr/crud.png?label=ready&title=Ready)](https://waffle.io/cddr/crud)

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

(defentity User
  :schema {:id s/Int
           :email s/Str
           :name s/Str
           :secret s/Str}
  :uniqueness {:id :db.unique/identity})

(defentity Tweet
  :schema {:id s/Int
           :created-at Date
           :msg s/Str}
  :links [(link :authored-by [User :id])]
  :uniqueness {:id :db.unique/identity})

(let [entities [User Tweet]
      db (crud-db {:type :datomic
                   :uri "datomic:mem://tweet-db"
                   :entities entities})]
  (run-jetty (hypercrud {:db db, :entities entities})
             {:port 3000}))
```

OK. Lets make sure we're not flagrently violating security principles
by storing the secret in plain text. The example below encrypts the
:secret attribute before persisting it to storage

```
(ns crud.twitter
  (:require [crypto.password.bcrypt :as password]
            [prismatic.schema :as s]))

(defn encrypt [attr] (fn [params]
                       (password/encrypt (attr params))))

(defentity Tweet
  :schema {:id s/Int
           :created-at Date
           :authored-by User
           :msg s/Str}
  :links [(link :authored-by [User :id])]
  :storage [:email :name (encrypt :secret)]}
```
## License

Copyright © 2014 Andy Chambers
