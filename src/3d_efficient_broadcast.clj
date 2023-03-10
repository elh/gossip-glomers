#!/usr/bin/env bb
(ns efficient-broadcast
  (:require [clojure.set :as set]))

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def node-id (atom ""))
(def next-message-id (atom 0))

(def messages (atom #{}))
(def topology (atom {}))

;; gossiping to all other nodes every 500ms
;; * Messages-per-operation is ~21.5, below goal of 30
;; * Median latency is ~350ms, below goal of 400ms
;; * Maximum latency is ~560ms, below goal of 600ms
(def gossip-freq 500)

;; gossip all values you know to all other nodes in an infinite loop
(defn- gossip []
  (loop []
    (doseq [node (keys @topology)]
      (when (not= node @node-id)
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