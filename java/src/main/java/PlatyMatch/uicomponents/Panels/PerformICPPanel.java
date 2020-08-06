package PlatyMatch.uicomponents.Panels;

import Jama.Matrix;
import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.RichSpot;
import net.imagej.ops.OpService;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PerformICPPanel<T extends NumericType<T>> extends JPanel {


  private JLabel transform1Label;
  private JTextField transform1TextField;
  private JButton browseTransform1Button;

  private JLabel transform2Label;
  private JTextField transform2TextField;
  private JButton browseTransform2Button;

  private JLabel iterationsLabel;
  private JTextField iterationsTextField;
  private JButton runICPButton;
  private JButton saveResultsButton;

  private EventService es;
  private CommandService cs;
  private ThreadService ts;
  private OpService ops;
  private List<EventSubscriber<?>> subs;

  private double[][] transformMatrix1;
  private double[][] transformMatrix2;


  private final JFileChooser fc = new JFileChooser("/home/manan/ownCloud/");

  public PerformICPPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {

    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);

    transform1Label = new JLabel("Transform 1");
    transform1TextField = new JTextField("", 20);
    browseTransform1Button = new JButton("Browse");
    transform2Label = new JLabel("Transform 2 (Optional) ");
    transform2TextField = new JTextField("", 20);
    browseTransform2Button = new JButton("Browse");
    iterationsLabel = new JLabel("# Iterations");
    iterationsTextField = new JTextField("50", 5);
    runICPButton = new JButton("Run ICP + Hungarian Assignment");
    saveResultsButton = new JButton("Save Results");
    setupPanel();
    setupBrowseTransform1Button(bdvUI);
    setupBrowseTransform2Button(bdvUI);
    setupRunICPButton(bdvUI);
    setupSaveResultsButton(bdvUI);

    // TODO: initialize as identity in case of new user input
    transformMatrix2 = new double[4][4];
    transformMatrix2[0][0] = 1.0;
    transformMatrix2[1][1] = 1.0;
    transformMatrix2[2][2] = 1.0;
    transformMatrix2[3][3] = 1.0;


    this.add(transform1TextField);
    this.add(transform1Label, "wrap");
    this.add(browseTransform1Button, "wrap");

    this.add(transform2TextField);
    this.add(transform2Label, "wrap");
    this.add(browseTransform2Button, "wrap");

    this.add(iterationsLabel);
    this.add(iterationsTextField, "wrap");
    this.add(runICPButton, "wrap");
    this.add(saveResultsButton);

  }

  private void setupPanel() {
    this.setBackground(Color.white);

    this.setLayout(new MigLayout("fillx", "", ""));
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


  private void setupBrowseTransform1Button(BigDataViewerUI bdvUI) {
    browseTransform1Button.setBackground(Color.WHITE);
    browseTransform1Button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseTransform1Button) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            transform1TextField.setText(file.getAbsolutePath());
            transformMatrix1 = readAffineMatrixCSV(file.getAbsolutePath());

          }
        }

      }
    });
  }

  private void setupBrowseTransform2Button(BigDataViewerUI bdvUI) {
    browseTransform2Button.setBackground(Color.WHITE);
    browseTransform2Button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseTransform2Button) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            transform2TextField.setText(file.getAbsolutePath());
            transformMatrix2 = readAffineMatrixCSV(file.getAbsolutePath());

          }
        }

      }
    });
  }

  private double[][] list2array(List<RichSpot> spots) {
    double[][] spotsArray = new double[spots.size()][3];
    for (int i = 0; i < spots.size(); i++) {
      spotsArray[i][0] = spots.get(i).getX();
      spotsArray[i][1] = spots.get(i).getY();
      spotsArray[i][2] = spots.get(i).getZ();
    }
    return spotsArray;
  }

  private List<RichSpot> applyAffineTransform(RealMatrix A, List<RichSpot> spots) {
    List<RichSpot> transformedSpots = new ArrayList<>();
    for (int i = 0; i < spots.size(); i++) {
      double x_ = A.getEntry(0, 0) * spots.get(i).getX() + A.getEntry(0, 1) * spots.get(i).getY() + A.getEntry(0, 2) * spots.get(i).getZ() + A.getEntry(0, 3);
      double y_ = A.getEntry(1, 0) * spots.get(i).getX() + A.getEntry(1, 1) * spots.get(i).getY() + A.getEntry(1, 2) * spots.get(i).getZ() + A.getEntry(1, 3);
      double z_ = A.getEntry(2, 0) * spots.get(i).getX() + A.getEntry(2, 1) * spots.get(i).getY() + A.getEntry(2, 2) * spots.get(i).getZ() + A.getEntry(2, 3);
      transformedSpots.add(new RichSpot(spots.get(i).getLabel(), x_, y_, z_));
    }
    return transformedSpots;
  }

  private float getEuclideanDistance(RichSpot source, RichSpot target) {
    return (float) Math.sqrt(Math.pow(source.getX() - target.getX(), 2) + Math.pow(source.getY() - target.getY(), 2) + Math.pow(source.getZ() - target.getZ(), 2));

  }

  private List<RichSpot> getCorrespondingNuclei(List<RichSpot> spotsSource, List<RichSpot> spotsTarget) {
    List<RichSpot> spotsTargetCorr = new ArrayList<>();

    float dmin;
    float dtemp;
    int jmin = 0;
    for (int i = 0; i < spotsSource.size(); i++) {
      dmin = 1000;
      for (int j = 0; j < spotsTarget.size(); j++) {
        dtemp = getEuclideanDistance(spotsSource.get(i), spotsTarget.get(j));
        if (dtemp < dmin) {
          dmin = dtemp;
          jmin = j;
        }

      }
      spotsTargetCorr.add(spotsTarget.get(jmin));
    }
    return spotsTargetCorr;
  }

  private void setupRunICPButton(BigDataViewerUI bdvUI) {
    runICPButton.setBackground(Color.WHITE);
    runICPButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {

        List<RichSpot> spotsSource = (List<RichSpot>) bdvUI.getSourceDetections();
        List<RichSpot> spotsTarget = (List<RichSpot>) bdvUI.getTargetDetections();

        List<RichSpot> spotsSourceTemp = applyAffineTransform(MatrixUtils.createRealMatrix(transformMatrix1), spotsSource);
        spotsSourceTemp = applyAffineTransform(MatrixUtils.createRealMatrix(transformMatrix2), spotsSourceTemp);

        double[][] AtempArray = new double[4][4];
        AtempArray[0][0] = 1.0;
        AtempArray[1][1] = 1.0;
        AtempArray[2][2] = 1.0;
        AtempArray[3][3] = 1.0;
        RealMatrix Atemp = MatrixUtils.createRealMatrix(AtempArray);
        RealMatrix A;
        for (int i = 0; i < Integer.parseInt(iterationsTextField.getText()); i++) {
          List<RichSpot> spotsTargetCorr = getCorrespondingNuclei(spotsSourceTemp, spotsTarget);
          RealMatrix sourceSquare = addOneColumn(spotsSourceTemp);
          RealMatrix targetSquare = addOneColumn(spotsTargetCorr);

          Matrix a = new Matrix(sourceSquare.getData());
          Matrix b = new Matrix(targetSquare.getData());
          Matrix x = a.solve(b);
          A = MatrixUtils.createRealMatrix(x.transpose().getArray());
          // TODO is the above jama evaluation correct>?

          RealMatrix predictedAllSquare = A.multiply(sourceSquare.transpose());
          Atemp = A.multiply(Atemp);
          spotsSourceTemp = removeOneColumn(predictedAllSquare.transpose(), spotsSourceTemp);
        }

        RealMatrix Anet = Atemp.multiply(MatrixUtils.createRealMatrix(transformMatrix2)).multiply(MatrixUtils.createRealMatrix(transformMatrix1));
        List<RichSpot> landmarksSource = (List<RichSpot>) bdvUI.getSourceLandmarks();
        List<RichSpot> landmarksTarget = (List<RichSpot>) bdvUI.getTargetLandmarks();
        double[][] landmarkSourceTransform = (double[][]) bdvUI.getSourceLandmarkTransform();
        double[][] landmarkTargetTransform = (double[][]) bdvUI.getTargetLandmarkTransform();

        RealMatrix affineMatrixSourceInverse = MatrixUtils.inverse(MatrixUtils.createRealMatrix(landmarkSourceTransform));
        RealMatrix affineMatrixTargetInverse = MatrixUtils.inverse(MatrixUtils.createRealMatrix(landmarkTargetTransform));
        List<RichSpot> transformedLandmarksSource = applyAffineTransform(Anet.multiply(affineMatrixSourceInverse), landmarksSource);
        List<RichSpot> correctedLandmarksTarget = applyAffineTransform(affineMatrixTargetInverse, landmarksTarget);
        computeErrorLandmarks(transformedLandmarksSource, correctedLandmarksTarget);

      }
    });
  }

  private void computeErrorLandmarks(List<RichSpot> transformedLandmarksSource, List<RichSpot> correctedLandmarksTarget) {
    double d = 0;
    for (int i = 0; i < transformedLandmarksSource.size(); i++) {
      double dis = getEuclideanDistance(transformedLandmarksSource.get(i), correctedLandmarksTarget.get(i));
      System.out.println(i + " : " + dis);
      d = d + dis;
    }
    System.out.println("Final Error:" + d / 12.0);
  }

  private List<RichSpot> removeOneColumn(RealMatrix neighbors, List<RichSpot> spotsSource) {
    List<RichSpot> spotsSourceTemp = new ArrayList<RichSpot>();
    for (int i = 0; i < neighbors.getRowDimension(); i++) {
      spotsSourceTemp.add(new RichSpot(spotsSource.get(i).getLabel(), neighbors.getEntry(i, 0), neighbors.getEntry(i, 1), neighbors.getEntry(i, 2)));
    }

    return spotsSourceTemp;

  }

  private RealMatrix addOneColumn(List<RichSpot> neighbors) {
    double[][] neighborsArray = new double[neighbors.size()][4];
    for (int i = 0; i < neighbors.size(); i++) {
      neighborsArray[i][0] = neighbors.get(i).getX();
      neighborsArray[i][1] = neighbors.get(i).getY();
      neighborsArray[i][2] = neighbors.get(i).getZ();
      neighborsArray[i][3] = 1.0;
    }
    return MatrixUtils.createRealMatrix(neighborsArray);
  }


  private void setupSaveResultsButton(BigDataViewerUI bdvUI) {
    saveResultsButton.setBackground(Color.WHITE);
  }
}
