package org.jshint;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Regular expressions. Some of these are stupidly long.
 */

public final class Reg
{
	// Unsafe comment or string (ax)
	static final Pattern UNSAFE_STRING = Pattern.compile("@cc|<\\/?|script|\\]\\s*\\]|<\\s*!|&lt", Pattern.CASE_INSENSITIVE);
	
	// Unsafe characters that are silently deleted by one or more browsers (cx)
	static final Pattern UNSAFE_CHARS = Pattern.compile("[\\u0000-\\u001f\\u007f-\\u009f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufeff\\ufff0-\\uffff]");
	
	// Characters in strings that need escaping (nx and nxg)
	static final Pattern NEED_ESC = Pattern.compile("[\\u0000-\\u001f&<\"\\/\\\\\\u007f-\\u009f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufeff\\ufff0-\\uffff]");
	
	// Star slash (lx)
	static final Pattern STAR_SLASH = Pattern.compile("\\*\\/");
	
	// Identifier (ix)
	static final Pattern INDENTIFIER = Pattern.compile("^([a-zA-Z_$][a-zA-Z0-9_$]*)$");
	
	// JavaScript URL (jx)
	static final Pattern JAVASCRIPT_URL = Pattern.compile("^(?:javascript|jscript|ecmascript|vbscript|livescript)\\s*:", Pattern.CASE_INSENSITIVE);
	
	// Catches /* falls through */ comments (ft)
	static final Pattern FALLS_THROUGH = Pattern.compile("^\\s*falls?\\sthrough\\s*$");
	
	// very conservative rule (eg: only one space between the start of the comment and the first character)
	// to relax the maxlen option
	static final Pattern MAXLEN_EXCEPTION = Pattern.compile("^(?:(?:\\/\\/|\\/\\*|\\*) ?)?[^ ]+$");
	
	public static String replaceAll(String regexp, String input, Replacer replacer)
	{
		return replaceAll(Pattern.compile(regexp), input, replacer);
	}
	
	public static String replaceAll(String regexp, int flags, String input, Replacer replacer)
	{
		return replaceAll(Pattern.compile(regexp, flags), input, replacer);
	}
	
	public static String replaceAll(Pattern p, String input, Replacer replacer)
	{
		Matcher m = p.matcher(StringUtils.defaultString(input));
		StringBuffer buffer = new StringBuffer();
		
		List<String> groups = null;
		while (m.find())
		{
			groups = new ArrayList<String>();
			for (int i = 0; i < m.groupCount(); i++)
			{
				groups.add(m.group(i+1));
			}
			m.appendReplacement(buffer, "");
			buffer.append(replacer.apply(m.group(0), groups));
		}
		m.appendTail(buffer);
		
		return buffer.toString();
	}
	
	public static boolean test(String regexp, String input)
	{
		return test(Pattern.compile(regexp), input);
	}
	
	public static boolean test(String regexp, int flags, String input)
	{
		return test(Pattern.compile(regexp, flags), input);
	}
	
	public static boolean test(Pattern p, String input)
	{
		return p.matcher(StringUtils.defaultString(input)).find();
	}
	
	public static String[] exec(String regexp, String input)
	{
		return exec(Pattern.compile(regexp), input);
	}
	
	public static String[] exec(Pattern p, String input)
	{
		String[] result;
		
		Matcher m = p.matcher(StringUtils.defaultString(input));
		if (m.find())
		{
			int groupCount = m.groupCount() + 1;
			result = new String[groupCount];
			
			for (int i = 0; i < groupCount; i++)
			{
				result[i] = m.group(i);
			}
			return result;
		}
		else
			return null;
	}
	
	public static int indexOf(Pattern p, String input)
	{
		Matcher m = p.matcher(StringUtils.defaultString(input));
		if (m.find())
			return m.start();
		else
			return -1;
	}
	
	public static interface Replacer
	{
		public String apply(String str, List<String> groups);
	}
}
