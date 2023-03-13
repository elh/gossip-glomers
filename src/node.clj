;;;; Common utilities for running maelstrom nodes
;; adapted Clojure helpers from maelstrom echo demo code

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
    (json/parse-string input true)
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

;; public fns

(defn log
  "Print to stderr which maelstrom uses for logging"
  [input]
  (locking *err*
    (binding [*out* *err*] ;; this locking is essential for thread safety
      (println input))))

(defn fmt-msg
  "Format a message with source node, destination node, and message body."
  [src dest body]
  {:src src
   :dest dest
   :body body})

(defn send!
  "Send a message."
  [src dest body]
  (-> (fmt-msg src dest body)
      generate-json
      printout))

;; TODO: embrace a stateful node model

(def rpcs
  "A map of message IDs to Futures which should be delivered with replies."
  (atom {}))

(defn rpc!
  "Sends an RPC request body to the given node, and returns a CompletableFuture
  of a response body."
  [src dest next-message-id body]
  (let [fut (CompletableFuture.)
        id  (swap! next-message-id inc)]
    (swap! rpcs assoc id fut)
    (send! src dest (assoc body :msg_id id))
    fut))

(defn handle-reply!
  "Handles a reply to an RPC we issued."
  [{:keys [body] :as reply}]
  (when-let [fut (get @rpcs (:in_reply_to body))]
    (if (= "error" (:type body))
      (.completeExceptionally fut (ex-info (:text body)
                                           (dissoc body :type :text)))
      (.complete fut body)))
  (swap! rpcs dissoc (:in_reply_to body))
  ;; TODO: fix this JANK. returning nil so when used in handle fn's, this does not send an empty message
  nil)

(defn run
  "Run executes the main event handling loop. Read input from stdin, handle, and send output to stdout."
  [handler]
  (process-stdin (comp printout
                       generate-json
                       handler
                       parse-json)))
