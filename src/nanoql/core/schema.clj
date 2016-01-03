(ns nanoql.core.schema
  (:require
    [schema.core :as s]))

(def Name
  (s/either
    s/Str
    s/Symbol
    s/Keyword
    s/Num))

;; TODO implement
(def Value s/Any)

(declare Schema)

(def Res
  (s/pred fn?))

;; TODO make it better; simply checking for var? is stupid
(def Rels
  (s/cond-pre
    (s/pred var?)
    {Name (s/recursive #'Schema)}))

;; TODO perhaps add the third one which is a resolver which handles the sit. when relation doesn't have what query wants
(def Schema
  [(s/one Res "res")
   (s/one Rels "rels")])

(declare Query)

(def Args
  {Name Value})

(def Props
  {Name
   (s/cond-pre
     (s/pred nil?)
     (s/recursive #'Query))})

(def Query
  [(s/one Args "args")
   (s/one Props "props")])
