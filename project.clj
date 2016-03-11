(defproject gumvic/nanoql "0.3.3"
  :description "A lib for declarative schemaless data querying."
  :url "https://github.com/gumvic/nanoql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [prismatic/schema "1.0.5"]
                 [funcool/promesa "0.8.1"]]
  :plugins [[lein-cljsbuild "1.1.2"]]
  :cljsbuild
  {:builds
   {:dev
    {:source-paths ["src"]
     :compiler {:output-to "target/main.js"
                :optimizations :none
                :source-map true
                :pretty-print true}}}})
