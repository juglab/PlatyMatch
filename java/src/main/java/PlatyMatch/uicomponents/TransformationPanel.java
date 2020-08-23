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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;

import bdv.BigDataViewer;
import bdv.tools.transformation.ManualTransformActiveListener;
import PlatyMatch.control.BDVController;
import PlatyMatch.events.DisplayModeFuseActiveEvent;
import PlatyMatch.events.LockTransformationEvent;
import PlatyMatch.events.ManualTransformEnableEvent;
import PlatyMatch.events.ResetTransformationEvent;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.miginfocom.swing.MigLayout;

/**
 *
 * Offering the different transformation option of the {@link BigDataViewer}.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 * @param <T>
 */
public class TransformationPanel<I extends IntegerType<I>, T extends NumericType<T>, L> extends JPanel {

	private static final long serialVersionUID = 1L;

	/**
	 * Reset transformation button.
	 */
	private JButton reset;

	/**
	 * Enable individual transformation of sources/groups.
	 */
	private JCheckBox individualTransformation;

	private EventService es;

	private List<EventSubscriber<?>> subs;

	/**
	 * Panel holding the controls of the viewer and individual transformation.
	 *
	 * @param es         the event-service
	 * @param controller the BDV controller
	 */
	public TransformationPanel(final EventService es, final BDVController<I, T, L> controller) {
		this.es = es;
		subs = this.es.subscribe(this);
		setupPanel();

		final JCheckBox translation = new JCheckBox("Allow Translation", true);
		final JCheckBox rotation = new JCheckBox("Allow Rotation", true);
		setupTranslationCheckBox(es, translation, rotation);
		setupRotationCheckBox(es, translation, rotation);

		reset = new JButton("Reset Viewer Transformation");
		setupResetButton(es);

		individualTransformation = new JCheckBox("Manipulate Initial Transformation");
		setupManualTransformationCheckBox(es);

		controller.getManualTransformationEditor()
				.addManualTransformActiveListener(new ManualTransformActiveListener() {

					@Override
					public void manualTransformActiveChanged(boolean active) {
						manualTransformation(active);
					}
				});

		controller.getManualTransformationEditor()
				.addManualTransformActiveListener(new ManualTransformActiveListener() {

					@Override
					public void manualTransformActiveChanged(boolean active) {
						individualTransformation.setSelected(active);
					}
				});

		translation.doClick();
		rotation.doClick();

		this.add(translation, "wrap");
		this.add(rotation, "wrap");
		this.add(individualTransformation, "growx, wrap");
		this.add(reset);
	}

	private void setupPanel() {
		this.setBackground(Color.white);
		this.setBorder(new TitledBorder("Transformation"));
		this.setLayout(new MigLayout("fillx", "", ""));
	}

	private void setupManualTransformationCheckBox(final EventService es) {
		individualTransformation.setToolTipText("Only possible if all active sources are shown.");
		individualTransformation.setBackground(Color.white);
		individualTransformation.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent ev) {
				if (ev.getSource() == individualTransformation) {
					es.publish(new ManualTransformEnableEvent(individualTransformation.isSelected()));
				}
			}
		});
	}

	private void setupResetButton(final EventService es) {
		reset.setBackground(Color.WHITE);
		reset.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == reset) {
					es.publish(new ResetTransformationEvent());
				}
			}
		});
	}

	private void setupRotationCheckBox(final EventService es, final JCheckBox translation, final JCheckBox rotation) {
		rotation.setBackground(Color.WHITE);
		rotation.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == rotation) {
					es.publish(new LockTransformationEvent(translation.isSelected(), rotation.isSelected()));
				}
			}
		});
	}

	private void setupTranslationCheckBox(final EventService es, final JCheckBox translation,
			final JCheckBox rotation) {
		translation.setBackground(Color.WHITE);
		translation.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == translation) {
					es.publish(new LockTransformationEvent(translation.isSelected(), rotation.isSelected()));
				}
			}
		});
	}

	@EventHandler
	public void manualTransformationEvent(final ManualTransformEnableEvent e) {
		manualTransformation(e.isEnabled());
	}

	@EventHandler
	public void fusedSelectedChanged(final DisplayModeFuseActiveEvent e) {
		individualTransformation.setEnabled(e.isActive());
	}

	/**
	 * Change text dependent on transformation handler.
	 *
	 * @param active
	 */
	private void manualTransformation(final boolean active) {
		if (active) {
			reset.setText("Reset to Initial Transformation");
		} else {
			reset.setText("Reset Viewer Transformation");
		}
	}

	public void unsubscribe() {
		this.es.unsubscribe(subs);
	}
}
