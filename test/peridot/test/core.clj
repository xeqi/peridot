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
      (has (inf [:request] #(not (nil? %)))
           "request returns session with request")
      (has (in [:request :request-method] :get)
           "request uses :get by default")
      (has (in [:response :status] 200)
           "request returns session with response")
      (request "/" :params {"foo" "bar"
                            "zoo" "car"})
      (has (in [:request :query-string] "foo=bar&zoo=car")
           "request sends params")
      (request "/redirect")
      (has (in [:response :status] 302)
           "request does not follow redirects by default")))

(deftest request-posts
  (-> (session app)
      (request "/" :request-method :post)
      (has (in [:request :content-type] "application/x-www-form-urlencoded")
           "request uses urlencoded content-type for post")
      (request "/"
               :request-method :post
               :content-type "application/xml")
      (has (in [:request :content-type] "application/xml")
           "request does not override the content-type")))

(deftest request-https
  (-> (session app)
      (request "https://www.example.org")
      (has (in [:request :scheme] :https)
           "request should set https scheme")
      (has (in [:request :server-port] 443)
           "request should set https port")))

(deftest header-generic
  (-> (session app)
      (header "User-Agent" "Firefox")
      (request "/")
      (has (in [:request :headers "user-agent"] "Firefox")
           "header sets for future requests")
      (request "/")
      (has (in [:request :headers "user-agent"] "Firefox")
           "header persists across requests")
      (request "/" :headers {"User-Agent" "Safari"})
      (has (in [:request :headers "user-agent"] "Safari")
           "header is overwritten by the request")
      (header "User-Agent" "Opera")
      (request "/")
      (has (in [:request :headers "user-agent"] "Opera")
           "header is overwritten by later calls")
      (header "User-Agent" nil)
      (request "/")
      (has (in [:request :headers "user-agent"] nil)
           "header can clear a value")))

(deftest authorize-generic
  (-> (session app)
      (authorize "bryan" "secret")
      (request "/")
      (has (in [:request :headers "authorization"] "Basic YnJ5YW46c2VjcmV0\n")
           "authorize sets the authorization header")
      (request "/")
      (has (in [:request :headers "authorization"] "Basic YnJ5YW46c2VjcmV0\n")
           "authorize persists the header across requests")))

(deftest follow-redirect-generic
  (-> (session app)
      (request "/redirect")
      (follow-redirect)
      (has (in [:response :status] 200)
           "follow redirect should follow")
      (has (in [:response :body] "You've been redirected")
           "follow redirect should have correct body")
      (has (in [:request :headers "referrer"] "http://localhost/redirect")
           "follow redirect should set referrer")
      (request "/redirect" :params {"bar" "foo"})
      (follow-redirect)
      (has (in [:response :params] nil)
           "follow redirect should not keep params")))

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
      (has (in [:request :content-type] "application/json")
           "content-type sets for future requests")
      (request "/")
      (has (in [:request :content-type] "application/json")
           "content-type persists across requests")
      (request "/" :content-type "application/xml")
      (has (in [:request :content-type] "application/xml")
           "content-type is overwritten by the request")
      (content-type "text/plain")
      (request "/")
      (has (in [:request :content-type] "text/plain")
           "content-type is overwritten by later calls")
      (content-type nil)
      (request "/")
      (has (in [:request :content-type] nil)
           "content-type can clear a value")))

(deftest test-additions
  (is (in {:k 2} [:k] 2))
  (is (inf {:k 2} [:k] #(= 2 %))))