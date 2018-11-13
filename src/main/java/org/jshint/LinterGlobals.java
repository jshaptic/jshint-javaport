package org.jshint;

import java.util.HashMap;

import com.github.jshaptic.js4j.UniversalContainer;

public class LinterGlobals extends HashMap<String, Boolean>
{
	private static final long serialVersionUID = -1914053710885665087L;
	
	public LinterGlobals()
	{
		
	}
	
	public LinterGlobals(boolean defaultValue, String... globals)
	{
		if (globals != null)
		{
			for (String g : globals)
			{
				put(g, defaultValue);
			}
		}
	}
	
	public void putAll(UniversalContainer globals)
	{
		if (globals == null || !globals.isObject()) return;
		
		for (String key : globals.keys())
		{
			put(key, globals.asBoolean(key));
		}
	}
}