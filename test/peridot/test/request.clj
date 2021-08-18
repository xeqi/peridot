(ns peridot.test.request
  (:require [clojure.test :refer :all]
            [peridot.request :refer [url get-host]]
            [ring.mock.request :as mock]))

(deftest url-generation
  (testing "it should construct a URI from a ring reqeust"
    (is (= "http://example.com:4000/fuzbats"
           (url (mock/request :get "http://example.com:4000/fuzbats")))))
  (testing "it should include querystrings"
    (is (= "http://example.com:4000/fuzbats?a=1"
           (url (mock/request :get "http://example.com:4000/fuzbats?a=1"))))))

(deftest get-host-test
  (is (= "example.com" (get-host (mock/request :get "http://example.com"))))
  (testing "port is ignored"
    (is (= "example.com" (get-host (mock/request :get "http://example.com:4000/")))))
  (testing "case is ignored"
    (is (= "example.com" (get-host (mock/request :get "http://eXamPLE.CoM/")))))
  (testing "IPv4 addresses"
    (is (= "127.0.0.1" (get-host (mock/request :get "http://127.0.0.1"))))
    (is (= "127.0.0.1" (get-host (mock/request :get "http://127.0.0.1:8080")))))
  (testing "IPv6 addresses"
    (is (= "[2001:db8:3333:4444:5555:6666:7777:8888]"
           (get-host (mock/request :get "http://[2001:db8:3333:4444:5555:6666:7777:8888]/"))))
    (is (= "[2001:db8:3333:4444:5555:6666:7777:8888]"
           (get-host (mock/request :get "http://[2001:db8:3333:4444:5555:6666:7777:8888]:8080/"))))
    (is (= "[2001:db8::]" (get-host (mock/request :get "http://[2001:db8::]"))))
    (is (= "[2001:db8::]" (get-host (mock/request :get "http://[2001:db8::]:8080"))))
    (testing "dual address"
      (is (= "[2001:db8::123.123.123.123]"
             (get-host (mock/request :get "http://[2001:db8::123.123.123.123]:8080/")))))))
