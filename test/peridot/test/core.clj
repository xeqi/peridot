(ns peridot.test.core
  (:use [peridot.core]
        [clojure.test])
  (:require [net.cgrand.moustache :as moustache]
            [ring.util.response :as response]))

(def app
  (moustache/app [""] {:get ""}
                 ["redirect"] {:get (constantly
                                     (response/redirect "/redirected"))}
                 ["redirected"] {:get "You've been redirected"}))

(deftest request-generic
  (-> (session app)
      (request "/")
      (dofns
       #(is (:request %)
            "request returns session with :request")
       #(is (= 200
               (:status (:response %)))
            "request returns session with :response")
       #(is (= :get
               (:request-method (:request %)))
            "request uses :get by default"))
      (request "/" :params {"foo" "bar"
                            "zoo" "car"})
      (dofns
       #(is (= (:query-string (:request %))
               "foo=bar&zoo=car")
            "request sends params"))
      (request "/redirect")
      (dofns
       #(is (= 302
               (:status (:response %)))
            "request does not follow redirects by default"))))

(deftest request-posts
  (-> (session app)
      (request "/" :request-method :post)
      (dofns
       #(is (= "application/x-www-form-urlencoded"
               (:content-type (:request %)))
            "request uses urlencoded content-type for post"))
      (request "/"
               :request-method :post
               :content-type "application/xml")
      (dofns
       #(is (= "application/xml"
               (:content-type (:request %)))
            "request does not override the content-type"))))

(deftest request-https
  (-> (session app)
      (request "https://www.example.org")
      (dofns
       #(is (= :https
               (:scheme (:request %)))
            "request should set https scheme")
       #(is (= 443
               (:server-port (:request %)))
            "request should set https port"))))

(deftest header-generic
  (-> (session app)
      (header "User-Agent" "Firefox")
      (request "/")
      (dofns
       #(is (= "Firefox"
               ((:headers (:request %)) "user-agent"))
            "header sets for future requests"))
      (request "/")
      (dofns
       #(is (= "Firefox"
               ((:headers (:request %)) "user-agent"))
            "header persists across requests"))
      (request "/" :headers {"User-Agent" "Safari"})
      (dofns
       #(is (= "Safari"
               ((:headers (:request %)) "user-agent"))
            "header is overwritten by the request"))
      (header "User-Agent" "Safari")
      (request "/")
      (dofns
       #(is (= "Safari"
               ((:headers (:request %)) "user-agent"))
            "header is overwritten by later calls"))
      (header "User-Agent" nil)
      (request "/")
      (dofns
       #(is (= nil
               ((:headers (:request %)) "user-agent"))
            "header can clear a value"))))

(deftest authorize-generic
  (-> (session app)
      (authorize "bryan" "secret")
      (request "/")
      (dofns
       #(is (= "Basic YnJ5YW46c2VjcmV0\n"
               ((:headers (:request %)) "authorization"))
            "authorize sets the authorization header"))
      (request "/")
      (dofns
       #(is (= "Basic YnJ5YW46c2VjcmV0\n"
               ((:headers (:request %)) "authorization"))
            "authorize persists the header across requests"))))

(deftest follow-redirect-generic
  (-> (session app)
      (request "/redirect")
      (follow-redirect)
      (dofns
       #(is (= 200
               (:status (:response %)))
            "follow redirect should follow")
       #(is (= "You've been redirected"
               (:body (:response %)))
            "follow redirect should have correct body")
       #(is (= "http://localhost/redirect"
               ((:headers (:request %)) "referrer"))
            "follow redirect should set referrer"))
      (request "/redirect" :params {"bar" "foo"})
      (follow-redirect)
      (dofns
       #(is (= nil (:params (:request %)))
        "follow redirect should not keep params"))))

(deftest follow-redirect-errors
  (is (thrown-with-msg? Exception #"Previous response was not a redirect"
        (-> (session app)
            (request "/")
            (follow-redirect))))
  (is (thrown-with-msg? Exception #"Previous response was not a redirect"
        (-> (session app)
            (follow-redirect)))))

(deftest setting-content-type
  (-> (session app)
      (content-type "application/json")
      (request "/")
      (dofns
       #(is (= "application/json"
               (:content-type (:request %)))
            "content-type sets for future requests"))
      (request "/")
      (dofns
       #(is (= "application/json"
               (:content-type (:request %)))
            "content-type persists across requests"))
      (request "/" :content-type "application/xml")
      (dofns
       #(is (= "application/xml"
               (:content-type (:request %)))
            "content-type is overwritten by the request"))
      (content-type "text/plain")
      (request "/")
      (dofns
       #(is (= "text/plain"
               (:content-type (:request %)))
            "content-type is overwritten by later calls"))
      (content-type nil)
      (request "/")
      (dofns
       #(is (= nil
               (:content-type (:request %)))
            "content-type can clear a value"))))