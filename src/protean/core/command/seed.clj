(ns protean.core.command.seed
  "Replace placeholder values a client provided set of values, grow seed
   values when incrementally negotiating (workflows etc)."
  (:require [clojure.string :as s]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.coerce :as txco]
            [protean.core.codex.placeholder :as ph]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defonce PSV-EXP "psv\\+")

(defn- token [seed strat]
  (let [tokens (get-in seed [h/azn])]
    (first (filter #(.contains % strat) tokens))))

; TODO: needs refactoring, trying to get a prototype out
; strat is either Basic or Bearer
(defn- header-authzn-> [strat seed [method uri mp :as payload]]
  (if-let [auth (get-in mp [:headers h/azn])]
    (if (and (.contains auth ph/ph) (.contains auth strat))
      (if-let [sauth (token seed strat)]
        (let [n (assoc-in mp [:headers h/azn]
                  (str strat " " (last (s/split sauth #" "))))]
          (list method uri n))
        payload)
      payload)
    payload))

(defn- substr? [s sub] (if s (.contains s sub) false))

(defn- bag-item [v seed]
  (let [ns (first (.split v "/psv\\+"))]
    (first (filter #(substr? % (str ns "/")) (get-in seed ["bag"])))))

; first search in first class seed items, then in the bag
(defn- holder-swap [seed k v tree]
  (if (ph/holder? v)
    (if-let [sv (get-in seed [(last (.split v PSV-EXP))])]
      [sv :seed]
      (if-let [sv (bag-item v seed)] [sv "seed"] [v :idn]))
    [v :idn]))

(defn- tx-payload-map [k mp res]
  (-> mp
      (assoc k (ph/encode-value k (first res)))
      (update-in [:codex :ph-swaps] conj (second res))
      (update-in [:codex :ph-swaps] vec)))

(defn- swap-placeholders [k seed [method uri mp options :as payload]]
  (if-let [phs (ph/encode-value k (k mp))]
    (let [swap-mp (merge seed mp)
          swapped (ph/holders-swap phs (partial holder-swap seed) k :seed (:tree options))]
      (list method uri (tx-payload-map k mp swapped)))
    payload))

; TODO: weak, only handles 1 instance of uri placeholder
(defn- uri-> [seed [method uri mp :as payload]]
  (if (ph/holder? uri)
    (let [v (ph/uri-ns-holder uri)
          sv (bag-item v seed)
          new-uri (if sv (s/replace uri #"psv\+" (last (.split sv "/"))) uri)]
      (if sv
        (let [raw (update-in mp [:codex :ph-swaps] conj "seed")
              ph-map (update-in raw [:codex :ph-swaps] vec)]
          (list method new-uri ph-map))
        payload))
    payload))

(defn- seed-> [test seed]
  (let [new-test
        (->> test
             (header-authzn-> "Basic" seed)
             (header-authzn-> "Bearer" seed)
             (swap-placeholders :query-params seed)
             (swap-placeholders :body seed)
             (swap-placeholders :form-params seed)
             (uri-> seed))]
    new-test))

(defn seeds [tests seed]
  (if seed (map #(seed-> % seed) tests) tests))
