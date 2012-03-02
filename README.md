# ring-test

ring-test is a small, simple testing API for [ring](https://github.com/mmcgrana/ring) apps. It can be used on its own or as a reusable starting point for Web frameworks and testing libraries to build on. Its initial functionality is based on a port of [Rack::Test](https://github.com/brynary/rack-test)'s test suite.

## Features

* Designed to be used with clojure.core/->
* Maintains a cookie jar across requests
* Set request headers to be used by all subsequent requests

## Releases and Dependency Information

To be added once a release has been built.

## Example Usage

```clojure
(use '[ring-test])

(let [state (-> (session ring-app) ;Use your ring app
                (request "/login" :request-method :post
                                  :params {"username" "someone"
                                           "password" "password"})
                (follow-redirect))]
  (= "Hi someone"
     (:body (:response state)))

(let [state (-> (session (constantly {})
                (headers "User-Agent" "FireFox")
                (request "/"))]
  (= "Firefox"  
     ((:headers (:request state)) "user-agent"))


(let [state (-> (session (constantly {})
                (authorize "bryan" "secret")
                (request "/"))]
  (= "Basic YnJ5YW46c2VjcmV0\n"
     ((:headers (:request state)) "authorization")))
```

session, request, headers, authorize, and follow-redirect are the main api methods.  Each returns a state map with :request and :response being the ring request and response maps that were sent/recieved.

Additional docs to be created later.  See tests until then.

## License

Copyright (C) 2012 Nelson Morris

Distributed under the Eclipse Public License, the same as Clojure.
