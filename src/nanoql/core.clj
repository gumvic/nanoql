;; TODO
;; (intersect [{} {:id nil :name nil}] [{} {:id nil}]) -> [{} {:id nil}]
;; (union [{} {:id nil}] [{} {:name nil}]) -> [{} {:id nil :name nil}]
;; generative testing for bad inputs for core and core.ql
;; ql - compile-query, compile-schema?

(ns nanoql.core
  (:require [schema.core :as s]))

(defn- prop? [x]
  (or
    (string? x)
    (keyword? x)
    (number? x)))

(def Prop
  (s/pred prop? "prop"))

(def Schema
  [(s/one (s/pred fn? "fn") "executor")
   (s/one
     (s/either
       (s/pred var?)
       {Prop (s/recursive #'Schema)})
     "props")])

(def Query
  [(s/one {Prop s/Any} "args")
   (s/one
     {Prop
      (s/either
        (s/pred nil?)
        (s/recursive #'Query))} "props")])

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

(defn query [schema conn query]
  (query-self schema conn query {}))