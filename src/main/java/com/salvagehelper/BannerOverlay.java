package com.salvagehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The big banner at the top of the game: danger, hold full, hold at your limit, and reminders. */
class BannerOverlay extends Overlay
{
	private static final int PAD_X = 14;
	private static final int PAD_Y = 6;
	private static final int GAP = 4;
	private static final long FLASH_MILLIS = 500;

	private final SalvageHelperConfig config;
	private final Banner banner;

	@Inject
	BannerOverlay(SalvageHelperConfig config, Banner banner)
	{
		this.config = config;
		this.banner = banner;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<Banner.Message> messages = banner.messages();
		if (!config.showBanner() || messages.isEmpty())
		{
			return null;
		}

		Font font = FontManager.getRunescapeBoldFont().deriveFont(config.bannerTextSize() * 1f);
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();

		int width = 0;
		for (Banner.Message message : messages)
		{
			width = Math.max(width, metrics.stringWidth(message.getText()) + PAD_X * 2);
		}
		int lineHeight = metrics.getHeight() + PAD_Y * 2;
		boolean dim = config.flashBanner() && (System.currentTimeMillis() / FLASH_MILLIS) % 2 == 0;

		int y = 0;
		for (Banner.Message message : messages)
		{
			Color background = background(message.getLevel());
			if (dim)
			{
				background = new Color(background.getRed(), background.getGreen(), background.getBlue(), 120);
			}
			graphics.setColor(background);
			graphics.fillRoundRect(0, y, width, lineHeight, 10, 10);

			int textX = (width - metrics.stringWidth(message.getText())) / 2;
			int textY = y + PAD_Y + metrics.getAscent();
			graphics.setColor(Color.BLACK);
			graphics.drawString(message.getText(), textX + 1, textY + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(message.getText(), textX, textY);
			y += lineHeight + GAP;
		}
		return new Dimension(width, y - GAP);
	}

	private Color background(Banner.Level level)
	{
		switch (level)
		{
			case DANGER:
			case FULL:
				return config.fullBannerColor();
			case LIMIT:
				return config.limitBannerColor();
			default:
				return config.reminderBannerColor();
		}
	}
}
