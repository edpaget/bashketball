(ns app.card.pages-test
  (:require
   ["@testing-library/react" :as tlr]
   [app.test-utils :as test-utils]
   [app.card.pages :as pages]
   [cljs.test :as t :refer [deftest is testing]]
   [uix.core :refer [$]])
  (:require-macros
   [cljs.test :refer [deftest testing is]]))

(test-utils/setup-frontend-test-env!)
(t/use-fixtures :each test-utils/react-cleanup-fixture)

;; Test data
(def mock-cards-data
  {:cards [{:name "Fire Blast" :id "fire-blast"}
           {:name "Lightning Strike" :id "lightning-strike"}
           {:name "Healing Potion" :id "healing-potion"}]})

(def empty-cards-data
  {:cards []})
