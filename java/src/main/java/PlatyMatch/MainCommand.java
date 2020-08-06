package PlatyMatch;
import bdv.util.BdvOptions;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import org.scijava.ui.UIService;
import org.scijava.ui.swing.widget.SwingInputHarvester;

import javax.swing.*;

@Plugin(type = Command.class, menuPath = "Plugins > PlatyMatch")
public class MainCommand<T extends RealType<T> & Type<T>> implements Command {

  @Parameter
  CommandService cs;

  @Parameter
  OpService ops;

  @Parameter
  ThreadService ts;

  @Parameter
  LogService log;

  @Parameter
  IOService io;

  @Parameter
  UIService ui;

  private SwingInputHarvester sih;


  @Override
  public void run() {

    final BigDataViewerUI bdvUI = createBDV();
    sih = new SwingInputHarvester();
    cs.context().inject(sih);

  }

  private BigDataViewerUI createBDV() {
    final JFrame frame = new JFrame("PlatyMatch");
    final BigDataViewerUI bdvUI = new BigDataViewerUI<>(frame, ops.context(),
      BdvOptions.options().preferredSize(800, 800));
    frame.add(bdvUI.getPanel());
    frame.pack();
    frame.setVisible(true);
    return bdvUI;
  }

  public static void main(String... args) {
    ImageJ imageJ = new ImageJ();
    imageJ.ui().showUI();
  }


}
