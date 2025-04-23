(ns app.db-test
  (:require
   [app.config :as config]
   [app.db :as db]
   [app.db.connection-pool :as db.pool]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ragtime.next-jdbc :as ragtime.next-jdbc]
   [ragtime.repl :as ragtime.repl]))

(set! *warn-on-reflection* true)

;; --- Test Fixtures ---

(def test-config (config/config {:profile :test})) ; Assuming :test profile or defaults are okay
(def test-datasource (atom nil))

(defn- ragtime-config []
  {:datastore  (ragtime.next-jdbc/sql-database @test-datasource)
   :migrations (ragtime.next-jdbc/load-resources "migrations")})

(defn db-fixture [f]
  (let [ds (db.pool/create-pool (:database-url test-config))]
    (reset! test-datasource ds)
    (binding [db/*datasource* ds] ; Bind dynamic var for tests using it
      (println "Running migrations...")
      (ragtime.repl/migrate (ragtime-config)) ; Apply migrations
      (try
        (f) ; Run tests
        (finally
          (println "Rolling back migrations...")
          (try
            ;;(ragtime.repl/rollback (ragtime-config) (-> (ragtime-config) :migrations count)) ; Rollback to first
            (catch Exception e (println "Error rolling back migrations:" (ex-message e))))
          (db.pool/close-pool! ds)
          (reset! test-datasource nil))))))

(defn rollback-fixture [f]
  (if-let [ds @test-datasource]
    (jdbc/with-transaction [tx ds {:rollback-only true}]
      (prn (.getAutoCommit tx))
      (binding [db/*current-connection* tx ; Bind connection for tests using it
                db/*datasource*         ds] ; Also bind datasource for consistency
        (f)))
    (throw (IllegalStateException. "Test datasource is not initialized for transaction fixture."))))

;; Register fixtures: db-fixture runs once, rollback-fixture runs per-test
(use-fixtures :once db-fixture)
(use-fixtures :each rollback-fixture)

;; --- Helper ---
(defn- insert-actor! [conn-or-nil {:keys [id username enrollment_state]}]
  (let [query (-> (h/insert-into :actor)
                  (h/values [{:id id :username username :enrollment_state enrollment_state}]))]
    (if conn-or-nil
      (db/execute! conn-or-nil query)
      (db/execute! query))))

(defn- get-actor-by-id [conn-or-nil actor-uuid]
  (let [query (-> (h/select :id :username :enrollment_state) ; Select specific columns
                  (h/from :actor)
                  (h/where [:= :id actor-uuid]))]
    (if conn-or-nil
      (db/execute-one! conn-or-nil query {:builder-fn rs/as-unqualified-maps})
      (db/execute-one! query {:builder-fn rs/as-unqualified-maps}))))

;; --- Tests ---

(deftest execute!-test
  (testing "Insert using dynamic datasource"
    (let [actor-uuid (random-uuid)
          result (insert-actor! nil {:id actor-uuid :username "pennyg" :enrollment_state "enrolled"})]
      (is (= [1] (mapv :next.jdbc/update-count result)) "Should return update count of 1")
      (is (= {:id actor-uuid :username "pennyg" :enrollment_state "enrolled"}
             (get-actor-by-id nil actor-uuid)))))

  (testing "Insert using explicit connection"
    (jdbc/with-transaction [tx @test-datasource {:rollback-only true}] ; Use a separate tx for explicit connection test
      (let [actor-uuid (random-uuid)
            result (insert-actor! tx {:id actor-uuid :username "nickw" :enrollment_state "pending"})]
        (is (= [1] (mapv :next.jdbc/update-count result)) "Should return update count of 1")
        (is (= {:id actor-uuid :username "nickw" :enrollment_state "pending"}
               (get-actor-by-id tx actor-uuid))))))) ; Read within same explicit tx

(deftest execute-one!-test
  (testing "Select one using dynamic datasource"
    (let [actor-uuid (random-uuid)]
      (insert-actor! nil {:id actor-uuid :username "edc" :enrollment_state "enrolled"})
      (let [actor (get-actor-by-id nil actor-uuid)]
        (is (= {:id actor-uuid :username "edc" :enrollment_state "enrolled"} actor)))))

  (testing "Select one using explicit connection"
    (jdbc/with-transaction [tx @test-datasource {:rollback-only true}]
      (let [actor-uuid (random-uuid)]
        (insert-actor! tx {:id actor-uuid :username "jend" :enrollment_state "invited"})
        (let [actor (get-actor-by-id tx actor-uuid)]
          (is (= {:id actor-uuid :username "jend" :enrollment_state "invited"} actor))))))

  (testing "Select non-existent returns nil"
    (is (nil? (get-actor-by-id nil (random-uuid))))))

(deftest plan-test
  (testing "Select multiple using dynamic datasource"
    (let [uuid5 (random-uuid)
          uuid6 (random-uuid)]
      (insert-actor! nil {:id uuid5 :username "johnnyl" :enrollment_state "enrolled"})
      (insert-actor! nil {:id uuid6 :username "betten" :enrollment_state "pending"})
      (let [query (-> (h/select :username) ; Select username
                      (h/from :actor)
                      (h/where [:in :id [uuid5 uuid6]]))
          actors (into #{} (map #(select-keys % [:username]))
                       (db/plan query {:builder-fn rs/as-unqualified-maps}))]
        (prn actors)
        (is (= #{{:username "johnnyl"} {:username "betten"}} actors))))) ; Use set comparison for unordered results

  (testing "Select multiple using explicit connection"
    (jdbc/with-transaction [tx @test-datasource {:rollback-only true}]
      (let [uuid7 (random-uuid)
            uuid8 (random-uuid)]
        (insert-actor! tx {:id uuid7 :username "gracem" :enrollment_state "invited"})
        (insert-actor! tx {:id uuid8 :username "mattj" :enrollment_state "enrolled"})
        (let [query (-> (h/select :username) ; Select username
                        (h/from :actor)
                        (h/where [:in :id [uuid7 uuid8]]))
              actors (into #{} (map #(select-keys % [:username]))
                           (db/plan tx query {:builder-fn rs/as-unqualified-maps}))]
          (is (= #{{:username "gracem"} {:username "mattj"}} actors))))))) ; Use set comparison

(deftest with-connection-test
  (testing "Operations within with-connection use the same connection"
    (let [uuid9 (random-uuid)
          uuid10 (random-uuid)]
      (db/with-connection [_conn]; Uses *datasource* implicitly to get a connection
      ;; *current-connection* is now bound within this block
        (let [result1 (insert-actor! nil {:id uuid9 :username "joes" :enrollment_state "enrolled"}) ; Uses bound *current-connection*
              actor1 (get-actor-by-id nil uuid9) ; Uses bound *current-connection*
              result2 (insert-actor! nil {:id uuid10 :username "chrisg" :enrollment_state "pending"}) ; Uses bound *current-connection*
              actor2 (get-actor-by-id nil uuid10)] ; Uses bound *current-connection*
          (is (= [1] (mapv :next.jdbc/update-count result1)))
          (is (= {:id uuid9 :username "joes" :enrollment_state "enrolled"} actor1))
          (is (= [1] (mapv :next.jdbc/update-count result2)))
          (is (= {:id uuid10 :username "chrisg" :enrollment_state "pending"} actor2))))
      ;; Verify outside the macro (but within the test's transaction)
      (is (= {:id uuid9 :username "joes" :enrollment_state "enrolled"} (get-actor-by-id nil uuid9)))
      (is (= {:id uuid10 :username "chrisg" :enrollment_state "pending"} (get-actor-by-id nil uuid10))))))

(deftest dynamic-binding-test
  (testing "Throws exception if *datasource* is not bound and no connection given"
    (binding [db/*datasource* nil          ; Unbind datasource
              db/*current-connection* nil] ; Ensure no connection either
      (is (thrown? IllegalStateException (db/execute! (h/select :* (h/from :actor))))))))
