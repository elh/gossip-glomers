;;;; Common utilities for running maelstrom nodes
;; Adapted from maelstrom clj demo code. I started by generalizing the "echo"
;; code and hit a wall after requiring rpc logic in challenge 5b. Using aphyr's
;; nifty futures implementation.

(ns node
  (:require
   [cheshire.core :as json])
  (:import (java.util.concurrent CompletableFuture)
           (java.util.function Function)))

(defn- process-stdin
  "Read lines from the stdin and calls the handler"
  [handler]
  (doseq [line (line-seq (java.io.BufferedReader. *in*))]
    (handler line)))

(defn- parse-json
  "Parse the received input as json"
  [input]
  (try
    (json/parse-string input true) ;; parses object keys as keywords
    (catch Exception _
      nil)))

(defn- generate-json
  "Generate json string from input"
  [input]
  (when input
    (json/generate-string input)))

(defn- printout
  "Print the received input to stdout"
  [input]
  (when input
    (locking *out* ;; this locking is essential for thread safety
      (println input))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; state

(def node-id
  "Our own node ID"
  (promise))

(def node-ids
  "All node IDs in the cluster."
  (promise))

(def next-message-id
  "What's the next message ID we'll emit?"
  (atom 0))

(def rpcs
  "A map of message IDs to Futures which should be delivered with replies."
  (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; fns

(defn log
  "Print to stderr which maelstrom uses for logging"
  [input]
  (locking *err*
    (binding [*out* *err*] ;; this locking is essential for thread safety
      (println input))))

(defn- fmt-msg
  "Format a message with source node, destination node, and message body."
  [src dest body]
  {:src src
   :dest dest
   :body body})

(defn send!
  "Send a message."
  [dest body]
  (-> (fmt-msg @node-id dest body)
      generate-json
      printout))

(defn reply!
  "Replies to a request message with the given body."
  [req body]
  (send! (:src req) (assoc body :in_reply_to (:msg_id (:body req)))))

(defn rpc!
  "Sends an RPC request body to the given node, and returns a CompletableFuture
  of a response body."
  [dest body]
  (let [fut (CompletableFuture.)
        id  (swap! next-message-id inc)]
    (swap! rpcs assoc id fut)
    (send! dest (assoc body :msg_id id))
    fut))

(defn handle-reply!
  "Handles a reply to an RPC we issued."
  [{:keys [body] :as reply}]
  (when-let [fut (get @rpcs (:in_reply_to body))]
    (if (= "error" (:type body))
      (.completeExceptionally fut (ex-info (:text body)
                                           (dissoc body :type :text)))
      (.complete fut body)))
  (swap! rpcs dissoc (:in_reply_to body)))

(defn run
  "Run executes the main event handling loop. Read input from stdin and pass to
  handler."
  [handler]
  (process-stdin (comp (fn [req]
                         (if (get-in req [:body :in_reply_to])
                           (handle-reply! req)
                           (if (= (get-in req [:body :type]) "init")
                             (do
                               (deliver node-id (get-in req [:body :node_id]))
                               (deliver node-ids (get-in req [:body :node_ids]))
                               (reply! req {:type :init_ok}))
                             (handler req))))
                       parse-json)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; macros

(defmacro then
  "Takes a CompletableFuture, a binding vector with a symbol for the value of
  that future and a body. Returns a CompletableFuture which evaluates body with
  the value bound."
  [fut [sym] & body]
  `(.thenApply ^CompletableFuture ~fut
               (reify Function
                 (apply [this# ~sym]
                   ~@body))))
