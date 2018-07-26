(ns tech.io.temp-file
  (:require [think.resource.core :as resource]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [clojure.string :as s]
            [tech.io.uuid :as uuid]
            [tech.io.url :as url])
  (:import [java.io File]))


;; We have had issues where we filled up the temporary space on the volume
;; of the docker image in production with temp files.  To avoid this there is
;; an implicit assumption that ring-produced temporary files will be deleted
;; once the handler is finished.  While this isn't a perfect solution it could
;; be extended and used in other places and then we would have a lot more confidence
;; that temporary data would be cleaned up regardless of failure conditions

(defn- random-temp-dir-str
  ^String [root]
  (s/join File/separator [root (uuid/random-uuid-str)]))


(defonce ^:dynamic *files-in-flight* (atom #{}))


(defrecord ResourceFile [path-or-file]
  resource/PResource
  (release-resource [this]
    (fs/delete-dir path-or-file)))


(defn watch-file-for-delete
  [path-or-file]
  (resource/track (ResourceFile. path-or-file))
  path-or-file)


(defn random-temp-dir
  [& {:keys [root]
      :or {root (System/getProperty "java.io.tmpdir")}}]
  (let [resource-dir (->ResourceDir (random-temp-dir-str root))
        retval (:path-or-file resource-dir)]
    (fs/mkdirs retval)
    (watch-for-delete retval)))


(defmacro with-temp-dir
  "Execute code with a variable bound to the name of a temp directory
that will be removed when the code completes (or throws an exception)."
  [dirname-var & body]
  `(resource/with-resource-context
     (let [~dirname-var (random-temp-dir)]
       ~@body)))


(defn random-file-url
  [dirname & [suffix]]
  (url/parts->url {:protocol :file
                   :path (concat
                          (s/split dirname (re-pattern "/\\\\"))
                          [(format "%s%s" (uuid/random-uuid-str) (or suffix ""))])}))
