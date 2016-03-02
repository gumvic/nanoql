(defproject gumvic/nanoql "0.2.0"
  :description "A micro lib for structured data querying."
  :url "https://github.com/gumvic/nanoql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [prismatic/schema "1.0.5"]]
  :plugins [[lein-cljsbuild "1.1.2"]]
  :cljsbuild
  {:builds
   {:dev
    {:source-paths ["src"]
     :compiler {:output-to "target/main.js"
                :optimizations :none
                :source-map true
                :pretty-print true}}}})
