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
package PlatyMatch.lut;

import java.awt.Color;
import java.util.Random;

import org.scijava.event.EventHandler;
import org.scijava.event.EventService;

import PlatyMatch.events.DisplayRangeChangedEvent;
import bdv.util.VirtualChannels.VirtualChannel;
import bdv.viewer.ViewerPanel;
import net.imglib2.roi.labeling.LabelingMapping;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

/**
 *
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 */
public class SegmentsColorTable<T extends NumericType<T>, L, I extends IntegerType<I>>
		implements ColorTable, VirtualChannel {

	private final double GOLDEN_RATIO_CONJUGATE = 0.61803398874988749895;

	/**
	 * Index image of the labeling.
	 */
	protected final LabelingMapping<L> labelingMapping;

	/**
	 * Converter combining multiple LUTs.
	 */
	private final ColorTableConverter<L> converter;

	/**
	 * Lookup table.
	 */
	protected int[] lut;

	/**
	 * BDV viewer panel.
	 */
	private ViewerPanel viewer;

	/**
	 * Random numbers.
	 */
	private Random r;

	/**
	 * Alpha scale for aRGB colors.
	 */
	protected int alpha = 255;

	private int id;

	/**
	 * Creates a random coloring for a given labeling.
	 *
	 * @param mapping
	 *            index image of labeling
	 * @param converter which combines multiple LUTs
	 * @param es event service
	 */
	public SegmentsColorTable(final LabelingMapping<L> mapping, final ColorTableConverter<L> converter,
			final EventService es) {
		this.labelingMapping = mapping;
		es.subscribe(this);
		this.converter = converter;
		lut = new int[labelingMapping.numSets()];
		r = new Random();
	}

	@Override
	public int[] getLut() {
		return lut;
	}

	@Override
	public void setLut(final int[] lut) {
		this.lut = lut;
	}

	/**
	 * Create colors for LUT with golden ratio distribution.
	 */
	public void fillLut() {
		// Zero-Label is transparent background.
		lut[0] = ARGBType.rgba(0, 0, 0, 0);
		float h = r.nextFloat();
		for (int i = 1; i < labelingMapping.numSets(); i++) {
			h += GOLDEN_RATIO_CONJUGATE;
			h %= 1;
			lut[i] = Color.HSBtoRGB(h, 0.75f, 1f);
		}
		updateAlpha();
	}

	/**
	 * Set BDV viewer panel
	 *
	 * @param viewerPanel
	 */
	public void setViewerPanel(final ViewerPanel viewerPanel) {
		this.viewer = viewerPanel;
	}

	/**
	 * Update LUT.
	 */
	public void update() {
		converter.update();
		if (viewer != null) {
			viewer.requestRepaint();
		}
	}

	@Override
	public void newColors() {
		update();
	}

	@Override
	public void updateVisibility() {
		update();
	}

	@Override
	public void updateSetupParameters() {
		update();
	}

	/**
	 * Handle BDV Display Range changes. Instead of chaning the range, change the
	 * alpha value of the colors.
	 *
	 * @param e display range event
	 */
	@EventHandler
	public void displayRangeChanged(final DisplayRangeChangedEvent e) {
		if (e.getSourceID() == id) {
			double min = e.getMin();
			double max = e.getMax();
			alpha = (int) (max - min);
			updateAlpha();
		}
	}

	/**
	 * Set transparency via alpha in aRGB.
	 */
	private void updateAlpha() {

		for (int i = 0; i < lut.length; i++) {
			final int colorCode = lut[i];
			lut[i] = ARGBType.rgba(ARGBType.red(colorCode), ARGBType.green(colorCode), ARGBType.blue(colorCode), alpha);
		}

		lut[ 0 ] = 0;

		converter.update();
		if (viewer != null) {
			viewer.requestRepaint();
		}
	}

	public void setSourceID(final int idxOfSource) {
		id = idxOfSource;
	}
}
