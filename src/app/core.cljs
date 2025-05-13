(ns app.core
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.dom]
   [app.router :as router]
   [app.authn.provider :as authn]
   [app.graphql.client :as graphql]
   [app.card.pages :as card.pages]
   [app.navbar :as navbar]
   ["@apollo/client" :as apollo.client]))

(defui home-page []
  ;; Style the home page link
  ($ :div {:className "container mx-auto p-4 text-center"} ; Center content
     ($ :a {:href (router/href :cards-index)
            :className "text-xl text-blue-600 hover:underline"} ; Larger text, blue, underline on hover
        "Manage Cards"))) ; Changed text slightly for clarity

(defui app []
  ($ router/router {:router-store router/router-store}
     ($ apollo.client/ApolloProvider {:client graphql/client}
        ($ authn/authn
           ;; Apply flex layout to the main container
           ($ :div {:className "app-container flex flex-col min-h-screen bg-gray-100 text-gray-800"}
              ($ navbar/navbar) ; Assuming navbar has its own styling
              ;; Make content area grow
              ($ :main {:className "content flex-grow"} ; Use <main> tag for semantics
                 ($ :<>
                    ($ router/route {:route-name :home-page}
                       ($ home-page))
                    ($ router/route {:route-name :cards-index}
                       ($ card.pages/cards-index))
                    ($ router/route {:route-name :cards-new}
                       ($ authn/login-required {:show-prompt true}
                          ($ card.pages/cards-new)))
                    ($ router/route {:route-name :cards-show}
                       ($ card.pages/cards-show)))))
           ;; Basic footer styling
           ($ :footer {:className "footer bg-gray-200 p-4 text-center text-sm text-gray-600"} ; Use <footer> tag
              "Bashketball Card Manager"))))) ; Add some footer text

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))
