import csv
import enum
import os
import sys

import SimpleITK as sitk
import napari
import numpy as np
import pandas as pd
import tifffile
from PyQt5 import QtCore
from PyQt5 import QtWidgets
from PyQt5.QtCore import Qt
from PyQt5.QtWidgets import QLabel, QLineEdit, QComboBox, QSlider, QFileDialog, QWidget, QProgressBar
from PyQt5.QtWidgets import QPushButton
from matplotlib.backends.backend_qt5agg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
from scipy.optimize import linear_sum_assignment
from sklearn.decomposition import PCA

import registration_callbacks as rc
import registration_utilities as ru
from detectNuclei.blob import findNuclei, getPeaksSubset, suppressIntersectingNuclei
from registerCoarsely.performICP import performICP, getTransformedNuclei, getAffineTransformMatrix
from registerCoarsely.performSC import *


# Enums are a convenient way to get a dropdown menu
class Operation(enum.Enum):
    """A set of valid arithmetic operations for image_arithmetic."""
    add = np.add
    subtract = np.subtract
    multiply = np.multiply
    divide = np.divide


class Slider(QWidget):
    def __init__(self, minVal, maxVal):
        super().__init__()
        self.minVal = minVal
        self.maxVal = maxVal

        self.setSizePolicy(
            QtWidgets.QSizePolicy.MinimumExpanding,
            QtWidgets.QSizePolicy.MinimumExpanding
        )

        layout = QtWidgets.QGridLayout()
        self.thresholdSlider = QSlider(Qt.Horizontal, self)
        # self.thresholdSlider.valueChanged.connect(self.valueChanged)
        layout.addWidget(self.thresholdSlider, 0, 0, 1, 2)
        self.minValLabel = QLabel(self)
        self.minValLabel.setText(str(self.minVal))
        self.minValLabel.setAlignment(Qt.AlignLeft)
        layout.addWidget(self.minValLabel)
        self.maxValLabel = QLabel(self)
        self.maxValLabel.setText(str(self.maxVal))
        self.maxValLabel.setAlignment(Qt.AlignRight)
        layout.addWidget(self.maxValLabel)
        self.setLayout(layout)

    def valueChanged(self):
        threshold = self.thresholdSlider.value()
        print("Current slider value is", threshold)

    def update(self):
        self.minValLabel.setText('%.2f' % self.minVal)
        self.maxValLabel.setText('%.2f' % self.maxVal)


class DetectNuclei(QWidget):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

        self.setSizePolicy(
            QtWidgets.QSizePolicy.MinimumExpanding,
            QtWidgets.QSizePolicy.MinimumExpanding
        )

        layout = QtWidgets.QGridLayout()
        self.nuclei = []
        self.openImageButton = QPushButton('Open Image', self)
        self.openImageButton.clicked.connect(self.openImages)
        layout.addWidget(self.openImageButton, 0, 0)

        self.processImageLabel = QLabel(self)
        self.processImageLabel.setText('Process Image:')
        layout.addWidget(self.processImageLabel, 1, 0)
        self.imagesComboBox = QComboBox(self)

        # for layer in viewer.layers:
        #     print(layer.name)
        #     self.imagesComboBox.addItem(layer.name)
        layout.addWidget(self.imagesComboBox, 1, 1)
        self.minSigmaLabel = QLabel(self)
        self.minSigmaLabel.setText('Min Sigma:')
        self.minSigmaLine = QLineEdit(self)
        self.minSigmaLine.setText("5")
        self.minSigmaLine.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.minSigmaLabel, 2, 0)
        layout.addWidget(self.minSigmaLine, 2, 1)

        self.maxSigmaLabel = QLabel(self)
        self.maxSigmaLabel.setText('Max Sigma:')
        self.maxSigmaLine = QLineEdit(self)
        self.maxSigmaLine.setText("9")
        self.maxSigmaLine.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.maxSigmaLabel, 3, 0)
        layout.addWidget(self.maxSigmaLine, 3, 1)

        self.stepSigmaLabel = QLabel(self)
        self.stepSigmaLabel.setText('Scale Sigma:')
        self.stepSigmaLine = QLineEdit(self)
        self.stepSigmaLine.setText("1")
        self.stepSigmaLine.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.stepSigmaLabel, 4, 0)
        layout.addWidget(self.stepSigmaLine, 4, 1)

        self.anisotropyLabel = QLabel(self)
        self.anisotropyLabel.setText('Anisotropy (Z):')
        self.anisotropyLine = QLineEdit(self)
        self.anisotropyLine.setText("5")
        self.anisotropyLine.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.anisotropyLabel, 5, 0)
        layout.addWidget(self.anisotropyLine, 5, 1)

        self.runButton = QPushButton('Run', self)
        self.runButton.clicked.connect(self.clickRun)
        layout.addWidget(self.runButton, 6, 0)

        self.progressBar = QProgressBar(self)
        layout.addWidget(self.progressBar, 7, 0, 1, 2)
        # steps = 20
        # self.worker = two_way_communication_with_args(0, steps)
        # self.runButton.clicked.connect(self.worker.start)

        self.thresholdSlider = Slider(minVal=-100, maxVal=0)
        self.thresholdSlider.thresholdSlider.valueChanged.connect(self.repaintDetections)
        layout.addWidget(self.thresholdSlider, 8, 0, 1, 2)  # how does this work?

        self.suppressIntersectingDetectionsButton = QPushButton('Suppress Intersections', self)
        self.suppressIntersectingDetectionsButton.clicked.connect(self.suppressIntersections)
        layout.addWidget(self.suppressIntersectingDetectionsButton, 9, 0)

        self.exportButton = QPushButton('Export Detections', self)
        self.exportButton.clicked.connect(self.exportMethod)
        layout.addWidget(self.exportButton, 9, 1)

        self.loadDetections = QPushButton('Load Detections', self)
        layout.addWidget(self.loadDetections, 10, 0)

        self.setLayout(layout)

    def clickRun(self):

        data = viewer.layers[self.imagesComboBox.currentIndex()].data
        self.peaks, self.nuclei, self.log, self.localMinima, threshold = findNuclei(data,
                                                                                    scales=range(
                                                                                        int(self.minSigmaLine.text()),
                                                                                        int(self.maxSigmaLine.text()),
                                                                                        int(self.stepSigmaLine.text())),
                                                                                    anisotropyFactor=float(
                                                                                        self.anisotropyLine.text()),
                                                                                    progress_bar=self.progressBar)
        self.thresholdSlider.minVal = threshold * 2
        self.thresholdSlider.maxVal = threshold * (1 / 10)
        self.thresholdSlider.update()

        print("Number of detected nuclei are", self.nuclei.shape[0])
        self.visualizeNuclei()

    def suppressIntersections(self):
        self.nuclei = np.asarray(
            suppressIntersectingNuclei(self.peaks, self.nuclei, self.log, float(self.anisotropyLine.text())))
        print("Total number of nuclei {} after suppressing intersecting detections is ******".format(len(self.nuclei)))
        self.visualizeNuclei()

    def exportMethod(self):
        name = QFileDialog.getSaveFileName(self, 'Save File')  # this returns a tuple!
        print("Saving Detections at {} ******".format(name[0]))
        self.to_csv(name[0])

    def openImages(self):
        name = QFileDialog.getOpenFileName(self, 'Open File')  # this returns a tuple!
        print("Opening image {} ******".format(name[0]))
        viewer.add_image(tifffile.imread(name[0]).astype('float32'), name=os.path.basename(name[0]))
        self.name = os.path.basename(name[0])
        for layer in viewer.layers:
            if (layer.data.ndim == 3):  # image
                self.imagesComboBox.addItem(layer.name)  # add the actual content of self.comboData

    def to_csv(self, filename):
        for layer in viewer.layers:
            if layer.name == 'points-' + self.name:
                self.nuclei = layer.data  # we lose the size information TODO

        with open(filename, mode='w') as file:
            writer = csv.writer(file, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
            n_dimensions = self.nuclei.shape[1]
            header = ['id'] + ['dimension_' + str(n) for n in range(n_dimensions)]
            writer.writerow(header)
            for idx, row in enumerate(self.nuclei):
                point_data = np.concatenate([np.array([idx]), row])
                writer.writerow(point_data)

    def map2threshold(self):
        return self.thresholdSlider.minVal + (self.thresholdSlider.thresholdSlider.value()) / 99 * (
                self.thresholdSlider.maxVal - self.thresholdSlider.minVal)

    def repaintDetections(self):
        threshold = self.map2threshold()
        self.peaks, self.nuclei, _ = getPeaksSubset(self.log, self.localMinima, np.asarray(
            range(int(self.minSigmaLine.text()), int(self.maxSigmaLine.text()), int(self.stepSigmaLine.text()))),
                                                    threshold)
        print(
            "Total number of nuclei {} at the current threshold value is {} ******".format(len(self.nuclei), threshold))
        self.visualizeNuclei()

    def visualizeNuclei(self):
        for layer in viewer.layers:
            if layer.name == 'points-' + self.name:
                viewer.layers.remove('points-' + self.name)

        point_properties = {
            'confidence': self.nuclei[:, 1:],  # directly use xyz as rgb!

        }
        viewer.add_points(self.nuclei[:, 1:], size=np.sqrt(3) * 2 * self.nuclei[:, 0],
                          properties=point_properties,
                          face_color='confidence',
                          face_colormap='viridis',
                          opacity=0.5,
                          name="points-" + self.name)

    # def paintEvent(self, e):
    #
    #     self.imagesComboBox = QComboBox(self)
    #     for layer in viewer.layers:
    #         print(layer.name)
    #         self.imagesComboBox.addItem(layer.name)
    #
    #
    # def _trigger_refresh(self):
    #     self.update()


class CoarseAlignment(QWidget):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

        self.setSizePolicy(
            QtWidgets.QSizePolicy.MinimumExpanding,
            QtWidgets.QSizePolicy.MinimumExpanding
        )

        layout = QtWidgets.QGridLayout()
        self.modelLabel = QLabel(self)
        self.modelLabel.setText("Mode")

        self.modeComboBox = QComboBox(self)
        self.modeComboBox.addItems(['Manual', 'Semi-automated', 'Automated'])
        self.modeComboBox.currentIndexChanged.connect(self.modeComboboxChanged)

        self.transformLabel = QLabel(self)
        self.transformLabel.setText("Transformation")

        self.transformComboBox = QComboBox(self)
        self.transformComboBox.addItems(['Rigid', 'Similar', 'Affine'])

        self.sourceDetectionsButton = QPushButton('Browse Source Detections', self)
        self.sourceDetectionsButton.clicked.connect(self.browseDetections)
        self.targetDetectionsButton = QPushButton('Browse Target Detections', self)
        self.targetDetectionsButton.clicked.connect(self.browseDetections)
        self.sourceLandmarksButton = QPushButton('Browse Source Landmarks', self)
        self.sourceLandmarksButton.clicked.connect(self.browseDetections)
        self.targetLandmarksButton = QPushButton('Browse Target Landmarks', self)
        self.targetLandmarksButton.clicked.connect(self.browseDetections)

        self.browseTransformButton1 = QPushButton('Browse Transform 1', self)
        self.browseTransformButton2 = QPushButton('Browse Transform 2', self)
        self.browseTransformButton1.clicked.connect(self.loadTransform)
        self.browseTransformButton2.clicked.connect(self.loadTransform)
        self.browseTransformButton1.hide()
        self.browseTransformButton2.hide()

        self.rBinsLabel = QLabel(self)
        self.rBinsLabel.setText('r bins')
        self.rBinsLine = QLineEdit(self)
        self.rBinsLine.setText('5')
        self.rBinsLine.setAlignment(Qt.AlignCenter)
        self.rBinsLabel.hide()
        self.rBinsLine.hide()

        self.thetaBinsLabel = QLabel(self)
        self.thetaBinsLabel.setText('\u03B8 bins')
        self.thetaBinsLine = QLineEdit(self)
        self.thetaBinsLine.setText('6')
        self.thetaBinsLine.setAlignment(Qt.AlignCenter)
        self.thetaBinsLabel.hide()
        self.thetaBinsLine.hide()

        self.phiBinsLabel = QLabel(self)
        self.phiBinsLabel.setText('\u03C6 bins')
        self.phiBinsLine = QLineEdit(self)
        self.phiBinsLine.setText('12')
        self.phiBinsLine.setAlignment(Qt.AlignCenter)
        self.phiBinsLabel.hide()
        self.phiBinsLine.hide()

        self.ransacIterationsLabel = QLabel(self)
        self.ransacIterationsLabel.setText('RANSAC iterations')
        self.ransacIterationsLine = QLineEdit(self)
        self.ransacIterationsLine.setText('4000')
        self.ransacIterationsLine.setAlignment(Qt.AlignCenter)
        self.ransacIterationsLabel.hide()
        self.ransacIterationsLine.hide()

        self.ransacSamplesLabel = QLabel(self)
        self.ransacSamplesLabel.setText('RANSAC samples')
        self.ransacSamplesLine = QLineEdit(self)
        self.ransacSamplesLine.setText('4')
        self.ransacSamplesLine.setAlignment(Qt.AlignCenter)
        self.ransacSamplesLabel.hide()
        self.ransacSamplesLine.hide()

        self.ransacThresholdLabel = QLabel(self)
        self.ransacThresholdLabel.setText('RANSAC threshold')
        self.ransacThresholdLine = QLineEdit(self)
        self.ransacThresholdLine.setText('15')
        self.ransacThresholdLine.setAlignment(Qt.AlignCenter)
        self.ransacThresholdLabel.hide()
        self.ransacThresholdLine.hide()

        self.runSCButton = QPushButton(self)
        self.runSCButton.setText('Run')
        self.runSCButton.clicked.connect(self.runTransform1)
        self.saveTransformSCButton = QPushButton(self)
        self.saveTransformSCButton.setText('Save Transform')
        self.saveTransformSCButton.clicked.connect(self.saveTransform)
        self.icpIterationsLabel = QLabel(self)
        self.icpIterationsLabel.setText('ICP iterations')
        self.icpIterationsLine = QLineEdit(self)
        self.icpIterationsLine.setText('50')
        self.icpIterationsLine.setAlignment(Qt.AlignCenter)

        self.runICPButton = QPushButton(self)
        self.runICPButton.setText('Run')
        self.runICPButton.clicked.connect(self.performICP)
        self.saveTransformICPButton = QPushButton(self)
        self.saveTransformICPButton.setText('Save Transform - ICP')
        self.saveTransformICPButton.clicked.connect(self.saveTransform)
        self.saveTransformCombinedButton = QPushButton(self)
        self.saveTransformCombinedButton.setText('Save Transform - Combined')
        self.saveTransformCombinedButton.clicked.connect(self.saveTransform)

        layout.addWidget(self.modelLabel, 0, 0)
        layout.addWidget(self.modeComboBox, 0, 1)
        layout.addWidget(self.transformLabel, 1, 0)
        layout.addWidget(self.transformComboBox, 1, 1)
        layout.addWidget(self.sourceDetectionsButton, 2, 0, 1, 2)
        layout.addWidget(self.targetDetectionsButton, 3, 0, 1, 2)
        layout.addWidget(self.sourceLandmarksButton, 4, 0, 1, 2)
        layout.addWidget(self.targetLandmarksButton, 5, 0, 1, 2)
        layout.addWidget(self.browseTransformButton1, 6, 0)
        layout.addWidget(self.browseTransformButton2, 6, 1)
        layout.addWidget(self.rBinsLabel, 6, 0)
        layout.addWidget(self.rBinsLine, 6, 1)
        layout.addWidget(self.thetaBinsLabel, 7, 0)
        layout.addWidget(self.thetaBinsLine, 7, 1)
        layout.addWidget(self.phiBinsLabel, 8, 0)
        layout.addWidget(self.phiBinsLine, 8, 1)
        layout.addWidget(self.ransacIterationsLabel, 9, 0)
        layout.addWidget(self.ransacIterationsLine, 9, 1)
        layout.addWidget(self.ransacSamplesLabel, 10, 0)
        layout.addWidget(self.ransacSamplesLine, 10, 1)
        layout.addWidget(self.ransacThresholdLabel, 11, 0)
        layout.addWidget(self.ransacThresholdLine, 11, 1)
        layout.addWidget(self.runSCButton, 12, 0)
        layout.addWidget(self.saveTransformSCButton, 12, 1)
        layout.addWidget(self.icpIterationsLabel, 13, 0)
        layout.addWidget(self.icpIterationsLine, 13, 1)
        layout.addWidget(self.runICPButton, 14, 0)
        layout.addWidget(self.saveTransformICPButton, 14, 1)
        layout.addWidget(self.saveTransformCombinedButton, 15, 0, 1, 2)
        self.setLayout(layout)

    def browseDetections(self):
        name = QFileDialog.getOpenFileName(self, 'Open File')  # this returns a tuple!
        print("Opening detections {} ******".format(name[0]))
        detections_df = pd.read_csv(name[0], skiprows=None, delimiter=' ',
                                    header=None)  # TODO skiprows = 0 == > if there is a
        detections_numpy = detections_df.to_numpy()
        detections_ids = detections_numpy[:, 0] # first column should contain ids
        detections_numpy = np.flip(detections_numpy[:, 1:4], 1)
        print("******")
        print("detections_numpy", detections_numpy)
        if self.sender().text() == 'Browse Source Detections':
            self.source_detections = detections_numpy  # TODO be careful if data has three or four columns
            self.source_ids= detections_ids
            temp_name = 'source' + '_' + 'detections'
            temp_marker = 'disc'
        elif self.sender().text() == 'Browse Target Detections':
            self.target_detections = detections_numpy
            self.target_ids = detections_ids
            temp_name = 'target' + '_' + 'detections'
            temp_marker = 'disc'
        elif self.sender().text() == 'Browse Source Landmarks':
            self.source_landmarks = detections_numpy  # TODO be careful if data has three or four columns
            self.source_landmarks_ids = detections_ids
            temp_name = 'source' + '_' + 'landmarks'
            temp_marker = 'cross'
        elif self.sender().text() == 'Browse Target Landmarks':
            self.target_landmarks = detections_numpy
            self.target_landmarks_ids = detections_ids
            temp_name = 'target' + '_' + 'landmarks'
            temp_marker = 'cross'

        point_properties = {
            'confidence': detections_numpy,  # directly use xyz as rgb!
        }

        viewer.add_points(detections_numpy, name=temp_name,
                          properties=point_properties,
                          face_color='confidence',
                          face_colormap='viridis',
                          symbol=temp_marker)

        # print("Detections {} is of shape {}******".format(name[0]), detections_numpy.shape)
        # viewer.add_points(tifffile.imread(name[0]).astype('float32'), name=os.path.basename(name[0]))
        # self.name = os.path.basename(name[0])
        # for layer in viewer.layers:
        #     if(layer.data.ndim==3): # image
        #         self.imagesComboBox.addItem(layer.name)  # add the actual content of self.comboData

    def runTransform1(self):
        if self.modeComboBox.currentIndex() == 0:
            self.transformMatrix1 = getAffineTransformMatrix(self.source_landmarks, self.target_landmarks)
        elif self.modeComboBox.currentIndex() == 2:
            self.performSC()

    def loadTransform(self):
        name = QFileDialog.getOpenFileName(self, 'Open Transform File')  # this returns a tuple!
        print("Opening transform file {} ******".format(name[0]))
        transform_df = pd.read_csv(name[0], skiprows=None, delimiter=',',
                                   header=None)  # TODO skiprows = 0 == > if there is a
        if self.sender().text() == "Browse Transform 1":
            self.transformMatrix1 = transform_df.to_numpy()
            print("Transform Matrix 1", self.transformMatrix1)
        elif self.sender().text() == "Browse Transform 2":
            self.transformMatrix2 = transform_df.to_numpy()
            print("Transform Matrix 2", self.transformMatrix2)
            self.transformMatrix1 = np.matmul(self.transformMatrix2, self.transformMatrix1)  # TODO not very clean!

    def performPCA(self):
        from sklearn.decomposition import PCA

        pca = PCA(n_components=3)
        pca.fit(self.source_detections)  # TODO
        V = pca.components_
        source_variance = pca.explained_variance_
        print("Initial source std dev {}".format(np.sqrt(source_variance)))
        sourceCOM = getCOM(self.source_detections)
        sourceDetections = self.source_detections - sourceCOM  # N x 3
        sourceDetections = np.matmul(np.linalg.inv(V), sourceDetections.transpose())  # 3 x N

        pca.fit(self.target_detections)  # TODO
        V = pca.components_
        target_variance = pca.explained_variance_
        print("Initial target st. dev {}".format(np.sqrt(target_variance)))
        targetCOM = getCOM(self.target_detections)
        targetDetections = self.target_detections - targetCOM  # N x 3
        targetDetections = np.matmul(np.linalg.inv(V), targetDetections.transpose())  # 3 x N

        sourceDetections[:, 0] = sourceDetections[:, 0] / np.sqrt(source_variance[0]) * np.sqrt(target_variance[0])
        sourceDetections[:, 1] = sourceDetections[:, 1] / np.sqrt(source_variance[1]) * np.sqrt(target_variance[1])
        sourceDetections[:, 2] = sourceDetections[:, 2] / np.sqrt(source_variance[2]) * np.sqrt(target_variance[2])

        sourceDetections = sourceDetections.transpose()  # N x 3
        targetDetections = targetDetections.transpose()  # N x 3

        return sourceDetections, targetDetections

    def performSC(self):
        ##### NEW STUFF
        sourceDetections, targetDetections = self.performPCA()
        #####

        sourceMeanDist = getMeanDistance(sourceDetections)
        sourceMeanDist = 1.0  # TODO
        sourceCOM = getCOM(sourceDetections)
        print("Source COM is {}".format(sourceCOM))
        targetMeanDist = getMeanDistance(targetDetections)
        targetMeanDist = 1.0  # TODO
        targetCOM = getCOM(targetDetections)
        print("Target COM is {}".format(targetCOM))
        print("Preparing unary features")
        unary11, unary12 = self.getUnary(sourceCOM, sourceMeanDist, sourceDetections)
        unary21, unary22 = self.getUnary(targetCOM, targetMeanDist, targetDetections)
        print("Preparing cost matrices on unary features")
        U1 = np.zeros((sourceDetections.shape[0], targetDetections.shape[0]))
        U2 = np.zeros((sourceDetections.shape[0], targetDetections.shape[0]))

        for i in range(U1.shape[0]):
            for j in range(U1.shape[1]):
                unaryi = unary11[i]  # TODO
                unaryj = unary21[j]  # TODO
                U1[i, j] = getUnaryDistance(unaryi, unaryj)

        for i in range(U2.shape[0]):
            for j in range(U2.shape[1]):
                unaryi = unary11[i]  # TODO
                unaryj = unary22[j]  # TODO
                U2[i, j] = getUnaryDistance(unaryi, unaryj)
        print("Beginning RANSAC")
        row_ind1, col_ind1 = linear_sum_assignment(U1)  # note that scipy does minimization
        Abest1, inliersBest1 = doRANSAC(self.source_detections[row_ind1, :], self.target_detections[col_ind1, :],
                                        min_samples=int(self.ransacSamplesLine.text()),
                                        trials=int(self.ransacIterationsLine.text()),
                                        error=float(self.ransacThresholdLine.text()))  # 20000, 25 work nicely
        row_ind2, col_ind2 = linear_sum_assignment(U2)  # note that scipy does minimization
        Abest2, inliersBest2 = doRANSAC(self.source_detections[row_ind2, :], self.target_detections[col_ind2, :],
                                        min_samples=int(self.ransacSamplesLine.text()),
                                        trials=int(self.ransacIterationsLine.text()),
                                        error=float(self.ransacThresholdLine.text()))  # 20000, 25 work nicely
        print("RANSAC done with inliers 1 {} and inliers 2 {}".format(str(inliersBest1), str(inliersBest2)))

        if (inliersBest1 > inliersBest2):
            self.transformMatrix1 = Abest1
        else:
            self.transformMatrix1 = Abest2

    def performSC_vanilla(self):
        sourceMeanDist = getMeanDistance(self.source_detections)
        sourceCOM = getCOM(self.source_detections)
        targetMeanDist = getMeanDistance(self.target_detections)
        targetCOM = getCOM(self.target_detections)
        print("Preparing unary features")
        unary11, unary12 = self.getUnary(sourceCOM, sourceMeanDist, self.source_detections)
        unary21, unary22 = self.getUnary(targetCOM, targetMeanDist, self.target_detections)
        print("Preparing cost matrices on unary features")
        U1 = np.zeros((self.source_detections.shape[0], self.target_detections.shape[0]))
        U2 = np.zeros((self.source_detections.shape[0], self.target_detections.shape[0]))

        for i in range(U1.shape[0]):
            for j in range(U1.shape[1]):
                unaryi = unary11[i]  # TODO
                unaryj = unary21[j]  # TODO
                U1[i, j] = getUnaryDistance(unaryi, unaryj)

        for i in range(U2.shape[0]):
            for j in range(U2.shape[1]):
                unaryi = unary11[i]  # TODO
                unaryj = unary22[j]  # TODO
                U2[i, j] = getUnaryDistance(unaryi, unaryj)
        print("Beginning RANSAC")
        row_ind1, col_ind1 = linear_sum_assignment(U1)  # note that scipy does minimization
        Abest1, inliersBest1 = doRANSAC(self.source_detections[row_ind1, :], self.target_detections[col_ind1, :],
                                        min_samples=int(self.ransacSamplesLine.text()),
                                        trials=int(self.ransacIterationsLine.text()),
                                        error=float(self.ransacThresholdLine.text()))  # 20000, 25 work nicely
        row_ind2, col_ind2 = linear_sum_assignment(U2)  # note that scipy does minimization
        Abest2, inliersBest2 = doRANSAC(self.source_detections[row_ind2, :], self.target_detections[col_ind2, :],
                                        min_samples=int(self.ransacSamplesLine.text()),
                                        trials=int(self.ransacIterationsLine.text()),
                                        error=float(self.ransacThresholdLine.text()))  # 20000, 25 work nicely
        print("RANSAC done with inliers 1 {} and inliers 2 {}".format(str(inliersBest1), str(inliersBest2)))

        if (inliersBest1 > inliersBest2):
            self.transformMatrix1 = Abest1
        else:
            self.transformMatrix1 = Abest2

    def getUnary(self, com, meanDist, detections):
        sc = []
        sc2 = []
        pca = PCA(n_components=3)
        pca.fit(detections)
        V = pca.components_
        x0_vector = V[0]  # TODO: take the first vector?
        x0_vector2 = -1 * x0_vector  # TODO
        for queriedIndex, point in enumerate(detections):
            neighbors = np.delete(detections, queriedIndex, 0)
            z_vector = (point - com) / np.linalg.norm(point - com)
            x_vector = x0_vector - z_vector * np.dot(x0_vector, z_vector)  # TODO
            x_vector = x_vector / np.linalg.norm(x_vector)
            x_vector2 = x0_vector2 - z_vector * np.dot(x0_vector2, z_vector)  # TODO
            x_vector2 = x_vector2 / np.linalg.norm(x_vector2)
            y_vector = getY(z_vector, x_vector)
            y_vector2 = getY(z_vector, x_vector2)
            points_transformed = transform(point, x_vector, y_vector, z_vector, neighbors)
            points_transformed2 = transform(point, x_vector2, y_vector2, z_vector, neighbors, verbose=False)  # TODO
            sc.append(getShapeContext(points_transformed, meanDist))  # numpy array
            sc2.append(getShapeContext(points_transformed2, meanDist, verbose=False))  # TODO
        return np.array(sc), np.array(sc2)

    def saveTransform(self):
        name = QFileDialog.getSaveFileName(self, 'Save File')  # this returns a tuple!
        print("Saving Transform Matrix at {} ******".format(name[0]))
        if (self.sender().text() == 'Save Transform - SC' or self.sender().text() == 'Save Transform'):
            self.to_csv(name[0], self.transformMatrix1)
        elif (self.sender().text() == 'Save Transform - ICP'):
            self.to_csv(name[0], self.transformMatrix2)
        elif (self.sender().text() == 'Save Transform - Combined'):
            self.to_csv(name[0], np.matmul(self.transformMatrix2, self.transformMatrix1))
            transformed_sourceLandmarks = getTransformedNuclei(self.source_landmarks,
                                                               np.matmul(self.transformMatrix2, self.transformMatrix1))
            residual = transformed_sourceLandmarks - self.target_landmarks
            ### MSE
            print("Mean Registration Error is {}".format(np.mean(np.linalg.norm(residual, axis=1))))
            print("Std Registration Error is {}".format(np.std(np.linalg.norm(residual, axis=1))))

            transformed_sourceDetections = getTransformedNuclei(self.source_detections,
                                                                np.matmul(self.transformMatrix2, self.transformMatrix1))

            from scipy.optimize import linear_sum_assignment
            from scipy.spatial import distance_matrix
            cost = distance_matrix(transformed_sourceDetections, self.target_detections)
            cost = np.load('/home/manan/Desktop/benefit-from-tracking/02-02/complete_cost_matrix.npy')
            #print("Distance Matrix shape is {}".format(cost.shape))
            #np.save("/home/manan/Desktop/benefit-from-tracking/02-02/distance", cost )
            row_ind, col_ind = linear_sum_assignment(cost)
            row_ids = self.source_ids[row_ind]
            col_ids = self.target_ids[col_ind]
            correct = 0
            for i, source_landmark_id in enumerate(self.source_landmarks_ids):
                row = np.where(row_ids == source_landmark_id)
                if(self.target_landmarks_ids[i] == col_ids[row]):
                    correct+=1

            print("Accuracy = {}".format(correct/12))


            ### Accuracy


    def to_csv(self, filename, matrix):
        with open(filename, mode='w') as file:
            writer = csv.writer(file, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
            for idx, row in enumerate(matrix):
                writer.writerow(row)

    def performICP(self):
        sourceDetections_transformed = getTransformedNuclei(self.source_detections, self.transformMatrix1)
        self.transformMatrix2 = performICP(int(self.icpIterationsLine.text()), sourceDetections_transformed,
                                           self.target_detections)

    def modeComboboxChanged(self):
        if self.modeComboBox.currentIndex() == 0:
            self.rBinsLabel.hide()
            self.rBinsLine.hide()
            self.thetaBinsLabel.hide()
            self.thetaBinsLine.hide()
            self.phiBinsLabel.hide()
            self.phiBinsLine.hide()
            self.ransacIterationsLabel.hide()
            self.ransacIterationsLine.hide()
            self.ransacSamplesLabel.hide()
            self.ransacSamplesLine.hide()
            self.ransacThresholdLabel.hide()
            self.ransacThresholdLine.hide()
            self.browseTransformButton1.hide()
            self.browseTransformButton2.hide()
            self.saveTransformSCButton.setText('Save Transform')

        elif self.modeComboBox.currentIndex() == 1:
            self.rBinsLabel.hide()
            self.rBinsLine.hide()
            self.thetaBinsLabel.hide()
            self.thetaBinsLine.hide()
            self.phiBinsLabel.hide()
            self.phiBinsLine.hide()
            self.ransacIterationsLabel.hide()
            self.ransacIterationsLine.hide()
            self.ransacSamplesLabel.hide()
            self.ransacSamplesLine.hide()
            self.ransacThresholdLabel.hide()
            self.ransacThresholdLine.hide()
            self.runSCButton.hide()
            self.saveTransformSCButton.hide()
            self.browseTransformButton1.show()
            self.browseTransformButton2.show()

        elif self.modeComboBox.currentIndex() == 2:
            self.rBinsLabel.show()
            self.rBinsLine.show()
            self.thetaBinsLabel.show()
            self.thetaBinsLine.show()
            self.phiBinsLabel.show()
            self.phiBinsLine.show()
            self.ransacIterationsLabel.show()
            self.ransacIterationsLine.show()
            self.ransacSamplesLabel.show()
            self.ransacSamplesLine.show()
            self.ransacThresholdLabel.show()
            self.ransacThresholdLine.show()
            self.browseTransformButton1.hide()
            self.browseTransformButton2.hide()
            self.saveTransformSCButton.setText('Save Transform - SC')


class MplCanvas(FigureCanvas):

    def __init__(self, parent=None, width=5, height=4, dpi=100):
        fig = Figure(figsize=(width, height), dpi=dpi)
        fig.set_facecolor("#262930")  # charcoal gray
        self.axes = fig.add_subplot(111)
        self.axes.set_aspect(aspect=1.0)  # TODO if you take this away, then the docked widget can be rescaled flexibly
        self.axes.set_facecolor("#414851")  # light gray
        super(MplCanvas, self).__init__(fig)


class FreeFormDeformation(QWidget):

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

        self.setSizePolicy(
            QtWidgets.QSizePolicy.MinimumExpanding,
            QtWidgets.QSizePolicy.MinimumExpanding
        )
        layout = QtWidgets.QGridLayout()
        self.openImageButton = QPushButton('Open Image', self)
        self.openImageButton.clicked.connect(self.openImages)

        self.processImageLabel = QLabel(self)
        self.processImageLabel.setText('Process Image:')
        self.imagesComboBox = QComboBox(self)

        self.browseTransformButton = QPushButton(self)
        self.browseTransformButton.setText('Browse Transform')
        self.browseTransformButton.clicked.connect(self.loadTransform)

        self.targetImageXSizeLabel = QLabel(self)
        self.targetImageXSizeLabel.setText('Target Image (X Size)')

        self.targetImageXSizeEdit = QLineEdit(self)
        self.targetImageXSizeEdit.setText('700')
        self.targetImageXSizeEdit.setAlignment(Qt.AlignCenter)
        self.targetImageYSizeLabel = QLabel(self)
        self.targetImageYSizeLabel.setText('Target Image (Y Size)')
        self.targetImageYSizeEdit = QLineEdit(self)
        self.targetImageYSizeEdit.setText('660')
        self.targetImageYSizeEdit.setAlignment(Qt.AlignCenter)

        self.targetImageZSizeLabel = QLabel(self)
        self.targetImageZSizeLabel.setText('Target Image (Z Size)')
        self.targetImageZSizeEdit = QLineEdit(self)
        self.targetImageZSizeEdit.setText('565')
        self.targetImageZSizeEdit.setAlignment(Qt.AlignCenter)

        self.targetImageZPixelLabel = QLabel(self)
        self.targetImageZPixelLabel.setText('Z Pixel')
        self.targetImageZPixelEdit = QLineEdit(self)
        self.targetImageZPixelEdit.setText('5')
        self.targetImageZPixelEdit.setAlignment(Qt.AlignCenter)

        self.generateImageButton = QPushButton(self)
        self.generateImageButton.setText('Generate Image')
        self.generateImageButton.clicked.connect(self.generateImage)
        self.saveImageButton = QPushButton(self)
        self.saveImageButton.setText('Save Image')
        self.saveImageButton.clicked.connect(self.saveImage)

        self.runFFDButton = QPushButton(self)
        self.runFFDButton.setText('Run FFD')
        self.runFFDButton.clicked.connect(self.runFFD)

        self.canvas = MplCanvas(self, width=5, height=5, dpi=100)
        self.current_iteration_number = -1

        n_data = 50
        self.xdata = list(range(n_data))
        self.ydata = [np.random.randint(0, 10) for i in range(n_data)]
        self.update_plot()
        self.timer = QtCore.QTimer()
        self.timer.single_shot = True
        self.timer.setInterval(1e8)
        self.timer.timeout.connect(self.update_plot)
        self.timer.start()

        layout.addWidget(self.openImageButton, 0, 0)
        layout.addWidget(self.processImageLabel, 1, 0)
        layout.addWidget(self.imagesComboBox, 1, 1)
        layout.addWidget(self.browseTransformButton, 2, 0, 1, 2)
        layout.addWidget(self.targetImageXSizeLabel, 3, 0)
        layout.addWidget(self.targetImageXSizeEdit, 3, 1)
        layout.addWidget(self.targetImageYSizeLabel, 4, 0)
        layout.addWidget(self.targetImageYSizeEdit, 4, 1)

        layout.addWidget(self.targetImageZSizeLabel, 5, 0)
        layout.addWidget(self.targetImageZSizeEdit, 5, 1)
        layout.addWidget(self.targetImageZPixelLabel, 6, 0)
        layout.addWidget(self.targetImageZPixelEdit, 6, 1)

        layout.addWidget(self.generateImageButton, 7, 0)
        layout.addWidget(self.saveImageButton, 7, 1)
        layout.addWidget(self.runFFDButton, 8, 0, 1, 2)
        layout.addWidget(self.canvas, 9, 0, 1, 2)
        self.setLayout(layout)

    def resample(self, image, transform, factor=1.0):
        print("source image size", image.GetSize())
        reference_image = np.zeros((int(self.targetImageZSizeEdit.text()), int(self.targetImageYSizeEdit.text()),
                                    int(self.targetImageXSizeEdit.text())))  # ZYX
        # reference_image = np.zeros(
        #    (int(factor * image.GetSize()[2]), int(factor * image.GetSize()[1]), int(factor * image.GetSize()[0])), dtype = np.uint8)
        reference_image = sitk.GetImageFromArray(reference_image)
        interpolator = sitk.sitkBSpline
        default_value = 0.0  # TODO
        return sitk.Resample(image, reference_image, transform,
                             interpolator, default_value)

    def generateImage(self):
        self.sourceImage = viewer.layers[self.imagesComboBox.currentIndex()].data
        sourceImage = sitk.GetImageFromArray(self.sourceImage)
        defaultAffineTransform = sitk.AffineTransform(3)
        new_transform = sitk.AffineTransform(defaultAffineTransform)  # initialization
        matrix = np.array(defaultAffineTransform.GetMatrix()).reshape((3, 3))
        # *********************
        transformMatrix1_xyz = np.copy(self.transformMatrix1)
        transformMatrix1_xyz[0:3, :] = np.flip(self.transformMatrix1[0:3, :], 0)
        transformMatrix1_xyz[:, 0:3] = np.flip(transformMatrix1_xyz[:, 0:3],
                                               1)  # TODO: make sure not to include the transaltion column at the extreme right
        transformMatrix_temp = np.linalg.inv(transformMatrix1_xyz)
        # *********************

        new_matrix = np.dot(transformMatrix_temp[:3, :3],
                            matrix)  # TODO flipped because so far the matrix generated was ZYX
        new_transform.SetMatrix(new_matrix.ravel())
        new_transform.SetTranslation(
            (transformMatrix_temp[0, 3], transformMatrix_temp[1, 3], transformMatrix_temp[2, 3]))
        print("Resampling Begins !!!")
        self.sourceImage_transformed = self.resample(sourceImage, new_transform)
        print("Resampling Finished !!!")
        # TODO
        moving_image = sitk.GetArrayFromImage(self.sourceImage_transformed)[::int(self.targetImageZPixelEdit.text()),
                       ...]  # TODO very unclean!
        tifffile.imsave('/home/manan/Desktop/source-transformed.tif', moving_image)
        self.meanImage, self.stdImage, moving_image = self.normalize(moving_image)
        self.sourceImage_transformed = sitk.GetImageFromArray(moving_image)

        # TODO
        viewer.add_image(sitk.GetArrayFromImage(self.sourceImage_transformed), name='source-transformed')

    def normalize(self, image):
        meanImage = np.mean(image)
        stdImage = np.std(image)
        return meanImage, stdImage, (image - np.mean(image)) / stdImage

    def openImages(self):
        name = QFileDialog.getOpenFileName(self, 'Open File')  # this returns a tuple!
        print("Opening image {} ******".format(name[0]))
        viewer.add_image(tifffile.imread(name[0]).astype('float32'), name=os.path.basename(name[0]))
        self.name = os.path.basename(name[0])
        self.imagesComboBox.clear()
        for layer in viewer.layers:
            if (layer.data.ndim == 3):  # image
                self.imagesComboBox.addItem(layer.name)  # add the actual content of self.comboData

    def saveImage(self):
        name = QFileDialog.getOpenFileName(self, 'Save File')  # this returns a tuple!
        print("Saving image {} ******".format(name[0]))
        tifffile.imsave(name[0], viewer.layers[self.imagesComboBox.currentIndex()].data)

    def loadTransform(self):
        name = QFileDialog.getOpenFileName(self, 'Open Transform File')  # this returns a tuple!
        print("Opening transform file {} ******".format(name[0]))
        transform_df = pd.read_csv(name[0], skiprows=None, delimiter=',',
                                   header=None)  # TODO skiprows = 0 == > if there is a
        self.transformMatrix1 = transform_df.to_numpy()
        print(self.transformMatrix1)

    def runFFD(self):
        targetImage = viewer.layers[1].data
        targetImage = sitk.GetImageFromArray(self.normalize(targetImage)[2])  # TODO

        print('transformation begins')

        #############
        detections_df = pd.read_csv(
            '/home/manan/ownCloud/BIC_ECCV_Data/01_Insitus/03_Landmarks/04/Pdu_Pax6MHT_16hpf_pNA_PB_20180504-9_12annotation',
            skiprows=None, delimiter=' ',
            header=None)  # TODO skiprows = 0 == > if there is a
        detections_numpy = detections_df.to_numpy()

        fixed_points = np.flip(detections_numpy[:, 1:4], 1)
        fixed_points = list(zip(fixed_points[:, 0], fixed_points[:, 1], fixed_points[:, 2]))
        ##############

        ##############
        detections_df = pd.read_csv(
            '/home/manan/ownCloud/BIC_ECCV_Data/01_Insitus/03_Landmarks/02/Pdu_Pax6MHT_16hpf_pNA_PB_20180504-3_reflected_12annotation',
            skiprows=None, delimiter=' ', header=None)  # TODO skiprows = 0 == > if there is a
        detections_numpy = detections_df.to_numpy()

        moving_points = np.flip(detections_numpy[:, 1:4], 1)  # zyx

        ##############

        moving_points = getTransformedNuclei(moving_points, self.transformMatrix1)
        print("transformed moving detections", moving_points.shape)
        moving_points = list(zip(moving_points[:, 0], moving_points[:, 1], moving_points[:, 2]))

        tx = self.bspline_intra_modal_registration(fixed_image=targetImage,  # target --> fixed
                                                   moving_image=self.sourceImage_transformed,
                                                   # source transformed --> moving!
                                                   fixed_image_mask=None,
                                                   fixed_points=fixed_points,
                                                   moving_points=moving_points
                                                   )

        initial_errors_mean, initial_errors_std, _, initial_errors_max, initial_errors = ru.registration_errors(
            sitk.Euler3DTransform(), fixed_points, moving_points)
        final_errors_mean, final_errors_std, _, final_errors_max, final_errors = ru.registration_errors(tx,
                                                                                                        fixed_points,
                                                                                                        moving_points)
        print('resampling begins!')
        moving_resampled_ffd = sitk.Resample(self.sourceImage_transformed, targetImage, tx, sitk.sitkLinear, 0.0,
                                             self.sourceImage_transformed.GetPixelID())
        print('resampling done!')
        tifffile.imsave('/home/manan/Desktop/moving_resampled_ffd.tif',
                        sitk.GetArrayFromImage(moving_resampled_ffd) * self.stdImage + self.meanImage)

    def bspline_intra_modal_registration(self, fixed_image, moving_image, fixed_image_mask=None, fixed_points=None,
                                         moving_points=None):

        registration_method = sitk.ImageRegistrationMethod()

        # Determine the number of BSpline control points using the physical spacing we want for the control grid.
        grid_physical_spacing = [50.0, 50.0, 10.0]  # A control point every 50mm #TODO [50, 50, 50]  for isotropic
        image_physical_size = [size * spacing for size, spacing in zip(fixed_image.GetSize(), fixed_image.GetSpacing())]
        mesh_size = [int(image_size / grid_spacing + 0.5) \
                     for image_size, grid_spacing in zip(image_physical_size, grid_physical_spacing)]

        initial_transform = sitk.BSplineTransformInitializer(image1=fixed_image,
                                                             transformDomainMeshSize=mesh_size, order=3)
        registration_method.SetInitialTransform(initial_transform)

        registration_method.SetMetricAsMeanSquares()
        # Settings for metric sampling, usage of a mask is optional. When given a mask the sample points will be
        # generated inside that region. Also, this implicitly speeds things up as the mask is smaller than the
        # whole image.
        registration_method.SetMetricSamplingStrategy(registration_method.RANDOM)
        registration_method.SetMetricSamplingPercentage(0.01)
        if fixed_image_mask:
            registration_method.SetMetricFixedMask(fixed_image_mask)

        # Multi-resolution framework.
        registration_method.SetShrinkFactorsPerLevel(shrinkFactors=[4, 2, 1])
        registration_method.SetSmoothingSigmasPerLevel(smoothingSigmas=[2, 1, 0])
        registration_method.SmoothingSigmasAreSpecifiedInPhysicalUnitsOn()

        registration_method.SetInterpolator(sitk.sitkLinear)
        registration_method.SetOptimizerAsLBFGSB(gradientConvergenceTolerance=1e-5, numberOfIterations=100)  # 100

        # If corresponding points in the fixed and moving image are given then we display the similarity metric
        # and the TRE during the registration.
        if fixed_points and moving_points:
            registration_method.AddCommand(sitk.sitkStartEvent, self.metric_and_reference_start_plot)
            registration_method.AddCommand(sitk.sitkEndEvent, self.metric_and_reference_end_plot)
            registration_method.AddCommand(sitk.sitkIterationEvent,
                                           lambda: self.metric_and_reference_plot_values(registration_method,
                                                                                         fixed_points, moving_points))

        return registration_method.Execute(fixed_image, moving_image)

    def metric_and_reference_start_plot(self):
        global metric_values, multires_iterations, reference_mean_values
        global reference_min_values, reference_max_values

        metric_values = []
        multires_iterations = []
        reference_mean_values = []
        reference_min_values = []
        reference_max_values = []
        self.current_iteration_number = -1

    # Callback we associate with the EndEvent, do cleanup of data and figure.
    def metric_and_reference_end_plot(self):
        global metric_values, multires_iterations, reference_mean_values
        global reference_min_values, reference_max_values

        del metric_values
        del multires_iterations
        del reference_mean_values
        del reference_min_values
        del reference_max_values

    def metric_and_reference_plot_values(self, registration_method, fixed_points, moving_points):
        global metric_values, multires_iterations, reference_mean_values
        global reference_min_values, reference_max_values

        # Some optimizers report an iteration event for function evaluations and not
        # a complete iteration, we only want to update every iteration.
        if registration_method.GetOptimizerIteration() == self.current_iteration_number:
            return

        self.current_iteration_number = registration_method.GetOptimizerIteration()
        metric_values.append(registration_method.GetMetricValue())
        # Compute and store TRE statistics (mean, min, max).
        current_transform = sitk.CompositeTransform(registration_method.GetInitialTransform())
        current_transform.SetParameters(registration_method.GetOptimizerPosition())
        current_transform.AddTransform(registration_method.GetMovingInitialTransform())
        current_transform.AddTransform(registration_method.GetFixedInitialTransform().GetInverse())
        mean_error, _, min_error, max_error, _ = rc.registration_errors(current_transform, fixed_points, moving_points)
        reference_mean_values.append(mean_error)
        reference_min_values.append(min_error)
        reference_max_values.append(max_error)
        print("iter", "mean error", self.current_iteration_number, mean_error)
        self.timer.timeout.emit()
        self.timer.start()

    def update_plot(self):
        # Drop off the first y element, append a new one.
        self.ydata = self.ydata[1:] + [np.random.randint(0, 10)]
        self.canvas.axes.cla()  # Clear the canvas.
        self.canvas.axes.plot(self.xdata, self.ydata, 'r')
        self.canvas.draw()


class PlatyMatch(QWidget):
    def __init__(self, *args, **kwargs):
        super(PlatyMatch, self).__init__(*args, **kwargs)
        self.layout = QtWidgets.QVBoxLayout()
        # initialization - main menu buttons
        self.detectNucleiButton = QPushButton('Detect Nuclei', self)
        self.coarseAlignmentButton = QPushButton('Coarse Alignment', self)
        self.freeFormDeformationButton = QPushButton('Non-Linear Registration', self)
        self.homeButton = QPushButton('Back to Main Menu', self)
        self.homeButton.hide()

        # initialization - separate modules
        self.detectNuclei = DetectNuclei()
        self.detectNuclei.hide()
        self.coarseAlignment = CoarseAlignment()
        self.coarseAlignment.hide()
        self.freeFormDeformation = FreeFormDeformation()
        self.freeFormDeformation.hide()

        # callbacks
        self.detectNucleiButton.clicked.connect(self.revealDetectNucleiPanel)
        self.coarseAlignmentButton.clicked.connect(self.revealCoarseAlignmentPanel)
        self.freeFormDeformationButton.clicked.connect(self.revealFreeFormDeformationPanel)
        self.homeButton.clicked.connect(self.revealMainMenuPanel)

        self.layout.addWidget(self.detectNucleiButton)
        self.layout.addWidget(self.coarseAlignmentButton)
        self.layout.addWidget(self.freeFormDeformationButton)
        self.layout.addWidget(self.homeButton)
        self.layout.addWidget(self.detectNuclei)
        self.layout.addWidget(self.coarseAlignment)
        self.layout.addWidget(self.freeFormDeformation)
        # self.detectNuclei = DetectNuclei()
        # layout.addWidget(self.detectNuclei)
        #
        # self.matchNuclei = MatchNuclei()
        # layout.addWidget(self.matchNuclei)
        #
        # self.freeFormDeformation = FreeFormDeformation()
        # layout.addWidget(self.freeFormDeformation)

        self.setLayout(self.layout)

    def revealDetectNucleiPanel(self):
        self.detectNucleiButton.hide()
        self.coarseAlignmentButton.hide()
        self.freeFormDeformationButton.hide()
        self.homeButton.show()
        self.detectNuclei.show()
        self.freeFormDeformation.hide()
        self.coarseAlignment.hide()

    def revealCoarseAlignmentPanel(self):
        self.detectNucleiButton.hide()
        self.coarseAlignmentButton.hide()
        self.freeFormDeformationButton.hide()
        self.homeButton.show()
        self.coarseAlignment.show()
        self.detectNuclei.hide()
        self.freeFormDeformation.hide()

    def revealFreeFormDeformationPanel(self):
        self.detectNuclei.hide()
        self.coarseAlignment.hide()
        self.freeFormDeformation.show()
        self.homeButton.show()
        self.detectNucleiButton.hide()
        self.coarseAlignmentButton.hide()
        self.freeFormDeformationButton.hide()

    def revealMainMenuPanel(self):
        self.detectNuclei.hide()
        self.coarseAlignment.hide()
        self.freeFormDeformation.hide()
        self.homeButton.hide()
        self.detectNucleiButton.show()
        self.coarseAlignmentButton.show()
        self.freeFormDeformationButton.show()


with napari.gui_qt():
    # create a new viewer with a couple image layers
    viewer = napari.Viewer()

    # here's the magicgui!  We also use the additional `call_button` option
    # @magicgui(call_button="execute")
    # def image_arithmetic(layerA: Image, operation: Operation, layerB: Image) -> Image:
    #    """Adds, subtracts, multiplies, or divides two image layers of similar shape."""
    #    return operation.value(layerA.data, layerB.data)

    # instantiate the widget
    # gui = image_arithmetic.Gui()
    # add our new widget to the napari viewer

    app = QtWidgets.QApplication(sys.argv)
    mainWin = PlatyMatch()

    viewer.window.add_dock_widget(mainWin, area='right')
    # viewer.window.add_dock_widget(gui)
    # keep the dropdown menus in the gui in sync with the layer model
    # viewer.layers.events.changed.connect(lambda x: app.refresh_choices())
