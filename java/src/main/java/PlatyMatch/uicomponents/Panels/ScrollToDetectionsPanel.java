package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.MyOverlay;
import net.imagej.ops.OpService;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * ScrollToDetectionsPanel allows adjusting the view such that a specific nucleus is centered on the viewer's screen.
 *
 * @param <T>
 */
public class ScrollToDetectionsPanel<I extends IntegerType<I>, T extends NumericType<T>, L> extends JPanel {

  private JButton goButton;
  private JTextField nucleusIdTextField;

  private JLabel overlayLabel;

  private JComboBox overlayComboBox;

  private EventService es;

  private CommandService cs;

  private ThreadService ts;

  private OpService ops;

  private List<EventSubscriber<?>> subs;

  private MyOverlay myOverlay;


  public ScrollToDetectionsPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);
    goButton = new JButton("Go");
    nucleusIdTextField = new JTextField("", 3);
    overlayLabel = new JLabel("Overlay");
    overlayComboBox = new JComboBox();
    overlayComboBox.addItem("Choose Overlay");

    setupGoButton(bdvUI);
    setupPanel();
    this.add(overlayLabel);
    this.add(overlayComboBox, "wrap");
    this.add(nucleusIdTextField);
    this.add(goButton);
  }

  private void setupGoButton(BigDataViewerUI bdvUI) {
    goButton.setBackground(Color.WHITE);
    goButton.addActionListener(e -> {
      if (e.getSource() == goButton) {
        int index = overlayComboBox.getSelectedIndex();
        myOverlay = bdvUI.getOverlay(index - 1);
        int nucleusIndex = Integer.parseInt(nucleusIdTextField.getText());
        float[] loc = {myOverlay.getThresholdedLocalMinima().get(nucleusIndex).getX(), myOverlay.getThresholdedLocalMinima().get(nucleusIndex).getY(), myOverlay.getThresholdedLocalMinima().get(nucleusIndex).getZ()};
        bdvUI.getBDVHandlePanel().getViewerPanel().setCurrentViewerTransform(changeZSlice(bdvUI, loc));
        bdvUI.getBDVHandlePanel().getViewerPanel().requestRepaint();

      }
    });

  }

  private AffineTransform3D changeZSlice(BigDataViewerUI bdvUI, float[] source) {

    final AffineTransform3D transform = new AffineTransform3D();
    bdvUI.getBDVHandlePanel().getViewerPanel().getState().getViewerTransform(transform);
    System.out.println(transform);
    float[] target = new float[3];
    transform.apply(source, target);
    transform.translate( -target[0] + 0.5*bdvUI.getBDVHandlePanel().getViewerPanel().getWidth(), -target[1]+0.5*bdvUI.getBDVHandlePanel().getViewerPanel().getHeight(), -target[2] );
    return transform;


  }

  private void setupPanel() {
    this.setBackground(Color.white);

    this.setLayout(new MigLayout("fillx", "", ""));
  }

  public void add(String s) {
    overlayComboBox.addItem(s);
  }
}
