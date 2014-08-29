(ns protean.core.command.generate
  "Generate values for placeholders, only when integration testing."
  (:refer-clojure :exclude [long int])
  (:require [protean.core.transformation.coerce :as c]
            [protean.core.codex.placeholder :as p]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- swap-placeholders [k [method uri mp :as payload]]
  (if-let [phs (p/encode-value k (k mp))]
    (let [swapped (p/holders-swap phs p/holder-swap-gen mp)
          swp (if (= k :body) (c/js-> swapped) swapped)]
      (list method uri (assoc mp k swp)))
    payload))

(defn- uri [codices payload]
  (let [uri (second payload)]
    (if (p/uri-ns-holder? uri)
      (let [v (p/uri-ns-holder uri)]
        (p/holder-swap-uri v payload))
      payload)))

(defn- generate [test codices]
  (->> test
       (swap-placeholders :query-params)
       (swap-placeholders :body)
       (uri codices)))

(defn generations [codices tests] (map #(generate % codices) tests))
