(ns frontend.components.build-timings
  (:require [om.core :as om :include-macros true]
            [goog.string :as gstring]
            [frontend.models.build :as build])
  (:require-macros [frontend.utils :refer [html]]))

(def padding-right 20)

(def top-axis-height 20)
(def left-axis-width 35)

(def bar-height 20)
(def bar-gap 10)
(def container-bar-height (- bar-height bar-gap))

(def min-container-rows 4)

(defn timings-width []  (-> (.querySelector js/document ".build-timings")
                            (.-offsetWidth)
                            (- padding-right)
                            (- left-axis-width)))

(defn timings-height [number-of-containers]
  (let [number-of-containers (if (< number-of-containers min-container-rows)
                               min-container-rows
                               number-of-containers)]
  (* (inc number-of-containers) bar-height)))

;;; Helpers
(defn create-x-scale [start-time stop-time]
  (let [start-time (js/Date. start-time)
        stop-time  (js/Date. stop-time)]
    (-> (js/d3.time.scale)
        (.domain #js [start-time stop-time])
        (.range  #js [0 (timings-width)]))))

(defn create-root-svg [number-of-containers]
  (-> (.select js/d3 ".build-timings")
      (.select "svg")
        (.attr "width"  (+ (timings-width) left-axis-width padding-right))
        (.attr "height" (+ (timings-height number-of-containers) top-axis-height))
      (.append "g") ;move everything over to see the axis
        (.attr "transform" (gstring/format "translate(%d,%d)" left-axis-width top-axis-height))))

(defn time-axis-tick-formatter [ms-value]
  (let [m (js/Math.floor (/ ms-value 1000 60))
        s (js/Math.floor (- (/ ms-value 1000) (* m 60)))]
    (if (<= m 0)
      (if (= s 0)
        (gstring/format "%ds" s)
        (gstring/format "%02ds" s))
      (gstring/format "%d:%02dm" m s))))

(defn create-y-axis [number-of-containers]
  (let [range-start (+ bar-height (/ container-bar-height 2))
        range-end   (+ (timings-height number-of-containers) (/ container-bar-height 2))
        axis-scale  (-> (js/d3.scale.linear)
                        (.domain #js [0 number-of-containers])
                        (.range  #js [range-start range-end]))]
  (-> (js/d3.svg.axis)
      (.tickValues (clj->js (range 0 number-of-containers)))
      (.scale axis-scale)
      (.tickFormat #(js/Math.floor %))
      (.orient "left"))))

(defn create-x-axis [build-duration]
  (let [axis-scale   (-> (js/d3.scale.linear)
                         (.domain #js [0 build-duration])
                         (.range  #js [0 (timings-width)]))]
  (-> (js/d3.svg.axis)
      (.tickValues (clj->js (range 0 (inc build-duration) (/ build-duration 4))))
      (.scale axis-scale)
      (.tickFormat time-axis-tick-formatter)
      (.orient "top"))))

(defn build-duration [start-time stop-time]
  (- (.getTime (js/Date. stop-time)) (.getTime (js/Date. start-time))))

(defn container-position [step]
  (* bar-height (inc (aget step "index"))))

(defn scaled-time [x-scale step time-key]
  (x-scale (js/Date. (aget step time-key))))

;;; Elements of the visualization
(defn draw-containers! [x-scale step]
  (let [step-length      #(- (scaled-time x-scale % "end_time")
                             (scaled-time x-scale % "start_time"))
        step-start-pos   #(x-scale (js/Date. (aget % "start_time")))]
    (-> step
        (.selectAll "rect")
          (.data #(aget % "actions"))
        (.enter)
          (.append "rect")
          (.attr "class"  "container-step")
          (.attr "width"  step-length)
          (.attr "height" container-bar-height)
          (.attr "y"      container-position)
          (.attr "x"      step-start-pos))))

(defn draw-step-start-line! [x-scale step]
  (let [step-start-position #(scaled-time x-scale % "start_time")]
  (-> step
      (.selectAll "line")
        (.data #(aget % "actions"))
      (.enter)
        (.append "line")
        (.attr "class" "container-step-start-line")
        (.attr "x1"    step-start-position)
        (.attr "x2"    step-start-position)
        (.attr "y1"    container-position)
        (.attr "y2"    #(+ (container-position %)
                           container-bar-height)))))

(defn draw-steps! [x-scale chart steps]
  (let [steps-group (-> chart
                        (.append "g"))

        step (-> steps-group
                 (.selectAll "g")
                   (.data (clj->js steps))
                 (.enter)
                   (.append "g"))]
    (draw-step-start-line! x-scale step)
    (draw-containers! x-scale step)))

(defn draw-label! [chart number-of-containers]
  (-> chart
      (.append "text")
      (.attr "class" "y-axis-label")
      (.attr "transform" (gstring/format "translate(%d,%d) rotate(%d)" -25 90 -90))
      (.text "CONTAINERS")))

(defn draw-axis! [chart axis class-name]
  (-> chart
      (.append "g")
      (.attr "class" class-name)
      (.call axis)))

(defn draw-chart! [{:keys [parallel steps start_time stop_time] :as build}]
  (let [x-scale (create-x-scale start_time stop_time)
        chart   (create-root-svg parallel)
        x-axis  (create-x-axis (build-duration start_time stop_time))
        y-axis  (create-y-axis parallel)]
    (draw-axis!  chart x-axis "x-axis")
    (draw-axis!  chart y-axis "y-axis")
    (draw-label! chart parallel)
    (draw-steps! x-scale chart steps)))

;;;; Main component
(defn build-timings [build owner]
  (reify
    om/IInitState
    (init-state [_]
      {:drawn? false})
    om/IDidMount
    (did-mount [_]
      (om/set-state! owner :drawn? true)
      (draw-chart! build))
    om/IRenderState
    (render-state [_ _]
      (html
       [:div.build-timings
        [:svg]]))))
