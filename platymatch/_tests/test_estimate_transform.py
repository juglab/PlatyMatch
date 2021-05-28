import numpy as np
import os
import pandas as pd
from platymatch.estimate_transform.apply_transform import apply_affine_transform
from platymatch.estimate_transform.perform_icp import perform_icp
from platymatch.estimate_transform.shape_context import get_unary, get_unary_distance, do_ransac
from platymatch.utils.utils import get_centroid, get_mean_distance
from scipy.optimize import linear_sum_assignment


def test_shape_context_1():
    # load sample detections
    main_script_dir = os.path.dirname(__file__)
    rel_path = "assets/02-insitu.csv"
    file_name = os.path.join(main_script_dir, rel_path)

    moving_detections = pd.read_csv(file_name, header=None, delimiter=' ')  # N x 4 # id x y z
    moving_detections_numpy = moving_detections.to_numpy()
    moving_detections = moving_detections_numpy[:, 1:4]  # N x 3
    moving_detections = np.flip(moving_detections, 1)  # N x 3 --> zyx
    moving_detections = moving_detections.transpose()  # 3 x N

    # create an arbitrary transform
    affine_transform_gt = np.identity(4)
    fixed_detections = apply_affine_transform(moving_detections, affine_transform_gt)  # 3 x N

    moving_centroid = get_centroid(moving_detections, transposed=False)  # 3 x 1
    fixed_centroid = get_centroid(fixed_detections, transposed=False)  # 3 x 1
    moving_mean_distance = get_mean_distance(moving_detections, transposed=False)
    fixed_mean_distance = get_mean_distance(fixed_detections, transposed=False)
    unary_11, unary_12 = get_unary(moving_centroid, mean_distance=moving_mean_distance, detections=moving_detections,
                                   transposed=False)  # (N, 360)
    unary_21, unary_22 = get_unary(fixed_centroid, mean_distance=fixed_mean_distance, detections=fixed_detections,
                                   transposed=False)
    U1 = np.zeros((moving_detections.shape[1], fixed_detections.shape[1]))  # N1 x N2
    U2 = np.zeros((moving_detections.shape[1], fixed_detections.shape[1]))  # N1 x N2

    for i in range(U1.shape[0]):
        for j in range(U1.shape[1]):
            unary_i = unary_11[i]
            unary_j = unary_21[j]
            U1[i, j] = get_unary_distance(unary_i, unary_j)

    for i in range(U2.shape[0]):
        for j in range(U2.shape[1]):
            unary_i = unary_11[i]
            unary_j = unary_22[j]
            U2[i, j] = get_unary_distance(unary_i, unary_j)

    row_indices_1, col_indices_1 = linear_sum_assignment(U1)

    moving_detections_copy = moving_detections.copy()
    fixed_detections_copy = fixed_detections.copy()
    transform_matrix_1, inliers_best_1 = do_ransac(moving_detections_copy[:, row_indices_1],  # 3 x N
                                                   fixed_detections_copy[:, col_indices_1],  # 3 x N
                                                   min_samples=4, trials=4000, error=15, transform='Affine')
    row_indices_2, col_indices_2 = linear_sum_assignment(U2)
    transform_matrix_2, inliers_best_2 = do_ransac(moving_detections_copy[:, row_indices_2],
                                                   fixed_detections_copy[:, col_indices_2],
                                                   min_samples=4, trials=4000, error=15, transform='Affine')

    print("Inliers 1 = {} and Inliers 2 = {}".format(inliers_best_1, inliers_best_2))
    if (inliers_best_1 > inliers_best_2):
        transform_matrix_sc = transform_matrix_1
    else:
        transform_matrix_sc = transform_matrix_2
    print("transform_matrix_sc \n", transform_matrix_sc)

    print("=" * 50)
    transformed_source_detections = apply_affine_transform(moving_detections_copy, transform_matrix_sc)
    transform_matrix_icp = perform_icp(transformed_source_detections, fixed_detections_copy, 50, 'Affine')
    np.testing.assert_array_almost_equal(affine_transform_gt, np.matmul(transform_matrix_icp, transform_matrix_sc))


def test_shape_context_2():
    main_script_dir = os.path.dirname(__file__)
    rel_path = "assets/02-insitu.csv"
    file_name = os.path.join(main_script_dir, rel_path)

    # load sample detections
    moving_detections = pd.read_csv(file_name, header=None, delimiter=' ')  # N x 4 # id x y z
    moving_detections_numpy = moving_detections.to_numpy()
    moving_detections = moving_detections_numpy[:, 1:4]  # N x 3
    moving_detections = np.flip(moving_detections, 1)  # N x 3 --> zyx
    moving_detections = moving_detections.transpose()  # 3 x N

    # create an arbitrary transform
    affine_transform_gt = np.array([[9.08173020e-01, -2.58092254e-01, 2.21387350e-01, 4.98532315e+00],
                                    [-2.85490902e-02, 5.66865806e-01, 7.60292965e-01, -2.13218259e+02],
                                    [-2.53059848e-01, -7.49475117e-01, 4.48778146e-01, 5.56203489e+02],
                                    [1.73472348e-17, 2.42861287e-17, -4.16333634e-17, 1.00000000e+00]])

    fixed_detections = apply_affine_transform(moving_detections, affine_transform_gt)  # 3 x N

    moving_centroid = get_centroid(moving_detections, transposed=False)  # 3 x 1
    fixed_centroid = get_centroid(fixed_detections, transposed=False)  # 3 x 1
    moving_mean_distance = get_mean_distance(moving_detections, transposed=False)
    fixed_mean_distance = get_mean_distance(fixed_detections, transposed=False)
    unary_11, unary_12 = get_unary(moving_centroid, mean_distance=moving_mean_distance, detections=moving_detections,
                                   transposed=False)  # (N, 360)
    unary_21, unary_22 = get_unary(fixed_centroid, mean_distance=fixed_mean_distance, detections=fixed_detections,
                                   transposed=False)
    U1 = np.zeros((moving_detections.shape[1], fixed_detections.shape[1]))  # N1 x N2
    U2 = np.zeros((moving_detections.shape[1], fixed_detections.shape[1]))  # N1 x N2

    for i in range(U1.shape[0]):
        for j in range(U1.shape[1]):
            unary_i = unary_11[i]
            unary_j = unary_21[j]
            U1[i, j] = get_unary_distance(unary_i, unary_j)

    for i in range(U2.shape[0]):
        for j in range(U2.shape[1]):
            unary_i = unary_11[i]
            unary_j = unary_22[j]
            U2[i, j] = get_unary_distance(unary_i, unary_j)

    row_indices_1, col_indices_1 = linear_sum_assignment(U1)

    moving_detections_copy = moving_detections.copy()
    fixed_detections_copy = fixed_detections.copy()
    transform_matrix_1, inliers_best_1 = do_ransac(moving_detections_copy[:, row_indices_1],  # 3 x N
                                                   fixed_detections_copy[:, col_indices_1],  # # x N
                                                   min_samples=4, trials=4000, error=15, transform='Affine')
    row_indices_2, col_indices_2 = linear_sum_assignment(U2)
    transform_matrix_2, inliers_best_2 = do_ransac(moving_detections_copy[:, row_indices_2],
                                                   fixed_detections_copy[:, col_indices_2],
                                                   min_samples=4, trials=4000, error=15, transform='Affine')

    print("Inliers 1 = {} and Inliers 2 = {}".format(inliers_best_1, inliers_best_2))
    if (inliers_best_1 > inliers_best_2):
        transform_matrix_sc = transform_matrix_1
    else:
        transform_matrix_sc = transform_matrix_2
    print("transform_matrix_sc \n", transform_matrix_sc)

    print("=" * 50)
    transformed_source_detections = apply_affine_transform(moving_detections_copy, transform_matrix_sc)
    transform_matrix_icp = perform_icp(transformed_source_detections, fixed_detections_copy, 50, 'Affine')
    np.testing.assert_array_almost_equal(affine_transform_gt, np.matmul(transform_matrix_icp, transform_matrix_sc))


def test_shape_context_3():
    main_script_dir = os.path.dirname(__file__)
    rel_path = "assets/04-insitu.csv"
    file_name = os.path.join(main_script_dir, rel_path)

    # load sample detections
    moving_detections = pd.read_csv(file_name, header=None, delimiter=' ')  # N x 4 # id x y z
    moving_detections_numpy = moving_detections.to_numpy()
    moving_detections = moving_detections_numpy[:, 1:4]  # N x 3
    moving_detections = np.flip(moving_detections, 1)  # N x 3 --> zyx
    moving_detections = moving_detections.transpose()  # 3 x N

    # create an arbitrary transform
    affine_transform_gt = np.array([[9.08173020e-01, -2.58092254e-01, 2.21387350e-01, 4.98532315e+00],
                                    [-2.85490902e-02, 5.66865806e-01, 7.60292965e-01, -2.13218259e+02],
                                    [-2.53059848e-01, -7.49475117e-01, 4.48778146e-01, 5.56203489e+02],
                                    [1.73472348e-17, 2.42861287e-17, -4.16333634e-17, 1.00000000e+00]])

    fixed_detections = apply_affine_transform(moving_detections, affine_transform_gt)  # 3 x N

    moving_centroid = get_centroid(moving_detections, transposed=False)  # 3 x 1
    fixed_centroid = get_centroid(fixed_detections, transposed=False)  # 3 x 1
    moving_mean_distance = get_mean_distance(moving_detections, transposed=False)
    fixed_mean_distance = get_mean_distance(fixed_detections, transposed=False)
    unary_11, unary_12 = get_unary(moving_centroid, mean_distance=moving_mean_distance, detections=moving_detections,
                                   transposed=False)  # (N, 360)
    unary_21, unary_22 = get_unary(fixed_centroid, mean_distance=fixed_mean_distance, detections=fixed_detections,
                                   transposed=False)
    U1 = np.zeros((moving_detections.shape[1], fixed_detections.shape[1]))  # N1 x N2
    U2 = np.zeros((moving_detections.shape[1], fixed_detections.shape[1]))  # N1 x N2

    for i in range(U1.shape[0]):
        for j in range(U1.shape[1]):
            unary_i = unary_11[i]
            unary_j = unary_21[j]
            U1[i, j] = get_unary_distance(unary_i, unary_j)

    for i in range(U2.shape[0]):
        for j in range(U2.shape[1]):
            unary_i = unary_11[i]
            unary_j = unary_22[j]
            U2[i, j] = get_unary_distance(unary_i, unary_j)

    row_indices_1, col_indices_1 = linear_sum_assignment(U1)

    moving_detections_copy = moving_detections.copy()
    fixed_detections_copy = fixed_detections.copy()
    transform_matrix_1, inliers_best_1 = do_ransac(moving_detections_copy[:, row_indices_1],  # 3 x N
                                                   fixed_detections_copy[:, col_indices_1],  # # x N
                                                   min_samples=4, trials=4000, error=15, transform='Affine')
    row_indices_2, col_indices_2 = linear_sum_assignment(U2)
    transform_matrix_2, inliers_best_2 = do_ransac(moving_detections_copy[:, row_indices_2],
                                                   fixed_detections_copy[:, col_indices_2],
                                                   min_samples=4, trials=4000, error=15, transform='Affine')

    print("Inliers 1 = {} and Inliers 2 = {}".format(inliers_best_1, inliers_best_2))
    if (inliers_best_1 > inliers_best_2):
        transform_matrix_sc = transform_matrix_1
    else:
        transform_matrix_sc = transform_matrix_2
    print("transform_matrix_sc \n", transform_matrix_sc)

    print("=" * 50)
    transformed_source_detections = apply_affine_transform(moving_detections_copy, transform_matrix_sc)
    transform_matrix_icp = perform_icp(transformed_source_detections, fixed_detections_copy, 50, 'Affine')
    np.testing.assert_array_almost_equal(affine_transform_gt, np.matmul(transform_matrix_icp, transform_matrix_sc))
