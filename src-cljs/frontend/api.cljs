(ns frontend.api
  (:require [clojure.set :as set]
            [frontend.models.user :as user-model]
            [frontend.models.build :as build-model]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [goog.string.format]
            [secretary.core :as sec]))

(def build-keys-mapping {:username :org
                         :reponame :repo
                         :default_branch :branch})

(defn project-build-id [project]
  "Takes project hash and filter down to keys that identify the build."
  (-> project
      (set/rename-keys build-keys-mapping)
      (select-keys (vals build-keys-mapping))))

(defn get-projects [api-ch & {:as context}]
  (ajax/ajax :get "/api/v1/projects?shallow=true" :projects api-ch :context context))

(defn get-github-repos [api-ch & {:keys [page]
                           :or {page 1}}]
  (ajax/ajax :get (str "/api/v1/user/repos?page=" page)
             :github-repos
             api-ch
             :context {:page page}))

(defn get-bitbucket-repos [api-ch & {:keys [page]
                                  :or {page 1}}]
  (ajax/ajax :get (str "/api/dangerzone/user/repos/bitbucket?page=" page)
             :bitbucket-repos
             api-ch
             :context {:page page}))

(defn get-orgs [api-ch & [{:keys [include-user?]}]]
  (ajax/ajax :get (str "/api/v1/user/organizations" (when include-user? "?include-user=true"))
             :organizations
             api-ch))

(defn get-org-plan [org-name api-ch]
  (ajax/ajax :get
             (gstring/format "/api/v1/organization/%s/plan" org-name)
             :org-plan
             api-ch
             :context {:org-name org-name}))

(defn get-user-plans [api-ch]
  (ajax/ajax :get "/api/v1/user/organizations/plans"
             :user-plans
             api-ch))

(defn get-usage-queue [build api-ch]
  (ajax/ajax :get
             (gstring/format "/api/v1/project/%s/%s/%s/usage-queue"
                             (vcs-url/org-name (:vcs_url build))
                             (vcs-url/repo-name (:vcs_url build))
                             (:build_num build))
             :usage-queue
             api-ch
             :context (build-model/id build)))

;; Note that dashboard-builds-url can take a :page (within :query-params)
;; and :builds-per-page, or :limit and :offset directly.
(defn dashboard-builds-url [{:keys [branch repo org admin deployments query-params builds-per-page offset limit]}]
  (let [offset (or offset (* (get query-params :page 0) builds-per-page))
        limit (or limit builds-per-page)
        url (cond admin "/api/v1/admin/recent-builds"
                  deployments "/api/v1/admin/deployments"
                  branch (gstring/format "/api/v1/project/%s/%s/tree/%s" org repo branch)
                  repo (gstring/format "/api/v1/project/%s/%s" org repo)
                  org (gstring/format "/api/v1/organization/%s" org)
                  :else "/api/v1/recent-builds")]
    (str url "?" (sec/encode-query-params (merge {:shallow true
                                                  :offset offset
                                                  :limit limit}
                                                 query-params)))))

(defn get-dashboard-builds [{:keys [branch repo org admin query-params builds-per-page] :as args} api-ch]
  (let [url (dashboard-builds-url args)]
    (ajax/ajax :get url :recent-builds api-ch :context {:branch branch :repo repo :org org})))

(defn get-admin-dashboard-builds
  [tab api-ch]
  (get-dashboard-builds
    {:admin true
     :query-params {:status (case tab
                              :running-builds "running"
                              :queued-builds "scheduled,queued")
                    :order "asc"}}
    api-ch))

;; This is defined in the API.
(def max-allowed-page-size 100)

(defn get-projects-builds [build-ids build-count api-ch]
  (doseq [build-id build-ids]
    ;; Assemble a list of pages descriptions and result atoms to deliver them to.
    (let [page-starts (range 0 build-count max-allowed-page-size)
          page-ends (concat (rest page-starts) [build-count])
          pages (for [[start end] (map vector page-starts page-ends)]
                  {:offset start
                   :limit (- end start)
                   :page-result (atom nil)})
          page-results (map :page-result pages)]
      (doseq [{:keys [offset limit page-result]} pages]
        (let [url (dashboard-builds-url (assoc build-id :offset offset :limit limit))]
          ;; Fire off an ajax call for the page. The API controllers will
          ;; deliver the response to page-result, and put the full data in the
          ;; state once all of the page-results are delivered.
          (ajax/ajax :get url :recent-project-builds api-ch :context {:project-id build-id
                                                                      :page-result page-result
                                                                      :all-page-results page-results}))))))

(defn get-action-output [{:keys [vcs-url build-num step index output-url]
                          :as args} api-ch]
  (let [url (or output-url
                (gstring/format "/api/v1/project/%s/%s/output/%s/%s"
                                (vcs-url/project-name vcs-url)
                                build-num
                                step
                                index))]
    (ajax/ajax :get
               url
               :action-log
               api-ch
               :context args)))

(defn get-org-settings [org-name api-ch]
  (ajax/ajax :get
             (gstring/format "/api/v1/organization/%s/settings" org-name)
             :org-settings
             api-ch
             :context {:org-name org-name}))

(defn get-project-settings [project-name api-ch]
  (ajax/ajax :get (gstring/format "/api/v1/project/%s/settings" project-name) :project-settings api-ch :context {:project-name project-name}))

(defn get-build-tests [build api-ch]
  (ajax/ajax :get
             (gstring/format "/api/v1/project/%s/%s/tests"
                             (vcs-url/project-name (:vcs_url build))
                             (:build_num build))
             :build-tests
             api-ch
             :context (build-model/id build)))

(defn get-build-state [api-ch]
  (ajax/ajax :get "/api/v1/admin/build-state" :build-state api-ch))

(defn get-fleet-state [api-ch]
  (ajax/ajax :get "/api/v1/admin/build-state-summary" :fleet-state api-ch)
  (ajax/ajax :get "/api/v1/admin/build-system-summary" :build-system-summary api-ch))

(defn get-all-users [api-ch]
  (ajax/ajax :get "/api/v1/admin/users" :all-users api-ch))

(defn set-user-suspension [login suspended? api-ch]
  (ajax/ajax :post
             (gstring/format "/api/v1/admin/user/%s" login)
             :set-user-admin-state
             api-ch
             :params {:suspended suspended?}))

(defn set-user-admin-scope [login scope api-ch]
  (ajax/ajax :post
             (gstring/format "/api/v1/admin/user/%s" login)
             :set-user-admin-state
             api-ch
             :params {:admin scope}))

(defn get-code-signing-keys [org-name api-ch]
  (ajax/ajax :get
             (gstring/format "/api/v1/organization/%s/code-signing/osx-keys" org-name)
             :get-code-signing-keys
             api-ch
             :context {:org-name org-name}))

(defn set-code-signing-keys [org-name file-content file-name password description api-ch uuid]
  (ajax/ajax :post
             (gstring/format "/api/v1/organization/%s/code-signing/osx-keys" org-name)
             :set-code-signing-keys
             api-ch
             :params {:file-content file-content
                      :file-name file-name
                      :password password
                      :description description}
             :context {:org-name org-name
                       :uuid uuid}))

(defn delete-code-signing-key [org-name id api-ch uuid]
  (ajax/ajax :delete
             (gstring/format "/api/v1/organization/%s/code-signing/osx-keys/%s" org-name id)
             :delete-code-signing-key
             api-ch
             :context {:id id
                       :uuid uuid}))

