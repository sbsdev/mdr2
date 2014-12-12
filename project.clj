(defproject mdr2 "0.1.0-SNAPSHOT"
  :description "A Production Management Tool for DAISY Talking Books"
  :url "https://github.com/sbsdev/mdr2"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [compojure "1.2.2"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [mysql/mysql-connector-java "5.1.34"]
                 [yesql "0.5.0-beta2"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.thaiopensource/jing "20091111"]
                 [pandect "0.4.1"] ; for pipeline2 client
                 [org.clojure/data.codec "0.1.0"] ; for pipeline2 client
                 [crypto-random "1.2.0"] ; for pipeline2 client
                 [clj-http "1.0.1"] ; for pipeline2 client
                 [ring/ring-defaults "0.1.2"]
                 [com.cemerick/friend "0.2.1"]
                 [me.raynes/fs "1.4.6"] ; to be replaced by nio.file
                 [org.tobereplaced/nio.file "0.3.0"]
                 [org.clojure/data.csv "0.1.2"]
                 [com.novemberain/pantomime "2.3.0"]
                 [clj-time "0.8.0"]
                 [environ "1.0.0"]
                 [org.immutant/immutant "2.0.0-beta1"]
                 [ring/ring-devel "1.3.2"]
                 [org.xerial/sqlite-jdbc "3.8.7"] ; just for testing
                 ]
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
  :immutant {:war {:context-path "/"}}
  :profiles {:dev {:dependencies [[org.xerial/sqlite-jdbc "3.8.7"]
                                  [javax.servlet/servlet-api "2.5"]]}
             :test {:dependencies [[ring-mock "0.1.5"]]}})
