(defproject mdr2 "0.7.0"
  :description "A Production Management Tool for DAISY Talking Books"
  :url "https://github.com/sbsdev/mdr2"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.codec "0.1.0"] ; for pipeline2 client
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.2"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.immutant/caching "2.1.5"]
                 [org.immutant/messaging "2.1.5"]
                 [org.immutant/transactions "2.1.5"]
                 [org.immutant/web "2.1.5"]
                 [org.tobereplaced/nio.file "0.4.0"]
                 [org.xerial/sqlite-jdbc "3.14.2.1"] ; just for testing
                 [clj-http "3.3.0"] ; for pipeline2 client
                 [clj-time "0.12.0"]
                 [com.cemerick/friend "0.2.3"]
                 [com.novemberain/pantomime "2.8.0"]
                 [com.thaiopensource/jing "20091111" :exclusions [xml-apis]]
                 [compojure "1.5.1"]
                 [crypto-random "1.2.0"] ; for pipeline2 client
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [me.raynes/fs "1.4.6" :exclusions [org.apache.commons/commons-compress]]
                 [mysql/mysql-connector-java "6.0.4"]
                 [pandect "0.6.0"] ; for pipeline2 client
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-core "1.5.0" :exclusions [org.clojure/tools.reader]]
                 [ring/ring-devel "1.5.0"]
                 [yesql "0.5.3"]
                 ]
  :plugins [[lein-immutant "2.1.0"]
            [codox "0.10.0"]
            [lein-environ "1.1.0"]
            [cider/cider-nrepl "0.13.0"]
            [org.clojars.cvillecsteele/lein-git-version "1.0.3"]]
  :codox {:project {:name "Madras2"}
          :src-dir-uri "https://github.com/sbsdev/mdr2/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}}
  :main ^:skip-aot mdr2.main
  :immutant {:war {:context-path "/"
                   :name "%p%v%t"
                   :nrepl {:port 42278
                           :start? true}}}
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["src" "dev"]
                   :dependencies [[org.xerial/sqlite-jdbc "3.14.2.1"]
                                  [javax.servlet/servlet-api "2.5"]]}
             :test {:dependencies [[ring-mock "0.1.5"]]
                    :env {:production-path "test/testfiles"
                          :archive-spool-dir "/tmp/mdr2/archive"
                          :archive-periodical-spool-dir "/tmp/mdr2/periodical-archive"
                          :archive-other-spool-dir "/tmp/mdr2/other-archive"}}})
