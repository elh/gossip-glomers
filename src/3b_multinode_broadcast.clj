#!/usr/bin/env bb
(ns multinode-broadcast)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def messages (atom #{}))

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "broadcast"
      (do
        (swap! messages conj (:message body))
        ;; forward the broadcast to all other nodes. forward's are not acked.
        (doseq [node @node/node-ids]
          (when (not= node @node/node-id)
            (node/send! node
                        {:type "forward"
                         :message (:message body)})))
        (node/reply! req
                     {:type "broadcast_ok"}))

      "forward"
      (swap! messages conj (:message body))

      "read"
      (node/reply! req
                   {:type "read_ok"
                    :messages (seq @messages)})

      "topology"
      (node/reply! req
                   {:type "topology_ok"}))))

(defn -main []
  (node/run handler))

(-main)
