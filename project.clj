(defproject frontend "0.1.0-SNAPSHOT"
  :description "CircleCI's frontend app"
  :url "https://circleci.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [inflections "0.8.2"]

                 [org.clojars.dwwoelfel/stefon "0.5.0-3198d1b33637d6bd79c7415b01cff843891ebfd4"
                  :exclusions [com.google.javascript/closure-compiler]]
                 [compojure "1.1.8"]
                 [ring/ring "1.2.2"]
                 [http-kit "2.1.18"]
                 [circleci/clj-yaml "0.5.2"]
                 [fs "0.11.1"]
                 [com.cemerick/url "0.1.1"]
                 [cheshire "5.3.1"]

                 ;; Prerelease version to avoid conflict with cljs.core/record?
                 ;; https://github.com/noprompt/ankha/commit/64423e04bf05459f96404ff087740bce1c9f9d37
                 [ankha "0.1.5.1-64423e"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [cljs-ajax "0.3.13"]
                 [cljsjs/react-with-addons "0.13.3-0"]
                 [cljsjs/c3 "0.4.10-0"]
                 [org.omcljs/om "0.9.0"]
                 [hiccups "0.3.0"]
                 [sablono "0.3.6"]
                 [secretary "1.2.2"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]

                 ;; Frontend tests
                 [com.cemerick/clojurescript.test "0.3.0"]
                 [org.clojure/tools.reader "0.9.2"]

                 ;; For Dirac DevTools
                 [environ "1.0.1"]]

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-2"]
            [cider/cider-nrepl "0.10.2"]
            [lein-environ "1.0.1"]]

  ;; Don't include these dependencies transitively. These are foundational
  ;; dependencies that lots of our direct dependencies depend on. We want to
  ;; make sure we get the version *we* asked for, not the version one of *them*
  ;; asked for (which means we're taking responsibility for the versions working
  ;; together). If Maven had useful version ranges like Bundler or npm, we could
  ;; let it take care of resolving the versions for us, but Maven's version
  ;; ranges are considered dysfuntional, so we can't.
  :exclusions [org.clojure/clojure
               org.clojure/clojurescript
               cljsjs/react]

  :main frontend.core

  :jvm-opts ["-Djava.net.preferIPv4Stack=true"
             "-server"
             "-XX:MaxPermSize=256m"
             "-XX:+UseConcMarkSweepGC"
             "-Xss1m"
             "-Xmx1024m"
             "-XX:+CMSClassUnloadingEnabled"
             "-Djava.library.path=target/native/macosx/x86_64:target/native/linux/x86_64:target/native/linux/x86"
             "-Djna.library.path=target/native/macosx/x86_64:target/native/linux/x86_64:target/native/linux/x86"
             "-Dfile.encoding=UTF-8"
             "-Djava.awt.headless=true"]

  :clean-targets ^{:protect false} [:target-path "resources/public/cljs/"]

  :figwheel {:css-dirs ["resources/assets/css"]
             :nrepl-port 7888}

  :cljsbuild {:builds {:dev {:source-paths ["src-cljs" "test-cljs"]
                             :figwheel {:websocket-host "prod.circlehost"
                                        :websocket-url "wss://prod.circlehost:4444/figwheel-ws"
                                        :on-jsload "frontend.core/reinstall-om!"}
                             :compiler {:output-to "resources/public/cljs/out/frontend-dev.js"
                                        :output-dir "resources/public/cljs/out"
                                        :optimizations :none
                                        ;; Speeds up Figwheel cycle, at the risk of dependent namespaces getting out of sync.
                                        :recompile-dependents false}}
                       :whitespace {:source-paths ["src-cljs"]
                                    :compiler {:output-to "resources/public/cljs/whitespace/frontend-whitespace.js"
                                               :output-dir "resources/public/cljs/whitespace"
                                               :optimizations :whitespace
                                               :source-map "resources/public/cljs/whitespace/frontend-whitespace.js.map"}}

                       :test {:source-paths ["src-cljs" "test-cljs"]
                              :compiler {:output-to "resources/public/cljs/test/frontend-test.js"
                                         :output-dir "resources/public/cljs/test"
                                         :optimizations :advanced
                                         :foreign-libs [{:provides ["cljsjs.react"]
                                                         ;; Unminified React necessary for TestUtils addon.
                                                         :file "resources/components/react/react-with-addons.js"
                                                         :file-min "resources/components/react/react-with-addons.js"}]
                                         :externs ["test-js/externs.js"
                                                   "src-cljs/js/pusher-externs.js"
                                                   "src-cljs/js/ci-externs.js"
                                                   "src-cljs/js/analytics-externs.js"
                                                   "src-cljs/js/intercom-jquery-externs.js"
                                                   "src-cljs/js/d3-externs.js"
                                                   "src-cljs/js/prismjs-externs.js"]
                                         :source-map "resources/public/cljs/test/frontend-test.js.map"}}
                       :production {:source-paths ["src-cljs"]
                                    :compiler {:pretty-print false
                                               :output-to "resources/public/cljs/production/frontend.js"
                                               :output-dir "resources/public/cljs/production"
                                               :optimizations :advanced
                                               :externs ["src-cljs/js/pusher-externs.js"
                                                         "src-cljs/js/ci-externs.js"
                                                         "src-cljs/js/analytics-externs.js"
                                                         "src-cljs/js/intercom-jquery-externs.js"
                                                         "src-cljs/js/d3-externs.js"
                                                         "src-cljs/js/prismjs-externs.js"]
                                               :source-map "resources/public/cljs/production/frontend.js.map"}}}}
  :profiles {:devtools {:repl-options {:port 8230
                                       :nrepl-middleware [dirac.nrepl.middleware/dirac-repl]
                                       :init (do
                                               (require 'dirac.agent)
                                               (dirac.agent/boot!))}
                        :env {:devtools "true"}
                        :cljsbuild {:builds {:dev {:source-paths ["devtools"]}}}
                        :dependencies [[binaryage/devtools "0.5.2"]
                                       [binaryage/dirac "0.1.3"]]}
             :dev {:source-paths ["src-cljs"]
                   :repl-options {:port 8230
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :dependencies [[figwheel-sidecar "0.5.0-2"]
                                  [com.cemerick/piggieback "0.2.1"]]}})
