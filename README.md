# nanoql

A Clojure(Script) lib for declarative schemaless data querying.

[![Clojars Project](https://img.shields.io/clojars/v/gumvic/nanoql.svg)](https://clojars.org/gumvic/nanoql)

## Prerequisites

You may want to gain some understanding of GraphQL to get into this library easier.

## In the nutshell

If you have...

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

## Static nodes

Let's see how we implement the previous example.

```clojure
(require '[nanoql.core :as q])

(def root 
  {:answers 
   {:everything 42
    :nothing 0}})
    
(def query
  (q/compile
    '{:answers 
      {:everything *}}))
    
(println 
  (q/execute query root))

;; =>
{:answers {:everything 42}}
``` 

So, there's nothing to "implement" at all!

Sure, it makes little if any sense to query static data like that, clojure has enough facilities already.

Let's get to something more interesting.

## Dynamic nodes

```clojure
(require '[nanoql.core :as q])

(def root 
  {:answers
    {:always-42 
     42
    :always-? 
     (fn [_ _] 
       (rand-int 100))}})
    
(def query
  (q/compile
    '{:answers
      {:always-42 *
       :always-? *}}))
    
(println 
  (q/execute query root))
  
;; =>
{:answers {:always-42 42, :always-? 91}}
```

Here, one of the nodes is a function producing the required value.

It also takes a couple of arguments, the execution context and the current query AST (more on that below).

## Deferred nodes

Of course, juggling maps in memory is cool, but we also want to query remote services, IO and such things, right?

This is where deferred nodes come into play.

A deferred node is just a dynamic node returning a promise. That's it.

```clojure
(require '[promesa.core :as p])
(require '[nanoql.core :as q])

(def root 
  {:answers
    {:always-42 
     42
    :always-? 
     (fn [_ _] 
       (p/promise
         (fn [res rej]
           (future
             (Thread/sleep 5000)
             (res (rand-int 100))))))}})
    
(def query
  (q/compile
    '{:answers
      {:always-42 *
       :always-? *}}))
    
(-> (q/execute query root)
  (p/then println))
  
;; => in 5 seconds
{:answers {:always-42 42, :always-? 69}}
```

(Note that those are not clojure style promises, they are closer to **Promises/A+**.)

## Less boring example

Ok, let's now take a look at this one.

```clojure
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
;; the node is a map containing :name and :age fields, and a dynamic node for :friends field
;; note that :friends will produce not just a value, but a vector of values
;; **q/execute** is smart enough to walk vectors 
;; (vectors only, not just any **sequential?**!)
(defn user [id]
  (let [user* (get-in data [:users id])
        friends (get user* :friends)]
    (assoc
      user*
      :friends
      (fn [_ _]
          (into [] (map user) friends)))))

;; this is a dynamic node which returns the users by their names
;; we are finally using the query AST (args)
;; again this is a vector, which will be handled properly by **q/execute**
(defn users [_ {:keys [args]}]
  (into
    []
    (comp
      (filter 
        (fn [[_ {:keys [name]}]] 
          (= name args)))
      (map 
        (fn [[id _]] 
          (user id))))
    (get data :users)))

;; and don't forget that our root is just another node
;; recognize? a static one
(def root
  {:users users})
  
;; note how we supply the args when they matter
;; when we have [args props], **q/compile** puts the args into the AST
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
;; when we have [name alias], **q/compile** puts the alias into the AST
(def query-alice-and-bob
  (q/compile
    '{[:users "Alice"] ["Alice"
                        {:name *
                         :age *}]
      [:users "Bob(s)"] ["Bob"
                         {:name *
                          :age *}]}))
    
(pprint
 [(q/execute query-alice root)
  (q/execute query-bob root)
  (q/execute query-alice-friends root)
  (q/execute query-alice-friends-friends root)
  (q/execute query-alice-and-bob root)])
  
;; =>
[{:users [{:name "Alice", :age 22}]}
 {:users [{:name "Bob", :age 25} {:name "Bob", :age 27}]}
 {:users [{:friends [{:name "Bob"}]}]}
 {:users [{:friends [{:friends [{:name "Alice"}]}]}]}
 {"Alice" [{:name "Alice", :age 22}],
  "Bob(s)" [{:name "Bob", :age 25} {:name "Bob", :age 27}]}]
```

To fully understand what's going on here, we have to delve into query AST.

## Query AST

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

Basically, a query describes:

1) What props do we need.

2) What nested queries (if any) to execute in the context of those props.

It's naturally recursive. (Think a GraphQL query.)

Query AST has the following structure (minimal knowledge of **plumatic/schema** is required):

```clojure
(declare Query)

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
Core functions work with the AST, they don't care what we compiled to get that AST.

## Execution context

We have a problem in our example - a global var, **data**. 
What if we want to query two such **data**s?

Let's make some adjustments to our Facebook killer.

```clojure
(require '[nanoql.core :as q])

;; note that we are now passing our "data" in the arguments
(defn user [data id]
  (let [user* (get-in data [:users id])
        friends (get user* :friends)]
    (assoc
      user*
      :friends
      (fn [data _]
          (into [] (map (partial user data)) friends)))))

;; "data" in the arguments, no global dependencies
(defn users [data {:keys [args]}]
  (into
    []
    (comp
      (filter 
        (fn [[_ {:keys [name]}]] 
          (= name args)))
      (map 
        (fn [[id _]] 
          (user data id))))
    (get data :users)))

(def root
  {:users users})
  
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
 
;; **q/execute** has an optional **ctx** parameter
(let [data-a {:users {1 {:name "Alice"
                         :age 22}
                      2 {:name "Bob"
                         :age 27}}}
      data-b {:users {1 {:name "Bob"
                         :age 25}
                      3 {:name "Bob"
                         :age 27}}}]

  (pprint
   [(q/execute query-alice root data-a)
    (q/execute query-bob root data-a)
    (q/execute query-alice root data-b)
    (q/execute query-bob root data-b)]))
    
;; =>
[{:users [{:name "Alice", :age 22}]}
 {:users [{:name "Bob", :age 27}]}
 {:users []}
 {:users [{:name "Bob", :age 25} {:name "Bob", :age 27}]}]
```

The benefits are obvious - we're not relying on a global var anymore.
Instead, we explicitly pass the context we want to execute a query in.

In a more real world example we could be passing a connection to a db or something like that.

## Query operations

There are **union**, **difference** and **intersection** operations available.

Please see their docstrings.

## Error handling

When a dynamic node throws an exception, a rejected promise is returned.

## Usage

Let's sum up.

1) First, define the root node. 
Most probably it is going to be a map holding dynamic nodes for your high level "methods".

2) Define a query, either using **q/compile** or crafting AST by hand (or writing your own compile, why not?).

3) Perhaps perform some transformations using query operation functions.

4) **(q/execute query root)** to get the promise.

5) The promise will hopefully produce whatever you were waiting for so much.

## License

Distributed under the Eclipse Public License, the same as Clojure.
