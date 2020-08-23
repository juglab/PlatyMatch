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

import java.util.Set;

import net.imglib2.roi.labeling.LabelingMapping;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

import org.scijava.event.EventService;

import PlatyMatch.util.RandomMissingColorHandler;

/**
 * Colortable for labelings.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 */
public class BDVColorTable<T extends NumericType<T>, L, I extends IntegerType<I>>
		extends SegmentsColorTable<T, L, I> {

	private RandomMissingColorHandler colorHandler;

	/**
	 * A new colortable
	 *
	 * @param mapping of the labeling
	 * @param converter combining the LUTs
	 * @param colorHandler generationg colors
	 * @param es event service
	 */
	public BDVColorTable(final LabelingMapping<L> mapping, ColorTableConverter<L> converter,
			final RandomMissingColorHandler colorHandler, final EventService es) {
		super(mapping, converter, es);
		this.colorHandler = colorHandler;
	}

	@Override
	public void fillLut() {
		for (int i = 0; i < labelingMapping.numSets(); i++) {
			final Set<L> labelSet = labelingMapping.labelsAtIndex(i);
			for (L l : labelSet) {
				final int color = colorHandler.getColor(l);
				lut[i] = ARGBType.rgba(ARGBType.red(color), ARGBType.green(color), ARGBType.blue(color), alpha);
			}
		}
	}

	@Override
	public void newColors() {
		RandomMissingColorHandler.resetColorMap();
		fillLut();
		super.update();
	}

}
