package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
import PlatyMatch.nucleiDetection.RichSpot;
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

public class OpenDetectionsPanel<T extends NumericType<T>> extends JPanel {

  private JLabel sourceDetectionsLabel;
  private JTextField sourceDetectionsTextField;
  private JButton browseSourceDetections;
  private JLabel targetDetectionsLabel;
  private JTextField targetDetectionsTextField;
  private JButton browseTargetDetections;
  private EventService es;
  private CommandService cs;
  private ThreadService ts;
  private OpService ops;
  private List<EventSubscriber<?>> subs;


  /*File chooser*/
  private final JFileChooser fc = new JFileChooser("/home/manan/ownCloud/");

  public OpenDetectionsPanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
    this.es = es;
    this.cs = cs;
    this.ts = ts;
    this.ops = ops;
    subs = this.es.subscribe(this);
    browseSourceDetections = new JButton("Browse");
    sourceDetectionsLabel = new JLabel(" Source Detections");
    sourceDetectionsTextField = new JTextField(" ", 20);
    browseTargetDetections = new JButton("Browse");
    targetDetectionsLabel = new JLabel(" Target Detections");
    targetDetectionsTextField = new JTextField(" ", 20);
    setupBrowseSourceDetectionsButton(bdvUI);
    setupBrowseTargetDetectionsButton(bdvUI);
    setupPanel();
    this.add(sourceDetectionsTextField);
    this.add(sourceDetectionsLabel, "wrap");
    this.add(browseSourceDetections, "wrap");
    this.add(targetDetectionsTextField);
    this.add(targetDetectionsLabel, "wrap");
    this.add(browseTargetDetections, "wrap");

  }

  private void setupBrowseTargetDetectionsButton(BigDataViewerUI bdvUI) {
    browseTargetDetections.setBackground(Color.WHITE);
    browseTargetDetections.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseTargetDetections) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            targetDetectionsTextField.setText(file.getAbsolutePath());
            bdvUI.setTargetDetections(readSpots(file.getAbsolutePath())); //TODO

          }
        }
      }

    });

  }


  private void setupBrowseSourceDetectionsButton(BigDataViewerUI bdvUI) {
    browseSourceDetections.setBackground(Color.WHITE);
    browseSourceDetections.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == browseSourceDetections) {
          int returnVal = fc.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            sourceDetectionsTextField.setText(file.getAbsolutePath());
            bdvUI.setSourceDetections(readSpots(file.getAbsolutePath())); //TODO

          }
        }
      }

    });
  }


  private List<RichSpot> readSpots(String filename) {

    List<RichSpot> spots = new ArrayList<>();
    String COMMA_DELIMITER = " ";
    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(filename))) {
      String line;
      while ((line = bufferedReader.readLine()) != null) {

        String[] values = line.split(COMMA_DELIMITER);
        spots.add(new RichSpot(values[0], Double.parseDouble(values[1]), Double.parseDouble(values[2]), Double.parseDouble(values[3])));
      }

    } catch (FileNotFoundException e) {
      System.out.println("File not found!");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("File read problem/IO!");
      e.printStackTrace();
    }
    return spots;
  }


  private void setupPanel() {
    this.setBackground(Color.white);
    this.setLayout(new MigLayout("fillx", "", ""));
  }


}


