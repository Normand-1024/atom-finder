(ns atom-finder.util-test
  (:require [clojure.test :refer :all]
            [atom-finder.util :refer :all]
            ))

(deftest count-nodes-test
  (testing "count-nodes"
    (is (= 3 (count-nodes (parse-expr "1 + 2"))))
    (is (= 6 (count-nodes (parse-expr "f(1 + 2)"))))
    ))

(deftest parse-expr-test
  (testing "parse-expr"
    (let [sanitize #(clojure.string/replace % #"\s+" " ")]
      (is (= "1 + 3" (sanitize (write-ast (parse-expr "1 + 3")))))
      (is (= "1 + 3; " (sanitize (write-ast (parse-stmt "1 + 3;")))))
      (is (= "{ 1 + 3; f(); } " (sanitize (write-ast (parse-stmt "{1 + 3; f();}")))))
    )))