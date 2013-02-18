(ns peridot.test.request
  (:use [peridot.request]
        [clojure.test])
  (:require [ring.mock.request :as mock]))

(deftest url-generation
  (testing "it should construct a URI from a ring reqeust"
    (is (= "http://example.com:4000/fuzbats"
           (url (mock/request :get "http://example.com:4000/fuzbats"))))))