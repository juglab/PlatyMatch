"""
Modified from https://github.com/kwohlfahrt/blob/blob/master/blob.py
"""
from math import pi
import numpy as np
from numpy import asarray, empty, nonzero, transpose
from scipy.ndimage.filters import gaussian_laplace, minimum_filter
from skimage import filters
from tqdm.contrib import tzip

def local_minima(data):
    peaks = data == minimum_filter(data, size=(3,) * data.ndim)
    return peaks

def get_peaks_subset(log, peaks, scales, threshold=None):
    if threshold is None:
        threshold = filters.threshold_otsu(log[peaks])
    peaks_subset = log < threshold*0.2 # just using otsu threshold was too conservative empiricallly!
    peaks_subset &= peaks  # SZYX
    peaks_list = transpose(nonzero(peaks_subset))
    peaks_list[:, 0] = scales[peaks_list[:, 0]]  # N x 4 table
    return peaks_subset, peaks_list, threshold


def sphere_log(data, scales=range(5, 9, 1), anisotropy_factor=5.0):
    data = asarray(data)
    scales = asarray(scales)

    log = empty((len(scales),) + data.shape, dtype=data.dtype)
    for slog, scale in (tzip(log, scales)):
        slog[...] = scale ** 2 * gaussian_laplace(data, asarray([scale / anisotropy_factor, scale, scale]))
    peaks = local_minima(log) # SZYX

    peaks_subset, peaks_list, threshold = get_peaks_subset(log, peaks, scales)
    return peaks_subset, peaks_list, log, peaks, threshold


def sphere_intersection(r1, r2, d):
    # https://en.wikipedia.org/wiki/Spherical_cap#Application

    valid = (d < (r1 + r2)) & (d > 0)
    if r1+d <=r2:
        return 4 / 3 * np.pi * (r1 ** 3)
    elif r2+d <=r1:
        return 4 / 3 * np.pi * (r2 ** 3)
    else:
        return valid * (pi * ((r1 + r2 - d) ** 2)
                        * (d ** 2 + 2 * d * (r1 + r2) - 3 * (r1 - r2) ** 2)
                        / (12 * d))



def suppress_intersecting_spheres(peaks, peaks_list, log, anisotropy_factor):
    peaks_complete = np.hstack((peaks_list, log[peaks][:, np.newaxis]))
    peaks_sorted = peaks_complete[peaks_complete[:, -1].argsort()]
    peaks_subset = []
    while (len(peaks_sorted) > 0):
        boolean_iou_table = get_intersection_truths(peaks_sorted, anisotropy_factor)
        indices, = np.where(boolean_iou_table[0, :] == 1)  # returns the x and y indices
        indices = indices[indices != 0]  # ignore if it is pointing to itself!
        peaks_subset.append(peaks_sorted[0, :4])
        peaks_sorted = np.delete(peaks_sorted, indices, 0)
        peaks_sorted = np.delete(peaks_sorted, 0, 0)
    return peaks_subset


def get_intersection_truths(peaks_sorted, anisotropy_factor):
    boolean_iou_table = np.zeros((1, peaks_sorted.shape[0]))
    for j, row in enumerate(peaks_sorted):
        d = np.linalg.norm([anisotropy_factor * (peaks_sorted[0, 1] - peaks_sorted[j, 1]), peaks_sorted[0, 2] - peaks_sorted[j, 2], peaks_sorted[0, 3] - peaks_sorted[j, 3]])
        radius_i = np.sqrt(3) * peaks_sorted[0, 0]
        radius_j = np.sqrt(3) * peaks_sorted[j, 0]
        volume_i = 4 / 3 * pi * radius_i ** 3
        volume_j = 4 / 3 * pi * radius_j ** 3
        if d != 0:
            intersection = sphere_intersection(radius_i, radius_j, d)
        else:
            intersection = np.minimum(volume_i, volume_j)
        boolean_iou_table[0, j] = intersection > 0.05 * np.minimum(volume_i, volume_j)
    return boolean_iou_table


def find_spheres(image, scales=range(1, 10), anisotropy_factor=5.0):
    peaks_otsu, peaks_list, log, peaks_local_minima, threshold = sphere_log(image.astype(np.float32), scales=scales,
                                                           anisotropy_factor=anisotropy_factor)
    peaks_subset = suppress_intersecting_spheres(peaks_otsu, peaks_list, log, anisotropy_factor)
    return peaks_otsu, np.asarray(peaks_subset), log, peaks_local_minima, threshold
