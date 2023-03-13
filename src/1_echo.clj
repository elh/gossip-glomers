#!/usr/bin/env bb
(ns echo)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "echo"
      (node/reply! req
                   {:type "echo_ok"
                    :echo (:echo body)}))))

(defn -main []
  (node/run handler))

(-main)
