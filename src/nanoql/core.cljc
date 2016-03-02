(ns nanoql.core
  (:refer-clojure :exclude [compile])
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    [clojure.data :refer [diff]]
    #?(:cljs [cljs.core.async :as a :refer [<!]])
    #?(:clj [clojure.core.async :as a :refer [go <!]])
    [schema.core :as s]))

;; TODO add Query schema
;; TODO add QueryDef schema

;; TODO problem - can't pass nils to ok; is this a problem or not?

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
                          (not-empty
                            (intersection** a* b*)))))
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
    (if-let [c-props (not-empty
                       (intersection* a-props b-props))]
      (if-let [c-args (if (some? a-args)
                        a-args
                        b-args)]
        {:args c-args :props c-props}
        {:props c-props})
      {})))

;; TODO recursive schemas
#_(defn query->schema
  "Gets a schema and returns its subset according to the query structure.
  This allows you to have one schema without manually reducing it for a query or using s/optional-key."
  [{:keys [props]} schema]
  (if (or
        (= schema s/Any)
        (empty? props))
    schema
    (into
      {}
      (fn [{:keys [name as] :as q}]
        (let [p (or as name)
              s (get schema name)
              v (query->schema q s)]
          [p v]))
      props)))

(deftype Err [msg])

(defn- chan []
  (a/promise-chan))

(defn- ok-cb [ch]
  (partial a/put! ch))

(defn- err-cb [ch]
  (fn [x]
    (a/put! ch (Err. x))))

(defn ok? [x]
  (not
    (instance? Err x)))

(defn err? [x]
  (instance? Err x))

(defn err [x]
  (when (err? x)
    (.-msg x)))

(defn- one [f ch]
  (go
    (let [x (<! ch)]
      (if (err? x)
        x
        (f x)))))

(defn- many [f chs]
  (a/map
    (fn [& xs]
      (if-let [err (first
                       (filter err? xs))]
        err
        (apply f xs)))
    chs))

(declare execute*)

(defn- execute** [props node]
  (if (empty? props)
    (go node)
    (many
      (fn [& kv]
        (into {} kv))
      (map
        (fn [{:keys [name as query]}]
          (let [p (or as name)]
            (go
              (if (empty? query)
                [p (get node name)]
                (let [x (<!
                          (execute*
                            (get node name)
                            query))]
                  (if (ok? x)
                    [p x]
                    x))))))
        props))))

(defn- execute* [exec {:keys [props] :as query}]
  (go
    (let [node (if (fn? exec)
                 (<!
                   (let [ch (chan)]
                     (exec
                       query
                       (ok-cb ch)
                       (err-cb ch))
                     ch))
                 exec)
          ch* (if (vector? node)
                (many
                  vector
                  (map (partial execute** props) node))
                (execute** props node))]
      (<! ch*))))

;; TODO schema validation
(defn execute
  "Execute a query with the supplied executor.
  Executor may be either a function or a value.
  If it is a function, that means that the value can't be produced immediately (think a cloud DB request).
  The function will receive (ast, ok, err), which are:
  ast - the current query AST
  ok - callback when done
  err - callback when something went wrong
  Note that you don't have to manually reduce the result to meet the props requested if you don't want to; it is done automatically."
  [exec query]
  (execute* exec query))

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
  Props: {prop (query OR nil)}
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