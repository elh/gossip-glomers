;; common utilities for running maelstrom nodes
;; adapted from maelstrom echo example

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
  (json/generate-string input))

(defn- printout
  "Print the received input to stdout"
  [input]
  (println input))

;; public fns

(defn fmtMsg
  "Formats a message with source node, destination node, and message body."
  [src dest body]
   {:src src
    :dest dest
    :body body})

(defn run
  "Run executes the main event handling loop. Read input from stdin, handle, and send output to stdout."
  [handler]
  (process-stdin (comp printout
                       generate-json
                       handler
                       parse-json)))
