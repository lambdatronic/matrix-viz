# matrix-viz

A simple library to visualize core.matrix matrices.

## Usage

(save-matrix-as-png matrix filename pixels-per-cell nodata-value)

This will render the matrix as an 8-bit RGBA color image and save it
to filename in PNG format. Any pixels matching nodata-value will be
masked out. The size of the output image will be rows *
pixels-per-cell by columns * pixels-per-cell, where pixels-per-cell is
a positive integer.

## License

Copyright © 2014 Gary W. Johnson (lambdatronic@gmail.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
