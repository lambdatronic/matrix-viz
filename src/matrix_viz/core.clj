(ns matrix-viz.core
  (:require [clojure.java.io :as io]
            [clojure.core.matrix :as m]
            [clojure.core.matrix.operators :as mop]
            [clojure.core.reducers :as r]
            [tech.v3.datatype :as d]
            [tech.v3.tensor :as t])
  (:import (java.awt Color Graphics Font)
           (java.awt.image BufferedImage)
           (javax.imageio ImageIO)
           (java.io IOException)))

(m/set-current-implementation :vectorz)

(defn- row-count
  [data]
  (if (t/tensor? data)
    (-> data t/tensor->dimensions :shape first)
    (m/row-count data)))

(defn- column-count
  [data]
  (if (t/tensor? data)
    (-> data t/tensor->dimensions :shape second)
    (m/column-count data)))

(defn- emap
  [f & args]
  (if (every? t/tensor? args)
    (d/clone
     (apply d/emap f nil args))
    (apply m/emap f args)))

(defn- eseq
  [data]
  (if (t/tensor? data)
    (t/tensor->buffer data)
    (m/eseq data)))

(defn- mget
  ([data x0] data)
  ([data x0]
   (if (t/tensor? data)
     (t/mget data x0)
     (m/mget data x0)))
  ([data x0 x1]
   (if (t/tensor? data)
     (t/mget data x0 x1)
     (m/mget data x0 x1)))
  ([data x0 x1 x2 & args]
   (if (t/tensor? data)
     (apply t/mget data x0 x1 x2 args)
     (apply m/mget data x0 x1 x2 args))))

(defn- mset!
  [data & args]
  (if (t/tensor? data)
    (apply t/mset! data args)
    (apply m/mset! data args)))

(defn- mutable
  [data]
  (if (t/tensor? data)
    (d/clone data)
    (m/mutable data)))

(defn- index-seq
  [data]
  (if (t/tensor? data)
    (-> (t/tensor->dimensions data)
        :shape
        (t/compute-tensor (fn [& args] (vec args)))
        d/->buffer)
    (m/index-seq data)))

(defn- compute-matrix
  [data shape f]
  (if (t/tensor? data)
    (t/compute-tensor shape f)
    (m/compute-matrix shape f)))

(defn- log-scale
  [x]
  (if (< x 1E-10)
    0.0
    (+ 1 (* 0.1 (Math/log10 x)))))

(defn- get-cell-color
  [color-ramp ^double percentage] ; [0-1]
  (case color-ramp
    :color    (let [h (float (- 0.7 (* percentage 0.7))) ; blue to red
                    s (float 1.0)
                    b (float 1.0)]
                (Color/getHSBColor h s b))
    :colorlog (let [h (float (- 0.7 (* (log-scale percentage) 0.7))) ; blue to red
                    s (float 1.0)
                    b (float 1.0)]
                (Color/getHSBColor h s b))
    :gray     (let [p (float (- 0.7 (* percentage 0.7)))]
                (Color. p p p))
    :graylog  (let [p (float (- 0.7 (* (log-scale percentage) 0.7)))]
                (Color. p p p))))

(defn- fill-cell
  [^Graphics graphics2D x y pixels-per-cell color]
  (doto graphics2D
    (.setColor color)
    (.fillRect (* x pixels-per-cell) (* y pixels-per-cell) pixels-per-cell pixels-per-cell)))

(defn- set-background-color!
  [^Graphics graphics2D ^Color color image-width image-height]
  (doto graphics2D
    (.setColor color)
    (.fillRect 0 0 image-width image-height)))

(defn- normalize
  [min max val]
  (let [range (- max min)]
    (if (zero? range)
      0.0
      (/ (- val min) range))))

(defn- denormalize
  [min max val]
  (+ (* val (- max min))
     min))

(defn- draw-matrix!
  [^Graphics graphics2D matrix rows cols pixels-per-cell nodata-value legend-min legend-max color-ramp]
  (dotimes [x cols]
    (dotimes [y rows]
      (let [cell-value (mget matrix y x)]
        (when-not (= nodata-value cell-value)
          (->> (normalize legend-min legend-max cell-value)
               (get-cell-color color-ramp)
               (fill-cell graphics2D x y pixels-per-cell)))))))

(defn- draw-legend-color-ramp!
  [^Graphics graphics2D legend-top legend-color-height legend-width legend-padding color-ramp]
  (doseq [x (range legend-padding (+ legend-padding legend-width))]
    (let [cell-color (get-cell-color color-ramp (/ (- x legend-padding) legend-width))] ; ranges from [0-1]
      (doseq [y (range legend-top (+ legend-top legend-color-height))]
        (fill-cell graphics2D x y 1 cell-color)))))

(defn- draw-legend-text!
  [^Graphics graphics2D legend-min legend-max legend-padding image-width image-height color-ramp]
  (let [min-val-string (format "Min: %.2f" legend-min)
        max-val-string (format "Max: %.2f" legend-max)
        current-font   (.getFont graphics2D)]
    (doto graphics2D
      (.setFont (.deriveFont current-font Font/BOLD 16.0))
      (.setColor (if (contains? #{:color :colorlog} color-ramp)
                   (get-cell-color color-ramp 0.0)
                   (get-cell-color color-ramp 1.0)))
      (.drawString min-val-string
                   ^Integer (* 2 legend-padding)
                   ^Integer (- image-height legend-padding))
      (.setColor (get-cell-color color-ramp 1.0))
      (.drawString max-val-string
                   ^Integer (- image-width legend-padding legend-padding
                               (.. graphics2D getFontMetrics (stringWidth max-val-string)))
                   ^Integer (- image-height legend-padding)))))

(defn- render-matrix
  [matrix pixels-per-cell nodata-value color-ramp
   {:keys [rows cols image-height image-width legend-color-height
           legend-padding legend-top legend-width legend-min legend-max]}]
  (let [image (BufferedImage. image-width image-height (if (contains? #{:color :colorlog} color-ramp)
                                                         BufferedImage/TYPE_INT_RGB
                                                         BufferedImage/TYPE_BYTE_GRAY))]
    (doto (.createGraphics image)
      (set-background-color! Color/WHITE image-width image-height)
      (draw-matrix! matrix rows cols pixels-per-cell nodata-value legend-min legend-max color-ramp)
      (draw-legend-color-ramp! legend-top legend-color-height legend-width legend-padding color-ramp)
      (draw-legend-text! legend-min legend-max legend-padding image-width image-height color-ramp)
      (.dispose))
    image))

(defn- matrix-values
  [nodata-value matrix]
  (->> (eseq matrix)
       (eduction (remove (fn [v] (= nodata-value v))))))

(defn- make-image-parameters
  [matrix pixels-per-cell nodata-value]
  (let [rows                   (row-count matrix)
        cols                   (column-count matrix)
        image-height-no-legend (* rows pixels-per-cell)
        image-width            (* cols pixels-per-cell)
        legend-color-height    (max 20 (quot image-height-no-legend 20))
        legend-text-height     (int (/ legend-color-height 1.5))
        legend-padding         (quot legend-color-height 4)
        v0                     (first (matrix-values nodata-value matrix))]
    {:rows                rows
     :cols                cols
     :image-height        (+ image-height-no-legend legend-color-height legend-text-height (* legend-padding 3))
     :image-width         image-width
     :legend-color-height legend-color-height
     :legend-padding      legend-padding
     :legend-top          (+ image-height-no-legend legend-padding)
     :legend-width        (- image-width (* 2 legend-padding))
     :legend-min          (reduce min v0 (matrix-values nodata-value matrix))
     :legend-max          (reduce max v0 (matrix-values nodata-value matrix))}))

(defn save-matrix-as-png
  "Renders the matrix as either an 8-bit grayscale image (color-ramp
   = :gray), an 8-bit grayscale image with semilog scaling (color-ramp
   = :graylog), an 8-bit RGB image (color-ramp = :color), or an 8-bit
   RGB image with semilog scaling (color-ramp = :colorlog) and saves it
   to the given filename in PNG format. Any pixels matching
   nodata-value will be masked out. The size of the output image will
   be rows * pixels-per-cell by columns * pixels-per-cell, where
   pixels-per-cell is a positive integer."
  [color-ramp pixels-per-cell nodata-value matrix filename]
  {:pre [(pos? pixels-per-cell)
         (integer? pixels-per-cell)
         (contains? #{:color :colorlog :gray :graylog} color-ramp)]}
  (let [^BufferedImage image (->> (make-image-parameters matrix pixels-per-cell nodata-value)
                                  (render-matrix matrix pixels-per-cell nodata-value color-ramp))]
    (try (ImageIO/write image "png" (io/file filename))
         (catch IOException _ (println "Failed to write matrix to PNG file" filename)))))

(defn apply-mask
  "Produces a new matrix containing the values in base-layer wherever
   mask-layer is positive. All other cells are set to nodata-value."
  [base-layer mask-layer nodata-value]
  (emap (fn [b m] (if (pos? m) b nodata-value))
        base-layer
        mask-layer))

(defn- get-masked-bounds
  "Returns [min max] for matrix after excluding nodata-value cells."
  [matrix nodata-value]
  (let [vals (remove #{nodata-value} (eseq matrix))]
    [(apply min vals) (apply max vals)]))

(defn- renormalize-matrix
  "Scales the values in from-matrix to the value range in to-matrix.
   Cells containing nodata-value are ignored by this operation."
  [from-matrix to-matrix nodata-value]
  (let [[from-min from-max] (get-masked-bounds from-matrix nodata-value)
        [to-min to-max]     (get-masked-bounds to-matrix   nodata-value)]
    (emap #(if (== % nodata-value)
             nodata-value
             (->> (normalize from-min from-max %)
                  (denormalize to-min to-max)))
          from-matrix)))

(defn- in-bounds?
  "Returns true if the point lies within the bounds [0,rows) by [0,cols)."
  [rows cols [i j]]
  (and (>= i 0)
       (>= j 0)
       (< i rows)
       (< j cols)))

(defn- neighborhood-points
  "Returns a vector of the points within radius steps of the passed in point."
  [point radius]
  (let [side   (+ (* 2 radius) 1)
        offset (- radius)]
    (mop/+ point [offset offset] (m/index-seq-for-shape [side side]))))

(defn blend-matrix
  "Creates a new matrix whose values are the averages of its
   neighboring values within blend-radius steps. Cells containing the
   nodata-value are unchanged by this operation. If :normalize? = true,
   scale the blended values to match the value range in matrix."
  [matrix blend-radius nodata-value & {:keys [normalize?]}]
  (let [rows           (row-count matrix)
        cols           (column-count matrix)
        blended-matrix (compute-matrix matrix
                                       [rows cols]
                                       (fn [i j]
                                         (if (== nodata-value (mget matrix i j))
                                           nodata-value
                                           (let [blend-values (->> (neighborhood-points [i j] blend-radius)
                                                                   (r/filter #(in-bounds? rows cols %))
                                                                   (r/map #(apply mget matrix %))
                                                                   (filterv #(not= nodata-value %)))]
                                             (/ (reduce + 0.0 blend-values)
                                                (count blend-values))))))]
    (if normalize?
      (renormalize-matrix blended-matrix matrix nodata-value)
      blended-matrix)))

(defn- points-where
  "Returns a set of the [i j] coordinates for each point in matrix
   which satisfies pred?."
  [pred? matrix]
  (into #{}
        (r/filter (fn [point] (pred? (apply mget matrix point)))
                  (index-seq matrix))))

(defn bleed-matrix
  "Creates a new matrix in which all cells whose values pass
   bleed-test? are copied into their neighboring cells out to distance
   bleed-radius. Cells containing the nodata-value are unchanged by
   this operation."
  [matrix bleed-radius nodata-value bleed-test?]
  (let [matrix' (mutable matrix)
        rows    (row-count matrix)
        cols    (column-count matrix)]
    (doseq [point (points-where bleed-test? matrix)]
      (let [bleed-value (apply mget matrix point)]
        (doseq [[i j] (->> (neighborhood-points point bleed-radius)
                           (filter #(and (in-bounds? rows cols %)
                                         (not= nodata-value
                                               (apply mget matrix %)))))]
          (mset! matrix' i j bleed-value))))
    matrix'))
