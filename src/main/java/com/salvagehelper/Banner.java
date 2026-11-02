package com.salvagehelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.Value;

/** The messages the big on-screen banner shows right now, most urgent first. */
@Singleton
class Banner
{
	enum Level
	{
		DANGER,
		FULL,
		LIMIT,
		REMINDER
	}

	@Value
	static class Message
	{
		Level level;
		String text;
	}

	private final Map<String, Message> messages = new HashMap<>();

	void set(String key, Level level, String text)
	{
		messages.put(key, new Message(level, text));
	}

	void clear(String key)
	{
		messages.remove(key);
	}

	/** Sets the message while {@code show} is true and clears it otherwise. */
	void toggle(String key, boolean show, Level level, String text)
	{
		if (show)
		{
			set(key, level, text);
		}
		else
		{
			clear(key);
		}
	}

	List<Message> messages()
	{
		List<Message> list = new ArrayList<>(messages.values());
		list.sort(Comparator.comparing(Message::getLevel));
		return list;
	}

	void reset()
	{
		messages.clear();
	}
}
