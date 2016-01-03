;; TODO generative testing for bad inputs (not improperly formatted, but logically bad)

(ns nanoql.core-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [nanoql.core :refer [query]]))

(def db
  {:users
   [{:id 0 :name "Bob" :friends #{1}}
    {:id 1 :name "Alice" :friends #{0}}
    {:id 2 :name "Roger" :friends #{}}]})

(def conn db)

(defn fetch-user [data {id :id}]
  (dissoc
    (get-in data [:users id])
    :friends))

(defn fetch-user-friends [data {{id :id} :q/parent}]
  (for [id (get-in data [:users id :friends])]
    (fetch-user data {:id id})))

(def user
  {:friends [fetch-user-friends #'user]})

(def schema
  [(constantly {})
   {:user [fetch-user user]}])

(deftest test-query
  (testing "empty"
    (is
      (=
        (query
          schema
          conn
          [{} {}])
        {})))
  (testing "with no props"
    (is
      (=
        (query
          schema
          conn
          [{}
           {:user [{:id 1}
                   {}]}])
        {:user {}})))
  (testing "with props"
    (is
      (=
        (query
          schema
          conn
          [{}
           {:user [{:id 1}
                   {:id nil
                    :name nil}]}])
        {:user {:id 1 :name "Alice"}})))
  (testing "nested with no props"
    (is
      (=
        (query
          schema
          conn
          [{}
           {:user [{:id 1}
                   {:id nil
                    :name nil
                    :friends nil}]}])
        {:user
         {:id 1
          :name "Alice"
          :friends [{}]}})))
  (testing "nested with props"
    (is
      (=
        (query
          schema
          conn
          [{}
           {:user [{:id 1}
                   {:id nil
                    :name nil
                    :friends [{} {:name nil}]}]}])
        {:user
         {:id 1
          :name "Alice"
          :friends [{:name "Bob"}]}})))
  (testing "super-nested with props"
    (is
      (=
        (query
          schema
          conn
          [{}
           {:user [{:id 1}
                   {:id nil
                    :name nil
                    :friends [{}
                              {:friends [{} {:name nil}]}]}]}])
        {:user
         {:id 1
          :name "Alice"
          :friends [{:friends
                     [{:name "Alice"}]}]}}))))

(deftest test-malformed-inputs
  (testing "bad schema"
    (is
      (thrown?
        Exception
        (query
          nil
          conn
          [{} {}]))))
  (testing "bad schema"
    (is
      (thrown?
        Exception
        (query
          {}
          conn
          [{} {}]))))
  (testing "bad query"
    (is
      (thrown?
        Exception
        (query
          schema
          conn
          nil))))
  (testing "bad query"
    (is
      (thrown?
        Exception
        (query
          schema
          conn
          {})))))