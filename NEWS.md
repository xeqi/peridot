## Unreleased

* Search headers case insensitively. [#40](https://github.com/xeqi/peridot/pull/40)
* Drop support for Clojure 1.3 and 1.4. It appears that there was already a latent problem with Java 8 and 1.3/1.4, but it wasn't detected because CI was running against Java 7. [#41](https://github.com/xeqi/peridot/pull/41)
* Parse dates independently from system locale, forcing parsing in the US locale. [#38](https://github.com/xeqi/peridot/pull/38)
* Bump dependency versions. [#36](https://github.com/xeqi/peridot/pull/36)
* Make per-request content type settings transient. [#34](https://github.com/xeqi/peridot/pull/34)

## 0.4.4 / 2016-06-05

* Fix compiler warnings with typehints.

## 0.2.1 / 2013-5-29

* Support lists in param maps (Glen Mailer, through work done in ring-mock)

## 0.2.0 / 2013-4-4

* Use RFC822 or RFC850 for cookie expiration parsing (Glen Mailer)
* Don't remove http-only cookines on https (Glen Mailer)

## 0.1.0 / 2013-2-19

* Use server-port per ring spec (Travis Vachon)

## 0.0.8 / 2013-1-16

* Use strings in headers for multipart requests

## 0.0.7 / 2013-1-12

* Coerce :body to be an input stream
* Remove newline from authencation helpder

## 0.0.6 / 2012-8-10

* Send all cookie values not explicitly deleted (Gijs Stuurman)
* Send :post parameters in body (Alex Redington)
