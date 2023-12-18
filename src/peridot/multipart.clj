(ns peridot.multipart
  (:require [clojure.java.io :as io]
            [ring.util.codec :as codec]
            [ring.util.mime-type :as mime-type])
  (:import (java.io ByteArrayOutputStream File)
           (java.nio.charset Charset)
           (org.apache.http HttpEntity)
           (org.apache.http.entity ContentType)
           (org.apache.http.entity.mime MultipartEntityBuilder)
           (org.apache.http.entity.mime.content StringBody FileBody)))

(defn multipart? [params]
  (some #(instance? File %) (vals params)))

(defn ensure-string [k]
  "Ensures that the resulting key is a form-encoded string. If k is not a
  keyword or a string, then (str k) turns it into a string and passes it on to
  form-encode."
  (codec/form-encode (if (keyword? k) (name k) (str k))))

(defmulti add-part
  (fn [multipartentity key value] (type value)))

(defmethod add-part File [^MultipartEntityBuilder m k ^File f]
  (.addPart m
            (ensure-string k)
            (FileBody. f (ContentType/create
                           (mime-type/ext-mime-type (.getName f)))
                       (.getName f))))

(defmethod add-part :default [^MultipartEntityBuilder m k v]
  (.addPart m
            (ensure-string k)
            (StringBody. (str v) (Charset/forName "UTF-8"))))

(defn entity [params]
  (let [b (doto (MultipartEntityBuilder/create)
            (.setCharset (Charset/forName "UTF-8")))]
    (doseq [p params]
      (apply add-part b p))
    (.build b)))

(defn build [params]
  (let [^HttpEntity mpe (entity params)]
    {:body           (let [out (ByteArrayOutputStream.)]
                       (.writeTo mpe out)
                       (.close out)
                       (io/input-stream (.toByteArray out)))

     :content-length (.getContentLength mpe)
     :content-type   (.getValue (.getContentType mpe))
     :headers        {"content-type"   (.getValue (.getContentType mpe))
                      "content-length" (str (.getContentLength mpe))}}))

