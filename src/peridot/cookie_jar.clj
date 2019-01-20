(ns peridot.cookie-jar
  (:require [clojure.string :as string]
            [ring.util.response :as rur])
  (:import (java.text SimpleDateFormat ParseException DateFormat)
           (java.util Date Locale)))

(def cookie-date-formats
  (map #(SimpleDateFormat. ^String % Locale/US)
       ["EEE, dd MMM yyyy HH:mm:ss z"
        "EEEE, dd-MMM-yy HH:mm:ss z"]))

(defn ^:private parse-date
  "Try valid HTTP date formats until we get a date"
  [^String date]
  (or (some
        (fn [^DateFormat format]
          (try
            (.parse format date)
            ; Clojure 1.3 wraps the ParseException in a RuntimeException
            (catch RuntimeException e false)
            (catch ParseException e false)))
        cookie-date-formats)
      (throw (RuntimeException. (format "Failed to parse date: %s" date)))))

(defn ^:private dash-match [[_ g1 g2]]
  (str g1 "-" g2))

(defn ^:private dasherize [k]
  (-> k
      (string/replace #"([A-Z]+)([A-Z][a-z])" dash-match)
      (string/replace #"([a-z\d])([A-Z])" dash-match)
      (string/lower-case)))

(defn ^:private parse-map
  ([map-string] (parse-map map-string #"; *"))
  ([map-string regex]
     (when map-string
       (apply merge (map #(let [[k v] (string/split % #"=" 2)]
                            {(keyword (dasherize k)) (or v true)})
                         (string/split map-string regex))))))

(defn ^:private build-cookie [cookie-string uri host]
  (let [[assign options] (or (string/split cookie-string #"; *" 2)
                             [cookie-string nil])
        [k v] (string/split assign #"=" 2)]
    [(string/lower-case k)
     (merge {:value v}
            {:path (re-find #".*\/" uri)}
            {:domain host}
            {:raw assign}
            (parse-map options))]))

(defn ^:private set-cookie [cookie-jar [k v]]
  (assoc cookie-jar k v))

(defn merge-cookies [headers cookie-jar uri host]
  (let [cookie-string (rur/get-header {:headers headers} "Set-Cookie")]
    (if (empty? cookie-string)
      cookie-jar
      (update-in cookie-jar [host] merge
                 (into {} (map #(build-cookie % uri host) cookie-string))))))

(defn cookies-for [cookie-jar scheme uri host]
  (let [cookie-string
        (->> cookie-jar
             (remove (fn [[domain _]]
                       (not (re-find (re-pattern (str "\\.?" domain "$"))
                                     host))))
             (sort-by (comp count first))
             (map second)
             (apply merge)
             (map second)
             (remove #(when-let [expires (:expires %)]
                        (.after (Date.)
                                (parse-date expires))))
             (remove #(not (re-find (re-pattern (str "^"
                                                     (:path %)))
                                    uri)))
             (remove #(and (:secure %) (= scheme :http)))
             (map :raw)
             (interpose ";")
             (apply str))]
    (when (not (empty? cookie-string))
      {"Cookie" cookie-string})))
