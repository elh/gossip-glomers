#!/usr/bin/env bb
(ns multinode-broadcast)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def node-id (atom ""))
(def next-message-id (atom 0))

(def messages (atom #{}))
(def topology (atom {}))

(defn- handler [input]
  (let [body (:body input)
        r-body {:msg_id (swap! next-message-id inc)
                :in_reply_to (:msg_id body)}]
    (case (:type body)
      "init"
      (do
        (reset! node-id (:node_id body))
        (node/fmt-msg @node-id
                      (:src input)
                      (assoc r-body :type "init_ok")))
      "broadcast"
      (do
        (swap! messages conj (:message body))
        ;; forward the broadcast to all other nodes. forward's are not acked.
        (doseq [node (keys @topology)]
          (when (not= node @node-id)
            (node/send! @node-id
                        node
                        {:type "forward"
                         :message (:message body)})))
        (node/fmt-msg @node-id
                      (:src input)
                      (assoc r-body :type "broadcast_ok")))
      "forward"
      (do
        (swap! messages conj (:message body))
        nil)
      "read"
      (node/fmt-msg @node-id
                    (:src input)
                    (assoc r-body
                           :type "read_ok"
                           :messages (seq @messages)))
      "topology"
      (do
        (reset! topology (:topology body))
        (node/fmt-msg @node-id
                      (:src input)
                      (assoc r-body :type "topology_ok"))))))

(defn -main []
  (node/run handler))

(-main)
