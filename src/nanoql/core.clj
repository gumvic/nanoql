(ns nanoql.core
  (:require
    [schema.core :refer [validate]]
    [nanoql.core.schema :as s]))

(declare query-self)
(declare query-props)

(defn- query-props [sch-props que-props conn self]
  (let [prop-names (keys que-props)]
    (into
      (select-keys self prop-names)
      (for
        [name prop-names
         :when
         (and
           (not (contains? self name))
           (contains? sch-props name))
         :let
         [sch (sch-props name)
          que (que-props name)]]
        [name (query-self sch conn que self)]))))

(defn- query-self [schema conn query parent]
  (let
    [[sch-self sch-props] schema
     sch-props (if (var? sch-props) @sch-props sch-props)
     [que-args que-props] query
     args (assoc que-args :q/query query :q/parent parent)
     self (sch-self conn args)
     query-props* (partial query-props sch-props que-props conn)]
    (cond
      (map? self) (query-props* self)
      (coll? self) (vec (for [x self] (query-props* x)))
      :else self)))

(defn- query* [schema conn query]
  (query-self schema conn query {}))

;; TODO perhaps query/query! shouldn't validate for perf reasons

(defn query
  "Executes the query against the schema using the conn.
  schema must be a nanoql.core.schema/Schema
  conn can be anything your resolvers can work with
  query must be a nanoql.core.schema/Query"
  [schema conn query]
  {:pre [(validate s/Schema schema)
         (validate s/Query query)]}
  (query* schema conn query))

(defn query!
  "The same as query.
  The point of this function is to make it explicit when the query has side effects.
  (NanoQL itself doesn't care about side effects.)"
  [schema conn query]
  {:pre [(validate s/Schema schema)
         (validate s/Query query)]}
  (query* schema conn query))