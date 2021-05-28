import numpy as np
from scipy.spatial import distance_matrix
from platymatch.estimate_transform.find_transform import get_affine_transform
from platymatch.estimate_transform.apply_transform import apply_affine_transform
from platymatch.utils.utils import get_error

def perform_icp(moving, fixed, icp_iterations = 50, transform='Affine'):
    if moving.shape[0] == 4:  # 4 x N
        moving = moving[:3, :] # 3 x N
    if fixed.shape[0] == 4:  # 4 x N
        fixed = fixed[:3, :] # 3 x N

    A_icp = np.identity(4)
    for i in range(icp_iterations):
        cost_matrix=distance_matrix(moving.transpose(), fixed.transpose()) # (N x 3, N x 3)
        i2=np.argmin(cost_matrix, 1)
        if(transform=='Affine'):
            A_est=get_affine_transform(moving, fixed[:, i2])
        elif(transform=='similar'):
            A_est = get_similar_transform(moving, fixed[:, i2])
        elif(transform=='rigid'):
            A_est = get_rigid_transform(moving, fixed[:, i2])
        moving = apply_affine_transform(moving, A_est)
        print("Residual at iteration {} is {}".format(str(i), get_error(moving, fixed[:, i2])))
        A_icp=np.matmul(A_est, A_icp)
    return A_icp