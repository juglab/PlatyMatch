.. PlatyMatch documentation master file, created by
   sphinx-quickstart on Sun Aug 23 15:51:10 2020.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

PlatyMatch's documentation
======================================

*PlatyMatch* is a *Fiji* plugin which enables performing registration of volumetric images of embryos, which have been collected through different imaging modalities. This is achieved by identifying cellular correspondences and leads to an implicit registration of the images. The core of the employed matching algorithm is based on a variant of 3-D `Shape Context` which was proposed by Belongie et al :cite:`Belongie2002`.  

.. figure:: images/intro.png
   :width: 100 %
   :alt: schematic


.. toctree::
   :maxdepth: 2
   :caption: Contents:

   Data
   Installation
   Detecting-Nuclei
   Coarse-Alignment
   Fine-Alignment


.. bibliography:: references.bib
