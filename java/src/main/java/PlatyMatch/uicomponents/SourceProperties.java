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
package PlatyMatch.uicomponents;

import java.awt.Color;
import java.util.Set;

import bdv.util.BdvStackSource;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;

/**
 *
 * SourceProperties holds all information about a source added to  the UI.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 */
public class SourceProperties<T extends NumericType<T>> extends MetaSourceProperties<T> {
	private AffineTransform3D transformation;
	private BdvStackSource<T> source;

	/**
	 * Information about a specific source.
	 *
	 * @param sourceName
	 * @param sourceID
	 * @param sourceType
	 * @param groupNames
	 * @param color
	 * @param dims
	 * @param visibility
	 */
	public SourceProperties(final String sourceName, final int sourceID, final String sourceType,
			final Set<String> groupNames, final Color color, final String dims,
			final boolean visibility, final AffineTransform3D transformation, final BdvStackSource<T> source, final boolean isLabeling) {
		super(sourceName, sourceID, sourceType, groupNames, color, dims, visibility, isLabeling);
		this.transformation = transformation;
		this.source = source;
	}

	/**
	 * Get the transformation of this source.
	 *
	 * @return the transformation
	 */
	public AffineTransform3D getTransformation() {
		return transformation;
	}

	/**
	 * Set transformation of this source.
	 * @param t
	 */
	public void setTransformation(final AffineTransform3D t) {
		this.transformation = t;
	}

	/**
	 * Get the actual image source.
	 *
	 * @return the image
	 */
	public BdvStackSource<T> getSource() {
		return source;
	}
}
