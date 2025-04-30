(ns app.db-test
  (:require
   [app.db :as db]
   [app.test-utils :as tu] ; Added require for test utilities
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.transaction]))

(set! *warn-on-reflection* true)

;; --- Test Fixtures ---

;; Register fixtures: db-fixture runs once, rollback-fixture runs per-test
;; Use fixtures from the test-utils namespace
(use-fixtures :once tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

;; --- Helper ---
(defn- random-email []
  (str (gensym "test-user-") "@example.com"))

(defn- insert-actor! [conn-or-nil {:keys [id use-name]}]
  (let [query (-> (h/insert-into :actor)
                  (h/values [{:id id :use-name use-name :enrollment-state "complete"}]))] ; Insert :use-name, ID is already string
    (if conn-or-nil
      (db/execute! conn-or-nil query)
      (db/execute! query))))

(defn- get-actor-by-id [conn-or-nil actor-id] ; Renamed actor-uuid to actor-id
  (let [query (-> (h/select :id :use-name) ; Select :use-name, remove enrollment_state
                  (h/from :actor)
                  (h/where [:= :id actor-id]))]
    (if conn-or-nil
      (db/execute-one! conn-or-nil query)
      (db/execute-one! query))))

;; --- Tests ---

(deftest execute!-test
  (testing "Insert using dynamic datasource"
    (let [actor-id (random-email) ; Use random-email
          result (insert-actor! nil {:id actor-id :use-name "pennyg"})]
      (is (= [1] (mapv :next.jdbc/update-count result)) "Should return update count of 1")
      (is (= {:id actor-id :use-name "pennyg"} ; Check :use-name
             (get-actor-by-id nil actor-id)))))

  (testing "Insert using explicit connection"
    (jdbc/with-transaction [tx db/*current-connection* {:rollback-only true}] ; Use a separate tx for explicit connection test
      (let [actor-id (random-email) ; Use random-email
            result (insert-actor! tx {:id actor-id :use-name "nickw"})]
        (is (= [1] (mapv :next.jdbc/update-count result)) "Should return update count of 1")
        (is (= {:id actor-id :use-name "nickw"} ; Check :use-name
               (get-actor-by-id tx actor-id))))))) ; Read within same explicit tx

(deftest execute-one!-test
  (testing "Select one using dynamic datasource"
    (let [actor-id (random-email)] ; Use random-email
      (insert-actor! nil {:id actor-id :use-name "edc"})
      (let [actor (get-actor-by-id nil actor-id)]
        (is (= {:id actor-id :use-name "edc"} actor))))) ; Check :use-name

  (testing "Select one using explicit connection"
    (jdbc/with-transaction [tx db/*current-connection* {:rollback-only true}]
      (let [actor-id (random-email)] ; Use random-email
        (insert-actor! tx {:id actor-id :use-name "jend"})
        (let [actor (get-actor-by-id tx actor-id)]
          (is (= {:id actor-id :use-name "jend"} actor)))))) ; Check :use-name

  (testing "Select non-existent returns nil"
    (is (nil? (get-actor-by-id nil (random-email)))))) ; Use random-email

(deftest plan-test
  (testing "Select multiple using dynamic datasource"
    (let [id5 (random-email) ; Use random-email
          id6 (random-email)] ; Use random-email
      (insert-actor! nil {:id id5 :use-name "johnnyl"})
      (insert-actor! nil {:id id6 :use-name "betten"})
      (let [query (-> (h/select :use-name) ; Select :use-name
                      (h/from :actor)
                      (h/where [:in :id [id5 id6]]))
            actors (into #{} (map #(select-keys % [:use_name])) ; Select :use-name
                         (db/plan query))]
        (prn actors)
        (is (= #{{:use_name "johnnyl"} {:use_name "betten"}} actors))))) ; Check :use-name

  (testing "Select multiple using explicit connection"
    (jdbc/with-transaction [tx db/*current-connection* {:rollback-only true}]
      (let [id7 (random-email) ; Use random-email
            id8 (random-email)] ; Use random-email
        (insert-actor! tx {:id id7 :use-name "gracem"})
        (insert-actor! tx {:id id8 :use-name "mattj"})
        (let [query (-> (h/select :use-name) ; Select :use-name
                        (h/from :actor)
                        (h/where [:in :id [id7 id8]]))
              actors (into #{} (map #(do (prn %) (select-keys % [:use_name]))) ; Select :use-name
                           (db/plan tx query))]
          (is (= #{{:use_name "gracem"} {:use_name "mattj"}} actors))))))) ; Check :use-name

(deftest with-connection-test
  (testing "Operations within with-connection use the same connection"
    (let [id9 (random-email) ; Use random-email
          id10 (random-email)] ; Use random-email
      (db/with-connection [_conn]; Uses *datasource* implicitly to get a connection
      ;; *current-connection* is now bound within this block
        (let [result1 (insert-actor! nil {:id id9 :use-name "joes"})
              actor1 (get-actor-by-id nil id9) ; Uses bound *current-connection*
              result2 (insert-actor! nil {:id id10 :use-name "chrisg"})
              actor2 (get-actor-by-id nil id10)] ; Uses bound *current-connection*
          (is (= [1] (mapv :next.jdbc/update-count result1)))
          (is (= {:id id9 :use-name "joes"} actor1)) ; Check :use-name
          (is (= [1] (mapv :next.jdbc/update-count result2)))
          (is (= {:id id10 :use-name "chrisg"} actor2)))) ; Check :use-name
      ;; Verify outside the macro (but within the test's transaction)
      (is (= {:id id9 :use-name "joes"} (get-actor-by-id nil id9))) ; Check :use-name
      (is (= {:id id10 :use-name "chrisg"} (get-actor-by-id nil id10)))))) ; Check :use-name

(deftest dynamic-binding-test
  (testing "Throws exception if *datasource* is not bound and no connection given"
    (binding [db/*datasource* nil          ; Unbind datasource
              db/*current-connection* nil] ; Ensure no connection either
      (is (thrown? IllegalStateException (db/execute! (h/select :* (h/from :actor))))))))
