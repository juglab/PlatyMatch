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
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;

import PlatyMatch.events.AddSourceEvent;
import PlatyMatch.events.SourceSelectionChangeEvent;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;

/**
 *
 * A panel displaying information about the currently selected source.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 */
public class InformationPanel<T extends NumericType<T>> extends JPanel {

	private static final long serialVersionUID = 1L;

	/**
	 * Display type information.
	 */
	private final JLabel type;

	/**
	 * Display dimensionality.
	 */
	private final JLabel dimensions;

	private EventService es;

	private List<EventSubscriber<?>> subs;

	/**
	 * An information panel displaying information about the currently selected
	 * source.
	 *
	 * @param es the event-service
	 */
	public InformationPanel(final EventService es) {

		this.es = es;
		subs = this.es.subscribe(this);

		this.setLayout(new MigLayout("fillx", "[][grow]", ""));
		this.setBackground(Color.white);
		this.add(new JLabel("Type:"));

		type = new JLabel("type");
		type.setBackground(Color.WHITE);
		this.add(type, "growx, wrap");

		this.add(new JLabel("Dimensions:"));

		dimensions = new JLabel("dimensions");
		dimensions.setBackground(Color.WHITE);
		this.add(dimensions, "growx");
	}

	@EventHandler
	public void currentSelectionChanged(final SourceSelectionChangeEvent<T> e) {
		if (e.getSource() != null) {
			type.setText(e.getSource().getSourceType());
			dimensions.setText(e.getSource().getDims());
		}
	}

	@EventHandler
	public void addSourceEvent(final AddSourceEvent<T> e) {
		type.setText(e.getType());
		dimensions.setText(e.getDims());
	}

	public void unsubscribe() {
		this.es.unsubscribe(subs);
	}
}
