#!/usr/bin/env bb
(ns single-node-kafka)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

;;;; A single node log

;; WARN: there is a lot of gross and finicky conversions between keywords and strings/numbers due to how Clojure
;; converts JSON. Be aware of `name` and `keyword` calls.

;; WARN: without scaling down the offsets, maelstrom result processing and graphing was OOMing. something there scales
;; with the magnitude of the offset numbers.
(def startMillis (System/currentTimeMillis))

(def node-id (atom ""))
(def next-message-id (atom 0))
(def log (atom {}))              ;; key to msg vector [<offset>, <msg>]
(def commits (atom {}))          ;; key to committed offset

;; update map values to be (apply f key value)
(defn map-kv->v [m f]
  (into {} (for [[k v] m] [k (f k v)])))

(defn- handler [input]
  (let [body (:body input)
        r-body {:msg_id (swap! next-message-id inc)
                :in_reply_to (:msg_id body)}]
    (case (:type body)
      "init"
      (do
        (reset! node-id (:node_id body))
        (node/fmtMsg @node-id (:src input) (assoc r-body :type "init_ok")))

      "send"
      (let [offset (- (System/currentTimeMillis) startMillis)]
        (swap! log update-in [(:key body)] (fnil conj []) [offset (:msg body)])
        (node/log (str "debug: send: log: " @log))
        (node/fmtMsg @node-id (:src input) (assoc r-body
                                                  :type "send_ok"
                                                  :offset offset)))

      "poll"
      (let [log-snap @log
            msgs (map-kv->v (select-keys log-snap (map name (keys (:offsets body)))) ;; keyword <-> string :'(
                            (fn [k v]
                              (filterv #(>= (first %) (get-in (:offsets body) [(keyword k)])) v)))]
        (node/log (str "debug: poll: msgs: " msgs))
        (node/fmtMsg @node-id (:src input) (assoc r-body
                                                  :type "poll_ok"
                                                  :msgs msgs)))

      "commit_offsets"
      (do
        (swap! commits #(merge-with (fn [v1 v2]
                                      (if (> v1 v2) v1 v2))
                                    %
                                    (:offsets body)))
        (node/log (str "debug: commit_offsets: commits: " @commits))
        (node/fmtMsg @node-id (:src input) (assoc r-body :type "commit_offsets_ok")))

      "list_committed_offsets"
      (let [commits-snap @commits
            offsets (select-keys commits-snap (map keyword (:keys body)))]
        (node/log (str "debug: list_committed_offsets: offsets: " offsets))
        (node/fmtMsg @node-id (:src input) (assoc r-body
                                                  :type "list_committed_offsets_ok"
                                                  :offsets offsets))))))


(defn -main []
  (node/run handler))

(-main)
