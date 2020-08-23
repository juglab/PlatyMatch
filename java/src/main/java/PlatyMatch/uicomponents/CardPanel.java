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

import net.miginfocom.swing.MigLayout;
import org.scijava.module.Module;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel which holds named Cards which can be opened and closed.
 *
 * @author Tim-Oliver Buchholz, CSBD/MPI-CBG Dresden
 *
 */
public class CardPanel extends JPanel {

	private static final long serialVersionUID = 1833879807294153532L;

	/**
	 * Name to Component lookup.
	 */
	private Map<String, JComponent> lookup = new HashMap<>();

	private Map<String, JPanel> cards = new HashMap<>();

	/**
	 * Name to Boolean is card open lookup.
	 */
	private Map<String, Boolean> openState = new HashMap<>();

	private Map<String, Boolean> enabledCard = new HashMap<>();

	/**
	 * Color scheme.
	 */
	final Color backgroundTabPanel = Color.white;
	final Color headerColor = new Color(238, 238, 238);
	final Color fontColor = Color.darkGray;

	/**
	 * Open card icon.
	 */
	private final ImageIcon downIcon;

	/**
	 * Close card icon.
	 */
	private final ImageIcon upIcon;

	/**
	 * A new card panel with no cards.
	 */
	public CardPanel() {
		super();
		downIcon = new ImageIcon(CardPanel.class.getResource("downbutton.png"), "Open Dialog.");
		upIcon = new ImageIcon(CardPanel.class.getResource("upbutton.png"), "Close Dialog.");

		this.setLayout(new MigLayout("fillx, ins 2", "", ""));
		this.setBackground(backgroundTabPanel);
	}

	/**
	 * Add a new card to this card panel.
	 *
	 * @param name
	 *            of the card
	 * @param closed
	 *            card is closed if added
	 * @param component
	 *            displayed in the card.
	 */
	public void addNewCard(final JLabel name, final boolean closed, final JComponent component) {
		if (lookup.containsKey(name.getText())) {
			throw new RuntimeException("A tab with name \"" + name + "\" already exists.");
		}

		openState.put(name.getText(), closed);
		component.setBackground(backgroundTabPanel);

		final JPanel card = new JPanel(new MigLayout("fillx, ins 4, hidemode 3", "[grow]", "[]0lp![]"));
		card.setBackground(backgroundTabPanel);

		// Holds the component with insets.
		final JPanel componentPanel = new JPanel(new MigLayout("fillx, ins 8, hidemode 3", "[grow]", "[]0lp![]"));
		componentPanel.setBackground(backgroundTabPanel);
		componentPanel.add(component, "growx");

		final JComponent header = createHeader(name, componentPanel, card);
		card.add(header, "growx, wrap");
		card.add(componentPanel, "growx");

		this.add(card, "growx, wrap");
    lookup.put(name.getText(), header);
    enabledCard.put(name.getText(), true);
    cards.put(name.getText(), card);
    this.revalidate();
  }

  public void addNewCard(final JLabel name, final boolean closed, final Module module) {

  }


  public void removeAllCards(boolean plusOne) {
    if (plusOne) {
      for (int i = 0; i < this.cards.size() + 1; i++) {
        this.remove(0); // TODO: don't use `i'
      }
      lookup = new HashMap<>();
      cards = new HashMap<>();
      this.revalidate();
      this.repaint();
    } else {
      for (int i = 0; i < this.cards.size(); i++) {
        this.remove(0); // TODO: don't use `i'
      }
      lookup = new HashMap<>();
      cards = new HashMap<>();
      this.revalidate();
      this.repaint();
    }

  }

  public void removeCard(final String name) {
    if (!lookup.containsKey(name)) {
      throw new RuntimeException("Tab with name \"" + name + "\" does not exists.");
    }

    this.remove(lookup.get(name)); //TODO
    //this.remove(0);
    lookup.remove(name);
    cards.remove(name);
    this.revalidate();
    this.repaint();
  }

	public Map<String, JPanel> getCards() {
		return cards;
	}

	/**
	 * Create clickable header.
	 */
	private JComponent createHeader(final JLabel label, final JComponent component, final JPanel tab) {
		final JPanel header = new JPanel(new MigLayout("fillx, aligny center, ins 0 0 0 4", "[grow][]", ""));
		header.setPreferredSize(new Dimension(30, 30));
		header.setBackground(headerColor);

		// Holds the name with insets.
		final JPanel labelPanel = new JPanel(new MigLayout("fillx, ins 4", "[grow]", ""));
		labelPanel.setBackground(headerColor);
		label.setForeground(fontColor);
		labelPanel.add(label);

		final JLabel icon = new JLabel();
		icon.setBackground(Color.WHITE);
		icon.setIcon(downIcon);

		String name = label.getText();

		// By default closed.
		header.addMouseListener(new MouseListener() {

			@Override
			public void mouseReleased(MouseEvent e) {
				// nothing
			}

			@Override
			public void mousePressed(MouseEvent e) {
				// nothing
			}

			@Override
			public void mouseExited(MouseEvent e) {
				// nothing
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				// nothing
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (enabledCard.get(name)) {
					boolean state = openState.get(name);
					for ( Component c : tab.getComponents()) {
						if (c != header) {
							c.setVisible(!state);
						}
					}
					openState.put(name, !state);

					if (state) {
						icon.setIcon(downIcon);
					} else {
						icon.setIcon(upIcon);
					}
					tab.revalidate();
				}
			}
		});

		header.add(labelPanel, "growx");
		header.add(icon);

		component.setVisible(openState.get(name));
		if (openState.get(name)) {
			icon.setIcon(upIcon);
		} else {
			icon.setIcon(downIcon);
		}

		return header;
	}

	/**
	 * Toggle card open/close.
	 *
	 * @param name
	 *            of the card to toggle.
	 */
	public void toggleCardFold(final String name) {
		if (lookup.containsKey(name)) {
			lookup.get(name).getMouseListeners()[0].mouseClicked(null);
			this.revalidate();
		}
	}

	/**
	 * Set mouse-listener active. If not active, the card can't be toggled.
	 *
	 * @param name
	 *            of the card
	 * @param active
	 *            state
	 */
	public void setCardActive(final String name, final boolean active) {
		if (lookup.containsKey(name)) {
			enabledCard.put(name, active);
		}
	}

}
