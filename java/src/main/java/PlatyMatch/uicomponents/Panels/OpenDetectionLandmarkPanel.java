package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.RichSpot;
import net.imagej.ops.OpService;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class OpenDetectionLandmarkPanel<T extends NumericType<T>> extends JPanel {


  private JLabel sourceDetectionsLabel;
  private JTextField sourceDetectionsTextField;
  private JButton browseSourceDetections;

  private JLabel sourceLandmarksLabel;
  private JTextField sourceLandmarksTextField;
  private JButton browseSourceLandmarks;

  private JLabel sourceLandmarksTransformLabel;
  private JTextField sourceLandmarksTransformTextField;
  private JButton browseSourceLandmarksTransform;


  private JLabel targetDetectionsLabel;
  private JTextField targetDetectionsTextField;
  private JButton browseTargetDetections;

  private JLabel targetLandmarksLabel;
  private JTextField targetLandmarksTextField;
  private JButton browseTargetLandmarks;

  private JLabel targetLandmarksTransformLabel;
  private JTextField targetLandmarksTransformTextField;
  private JButton browseTargetLandmarksTransform;

  private EventService es;
  private CommandService cs;
  private ThreadService ts;
  private OpService ops;
  private List<EventSubscriber<?>> subs;


  /*File chooser*/
  private final JFileChooser fc = new JFileChooser("/home/manan/ownCloud/");

  public OpenDetectionLandmarkPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);


    sourceDetectionsLabel = new JLabel("Source Detections");
    sourceDetectionsTextField = new JTextField("", 20);
    browseSourceDetections = new JButton("Browse");

    sourceLandmarksLabel = new JLabel("Source Landmarks");
    sourceLandmarksTextField = new JTextField("", 20);
    browseSourceLandmarks = new JButton("Browse");

    sourceLandmarksTransformLabel = new JLabel("Source Landmarks Transform");
    sourceLandmarksTransformTextField = new JTextField("", 20);
    browseSourceLandmarksTransform = new JButton("Browse");

    targetDetectionsLabel = new JLabel("Target Detections");
    targetDetectionsTextField = new JTextField("", 20);
    browseTargetDetections = new JButton("Browse");

    targetLandmarksLabel = new JLabel("Target Landmarks");
    targetLandmarksTextField = new JTextField("", 20);
    browseTargetLandmarks = new JButton("Browse");

    targetLandmarksTransformLabel = new JLabel("Target Landmarks Transform");
    targetLandmarksTransformTextField = new JTextField("", 20);
    browseTargetLandmarksTransform = new JButton("Browse");

    setupPanel();
    setupBrowseSourceDetections(bdvUI);
    setupBrowseSourceLandmarks(bdvUI);
    setupBrowseSourceTransform(bdvUI);

    setupBrowseTargetDetections(bdvUI);
    setupBrowseTargetLandmarks(bdvUI);
    setupBrowseTargetTransform(bdvUI);

    this.add(sourceDetectionsTextField);
    this.add(sourceDetectionsLabel, "wrap");
    this.add(browseSourceDetections, "wrap");

    this.add(sourceLandmarksTextField);
    this.add(sourceLandmarksLabel, "wrap");
    this.add(browseSourceLandmarks, "wrap");

    this.add(sourceLandmarksTransformTextField);
    this.add(sourceLandmarksTransformLabel, "wrap");
    this.add(browseSourceLandmarksTransform, "wrap");

    this.add(new JSeparator(), "growx, spanx, wrap");


    this.add(targetDetectionsTextField);
    this.add(targetDetectionsLabel, "wrap");
    this.add(browseTargetDetections, "wrap");

    this.add(targetLandmarksTextField);
    this.add(targetLandmarksLabel, "wrap");
    this.add(browseTargetLandmarks, "wrap");

    this.add(targetLandmarksTransformTextField);
    this.add(targetLandmarksTransformLabel, "wrap");
    this.add(browseTargetLandmarksTransform, "wrap");

  }

  private List<RichSpot> readSpots(String filename) {

    List<RichSpot> spots = new ArrayList<>();
    String COMMA_DELIMITER = " ";
    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(filename))) {
      String line;
      while ((line = bufferedReader.readLine()) != null) {

        String[] values = line.split(COMMA_DELIMITER);
        spots.add(new RichSpot(values[0], Double.parseDouble(values[1]), Double.parseDouble(values[2]), Double.parseDouble(values[3])));
      }

    } catch (FileNotFoundException e) {
      System.out.println("File not found!");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("File read problem/IO!");
      e.printStackTrace();
    }
    return spots;
  }


  private void setupBrowseSourceDetections(BigDataViewerUI bdvUI) {
    browseSourceDetections.setBackground(Color.WHITE);
    browseSourceDetections.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseSourceDetections) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            sourceDetectionsTextField.setText(file.getAbsolutePath());
            bdvUI.setSourceDetections(readSpots(file.getAbsolutePath())); //TODO

          }
        }
      }

    });
  }

  private void setupBrowseSourceLandmarks(BigDataViewerUI bdvUI) {
    browseSourceLandmarks.setBackground(Color.WHITE);
    browseSourceLandmarks.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseSourceLandmarks) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            sourceLandmarksTextField.setText(file.getAbsolutePath());
            bdvUI.setSourceLandmarks(readSpots(file.getAbsolutePath()));

          }
        }
      }
    });
  }

  private void setupBrowseSourceTransform(BigDataViewerUI bdvUI) {
    browseSourceLandmarksTransform.setBackground(Color.WHITE);
    browseSourceLandmarksTransform.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseSourceLandmarksTransform) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            sourceLandmarksTransformTextField.setText(file.getAbsolutePath());
            bdvUI.setSourceLandmarkTransform(readAffineMatrixCSV(file.getAbsolutePath()));

          }
        }

      }
    });
  }

  private double[][] readAffineMatrixCSV(String filename) {
    try {

      double[][] affineMatrixArray = new double[4][4];
      BufferedReader reader = new BufferedReader(new FileReader(filename));
      String l = reader.readLine();
      int row = 0;
      while (l != null) {
        if (l.contains("#")) {
          l = reader.readLine();
        } else {
          String[] tokens = l.split(" ");
          affineMatrixArray[row][0] = Float.parseFloat(tokens[0]);
          affineMatrixArray[row][1] = Float.parseFloat(tokens[1]);
          affineMatrixArray[row][2] = Float.parseFloat(tokens[2]);
          affineMatrixArray[row][3] = Float.parseFloat(tokens[3]);
          l = reader.readLine();
          row++;
        }


      }
      reader.close();
      return affineMatrixArray;

    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }


  private void setupBrowseTargetDetections(BigDataViewerUI bdvUI) {
    browseTargetDetections.setBackground(Color.WHITE);
    browseTargetDetections.setBackground(Color.WHITE);
    browseTargetDetections.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseTargetDetections) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            targetDetectionsTextField.setText(file.getAbsolutePath());
            bdvUI.setTargetDetections(readSpots(file.getAbsolutePath())); //TODO

          }
        }
      }

    });
  }


  private void setupBrowseTargetLandmarks(BigDataViewerUI bdvUI) {
    browseTargetLandmarks.setBackground(Color.WHITE);
    browseTargetLandmarks.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseTargetLandmarks) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            targetLandmarksTextField.setText(file.getAbsolutePath());

            bdvUI.setTargetLandmarks(readSpots(file.getAbsolutePath()));

          }
        }
      }
    });
  }

  private void setupBrowseTargetTransform(BigDataViewerUI bdvUI) {
    browseTargetLandmarksTransform.setBackground(Color.WHITE);
    browseTargetLandmarksTransform.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseTargetLandmarksTransform) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            targetLandmarksTransformTextField.setText(file.getAbsolutePath());
            bdvUI.setTargetLandmarkTransform(readAffineMatrixCSV(file.getAbsolutePath()));

          }
        }

      }
    });
  }

  private void setupPanel() {
    this.setBackground(Color.white);

    this.setLayout(new MigLayout("fillx", "", ""));
  }
}
