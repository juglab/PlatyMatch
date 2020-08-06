package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.MyOverlay;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.ops.OpService;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * SetThresholdPanel allows adjusting the threshold of laplacian of gaussian convolution response to display a subset of the
 * local minima (see {@link PlatyMatch.nucleiDetection.LocalMinima}. For example, setting the threshold to zero displays all the minima)
 *
 * @param <T>
 */
public class SetThresholdPanel<T extends NumericType<T>> extends JPanel {

  private JLabel overlayLabel;

  private JComboBox overlayComboBox;

  private JLabel thresholdLabel;

  private JSlider thresholdSlider;

  private JCheckBox NMSCheckbox;

    private JButton runButton;

    private JButton saveResultsButton;

    private JButton saveImageButton;

    private EventService es;

    private CommandService cs;

    private ThreadService ts;

    private OpService ops;

    private List<EventSubscriber<?>> subs;

    private MyOverlay myOverlay;


    public SetThresholdPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
        this.es = es;
        this.cs = cs;
        this.ts = ts;
        this.ops = ops;
        subs = this.es.subscribe(this);
        overlayLabel = new JLabel("Overlay");
        overlayComboBox = new JComboBox();
        overlayComboBox.addItem("Choose Overlay");
        thresholdLabel = new JLabel("Threshold");
        thresholdSlider = new JSlider(JSlider.HORIZONTAL, -100, 0, -80);
        thresholdSlider.setBackground(Color.WHITE);
        NMSCheckbox = new JCheckBox("Suppress Intersections", true);
        runButton = new JButton("Run");
        saveResultsButton = new JButton("Save Results");
        saveImageButton = new JButton("Save Image");
        setupRunButton(bdvUI);
        setupOverlayComboBox(bdvUI);
        setupSaveResultsButton(bdvUI);
        setupSaveImageButton(bdvUI);
        setupPanel();

        this.add(overlayLabel);
        this.add(overlayComboBox, "wrap");
        this.add(thresholdLabel);
        this.add(thresholdSlider, "wrap");
        this.add(NMSCheckbox, "wrap");
        this.add(runButton, "wrap");
        this.add(saveResultsButton, "wrap");
        this.add(saveImageButton);

    }

    private void setupSaveResultsButton(BigDataViewerUI bdvUI) {
        saveResultsButton.setBackground(Color.WHITE);
        saveResultsButton.setEnabled(false);
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        saveResultsButton.addActionListener(e -> {
            if (e.getSource() == saveResultsButton) {
                int returnVal = fc.showOpenDialog(null);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    myOverlay.writeNuclei(file.getAbsolutePath());
                    myOverlay.writeToCSV(file.getAbsolutePath());
                    myOverlay.createTGMM(file.getAbsolutePath());

                }

            }
        });

    }


    private void setupSaveImageButton(BigDataViewerUI bdvUI) {
        saveImageButton.setBackground(Color.WHITE);
        saveImageButton.setEnabled(false);
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        saveImageButton.addActionListener(e -> {
            if (e.getSource() == saveImageButton) {
                int returnVal = fc.showOpenDialog(null);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    Context context = new Context();
                    DatasetIOService ioService = context.service(DatasetIOService.class);
                    try {
                        Dataset img = null;
                        img = ioService.open(String.valueOf(overlayComboBox.getSelectedItem()));
                        if (img.numDimensions() == 3) {
                            long[] dimensionsLong = new long[3];
                            dimensionsLong[0] = img.getWidth();
                            dimensionsLong[1] = img.getHeight();
                            dimensionsLong[2] = img.getDepth();
                            myOverlay.createImage(file.getAbsolutePath(), dimensionsLong);
                        } else if (img.numDimensions() == 2) {
                            long[] dimensionsLong = new long[2];
                            dimensionsLong[0] = img.getWidth();
                            dimensionsLong[1] = img.getHeight();
                            myOverlay.createImage(file.getAbsolutePath(), dimensionsLong);
                        }


                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }


                }

            }
        });

    }

    private void repaintThresholdSlider(BigDataViewerUI bdvUI) {

        int index = overlayComboBox.getSelectedIndex() - 1;
        if (thresholdSlider.getChangeListeners().length > 0) {
            thresholdSlider.removeChangeListener(thresholdSlider.getChangeListeners()[0]);
        }
        MyOverlay myOverlay = bdvUI.getOverlay(index);


        thresholdSlider.setMinimum(Math.round(myOverlay.getStartThreshold()));
        thresholdSlider.setValue(Math.round(myOverlay.getStartThreshold()));
        thresholdSlider.setMaximum((int) Math.round(0.25 * myOverlay.getStartThreshold()));
        thresholdSlider.addChangeListener(ce -> {
            final int currentThreshold = ((JSlider) ce.getSource()).getValue();
            myOverlay.setThresholdedLocalMinima(currentThreshold);
            myOverlay.setCurrentThreshold(currentThreshold);
            bdvUI.getBDVHandlePanel().getViewerPanel().requestRepaint();
            bdvUI.getLogger().info("Number of features detected is equal to: " + myOverlay.getNumberBlobs() + " @ threshold equal to :" + currentThreshold);


        });

    }


    private void setupRunButton(BigDataViewerUI bdvUI) {
        runButton.setBackground(Color.WHITE);

        runButton.addActionListener(e -> {
            if (e.getSource() == runButton) {
                ts.getExecutorService().submit((Callable<T>) () -> {

                    int index = overlayComboBox.getSelectedIndex();
                    myOverlay = bdvUI.getOverlay(index - 1);
                    if (NMSCheckbox.isSelected()) {
                        myOverlay.eliminateInsignificantNuclei2((float) 0.05);
                        bdvUI.getBDVHandlePanel().getViewerPanel().requestRepaint();
                    }
                    saveImageButton.setEnabled(true);
                    saveResultsButton.setEnabled(true);

                    return null;
                });
            }
        });

    }


    private void setupOverlayComboBox(BigDataViewerUI bdvUI) {

        overlayComboBox.setBackground(Color.WHITE);
        overlayComboBox.addActionListener(e -> {
            if (e.getSource() == overlayComboBox) {
                ts.getExecutorService().submit(() -> repaintThresholdSlider(bdvUI));
            }
        });
    }


    private void setupPanel() {
        this.setBackground(Color.white);
        this.setBorder(new TitledBorder("Set Threshold"));
        this.setLayout(new MigLayout("fillx", "", ""));
    }

    public void add(String s) {
        overlayComboBox.addItem(s);
    }

}
