(defproject mdr2 "0.3.0-SNAPSHOT"
  :description "A Production Management Tool for DAISY Talking Books"
  :url "https://github.com/sbsdev/mdr2"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.codec "0.1.0"] ; for pipeline2 client
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.immutant/web "2.0.0"]
                 [org.immutant/messaging "2.0.0"]
                 [org.immutant/transactions "2.0.0"]
                 [org.tobereplaced/nio.file "0.4.0"]
                 [org.xerial/sqlite-jdbc "3.8.7"] ; just for testing
                 [clj-http "1.1.0"] ; for pipeline2 client
                 [clj-time "0.9.0"]
                 [com.cemerick/friend "0.2.1"]
                 [com.novemberain/pantomime "2.4.0"]
                 [com.thaiopensource/jing "20091111"]
                 [compojure "1.3.2"]
                 [crypto-random "1.2.0"] ; for pipeline2 client
                 [environ "1.0.0"]
                 [hiccup "1.0.5"]
                 [me.raynes/fs "1.4.6"]
                 [mysql/mysql-connector-java "5.1.35"]
                 [pandect "0.5.1"] ; for pipeline2 client
                 [ring/ring-defaults "0.1.4"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-devel "1.3.2"]
                 [yesql "0.5.0-rc2"]
                 ]
  :plugins [[lein-immutant "2.0.0"]
            [codox "0.8.11"]
            [lein-environ "1.0.0"]
            [cider/cider-nrepl "0.8.2"]]
  :codox {:project {:name "Madras2"}
          :src-dir-uri "https://github.com/sbsdev/mdr2/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}}
  :main mdr2.main
  :immutant {:war {:context-path "/"}}
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.xerial/sqlite-jdbc "3.8.7"]
                                  [javax.servlet/servlet-api "2.5"]]}
             :test {:dependencies [[ring-mock "0.1.5"]]
                    :env {:production-path "test/testfiles"
                          :archive-spool-dir "/tmp/mdr2/archive"
                          :archive-periodical-spool-dir "/tmp/mdr2/periodical-archive"
                          :archive-other-spool-dir "/tmp/mdr2/other-archive"}}})
