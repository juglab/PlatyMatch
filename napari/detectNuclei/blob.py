#!/usr/bin/env python3
import numpy as np
from numpy import zeros, ones, asarray, empty, nonzero, transpose, triu, seterr, arccos, sqrt
from numpy.linalg import norm
from math import pi
from scipy.ndimage.filters import gaussian_laplace, minimum_filter
from operator import contains
from functools import partial
from itertools import filterfalse
from tqdm.contrib import tzip
import tifffile
from skimage import filters

def localMinima(data):
    peaks1 = data == minimum_filter(data, size=(3,)*data.ndim) # TODO peaks Scales x Z x Y x X boolean type
    return peaks1

def getPeaksSubset(log, peaks1, scales, threshold = None):
    if threshold is None:
        threshold = filters.threshold_otsu(log[peaks1])
        print("Value of Otsu Threshold is = {} *****".format(threshold))
    peaks = log < threshold
    peaks &= peaks1 # boolean array scales, Z, Y, X
    peaksList = transpose(nonzero(peaks))
    peaksList[:, 0] = scales[peaksList[:, 0]] # N x 4 table
    return peaks, peaksList, threshold

def blobLOG(data, progress_bar, scales=range(5, 9, 1), anisotropyFactor = 5.0):
    """Find blobs. Returns [[scale, x, y, ...], ...]"""

    data = asarray(data)
    scales = asarray(scales)

    log = empty((len(scales),) + data.shape, dtype=data.dtype)
    count = 1
    for slog, scale in (tzip(log, scales)):
        slog[...] = scale ** 2 * gaussian_laplace(data, asarray([scale/anisotropyFactor, scale, scale]))
        progress_bar.setValue(100 * count // len(scales))
        count+=1
    peaks1 = localMinima(log)

    peaks, peaksList, threshold = getPeaksSubset(log, peaks1, scales)
    return peaks, peaksList, log, peaks1, threshold

def sphereIntersection(r1, r2, d):
    # https://en.wikipedia.org/wiki/Spherical_cap#Application

    valid = (d < (r1 + r2)) & (d > 0)
    return valid * (pi * (r1 + r2 - d) ** 2
            * (d ** 2  + 2 * d * (r1 + r2) - 3 * (r1 - r2) ** 2)
            / (12 * d))

def circleIntersection(r1, r2, d):
    # http://mathworld.wolfram.com/Circle-CircleIntersection.html
        return (r1 ** 2 * arccos((d ** 2 + r1 ** 2 - r2 ** 2) / (2 * d * r1))
            + r2 ** 2 * arccos((d ** 2 + r2 ** 2 - r1 ** 2) / (2 * d * r2))
            - sqrt((-d + r1 + r2) * (d + r1 - r2)
                   * (d - r1 + r2) * (d + r1 + r2)) / 2)

def suppressIntersectingNuclei(peaks, peaksList, log, anisotropyFactor):
    peaksComplete = np.hstack((peaksList, log[peaks][:, np.newaxis]))
    peaksSorted = peaksComplete[peaksComplete[:, -1].argsort()]
    peaksSubset = []
    while(len(peaksSorted) > 0):
        booleanIOUTable = getIntersectionTruths(peaksSorted, anisotropyFactor)
        indices,  = np.where(booleanIOUTable[0, :] == 1)  # returns the x and y indices
        indices = indices[indices!=0] # ignore if it is pointing to itself!
        peaksSubset.append(peaksSorted[0, :4])
        peaksSorted = np.delete(peaksSorted, indices, 0)
        peaksSorted = np.delete(peaksSorted, 0, 0)
    return peaksSubset

def getIntersectionTruths(peaksSorted, anisotropyFactor):
    booleanIOUTable = np.zeros((1, peaksSorted.shape[0]))
    for j, row in enumerate(peaksSorted):
        d = np.linalg.norm([anisotropyFactor*(peaksSorted[0, 1] - peaksSorted[j, 1]), peaksSorted[0, 2] - peaksSorted[j, 2], peaksSorted[0, 3] - peaksSorted[j, 3]])
        radius_i = np.sqrt(3) * peaksSorted[0, 0]
        radius_j = np.sqrt(3) * peaksSorted[j, 0]
        volume_i = 4 / 3 * pi * radius_i ** 3
        volume_j = 4 / 3 * pi * radius_j ** 3
        if d != 0:
            intersection = sphereIntersection(radius_i, radius_j, d)
        else:
            intersection = np.minimum(volume_i, volume_j)
        booleanIOUTable[0, j] = intersection > 0.05 * np.minimum(volume_i, volume_j)
    return booleanIOUTable

def findNuclei(img, progress_bar, scales=range(1, 10), anisotropyFactor = 5.0, max_overlap=0.05):
    peaks, peaksList, log, peaks1, threshold = blobLOG(img, progress_bar, scales=scales, anisotropyFactor = anisotropyFactor) # Important to flip the sign!
    # peaks     SZYX
    # peaks1    SZYX
    # peaksList N x 4
    #np.savetxt('/home/manan/Desktop/seeds_1', peaksList, delimiter=',')
    print("Minima saved!!")
    print("Peaks shape is {}, Peaks list shape is {}, peaks1 shape is {}".format(peaks.shape, peaksList.shape, peaks1.shape))
    print("Log peaks shape = {}".format(log[peaks].shape))
    peaksSubset = suppressIntersectingNuclei(peaks, peaksList, log, anisotropyFactor)
    return peaks, np.asarray(peaksSubset), log, peaks1, threshold

def peakEnclosed(peaks, shape, size=1):

    shape = asarray(shape)
    return ((size <= peaks).all(axis=-1) & (size < (shape - peaks)).all(axis=-1))

def plot(args):
    from tifffile import imread
    from numpy import loadtxt, delete
    from pickle import load
    import matplotlib
    from mpl_toolkits.axes_grid.anchored_artists import AnchoredAuxTransformBox
    from matplotlib.text import Text
    from matplotlib.text import Line2D

    if args.outfile is not None:
        matplotlib.use('Agg')
    import matplotlib.pyplot as plt

    image = imread(str(args.image)).T
    scale = asarray(args.scale) if args.scale else ones(image.ndim, dtype='int')

    if args.peaks.suffix == '.txt':
        peaks = loadtxt(str(args.peaks), ndmin=2)
    elif args.peaks.suffix == ".csv":
        peaks = loadtxt(str(args.peaks), ndmin=2, delimiter=',')
    elif args.peaks.suffix == ".pickle":
        with args.peaks.open("rb") as f:
            peaks = load(f)
    else:
        raise ValueError("Unrecognized file type: '{}', need '.pickle' or '.csv'"
                         .format(args.peaks.suffix))
    peaks = peaks / scale

    proj_axes = tuple(filterfalse(partial(contains, args.axes), range(image.ndim)))
    image = image.max(proj_axes)
    peaks = delete(peaks, proj_axes, axis=1)

    fig, ax = plt.subplots(1, 1, figsize=args.size)
    ax.imshow(image.T, cmap='gray')
    ax.set_xticks([])
    ax.set_yticks([])
    ax.scatter(*peaks.T, edgecolor="C1", facecolor='none')

    if args.scalebar is not None:
        pixel, units, length = args.scalebar
        pixel = float(pixel)
        length = int(length)

        box = AnchoredAuxTransformBox(ax.transData, loc=4)
        box.patch.set_alpha(0.8)
        bar = Line2D([-length/pixel/2, length/pixel/2], [0.0, 0.0], color='black')
        box.drawing_area.add_artist(bar)
        label = Text(
            0.0, 0.0, "{} {}".format(length, units),
            horizontalalignment="center", verticalalignment="bottom"
        )
        box.drawing_area.add_artist(label)
        ax.add_artist(box)

    if args.outfile is None:
        plt.show()
    else:
        fig.tight_layout()
        fig.savefig(str(args.outfile))

def find(args):
    from sys import stdout
    from tifffile import imread

    image = imread(str(args.image)).astype('float32')

    scale = asarray(args.scale) if args.scale else ones(image.ndim, dtype='int')
    blobs = findNuclei(image, range(*args.size), args.threshold)[:, 1:] # Remove scale
    blobs = blobs[peakEnclosed(blobs, shape=image.shape, size=args.edge)]
    blobs = blobs[:, ::-1] # Reverse to xyz order
    blobs = blobs * scale

    if args.format == "pickle":
        from pickle import dump, HIGHEST_PROTOCOL
        from functools import partial
        dump = partial(dump, protocol=HIGHEST_PROTOCOL)

        dump(blobs, stdout.buffer)
    else:
        import csv

        if args.format == 'txt':
            delimiter = ' '
        elif args.format == 'csv':
            delimiter = ','
        writer = csv.writer(stdout, delimiter=delimiter)
        for blob in blobs:
            writer.writerow(blob)

# For setuptools entry_points
def main(args=None):
    from argparse import ArgumentParser
    from pathlib import Path
    from sys import argv

    parser = ArgumentParser(description="Find peaks in an nD image")
    subparsers = parser.add_subparsers()

    find_parser = subparsers.add_parser("find")
    find_parser.add_argument("image", type=Path, help="The image to process")
    find_parser.add_argument("--size", type=int, nargs=2, default=(1, 1),
                             help="The range of sizes (in px) to search.")
    find_parser.add_argument("--threshold", type=float, default=5,
                             help="The minimum spot intensity")
    find_parser.add_argument("--format", choices={"csv", "txt", "pickle"}, default="csv",
                             help="The output format (for stdout)")
    find_parser.add_argument("--edge", type=int, default=0,
                             help="Minimum distance to edge allowed.")
    find_parser.set_defaults(func=find)

    plot_parser = subparsers.add_parser("plot")
    plot_parser.add_argument("image", type=Path, help="The image to process")
    plot_parser.add_argument("peaks", type=Path, help="The peaks to plot")
    plot_parser.add_argument("outfile", nargs='?', type=Path, default=None,
                             help="Where to save the plot (omit to display)")
    plot_parser.add_argument("--axes", type=int, nargs=2, default=(0, 1),
                             help="The axes to plot")
    plot_parser.add_argument("--size", type=float, nargs=2, default=(5, 5),
                             help="The size of the figure (in inches)")
    plot_parser.add_argument("--scalebar", type=str, nargs=3, default=None,
                             help="The pixel-size, units and scalebar size")
    plot_parser.set_defaults(func=plot)

    for p in (plot_parser, find_parser):
        p.add_argument("--scale", nargs="*", type=float,
                       help="The scale for the points along each axis.")

    args = parser.parse_args(argv[1:] if args is None else args)
    args.func(args)

if __name__ == "__main__":
    main()
