(ns ewen.github-stats.server
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :as ring-resp]
            [immutant.web :as web]
            [net.cgrand.enlive-html :as enlive]
            [cemerick.austin.repls :refer [browser-connected-repl-js]]
            [ewen.github-stats.env :as env]

            [ring.middleware.resource])
  (:gen-class))

(when (:enable-browser-repl (env/env))
  (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                        (cemerick.austin/repl-env))))

(defn browser-connected-repl-html []
  [:script "goog.require('clojure.browser.repl');"]
  [:script (browser-connected-repl-js)])


(enlive/deftemplate search-tml "public/github-stats.html" []
                    [[:script (enlive/attr= :src "cljs/github-stats.js")]]
                    (enlive/before
                      (when (:dev (env/env))
                        (enlive/html
                          [:script {:src "cljs/goog/base.js" :type "text/javascript"}]
                          [:script {:src "cljs/goog/deps.js" :type "text/javascript"}])))
                    [:body]
                    (enlive/append
                      (when (:dev (env/env))
                        (enlive/html [:script "goog.require('ewen.github_stats.search');"]
                                     (browser-connected-repl-html)))))

(enlive/deftemplate repository-tml "public/github-stats.html" [repository full_name]
                    [[:script (enlive/attr= :src "cljs/github-stats.js")]]
                    (enlive/before
                      (when (:dev (env/env))
                        (enlive/html
                          [:script {:src "cljs/goog/base.js" :type "text/javascript"}]
                          [:script {:src "cljs/goog/deps.js" :type "text/javascript"}])))
                    [:body]
                    (enlive/append
                      (when (:dev (env/env))
                        (enlive/html [:script "goog.require('ewen.github_stats.repository');"]
                                     (browser-connected-repl-html))))
                    [:body]
                    (enlive/append
                      (enlive/html
                        (if full_name [:script (format "ewen.github_stats.repository.main('%s','%s');"
                                                       repository full_name)]
                                      [:script (format "ewen.github_stats.repository.main('%s');"
                                                       repository)]))))



(defn search-page
  [request]
  (ring-resp/response (apply str (search-tml))))

(defn repository-page
  [request]
  (let [repository (get-in request [:path-params :repository])
        full_name (-> request :params :full_name)]
    (ring-resp/response (apply str (repository-tml repository full_name)))))

(defroutes routes
           [[["/" {:get search-page} ^:interceptors [http/html-body]]
             ["/:repository" {:get repository-page} ^:interceptors [http/html-body]]]])

(def service {::http/routes        #(deref #'routes)
              ::http/resource-path "/public/"})

(defn -main
  [& args]
  (web/run (::http/servlet (http/create-servlet service)) {:path "/"}))


(comment
  (web/run (::http/servlet (http/create-servlet service)) {:path "/"})
  (cemerick.austin.repls/cljs-repl repl-env)
  (cemerick.austin.repls/exec)
  (web/stop {:path "/"}))
