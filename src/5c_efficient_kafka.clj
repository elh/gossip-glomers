#!/usr/bin/env bb
(load-file (clojure.string/replace *file* #"/[^/]+$" "/node.clj"))

(ns efficient-kafka
  (:require [node]))

;;;; A (more) efficient, multi-node log
;;
;; * Funnel sends to a single key leader a la Chord to prevent cas conflicts. Ensure own writes are serial
;;   per key.
;; * Funnel commits to a single commit leader. Ensure own writes are serial.
;; * note: Kafka workload does not have partitions so there is no requirement for leader election.
;; * note: unscalably writing entire log for a key. Should segment.
(def db "lin-kv")

;; If you are the commit leader:
;; * Ensure we serialize our own writes to prevent cas conflicts
;; * Keep a local store of commits to prevent uneeded round trip to see our own writes
(def commit-mtx (Object.))
(def commits (atom {}))

(def send-mtxs (atom {})) ;; keys -> monitor

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

(defn merge-commits [current new-commits]
  (merge-with (fn [v1 v2]
                (if (> v1 v2) v1 v2))
              current
              new-commits))

(defn update-commits! [current new-commits]
  (deref
   (node/rpc! db
              {:type "cas"
               :key "commits"
               :from current
               :to (merge-commits current new-commits)
               :create_if_not_exists true})))

;; Forward a message to another node to handle
(defn forward! [dest body]
  (deref
   (node/rpc! dest body)))

;; helper: update map values to be (apply f key value)
(defn map-kv->v [m f]
  (into {} (for [[k v] m] [k (f k v)])))

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      ;; Forward sends to key leader
      "send"
      (let [key-leader (nth @node/node-ids (mod (hash (:key body)) (count @node/node-ids)))]
        ;; ensure we are not clobbering the existing value being used as lock monitor
        (swap! send-mtxs (fn [current]
                           (if (get current (:key body))
                             current
                             (assoc current (:key body) (Object.)))))
        (if (= @node/node-id key-leader)
          (loop []
            (locking (get @send-mtxs (:key body))
              (let [cur (read-log (:key body))
                    offset (if (empty? cur) 1 (inc (first (last cur))))
                    update-resp (update-log! (:key body) cur [offset (:msg body)])]
                (if (= (:type update-resp) "error")
                  (do
                    (node/log (str "debug: send: cas error. retrying"))
                    (recur))
                  (node/reply! req {:type "send_ok"
                                    :offset offset})))))
          (node/reply! req (forward! key-leader body))))

      "poll"
      (let [msg-seqs (map #(read-log %) (keys (:offsets body)))
            msgs (zipmap (keys (:offsets body)) msg-seqs)
            filtered-msgs (map-kv->v (select-keys msgs (keys (:offsets body)))
                                     (fn [k v]
                                       (filterv #(>= (first %) (get-in (:offsets body) [(keyword k)])) v)))]
        (node/reply! req {:type "poll_ok"
                          :msgs filtered-msgs}))

      ;; Forward all commits to the commit leader
      "commit_offsets"
      (let [commit-leader (first @node/node-ids)]
        (if (= @node/node-id commit-leader)
          (loop []
            (locking commit-mtx
              (let [update-resp (update-commits! @commits (:offsets body))]
                (if (= (:type update-resp) "error")
                  (do
                    (node/log (str "debug: commit_offsets: cas error. retrying"))
                    (recur))
                  (do
                    (node/reply! req {:type "commit_offsets_ok"})
                    (reset! commits (merge-commits @commits (:offsets body))))))))
          (node/reply! req (forward! commit-leader body))))

      "list_committed_offsets"
      (let [offsets (select-keys (read-commits) (map keyword (:keys body)))]
        (node/reply! req {:type "list_committed_offsets_ok"
                          :offsets offsets})))))

(defn -main []
  (node/run handler))

(-main)