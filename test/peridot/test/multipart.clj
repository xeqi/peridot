(ns peridot.test.multipart
  (:use [peridot.core]
        [clojure.test])
  (:require [peridot.multipart :as multipart]
            [ring.util.response :as response]
            [clojure.java.io :as io]))

(deftest file-as-param-is-multipart
  (is (multipart/multipart? {"file" (io/file (io/resource "file.txt"))}))
  (is (not (multipart/multipart? {"file" "value"}))))

(deftest uploading-a-file
  (-> (session (constantly (response/response "ok")))
      (request "/"
               :request-method :post
               :params {"file" (io/file (io/resource "file.txt"))})
      (doto
          (#(is (re-find #"multipart/form-data;"
                         (:content-type (:request %)))
                "files shoul set content-type to multipart/form-data"))
        (#(is (re-find #"hi from file" (slurp (:body (:request %)))))))))

(deftest uploading-a-file-with-params
  (-> (session (constantly (response/response "ok")))
      (request "/"
               :request-method :post
               :params {"file" (io/file (io/resource "file.txt"))
                        "something" "☃"})
      (doto
          (#(is (re-find #"multipart/form-data;"
                         (:content-type (:request %)))
                "files should set content-type to multipart/form-data"))
        ;;This is a simple test
        ;;perhaps doing a roundtrip with multipart-params middleware
        ;;and checking response would be better
        (#(let [body (slurp (:body (:request %)))]
            (is (re-find #"hi from file" body))
            (is (re-find #"name=\"something\"" body))
            (is (re-find #"\r\n☃\r\n--" body)))))))