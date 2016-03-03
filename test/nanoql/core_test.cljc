(ns nanoql.core-test
  (:refer-clojure :exclude [compile])
  (:require
    #?(:cljs [cljs.test :refer :all])
    #?(:clj [clojure.test :refer :all])
    [promesa.core :as p]
    [nanoql.core :as q]))

;; TODO cljs tests for execute

(deftest union
  (testing "union of empty queries is empty query"
    (is
      (=
        (q/union {} {})
        {})))
  (testing "query with prop A (alias A*) and query with prop A (alias A**); union is A (alias A*), A (alias A**)"
    (is
      (=
        (q/union
          {:props [{:name :users
                    :as :friends}]}
          {:props [{:name :users
                    :as :foes}]})
        {:props [{:name :users
                  :as :friends}
                 {:name :users
                  :as :foes}]})))
  (testing "query with prop A (alias A*) and query with prop A (alias A*); union is A (alias A*)"
    (is
      (=
        (q/union
          {:props [{:name :users
                    :as :friends}]}
          {:props [{:name :users
                    :as :friends}]})
        {:props [{:name :users
                  :as :friends}]})))
  (testing "query with prop A (with query) and query with prop A (without query); union is A (with query)"
    (is
      (=
        (q/union
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]}
          {:props [{:name :users}]})
        {:props [{:name :users
                  :query {:args "Alice"
                          :props [{:name :name}]}}
                 {:name :users}]})))
  (testing "query with prop A (with query A*) and query with prop A (with query A**); union is A (with query A*), A (with query A**)"
    (is
      (=
        (q/union
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]}
          {:props [{:name :users
                    :query {:args "Bob"
                            :props [{:name :name}
                                    {:name :age}]}}]})
        {:props [{:name :users
                  :query {:args "Alice"
                          :props [{:name :name}]}}
                 {:name :users
                  :query {:args "Bob"
                          :props [{:name :name}
                                  {:name :age}]}}]})))
  (testing "query with prop A (with query A*) and query with prop A (with query A*); union is A (with query A*)"
    (is
      (=
        (q/union
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]}
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}
                                    {:name :age}]}}]})
        {:props [{:name :users
                  :query {:args "Alice"
                          :props [{:name :name}
                                  {:name :age}]}}]})))
  (testing "just a big nested example"
    (is
      (=
        (q/union
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :friends
                                     :query {:args 4
                                             :props [{:name :name}]}}]}}]}
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :friends
                                     :query {:args 5
                                             :props [{:name :name}]}}]}}]})
        {:props [{:name :users
                  :query {:args "Alice"
                          :props [{:name :friends
                                   :query {:args 4
                                           :props [{:name :name}]}}
                                  {:name :friends
                                   :query {:args 5
                                           :props [{:name :name}]}}]}}]}))))

(deftest difference
  (testing "empty queries are no different"
    (is
      (=
        (q/difference {} {})
        {})))
  (testing "query with prop A and query with prop B; difference is B"
    (is
      (=
        (q/difference
          {:props [{:name :users}]}
          {:props [{:name :viewer}]})
        {:props [{:name :viewer}]})))
  (testing "query with props A, B and query with prop B; difference is B"
    (is
      (=
        (q/difference
          {:props [{:name :viewer}
                   {:name :users}]}
          {:props [{:name :users}]})
        {})))
  (testing "query with prop A and query with props B, A; difference is B"
    (is
      (=
        (q/difference
          {:props [{:name :users}]}
          {:props [{:name :viewer}
                   {:name :users}]})
        {:props [{:name :viewer}]})))
  (testing "query with prop A (with query) and query with prop A (without query); difference is A (without query)"
    (is
      (=
        (q/difference
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]}
          {:props [{:name :users}]})
        {:props [{:name :users}]})))
  (testing "queries with the same nested props are no different"
    (is
      (=
        (q/difference
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]}
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]})
        {})))
  (testing "query with nested props and query with the same structure but some of the props missing are no different"
    (is
      (=
        (q/difference
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}
                                    {:name :age}]}}]}
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]})
        {})))
  (testing "query with nested props and query with the same structure but more props; difference is those new props"
    (is
      (=
        (q/difference
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]}
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}
                                    {:name :age}]}}]})
        {:props [{:name :users
                  :query {:args "Alice"
                          :props [{:name :age}]}}]})))
  (testing "query with nested props and query with nested props of different structure; difference is the whole branch"
    (is
      (=
        (q/difference
          {:props [{:name :users
                    :query {:args "Alice"
                            :props [{:name :name}]}}]}
          {:props [{:name :users
                    :query {:args "Bob"
                            :props [{:name :name}]}}]})
        {:props [{:name :users
                  :query {:args "Bob"
                          :props [{:name :name}]}}]}))))

(deftest intersection
  (testing "empty queries don't intersect"
    (is
      (=
        (q/intersection {} {})
        {})))
  (testing "query with props and empty qiery; intersection is those props"
    (is
      (=
        (q/intersection
          {:props [{:name :name}]}
          {})
        {:props [{:name :name}]})))
  (testing "query with props A, B and query with prop A; intersection is A"
    (is
      (=
        (q/intersection
          {:props [{:name :name}
                   {:name :age}]}
          {:props [{:name :name}]})
        {:props [{:name :name}]})))
  (testing "query with args and query without args; intersection is query with args"
    (is
      (=
        (q/intersection
          {:props [{:name :name}
                   {:name :friends
                    :query {:props [{:name :name}]}}]}
          {:props [{:name :name}
                   {:name :friends
                    :query {:args 4
                            :props [{:name :name}]}}]})
        {:props [{:name :name}
                 {:name :friends
                  :query {:args 4
                          :props [{:name :name}]}}]})))
  (testing "query without args and query with args; intersection is query with args"
    (is
      (=
        (q/intersection
          {:props [{:name :name}
                   {:name :friends
                    :query {:args 4
                            :props [{:name :name}]}}]}
          {:props [{:name :name}
                   {:name :friends
                    :query {:props [{:name :name}]}}]})
        {:props [{:name :name}
                 {:name :friends
                  :query {:args 4
                          :props [{:name :name}]}}]})))
  (testing "query with args A and query with args A; intersection is query with args A"
    (is
      (=
        (q/intersection
          {:props [{:name :name}
                   {:name :friends
                    :query {:args 5
                            :props [{:name :name}]}}]}
          {:props [{:name :age}
                   {:name :friends
                    :query {:args 5
                            :props [{:name :name}]}}]})
        {:props [{:name :friends
                  :query {:args 5
                          :props [{:name :name}]}}]})))
  (testing "query with args A and query with args B don't intersect"
    (is
      (=
        (q/intersection
          {:props [{:name :name}
                   {:name :friends
                    :query {:args 4
                            :props [{:name :name}]}}]}
          {:props [{:name :age}
                   {:name :friends
                    :query {:args 5
                            :props [{:name :name}]}}]})
        {}))))

(def data
  {:users {1 {:id 1
              :name "Alice"
              :age 22
              :friends [2]
              :avatar {:small "Alice-small.jpg"
                       :big "Alice-big.jpg"}}
           2 {:id 2
              :name "Bob"
              :age 25
              :friends [1]
              :avatar {:small "Bob-small.jpg"
                       :big "Bob-big.jpg"}}
           3 {:id 3
              :name "Roger"
              :age 27
              :avatar {:small "Roger-small.jpg"
                       :big "Roger-big.jpg"}}}
   :viewer 3})

(declare user)

#_(defn- friends [id]
  (fn [_]
    (go
      (into [] (map user) (get-in data [:users id :friends])))))

#_(defn- user [id]
  (assoc
    (get-in data [:users id])
    :friends
    (friends id)))

#_(defn- users [{:keys [args]}]
  (go
    (into
      []
      (comp
        (filter (fn [[_ {:keys [name]}]] (or (nil? args) (= name args))))
        (map (fn [[id _]] (user id))))
      (get data :users))))

#_(defn- viewer [_]
  (go
    (user (get data :viewer))))

(def root
  {:users ()
   :viewer ()
   :nil nil
   :defer (fn [_] (p/resolved 42))})

(deftest execute
  (testing "prop"
    (let [query (q/compile
                  '{:nil *})
          res {:nil nil}]
      #?(:cljs ()
         :clj (is
                (=
                  @(q/execute query root) res)))))
  (testing "deferred prop"
    (let [query (q/compile
                  '{:defer *})
          res {:defer 42}]
      #?(:cljs ()
         :clj (is
                (=
                  @(q/execute query root) res))))))

#_(deftest execute
  (testing "props"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name :users
                          :query {:props [{:name :name}]}}]}))
            {:users [{:name "Alice"}
                     {:name "Bob"}
                     {:name "Roger"}]})))))
  (testing "more props"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name :viewer
                          :query {:props [{:name :name}
                                          {:name :age}]}}]}))
            {:viewer {:name "Roger"
                      :age 27}})))))
  (testing "props with args"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name :users
                          :query {:args "Alice"
                                  :props [{:name :name}]}}]}))
            {:users [{:name "Alice"}]})))))
  (testing "aliases"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name :users
                          :as "Alice"
                          :query {:args "Alice"
                                  :props [{:name :name}]}}
                         {:name :users
                          :as "Bob"
                          :query {:args "Bob"
                                  :props [{:name :name}
                                          {:name :age}]}}]}))
            {"Alice" [{:name "Alice"}]
             "Bob" [{:name "Bob"
                     :age 25}]})))))
  (testing "calling function executor"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name :users
                          :query {:args "Alice"
                                  :props [{:name :name}
                                          {:name :friends
                                           :query {:props [{:name :name}]}}]}}]}))
            {:users [{:name "Alice"
                      :friends [{:name "Bob"}]}]})))))
  (testing "calling function executor, recursive"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name :users
                          :query {:args "Alice"
                                  :props [{:name :name}
                                          {:name :friends
                                           :query {:props [{:name :friends
                                                            :query {:props [{:name :name}]}}]}}]}}]}))
            {:users [{:name "Alice"
                      :friends [{:friends [{:name "Alice"}]}]}]})))))
  (testing "nil"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name :nil}]}))
            {:nil nil})))))
  (testing "42"
    (test-async
      (go
        (is
          (=
            (<!
              (q/execute
                root
                {:props [{:name 42}]}))
            {42 42}))))))

(deftest compile
  (testing "empty"
    (is
      (=
        (q/compile {})
        {})))
  (testing "prop"
    (is
      (=
        (q/compile '{:viewer *})
        {:props [{:name :viewer}]})))
  (testing "props"
    (is
      (=
        (q/compile '[{:server 42}
                    {:viewer *}])
        {:args {:server 42}
         :props [{:name :viewer}]})))
  (testing "nested props, no args"
    (is
      (=
        (q/compile '{:viewer {:name *
                             :age *}})
        {:props [{:name :viewer
                  :query {:props [{:name :name}
                                  {:name :age}]}}]})))
  (testing "args"
    (is
      (=
        (q/compile '{:user ["Alice"
                           {:name *
                            :friends {:name *}}]})
        {:props [{:name :user
                  :query {:args "Alice"
                          :props [{:name :name}
                                  {:name :friends
                                   :query {:props [{:name :name}]}}]}}]})))
  (testing "nested props with args"
    (is
      (=
        (q/compile '{:user ["Alice"
                           {:name *
                            :friends [{:first 5}
                                      {:name *}]}]})
        {:props [{:name :user
                  :query {:args "Alice"
                          :props [{:name :name}
                                  {:name :friends
                                   :query {:args {:first 5}
                                           :props [{:name :name}]}}]}}]})))
  (testing "aliases"
    (is
      (=
        (q/compile '{[:users :friends] [{:friend true}
                                       {:name *}]
                    [:users :foes] [{:friend false}
                                    {:home-address *}]})
        {:props [{:name :users
                  :as :friends
                  :query {:args {:friend true}
                          :props [{:name :name}]}}
                 {:name :users
                  :as :foes
                  :query {:args {:friend false}
                          :props [{:name :home-address}]}}]}))))