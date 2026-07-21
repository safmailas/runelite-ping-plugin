package com.pingworlds;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar panel: a compact, World-Hopper-style table of the monitored worlds. Pure view — it
 * renders whatever {@link WorldStatus} rows it is handed and reports clicks back through a callback.
 * display() must be called on the Swing Event Dispatch Thread.
 */
class PingWorldsPanel extends PluginPanel
{
	private static final Color GREEN = new Color(101, 186, 106);
	private static final Color RED = new Color(214, 92, 92);
	private static final Color GREY = new Color(150, 150, 150);
	private static final Color TEXT = new Color(220, 220, 220);
	private static final Color ROW_BG = new Color(42, 46, 54);
	private static final Color ROW_HOVER = new Color(54, 58, 68);
	private static final Color CURRENT_BG = new Color(35, 58, 82);
	private static final Color CURRENT_HOVER = new Color(44, 70, 98);

	private final IntConsumer onWorldSelected;
	private final JLabel subtitle = new JLabel();
	private final JPanel rows = new JPanel();

	PingWorldsPanel(IntConsumer onWorldSelected)
	{
		this.onWorldSelected = onWorldSelected;

		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel head = new JPanel(new BorderLayout());
		head.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 2));
		JLabel title = new JLabel("Ping Worlds");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		head.add(title, BorderLayout.NORTH);
		subtitle.setFont(subtitle.getFont().deriveFont(11f));
		subtitle.setForeground(GREY);
		head.add(subtitle, BorderLayout.SOUTH);
		add(head, BorderLayout.NORTH);

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		add(rows, BorderLayout.CENTER);

		display(java.util.Collections.emptyList(), -1, "Waiting for pings…");
	}

	/** Render the given rows. Must be called on the Swing EDT. */
	void display(List<WorldStatus> statuses, int currentWorldId, String status)
	{
		subtitle.setText(status);
		rows.removeAll();

		if (statuses.isEmpty())
		{
			JLabel empty = new JLabel("No matching worlds — try another profile or region.");
			empty.setForeground(GREY);
			empty.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
			rows.add(empty);
		}
		else
		{
			rows.add(headerRow());
			rows.add(Box.createVerticalStrut(2));
			for (WorldStatus s : statuses)
			{
				rows.add(row(s, s.getWorldId() == currentWorldId));
				rows.add(Box.createVerticalStrut(3));
			}
		}
		rows.add(Box.createVerticalGlue());
		rows.revalidate();
		rows.repaint();
	}

	private JPanel headerRow()
	{
		JPanel h = new JPanel(new BorderLayout(6, 0));
		h.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 6));
		h.setOpaque(false);
		h.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		h.add(header("World", SwingConstants.LEFT, 78), BorderLayout.WEST);
		h.add(header("Activity", SwingConstants.LEFT, 0), BorderLayout.CENTER);
		h.add(header("Ping", SwingConstants.RIGHT, 52), BorderLayout.EAST);
		return h;
	}

	private JLabel header(String text, int align, int width)
	{
		JLabel l = new JLabel(text, align);
		l.setForeground(GREY);
		l.setFont(l.getFont().deriveFont(10f));
		if (width > 0)
		{
			l.setPreferredSize(new Dimension(width, 14));
		}
		return l;
	}

	private JPanel row(WorldStatus s, boolean current)
	{
		final Color base = current ? CURRENT_BG : ROW_BG;
		final Color hover = current ? CURRENT_HOVER : ROW_HOVER;

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		row.setBackground(base);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Left: world number + region code
		String left = String.valueOf(s.getWorldId());
		if (!s.getRegionLabel().isEmpty())
		{
			left += "  " + s.getRegionLabel();
		}
		JLabel world = new JLabel(left);
		world.setForeground(TEXT);
		world.setFont(world.getFont().deriveFont(Font.BOLD, 12f));
		world.setPreferredSize(new Dimension(78, 18));
		row.add(world, BorderLayout.WEST);

		// Center: activity
		JLabel activity = new JLabel(truncate(s.getActivity(), 16));
		activity.setForeground(GREY);
		activity.setFont(activity.getFont().deriveFont(11f));
		row.add(activity, BorderLayout.CENTER);

		// Right: ping (colored by consistency)
		JLabel ping = new JLabel(s.hasData() ? s.getAverage() + " ms" : "—", SwingConstants.RIGHT);
		ping.setFont(ping.getFont().deriveFont(Font.BOLD, 12f));
		ping.setForeground(!s.hasData() ? GREY : (s.isConsistent() ? GREEN : RED));
		ping.setPreferredSize(new Dimension(52, 18));
		row.add(ping, BorderLayout.EAST);

		row.setToolTipText(tooltip(s, current));

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onWorldSelected.accept(s.getWorldId());
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(hover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(base);
			}
		});

		return row;
	}

	private static String tooltip(WorldStatus s, boolean current)
	{
		StringBuilder b = new StringBuilder("World ").append(s.getWorldId());
		if (s.hasData())
		{
			b.append(" — avg ").append(s.getAverage()).append(" ms, jitter ")
				.append(s.getJitter()).append(" ms, ").append(s.getSamples()).append(" samples");
		}
		else
		{
			b.append(" — pinging…");
		}
		if (s.getPlayers() >= 0)
		{
			b.append(", ").append(s.getPlayers()).append(" players");
		}
		b.append(current ? " (current — click to stay)" : " — click to switch");
		return b.toString();
	}

	private static String truncate(String s, int max)
	{
		if (s.length() <= max)
		{
			return s;
		}
		return s.substring(0, Math.max(0, max - 1)).trim() + "…";
	}
}
