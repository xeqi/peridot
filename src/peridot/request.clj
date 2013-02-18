(ns peridot.request
  (:require [peridot.multipart :as multipart]
            [peridot.cookie-jar :as cj]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [ring.mock.request :as mock])
  (:import java.io.ByteArrayInputStream))

(defmulti to-input-stream class)

(defmethod to-input-stream nil [_] nil)
(defmethod to-input-stream String [s] (-> s .getBytes ByteArrayInputStream.))
(defmethod to-input-stream :default [x] (io/input-stream x))

(defn get-host [request]
  (string/lower-case (get (:headers request) "host")))

(defn set-post-content-type [request]
  (if (and (not (:content-type request))
           (= :post (:request-method request)))
    (mock/content-type request "application/x-www-form-urlencoded")
    request))

(defn set-https-port [request]
  (if (= :https (:scheme request))
    (assoc request :server-port 443)
    request))

(defn add-headers [request headers]
  (reduce (fn [req [k v]]
            (if v
              (mock/header req k v)
              req))
          request
          headers))

(defn add-env [request env]
  (reduce (fn [req [k v]] (assoc req k v))
          request
          env))

(defn set-content-type [request content-type]
  (if content-type
    (mock/content-type request content-type)
    request))

(defn build [uri env headers cookie-jar content-type]
  (let [env (apply hash-map env)
        params (:params env)
        method (:request-method env :get)
        request (if (multipart/multipart? params)
                  (merge-with merge
                              (multipart/build params)
                              (mock/request method uri))
                  (mock/request method uri params))]
    (-> request
        (add-headers (-> headers
                         (merge (cj/cookies-for cookie-jar
                                                (:scheme request)
                                                (:uri request)
                                                (get-host request)))
                         (merge (:headers env))))
        (set-content-type content-type)
        (add-env (dissoc (dissoc env :params) :headers))
        (update-in [:body] to-input-stream)
        set-post-content-type
        set-https-port)))

(defn url [{:keys [scheme server-name server-port uri query-string]}]
  (str (name scheme)
       "://"
       server-name
       (when (and server-port
                  (not= server-port (scheme {:https 443 :http 80})))
         (str ":" server-port))
       uri
       query-string))