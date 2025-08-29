(ns app.main-node
  (:require
   ["jsdom" :as jsdom]
   [shadow.test.node]))

;; JSDOM Setup
;; =============================================================================

(defn setup-jsdom!
  "Sets up JSDOM environment for React Testing Library.
   Should be called once before running tests."
  []
  (when (and (exists? js/global)
             (not (exists? js/document)))
    (let [dom (jsdom/JSDOM. "<!DOCTYPE html><html><body></body></html>"
                            #js {:url "http://localhost"
                                 :pretendToBeVisual true
                                 :resources "usable"})
          window (.-window dom)]
      ;; Set global properties
      (set! js/global.window window)
      (set! js/global.document (.-document window))
      (set! js/global.navigator (.-navigator window))
      (set! js/global.HTMLElement (.-HTMLElement window))
      ;; Also set on js/document for Testing Library
      (set! js/document (.-document window))
      ;; Copy window properties to global for React
      (doseq [key (.keys js/Object window)]
        (when (and (not (exists? (aget js/global key)))
                   (not= key "localStorage")
                   (not= key "sessionStorage"))
          (aset js/global key (aget window key)))))))

(defn main [& args]
  (setup-jsdom!)
  (apply shadow.test.node/main args))
