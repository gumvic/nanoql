(ns nanoql.ql
  (:refer-clojure :exclude [compile])
  (:require
    [schema.core :as s]
    [nanoql.core.schema :as nq.s]))

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
  {:post [(s/validate nq.s/Query query)]}
  (let [[args & props] query]
    (if (map? args)
      (compile* args props)
      (compile* {} query))))