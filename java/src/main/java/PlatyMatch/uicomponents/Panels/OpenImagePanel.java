package PlatyMatch.uicomponents.Panels;

import PlatyMatch.BigDataViewerUI;
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
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * OpenImagePanel allows opening a TIFF 2-D or 3-D image.
 *
 * @param <T>
 */
public class OpenImagePanel<T extends NumericType<T>> extends JPanel {

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

  private ThreadService ts;

  private OpService ops;

  private List<EventSubscriber<?>> subs;


  public OpenImagePanel(final CommandService cs, final EventService es, final ThreadService ts, final OpService ops, final BigDataViewerUI bdvUI) {
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
          openImage(bdvui);

        }
      }


    });

  }

  private void openImage(final BigDataViewerUI bdvUI) {
    open.setEnabled(false);
    Context context = new Context();
    DatasetIOService ioService = context.service(DatasetIOService.class);
    try {
      Dataset img = ioService.open(textField.getText());
      bdvUI.addImage(img, textField.getText(), Color.white);
      bdvUI.getDetectSphericalStructuresPanel().addImage(textField.getText());
      bdvUI.getGroundTruthDetectionsPanel().addImage(textField.getText());
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      open.setEnabled(true);
    }
  }


  private void setupPanel() {
    this.setBackground(Color.white);

    this.setLayout(new MigLayout("fillx", "", ""));
  }


}

