package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.MyOverlay;
import bdv.viewer.ViewerPanel;
import net.imagej.ops.OpService;
import net.imglib2.RealPoint;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * SelectNucleiPanel allows user-interactivity with the predicted nuclei centroids.
 * Nuclei can be either selected and deleted, or new nuclei can be added.
 *
 * @param <T>
 */
public class SelectNucleiPanel<T extends NumericType<T>> extends JPanel {
  private JLabel overlayLabel;
  private JComboBox overlayComboBox;
  private JButton selectNucleiButton;
  private JButton addNucleiButton;
  private JButton deleteNucleiButton;
  private EventService es;
  private CommandService cs;
  private ThreadService ts;
  private OpService ops;
  private List<EventSubscriber<?>> subs;
    private MyOverlay myOverlay;
    private ViewerPanel viewer;

    public SelectNucleiPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
        this.es = es;
        this.cs = cs;
        this.ts = ts;
        this.ops = ops;
        subs = this.es.subscribe(this);
        overlayLabel = new JLabel("Overlay");
        overlayComboBox = new JComboBox();
        overlayComboBox.addItem("Choose Overlay");
        selectNucleiButton = new JButton("Select");
        addNucleiButton = new JButton("Add");
        deleteNucleiButton = new JButton("Delete");
        setupSelectNucleiButton(bdvUI);
        setupAddNucleiButton(bdvUI);
        setupDeleteNucleiButton(bdvUI);
        setupPanel();
        this.add(overlayLabel);
        this.add(overlayComboBox, "wrap");
        this.add(selectNucleiButton, "wrap");
        this.add(deleteNucleiButton, "wrap");
        this.add(addNucleiButton);

    }

    private void setupPanel() {
        this.setBackground(Color.white);
        this.setBorder(new TitledBorder("Select Nuclei"));
        this.setLayout(new MigLayout("fillx", "", ""));
    }

    private void setupSelectNucleiButton(BigDataViewerUI bdvUI) {
        selectNucleiButton.setBackground(Color.white);
        selectNucleiButton.addActionListener(e -> {
            if (e.getSource() == selectNucleiButton) {
                int index = overlayComboBox.getSelectedIndex();
                myOverlay = bdvUI.getOverlay(index - 1);
                viewer = bdvUI.getBDVHandlePanel().getViewerPanel();
                viewer.getDisplay().addHandler(new MyListener());

            }
        });


    }

    public void add(String fileName) {
        overlayComboBox.addItem(fileName);
    }

    private class MyListener extends MouseInputAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            toggleSelected(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            updateStart(e);
        }

        @Override
        public void mouseDragged(final MouseEvent e) {
            updateSize(e);
        }
        @Override
        public void mouseReleased(MouseEvent e) {
            updateSize(e);
        }


    }

    private void toggleSelected(MouseEvent e) {
        final RealPoint pos = new RealPoint(3);
        viewer.getGlobalMouseCoordinates(pos);
        System.out.println(pos);
        myOverlay.toggleSelected(pos);
        viewer.requestRepaint();

    }

    void updateStart(MouseEvent e) {
        final RealPoint pos = new RealPoint(3);
        viewer.getGlobalMouseCoordinates(pos);
        myOverlay.setXY(pos);

    }

    void updateSize(MouseEvent e) {
        final RealPoint pos = new RealPoint(3);
        viewer.getGlobalMouseCoordinates(pos);
        System.out.println(pos);
        myOverlay.setWidthHeight(pos);
        viewer.requestRepaint();



    }

    // Correct the code below so that it is shorter and such that it is not hard coded which axis requires the sampling factor
    // note pos is already in the isotropic space, hence the sampling factor is not applied to it!

  //not optimized for 2D
    private void setupAddNucleiButton(BigDataViewerUI bdvUI) {
        this.addNucleiButton.setBackground(Color.WHITE);
        addNucleiButton.addActionListener(e -> {
            if (e.getSource() == addNucleiButton) {
                myOverlay.add(myOverlay.getAddedPoint());
                viewer.requestRepaint();
            }
        });


    }

    private void setupDeleteNucleiButton(BigDataViewerUI bdvUI) {
        this.deleteNucleiButton.setBackground(Color.WHITE);
        deleteNucleiButton.addActionListener(e -> {
            if (e.getSource() == deleteNucleiButton) {
                myOverlay.deleteSelected();
                viewer.requestRepaint();
            }
        });


    }
}
