import numpy as np

def getY(z, x):
    y= np.cross(z, x)
    return y/np.linalg.norm(y)

def getShapeContext(points, meanDist, r_inner=1/8, r_outer=2, n_rbins=5, n_thetabins=6, n_phibins=12, verbose=False):
    r=[]
    theta=[]
    phi=[]
    r_edges=np.logspace(np.log10(r_inner),np.log10(r_outer),n_rbins)
    for point in points:
        x_=point[0]
        y_=point[1]
        z_=point[2]
        r_=np.linalg.norm(point)
        r.append(r_/meanDist)
        theta.append(np.arccos(z_/r_))
        if(np.arctan2(y_, x_)<0):
            phi.append(2*np.pi+np.arctan2(y_, x_))
        else:
            phi.append(np.arctan2(y_, x_))
    if(verbose):
        print("r", r)
    index=getBinIndex(r, theta, phi, r_edges, n_rbins, n_thetabins, n_phibins, verbose)
    
    sc=np.zeros((n_rbins*n_thetabins*n_phibins))
    for i in range(n_rbins*n_thetabins*n_phibins):
        sc[i]=index.count(i)
    if(verbose):
        print("sc=", sc)
    return sc

def getMeanDistance(points):
    dist=[]
    for i in range(len(points)-1):
        for j in range(i+1, len(points)):
            dist.append(np.linalg.norm(points[i]-points[j]))
    return np.average(dist)

def getCOM(arr):
    return np.average(arr, 0)

def getBinIndex(r,  theta, phi, r_edges, n_rbins, n_thetabins, n_phibins, verbose=False):
    binIndex=[] # 0 ... 359 (12 * 5 * 6)
    for i in range(len(r)): # i: index over all neighbors
        r_index=n_rbins-1 # by default, it is assumed to be the last most ring
        
            
        theta_index=theta[i]//(np.pi/n_thetabins)
        phi_index=phi[i]//(2*np.pi/n_phibins)
        for ind, edge in enumerate(r_edges): # ind: over all possible edges of bins
            if(r[i]<edge):
                r_index=ind 
                break;
        binIndex.append(r_index*n_thetabins*n_phibins+theta_index*n_phibins+phi_index)
    if(verbose):
        print(binIndex)
    return binIndex

def transform(origin, x_vector, y_vector, z_vector, points, verbose=False):
    x_1_3d=origin + x_vector
    y_1_3d=origin + y_vector
    z_1_3d=origin + z_vector
    if(verbose):
        print("origin", origin)
        print("x_vector", x_vector)
        print("y_vector", y_vector)
        print("z_vector", z_vector)
        print("x_1_3d", x_1_3d)
        print("y_1_3d", y_1_3d)
        print("z_1_3d", z_1_3d)
    A=np.ones((4,4))
    A[0, :3]=origin
    A[1, :3]=x_1_3d
    A[2, :3]=y_1_3d
    A[3, :3]=z_1_3d
    if(verbose):
        print("A", A)
    A=np.transpose(A)
    B=np.array([[0, 1, 0, 0], [0, 0, 1, 0], [ 0, 0, 0, 1], [1, 1, 1, 1]])
    if(verbose):
        print("A inv", np.linalg.inv(A))
    T=np.matmul(B, np.linalg.inv(A))
    if(verbose):
        print("T", T)
    points=np.hstack((points, np.ones((points.shape[0], 1))))
    points_transformed=np.matmul(T, np.transpose(points))
    if(verbose):
        print("points", np.transpose(points_transformed)[:, :-1])
    return np.transpose(points_transformed)[:, :-1]

def getAdjacencyMatrix(points, meanDist, factor=0.2):
    adj=np.zeros((points.shape[0], points.shape[0]))
    adjDist=np.zeros((points.shape[0], points.shape[0]))
    for i in range(adj.shape[0]):
        for j in range(adj.shape[1]):
            if i!=j:
                dis=np.linalg.norm(points[i]-points[j])
                adj[i,j]=dis<factor*meanDist
                adjDist[i,j]=dis
    
    return adj, adjDist


def getUnaryDistance(sc1, sc2):
    dist=0
    R=len(sc1)
    for i in range(sc1.shape[0]):
        if(sc1[i]!=sc2[i]):
            dist=dist+((sc1[i]-sc2[i])**2)/(sc1[i]+sc2[i])
    return 0.5*dist

def doRANSAC(fixedAll, liveAll, min_samples=4, trials=500, error=5):
    inliersBest=0
    Abest=np.ones((4,4))
    for i in range(trials):
        indices=np.random.choice(liveAll.shape[0], min_samples, replace=False)
        liveChosen=liveAll[indices, :] # TODO: is this correct?
        fixedChosen=fixedAll[indices, :]
        fixedChosenSquare=np.hstack((fixedChosen, np.ones((fixedChosen.shape[0], 1))))
        liveChosenSquare=np.hstack((liveChosen, np.ones((liveChosen.shape[0], 1))))
        A=np.matmul(np.transpose(liveChosenSquare), np.linalg.pinv(np.transpose(fixedChosenSquare)))
        fixedAllSquare=np.hstack((fixedAll, np.ones((fixedAll.shape[0], 1))))
        predictedAllSquare=np.matmul(A, np.transpose(fixedAllSquare))
        predictedAllSquare=np.transpose(predictedAllSquare)[:, :-1]
        inliers=0
        for index in range(liveAll.shape[0]):
            d=np.linalg.norm(liveAll[index, :]-predictedAllSquare[index, :])
            if(d<=error):
                inliers+=1
        if (inliers >inliersBest):
            inliersBest=inliers
            Abest=A
    return Abest, inliersBest      

   
def convertIndices2Labels(df, ind, dic):
    lbl=[]
    for i in ind:
        lbl.append(dic[df[i].tobytes()])
    return lbl
    
def getRegisteredNuclei(fixedAll, A):
    fixedAllSquare=np.hstack((fixedAll, np.ones((fixedAll.shape[0], 1))))
    predictedAllSquare=np.matmul(A, np.transpose(fixedAllSquare))
    predictedFixed=np.transpose(predictedAllSquare)[:, :-1]
    return predictedFixed

def getAffineTransform(fixed, live):
    liveChosen=live #TODO
    fixedChosen=fixed #TODO
    fixedChosenSquare=np.hstack((fixedChosen, np.ones((fixedChosen.shape[0], 1))))
    liveChosenSquare=np.hstack((liveChosen, np.ones((liveChosen.shape[0], 1))))
    return np.matmul(np.transpose(liveChosenSquare), np.linalg.pinv(np.transpose(fixedChosenSquare)))

