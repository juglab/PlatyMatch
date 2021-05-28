import SimpleITK as sitk
import csv
import numpy as np
import tifffile
from PyQt5.QtCore import Qt
from napari_plugin_engine import napari_hook_implementation
from platymatch.detect_nuclei.ss_log import find_spheres
from platymatch.estimate_transform.apply_transform import apply_affine_transform
from platymatch.estimate_transform.find_transform import get_affine_transform, get_similar_transform
from platymatch.estimate_transform.perform_icp import perform_icp
from platymatch.estimate_transform.shape_context import get_unary, get_unary_distance, do_ransac
from platymatch.utils.utils import _visualize_nuclei, _browse_detections, _browse_transform, get_centroid, \
    get_mean_distance
from qtpy.QtWidgets import QWidget, QGridLayout, QVBoxLayout, QPushButton, QCheckBox, QLabel, QComboBox, QLineEdit, \
    QFileDialog, QProgressBar
from scipy.optimize import linear_sum_assignment
from scipy.spatial.distance import cdist
from tqdm import tqdm


class DetectNuclei(QWidget):
    def __init__(self, napari_viewer):
        super().__init__()
        self.viewer = napari_viewer

        # define components
        self.sync_button = QPushButton('Sync with Viewer')
        self.sync_button.clicked.connect(self._refresh)
        self.process_image_label = QLabel('Process Image')
        self.images_combo_box = QComboBox(self)

        self.min_sigma_label = QLabel('Min Sigma:')
        self.min_sigma_text = QLineEdit('5')
        self.min_sigma_text.setAlignment(Qt.AlignCenter)

        self.step_sigma_label = QLabel('Scale Sigma:')
        self.step_sigma_text = QLineEdit('1')
        self.step_sigma_text.setAlignment(Qt.AlignCenter)

        self.max_sigma_label = QLabel('Max Sigma:')
        self.max_sigma_text = QLineEdit('9')
        self.max_sigma_text.setAlignment(Qt.AlignCenter)

        self.anisotropy_label = QLabel('Anisotropy (Z):')
        self.anisotropy_text = QLineEdit('1.0')
        self.anisotropy_text.setAlignment(Qt.AlignCenter)

        self.run_button = QPushButton('Run Scale Space Log')
        self.run_button.clicked.connect(self._click_run)

        self.export_detections_button = QPushButton('Export Detections to csv')
        self.export_detections_button.clicked.connect(self._export_detections)

        self.export_instance_mask_button = QPushButton('Export Instance Mask')
        self.export_instance_mask_button.clicked.connect(self._export_instance_mask)

        for layer in self.viewer.layers:
            self.images_combo_box.addItem(layer.name)

        layout = QVBoxLayout()
        grid = QGridLayout()
        grid.addWidget(self.sync_button, 0, 0)
        grid.addWidget(self.process_image_label, 1, 0)
        grid.addWidget(self.images_combo_box, 1, 1)
        grid.addWidget(self.min_sigma_label, 2, 0)
        grid.addWidget(self.min_sigma_text, 2, 1)
        grid.addWidget(self.max_sigma_label, 3, 0)
        grid.addWidget(self.max_sigma_text, 3, 1)
        grid.addWidget(self.step_sigma_label, 4, 0)
        grid.addWidget(self.step_sigma_text, 4, 1)
        grid.addWidget(self.anisotropy_label, 5, 0)
        grid.addWidget(self.anisotropy_text, 5, 1)
        grid.addWidget(self.run_button, 6, 0)
        grid.setSpacing(10)
        layout.addLayout(grid)

        grid_2 = QGridLayout()
        grid_2.addWidget(self.export_detections_button, 1, 0)
        grid_2.addWidget(self.export_instance_mask_button, 1, 1)
        grid_2.setSpacing(10)
        layout.addLayout(grid_2)
        self.setLayout(layout)

    def _refresh(self):
        self.images_combo_box.clear()
        for layer in self.viewer.layers:
            self.images_combo_box.addItem(layer.name)

    def _click_run(self):
        print("=" * 25)
        print("Beginning Nuclei Detection for scales from {} to {}".format(self.min_sigma_text.text(),
                                                                           self.max_sigma_text.text()))
        image = self.viewer.layers[self.images_combo_box.currentIndex()].data
        peaks_otsu, peaks_subset, log, peaks_local_minima, threshold = find_spheres(image, scales=range(
            int(self.min_sigma_text.text()), int(self.max_sigma_text.text()), int(self.step_sigma_text.text())),
                                                                                    anisotropy_factor=float(
                                                                                        self.anisotropy_text.text()))
        _visualize_nuclei(self, peaks_subset)
        print("=" * 25)
        print("Nuclei detection is complete. Please export locations of these nuclei to a csv file or export an instance mask")
        
    def _export_detections(self):
        save_file_name = QFileDialog.getSaveFileName(self, 'Save File')  # this returns a tuple!
        print("Saving Detections at {}".format(save_file_name[0]))
        for layer in self.viewer.layers:
            if layer.name == 'points-' + self.viewer.layers[self.images_combo_box.currentIndex()].name:
                nuclei = layer.data
                properties = layer.properties

        with open(save_file_name[0], mode='w') as file:
            writer = csv.writer(file, delimiter=' ', quotechar='"', quoting=csv.QUOTE_MINIMAL)
            n_dimensions = nuclei.shape[1]
            if n_dimensions == 3:
                dimensions_list = ['z', 'y', 'x']
            header = ['id'] + ['dimension_' + dimension for dimension in dimensions_list] + ['radius']
            writer.writerow(header)
            for idx, row in enumerate(nuclei):
                row_ = row.copy()
                row_[0] = float(self.anisotropy_text.text()) * row_[0]  # correct z dimension !

                point_data = np.concatenate([np.array([int(idx) + 1]), row_, np.array([properties['radius'][idx]])])
                writer.writerow(point_data)

    def _export_instance_mask(self):
        save_file_name = QFileDialog.getSaveFileName(self, 'Save File')  # this returns a tuple!
        print("Saving Detections at {}".format(save_file_name[0]))
        for layer in self.viewer.layers:
            if layer.name == 'points-' + self.viewer.layers[self.images_combo_box.currentIndex()].name:
                nuclei = layer.data
                properties = layer.properties

        image = self.viewer.layers[self.images_combo_box.currentIndex()].data
        mask = np.zeros(image.shape, dtype=np.uint16)

        for idx, row in enumerate(tqdm(nuclei)):
            zmin = int(np.round(row[0] - properties['radius'][idx] / float(self.anisotropy_text.text())))
            zmax = int(np.round(row[0] + properties['radius'][idx] / float(self.anisotropy_text.text())))
            ymin = int(np.round(row[1] - properties['radius'][idx]))
            ymax = int(np.round(row[1] + properties['radius'][idx]))
            xmin = int(np.round(row[2] - properties['radius'][idx]))
            xmax = int(np.round(row[2] + properties['radius'][idx]))
            for z in range(zmin, zmax + 1):
                for y in range(ymin, ymax + 1):
                    for x in range(xmin, xmax + 1):
                        if (np.linalg.norm(
                                [float(self.anisotropy_text.text()) * (z - row[0]), y - row[1], x - row[2]]) <=
                                properties['radius'][idx]):
                            mask[z, y, x] = idx + 1  # start indexing from 1 since b.g is 0
        tifffile.imsave(save_file_name[0], mask.astype(np.uint16))


class EstimateTransform(QWidget):
    def __init__(self, napari_viewer):
        super().__init__()
        self.viewer = napari_viewer

        # define components
        self.sync_button = QPushButton('Sync with Viewer')
        self.sync_button.clicked.connect(self._refresh)
        self.csv_checkbox = QCheckBox('csv?')
        self.izyx_checkbox = QCheckBox('IZYXR?')
        self.izyx_checkbox.hide()
        self.header_checkbox = QCheckBox('Header?')
        self.header_checkbox.hide()
        self.csv_checkbox.clicked.connect(self._open_text_file)

        self.moving_image_combobox = QComboBox(self)
        self.fixed_image_combobox = QComboBox(self)

        self.moving_detections_label = QLabel('Moving Detections:')
        self.moving_detections_pushbutton = QPushButton('Load')
        self.moving_detections_pushbutton.hide()
        self.moving_detections_pushbutton.clicked.connect(self._browse)

        self.fixed_detections_label = QLabel('Fixed Detections:')
        self.fixed_detections_pushbutton = QPushButton('Load')
        self.fixed_detections_pushbutton.hide()
        self.fixed_detections_pushbutton.clicked.connect(self._browse)

        self.moving_progress_bar = QProgressBar(self)
        self.fixed_progress_bar = QProgressBar(self)

        self.moving_image_anisotropy_label = QLabel('Moving Image Anisotropy [Z]:')
        self.moving_image_anisotropy_line = QLineEdit('1.0')
        self.moving_image_anisotropy_line.setAlignment(Qt.AlignCenter)

        self.fixed_image_anisotropy_label = QLabel('Fixed Image Anisotropy [Z]:')
        self.fixed_image_anisotropy_line = QLineEdit('1.0')
        self.fixed_image_anisotropy_line.setAlignment(Qt.AlignCenter)

        self.transform_label = QLabel('Type of Transform:')
        self.transform_combobox = QComboBox(self)  # affine, similar
        self.transform_combobox.addItems(['Affine', 'Similar'])
        self.transform_combobox.setEditable(True)
        self.transform_combobox.lineEdit().setAlignment(Qt.AlignCenter)
        self.transform_combobox.lineEdit().setReadOnly(True)

        self.estimate_transform_label = QLabel('Estimate Transform:')
        self.estimate_transform_combo_box = QComboBox(self)  # unsupervised, supervised
        self.estimate_transform_combo_box.addItems(['Unsupervised', 'Supervised'])
        self.estimate_transform_combo_box.currentIndexChanged.connect(self._estimate_transform_changed)
        self.estimate_transform_combo_box.setEditable(True)
        self.estimate_transform_combo_box.lineEdit().setAlignment(Qt.AlignCenter)
        self.estimate_transform_combo_box.lineEdit().setReadOnly(True)

        self.moving_keypoints_label = QLabel('Moving keypoints:')
        self.moving_keypoints_pushbutton = QPushButton('Load')
        self.moving_keypoints_label.hide()
        self.moving_keypoints_pushbutton.clicked.connect(self._browse)
        self.moving_keypoints_pushbutton.hide()

        self.fixed_keypoints_label = QLabel('Fixed keypoints:')
        self.fixed_keypoints_pushbutton = QPushButton('Load')
        self.fixed_keypoints_label.hide()
        self.fixed_keypoints_pushbutton.clicked.connect(self._browse)
        self.fixed_keypoints_pushbutton.hide()

        self.r_bins_label = QLabel('Number of r bins:')
        self.r_bins_lineedit = QLineEdit('5')
        self.r_bins_lineedit.setAlignment(Qt.AlignCenter)

        self.theta_bins_label = QLabel('Number of \u03B8 bins:')
        self.theta_bins_lineedit = QLineEdit('6')
        self.theta_bins_lineedit.setAlignment(Qt.AlignCenter)

        self.phi_bins_label = QLabel('Number of \u03C6 bins:')
        self.phi_bins_lineedit = QLineEdit('12')
        self.phi_bins_lineedit.setAlignment(Qt.AlignCenter)

        self.ransac_iterations_label = QLabel('Number of RANSAC iterations:')
        self.ransac_iterations_lineedit = QLineEdit('4000')
        self.ransac_iterations_lineedit.setAlignment(Qt.AlignCenter)

        self.ransac_samples_label = QLabel('Number of RANSAC samples:')
        self.ransac_samples_lineedit = QLineEdit('4')
        self.ransac_samples_lineedit.setAlignment(Qt.AlignCenter)

        self.icp_iterations_label = QLabel('Number of ICP iterations:')
        self.icp_iterations_lineedit = QLineEdit('50')
        self.icp_iterations_lineedit.setAlignment(Qt.AlignCenter)

        self.run_button = QPushButton('Run')
        self.run_button.clicked.connect(self._click_run)
        self.export_transform_button = QPushButton('Export Transform')
        self.export_transform_button.clicked.connect(self._save_transform)

        for layer in self.viewer.layers:
            self.moving_image_combobox.addItem(layer.name)

        for layer in self.viewer.layers:
            self.fixed_image_combobox.addItem(layer.name)

        # define layouut
        layout = QVBoxLayout()
        grid_1 = QGridLayout()
        grid_1.addWidget(self.sync_button, 0, 0)
        grid_1.addWidget(self.csv_checkbox, 1, 0)
        grid_1.addWidget(self.izyx_checkbox, 1, 1)
        grid_1.addWidget(self.header_checkbox, 1, 2)

        grid_1.addWidget(self.moving_detections_label, 2, 0)
        grid_1.addWidget(self.moving_detections_pushbutton, 2, 1)
        grid_1.addWidget(self.moving_image_combobox, 2, 1)
        grid_1.addWidget(self.moving_image_anisotropy_label, 3, 0)
        grid_1.addWidget(self.moving_image_anisotropy_line, 3, 1)
        grid_1.addWidget(self.moving_progress_bar, 4, 0, 1, 2)

        grid_1.addWidget(self.fixed_detections_label, 5, 0)
        grid_1.addWidget(self.fixed_detections_pushbutton, 5, 1)
        grid_1.addWidget(self.fixed_image_combobox, 5, 1)
        grid_1.addWidget(self.fixed_image_anisotropy_label, 6, 0)
        grid_1.addWidget(self.fixed_image_anisotropy_line, 6, 1)
        grid_1.addWidget(self.fixed_progress_bar, 7, 0, 1, 2)

        grid_1.addWidget(self.transform_label, 8, 0)
        grid_1.addWidget(self.transform_combobox, 8, 1)
        grid_1.addWidget(self.estimate_transform_label, 9, 0)
        grid_1.addWidget(self.estimate_transform_combo_box, 9, 1)
        grid_1.addWidget(self.moving_keypoints_label, 10, 0)
        grid_1.addWidget(self.moving_keypoints_pushbutton, 10, 1)
        grid_1.addWidget(self.fixed_keypoints_label, 11, 0)
        grid_1.addWidget(self.fixed_keypoints_pushbutton, 11, 1)
        grid_1.addWidget(self.r_bins_label, 10, 0)
        grid_1.addWidget(self.r_bins_lineedit, 10, 1)
        grid_1.addWidget(self.theta_bins_label, 11, 0)
        grid_1.addWidget(self.theta_bins_lineedit, 11, 1)
        grid_1.addWidget(self.phi_bins_label, 12, 0)
        grid_1.addWidget(self.phi_bins_lineedit, 12, 1)
        grid_1.addWidget(self.ransac_samples_label, 13, 0)
        grid_1.addWidget(self.ransac_samples_lineedit, 13, 1)
        grid_1.addWidget(self.ransac_iterations_label, 14, 0)
        grid_1.addWidget(self.ransac_iterations_lineedit, 14, 1)

        grid_1.setSpacing(10)
        layout.addLayout(grid_1)

        grid_2 = QGridLayout()
        grid_2.addWidget(self.icp_iterations_label, 1, 0)
        grid_2.addWidget(self.icp_iterations_lineedit, 1, 1)
        grid_2.setSpacing(10)
        layout.addLayout(grid_2)

        grid_3 = QGridLayout()
        grid_3.addWidget(self.run_button, 1, 0)
        grid_3.addWidget(self.export_transform_button, 1, 1)
        grid_3.setSpacing(10)
        layout.addLayout(grid_3)
        self.setLayout(layout)

    def _browse(self):

        if self.sender() == self.moving_detections_pushbutton:
            self.moving_detections, self.moving_ids = _browse_detections(self)
        elif self.sender() == self.fixed_detections_pushbutton:
            self.fixed_detections, self.fixed_ids = _browse_detections(self)
        elif self.sender() == self.moving_keypoints_pushbutton:
            self.moving_keypoints, _ = _browse_detections(self)
        elif self.sender() == self.fixed_keypoints_pushbutton:
            self.fixed_keypoints, _ = _browse_detections(self)

    def _refresh(self):
        self.moving_image_combobox.clear()
        self.fixed_image_combobox.clear()
        for layer in self.viewer.layers:
            self.moving_image_combobox.addItem(layer.name)
            self.fixed_image_combobox.addItem(layer.name)

    def _save_transform(self):
        transform_matrix_combined = np.matmul(self.transform_matrix_icp, self.transform_matrix_sc)
        save_file_name = QFileDialog.getSaveFileName(self, 'Save Transform Matrix')  # this returns a tuple!
        print("=" * 20)
        print("Saving Transform Matrix at {}".format(save_file_name[0]))
        np.savetxt(save_file_name[0], transform_matrix_combined, delimiter=' ', fmt='%1.3f')

    def _estimate_transform_changed(self):
        if self.estimate_transform_combo_box.currentIndex() == 0:  # unsupervised
            self.r_bins_label.show()
            self.r_bins_lineedit.show()
            self.theta_bins_label.show()
            self.theta_bins_lineedit.show()
            self.phi_bins_label.show()
            self.phi_bins_lineedit.show()
            self.ransac_samples_label.show()
            self.ransac_samples_lineedit.show()
            self.ransac_iterations_label.show()
            self.ransac_iterations_lineedit.show()
            self.moving_keypoints_label.hide()
            self.moving_keypoints_pushbutton.hide()
            self.fixed_keypoints_label.hide()
            self.fixed_keypoints_pushbutton.hide()
        else:  # supervised
            self.r_bins_label.hide()
            self.r_bins_lineedit.hide()
            self.theta_bins_label.hide()
            self.theta_bins_lineedit.hide()
            self.phi_bins_label.hide()
            self.phi_bins_lineedit.hide()
            self.ransac_samples_label.hide()
            self.ransac_samples_lineedit.hide()
            self.ransac_iterations_label.hide()
            self.ransac_iterations_lineedit.hide()
            self.moving_keypoints_label.show()
            self.moving_keypoints_pushbutton.show()
            self.fixed_keypoints_label.show()
            self.fixed_keypoints_pushbutton.show()

    def _click_run(self):
        moving_nucleus_size = []
        fixed_nucleus_size = []
        if (not self.csv_checkbox.isChecked()):
            temp_1 = []
            temp_2 = []

            moving_ids = np.unique(self.viewer.layers[self.moving_image_combobox.currentIndex()].data)
            moving_ids = moving_ids[moving_ids != 0]
            fixed_ids = np.unique(self.viewer.layers[self.fixed_image_combobox.currentIndex()].data)
            fixed_ids = fixed_ids[fixed_ids != 0]
            print("=" * 20)
            print("Obtaining moving image centroids")
            for i, id in enumerate(tqdm(moving_ids)):
                self.moving_progress_bar.setValue(i / len(moving_ids) * 100)
                z, y, x = np.where(self.viewer.layers[self.moving_image_combobox.currentIndex()].data == id)
                zmean, ymean, xmean = np.mean(z), np.mean(y), np.mean(x)
                temp_1.append([zmean, ymean, xmean])
                moving_nucleus_size.append(float(self.moving_image_anisotropy_line.text()) * len(z))

            print("=" * 20)
            print("Obtaining fixed image centroids")
            for j, id in enumerate(tqdm(fixed_ids)):
                self.fixed_progress_bar.setValue(j / len(fixed_ids) * 100)
                z, y, x = np.where(self.viewer.layers[self.fixed_image_combobox.currentIndex()].data == id)
                zmean, ymean, xmean = np.mean(z), np.mean(y), np.mean(x)
                temp_2.append([zmean, ymean, xmean])
                fixed_nucleus_size.append(float(self.fixed_image_anisotropy_line.text()) * len(z))
            self.moving_detections = np.asarray(temp_1).transpose()  # 3 X N --> z y x
            self.fixed_detections = np.asarray(temp_2).transpose()  # 3 x N  --> z y x
        else:
            pass

        moving_centroid = get_centroid(self.moving_detections, transposed=False)  # 3 x 1
        fixed_centroid = get_centroid(self.fixed_detections, transposed=False)  # 3 x 1
        moving_mean_distance = get_mean_distance(self.moving_detections, transposed=False)
        fixed_mean_distance = get_mean_distance(self.fixed_detections, transposed=False)
        moving_detections_copy = self.moving_detections.copy()
        fixed_detections_copy = self.fixed_detections.copy()
        if (self.estimate_transform_combo_box.currentIndex() == 0):  # unsupervised

            print("=" * 20)
            print("Generating Unaries")

            unary_11, unary_12 = get_unary(moving_centroid, mean_distance=moving_mean_distance,
                                           detections=self.moving_detections, transposed=False)  # (N, 360)
            unary_21, unary_22 = get_unary(fixed_centroid, mean_distance=fixed_mean_distance,
                                           detections=self.fixed_detections, transposed=False)
            U1 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
            U2 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2

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
            if (len(moving_nucleus_size) == 0 or len(fixed_nucleus_size) == 0):
                ransac_error = 16  # approx average nucleus radius
            else:
                ransac_error = 0.5 * (np.average(moving_nucleus_size) ** (1 / 3) + np.average(fixed_nucleus_size) ** (
                            1 / 3))  # approx average nucleus radius

            print("=" * 20)
            print("Beginning RANSAC")
            transform_matrix_1, inliers_best_1 = do_ransac(moving_detections_copy[:, row_indices_1],  # 4 x N
                                                           fixed_detections_copy[:, col_indices_1],  # 4 x N
                                                           min_samples=int(self.ransac_samples_lineedit.text()),
                                                           trials=int(self.ransac_iterations_lineedit.text()),
                                                           error=ransac_error,
                                                           transform=self.transform_combobox.currentText())
            row_indices_2, col_indices_2 = linear_sum_assignment(U2)
            transform_matrix_2, inliers_best_2 = do_ransac(moving_detections_copy[:, row_indices_2],
                                                           fixed_detections_copy[:, col_indices_2],
                                                           min_samples=int(self.ransac_samples_lineedit.text()),
                                                           trials=int(self.ransac_iterations_lineedit.text()),
                                                           error=ransac_error,
                                                           transform=self.transform_combobox.currentText())
            print("=" * 20)
            print("RANSAC # Inliers 1 = {} and # Inliers 2 = {}".format(inliers_best_1, inliers_best_2))
            if (inliers_best_1 > inliers_best_2):
                self.transform_matrix_sc = transform_matrix_1
            else:
                self.transform_matrix_sc = transform_matrix_2
            print("=" * 20)
            print("4 x 4 Transform matrix estimated from Shape Context Approach is \n", self.transform_matrix_sc)
        elif (self.estimate_transform_combo_box.currentIndex() == 1):  # supervised
            if self.transform_combobox.currentIndex() == 0:  # affine
                self.transform_matrix_sc = get_affine_transform(self.moving_keypoints, self.fixed_keypoints)
            elif self.transform_combobox.currentIndex() == 1:  # similar
                self.transform_matrix_sc = get_similar_transform(self.moving_keypoints, self.fixed_keypoints)

        print("=" * 20)
        transformed_moving_detections = apply_affine_transform(moving_detections_copy, self.transform_matrix_sc)
        self.transform_matrix_icp = perform_icp(transformed_moving_detections, fixed_detections_copy,
                                                int(self.icp_iterations_lineedit.text()),
                                                self.transform_combobox.currentText())
        print("4 x 4 Finetuning Transform matrix estimated from ICP Approach is \n", self.transform_matrix_icp)
        print("=" * 20)
        print("Estimate Transform Matrix is ready to export. Please click on {} push button \n".format(
            "Export Transform"))

    def _browse(self):

        if self.sender() == self.moving_detections_pushbutton:
            self.moving_detections, self.moving_ids = _browse_detections(self)
        elif self.sender() == self.fixed_detections_pushbutton:
            self.fixed_detections, self.fixed_ids = _browse_detections(self)
        elif self.sender() == self.moving_keypoints_pushbutton:
            self.moving_keypoints, _ = _browse_detections(self)
        elif self.sender() == self.fixed_keypoints_pushbutton:
            self.fixed_keypoints, _ = _browse_detections(self)

    def _open_text_file(self):
        if not self.sender().isChecked():
            self.fixed_image_combobox.show()
            self.fixed_detections_pushbutton.hide()
            self.fixed_image_anisotropy_line.show()
            self.fixed_image_anisotropy_label.show()
            self.fixed_progress_bar.show()
            self.moving_image_combobox.show()
            self.moving_detections_pushbutton.hide()
            self.moving_progress_bar.show()
            self.moving_image_anisotropy_label.show()
            self.moving_image_anisotropy_line.show()
            self.izyx_checkbox.hide()
            self.header_checkbox.hide()
        else:
            self.fixed_image_combobox.hide()
            self.fixed_detections_pushbutton.show()
            self.fixed_progress_bar.hide()
            self.fixed_image_anisotropy_label.hide()
            self.fixed_image_anisotropy_line.hide()
            self.moving_image_combobox.hide()
            self.moving_detections_pushbutton.show()
            self.moving_image_combobox.hide()
            self.moving_image_anisotropy_line.hide()
            self.moving_image_anisotropy_label.hide()
            self.moving_progress_bar.hide()
            self.izyx_checkbox.show()
            self.header_checkbox.show()


class EvaluateMetrics(QWidget):
    def __init__(self, napari_viewer):
        super().__init__()
        self.viewer = napari_viewer

        # define components
        self.sync_button = QPushButton('Sync with Viewer')
        self.sync_button.clicked.connect(self._refresh)
        self.moving_image_label = QLabel('Moving Image:')
        self.moving_image_combobox = QComboBox(self)
        self.moving_image_anisotropy_label = QLabel('Moving Image Anisotropy [Z]:')
        self.moving_image_anisotropy_line = QLineEdit('1.0')

        self.fixed_image_label = QLabel('Fixed Image:')
        self.fixed_image_combobox = QComboBox(self)
        self.fixed_image_anisotropy_label = QLabel('Fixed Image Anisotropy [Z]:')
        self.fixed_image_anisotropy_line = QLineEdit('1.0')

        self.transform_label = QLabel('Transform:')
        self.transform_pushbutton = QPushButton('Load')
        self.transform_pushbutton.clicked.connect(self._load_transform)

        self.transform_label_2 = QLabel('Transform (Optional):')
        self.transform_pushbutton_2 = QPushButton('Load')
        self.transform_pushbutton_2.clicked.connect(self._load_transform)

        self.icp_checkbox = QCheckBox('ICP?')

        self.csv_checkbox = QCheckBox('csv?')
        self.csv_checkbox.clicked.connect(self._open_text_file)
        self.izyx_checkbox = QCheckBox('IZYXR?')
        self.izyx_checkbox.hide()
        self.header_checkbox = QCheckBox('Header?')
        self.header_checkbox.hide()

        self.moving_image_2_combobox = QComboBox(self)
        self.fixed_image_2_combobox = QComboBox(self)

        self.moving_detections_label = QLabel('Moving Detections:')
        self.moving_detections_pushbutton = QPushButton('Load')
        self.moving_detections_pushbutton.hide()
        self.moving_detections_pushbutton.clicked.connect(self._browse)

        self.fixed_detections_label = QLabel('Fixed Detections:')
        self.fixed_detections_pushbutton = QPushButton('Load')
        self.fixed_detections_pushbutton.hide()
        self.fixed_detections_pushbutton.clicked.connect(self._browse)

        self.moving_progress_bar = QProgressBar(self)
        self.fixed_progress_bar = QProgressBar(self)

        self.moving_keypoints_label = QLabel('Moving Keypoints:')
        self.moving_keypoints_pushbutton = QPushButton('Load')
        self.moving_keypoints_pushbutton.clicked.connect(self._browse)

        self.fixed_keypoints_label = QLabel('Fixed Keypoints:')
        self.fixed_keypoints_pushbutton = QPushButton('Load')
        self.fixed_keypoints_pushbutton.clicked.connect(self._browse)

        self.run_button = QPushButton('Run')
        self.run_button.clicked.connect(self._calculate_metrics)
        self.export_transformed_image_button = QPushButton('Export Transformed Image')
        self.export_transformed_image_button.clicked.connect(self._export_transformed_image)

        self.matching_accuracy_label = QLabel('Matching Accuracy:')
        self.matching_accuracy_linedit = QLineEdit('')

        self.avg_registration_error_label = QLabel('Average Registration Error:')
        self.avg_registration_error_lineedit = QLineEdit('')

        # define layout
        layout = QVBoxLayout()
        grid_1 = QGridLayout()
        grid_1.addWidget(self.sync_button, 0, 0)
        grid_1.addWidget(self.moving_image_label, 1, 0)
        grid_1.addWidget(self.moving_image_combobox, 1, 1)
        grid_1.addWidget(self.moving_image_anisotropy_label, 2, 0)
        grid_1.addWidget(self.moving_image_anisotropy_line, 2, 1)

        grid_1.addWidget(self.fixed_image_label, 3, 0)
        grid_1.addWidget(self.fixed_image_combobox, 3, 1)
        grid_1.addWidget(self.fixed_image_anisotropy_label, 4, 0)
        grid_1.addWidget(self.fixed_image_anisotropy_line, 4, 1)

        grid_1.addWidget(self.transform_label, 5, 0)
        grid_1.addWidget(self.transform_pushbutton, 5, 1)

        grid_1.addWidget(self.transform_label_2, 6, 0)
        grid_1.addWidget(self.transform_pushbutton_2, 6, 1)

        grid_1.addWidget(self.icp_checkbox, 7, 0)

        grid_1.setSpacing(10)
        layout.addLayout(grid_1)

        grid_2 = QGridLayout()
        grid_2.addWidget(self.csv_checkbox, 0, 0)
        grid_2.addWidget(self.izyx_checkbox, 0, 1)
        grid_2.addWidget(self.header_checkbox, 0, 2)

        grid_2.addWidget(self.moving_detections_label, 1, 0)
        grid_2.addWidget(self.moving_image_2_combobox, 1, 1)
        grid_2.addWidget(self.moving_detections_pushbutton, 1, 1)

        grid_2.addWidget(self.moving_progress_bar, 2, 0, 1, 2)

        grid_2.addWidget(self.fixed_detections_label, 3, 0)
        grid_2.addWidget(self.fixed_image_2_combobox, 3, 1)
        grid_2.addWidget(self.fixed_detections_pushbutton, 3, 1)
        grid_2.addWidget(self.fixed_progress_bar, 4, 0, 1, 2)

        grid_2.addWidget(self.moving_keypoints_label, 5, 0)
        grid_2.addWidget(self.moving_keypoints_pushbutton, 5, 1)

        grid_2.addWidget(self.fixed_keypoints_label, 6, 0)
        grid_2.addWidget(self.fixed_keypoints_pushbutton, 6, 1)

        grid_2.setSpacing(10)
        layout.addLayout(grid_2)

        grid_3 = QGridLayout()
        grid_3.addWidget(self.run_button, 0, 0)
        grid_3.addWidget(self.export_transformed_image_button, 0, 1)
        grid_3.addWidget(self.matching_accuracy_label, 1, 0)
        grid_3.addWidget(self.matching_accuracy_linedit, 1, 1)
        grid_3.addWidget(self.avg_registration_error_label, 2, 0)
        grid_3.addWidget(self.avg_registration_error_lineedit, 2, 1)
        grid_3.setSpacing(10)
        layout.addLayout(grid_3)
        self.setLayout(layout)
        self.transform_matrix_2 = np.identity(4)  # just a placeholder, if a second matrix exists, this is replaced !!

    def _browse(self):

        if self.sender() == self.moving_detections_pushbutton:
            self.moving_detections, self.moving_ids = _browse_detections(self)
        elif self.sender() == self.fixed_detections_pushbutton:
            self.fixed_detections, self.fixed_ids = _browse_detections(self)
        elif self.sender() == self.moving_keypoints_pushbutton:
            self.moving_keypoints, _ = _browse_detections(self)
        elif self.sender() == self.fixed_keypoints_pushbutton:
            self.fixed_keypoints, _ = _browse_detections(self)

    def _load_transform(self):
        if self.sender() == self.transform_pushbutton:
            self.transform_matrix_1 = _browse_transform(self)
        elif self.sender() == self.transform_pushbutton_2:
            self.transform_matrix_2 = _browse_transform(self)

    def _calculate_metrics(self):
        # first associate keypoints with detections
        moving_kp_detection_cost_matrix = cdist(self.moving_keypoints.transpose(), self.moving_detections.transpose())
        moving_row_indices, moving_col_indices = linear_sum_assignment(moving_kp_detection_cost_matrix)
        moving_dictionary = {}
        for index in moving_row_indices:  # 0 ... 11
            moving_dictionary[index + 1] = self.moving_ids[
                moving_col_indices[index]]  # 1...12 are keys ---> corresponding actual ids of moving detections

        fixed_kp_detection_cost_matrix = cdist(self.fixed_keypoints.transpose(), self.fixed_detections.transpose())
        fixed_row_indices, fixed_col_indices = linear_sum_assignment(fixed_kp_detection_cost_matrix)
        fixed_dictionary = {}
        for index in fixed_row_indices:
            fixed_dictionary[index + 1] = self.fixed_ids[fixed_col_indices[index]]

        # next apply affine transform onto moving detections
        transformed_moving_detections = apply_affine_transform(self.moving_detections, self.transform_matrix_1)  # 3 x N
        transformed_moving_detections = apply_affine_transform(transformed_moving_detections,
                                                               self.transform_matrix_2)  # 3 x N

        # if icp is checked, apply icp
        if self.icp_checkbox.isChecked():
            self.transform_matrix_icp = perform_icp(transformed_moving_detections, self.fixed_detections, 50, 'Affine')
            self.transform_matrix_combined = np.matmul(self.transform_matrix_icp,
                                                       np.matmul(self.transform_matrix_2, self.transform_matrix_1))
            transformed_moving_detections = apply_affine_transform(transformed_moving_detections,
                                                                   self.transform_matrix_icp)
        else:
            self.transform_matrix_combined = np.matmul(self.transform_matrix_2, self.transform_matrix_1)

        # then apply linear sum assignment between transformed moving detections and fixed detections
        cost_matrix = cdist(transformed_moving_detections.transpose(), self.fixed_detections.transpose())
        row_indices, col_indices = linear_sum_assignment(cost_matrix)

        # these are actual ids
        row_ids = self.moving_ids[row_indices]
        col_ids = self.fixed_ids[col_indices]

        # evaluate accuracy
        hits = 0
        for key in moving_dictionary.keys():
            if col_ids[np.where(row_ids == moving_dictionary[key])] == fixed_dictionary[key]:
                hits += 1
        print("=" * 20)

        self.matching_accuracy_linedit.setText("{:.3f}".format(hits / len(moving_dictionary.keys())))
        print("Matching Accuracy is equal to {}".format(self.matching_accuracy_linedit.text()))

        # evaluate avg. registration error
        transformed_moving_keypoints = apply_affine_transform(self.moving_keypoints, self.transform_matrix_combined)
        distance = 0
        for i in range(transformed_moving_keypoints.shape[1]):
            distance += np.linalg.norm(
                [transformed_moving_keypoints.transpose()[i, :] - self.fixed_keypoints.transpose()[i, :]])
        print("=" * 20)
        self.avg_registration_error_lineedit.setText("{:.3f}".format(distance / len(moving_dictionary.keys())))
        print("Average Registration Error is equal to {}".format(self.avg_registration_error_lineedit.text()))

    def _affine_translate(self, transform, dx, dy, dz):
        new_transform = sitk.AffineTransform(transform)
        new_transform.SetTranslation((dx, dy, dz))
        return new_transform

    def _affine_rotate(self, transform, rotation, dimension=3):
        parameters = np.array(transform.GetParameters())
        new_transform = sitk.AffineTransform(transform)
        matrix = np.array(transform.GetMatrix()).reshape((dimension, dimension))
        new_matrix = np.dot(rotation, matrix)
        new_transform.SetMatrix(new_matrix.ravel())
        return new_transform

    def _export_transformed_image(self):
        save_file_name = QFileDialog.getSaveFileName(self, 'Save Transformed Image')  # this returns a tuple!
        print("Saving Transformed Image at {}".format(save_file_name[0]))
        affine = sitk.AffineTransform(3)
        transform_xyz = np.zeros_like(self.transform_matrix_combined)  # 4 x 4
        transform_xyz[:3, :3] = np.flip(np.flip(self.transform_matrix_combined[:3, :3], 0), 1)
        transform_xyz[0, 3] = self.transform_matrix_combined[2, 3]
        transform_xyz[1, 3] = self.transform_matrix_combined[1, 3]
        transform_xyz[2, 3] = self.transform_matrix_combined[0, 3]
        transform_xyz[3, 3] = 1.0

        # now also include the anisotropy factors of the moving and fixed image

        transform_xyz[2, 0] /= float(self.fixed_image_anisotropy_line.text())
        transform_xyz[2, 1] /= float(self.fixed_image_anisotropy_line.text())
        transform_xyz[2, 2] /= float(self.fixed_image_anisotropy_line.text()) / float(
            self.moving_image_anisotropy_line.text())
        transform_xyz[2, 3] /= float(self.fixed_image_anisotropy_line.text())
        inv_matrix = np.linalg.inv(transform_xyz)
        affine = self._affine_translate(affine, inv_matrix[0, 3], inv_matrix[1, 3], inv_matrix[2, 3])
        affine = self._affine_rotate(affine, inv_matrix[:3, :3])
        reference_image = sitk.GetImageFromArray(self.viewer.layers[self.fixed_image_combobox.currentIndex()].data)
        moving_image = sitk.GetImageFromArray(self.viewer.layers[self.moving_image_combobox.currentIndex()].data)
        interpolator = sitk.sitkNearestNeighbor
        default_value = 0.0
        transformed_image = sitk.GetArrayFromImage(
            sitk.Resample(moving_image, reference_image, affine, interpolator, default_value))
        print("Transformed image has shape {}".format(transformed_image.shape))
        tifffile.imsave(save_file_name[0], transformed_image)

    def _open_text_file(self):
        if not self.sender().isChecked():
            self.fixed_image_2_combobox.show()
            self.fixed_detections_pushbutton.hide()
            self.fixed_progress_bar.show()
            self.moving_image_2_combobox.show()
            self.moving_detections_pushbutton.hide()
            self.moving_progress_bar.show()
            self.izyx_checkbox.hide()
            self.header_checkbox.hide()
        else:
            self.fixed_image_2_combobox.hide()
            self.fixed_detections_pushbutton.show()
            self.fixed_progress_bar.hide()
            self.moving_image_2_combobox.hide()
            self.moving_detections_pushbutton.show()
            self.moving_progress_bar.hide()
            self.izyx_checkbox.show()
            self.header_checkbox.show()

    def _refresh(self):
        self.moving_image_combobox.clear()
        self.fixed_image_combobox.clear()
        for layer in self.viewer.layers:
            self.moving_image_combobox.addItem(layer.name)
            self.fixed_image_combobox.addItem(layer.name)


@napari_hook_implementation
def napari_experimental_provide_dock_widget():
    return [DetectNuclei, EstimateTransform, EvaluateMetrics]
