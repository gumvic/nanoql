# nanoql

A Clojure(Script) nano lib for declarative data querying.

[![Clojars Project](https://img.shields.io/clojars/v/gumvic/nanoql.svg)](https://clojars.org/gumvic/nanoql)

**NOTE This library relies on core.async. Minimal understanding is required.**

Understanding of GraphQL principles will be a huge help.

## In the nutshell

If you have

```clojure
{:answers 
  {:everything 42
   :nothing 0}}
```

...and you do...

```clojure
{:answers
  {:everything *}}
```

...then you'll get...

```clojure
{:answers
  {:everything 42}}
```

## Simplest case

Let's see how we implement the previous example.

```clojure
(require '[clojure.core.async :as a :refer [go <!]])
(require '[nanoql.core :as q])

(def root 
  {:answers 
   {:everything 42
    :nothing 0}})
    
(def query
  (q/compile
    '{:answers 
      {:everything *}}))
    
(go
  (println (<! (q/execute root query))))
  
;; {:answers {:everything 42}}
``` 

So, there's nothing to "implement" at all!

Sure, it makes little if any sense to query static data like that, clojure has enough facilities already.

The whole deal is to have dynamic responses.

Let's get right to those!

## Dynamic results

```clojure
(def root 
  {:answers
    {:always-42 
     42
    :always-? 
     (fn [_] 
       (go 
         (rand-int 100)))}})
    
(def query
  (q/compile
    '{:answers
      {:always-42 *
       :always-? *}}))
    
(go
  (println (<! (q/execute root query)))
  (println (<! (q/execute root query))))
  
;; {:answers {:always-42 42, :always-? 49}}
;; {:answers {:always-42 42, :always-? 19}}
```

We supplied a function to produce a channel which in turn will produce the value. 
Of course, in this case a channel seems too much, but that will be just about right when doing almost anything beyond toy examples.  

Let's now see what a query is and how the execution actually works.

## Query structure

Remember our query from the previous example?

Let's quickly take a look at its AST.

```clojure
(pprint
  (q/compile
    '{:answers
      {:always-42 *
       :always-? *}}))
;; =>
{:props
 [{:name :answers,
   :query {:props [{:name :always-42} {:name :always-?}]}}]}
```

Basically, a query describes:

1) What props do we need.

2) What nested queries (if any) to execute in the context of those props.

It's naturally recursive.

(Think about a GraphQL query.)

## Execution

The execution is simple.

So, we have a node (called **root** in the previous example) and a query, and we want to execute that query against that node.

What happens when **q/execute** gets called?

1) It allows a node to be a function. 
In this case it supposes that it's a deferred node, and now it's time to resolve it.
It calls that function with the query AST as a single parameter. 
(That's why our RNG function had a parameter at all.)
It expects it to return a channel and waits (non blocking, of course) for a single value.
The value the channel produces is the resolved node. Now it can proceed to the step 2).

And if the node wasn't a function initially, it supposes it was already "resolved", and proceeds to the step 2) right away.

2) It will apply 1) to the node's requested properties, with the respective subqueries' AST. 

Note that if the node is a vector, it will be smart enough to walk through it doing that.

Also, don't forget that even if nothing is deferred, obviously, execution will still return a channel! 

## Less boring example

```clojure
(require '[clojure.core.async :as a :refer [go <!]])
(require '[nanoql.core :as q])

;; this is the data we want to query
(def data
  {:users {1 {:name "Alice"
              :age 22
              :friends #{2}}
           2 {:name "Bob"
              :age 25
              :friends #{1}}
           3 {:name "Bob"
              :age 27}}})

;; this function gets the user's id and returns the node for that user
;; the node is a map containing :name and :age fields, and a deferred node for :friends field
;; the point is to defer the retrieving of :friends until we need them (if we do at all)
(defn user [id]
  (let [user* (get-in data [:users id])
        friends (get user* :friends)]
    (assoc
      user*
      :friends
      (fn [_]
          (go
            (into [] (map user) friends))))))

;; this is the node which represents the users by their names
;; note that we are finally using the query AST (args)
;; by this very reason it should be a deferred node, since we can't know what name will be requested
;; this node produces a vector of nodes
(defn users [{:keys [args]}]
  (go
    (into
      []
      (comp
        (filter 
          (fn [[_ {:keys [name]}]] 
            (= name args)))
        (map 
          (fn [[id _]] 
            (user id))))
      (get data :users))))

;; and don't forget that our root is just another node
;; recognize? an already "resolved", ready to use node
(def root
  {:users users})
  
;; note how we supply the args when they matter
;; when we have [args props], compile puts the args into the AST
(def query-alice
  (q/compile
    '{:users ["Alice"
              {:name *
               :age *}]}))
               
(def query-bob
  (q/compile
    '{:users ["Bob"
              {:name *
               :age *}]}))
               
(def query-alice-friends
  (q/compile
    '{:users ["Alice"
              {:friends 
                {:name *}}]}))
              
(def query-alice-friends-friends
  (q/compile
    '{:users ["Alice"
              {:friends 
                {:friends 
                  {:name *}}}]}))
    
;; finally, aliases
;; when we have [name alias], compile puts the alias into the AST
(def query-alice-and-bob
  (q/compile
    '{[:users "Alice"] ["Alice"
                        {:name *
                         :age *}]
      [:users "Bob(s)"] ["Bob"
                      {:name *
                       :age *}]}))
    
(go
  (println "Alice: " (<! (q/execute root query-alice)))
  (println "Bob(s): " (<! (q/execute root query-bob)))
  (println "Alice's friends: " (<! (q/execute root query-alice-friends)))
  (println "Alice's friends' friends: " (<! (q/execute root query-alice-friends-friends)))
  (println "Alice and Bob: " (<! (q/execute root query-alice-and-bob))))
  
;; Alice:  {:users [{:name Alice, :age 22}]}
;; Bob(s):  {:users [{:name Bob, :age 25} {:name Bob, :age 27}]}
;; Alice's friends:  {:users [{:friends [{:name Bob}]}]}
;; Alice's friends' friends:  {:users [{:friends [{:friends [{:name Alice}]}]}]}
;; Alice and Bob(s):  {Alice [{:name Alice, :age 22}], Bob(s) [{:name Bob, :age 25} {:name Bob, :age 27}]}
```

## Query AST, detailed

In the previous example we made use of the query AST.

Let's take a look at one of our queries again:

```clojure
(pprint
  (q/compile
      '{:users ["Alice"
                {:name *
                 :age *}]}))
;; =>
{:props
 [{:name :users,
   :query {:args "Alice", :props [{:name :name} {:name :age}]}}]}
```

So, query AST has the following structure (minimal knowledge of **plumatic/schema** is required):

```clojure
(def Prop
  {:name s/Any
   (s/optional-key :as) s/Any
   (s/optional-key :query) (s/recursive #'Query)})

(def Query
  {(s/optional-key :args) s/Any
   (s/optional-key :props) [Prop]})
```

We were using **q/compile** earlier to get the AST from something less unreadable (see **Query-Def** schema).
 
It is important to understand that **q/compile** is just a convenience function. 
Core functions work with the AST and the AST only, they don't care what we compiled to get that AST.

## Query operations

There are **union**, **difference** and **intersection** operations available.

Please see their docstrings.

## Error handling

At this moment, this is not a thing, unfortunately.

Yet to come.

## Usage

Let's sum up.

1) First, define the root node. 
Most probably it is going to be a map holding deferred nodes for your high level "methods".

2) Define a query, either using **q/compile** or crafting AST by hand (or writing your own compile, why not?).

3) Perhaps perform some transformations using query operation functions.

4) **(q/execute root query)** to get the channel.

5) The channel will hopefully produce whatever you were waiting for so much.

## License

Distributed under the Eclipse Public License, the same as Clojure.
