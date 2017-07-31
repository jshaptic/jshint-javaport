package org.jshint.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jshint.JSHintException;
import org.jshint.LexerEventListener;

public class EventEmitter
{
	Map<String, List<LexerEventListener>> events = new HashMap<String, List<LexerEventListener>>();
	
	public void on(String name, LexerEventListener listener)
	{
		if (!events.containsKey(name))
		{
			events.put(name, new ArrayList<LexerEventListener>());
		}
		
		events.get(name).add(listener);
	}
	
	public void emit(String name, EventContext context) throws JSHintException
	{
		if (events.containsKey(name))
		{
			for (LexerEventListener listener : events.get(name))
			{
				listener.accept(context);
			}
		}
	}
	
	public void removeAllListeners()
	{
		events.clear();
	}
}