# peridot [![Build Status](https://secure.travis-ci.org/xeqi/peridot.png)](http://travis-ci.org/xeqi/peridot)

peridot is a testing API for [ring](https://github.com/mmcgrana/ring) apps. It can be used on its own or as a reusable starting point for Web frameworks and testing libraries to build on. Its initial functionality is based on an incomplete port of [Rack::Test](https://github.com/brynary/rack-test)'s test suite.

## Dependency Information

peridot is available from [clojars](http://clojars.org).

### Leiningen
```clojure
[peridot/peridot "0.0.5"]
```

### Maven (requires adding clojars repo):

```xml
<dependency>
  <groupId>peridot</groupId>
  <artifactId>peridot</artifactId>
  <version>0.0.5</version>
</dependency>
```

## Usage

The api namespace is ```peridot.core```.  If you are using peridot in tests you may want to have ```(:use [peridot.core])``` in your ```ns``` declaration.  All examples below assume so.

peridot is designed to be used with clojure.core/->, and maintains cookies across requests in the threading.

### Initialization

You can create an initial state with ```session```.

```clojure
(session ring-app) ;Use your ring app
```

### Navigation

You can use ```request``` to send a request to your ring app.

```clojure
(-> (session ring-app) ;Use your ring app
    (request "/")
    (request "/search" :request-method :post
                       :params {:q "clojure"}))
```

It will use ```:get``` by default.  Options should be from the request map portion of the [ring spec](https://github.com/mmcgrana/ring/blob/master/SPEC).

```:params``` should not be nested. Most params will be sent as ```(str value)```. If a value is a ```java.io.File``` then peridot will send the request as a multipart form using the contents of the file.

peridot will not follow redirects automatically.  To follow a redirect use ```follow-redirect```.  This will throw an ```IllegalArgumentException``` when the last response was not a redirect.

```clojure
(-> (session ring-app) ;Use your ring app
    (request "/login" :request-method :post
                      :params {:username "someone"
                               :password "password"})
    (follow-redirect))
```

### Cookies

peridot will manage cookies through the threading.  This allows you to login and perform actions as that user.

```clojure
(-> (session ring-app) ;Use your ring app
    (request "/login" :request-method :post
                      :params {:username "someone"
                               :password "password"})
    (follow-redirect)
    (request "/tasks")
    (request "/tasks/create" ...)
    (request "/tasks/1")
```

### Persistent Information

It can be useful to set persistent information across requests.

```header``` will set a header.
```authorize``` will use basic authentication.
```content-type``` will set the content-type.

```clojure
(-> (session ring-app) ;Use your ring app
    (header "User-Agent" "Firefox")
    (authorize "bryan" "secret")
    (content-type "application/json")
    (request "/tasks/create" :request-method :put
                             :body some-json))
```

### Querying

The state information returned by each function has ```:request``` and ```:response``` for information from the last interaction with the ring app.

## Transactions and database setup

peridot runs without an http server and, depending on your setup, transactions can be used to rollback and isolate tests.  Some fixtures may be helpful:

```clojure
(use-fixtures :once
              (fn [f]
                (clojure.java.jdbc/with-connection db (f))))
(use-fixtures :each
              (fn [f]
                (clojure.java.jdbc/transaction
                 (clojure.java.jdbc/set-rollback-only)
                 (f))))
```

## Building

[leiningen](https://github.com/technomancy/leiningen) version 2 is used as the build tool.  ```lein2 all test``` will run the test suite against clojure 1.3 and a recent 1.4-beta.

## License

Copyright (C) 2012 Nelson Morris

Distributed under the Eclipse Public License, the same as Clojure.
