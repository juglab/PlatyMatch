import SimpleITK as sitk
import csv
import numpy as np
import tifffile
from PyQt5.QtCore import Qt
from napari.qt.threading import thread_worker
from napari_plugin_engine import napari_hook_implementation
from qtpy.QtWidgets import QWidget, QGridLayout, QVBoxLayout, QPushButton, QCheckBox, QLabel, QComboBox, QLineEdit, \
    QFileDialog, QProgressBar
from scipy.optimize import linear_sum_assignment
from scipy.spatial.distance import cdist
from sklearn.decomposition import PCA
from tqdm import tqdm

from platymatch.detect_nuclei.ss_log import find_spheres
from platymatch.estimate_transform.apply_transform import apply_affine_transform
from platymatch.estimate_transform.find_transform import get_affine_transform, get_similar_transform
from platymatch.estimate_transform.perform_icp import perform_icp
from platymatch.estimate_transform.shape_context import get_unary, get_unary_distance, do_ransac
from platymatch.utils.utils import _visualize_nuclei, _browse_detections, _browse_transform, get_centroid, \
    get_mean_distance


class DetectNuclei(QWidget):
    def __init__(self, napari_viewer):
        super().__init__()
        self.viewer = napari_viewer
        # define components
        logo_path = 'platymatch/resources/platymatch_logo_small.png'
        self.logo_label = QLabel(f'<h1><img src="{logo_path}"></h1>')
        self.logo_label.setAlignment(Qt.AlignCenter)
        self.method_description_label = QLabel(
            '<small>Registration of Multi-modal Volumetric Images by Establishing <br>Cell Correspondence.<br> If you are using this in your research please <a href="https://github.com/juglab/PlatyMatch#citation" style="color:gray;">cite us</a>.</small><br><small><tt><a href="https://github.com/juglab/PlatyMatch" style="color:gray;">https://github.com/juglab/PlatyMatch</a></tt></small>')
        self.method_description_label.setOpenExternalLinks(True)

        # define components
        self.sync_button = QPushButton('Sync with Viewer')
        self.sync_button.clicked.connect(self._refresh)
        self.process_image_label = QLabel('Process Image')
        self.images_combo_box = QComboBox(self)

        self.min_radius_label = QLabel('Min Radius')
        self.min_radius_text = QLineEdit('9')
        self.min_radius_text.setMaximumWidth(280)
        self.min_radius_text.setAlignment(Qt.AlignCenter)

        self.step_radius_label = QLabel('Step')
        self.step_radius_text = QLineEdit('1')
        self.step_radius_text.setMaximumWidth(280)
        self.step_radius_text.setAlignment(Qt.AlignCenter)

        self.max_radius_label = QLabel('Max Radius')
        self.max_radius_text = QLineEdit('15')
        self.max_radius_text.setMaximumWidth(280)
        self.max_radius_text.setAlignment(Qt.AlignCenter)

        self.anisotropy_label = QLabel('Anisotropy (Z)')
        self.anisotropy_text = QLineEdit('1.0')
        self.anisotropy_text.setMaximumWidth(280)
        self.anisotropy_text.setAlignment(Qt.AlignCenter)

        self.run_button = QPushButton('Run Scale Space Log')
        self.run_button.setMaximumWidth(280)
        self.stop_button = QPushButton('Stop')
        self.stop_button.setMaximumWidth(280)

        self.run_button.clicked.connect(self._start_worker)
        self.stop_button.clicked.connect(self._stop_worker)

        self.export_detections_button = QPushButton('Export Detections to csv')
        self.export_detections_button.setMaximumWidth(280)
        self.export_detections_button.clicked.connect(self._export_detections)

        self.export_instance_mask_button = QPushButton('Export Instance Mask')
        self.export_instance_mask_button.setMaximumWidth(280)
        self.export_instance_mask_button.clicked.connect(self._export_instance_mask)

        for layer in self.viewer.layers:
            self.images_combo_box.addItem(layer.name)

        outer_layout = QVBoxLayout()

        grid_0 = QGridLayout()
        grid_0.addWidget(self.logo_label, 0, 0, 1, 1)
        grid_0.addWidget(self.method_description_label, 0, 1, 1, 1)
        grid_0.setSpacing(10)

        grid_1 = QGridLayout()
        grid_1.addWidget(self.sync_button, 0, 0)
        grid_1.addWidget(self.process_image_label, 1, 0)
        grid_1.addWidget(self.images_combo_box, 1, 1)
        grid_1.addWidget(self.min_radius_label, 2, 0)
        grid_1.addWidget(self.min_radius_text, 2, 1)
        grid_1.addWidget(self.max_radius_label, 3, 0)
        grid_1.addWidget(self.max_radius_text, 3, 1)
        grid_1.addWidget(self.step_radius_label, 4, 0)
        grid_1.addWidget(self.step_radius_text, 4, 1)
        grid_1.addWidget(self.anisotropy_label, 5, 0)
        grid_1.addWidget(self.anisotropy_text, 5, 1)
        grid_1.addWidget(self.run_button, 6, 0)
        grid_1.addWidget(self.stop_button, 6, 1)

        grid_1.addWidget(self.export_detections_button, 8, 0)
        grid_1.addWidget(self.export_instance_mask_button, 8, 1)
        grid_1.setSpacing(10)

        outer_layout.addLayout(grid_0)
        outer_layout.addLayout(grid_1)
        self.setLayout(outer_layout)
        self.setFixedWidth(560)

    def _refresh(self):
        self.images_combo_box.clear()
        for layer in self.viewer.layers:
            self.images_combo_box.addItem(layer.name)

    def _finish(self):
        self.run_button.setStyleSheet("")

    def _stop_worker(self):
        self.worker.quit()
        self.run_button.setStyleSheet("")
        print("=" * 25)
        print("Nuclei Detection for radii from {} to {} was stopped".format(self.min_radius_text.text(),
                                                                            self.max_radius_text.text()))

    def _start_worker(self):
        self.worker = self._click_run()
        self.worker.finished.connect(self._finish)
        self.worker.finished.connect(self.stop_button.clicked.disconnect)
        self.worker.start()

    @thread_worker
    def _click_run(self):
        self.run_button.setStyleSheet("border :3px solid green")
        print("=" * 25)
        print("Beginning Nuclei Detection for radii from {} to {}".format(self.min_radius_text.text(),
                                                                          self.max_radius_text.text()))
        image = self.viewer.layers[self.images_combo_box.currentIndex()].data

        peaks_otsu, peaks_subset, log, peaks_local_minima, threshold = find_spheres(image, scales=range(
            int(np.round(float(self.min_radius_text.text()) / np.sqrt(3))),
            int(np.round(float(self.max_radius_text.text()) / np.sqrt(3))), int(self.step_radius_text.text())),
                                                                                    anisotropy_factor=float(
                                                                                        self.anisotropy_text.text()))
        _visualize_nuclei(self, peaks_subset)
        print("=" * 25)
        print(
            "Nuclei detection is complete. Please export locations of these nuclei to a csv file or export an instance mask")

    def _export_detections(self):
        save_file_name = QFileDialog.getSaveFileName(self, 'Save File')  # this returns a tuple!
        print("=" * 25)
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
        print("=" * 25)
        print("Saving Label Image at {}".format(save_file_name[0]))
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
        logo_path = 'platymatch/resources/platymatch_logo_small.png'
        self.logo_label = QLabel(f'<h1><img src="{logo_path}"></h1>')
        self.logo_label.setAlignment(Qt.AlignCenter)
        self.method_description_label = QLabel(
            '<small>Registration of Multi-modal Volumetric Images by Establishing <br>Cell Correspondence.<br> If you are using this in your research please <a href="https://github.com/juglab/PlatyMatch#citation" style="color:gray;">cite us</a>.</small><br><small><tt><a href="https://github.com/juglab/PlatyMatch" style="color:gray;">https://github.com/juglab/PlatyMatch</a></tt></small>')
        self.method_description_label.setOpenExternalLinks(True)

        # define components
        self.sync_button = QPushButton('Sync with Viewer')
        self.sync_button.clicked.connect(self._refresh)
        self.sync_button.setMaximumWidth(280)
        self.csv_checkbox = QCheckBox('csv?')
        self.izyx_checkbox = QCheckBox('IZYXR?')
        self.izyx_checkbox.hide()
        self.header_checkbox = QCheckBox('Header?')
        self.header_checkbox.hide()
        self.csv_checkbox.clicked.connect(self._open_text_file)

        self.moving_image_combobox = QComboBox(self)
        self.moving_image_combobox.setMaximumWidth(280)
        self.fixed_image_combobox = QComboBox(self)
        self.fixed_image_combobox.setMaximumWidth(280)

        self.moving_detections_label = QLabel('Moving Detections')
        self.moving_detections_label.setMaximumWidth(280)
        self.moving_detections_pushbutton = QPushButton('Load')
        self.moving_detections_pushbutton.setMaximumWidth(280)
        self.moving_detections_pushbutton.hide()
        self.moving_detections_pushbutton.clicked.connect(self._browse)

        self.fixed_detections_label = QLabel('Fixed Detections')
        self.fixed_detections_label.setMaximumWidth(280)
        self.fixed_detections_pushbutton = QPushButton('Load')
        self.fixed_detections_pushbutton.setMaximumWidth(280)
        self.fixed_detections_pushbutton.hide()
        self.fixed_detections_pushbutton.clicked.connect(self._browse)

        self.moving_progress_bar = QProgressBar(self)
        self.fixed_progress_bar = QProgressBar(self)

        self.moving_image_anisotropy_label = QLabel('Moving Image Anisotropy [Z]')
        self.moving_image_anisotropy_label.setMaximumWidth(280)
        self.moving_image_anisotropy_line = QLineEdit('1.0')
        self.moving_image_anisotropy_line.setMaximumWidth(280)
        self.moving_image_anisotropy_line.setAlignment(Qt.AlignCenter)

        self.fixed_image_anisotropy_label = QLabel('Fixed Image Anisotropy [Z]')
        self.fixed_image_anisotropy_label.setMaximumWidth(280)
        self.fixed_image_anisotropy_line = QLineEdit('1.0')
        self.fixed_image_anisotropy_line.setMaximumWidth(280)
        self.fixed_image_anisotropy_line.setAlignment(Qt.AlignCenter)

        self.transform_label = QLabel('Type of Transform')
        self.transform_combobox = QComboBox(self)  # affine, similar
        self.transform_combobox.addItems(['Affine', 'Similar'])
        self.transform_combobox.setEditable(True)
        self.transform_combobox.lineEdit().setAlignment(Qt.AlignCenter)
        self.transform_combobox.lineEdit().setReadOnly(True)

        self.estimate_transform_label = QLabel('Estimate Transform')
        self.estimate_transform_combo_box = QComboBox(self)  # unsupervised, supervised
        self.estimate_transform_combo_box.addItems(['Unsupervised', 'Supervised'])
        self.estimate_transform_combo_box.currentIndexChanged.connect(self._estimate_transform_changed)
        self.estimate_transform_combo_box.setEditable(True)
        self.estimate_transform_combo_box.lineEdit().setAlignment(Qt.AlignCenter)
        self.estimate_transform_combo_box.lineEdit().setReadOnly(True)

        self.moving_keypoints_label = QLabel('Moving keypoints')
        self.moving_keypoints_label.setMaximumWidth(280)
        self.moving_keypoints_pushbutton = QPushButton('Load')
        self.moving_keypoints_pushbutton.setMaximumWidth(280)
        self.moving_keypoints_label.hide()
        self.moving_keypoints_pushbutton.clicked.connect(self._browse)
        self.moving_keypoints_pushbutton.setMaximumWidth(280)
        self.moving_keypoints_pushbutton.hide()

        self.fixed_keypoints_label = QLabel('Fixed keypoints')
        self.fixed_keypoints_label.setMaximumWidth(280)
        self.fixed_keypoints_pushbutton = QPushButton('Load')
        self.fixed_keypoints_pushbutton.setMaximumWidth(280)
        self.fixed_keypoints_label.hide()
        self.fixed_keypoints_pushbutton.clicked.connect(self._browse)
        self.fixed_keypoints_pushbutton.hide()

        self.shape_context_checkbox = QCheckBox('Shape Context')
        self.shape_context_checkbox.setMaximumWidth(280)
        self.pca_checkbox = QCheckBox('PCA')
        self.pca_checkbox.setMaximumWidth(280)
        self.shape_context_checkbox.setChecked(True)
        self.shape_context_checkbox.clicked.connect(self._hide_shape_context)
        self.pca_checkbox.clicked.connect(self._hide_shape_context)

        self.r_bins_label = QLabel('Number of r bins')
        self.r_bins_label.setMaximumWidth(280)
        self.r_bins_lineedit = QLineEdit('5')
        self.r_bins_lineedit.setMaximumWidth(280)
        self.r_bins_lineedit.setAlignment(Qt.AlignCenter)

        self.theta_bins_label = QLabel('Number of \u03B8 bins')
        self.theta_bins_label.setMaximumWidth(280)
        self.theta_bins_lineedit = QLineEdit('6')
        self.theta_bins_lineedit.setMaximumWidth(280)
        self.theta_bins_lineedit.setAlignment(Qt.AlignCenter)

        self.phi_bins_label = QLabel('Number of \u03C6 bins')
        self.phi_bins_label.setMaximumWidth(280)
        self.phi_bins_lineedit = QLineEdit('12')
        self.phi_bins_lineedit.setMaximumWidth(280)
        self.phi_bins_lineedit.setAlignment(Qt.AlignCenter)

        self.ransac_iterations_label = QLabel('Number of RANSAC iterations')
        self.ransac_iterations_label.setMaximumWidth(280)
        self.ransac_iterations_lineedit = QLineEdit('8000')
        self.ransac_iterations_lineedit.setMaximumWidth(280)
        self.ransac_iterations_lineedit.setAlignment(Qt.AlignCenter)

        self.ransac_samples_label = QLabel('Number of RANSAC samples')
        self.ransac_samples_label.setMaximumWidth(280)
        self.ransac_samples_lineedit = QLineEdit('4')
        self.ransac_samples_lineedit.setMaximumWidth(280)
        self.ransac_samples_lineedit.setAlignment(Qt.AlignCenter)

        self.icp_iterations_label = QLabel('Number of ICP iterations')
        self.icp_iterations_label.setMaximumWidth(280)
        self.icp_iterations_lineedit = QLineEdit('50')
        self.icp_iterations_lineedit.setMaximumWidth(280)
        self.icp_iterations_lineedit.setAlignment(Qt.AlignCenter)

        self.run_button = QPushButton('Run')
        self.run_button.setMaximumWidth(280)
        self.run_button.clicked.connect(self._start_worker)
        self.export_transform_button = QPushButton('Export Transform')
        self.export_transform_button.setMaximumWidth(280)
        self.export_transform_button.clicked.connect(self._save_transform)

        for layer in self.viewer.layers:
            self.moving_image_combobox.addItem(layer.name)

        for layer in self.viewer.layers:
            self.fixed_image_combobox.addItem(layer.name)

        # define layouut
        layout = QVBoxLayout()
        grid_0 = QGridLayout()
        grid_0.addWidget(self.logo_label, 0, 0, 1, 1)
        grid_0.addWidget(self.method_description_label, 0, 1, 1, 1)
        grid_0.setSpacing(10)
        layout.addLayout(grid_0)

        grid_1 = QGridLayout()
        grid_1.addWidget(self.sync_button, 0, 0)
        grid_1.addWidget(self.csv_checkbox, 1, 0)
        grid_1.addWidget(self.izyx_checkbox, 1, 1)
        grid_1.addWidget(self.header_checkbox, 2, 1)

        grid_1.addWidget(self.moving_detections_label, 3, 0)
        grid_1.addWidget(self.moving_detections_pushbutton, 3, 1)
        grid_1.addWidget(self.moving_image_combobox, 3, 1)
        grid_1.addWidget(self.moving_image_anisotropy_label, 4, 0)
        grid_1.addWidget(self.moving_image_anisotropy_line, 4, 1)
        grid_1.addWidget(self.moving_progress_bar, 5, 0, 1, 2)

        grid_1.addWidget(self.fixed_detections_label, 6, 0)
        grid_1.addWidget(self.fixed_detections_pushbutton, 6, 1)
        grid_1.addWidget(self.fixed_image_combobox, 6, 1)
        grid_1.addWidget(self.fixed_image_anisotropy_label, 7, 0)
        grid_1.addWidget(self.fixed_image_anisotropy_line, 7, 1)
        grid_1.addWidget(self.fixed_progress_bar, 8, 0, 1, 2)

        grid_1.addWidget(self.transform_label, 9, 0)
        grid_1.addWidget(self.transform_combobox, 9, 1)
        grid_1.addWidget(self.estimate_transform_label, 10, 0)
        grid_1.addWidget(self.estimate_transform_combo_box, 10, 1)
        grid_1.addWidget(self.moving_keypoints_label, 11, 0)
        grid_1.addWidget(self.moving_keypoints_pushbutton, 11, 1)
        grid_1.addWidget(self.fixed_keypoints_label, 12, 0)
        grid_1.addWidget(self.fixed_keypoints_pushbutton, 12, 1)
        grid_1.addWidget(self.shape_context_checkbox, 11, 0)
        grid_1.addWidget(self.pca_checkbox, 11, 1)
        grid_1.addWidget(self.r_bins_label, 12, 0)
        grid_1.addWidget(self.r_bins_lineedit, 12, 1)
        grid_1.addWidget(self.theta_bins_label, 13, 0)
        grid_1.addWidget(self.theta_bins_lineedit, 13, 1)
        grid_1.addWidget(self.phi_bins_label, 14, 0)
        grid_1.addWidget(self.phi_bins_lineedit, 14, 1)
        grid_1.addWidget(self.ransac_samples_label, 15, 0)
        grid_1.addWidget(self.ransac_samples_lineedit, 15, 1)
        grid_1.addWidget(self.ransac_iterations_label, 16, 0)
        grid_1.addWidget(self.ransac_iterations_lineedit, 16, 1)
        grid_1.addWidget(self.icp_iterations_label, 17, 0)
        grid_1.addWidget(self.icp_iterations_lineedit, 17, 1)
        grid_1.addWidget(self.run_button, 18, 0)
        grid_1.addWidget(self.export_transform_button, 18, 1)
        grid_1.setSpacing(10)

        layout.addLayout(grid_1)
        self.setLayout(layout)
        self.setFixedWidth(560)

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
        if self.shape_context_checkbox.isChecked():
            transform_matrix_combined = np.matmul(self.transform_matrix_icp, self.transform_matrix_sc)
            save_file_name = QFileDialog.getSaveFileName(self, 'Save Transform Matrix')  # this returns a tuple!
            print("=" * 25)
            print("Saving Transform Matrix at {}".format(save_file_name[0]))
            np.savetxt(save_file_name[0], transform_matrix_combined, delimiter=' ', fmt='%1.3f')
        elif self.pca_checkbox.isChecked():
            save_dir_name = str(QFileDialog.getExistingDirectory(self, "Select Directory"))
            print("=" * 25)
            print("Saving Transform Matrix at {}".format(save_dir_name[0]))
            np.savetxt(save_dir_name + '/moving_transform.txt', self.moving_transform, delimiter=' ', fmt='%1.3f')
            np.savetxt(save_dir_name + '/fixed_transform.txt', self.fixed_transform, delimiter=' ', fmt='%1.3f')

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

    def _finish(self):
        self.run_button.setStyleSheet("")

    def _start_worker(self):
        self.worker = self._click_run()
        self.worker.yielded.connect(self.show_intermediate_result)
        self.worker.finished.connect(self._finish)
        # self.worker.finished.connect(self.stop_button.clicked.disconnect)
        self.worker.start()

    def show_intermediate_result(self, yielded_data):
        if yielded_data[1] is None:
            self.moving_progress_bar.setValue(yielded_data[0])
        else:
            self.fixed_progress_bar.setValue(yielded_data[1])

    @thread_worker
    def _click_run(self):
        self.run_button.setStyleSheet("border :3px solid green")
        moving_nucleus_size = []
        fixed_nucleus_size = []
        if (not self.csv_checkbox.isChecked()):
            temp_1 = []
            temp_2 = []

            moving_ids = np.unique(self.viewer.layers[self.moving_image_combobox.currentIndex()].data)
            moving_ids = moving_ids[moving_ids != 0]
            fixed_ids = np.unique(self.viewer.layers[self.fixed_image_combobox.currentIndex()].data)
            fixed_ids = fixed_ids[fixed_ids != 0]
            print("=" * 25)
            print("Obtaining moving image centroids")
            for i, id in enumerate(tqdm(moving_ids)):
                # self.moving_progress_bar.setValue(int((i+1) / len(moving_ids) * 100))
                z, y, x = np.where(self.viewer.layers[self.moving_image_combobox.currentIndex()].data == id)
                zmean, ymean, xmean = np.mean(z), np.mean(y), np.mean(x)
                temp_1.append([zmean, ymean, xmean])
                moving_nucleus_size.append(float(self.moving_image_anisotropy_line.text()) * len(z))
                yield np.round(100 * (i + 1) / len(moving_ids)).astype(int), None
            print("=" * 25)
            print("Obtaining fixed image centroids")
            for j, id in enumerate(tqdm(fixed_ids)):
                # self.fixed_progress_bar.setValue(int((j+1) / len(fixed_ids) * 100))
                z, y, x = np.where(self.viewer.layers[self.fixed_image_combobox.currentIndex()].data == id)
                zmean, ymean, xmean = np.mean(z), np.mean(y), np.mean(x)
                temp_2.append([zmean, ymean, xmean])
                fixed_nucleus_size.append(float(self.fixed_image_anisotropy_line.text()) * len(z))
                yield np.round(100 * (i + 1) / len(moving_ids)).astype(int), np.round(
                    100 * (j + 1) / len(fixed_ids)).astype(int)
            self.moving_detections = np.asarray(temp_1).transpose()  # 3 X N --> z y x
            self.fixed_detections = np.asarray(temp_2).transpose()  # 3 x N  --> z y x

        else:
            pass

        moving_centroid = get_centroid(self.moving_detections, transposed=False)  # 3 x 1
        fixed_centroid = get_centroid(self.fixed_detections, transposed=False)  # 3 x 1

        if self.shape_context_checkbox.isChecked():

            moving_mean_distance = get_mean_distance(self.moving_detections, transposed=False)
            fixed_mean_distance = get_mean_distance(self.fixed_detections, transposed=False)
            moving_detections_copy = self.moving_detections.copy()
            fixed_detections_copy = self.fixed_detections.copy()
            if (self.estimate_transform_combo_box.currentIndex() == 0):  # unsupervised

                print("=" * 25)
                print("Generating Unaries")

                unary_11, unary_12, _, _ = get_unary(moving_centroid, mean_distance=moving_mean_distance,
                                                     detections=self.moving_detections, type='moving',
                                                     transposed=False)  # (N, 360)
                unary_21, unary_22, unary_23, unary_24 = get_unary(fixed_centroid, mean_distance=fixed_mean_distance,
                                                                   detections=self.fixed_detections, type='fixed',
                                                                   transposed=False)

                U11 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
                U12 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
                U13 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
                U14 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
                U21 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
                U22 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
                U23 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2
                U24 = np.zeros((self.moving_detections.shape[1], self.fixed_detections.shape[1]))  # N1 x N2

                for i in range(U11.shape[0]):
                    for j in range(U11.shape[1]):
                        unary_i = unary_11[i]
                        unary_j = unary_21[j]
                        U11[i, j] = get_unary_distance(unary_i, unary_j)

                for i in range(U12.shape[0]):
                    for j in range(U12.shape[1]):
                        unary_i = unary_11[i]
                        unary_j = unary_22[j]
                        U12[i, j] = get_unary_distance(unary_i, unary_j)

                for i in range(U13.shape[0]):
                    for j in range(U13.shape[1]):
                        unary_i = unary_11[i]
                        unary_j = unary_23[j]
                        U13[i, j] = get_unary_distance(unary_i, unary_j)

                for i in range(U14.shape[0]):
                    for j in range(U14.shape[1]):
                        unary_i = unary_11[i]
                        unary_j = unary_24[j]
                        U14[i, j] = get_unary_distance(unary_i, unary_j)

                for i in range(U21.shape[0]):
                    for j in range(U21.shape[1]):
                        unary_i = unary_12[i]
                        unary_j = unary_21[j]
                        U21[i, j] = get_unary_distance(unary_i, unary_j)

                for i in range(U22.shape[0]):
                    for j in range(U22.shape[1]):
                        unary_i = unary_12[i]
                        unary_j = unary_22[j]
                        U22[i, j] = get_unary_distance(unary_i, unary_j)

                for i in range(U23.shape[0]):
                    for j in range(U23.shape[1]):
                        unary_i = unary_12[i]
                        unary_j = unary_23[j]
                        U23[i, j] = get_unary_distance(unary_i, unary_j)

                for i in range(U24.shape[0]):
                    for j in range(U24.shape[1]):
                        unary_i = unary_12[i]
                        unary_j = unary_24[j]
                        U24[i, j] = get_unary_distance(unary_i, unary_j)

                row_indices_11, col_indices_11 = linear_sum_assignment(U11)
                row_indices_12, col_indices_12 = linear_sum_assignment(U12)
                row_indices_13, col_indices_13 = linear_sum_assignment(U13)
                row_indices_14, col_indices_14 = linear_sum_assignment(U14)
                row_indices_21, col_indices_21 = linear_sum_assignment(U21)
                row_indices_22, col_indices_22 = linear_sum_assignment(U22)
                row_indices_23, col_indices_23 = linear_sum_assignment(U23)
                row_indices_24, col_indices_24 = linear_sum_assignment(U24)

                if (len(moving_nucleus_size) == 0 or len(fixed_nucleus_size) == 0):
                    ransac_error = 16  # approx average nucleus radius # TODO
                else:
                    ransac_error = 0.5 * (
                            np.average(moving_nucleus_size) ** (1 / 3) + np.average(fixed_nucleus_size) ** (
                            1 / 3))  # approx average nucleus radius

                print("=" * 25)
                print("Beginning RANSAC")
                transform_matrix_11, inliers_best_11 = do_ransac(moving_detections_copy[:, row_indices_11],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_11],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())
                transform_matrix_12, inliers_best_12 = do_ransac(moving_detections_copy[:, row_indices_12],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_12],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())

                transform_matrix_13, inliers_best_13 = do_ransac(moving_detections_copy[:, row_indices_13],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_13],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())

                transform_matrix_14, inliers_best_14 = do_ransac(moving_detections_copy[:, row_indices_14],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_14],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())

                transform_matrix_21, inliers_best_21 = do_ransac(moving_detections_copy[:, row_indices_21],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_21],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())

                transform_matrix_22, inliers_best_22 = do_ransac(moving_detections_copy[:, row_indices_22],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_22],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())

                transform_matrix_23, inliers_best_23 = do_ransac(moving_detections_copy[:, row_indices_23],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_23],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())

                transform_matrix_24, inliers_best_24 = do_ransac(moving_detections_copy[:, row_indices_24],  # 4 x N
                                                                 fixed_detections_copy[:, col_indices_24],  # 4 x N
                                                                 min_samples=int(self.ransac_samples_lineedit.text()),
                                                                 trials=int(self.ransac_iterations_lineedit.text()),
                                                                 error=ransac_error,
                                                                 transform=self.transform_combobox.currentText())

                print("=" * 25)
                print("RANSAC # Inliers 11 = {} and # Inliers 12 = {} and # Inliers 13 = {} and # Inliers 14 = {} "
                      "and # Inliers 21 = {} and # Inliers 22 = {} and # Inliers 23 = {} and # Inliers 24 = {}".format(
                    inliers_best_11, inliers_best_12, inliers_best_13, inliers_best_14, inliers_best_21,
                    inliers_best_22, inliers_best_23, inliers_best_24))

                inliers = np.array(
                    [inliers_best_11, inliers_best_12, inliers_best_13, inliers_best_14, inliers_best_21,
                     inliers_best_22,
                     inliers_best_23, inliers_best_24])

                if np.argmax(inliers) == 0:
                    self.transform_matrix_sc = transform_matrix_11
                elif np.argmax(inliers) == 1:
                    self.transform_matrix_sc = transform_matrix_12
                elif np.argmax(inliers) == 2:
                    self.transform_matrix_sc = transform_matrix_13
                elif np.argmax(inliers) == 3:
                    self.transform_matrix_sc = transform_matrix_14
                elif np.argmax(inliers) == 4:
                    self.transform_matrix_sc = transform_matrix_21
                elif np.argmax(inliers) == 5:
                    self.transform_matrix_sc = transform_matrix_22
                elif np.argmax(inliers) == 6:
                    self.transform_matrix_sc = transform_matrix_23
                elif np.argmax(inliers) == 7:
                    self.transform_matrix_sc = transform_matrix_24

                print("=" * 25)
                print("4 x 4 Transform matrix estimated from Shape Context Approach is \n", self.transform_matrix_sc)
            elif (self.estimate_transform_combo_box.currentIndex() == 1):  # supervised
                if self.transform_combobox.currentIndex() == 0:  # affine
                    self.transform_matrix_sc = get_affine_transform(self.moving_keypoints, self.fixed_keypoints)
                elif self.transform_combobox.currentIndex() == 1:  # similar
                    self.transform_matrix_sc = get_similar_transform(self.moving_keypoints, self.fixed_keypoints)

            print("=" * 25)
            transformed_moving_detections = apply_affine_transform(moving_detections_copy, self.transform_matrix_sc)
            self.transform_matrix_icp = perform_icp(transformed_moving_detections, fixed_detections_copy,
                                                    int(self.icp_iterations_lineedit.text()),
                                                    self.transform_combobox.currentText())
            print("4 x 4 Finetuning Transform matrix estimated from ICP Approach is \n", self.transform_matrix_icp)
            print("=" * 25)
            print("Estimate Transform Matrix is ready to export. Please click on {} push button \n".format(
                "Export Transform"))
        elif self.pca_checkbox.isChecked():
            self.moving_detections -= moving_centroid  # 3 x N --> z y x
            self.fixed_detections -= fixed_centroid  # 3x N --> z y x
            pca = PCA(n_components=3)
            pca.fit(self.moving_detections.transpose())
            self.moving_transform = pca.components_  # 3 x 3
            pca.fit(self.fixed_detections.transpose())
            self.fixed_transform = pca.components_  # 3 x 3
            print("Aligning Transform Matrix is ready to export. Please click on {} push button \n".format(
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

    def _hide_shape_context(self):
        if self.sender() == self.shape_context_checkbox and self.shape_context_checkbox.isChecked():

            self.ransac_samples_lineedit.show()
            self.ransac_samples_label.show()
            self.ransac_iterations_lineedit.show()
            self.ransac_iterations_label.show()
            self.icp_iterations_lineedit.show()
            self.icp_iterations_label.show()
            self.r_bins_lineedit.show()
            self.r_bins_label.show()
            self.theta_bins_lineedit.show()
            self.theta_bins_label.show()
            self.phi_bins_lineedit.show()
            self.phi_bins_label.show()

            self.pca_checkbox.setChecked(False)
        elif self.sender() == self.shape_context_checkbox and not self.shape_context_checkbox.isChecked():
            self.ransac_samples_lineedit.hide()
            self.ransac_samples_label.hide()
            self.ransac_iterations_lineedit.hide()
            self.ransac_iterations_label.hide()
            self.icp_iterations_lineedit.hide()
            self.icp_iterations_label.hide()
            self.r_bins_lineedit.hide()
            self.r_bins_label.hide()
            self.theta_bins_lineedit.hide()
            self.theta_bins_label.hide()
            self.phi_bins_lineedit.hide()
            self.phi_bins_label.hide()

            self.pca_checkbox.setChecked(True)
        elif self.sender() == self.pca_checkbox and self.pca_checkbox.isChecked():
            self.ransac_samples_lineedit.hide()
            self.ransac_samples_label.hide()
            self.ransac_iterations_lineedit.hide()
            self.ransac_iterations_label.hide()
            self.icp_iterations_lineedit.hide()
            self.icp_iterations_label.hide()
            self.r_bins_lineedit.hide()
            self.r_bins_label.hide()
            self.theta_bins_lineedit.hide()
            self.theta_bins_label.hide()
            self.phi_bins_lineedit.hide()
            self.phi_bins_label.hide()
            self.shape_context_checkbox.setChecked(False)
        elif self.sender() == self.pca_checkbox and not self.pca_checkbox.isChecked():
            self.ransac_samples_lineedit.show()
            self.ransac_samples_label.show()
            self.ransac_iterations_lineedit.show()
            self.ransac_iterations_label.show()
            self.icp_iterations_lineedit.show()
            self.icp_iterations_label.show()
            self.r_bins_lineedit.show()
            self.r_bins_label.show()
            self.theta_bins_lineedit.show()
            self.theta_bins_label.show()
            self.phi_bins_lineedit.show()
            self.phi_bins_label.show()
            self.shape_context_checkbox.setChecked(True)


class EvaluateMetrics(QWidget):
    def __init__(self, napari_viewer):
        super().__init__()
        self.viewer = napari_viewer

        # define components
        logo_path = 'platymatch/resources/platymatch_logo_small.png'
        self.logo_label = QLabel(f'<h1><img src="{logo_path}"></h1>')
        self.logo_label.setAlignment(Qt.AlignCenter)
        self.method_description_label = QLabel(
            '<small>Registration of Multi-modal Volumetric Images by Establishing <br>Cell Correspondence.<br> If you are using this in your research please <a href="https://github.com/juglab/PlatyMatch#citation" style="color:gray;">cite us</a>.</small><br><small><tt><a href="https://github.com/juglab/PlatyMatch" style="color:gray;">https://github.com/juglab/PlatyMatch</a></tt></small>')
        self.method_description_label.setOpenExternalLinks(True)

        # define components
        self.sync_button = QPushButton('Sync with Viewer')
        self.sync_button.setMaximumWidth(280)
        self.sync_button.clicked.connect(self._refresh)
        self.moving_image_label = QLabel('Moving Image:')
        self.moving_image_label.setMaximumWidth(280)
        self.moving_image_combobox = QComboBox(self)
        self.moving_image_combobox.setMaximumWidth(280)
        self.moving_image_anisotropy_label = QLabel('Moving Image Anisotropy [Z]:')
        self.moving_image_anisotropy_label.setMaximumWidth(280)
        self.moving_image_anisotropy_line = QLineEdit('1.0')
        self.moving_image_anisotropy_line.setMaximumWidth(280)
        self.moving_image_anisotropy_line.setAlignment(Qt.AlignCenter)

        self.fixed_image_label = QLabel('Fixed Image:')
        self.fixed_image_label.setMaximumWidth(280)
        self.fixed_image_combobox = QComboBox(self)
        self.fixed_image_combobox.setMaximumWidth(280)
        self.fixed_image_anisotropy_label = QLabel('Fixed Image Anisotropy [Z]:')
        self.fixed_image_anisotropy_label.setMaximumWidth(280)
        self.fixed_image_anisotropy_line = QLineEdit('1.0')
        self.fixed_image_anisotropy_line.setMaximumWidth(280)
        self.fixed_image_anisotropy_line.setAlignment(Qt.AlignCenter)

        self.transform_label = QLabel('Transform:')
        self.transform_label.setMaximumWidth(280)
        self.transform_pushbutton = QPushButton('Load')
        self.transform_pushbutton.setMaximumWidth(280)
        self.transform_pushbutton.clicked.connect(self._load_transform)

        self.csv_checkbox = QCheckBox('csv?')
        self.csv_checkbox.setMaximumWidth(280)
        self.csv_checkbox.clicked.connect(self._open_text_file)
        self.izyx_checkbox = QCheckBox('IZYXR?')
        self.izyx_checkbox.setMaximumWidth(280)
        self.izyx_checkbox.hide()
        self.header_checkbox = QCheckBox('Header?')
        self.header_checkbox.setMaximumWidth(280)
        self.header_checkbox.hide()

        self.moving_image_2_combobox = QComboBox(self)
        self.moving_image_2_combobox.setMaximumWidth(280)
        self.fixed_image_2_combobox = QComboBox(self)
        self.fixed_image_2_combobox.setMaximumWidth(280)

        self.moving_detections_label = QLabel('Moving Detections:')
        self.moving_detections_label.setMaximumWidth(280)
        self.moving_detections_pushbutton = QPushButton('Load')
        self.moving_detections_pushbutton.setMaximumWidth(280)
        self.moving_detections_pushbutton.hide()
        self.moving_detections_pushbutton.clicked.connect(self._browse)

        self.fixed_detections_label = QLabel('Fixed Detections:')
        self.fixed_detections_label.setMaximumWidth(280)
        self.fixed_detections_pushbutton = QPushButton('Load')
        self.fixed_detections_pushbutton.setMaximumWidth(280)
        self.fixed_detections_pushbutton.hide()
        self.fixed_detections_pushbutton.clicked.connect(self._browse)

        self.moving_progress_bar = QProgressBar(self)
        self.fixed_progress_bar = QProgressBar(self)

        self.moving_keypoints_label = QLabel('Moving Keypoints:')
        self.moving_keypoints_label.setMaximumWidth(280)
        self.moving_keypoints_pushbutton = QPushButton('Load')
        self.moving_keypoints_pushbutton.setMaximumWidth(280)
        self.moving_keypoints_pushbutton.clicked.connect(self._browse)

        self.fixed_keypoints_label = QLabel('Fixed Keypoints:')
        self.fixed_keypoints_label.setMaximumWidth(280)
        self.fixed_keypoints_pushbutton = QPushButton('Load')
        self.fixed_keypoints_pushbutton.setMaximumWidth(280)
        self.fixed_keypoints_pushbutton.clicked.connect(self._browse)

        self.run_button = QPushButton('Evaluate Metrics')
        self.run_button.setMaximumWidth(280)
        self.run_button.clicked.connect(self._calculate_metrics)
        self.export_transformed_image_button = QPushButton('Export Transformed Image')
        self.export_transformed_image_button.setMaximumWidth(280)
        self.export_transformed_image_button.clicked.connect(self._export_transformed_image)

        self.matching_accuracy_label = QLabel('Matching Accuracy:')
        self.matching_accuracy_label.setMaximumWidth(280)
        self.matching_accuracy_linedit = QLineEdit('')
        self.matching_accuracy_linedit.setMaximumWidth(280)

        self.avg_registration_error_label = QLabel('Average Registration Error:')
        self.avg_registration_error_label.setMaximumWidth(280)
        self.avg_registration_error_lineedit = QLineEdit('')
        self.avg_registration_error_lineedit.setMaximumWidth(280)

        self.hausdorff_distance_label = QLabel('Hausdorff Distance:')
        self.hausdorff_distance_label.setMaximumWidth(280)
        self.hausdorff_distance_lineedit = QLineEdit('')
        self.hausdorff_distance_lineedit.setMaximumWidth(280)

        self.iou_mask_label = QLabel('IOU Mask:')
        self.iou_mask_label.setMaximumWidth(280)
        self.iou_mask_lineedit = QLineEdit('')
        self.iou_mask_lineedit.setMaximumWidth(280)

        # define layout
        layout = QVBoxLayout()

        grid_0 = QGridLayout()
        grid_0.addWidget(self.logo_label, 0, 0, 1, 1)
        grid_0.addWidget(self.method_description_label, 0, 1, 1, 1)
        grid_0.setSpacing(10)
        layout.addLayout(grid_0)

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

        grid_1.addWidget(self.export_transformed_image_button, 6, 1)

        grid_1.addWidget(self.csv_checkbox, 7, 0)
        grid_1.addWidget(self.izyx_checkbox, 7, 1)
        grid_1.addWidget(self.header_checkbox, 8, 1)

        grid_1.addWidget(self.moving_detections_label, 9, 0)
        grid_1.addWidget(self.moving_image_2_combobox, 9, 1)
        grid_1.addWidget(self.moving_detections_pushbutton, 9, 1)

        grid_1.addWidget(self.moving_progress_bar, 10, 0, 1, 2)

        grid_1.addWidget(self.fixed_detections_label, 11, 0)
        grid_1.addWidget(self.fixed_image_2_combobox, 11, 1)
        grid_1.addWidget(self.fixed_detections_pushbutton, 11, 1)
        grid_1.addWidget(self.fixed_progress_bar, 12, 0, 1, 2)

        grid_1.addWidget(self.moving_keypoints_label, 13, 0)
        grid_1.addWidget(self.moving_keypoints_pushbutton, 13, 1)

        grid_1.addWidget(self.fixed_keypoints_label, 14, 0)
        grid_1.addWidget(self.fixed_keypoints_pushbutton, 14, 1)
        grid_1.addWidget(self.run_button, 15, 0)
        grid_1.addWidget(self.matching_accuracy_label, 16, 0)
        grid_1.addWidget(self.matching_accuracy_linedit, 16, 1)
        grid_1.addWidget(self.avg_registration_error_label, 17, 0)
        grid_1.addWidget(self.avg_registration_error_lineedit, 17, 1)
        grid_1.addWidget(self.hausdorff_distance_label, 18, 0)
        grid_1.addWidget(self.hausdorff_distance_lineedit, 18, 1)
        grid_1.addWidget(self.iou_mask_label, 19, 0)
        grid_1.addWidget(self.iou_mask_lineedit, 19, 1)

        grid_1.setSpacing(10)
        layout.addLayout(grid_1)

        self.setLayout(layout)
        self.setFixedWidth(560)
        self.transform_matrix_2 = np.identity(4)  # just a placeholder, if a second matrix exists, this is replaced !!

    def _browse(self):

        if self.sender() == self.moving_detections_pushbutton:
            self.moving_detections, self.moving_ids = _browse_detections(self)
        elif self.sender() == self.fixed_detections_pushbutton:
            self.fixed_detections, self.fixed_ids = _browse_detections(self)
        elif self.sender() == self.moving_keypoints_pushbutton:
            self.moving_keypoints, self.moving_keypoint_ids = _browse_detections(self)
        elif self.sender() == self.fixed_keypoints_pushbutton:
            self.fixed_keypoints, self.fixed_keypoint_ids = _browse_detections(self)

    def _load_transform(self):
        if self.sender() == self.transform_pushbutton:
            self.transform_matrix_1 = _browse_transform(self)
        elif self.sender() == self.transform_pushbutton_2:
            self.transform_matrix_2 = _browse_transform(self)
        self.transform_matrix_combined = np.matmul(self.transform_matrix_2, self.transform_matrix_1)

    def _calculate_metrics(self):
        # first associate keypoints with detections
        moving_kp_detection_cost_matrix = cdist(self.moving_keypoints.transpose(), self.moving_detections.transpose())
        moving_row_indices, moving_col_indices = linear_sum_assignment(moving_kp_detection_cost_matrix)
        moving_dictionary = {}
        for index in moving_row_indices:
            moving_dictionary[self.moving_keypoint_ids[index]] = self.moving_ids[moving_col_indices[index]]

        fixed_kp_detection_cost_matrix = cdist(self.fixed_keypoints.transpose(), self.fixed_detections.transpose())
        fixed_row_indices, fixed_col_indices = linear_sum_assignment(fixed_kp_detection_cost_matrix)
        fixed_dictionary = {}
        for index in fixed_row_indices:
            fixed_dictionary[self.fixed_keypoint_ids[index]] = self.fixed_ids[fixed_col_indices[index]]

        # next apply affine transform onto moving detections
        transformed_moving_detections = apply_affine_transform(self.moving_detections, self.transform_matrix_1)  # 3 x N
        transformed_moving_detections = apply_affine_transform(transformed_moving_detections,
                                                               self.transform_matrix_2)  # 3 x N

        # then apply linear sum assignment between transformed moving detections and fixed detections
        cost_matrix = cdist(transformed_moving_detections.transpose(), self.fixed_detections.transpose())
        row_indices, col_indices = linear_sum_assignment(cost_matrix)

        # these are actual ids
        row_ids = self.moving_ids[row_indices]
        col_ids = self.fixed_ids[col_indices]

        # evaluate accuracy
        hits = 0
        for key in moving_dictionary.keys():
            if (key in fixed_dictionary.keys()):
                if col_ids[np.where(row_ids == moving_dictionary[key])] == fixed_dictionary[key]:
                    hits += 1
        print("=" * 25)

        # self.matching_accuracy_linedit.setText("{:.3f}".format(hits / len(moving_dictionary.keys()))) # TODO
        # TODO --> should this be made symmetric?
        self.matching_accuracy_linedit.setText("{:.3f}".format(hits / len(fixed_dictionary.keys())))  # TODO
        print("Matching Accuracy is equal to {}".format(self.matching_accuracy_linedit.text()))

        # evaluate avg. registration error
        transformed_moving_keypoints = apply_affine_transform(self.moving_keypoints, self.transform_matrix_combined)
        distance = 0
        for i in range(transformed_moving_keypoints.shape[1]):
            distance += np.linalg.norm([self.fixed_keypoints.transpose()[
                                        np.where(self.fixed_keypoint_ids == self.moving_keypoint_ids[i]),
                                        :] - transformed_moving_keypoints.transpose()[i, :]])

        print("=" * 25)
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
        print("=" * 25)
        print("Saving Transformed Image at {}".format(save_file_name[0]))
        affine = sitk.AffineTransform(3)
        transform_xyz = np.zeros_like(self.transform_matrix_combined)  # 4 x 4
        # flipping the matrix
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

        transform_xyz[0, 2] *= float(self.moving_image_anisotropy_line.text())
        transform_xyz[1, 2] *= float(self.moving_image_anisotropy_line.text())


        inv_matrix = np.linalg.inv(transform_xyz)
        affine = self._affine_translate(affine, inv_matrix[0, 3], inv_matrix[1, 3], inv_matrix[2, 3])
        affine = self._affine_rotate(affine, inv_matrix[:3, :3])
        reference_image = sitk.GetImageFromArray(self.viewer.layers[self.fixed_image_combobox.currentIndex()].data)
        moving_image = sitk.GetImageFromArray(self.viewer.layers[self.moving_image_combobox.currentIndex()].data)
        interpolator = sitk.sitkNearestNeighbor
        default_value = 0.0
        transformed_image = sitk.GetArrayFromImage(
            sitk.Resample(moving_image, reference_image, affine, interpolator, default_value))
        print("=" * 25)
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


# class NonLinearTransform(QWidget):
#     # Non Rigid Transform (Simple ITK)
#     # Voxel Morph
#     def __init__(self, napari_viewer):
#         pass


@napari_hook_implementation
def napari_experimental_provide_dock_widget():
    return [DetectNuclei, EstimateTransform, EvaluateMetrics]
    #return [DetectNuclei, EstimateTransform, EvaluateMetrics, NonLinearTransform]
