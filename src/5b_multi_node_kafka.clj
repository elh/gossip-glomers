#!/usr/bin/env bb
(load-file (clojure.string/replace *file* #"/[^/]+$" "/node.clj"))

(ns multi-node-kafka
  (:require [node]))

;;;; A multi-node log
;; Leveraging maelstrom's linearizable key value service.
;;
;; In this example, the are leightweight abstractions over the key value store. We are
;; very bandwidth inefficiently writing the entire log for a key in this naive impl.
(def db "lin-kv")

;;; RPC functions
;; as synchronous functions.

(defn read-log [key]
  (let [resp (deref
              (node/rpc! db
                         {:type "read"
                          :key (str "log:" (name key))}))]
    (if (get-in resp [:value])
      (get-in resp [:value])
      [])))

(defn update-log! [key current new-value]
  (deref
   (node/rpc! db
              {:type "cas"
               :key (str "log:" (name key))
               :from current
               :to (conj current new-value)
               :create_if_not_exists true})))

(defn read-commits []
  (let [resp (deref
              (node/rpc! db
                         {:type "read"
                          :key "commits"}))]
    (if (get-in resp [:value])
      (get-in resp [:value])
      {})))

(defn update-commits! [current new-commits]
  (deref
   (node/rpc! db
              {:type "cas"
               :key "commits"
               :from current
               :to (merge-with (fn [v1 v2]
                                 (if (> v1 v2) v1 v2))
                               current
                               new-commits)
               :create_if_not_exists true})))

;; helper: update map values to be (apply f key value)
(defn map-kv->v [m f]
  (into {} (for [[k v] m] [k (f k v)])))

(defn- handler [req]
  (future ;; first time the handler is async so we can await downstream rpcs. push to node?
    (let [body (:body req)]
      (case (:type body)
        "send"
        (loop []
          (let [cur (read-log (:key body))
                offset (if (empty? cur) 1 (inc (first (last cur))))
                update-resp (update-log! (:key body) cur [offset (:msg body)])]
            (if (= (:type update-resp) "error")
              (do
                (node/log (str "debug: send: cas error. retrying"))
                (recur))
              (node/reply! req {:type "send_ok"
                                :offset offset}))))

        "poll"
        (let [msg-seqs (map #(read-log %) (keys (:offsets body)))
              msgs (zipmap (keys (:offsets body)) msg-seqs)
              filtered-msgs (map-kv->v (select-keys msgs (keys (:offsets body)))
                                       (fn [k v]
                                         (filterv #(>= (first %) (get-in (:offsets body) [(keyword k)])) v)))]
          (node/reply! req {:type "poll_ok"
                            :msgs filtered-msgs}))

        "commit_offsets"
        (loop []
          (let [update-resp (update-commits! (read-commits) (:offsets body))]
            (if (= (:type update-resp) "error")
              (do
                (node/log (str "debug: commit_offsets: cas error. retrying"))
                (recur))
              (node/reply! req {:type "commit_offsets_ok"}))))

        "list_committed_offsets"
        (let [offsets (select-keys (read-commits) (map keyword (:keys body)))]
          (node/reply! req {:type "list_committed_offsets_ok"
                            :offsets offsets}))))))

(defn -main []
  (node/run handler))

(-main)
