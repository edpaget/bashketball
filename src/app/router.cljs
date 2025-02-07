(ns app.router
  (:require [reitit.core :as r]
            [reitit.frontend.easy :as rfe]
            [react]
            [uix.core :as uix :refer [defui $]]))

(def router-def
  (r/router
   [["/" :home-page]
    ["/cards" :cards-index]
    ["/cards/new" :cards-new]
    ["/cards/:id" :cards-show]]
   {:conflicts nil}))

(def router-provider (uix/create-context {}))

(defn make-router-store
  []
  (let [path (atom {})
        subscribers (atom #{})]
    (rfe/start!
     router-def
     (fn [m]
       (reset! path m)
       (doseq [subscriber @subscribers]
         (subscriber)))
     {:use-fragment false})
    {:subscribe (fn [cb] (swap! subscribers conj cb) (fn [] (swap! subscribers disj cb)))
     :snapshot (fn [] @path)}))

(defonce router-store (make-router-store))

(defui router [{:keys [router-store children]}]
  (let [router-state (react/useSyncExternalStore (:subscribe router-store)
                                                 (:snapshot router-store))]
    (prn router-state)
    ($ router-provider {:value router-state}
       children)))

(defui route [{:keys [route-name children]}]
  (let [router-state (uix/use-context router-provider)]
    (when (= route-name (get-in router-state [:data :name]))
      children)))

(defn href
  [page]
  (rfe/href page))
