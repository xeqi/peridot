(ns peridot.core
  (:require [clojure.data.codec.base64 :as base64]
            [peridot.cookie-jar :as pcj]
            [peridot.request :as pr]
            [ring.util.response :as rur]))

(defn session
  "Creates an initial state for passing through the api."
  [app & params]
  (assoc (apply hash-map params) :app app))

(defn request
  "Send a request to the ring app, returns state containing :response and :request sent to and returned from the ring app."
  [{:keys [app headers cookie-jar content-type]} uri & env]
  (let [env (apply hash-map env)
        request-content-type (or (:content-type env) content-type)
        request (pr/build uri env headers cookie-jar request-content-type)
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
  "Set basic authorization to be used in future requests."
  [state user pass]
  (header state "authorization" (str "Basic "
                                     (String. ^"[B" (base64/encode
                                                      (.getBytes (str user ":" pass)
                                                                 "UTF-8"))
                                              "UTF-8"))))
(defn- expand-location
  "Expand a location header into an absolute url"
  [location request]
  (if (re-find #"://" location)
    location
    (pr/url (assoc request :uri location :query-string nil))))

;; Backward compatibility for clojure < 1.8 -> clojure.string/start-with?
(def ^:private
  string-starts-with?
  (or (resolve 'clojure.string/starts-with?)
      (fn [^CharSequence s ^String substr]
        (.startsWith (.toString s) substr))))

(defn follow-redirect
  "Follow the redirect from the previous response."
  [{request-map :request :as state}]
  (if-let [location (rur/get-header (:response state) "Location")]
    (let [prev-location (str (name (:scheme request-map)) "://" (:server-name request-map))
          new-location (expand-location location request-map)
          same-site? (string-starts-with? new-location prev-location)]
      (request state new-location
               :same-site? same-site?
               :headers {"referer" (pr/url request-map)}))
    (throw (IllegalArgumentException. "Previous response was not a redirect"))))
