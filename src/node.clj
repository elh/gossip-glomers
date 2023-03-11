;;;; Common utilities for running maelstrom nodes
;; adapted Clojure helpers from maelstrom echo demo code

(ns node
  (:require
   [cheshire.core :as json]))

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

;; public fns

(defn log
  "Print to stderr which maelstrom uses for logging"
  [input]
  (locking *err*
    (binding [*out* *err*] ;; this locking is essential for thread safety
      (println input))))

(defn fmtMsg
  "Format a message with source node, destination node, and message body."
  [src dest body]
  {:src src
   :dest dest
   :body body})

(defn send!
  "Send a message."
  [src dest body]
  (-> (fmtMsg src dest body)
      generate-json
      printout))

(defn run
  "Run executes the main event handling loop. Read input from stdin, handle, and send output to stdout."
  [handler]
  (process-stdin (comp printout
                       generate-json
                       handler
                       parse-json)))
