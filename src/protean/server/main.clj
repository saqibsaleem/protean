(ns protean.server.main
  "Entry point into the server component.  Config, server and routes."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.multipart-params :as mp]
            [compojure.core :refer [defroutes ANY DELETE GET POST PUT]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [cemerick.pomegranate :as pom]
            [me.rossputin.diskops :as do]
            [protean.config :as conf]
            [protean.server.pipeline :as pipe]
            [protean.api.transformation.coerce :as co]
            [clojure.pprint]
            [taoensso.timbre.appenders.core :as appenders]
            [hawk.core :as hawk])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.File)
  (:gen-class))

;; =============================================================================
;; Helper functions
;; =============================================================================

(timbre/merge-config! {:timestamp-opts {:pattern "yyyy-MM-dd HH:mm:ss.SSS"}})
(timbre/merge-config! {:appenders {:spit (appenders/spit-appender {:fname
  (str (conf/log-dir) "/protean.log")})}})
(timbre/set-level! (conf/log-level))

(defn- files [c-dir ext]
  (-> (remove #(.isDirectory %) (.listFiles (io/file c-dir)))
      (do/filter-exts [ext])))

(defmacro version [] (System/getProperty "protean.version"))


;; =============================================================================
;; Routes
;; =============================================================================

(defroutes admin-routes
  (route/files "/public" {:root (conf/public-dir)})
  (GET    "/services" [] (pipe/services))
  (GET    "/services/:id" [id] (pipe/service id))
  (GET    "/services/:id/analysis" [id] (pipe/service-analysis id conf/host))
  (GET    "/services/:id/usage" [id] (pipe/service-usage id conf/host))
  (PUT    "/services" req (pipe/put-services req))
  (DELETE "/services/:id" [id] (pipe/del-service-handled id))
  (GET    "/sims" [] (pipe/sims-names))
  (PUT    "/sims" req (pipe/put-sims req))
  (DELETE "/sims/:id" [id] (pipe/del-sim-handled id))
  (GET    "/status" [] (pipe/status))
  (GET    "/version" [] (pipe/version (version))))

(defroutes api-routes
  (ANY "*" req (pipe/api req)))


;; =============================================================================
;; Server setup
;; =============================================================================

(defn wrap-ignore-trailing-slash
  "If the requested url has a trailing slash, remove it."
  [handler]
  (fn [request]
    (handler (update-in request [:uri] s/replace #"(?<=.)/$" ""))))

(defn- server [sim-port sim-max-threads admin-port admin-max-threads]
  (when-not (conf/sim-server?)
    (jetty/run-jetty
      (-> admin-routes mp/wrap-multipart-params)
      { :port (co/int admin-port)
        :join? false
        :max-threads (co/int admin-max-threads)})
    (info "Protean Admin Server has started Admin Port:" admin-port "Max threads:" admin-max-threads))
  (when-not (conf/admin-server?)
    (jetty/run-jetty
      (-> api-routes handler/api mp/wrap-multipart-params wrap-ignore-trailing-slash)
      { :port (co/int sim-port)
        :join? false
        :max-threads (co/int sim-max-threads)})
    (info "Protean Sim Server has started Sim Port:" sim-port "Max threads:" sim-max-threads)))


;; =============================================================================
;; Application entry point
;; =============================================================================

(defn start [codex-dir reload]
  (timbre/log-and-rethrow-errors
    (let [sim-port (conf/sim-port)
          sim-max-threads (conf/sim-max-threads)
          admin-port (conf/admin-port)
          admin-max-threads (conf/admin-max-threads)
          c-dir (or codex-dir (conf/codex-dir))
          ;; configure classpath for this instance of protean
          ;; we currently support local clj artefacts and remote coords (e.g. clojars)
          ;; TODO: support local jar files in a directory
          _ (pom/add-classpath c-dir)
          _ (pom/add-classpath (str (io/file c-dir "clj")))
          cods (mapv pipe/load-codex (files c-dir "cod.edn"))
          sims (mapv pipe/load-sim   (files c-dir "sim.edn"))
          cod? #(s/ends-with? (.getPath %) ".cod.edn")
          sim? #(s/ends-with? (.getPath %) ".sim.edn")
          clj? #(s/ends-with? (.getPath %) ".clj")
          hnd (fn [ctx {f :file kind :kind}]
                (when-let [msg (cond
                    (and (.exists f) (cod? f)) (str "loaded codex: "  (.getName f) " - " (pipe/load-codex f))
                    (and (.exists f) (sim? f)) (str "loaded sim: "    (.getName f) " - " (pipe/load-sim f))
                    (and (.exists f) (clj? f)) (str "loaded clj: "    (.getName f) " - " (pom/add-classpath f))
                    (cod? f)                   (str "removed codex: " (.getName f) " - " (pipe/unload-codex f))
                    (sim? f)                   (str "removed sim: "   (.getName f) " - " (pipe/unload-sim f))
                    ; (clj? f)                   (str "remove clj:"     (.getName f) (pom/remove-classpath f))
                    :else                             nil)]
                  (println msg "Watching for changes. Press enter to exit"))
                ctx)]
      (info "Starting protean - v" (version))
      (info "Codex directory:" c-dir)
      (info "Codices loaded:" (s/join ", " cods))
      (info "Sim extensions loaded:" (s/join ", " sims))
      (info "Public static resources can be served from:" (conf/public-dir))
      (server sim-port sim-max-threads admin-port admin-max-threads)
      (when reload
        (println "Watching for changes. Press enter to exit")
        (hawk/watch! [{:paths [c-dir]
                       :handler hnd}])
        (loop [input (read-line)]
          (when-not (= "\n" input)
            (System/exit 0)
            (recur (read-line))))))))

(defn -main [& args] (start nil nil))
