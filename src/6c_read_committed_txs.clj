#!/usr/bin/env bb
(load-file (clojure.string/replace *file* #"/[^/]+$" "/node.clj"))

(ns read-committed-txs
  (:require [node]))

;; NOTE: solving for the time being with some unsatisfying solutions

;;;; Read Committed Transactions
;; I satisfy this requirement in the style of REPEATABLE READ by taking a snapshot at the beginning of the transaction.
;; This does not really follow the spirit of the isolation level by disallowing the non-repeatable reads of newly committed
;; data to appear in the middle of a transaction. This increases latency of seeing newly committed data.
;;
;; With no strict, real time, single object consistency requirement, we *can* update state pretty carelessly. I am inclined
;; to believe that this does not violate the letter of READ COMMITTED isolation because I am doing many concurrent updates here
;; and maelstrom is satisfied. It is certainly pretty loose though...
;;
;; TODO: consider a more aggresive READ COMMITTED model. use vector clocks to order remote updates.
;; TODO: implement the stricter models which i find more interesting. not totally available though.
;; TODO: do not replicate entire "db" state

(def db (atom {})) ;; key -> value

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "txn"
      (let [res (reduce (fn [{:keys [db-snaphot stmts]} [op a1 a2]]
                          (case op
                            "r" {:db-snaphot db-snaphot
                                 :stmts (conj stmts [op a1 (get db-snaphot a1)])}
                            "w" {:db-snaphot (assoc db-snaphot a1 a2)
                                 :stmts (conj stmts [op a1 a2])}))
                        {:db-snaphot @db
                         :stmts []}
                        (:txn body))
            {db-snapshot :db-snaphot stmts :stmts} res]
        (reset! db db-snapshot)
        ;; On commit, broadcast this nodes state to all other nodes
        (doseq [node @node/node-ids]
          (when (not= node @node/node-id)
            (node/send! node
                        {:type "gossip"
                         :db db-snapshot})))
        (node/reply! req {:type "txn_ok"
                          :txn stmts}))

      "gossip"
      (reset! db (:db body)))))

(defn -main []
  (node/run handler))

(-main)
