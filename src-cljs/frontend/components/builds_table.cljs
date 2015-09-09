(ns frontend.components.builds-table
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.models.build :as build-model]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn build-row [build owner {:keys [show-actions? show-branch? show-project?]}]
  (let [url (build-model/path-for (select-keys build [:vcs_url]) build)]
    [:tr {:class (when (:dont_build build) "dont_build")}
     [:td
      [:a {:title (str (:username build) "/" (:reponame build) " #" (:build_num build))
          :href url}
       (when show-project? (str (:username build) "/" (:reponame build) " ")) "#" (:build_num build)]]
     [:td
      (if-not (:vcs_revision build)
        [:a {:href url}]
        [:a {:title (build-model/github-revision build)
             :href url}
         (build-model/github-revision build)])]
     (when show-branch?
       [:td
        [:a
         {:title (build-model/vcs-ref-name build)
          :href url}
         (-> build build-model/vcs-ref-name (utils/trim-middle 23))]])
     [:td.recent-user
      [:a
       {:title (build-model/ui-user build)
        :href url}
       (build-model/author build)]]
     [:td.recent-log
      [:a
       {:title (:body build)
        :href url}
       (:subject build)]]
     (if (or (not (:start_time build))
             (= "not_run" (:status build)))
       [:td {:col-span 2}]
       (list [:td.recent-time
              [:a
               {:title  (datetime/full-datetime (js/Date.parse (:start_time build)))
                :href url}
               (om/build common/updating-duration {:start (:start_time build)} {:opts {:formatter datetime/time-ago}})
               " ago"]]
             [:td.recent-time
              [:a
               {:title (build-model/duration build)
                :href url}
               (om/build common/updating-duration {:start (:start_time build)
                                                   :stop (:stop_time build)})]]))
     [:td.recent-status-badge
      [:a
       {:title "status"
        :href url
        :class (build-model/status-class build)}
       (build-model/status-words build)]]
     (when show-actions?
       [:td.build_actions
        (when (build-model/can-cancel? build)
          (let [build-id (build-model/id build)
                vcs-url (:vcs_url build)
                build-num (:build_num build)]
            ;; TODO: how are we going to get back to the correct build in the app-state?
            ;;       Not a problem here, b/c the websocket will be updated, but something to think about
            (forms/managed-button
             [:button.cancel_build
              {:on-click #(raise! owner [:cancel-build-clicked {:build-id build-id
                                                                :vcs-url vcs-url
                                                                :build-num build-num}])}
              "Cancel"])))])]))

(defn builds-table [builds owner {:keys [show-actions? show-branch? show-project?]
                                  :or {show-branch? true
                                       show-project? true}}]
  (reify
    om/IDisplayName (display-name [_] "Builds Table")
    om/IRender
    (render [_]
      (html
       [:table.recent-builds-table
        [:thead
         [:tr
          [:th "Build"]
          [:th "Revision"]
          (when show-branch?
            [:th "Branch"])
          [:th "Author"]
          [:th "Log"]
          [:th.condense "Started"]
          [:th.condense "Length"]
          [:th.condense "Status"]
          (when show-actions?
            [:th.condense "Actions"])]]
        [:tbody
         (map #(build-row % owner {:show-actions? show-actions?
                                   :show-branch? show-branch?
                                   :show-project? show-project?})
              builds)]]))))
