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

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BehaviourTransformEventHandler;
import bdv.tools.transformation.ManualTransformActiveListener;
import bdv.tools.transformation.ManualTransformationEditor;
import bdv.tools.transformation.TransformedSource;
import PlatyMatch.lut.BDVColorTable;
import PlatyMatch.lut.ColorTableConverter;
import PlatyMatch.lut.SegmentsColorTable;
import PlatyMatch.events.AddSourceEvent;
import PlatyMatch.events.ColorChangeEvent;
import PlatyMatch.events.DisplayModeFuseActiveEvent;
import PlatyMatch.events.DisplayModeGroupActiveEvent;
import PlatyMatch.events.GroupAddNewEvent;
import PlatyMatch.events.GroupRemoveEvent;
import PlatyMatch.events.GroupSelectedEvent;
import PlatyMatch.events.GroupVisibilityChangeEvent;
import PlatyMatch.events.InterpolationModeChangeEvent;
import PlatyMatch.events.LockTransformationEvent;
import PlatyMatch.events.ManualTransformEnableEvent;
import PlatyMatch.events.RemoveSourceEvent;
import PlatyMatch.events.RemoveSourceFromGroupEvent;
import PlatyMatch.events.ResetTransformationEvent;
import PlatyMatch.events.SourceAddedToGroupEvent;
import PlatyMatch.events.SourceSelectionChangeEvent;
import PlatyMatch.events.SourceVisibilityChangeEvent;
import PlatyMatch.uicomponents.MetaSourceProperties;
import PlatyMatch.uicomponents.SelectionAndGroupingTabs;
import PlatyMatch.uicomponents.SourceProperties;
import PlatyMatch.util.RandomMissingColorHandler;
import bdv.util.Affine3DHelpers;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.VisibilityAndGrouping.Event;
import bdv.viewer.VisibilityAndGrouping.UpdateListener;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import gnu.trove.map.hash.TIntIntHashMap;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.labeling.LabelingMapping;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

/**
 * This class handles the events fired by the UI. This includes the following
 * events: - Transformation blocks (rotation, translation) - Reset of
 * transformation - Manual transformation of single source - Add sources (this
 * call is not coming from the UI, and is handled in the UI too) - Remove
 * sources (like add sources) - Add group - Remove group - Add source to group -
 * Remove source from group - Change visibility of group and source - Change
 * color of source - Change display range of selected source - Change display
 * mode (single, fused, single group, fused group) - Set interpolation mode
 *
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 * @param <T>
 * @param <L>
 */
public class BDVController<I extends IntegerType<I>, T extends NumericType<T>, L> {

	/**
	 * The bdv handle panel instance.
	 */
	private final BDVHandlePanel<I, T, L> bdvHandlePanel;

	/**
	 * Event service.
	 */
	private final EventService es;

	/**
	 * Lookup map from source-names to {@link SourceProperties}.
	 */
	private final Map<String, SourceProperties<T>> sourceLookup;

	/**
	 * Vis and grouping instance.
	 */
	private final VisibilityAndGrouping visibilityAndGrouping;

	/**
	 * Trigger behaviours.
	 */
	private final TriggerBehaviourBindings triggerBindings;

	/**
	 * Manual transformation editor instance.
	 */
	private final ManualTransformationEditor manualTransformationEditor;

	/**
	 * Global state fusion mode of BDV.
	 */
	private boolean fusedSelected = true;

	/**
	 * Global state grouping mode of BDV.
	 */
	private boolean groupingSelected = false;

	/**
	 * Global state if manual transformation mode is active.
	 */
	private boolean manualTransformationActive;

	/**
	 * Index of the group which contains all sources. The group is called "All".
	 */
	private int allGroupIdx = 0;

	/**
	 * Maps labeling-names to LUTs.
	 */
	private Map<String, SegmentsColorTable<T, L, I>> colorTables;

	/**
	 * The colortable converters for the labelings.
	 */
	private Map<Integer, ColorTableConverter<L>> converters;

	/**
	 * 2D/3D switchable transfrom event handler.
	 */
	private BehaviourTransformEventHandlerSwitchable tfh;

	/**
	 * Selection and grouping tabs.
	 */
	private SelectionAndGroupingTabs<I, T, L> selectionAndGrouping;

	/**
	 * Keep BDV and UI in synch. Use this control to add and remove sources.
	 *
	 * @param bdvHandlePanel       of a bdv instance
	 * @param selectionAndGrouping tabs
	 * @param sourceLookup         holding all sources
	 * @param converters           of the sources to display values
	 * @param es                   the event service
	 */
	public BDVController(final BDVHandlePanel<I, T, L> bdvHandlePanel,
			final SelectionAndGroupingTabs<I, T, L> selectionAndGrouping,
			final Map<String, SourceProperties<T>> sourceLookup, final Map<Integer, ColorTableConverter<L>> converters,
			final EventService es) {
		this.es = es;
		this.bdvHandlePanel = bdvHandlePanel;
		this.selectionAndGrouping = selectionAndGrouping;

		this.sourceLookup = sourceLookup;

		this.converters = converters;

		this.visibilityAndGrouping = this.bdvHandlePanel.getViewerPanel().getVisibilityAndGrouping();
		triggerBindings = this.bdvHandlePanel.getTriggerbindings();

		visibilityAndGrouping.setGroupName(allGroupIdx, "All");
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerTransform.set(-0.5, 2, 3);
		this.bdvHandlePanel.getViewerPanel().setCurrentViewerTransform(viewerTransform);

		tfh = (BehaviourTransformEventHandlerSwitchable) bdvHandlePanel.getViewerPanel().getDisplay()
				.getTransformEventHandler();

		manualTransformationEditor = bdvHandlePanel.getManualTransformEditor();
		manualTransformationEditor.addManualTransformActiveListener(new ManualTransformActiveListener() {

			@Override
			public void manualTransformActiveChanged(boolean arg0) {
				manualTransformationActive = arg0;
				if (!manualTransformationActive) {
					saveTransformation();
				}
			}
		});

		visibilityAndGrouping.addUpdateListener(new UpdateListener() {

			@Override
			public void visibilityChanged(Event e) {
				if (e.id == VisibilityAndGrouping.Event.DISPLAY_MODE_CHANGED) {
					DisplayMode mode = visibilityAndGrouping.getDisplayMode();
					if (mode.equals(DisplayMode.FUSEDGROUP)) {
						fusedSelected = true;
						groupingSelected = true;
					} else if (mode.equals(DisplayMode.FUSED)) {
						fusedSelected = true;
						groupingSelected = false;
					} else if (mode.equals(DisplayMode.GROUP)) {
						fusedSelected = false;
						groupingSelected = true;
					} else {
						fusedSelected = false;
						groupingSelected = false;
					}
				}
			}
		});

		colorTables = new HashMap<>();
	}

	public BehaviourTransformEventHandler getTransformEventHandler() {
		return tfh;
	}

	/**
	 * Switch BDV between 2D and 3D mode.
	 *
	 * @param twoDimensional BDV controls
	 */
	public void switch2D(final boolean twoDimensional) {
		if (tfh.is2D() != twoDimensional) {
			tfh.set2D(twoDimensional);
			tfh.install(bdvHandlePanel.getTriggerbindings());
			blockRotation();
			blockTranslation();
		}
	}

	/**
	 * Change color of the selected source.
	 *
	 * @param event color change
	 */
	@EventHandler
	public void colorChangedEvent(final ColorChangeEvent event) {
		final SourceProperties<T> source = sourceLookup.get(event.getSourceName());
		if (source.isLabeling()) {
			this.colorTables.get(event.getSourceName()).newColors();
		} else {
			final Color color = event.getColor();
			if (color != null) {
				source.setColor(color);
				source.getSource().setColor(createColor(color));
			}
		}

		this.bdvHandlePanel.getViewerPanel().requestRepaint();
	}

	private ARGBType createColor(final Color c) {
		return new ARGBType(ARGBType.rgba(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
	}

	/**
	 * Toggle single source transformation.
	 *
	 * @param event if manual transformation is enabled
	 */
	@EventHandler
	public void manualTransformationMode(final ManualTransformEnableEvent event) {
		manualTransformationEditor.setActive(event.isEnabled());
		if (!manualTransformationActive) {
			saveTransformation();
		}
	}

	/**
	 * Save transformation on selected source/group.
	 */
	private void saveTransformation() {
		final AffineTransform3D t = new AffineTransform3D();
		if (groupingSelected) {
			final SourceGroup currentGroup = bdvHandlePanel.getViewerPanel().getState().getSourceGroups()
					.get(bdvHandlePanel.getViewerPanel().getState().getCurrentGroup());
			final List<SourceState<?>> sources = bdvHandlePanel.getViewerPanel().getState().getSources();
			for (int id : currentGroup.getSourceIds()) {
				final Source<?> s = sources.get(id).getSpimSource();
				if (TransformedSource.class.isInstance(s)) {
					((TransformedSource<?>) s).getFixedTransform(t);
				}
				sourceLookup.get(s.getName()).setTransformation(t);
			}
		} else {
			final int currentSource = bdvHandlePanel.getViewerPanel().getState().getCurrentSource();
			if (currentSource > -1) {
				final Source<?> source = bdvHandlePanel.getViewerPanel().getState().getSources().get(currentSource)
						.getSpimSource();
				if (TransformedSource.class.isInstance(source)) {
					((TransformedSource<?>) source).getFixedTransform(t);
				}
				sourceLookup.get(bdvHandlePanel.getViewerPanel().getState().getSources().get(currentSource)
						.getSpimSource().getName()).setTransformation(t);
			}

		}
	}

	/**
	 * Add a new image to the the BDV & UI.
	 *
	 * @param img            the image
	 * @param type           of the image (only used for display purposes)
	 * @param name           of the image
	 * @param visibility     status
	 * @param assignedGroups of this image
	 * @param color          used for displaying this image
	 * @param transformation initial transformation
	 * @param min            display range
	 * @param max            display range
	 */
	private void addSource(final RandomAccessibleInterval<T> img, final String type, final String name,
			final int idxOfSource, final boolean visibility, final Set<String> assignedGroups, final Color color,
			final AffineTransform3D transformation, final double min, final double max, final boolean isLabeling) {

		final String dimString = getDimensionString(img);
		// Add source
		final BdvStackSource<T> source = BdvFunctions.show(img, name,
				BdvOptions.options().sourceTransform(transformation).addTo(bdvHandlePanel));
		sourceLookup.put(name, new SourceProperties<>(name, idxOfSource, type, assignedGroups, color, dimString,
				visibility, new AffineTransform3D(), source, isLabeling));
		source.setActive(visibility);

		double[] minMax;
		if (Double.isNaN(min) || Double.isNaN(max)) {
			minMax = getMinMax(img);
		} else {
			minMax = new double[] { min, max };
		}

		source.setDisplayRangeBounds(minMax[0], minMax[1]);
		source.setDisplayRange(minMax[0], minMax[1]);
		if (color != null)
			source.setColor(createColor(color));

		if (isLabeling)
			colorTables.get(name).setSourceID(idxOfSource);

		visibilityAndGrouping.setCurrentSource(idxOfSource);
		visibilityAndGrouping.setSourceActive(idxOfSource, visibility);
		visibilityAndGrouping.addSourceToGroup(idxOfSource, allGroupIdx);

		// Manage new groups
		final List<String> groupNames = new ArrayList<>();
		bdvHandlePanel.getViewerPanel().getState().getSourceGroups().forEach(g -> groupNames.add(g.getName()));
		for (final String groupName : assignedGroups) {
			if (!groupNames.contains(groupName)) {
				bdvHandlePanel.getViewerPanel().addGroup(new SourceGroup(groupName));
				groupNames.add(groupName);

			}
			final int idxOfGroup = groupNames.indexOf(groupName);
			visibilityAndGrouping.setGroupName(idxOfGroup, groupName);
			visibilityAndGrouping.addSourceToGroup(idxOfSource, idxOfGroup);
			visibilityAndGrouping.setGroupActive(idxOfGroup, visibility);
		}

		final MetaSourceProperties<T> p = new MetaSourceProperties<T>(name, idxOfSource, type, assignedGroups, color,
				dimString, visibility, isLabeling);
		this.selectionAndGrouping.addSource(p);

		// Notify UI
		es.publish(new AddSourceEvent<T>(name, idxOfSource, type, assignedGroups, color, dimString, visibility,
				isLabeling));

		if (idxOfSource < 1) {
			bdvHandlePanel.getViewerPanel().setCurrentViewerTransform(createViewerInitTransformation());
		}
	}

	/**
	 * Samples 1000 pixels from the initially displayed slice to determine a initial
	 * min-max guess.
	 *
	 * @param img of which the min max should be computed
	 * @return min, max values
	 */
	private double[] getMinMax(final RandomAccessibleInterval<T> img) {
		if (img.randomAccess().get() instanceof RealType) {
			double min = Double.MAX_VALUE;
			double max = Double.MIN_VALUE;
			Cursor<T> c = null;

			if (img.numDimensions() <= 2) {
				c = Views.iterable(img).cursor();
			} else {
				c = Views.hyperSlice(img, 2, (img.max(2) - img.min(2)) / 2).cursor();
			}
			if (getSliceSize(img) < 1000) {
				double current = 0;
				while (c.hasNext()) {
					current = ((RealType<?>) c.next()).getRealDouble();
					min = current < min ? current : min;
					max = current > max ? current : max;
				}
			} else {
				double current = 0;
				final long imgSize = getSliceSize(img);
				final long step = imgSize / 1000;
				long count = 0;
				c.fwd();
				while (count < imgSize) {
					current = ((RealType<?>) c.get()).getRealDouble();
					min = current < min ? current : min;
					max = current > max ? current : max;
					c.jumpFwd(step);
					count += step;
				}
			}

			return new double[] { min, max };

		} else {
			return new double[] { 0, 255 };
		}
	}

	/**
	 * Get the number of pixels in a X*Y slice.
	 *
	 * @param img to compute the slice-size from
	 * @return number of pixels
	 */
	private long getSliceSize(final RandomAccessibleInterval<T> img) {
		if (img.numDimensions() == 1) {
			return img.dimension(0);
		} else {
			return img.dimension(0) * img.dimension(1);
		}
	}

	/**
	 * Generate a unique name based on the already added image names.
	 *
	 * @param origName    of the source
	 * @param sourceNames all used names
	 * @return a unique name
	 */
	private String createUniqueName(final String origName, final List<String> sourceNames) {
		String name = origName;
		int counter = 0;
		while (sourceNames.contains(name)) {
			name = counter++ + "-" + origName;
		}
		return name;
	}

	/**
	 * Put dimensions into human readable string.
	 *
	 * Displayed in the UI.
	 *
	 * @param img to extract dimensions from
	 * @return dimensions in string form
	 */
	private String getDimensionString(final RandomAccessibleInterval<T> img) {
		long[] dims = new long[img.numDimensions()];
		img.dimensions(dims);
		String dimensions = "[";
		for (int i = 0; i < dims.length - 1; i++) {
			dimensions += dims[i] + ", ";
		}
		dimensions += dims[dims.length - 1] + "]";
		return dimensions;
	}

	/**
	 * Add source to group.
	 *
	 * @param event added source
	 */
	@EventHandler
	public void sourceAddedToGroupEvent(final SourceAddedToGroupEvent event) {
		visibilityAndGrouping.addSourceToGroup(getSourceIndex(event.getSource()), getGroupIndex(event.getGroup()));
	}

	/**
	 * Remove source from group.
	 *
	 * @param event removed source
	 */
	@EventHandler
	public void removeSourceFromGroupEvent(final RemoveSourceFromGroupEvent event) {
		visibilityAndGrouping.removeSourceFromGroup(getSourceIndex(event.getSourceName()),
				getGroupIndex(event.getGroupName()));
	}

	/**
	 * Ugly hack to get correct group index.
	 *
	 * @param groupName to get index of
	 * @return index
	 */
	private int getGroupIndex(final String groupName) {
		final List<String> groupNames = new ArrayList<>();
		bdvHandlePanel.getViewerPanel().getState().getSourceGroups().forEach(g -> groupNames.add(g.getName()));
		return groupNames.indexOf(groupName);
	}

	/**
	 * Ugly hack to get correct source index.
	 *
	 * @param sourceName to get index of
	 * @return index
	 */
	private int getSourceIndex(final String sourceName) {
		final List<String> sourceNames = new ArrayList<>();
		bdvHandlePanel.getViewerPanel().getState().getSources()
				.forEach(c -> sourceNames.add(c.getSpimSource().getName()));
		return sourceNames.indexOf(sourceName);
	}

	/**
	 * Change display mode.
	 *
	 * @param event display mode
	 */
	@EventHandler
	public void fusedSelectionChanged(final DisplayModeFuseActiveEvent event) {
		fusedSelected = event.isActive();
		if (fusedSelected && groupingSelected) {
			visibilityAndGrouping.setDisplayMode(DisplayMode.FUSEDGROUP);
		} else if (fusedSelected && !groupingSelected) {
			visibilityAndGrouping.setDisplayMode(DisplayMode.FUSED);
		} else if (!fusedSelected && groupingSelected) {
			visibilityAndGrouping.setDisplayMode(DisplayMode.GROUP);
		} else {
			visibilityAndGrouping.setDisplayMode(DisplayMode.SINGLE);
		}
	}

	/**
	 * Set selected group.
	 *
	 * @param event group selection
	 */
	@EventHandler
	public void groupingSelectionChanged(final DisplayModeGroupActiveEvent event) {
		groupingSelected = event.isActive();
		if (fusedSelected && groupingSelected) {
			visibilityAndGrouping.setDisplayMode(DisplayMode.FUSEDGROUP);
		} else if (fusedSelected && !groupingSelected) {
			visibilityAndGrouping.setDisplayMode(DisplayMode.FUSED);
		} else if (!fusedSelected && groupingSelected) {
			visibilityAndGrouping.setDisplayMode(DisplayMode.GROUP);
		} else {
			visibilityAndGrouping.setDisplayMode(DisplayMode.SINGLE);
		}
	}

	/**
	 * Change visibility of selected source.
	 *
	 * @param event source visibility
	 */
	@EventHandler
	public void changeSourceVisibility(final SourceVisibilityChangeEvent event) {
		visibilityAndGrouping.setSourceActive(getSourceIndex(event.getSourceName()), event.isVisible());
		sourceLookup.get(event.getSourceName()).setVisible(event.isVisible());
		bdvHandlePanel.getViewerPanel().requestRepaint();
	}

	/**
	 * Change visibility of selected group.
	 *
	 * @param event group visibility
	 */
	@EventHandler
	public void changeGroupVisibility(final GroupVisibilityChangeEvent event) {
		visibilityAndGrouping.setGroupActive(getGroupIndex(event.getGroupName()), event.isVisible());
		bdvHandlePanel.getViewerPanel().requestRepaint();
	}

	/**
	 * Reset transformation.
	 *
	 * Note: If single source transformation is active, only the selected source is
	 * reset.
	 *
	 * @param event reset transformation
	 */
	@EventHandler
	public void onResetTransformation(final ResetTransformationEvent event) {
		if (manualTransformationActive) {
			manualTransformationEditor.reset();
		} else {
			final int numSources = bdvHandlePanel.getViewerPanel().getState().numSources();
			for (int i = 0; i < numSources; ++i) {
				final Source<?> source = bdvHandlePanel.getViewerPanel().getState().getSources().get(i).getSpimSource();
				if (TransformedSource.class.isInstance(source)) {
					((TransformedSource<?>) source).setFixedTransform(new AffineTransform3D());
					((TransformedSource<?>) source).setIncrementalTransform(new AffineTransform3D());
					((TransformedSource<?>) source)
							.setIncrementalTransform(sourceLookup.get(source.getName()).getTransformation());
				}
			}
			bdvHandlePanel.getViewerPanel().setCurrentViewerTransform(createViewerInitTransformation());
		}
	}

	/**
	 * Compute initial transformation.
	 *
	 * @return the transformation.
	 */
	private AffineTransform3D createViewerInitTransformation() {
		final double cX = bdvHandlePanel.getViewerPanel().getWidth() / 2d;
		final double cY = bdvHandlePanel.getViewerPanel().getHeight() / 2d;
		ViewerState state = bdvHandlePanel.getViewerPanel().getState();
		if (state.getCurrentSource() < 0) {
			return new AffineTransform3D();
		}
		final Source<?> source = state.getSources().get(state.getCurrentSource()).getSpimSource();
		final int timepoint = state.getCurrentTimepoint();

		final AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSourceTransform(timepoint, 0, sourceTransform);

		final Interval sourceInterval = source.getSource(timepoint, 0);
		final double sX0 = sourceInterval.min(0);
		final double sX1 = sourceInterval.max(0);
		final double sY0 = sourceInterval.min(1);
		final double sY1 = sourceInterval.max(1);
		final double sZ0 = sourceInterval.min(2);
		final double sZ1 = sourceInterval.max(2);
		final double sX = (sX0 + sX1 + 1) / 2;
		final double sY = (sY0 + sY1 + 1) / 2;
		final double sZ = (int) (sZ0 + sZ1 + 1) / 2;

		final double[][] m = new double[3][4];

		// rotation
		final double[] qSource = new double[4];
		final double[] qViewer = new double[4];
		Affine3DHelpers.extractApproximateRotationAffine(sourceTransform, qSource, 2);
		LinAlgHelpers.quaternionInvert(qSource, qViewer);
		LinAlgHelpers.quaternionToR(qViewer, m);

		// translation
		final double[] centerSource = new double[] { sX, sY, sZ };
		final double[] centerGlobal = new double[3];
		final double[] translation = new double[3];
		sourceTransform.apply(centerSource, centerGlobal);
		LinAlgHelpers.quaternionApply(qViewer, centerGlobal, translation);
		LinAlgHelpers.scale(translation, -1, translation);
		LinAlgHelpers.setCol(3, translation, m);

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerTransform.set(m);

		if ((sX1 - sX0) >= (sY1 - sY0)) {
			viewerTransform.scale(bdvHandlePanel.getViewerPanel().getWidth() / (4.0 * (sX1 - sX0)));
		} else {
			viewerTransform.scale(bdvHandlePanel.getViewerPanel().getHeight() / (4.0 * (sY1 - sY0)));
		}

		// window center offset
		viewerTransform.set(viewerTransform.get(0, 3) + cX, 0, 3);
		viewerTransform.set(viewerTransform.get(1, 3) + cY, 1, 3);

		return viewerTransform;
	}

	/**
	 * Block mouse drag to suppress rotation.
	 *
	 * @param event block rotation
	 */
	@EventHandler
	public void onBlockRotation(final LockTransformationEvent event) {
		if (!event.allowTranslation()) {
			blockTranslation();
		} else {
			triggerBindings.removeBehaviourMap("blockTranslation");
		}

		if (!event.allowRotation()) {
			blockRotation();
		} else {
			triggerBindings.removeBehaviourMap("blockRotation");
		}
	}

	private void blockTranslation() {
		final BehaviourMap blockTranslation = new BehaviourMap();
		blockTranslation.put("drag translate", new Behaviour() {
		});

		// 2D
		blockTranslation.put("2d drag translate", new Behaviour() {
		});

		triggerBindings.addBehaviourMap("blockTranslation", blockTranslation);
	}

	private void blockRotation() {
		final BehaviourMap blockRotation = new BehaviourMap();
		blockRotation.put("rotate left", new Behaviour() {
		});
		blockRotation.put("rotate left slow", new Behaviour() {
		});
		blockRotation.put("rotate left fast", new Behaviour() {
		});

		blockRotation.put("rotate right", new Behaviour() {
		});
		blockRotation.put("rotate right slow", new Behaviour() {
		});
		blockRotation.put("rotate right fast", new Behaviour() {
		});

		blockRotation.put("drag rotate", new Behaviour() {
		});
		blockRotation.put("drag rotate slow", new Behaviour() {
		});
		blockRotation.put("drag rotate fast", new Behaviour() {
		});

		// 2D
		blockRotation.put("2d drag rotate", new Behaviour() {
		});
		blockRotation.put("2d scroll rotate", new Behaviour() {
		});
		blockRotation.put("2d scroll rotate slow", new Behaviour() {
		});
		blockRotation.put("2d scroll rotate fast", new Behaviour() {
		});
		blockRotation.put("2d scroll translate", new Behaviour() {
		});
		blockRotation.put("2d rotate left", new Behaviour() {
		});
		blockRotation.put("2d rotate right", new Behaviour() {
		});
		triggerBindings.addBehaviourMap("blockRotation", blockRotation);
	}

	/**
	 * Change source selection.
	 *
	 * @param event source selection
	 */
	@EventHandler
	public void currentSelectionChanged(final SourceSelectionChangeEvent<T> event) {
		if (event.getSource() != null)
			visibilityAndGrouping.setCurrentSource(getSourceIndex(event.getSource().getSourceName()));
	}

	/**
	 * Change group selection.
	 *
	 * @param event group selection
	 */
	@EventHandler
	public void groupSelected(final GroupSelectedEvent event) {
		visibilityAndGrouping.setCurrentGroup(getGroupIndex(event.getGroupName()));
	}

	/**
	 * Add a new group.
	 *
	 * @param event add new group
	 */
	@EventHandler
	public synchronized void addNewGroup(final GroupAddNewEvent event) {
		bdvHandlePanel.getViewerPanel().addGroup(new SourceGroup(event.getGroupName()));
		int idx = bdvHandlePanel.getViewerPanel().getState().getSourceGroups().size() - 1;
		visibilityAndGrouping.setGroupName(idx, event.getGroupName());
		bdvHandlePanel.getViewerPanel().getState().setCurrentGroup(idx);
	}

	/**
	 * Remove source.
	 *
	 */
	public synchronized void removeSource(final String sourceName) {
		int idx = getSourceIndex(sourceName);
		SourceProperties<T> sp = sourceLookup.get(sourceName);
		sp.getSource().removeFromBdv();
		sourceLookup.remove(sourceName);

		final Map<Integer, ColorTableConverter<L>> tmp = new HashMap<>();
		sourceLookup.values().forEach(p -> {
			int sourceID = p.getSourceID();
			if (sourceID > idx) {
				p.setSourceID(sourceID - 1);
				if (p.isLabeling()) {
					tmp.put(sourceID - 1, converters.get(sourceID));
					colorTables.get(p.getSourceName()).setSourceID(sourceID - 1);
				}
			} else {
				if (p.isLabeling()) {
					tmp.put(sourceID, converters.get(sourceID));
				}
			}
		});
		converters.clear();
		tmp.keySet().forEach(i -> converters.put(i, tmp.get(i)));

		colorTables.remove(sourceName);

		this.selectionAndGrouping.removeSource(sourceName);

		es.publish(new RemoveSourceEvent(sourceName, idx));

	}

	/**
	 * Remove group.
	 *
	 * @param event remove group
	 */
	@EventHandler
	public void removeGroupEvent(final GroupRemoveEvent event) {
		bdvHandlePanel.getViewerPanel().getState().getSourceGroups().forEach(new Consumer<SourceGroup>() {

			@Override
			public void accept(SourceGroup t) {
				if (t.getName().equals(event.getGroupName())) {
					bdvHandlePanel.getViewerPanel().getState().removeGroup(t);
				}
			}
		});
	}

	/**
	 * Change interpolation mode.
	 *
	 * @param event change interpolation
	 */
	@EventHandler
	public void interpolationModeChanged(final InterpolationModeChangeEvent event) {
		bdvHandlePanel.getViewerPanel().setInterpolation(event.getInterpolationMode());
	}

	/**
	 * Get the manaual transformation editor. This component allows single
	 * source/group transformation.
	 *
	 * @return manual transformation editor
	 */
	public ManualTransformationEditor getManualTransformationEditor() {
		return manualTransformationEditor;
	}

	/**
	 * Add a new labeling image to the BDV - UI.
	 *
	 * @param imgLab     index image of the labeling
	 * @param type       of the image, displayed in the information box
	 * @param origName   of the source
	 * @param visible    if the source should be visible initially
	 * @param groupNames of the groups to which this source should be added
	 * @param t          init transformation
	 * @param lut        colormap
	 */
	@SuppressWarnings("unchecked")
	public void addLabeling(final RandomAccessibleInterval<LabelingType<L>> imgLab, final String type, final String origName,
			final boolean visible, final Set<String> groupNames, final AffineTransform3D t, final TIntIntHashMap lut) {
		// Get all sources
		final List<String> sourceNames = new ArrayList<>();
		bdvHandlePanel.getViewerPanel().getState().getSources().forEach(new Consumer<SourceState<?>>() {

			@Override
			public void accept(SourceState<?> t) {
				sourceNames.add(t.getSpimSource().getName());
			}
		});
		String name = createUniqueName(origName, sourceNames);
		sourceNames.add(name);
		final int idxOfSource = sourceNames.indexOf(name);
		final LabelingMapping<L> mapping = Util.getTypeFromInterval(imgLab).getMapping();
		final ColorTableConverter<L> conv = new ColorTableConverter<L>(mapping);
		final SegmentsColorTable<T, L, I> segmentColorTable = new BDVColorTable<>(mapping, conv,
				new RandomMissingColorHandler(), es);
		conv.addColorTable(segmentColorTable);

		segmentColorTable.fillLut();
		segmentColorTable.update();
		segmentColorTable.setViewerPanel(bdvHandlePanel.getViewerPanel());
		colorTables.put(name, segmentColorTable);

		addSource((RandomAccessibleInterval<T>) Converters.convert(imgLab, conv, new ARGBType()), type, name, idxOfSource,
				visible, groupNames, null, t, 0, 255, true);

		converters.put(sourceLookup.get(name).getSourceID(), conv);
	}

	/**
	 * Add an image to BDV and UI.
	 *
	 * @param img        to add
	 * @param type       of the image
	 * @param origName   of the source to display
	 * @param visible    status of the source
	 * @param groupNames to which this image should be added
	 * @param color      of the displayed image
	 * @param t          initial transformation
	 * @param min        display value if either min or max is NaN min max will be
	 *                   extracted from data
	 * @param max        display value
	 */
	public void addImg(final RandomAccessibleInterval<T> img, final String type, final String origName, final boolean visible,
			final Set<String> groupNames, final Color color, final AffineTransform3D t, final double min, final double max) {
		// Get all sources
		final List<String> sourceNames = new ArrayList<>();
		bdvHandlePanel.getViewerPanel().getState().getSources().forEach(new Consumer<SourceState<?>>() {

			@Override
			public void accept(SourceState<?> t) {
				sourceNames.add(t.getSpimSource().getName());
			}
		});

		// Check if source is already present
		String name = createUniqueName(origName, sourceNames);
		// Set source visibility and add to the "all" group
		sourceNames.add(name);
		final int idxOfSource = sourceNames.indexOf(name);
		addSource(img, type, name, idxOfSource, visible, groupNames, color, t, min, max, false);
	}

	/**
	 *
	 * @return number of sources (active and inactive)
	 */
	public int getNumSources() {
		return sourceLookup.keySet().size();
	}

	public BDVHandlePanel<I, T, L> getBDVHandlePanel() {
		return bdvHandlePanel;
	}
}
