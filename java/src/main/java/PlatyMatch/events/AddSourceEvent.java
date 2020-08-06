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
package PlatyMatch.events;

import java.awt.Color;
import java.util.Set;

import org.scijava.event.SciJavaEvent;

import bdv.BigDataViewer;
import PlatyMatch.uicomponents.MetaSourceProperties;
import net.imglib2.type.numeric.NumericType;

/**
 *
 * Add new source to {@link BigDataViewer}.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 */
public class AddSourceEvent<T extends NumericType<T>> extends SciJavaEvent {

	private final MetaSourceProperties<T> properties;

	/**
	 * Add a new source to the {@link BigDataViewer}.
	 *
	 * Note: Type and dimensions have to be provided. The GUI will not perform any
	 * information extraction.
	 *
	 * @param sourceName
	 *            a unique name
	 * @param sourceID
	 *            a unique ID
	 * @param sourceType
	 *            displayed as information
	 * @param groupNames
	 *            an image is per default part of group "All"
	 * @param color
	 *            display color
	 * @param dims
	 *            displayed as information
	 * @param visibility
	 *            of the source
	 */
	public AddSourceEvent(final String sourceName, final int sourceID, final String sourceType,
			final Set<String> groupNames, final Color color, final String dims,
			final boolean visibility, final boolean isLabeling) {
		properties = new MetaSourceProperties<>(sourceName, sourceID, sourceType, groupNames, color, dims, visibility, isLabeling);
	}

	/**
	 *
	 * @return name of the source
	 */
	public String getSourceName() {
		return properties.getSourceName();
	}

	/**
	 *
	 * @return type which will be displayed
	 */
	public String getType() {
		return properties.getSourceType();
	}

	/**
	 *
	 * @return dims which will be displayed
	 */
	public String getDims() {
		return properties.getDims();
	}


	/**
	 *
	 * @return if this source is a labeling
	 */
	public boolean isLabeling() {
		return properties.isLabeling();
	}
}
