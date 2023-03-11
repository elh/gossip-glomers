#!/usr/bin/env bb
(ns grow-only-counter)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(def node-id (atom ""))
(def next-message-id (atom 0))

;; version vector mapping node id to versioned value (keys: :value, :version)
(def version-vec (atom {}))
(def topology (atom {}))

(def gossip-freq 500)
(def gossip-prob 1)
(defn- gossip []
  (loop []
    (doseq [node (keys @topology)]
      (when (and (not= node @node-id) (< (rand) gossip-prob))
        (node/send! @node-id
                    node
                    {:type "gossip"
                     :version-vec @version-vec})))
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
      ;; TODO: oops. there is no topology request. we can use the seq-kv to communicate membership and then gossip on our own
      "topology"
      (do
        (prn "CRASH: GOT TOPOLOGY??")
        (reset! topology (:topology body))
        (reset! version-vec (zipmap (keys (:topology body)) (repeat {:value 0 :version 0})))
        (node/fmtMsg @node-id
                     (:src input)
                     (assoc r-body :type "topology_ok")))
      "add"
      (do
        (swap! version-vec assoc @node-id
               {:value (+ (get-in @version-vec [@node-id :value] 0) (:delta body))
                :version (inc (get-in @version-vec [@node-id :version] 0))})
        (node/fmtMsg @node-id
                     (:src input)
                     (assoc r-body :type "add_ok")))
      "read"
      (let [v (reduce #(+ %1 (:value %2)) 0 (vals @version-vec))]
        (node/fmtMsg @node-id
                     (:src input)
                     (assoc r-body
                            :type "read_ok"
                            :value v)))
      "gossip"
      (do
        (swap! version-vec merge-with
               (fn [v1 v2]
                 (if (> (:version v1) (:version v2))
                   v1
                   v2))
               (:version-vec body))
        nil))))

(defn -main []
  (future (gossip))
  (node/run handler))

(-main)
