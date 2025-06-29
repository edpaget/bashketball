(ns dev.seed-data
  (:require
   [app.db :as db]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [camel-snake-kebab.core :as csk]
   [honey.sql.helpers :as hsql]))

(def ^:private seed-data-path "resources/seed-data")

(defn- filename->table-name
  "Converts a filename like 'my_entities.edn' to a snake_case keyword like :my_entities."
  [filename]
  (-> filename
      (str/split #"\.")
      first
      csk/->snake_case_keyword))

(defn- prepare
  [row]
  (update-vals row #(cond->> %
                      (or (map? %) (vector? %)) (conj [:lift]))))

(defn- seed-file!
  "Seeds data from a single EDN file into the specified database connection/transaction.
   Assumes the file contains a vector of maps."
  [db-conn file]
  (let [table-name (filename->table-name (.getName file))]
    (log/info (str "Attempting to seed table: " table-name " from file: " (.getName file)))
    (try
      (let [data-str (slurp file)
            parsed-data (when-not (str/blank? data-str)
                          (edn/read-string {:readers {'pg_enum db/->pg_enum}} data-str ))]
        (if (and (vector? parsed-data) (every? map? parsed-data))
          (if (seq parsed-data)
            (let [prepared-data (map prepare parsed-data)
                  insert-query (-> (hsql/insert-into table-name)
                                   (hsql/values prepared-data))]
              (db/execute! db-conn insert-query)
              (log/info (str "Successfully seeded " (count prepared-data) " rows into " table-name)))
            (log/info (str "No data rows to seed in " (.getName file) " for table " table-name)))
          (log/warn (str "Skipping file " (.getName file) ": content is not a vector of maps or is empty."))))
      (catch java.io.FileNotFoundException _
        (log/error (str "Seed file not found: " (.getAbsolutePath file))))
      (catch Exception e
        (log/error e (str "Failed to seed data from file: " (.getName file) ". Error: " (.getMessage e)))))))

(defn seed-all!
  "Loads all EDN files from the ` seed-data-path` directory into the database.
   This function expects app.db/*datasource* to be bound, or it can be called
   within an existing app.db/with-connection or app.db/with-transaction block
   if you pass the connection explicitly.

   Example usage from REPL (assuming datasource is configured in your dev system):
   (require '[dev.seed-data :as seeder])
   (require '[app.db :as db])
   ;; Make sure your dev system has *datasource* bound, e.g., from Integrant
   ;; (binding [db/*datasource* my-dev-datasource] (seeder/seed-all!))
   ;; Or, if your system component manages the datasource:
   ;; (seeder/seed-all! (:app.db/pool system))"
  ([] ; Arity for using dynamic *datasource*
   (db/with-transaction [conn] ; Use a single transaction for all seeding from *datasource*
     (seed-all! conn)))
  ([connectable] ; Arity for explicit connectable (datasource or connection)
   (let [seed-dir (io/file seed-data-path)]
     (if (.exists seed-dir)
       (let [edn-files (filter #(str/ends-with? (.getName %) ".edn")
                               (file-seq seed-dir))] ; file-seq to find files recursively if needed, or .listFiles for flat
         (if (seq edn-files)
           (do
             (doseq [file edn-files]
               (when (.isFile file) (seed-file! connectable file)))
             (log/info "All seed data processing complete (using provided connectable)."))
           (log/info (str "No .edn files found in " seed-data-path))))
       (log/warn (str "Seed data directory not found: " seed-data-path))))))
