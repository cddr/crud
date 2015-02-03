(ns crud.db
  "Provides storage facilities for creating and updating entities
in a database")

(defprotocol CrudDB
  "Protocol for representing the capabilities of a basic CRUD database"
  (commit! [db entity value]
    "Attempt to commit `value` to permanent storage")
  (retract! [db entity value]
    "Attempt to retract `value`")
  (present
    [db entity value]
    [db entity]
    "Generate a resource for `value`")    
  (find-by [db params]
    "Search `db` for entity matching the specified `params`")
  (find-by-id [db id]
    "Search `db` for entity that can be uniquely identified by `id`")
  (has-attr? [db id]))

(defmulti crud-db "Return a CrudDB using the specified `db-spec`" :type)

