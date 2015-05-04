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

(defn vhost-app [req]
  (condp = (:server-name req)
    "example.com" (app req)
    (response/not-found "not found")))

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
      (request "/" :params (sorted-map "foo" "bar"
                                       "zoo" "car"))
      (doto
          (#(is (= (:query-string (:request %))
                   "foo=bar&zoo=car")
                "request sends params")))
      (request "/" :params (sorted-map :foo "bar"
                                       :zoo "car"))
      (doto
          (#(is (= (:query-string (:request %))
                   "foo=bar&zoo=car")
                "request sends keyword params")))
      (request "/" :params {"list" ["a" "b" "c"]})
      (doto
        (#(is (= (:query-string (:request %))
                 "list=a&list=b&list=c")
              "request sends lists of params")))
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
                "request uses urlencoded content-type for post"))
        (#(is (= (:request-method (:request %))
                 :post)
              "request uses post verb as the :request-method")))
      (request "/" :request-method :post
               :params {"foo" "bar"
                        "zoo" "car"})
      (doto
          (#(is (= (:content-type (:request %))
                   "application/x-www-form-urlencoded")
                "request uses urlencoded content-type"))
        (#(is (= (:request-method (:request %))
                 :post)
              "request uses post verb as the :request-method"))
        (#(is (not (nil? (:body (:request %))))
              "request has a body of content"))
        (#(is (= (slurp (:body (:request %)))
                 "foo=bar&zoo=car")
              "request body reflects the parameters")))
      (request "/" :request-method :post
               :params {"list" ["a" "b" "c"]})
      (doto
        (#(is (= (slurp (:body (:request %)))
                 "list=a&list=b&list=c")
              "request body can send lists of params")))
      (request "/"
               :request-method :post
               :content-type "application/xml"
               :body "<?<?xml version=\"1.0\" encoding=\"UTF-8\"?><root />")
      (doto
          (#(is (= (:content-type (:request %))
                   "application/xml")
           "request does not override the content-type")))
      (request "/"
               :request-method :post
               :content-type "application/edn"
               :body (pr-str {:a {:b 'c}}))
      (doto
        (#(is (= (:content-type (:request %))
                 "application/edn")
              "request does not override the content-type"))
        (#(is (= (read-string (slurp (:body (:request %))))
                 {:a {:b 'c}})
              "request body remains intact")))))

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
                   "Basic YnJ5YW46c2VjcmV0")
                "authorize sets the authorization header")))
      (request "/")
      (doto
          (#(is (= (get (:headers (:request %)) "authorization")
                   "Basic YnJ5YW46c2VjcmV0")
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
        (#(is (= (get (:headers (:request %)) "referer")
                 "http://localhost/redirect")
              "follow redirect should set referrer with official spelling")))
      (request "/redirect" :params {"bar" "foo"})
      (follow-redirect)
      (doto
          (#(is (nil? (:params (:response %)))
                "follow redirect should not keep params")))))

(deftest follow-redirect-vhosts
  (-> (session vhost-app)
      (request "http://example.com/redirect" :params {"bar" "foo"})
      (follow-redirect)
      (doto
        (#(is (= (get-in % [:response :status])
                 200)
              "follow redirect should have kept server-name"))
        (#(is (= (get-in % [:response :body])
                 "You've been redirected")
              "follow redirect should have correct body"))
        (#(is (nil? (get-in % [:response :params]))
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

(deftest body-is-an-inputstream
  (-> (session app)
      (request "/" :body "some string")
      (doto
          (#(is (instance? java.io.InputStream (:body (:request %)))))
        (#(is (= "some string" (slurp (:body (:request %)))))))))
