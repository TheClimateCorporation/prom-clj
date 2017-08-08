(defproject menoetius-clj "Autogenerated."
  :description "Developer friendly Clojure wrapper for Prometheus custom metrics."
  :url "https://github.com/TheClimateCorporation/menoetius-clj"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [io.prometheus/simpleclient "0.0.23"]
                 [io.prometheus/simpleclient_servlet "0.0.23"]] ; <https://git.tcc.li/projects/GE/repos/ndarray>
  :plugins [[lein-midje "3.2.1"]]
  :profiles {:midje {:dependencies [[midje "1.7.0" :exclusions [org.clojure/clojure]]]
                     :resource-paths
                     ["test-resources"]
                     :plugins
                     [[lein-midje "3.2.1"]]}
             :test {:dependencies
                    [[midje "1.7.0" :exclusions [org.clojure/clojure]]
                     [org.clojure/tools.trace "0.7.8"]]
                    :plugins
                    [[lein-midje "3.2.1"]]}}

  :aliases {"test" ["with-profile" "+test" "midje"]})
