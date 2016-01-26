(ns nanoql.core
  (:require
    [schema.core :refer [validate]]
    [nanoql.core.schema :as s]))

(declare intersection)

(defn- common-keys [a b]
  (keys
    (select-keys a (keys b))))

(defn- int-args [a b]
  (let [keys (common-keys a b)]
    (into
      {}
      (for [key keys
            :let [a-arg (key a)
                  b-arg (key b)]
            :when (= a-arg b-arg)]
        [key a-arg]))))

(defn- int-sub-query [a b]
  (let [[a-args a-que] a
        [b-args b-que] b
        int-args (int-args a-args b-args)
        int-que (intersection a-que b-que)]
    [int-args int-que]))

(defn intersection
  "Returns the intersection of two queries."
  [a b]
  {:pre [(validate s/Query a)
         (validate s/Query b)]}
  (let [keys (common-keys a b)]
    (into {}
          (for [k keys
                :let [sub-a (k a)
                      sub-b (k b)
                      int (if (or
                                (nil? sub-a)
                                (nil? sub-b))
                            nil
                            (int-sub-query sub-a sub-b))]]
            [k int]))))

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

;; TODO add check that is sch-props is var?, it should be Rels

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

(defn- query* [sch-root conn que-root]
  (let [schema [(constantly {}) sch-root]
        query [{} que-root]]
    (query-self schema conn query {})))

(defn query
  "Executes the query against the schema using the conn.
  schema must be a nanoql.core.schema/Schema-Root
  conn may be anything your resolvers can work with
  query must be a nanoql.core.schema/Query-Root"
  [schema conn query]
  {:pre [(validate s/Schema schema)
         (validate s/Query query)]}
  (query* schema conn query))

(defn query!
  "The same as query.
  The point of this function is to make it explicit when the query has side effects.
  (NanoQL itself doesn't care about side effects.)"
  [schema conn query]
  (query schema conn query))