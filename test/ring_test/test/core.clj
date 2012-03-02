(ns ring-test.test.core
  (:use [ring-test]
        [clojure.test])
  (:require [net.cgrand.moustache :as moustache]
            [ring.util.response :as response]))

(def app
  (moustache/app [""] {:get ""}
                 ["redirect"] {:get (constantly
                                     (response/redirect "/redirected"))}
                 ["redirected"] {:get "You've been redirected"}))

(deftest request-generic
  (let [session (session app)]
    (let [state (-> session
                    (request "/"))]
      (is (:request state)
          "request returns session with :request"))
    (let [state (-> session
                    (request "/"))]
      (is (= 200
             (:status (:response state)))
          "request returns session with :response"))
    (let [state (-> session
                    (request "/"))]
      (is (= :get
             (:request-method (:request state)))
          "request uses :get by default"))
    (let [state (-> session
                    (request "/" :params {"foo" "bar"
                                          "zoo" "car"}))]
      (is (= (:query-string (:request state))
             "foo=bar&zoo=car")
          "request sends params"))
    (let [state (-> session
                    (request "/redirect"))]
      (is (= 302
             (:status (:response state)))
          "request does not follow redirects by default"))))

(deftest request-posts
  (let [state (-> (session app)
                  (request "/" :request-method :post))]
    (is (= "application/x-www-form-urlencoded"
           (:content-type (:request state)))
        "request uses urlencoded content-type for post"))
  (let [state (-> (session app)
                  (request "/"
                           :request-method :post
                           :content-type "application/xml"))]
    (is (= "application/xml"
           (:content-type (:request state)))
        "request does not override the content-type")))

(deftest request-https
  (let [state (-> (session app)
                  (request "https://www.example.org"))]
    (is (= :https
           (:scheme (:request state)))
        "request should set https scheme")
    (is (= 443
           (:server-port (:request state)))
        "request should set https port")))


(deftest header-generic
  (let [state (-> (session app)
                  (header "User-Agent" "Firefox")
                  (request "/"))]
    (is (= "Firefox"
           ((:headers (:request state)) "user-agent"))
        "header sets for future requests"))
  (let [state (-> (session app)
                  (header "User-Agent" "Firefox")
                  (request "/")
                  (request "/"))]
    (is (= "Firefox"
           ((:headers (:request state)) "user-agent"))
        "header persists across requests"))
  (let [state (-> (session app)
                  (header "User-Agent" "Firefox")
                  (header "User-Agent" "Safari")
                  (request "/"))]
    (is (= "Safari"
           ((:headers (:request state)) "user-agent"))
        "header is overwritten by later calls"))
  (let [state (-> (session app)
                  (header "User-Agent" "Firefox")
                  (header "User-Agent" nil)
                  (request "/"))]
    (is (= nil
           ((:headers (:request state)) "user-agent"))
        "header can clear a value"))
  (let [state (-> (session app)
                  (header "User-Agent" "Firefox")
                  (request "/" :headers {"User-Agent" "Safari"}))]
    (is (= "Safari"
           ((:headers (:request state)) "user-agent"))
        "header is overwritten by the request")))

(deftest authorize-generic
  (let [state (-> (session app)
                  (authorize "bryan" "secret")
                  (request "/"))]
    (is (= "Basic YnJ5YW46c2VjcmV0\n"
           ((:headers (:request state)) "authorization"))
        "authorize sets the authorization header"))
    (let [state (-> (session app)
                    (authorize "bryan" "secret")
                    (request "/")
                    (request "/"))]
    (is (= "Basic YnJ5YW46c2VjcmV0\n"
           ((:headers (:request state)) "authorization"))
        "authorize persists the header across requests")))

(deftest follow-redirect-generic
  (let [state (-> (session app)
                  (request "/redirect")
                  (follow-redirect))]
    (is (= 200
           (:status (:response state)))
        "follow redirect should follow")
    (is (= "You've been redirected"
           (:body (:response state)))
        "follow redirect should have correct body")
    (is (= "http://localhost/redirect"
           ((:headers (:request state)) "referrer"))
        "follow redirect should set referrer"))
  (let [state (-> (session app)
                  (request "/redirect" :params {"bar" "foo"})
                  (follow-redirect))]

    (is (= nil (:params (:request state)))
        "follow redirect should not keep params")))

(deftest follow-redirect-errors
  (is (thrown-with-msg? Exception #"Previous response was not a redirect"
        (-> (session app)
            (request "/")
            (follow-redirect))))
  (is (thrown-with-msg? Exception #"Previous response was not a redirect"
        (-> (session app)
            (follow-redirect)))))