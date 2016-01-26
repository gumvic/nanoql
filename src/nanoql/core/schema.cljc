(ns nanoql.core.schema
  (:require
    [schema.core :as s]))

(def Name
  (s/cond-pre
    s/Str
    s/Symbol
    s/Keyword
    s/Num))

;; TODO should be something edn-able instead of s/Any
(def Value s/Any)

(def Res
  (s/pred fn?))

(declare Schema)

;; TODO perhaps add the third one which is a resolver which handles the sit. when relation doesn't have what query wants
(def Sub-Schema
  [(s/one Res "res")
   (s/one (s/recursive #'Schema) "schema")])

;; TODO make it better; simply checking for var? is stupid
(def Schema
  (s/cond-pre
    (s/pred var?)
    {Name Sub-Schema}))

(declare Query)

(def Args
  {Name Value})

(def Sub-Query
  [(s/one Args "args")
   (s/one (s/recursive #'Query) "query")])

(def Query
  {Name
   (s/cond-pre
     (s/pred nil?)
     Sub-Query)})