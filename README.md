# nanoql

A Clojure(Script) nano lib for declarative data querying.

[![Clojars Project](https://img.shields.io/clojars/v/gumvic/nanoql.svg)](https://clojars.org/gumvic/nanoql)

**NOTE This library relies on core.async. Minimal understanding is required.**

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

Of course, it makes little if any sense to query static data like that, clojure has enough facilities already.

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
       (go (rand-int 100)))}})
    
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
Let's call that function an executor.
Actually, let's call our root map an executor, too.

## Executors

Executors are simple.

Executor produces a value according to a query. 

It can do so either by being that value already, or by being a function which returns a channel producing that value.

Of course, executors can be nested (we'll get to that soon).

If you have a collection of values, don't hesitate to put them in the vector, and each value will be processed automatically. 
But remember, vectors only!

So:

1) Executor produces a value either by simply being that value or by being a function which will give a channel with that value.

2) The produced value **is not** considered an executor, but can **contain** executors.

3) The produced value can be a vector of values. They will be recursively processed separately (think of **core.async/map**).

## Less boring example

TODO

TODO aliases

## Query AST

Query AST has the following structure (minimal knowledge of **plumatic/schema** is required):

```clojure
(def Prop
  {:name s/Any
   (s/optional-key :as) s/Any
   (s/optional-key :query) (s/recursive #'Query)})

(def Query
  {(s/optional-key :args) s/Any
   (s/optional-key :props) [Prop]})
```

We were using **q/compile** earlier to get the AST from something more readable (please see **Query-Def** schema).
 
It is important to understand that **q/compile** is just a convenience function. Core functions work with the AST and the AST only.

## Query operations.

There are **union**, **difference** and **intersection** operations available.

Please see their docstrings.

## Error handling

At this moment, this is not a thing, unfortunately.

More to come.

## Usage

So, let's sum up.

1) First, define the root executor - a function or a value holding values or other executors.

2) Define a query, either using **q/compile** or crafting AST by hand (or writing your own compile, why not?).

3) Perhaps perform some transformations using query operation functions.

4) **(q/execute root query)** to get the channel.

5) The channel will hopefully produce whatever you were waiting for.

## License

Distributed under the Eclipse Public License, the same as Clojure.
