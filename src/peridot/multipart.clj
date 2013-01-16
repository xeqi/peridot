(ns peridot.multipart
  (:import org.apache.http.entity.mime.MultipartEntity
           org.apache.http.entity.mime.content.StringBody
           org.apache.http.entity.mime.content.FileBody
           java.io.PipedOutputStream
           java.io.PipedInputStream
           java.io.File
           java.nio.charset.Charset
           javax.activation.FileTypeMap))

(defn multipart? [params]
  (some #(instance? File %) (vals params)))

(defmulti add-part
  (fn [multipartentity key value] (type value)))

(defmethod add-part File [m k f]
  (.addPart m
            k
            (FileBody. f (.getContentType (FileTypeMap/getDefaultFileTypeMap)
                                          f))))

(defmethod add-part :default [m k v]
  (.addPart m
            k
            (StringBody. (str v) (Charset/forName "UTF-8"))))

(defn entity [params]
  (let [mpe (MultipartEntity.)]
    (doseq [p params]
      (apply add-part mpe p))
    mpe))

(defn build [params]
  (let [mpe (entity params)]
    {:body (let [in (PipedInputStream.)
                 out (PipedOutputStream. in)]
             (future (do (.writeTo mpe out)
                         (.close out)))
             in)
     :content-length (.getContentLength mpe)
     :content-type (.getValue (.getContentType mpe))
     :headers {"content-type"  (.getValue (.getContentType mpe))
               "content-length" (.getContentLength mpe)}}))