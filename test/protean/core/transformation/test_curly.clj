(ns protean.core.transformation.test-curly
  (:require [clojure.string :refer [split]]
            [protean.core.transformation.curly :refer [curly-analysis->]]
            [expectations :refer :all]))

;; =============================================================================
;; Testing transformation from analysis structure to curl command
;; =============================================================================

(let [analysed '(
  {:method :get
   :uri "http://localhost:3000/sample/simple"
   :tree [nil
          {:get nil}
          {"simple" {:get nil}} {:rsp {:200 {:doc "OK"}}}
          {"sample" {"simple" {:get nil}} :get {:rsp {:200 {:doc "OK"}}}}]})
     [cmd options uri] (split (first (curly-analysis-> analysed)) #" ")]
  (expect "curl" cmd)
  (expect "-i" options)
  (expect true (.endsWith uri "/sample/simple'")))

(let [analysed '(
  {:method :get
   :uri "http://localhost:3000/curly/query-params"
   :tree [{:rsp {:200 {:body-examples ["test/resources/200-ref.json"]}}
           :req {:query-params {"blurb" ["${blurb}" :required]}}
           :vars {"blurb" {:doc "A sample request param", :type :String}}}
          {:get {
             :rsp {:200 {:body-examples ["test/resources/200-ref.json"]}}
             :req {:query-params {"blurb" ["${blurb}" :required]}}
             :vars {"blurb" {:doc "A sample request param", :type :String}}}}
          {"query-params" {
             :get {
               :rsp {:200 {:body-examples ["test/resources/200-ref.json"]}}
               :req {:query-params {"blurb" ["${blurb}" :required]}}
               :vars {"blurb" {:doc "A sample request param", :type :String}}}}}
               :get {
          {:rsp {:200 {:doc OK}}}
          {"curly" {
             "query-params" {
                 :rsp {:200 {:body-examples ["test/resources/200-ref.json"]}}
                 :req {:query-params {"blurb" ["${blurb}" :required]}}
                 :vars {"blurb" {:doc "A sample request param", :type :String}}}}}
                 :types {:String "[a-zA-Z0-9]+"}
                 :get {:rsp {:200 {:doc "OK"}}}}]})
      [cmd options uri] (split (first (curly-analysis-> analysed)) #" ")]
  (expect "curl" cmd)
  (expect "-i" options)
  (expect true (.contains uri "?blurb=")))
