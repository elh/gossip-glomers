#!/usr/bin/env bb
(load-file (clojure.string/replace *file* #"/[^/]+$" "/node.clj"))

(ns multi-node-kafka
  (:require [node :refer [then]]))

;;;; A multi-node log
;; leveraging maelstrom's linearizable key value service (and very coarse-grained data transfer)
(def db "lin-kv")

;;; RPC functions
;; These all return futures which can be blocked for using deref/@
;; In this example, the are leightweight abstractions over the key value store. We are
;; very bandwidth inefficiently writing the entire log

(defn read-log [key]
  (-> (node/rpc! db
                 {:type "read"
                  :key (str "log:" (name key))})
      (then [body]
            (if (get-in body [:value])
              (get-in body [:value])
              []))))

(defn update-log! [key current new-value]
  (node/rpc! db
             {:type "cas"
              :key (str "log:" (name key))
              :from current
              :to (conj current new-value)
              :create_if_not_exists true}))

(defn read-commits []
  (-> (node/rpc! db
                 {:type "read"
                  :key "commits"})
      (then [body]
            (if (get-in body [:value])
              (get-in body [:value])
              {}))))

(defn update-commits! [current new-commits]
  (node/rpc! db
             {:type "cas"
              :key "commits"
              :from current
              :to (merge-with (fn [v1 v2]
                                (if (> v1 v2) v1 v2))
                              current
                              new-commits)
              :create_if_not_exists true}))

;; helper: update map values to be (apply f key value)
(defn map-kv->v [m f]
  (into {} (for [[k v] m] [k (f k v)])))

(defn- handler [req]
  (future ;; first time the handler is async so we can await downstream rpcs
    (let [body (:body req)]
      (case (:type body)
        "send"
        (loop []
          (let [cur (deref (read-log (:key body)))
                offset (if (empty? cur) 1 (inc (first (last cur))))
                update-resp (deref (update-log! (:key body) cur [offset (:msg body)]))]
            (if (= (:type update-resp) "error")
              (do
                (node/log (str "debug: cas error. retrying"))
                (recur))
              (node/reply! req {:type "send_ok"
                                :offset offset}))))

        "poll"
        (let [msg-seqs (map #(deref (read-log %)) (keys (:offsets body)))
              msgs (zipmap (keys (:offsets body)) msg-seqs)
              filtered-msgs (map-kv->v (select-keys msgs (keys (:offsets body)))
                                       (fn [k v]
                                         (filterv #(>= (first %) (get-in (:offsets body) [(keyword k)])) v)))]
          (node/reply! req {:type "poll_ok"
                            :msgs filtered-msgs}))

        "commit_offsets"
        (let [commits (deref (read-commits))]
          (deref (update-commits! commits (:offsets body)))
          (node/reply! req {:type "commit_offsets_ok"}))

        "list_committed_offsets"
        (let [commits (deref (read-commits))
              offsets (select-keys commits (map keyword (:keys body)))]
          (node/reply! req {:type "list_committed_offsets_ok"
                            :offsets offsets}))))))


(defn -main []
  (node/run handler))

(-main)
