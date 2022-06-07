(ns peridot.test.cookie-jar
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.edn]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [net.cgrand.moustache :as moustache]
            [peridot.core :refer [session request follow-redirect]]
            [peridot.cookie-jar :as cj]
            [ring.util.response :as response]
            [ring.util.codec :as codec]
            [ring.middleware.params :as params]
            [ring.middleware.cookies :as cookies]
            [ring.util.response :as rur])
  (:import (java.text DateFormat)
           (java.util Date Locale)))

(defn cookies-from-map [m f]
  (apply merge
         (map (fn [[k v]]
                {k (merge {:value v} (f k v))})
              (seq m))))

(def expired-date
  (.format ^DateFormat (first cj/cookie-date-formats) (Date. 0)))

(defn expire-cookie [m]
  (assoc m :expires expired-date :value ""))

(defn cookie-set
  "Given a cookie header string, split into a set of cookie name=val strings"
  [string]
  (into #{} (str/split string #";")))

(def app
  (params/wrap-params
    (cookies/wrap-cookies
      (moustache/app
        ["expirable" "set"]
        {:get (fn [req]
                (assoc (response/response "ok")
                  :cookies
                  (cookies-from-map (:params req)
                                    (fn [k v] {:expires v}))))}
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
                  :cookies (into {} (for [[k v] (:cookies req)]
                                      [k (expire-cookie v)]))))}
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
        ["echo"]
        {:post (fn [{:keys [params] :as req}]
                 (let [cookies (some-> params (get "cookies") clojure.edn/read-string)
                    headers (some-> params (get "headers") clojure.edn/read-string)]
                   (cond-> (response/response "ok")
                     cookies (assoc :cookies cookies)
                     headers (assoc :headers headers))))}
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
        (#(is (= (cookie-set (get-in % [:request :headers "cookie"]))
                 #{"second-value=2" "value=1"})
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

(deftest cookies-on-different-ports
  (-> (session app)
      (request "http://www.example.com/set" :params {"value" "1"})
      (request "http://www.example.com/show")
      (doto
        (#(is (= (rur/get-header (:request %) "cookie")
                 "value=1"))))
      (request "http://www.example.com:80/set" :params {"value" "2"})
      (doto
        (#(is (= (rur/get-header (:request %) "cookie")
                 "value=1")))
        (#(is (= (rur/get-header (:response %) "Set-Cookie")
                 '("value=2")))))
      (request "http://www.example.com:8080/set" :params {"value" "3"})
      (doto
        (#(is (= (rur/get-header (:request %) "cookie")
                 "value=2")))
        (#(is (= (rur/get-header (:response %) "Set-Cookie")
                 '("value=3")))))
      (request "https://www.example.com:443/set" :params {"value" "4"})
      (doto
        (#(is (= (rur/get-header (:request %) "cookie")
                 "value=3")))
        (#(is (= (rur/get-header (:response %) "Set-Cookie")
                 '("value=4")))))
      (request "https://www.example.com:4443/set" :params {"value" "5"})
      (doto
        (#(is (= (rur/get-header (:request %) "cookie")
                 "value=4")))
        (#(is (= (rur/get-header (:response %) "Set-Cookie")
                 '("value=5")))))))

(deftest cookie-expires
  (let [hour-ago (t/from-now (t/hours -1))
        ; See http://tools.ietf.org/html/rfc2616#section-3.3.1
        params   {"rfc822" (tf/unparse (:rfc822 tf/formatters) hour-ago)
                  "rfc850" (tf/unparse
                             (tf/with-locale (tf/formatter "EEEE, dd-MMM-yy HH:mm:ss z") Locale/US)
                             hour-ago)}
        state    (-> (session app)
                     (request "/expirable/set" :params params))]
    (-> state
        (request "/expirable/show")
        (doto
          (#(is (nil? (get (:headers (:request %)) "cookie"))
                "expired cookies should not be sent")))))
  (let [hour-ahead (t/from-now (t/hours 1))
        rfc822date (tf/unparse (:rfc822 tf/formatters) hour-ahead)
        rfc850date (tf/unparse (tf/with-locale (tf/formatter "EEEE, dd-MMM-yy HH:mm:ss z") Locale/US)
                               hour-ahead)
        params     {"rfc822" rfc822date, "rfc850" rfc850date}
        state      (-> (session app)
                       (request "/expirable/set" :params params))]
    (-> state
        (request "/expirable/show")
        (doto
          (#(is (= (get (:headers (:request %)) "cookie")
                   (format "rfc822=%s;rfc850=%s"
                           (codec/form-encode rfc822date)
                           (codec/form-encode rfc850date)))
                "un-expired cookies should be sent"))))))

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
        (#(is (= (get (:headers (:request %)) "cookie")
                 "value=1")
              "http-only cookies are still sent to https")))
      (request "http://example.com/get")
      (doto
        (#(is (= (get (:headers (:request %)) "cookie")
                 "value=1")
              "http-only cookies are sent")))))

(defn set-cookie [app host cookie]
  (request app
           (str host "/echo")
           :request-method :post
           :params {:cookies (pr-str {"a" (merge {:value "b",
                                                  :path "/",
                                                  ;; :lax is default
                                                  :same-site :lax}
                                                 cookie)})}))

(defn cross-site-request [a b & [cookie]]
  (-> (session app)
      (set-cookie a cookie)
      ;; Redirect back from B to A
      (request (str b "/echo")
               :request-method :post
               :params {:headers (pr-str {"Location" (str a "/cookies/get")})})
      (follow-redirect)))

(defn cookie-has-been-sent [app]
  (doto app
    (#(is (= (get (:headers (:request %)) "cookie")
             "a=b")
          "cookie has been sent"))))

(defn cookie-has-not-been-sent [app]
  (doto app
    (#(is (not= (get (:headers (:request %)) "cookie")
                "a=b")
          "cookie has not been sent"))))

(deftest cookie-security-same-site
  ;; - cookies are sent cross domain (and cross protocol) for :same-site :lax (default)
  (-> (cross-site-request
       "http://host-a.com"
       "http://host-b.com")
      (cookie-has-been-sent))

  ;; - cookies should not be sent cross-domain for :same-site :strict
  (-> (cross-site-request
       "http://host-a.com"
       "http://host-b.com"
       {:same-site :strict})

      (cookie-has-not-been-sent)

      ;; On a subsequent normal request cookies are sent again
      (request "http://host-a.com/cookies/get")
      (cookie-has-been-sent))

  ;; Not sure about this one (also not supported in all browsers, see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite)
  ;; - cookies should not be send for :same-site :none without :secure true
  (-> (session app)
      (set-cookie "https://host-a.com" {:same-site :none})

      (request "https://host-a.com/cookies/get")
      (cookie-has-not-been-sent))

  ;; - cookies are sent cross domain for :same-site :none and :secure true
  (-> (cross-site-request
       "https://host-a.com"
       "https://host-b.com"
       {:same-site :strict
        :secure true})

      (cookie-has-been-sent)))
