(ns protean.core.command.generate
  "Generate values for placeholders, only when integration testing."
  (:refer-clojure :exclude [long int])
  (:require [protean.core.transformation.coerce :as c]
            [protean.core.codex.placeholder :as p]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- track-payload-swap [k mp res]
  (-> mp
      (assoc k (first res))
      (update-in [:codex :ph-swaps] conj (second res))
      (update-in [:codex :ph-swaps] vec)))

(defn- swap-placeholders [k tree [method uri mp :as payload]]
  (if-let [phs (p/encode-value k (k mp))]
    (let [swapped (p/holders-swap phs p/holder-swap-gen mp k :vars tree)
          svs (if (= k :body) (c/js (first swapped)) (first swapped))
          res [svs (last swapped)]]
      (list method uri (track-payload-swap k mp res)))
    payload))

(defn- uri [codices tree payload]
  (let [uri (second payload)]
    (if (p/uri-ns-holder? uri)
      (let [v (p/uri-ns-holder uri)]
        (p/holder-swap-uri v payload tree))
      payload)))

(defn- generate [test codices]
  (->> test
       (swap-placeholders :query-params (:tree (last test)))
       (swap-placeholders :body (:tree (last test)))
       (uri codices (:tree (last test)))))

(defn generations [codices tests]
  (map #(generate % codices) tests))
