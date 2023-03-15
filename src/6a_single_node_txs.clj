#!/usr/bin/env bb
(load-file (clojure.string/replace *file* #"/[^/]+$" "/node.clj"))

(ns single-node-txs
  (:require [node]))

(def db (atom {})) ;; key -> value

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "txn"
      (let [resp (map (fn [[op a1 a2]]
                        (case op
                          "r" [op a1 (get @db a1)]
                          "w" (do
                                (swap! db assoc a1 a2)
                                [op a1 a2])))
                      (:txn body))]
        (node/reply! req {:type "txn_ok"
                          :txn resp})))))

(defn -main []
  (node/run handler))

(-main)
