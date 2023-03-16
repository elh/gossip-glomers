#!/usr/bin/env bb
(load-file (clojure.string/replace *file* #"/[^/]+$" "/node.clj"))

(ns read-uncommitted-txs
  (:require [node]))

;; NOTE: solving for the time being with some unsatisfying solutions

;;;; Read Uncommitted Transactions
;; (Added this after I already decided to do something very simple in #6c.)
;;
;; I prevent dirty writes by not re-reading values after the snapshot on tx start.
;; The READ UNCOMMITTED part of this is the fact that I will read uncommitted data
;; but only if present at snapshot time. I am not preventing degenerate single
;; object orderings though...
;;
;; TODO: consider vector clocks and a bit more broadcast coordination so single
;; object orderings are more reasonable.

(def db (atom {})) ;; key -> value

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "txn"
      (let [res (reduce (fn [{:keys [db-snapshot stmts]} [op a1 a2]]
                          (case op
                            "r" {:db-snapshot db-snapshot
                                 :stmts (conj stmts [op a1 (get db-snapshot a1)])}
                            "w" (do
                                  ;; On write, broadcast this node's state to all other nodes
                                  (doseq [node @node/node-ids]
                                    (when (not= node @node/node-id)
                                      (node/send! node
                                                  {:type "gossip"
                                                   :db (assoc db-snapshot a1 a2)})))
                                  {:db-snapshot (assoc db-snapshot a1 a2)
                                   :stmts (conj stmts [op a1 a2])})))
                        {:db-snapshot @db
                         :stmts []}
                        (:txn body))
            {db-snapshot :db-snapshot stmts :stmts} res]
        (reset! db db-snapshot)
        (node/reply! req {:type "txn_ok"
                          :txn stmts}))

      "gossip"
      (reset! db (:db body)))))

(defn -main []
  (node/run handler))

(-main)
