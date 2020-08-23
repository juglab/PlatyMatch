package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.RichSpot;
import PlatyMatch.nucleiMatching.HungarianAlgorithm;
import net.imagej.ops.OpService;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PerformShapeContextPanel<T extends NumericType<T>> extends JPanel {
  private JLabel radialBinLabel;
  private JTextField radialBinTextField;
  private JLabel thetaBinLabel;
  private JTextField thetaBinTextField;
  private JLabel phiBinLabel;
  private JTextField phiBinTextField;
  private JLabel ransacLabel;
  private JTextField ransacTextField;
  private JLabel minSamplesLabel;
  private JTextField minSamplesTextField;
  private JLabel thresholdLabel;
  private JTextField thresholdTextField;
  private JButton runButton;
  private JButton saveTransformButton;
  private RealMatrix affineMatrix1;
  private RealMatrix affineMatrix2;
  private final JFileChooser fc = new JFileChooser("/home/manan/Desktop/");
  private EventService es;
  private CommandService cs;
  private ThreadService ts;
  private OpService ops;
  private List<EventSubscriber<?>> subs;
  private int inliers1;
  private int inliers2;

  public PerformShapeContextPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {

    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);

    radialBinLabel = new JLabel("# Radial Bins");
    radialBinTextField = new JTextField("5", 5);
    thetaBinLabel = new JLabel("# theta Bins");
    thetaBinTextField = new JTextField("6", 5);
    phiBinLabel = new JLabel("# phi Bins");
    phiBinTextField = new JTextField("12", 5);
    ransacLabel = new JLabel("# Iterations");
    ransacTextField = new JTextField("20000", 5);
    minSamplesLabel = new JLabel("Min Samples");
    minSamplesTextField = new JTextField("4", 5);
    thresholdLabel = new JLabel("Threshold");
    thresholdTextField = new JTextField("15", 5);
    runButton = new JButton("Run Shape Context");
    saveTransformButton = new JButton("Save AffineTransform Matrix");
    setupRunButton(bdvUI);
    setupSaveTransformButton(bdvUI);
    setupPanel();
    this.add(radialBinLabel);
    this.add(radialBinTextField, "wrap");
    this.add(thetaBinLabel);
    this.add(thetaBinTextField, "wrap");
    this.add(phiBinLabel);
    this.add(phiBinTextField, "wrap");
    this.add(new JSeparator(), "growx, spanx, wrap");
    this.add(ransacLabel);
    this.add(ransacTextField, "wrap");
    this.add(minSamplesLabel);
    this.add(minSamplesTextField, "wrap");
    this.add(thresholdLabel);
    this.add(thresholdTextField, "wrap");
    this.add(runButton, "wrap");
    this.add(saveTransformButton, "wrap");


  }

  private void setupSaveTransformButton(BigDataViewerUI bdvUI) {
    saveTransformButton.setBackground(Color.WHITE);
    saveTransformButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {

        fc.setDialogTitle("Choose Files");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int returnVal = fc.showSaveDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fc.getSelectedFile();
          if (inliers1 > inliers2) {
            writeAffineTransformToCSV(file.getAbsolutePath(), affineMatrix1);
          } else {
            writeAffineTransformToCSV(file.getAbsolutePath(), affineMatrix2);
          }
        }
      }
    });

  }


  private void writeAffineTransformToCSV(String path, RealMatrix affineTransform) {
    final String COMMA_DELIMITER = " ";
    final String NEW_LINE_SEPARATOR = "\n";
    FileWriter fileWriter = null;
    final String fileName = path;


    try {
      fileWriter = new FileWriter(fileName);
      for (int i = 0; i < affineTransform.getRowDimension(); i++) {
        for (int j = 0; j < affineTransform.getColumnDimension(); j++) {
          fileWriter.append(String.valueOf(affineTransform.getEntry(i, j)));
          fileWriter.append(COMMA_DELIMITER);

        }
        fileWriter.append(NEW_LINE_SEPARATOR);

      }

    } catch (Exception e) {
      System.out.println("Error in CSVFileWriter !!!");
      e.printStackTrace();
    } finally {

      try {
        fileWriter.flush();
        fileWriter.close();
      } catch (IOException e) {
        System.out.println("Error while flushing/closing fileWriter !!!");
        e.printStackTrace();
      }
    }
  }


  private void setupRunButton(BigDataViewerUI bdvUI) {
    runButton.setBackground(Color.WHITE);
    runButton.addActionListener(e -> {
      if (e.getSource() == runButton) {
        List<RichSpot> spotsSource = (List<RichSpot>) bdvUI.getSourceDetections();
        List<RichSpot> spotsTarget = (List<RichSpot>) bdvUI.getTargetDetections();
        RichSpot comTarget = getCOM(spotsTarget);
        RichSpot comSource = getCOM(spotsSource);
        double meanDistTarget = getMeanDistance(spotsTarget);
        double meanDistSource = getMeanDistance(spotsSource);
        double[][] spotsTargetArray = list2array(spotsTarget);
        double[][] spotsSourceArray = list2array(spotsSource);
        RealMatrix targetMatrix = MatrixUtils.createRealMatrix(spotsTargetArray);
        RealMatrix sourceMatrix = MatrixUtils.createRealMatrix(spotsSourceArray);


        RichSpot xAxisTarget = getXAxis1(targetMatrix); // TODO: it could be negative
        RichSpot xAxisSource = getXAxis1(sourceMatrix); // TODO : it could be negative

        List<Integer[]> unaryTarget = getShapeContext(spotsTarget, comTarget, meanDistTarget, xAxisTarget);
        List<Integer[]> unarySource = getShapeContext(spotsSource, comSource, meanDistSource, xAxisSource);
        double[][] unaryCost = getUnaryDistance(unaryTarget, unarySource);
        HungarianAlgorithm ha = new HungarianAlgorithm(unaryCost);
        int[] assignment = ha.execute();
        List<List<RichSpot>> spotsCorr = getNuclei(assignment, spotsTarget, spotsSource);
        List<RealMatrix> Results = applyRANSAC(spotsCorr, 1);
        affineMatrix1 = Results.get(0);

        xAxisTarget = getXAxis2(targetMatrix); // TODO: it could be negative
        xAxisSource = getXAxis1(sourceMatrix);
        unaryTarget = getShapeContext(spotsTarget, comTarget, meanDistTarget, xAxisTarget);
        unarySource = getShapeContext(spotsSource, comSource, meanDistSource, xAxisSource);
        unaryCost = getUnaryDistance(unaryTarget, unarySource);
        ha = new HungarianAlgorithm(unaryCost);
        assignment = ha.execute();
        spotsCorr = getNuclei(assignment, spotsTarget, spotsSource);
        Results = applyRANSAC(spotsCorr, 2);
        affineMatrix2 = Results.get(0);
      }
    });
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


  private List<RealMatrix> applyRANSAC(List<List<RichSpot>> spotsCorr, int dir) {
    int inliersBest = 0;
    RealMatrix Abest = null;

    List<RichSpot> liveAll = spotsCorr.get(0);
    List<RichSpot> fixedAll = spotsCorr.get(1);

    List<RichSpot> liveChosen;
    List<RichSpot> fixedChosen;
    RealMatrix predictedBest = null;
    List<Integer> indices;
    for (int iter = 0; iter < Integer.parseInt(ransacTextField.getText()); iter++) {
      indices = new ArrayList<>();
      int count = 0;
      while (count < Integer.parseInt(minSamplesTextField.getText())) {
        int randomNum = ThreadLocalRandom.current().nextInt(0, liveAll.size());
        if (indices.contains(randomNum)) {

        } else {
          indices.add(randomNum);
          count = count + 1;
        }
      }
      liveChosen = getSubset(liveAll, indices);
      fixedChosen = getSubset(fixedAll, indices);
      RealMatrix fixedChosenSquare = addOneColumn(fixedChosen);
      RealMatrix liveChosenSquare = addOneColumn(liveChosen);
      RealMatrix A = liveChosenSquare.transpose().multiply(MatrixUtils.inverse(fixedChosenSquare.transpose()));
      RealMatrix fixedAllSquare = addOneColumn(fixedAll);
      RealMatrix predictedAllSquare = A.multiply(fixedAllSquare.transpose());
      predictedAllSquare = removeOneColumn(predictedAllSquare.transpose());
      int inliers = 0;
      for (int index = 0; index < liveAll.size(); index++) {
        double d = getDistance(liveAll.get(index), new RichSpot(" ", predictedAllSquare.getEntry(index, 0), predictedAllSquare.getEntry(index, 1), predictedAllSquare.getEntry(index, 2)));
        if (d < Double.parseDouble(thresholdTextField.getText())) {
          inliers = inliers + 1;
        }
      }
      if (inliers > inliersBest) {
        inliersBest = inliers;
        Abest = A;
        predictedBest = predictedAllSquare;
      }
    }
    if (dir == 1) {
      inliers1 = inliersBest;
    } else {
      inliers2 = inliersBest;
    }
    List<RealMatrix> Results = new ArrayList<>();
    Results.add(Abest);
    Results.add(predictedBest);
    return Results;
  }

  private List<RichSpot> getSubset(List<RichSpot> spots, List<Integer> indices) {
    List<RichSpot> spotsSubset = new ArrayList<>();
    for (int i = 0; i < indices.size(); i++) {
      spotsSubset.add(spots.get(indices.get(i)));
    }
    return spotsSubset;
  }

  private List<List<RichSpot>> getNuclei(int[] assignment, List<RichSpot> spotsLive, List<RichSpot> spotsFixed) {
    List<List<RichSpot>> spotsCorr = new ArrayList<>();
    List<RichSpot> spotsLiveCorr = new ArrayList<>();
    List<RichSpot> spotsFixedCorr = new ArrayList<>();

    for (int i = 0; i < assignment.length; i++) {
      if (assignment[i] == -1) {

      } else {
        spotsLiveCorr.add(spotsLive.get(i));
        spotsFixedCorr.add(spotsFixed.get(assignment[i]));
      }
    }
    spotsCorr.add(spotsLiveCorr);
    spotsCorr.add(spotsFixedCorr);
    return spotsCorr;
  }

  private double[][] getUnaryDistance(List<Integer[]> unaryLive, List<Integer[]> unaryFixed) {
    double[][] unaryCost = new double[unaryLive.size()][unaryFixed.size()];
    for (int i = 0; i < unaryLive.size(); i++) {
      for (int j = 0; j < unaryFixed.size(); j++) {
        unaryCost[i][j] = getCost(unaryLive.get(i), unaryFixed.get(j));
      }
    }
    return unaryCost;
  }


  private Double[] normalize(Integer[] sc) {
    double sum = 0;
    for (int i = 0; i < sc.length; i++) {
      sum = sum + sc[i];
    }
    Double[] scDouble = new Double[sc.length];
    for (int i = 0; i < sc.length; i++) {
      scDouble[i] = sc[i] / sum;
    }
    return scDouble;
  }

  private double getCost(Integer[] sc1, Integer[] sc2) {
    double dist = 0;
    Double[] sc1Double = normalize(sc1);
    Double[] sc2Double = normalize(sc2);

    for (int i = 0; i < sc1Double.length; i++) {
      if (sc1Double[i] == 0 && sc2Double[i] == 0) {
        dist = dist + 0.0;
      } else {
        dist = dist + 0.5 * Math.pow(sc1Double[i] - sc2Double[i], 2) / (sc1Double[i] + sc2Double[i]);
      }
    }
    return dist; //TODO: How does it fare with other costs
  }


  private List<RichSpot> deepCopy(List<RichSpot> spots) {
    List<RichSpot> neighbors = new ArrayList<>();
    for (int i = 0; i < spots.size(); i++) {
      neighbors.add(new RichSpot(spots.get(i).getLabel(), spots.get(i).getX(), spots.get(i).getY(), spots.get(i).getZ()));
    }
    return neighbors;
  }

  private List<Integer[]> getShapeContext(List<RichSpot> spots, RichSpot com, double meanDist, RichSpot xAxis) {
    List<Integer[]> unary = new ArrayList<>();
    for (int i = 0; i < spots.size(); i++) { //TODO spotsArray length is same as size?
      List<RichSpot> neighbors = deepCopy(spots);
      neighbors.remove(i);
      double mag = getNorm(new RichSpot("tmp", spots.get(i).getX() - com.getX(), spots.get(i).getY() - com.getY(), spots.get(i).getZ() - com.getZ()));
      RichSpot zAxis = new RichSpot("zaxis", (spots.get(i).getX() - com.getX()) / mag, (spots.get(i).getY() - com.getY()) / mag, (spots.get(i).getZ() - com.getZ()) / mag);
      RichSpot yAxis = cross(zAxis, xAxis);
      RealMatrix transformedNeighbors = transform(spots.get(i), xAxis, yAxis, zAxis, neighbors);
      Integer[] tmp = getShapeContext(transformedNeighbors, meanDist);
      unary.add(tmp);
    }
    return unary;
  }

  private Integer[] getShapeContext(RealMatrix transformedNeighbors, double meanDist) {
    List<Double> r = new ArrayList<>();
    List<Double> theta = new ArrayList<>();
    List<Double> phi = new ArrayList<>();
    double z_;
    double x_;
    double y_;
    double r_;
    for (int i = 0; i < transformedNeighbors.getData().length; i++) {
      x_ = transformedNeighbors.getEntry(i, 0);
      y_ = transformedNeighbors.getEntry(i, 1);
      z_ = transformedNeighbors.getEntry(i, 2);
      r_ = Math.sqrt(Math.pow(x_, 2) + Math.pow(y_, 2) + Math.pow(z_, 2));
      r.add(r_ / meanDist);
      theta.add(Math.acos(z_ / r_));
      if (Math.atan2(y_, x_) < 0) {
        phi.add(2 * Math.PI + Math.atan2(y_, x_));
      } else {
        phi.add(Math.atan2(y_, x_));
      }
    }
    List<Integer> binIndex = getBinIndex(r, theta, phi, (double) 0.125, 2, Integer.parseInt(radialBinTextField.getText()), Integer.parseInt(thetaBinTextField.getText()), Integer.parseInt(phiBinTextField.getText()));
    Integer[] sc = new Integer[Integer.parseInt(radialBinTextField.getText()) * Integer.parseInt(thetaBinTextField.getText()) * Integer.parseInt(phiBinTextField.getText())]; //TODO
    for (int i = 0; i < sc.length; i++) {
      sc[i] = count(binIndex, i);
    }
    return sc;
  }

  private Integer count(List<Integer> binIndex, int val) {
    int sum = 0;
    for (int i = 0; i < binIndex.size(); i++) {
      if (binIndex.get(i).equals(val)) {
        sum = sum + 1;
      }
    }
    return sum;
  }

  private List<Integer> getBinIndex(List<Double> r, List<Double> theta, List<Double> phi, double edge_l,
                                    double edge_u, int r_bins, int theta_bins, int phi_bins) {
    int r_index = r_bins - 1;
    int theta_index;
    int phi_index;
    List<Integer> binIndex = new ArrayList<>();
    double factor = Math.pow(edge_u / edge_l, (double) 1 / (r_bins - 1));
    for (int i = 0; i < r.size(); i++) {
      theta_index = (int) (theta.get(i) / (Math.PI / theta_bins));
      phi_index = (int) (phi.get(i) / (2 * Math.PI / phi_bins));
      for (int j = 0; j < r_bins; j++) {
        if (r.get(i) < edge_l * Math.pow(factor, j)) {
          r_index = j;
          break;
        }
      }
      binIndex.add(r_index * theta_bins * phi_bins + theta_index * phi_bins + phi_index);
    }
    return binIndex;

  }

  private RealMatrix transform(RichSpot origin, RichSpot xAxis, RichSpot yAxis, RichSpot
    zAxis, List<RichSpot> neighbors) {
    RichSpot x_1_3d = new RichSpot("x_1_3d", origin.getX() + xAxis.getX(), origin.getY() + xAxis.getY(), origin.getZ() + xAxis.getZ());
    RichSpot y_1_3d = new RichSpot("y_1_3d", origin.getX() + yAxis.getX(), origin.getY() + yAxis.getY(), origin.getZ() + yAxis.getZ());
    RichSpot z_1_3d = new RichSpot("z_1_3d", origin.getX() + zAxis.getX(), origin.getY() + zAxis.getY(), origin.getZ() + zAxis.getZ());
    double[][] A = new double[4][4];
    A[0][0] = origin.getX();
    A[0][1] = origin.getY();
    A[0][2] = origin.getZ();
    A[0][3] = 1.0;
    A[1][0] = x_1_3d.getX();
    A[1][1] = x_1_3d.getY();
    A[1][2] = x_1_3d.getZ();
    A[1][3] = 1.0;
    A[2][0] = y_1_3d.getX();
    A[2][1] = y_1_3d.getY();
    A[2][2] = y_1_3d.getZ();
    A[2][3] = 1.0;
    A[3][0] = z_1_3d.getX();
    A[3][1] = z_1_3d.getY();
    A[3][2] = z_1_3d.getZ();
    A[3][3] = 1.0; // TODO : important to include this to be 1.0
    RealMatrix AMatrix = MatrixUtils.createRealMatrix(A);
    RealMatrix AMatrixTranspose = AMatrix.transpose();
    double[][] B = new double[4][4];
    B[0][0] = 0;
    B[0][1] = 1;
    B[0][2] = 0;
    B[0][3] = 0;

    B[1][0] = 0;
    B[1][1] = 0;
    B[1][2] = 1;
    B[1][3] = 0;

    B[2][0] = 0;
    B[2][1] = 0;
    B[2][2] = 0;
    B[2][3] = 1;

    B[3][0] = 1;
    B[3][1] = 1;
    B[3][2] = 1;
    B[3][3] = 1;
    RealMatrix BMatrix = MatrixUtils.createRealMatrix(B);
    RealMatrix TMatrix = BMatrix.multiply(MatrixUtils.inverse(AMatrixTranspose));
    //RealMatrix TMatrix=BMatrix.multiply(MatrixUtils.blockInverse(AMatrixTranspose, 2));
    RealMatrix neighborsMatrix = addOneColumn(neighbors);
    RealMatrix pointsTransformed = TMatrix.multiply(neighborsMatrix.transpose());
    return removeOneColumn(pointsTransformed.transpose());
  }

  private RealMatrix removeOneColumn(RealMatrix neighbors) {
    return neighbors.getSubMatrix(0, neighbors.getData().length - 1, 0, 2);

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

  private RichSpot cross(RichSpot zAxis, RichSpot xAxis) {
    return new RichSpot("yAxis", zAxis.getY() * xAxis.getZ() - zAxis.getZ() * xAxis.getY(), zAxis.getZ() * xAxis.getX() - zAxis.getX() * xAxis.getZ(), zAxis.getX() * xAxis.getY() - zAxis.getY() * xAxis.getX());
  }

  private RichSpot getXAxis1(RealMatrix matrix) {
    Covariance covariance = new Covariance(matrix);
    RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
    EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);
    RichSpot xaxis = new RichSpot("xaxis", ed.getEigenvector(0).getEntry(0), ed.getEigenvector(0).getEntry(1), ed.getEigenvector(0).getEntry(2));
    return xaxis;
  }

  private RichSpot getXAxis2(RealMatrix matrix) {
    Covariance covariance = new Covariance(matrix);
    RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
    EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);
    RichSpot xaxis = new RichSpot("xaxis", -ed.getEigenvector(0).getEntry(0), -ed.getEigenvector(0).getEntry(1), -ed.getEigenvector(0).getEntry(2));
    return xaxis;
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


  //create real matrix


//create covariance matrix of points, then find eigen vectors
//see https://stats.stackexchange.com/questions/2691/making-sense-of-principal-component-analysis-eigenvectors-eigenvalues


  private double getMeanDistance(List<RichSpot> spots) {
    List<Double> distList = new ArrayList<>();
    for (int i = 0; i < spots.size() - 1; i++) {
      for (int j = i + 1; j < spots.size(); j++) {
        distList.add(getDistance(spots.get(i), spots.get(j)));
      }
    }
    return averageList(distList);
  }

  private double averageList(List<Double> list) {
    double s = 0;
    for (int i = 0; i < list.size(); i++) {
      s = s + list.get(i);
    }
    return s / list.size();

  }


  private double getNorm(RichSpot spot) {
    return Math.sqrt(Math.pow(spot.getX(), 2) + Math.pow(spot.getY(), 2) + Math.pow(spot.getZ(), 2));
  }

  private double getDistance(RichSpot spot1, RichSpot spot2) {
    return Math.sqrt(Math.pow(spot1.getX() - spot2.getX(), 2) + Math.pow(spot1.getY() - spot2.getY(), 2) + Math.pow(spot1.getZ() - spot2.getZ(), 2));
  }


  private RichSpot getCOM(List<RichSpot> spots) {
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    for (int i = 0; i < spots.size(); i++) {
      x = x + spots.get(i).getX();
      y = y + spots.get(i).getY();
      z = z + spots.get(i).getZ();
    }
    return new RichSpot("com", x / spots.size(), y / spots.size(), z / spots.size());
  }


  private void setupPanel() {
    this.setBackground(Color.white);
    this.setLayout(new MigLayout("fillx", "", ""));
  }

}

