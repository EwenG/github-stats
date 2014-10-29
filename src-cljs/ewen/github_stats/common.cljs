(ns ewen.github-stats.common
  (:require [cljs.core.match]
            [sablono.core :refer-macros [html]])
  (:require-macros [cljs.core.match.macros :refer [match]]))

;React utilities

(defn get-in-props [component key]
  (-> component (aget "props") (aget (name key))))

(defn get-in-state [component key]
  (-> component (aget "state") (aget (name key))))









;Github API utilities

(defn api-response->error-code [api-response]
  (-> api-response (.-target) (.getLastErrorCode)))

(defn api-response->json [api-response]
  (-> api-response (.-target) (.getResponseJson)))


(defn url->param-map [url-string]
  (let [param-strings (-> (clojure.string/split url-string "?")
                          (get 1)
                          (clojure.string/split "&"))]
    (->> (map #(clojure.string/split % "=") param-strings)
         (mapcat (fn [[k v]] [(keyword k) v]))
         (apply array-map))))

(defn parse-url-rel [[url-str rel-str]]
  [   (-> (.replace rel-str #"rel=\"(.*)\"" "$1") (.trim) (keyword))
      (-> (.replace url-str #"<(.*)>" "$1") (.trim))])

(defn link-header->map [link-string]
  (->> (clojure.string/split link-string ",")
       (mapv #(clojure.string/split % ";"))
       (mapcat parse-url-rel)
       (apply array-map)
       (map (fn [[k v]] [k (url->param-map v)]))
       (into {})))



(defn header-map->page-info [header-map]
  (match [(-> header-map :next :page) (-> header-map :last :page)
          (-> header-map :prev :page) (-> header-map :first :page)]
         [nil nil nil nil] {:page-count 1 :current-page 1}  ;All results in 1 page
         [nil "0" nil nil] {:page-count 0 :current-page 0}  ;No result
         [nil nil prev "1"] {:page-count (inc (js/parseInt prev))
                             :current-page (inc (js/parseInt prev))} ;Multiple page result - current page is the last one
         [next last _ _]  {:page-count (js/parseInt last)
                           :current-page (dec (js/parseInt next))}       ;Multiple page result - current page is NOT the last one
         :else (throw (js/Error. "header-map->page-info error"))))



(defn response->links [api-response]
  (-> api-response (.-target) (.getResponseHeader "Link")))





;Pagination

(defn pagination-blocks [page-count current-page]
  (let [current-page (cond (< current-page 3) 3
                           (> current-page (- page-count 2)) (- page-count 2)
                           :else current-page)
        current-page-block (-> (range (- current-page 2) (+ current-page 3)) set)
        start-block #{1 2}
        end-block #{(dec page-count) page-count}
        all-pages (-> (range 1 (inc page-count)) set)]
    (-> (clojure.set/intersection all-pages (clojure.set/union start-block current-page-block end-block))
        sort
        vec)))

(defn insert-placeholders [pages]
  (reduce (fn [pages p]
            (cond (= p (inc (last pages))) (conj pages p)
                  (= (dec p) (inc (last pages))) (conj pages (dec p) p)
                  :else (conj pages :placeholder p)))
          [] pages))

(defn collapse-pagination [page-count current-page]
  (-> (pagination-blocks page-count current-page)
      insert-placeholders))




;Components


(def header
  (.createClass js/React
                #js {:render (fn []
                               (html
                                 [:div#header.row
                                  [:div.col-md-12.center-block
                                   [:img#logo.center-block {:src "img/chart.png"}]]]))}))

(def pagination
  (.createClass js/React
                #js {:render (fn []
                               (this-as this
                                        (let [{:keys [page-count current-page repository result-by-page-count]}
                                              (get-in-props this :state)
                                              on-change-page (get-in-props this :on-change-page)
                                              collapsed-p (collapse-pagination page-count current-page)]
                                          (html
                                            [:div.text-center
                                             (-> (into [:ul.pagination.text-center
                                                        [:li {:class (when (= 1 current-page) "disabled")}
                                                         [:a {:href "#"
                                                              :on-click #(do (.preventDefault %)
                                                                          (on-change-page repository
                                                                                             result-by-page-count
                                                                                             (- current-page 1)))}
                                                          "\u00AB"]]]
                                                       (conj (for [page collapsed-p]
                                                               (cond (= :placeholder page)
                                                                     [:li.disabled
                                                                      [:a {:href "#"} "..."]]
                                                                     (= current-page page)
                                                                     [:li.active [:a {:href "#"} page]]
                                                                     :else
                                                                     [:li [:a
                                                                           {:href     "#"
                                                                            :on-click #(do (.preventDefault %)
                                                                                        (on-change-page repository
                                                                                                           result-by-page-count
                                                                                                           page))}
                                                                           page]]))))
                                                 (conj [:li {:class (when (= page-count current-page) "disabled")}
                                                        [:a {:href "#"
                                                             :on-click #(do (.preventDefault %)
                                                                         (on-change-page repository
                                                                                            result-by-page-count
                                                                                            (+ current-page 1)))}
                                                         "\u00BB"]]))]))))}))

(def error
  (.createClass js/React
                #js {:render (fn []
                               (html [:div.row
                                      [:div#no-result.alert.alert-warning.col-md-8.col-md-offset-2
                                       {:role "alert"}
                                       [:strong "Sorry!"]
                                       " An error occured while processing your request"]]))}))