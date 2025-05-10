(ns app.core-e2e-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [etaoin.api :as e]
   [e2e.fixtures :as fx]))

;; Use fixtures for all tests in this namespace
(use-fixtures :once fx/container-fixture fx/server-fixture)
(use-fixtures :each fx/webdriver-fixture)

(deftest ^:e2e homepage-loads-test
  (testing "Example.com loads"
    (e/go fx/*driver* "http://host.testcontainers.internal:9000/")
    (e/wait-visible fx/*driver* {:tag :h1})
    (is (e/has-text? fx/*driver* "Blood Basket"))))
