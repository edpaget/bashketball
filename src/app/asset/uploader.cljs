(ns app.asset.uploader
  (:require
   ["@headlessui/react" :as headless]

   [app.asset.graphql-types :as asset.gql-types]
   [app.graphql.client :as gql.client]
   [app.models :as models]
   [uix.core :as uix :refer [defui $]]))

(defn convert-to-blob
  [event mutate! update-img-url]
  (let [file (aget (.. event -target -files) 0)
        reader (js/FileReader.)]
    (set! (.-onload reader)

          (fn [e]
            (let [result (.. e -target -result)]
              (when update-img-url
                (update-img-url {:img-url result}))
              (if-let [[_ mime-type base64-data] (re-matches #"^data:([^;]+);base64,(.+)$"
                                                             result)]
                (mutate! {:variables {:mime-type mime-type
                                      :img-blob base64-data}})
                (throw (ex-info "cannot parse data url" {:data-url result}))))))
    (.readAsDataURL reader file)))

(defn- use-create-asset
  []
  (let [[mutate! {:keys [loading data error]}] (gql.client/use-mutation {:Mutation/createAsset '([::models/GameAsset :id]
                                                                                                 :mime-type :img-blob)}
                                                                        "createNewCardImage"
                                                                        ::asset.gql-types/create-asset-args)]
    [(cond
       loading {:state :loading}
       error   {:state :error :value error}
       data    {:state :finished :value (-> data :createAsset :id)}
       :else   {:state :initialized})
     mutate!]))

(defui asset-upload [{:keys [update-card-field update-img-url]}]
  (let [[asset-id mutate!] (use-create-asset)]
    (uix/use-effect (fn [] (when (= :finished (:state asset-id))
                             (update-card-field :game-asset-id (:value asset-id))))
                    [update-card-field asset-id])
    ($ headless/Field {:class "flex items-center mb-4"}
       ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} "Card Image")
       ($ headless/Input {:type "file"
                          :accept "image/*"
                          :name "card-img"
                          :on-change #(convert-to-blob % mutate! update-img-url)
                          :class "flex-grow mt-1 block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:text-sm file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100"}))))
