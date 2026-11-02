package com.salvagehelper;

final class Durations
{
	private Durations()
	{
	}

	/** "4:05", or "1:02:30" past an hour. Negative becomes 0:00. */
	static String countdown(long millis)
	{
		long seconds = Math.max(0, millis / 1000);
		long hours = seconds / 3600;
		long minutes = seconds / 60 % 60;
		long secs = seconds % 60;
		return hours > 0
			? String.format("%d:%02d:%02d", hours, minutes, secs)
			: String.format("%d:%02d", minutes, secs);
	}

	/** "52 min", "1 h 05 min", or "<1 min". */
	static String approx(long millis)
	{
		long minutes = Math.max(0, millis / 60_000);
		if (minutes < 1)
		{
			return "<1 min";
		}
		return minutes >= 60 ? String.format("%d h %02d min", minutes / 60, minutes % 60) : minutes + " min";
	}
}
