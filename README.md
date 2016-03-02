# nanoql

A micro lib for declarative data querying.

## In a nutshell

If you have

```clojure
{:answers
  {:everything 42}
  {:nothing 0}}
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

So, there's nothing to "implement" at all!

Of course, it makes little if any sense to query static data like that, clojure has enough facilities already.
The whole deal is to have dynamic responses.
Let's get right to those!

## Dynamic results

```clojure
{:always-42 42
 :always-? (fn [ast ok err] (ok (rand-int 100)))}

(q/execute
  (q/compile {:always-42 nil
              :always-? nil}))

;; =>
{:always-42 42
 :always-? 57}
{:always-42 42
 :always-? 98}
;;...
```

We supplied a function to produce something dynamically.

That function is called an executor.

Actually, our root object is an executor, too.

Let's delve into that right now!

## Executors

Executors are simple.

If you have the value, the executor is that value. 

If you don't have the value, the executor is the function that will produce that value. And of course that value may itself contain function executors!

If you have a collection of values, don't hesitate to put them in the vector, and each value will be processed automatically. But remember, vectors only!

So:

1) Executor produces a value either by simply being that value or by being a function which will give that value.

2) The produced value can contain executors.

3) The produced value may be a vector of values. They will be processed separatedly.

4) Ad infinitum.

As we saw, a function executor receives three parameters.

1) AST - this is the current query AST (more on that below).

2) ok - when everything went right, use this callback.

3) err - when everything went wrong, use this one.

At this point, you probably are wondering what the result of execute actually is.

Since we are using callbacks there, that means we are **async**hronous, so... drumroll...

It's a channel!

The result of execute is always a channel, no matter if we were actually using any callbacks or were just querying a couple of static maps.

## Less boring example



## Query AST

Query AST has the following structure (minimal knowledge of plumatic/schema is required):

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

## Query operations.

There are **union**, **difference** and **intersection** operations available.

Please see their docstrings.

## Error handling



## Usage

So, let's sum up.

1) First, define the root executor - a function or a map holding values or executors.

2) Define a query, using **compile** or crafting AST by hand (or writing your own compile, why not?).

3) Perhaps perform some transformations using query operation functions.

4) (execute root query) to get the channel.

5) Wait for the x, check it with **err?** and perhaps use **err** to get the message. Otherwise, just use the x!

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
