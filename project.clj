(defproject mdr2 "0.1.0-SNAPSHOT"
  :description "A Production Management Tool for DAISY Talking Books"
  :url "https://github.com/sbsdev/mdr2"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [compojure "1.2.1"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [mysql/mysql-connector-java "5.1.25"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [clj-http "1.0.1"]
                 [com.cemerick/friend "0.2.1"]
                 [me.raynes/fs "1.4.6"]
                 [com.novemberain/pantomime "2.3.0"]
                 [clj-time "0.8.0"]
                 [environ "1.0.0"]
                 [org.immutant/immutant "2.0.0-alpha2"]
                 [ring/ring-devel "1.3.1"]]
  :repositories [["Immutant 2.x incremental builds"
                  "http://downloads.immutant.org/incremental/"]]
  :plugins [[lein-immutant "2.0.0-alpha2"]
            [lein-ring "0.8.10" :exclusions [org.clojure/clojure]]
            [codox "0.8.9" :exclusions [org.clojure/clojure leinjacker]]
            [lein-environ "1.0.0"]]
  :codox {:project {:name "Madras2"}
          :src-dir-uri "https://github.com/sbsdev/mdr2/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}}
  :ring {:handler mdr2.handler/app}
  :main mdr2.main
  :immutant {:context-path "/"
             :dev? true}
  :profiles {:dev {:dependencies [[org.xerial/sqlite-jdbc "3.8.7"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [ring/ring-devel "1.3.1"]]}
             :test {:dependencies [[ring-mock "0.1.5"]]}})
