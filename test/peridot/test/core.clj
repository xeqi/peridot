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
      (doto
          (#(is (not (nil? (:request %)))
                "request returns session with request"))
        (#(is (= (:request-method (:request %))
                 :get)
              "request uses :get by default"))
        (#(is (= (:status (:response %))
                 200)
              "request returns session with response")))
      (request "/" :params {"foo" "bar"
                            "zoo" "car"})
      (doto
          (#(is (= (:query-string (:request %))
                   "foo=bar&zoo=car")
                "request sends params")))
      (request "/" :params {:foo "bar"
                            :zoo "car"})
      (doto
          (#(is (= (:query-string (:request %))
                   "foo=bar&zoo=car")
                "request sends keyword params")))
      (request "/redirect")
      (doto
          (#(is (= (:status (:response %)) 302)
                "request does not follow redirects by default")))))

(deftest request-posts
  (-> (session app)
      (request "/" :request-method :post)
      (doto
          (#(is (= (:content-type (:request %))
                   "application/x-www-form-urlencoded")
                 "request uses urlencoded content-type for post")))
      (request "/"
               :request-method :post
               :content-type "application/xml")
      (doto
          (#(is (= (:content-type (:request %))
                   "application/xml")
           "request does not override the content-type")))))

(deftest request-https
  (-> (session app)
      (request "https://www.example.org")
      (doto
          (#(is (= (:scheme (:request %)) :https)
               "request should set https scheme"))
        (#(is (= (:server-port (:request %)) 443)
             "request should set https port")))))

(deftest header-generic
  (-> (session app)
      (header "User-Agent" "Firefox")
      (request "/")
      (doto
          (#(is (= (get (:headers (:request %)) "user-agent")
                   "Firefox")
                "header sets for future requests")))
      (request "/")
      (doto
          (#(is (= (get (:headers (:request %)) "user-agent")
                   "Firefox")
                "header persists across requests")))
      (request "/" :headers {"User-Agent" "Safari"})
      (doto
          (#(is (= (get (:headers (:request %)) "user-agent")
                   "Safari")
                "header is overwritten by the request")))
      (header "User-Agent" "Opera")
      (request "/")
      (doto
          (#(is (= (get (:headers (:request %)) "user-agent")
                   "Opera")
                "header is overwritten by later calls")))
      (header "User-Agent" nil)
      (request "/")
      (doto
          (#(is (nil? (get (:headers (:request %)) "user-agent"))
           "header can clear a value")))))

(deftest authorize-generic
  (-> (session app)
      (authorize "bryan" "secret")
      (request "/")
      (doto
          (#(is (= (get (:headers (:request %)) "authorization")
                   "Basic YnJ5YW46c2VjcmV0\n")
                "authorize sets the authorization header")))
      (request "/")
      (doto
          (#(is (= (get (:headers (:request %)) "authorization")
                   "Basic YnJ5YW46c2VjcmV0\n")
           "authorize persists the header across requests")))))

(deftest follow-redirect-generic
  (-> (session app)
      (request "/redirect")
      (follow-redirect)
      (doto
          (#(is (= (:status (:response %))
                   200)
                "follow redirect should follow"))
        (#(is (= (:body (:response %))
                 "You've been redirected")
              "follow redirect should have correct body"))
        (#(is (= (get (:headers (:request %)) "referrer")
                 "http://localhost/redirect")
              "follow redirect should set referrer")))
      (request "/redirect" :params {"bar" "foo"})
      (follow-redirect)
      (doto
          (#(is (nil? (:params (:response %)))
                "follow redirect should not keep params")))))

(deftest follow-redirect-errors
  (is (thrown-with-msg? IllegalArgumentException
        #"Previous response was not a redirect"
        (-> (session app)
            (request "/")
            (follow-redirect))))
  (is (thrown-with-msg? IllegalArgumentException
        #"Previous response was not a redirect"
        (-> (session app)
            (follow-redirect)))))

(deftest setting-content-type
  (-> (session app)
      (content-type "application/json")
      (request "/")
      (doto
          (#(is (= (:content-type (:request %)) "application/json")
                "content-type sets for future requests")))
      (request "/")
      (doto
          (#(is (= (:content-type (:request %)) "application/json")
           "content-type persists across requests")))
      (request "/" :content-type "application/xml")
      (doto
          (#(is (= (:content-type (:request %)) "application/xml")
           "content-type is overwritten by the request")))
      (content-type "text/plain")
      (request "/")
      (doto
          (#(is (= (:content-type (:request %)) "text/plain")
           "content-type is overwritten by later calls")))
      (content-type nil)
      (request "/")
      (doto
          (#(is (nil? (:content-type (:request %)))
           "content-type can clear a value")))))