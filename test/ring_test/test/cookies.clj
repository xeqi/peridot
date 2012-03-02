(ns ring-test.test.cookies
  (:import java.util.Date)
  (:use [ring-test]
        [clojure.test])
  (:require [net.cgrand.moustache :as moustache]
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
     ["set"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies
               (cookies-from-map (:params req)
                                 (fn [k v]
                                   {:expires
                                    (.format cookie-date-format
                                             (Date. 60000))}))))}
     ["cookies" "set"]
     {:get (fn [req]
             (assoc (response/response "ok")
               :cookies
               (cookies-from-map (:params req)
                                 (constantly {}))))}
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
  (binding [get-time (constantly 0)]
    (let [state (-> (session app)
                    (request "/show"))]
      (is (empty? ((:headers (:request state)) "cookie"))
          "cookies should be empty for new session"))
    (let [state (-> (session app)
                    (request "/set" :params {"value" "1"})
                    (request "/show"))]
      (is (= "value=1"
             ((:headers (:request state)) "cookie"))
          "cookies should be saved and sent back"))
    (let [state (-> (session app)
                    (request "/set" :params {"value" "1"})
                    (request "/show")
                    (request "/show"))]
      (is (= "value=1"
             ((:headers (:request state)) "cookie"))
          "old cookies should be saved when no cookies are send back"))
    (let [state (-> (session app)
                    (request "http://www.example.com/set" :params {"value" "1"})
                    (request "http://www.example.com/show"))]
      (is (= "value=1"
             ((:headers (:request state)) "cookie"))
          "cookies should be saved and sent back for absolute url"))
    (let [state (-> (session app)
                    (request "http://www.example.com/set" :params {"value" "1"})
                    (request "http://WWW.EXAMPLE.COM/show"))]
      (is (= "value=1"
             ((:headers (:request state)) "cookie"))
          "cookies domain should be case insensitive"))
    (let [state (-> (session app)
                    (request "/set" :params {"value" "1"})
                    (request "/set" :params {"VALUE" "2"})
                    (request "/show"))]
      (is (= "VALUE=2"
             ((:headers (:request state)) "cookie"))
          "cookies names should be case insensitive"))))

(deftest cookie-expires
  (let [state (-> (session app)
                  (request "/set" :params {"value" "1"}))]
    (binding [get-time (fn [] 60001)]
      (let [state (-> state
                      (request "/show"))]
        (is (empty? ((:headers (:request state)) "cookie"))
            "expired cookies should not be sent")))))

(deftest cookies-uri
  (let [state (-> (session app)
                  (request "/cookies/set" :params {"value" "1"})
                  (request "/cookies/get"))]
    (is (= "value=1"
           ((:headers (:request state)) "cookie"))
        "cookies without uri are sent to path up to last slash")
    (let [state (-> state
                    (request "/no-cookies/show"))]
      (is (empty? ((:headers (:request state)) "cookie"))
          "cookies without uri are not sent to other pages")))
  (binding [get-time (constantly 0)]
    (let [state (-> (session app)
                    (request "http://www.example.com/set" :params {"value" "1"})
                    (request "http://www.other.example.com/show"))]
      (is (empty? ((:headers (:request state)) "cookie"))
          "cookies are not sent to other hosts"))
    (let [state (-> (session app)
                    (request "http://example.com/set" :params {"value" "1"})
                    (request "http://www.example.com/show"))]
      (is (= "value=1"
             ((:headers (:request state)) "cookie"))
          "cookies are sent to subdomains"))
    (let [state (-> (session app)
                    (request "http://example.com/set" :params {"value" "1"})
                    (request "http://www.example.com/set" :params {"value" "2"})
                    (request "http://www.example.com/show"))]
      (is (= "value=2"
             ((:headers (:request state)) "cookie"))
          "cookies are preferred to be more specific"))
    (let [state (-> (session app)
                    (request "http://www.example.com/set" :params {"value" "2"})
                    (request "http://example.com/set" :params {"value" "1"})
                    (request "http://www.example.com/show"))]
      (is (= "value=2"
             ((:headers (:request state)) "cookie"))
          "cookies ordering does not matter for specificity")))
  (let [state (-> (session app)
                  (request "/cookies/set" :params {"value" "1"})
                  (request "/COOKIES/show"))]
    (is (empty? ((:headers (:request state)) "cookie"))
        "cookies treat path as case sensitive")))

(deftest cookie-security
  (let [state (-> (session app)
                  (request "https://example.com/set-secure"
                           :params {"value" "1"})
                  (request "http://example.com/get"))]
    (is (empty? ((:headers (:request state)) "cookie"))
        "secure cookies are not sent to http")
    (let [state (-> state
                    (request "https://example.com/get"))]
      (is (= "value=1"
             ((:headers (:request state)) "cookie"))
          "secure cookies are sent")))
  (let [state (-> (session app)
                  (request "http://example.com/set-http-only"
                           :params {"value" "1"})
                  (request "https://example.com/get"))]
    (is (empty? ((:headers (:request state)) "cookie"))
        "http-only cookies are not sent to https")
    (let [state (-> state
                    (request "http://example.com/get"))]
      (is (= "value=1"
             ((:headers (:request state)) "cookie"))
          "http-only cookies are sent"))))