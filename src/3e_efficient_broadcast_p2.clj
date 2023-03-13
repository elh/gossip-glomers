#!/usr/bin/env bb
(ns efficient-broadcast-p2
  (:require [clojure.set :as set]))

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def messages (atom #{}))

;; gossiping to 50% of the other nodes every 500ms
;; * Messages-per-operation is 10.7, below goal of 20
;; * Median latency is 560ms, below goal of 1s
;; * Maximum latency is 1000s, below goal of 2s
(def gossip-freq 500)
(def gossip-prob 0.5)

;; gossip all values you know to other nodes w/ some probability in an infinite loop
(defn- gossip []
  (loop []
    (doseq [node @node/node-ids]
      (when (and (not= node @node/node-id) (< (rand) gossip-prob))
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
