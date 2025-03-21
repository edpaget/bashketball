(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.router :as router]
            [app.authn.provider :as authn]
            [app.graphql.client :as graphql]
            [app.card.pages :as card.pages]
            ["@apollo/client" :as apollo.client]))

(defui home-page []
  ($ :a {:href (router/href :cards-index)}
     "Cards"))

(defui app []
  ($ router/router {:router-store router/router-store}
     ($ apollo.client/ApolloProvider {:client graphql/client}
        ($ authn/authn
           ($ :div.app-container
              ($ :div.navbar
                 ($ :h1 "Blood Basket")
                 ($ authn/login-required ($ authn/logout-button)))
              ($ :div.content
                 ($ :<>
                    ($ router/route {:route-name :home-page}
                       ($ home-page))
                    ($ router/route {:route-name :cards-index}
                       ($ card.pages/cards-index))
                    ($ router/route {:route-name :cards-new}
                       ($ authn/login-required {:show-prompt true}
                          ($ card.pages/cards-show)))
                    ($ router/route {:route-name :cards-show}
                       ($ card.pages/cards-show)))))
           ($ :div.footer)))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))
