Coarse Alignment
==================

Shape Context
^^^^^^^^^^^^^^^^^

In this section, we will provide the details of our implementation of the 3D shape context  geometric descriptor, which is a signature obtained uniquely for all feature points in the source and target point clouds. 
FOr each basis point, we identify a unique coordinate system which makes the problem of matching rotationally (and partially scale) invariant.
The support region around any basis point is discretized into bins, and a histogram is formed by counting the number of point neighbours falling within each bin. Like in Belongie et al 's work, in order to be more sensitive to nearby points, we use a log-polar coordinate system  In our experiments, we build a 3D histogram with 5  equally spaced log-radius bins and 6 and 12 equally spaced elevation  and azimuth  bins respectively.

.. figure:: images/shapeContext.gif
   :width: 100 %
   :alt: schematic


RANSAC
^^^^^^^^^^

By comparing shape contexts resulting from the two clouds of cell nuclei detections, we obtain an initial temporary set of correspondences. These are filtered to obtain a set of inlier point correspondences using RANSAC Fischler1981.
In our experiments, we specified an affine transform model, which requires a sampling of 4 pairs of corresponding points.
We executed RANSAC for 20000 trials, used the Moore-Penrose Pseudo-Inverse operation to estimate the affine transform between the two sets of corresponding locations, and allowed an inlier cutoff of 15 pixels L2 distance between the transformed and the target nucleus locations.

.. figure:: images/ransac.png
   :width: 100 %
   :alt: schematic



