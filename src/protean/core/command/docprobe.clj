(ns protean.core.command.docprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [ring.util.codec :as cod]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.core.codex.document :as d]
            [protean.core.codex.placeholder :as ph]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.core.command.test :as t]
            [protean.core.command.probe :as pb])
  (:import java.io.File java.net.URI java.util.UUID))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [t] (println (aa/bold-red t)))
(defn- hlg [t] (println (aa/bold-green t)))

(defn- bomb [msg]
  (println (aa/red msg))
    (System/exit 0))

(defn- prep-docs [{:keys [directory]}]
  (if (not directory)
    (bomb "please provide \"directory\" config to generate docs")
    (if (dsk/exists-dir? directory)
      (do (dsk/delete-directory (file directory))
          (.mkdirs (file (str directory "/api"))))
      (.mkdirs (file (str directory "/api"))))))

(defn spit-to
  "Will make directory if does not exist before spitting to file."
  [target content]
  (.mkdirs (file (.getParent (.getAbsoluteFile (File. target)))))
  (spit target content))

;; =============================================================================
;; Probe config
;; =============================================================================

(defmethod pb/config :doc [_ corpus] (hlg "building probes"))

;; =============================================================================
;; Probe construction
;; =============================================================================

(defn- doc-params [target-dir params]
  "Doc query params for a given node.
   target-dir is the directory to write to.
   Params is the gen information for a resources params."
  (.mkdirs (File. target-dir))
  (doseq [[k v] params]
    (let [qm {:title k :type (:type v) :doc (:doc v)}]
      (spit (str target-dir (UUID/randomUUID) ".edn") (pr-str qm)))))

(defn- doc-hdrs [target-dir hdrs]
  "Doc headers for a given node.
   target-dir is the directory to write to.
   hdrs is the codex rsp headers."
  (.mkdirs (File. target-dir))
  (doseq [[k v] hdrs]
    (spit (str target-dir (UUID/randomUUID) ".edn")
          (pr-str {:title k :value v}))))

(defn- body-example [tree v] (if-let [bf (:body-example v)] (slurp bf) "N/A"))

(defn- doc-status-codes [target-dir tree statuses]
  "Doc response headers for a given node.
   Directory is the data directory root.
   Resource is the current endpoint (parent of headers).
   filter-exp is a regular expression to match the status codes to include."
  (.mkdirs (File. target-dir))
  (doseq [[k v] statuses]
    (spit (str target-dir (name k) ".edn")
      (pr-str { :code (name k)
                :doc (:doc v)
                :sample-response (body-example tree v)
                :headers (if-let [h (:headers v)] (pr-str h) "N/A")}))))

(defn- input-params [tree uri]
  (let [inputs (concat
                 (list uri)
                 (map val (d/get-in-tree tree [:req :query-params :required]))
                 (map val (d/get-in-tree tree [:req :query-params :optional]))
                 (map val (d/get-in-tree tree [:req :body]))
                 (map val (d/get-in-tree tree [:req :headers])))
        extract-ph-names (fn [input]
            (map #(nth % 1) (ph/holder? input)))
        ph-names (filter identity (reduce concat (map extract-ph-names inputs)))
        to-map (fn [varname] {varname (d/get-in-tree tree [:vars varname])})]
  (reduce merge (map to-map ph-names))))

(defmethod pb/build :doc [_ {:keys [locs directory] :as corpus} entry]
  (println "building a doc probe to visit " (:method entry) ":" locs)
  (prep-docs corpus)
  {:entry entry
   :engage (fn []
    (let [{:keys [svc method tree path] :as e} entry
          uri (p/uri "host" 1234 svc path)
          safe-uri (fn [uri] (ph/replace-all-with uri #(str "_" % "_")))
          uri-path (-> (URI. (safe-uri uri)) (.getPath))
          id (str (name method) (stg/replace uri-path #"/" "-"))
          main (filter #(get-in % [:title]) tree)
          site {:site-name (d/get-in-tree main [:title])
                :site-doc (if-let [d (d/get-in-tree main [:doc])] d "")}
          full {:id id
                :path (subs uri-path 1)
                :curl (cod/url-decode (c/curly-> (assoc-in e [:uri] uri)))
                :doc (d/get-in-tree tree [:doc])
                :desc (if-let [d (d/get-in-tree tree [:description])] d "")
                :method (name method)}]
      (spit-to (str directory "/global/site.edn") (pr-str site))
      (spit-to (str directory "/api/" id ".edn") (pr-str full))
      (doc-params (str directory "/" id "/params/") (input-params tree uri))
      (doc-hdrs (str directory "/" id "/headers/") (d/get-in-tree tree [:rsp :headers]))
      (doc-status-codes (str directory "/" id "/status-codes-success/") tree (d/success-status tree))
      (doc-status-codes (str directory "/" id "/status-codes-error/") tree (d/error-status tree))))
  })

;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defmethod pb/dispatch :doc [_ corpus probes]
  (hlg "dispatching probes")
  (doall (map (fn [x] [(:entry x) ((:engage x))]) probes)))


;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defmethod pb/analyse :doc [_ corpus results]
  (hlg "analysing probe data")
  (let [path (.getAbsolutePath (file (:directory corpus)))
        silk-path (subs path 0 (.indexOf path (str (dsk/fs) "data" (dsk/fs))))]
    (silk/spin-or-reload false silk-path false false)))

