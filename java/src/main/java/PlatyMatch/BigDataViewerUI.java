/*-
 * #%L
 * UI for BigDataViewer.
 * %%
 * Copyright (C) 2017 - 2018 Tim-Oliver Buchholz
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package PlatyMatch;

import PlatyMatch.control.BDVController;
import PlatyMatch.control.BDVHandlePanel;
import PlatyMatch.control.BehaviourTransformEventHandlerSwitchable;
import PlatyMatch.lut.ColorTableConverter;
import PlatyMatch.nucleiDetection.MyOverlay;
import PlatyMatch.nucleiDetection.RichSpot;
import PlatyMatch.projector.AccumulateProjectorAlphaBlendingARGB;
import PlatyMatch.uicomponents.*;
import PlatyMatch.uicomponents.Panels.*;
import bdv.BigDataViewer;
import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import gnu.trove.map.hash.TIntIntHashMap;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.log.DefaultLogger;
import org.scijava.log.LogLevel;
import org.scijava.log.LogSource;
import org.scijava.log.Logger;
import org.scijava.thread.ThreadService;
import org.scijava.ui.swing.console.LoggingPanel;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * UI for the {@link BigDataViewer} mapping all functionality to UI-Components.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 * @author Manan Lalit
 */
public class BigDataViewerUI<I extends IntegerType<I>, T extends NumericType<T>, L> {

  private static final String CONTROL_CARD_NAME = "Viewer Settings";
  private static final String SELECTION_CARD_NAME = "Selection";
  private static final String LOG_CARD_NAME = "Log";
  private static final String DETECT_SPHERICAL_STRUCTURES = "Detect Spherical Structures";
  private static final String EDIT_DETECTIONS_CARD_NAME = "Edit Detections";
  private static final String SCROLL_TO_DETECTIONS_CARD_NAME = "Scroll To Detection";
  private static final String LOAD_DETECTIONS_CARD_NAME = "Load Detections";
  private static final String LOAD_GROUNDTRUTH_DETECTIONS_CARD_NAME = "Load Ground Truth Detections";
  private static final String OPEN_IMAGE_CARD_NAME = "Open Image";
  private static final String OPEN_DETECTIONS_CARD_NAME = "Open Detections";
  private static final String PERFORM_SHAPE_CONTEXT_ICP_CARD_NAME = "Perform Shape Context";
  private static final String OVERLAY_FILE_CARD_NAME = "List Overlays";
  private static final String OPEN_IMAGE_DETECTION_LANDMARK_CARD_NAME = "Open Detections and Landmarks";
  private static final String PERFORM_ICP_CARD_NAME = "Perform ICP & Hungarian Assignment";

  /**
   * Splitpane holding the BDV and UI.
   */
  private final JSplitPane splitPane;

  /**
   * Controller which keeps BDV and UI in synch.
   */
  private BDVController<I, T, L> bdv;

  /**
   * Map from source names to {@link SourceProperties}.
   */
  private final Map<String, SourceProperties<T>> sourceLookup = new HashMap<>();

  /**
   * Map from label-source-index to {@link ColorTableConverter}.
   */
  private Map<Integer, ColorTableConverter<L>> converters;

  /* Panel holding the control UI-Components */
  private CardPanel controlsPanel; // TODO


  /*
   * The main panel.
   */
  private JPanel panel;


  /* Detect Nuclei Panel*/
  private DetectSphericalStructuresPanel<T> detectSphericalStructuresPanel;

  /* Edit Detections Panel*/
  private SetThresholdPanel<T> setThresholdPanel;
  private SelectNucleiPanel<T> selectNucleiPanel;

  private ScrollToDetectionsPanel<I, T, L> scrollToDetectionsPanel;

  /* Load Detections Panel */
  private LoadDetectionsPanel<T> loadDetectionsPanel;

  /* Load Ground Truth Panel*/
  private LoadGroundTruthDetectionsPanel<T> loadGroundTruthDetectionsPanel;

  /* Load Open File Panel */
  private OpenImagePanel<T> openImagePanel;

  /* Panel holding the transformation manipulation options */
  private TransformationPanel<I, T, L> transformationPanel;

  /* Source & Group selection panel */
  private SelectionAndGroupingTabs<I, T, L> selectionAndGrouping;

  /* The event service */
  private EventService es;

  /* The command service */
  private CommandService cs;

  /* The thread service */
  private ThreadService ts;

  /* The Ops service */
  private OpService ops;

  /**
   * List of all subscribers.
   */
  private List<EventSubscriber<?>> subs;


  private LoggingPanel loggingPanel;

  private Logger logger;

  private OverlayPanel overlayPanel;

  private HomePanel homePanel;

  private JPanel visAndGroup;
  private JPanel globalControls;

  JButton homeButton;

  private List<RichSpot> sourceDetections;
  private List<RichSpot> targetDetections;
  private List<RichSpot> sourceLandmarks;
  private List<RichSpot> targetLandmarks;
  private double[][] sourceLandmarkTransform;
  private double[][] targetLandmarkTransform;

  private List<MyOverlay> myOverlayList = new ArrayList<>();

  private OpenDetectionsPanel openDetectionsPanel;
  private PerformShapeContextPanel performShapeContext_Panel;
  private OpenDetectionLandmarkPanel openDetectionLandmarkPanel;
  private PerformICPPanel performICPPanel;
  /**
   * BDV {@link AccumulateProjectorFactory} which generates
   * {@link AccumulateProjectorAlphaBlendingARGB} which adds image sources
   * together and blends labeling sources on top with alpha-blending.
   */
  final AccumulateProjectorFactory<ARGBType> myFactory = new AccumulateProjectorFactory<ARGBType>() {

    @Override
    public synchronized AccumulateProjectorAlphaBlendingARGB createAccumulateProjector(
      final ArrayList<VolatileProjector> sourceProjectors, final ArrayList<Source<?>> sources,
      final ArrayList<? extends RandomAccessible<? extends ARGBType>> sourceScreenImages,
      final RandomAccessibleInterval<ARGBType> targetScreenImages, final int numThreads,
      final ExecutorService executorService) {

      // lookup is true if source is a labeling
      final List<Boolean> lookup = new ArrayList<>();
      int startImgs = -1;
      int startLabs = -1;

      for (Source<?> s : sources) {
        try {
          lookup.add(new Boolean(sourceLookup.get(s.getName()).isLabeling()));
        } catch (Exception e) {
          for (SourceProperties<T> p : sourceLookup.values()) {
            System.out.println(p.getSourceName() + ", " + p.isLabeling());
          }
        }
      }

      final boolean[] labelingLookup = new boolean[lookup.size()];
      for (int i = 0; i < lookup.size(); i++) {
        final boolean b = lookup.get(i).booleanValue();
        if (startImgs < 0 && !b) {
          startImgs = i;
        }
        if (startLabs < 0 && b) {
          startLabs = i;
        }
        labelingLookup[i] = b;
      }

      return new AccumulateProjectorAlphaBlendingARGB(sourceProjectors, sourceScreenImages, targetScreenImages,
        numThreads, executorService, labelingLookup, startImgs, startLabs);
    }

  };


  /**
   * A new BigDataViewer-UI instance.
   *
   * @param frame the parent
   * @param ctx   context
   */
  public BigDataViewerUI(final JFrame frame, final Context ctx, final BdvOptions options) {
    ctx.inject(this);
    this.es = ctx.getService(EventService.class);
    this.cs = ctx.getService(CommandService.class);
    this.ts = ctx.getService(ThreadService.class);
    this.ops = ctx.getService(OpService.class);
    subs = es.subscribe(this);

    final BDVHandlePanel<I, T, L> bdvHandlePanel = createBDVHandlePanel(frame, options);

    selectionAndGrouping = new SelectionAndGroupingTabs<>(es, bdvHandlePanel);

    bdv = new BDVController<>(bdvHandlePanel, selectionAndGrouping, sourceLookup, converters, es);
    ctx.inject(bdv);

    controlsPanel = new CardPanel();  // TODO


    homePanel = new HomePanel<>(cs, es, ts, ops, this); //TODO
    controlsPanel.addNewCard(new JLabel("CORE"), true, homePanel);

    // Add log card (Card No. 0)
    loggingPanel = new LoggingPanel(ctx);
    loggingPanel.setBackground(Color.WHITE);
    loggingPanel.setTextFilterVisible(true);
    loggingPanel.setPreferredSize(new Dimension(50, 150));
    logger = new DefaultLogger(log -> {
    }, LogSource.newRoot(), LogLevel.INFO);
    logger.addLogListener(loggingPanel);
    logger.info("Hello World!");
    //controlsPanel.addNewCard(new JLabel(LOG_CARD_NAME), false, loggingPanel); //TODO


    // Add selection card (Card No. 1)
    visAndGroup = new JPanel(new MigLayout("fillx, ins 2", "[grow]", ""));
    visAndGroup.setBackground(Color.WHITE);
    visAndGroup.add(selectionAndGrouping, "growx, wrap");
    // controlsPanel.addNewCard(new JLabel(SELECTION_CARD_NAME), false, visAndGroup); //TODO

    // Add overlay card (Card No. 2)
    overlayPanel = new OverlayPanel<>(es, this);
    //controlsPanel.addNewCard(new JLabel(OVERLAY_FILE_CARD_NAME), false, overlayPanel); //TODO


    // Add control card (Card No. 2)
    globalControls = new JPanel(new MigLayout("fillx, ins 2", "[grow]", ""));
    transformationPanel = new TransformationPanel<>(es, bdv);
    globalControls.add(transformationPanel, "growx, wrap");
    globalControls.add(new InterpolationModePanel(es, bdvHandlePanel.getViewerPanel()), "growx");
    //controlsPanel.addNewCard(new JLabel(CONTROL_CARD_NAME), false, globalControls); //TODO

    // Add control card (Card No. 3)
    openImagePanel = new OpenImagePanel<>(cs, es, ts, ops, this);
    // controlsPanel.addNewCard(new JLabel(OPEN_FILE_CARD_NAME), false, openFilePanel); //TODO

    // Add Detect Nuclei Card (Card No. 4)
    detectSphericalStructuresPanel = new DetectSphericalStructuresPanel<>(cs, es, ts, ops, this);
    // controlsPanel.addNewCard(new JLabel(DETECT_SPHERICAL_STRUCTURES), false, detectSphericalStructuresPanel); // TODO

    // Add Set Threshold Panel (Card No. 5)
    final JPanel editDetections = new JPanel(new MigLayout("fillx, ins 2", "[grow]", ""));
    editDetections.setBackground(Color.WHITE);
    setThresholdPanel = new SetThresholdPanel<>(cs, es, ts, ops, this);
    selectNucleiPanel = new SelectNucleiPanel<>(cs, es, ts, ops, this);
    editDetections.add(setThresholdPanel, "wrap");
    editDetections.add(selectNucleiPanel);
    // controlsPanel.addNewCard(new JLabel(EDIT_DETECTIONS_CARD_NAME), false, editDetections); //TODO
    // Add Scroll to Detections Panel (Card No. 6)


    scrollToDetectionsPanel = new ScrollToDetectionsPanel<>(cs, es, ts, ops, this);
    //controlsPanel.addNewCard(new JLabel(SCROLL_TO_DETECTIONS_CARD_NAME), false, scrollToDetectionsPanel); //TODO

    // Add Load Detections Card (Card No. 7)
    loadDetectionsPanel = new LoadDetectionsPanel<>(cs, es, ts, ops, this);
    // controlsPanel.addNewCard(new JLabel(LOAD_DETECTIONS_CARD_NAME), false, loadDetectionsPanel); // TODO

    // Add Load Ground Truth Detections Panel (Card No. 8)
    loadGroundTruthDetectionsPanel = new LoadGroundTruthDetectionsPanel<>(cs, es, ts, ops, this);
    // controlsPanel.addNewCard(new JLabel(LOAD_GROUNDTRUTH_DETECTIONS_CARD_NAME), false, loadGroundTruthDetectionsPanel); //TODO

    openDetectionsPanel = new OpenDetectionsPanel<>(cs, es, ts, ops, this);
    performShapeContext_Panel = new PerformShapeContextPanel<>(cs, es, ts, ops, this);
    openDetectionLandmarkPanel = new OpenDetectionLandmarkPanel<>(cs, es, ts, ops, this);
    performICPPanel = new PerformICPPanel<>(cs, es, ts, ops, this);


    ImageIcon homeIcon = new ImageIcon(SelectionAndGroupingTabs.class.getResource("home.png"), "Home");
    ImageIcon homeIconDS = new ImageIcon(getScaledImage(homeIcon.getImage(), 50, 50), "smallHome");
    homeButton = new JButton(homeIconDS);
    homeButton.setBackground(Color.white);
    homeButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == homeButton) {
          controlsPanel.removeAllCards(true);
          controlsPanel.addNewCard(new JLabel("CORE"), true, homePanel);
        }
      }

    });
    splitPane = createSplitPane();
    final JScrollPane scrollPane = new JScrollPane(controlsPanel); // TODO


    scrollPane.setPreferredSize(
      new Dimension(370, bdv.getBDVHandlePanel().getViewerPanel().getPreferredSize().height));
    scrollPane.getVerticalScrollBar().setUnitIncrement(20);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);


    splitPane.setLeftComponent(bdvHandlePanel.getViewerPanel());
    splitPane.setRightComponent(scrollPane);
    splitPane.getLeftComponent().setMinimumSize(new Dimension(20, 20));
    splitPane.getLeftComponent().setPreferredSize(bdv.getBDVHandlePanel().getViewerPanel().getPreferredSize());


    panel = new JPanel();
    panel.setLayout(new MigLayout("fillx, filly, ins 0", "[grow]", "[grow]"));
    panel.add(splitPane, "growx, growy");


  }

  private Image getScaledImage(Image srcImg, int w, int h) {
    BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = resizedImg.createGraphics();

    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.drawImage(srcImg, 0, 0, w, h, null);
    g2.dispose();

    return resizedImg;
  }

  private BDVHandlePanel<I, T, L> createBDVHandlePanel(final JFrame frame, final BdvOptions options) {
    converters = new HashMap<>();
    return new BDVHandlePanel<>(frame,
      options.numSourceGroups(1)
        .transformEventHandlerFactory(BehaviourTransformEventHandlerSwitchable.factory())
        .accumulateProjectorFactory(myFactory).numRenderingThreads(1),
      converters);
  }

  /**
   * Add image to the BDV-UI.
   *
   * @param img            the image to add
   * @param type           information
   * @param name           of the source
   * @param visibility     of the source
   * @param groupNames     groups to which this source belongs
   * @param color          of the source
   * @param transformation initial transformation of the source
   * @param min            display range
   * @param max            display range
   */
  public synchronized void addImage(final RandomAccessibleInterval<T> img, final String type, final String name,
                                    final boolean visibility, final Set<String> groupNames, final Color color,
                                    final AffineTransform3D transformation, final double min, final double max) {
    bdv.addImg(img, type, name, visibility, groupNames, color, transformation, min, max);
    if (bdv.getNumSources() == 1) {
      controlsPanel.setCardActive(SELECTION_CARD_NAME, true);
      controlsPanel.toggleCardFold(SELECTION_CARD_NAME);
    }
  }

  public synchronized void addImage(final RandomAccessibleInterval<T> img, final String name, final Color color) {
    final Set<String> groupNames = new HashSet<>();
    groupNames.add("Images");
    bdv.addImg(img, img.randomAccess().get().getClass().getSimpleName(), name, true, groupNames, color,
      new AffineTransform3D(), Double.NaN, Double.NaN);
  }

  public synchronized BdvOverlaySource addOverlay(BdvOverlay overlay, final String name, Color white) {

    BdvOverlaySource overlaySource = BdvFunctions.showOverlay(overlay, name, BdvOptions.options().addTo(bdv.getBDVHandlePanel().getBdvHandle()));
    return overlaySource;
  }

  /**
   * Add labeling to the BDV-UI.
   *
   * @param imgLab         the labeling image
   * @param type           information
   * @param name           of the source
   * @param visibility     of the source
   * @param groupNames     groups to which this source belongs
   * @param transformation initial transformation of the source
   * @param lut            look up table for the labeling
   */
  public synchronized void addLabeling(RandomAccessibleInterval<LabelingType<L>> imgLab, final String type,
                                       final String name, final boolean visibility, final Set<String> groupNames,
                                       final AffineTransform3D transformation, final TIntIntHashMap lut) {
    bdv.addLabeling(imgLab, type, name, visibility, groupNames, transformation, lut);
    if (bdv.getNumSources() == 1) {
      controlsPanel.setCardActive(SELECTION_CARD_NAME, true);
      controlsPanel.toggleCardFold(SELECTION_CARD_NAME);
    }
  }

  /**
   * Remove source with given name.
   * <p>
   * Note: Removes images and labelings.
   *
   * @param sourceName to remove
   */
  public synchronized void removeSource(final String sourceName) {
    bdv.removeSource(sourceName);
    closeControlPanels();
  }

  /**
   * Toggle cards.
   */
  private void closeControlPanels() {
    if (bdv.getNumSources() <= 0) {
      controlsPanel.toggleCardFold(SELECTION_CARD_NAME);
      controlsPanel.setCardActive(SELECTION_CARD_NAME, false);
    }
  }

  /**
   * @return the splitpane with BDV and BDV-UI
   */
  public JPanel getPanel() {
    return this.panel;
  }


  /**
   * Create splitpane.
   *
   * @return splitpane
   */
  private JSplitPane createSplitPane() {
    final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    splitPane.setUI(new BasicSplitPaneUI() {
      public BasicSplitPaneDivider createDefaultDivider() {
        return new BasicSplitPaneDivider(this) {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void paint(Graphics g) {
            g.setColor(new Color(238, 238, 238));
            g.fillRect(0, 0, getSize().width, getSize().height);
            super.paint(g);
          }
        };
      }
    });

    splitPane.setBackground(new Color(31, 31, 45));
    splitPane.setDividerLocation(bdv.getBDVHandlePanel().getViewerPanel().getPreferredSize().width);
    splitPane.setResizeWeight(1.0);

    return splitPane;
  }

  /**
   * Remove all sources from BDV.
   */
  public synchronized void removeAll() {
    Set<String> keySet = new HashSet<>(sourceLookup.keySet());
    for (final String source : keySet) {
      removeSource(source);
    }
  }

  /**
   * Switch BDV between 2D and 3D mode.
   *
   * @param twoDimensional BDV mode
   */
  public void switch2D(final boolean twoDimensional) {
    bdv.switch2D(twoDimensional);
  }

  /**
   * Unsubscribe everything from the eventservice.
   */
  public synchronized void unsubscribe() {
    this.es.unsubscribe(subs);
    this.transformationPanel.unsubscribe();
    this.selectionAndGrouping.unsubscribe();
  }

  public void addCard(final JLabel name, final boolean closed, final JComponent component) {
    controlsPanel.addNewCard(name, closed, component);
  }


  public Map<String, JPanel> getCards() {
    return controlsPanel.getCards();
  }

  public BDVHandlePanel<I, T, L> getBDVHandlePanel() {
    return bdv.getBDVHandlePanel();
  }

  public BdvHandle getBDV() {
    return bdv.getBDVHandlePanel().getBdvHandle();
  }

  public Logger getLogger() {
    return logger;
  }


  public OverlayPanel getOverlayPanel() {
    return overlayPanel;
  }

  public SetThresholdPanel getSetThresholdPanel() {
    return setThresholdPanel;
  }

  public void addToMyOverlayList(MyOverlay myOverlay) {
    myOverlayList.add(myOverlay);
  }

  public MyOverlay getOverlay(int index) {
    return myOverlayList.get(index);
  }

  public LoggingPanel getLoggingPanel() {
    return this.loggingPanel;
  }

  public DetectSphericalStructuresPanel getDetectSphericalStructuresPanel() {
    return detectSphericalStructuresPanel;
  }


  public ScrollToDetectionsPanel getScrollToDetectionsPanel() {
    return scrollToDetectionsPanel;

  }

  public SelectNucleiPanel getSelectNucleiPanel() {
    return selectNucleiPanel;
  }

  public LoadGroundTruthDetectionsPanel getGroundTruthDetectionsPanel() {
    return loadGroundTruthDetectionsPanel;
  }

  public LoadDetectionsPanel getLoadDetectionsPanel() {
    return loadDetectionsPanel;
  }

  public OpenImagePanel getOpenFilePanel() {
    return openImagePanel;
  }

  public HomePanel getHomePanel() {
    return this.homePanel;
  }

  public JPanel getVisAndGroup() {
    return this.visAndGroup;
  }

  public JPanel getGlobalControls() {
    return this.globalControls;
  }

  public CardPanel getControlPanel() {
    return this.controlsPanel;
  }

  public String getDetectSphericalStructuresPanelLabel() {
    return DETECT_SPHERICAL_STRUCTURES;
  }

  public String getLoggingPanelLabel() {
    return LOG_CARD_NAME;
  }

  public String getVisAndGroupLabel() {
    return SELECTION_CARD_NAME;
  }

  public String getOverlayPanelLabel() {
    return OVERLAY_FILE_CARD_NAME;

  }

  public String getGlobalControlsLabel() {
    return CONTROL_CARD_NAME;
  }


  public String getOpenImagePanelLabel() {
    return OPEN_IMAGE_CARD_NAME;
  }

  public String getEditDetectionsLabel() {
    return EDIT_DETECTIONS_CARD_NAME;

  }

  public String getScrollToDetectionsPanelLabel() {
    return SCROLL_TO_DETECTIONS_CARD_NAME;
  }


  public String getLoadDetectionsPanelLabel() {
    return LOAD_DETECTIONS_CARD_NAME;
  }

  public String getLoadGroundTruthDetectionsPanelLabel() {
    return LOAD_GROUNDTRUTH_DETECTIONS_CARD_NAME;
  }

  public JButton getHomeButton() {
    return this.homeButton;
  }

  public String getOpenDetectionsPanelLabel() {
    return OPEN_DETECTIONS_CARD_NAME;
  }

  public String getPerformShapeContext_ICPPanelLabel() {
    return PERFORM_SHAPE_CONTEXT_ICP_CARD_NAME;
  }

  public OpenDetectionsPanel getOpenDetectionsPanel() {
    return this.openDetectionsPanel;
  }


  public PerformShapeContextPanel getPeformShapeContext_ICPPanel() {
    return this.performShapeContext_Panel;
  }

  public List<RichSpot> getSourceDetections() {
    return this.sourceDetections;
  }

  public List<RichSpot> getTargetDetections() {
    return this.targetDetections;
  }

  public void setSourceDetections(List<RichSpot> readSpots) {
    this.sourceDetections = readSpots;
  }

  public void setTargetDetections(List<RichSpot> readSpots) {
    this.targetDetections = readSpots;
  }

  public OpenDetectionLandmarkPanel getOpenImageDetectionLandmarkPanel() {
    return this.openDetectionLandmarkPanel;
  }

  public String getOpenImageDetectionLandmarkPanelLabel() {
    return OPEN_IMAGE_DETECTION_LANDMARK_CARD_NAME;
  }

  public PerformICPPanel getPerformICPPanel() {
    return this.performICPPanel;
  }

  public String getPerformICPPanelLabel() {
    return PERFORM_ICP_CARD_NAME;
  }

  public void setSourceLandmarks(List<RichSpot> readSpots) {
    this.sourceLandmarks = readSpots;
  }

  public void setTargetLandmarks(List<RichSpot> readSpots) {
    this.targetLandmarks = readSpots;
  }

  public void setSourceLandmarkTransform(double[][] readAffineMatrixCSV) {
    this.sourceLandmarkTransform = readAffineMatrixCSV;
  }


  public void setTargetLandmarkTransform(double[][] readAffineMatrixCSV) {
    this.targetLandmarkTransform = readAffineMatrixCSV;
  }

  public List<RichSpot> getSourceLandmarks() {
    return this.sourceLandmarks;
  }

  public List<RichSpot> getTargetLandmarks() {
    return this.targetLandmarks;
  }

  public double[][] getTargetLandmarkTransform() {
    return this.targetLandmarkTransform;
  }


  public double[][] getSourceLandmarkTransform() {
    return this.sourceLandmarkTransform;
  }
}
