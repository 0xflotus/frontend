(ns frontend.utils.bitbucket
  (:require
   [frontend.utils :as utils]
   [cemerick.url :refer [url]]))

(defn http-endpoint []
  "https://bitbucket.org")

(defn auth-url []
  (let [state {"return-to" (str js/window.location.pathname
                                js/window.location.hash)
               "token" (utils/oauth-csrf-token)
               "delegate" (str js/window.location.protocol "//" js/window.location.host)}
        state-str (.stringify js/JSON (clj->js state))]
    (-> (url (http-endpoint) "site/oauth2/authorize")
        (assoc :query {"state"        state-str
                       "response_type" "code"
                       "client_id" (aget js/window "renderContext" "bitbucketClientId")})
        str)))
