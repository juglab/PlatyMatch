import numpy as np
from platymatch.utils.utils import get_centroid


def test_get_centroid():
    points = np.array([[0, 0, 0], [1, 0, 0], [0, 0, 1], [1, 0, 1], [1, 1, 0], [1, 1, 1], [0, 1, 1], [0, 1, 0]])  # 8 x 3
    centroid = get_centroid(points, transposed=True)
    np.testing.assert_array_almost_equal(centroid, np.array([[0.5, 0.5, 0.5]]))
