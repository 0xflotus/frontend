(ns frontend.components.aside
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :refer [lower-case]]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.license :as license]
            [frontend.components.shared :as shared]
            [frontend.components.svg :refer [svg]]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.models.plan :as pm]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.seq :refer [select-in]]
            [goog.style]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn changelog-updated-since?
  [date]
  (< date (config/changelog-updated-at)))

(defn status-ico-name [build]
  (case (:status build)
    "running" :busy-light

    "success" :pass-light
    "fixed"   :pass-light

    "failed"   :fail-light
    "timedout" :fail-light

    "queued"      :hold-light
    "not_running" :hold-light
    "retried"     :hold-light
    "scheduled"   :hold-light

    "canceled"            :stop-light
    "no_tests"            :stop-light
    "not_run"             :stop-light
    "infrastructure_fail" :stop-light
    "killed"              :stop-light

    :none-light))

(defn sidebar-build [build {:keys [org repo branch latest?]}]
  [:a.status {:class (when latest? "latest")
       :href (routes/v1-build-path org repo (:build_num build))
       :title (str (build-model/status-words build) ": " (:build_num build))}
   (common/ico (status-ico-name build))])

(defn branch [data owner]
  (reify
    om/IDisplayName (display-name [_] "Aside Branch Activity")
    om/IRender
    (render [_]
      (let [{:keys [org repo branch-data]} data
            [name-kw branch-builds] branch-data
            display-builds (take-last 5 (sort-by :build_num (concat (:running_builds branch-builds)
                                                                    (:recent_builds branch-builds))))]
        (html
         [:li
          [:div.branch
           {:role "button"}
           [:a {:href (routes/v1-dashboard-path {:org org :repo repo :branch (name name-kw)})
                :title (utils/display-branch name-kw)}
            (-> name-kw utils/display-branch (utils/trim-middle 23))]]
          [:div.statuses {:role "button"}
           (for [build display-builds]
             (sidebar-build build {:org org :repo repo :branch (name name-kw)}))]])))))

(defn project-aside [data owner opts]
  (reify
    om/IDisplayName (display-name [_] "Aside Project Activity")
    om/IRender
    (render [_]
      (let [login (:login opts)
            {:keys [project settings collapse-group-id show-activity-time?]} data
            show-all-branches? (get-in data state/show-all-branches-path)
            collapse-branches? (get-in data (state/project-branches-collapsed-path collapse-group-id))
            vcs-url (:vcs_url project)
            org (vcs-url/org-name vcs-url)
            repo (vcs-url/repo-name vcs-url)
            branches-filter (if show-all-branches? identity (partial project-model/personal-branch? {:login login} project))]
        (html
         [:ul {:class (when-not collapse-branches? "open")}
          [:li
           [:div.project {:role "button"}
            [:a.toggle {:title "show/hide"
                        :on-click #(raise! owner [:collapse-branches-toggled {:collapse-group-id collapse-group-id}])}
             (common/ico :repo)]

            [:a.title {:href (routes/v1-project-dashboard {:org org
                                                           :repo repo})
                       :title (project-model/project-name project)}
             (project-model/project-name project)]
            (when (and (project-model/can-read-settings? project) (not collapse-branches?))
             [:a.project-settings-icon {:href (routes/v1-project-settings {:org org :repo repo})
                                        :title (str "Settings for " org "/" repo)}
              (common/ico :settings-light)])
            (when-let [latest-master-build (last (project-model/master-builds project))]
              (sidebar-build latest-master-build {:org org :repo repo :branch (name (:default_branch project)) :latest? true}))]]
          (when-not collapse-branches?
            (for [branch-data (->> project
                                   :branches
                                   (filter branches-filter)
                                   ;; alphabetize
                                   (sort-by first))]
              (list
               (om/build branch
                         {:branch-data branch-data
                          :org org
                          :repo repo}
                         {:react-key (first branch-data)})
               (when show-activity-time?
                 [:li.when
                  (om/build common/updating-duration
                            {:start (project-model/most-recent-activity-time (second branch-data))}
                            {:opts {:formatter datetime/time-ago}})]))))])))))

(defn project-settings-link [project]
  (when (and (project-model/can-read-settings? project))
    [:a.project-settings-icon {:href (routes/v1-project-settings {:org (:username project)
                                                                  :repo (:reponame project)})
                               :title (project-model/project-name project)
                               :on-click #(analytics/track "branch-list-project-settings-clicked")}
     (common/ico :settings-light)]))

(defn branch-list-v2 [{:keys [branches show-all-branches? navigation-data]} owner {:keys [login show-project?]}]
  (reify
      om/IDisplayName (display-name [_] "Aside Branch List")
      om/IRender
      (render [_]
        (let [branches-filter (if show-all-branches?
                                (constantly true)
                                (partial project-model/personal-branch-v2? login))]
          (html
           [:ul.branches
            (for [branch (filter branches-filter branches)]
              (let [project (:project branch)
                    latest-build (last (sort-by :build_num (concat (:running_builds branch)
                                                                   (:recent_builds branch))))]
                [:li {:class (when (and (= (vcs-url/org-name (:vcs_url project))
                                           (:org navigation-data))
                                        (= (vcs-url/repo-name (:vcs_url project))
                                           (:repo navigation-data))
                                        (= (name (:identifier branch))
                                           (:branch navigation-data)))
                               "selected")}
                 [:a {:href (routes/v1-dashboard-path {:org (:username project)
                                                       :repo (:reponame project)
                                                       :branch (name (:identifier branch))})
                      :on-click #(analytics/track "branch-list-branch-clicked")}
                  [:.branch
                   [:.last-build-status
                    (om/build svg {:class "badge-icon"
                                   :src (-> latest-build build-model/status-icon-v2 common/icon-path)})]
                   [:.branch-info
                    (when show-project?
                      [:.project-name
                       {:title (project-model/project-name project)}
                       (project-model/project-name project)])
                    [:.branch-name
                     {:title (utils/display-branch (:identifier branch))}
                     (utils/display-branch (:identifier branch))]
                    (let [last-activity-time (project-model/most-recent-activity-time branch)]
                      [:.last-build-info
                       {:title (when last-activity-time
                                 (datetime/full-datetime (js/Date.parse last-activity-time)))}
                       (if last-activity-time
                         (list
                          (om/build common/updating-duration
                                    {:start last-activity-time}
                                    {:opts {:formatter datetime/time-ago}})
                          " ago")
                         "never")])]]]
                 (when show-project?
                   (project-settings-link project))]))])))))

(defn project-aside-v2 [{:keys [project show-all-branches? navigation-data expanded-repos]} owner {:keys [login]}]
  (reify
    om/IDisplayName (display-name [_] "Aside Project")
    om/IRender
    (render [_]
      (let [{repo-name :reponame} project]
        (html [:li
               [:.project-heading
                {:class (when (and (= (vcs-url/org-name (:vcs_url project))
                                      (:org navigation-data))
                                   (= (vcs-url/repo-name (:vcs_url project))
                                      (:repo navigation-data))
                                   (not (contains? navigation-data :branch)))
                          "selected")
                 :title (project-model/project-name project)}
                [:i.fa.rotating-chevron {:class (when (expanded-repos repo-name) "expanded")
                                         :on-click #(do
                                                      (raise! owner [:expand-repo-toggled {:repo-name repo-name}])
                                                      nil)}]
                [:a.project-name {:href (routes/v1-project-dashboard {:org (:username project)
                                                                      :repo (:reponame project)})
                                  :on-click #(analytics/track "branch-list-project-clicked")}
                 (project-model/project-name project)]
                (project-settings-link project)]
               (when (expanded-repos repo-name)
                 (om/build branch-list-v2
                           {:branches (->> project
                                           project-model/branches
                                           (sort-by (comp lower-case name :identifier)))
                            :show-all-branches? show-all-branches?
                            :navigation-data navigation-data}
                           {:opts {:login login}}))])))))

(defn expand-menu-items [items subpage]
  (for [item items]
    (case (:type item)

      :heading
      [:.aside-item.aside-heading
       (:title item)]

      :subpage
      [:a.aside-item {:href (:href item)
                      :class (when (= subpage (:subpage item)) "active")}
       (:title item)])))

(defn project-settings-nav-items [data owner]
  (let [navigation-data (:navigation-data data)]
    [{:type :heading :title "Project Settings"}
     {:type :subpage :href "edit" :title "Overview" :subpage :overview}
     {:type :subpage :href (routes/v1-org-settings navigation-data) :title "Org Settings"
      :class "project-settings-to-org-settings"}
     {:type :heading :title "Tweaks"}
     {:type :subpage :href "#parallel-builds" :title "Adjust Parallelism" :subpage :parallel-builds}
     {:type :subpage :href "#env-vars" :title "Environment variables" :subpage :env-vars}
     {:type :subpage :href "#experimental" :title "Experimental Settings" :subpage :experimental}
     {:type :heading :title "Test Commands"}
     {:type :subpage :href "#setup" :title "Dependency Commands" :subpage :setup}
     {:type :subpage :href "#tests" :title "Test Commands" :subpage :tests}
     {:type :heading :title "Notifications"}
     {:type :subpage :href "#hooks" :title "Chat Notifications" :subpage :hooks}
     {:type :subpage :href "#webhooks" :title "Webhook Notifications" :subpage :webhooks}
     {:type :subpage :href "#badges" :title "Status Badges" :subpage :badges}
     {:type :heading :title "Permissions"}
     {:type :subpage :href "#checkout" :title "Checkout SSH keys" :subpage :checkout}
     {:type :subpage :href "#ssh" :title "SSH Permissions" :subpage :ssh}
     {:type :subpage :href "#api" :title "API Permissions" :subpage :api}
     {:type :subpage :href "#aws" :title "AWS Permissions" :subpage :aws}
     {:type :heading :title "Continuous Deployment"}
     {:type :subpage :href "#heroku" :title "Heroku Deployment" :subpage :heroku}
     {:type :subpage :href "#aws-codedeploy" :title "AWS CodeDeploy" :subpage :aws-codedeploy}
     {:type :subpage :href "#deployment" :title "Other Deployments" :subpage :deployment}]))

(defn project-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (:project-settings-subpage app :overview)]
        (html
          [:div.aside-user {:class (when (= :project-settings (:navigation-point app)) "open")}
           (if (feature/enabled? :ui-v2)
             [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
               (common/ico :fail-light)]
             [:header
              [:h5 "Project Settings"]
              [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
               (common/ico :fail-light)]])
           [:div.aside-user-options
            (expand-menu-items (project-settings-nav-items app owner) subpage)]])))))

(defn org-settings-nav-items [plan org-name]
  (concat
   [{:type :heading :title "Plan"}
    {:type :subpage :title "Overview" :href "#" :subpage :overview}]
   (if-not (pm/can-edit-plan? plan org-name)
     [{:type :subpage :href "#containers" :title "Add containers" :subpage :containers}]
     (concat
      [{:type :subpage :title "Adjust containers" :href "#containers" :subpage :containers}]
      (when (pm/transferrable-or-piggiebackable-plan? plan)
        [{:type :subpage :title "Organizations" :href "#organizations" :subpage :organizations}])
      (when (pm/paid? plan)
        [{:type :subpage :title "Billing info" :href "#billing" :subpage :billing}
         {:type :subpage :title "Cancel" :href "#cancel" :subpage :cancel}])))
   [{:type :heading :title "Organization"}
    {:type :subpage :href "#projects" :title "Projects" :subpage :projects}
    {:type :subpage :href "#users" :title "Users" :subpage :users}]))

(defn admin-settings-nav-items []
  (filter
    identity
    [{:type :subpage :href "/admin" :title "Overview" :subpage nil}
     (when (config/enterprise?)
       {:type :subpage :href "/admin/management-console" :title "Management Console" :subpage nil})
     {:type :subpage :href "/admin/fleet-state" :title "Fleet State" :subpage :fleet-state}
     (when (config/enterprise?)
       {:type :subpage :href "/admin/license" :title "License" :subpage :license})
     (when (config/enterprise?)
       {:type :subpage :href "/admin/users" :title "Users" :subpage :users})]))

(defn admin-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (:project-settings-subpage app :overview)]
        (html
          [:div.aside-user {:class (when (= :admin-settings (:navigation-point app)) "open")}
           [:header
            [:h5 "Admin Settings"]
            [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
             (common/ico :fail-light)]]
           [:div.aside-user-options
            (expand-menu-items (admin-settings-nav-items) subpage)]])))))

(defn redirect-org-settings-subpage
  "Piggiebacked plans can't go to :containers, :organizations, :billing, or :cancel.
  Un-piggiebacked plans shouldn't be able to go to the old 'add plan' page. This function
  selects a different page for these cases."
  [subpage plan org-name]
  (cond ;; Redirect :plan to :containers for paid plans that aren't piggiebacked.
        (and plan
             (pm/can-edit-plan? plan org-name)
             (= subpage :plan))
        :containers

        ;; Redirect :organizations, :billing, and :cancel to the overview page
        ;; for piggiebacked plans.
        (and plan
             (not (pm/can-edit-plan? plan org-name))
             (#{:organizations :billing :cancel} subpage))
        :overview

        :else subpage))

(defn org-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [plan (get-in app state/org-plan-path)
            org-data (get-in app state/org-data-path)
            org-name (:name org-data)
            subpage (redirect-org-settings-subpage (:project-settings-subpage app) plan org-name)
            items (org-settings-nav-items plan org-name)]
        (html
         [:div.aside-user {:class (when (= :org-settings (:navigation-point app)) "open")}
          [:header
           [:h5 "Organization Settings"]
           [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
            (common/ico :fail-light)]]
          [:div.aside-user-options
           (expand-menu-items items subpage)]])))))

(defn collapse-group-id [project]
  "Computes a hash of the project id.  Includes the :current-branch if
  available.  The hashing is performed because this data is stored on
  the client side and we don't want to leak data"
  (let [project-id (project-model/id project)
        branch (:current-branch project)]
    (utils/md5 (str project-id branch))))

(def aside-width 210)
(def new-aside-width 285)


(defn branch-activity-list [app owner opts]
  (reify
    om/IRender
    (render [_]
      (let [show-all-branches? (get-in app state/show-all-branches-path)
            expanded-repos (get-in app state/expanded-repos-path)
            sort-branches-by-recency? (get-in app state/sort-branches-by-recency-path)
            projects (get-in app state/projects-path)
            settings (get-in app state/settings-path)
            recent-projects-filter (if (and sort-branches-by-recency?
                                            (not show-all-branches?))
                                     (partial project-model/personal-recent-project? (:login opts))
                                     identity)]
        (html
         [:div.aside-activity.open
          [:header
           [:select {:class "toggle-sorting"
                     :name "toggle-sorting"
                     :on-change #(raise! owner [:sort-branches-toggled
                                                (utils/parse-uri-bool (.. % -target -value))])
                     :value sort-branches-by-recency?}
            [:option {:value false} "By Repo"]
            [:option {:value true} "Recent"]]

           [:div.toggle-all-branches
            [:input {:id "my-branches"
                     :name "toggle-all-branches"
                     :type "radio"
                     :value "false"
                     :checked (not show-all-branches?)
                     :react-key "toggle-all-branches-my-branches"
                     :on-change #(raise! owner [:show-all-branches-toggled false])}]
            [:label {:for "my-branches"}
             "Mine"]
            [:input {:id "all-branches"
                     :name "toggle-all-branches"
                     :type "radio"
                     :value "true"
                     :checked show-all-branches?
                     :react-key "toggle-all-branches-all-branches"
                     :on-change #(raise! owner [:show-all-branches-toggled true])}]
            [:label {:for "all-branches"}
             "All"]]]


          (if (feature/enabled? :ui-v2)
            (if sort-branches-by-recency?
              (om/build branch-list-v2
                        {:branches (->> projects
                                        project-model/sort-branches-by-recency-v2
                                        ;; Arbitrary limit on visible branches.
                                        (take 100))
                         :show-all-branches? show-all-branches?
                         :navigation-data (:navigation-data app)}
                        {:opts {:login (:login opts)
                                :show-project? true}})
              [:ul.projects
               (for [project (sort project-model/sidebar-sort projects)]
                 (om/build project-aside-v2
                           {:project project
                            :show-all-branches? show-all-branches?
                            :expanded-repos expanded-repos
                            :navigation-data (:navigation-data app)}
                           {:react-key (project-model/id project)
                            :opts {:login (:login opts)}}))])
            [:div.projects
             (for [project (if sort-branches-by-recency?
                             (->> projects
                                  project-model/sort-branches-by-recency
                                  (filter recent-projects-filter)
                                  (take 100))
                             (sort project-model/sidebar-sort projects))]
               (om/build project-aside
                         {:project project
                          :settings settings
                          :collapse-group-id (collapse-group-id project)
                          :show-activity-time? sort-branches-by-recency?}
                         {:react-key (collapse-group-id project)
                          :opts {:login (:login opts)}}))])])))))

(defn aside-menu [app owner opts]
  (reify
    om/IDisplayName (display-name [_] "Aside Menu")
    om/IInitState (init-state [_] {:scrollbar-width 0})
    om/IDidMount (did-mount [_] (om/set-state! owner :scrollbar-width (goog.style/getScrollbarWidth)))
    om/IRender
    (render [_]
      (html
       [:nav.aside-left-menu

        {:class (if (feature/enabled? :ui-v2) "ui-v2" "ui-v1")}
        (om/build project-settings-menu app)
        (om/build org-settings-menu app)
        (om/build admin-settings-menu app)
        (om/build branch-activity-list app {:opts {:login (:login opts)
                                                   :scrollbar-width (om/get-state owner :scrollbar-width)}})]))))

(defn nav-icon
  [v1 v2]
  (if (feature/enabled? :ui-v2)
    [:img.aside-icon {:src (utils/cdn-path (str "/img/inner/icons/Aside-" v2 ".svg"))}]
    [:i.fa {:class v1}]))

(defn aside-nav [app owner]
  (reify
    om/IDisplayName (display-name [_] "Aside Nav")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip ".aside-item"))
    om/IRender
    (render [_]
      (let [user (get-in app state/user-path)
            avatar-url (gh-utils/make-avatar-url user)]

        (html
          [:nav.aside-left-nav

           [:a.aside-item.logo {:title "Home"
                                :data-placement "right"
                                :data-trigger "hover"
                                :href "/"}
            [:div.logomark
             (common/ico :logo)]]

           [:a.aside-item.avatar {:data-placement "right"
                                  :data-trigger "hover"
                                  :title "Settings"
                                  :href "/account"}
            [:img.account-avatar {:src avatar-url}]]

           [:a.aside-item {:title "Documentation"
                           :data-placement "right"
                           :data-trigger "hover"
                           :href "/docs"}
            (nav-icon "fa-copy" "Docs")]

           [:a.aside-item (merge (common/contact-support-a-info owner)
                                 {:title "Support"
                                  :data-placement "right"
                                  :data-trigger "hover"
                                  :data-bind "tooltip: {title: 'Support', placement: 'right', trigger: 'hover'}"})
            (nav-icon "fa-comments" "Support")]

           [:a.aside-item {:href "/add-projects",
                           :data-placement "right"
                           :data-trigger "hover"
                           :title "Add Projects"}
            (nav-icon "fa-plus-circle" "AddProject")]

           [:a.aside-item {:href "/invite-teammates",
                           :data-placement "right"
                           :data-trigger "hover"
                           :title "Invite your teammates"}
            (nav-icon "fa-user" "Team")]

           [:a.aside-item {:data-placement "right"
                           :data-trigger "hover"
                           :title "Changelog"
                           :href "/changelog"
                           :class (when (changelog-updated-since? (:last_viewed_changelog user))
                                    "unread")}
            (nav-icon "fa-bell" "Notifications")]

	   [:a.aside-item {:data-placement "right"
                           :data-trigger "hover"
                           :title "Insights"
                           :href "/build-insights"}
            (nav-icon "fa-bar-chart" "Insights")]

           (when (:admin user)
             [:a.aside-item {:data-placement "right"
                             :data-trigger "hover"
                             :title "Admin"
                             :href "/admin"}
              (nav-icon "fa-cogs" "Admin")])

           [:a.aside-item.push-to-bottom {:data-placement "right"
                                          :data-trigger "hover"
                                          :title "Logout"
                                          :href "/logout"}
            (nav-icon "fa-power-off" "Power")]])))))

(defn aside [app owner]
  (reify
    om/IDisplayName (display-name [_] "Aside")
    om/IRender
    (render [_]
      (let [user (get-in app state/user-path)
            login (:login user)
            avatar-url (gh-utils/make-avatar-url user)
            show-aside-menu? (get-in app [:navigation-data :show-aside-menu?] true)
            license (get-in app state/license-path)]
        (html
         [:aside.app-aside {:class (cond-> []
                                     (not show-aside-menu?) (conj "menuless"))}
          (when show-aside-menu?
            (om/build aside-menu app {:opts {:login login}}))])))))
