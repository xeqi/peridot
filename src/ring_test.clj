(ns ring-test
  (:import java.text.SimpleDateFormat
           java.util.Date)
  (:require [ring.mock.request :as mock]
            [clojure.data.codec.base64 :as base64]
            [clojure.string :as string]))

(defn ^:dynamic get-time []
  (System/currentTimeMillis))

(def cookie-date-format
  (SimpleDateFormat. "EEE, dd-MMM-yyyy hh:mm:ss z"))

(defn session [app & params]
  (assoc (apply hash-map params)
    :app app))

(defn ^:private dash-match [[ _ g1 g2]]
  (str g1 "-" g2))

(defn ^:private dasherize [k]
  (-> k
      (clojure.string/replace #"([A-Z]+)([A-Z][a-z])" dash-match)
      (clojure.string/replace #"([a-z\d])([A-Z])" dash-match)
      (clojure.string/lower-case)))

(defn ^:private get-host [request]
  (.toLowerCase (get (:headers request) "host")))

(defn ^:private post-content-type [request]
  (if (and (not (:content-type request))
           (= :post (:request-method request)))
    (assoc request :content-type "application/x-www-form-urlencoded")
    request))

(defn ^:private https-port [request]
  (if (= :https (:scheme request))
    (assoc request :server-port 443)
    request))

(defn ^:private to-header-key [k]
  (dasherize (str k)))

(defn ^:private add-headers [request headers]
  (reduce (fn [req [k v]]
            (if v
              (assoc-in req [:headers (to-header-key k)] v)
              req))
          request
          headers))

(defn ^:private add-env [request env]
  (reduce (fn [req [k v]] (assoc req k v))
          request
          env))

(defn ^:private parse-map
  ([map-string] (parse-map map-string #"; *"))
  ([map-string regex]
     (when map-string
       (apply merge (map #(let [[k v] (string/split % #"=" 2)]
                            {(keyword (dasherize k)) (or v true)})
                         (string/split map-string regex))))))

(defn ^:private set-cookie [cookie-jar [k v]]
  (assoc-in cookie-jar [(:domain v) k] v))

(defn ^:private build-cookie [cookie-string uri host]
  (let [[assign options] (or (string/split cookie-string #"; *" 2)
                             [cookie-string nil])
        [k v] (string/split assign #"=" 2)]
    [(.toLowerCase k)
     (merge {:value v}
            {:path (re-find #".*\/" uri)}
            {:domain host}
            {:raw assign}
            (parse-map options))]))

(defn ^:private merge-cookies [headers cookie-jar uri host]
  (reduce #(set-cookie %1 (build-cookie %2 uri host))
          cookie-jar
          (get headers "Set-Cookie")))

(defn ^:private cookies-for [cookie-jar scheme uri host]
  {"Cookie"
   (->> cookie-jar
        (remove (fn [[domain _]]
                  (not (re-find (re-pattern (str "\\.?" domain "$"))
                                host))))
        (sort-by (comp count first))
        (map second)
        (apply merge)
        (map second)
        (remove #(when-let [expires (:expires %)]
                   (.after (Date. (get-time))
                           (.parse cookie-date-format
                                   expires))))
        (remove #(when-let [path (:path %)]
                   (not (re-find (re-pattern (str "^" path "[^/]*$")) uri))))
        (remove (scheme {:http  :secure
                         :https :http-only}))
        (map :raw)
        (interpose ";")
        (apply str))})

(defn ^:private build-request [uri env headers cookie-jar]
  (let [env (apply hash-map env)
        params (:params env)
        request (mock/request :get uri params)]
    (-> request
        (add-headers (-> headers
                         (merge (cookies-for cookie-jar
                                             (:scheme request)
                                             (:uri request)
                                             (get-host request)))
                         (merge (:headers env))))
        (add-env (dissoc (dissoc env :params) :headers))
        post-content-type
        https-port)))

(defn ^:private build-url [{:keys [scheme server-name port uri query-string]}]
  (str (name scheme)
       "://"
       server-name
       (when (and port
                  (not= port (scheme {:https 443 :http 80})))
         (str ":" port))
       uri
       query-string))

(defn request [{:keys [app headers cookie-jar]} uri & env]
  (let [request (build-request uri env headers cookie-jar)
        response (app request)]
    (session app
             :response response
             :request request
             :headers headers
             :cookie-jar (merge-cookies (:headers response) cookie-jar
                                        (:uri request)
                                        (get-host request)))))

(defn header [state key value]
  (assoc-in state [:headers key] value))

(defn authorize [state user pass]
  (header state "authorization" (str "Basic "
                                     (String. (base64/encode
                                               (.getBytes (str user ":" pass)
                                                          "UTF-8"))
                                              "UTF-8")
                                     "\n")))

(defn follow-redirect [state]
  (let [headers (:headers (:response state))
        location (when headers (headers "Location"))]
    (if location
        (request state
           location
           :headers {"referrer" (build-url (:request state))})
        (throw (Exception. "Previous response was not a redirect")))))