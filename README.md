# matrix-viz

A simple library to visualize core.matrix matrices.

## Usage

(save-matrix-as-png color-ramp pixels-per-cell nodata-value matrix filename)

This will render the matrix as either an 8-bit grayscale image
(color-ramp = :gray), an 8-bit grayscale image with semilog scaling
(color-ramp = :graylog), an 8-bit RGB image (color-ramp = :color), or
an 8-bit RGB image with semilog scaling (color-ramp = :colorlog) and
save it to the given filename in PNG format. Any pixels matching
nodata-value will be masked out. The size of the output image will be
rows * pixels-per-cell by columns * pixels-per-cell, where
pixels-per-cell is a positive integer.

(apply-mask base-layer mask-layer nodata-value)

This helper function produces a new matrix containing the values in
base-layer wherever mask-layer is positive. All other cells are set to
nodata-value. Use this if you want to mask out values from base-layer
before sending it to save-matrix-as-png.

(blend-matrix matrix blend-radius nodata-value & {:keys [normalize?]})

This helper function creates a new matrix whose values are the
averages of its neighboring values within blend-radius steps. Cells
containing the nodata-value are unchanged by this operation. If
:normalize? = true, it scales the blended values to match the value
range in matrix.

(bleed-matrix matrix bleed-radius nodata-value bleed-test?)

This helper function creates a new matrix in which all cells whose
values pass bleed-test? are copied into their neighboring cells out to
distance bleed-radius. Cells containing the nodata-value are unchanged
by this operation.

## License

Copyright © 2014 Gary W. Johnson (lambdatronic@gmail.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
