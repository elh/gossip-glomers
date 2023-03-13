#!/usr/bin/env bb
(ns efficient-broadcast
  (:require [clojure.set :as set]))

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def messages (atom #{}))

;; gossiping to all other nodes every 500ms
;; * Messages-per-operation is ~21.5, below goal of 30
;; * Median latency is ~350ms, below goal of 400ms
;; * Maximum latency is ~560ms, below goal of 600ms
(def gossip-freq 500)

;; gossip all values you know to all other nodes in an infinite loop
(defn- gossip []
  (loop []
    (doseq [node @node/node-ids]
      (when (not= node @node/node-id)
        (node/send! node
                    {:type "gossip"
                     :messages (seq @messages)})))
    (Thread/sleep gossip-freq)
    (recur)))

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "broadcast"
      (do
        (swap! messages conj (:message body))
        (node/reply! req
                     {:type "broadcast_ok"}))

      "gossip"
      (swap! messages set/union (set (:messages body)))

      "read"
      (node/reply! req
                   {:type "read_ok"
                    :messages (seq @messages)})

      "topology"
      (node/reply! req
                   {:type "topology_ok"}))))

(defn -main []
  (future (gossip)) ;; gossip in another thread
  (node/run handler))

(-main)
