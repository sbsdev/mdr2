(defproject mdr2 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [compojure "1.1.6"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.github.kyleburton/clj-xpath "1.4.3"]]
  :plugins [[lein-ring "0.8.10"]]
  :ring {:handler mdr2.web/app}
  :immutant {:context-path "/"}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
