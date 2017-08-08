(ns prom.clj.core
    (:require
    [clojure.string :as s]
    [clojure.tools.logging :as log])
    (:import
    [io.prometheus.client.exporter.common TextFormat]
    [io.prometheus.client Counter
                          Gauge
                          Histogram
                          Summary
                          CollectorRegistry]))

;; All metrics are written to this Prometheus client registry.
(def registry (atom (CollectorRegistry.)))

;; This map keeps a running list of existing metrics so that they don't have to be initialized by hand.
(def ^:private existing-metrics (atom {}))

;; The default max age (in seconds) and quantile settings for summary metrics.
(def default-max-age 600)
(def default-quantiles {0.5 0.1
                        0.9 0.01
                        0.99 0.001
                        0.999 0.0001})

(defn- add-tags
  "Add tags to a metric viz. name, help-string etc."
  [metric name help-string]
  (doto
    metric
    (.name name)
    (.help help-string)))

(defn- add-quantile
  [summary [quantile error]]
  (.quantile summary quantile error))

(defn- add-tags-summary
  "Add custom tags to a summary metric viz. name, quantiles, etc."
  [metric name help-string max-age quantiles]
  (doto
    metric
    (.name name)
    (.help help-string)
    (.maxAgeSeconds max-age))
  (reduce add-quantile metric quantiles))

(defn create-counter
  "Creates an instance of ip.prometheus.client.Counter.
   Name defines the label name of the metric, and a humanly readable helper string."
  [registry name help-string]
  (let [name (clojure.core/name name)
        builder (add-tags (Counter/build) name help-string)]
    (.register builder registry)))

(defn create-gauge
  "Creates an instance of ip.prometheus.client.Gauge.
   Name defines the label name of the metric, and a humanly readable helper string."
  [registry name help-string]
  (let [name (clojure.core/name name)
        builder (add-tags (Gauge/build) name help-string)]
    (.register builder registry)))

(defn create-histogram
  "Creates an instance of ip.prometheus.client.Histogram. Name defines the label name for the metric, along with a humanly
  readable help-string and a double-array of buckets."
  [registry name help-string buckets]
  (let [name (clojure.core/name name)
        builder (add-tags (Histogram/build) name help-string)]
    (when buckets
      (.buckets builder (double-array buckets)))
    (.register builder registry)))

(defn create-summary
  "Creates an instance of ip.prometheus.client.Summary. Name defines the label name for the metric, along with a humanly
  readable help-string, an int max-age defining the max age (in seconds) of data used to calculate quantiles, and
  a map of double pairs (each is a quantile and tolerated error) defining the quantiles."
  [registry name help-string max-age quantiles]
  (let [name (clojure.core/name name)
       builder (add-tags-summary (Summary/build) name help-string max-age quantiles)]
    (.register builder registry)))

(defn inspect-create
  "Checks to see if the given label exists as a metric. If so, returns the metric; otherwise it creates and returns
  a new metric with the given label. Valid labels must be keywords with underscores, and the first word should describe
  the metric's namespace (e.g. :namespace_request_duration_seconds)."
  [create-metric label & rest]
  (let [metric (get @existing-metrics label)]
    (if metric
      metric
    (do (reset! existing-metrics (assoc @existing-metrics label (apply create-metric @registry label rest)))
        (get @existing-metrics label)))))


(defn get-value
  "Returns the value of the counter, gauge, or summary identified by label. Primarily useful for testing purposes."
  [label]
  (try
    (->> @existing-metrics label .get)
    (catch Exception e
      (if (get @existing-metrics label)
        (log/warnf e "Unable to get the value of metric %s" label)
        (log/warnf e "The metric %s does not exist. Are you sure that is has been called yet?" label)))))

(defn increment-counter
  "Increments the counter identified by label and returns the result of expr, or true if no expr is provided."
  ([label help-string]
   (increment-counter true label help-string))
  ([label help-string expr]
   (try
     (let [metric (inspect-create create-counter label help-string)]
       (.inc metric)
       expr)
     (catch Exception e
       (log/warnf e "Unable to increment the counter %s" label)
       expr))))

(defn increment-gauge
  "Increments the gauge identified by label and returns the result of expr, or true if no expr is provided."
  ([label help-string]
   (increment-gauge true label help-string))
  ([label help-string expr]
   (try
     (let [metric (inspect-create create-gauge label help-string)]
       (.inc metric)
       expr)
     (catch Exception e
       (log/warnf e "Unable to increment the gauge %s" label)
       expr))))

(defn decrement-gauge
  "Decrements the gauge identified by label and returns the result of expr, or true if no expr is provided."
  ([label help-string]
   (decrement-gauge true label help-string))
  ([label help-string expr]
   (try
     (let [metric (inspect-create create-gauge label help-string)]
       (.dec metric)
       expr)
     (catch Exception e
       (log/warnf e "Unable to decrement the gauge %s" label)
       expr))))

(defn set-gauge
  "Sets the gauge, identified by label, to the result of expr (result must be a number). Returns the result of expr."
  [label help-string expr]
   (try
     (let [metric (inspect-create create-gauge label help-string)]
       (.set metric expr)
       expr)
     (catch Exception e
       (log/warnf e "Unable to set the gauge %s to value %s. Please ensure that the value is a number." label expr)
       expr)))

(defn observe-summary
  "Observes the result of expr in the summary identified by label (result must be a number), and returns the result of expr. "
  ([label help-string expr]
   (observe-summary label help-string default-max-age default-quantiles expr))
  ([label help-string max-age expr]
   (observe-summary label help-string max-age default-quantiles expr))
  ([label help-string max-age quantiles expr]
   (try
     (let [metric (inspect-create create-summary label help-string max-age quantiles)]
       (.observe metric expr)
       expr)
     (catch Exception e
       (log/warnf e "Unable to observe the value %s in summary %s. Please ensure that value is a number." expr label)
       expr))))

(defn get-count
  "Returns the count of observations to the summary identified by label."
  [label]
  (try
    (->> @existing-metrics label .get .count)
    (catch Exception e
      (if (get @existing-metrics label)
        (log/warnf e "Unable to get the count of observed values for summary %s" label)
        (log/warnf e "The summary %s does not exist. Are you sure that it has been called yet?" label)))))

(defn get-sum
  "Returns the sum of observations to the summary identified by label."
  [label]
  (try
    (->> @existing-metrics label .get .sum)
    (catch Exception e
      (if (get @existing-metrics label)
        (log/warnf e "Unable to get the sum of observed values for summary %s" label)
        (log/warnf e "The summary %s does not exist. Are you sure that it has been called yet?" label)))))

;; If a custom bucket array is not given, nil is passed as the bucket param to trigger the default buckets.
(defn observe-histogram
  "Observes value in the histogram identified by label, and returns the result of expr."
  ([label help-string expr]
   (observe-histogram label help-string nil expr))
  ([label help-string buckets expr]
   (try
     (let [metric (inspect-create create-histogram label help-string buckets)]
       (.observe metric expr)
       expr)
     (catch Exception e
       (log/warnf e "Unable to observe the value %s in histogram %s" expr label)
       expr))))

;; Multi-arity to accommodate for the user inputting custom max-age or custom quantiles.
(defmacro time-summary
  "Observe the time of the body and write to summary identified by label, with custom max-age and quantiles as desired.
  Returns the result of expr."
  ([label help-string expr]
   `(time-summary ~label ~help-string ~default-max-age ~default-quantiles ~expr))
  ([label help-string expr max-age]
   `(time-summary ~label ~help-string ~max-age ~default-quantiles ~expr))
  ([label help-string max-age quantiles expr]
  `(try
     (let [timer# (.startTimer (inspect-create create-summary ~label ~help-string ~default-max-age ~default-quantiles))
           ret# ~expr]
       (.observeDuration timer#)
       ret#)
     (catch Exception e#
       (log/warnf e# "Unable to time the expression in the summary" ~label)
       ~expr))))

(defmacro time-histogram
  "Observe the time of the body and write to summary identified by label, with custom max-age and quantiles as desired.
  Returns the result of expr."
  ([label help-string expr]
   `(time-histogram ~label ~help-string nil ~expr))
  ([label help-string buckets expr]
  `(try
     (let [timer# (.startTimer (inspect-create create-histogram ~label ~help-string ~buckets))
           ret# ~expr]
       (.observeDuration timer#)
       ret#)
     (catch Exception e#
       (log/warnf e# "Unable to time the expression in the histogram" ~label)
       ~expr))))

(defn current-metrics
  "Prints a list with the names of currently existing metrics and returns a map containing the metrics."
  []
  (let [metrics @existing-metrics]
    (println (keys metrics))
    metrics))

(defn metricz
  "Render a metrics html page."
  []
  {:status 200
   :content-type TextFormat/CONTENT_TYPE_004
   :body (with-out-str
           (TextFormat/write004
             *out*
             (.metricFamilySamples @registry)))})