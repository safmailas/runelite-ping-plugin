package com.pingworlds;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar panel. Pure view: it renders whatever {@link WorldStatus} rows it is handed and never
 * computes anything itself. display() must be called on the Swing Event Dispatch Thread.
 */
class PingWorldsPanel extends PluginPanel
{
	private static final Color GREEN = new Color(76, 175, 80);
	private static final Color RED = new Color(198, 60, 60);
	private static final Color MUTED = new Color(150, 150, 150);
	private static final Color ROW_BG = new Color(40, 44, 52);
	private static final Color BEST_BG = new Color(38, 60, 42);

	private final JLabel header = new JLabel();
	private final JPanel list = new JPanel();

	PingWorldsPanel()
	{
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
		add(header, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		add(list, BorderLayout.CENTER);

		display(java.util.Collections.emptyList(), -1, "Waiting for pings…");
	}

	/** Render the given rows. Must be called on the Swing EDT. */
	void display(List<WorldStatus> rows, int recommendedWorldId, String status)
	{
		header.setText("<html>Ping Worlds<br>"
			+ "<span style='font-weight:normal;font-size:9px'>" + escape(status) + "</span></html>");

		list.removeAll();
		if (rows.isEmpty())
		{
			JLabel empty = new JLabel("No matching worlds yet.");
			empty.setForeground(MUTED);
			list.add(empty);
		}
		else
		{
			for (WorldStatus r : rows)
			{
				list.add(rowComponent(r, r.getWorldId() == recommendedWorldId));
				list.add(Box.createVerticalStrut(4));
			}
		}
		list.revalidate();
		list.repaint();
	}

	private JPanel rowComponent(WorldStatus r, boolean recommended)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		row.setBackground(recommended ? BEST_BG : ROW_BG);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

		String mark;
		Color markColor;
		if (!r.hasData())
		{
			mark = "…"; // …
			markColor = MUTED;
		}
		else if (r.isConsistent())
		{
			mark = "✓"; // ✓
			markColor = GREEN;
		}
		else
		{
			mark = "✗"; // ✗
			markColor = RED;
		}

		JLabel status = new JLabel(mark);
		status.setForeground(markColor);
		status.setFont(status.getFont().deriveFont(Font.BOLD, 16f));
		row.add(status, BorderLayout.WEST);

		String title = "World " + r.getWorldId() + (recommended ? "  ★ best" : "");
		String detail = r.hasData()
			? "avg " + r.getAverage() + " ms · jitter " + r.getJitter() + " ms · " + r.getSamples() + " samples"
			: "pinging…";
		JLabel text = new JLabel("<html><b>" + escape(title) + "</b><br>"
			+ "<span style='font-size:9px'>" + escape(detail) + "</span></html>");
		if (recommended)
		{
			text.setForeground(GREEN);
		}
		row.add(text, BorderLayout.CENTER);

		return row;
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
