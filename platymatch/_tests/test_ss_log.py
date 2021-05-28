import numpy as np
from platymatch.detect_nuclei.ss_log import sphere_intersection, find_spheres


def test_sphere_intersection_1():
    r1 = np.random.randint(100)
    r2 = np.random.randint(100)
    d = r1 + r2 + np.random.randint(100)
    assert sphere_intersection(r1, r2, d) == 0


def test_sphere_intersection_2():
    r1 = np.random.randint(100)
    r2 = np.random.randint(100)
    d = 0
    if r1 < r2:
        assert sphere_intersection(r1, r2, d) == 4 / 3 * np.pi * (r1 ** 3)
    else:
        assert sphere_intersection(r1, r2, d) == 4 / 3 * np.pi * (r2 ** 3)


def test_sphere_intersection_3():
    r1 = np.random.randint(100)
    r2 = r1/2
    d = r2/2
    
    assert sphere_intersection(r1, r2, d) == 4 / 3 * np.pi * (r2 ** 3)


def test_sphere_intersection_4():
    r1 = np.random.randint(100)
    r2 = np.random.randint(100)
    d = r1+r2

    assert sphere_intersection(r1, r2, d) == 0.0

def test_find_spheres_1():
    im = np.zeros((100, 100, 100))
    x1, y1, z1 = np.random.randint(20, 40, 3)  # sphere 1 between 10:40, 10:40, 10:40
    r1 = 20

    x2, y2, z2 = np.random.randint(60, 90, 3)
    r2 = 20

    for z_i in range(z1 - r1, z1 + r1 + 1):
        for y_i in range(y1 - r1, y1 + r1 + 1):
            for x_i in range(x1 - r1, x1 + r1 + 1):
                d = np.linalg.norm((x_i - x1) ** 2 + (y_i - y1) ** 2 + (z_i - z1) ** 2)
                if (d <= r1):
                    im[z_i, y_i, x_i] = 100 - d

    for z_i in range(z2 - r2, z2 + r2 + 1):
        for y_i in range(y2 - r2, y2 + r2 + 1):
            for x_i in range(x2 - r2, x2 + r2 + 1):
                d = np.linalg.norm((x_i - x2) ** 2 + (y_i - y2) ** 2 + (z_i - z2) ** 2)
                if (d <= r2):
                    im[z_i, y_i, x_i] = 100 - d

    _, peaks_subset, _, _, _ = find_spheres(im, scales=range(1, 10), anisotropy_factor=1.0)
    assert len(peaks_subset) == 2
