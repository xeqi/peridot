(ns peridot.test.multipart
  (:use [peridot.core]
        [clojure.test])
  (:require [peridot.multipart :as multipart]
            [ring.util.response :as response]
            [ring.middleware.multipart-params :refer [wrap-multipart-params] :as multiparams]
            [clojure.java.io :as io]))

(deftest file-as-param-is-multipart
  (is (multipart/multipart? {"file" (io/file (io/resource "file.txt"))}))
  (is (not (multipart/multipart? {"file" "value"}))))

(deftest uploading-a-file
  (let [req (:request (->
                       (response/response "ok")
                       (multiparams/wrap-multipart-params)
                       (constantly)
                       (session)
                       (request "/"
                                :request-method :post
                                :params {"file" (io/file (io/resource
                                                          "file.txt"))})))]
    (is (re-find #"multipart/form-data;"
                 (:content-type req))
        "files should set content-type to multipart/form-data")
    (is (re-find #"multipart/form-data;"
                 (get-in req [:headers "content-type"]))
        "files should set content-type header to multipart/form-data")
    (is (re-find #"hi from file\n" (slurp (:body req))))))

(deftest uploading-a-file-with-keyword-keys
  (let [req (:request (-> (session (constantly (response/response "ok")))
                          (request "/"
                                   :request-method :post
                                   :params {:file (io/file (io/resource
                                                             "file.txt"))})))]
    (is (re-find #"multipart/form-data;"
                 (:content-type req))
        "files should set content-type to multipart/form-data")
    (is (re-find #"multipart/form-data;"
                 (get-in req [:headers "content-type"]))
        "files should set content-type header to multipart/form-data")
    (is (re-find #"hi from file\n" (slurp (:body req))))))

(deftest uploading-a-file-with-params
  (let [req (:request (-> (session (constantly (response/response "ok")))
                          (request "/"
                                   :request-method :post
                                   :params {"file" (io/file (io/resource "file.txt"))
                                            "something" "☃"})))]
    (is (re-find #"multipart/form-data;" (:content-type req))
        "files should set content-type to multipart/form-data")
    (is (re-find #"multipart/form-data;"
                 (get-in req [:headers "content-type"]))
        "files should set content-type header to multipart/form-data")
    ;;This is a simple test
    ;;perhaps doing a roundtrip with multipart-params middleware
    ;;and checking response would be better
    (let [body (slurp (:body req))]
      (is (re-find #"hi from file" body))
      (is (re-find #"name=\"something\"" body))
      (is (re-find #"\r\n☃\r\n--" body)))))
