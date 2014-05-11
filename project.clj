(defproject peridot "0.2.3-SNAPSHOT"
  :description "Interact with ring apps"
  :url "https://github.com/xeqi/peridot"
  :min-lein-version "2.0.0"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [ring-mock "0.1.5"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.apache.httpcomponents/httpmime "4.1.3"
                  :exclusions [commons-logging]]
                 [clj-time "0.5.1"]]
  :profiles {:test {:dependencies [[net.cgrand/moustache "1.1.0"
                                    :exclusions
                                    [[org.clojure/clojure]
                                     [ring/ring-core]]]
                                   [ring/ring-core "1.1.8"]]
                    :resource-paths ["test-resources"]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}}
  :aliases {"all" ["with-profile" "test:test,1.4:test,1.5"]})
