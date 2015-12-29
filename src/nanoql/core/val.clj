(ns nanoql.core.val
  (:require [schema.core :as s]))

(defn- prop? [x]
  (or
    (string? x)
    (keyword? x)
    (symbol? x)
    (number? x)))

(def Prop
  (s/pred prop? "prop"))

(declare Schema)

(def Schema-Reifier
  (s/pred fn?))

(def Schema-Props
  {Prop (s/recursive #'Schema)})

(def Schema
  [(s/one Schema-Reifier "reifier")
   (s/one
     (s/either
       (s/pred var?)
       Schema-Props)
     "props")])

(declare Query)

;; TODO instead of s/Any, should be something edn-able
(def Query-Args
  {Prop s/Any})

(def Query-Props
  {Prop
   (s/either
     (s/pred nil?)
     (s/recursive #'Query))})

(def Query
  [(s/one Query-Args "args")
   (s/one Query-Props "props")])