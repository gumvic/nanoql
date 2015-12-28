(ns nanoql.core.ql-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [nanoql.core.ql :as ql]))

(deftest test_compile
  (testing "empty"
    (is
      (=
        (ql/compile [])
        [{} {}])))
  (testing "empty args only"
    (is
      (=
        (ql/compile [{}])
        [{} {}])))
  (testing "args only"
    (is
      (=
        (ql/compile [{:id 123}])
        [{:id 123} {}])))
  (testing "props only"
    (is
      (=
        (ql/compile [:id :name])
        [{} {:id nil :name nil}])))
  (testing "args and props"
    (is
      (=
        (ql/compile [{:id 123} :id :name])
        [{:id 123} {:id nil :name nil}])))
  (testing "nested"
    (is
      (=
        (ql/compile [{:id 123} :id :name :friends [{:lim 5} :name]])
        [{:id 123} {:id nil :name nil :friends [{:lim 5} {:name nil}]}]))))