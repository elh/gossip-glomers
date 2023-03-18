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
;; but only if present at snapshot time. I do some very simple best effort single
;; object ordering with last-write-wins versioning.

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
                            "w" (let [updated (write-db-snapshot db-snapshot a1 a2)]
                                  ;; On write, broadcast this node's state to all other nodes
                                  (doseq [node @node/node-ids]
                                    (when (not= node @node/node-id)
                                      (node/send! node
                                                  {:type "gossip"
                                                   :db updated})))
                                  {:db-snapshot updated
                                   :stmts (conj stmts [op a1 a2])})))
                        {:db-snapshot @db
                         :stmts []}
                        (:txn body))
            {db-snapshot :db-snapshot stmts :stmts} res]
        (merge-db (update-keys db-snapshot #(str %)) @node/node-id)
        (node/reply! req {:type "txn_ok"
                          :txn stmts}))

      "gossip"
      (merge-db (update-keys (:db body) #(name %)) (:src req)))))

(defn -main []
  (node/run handler))

(-main)
