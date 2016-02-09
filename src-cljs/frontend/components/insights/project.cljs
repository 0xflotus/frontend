(ns frontend.components.insights.project
  (:require [frontend.components.common :as common]
            [frontend.components.insights :as insights]
            [frontend.datetime :as datetime]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [om.core :as om :include-macros true]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            cljsjs.c3)
  (:require-macros [frontend.utils :refer [html defrender]]))

(def build-time-bar-chart-plot-info
  {:top 10
   :right 10
   :bottom 10
   :left 30
   :max-bars 100
   :positive-y% 0.6})

(defn build-time-bar-chart [builds owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)]
        (insights/insert-skeleton build-time-bar-chart-plot-info el)
        (insights/visualize-insights-bar! build-time-bar-chart-plot-info el builds owner)))
    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [el (om/get-node owner)]
        (insights/visualize-insights-bar! build-time-bar-chart-plot-info el builds owner)))
    om/IRender
    (render [_]
      (html
       [:div.build-time-visualization]))))


(defn daily-median-build-time
  "Given a collection of builds, returns a list of vector pairs
  [build-date median-build-time], where build-date is midnight of each day where
  a build started (UTC), and median-build-time is the median of the build times
  of all builds from that day. The list is sorted by build-date, ascending."
  [builds]
  (->> builds
       (group-by #(-> % :start_time time-format/parse time/at-midnight))
       (into (sorted-map))
       (map (fn [[build-date bs]]
              [build-date (->> bs
                               (map :build_time_millis)
                               insights/median)]))))


(defn build-time-line-chart [builds owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)
            build-times (daily-median-build-time builds)]
        (js/c3.generate (clj->js {:bindto el
                                  :padding {:top 10
                                            :right 20}
                                  :data {:x "date"
                                         :columns [(concat ["date"] (map first build-times))
                                                   (concat ["Median Build Time"] (map last build-times))]}
                                  :legend {:hide true}
                                  :grid {:y {:show true}}
                                  :axis {:x {:type "timeseries"
                                             :tick {:format "%m/%d"}
                                                    :fit "true"}
                                         :y {:min 0
                                             :tick {:format #(str (quot % 60000) "m")}}}}))))
    om/IRender
    (render [_]
      (html
       [:div]))))

(defrender project-insights [state owner]
  (let [projects (get-in state state/projects-path)
        plans (get-in state state/user-plans-path)
        navigation-data (:navigation-data state)
        {:keys [branches parallel] :as project} (some->> projects
                                                         (filter #(and (= (:reponame %) (:repo navigation-data))
                                                                       (= (:username %) (:org navigation-data))))
                                                         first)
        chartable-builds (some->> (:recent-builds project)
                                  (filter insights/build-chartable?))
        bar-chart-builds (->> chartable-builds
                              (take (:max-bars build-time-bar-chart-plot-info))
                              (map insights/add-queued-time))]
    (html
     (if (nil? chartable-builds)
       ;; Loading...
       [:div.loading-spinner-big common/spinner]

       ;; We have data to show.
       [:div.insights-project
        [:div.insights-metadata-header
         [:div.card.insights-metadata
          [:dl
           [:dt "last build"]
           [:dd (om/build common/updating-duration
                          {:start (->> chartable-builds
                                       (filter :start_time)
                                       first
                                       :start_time)}
                          {:opts {:formatter datetime/as-time-since
                                  :formatter-use-start? true}})]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "median queue time"]
           [:dd (datetime/as-duration (insights/median (map :queued_time_millis bar-chart-builds))) " min"]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "median build time"]
           [:dd (datetime/as-duration (insights/median (map :build_time_millis bar-chart-builds))) " min"]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "current parallelism"]
           [:dd parallel
            [:a.btn.btn-xs.btn-default {:href (routes/v1-project-settings-subpage {:org (:username project)
                                                                                   :repo (:reponame project)
                                                                                   :subpage "parallel-builds"})}
             [:i.material-icons "tune"]]]]]]
        [:div.card
         [:div.card-header
          [:h2 "Build Timing"]]
         [:div.card-body
          (om/build build-time-bar-chart (reverse bar-chart-builds))]]
        [:div.card
         [:div.card-header
          [:h2 "Build Performance"]]
         [:div.card-body
          (om/build build-time-line-chart chartable-builds)]]]))))
