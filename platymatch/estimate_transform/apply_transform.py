import numpy as np

def apply_affine_transform(moving, affine_transform_matrix):
    """

    :param moving: 3 x N
    :param affine_transform_matrix: 4 x 4
    :param with_ones: if False, then source is 3 x N, else 4 x N
    :return: target point cloud 3 x N
    """
    if moving.shape[0] == 4:  # 4 x N
        moving = moving[:3, :] # 3 x N

    extra_one_row = np.ones((1, moving.shape[1]))
    moving = np.vstack((moving, extra_one_row))
    fixed_predicted = np.matmul(affine_transform_matrix, moving) # 4 x N
    return fixed_predicted[:3, :]

def apply_similar_transform(source, scale, rotation, translation, with_ones=False):
    """

    :param source:
    :param scale: scalar
    :param rotation: 3 x 3
    :param translation: 3 x 1
    :param with_ones: False
    :return: target 3 x N
    """
    if (with_ones):
        source = source[:3, :]
    else:
        pass
    return scale * np.matmul(rotation, source) + translation