(ns peridot.core
  (:require [peridot.request :as pr]
            [peridot.cookie-jar :as pcj]
            [clojure.data.codec.base64 :as base64]))

(defn session
  "Creates an initial state for passing through the api."
  [app & params]
  (assoc (apply hash-map params) :app app))

(defn request
  "Send a request to the ring app, returns state containing :response and :request sent to and returned from the ring app."
  [{:keys [app headers cookie-jar content-type]} uri & env]
  (let [request (pr/build uri env headers cookie-jar content-type)
        response (app request)]
    (session app
             :response response
             :request request
             :headers headers
             :content-type content-type
             :cookie-jar (pcj/merge-cookies (:headers response)
                                            cookie-jar
                                            (:uri request)
                                            (pr/get-host request)))))

(defn header
  "Set headers to be sent for future requests."
  [state key value]
  (assoc-in state [:headers key] value))

(defn content-type
  "Set content-type to be sent for future requests."
  [state value]
  (assoc state :content-type value))

(defn authorize
  "Set basic autorization to be used in future requests."
  [state user pass]
  (header state "authorization" (str "Basic "
                                     (String. (base64/encode
                                               (.getBytes (str user ":" pass)
                                                          "UTF-8"))
                                              "UTF-8"))))
(defn- expand-location
  "Expand a location header into an absolute url"
  [location request]
  (if (re-find #"://" location)
    location
    (pr/url (assoc request :uri location :query-string nil))))

(defn follow-redirect
  "Follow the redirect from the previous response."
  [state]
  (let [headers (:headers (:response state))
        location (when headers (headers "Location"))]
    (if location
        (request state
           (expand-location location (:request state))
           :headers {"referrer" (pr/url (:request state))})
        (throw (IllegalArgumentException. "Previous response was not a redirect")))))
