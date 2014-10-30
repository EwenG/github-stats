(ns ewen.github-stats.search
  (:require [clojure.browser.repl]                          ;Only for development mode. TODO find a way to make a conditional require
            [sablono.core :refer-macros [html]]
            [domina :refer [single-node]]
            [domina.css :refer [sel]]
            [goog.string.format]
            [cljs.core.match]
            [schema.core :as s]
            [ewen.github-stats.common :refer [get-in-props get-in-state api-response->json
                                              header-map->page-info link-header->map
                                              response->links header-map->page-info
                                              link-header->map response->links
                                              pagination-blocks insert-placeholders
                                              collapse-pagination pagination header
                                              api-response->error-code error]])
  (:require-macros [cljs.core.match.macros :refer [match]]))






;Components




(def search-result
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [result (get-in-props this :result)
                                              repository (get result "full_name")
                                              id (get result "id")
                                              description (get result "description")]
                                          (html [:a.list-group-item
                                                 {:href (goog.string/format "%s?full_name=%s"
                                                                            id
                                                                            (js/encodeURIComponent repository))}
                                                 [:h4.list-group-item-heading repository]
                                                 [:p.list-group-item-text description]]))))}))

(def search-results
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [{:keys [page-count results]} (get-in-props this :state)
                                              on-search (aget (.-props this) "on-search")]
                                          (html
                                            (-> (into [:div#results.list-group]
                                                      (for [result results]
                                                        (.transferPropsTo this (search-result #js {:result result}))))
                                                (conj (when (> page-count 1)
                                                        (.transferPropsTo this (pagination #js {:on-change-page on-search})))))))))}))


(def no-result
  (.createClass js/React
                #js {:render (fn []
                               (html [:div.row
                                      [:div#no-result.alert.alert-warning.col-md-8.col-md-offset-2
                                       {:role "alert"}
                                                [:strong "Sorry!"]
                                                " No result found"]]))}))

(def github-repo
  (.createClass js/React
                #js {:render (fn []
                               (html [:a {:href "https://github.com/EwenG/github-stats"}
                                      [:img {:style {:position "absolute"
                                                     :top "0"
                                                     :right "0"
                                                     :border "0"}
                                             :src                "https://camo.githubusercontent.com/e7bbb0521b397edbd5fe43e7f760759336b5e05f/68747470733a2f2f73332e616d617a6f6e6177732e636f6d2f6769746875622f726962626f6e732f666f726b6d655f72696768745f677265656e5f3030373230302e706e67"
                                             :alt                "Fork me on GitHub"
                                             :data-canonical-src "https://s3.amazonaws.com/github/ribbons/forkme_right_green_007200.png"}]]))}))


(def search-form
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [on-search (get-in-props this :on-search)
                                              search-string (get-in-state this :search-string)
                                              {:keys [result-by-page-count processing page]} (get-in-props this :state)]
                                          (html [:div [:div.row
                                                       [:div.col-md-4.col-md-offset-4
                                                        [:input#search-field.form-control
                                                         {:type        "text"
                                                          :placeholder "Search repositories..."
                                                          :value       search-string
                                                          :onChange    #(.replaceState this #js {:search-string (.. % -target -value)})
                                                          :on-key-up #(case (.-which %)
                                                                       13 (when (and search-string
                                                                                     (not= "" (.trim search-string)))
                                                                            (on-search search-string
                                                                                       result-by-page-count
                                                                                       1))
                                                                       nil)}]]
                                                       [:div.col-md-4
                                                        (if (= :search page)
                                                          [:button.btn.btn-default
                                                           {:type     "button"
                                                            :disabled (when (or (nil? search-string)
                                                                                (= "" (.trim search-string))
                                                                                processing)
                                                                        "disabled")
                                                            :on-click #(on-search search-string
                                                                                  result-by-page-count
                                                                                  1)}
                                                           (if processing "Loading..." "Search")]
                                                          [:span.glyphicon.glyphicon-hand-left {:style {:font-size "30px"}}])]]
                                                 (when (= :home page)
                                                   [:div#welcome-button.row
                                                    [:div.col-md-12.center-block
                                                     [:button.btn.btn-primary.btn-lg.center-block
                                                      {:type     "button"
                                                       :disabled (when (or (nil? search-string)
                                                                           (= "" (.trim search-string))
                                                                           processing)
                                                                   "disabled")
                                                       :on-click #(on-search search-string
                                                                             result-by-page-count
                                                                             1)}
                                                      (if processing "Loading..." "Search now !")]]])]))))
                     :getInitialState (fn [] #js {:search-string ""})}))




(def github-stats-search
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [{:keys [page-count page error-code]}
                                              (get-in-props this :state)]
                                          (html
                                            [:div.container
                                             (header)
                                             (.transferPropsTo this (search-form))
                                             (when error-code
                                               (.transferPropsTo this (error)))
                                             (when (and (= :search page) (= 0 page-count))
                                               (.transferPropsTo this (no-result)))
                                             (when (and (= :home page) (= 0 page-count))
                                               (github-repo))
                                             (when (= :search page)
                                               (.transferPropsTo this (search-results)))]))))}))








(defn response->map [response]
  (mapv #(select-keys % ["full_name" "description" "id"])
        (-> (.-items response) js->clj)))

(defn on-search-response [response]
  (let [page-count (-> (response->links response)
                       link-header->map
                       header-map->page-info
                       :page-count)
        current-page (-> (response->links response)
                         link-header->map
                         header-map->page-info
                         :current-page)]
    {:page-count page-count
     :current-page current-page
     :results (-> (api-response->json response)
                  response->map)}))

(def app-state (atom {:page         :home
                      :processing   false
                      :error-code nil
                      :page-count   0
                      :current-page 0
                      :result-by-page-count 10}))

(defn on-search [search-string result-by-page-count page-nb]
  (swap! app-state assoc :processing true)
  (.send goog.net.XhrIo
         (goog.string/format "https://api.github.com/search/repositories?q=%s+in:name&per_page=%s&page=%s"
                             search-string
                             result-by-page-count
                             page-nb)
         #(let [error-code (api-response->error-code %)]
           (if (> error-code 0)
             (swap! app-state merge {:error-code error-code
                                     :processing false})
             (swap! app-state
                    (fn [app-state]
                      (merge app-state
                             (on-search-response %)
                             {:repository search-string
                              :error-code nil
                              :page       :search
                              :processing false})))))))



(.renderComponent js/React (github-stats-search #js {:on-search on-search :state @app-state})
                  (.-body js/document))

(add-watch app-state :state-watch
           (fn [_ _ old-state new-state]
             (.renderComponent js/React (github-stats-search #js {:on-search on-search :state new-state})
                               (.-body js/document))))


