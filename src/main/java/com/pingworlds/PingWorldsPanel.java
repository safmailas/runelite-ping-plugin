package com.pingworlds;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar panel: a compact, World-Hopper-style table of worlds with a Stability (check/cross)
 * column plus players, ping and jitter. Pure view — it renders whatever {@link WorldStatus} rows it
 * is handed and reports clicks back through a callback. display() must be called on the Swing EDT.
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

	private static final ImageIcon CHECK = drawCheck();
	private static final ImageIcon CROSS = drawCross();
	private static final ImageIcon PENDING = drawPending();

	private final IntConsumer onWorldSelected;
	private final JLabel subtitle = new JLabel();
	private final JPanel rows = new JPanel();

	PingWorldsPanel(IntConsumer onWorldSelected)
	{
		this.onWorldSelected = onWorldSelected;

		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

		JPanel head = new JPanel(new BorderLayout());
		head.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));
		JLabel title = new JLabel("Ping Worlds");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		head.add(title, BorderLayout.NORTH);
		subtitle.setFont(subtitle.getFont().deriveFont(11f));
		subtitle.setForeground(GREY);
		head.add(subtitle, BorderLayout.SOUTH);
		add(head, BorderLayout.NORTH);

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		add(rows, BorderLayout.CENTER);

		display(java.util.Collections.emptyList(), -1, "Scanning worlds…");
	}

	/** Render the given rows. Must be called on the Swing EDT. */
	void display(List<WorldStatus> statuses, int currentWorldId, String status)
	{
		subtitle.setText(status);
		rows.removeAll();

		if (statuses.isEmpty())
		{
			JLabel empty = new JLabel("No worlds yet — scanning…");
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
		JPanel h = new JPanel(new BorderLayout(4, 0));
		h.setOpaque(false);
		h.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 6));
		h.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

		JPanel west = rowSection();
		west.add(fixed(headerLabel("", SwingConstants.CENTER), 16));
		west.add(fixed(headerLabel("World", SwingConstants.LEFT), 34));
		west.add(fixed(headerLabel("Pl", SwingConstants.RIGHT), 34));
		h.add(west, BorderLayout.WEST);

		JLabel act = headerLabel("Activity", SwingConstants.LEFT);
		act.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
		h.add(act, BorderLayout.CENTER);

		JPanel east = rowSection();
		east.add(fixed(headerLabel("Png", SwingConstants.RIGHT), 30));
		east.add(Box.createHorizontalStrut(4));
		east.add(fixed(headerLabel("Jit", SwingConstants.RIGHT), 26));
		h.add(east, BorderLayout.EAST);

		return h;
	}

	private JPanel row(WorldStatus s, boolean current)
	{
		final Color base = current ? CURRENT_BG : ROW_BG;
		final Color hover = current ? CURRENT_HOVER : ROW_HOVER;

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		row.setBackground(base);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// West: stability icon + world number + player count
		JPanel west = rowSection();
		JLabel stab = new JLabel(!s.hasData() ? PENDING : (s.isConsistent() ? CHECK : CROSS));
		west.add(fixed(stab, 16));
		JLabel world = new JLabel(String.valueOf(s.getWorldId()));
		world.setForeground(TEXT);
		world.setFont(world.getFont().deriveFont(Font.BOLD, 12f));
		west.add(fixed(world, 34));
		JLabel players = right(new JLabel(s.getPlayers() >= 0 ? String.valueOf(s.getPlayers()) : "-"));
		players.setForeground(GREY);
		players.setFont(players.getFont().deriveFont(10f));
		west.add(fixed(players, 34));
		row.add(west, BorderLayout.WEST);

		// Center: activity
		JLabel activity = new JLabel(truncate(s.getActivity(), 12));
		activity.setForeground(GREY);
		activity.setFont(activity.getFont().deriveFont(11f));
		activity.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
		row.add(activity, BorderLayout.CENTER);

		// East: ping (coloured) + jitter
		JPanel east = rowSection();
		JLabel ping = right(new JLabel(s.hasData() ? String.valueOf(s.getAverage()) : "—"));
		ping.setFont(ping.getFont().deriveFont(Font.BOLD, 12f));
		ping.setForeground(!s.hasData() ? GREY : (s.isConsistent() ? GREEN : RED));
		east.add(fixed(ping, 30));
		east.add(Box.createHorizontalStrut(4));
		JLabel jitter = right(new JLabel(s.hasData() ? String.valueOf(s.getJitter()) : "—"));
		jitter.setForeground(GREY);
		jitter.setFont(jitter.getFont().deriveFont(11f));
		east.add(fixed(jitter, 26));
		row.add(east, BorderLayout.EAST);

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

	private static JPanel rowSection()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		return p;
	}

	private static JLabel headerLabel(String text, int align)
	{
		JLabel l = new JLabel(text, align);
		l.setForeground(GREY);
		l.setFont(l.getFont().deriveFont(10f));
		return l;
	}

	private static JLabel right(JLabel l)
	{
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		return l;
	}

	private static Component fixed(JLabel l, int width)
	{
		Dimension d = new Dimension(width, 18);
		l.setPreferredSize(d);
		l.setMinimumSize(d);
		l.setMaximumSize(d);
		return l;
	}

	private static String tooltip(WorldStatus s, boolean current)
	{
		StringBuilder b = new StringBuilder("World ").append(s.getWorldId());
		if (!s.getRegionLabel().isEmpty())
		{
			b.append(" (").append(s.getRegionLabel()).append(')');
		}
		if (s.hasData())
		{
			b.append(" — avg ").append(s.getAverage()).append(" ms, jitter ")
				.append(s.getJitter()).append(" ms, ").append(s.getSamples()).append(" pings");
		}
		else
		{
			b.append(" — measuring…");
		}
		b.append(current ? "  •  current world" : "  •  click to switch");
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

	// ---- Drawn status icons (crisp check / cross / pending dot) -------------------------------------

	private static ImageIcon drawCheck()
	{
		return icon(g ->
		{
			g.setColor(GREEN);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawPolyline(new int[]{2, 6, 12}, new int[]{8, 12, 3}, 3);
		});
	}

	private static ImageIcon drawCross()
	{
		return icon(g ->
		{
			g.setColor(RED);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(3, 3, 11, 11);
			g.drawLine(11, 3, 3, 11);
		});
	}

	private static ImageIcon drawPending()
	{
		return icon(g ->
		{
			g.setColor(GREY);
			g.fillOval(4, 4, 6, 6);
		});
	}

	private interface IconPainter
	{
		void paint(Graphics2D g);
	}

	private static ImageIcon icon(IconPainter painter)
	{
		BufferedImage img = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		painter.paint(g);
		g.dispose();
		return new ImageIcon(img);
	}
}
