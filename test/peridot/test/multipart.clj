(ns peridot.test.multipart
  (:use [peridot.core]
        [clojure.test])
  (:require [peridot.multipart :as multipart]
            [ring.middleware.multipart-params :as multiparams]
            [ring.util.response :as response]
            [clojure.java.io :as io]))

(deftest file-as-param-is-multipart
  (is (multipart/multipart? {"file" (io/file (io/resource "file.txt"))}))
  (is (not (multipart/multipart? {"file" "value"}))))

(def ok-with-multipart-params
  (-> (fn [req]
        (-> (response/response "ok")
            (assoc :multipart-params
              (:multipart-params req))))
      (multiparams/wrap-multipart-params)))

(deftest uploading-a-file
  (let [file (io/file (io/resource "file.txt"))
        res (-> (session ok-with-multipart-params)
                (request "/"
                         :request-method :post
                         :params {"file" file})
                :response)]
    (let [{:keys [size filename content-type tempfile]}
          (get-in res [:multipart-params "file"])]
      (is (= size 13))
      (is (= filename "file.txt"))
      ;; TODO should this be text content type?
      (is (= content-type "application/octet-stream"))
      (is (= (slurp tempfile) (slurp file))))))

(deftest uploading-a-file-with-keyword-keys
  (let [file (io/file (io/resource "file.txt"))
        res (-> (session ok-with-multipart-params)
                (request "/"
                         :request-method :post
                         :params {:file file})
                :response)]
    (let [{:keys [size filename content-type tempfile]}
          (get-in res [:multipart-params "file"])]
      (is (= size 13))
      (is (= filename "file.txt"))
      ;; TODO should this be text content type?
      (is (= content-type "application/octet-stream"))
      (is (= (slurp tempfile) (slurp file))))))

(deftest uploading-a-file-with-params
  (let [file (io/file (io/resource "file.txt"))
        res (-> (session ok-with-multipart-params)
                (request "/"
                         :request-method :post
                         :params {"file" file
                                  "something" "☃"})
                :response)]
    (let [{:keys [size filename content-type tempfile]}
          (get-in res [:multipart-params "file"])]
      (is (= size 13))
      (is (= filename "file.txt"))
      ;; TODO should this be text content type?
      (is (= content-type "application/octet-stream"))
      (is (= (slurp tempfile) (slurp file))))
    (is (= (get-in res [:multipart-params "something"])
           "☃"))))
