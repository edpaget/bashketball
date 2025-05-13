(ns app.navbar
  (:require
   [uix.core :as uix :refer [defui $]]
   [app.authn.provider :as authn]))

(defui navbar []
  ($ :nav {:class "bg-gray-800"}
     ($ :div {:class "max-w-7xl mx-auto px-2 sm:px-6 lg:px-8"}
        ($ :div {:class "relative flex items-center justify-between h-16"}
           ($ :div {:class "flex-1 flex items-center justify-center sm:items-stretch sm:justify-start"}
              ($ :div {:class "flex-shrink-0 flex items-center"}
                 ($ :a {:href "/"}
                    ($ :h1 {:class "text-white text-xl font-bold"} "Bashketball"))))
           ($ :div {:class "absolute inset-y-0 right-0 flex items-center pr-2 sm:static sm:inset-auto sm:ml-6 sm:pr-0"}
              ($ authn/login-required {:show-prompt true}
                 ($ authn/logout-button)))))))
