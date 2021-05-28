[![DOI:10.1007/978-3-030-66415-2_30](https://zenodo.org/badge/DOI/10.1007/978-3-030-66415-2_30.svg)](https://link.springer.com/chapter/10.1007/978-3-030-66415-2_30)
[![License](https://img.shields.io/pypi/l/PlatyMatch.svg?color=green)](https://github.com/juglab/PlatyMatch/raw/master/LICENSE)
[![PyPI](https://img.shields.io/pypi/v/PlatyMatch.svg?color=green)](https://pypi.org/project/PlatyMatch)
[![Python Version](https://img.shields.io/pypi/pyversions/PlatyMatch.svg?color=green)](https://python.org)
[![tests](https://github.com/juglab/PlatyMatch/workflows/tests/badge.svg)](https://github.com/juglab/PlatyMatch/actions)
[![codecov](https://codecov.io/gh/juglab/PlatyMatch/branch/master/graph/badge.svg)](https://codecov.io/gh/juglab/PlatyMatch)


<p align="center">
  <img src="https://user-images.githubusercontent.com/34229641/117537510-b26ee500-b001-11eb-9642-3baa461bfc94.png" width=400 />
</p>
<h2 align="center">Registration of Multi-modal Volumetric Images by Establishing Cell Correspondence</h2>

## Table of Contents

- **[Introduction](#introduction)**
- **[Dependencies](#dependencies)**
- **[Getting Started](#getting-started)**
- **[Datasets](#datasets)**
- **[Registering your data](#registering-your-data)**
- **[Contributing](#contributing)**
- **[Issues](#issues)**
- **[Citation](#citation)**

### Introduction
This repository hosts the version of the code used for the **[publication](https://link.springer.com/chapter/10.1007/978-3-030-66415-2_30)** **Registration of Multi-modal Volumetric Images by Establishing Cell Correspondence**. 

We refer to the techniques elaborated in the publication, here as **PlatyMatch**. `PlatyMatch` allows registration of volumetric, microscopy images of embryos by establishing correspondences between cells. 

`PlatyMatch` first performs detection of nuclei in the two images being considered, next calculates unique `shape context` features for each nucleus detection which encapsulates the neighborhood as seen by that nucleus, and finally identifies pairs of matching nuclei through bipartite matching applied to the pairwise distance matrix generated from these features. 

### Dependencies 

You can install `PlatyMatch` via **[pip]**:

```
conda create -y -n PlatyMatchEnv python=3.8
conda activate PlatyMatchEnv
python3 -m pip install PlatyMatch
```

### Getting Started

Type in the following commands in a new terminal window.

```
conda activate PlatyMatchEnv
napari
```

Next, select `PlatyMatch` from `Plugins> Add Dock Widget`.

### Datasets

Datasets are available as release assets **[here](https://github.com/juglab/PlatyMatch/releases/tag/v0.0.1)**.
These comprise of images, nuclei detections and keypoint locations for confocal images of 12 individual specimens under the `01-insitus` directory and static snapshots of a live embryo imaged through Light Sheet Microscopy under the `02-live` directory. 
Folders with the same name in these two directories correspond in their developmental age, for example, `01-insitus/02` corresponds to `02-live/02`, `01-insitus/03` corresponds to `02-live/03` and so on.   


### Registering your data

- **Detect Nuclei** 
	- Drag and drop your images in the viewer 
	- Click on `Sync with Viewer` button to refresh the drop-down menus 
	- Select the appropraite image in the drop down menu (for which nuclei detections are desired)
	- Select **`Detect Nuclei`** from the drop-down menu
	- Specify the anisotropy factor (`Anisotropy (Z)`) (i.e. the ratio of the size of the z pixel with respect to the x or y pixel. This factor is typically more than 1.0 because the z dimension is often undersampled)
	- Click `Run Scale Space Log` button
	- Wait until a confirmation message suggesting that nuclei detection is over shows up on the terminal
	- Export the nuclei locations (`Export detections to csv`) to a csv file
	- Repeat this step for all images which need to be matched
- **Estimate Transform**
	- In case, nuclei were exported to a csv in the `Detect Nuclei` panel, tick `csv` checkbox
	- If the nuclei detected were specified in the order id, z, y and x in the csv file, then tick `IZYXR` checkbox
	- Additionally if there is a header in the csv file, tick `Header` checkbox
	- Load the detections for the `Moving Image`, which is defined as the image which will be transformed to later match another `fixed` image
	- Load the detections for the `Fixed Image`
	- If these two images correspond to the same imaging modality, then select the `Unsupervised` option under `Estimate Transform` checkbox (this corresponds to Intramodal Registration in the publication)
	- If these two images correspond to different imaging modalities, then select the `Supervised` option under `Estimate Transform` checkbox (this corresponds to Intermodal Registration in the publication)
	- For the Intramodal use case, click on `Run` pushbutton. Once the calculation is complete, a confirmation message shows up in the terminal. Export the transform matrix to a csv.
	- For the Intermodal use case, upload the locations of a few matching keypoints in both images. These locations serve to provide a good starting point for the transform calculation. Once the keypoint files have been uploaded for both the images, then click `Run` and then export the transform matrix to a csv.  
- **Evaluate Metrics**
	- Drag images which need to be transformed, in the viewer
	- Click on `Sync with Viewer` button to refresh the drop-down menus
	- Specify the anisotropy factor (`Moving Image Anisotropy (Z)` and `Fixed Image Anisotropy (Z)`) (i.e. the ratio of the size of the z pixel with respect to the x or y pixel. This factor is typically more than 1.0 because the z dimension is often undersampled)
	- Load the transform which was calculated in the previous steps
	- If you simply wish to export a transformed version of the moving image, click on `Export Transformed Image`
	- Additionallly, one could quantify metrics such as average registration error evaluated on a few keypoints. To do so, tick the `csv` checkbox, if keypoints and detections are available as a csv file. Then load the keypoints for the moving image (`Moving Kepoints`) and the fixed image (`Fixed Keypoints`).
	- Also, upload the detections calculated in the previous steps (`Detect Nuclei`)  by uploading the `Moving Detections` and the `Fixed Detections`
	- Click on the `Run` push button. 
	- The text fields such as `Matching Accuracy`(0 to 1, with 1 being the best) and `Average Registration Error` (the lower the better) should become populated once the results are available

### Contributing

Contributions are very welcome. Tests can be run with **[tox]**.

### Issues

If you encounter any problems, please **[file an issue]** along with a detailed description.

[file an issue]: https://github.com/juglab/PlatyMatch/issues
[tox]: https://tox.readthedocs.io/en/latest/
[pip]: https://pypi.org/project/EmbedSeg/


### Citation
If you find our work useful in your research, please consider citing:

```bibtex
@InProceedings{10.1007/978-3-030-66415-2_30,
author="Lalit, Manan and Handberg-Thorsager, Mette and Hsieh, Yu-Wen and Jug, Florian and Tomancak, Pavel",
editor="Bartoli, Adrien
and Fusiello, Andrea",
title="Registration of Multi-modal Volumetric Images by Establishing Cell Correspondence",
booktitle="Computer Vision -- ECCV 2020 Workshops",
year="2020",
publisher="Springer International Publishing",
address="Cham",
pages="458--473",
isbn="978-3-030-66415-2"
}
```

`PlatyMatch` plugin was generated with [Cookiecutter] using with [@napari]'s [cookiecutter-napari-plugin] template.

[napari]: https://github.com/napari/napari
[Cookiecutter]: https://github.com/audreyr/cookiecutter
[@napari]: https://github.com/napari
[MIT]: http://opensource.org/licenses/MIT
[BSD-3]: http://opensource.org/licenses/BSD-3-Clause
[GNU GPL v3.0]: http://www.gnu.org/licenses/gpl-3.0.txt
[GNU LGPL v3.0]: http://www.gnu.org/licenses/lgpl-3.0.txt
[Apache Software License 2.0]: http://www.apache.org/licenses/LICENSE-2.0
[Mozilla Public License 2.0]: https://www.mozilla.org/media/MPL/2.0/index.txt
[cookiecutter-napari-plugin]: https://github.com/napari/cookiecutter-napari-plugin
[file an issue]: https://github.com/juglab/PlatyMatch/issues
[napari]: https://github.com/napari/napari
[tox]: https://tox.readthedocs.io/en/latest/
[pip]: https://pypi.org/project/pip/
[PyPI]: https://pypi.org/
