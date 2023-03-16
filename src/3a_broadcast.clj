#!/usr/bin/env bb
(ns broadcast)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def messages (atom ()))

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "broadcast"
      (do
        (swap! messages conj (:message body))
        (node/reply! req
                     {:type "broadcast_ok"}))
      "read"
      (node/reply! req
                   {:type "read_ok"
                    :messages @messages})
      "topology"
      (node/reply! req
                   {:type "topology_ok"}))))

(defn -main []
  (node/run handler))

(-main)
