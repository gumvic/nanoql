(defproject org.clojars.gumvic/nanoql "0.1.3"
  :description "A tiny layer for normalized data querying/modifying."
  :url "https://github.com/gumvic/nanoql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [prismatic/schema "1.0.4"]]
  :plugins [[lein-cljsbuild "1.1.2"]]
  :cljsbuild
  {:builds
   {:dev
    {:source-paths ["src"]
     :compiler
                   {:output-to "target/main.js"
                    :optimizations :none
                    :source-map true
                    :pretty-print true}}}})
