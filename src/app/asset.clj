(ns app.asset
  (:require
   [app.models :as models]
   [app.s3 :as s3]
   [malli.experimental :as me]
   [app.db :as db]
   [app.graphql.resolvers :as gql.resolvers]
   [app.registry :as registry])
  (:import
   [java.util Base64]))

(me/defn create :- ::models/GameAsset
  "Create a new GameAsset with it's status as pending. It should generate a signed s3 url
   that allows the client to upload the asset to s3"
  [{:keys [mime-type asset-path]} :- [:map [:mime-type :string]]]
  (db/execute-one! {:insert-into [(models/->table-name ::models/GameAsset)]
                    :columns     [:mime-type :img-url :status]
                    :values      [[mime-type asset-path (db/->pg_enum :game-asset-status-enum/PENDING)]]
                    :returning   [:*]}))

(me/defn update-status :- ::models/GameAsset
  "Update the status of the GameAsset"
  [id :- :uuid status :- ::models/GameAssetStatus & [err-msg] :- [:tuple :string]]
  (db/execute-one! {:update [(models/->table-name ::models/GameAsset)]
                    :set (cond-> {:status #pg_enum status}
                           err-msg (assoc :error-message err-msg))
                    :where [:= :id id]
                    :returning [:*]}))

(me/defn with-full-path :- ::models/GameAsset
  "Takes an asset model and formats the img-url as a full path"
  [{:keys [id] :as asset} :- ::models/GameAsset]
  (update asset :img-url str "/" id))

(gql.resolvers/defresolver :Mutation/createAsset
  "Create a new game asset. Accepts the mime type for the asset and the asset as a
   b64 encoded string."
  [:=> [:cat
        [:map
         [:config [:map [:game-assets [:map [:asset-path :string]]]]]
         [:s3-client ::s3/client]]
        [:map
         [:mime-type :string]
         [:img-blob :string]]
        :any]
   ::models/GameAsset]
  [{:keys [config]} {:keys [mime-type img-blob]} _]
  (let [asset-path (-> config :game-assets :asset-path)
        {asset-id :id} (create {:mime-type mime-type :asset-path asset-path})]
    (with-full-path
     (try
       (s3/put-object (str asset-path "/" asset-id)
                      (.decode (Base64/getDecoder) img-blob)
                      {:Content-Type mime-type})
       (update-status asset-id :game-asset-status-enum/UPLOADED)
       (catch Throwable e
         (update-status asset-id :game-asset-status-enum/ERROR (ex-message e)))))))
