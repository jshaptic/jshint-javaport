package org.jshint.utils;

import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

public class SubstitutionMap extends HashMap<String, String>
{
	private static final long serialVersionUID = -55361965545928189L;
	private static final String SUBSTITUTION_CHARS = "abcd"; 
	
	public SubstitutionMap(String... values)
	{
		if (values != null)
		{
			for (int i = 0; i < values.length && i < SUBSTITUTION_CHARS.length(); i++)
			{
				put(String.valueOf(SUBSTITUTION_CHARS.charAt(i)), StringUtils.defaultString(values[i]));
			}
		}
	}
	
	public String[] toArray()
	{
		String[] result = new String[SUBSTITUTION_CHARS.length()];
		
		for (int i = 0; i < SUBSTITUTION_CHARS.length(); i++)
		{
			result[i] = get(String.valueOf(SUBSTITUTION_CHARS.charAt(i)));
		}
		
		return result;
	}
}