Fine Alignment
=================

ICP
^^^^
The previous step provides us a good initial alignment. Next, we employ `Iterative Closest Point` which  alternates between establishing correspondences via closest-point lookups and recomputing the optimal transform based on the current set of correspondences.

.. figure:: images/after_ICP.png
   :width: 100 %
   :alt: schematic



Obtaining the complete Set of Correspondences
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

We build a M * N-sized cost matrix C where  the entry Cij is the euclidean distance between the ith transformed source cell nucleus detection and the jth target cell nucleus detection. Next, we employ the Hungarian Algorithm to perform a maximum bipartite matching and estimate correspondences

