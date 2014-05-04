(ns protean.transformations.testapi
  "Uses output from the analysis transformations to generate a
   datastructure which can drive automated testing.  This variant
   tests the live API surface area."
  (:require [clojure.string :as stg]
            [protean.transformations.coerce :as txco]
            [protean.transformations.test :as tst])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)]))

;; =============================================================================
;; Helper functions
;; =============================================================================

; TODO: refactor put in a common place
(defn substring? [sub st] (not= (.indexOf st sub) -1))

(defonce PSV "psv+")
(defonce PSV-EXP "psv\\+")
(defonce AZN "Authorization")

(defn- token [seed strat]
  (let [tokens (get-in seed [AZN])]
    (first (filter #(substring? strat %) tokens))))

; TODO: needs refactoring, trying to get a prototype out
; strat is either Basic or Bearer
(defn- header-authzn-> [strat seed payload]
  (let [m (last payload)]
    (if-let [auth (get-in m [:headers AZN])]
      (if (and (substring? PSV auth) (substring? strat auth))
        (if-let [sauth (token seed strat)]
          (let [n (assoc-in m [:headers AZN]
                            (str strat " " (last (stg/split sauth #" "))))]
            (list (first payload) (second payload) n))
          payload)
        payload)
      payload)))

(defn- v-swap [v seed]
  (if (substring? PSV v)
    (if-let [sv (get-in seed [(last (.split v PSV-EXP))])] sv v)
    v))

(defn- body-> [k seed payload]
  (let [m (last payload)]
    (if-let [qp (if (= k :body) (txco/clj-> (k m)) (k m))]
      (list
        (first payload)
        (second payload)
        (assoc m k
          (let [res (into {} (for [[k v] qp] [k (v-swap v seed)]))]
            (if (= k :body)
              (txco/js-> res)
              res))))
      payload)))

(defn- uri-namespace [uri]
  (-> uri (.split "/psv\\+") first (.split "/") last))

; TODO: weak, only handles 1 instance of uri placeholder
(defn- uri-> [seed payload]
  (let [uri (second payload)]
    (if (substring? (str "/" PSV) uri)
      (let [ns (str (uri-namespace uri) "/")
            v (first (filter #(substring? ns %) (vals seed)))]
        (if v
          (list (first payload)
                (stg/replace uri #"psv\+" (last (.split v "/")))
                (last payload))
          payload))
      payload)))

(defn- seed-> [test seed]
  (->> test
       (header-authzn-> "Basic" seed)
       (header-authzn-> "Bearer" seed)
       (body-> :query-params seed)
       (body-> :body seed)
       (uri-> seed)))

(defn- seeds [tests seed] (if seed (map #(seed-> % seed) tests) tests))

(defn- untestable-payload? [body k]
  (if-let [qp (if (= k :body) (txco/clj-> (k body)) (k body))]
    (if (first (filter #(substring? PSV %) (vals qp)))
      true
      false)
    false))

; does this test have any unseeded items ?
(defn- untestable? [test]
  (or
   (substring? (str "/" PSV) (second test))
   (if-let [auth (get-in (last test) [:headers AZN])]
     (if (substring? PSV auth) true false)
     false)
   (untestable-payload? (last test) :query-params)
   (untestable-payload? (last test) :body)))

(defn- result [result test]
  (-> result
      (conj (get-in (last test) [:codex :success-codes]))
      (conj (get-in (last test) [:codex :body-res]))))

(defn- test-results! [tests] (map #(result (tst/test! %) %) tests))

; TODO: basic - does not handle multiple body types, just json payload
; TODO: feed result items into bag as per before otherwise yet more mapping is needed
(defn- seed-stitch [seed res]
  (let [res-map (second res)]
    ; TODO: do this if we have successful test result - may have failed
    (cond
     (get-in res-map [:body])
     (let [b (txco/clj-> (get-in res-map [:body]))
           extraction-key (last res)
           extraction (get-in b [extraction-key])]
       (if (= extraction-key "access_token")
         (update-in seed ["Authorization"] conj (str "Bearer " extraction))
         (update-in seed ["bag"] conj extraction)))
     ;()
     :else seed)))

(defn- update-seed [seed payload] (assoc payload :seed seed))

(defn- update-results [res payload] (update-in payload [:results] concat res))

(defn- update-tests [new-tests payload] (assoc payload :tests new-tests))

(defn- update-state [state testable new-seed res new-tests]
  (->> state
       (update-seed new-seed)
       (update-results res)
       (update-tests new-tests)))

(defn- testable [{:keys [tests results]}]
  (let [testable (remove #(untestable? %) tests)
        tested (map #(first %) results)]
    (remove #(some #{(second %)} tested) testable)))

(defn- test! [state]
  (let [testable-tests (testable state)]
    (if (empty? testable-tests)
      state
      (test!
       (let [res (test-results! testable-tests)
             new-seed (reduce seed-stitch (:seed state) res)
             new-tests (seeds (:tests state) new-seed)]
         (update-state state testable-tests new-seed res new-tests))))))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn testapi-analysis-> [host port codices corpus]
  (info "testing the API")
  (let [seed (corpus "seed")
        tests (tst/test-> host port codices corpus)
        seeded (seeds tests seed)]
    (let [res (test! {:tests seeded :seed seed :results []})]
      res)))
