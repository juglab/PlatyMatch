package PlatyMatch.nucleiDetection;

import net.imagej.ops.OpService;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.scijava.command.CommandService;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.FloatColumn;
import org.scijava.table.GenericTable;
import org.scijava.table.IntColumn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * This is an implementation that calls {@link Laplacian} and {@link LocalMinima} in order to detect nuclei.
 *
 * @param <T> type
 * @author Matthias Arzt, Tim-Oliver Buccholz, Manan Lalit MPI-CBG / CSBD, Dresden
 */


public class BlobDetection<T extends RealType<T> & Type<T>> {

  private Img<T> image;
  private double minScale;
  private double maxScale;
  private double stepScale;
  private boolean brightBlobs;
  private int axis;
  private double samplingFactor;
  private GenericTable resultsTable;
  private CommandService cs;
  private OpService ops;

  public BlobDetection(Img<T> image, double minScale, double maxScale, double stepScale, boolean brightBlobs, int axis, double samplingFactor, OpService ops) {
    this.image = image;
    this.minScale = minScale;
    this.maxScale = maxScale;
    this.stepScale = stepScale;
    this.brightBlobs = brightBlobs;
    this.axis = axis;
    this.samplingFactor = samplingFactor;
    this.ops = ops;

    /*Step One: Obtain Laplacian reponse, normalize to make scale-independent and stack in a pyramid*/
    Img<FloatType> normalizedExpandedImage = (Img<FloatType>) multiScaleLaplacian();
    /*Step Two: Apply LocalMinima Command to obtain the minima on the normalizedExpandedImage*/
    List<Point> predictedResult = new LocalMinima(normalizedExpandedImage).getOutput();

    /*Step Three: Create a (N+7) dimensioned table based on the results */
    resultsTable = createResultsTable(normalizedExpandedImage, predictedResult);

    predictedResult = null;
    normalizedExpandedImage = null;
    System.gc();
  }

  public GenericTable getResultsTable() {
    return resultsTable;
  }

  private RandomAccessibleInterval<FloatType> multiScaleLaplacian() {

    final List<Img<FloatType>> results = new ArrayList<>();
    Img<FloatType> normalizedLaplacianOfGaussian;
    Img<FloatType> laplacianOfGaussian;
    for (double scale = minScale; scale <= maxScale; scale = scale + stepScale) {
      normalizedLaplacianOfGaussian = ops.create().img(image, new FloatType());
      laplacianOfGaussian = new Laplacian(image, scale, axis, samplingFactor, ops).getOutput();

      final double s = scale;
      if (brightBlobs) {
        LoopBuilder.setImages(laplacianOfGaussian, normalizedLaplacianOfGaussian).forEachPixel((i, o) -> o.setReal(Math.pow(s, 2) * i.getRealFloat()));
      } else {
        LoopBuilder.setImages(laplacianOfGaussian, normalizedLaplacianOfGaussian).forEachPixel((i, o) -> o.setReal(-1 * Math.pow(s, 2) * i.getRealFloat()));
      }
      //new ImgSaver().saveImg( "/home/manan/Desktop/normalized_"+s+".tif", normalizedLaplacianOfGaussian);
      results.add(normalizedLaplacianOfGaussian);

    }
    return copy(Views.stack(results));


  }

  private RandomAccessibleInterval<FloatType> copy(final RandomAccessibleInterval<FloatType> input) {
    Img<FloatType> output = ArrayImgs.floats(Intervals.dimensionsAsLongArray(input));
    LoopBuilder.setImages(input, output).forEachPixel((i, o) -> o.set(i));
    return output;
  }


  private GenericTable createResultsTable(final Img<FloatType> input, final List<Point> listOfMinima) {

    IntColumn XColumn = new IntColumn("X");
    IntColumn YColumn = new IntColumn("Y");
    IntColumn ZColumn = new IntColumn("Z");
    IntColumn SliceColumn = new IntColumn("Slice");
    FloatColumn ScaleColumn = new FloatColumn("Scale");
    FloatColumn RadiusColumn = new FloatColumn("Radius");
    FloatColumn ValueColumn = new FloatColumn("Value");
    IntColumn RedColumn = new IntColumn("Red");
    IntColumn GreenColumn = new IntColumn("Green");
    IntColumn BlueColumn = new IntColumn("Blue");

    GenericTable resultsTable = new DefaultGenericTable();
    final Iterator<Point> iterator = listOfMinima.iterator();
    final RandomAccess<FloatType> randomAccess = input.randomAccess();

    while (iterator.hasNext()) {
      Point point = iterator.next();
      if (point.numDimensions() == 4) {
        /*i.e. 3D image*/
        randomAccess.setPosition(point);
        XColumn.add(point.getIntPosition(0));
        YColumn.add(point.getIntPosition(1));
        ZColumn.add(point.getIntPosition(2));
        SliceColumn.add(point.getIntPosition(3));
        ScaleColumn.add((float) (minScale + stepScale * point.getIntPosition(3)));
        RadiusColumn.add((float) (Math.sqrt(3) * (minScale + stepScale * point.getIntPosition(3))));

      } else if (point.numDimensions() == 3) {
        /*i.e. 2D image*/
        randomAccess.setPosition(point);
        XColumn.add(point.getIntPosition(0));
        YColumn.add(point.getIntPosition(1));
        ZColumn.add(-1);
        SliceColumn.add(point.getIntPosition(2));
        ScaleColumn.add((float) (minScale + stepScale * point.getIntPosition(2)));
        RadiusColumn.add((float) (Math.sqrt(2) * (minScale + stepScale * point.getIntPosition(2))));

      }
      ValueColumn.add(randomAccess.get().copy().getRealFloat());
      RedColumn.add((int) (255 * Math.random()));
      GreenColumn.add((int) (255 * Math.random()));
      BlueColumn.add((int) (255 * Math.random()));
    }


    resultsTable.add(XColumn);
    resultsTable.add(YColumn);
    resultsTable.add(ZColumn);
    resultsTable.add(SliceColumn);
    resultsTable.add(ScaleColumn);
    resultsTable.add(RadiusColumn);
    resultsTable.add(ValueColumn);
    resultsTable.add(RedColumn);
    resultsTable.add(GreenColumn);
    resultsTable.add(BlueColumn);

    return resultsTable;

  }


}







