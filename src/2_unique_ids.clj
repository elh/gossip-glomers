#!/usr/bin/env bb
(ns unique-ids)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def node-id (atom ""))
(def next-message-id (atom 0))

(defn- handler [input]
  (let [body (:body input)
        r-body {:msg_id (swap! next-message-id inc)
                :in_reply_to (:msg_id body)}]
    (case (:type body)
      "init"
      (do
        (reset! node-id (:node_id body))
        (node/sendMsg @node-id
                      (:src input)
                      (assoc r-body :type "init_ok")))
      "generate"
      (node/sendMsg @node-id
                    (:src input)
                    (assoc r-body
                           :type "generate_ok"
                           :id (str (java.util.UUID/randomUUID)))))))

(defn -main []
  (node/run handler))

(-main)
