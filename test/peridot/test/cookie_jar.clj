(ns peridot.test.cookie-jar
  (:import java.util.Date)
  (:use [peridot.core]
        [clojure.test])
  (:require [peridot.cookie-jar :as cj]
            [net.cgrand.moustache :as moustache]
            [ring.util.response :as response]
            [ring.middleware.params :as params]
            [ring.middleware.cookies :as cookies]))

(defn cookies-from-map [m f]
  (apply merge
         (map (fn [[k v]]
                {k (merge {:value v} (f k v))})
              (seq m))))

(def app
  (params/wrap-params
   (cookies/wrap-cookies
    (moustache/app
     ["expireable" "set"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies
               (cookies-from-map (:params req)
                                 (fn [k v]
                                   {:expires
                                    (.format cj/cookie-date-format
                                             (Date. 60000))}))))}
     ["cookies" "set"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies
               (cookies-from-map (:params req)
                                 (constantly {}))))}
     ["set"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies
               (cookies-from-map (:params req)
                                 (constantly {}))))}
     ["delete"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies {}))}
     ["set-secure"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies
               (cookies-from-map (:params req)
                                 (constantly {:secure true}))))}
     ["set-http-only"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies
               (cookies-from-map (:params req)
                                 (constantly {:http-only true}))))}
     ["default-path"]
     {:get (fn [req]
             (let [resp (response/response "ok")]
               (if (not (empty? (:params req)))
                 (assoc resp
                   :cookies
                   (cookies-from-map (:params req)
                                     (constantly {})))
                 resp)))}))))

(deftest cookies-keep-a-cookie-jar
  (-> (session app)
      (request "/show")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "cookies should be empty for new session")))
      (request "/set" :params {"value" "1"})
      (request "/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "cookies should be saved and sent back")))
      (request "/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "old cookies should be saved when no cookies are send back")))
      (request "/set" :params {"VALUE" "2"})
      (request "/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "VALUE=2")
                "cookies are case insensitive")))
      (request "/delete")
      (request "/show")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
           "cookies can be deleted")))))

(deftest cookie-jar-keeps-multiple-cookies-per-host
  (-> (session app)
      (request "/show")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "cookies should be empty for new session")))
      (request "/set" :params {"value" "1"})
      (request "/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "first cookie should be saved and sent back")))
      (request "/set" :params {"second-value" "2"})
      (request "/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "second-value=2;value=1")
                "first and second cookie under same host should be stored and send back")))
      (request "/delete")
      (request "/show")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
           "all cookies under same host can be deleted")))))

(deftest cookies-for-absolute-url
  (-> (session app)
      (request "http://www.example.com/set" :params {"value" "1"})
      (request "http://www.example.com/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "cookies should be saved and sent back for absolute url")))
      (request "http://WWW.EXAMPLE.COM/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "cookies domain should be case insensitive")))
      (request "http://www.other.example.com/show")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "cookies are not sent to other hosts")))
      (request "http://www.example.com/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "cookies are sent to subdomains")))
      (request "http://example.com/set" :params {"value" "2"})
      (request "http://www.example.com/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
           "cookies are preferred to be more specific")))
      (request "http://www.example.com/set" :params {"value" "3"})
      (request "http://www.example.com/show")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=3")
           "cookies ordering does not matter for specificity")))))

(deftest cookie-expires
  (let [state (-> (session app)
                  (request "/expirable/set" :params {"value" "1"}))]
    (binding [cj/get-time (fn [] 60001)]
      (-> state
          (request "/expirable/show")
          (doto
              (#(is (nil? (get (:headers (:request %)) "cookie"))
               "expired cookies should not be sent")))))))

(deftest cookies-uri
  (-> (session app)
      (request "/cookies/set" :params {"value" "1"})
      (request "/cookies/get")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "cookies without uri are sent to path up to last slash")))
      (request "/no-cookies/show")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "cookies without uri are not sent to other pages")))
      (request "/COOKIES/show")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "cookies treat path as case sensitive")))
      (request "/cookies/further/get")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "cookies get sent to deeper paths")))))

(deftest cookie-security
  (-> (session app)
      (request "https://example.com/set-secure"
               :params {"value" "1"})
      (request "http://example.com/get")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "secure cookies are not sent to http")))
      (request "https://example.com/get")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "secure cookies are sent"))))
  (-> (session app)
      (request "http://example.com/set-http-only"
               :params {"value" "1"})
      (request "https://example.com/get")
      (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "http-only cookies are not sent to https")))
      (request "http://example.com/get")
      (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   "value=1")
                "http-only cookies are sent")))))