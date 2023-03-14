#!/usr/bin/env bb
(load-file (clojure.string/replace *file* #"/[^/]+$" "/node.clj"))

(ns single-node-kafka
  (:require [node]))

;;;; A single node log

;; WARN: without scaling down the offsets, maelstrom result processing and graphing was OOMing. something there scales
;; with the magnitude of the offset numbers.
(def startNanos (System/nanoTime))

(def log (atom {}))              ;; key to msg vector [<offset>, <msg>]
(def commits (atom {}))          ;; key to committed offset

;; update map values to be (apply f key value)
(defn map-kv->v [m f]
  (into {} (for [[k v] m] [k (f k v)])))

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)

      "send"
      (let [offset (int (Math/floor (/ (- (System/nanoTime) startNanos) 100000)))]
        (swap! log update-in [(:key body)] (fnil conj []) [offset (:msg body)])
        (node/log (str "debug: send: log: " @log))
        (node/reply! req {:type "send_ok"
                          :offset offset}))

      "poll"
      (let [log-snap @log
            msgs (map-kv->v (select-keys log-snap (map name (keys (:offsets body)))) ;; keyword <-> string :'(
                            (fn [k v]
                              (filterv #(>= (first %) (get-in (:offsets body) [(keyword k)])) v)))]
        (node/log (str "debug: poll: msgs: " msgs))
        (node/reply! req {:type "poll_ok"
                          :msgs msgs}))

      "commit_offsets"
      (do
        (swap! commits #(merge-with (fn [v1 v2]
                                      (if (> v1 v2) v1 v2))
                                    %
                                    (:offsets body)))
        (node/log (str "debug: commit_offsets: commits: " @commits))
        (node/reply! req {:type "commit_offsets_ok"}))

      "list_committed_offsets"
      (let [commits-snap @commits
            offsets (select-keys commits-snap (map keyword (:keys body)))]
        (node/log (str "debug: list_committed_offsets: offsets: " offsets))
        (node/reply! req {:type "list_committed_offsets_ok"
                          :offsets offsets})))))


(defn -main []
  (node/run handler))

(-main)
