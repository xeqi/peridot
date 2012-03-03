# peridot

peridot is a testing API for [ring](https://github.com/mmcgrana/ring) apps. It can be used on its own or as a reusable starting point for Web frameworks and testing libraries to build on. Its initial functionality is based on an incomplete port of [Rack::Test](https://github.com/brynary/rack-test)'s test suite.

## Features

* Designed to be used with clojure.core/->
* Maintains a cookie jar across requests
* Set request headers to be used by all subsequent requests

## Dependency Information

peridot is available from [clojars](http://clojars.org).

```clojure
[peridot/peridot "0.0.1"]
```

or to your Maven project's `pom.xml` (requires adding clojars repo):

```xml
<dependency>
  <groupId>peridot</groupId>
  <artifactId>peridot</artifactId>
  <version>0.0.1</version>
</dependency>
```

## Example Usage

```clojure
(use '[peridot.core])

(-> (session ring-app) ;Use your ring app
    (request "/login" :request-method :post
                      :params {"username" "someone"
                               "password" "password"})
    (follow-redirect)
    (dofns
     #(= "Hi someone"
         (:body (:response state))))
    (request "/logout")
    (follow-redirect)
    (dofns
     #(= "Hi unknown person"
         (:body (:response state)))))
  
(-> (session app)
    (header "User-Agent" "Firefox")
    (request "/")
    (dofns
     #(= "Firefox"
         ((:headers (:request %)) "user-agent"))))

(-> (session ring-app)
    (authorize "bryan" "secret")
    (request "/")
    (dofns
     #(= "Basic YnJ5YW46c2VjcmV0\n"
         ((:headers (:request %)) "authorization"))))
```

session, request, headers, authorize, follow-redirect, and dofns are the main api methods.  Each returns a state map with :request and :response being the ring request and response maps that were sent and recieved.

Additional docs to be created later.  See tests until then.

## Building

`lein` version 2 is used as the build tool.

## License

Copyright (C) 2012 Nelson Morris

Distributed under the Eclipse Public License, the same as Clojure.
