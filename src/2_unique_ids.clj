#!/usr/bin/env bb
(ns unique-ids)

(require '[babashka.classpath :as cp])
(require '[babashka.fs :as fs])
(cp/add-classpath (str (fs/file (fs/parent *file*))))

(require 'node)

(defn- handler [req]
  (let [body (:body req)]
    (case (:type body)
      "generate"
      (node/reply! req
                   {:type "generate_ok"
                    :id (str (java.util.UUID/randomUUID))}))))

(defn -main []
  (node/run handler))

(-main)
