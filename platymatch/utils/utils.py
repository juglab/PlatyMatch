import numpy as np
import pandas as pd
from qtpy.QtWidgets import QFileDialog

def _visualize_nuclei(self, nuclei):
    for layer in self.viewer.layers:
        if layer.name == 'points-' + self.viewer.layers[self.images_combo_box.currentIndex()].name:
            self.viewer.layers.remove('points-' + self.viewer.layers[self.images_combo_box.currentIndex()].name)

    point_properties = {
        'color': nuclei[:, 1] + nuclei[:, 2] + nuclei[:, 3],  # use z coordinate as placeholder for color!
        'radius': np.sqrt(3)*nuclei[:, 0]
    }

    self.viewer.add_points(nuclei[:, 1:], size=np.sqrt(3) * 2 * nuclei[:, 0], properties=point_properties,
                           face_color='color', face_colormap='viridis', opacity=0.5,
                           name="points-" + self.viewer.layers[self.images_combo_box.currentIndex()].name)

def _browse_detections(self):
    name = QFileDialog.getOpenFileName(self, 'Open File')  # this returns a tuple!
    print("Opening detections {} ******".format(name[0]))
    if self.header_checkbox.isChecked():
        detections_df = pd.read_csv(name[0], skiprows=[0], delimiter=' ', header=None)
    else:
        detections_df = pd.read_csv(name[0], skiprows=None, delimiter=' ', header=None)
    detections_numpy = detections_df.to_numpy()
    ids = detections_numpy[:, 0]  # first column should contain ids
    detections = detections_numpy[:, 1:4] # N x 3
    if self.izyx_checkbox.isChecked():
        pass
    else:
        detections = np.flip(detections, 1) # N x 3

    return detections.transpose(), ids.transpose() # 3 x N, 1 x N


def _browse_transform(self):
    name = QFileDialog.getOpenFileName(self, 'Open File')  # this returns a tuple!
    print("Opening transform {} ******".format(name[0]))
    transform_df = pd.read_csv(name[0], skiprows=None, delimiter=' ', header= None)
    transform_numpy = transform_df.to_numpy()
    assert (transform_numpy.shape==(4,4)), 'Loaded transform does not hasve shape 4 x 4'
    return transform_numpy # 4 x 4




def get_centroid(detections, transposed = True):
    """
    :param detections: N x 3/4 or 3/4 x N
    :return: 3 x 1
    """
    if (transposed):  # N x 4
        return np.mean(detections[:, :3], 0, keepdims=True)
    else: # 4 x N
        return np.mean(detections[:3, :], 1, keepdims=True)

def get_mean_distance(detections, transposed = False):
    """
    :param detections: 3 x N
    :return:
    """

    if (transposed): # 3 x N
        pass
    else: # N x 3
        detections = detections.transpose() # N x 3

    if detections.shape[1] == 4:
        detections = detections[:, :3] # N x 3
    pair_wise_distance = []
    for i in range(detections.shape[0]):
        for j in range(i + 1, detections.shape[0]):
            pair_wise_distance.append(np.linalg.norm(detections[i] - detections[j]))
    return np.average(pair_wise_distance)

def get_error(moving_landmarks, fixed_landmarks):
    """

    :param moving_landmarks: 3 x N
    :param fixed_landmarks: 3 x N
    :return:
    """
    if (moving_landmarks is not None or fixed_landmarks is not None):
        residual = moving_landmarks - fixed_landmarks
        return np.mean(np.linalg.norm(residual, axis=0)) # axis= 0 means norm is taken along axis = 0
    else:
        return None