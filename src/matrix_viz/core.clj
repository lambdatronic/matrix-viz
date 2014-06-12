(ns matrix-viz.core
  (:require [clojure.java.io :as io]
            [clojure.core.matrix :as m])
  (:import (java.awt Color Graphics)
           (java.awt.image BufferedImage)
           (javax.imageio ImageIO)
           (java.io IOException)))

(m/set-current-implementation :vectorz)

(defn get-cell-color
  [^double percentage] ; [0-1]
  (let [h (float (- 0.7 (* percentage 0.7))) ; blue to red
        s (float 1.0)
        b (float 1.0)]
    (Color/getHSBColor h s b)))

(defn fill-cell
  [^Graphics graphics2D x y pixels-per-cell color]
  (doto graphics2D
    (.setColor color)
    (.fillRect (* x pixels-per-cell) (* y pixels-per-cell) pixels-per-cell pixels-per-cell)))

(defn set-background-color!
  [^Graphics graphics2D ^Color color image-width image-height]
  (doto graphics2D
    (.setColor color)
    (.fillRect 0 0 image-width image-height)))

(defn normalize
  [min max val]
  (/ (- val min)
     (- max min)))

(defn draw-matrix!
  [^Graphics graphics2D matrix rows cols pixels-per-cell nodata-value legend-min legend-max]
  (dotimes [x cols]
    (dotimes [y rows]
      (let [cell-value (m/mget matrix y x)]
        (if-not (= nodata-value cell-value)
          (->> (normalize legend-min legend-max cell-value)
               (get-cell-color)
               (fill-cell graphics2D x y pixels-per-cell)))))))

(defn draw-legend-color-ramp!
  [^Graphics graphics2D legend-top legend-color-height legend-width legend-padding]
  (doseq [x (range legend-padding (+ legend-padding legend-width))]
    (let [cell-color (get-cell-color (/ (- x legend-padding) legend-width))] ; ranges from [0-1]
      (doseq [y (range legend-top (+ legend-top legend-color-height))]
        (fill-cell graphics2D x y 1 cell-color)))))

(defn draw-legend-text!
  [^Graphics graphics2D legend-min legend-max legend-padding image-width image-height]
  (let [min-val-string (format "Min: %.2f" legend-min)
        max-val-string (format "Max: %.2f" legend-max)
        max-val-width  (.. graphics2D getFontMetrics (stringWidth max-val-string))]
    (doto graphics2D
      (.setColor (get-cell-color 0.0))
      (.drawString min-val-string
                   ^Integer legend-padding
                   ^Integer (- image-height legend-padding))
      (.setColor (get-cell-color 1.0))
      (.drawString max-val-string
                   ^Integer (- image-width legend-padding max-val-width)
                   ^Integer (- image-height legend-padding)))))

(defn render-matrix
  [matrix pixels-per-cell nodata-value
   {:keys [rows cols image-height image-width legend-color-height
           legend-padding legend-top legend-width legend-min legend-max]}]
  (let [image (BufferedImage. image-width image-height BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics image)
      (set-background-color! Color/WHITE image-width image-height)
      (draw-matrix! matrix rows cols pixels-per-cell nodata-value legend-min legend-max)
      (draw-legend-color-ramp! legend-top legend-color-height legend-width legend-padding)
      (draw-legend-text! legend-min legend-max legend-padding image-width image-height)
      (.dispose))
    image))

(defn make-image-parameters
  [matrix pixels-per-cell nodata-value]
  (let [rows                   (m/row-count matrix)
        cols                   (m/column-count matrix)
        image-height-no-legend (* rows pixels-per-cell)
        image-width            (* cols pixels-per-cell)
        legend-color-height    (max 20 (quot image-height-no-legend 20))
        legend-text-height     (quot legend-color-height 2)
        legend-padding         (quot legend-text-height 2)]
    {:rows                rows
     :cols                cols
     :image-height        (+ image-height-no-legend legend-color-height legend-text-height (* legend-padding 3))
     :image-width         image-width
     :legend-color-height legend-color-height
     :legend-padding      legend-padding
     :legend-top          (+ image-height-no-legend legend-padding)
     :legend-width        (- image-width (* 2 legend-padding))
     :legend-min          (apply min (remove #{nodata-value} (m/eseq matrix)))
     :legend-max          (apply max (remove #{nodata-value} (m/eseq matrix)))}))

(defn save-matrix-as-png
  [matrix filename pixels-per-cell nodata-value]
  {:pre [(pos? pixels-per-cell) (integer? pixels-per-cell)]}
  (let [^BufferedImage image (->> (make-image-parameters matrix pixels-per-cell nodata-value)
                                  (render-matrix matrix pixels-per-cell nodata-value))]
    (try (ImageIO/write image "png" (io/file filename))
         (catch IOException _ (println "Failed to write matrix to PNG file" filename)))))
