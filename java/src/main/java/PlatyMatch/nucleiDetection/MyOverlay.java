package PlatyMatch.nucleiDetection;

import bdv.util.BdvOverlay;
import net.imglib2.Point;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class MyOverlay extends BdvOverlay {


  private int axis;
  private double samplingFactor;
  private double minScale;
  private double stepScale;
  private double maxScale;

  private List<RichFeaturePoint> localMinima;
  private List<RichFeaturePoint> thresholdedLocalMinima;
  private List<Point> thresholdedLocalMinimaLocations;
  private List<Float> thresholdedLocalMinimaRadii;
  private List<Point> thresholdedLocalMinimaColours;

  private float startThreshold;
  private float currentThreshold;
  private int numDims;

  private int x;
  private int y;
  private int width;
  private int height;
  private double[] lPos_initial = new double[3];
  private double[] lPos_final = new double[3];
  private double[] vPos_initial = new double[3];
  private double[] vPos_final = new double[3];

  public MyOverlay(int axis, double samplingFactor, double minScale, double stepScale, double maxScale, List<RichFeaturePoint> localMinima, float startThreshold, float currentThreshold, int numDims) {
    this.axis = axis;
    this.samplingFactor = samplingFactor;
    this.localMinima = localMinima;
    this.minScale = minScale;
    this.stepScale = stepScale;
    this.maxScale = maxScale;
    this.startThreshold = startThreshold;
    this.currentThreshold = currentThreshold;
    this.numDims = numDims;

  }


  @Override
  protected synchronized void draw(final Graphics2D g) {
    if (thresholdedLocalMinimaLocations == null)
      return;

    final AffineTransform3D t = new AffineTransform3D();
    getCurrentTransform3D(t);
    double calibrationFactor = extractScale(t, 0);
    final double[] lPos = new double[3];
    final double[] vPos = new double[3];
    for (int i = 0; i < thresholdedLocalMinimaLocations.size(); i++) {
      Point p = thresholdedLocalMinimaLocations.get(i);
      float radius = thresholdedLocalMinimaRadii.get(i);
      radius = (float) calibrationFactor * radius;
      p.localize(lPos);
      t.apply(lPos, vPos);

      double dis = vPos[2];
      final int size;
      if (Math.abs(dis) <= radius) {
        size = 2 * (int) Math.ceil(Math.sqrt(Math.pow(radius, 2) - Math.pow(samplingFactor * dis, 2)));

        final int x = (int) (vPos[0] - 0.5 * size);
        final int y = (int) (vPos[1] - 0.5 * size);
        g.setColor(getColor(thresholdedLocalMinimaColours.get(i)));
        g.fillOval(x, y, size, size);
        g.setFont(new Font("Serif", Font.BOLD, 20));
        String s = String.valueOf(i);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(255, 255, 0));
        g.drawString(s, (int) vPos[0] + radius, (int) vPos[1] + radius);

      } else {
        size = 0;
      }
    }

    t.apply(lPos_initial, vPos_initial);
    t.apply(lPos_final, vPos_final);
    int xleft = (int) vPos_initial[0];

    int yleft = (int) vPos_initial[1];
    int xright = (int) vPos_final[0];
    int yright = (int) vPos_final[1];

    this.width = (int) (xright - xleft);
    this.height = (int) (yright - yleft);
    this.x = xleft;
    this.y = yleft;

    g.setColor(new Color(255, 0, 0, 150));
    g.setStroke(new BasicStroke(10));
    g.drawOval(this.x, this.y, width, height);

  }

  public void setXY(RealPoint p) {
    p.localize(lPos_initial);

  }

  public void setWidthHeight(RealPoint p) {
    p.localize(lPos_final);
  }

  public RichFeaturePoint getAddedPoint() {
    int x = (int) (0.5 * (this.lPos_initial[0] + this.lPos_final[0]));
    int y = (int) (0.5 * (this.lPos_initial[1] + this.lPos_final[1]));
    int z;
    if (numDims == 2) {
      z = -1;

    } else {
      z = (int) this.lPos_final[2];

    }
    double radius = Math.sqrt(Math.pow(this.lPos_final[0] - this.lPos_initial[0], 2) + Math.pow(this.lPos_final[1] - this.lPos_initial[1], 2)) / 2 / Math.sqrt(numDims);

    int scale = (int) ((radius - this.minScale) / this.stepScale - 1);
    return new RichFeaturePoint(x, y, z, scale, -100, x % 255, y % 255, z % 255);
  }


  public void setThresholdedLocalMinima(float threshold) {
    thresholdedLocalMinima = new ArrayList<>();
    for (RichFeaturePoint richFeaturePoint : localMinima) {
      if (richFeaturePoint.getValue() <= threshold) {
        // Change for dark blobs
        thresholdedLocalMinima.add(richFeaturePoint);
      }

    }


    /*Split into pointLocations, pointRadii and pointColours*/
    this.thresholdedLocalMinimaLocations = obtainPointLocations(thresholdedLocalMinima);
    this.thresholdedLocalMinimaRadii = obtainPointRadii(thresholdedLocalMinima);
    this.thresholdedLocalMinimaColours = obtainPointColours(thresholdedLocalMinima);


  }


  public void eliminateInsignificantNuclei2(float overlapThreshold) {

    // Make Graph
    Graph G = new Graph(thresholdedLocalMinima.size());

    // Populate Graph
    G = populateGraph(G, overlapThreshold, thresholdedLocalMinima);

    // make a call to recursive minima finding
    List<RichFeaturePoint> output = new ArrayList<>();
    thresholdedLocalMinima = recursiveMinimaFinding(G, thresholdedLocalMinima, output, overlapThreshold);
    this.thresholdedLocalMinimaLocations = obtainPointLocations(thresholdedLocalMinima);
    this.thresholdedLocalMinimaRadii = obtainPointRadii(thresholdedLocalMinima);
    this.thresholdedLocalMinimaColours = obtainPointColours(thresholdedLocalMinima);
    System.out.println(thresholdedLocalMinima.size());

  }

  public void toggleSelected(RealPoint pos) {

    for (int i = 0; i < this.getThresholdedLocalMinima().size(); i++) {

      if (Math.pow(this.getThresholdedLocalMinima().get(i).getX() - pos.getDoublePosition(0), 2) + Math.pow(this.getThresholdedLocalMinima().get(i).getY() - pos.getDoublePosition(1), 2) + Math.pow(this.getSamplingFactor(), 2) * Math.pow(this.getThresholdedLocalMinima().get(i).getZ() - pos.getDoublePosition(2), 2) <= 3 * Math.pow(this.getMinScale() + this.getStepScale() * this.getThresholdedLocalMinima().get(i).getScale(), 2)) {
        if (this.getThresholdedLocalMinima().get(i).getSelected()) {
          this.getThresholdedLocalMinima().get(i).setSelected(false);
          this.getThresholdedLocalMinima().get(i).setRed(this.getThresholdedLocalMinima().get(i).getRedOld());
          this.getThresholdedLocalMinima().get(i).setGreen(this.getThresholdedLocalMinima().get(i).getGreenOld());
          this.getThresholdedLocalMinima().get(i).setBlue(this.getThresholdedLocalMinima().get(i).getBlueOld());

        } else {
          this.getThresholdedLocalMinima().get(i).setSelected(true);
          this.getThresholdedLocalMinima().get(i).setRed(0);
          this.getThresholdedLocalMinima().get(i).setGreen(255);
          this.getThresholdedLocalMinima().get(i).setBlue(0);

        }
      }
    }
    this.thresholdedLocalMinimaLocations = obtainPointLocations(thresholdedLocalMinima);
    this.thresholdedLocalMinimaRadii = obtainPointRadii(thresholdedLocalMinima);
    this.thresholdedLocalMinimaColours = obtainPointColours(thresholdedLocalMinima);

  }


  public void deleteSelected() {
    for (int i = this.getThresholdedLocalMinima().size() - 1; i >= 0; i--) {
      if (this.getThresholdedLocalMinima().get(i).getSelected()) {
        this.getThresholdedLocalMinima().remove(i);
      }

    }
    this.thresholdedLocalMinimaLocations = obtainPointLocations(thresholdedLocalMinima);
    this.thresholdedLocalMinimaRadii = obtainPointRadii(thresholdedLocalMinima);
    this.thresholdedLocalMinimaColours = obtainPointColours(thresholdedLocalMinima);
  }


  private List<RichFeaturePoint> recursiveMinimaFinding(Graph G, List<RichFeaturePoint> input, List<RichFeaturePoint> output, float overlapThreshold) {

    List<ArrayList<Integer>> cc = G.getCC();
    for (int i = 0; i < cc.size(); i++) {
      if (cc.get(i).size() == 1) {
        output.add(input.get(cc.get(i).get(0)));
      } else {
        //find strongest response
        ArrayList<RichFeaturePoint> minimaSubset = new ArrayList<RichFeaturePoint>();
        for (int j = 0; j < cc.get(i).size(); j++) {
          minimaSubset.add(input.get(cc.get(i).get(j)));
        }

        Comparator<RichFeaturePoint> compareByResponse = (RichFeaturePoint p1, RichFeaturePoint p2) -> p1.compareTo(p2);
        Collections.sort(minimaSubset, compareByResponse);


        // add to results
        output.add(minimaSubset.get(0));

        // take away intersection
        List<Integer> deleteArray = new ArrayList<>();
        for (int j = 1; j < minimaSubset.size(); j++) {

          final double radius1 = Math.sqrt(numDims) * (minScale + stepScale * minimaSubset.get(0).getScale());
          final double radius2 = Math.sqrt(numDims) * (minScale + stepScale * minimaSubset.get(j).getScale());
          if (numDims == 3) {
            final double v1 = 4.0 / 3.0 * Math.PI * Math.pow(radius1, 3);
            final double v2 = 4.0 / 3.0 * Math.PI * Math.pow(radius2, 3);
            int[] loc1 = new int[3];
            loc1[0] = minimaSubset.get(0).getX();
            loc1[1] = minimaSubset.get(0).getY();
            loc1[2] = minimaSubset.get(0).getZ();

            int[] loc2 = new int[3];
            loc2[0] = minimaSubset.get(j).getX();
            loc2[1] = minimaSubset.get(j).getY();
            loc2[2] = minimaSubset.get(j).getZ();

            Point pi = new Point(loc1);
            Point pj = new Point(loc2);
            final double distance12 = findDistanceBetweenCentres(pi, pj);
            final double VOI = findIntersectionOfVolumes(radius1, radius2, distance12);
            if (VOI > overlapThreshold * Math.min(v1, v2)) { // I modified this to be similar to StarDist
              deleteArray.add(j);
            }
          } else if (numDims == 2) {
            final double v1 = Math.PI * Math.pow(radius1, 2);
            final double v2 = Math.PI * Math.pow(radius2, 2);
            int[] loc1 = new int[2];
            loc1[0] = minimaSubset.get(0).getX();
            loc1[1] = minimaSubset.get(0).getY();

            int[] loc2 = new int[2];
            loc2[0] = minimaSubset.get(j).getX();
            loc2[1] = minimaSubset.get(j).getY();

            Point pi = new Point(loc1);
            Point pj = new Point(loc2);
            final double distance12 = findDistanceBetweenCentres(pi, pj);
            final double VOI = findIntersectionOfVolumes(radius1, radius2, distance12);
            if (VOI > overlapThreshold * Math.min(v1, v2)) {
              deleteArray.add(j);
            }
          }


        }

        for (int j = deleteArray.size() - 1; j >= 0; j--) {
          minimaSubset.remove((int) deleteArray.get(j));
        }

        minimaSubset.remove(0);

        // compute graph from remaining nodes
        if (minimaSubset.size() > 0) {
          G = new Graph(minimaSubset.size());

          // Populate Graph
          G = populateGraph(G, overlapThreshold, minimaSubset);
          // call recursiveMinimaFinding
          output = recursiveMinimaFinding(G, minimaSubset, output, overlapThreshold);
        }
      }

    }

    return output;

  }


  private Graph populateGraph(Graph G, float overlapThreshold, List<RichFeaturePoint> minimaSubset) {
    for (int i = 0; i < minimaSubset.size() - 1; i++) {
      for (int j = i + 1; j < minimaSubset.size(); j++) {
        final double radius1 = Math.sqrt(numDims) * (minScale + stepScale * minimaSubset.get(i).getScale());
        final double radius2 = Math.sqrt(numDims) * (minScale + stepScale * minimaSubset.get(j).getScale());
        if (numDims == 3) {

          final double v1 = 4.0 / 3.0 * Math.PI * Math.pow(radius1, 3);
          final double v2 = 4.0 / 3.0 * Math.PI * Math.pow(radius2, 3);
          int[] loc1 = new int[3];
          loc1[0] = minimaSubset.get(i).getX();
          loc1[1] = minimaSubset.get(i).getY();
          loc1[2] = minimaSubset.get(i).getZ();

          int[] loc2 = new int[3];
          loc2[0] = minimaSubset.get(j).getX();
          loc2[1] = minimaSubset.get(j).getY();
          loc2[2] = minimaSubset.get(j).getZ();

          Point pi = new Point(loc1);
          Point pj = new Point(loc2);
          final double distance12 = findDistanceBetweenCentres(pi, pj);
          final double VOI = findIntersectionOfVolumes(radius1, radius2, distance12);

          if (VOI > (overlapThreshold * Math.min(v1, v2))) //modified to be similar to stardist
            G.addEdge(i, j);
        } else if (numDims == 2) {
          final double v1 = Math.PI * Math.pow(radius1, 2);
          final double v2 = Math.PI * Math.pow(radius2, 2);
          int[] loc1 = new int[2];
          loc1[0] = minimaSubset.get(i).getX();
          loc1[1] = minimaSubset.get(i).getY();

          int[] loc2 = new int[2];
          loc2[0] = minimaSubset.get(j).getX();
          loc2[1] = minimaSubset.get(j).getY();

          Point pi = new Point(loc1);
          Point pj = new Point(loc2);
          final double distance12 = findDistanceBetweenCentres(pi, pj);
          final double VOI = findIntersectionOfVolumes(radius1, radius2, distance12);

          if (VOI > (overlapThreshold * Math.min(v1, v2))) //modified to be similar to stardist
            G.addEdge(i, j);

        }
      }
    }

    return G;
  }


  private double findDistanceBetweenCentres(final Point p1, final Point p2) {

    double sum = 0; /*Euclidean Distance between two points*/
    for (int i = 0; i < numDims; i++) {
      if (i == axis) {
        sum += Math.pow(samplingFactor, 2) * (Math.pow(p1.getLongPosition(i) - p2.getLongPosition(i), 2));
      } else {
        sum += Math.pow(p1.getLongPosition(i) - p2.getLongPosition(i), 2);
      }
    }
    sum = Math.sqrt(sum);
    return sum;
  }

  private double findIntersectionOfVolumes(final double r1, final double r2, final double d) {
    double volumeOfIntersection = 0.0;
    final boolean valid = (d < (r1 + r2)) & (d > 0);
    if (valid) {
      if (numDims == 2) {

        if (d + r1 < r2) {
          volumeOfIntersection = Math.PI * Math.pow(r1, 2); //modified to include cases when complete circle is inside another circle
        } else if (d + r2 < r1) {
          volumeOfIntersection = Math.PI * Math.pow(r2, 2); //modified to include cases when complete circle is inside another circle
        } else {
          volumeOfIntersection = (Math.pow(r1, 2) * Math.acos((Math.pow(d, 2) + Math.pow(r1, 2) - Math.pow(r2, 2)) / (2 * d * r1))
            + Math.pow(r2, 2) * Math.acos((Math.pow(d, 2) + Math.pow(r2, 2) - Math.pow(r1, 2)) / (2 * d * r2))
            - Math.sqrt((-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)) / 2);
        }
        /*i.e. 2D : Here volumeofIntersection is actually areaOfIntersection*/
        /*Equation 14 from http://mathworld.wolfram.com/Circle-CircleIntersection.html*/


      } else if (numDims == 3) {
                /*i.e. 3D
                Sphere-Sphere Intersection https://en.wikipedia.org/wiki/Spherical_cap*/
        if (d + r1 < r2) {
          volumeOfIntersection = 4 / 3 * Math.PI * Math.pow(r1, 3);
        } else if (d + r2 < r1) {
          volumeOfIntersection = 4 / 3 * Math.PI * Math.pow(r2, 3);
        } else {
          volumeOfIntersection = (Math.PI * Math.pow((r1 + r2 - d), 2)
            * (Math.pow(d, 2)
            + 2 * d * (r1 + r2)
            - 3 * Math.pow((r1 - r2), 2)) / (12 * d));
        }

      }

    }
    return volumeOfIntersection;
  }


  private List<Point> obtainPointLocations(List<RichFeaturePoint> inputList) {
    List<Point> LocationInputList = new ArrayList<>();
    for (RichFeaturePoint richFeaturePoint : inputList) {
      long[] pointLocation = new long[numDims];
      pointLocation[0] = richFeaturePoint.getX();
      pointLocation[1] = richFeaturePoint.getY();
      if (numDims == 3) {
        pointLocation[2] = richFeaturePoint.getZ();
      }
      Point euclideanPoint = new Point(pointLocation);
      LocationInputList.add(euclideanPoint);
    }
    return LocationInputList;
  }

  private List<Float> obtainPointRadii(List<RichFeaturePoint> inputList) {
    List RadiiInputList = new ArrayList<>();
    for (RichFeaturePoint richfeaturePoint : inputList) {

      final double scale = minScale + stepScale * richfeaturePoint.getScale();
      final float radius = (float) (Math.sqrt(numDims) * scale);
      RadiiInputList.add(radius);
    }
    return RadiiInputList;
  }

  private List<Point> obtainPointColours(List<RichFeaturePoint> inputList) {
    List<Point> ColoursInputList = new ArrayList<>();
    for (RichFeaturePoint richFeaturePoint : inputList) {
      int[] pointColours = new int[3]; // This should always be 3
      pointColours[0] = richFeaturePoint.getRed();
      pointColours[1] = richFeaturePoint.getGreen();
      pointColours[2] = richFeaturePoint.getBlue();
      ColoursInputList.add(new Point(pointColours));
    }


    return ColoursInputList;
  }


  private Color getColor(final Point point) {
    int alpha = 150;
    final int r = point.getIntPosition(0);
    final int g = point.getIntPosition(1);
    final int b;
    if (numDims == 2) {
      b = 128;
    } else {
      b = point.getIntPosition(2);
    }

    return new Color(r, g, b, alpha);
  }

  public int getNumberBlobs() {

    return this.thresholdedLocalMinimaLocations.size();
  }

  public void writeNuclei(String outputPath) {


    //final String outputPath = "/home/manan/Desktop/02_Repositories/26_Final/08_CPDExperiments/CPDExperiments/CPD/utils/AnnotatedData/02_Detections/";
    final String DELIMITER = " ";
    final String NEW_LINE_SEPARATOR = "\n";
    FileWriter fileWriter = null;

    try {
      fileWriter = new FileWriter(outputPath + "/nuclei.csv");
      Iterator<RichFeaturePoint> iterator = this.thresholdedLocalMinima.iterator();
      int index = 0;
      while (iterator.hasNext()) {
        RichFeaturePoint point = iterator.next();
        double scale = minScale + stepScale * point.getScale();
        fileWriter.append(String.valueOf(index));
        fileWriter.append(DELIMITER);
        fileWriter.append(String.valueOf(point.getX()));
        fileWriter.append(DELIMITER);
        fileWriter.append(String.valueOf(point.getY()));
        fileWriter.append(DELIMITER);
        fileWriter.append(String.valueOf(point.getZ()));
        fileWriter.append(DELIMITER);
        fileWriter.append(String.valueOf(scale));
        fileWriter.append(NEW_LINE_SEPARATOR);
        index++;
      }
    } catch (IOException e) {
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


  public void writeToCSV(String path) {
    final String COMMA_DELIMITER = ",";
    final String NEW_LINE_SEPARATOR = "\n";
    final String FILE_HEADER = "X, Y, Z, Scale, Value, Red, Green, Blue";
    FileWriter fileWriter = null;

    final String fileName = path + "/Predictions/" + java.time.LocalDateTime.now() + ".csv";
    if (new File(path, "Predictions").exists()) {
      //Do noting
    } else {
      new File(path, "Predictions").mkdir();
    }

    try {
      fileWriter = new FileWriter(fileName);
      fileWriter.append(String.valueOf(minScale));
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append(String.valueOf(stepScale));
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append(String.valueOf(maxScale));
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append(String.valueOf(samplingFactor));
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append(String.valueOf(axis));
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append(String.valueOf(startThreshold));
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append(String.valueOf(currentThreshold));
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append(FILE_HEADER.toString());
      fileWriter.append(NEW_LINE_SEPARATOR);
      Iterator<RichFeaturePoint> iterator = this.localMinima.iterator();

      while (iterator.hasNext()) {
        RichFeaturePoint point = iterator.next();
        fileWriter.append(String.valueOf(point.getX()));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(point.getY()));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(point.getZ()));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(point.getScale()));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(point.getValue()));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(point.getRed()));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(point.getGreen()));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(point.getBlue()));
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


  public void createTGMM(String path) {
    FileWriter fileWriter = null;
    final String NEW_LINE_SEPARATOR = "\n";
    final String fileName = path + "/TGMM/" + java.time.LocalDateTime.now() + ".xml";
    if (new File(path, "TGMM").exists()) {
      // Do nothing
    } else {
      new File(path, "TGMM").mkdir();
    }
    try {
      fileWriter = new FileWriter(fileName);
      fileWriter.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      fileWriter.append(NEW_LINE_SEPARATOR);
      fileWriter.append("<document>");
      fileWriter.append(NEW_LINE_SEPARATOR);
      Iterator<RichFeaturePoint> iterator = this.thresholdedLocalMinima.iterator();
      int index = 0;
      while (iterator.hasNext()) {
        RichFeaturePoint point = iterator.next();
        double scale = minScale + stepScale * point.getScale();
        String information = "<GaussianMixtureModel id=\"" + index + "\" dims=\"3\" splitScore=\"5\" scale=\"1 \" nu=\"10\" beta=\"-1\" alpha=\"-1\" lineage=\"-1\" parent=\"-1\" m=\"" + point.getX() + " " + point.getY() + " " + point.getZ() + "\" W=\"0.02 0 0 0 0.02 0 0 0 0.02 \" nuPrior=\"-1\" betaPrior=\"-1\" alphaPrior=\"-1\" distMRFPrior=\"-1\" mPrior=\"" + point.getX() + " " + point.getY() + " " + point.getZ() + "\" WPrior=\"0.02 0 0 0 0.02 0 0 0 0.02\" svIdx=\" \" LOGScale=\"" + scale + "\"></GaussianMixtureModel> ";
        fileWriter.append(information);
        fileWriter.append(NEW_LINE_SEPARATOR);
        index++;
      }
      fileWriter.append("</document>");
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


  private static double extractScale(final AffineTransform3D transform, final int axis) {
    double sqSum = 0;
    final int c = axis;
    for (int r = 0; r < 3; ++r) {
      final double x = transform.get(r, c);
      sqSum += x * x;
    }
    return Math.sqrt(sqSum);
  }

  public double getMinScale() {
    return this.minScale;
  }

  public double getStepScale() {
    return this.stepScale;
  }

  public double getMaxScale() {
    return this.maxScale;
  }

  public int getAxis() {
    return this.axis;
  }

  public double getSamplingFactor() {
    return this.samplingFactor;
  }

  public List<RichFeaturePoint> getLocalMinima() {
    return this.localMinima;
  }

  public List<RichFeaturePoint> getThresholdedLocalMinima() {
    return this.thresholdedLocalMinima;
  }

  public List<Point> getThresholdedLocalMinimaLocations() {
    return this.thresholdedLocalMinimaLocations;
  }

  public void setCurrentThreshold(float currentThreshold) {
    this.currentThreshold = currentThreshold;
  }

  public float getCurrentThreshold() {
    return this.currentThreshold;
  }

  public float getStartThreshold() {
    return startThreshold;
  }

  // not optimised for 2d
  public void createImage(String absolutePath, long[] dimensions) {
    Img<UnsignedByteType> outputRed = ArrayImgs.unsignedBytes(dimensions);
    Img<UnsignedByteType> outputGreen = ArrayImgs.unsignedBytes(dimensions);
    Img<UnsignedByteType> outputBlue = ArrayImgs.unsignedBytes(dimensions);
    for (int i = 0; i < thresholdedLocalMinima.size(); i++) {

      int xCenter = thresholdedLocalMinima.get(i).getX();
      int yCenter = thresholdedLocalMinima.get(i).getY();
      int zCenter = thresholdedLocalMinima.get(i).getZ();
      int radius = (int) ((Math.sqrt(dimensions.length)) * (minScale + stepScale * thresholdedLocalMinima.get(i).getScale()));
      if (zCenter != -1) {
        HyperSphere<UnsignedByteType> sphereRed = new HyperSphere<>(outputRed, new net.imglib2.Point(xCenter, yCenter, zCenter), radius);
        net.imglib2.Cursor<UnsignedByteType> cursorRed = sphereRed.localizingCursor();
        HyperSphere<UnsignedByteType> sphereGreen = new HyperSphere<>(outputGreen, new net.imglib2.Point(xCenter, yCenter, zCenter), radius);
        net.imglib2.Cursor<UnsignedByteType> cursorGreen = sphereGreen.localizingCursor();
        HyperSphere<UnsignedByteType> sphereBlue = new HyperSphere<>(outputBlue, new net.imglib2.Point(xCenter, yCenter, zCenter), radius);
        net.imglib2.Cursor<UnsignedByteType> cursorBlue = sphereBlue.localizingCursor();
        while (cursorRed.hasNext()) {
          cursorRed.fwd();
          cursorRed.get().set(thresholdedLocalMinima.get(i).getRed());
          cursorGreen.fwd();
          cursorGreen.get().set(thresholdedLocalMinima.get(i).getGreen());
          cursorBlue.fwd();
          cursorBlue.get().set(thresholdedLocalMinima.get(i).getBlue());

        }
      } else {
        HyperSphere<UnsignedByteType> sphereRed = new HyperSphere<>(outputRed, new net.imglib2.Point(xCenter, yCenter), radius);
        net.imglib2.Cursor<UnsignedByteType> cursorRed = sphereRed.localizingCursor();
        HyperSphere<UnsignedByteType> sphereGreen = new HyperSphere<>(outputGreen, new net.imglib2.Point(xCenter, yCenter), radius);
        net.imglib2.Cursor<UnsignedByteType> cursorGreen = sphereGreen.localizingCursor();
        HyperSphere<UnsignedByteType> sphereBlue = new HyperSphere<>(outputBlue, new net.imglib2.Point(xCenter, yCenter), radius);
        net.imglib2.Cursor<UnsignedByteType> cursorBlue = sphereBlue.localizingCursor();
        while (cursorRed.hasNext()) {
          cursorRed.fwd();
          cursorRed.get().set(thresholdedLocalMinima.get(i).getRed());
          cursorGreen.fwd();
          cursorGreen.get().set(thresholdedLocalMinima.get(i).getGreen());
          cursorBlue.fwd();
          cursorBlue.get().set(thresholdedLocalMinima.get(i).getBlue());
        }
      }
    }
    //new ImgSaver().saveImg(absolutePath + "/Red.tif", outputRed);
    //new ImgSaver().saveImg(absolutePath + "/Green.tif", outputGreen);
    //new ImgSaver().saveImg(absolutePath + "/Blue.tif", outputBlue);
  }


  public void add(RichFeaturePoint addedPoint) {
    this.thresholdedLocalMinima.add(addedPoint);
    this.thresholdedLocalMinimaLocations = obtainPointLocations(thresholdedLocalMinima);
    this.thresholdedLocalMinimaRadii = obtainPointRadii(thresholdedLocalMinima);
    this.thresholdedLocalMinimaColours = obtainPointColours(thresholdedLocalMinima);
  }

  public void setGeneExpression(int i, String text, double val) {
    this.thresholdedLocalMinima.get(i).setGeneExpression(text, val);


  }
}




