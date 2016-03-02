# nanoql

A micro lib for declarative data querying.

## In a nutshell

If you have

```clojure
{:users
  {:alice {:name "Alice"}}
  {:bob {:name "Bob"}}}
```

...and you do...

```clojure
{:users
  {:alice
    {:name nil}
```

...then you get...

```clojure
{:users
  {:alice
    {:name "Alice"}}}
```

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

Actually, our root object is an executor, too, which means:

1) Executor may be either a value or function.

2) Executor can contain or even dynamically produce other executors!

## Executors

As we saw, a function executor receives three parameters.

1) AST - this is the current query AST (more on that below).

2) ok - when everything went right, use this callback.

3) err - when everything went wrong, use this one.

At this point, you probably are wondering what the result of execute actually is.

Since we are using callbacks there, that means we are asynchronous, so... drumroll...

It's a channel!

The result of execute is always a channel, no matter if we were actually using any callbacks or were just querying a couple of static maps.

## More interesting example



## Query AST

Query AST has the following structure:

Query: 

```clojure
{:args, :props}
;; both are optional
```

Args:

```clojure
;; any clojure value.
```

Props: 

```clojure
[{:name, :as, :query}]
;; name is required - any clojure value
;; as is optional - any clojure value
;; query is optional - if provided must be a Query
```

We were using q/compile earlier. 

This is just a helper function to get query AST from something more readable.

## Query operations.

There are union, difference and intersection operations.

Please see their docstrings.

## Usage

So, let's sum up.

1) First, define the root executor - a function or a map holding values or executors.

2) Define a query, using compile or crafting AST by hand (or writing your own compile, why not?).

3) Perhaps perform some transformations using query operation functions.

4) (take! (execute root query) println), or something more real world-ish.

5) That's it!

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
