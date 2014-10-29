(defproject ewen/github-stats "0.1.0"
            :description "Github stats"
            :url "https://github.com/EwenG/github-stats"
            :license {:name "Eclipse Public License"
                      :url "http://www.eclipse.org/legal/epl-v10.html"}
            :min-lein-version "2.0.0"
            :source-paths ["src" "src-cljs"]
            :resource-paths ["resources/main"]
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [org.clojure/clojurescript "0.0-2371"]
                           [sablono "0.2.22"]
                           [domina "1.0.2"]
                           [datascript "0.4.1"]
                           [javax.servlet/javax.servlet-api "3.1.0" :scope "provided"]
                           [io.pedestal/pedestal.service "0.3.0"]
                           [io.pedestal/pedestal.service-tools "0.3.0"]
                           [org.immutant/web "2.0.0-alpha2"]
                           [enlive "1.1.5"]
                           [ring/ring-core "1.3.0" :exclusions [[org.clojure/clojure]
                                                                [org.clojure/tools.reader]
                                                                [srypto-random]
                                                                [crypto-equality]]]
                           [org.clojure/core.match "0.2.1"]]
            :dev-dependencies [[lein-cljsbuild "1.0.3"]]
            :plugins [[lein-immutant "2.0.0-alpha2"]
                      [com.cemerick/austin "0.1.5"]]
            :profiles {:dev {:plugins [[lein-cljsbuild "1.0.3"]]
                             :resource-paths ["resources/dev"]}
                       :uberjar {:resource-paths ["resources/prod"]
                                 :aot :all}}
            :cljsbuild {:builds [{:id "dev"
                                  :source-paths ["src-cljs"]
                                  :compiler {:output-to "resources/dev/public/cljs/github-stats.js"
                                             :output-dir "resources/dev/public/cljs/"
                                             :optimizations :none
                                             :source-map true}}
                                 {:id "prod"
                                  :source-paths ["src-cljs"]
                                  :compiler {
                                             :output-to "resources/prod/public/cljs/github-stats.js"
                                             :optimizations :advanced
                                             :pretty-print false
                                             :externs ["resources/main/public/js/react-externs.js"
                                                       "resources/main/public/js/c3-externs.js"]}}]}
            :jvm-opts ["-Xss1G"]
            :repl-options {;; If nREPL takes too long to load it may timeout,
                           ;; increase this to wait longer before timing out.
                           ;; Defaults to 30000 (30 seconds)
                           :timeout 60000}
            :main ewen.github-stats.server)