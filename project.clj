(defproject peridot "0.5.4"
  :description "Interact with ring apps"
  :url "https://github.com/xeqi/peridot"
  :min-lein-version "2.0.0"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;;Use clojure 1.3 for pom generation
                 [org.clojure/clojure "1.5.1"]
                 [ring/ring-mock "0.4.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.apache.httpcomponents/httpmime "4.5.1"
                  :exclusions [commons-logging]]
                 [org.apache.httpcomponents/httpcore "4.4.5"]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :deploy-repositories [["releases"  {:sign-releases false :url "https://repo.clojars.org"}]
                        ["snapshots" {:sign-releases false :url "https://repo.clojars.org"}]]

  :profiles {:dev {:dependencies   [[net.cgrand/moustache "1.1.0"
                                     :exclusions
                                     [org.clojure/clojure
                                      ring/ring-core]]
                                    [clj-time "0.12.0"]
                                    [ring/ring-core "1.9.5" :exclusions [ring/ring-codec commons-codec]]
                                    [javax.servlet/servlet-api "2.5"]
                                    ;; use 1.8 for development
                                    ^:replace [org.clojure/clojure "1.8.0"]]
                   :resource-paths ["test-resources"]}
             ;; use the relevant clojure version for testing
             :1.5 {:dependencies [^:replace [org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [^:replace [org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [^:replace [org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [^:replace [org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [^:replace [org.clojure/clojure "1.9.0"]]}}
  :aliases {"all" ["with-profile" "+1.5:+1.6:+1.7:+1.8:+1.9"]})
