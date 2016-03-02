# nanoql

If you have

{:users
  {:alice {:name "Alice"}}
  {:bob {:name "Bob"}}}

And you do

{:users
  {:alice
    {:name nil}

Then you get

{:users
  {:alice
    {:name "Alice"}}}

Of course, it makes little if any sense to query static data like that, clojure has enough facilities already.
The whole deal is to have dynamic responses.
Let's get right to those!

{:always-42 42
 :always-? }

{:always-42 nil
 :always-? nil}

We supplied a function to produce something we don't know ahead.
That function is called executor.
Actually, our root object is an executor, too, which means:
1) Executor may be either a value or  function.
2) Executor can produce other executors!
EXAMPLE

Query structure.
We were using q/compile, just a helper function to get query AST from something

Query operations.
There are union, difference and intersection operations.

## Usage

FIXME

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
