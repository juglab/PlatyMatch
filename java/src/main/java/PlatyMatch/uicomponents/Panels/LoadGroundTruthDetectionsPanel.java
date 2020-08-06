package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.MyOverlay;
import PlatyMatch.nucleiDetection.RichFeaturePoint;
import bdv.util.BdvOverlaySource;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.ops.OpService;
import net.imglib2.Point;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.thread.ThreadService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LoadGroundTruthDetectionsPanel allows loading Ground Truth or curated nuclei detections.
 * Here the file should contain 4 columns : index, x, y and z
 *
 * @param <T>
 */
public class LoadGroundTruthDetectionsPanel<T extends NumericType<T>> extends JPanel {


  /*SamplingFactor TextField*/

  private JLabel samplingFactorLabel;
  private JTextField samplingFactorTextField;

  /*Browse button*/
  private JButton browse;

    /*Open File button*/
    private JButton open;

    /*Text Area for filename*/
    private JTextField textField;

    /*File chooser*/
    private final JFileChooser fc = new JFileChooser("/home/manan/Desktop/03_Datasets/Manan/TransModalRegistration/September162019/16hpf/04_Pax6/");

    private EventService es;

    private CommandService cs;

    private OpService ops;

    private ThreadService ts;

    private List<EventSubscriber<?>> subs;

    private List<Point> groundTruthLocalMinima;


    private JComboBox imageComboBox;


    public LoadGroundTruthDetectionsPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
        this.es = es;
        this.cs = cs;
        this.ts = ts;
        this.ops = ops;
        subs = this.es.subscribe(this);
        samplingFactorLabel= new JLabel("Sampling Factor");
        samplingFactorTextField = new JTextField("1", 3);
        textField = new JTextField("", 20);
        browse = new JButton("Browse");
        imageComboBox = new JComboBox();
        setupBrowseButton();
        open = new JButton("Open");
        setupOpenButton(bdvUI);
        setupPanel();

        this.add(samplingFactorLabel);
        this.add(samplingFactorTextField, "wrap");
        this.add(textField);
        this.add(browse, "wrap");
        this.add(open, "wrap");


    }


    private void setupBrowseButton() {
        browse.setBackground(Color.WHITE);
        browse.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == browse) {
                    int returnVal = fc.showOpenDialog(null);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();
                        textField.setText(file.getAbsolutePath());
                    }

                }
            }
        });
    }


    private void setupOpenButton(final BigDataViewerUI bdvui) {
        open.setBackground(Color.WHITE);
        open.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == open) {
                    createOverlay(bdvui);

                }
            }


        });

    }


    private void createOverlay(final BigDataViewerUI bdvUI) {
        open.setEnabled(false);
        final String COMMA_DELIMITER = " ";
        final String fileName = textField.getText();
        double minScale = 5;
        double stepScale = 1;
        double maxScale = 9;
        double samplingFactor = 1;
        int axis = 2;
        float startThreshold = -80;
        float currentThreshold = -80;
        List<RichFeaturePoint> localMinima = new ArrayList<>();
        groundTruthLocalMinima= new ArrayList<>();
        Random rand = new Random();
        RichFeaturePoint temp;
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {
            String line;
            int index = 0;
            while ((line = bufferedReader.readLine()) != null) {

                String[] values = line.split(COMMA_DELIMITER);
                groundTruthLocalMinima.add(new Point((int) Double.parseDouble(values[1]), (int) Double.parseDouble(values[2]), (int) Double.parseDouble(values[3])));
                temp=new RichFeaturePoint((int) Double.parseDouble(values[1]), (int) Double.parseDouble(values[2]), (int) Double.parseDouble(values[3]), 0, -100, rand.nextInt(255), rand.nextInt(255), rand.nextInt(255));
                temp.setLabel((int) Double.parseDouble(values[0]));
                localMinima.add(temp);

            }

        } catch (FileNotFoundException e) {
          System.out.println("File not found!");
          e.printStackTrace();
        } catch (IOException e) {
          System.out.println("File read problem/IO!");
          e.printStackTrace();
        }


      MyOverlay myOverlay = new MyOverlay(axis, samplingFactor, minScale, stepScale, maxScale, localMinima, startThreshold, currentThreshold, 3);
      myOverlay.setThresholdedLocalMinima(currentThreshold);

      BdvOverlaySource overlaySource = bdvUI.addOverlay(myOverlay, fileName, Color.white);
      bdvUI.getBDVHandlePanel().getViewerPanel().requestRepaint();
      bdvUI.getOverlayPanel().add(fileName);
      bdvUI.getSetThresholdPanel().add(fileName);
      bdvUI.getSelectNucleiPanel().add(fileName);
      bdvUI.getScrollToDetectionsPanel().add(fileName);

      bdvUI.addToMyOverlayList(myOverlay);
      long[] dimensionsLong = new long[3];
      Context context = new Context();
      DatasetIOService ioService = context.service(DatasetIOService.class);
      Dataset img;
      try {
        img = ioService.open(String.valueOf(imageComboBox.getSelectedItem()));
        dimensionsLong[0] = img.getWidth();
        dimensionsLong[1] = img.getHeight();
        dimensionsLong[2] = img.getDepth();
        myOverlay.createImage("/home/manan/Desktop", dimensionsLong);
      } catch (IOException e) {
        e.printStackTrace();
      }

      open.setEnabled(true);

    }

    private void setupPanel() {
        this.setBackground(Color.white);

        this.setLayout(new MigLayout("fillx", "", ""));
    }


        public void addImage(String text) {
            imageComboBox.addItem(text);

        }

}



