#!/usr/bin/env bb
(ns broadcast)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def node-id (atom ""))
(def next-message-id (atom 0))

(def messages (atom ()))

(defn- handler [input]
  (let [body (:body input)
        r-body {:msg_id (swap! next-message-id inc)
                :in_reply_to (:msg_id body)}]
    (case (:type body)
      "init"
      (do
        (reset! node-id (:node_id body))
        (node/fmtMsg @node-id
                      (:src input)
                      (assoc r-body :type "init_ok")))
      "broadcast"
      (do
        (swap! messages conj (:message body))
        (node/fmtMsg @node-id
                      (:src input)
                      (assoc r-body :type "broadcast_ok")))
      "read"
      (node/fmtMsg @node-id
                    (:src input)
                    (assoc r-body
                           :type "read_ok"
                           :messages @messages))
      "topology"
      (do
        (comment "TODO: implement topology")
        (node/fmtMsg @node-id
                      (:src input)
                      (assoc r-body :type "topology_ok"))))))

(defn -main []
  (node/run handler))

(-main)
