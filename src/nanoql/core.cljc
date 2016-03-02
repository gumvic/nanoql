(ns nanoql.core
  (:refer-clojure :exclude [compile])
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    [clojure.data :refer [diff]]
    #?(:cljs [cljs.core.async :as a :refer [<!]])
    #?(:clj [clojure.core.async :as a :refer [go <!]])
    [schema.core :as s]))

;; TODO errors handling
;; TODO [?] can't pass nils (kind of, (go nil) is ok, but channels are not meant for passing nils)
;; TODO [optimization] add *ready* function which will tell the engine that the result is ready and doesn't need to be recursively processed
;; TODO [optimization] operations use *into* a lot; is it bad or good for performance?

(declare Query)

(def Prop
  {:name s/Any
   (s/optional-key :as) s/Any
   (s/optional-key :query) (s/recursive #'Query)})

(def Query
  {(s/optional-key :args) s/Any
   (s/optional-key :props) [Prop]})

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

;; TODO refactor this hell

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

(declare execute*)

(defn- execute** [props node]
  (if (empty? props)
    (go node)
    (a/map
      (fn [& kv]
        (into {} kv))
      (map
        (fn [{:keys [name as query]}]
          (go
            [(or as name)
             (<!
               (execute*
                 (get node name)
                 query))]))
        props))))

(defn- execute* [node {:keys [props] :as query}]
  (go
    (let [node* (if (fn? node)
                 (<! (node query))
                 node)
          ch* (if (vector? node*)
                (a/map
                  vector
                  (map (partial execute** props) node*))
                (execute** props node*))]
      (<! ch*))))

;; TODO schema validation
(defn execute
  "Execute a query against the node.
  A node can be a function, which means that the value can't be produced immediately (think a cloud request).
  The function will receive the current query AST and must return a channel producing the result.
  Note that you don't have to manually reduce the result to meet the props requested if you don't want to; it is done automatically."
  [node query]
  (execute* node query))

(declare Query-Def)

(def Props-Def
  {s/Any
   (s/cond-pre
     (s/pred (partial = '*))
     (s/recursive #'Query-Def))})

(def Query-Def
  (s/cond-pre
    [(s/one s/Any "args")
     (s/one Props-Def "props")]
    Props-Def))

(declare compile*)

(defn- compile** [props]
  (into
    []
    (map
      (fn [[p q]]
        (let [p* (if (vector? p)
                   {:name (first p)
                    :as (second p)}
                   {:name p})]
          (if-let [q* (not-empty (compile* q))]
            (assoc p* :query q*)
            p*))))
    props))

(defn- compile* [query]
  (cond
    (= query '*) {}
    (empty? query) {}
    (vector? query)
    {:args (first query)
     :props (compile** (second query))}
    (map? query)
    {:props (compile** query)}))

(defn compile
  "Compile a query definition to query AST.
  See Query-Def schema."
  [query]
  {:pre (s/validate Query-Def query)}
  (compile* query))