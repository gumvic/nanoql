(ns nanoql.core
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.data :refer [diff]]
    [promesa.core :as p]
    [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query AST definition ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare Query)

(def Prop
  {:name s/Any
   (s/optional-key :as) s/Any
   (s/optional-key :query) (s/recursive #'Query)})

(def Query
  {(s/optional-key :args) s/Any
   (s/optional-key :props) [Prop]})

;;;;;;;;;;;;;;;;;;;;;;
;; Query operations ;;
;;;;;;;;;;;;;;;;;;;;;;

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
                    (if-let [qc* (not-empty
                                   (union qa* qb*))]
                      (assoc a* :query qc*)
                      a*))
                  a-props
                  b-props)]
    (into [] (vals c-props))))

(defn union
  "A union of two queries.
  Note that this may produce a query with the same props.
  users('Alice') UNION users('Bob') will result in users('Alice'), users('Bob').
  Since the result of execution of props is a map, executing that query may give unexpected results ('Bob' overriding 'Alice').
  Use aliases to avoid that situation."
  [{a-args :args a-props :props :as a}
   {b-args :args b-props :props :as b}]
  (cond
    (not= a-args b-args) {}
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
    (if-let [c-props (not-empty
                       (difference* a-props b-props))]
      (assoc a :props c-props)
      {})))

(declare intersection)

(defn- intersection* [a b]
  (let [c-props (for [{a-name :name a-as :as a-query :query :as a-prop} a
                      {b-name :name b-as :as b-query :query :as b-prop} b
                      :when (and
                              (= a-name b-name)
                              (= a-as b-as))]
                  (if (and
                        (nil? a-query)
                        (nil? b-query))
                    a-prop
                    (when-let [c-query (not-empty
                                         (intersection a-query b-query))]
                      (assoc a-prop :query c-query))))]
    (into
      []
      (filter some?)
      c-props)))

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

;;;;;;;;;;;;;;;;;;;;;;;
;; Schema validation ;;
;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;
;; Query execution ;;
;;;;;;;;;;;;;;;;;;;;;

(declare execute)

(defn- execute* [props node]
    (if (empty? props)
      (p/resolved node)
      (let [ps (map
                 (fn [{:keys [name as query]}]
                   (-> (execute
                         query
                         (get node name))
                       (p/then
                         (fn [node*]
                           [(or as name) node*]))))
                 props)]
        (-> (p/all ps)
            (p/then (partial into {}))))))

;; TODO schema validation
;; TODO when dynamic node throws an exception, it should be handled (now, it is not)
;; TODO [optimization] ise reduced to tell the engine to not go recursively (unreduced if forgiving, which is cool!)
;; TODO [optimization] execute creates a lot of not needed promises
(defn execute
    "Execute a query against a node.
    A node can be:
    - a value
    - a function (AST -> value)
    - a function (AST -> promise)"
    [{:keys [props] :as query} node]
    (let [node* (if (fn? node)
                  (node query)
                  node)]
      (p/promise
        (fn [res rej]
          (p/branch
            (if (p/promise? node*)
              node*
              (p/resolved node*))
            (fn [node**]
              (if (vector? node**)
                (p/branch (p/all (map (partial execute* props) node**)) res rej)
                (p/branch (execute* props node**) res rej)))
            rej)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query compilation helper ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare Query-Def)

(def Props-Def
  {s/Any
   (s/cond-pre
     (s/eq '*)
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