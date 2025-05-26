(ns app.asset
  (:require
   [app.models :as models]
   [app.s3 :as s3]
   [malli.experimental :as me]
   [app.db :as db]
   [app.graphql.resolvers :as gql.resolvers])
  (:import
   [java.util Base64]))

(me/defn create :- ::models/GameAsset
  "Create a new GameAsset with it's status as pending. It should generate a signed s3 url
   that allows the client to upload the asset to s3"
  [{:keys [mime-type asset-path]} :- [:map [:mime-type :string]]]
  (db/execute-one! {:insert-into [(models/->table-name ::models/GameAsset)]
                    :columns     [:mime-type :img-url :status]
                    :values      [[mime-type asset-path #pg_enum :game-asset-status/PENDING]]
                    :returning   [:*]}))

(me/defn update-status :- ::models/GameAsset
  "Update the status of the GameAsset"
  [id :- :uuid status :- ::models/GameAssetStatus & [err-msg] :- [:tuple :string]]
  (db/execute! {:update [(models/->table-name ::models/GameAsset)]
                :set (cond-> {:status #pg_enum status}
                       err-msg (assoc :error-message err-msg))
                :where [:= :id id]
                :returning [:*]}))

(gql.resolvers/defresolver :Mutation/createAsset
  "Create a new game asset. Accepts the mime type for the asset and the asset as a
   b64 encoded string."
  [:=> [:cat
        [:map
         [:config [:map [:game-assets [:map [:asset-path]]]]]
         [:s3-client [::s3/client]]]
        [:map
         [:mime-type :string]
         [:img-blob :string]]
        :any]
   ::models/GameAsset]
  [{:keys [config]} {:keys [mime-type img-blob]} _]
  (let [asset-path (-> config :game-assets :asset-path)
        {asset-id :id} (create {:mime-type mime-type :asset-path asset-path})]
    (try
      (s3/put-object (str asset-path "/" asset-id)
                     (.decode (Base64/getDecoder) img-blob)
                     nil)
      (update-status asset-id :game-asset-status/UPLOADED)
      (catch Throwable e
        (update-status asset-id :game-asset-status/ERROR (ex-message e))
        (throw e)))))
