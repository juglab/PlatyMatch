/*-
 * #%L
 * UI for BigDataViewer.
 * %%
 * Copyright (C) 2017 - 2018 Tim-Oliver Buchholz
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package PlatyMatch.control;

import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BehaviourTransformEventHandler;
import bdv.BehaviourTransformEventHandlerFactory;
import bdv.BigDataViewer;
import bdv.BigDataViewerActions;
import bdv.cache.CacheControl.CacheControls;
import bdv.tools.HelpDialog;
import bdv.tools.bookmarks.Bookmarks;
import bdv.tools.bookmarks.BookmarksEditor;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.ManualTransformationEditor;
import PlatyMatch.overlay.IntensityMouseOverOverlay;
import PlatyMatch.lut.ColorTableConverter;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvOptions;
import bdv.viewer.DisplayMode;
import bdv.viewer.NavigationActions;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerPanel.AlignPlane;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.ui.TransformEventHandlerFactory;

/**
 * This is a copy of the BdvHandler from bigdataviewer-vistools but the
 * Brightness and Color panel and the Visibility and Grouping panel are removed.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 */
public class BDVHandlePanel<I extends IntegerType<I>, T extends NumericType<T>, L> extends BdvHandlePanel {

	private final ManualTransformationEditor manualTransformationEditor;

	private final Bookmarks bookmarks;

	private final BookmarksEditor bookmarksEditor;

	private final InputActionBindings keybindings;

	private final TriggerBehaviourBindings triggerbindings;

	private HelpDialog helpDialog;

	public BDVHandlePanel(final Frame dialogOwner, final BdvOptions options,
			final Map<Integer, ColorTableConverter<L>> convs) {
		super(dialogOwner, options);
		final ViewerOptions viewerOptions = options.values.getViewerOptions();
		final InputTriggerConfig inputTriggerConfig = BigDataViewer.getInputTriggerConfig(viewerOptions);

		final TransformEventHandlerFactory<AffineTransform3D> thf = viewerOptions.values
				.getTransformEventHandlerFactory();
		if (thf instanceof BehaviourTransformEventHandlerFactory)
			((BehaviourTransformEventHandlerFactory<?>) thf).setConfig(inputTriggerConfig);

		cacheControls = new CacheControls();

		viewer = new ViewerPanel(new ArrayList<>(), 1, cacheControls, viewerOptions);
		if (!options.values.hasPreferredSize())
			viewer.getDisplay().setPreferredSize(null);
			viewer.getDisplay().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(final ComponentEvent e) {
				tryInitTransform();
			}
		});

		setupAssignments = new SetupAssignments(new ArrayList<>(), 0, 65535);

		keybindings = new InputActionBindings();
		SwingUtilities.replaceUIActionMap(viewer, keybindings.getConcatenatedActionMap());
		SwingUtilities.replaceUIInputMap(viewer, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				keybindings.getConcatenatedInputMap());

		triggerbindings = new TriggerBehaviourBindings();
		final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
		mouseAndKeyHandler.setInputMap(triggerbindings.getConcatenatedInputTriggerMap());
		mouseAndKeyHandler.setBehaviourMap(triggerbindings.getConcatenatedBehaviourMap());
		viewer.getDisplay().addHandler(mouseAndKeyHandler);

		final TransformEventHandler<?> tfHandler = viewer.getDisplay().getTransformEventHandler();
		if (tfHandler instanceof BehaviourTransformEventHandler)
			((BehaviourTransformEventHandler<?>) tfHandler).install(triggerbindings);

		manualTransformationEditor = new ManualTransformationEditor(viewer, keybindings);

		bookmarks = new Bookmarks();
		bookmarksEditor = new BookmarksEditor(viewer, keybindings, bookmarks);

		final NavigationActions navactions = new NavigationActions(inputTriggerConfig);
		navactions.install(keybindings, "navigation");
		navactions.modes(viewer);
		navactions.sources(viewer);
		navactions.time(viewer);
		if (options.values.is2D())
			navactions.alignPlaneAction(viewer, AlignPlane.XY, "shift Z");
		else
			navactions.alignPlanes(viewer);

		helpDialog = new HelpDialog( dialogOwner, BDVHandlePanel.class.getResource( "Help.html" ) );

		final BigDataViewerActions bdvactions = new BigDataViewerActions(inputTriggerConfig);
		bdvactions.install(keybindings, "bdv");
		bdvactions.bookmarks(bookmarksEditor);
		bdvactions.manualTransform(manualTransformationEditor);
		bdvactions.dialog(helpDialog);

		new IntensityMouseOverOverlay<L, I>(viewer, convs);

		viewer.setDisplayMode(DisplayMode.FUSED);
	}

	@Override
	public TriggerBehaviourBindings getTriggerbindings() {
		return triggerbindings;
	}

	@Override
	public ManualTransformationEditor getManualTransformEditor() {
		return manualTransformationEditor;
	}
}
