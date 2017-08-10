(ns prom.clj.test.core
    "Unit tests for prometheus metrics."
    (:require
    [clojure.tools.logging :as log]
    [clojure.test :refer :all]
    [prom.clj.core :as prometheus]
    [midje.sweet :refer :all])
    (:import
    [io.prometheus.client Counter]))


(def metrics-registry @prometheus/registry)

(facts "Test exception protocol for metrics."
       (fact "Counter, gauge, histogram and summary are all created as desired."
             (let [counter (prometheus/create-counter metrics-registry "test_counter" "This is a test counter")
                   gauge (prometheus/create-gauge metrics-registry "test_gauge" "This is a test gauge")
                   histogram (prometheus/create-histogram metrics-registry "test_histogram" "This is a test histogram." (double-array [0.5 0.9]))
                   summary (prometheus/create-summary metrics-registry "test_summary" "This is a test summary"
                                                      600 {0.5 0.1})]
               (and (not (nil? counter)) (not (nil? gauge)) (not (nil? histogram)) (not (nil? summary))) => true))

       (fact "Expression is returned even on Prometheus error."
             (let [label :this-is-invalid
                   expr (* 3 8)]
               (prometheus/time-summary label "Label name is invalid." expr) => 24))

       (fact "Exception is logged when method is called on wrong type of metric; expression is still returned."
             (let [counter :valid_counter
                   expr (* 3 8)]
               (prometheus/increment-counter counter "This is a valid counter." expr)
               (prometheus/increment-counter counter "This is a valid counter.")
               (prometheus/get-value counter) => 2.0
               (prometheus/set-gauge counter "Can not set a counter." expr) => 24))

       (fact "Timer operates as expected."
             (do
               (prometheus/time-summary :summary1 "This is a shorter time." (Thread/sleep 10))
               (prometheus/time-summary :summary2 "This is a longer time." (Thread/sleep 50))
               (prometheus/time-summary :summary3 "This is the longest time." (Thread/sleep 100))
               (> (prometheus/get-sum :summary2) (prometheus/get-sum :summary1)) => true
               (> (prometheus/get-sum :summary3) (prometheus/get-sum :summary2)) => true
               (prometheus/get-count :summary1) => 1.0))

       (fact "Test type-check for set-gauge."
             (let [expr1 nil
                   expr2 "example string"]
               (prometheus/set-gauge :valid_gauge "The expr does not result in a number." expr1) => expr1
               (prometheus/set-gauge :valid_gauge "The expr does not result in a number." expr2) => expr2))

       (fact "Value of a gauge is set correctly."
             (let [label :valid_gauge
                   expr (* 3 8)]
               (prometheus/set-gauge label "This is a test gauge." 10)
               (prometheus/get-value label) => 10.0
               (prometheus/set-gauge label "This is a test gauge." expr)
               (prometheus/get-value label) => 24.0
               (prometheus/increment-gauge label "This is a test gauge.")
               (prometheus/decrement-gauge label "This is a test gauge.")
               (prometheus/get-value label) => 24.0))

       (fact "current-metrics behaves as desired."
             (not (nil? (get (prometheus/current-metrics) :valid_gauge))) => true)

       (fact "Histograms behave as desired."
             (prometheus/observe-histogram :valid_histogram "This is a test histogram." (* 3 8))
             (prometheus/observe-histogram :valid_histogram2 "Test histogram #2." [10 100] (* 3 800))
             (println (.collect (get (prometheus/current-metrics) :valid_histogram2)))
             (not (nil? (.collect (get (prometheus/current-metrics) :valid_histogram)))) => true
             (not (nil? (.collect (get (prometheus/current-metrics) :valid_histogram2)))) => true))