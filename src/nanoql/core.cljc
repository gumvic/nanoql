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

;; **query->schema** must:
;; 1) reduce the schema to the query AST
;; 2) replace the schema props w/ query AST aliases
;; 3) know a recursive schema when it sees it

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

(declare exec* exec*-coll)

(defn- exec**-props [ctx {:keys [props]} node]
  (map
    (fn [{:keys [name as query]}]
      (let [p (or as name)
            node* (exec*
                    ctx
                    query
                    (get node name))]
        (if (p/promise? node*)
          (p/promise
            (fn [res rej]
              (p/branch
                node*
                (fn [node**]
                  (res [p node**]))
                rej)))
          [p node*])))
    props))

(defn- exec** [ctx query node]
  (let [props (exec**-props ctx query node)]
    (cond
      (some p/promise? props)
      (p/then
        (p/all
          (map p/promise props))
        (partial into {}))
      :else
      (into {} props))))

(defn- exec*-static [ctx {:keys [props] :as query} node]
  (cond
    (empty? props) node
    (map? node) (exec** ctx query node)
    (vector? node) (exec*-coll ctx query node)
    :else node))

(defn- exec*-deferred [ctx query node]
  (p/promise
    (fn [res rej]
      (p/branch
        node
        (fn [node*]
          (let [node** (exec*-static ctx query node*)]
            (if (p/promise? node**)
              (p/branch node** res rej)
              (res node**))))
        rej))))

(defn- exec*-dynamic [ctx query node]
  (try
    (let [node* (node ctx query)]
      (if (p/promise? node*)
        (exec*-deferred ctx query node*)
        (exec*-static ctx query node*)))
    #?(:clj
       (catch Exception e
         (p/rejected e))
       :cljs
       (catch :default e
         (p/rejected e)))))

(defn- exec*-coll [ctx query nodes]
  (let [nodes* (into
                []
                (map
                  (partial exec*-static ctx query))
                nodes)]
    (if (some p/promise? nodes*)
      (p/all
        (map p/promise nodes*))
      nodes*)))

(defn- exec*
  [ctx query node]
  (cond
    (fn? node) (exec*-dynamic ctx query node)
    :else (exec*-static ctx query node)))

(defn execute
  "Executes a query against a node or a value in an optional context.
    A node is:
    - a map of nodes and/or values
    - a vector of nodes and/or values
    - a function (ctx, AST -> node or value)
    - a function (ctx, AST -> promise with node or value)
    A value is anything else.
    Nodes get queried according to the query's structure, values get returned as they are.
    Always returns a promise."
  ([query node]
   (p/promise
     (exec* nil query node)))
  ([query node ctx]
    (p/promise
      (exec* ctx query node))))

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