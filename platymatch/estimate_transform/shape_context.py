import numpy as np
from sklearn.decomposition import PCA
from platymatch.estimate_transform.find_transform import get_affine_transform, get_similar_transform
from platymatch.estimate_transform.apply_transform import *

def get_Y(z, x):
    y = np.cross(z, x)
    return y / np.linalg.norm(y)

def get_shape_context(neighbors, mean_dist, r_inner=1 / 8, r_outer=2, n_rbins=5, n_thetabins=6, n_phibins=12):
    """
    :param neighbors:  N-1 x 3
    :param mean_dist: 1
    :param r_inner:
    :param r_outer:
    :param n_rbins:
    :param n_thetabins:
    :param n_phibins:
    :return:
    """
    r = []
    theta = []
    phi = []
    r_edges = np.logspace(np.log10(r_inner), np.log10(r_outer), n_rbins)
    for neighbor in neighbors:
        x_ = neighbor[0]
        y_ = neighbor[1]
        z_ = neighbor[2]
        r_ = np.linalg.norm(neighbor)
        r.append(r_ / mean_dist)
        theta.append(np.arccos(z_ / r_))
        if (np.arctan2(y_, x_) < 0):
            phi.append(2 * np.pi + np.arctan2(y_, x_))
        else:
            phi.append(np.arctan2(y_, x_))
    index = get_bin_index(r, theta, phi, r_edges, n_rbins, n_thetabins, n_phibins)

    sc = np.zeros((n_rbins * n_thetabins * n_phibins))
    for i in range(n_rbins * n_thetabins * n_phibins):
        sc[i] = index.count(i)
    sc= sc/sc.sum()
    return sc



def get_bin_index(r, theta, phi, r_edges, n_rbins, n_thetabins, n_phibins):
    binIndex = []  # 0 ... 359 (12 * 5 * 6)
    for i in range(len(r)):  # i: index over all neighbors
        r_index = n_rbins - 1  # by default, it is assumed to be the last most ring

        theta_index = theta[i] // (np.pi / n_thetabins)
        phi_index = phi[i] // (2 * np.pi / n_phibins)
        for ind, edge in enumerate(r_edges):  # ind: over all possible edges of bins
            if (r[i] < edge):
                r_index = ind
                break;
        binIndex.append(r_index * n_thetabins * n_phibins + theta_index * n_phibins + phi_index)
    return binIndex


def transform(detection, x_vector, y_vector, z_vector, neighbors):
    """

    :param detection: 1 x 3
    :param x_vector: 1 x 3
    :param y_vector: 1 x 3
    :param z_vector: 1 x 3
    :param neighbors: (N-1) x 3
    :return:
    """
    x_1_3d = detection + x_vector
    y_1_3d = detection + y_vector
    z_1_3d = detection + z_vector
    A = np.ones((4, 4))
    A[0, :3] = detection
    A[1, :3] = x_1_3d
    A[2, :3] = y_1_3d
    A[3, :3] = z_1_3d
    A = np.transpose(A)
    B = np.array([[0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1], [1, 1, 1, 1]])
    T = np.matmul(B, np.linalg.inv(A))
    neighbors = np.hstack((neighbors, np.ones((neighbors.shape[0], 1))))
    points_transformed = np.matmul(T, np.transpose(neighbors))
    return np.transpose(points_transformed)[:, :3]



def get_unary_distance(sc1, sc2):
    """

    :param sc1: (360, )
    :param sc2: (360, )
    :return:
    """
    dist = 0
    for i in range(sc1.shape[0]):
        if (sc1[i] != sc2[i]):
            dist = dist + ((sc1[i] - sc2[i]) ** 2) / (sc1[i] + sc2[i])
    return 0.5 * dist



def do_ransac(moving_all, fixed_all, min_samples=4, trials=500, error=5, transform ='Affine'):
    """

    :param moving_all: 4 x N
    :param fixed_all: 4 x N
    :param min_samples:
    :param trials:
    :param error:
    :param transform:
    :return:
    """

    if moving_all.shape[0] == 4 or fixed_all.shape[0]==4:  # 4 x N
        moving_all = moving_all[:3, :] # 3 x N
        fixed_all = fixed_all[:3, :] # 3 x N

    inliers_best = 0
    A_best = np.ones((4, 4))
    for i in range(trials):
        indices = np.random.choice(fixed_all.shape[1], min_samples, replace=False)
        target_chosen = fixed_all[:, indices] # 3 x min_samples
        source_chosen = moving_all[:, indices] # 3 x min_samples

        if transform =='Affine':
            transform_matrix = get_affine_transform(source_chosen, target_chosen)
        elif transform =='Similar':
            transform_matrix = get_similar_transform(source_chosen, target_chosen)
        predicted_all = apply_affine_transform(moving_all, transform_matrix)  # 3 x N
        inliers = 0
        for index in range(fixed_all.shape[1]):
            d = np.linalg.norm(fixed_all[:, index] - predicted_all[:, index])
            if (d <= error):
                inliers += 1
        if (inliers > inliers_best):
            inliers_best = inliers
            A_best = transform_matrix
    return A_best, inliers_best




def get_unary(centroid, mean_distance, detections, transposed = False):
    """
    :param centroid: N x 3 or 3 x N (transposed = False)
    :param mean_distance:
    :param detections: N x 4
    :return:
    """
    if(transposed): # N x 3
        pass
    else: # 3 x N
        detections = detections.transpose()
        centroid = centroid.transpose()
    if detections.shape[1] == 4:  # N x 4
        detections = detections[:, :3] # N x 3
    sc = []
    sc2 = []
    pca = PCA(n_components=3)
    pca.fit(detections)
    V = pca.components_
    x0_vector = V[0:1, :] # 1 x 3
    x0_vector2 = -1 * x0_vector
    for queried_index, detection in enumerate(detections):
        neighbors = np.delete(detections, queried_index, 0) # (N-1) x 3
        z_vector = (detection - centroid) / np.linalg.norm(detection - centroid) # vector 1 --> Z
        x_vector = x0_vector - z_vector * np.dot(x0_vector[0, :], z_vector[0, :]) # convert to 1D vectors
        x_vector = x_vector / np.linalg.norm(x_vector) # vector 2 --> X
        x_vector2 = x0_vector2 - z_vector * np.dot(x0_vector2[0, :], z_vector[0, :])
        x_vector2 = x_vector2 / np.linalg.norm(x_vector2)
        y_vector = get_Y(z_vector, x_vector)
        y_vector2 = get_Y(z_vector, x_vector2)
        neighbors_transformed = transform(detection, x_vector, y_vector, z_vector, neighbors) # (N-1) x 3
        neighbors_transformed2 = transform(detection, x_vector2, y_vector2, z_vector, neighbors) # (N-1) x 3
        sc.append(get_shape_context(neighbors_transformed, mean_distance))
        sc2.append(get_shape_context(neighbors_transformed2, mean_distance))
    return np.array(sc), np.array(sc2)