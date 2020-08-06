package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
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
import java.util.List;

public class HomePanel<T extends NumericType<T>> extends JPanel {
  private JButton detectNucleiButton;
  private JButton performShapeContextButton;
  private JButton transformButton;
  private EventService es;

  private CommandService cs;

  private List<EventSubscriber<?>> subs;

  private ThreadService ts;

  private OpService ops;

  public HomePanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);

    detectNucleiButton = new JButton("Detect Nuclei");
    performShapeContextButton = new JButton("Coarse Alignment");
    transformButton = new JButton("Fine Alignment");
    setupPanel();
    setupDetectNucleiButton(bdvUI);
    setupPerformShapeContextButton(bdvUI);
    setupTransformButton(bdvUI);
    this.add(detectNucleiButton, "wrap");
    this.add(performShapeContextButton, "wrap");
    this.add(transformButton);
  }

  private void setupDetectNucleiButton(BigDataViewerUI bdvUI) {
    detectNucleiButton.setBackground(Color.WHITE);
    detectNucleiButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == detectNucleiButton) {
          bdvUI.getControlPanel().removeAllCards(false);

          bdvUI.getControlPanel().add(bdvUI.getHomeButton(), "wrap");
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getLoggingPanelLabel()), false, bdvUI.getLoggingPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getVisAndGroupLabel()), false, bdvUI.getVisAndGroup());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getOverlayPanelLabel()), false, bdvUI.getOverlayPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getGlobalControlsLabel()), false, bdvUI.getGlobalControls());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getOpenImagePanelLabel()), false, bdvUI.getOpenFilePanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getDetectSphericalStructuresPanelLabel()), false, bdvUI.getDetectSphericalStructuresPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getEditDetectionsLabel()), false, bdvUI.getSelectNucleiPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getScrollToDetectionsPanelLabel()), false, bdvUI.getScrollToDetectionsPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getLoadDetectionsPanelLabel()), false, bdvUI.getLoadDetectionsPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getLoadGroundTruthDetectionsPanelLabel()), false, bdvUI.getGroundTruthDetectionsPanel());
        }
      }

    });
  }

  private void setupPerformShapeContextButton(BigDataViewerUI bdvUI) {
    performShapeContextButton.setBackground(Color.WHITE);
    performShapeContextButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == performShapeContextButton) {
          bdvUI.getControlPanel().removeAllCards(false);

          bdvUI.getControlPanel().add(bdvUI.getHomeButton(), "wrap");
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getLoggingPanelLabel()), false, bdvUI.getLoggingPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getVisAndGroupLabel()), false, bdvUI.getVisAndGroup());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getGlobalControlsLabel()), false, bdvUI.getGlobalControls());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getOpenDetectionsPanelLabel()), false, bdvUI.getOpenDetectionsPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getPerformShapeContext_ICPPanelLabel()), false, bdvUI.getPeformShapeContext_ICPPanel());
        }
      }

    });

  }

  private void setupTransformButton(BigDataViewerUI bdvUI) {
    transformButton.setBackground(Color.WHITE);
    transformButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == transformButton) {
          bdvUI.getControlPanel().removeAllCards(false);

          bdvUI.getControlPanel().add(bdvUI.getHomeButton(), "wrap");
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getLoggingPanelLabel()), false, bdvUI.getLoggingPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getVisAndGroupLabel()), false, bdvUI.getVisAndGroup());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getGlobalControlsLabel()), false, bdvUI.getGlobalControls());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getOpenImageDetectionLandmarkPanelLabel()), false, bdvUI.getOpenImageDetectionLandmarkPanel());
          bdvUI.getControlPanel().addNewCard(new JLabel(bdvUI.getPerformICPPanelLabel()), false, bdvUI.getPerformICPPanel());
        }
      }

    });
  }

  private void setupPanel() {
    this.setBackground(Color.white);
    this.setLayout(new MigLayout("fillx", "", ""));
  }


}
