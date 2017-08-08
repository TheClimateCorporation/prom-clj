# prom-clj

This library is intended to provide a developer-friendly Clojure wrapper for Prometheus custom metrics.

## Metric Instructions

### Syntax Guidelines

Each metric must have a label, its name. The first word of the label should describe the relevant service, followed by a
description of the metric, and lastly a description of the metric units (e.g. `seconds`, `bytes`, or `total`),
formatted as: ```:service_metric_description_units```.

Examples:
	
	:api_request_duration_seconds

	:api_http_requests_total

Each metric must also have a help-string. This string should be a human-readable description the metric type and what
it tracks.
Examples:

    "Summary for seconds to complete an API request." 
    
    "Counter for total number of calls to API's http endpoint."
    
### Method Return Values

In order to make implementation as easy as possible, all metrics described below that take an `expr` argument will
return the result of `expr` (even in the case of a metric failure). This ensures that your service will run unhindered
while Prometheus gathers custom metrics.
    
### Counter

Counters are the simplest metric type. After initializing, counters can only increment. 
They are useful for tracking events and the rate of their occurrence (e.g. non-critical exceptions). 
To increment a counter, use:

    (increment-counter label help-string)
    
To increment a counter each time `expr` is called:

	(increment-counter label help-string expr)

To get the current value of a counter:

	(get-value label)

### Gauge

Gauges can increment or decrement, so they are useful for tracking values which can fluctuate (e.g. pending job requests):

	(increment-gauge label help-string expr)

	(decrement-gauge label help-string expr)
	
Gauges can also be set to any numerical value. To set a gauge to the result of `expr`, use:

	(set-gauge label help-string expr)

Just like counters and summaries, `get-value` will return the current value of the gauge:

	(get-value label)


### Summary

A summary samples observations (e.g. request durations or response sizes). Summaries provide a total count of 
observations, a sum of all observed values, and quantiles (percentile ranges) for their observations. 
The default quantiles are: 

    50th percentile with a 5% tolerated error (i.e. the mean of the 45th-55th percentile results)
    90th percentile with a 1% tolerated error
    99th percentile with a 0.1% tolerated error
    99.9th percentile with a 0.01% tolerated error
 
Summaries also have a max-age for their observations, the default for which is 10 minutes. Data that is older than
the max-age is no longer used to calculate quantiles.
    
Summaries are extremely practical for general-use metrics. To time an expression each time it is called, wrap it with:

	(time-summary label help-string expr)

Optional parameters are 
* custom max-age in seconds (e.g. `60000`)
* custom quantiles 
 (a map of double-pairs representing percentiles paired with tolerated errors, e.g. `{0.25 0.05, 0.75 0.05, 0.9 0.01}`)

Passed as such:

    (time-summary label help-string max-age expr)
    
    (time-summary label help-string max-age quantiles expr)

Please note that providing custom quantiles will replace the defaults, not append to them.
  
A summary can observe any numerical value. To observe the result of expr in a summary, use:

	(observe-summary value label help-string expr)

Custom max-age and custom quantiles are also available:

    (observe-summary label help-string max-age expr)
    (observe-summary label help-string max-age quantiles expr)
    
To get the current count of observed values and cumulative sum of observed values for a summary:

	(get-count label)
	
	(get-sum label)

### Histogram

A histogram samples observations (e.g. request durations or response sizes) and counts them in configurable buckets. 
The default bucket values are:
    
    0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
    
To observe an expression or value in a histogram, use:
    
    (observe-histogram label help-string expr)

To use custom buckets instead of the default, provide an array of doubles containing your desired buckets,
e.g. `[10.0, 25.0, 100.0]`:

    (observe-histogram label help-string buckets expr)


Timing an expression is accomplished similarly, using:
    
    (time-histogram label help-string expr)
    
    (time-histogram label help-string buckets expr)
      
Histograms are useful if you seek very specific categorization of a metric. For more details on histograms and summaries,
please see [Histograms and Summaries](https://prometheus.io/docs/practices/histograms/)

### Metric Errors

Since metrics errors are not service-critical for your project, errors from this package occur at the warning level.

## Enabling a /metrics endpoint

Prometheus is a pull-based monitoring system configured to scrape the `/metrics` endpoint of a service. In order to point
Prometheus to the correct registry, please configure your service so that the `/metrics` route returns a call to 
`(prom-clj/metricz)`.

## Accessing Your Custom Metrics

Once you have begun collecting metrics in your code and enabled a `/metrics` route,
you can enable your service's `/metrics` endpoint as a target for your Prometheus instance.

## Graphing your Custom Metrics

Creating persistent graphs of your custom metrics is best achieved through 
[Grafana](https://prometheus.io/docs/visualization/grafana/).





