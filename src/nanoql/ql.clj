(ns nanoql.ql
  (:refer-clojure :exclude [compile])
  (:require
    [schema.core :refer [validate]]
    [nanoql.core.schema :as s]))

(declare compile)

(defn- compile-args [args]
  args)

(defn- compile-props [props]
  (loop
    [props* (next props)
     prop* (first props)
     props** {}]
    (if-let [prop** (first props*)]
      (if (vector? prop**)
        (recur
          (drop 2 props*)
          (second props*)
          (assoc
            props**
            prop*
            (compile prop**)))
        (recur
          (next props*)
          prop**
          (assoc props** prop* nil)))
      (if prop*
        (assoc props** prop* nil)
        props**))))

(defn- compile* [args props]
  [(compile-args args)
   (compile-props props)])

(defn compile [query]
  {:post [(partial validate s/Query)]}
  (let [[args & props] query]
    (if (map? args)
      (compile* args props)
      (compile* {} query))))