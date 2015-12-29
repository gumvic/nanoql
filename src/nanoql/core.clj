(ns nanoql.core
  (:require
    [schema.core :as s]
    [nanoql.core.val :as v]))

(defn make-schema [resolve props]
  { :pre (
           (s/validate v/Schema-Reifier resolve)
           (s/validate v/Schema-Props props)) }
  [resolve props])

(defn schema-re [schema]
  (first schema))

(defn schema-props [schema]
  (second schema))

(defn make-query [args props]
  {:pre (
          (s/validate v/Query-Args args)
          (s/validate v/Query-Props props))}
  [args props])

(defn query-args [query]
  (first query))

(defn query-props [query]
  (second query))

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

(defn query
  "Executes the query against the schema in context of the conn.
  Note that this function doesn't validate its parameters! (Use nanoql.core.val)."
  [schema conn query]
  (query-self schema conn query {}))

;; nanoql doesn't care if a query has side effects or not
;; this is just a convinience function,
;; so it is clear from the usage if side effects are present
(def query! query)