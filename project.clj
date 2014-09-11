(defproject mdr2 "0.1.0-SNAPSHOT"
  :description "A Production Management Tool for DAISY Talking Books"
  :url "https://github.com/sbsdev/mdr2"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [compojure "1.1.9"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [com.github.kyleburton/clj-xpath "1.4.3"]
                 [com.cemerick/friend "0.2.1"]
                 [me.raynes/fs "1.4.6"]
                 [clj-time "0.8.0"]
                 [environ "1.0.0"]
                 [org.immutant/immutant "2.x.incremental.264"]]
  :repositories [["Immutant 2.x incremental builds"
                  "http://downloads.immutant.org/incremental/"]]
  :plugins [[lein-ring "0.8.10"]
            [codox "0.8.9"]
            [lein-environ "1.0.0"]]
  :codox {:project {:name "Madras2"}
          :src-dir-uri "https://github.com/sbsdev/mdr2/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}
          :exclude [immutant.init]}
  :ring {:handler mdr2.handler/app}
  :immutant {:context-path "/"}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-devel "1.3.1"]]}
             :test {:dependencies [[ring-mock "0.1.5"]]}})
