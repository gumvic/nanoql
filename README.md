# nanoql

A micro lib for declarative data querying.

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

## Executors

```clojure
{:always-42 42
 :always-? (fn [ast ok] (ok (rand-int 100)))}
(q/execute
  (q/compile {:always-42 nil
              :always-? nil}))
;; =>
{:always-42 nil
 :always-? nil}
```

We supplied a function to produce something dynamically.
That function is called an executor.
Actually, our root object is an executor, too, which means:
1) Executor may be either a value or function.
2) Executor can produce other executors!
EXAMPLE

Query structure.
We were using q/compile, just a helper function to get query AST from something more readable.

Query operations.
There are union, difference and intersection operations.

## Usage

FIXME

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
