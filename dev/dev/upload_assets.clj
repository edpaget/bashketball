(ns dev.upload-assets
  (:require
   [app.asset.resolvers :as asset]
   [app.card.resolvers :as card]
   [app.db :as db]
   [app.integrant :as i]
   [app.s3 :as s3]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log])
  (:import
   [java.nio.file Files])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn process-single-asset-upload
  "Handles the upload of a single asset by delegating to app.asset/create-game-card-asset-from-file!.
  Requires app.db/*datasource* to be bound (which is done in -main)."
  [card-name card-version file-path config s3-client]
  (let [file-to-upload (io/file file-path)]
    (if-not s3-client
      (log/error "S3 client could not be created. Check S3 configuration in app-config.edn. Asset processing aborted.")
      (if-not (.exists file-to-upload)
        (log/errorf "File not found: %s. Asset processing aborted." file-path)
        (binding [s3/*s3-client* s3-client]
          (->> (asset/create-and-upload-asset (-> config :game-assets :asset-path)
                                                  (Files/probeContentType (.toPath file-to-upload))
                                                  (Files/readAllBytes (.toPath file-to-upload)))
               (card/set-game-asset-id card-name card-version)))))))

(defn -main
  "Main entry point for the script.
  Expects arguments: <card-name> <card-version> <file-path>
  Example: clj -M:dev -m dev.upload-assets \"Rookie Guard\" \"0\" \"path/to/assets/rookie_guard_art.png\""
  [& args]
  (if (not= 3 (count args))
    (do
      (log/error "Invalid arguments. Usage: clj -M:dev -m dev.upload-assets <card-name> <card-version> <file-path>")
      (System/exit 1))
    (let [[card-name card-version file-path] args]
      (log/infof "Starting S3 asset upload for card: '%s' v'%s', file: '%s'" card-name card-version file-path)
      (i/with-system [{:keys [app.config/config app.db/pool app.s3/client]} "migrate.edn"]
        (binding [db/*datasource* pool]
          (process-single-asset-upload card-name card-version file-path config client)))
      (log/info "S3 asset upload script finished.")
      (System/exit 0))))
