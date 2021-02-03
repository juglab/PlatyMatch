import numpy as np
from scipy.spatial import distance_matrix


def getTransformedNuclei(sourceAll, A):
    sourceAllSquare=np.hstack((sourceAll, np.ones((sourceAll.shape[0], 1))))
    sourceAllSquare_transformed=np.matmul(A, np.transpose(sourceAllSquare))
    sourceAll_transformed=np.transpose(sourceAllSquare_transformed)[:, :-1]
    return sourceAll_transformed

def getAffineTransformMatrix(source, target):
    sourceSquare=np.hstack((source, np.ones((source.shape[0], 1))))
    targetSquare=np.hstack((target, np.ones((target.shape[0], 1))))
    return np.matmul(np.transpose(targetSquare), np.linalg.pinv(np.transpose(sourceSquare)))

def performICP(icpIterations, sourceDetections, targetDetections):
    A_icp = np.identity(4)
    for i in range(icpIterations):
        costmatrix=distance_matrix(sourceDetections, targetDetections)
        i2=np.argmin(costmatrix, 1)
        A_est=getAffineTransformMatrix(sourceDetections, targetDetections[i2, ...])
        sourceDetections=getTransformedNuclei(sourceDetections, A_est)
        residual=sourceDetections-targetDetections[i2, :]
        print("Residual at iteration {} is {}".format(str(i), np.mean(np.linalg.norm(residual, axis = 1))))
        A_icp=np.matmul(A_est, A_icp)
    np.save('/home/manan/Desktop/cost_matrix', costmatrix)
    return A_icp
