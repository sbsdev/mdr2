(defproject mdr2 "0.1.0-SNAPSHOT"
  :description "A Production Management Tool for DAISY Talking Books"
  :url "https://github.com/sbsdev/mdr2"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [compojure "1.1.6"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.github.kyleburton/clj-xpath "1.4.3"]
                 [com.cemerick/friend "0.2.1"]
                 [me.raynes/fs "1.4.4"]]
  :plugins [[lein-ring "0.8.10"]
            [codox "0.8.9"]]
  :codox {:project {:name "Madras2"}
          :src-dir-uri "https://github.com/sbsdev/mdr2/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}
          ;; FIXME: we have problems with files that require immutant
          ;; modules as this is only available during run time
          :exclude [immutant.init mdr2.abacus mdr2.messaging]}
  :ring {:handler mdr2.web/app}
  :immutant {:context-path "/"}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
