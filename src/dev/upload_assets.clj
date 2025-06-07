(ns dev.upload-assets
  (:require
   [app.config :as app-config]
   [app.s3 :as s3]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log])
  (:import [java.io File InputStream]
           [java.nio.file Files Path])
  (:gen-class))

(defn- relativize-path
  "Calculates the relative path of a file with respect to a base directory.
  Ensures S3-friendly forward slashes."
  [^File base-dir ^File file]
  (let [base-path (.toPath (.getCanonicalFile base-dir))
        file-path (.toPath (.getCanonicalFile file))]
    (-> (.relativize base-path file-path)
        (.toString)
        (str/replace File/separator "/"))))

(defn- get-content-type
  "Probes the file to determine its content type."
  [^File file]
  (try
    (Files/probeContentType (.toPath file))
    (catch Exception e
      (log/warnf "Could not determine content type for %s: %s. Defaulting to nil."
                 (.getName file) (.getMessage e))
      nil)))

(defn- upload-file-to-s3
  "Uploads a single file to S3 using the provided S3 client map."
  [s3-client-map s3-key ^File file]
  (try
    (let [content-type (get-content-type file)
          upload-opts (when content-type {:ContentType content-type})]
      (with-open [input-stream (io/input-stream file)]
        (log/infof "Uploading %s to s3://%s/%s (Content-Type: %s)"
                   (.getAbsolutePath file) (:bucket-name s3-client-map) s3-key (or content-type "N/A"))
        (s3/put-object s3-client-map s3-key input-stream upload-opts)
        (log/infof "Successfully uploaded %s to s3://%s/%s"
                   (.getName file) (:bucket-name s3-client-map) s3-key)))
    true
    (catch Exception e
      (log/errorf e "Failed to upload %s. Error: %s" (.getAbsolutePath file) (.getMessage e))
      false)))

(defn- process-directory
  "Processes all files in the given directory and uploads them."
  [directory-path config]
  (let [bucket-name   (get-in config [:s3 :bucket-name])
        aws-opts      (get config :aws-opts {})
        s3-client-map (when bucket-name (s3/create-client bucket-name aws-opts))]
    (if-not s3-client-map
      (log/error "S3 bucket name not found in config or client could not be created. Check :s3/:bucket-name and :aws-opts in app-config.edn")
      (let [base-dir (io/file directory-path)]
        (if-not (and (.exists base-dir) (.isDirectory base-dir))
          (log/errorf "Directory %s does not exist or is not a directory." directory-path)
          (let [files (filter #(.isFile %) (file-seq base-dir))]
            (if (empty? files)
              (log/infof "No files found in %s to upload." directory-path)
              (do
                (log/infof "Found %d file(s) in %s for bucket %s. Starting upload..."
                           (count files) directory-path (:bucket-name s3-client-map))
                (doseq [file files]
                  (let [s3-key (relativize-path base-dir file)]
                    (upload-file-to-s3 s3-client-map s3-key file)))
                (log/info "Finished processing all files.")))))))))

(defn -main
  "Main entry point for the script. Expects a directory path as an argument.
  Example: clj -M:dev -m dev.upload-assets resources/public/images"
  [& args]
  (let [config (app-config/config)]
    (if (not= 1 (count args))
      (do
        (log/error "Invalid arguments. Usage: clj -M:dev -m dev.upload-assets <directory-path>")
        (System/exit 1))
      (let [directory-path (first args)]
        (log/infof "Starting S3 upload script for directory: %s" directory-path)
        (process-directory directory-path config)
        (log/info "S3 upload script finished.")
        ;; The Cognitect AWS client might hold resources.
        ;; For a dev script that exits, this is often acceptable.
        ;; If this were a long-running process not managed by Integrant,
        ;; you'd want to aws/stop the client from s3-client-map.
        (System/exit 0)))))
