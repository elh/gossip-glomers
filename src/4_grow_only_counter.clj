#!/usr/bin/env bb
(ns grow-only-counter)

;;;; Grow-only counter, state-based CRDT implemented with a version vector and gossip.

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

;;; Node state

(def version-vec (atom {}))     ;; version vector mapping node id to versioned value
(def members (atom []))         ;; cluster membership

;;; Cluster membership
;;;
;;; We use the seq-kv service only for eventually consistent cluster membership.

(def register-freq 1000) ;; add jitter?
(defn- register-membership []
  (loop []
    (when-not (some #{@node/node-id} @members)
      (node/send! "seq-kv"
                  {:type "cas"
                   :key "members"
                   :from @members
                   :to (conj @members @node/node-id)
                   :create_if_not_exists true})
      (Thread/sleep register-freq)
      (recur))))

(def check-freq 1000)
(defn- check-membership []
  (loop []
    (node/send! "seq-kv"
                {:type "read"
                 :key "members"})
    (Thread/sleep check-freq)
    (recur)))

;;; G-Counter
;;;
;;; We represent state as a versioned vector of node ids to versioned values. Each
;;; node accepts "adds" to the value they own and and increments the version. "Reads"
;;; return the sum of all known values in the version vector.
;;;
;;; We gossip the version vector to all other nodes in the cluster. With eventual
;;; consistency, each node merges their state by taking the value for each node with
;;; the highest version.

(def gossip-freq 500)
(def gossip-prob 0.5)
(defn- gossip []
  (loop []
    (doseq [node @members]
      (when (and (not= node @node/node-id) (< (rand) gossip-prob))
        (node/send! node
                    {:type "gossip"
                     :version-vec @version-vec})))
    (Thread/sleep gossip-freq)
    (recur)))

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "add"
      (let [node-id-key (keyword @node/node-id)]
        (swap! version-vec (fn [cur] (assoc cur
                                            node-id-key
                                            {:value (+ (get-in cur [node-id-key :value] 0) (:delta body))
                                             :version (inc (get-in cur [node-id-key :version] 0))})))
        (node/reply! req
                     {:type "add_ok"}))
      "read"
      (let [v (reduce #(+ %1 (:value %2)) 0 (vals @version-vec))]
        (node/log (str "debug: version vector: " @version-vec))
        (node/reply! req
                     {:type "read_ok"
                      :value v}))
      "gossip"
      (swap! version-vec #(merge-with (fn [v1 v2]
                                        (if (> (:version v1) (:version v2))
                                          v1
                                          v2))
                                      %
                                      (:version-vec body)))
      ;; assumes this is the read response for "members" in the seq-kv
      "read_ok"
      (reset! members (:value body))

      ;; ignored
      "cas_ok" nil
      "error" nil)))

(defn -main []
  ;; in the background, manage membership and gossip state
  (future (check-membership))
  (future (register-membership))
  (future (gossip))
  (node/run handler))

(-main)
