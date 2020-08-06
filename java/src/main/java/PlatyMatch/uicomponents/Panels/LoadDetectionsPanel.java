package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.MyOverlay;
import PlatyMatch.nucleiDetection.RichFeaturePoint;
import bdv.util.BdvOverlaySource;
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
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * LoadDetectionsPanel allows loading minima from a previous run of (see {@link DetectSphericalStructuresPanel}).
 * All local minima are loaded and can be further edited.
 *
 * @param <T>
 */
public class LoadDetectionsPanel<T extends NumericType<T>> extends JPanel {

  /*Browse button*/
  private JButton browse;

  /*Open File button*/
  private JButton open;

  /*Text Area for filename*/
  private JTextField textField;


  /*File chooser*/
  private final JFileChooser fc = new JFileChooser("/home/manan/Desktop/11_ToMette/01_Mastodon/15_March_2019_CC_intensity");

  private EventService es;

  private CommandService cs;

  private ThreadService ts;

  private OpService ops;

  private List<EventSubscriber<?>> subs;


  public LoadDetectionsPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);
    textField = new JTextField("", 20);
    browse = new JButton("Browse");
    setupBrowseButton();
    open = new JButton("Open");

    setupOpenButton(bdvUI);
    setupPanel();
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
    final String COMMA_DELIMITER = ",";
    final String NEW_LINE_SEPARATOR = "\n";
    final String FILE_HEADER = "X, Y, Z, Scale, Value, Red, Green, Blue";
    FileReader fileReader = null;
    final String fileName = textField.getText();
    double minScale = 5;
    double stepScale = 1;
    double maxScale = 9;
    double samplingFactor = 1;
    int axis = 2;
    float startThreshold = -80;
    float currentThreshold = -80;
    List<RichFeaturePoint> localMinima = new ArrayList<>();
    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {
      String line;
      int index = 0;
      while ((line = bufferedReader.readLine()) != null) {
        if (index == 0) {
          minScale = Double.parseDouble(line);
          index++;

        } else if (index == 1) {
          stepScale = Double.parseDouble(line);
          index++;
        } else if (index == 2) {
          maxScale = Double.parseDouble(line);
          index++;
        } else if (index == 3) {
          samplingFactor = Double.parseDouble(line);
          index++;
        } else if (index == 4) {
          axis = Integer.parseInt(line);
          index++;
        } else if (index == 5) {
          startThreshold = Float.parseFloat(line);
          index++;
        } else if (index == 6) {
          currentThreshold = Float.parseFloat(line);
          index++;
        } else if (index == 7) {
          // Heading
          index++;
        } else {
          String[] values = line.split(COMMA_DELIMITER);
          localMinima.add(new RichFeaturePoint(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[3]), Float.parseFloat(values[4]), Integer.parseInt(values[5]), Integer.parseInt(values[6]), Integer.parseInt(values[7])));
        }


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
    open.setEnabled(true);

  }


  private void setupPanel() {
    this.setBackground(Color.white);
    this.setLayout(new MigLayout("fillx", "", ""));
  }


}
