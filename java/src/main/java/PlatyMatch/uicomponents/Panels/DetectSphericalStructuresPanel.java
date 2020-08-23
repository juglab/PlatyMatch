package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.*;
import bdv.util.BdvOverlaySource;
import bdv.viewer.state.SourceState;
import net.imagej.ops.OpService;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.FloatType;
import net.miginfocom.swing.MigLayout;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.io.IOService;
import org.scijava.table.Column;
import org.scijava.table.GenericTable;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;


/**
 * DetectSphericalStructuresPanel employs the Scale Space Theory
 * (@see <a href="https://people.kth.se/~tony/papers/cvap198.pdf">
 * Lindeberg, T.: Feature detection with automatic scale selection. International Journal of Computer Vision, 30(2), 70–116 (1998) </a>)
 * to detect nuclei by performing a convolution of the normalized laplacian of gaussian filter
 * with the image intensities (see {@link BlobDetection}) and then performing a local minima (see {@link LocalMinima}) in the 4-D (x, y, z, &#963) space.
 *
 * @param <T>
 * @author Manan
 */
public class DetectSphericalStructuresPanel<T extends NumericType<T>> extends JPanel {

  /* List of Images in bdvui Viewer Panel*/
  private JLabel imageListLabel;

  /* List of Images in bdvui Viewer Panel*/
  private JComboBox imageList;

  /* MinScaleLabel */
  private JLabel minScaleLabel;

  /* MinScaleTextField */
  private JTextField minScaleTextField;

  /* StepScaleLabel */
  private JLabel stepScaleLabel;

  /* StepScaleTextField */
  private JTextField stepScaleTextField;

  /* MaxScaleLabel */
  private JLabel maxScaleLabel;

  /* MaxScaleTextField */
  private JTextField maxScaleTextField;

  /* Bright Blobs Checkbox*/
  private JCheckBox brightBlobsCheckBox;

  /* Anisotropic Sampling Axis*/
  private JLabel anisotropicAxisLabel;

  /* Anisotropic Sampling DropDownMenu*/
  private JComboBox anisotropicAxisComboBox;

  /* Anisotropic Sampling Factor*/
  private JLabel anisotropicFactorLabel;

  /* Anisotropic Sampling Factor TextField*/
  private JTextField anisotropicFactorTextfield;

  /* Run Button*/
  private JButton runButton;

  private EventService es;

  private CommandService cs;

  private List<EventSubscriber<?>> subs;

  private ThreadService ts;

  private OpService ops;

  private double minScale;

  private double stepScale;

  private double maxScale;

  private boolean brightBlobs;

  private int axis;

  private double samplingFactor;

  public DetectSphericalStructuresPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);


    imageList = new JComboBox();
    imageListLabel = new JLabel("Image");
    minScaleLabel = new JLabel("Min Scale");
    minScaleTextField = new JTextField("5", 3);

    stepScaleLabel = new JLabel("Step Scale");
    stepScaleTextField = new JTextField("1", 3);

    maxScaleLabel = new JLabel("Max Scale");
    maxScaleTextField = new JTextField("9", 3);

    brightBlobsCheckBox = new JCheckBox("Bright Blobs (?)", true);

    anisotropicAxisLabel = new JLabel("Anisotropic Axis");
    String[] axes = new String[]{"X", "Y", "Z"};
    anisotropicAxisComboBox = new JComboBox(axes);

    anisotropicFactorLabel = new JLabel("Anisotropic Sampling Factor");
    anisotropicFactorTextfield = new JTextField("1", 3);
    runButton = new JButton("Run");

    setupPanel();
    setupRunButton(bdvUI);
    setupImageList(bdvUI);
    setupAnisotropicAxisList(bdvUI);

    this.add(imageListLabel);
    this.add(imageList, "wrap");
    this.add(minScaleLabel);
    this.add(minScaleTextField, "wrap");
    this.add(stepScaleLabel);
    this.add(stepScaleTextField, "wrap");
    this.add(maxScaleLabel);
    this.add(maxScaleTextField, "wrap");

    this.add(anisotropicAxisLabel);
    this.add(anisotropicAxisComboBox, "wrap");
    this.add(anisotropicFactorLabel);
    this.add(anisotropicFactorTextfield, "wrap");
    this.add(brightBlobsCheckBox, "wrap");
    this.add(runButton);


  }

  private void setupPanel() {
    this.setBackground(Color.white);

    this.setLayout(new MigLayout("fillx", "", ""));
  }

  private void setupRunButton(final BigDataViewerUI bdvUI) {
    runButton.setBackground(Color.WHITE);
    runButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == runButton) {
          ts.getExecutorService().submit(() -> detectFeatures(bdvUI));
        }
      }

    });
  }


  public void setupImageList(final BigDataViewerUI bdvUI) {

    imageList.setBackground(Color.WHITE);
    final List<String> sourceNames = new ArrayList<>();

    bdvUI.getBDVHandlePanel().getViewerPanel().getState().getSources().forEach(new Consumer<SourceState<?>>() {

      @Override
      public void accept(SourceState<?> t) {
        sourceNames.add(t.getSpimSource().getName());
      }
    });

    String[] sourceNamesArray = sourceNames.toArray(new String[0]);
    for (int i = 0; i < sourceNamesArray.length; i++) {
      imageList.addItem(sourceNamesArray[i]);
    }

  }

  private void setupAnisotropicAxisList(final BigDataViewerUI bdvui) {
    anisotropicAxisComboBox.setBackground(Color.WHITE);

  }

  public void addImage(String s) {
    imageList.addItem(s);

  }

  private void detectFeatures(BigDataViewerUI bdvUI) {
    runButton.setEnabled(false);

    Context context = new Context();
    IOService ioService = context.service(IOService.class);
    Img<T> img = null;
    try {
      img = (Img<T>) ioService.open(String.valueOf(imageList.getSelectedItem()));
    } catch (IOException e) {
      e.printStackTrace();
    }


    minScale = Double.parseDouble(minScaleTextField.getText());
    stepScale = Double.parseDouble(stepScaleTextField.getText());
    maxScale = Double.parseDouble(maxScaleTextField.getText());
    brightBlobs = brightBlobsCheckBox.isSelected();
    axis = anisotropicAxisComboBox.getSelectedIndex();
    samplingFactor = Double.parseDouble(anisotropicFactorTextfield.getText());


    GenericTable resultsTable = new BlobDetection(img, minScale, maxScale, stepScale, brightBlobs, axis, samplingFactor, ops).getResultsTable();
    /*Step One: Convert Table to List of Points*/
    final List<RichFeaturePoint> localMinima = convertTableToList(resultsTable);

    /*Step Two: Find Otsu Threshold Value on the new List, so obtained*/
    SampleList<FloatType> localMinimaResponse = createIterableList(resultsTable.get("Value"));
    Histogram1d<FloatType> hist = ops.image().histogram(localMinimaResponse);
    float otsuThreshold = (float) ops.threshold().otsu(hist).getRealFloat();

    /*Step Three; Create an Overlay based on Thresholded Local Minima Locations*/
    int numDims = img.numDimensions();
    MyOverlay myOverlay = createBDVOverlay(localMinima, otsuThreshold, numDims);
    myOverlay.setThresholdedLocalMinima(otsuThreshold);
    //myOverlay.setStartThreshold(otsuThreshold);

    /*Step four: Add the Overlay to Viewer and Edit features Panel and also to List of Overlays*/
    BdvOverlaySource overlaySource = bdvUI.addOverlay(myOverlay, String.valueOf(imageList.getSelectedItem()), Color.white);
    bdvUI.getBDVHandlePanel().getViewerPanel().requestRepaint();
    bdvUI.getOverlayPanel().add(String.valueOf(imageList.getSelectedItem()));
    bdvUI.getSetThresholdPanel().add(String.valueOf(imageList.getSelectedItem()));
    bdvUI.getSelectNucleiPanel().add(String.valueOf(imageList.getSelectedItem()));
    bdvUI.getScrollToDetectionsPanel().add(String.valueOf(imageList.getSelectedItem()));
    bdvUI.addToMyOverlayList(myOverlay);

    /*Step Five: Mention the number of features which were detected*/
    bdvUI.getLogger().info("Number of features detected is equal to: " + myOverlay.getNumberBlobs());
    runButton.setEnabled(true);
  }

  private List<RichFeaturePoint> convertTableToList(GenericTable table) {
    final List<RichFeaturePoint> localMinima = new ArrayList<>();
    Column xColumn = table.get("X");
    Column yColumn = table.get("Y");
    Column zColumn = table.get("Z");
    Column sliceColumn = table.get("Slice");
    Column redColumn = table.get("Red");
    Column greenColumn = table.get("Green");
    Column blueColumn = table.get("Blue");
    Column valueColumn = table.get("Value");
    Iterator<Integer> xIterator = xColumn.iterator();
    Iterator<Integer> yIterator = yColumn.iterator();
    Iterator<Integer> zIterator = zColumn.iterator();
    Iterator<Integer> sliceIterator = sliceColumn.iterator();
    Iterator<Float> valueIterator = valueColumn.iterator();
    Iterator<Integer> redIterator = redColumn.iterator();
    Iterator<Integer> greenIterator = greenColumn.iterator();
    Iterator<Integer> blueIterator = blueColumn.iterator();

    while (xIterator.hasNext()) {
      int x = xIterator.next().intValue();
      int y = yIterator.next().intValue();
      int z = zIterator.next().intValue();
      int slice = sliceIterator.next().intValue();
      float value = valueIterator.next().floatValue();
      int red = redIterator.next().intValue();
      int green = greenIterator.next().intValue();
      int blue = blueIterator.next().intValue();
      RichFeaturePoint richFeaturePoint = new RichFeaturePoint(x, y, z, slice, value, red, green, blue);
      localMinima.add(richFeaturePoint);
    }
    return localMinima;
  }

  private SampleList<FloatType> createIterableList(final Column column) {
    final Iterator<Float> iterator = column.iterator();
    final List<FloatType> imageResponse = new ArrayList<>();
    while (iterator.hasNext()) {
      imageResponse.add(new FloatType(iterator.next().floatValue()));
    }

    return new SampleList<>(imageResponse);
  }

  private MyOverlay createBDVOverlay(List<RichFeaturePoint> localMinima, float otsuThreshold, int numDims) {
    MyOverlay myOverlay = new MyOverlay(axis, samplingFactor, minScale, stepScale, maxScale, localMinima, otsuThreshold, otsuThreshold, numDims);
    return myOverlay;
  }


}
