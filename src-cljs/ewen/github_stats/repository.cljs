(ns ewen.github-stats.repository
  (:require [clojure.browser.repl]                          ;Only for development mode. TODO find a way to make a conditional require
            [sablono.core :refer-macros [html]]
            [domina :refer [single-node]]
            [domina.css :refer [sel]]
            [goog.string.format]
            [cljs.core.match]
            [goog.date.Date]
            [ewen.github-stats.common :refer [get-in-props get-in-state api-response->json
                                              header-map->page-info link-header->map
                                              response->links header-map->page-info
                                              link-header->map response->links
                                              pagination-blocks insert-placeholders
                                              collapse-pagination pagination header
                                              api-response->error-code error]])
  (:require-macros [cljs.core.match.macros :refer [match]]))



(def breadcrumb
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [{:keys [page]} (get-in-props this :state)
                                              breadcrumb-switch (aget (.-props this) "on-breadcrumb-switch")]
                                          (html [:ol.breadcrumb
                                                 (if (= :contributors page)
                                                   [:li.active "Contributors"]
                                                   [:li [:a {:href "#"
                                                             :on-click (fn [e]
                                                                         (.preventDefault e)
                                                                         (breadcrumb-switch :contributors))}
                                                         "Contributors"]])
                                                 (if (= :commits page)
                                                   [:li.active "Commits"]
                                                   [:li [:a {:href "#"
                                                             :on-click (fn [e]
                                                                         (.preventDefault e)
                                                                         (breadcrumb-switch :commits))}
                                                         "Commits"]])]))))}))

(def contributors
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [{{:keys [results page-count]} :contributors}
                                              (get-in-props this :state)
                                              on-change-page (aget (.-props this) "on-change-page")]
                                          (html
                                            (-> (into [:div]
                                                      (for [row-contrib (partition-all 3 results)]
                                                        [:div.row
                                                         (for [contrib row-contrib]
                                                           (let [login (get contrib "login")
                                                                 avatar-url (get contrib "avatar_url")]
                                                             [:div.col-md-4
                                                              [:div.thumbnail
                                                               [:img.avatar {:src (or avatar-url
                                                                                      "img/question_mark.jpeg")
                                                                             :alt login}]
                                                               [:div.caption
                                                                [:h3 login]]]]))]))
                                                (conj (when (> page-count 1)
                                                        (pagination
                                                          #js {:on-change-page on-change-page
                                                               :state (-> (get-in-props this :state)
                                                                   :contributors)}))))))))}))

(defn date-string->date-month [date-string]
  (let [date (js/Date. date-string)]
    (-> (js/Date. (.getFullYear date) (.getMonth date))
        .getTime)))

(defn display-timeline [this]
  (let [{:keys [commits]} (get-in-props this :state)
        commits-by-date (map #(update-in % ["date"] date-string->date-month)
                             commits)
        commits-by-date (->> (group-by #(get % "date") commits-by-date)
                             sort)
        dates (vec (cons "date" (keys commits-by-date)))
        commits-count (->> (map (comp count val) commits-by-date)
                           (cons "commits")
                           vec)]
    (let [chart (clj->js {:bindto "#timeline"
                          :data   {:x       "date"
                                   :columns [dates
                                             commits-count]}
                          :axis   {:x {:type "timeseries"
                                       :tick {:format "%Y-%m"}}}})]
      (js/c3.generate chart))))

(def timeline
  (.createClass js/React
                #js {:render (fn []
                               (html
                                 [:div#timeline]))
                     :componentDidMount
                     (fn []
                       (this-as this (display-timeline this)))
                     :componentDidUpdate
                             (fn []
                               (this-as this (display-timeline this)))}))

(def contributors-impact
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [{:keys [commits]} (get-in-props this :state)
                                              grouped-commits (->> (group-by #(get % "id") commits)
                                                                   (sort-by (comp count val) >))]
                                          (html
                                            (into [:div]
                                                  (for [row-impact (partition-all 3 grouped-commits)]
                                                    [:div.row
                                                     (for [impact row-impact]
                                                       (let [impact-val (-> impact val first)]
                                                         [:div.col-md-4
                                                          [:div.thumbnail
                                                           [:img.avatar {:src (or (get impact-val "avatar_url")
                                                                                  "img/question_mark.jpeg")
                                                                         :alt (get impact-val "login")}]
                                                           [:div.caption
                                                            [:h3 (get impact-val "login")]
                                                            [:p (str (-> impact val count) " commits")]]]]))]))))))}))

(def github-stats-repository
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [{:keys [page full_name error-code processing]} (get-in-props this :state)]
                                          (html
                                            [:div.container
                                             (header)
                                             (when full_name [:div.page-header
                                                              [:h1 [:span.glyphicon.glyphicon-hand-right] " " full_name]])
                                             (.transferPropsTo this (breadcrumb))
                                             (when error-code
                                               (.transferPropsTo this (error)))
                                             (when processing
                                               [:div.row
                                                [:div.col-md-12.center-block
                                                 [:img.center-block
                                                  {:src    "https://assets-cdn.github.com/images/spinners/octocat-spinner-128.gif"}]]])
                                             (when (and (not processing)
                                                        (= page :contributors))
                                               (.transferPropsTo this (contributors)))
                                             (when (and (not processing)
                                                        (= page :commits))
                                               (.transferPropsTo this (timeline)))
                                             (when (and (not processing)
                                                        (= page :commits))
                                               (.transferPropsTo this (contributors-impact)))]))))}))


(defn contributor-response->map [response]
  (mapv #(select-keys % ["login" "id" "avatar_url"])
        (js->clj response)))

(defn commit->map [commit]
  (let [{{id "id" login "login"
          avatar-url "avatar_url"} "author"
         {{date "date"} "author"} "commit"} commit]
    {"id" (or id -1)
     "login" (or login "bot")
     "date" date
     "avatar_url" avatar-url}))

(defn commits-response->map [response]
  (mapv commit->map
        (js->clj response)))

(defn handle-contributors-response [response]
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
                  contributor-response->map)}))

(defn handle-commits-response [response]
  {:commits      (-> (api-response->json response)
                     commits-response->map)})


(def app-state (atom {:page :contributors
                      :processing   false
                      :error-code nil
                      :contributors {:page-count   0
                                     :current-page 0
                                     :result-by-page-count 100}
                      :commits []}))


(defn get-contributors [repository result-by-page-count page-nb]
  (swap! app-state assoc :processing true)
  (.send goog.net.XhrIo (goog.string/format "https://api.github.com/repositories/%s/contributors?per_page=%s&page=%s"
                                            (js/decodeURIComponent repository)
                                            result-by-page-count
                                            page-nb)
         #(let [error-code (api-response->error-code %)]
           (if (> error-code 0)
             (swap! app-state merge {:error-code error-code
                                     :processing false})
             (swap! app-state
                    (fn [app-state]
                      (-> app-state
                          (update-in [:contributors]
                                     merge
                                     (handle-contributors-response %))
                          (assoc :processing false))))))))

(defn get-commits [repository result-by-page-count]
  (swap! app-state assoc :processing true :error-code nil)
  (.send goog.net.XhrIo (goog.string/format "https://api.github.com/repositories/%s/commits?per_page=%s"
                                            (js/decodeURIComponent repository)
                                            result-by-page-count)
         #(let [error-code (api-response->error-code %)]
           (if (> error-code 0)
             (swap! app-state merge {:error-code error-code
                                     :processing false})
             (swap! app-state merge
                    (handle-commits-response %)
                    {:processing false
                     :error-code nil})))))

(defn on-change-page [repository result-by-page-count page-nb]
  (get-contributors repository result-by-page-count page-nb))

(defn on-breadcrumb-switch [new-page]
  (let [app-state @app-state]
    (cond
      (= :commits new-page)
      (get-commits (:repository app-state) 100)
      (= :contributors new-page)
      (get-contributors (:repository app-state)
                   (-> app-state :contributors :result-by-page-count)
                   (-> app-state :contributors :current-page))))
  (swap! app-state assoc :page new-page))


(defn ^:export main [repository full_name]
  (swap! app-state assoc :repository (js/decodeURIComponent repository) :full_name full_name)
  (swap! app-state assoc-in [:contributors :repository] (js/decodeURIComponent repository))
  (.renderComponent js/React (github-stats-repository #js {:state                @app-state
                                                           :on-change-page       on-change-page
                                                           :on-breadcrumb-switch on-breadcrumb-switch})
                    (.-body js/document))
  (get-contributors repository (-> @app-state :contributors :result-by-page-count) 1))

(add-watch app-state :state-watch
           (fn [_ _ old-state new-state]
             (.renderComponent js/React (github-stats-repository #js {:state @app-state
                                                                      :on-change-page on-change-page
                                                                      :on-breadcrumb-switch on-breadcrumb-switch})
                               (.-body js/document))))







