(defproject ch.sbs/mdr2 "0.9.0-alpha6"

  :description "A Production Management Tool for DAISY Talking Books"
  :url "https://github.com/sbsdev/mdr2"

  :dependencies [[buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-core "1.10.413"]
                 [buddy/buddy-hashers "1.8.158"]
                 [buddy/buddy-sign "3.4.333"]
                 [ch.qos.logback/logback-classic "1.4.3"]
                 [clj-commons/iapetos "0.1.13" :exclusions [io.prometheus/simpleclient]]
                 [cljs-ajax "0.8.4"]
                 [clojure.java-time "0.3.3"]
                 [com.cognitect/transit-clj "1.0.329"]
                 [com.cognitect/transit-cljs "0.8.280"]
                 [com.google.javascript/closure-compiler-unshaded "v20220502"]
                 [com.google.protobuf/protobuf-java "3.21.5"]
                 [conman "0.9.4"]
                 [cprop "0.1.19"]
                 [day8.re-frame/http-fx "0.2.4"]
                 [expound "0.9.0"]
                 [fork "2.4.3"] ;; Form Library for re-frame
                 [funcool/struct "1.4.0"]
                 [io.prometheus/simpleclient_hotspot "0.16.0"]
                 [json-html "0.4.7"]
                 [luminus-migrations "0.7.2"]
                 [luminus-transit "0.1.5"]
                 [luminus-undertow "0.1.14"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [markdown-clj "1.11.1"]
                 [medley "1.4.0"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.18"]
                 [metosin/ring-http-response "0.9.3"]
                 [mount "0.1.16"]
                 [mysql/mysql-connector-java "8.0.30"]
                 [nrepl "0.9.0"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.51" :scope "provided"]
                 [org.clojure/core.async "1.5.648"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.webjars.npm/bulma "0.9.4"]
                 [org.webjars.npm/creativebulma__bulma-tooltip "1.0.2"]
                 [org.webjars.npm/material-icons "1.10.8"]
                 [org.webjars/webjars-locator "0.45"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [re-frame "1.2.0"]
                 [reagent "1.1.1"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.9.5"]
                 [ring/ring-defaults "0.3.3"]
                 [selmer "1.12.53"]
                 [thheller/shadow-cljs "2.19.0" :scope "provided"]
                 [toyokumo/tarayo "0.2.5"]
                 [com.thaiopensource/jing "20091111" :exclusions [xml-apis]]
                 [com.taoensso/tempura "1.3.0"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [babashka/fs "0.1.11"]
                 [pandect "1.0.2"]
                 [org.clojure/data.zip "1.0.0"]
                 [com.novemberain/pantomime "2.11.0" :exclusions [com.google.guava/guava]]
                 [clj-http "3.12.3"]
                 [org.clojure/data.csv "1.0.1"]
                 [org.clojars.pntblnk/clj-ldap "0.0.17"]
                 ;; the next two deps are needed for the logback SMTPAppender (so we can send log entries by mail)
                 [com.sun.mail/jakarta.mail "2.0.1"]
                 [com.sun.activation/jakarta.activation "2.0.1"]
                 [trptcolin/versioneer "0.2.0"]] ;; extract version information from the jar

  :min-lein-version "2.0.0"
  
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot mdr2.core

  :plugins [[lein-kibit "0.1.2"]
            [lein-codox "0.10.8"]]

  :codox {:project {:name "Madras2"}
          :source-paths ["src"]
          :source-uri "https://github.com/sbsdev/mdr2/blob/v{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  :clean-targets ^{:protect false}
  [:target-path "target/cljsbuild"]
  
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  #_["deploy"]
                  #_["uberjar"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :profiles
  {:uberjar {:omit-source true
             
             :prep-tasks ["compile" ["run" "-m" "shadow.cljs.devtools.cli" "release" "app"]]
             :aot :all
             :uberjar-name "mdr2.jar"
             :source-paths ["env/prod/clj"  "env/prod/cljs" ]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn" ]
                  :dependencies [[binaryage/devtools "1.0.6"]
                                 [cider/piggieback "0.5.3"]
                                 [org.clojure/tools.namespace "1.3.0"]
                                 [pjstadig/humane-test-output "0.11.0"]
                                 [prone "2021-04-23"]
                                 [re-frisk "1.6.0"]
                                 [ring/ring-devel "1.9.5"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                 [jonase/eastwood "0.3.5"]
                                 [cider/cider-nrepl "0.28.5"]]
                  
                  
                  :source-paths ["env/dev/clj"  "env/dev/cljs" "test/cljs" ]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :timeout 120000}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn" ]
                  :resource-paths ["env/test/resources"] 
                  
                  
                  }
   :profiles/dev {}
   :profiles/test {}})
