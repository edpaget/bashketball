(ns app.authn-e2e-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [e2e.fixtures :as fx]))

(use-fixtures :once fx/container-fixture fx/server-fixture)
(use-fixtures :each fx/webdriver-fixture)

;; Does not render the iframe because this is not an allowed origin
(def login-prompt-selector {:tag :span :fn/text "Sign in with Google"})
(def logout-button-selector {:tag :button :fn/text "Logout"})

(deftest ^:e2e authn-login-logout-test
  (testing "user can log in and log out via navbar controls"
    (e/go fx/*driver* fx/*app-url*)

    (testing "Initial state: login prompt is visible"
      (is (e/wait-visible fx/*driver* login-prompt-selector) "Login prompt should be visible")
      (is (not (e/visible? fx/*driver* logout-button-selector)) "Logout button should not be visible"))

    ;; TODO: test the actual login at some point
    (testing "Perform login"
      (e/js-execute fx/*driver*
                    "fetch('/authn', {
                       method: 'POST',
                       headers: {'Content-Type': 'application/json'},
                       body: JSON.stringify({action: 'login', 'id-token': 'test-user-token'})
                     }).then(response => {
                       if (!response.ok) {
                         console.error('E2E Login POST failed:', response.status);
                         throw new Error('Login POST failed');
                       }
                       // Reload the page to ensure the new session state is picked up
                       // and the 'me' query reflects the logged-in user.
                       window.location.reload();
                     });")

      ;; Wait for page reload and UI to update
      (is (e/wait-visible fx/*driver* logout-button-selector) "Logout button should be visible after login")
      (is (not (e/visible? fx/*driver* login-prompt-selector))
          "Login prompt should not be visible after login"))

    (testing "Perform logout"
      (e/click fx/*driver* logout-button-selector)

      ;; After clicking logout, the app should reactively update.
      (is (e/wait-visible fx/*driver* login-prompt-selector) "Login prompt should be visible after logout")
      (is (not (e/visible? fx/*driver* logout-button-selector))
          "Logout button should not be visible after logout"))))
