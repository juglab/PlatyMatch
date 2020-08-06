package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.uicomponents.SelectionAndGroupingTabs;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

/**
 * OverlayPanel accumulates the various overlays generated during the analysis of the images.
 *
 * @param <T>
 */
public class OverlayPanel<T extends NumericType<T>> extends JPanel {

  private JComboBox overlayListComboBox;

  private EventService es;

  private List<EventSubscriber<?>> subs;

  private ImageIcon visibleIcon;

  private ImageIcon notVisibleIcon;

  private ImageIcon visibleIconSmall;

  private ImageIcon notVisibleIconSmall;

  private JLabel overlayVisibilityLabel;


  public OverlayPanel(final EventService es, final BigDataViewerUI bdvUI) {
    this.es = es;
    subs = this.es.subscribe(this);
    overlayListComboBox = new JComboBox();

    visibleIcon = new ImageIcon(SelectionAndGroupingTabs.class.getResource("visible.png"), "Visible");
    notVisibleIcon = new ImageIcon(SelectionAndGroupingTabs.class.getResource("notVisible.png"), "Not Visible");

    visibleIconSmall = new ImageIcon(SelectionAndGroupingTabs.class.getResource("visible_small.png"), "Visible");
    notVisibleIconSmall = new ImageIcon(SelectionAndGroupingTabs.class.getResource("notVisible_small.png"),
      "Not Visible");


    setupComboBox();
    setupPanel();
    setupOverlayVisibilityLabel(bdvUI);


    this.add(overlayListComboBox);
    this.add(overlayVisibilityLabel, "wrap");


  }

  private void setupPanel() {
    this.setBackground(Color.white);

    this.setLayout(new MigLayout("fillx", "", ""));
  }


  private void setupComboBox() {
    overlayListComboBox.setBackground(Color.WHITE);

  }

  public void add(String s) {
    overlayListComboBox.addItem(s);

  }

  private void setupOverlayVisibilityLabel(BigDataViewerUI bdvUI) {
    overlayVisibilityLabel = new JLabel(visibleIcon);
    overlayVisibilityLabel.setBackground(Color.WHITE);

    //bdvUI.getBDVHandlePanel().getViewerPanel().getVisibilityAndGrouping().getSources().get(0).setActive(false);
    overlayVisibilityLabel.addMouseListener(new MouseListener() {

      @Override
      public void mouseReleased(MouseEvent e) {

        if (bdvUI.getBDVHandlePanel().getViewerPanel().getVisibilityAndGrouping().getSources().get(2 * overlayListComboBox.getSelectedIndex() + 1).isActive()) {
          bdvUI.getBDVHandlePanel().getViewerPanel().getVisibilityAndGrouping().getSources().get(2 * overlayListComboBox.getSelectedIndex() + 1).setActive(false);
          bdvUI.getBDVHandlePanel().getViewerPanel().repaint();
        } else {
          bdvUI.getBDVHandlePanel().getViewerPanel().getVisibilityAndGrouping().getSources().get(2 * overlayListComboBox.getSelectedIndex() + 1).setActive(true);
          bdvUI.getBDVHandlePanel().getViewerPanel().repaint();
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        // nothing
      }

      @Override
      public void mouseExited(MouseEvent e) {
        // nothing
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        // nothing
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        // nothing
      }
    });
  }

}

