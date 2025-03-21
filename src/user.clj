(ns user
  (:require [hashp.preload]))

(defn dev
  []
  (require '[dev])
  (in-ns 'dev))
