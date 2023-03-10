#!/usr/bin/env bb
(ns efficient-broadcast-p2
  (:require [clojure.set :as set]))

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def node-id (atom ""))
(def next-message-id (atom 0))

(def messages (atom #{}))
(def topology (atom {}))

;; gossiping to 50% of the other nodes every 500ms
;; * Messages-per-operation is 10.7, below goal of 20
;; * Median latency is 560ms, below goal of 1s
;; * Maximum latency is 1000s, below goal of 2s
(def gossip-freq 500)
(def gossip-prob 0.5)

;; gossip all values you know to other nodes w/ some probability in an infinite loop
(defn- gossip []
  (loop []
    (doseq [node (keys @topology)]
      (when (and (not= node @node-id) (< (rand) gossip-prob))
        (node/send! @node-id
                    node
                    {:type "gossip"
                     :messages (seq @messages)})))
    (Thread/sleep gossip-freq)
    (recur)))

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
      "gossip"
      (do
        (swap! messages set/union (set (:messages body)))
        nil)
      "read"
      (node/fmtMsg @node-id
                   (:src input)
                   (assoc r-body
                          :type "read_ok"
                          :messages (seq @messages)))
      "topology"
      (do
        (reset! topology (:topology body))
        (node/fmtMsg @node-id
                     (:src input)
                     (assoc r-body :type "topology_ok"))))))

(defn -main []
  (future (gossip)) ;; gossip in another thread
  (node/run handler))

(-main)
