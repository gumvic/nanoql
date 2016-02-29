(ns nanoql.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.data :refer [diff]]))

;; TODO add Query schema
;; TODO add QueryDef schema

(defn- diff-map [a b]
  (diff
    (set (keys a))
    (set (keys b))))

(defn- props->map [props]
  (into
    {}
    (map
      (fn [{name :name as :as {args :args} :query :as q}]
        [[name as args] q]))
    props))

(declare union)

(defn union* [a b]
  (let [a-props (props->map a)
        b-props (props->map b)
        c-props (merge-with
                  (fn [{qa* :query :as a*} {qb* :query :as b*}]
                    (if-let [qc* (not-empty (union qa* qb*))]
                      (assoc a* :query qc*)
                      a*))
                  a-props
                  b-props)]
    (into [] (vals c-props))))

;; TODO exception if args are different
(defn union
  "A union of two queries.
  Note that this may produce a query with the same props.
  users('Alice') UNION users('Bob') will result in users('Alice'), users('Bob').
  Since the result of execution of props is a map, executing that query may give unexpected results ('Bob' overriding 'Alice').
  Use aliases to avoid that situation."
  [{a-props :props :as a}
   {b-props :props :as b}]
  (cond
    (empty? a-props) b
    (empty? b-props) a
    :else (assoc a :props (union* a-props b-props))))

(declare difference)

(defn- difference* [a b]
  (let [a-props (props->map a)
        b-props (props->map b)
        [_ new shared] (diff-map a-props b-props)]
    (into
      (into [] (map (partial get b-props)) new)
      (comp
        (map
          (fn [p]
            (let [{qa* :query :as a*} (get a-props p)
                  {qb* :query :as b*} (get b-props p)]
              (when-let [qc* (not-empty (difference qa* qb*))]
                (assoc a* :query qc*)))))
        (filter some?))
      shared)))

(defn difference
  "A difference of two queries.
  Given A and B, what B has that A doesn't?"
  [{a-args :args a-props :props :as a}
   {b-args :args b-props :props :as b}]
  (cond
    (empty? a-props) b
    (empty? b-props) b
    (not= a-args b-args) b
    :else
    (if-let [c-props (not-empty (difference* a-props b-props))]
      (assoc a :props c-props)
      {})))

;; TODO refactor intersection

(declare intersection)

(defn- intersection** [a b]
  (into
    []
    (comp
      (map
        (fn [{a-query :query :as a*}]
          (let [c* (first
                     (filter
                       some?
                       (map
                         (fn [{b-query :query}]
                           (when-let [c-query (not-empty
                                                (intersection a-query b-query))]
                             (assoc a* :query c-query)))
                         b)))]
            (if c*
              c*
              (when-not a-query
                a*)))))
      (filter some?))
    a))

(defn- intersection* [a b]
  (let [gfun (fn [{:keys [name as]}] [name as])
        a-props (group-by gfun a)
        b-props (group-by gfun b)
        [_ _ shared] (diff-map a-props b-props)
        c-props (into
                  []
                  (comp
                    (map
                      (fn [p]
                        (let [a* (get a-props p)
                              b* (get b-props p)]
                          (not-empty (intersection** a* b*)))))
                    (filter some?))
                  shared)]
    (into [] (flatten c-props))))

(defn intersection
  "Intersection of two queries."
  [{a-args :args a-props :props :as a}
   {b-args :args b-props :props :as b}]
  (cond
    (empty? a) b
    (empty? b) a
    (and
      (some? a-args)
      (some? b-args)
      (not= a-args b-args)) {}
    :else
    (if-let [c-props (not-empty (intersection* a-props b-props))]
      (if-let [c-args (if (some? a-args)
                        a-args
                        b-args)]
        {:args c-args :props c-props}
        {:props c-props})
      {})))

;; TODO async execute
;; TODO support schema validation

;; will select schema subset for validating query
(defn- query->schema [query schema])

(declare execute)

(defn- execute* [props node]
  (into
    {}
    (map
      (fn [{:keys [name as query]}]
        (let [p (or as name)
              exec (get node name)]
          [p (execute exec query)])))
    props))

(defn execute
  "Execute a query with the supplied executor.
  Executor may be either a value or a function.
  A function is called with the current query AST, so it may do reducing if it wants."
  [exec {:keys [props] :as query}]
  (let [node (if (fn? exec)
               (exec query)
               exec)]
    (if-not (empty? props)
      (if (vector? node)
        (into [] (map (partial execute* props)) node)
        (execute* props node))
      node)))

(declare compile)

(defn- compile* [props]
  (into
    []
    (map
      (fn [[p q]]
        (let [p* (if (vector? p)
                   {:name (first p)
                    :as (second p)}
                   {:name p})]
          (if-let [q* (not-empty (compile q))]
            (assoc p* :query q*)
            p*))))
    props))

(defn compile
  "Compile a query definition to query AST.
  Query definition:
  Query: [args props] OR props
  Args: anything
  Props: {prop query}
  Prop: name OR [name alias]
  Name: anything
  Alias: anything"
  [query]
  (cond
    (empty? query) {}
    (vector? query)
    {:args (first query)
     :props (compile* (second query))}
    (map? query)
    {:props (compile* query)}))