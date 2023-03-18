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
;; With no strict, real time, single object consistency requirement, we *can* update state pretty carelessly. I do a simple
;; best-effort attempt to give monotonic reads on assuming sticky client-server connections. I implement this with a
;; last-writer-wins policy and merge knowledge of the db upon txs and gossip of other node's state.
;;
;; TODO: consider a more aggresive READ COMMITTED model. use vector clocks to order remote updates?
;; TODO: do not replicate entire "db" state

(def db (atom {})) ;; key -> {:value :version}
(def db-mtx (Object.))

(defn merge-db [new from-node]
  (locking db-mtx
    (let [old @db
          merged (merge-with (fn [old-v new-v]
                               (if (or
                                    (> (:version new-v) (:version old-v))
                                    (and
                                     (= (:version new-v) (:version old-v))
                                     (< (compare from-node @node/node-id) 0)))
                                 new-v
                                 old-v))
                             old new)]
      ;; (node/log (str "merge-db: my node + from-node // " @node/node-id " + " from-node))
      ;; (node/log (str "merge-db: old db: " old))
      ;; (node/log (str "merge-db: new db: " new))
      ;; (node/log (str "merge-db: merged db: " merged))
      (reset! db merged))))

(defn write-db-snapshot [db-snapshot key value]
  (if (contains? db-snapshot key)
    (-> db-snapshot
        (assoc-in [key :value] value)
        (update-in [key :version] inc))
    (assoc db-snapshot key {:value value :version 1})))

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "txn"
      (let [res (reduce (fn [{:keys [db-snapshot stmts]} [op a1 a2]]
                          (case op
                            "r" {:db-snapshot db-snapshot
                                 :stmts (conj stmts [op a1 (get-in db-snapshot [a1 :value])])}
                            "w" {:db-snapshot (write-db-snapshot db-snapshot a1 a2)
                                 :stmts (conj stmts [op a1 a2])}))
                        {:db-snapshot @db
                         :stmts []}
                        (:txn body))
            {db-snapshot :db-snapshot stmts :stmts} res]
        (merge-db (update-keys db-snapshot #(str %)) @node/node-id)
        ;; On commit, broadcast this node's updated state to all other nodes
        (let [db-snapshot @db] (doseq [node @node/node-ids]
                                 (when (not= node @node/node-id)
                                   (node/send! node
                                               {:type "gossip"
                                                :db db-snapshot}))))
        (node/reply! req {:type "txn_ok"
                          :txn stmts}))

      "gossip"
      (merge-db (update-keys (:db body) #(name %)) (:src req)))))

(defn -main []
  (node/run handler))

(-main)
