(ns peridot.multipart
  (:require [ring.util.codec :as codec])
  (:import org.apache.http.entity.mime.MultipartEntity
           org.apache.http.entity.mime.content.StringBody
           org.apache.http.entity.mime.content.FileBody
           org.apache.http.entity.ContentType
           java.io.ByteArrayOutputStream
           java.io.File
           java.nio.charset.Charset
           javax.activation.FileTypeMap))

(defn multipart? [params]
  (some #(instance? File %) (vals params)))

(defn ensure-string [k]
  "Ensures that the resulting key is a form-encoded string. If k is not a
  keyword or a string, then (str k) turns it into a string and passes it on to
  form-encode."
  (codec/form-encode (if (keyword? k) (name k) (str k))))

(defmulti add-part
  (fn [multipartentity key value] (type value)))

(defmethod add-part File [m k ^File f]
  (.addPart m
            (ensure-string k)
            (FileBody. f (ContentType/create 
                           (.getContentType (FileTypeMap/getDefaultFileTypeMap) f))
                       (.getName f))))

(defmethod add-part :default [m k v]
  (.addPart m
            (ensure-string k)
            (StringBody. (str v) (Charset/forName "UTF-8"))))

(defn entity [params]
  (let [mpe (MultipartEntity.)]
    (doseq [p params]
      (apply add-part mpe p))
    mpe))

(defn build [params]
  (let [^MultipartEntity mpe (entity params)]
     {:body (let [out (ByteArrayOutputStream.)]
                        (.writeTo mpe out)
                        (.close out)
                        (.toString out))

     :content-length (.getContentLength mpe)
     :content-type (.getValue (.getContentType mpe))
     :headers {"content-type"  (.getValue (.getContentType mpe))
               "content-length" (str (.getContentLength mpe))}}))

