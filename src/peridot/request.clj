(ns peridot.request
  (:require [peridot.multipart :as multipart]
            [peridot.cookie-jar :as cj]
            [clojure.string :as string]
            [ring.mock.request :as mock]))

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
        request (if (multipart/multipart? params)
                  (merge-with merge
                              (multipart/build params)
                              (mock/request :get uri))
                  (mock/request :get uri params))]
    (-> request
        (add-headers (-> headers
                         (merge (cj/cookies-for cookie-jar
                                                (:scheme request)
                                                (:uri request)
                                                (get-host request)))
                         (merge (:headers env))))
        (set-content-type content-type)
        (add-env (dissoc (dissoc env :params) :headers))
        set-post-content-type
        set-https-port)))

(defn url [{:keys [scheme server-name port uri query-string]}]
  (str (name scheme)
       "://"
       server-name
       (when (and port
                  (not= port (scheme {:https 443 :http 80})))
         (str ":" port))
       uri
       query-string))