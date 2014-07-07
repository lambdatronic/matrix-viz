# matrix-viz

A simple library to visualize core.matrix matrices.

## Usage

(save-matrix-as-png color-ramp pixels-per-cell nodata-value matrix filename)

This will render the matrix as either an 8-bit grayscale image
(color-ramp = :gray) or an 8-bit RGB image (color-ramp = :color) and
save it to the given filename in PNG format. Any pixels matching
nodata-value will be masked out. The size of the output image will be
rows * pixels-per-cell by columns * pixels-per-cell, where
pixels-per-cell is a positive integer.

## License

Copyright © 2014 Gary W. Johnson (lambdatronic@gmail.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
