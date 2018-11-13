package org.jshint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Regular expressions. Some of these are stupidly long.
 */
public class Reg
{
	private Reg() {}
	
	// Unsafe comment or string (ax)
	static final Pattern UNSAFE_STRING = Pattern.compile("@cc|<\\/?|script|\\]\\s*\\]|<\\s*!|&lt", Pattern.CASE_INSENSITIVE);
	
	// Characters in strings that need escaping (nx and nxg)
	static final Pattern NEED_ESC = Pattern.compile("[\\u0000-\\u001f&<\"\\/\\\\\\u007f-\\u009f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufeff\\ufff0-\\uffff]");
	
	// Star slash (lx)
	static final Pattern STAR_SLASH = Pattern.compile("\\*\\/");
	
	// Identifier (ix)	
	// PORT INFO: replaced regexp /^([a-zA-Z_$][a-zA-Z0-9_$]*)$/ with custom function
	public static String getIdentifier(String input)
	{
		if (input == null || input.length() == 0) return "";
		
		int i;
		for (i = 0; i < input.length(); i++)
		{
			switch (input.charAt(i))
			{
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				if (i == 0) return "";
			case 'a':
			case 'b':
			case 'c':
			case 'd':
			case 'e':
			case 'f':
			case 'g':
			case 'h':
			case 'i':
			case 'j':
			case 'k':
			case 'l':
			case 'm':
			case 'n':
			case 'o':
			case 'p':
			case 'q':
			case 'r':
			case 's':
			case 't':
			case 'u':
			case 'v':
			case 'w':
			case 'x':
			case 'y':
			case 'z':
			case 'A':
			case 'B':
			case 'C':
			case 'D':
			case 'E':
			case 'F':
			case 'G':
			case 'H':
			case 'I':
			case 'J':
			case 'K':
			case 'L':
			case 'M':
			case 'N':
			case 'O':
			case 'P':
			case 'Q':
			case 'R':
			case 'S':
			case 'T':
			case 'U':
			case 'V':
			case 'W':
			case 'X':
			case 'Y':
			case 'Z':
			case '_':
			case '$':
				continue;
			}
			break;
		}
		
		return input.substring(0,  i);
	}
	
	// JavaScript URL (jx)
	static final Pattern JAVASCRIPT_URL = Pattern.compile("^(?:javascript|jscript|ecmascript|vbscript|livescript)\\s*:", Pattern.CASE_INSENSITIVE);
	
	// Catches /* falls through */ comments (ft)
	static final Pattern FALLS_THROUGH = Pattern.compile("^\\s*falls?\\sthrough\\s*$");
	
	// very conservative rule (eg: only one space between the start of the comment and the first character)
	// to relax the maxlen option
	static final Pattern MAXLEN_EXCEPTION = Pattern.compile("^(?:(?:\\/\\/|\\/\\*|\\*) ?)?[^ ]+$");
	
	// PORT INFO: replaced global regexp whitespace with simple function that check whitespace characters 
	public static boolean isWhitespace(String input)
	{
		if (input == null || input.length() == 0) return false;
		
		switch (input.charAt(0))
		{
		case ' ':
		case '\f':
		case '\n':
		case '\r':
		case '\t':
		case '\u000b':
		case '\u00a0':
		case '\u1680':
		case '\u2000':
		case '\u2001':
		case '\u2002':
		case '\u2003':
		case '\u2004':
		case '\u2005':
		case '\u2006':
		case '\u2007':
		case '\u2008':
		case '\u2009':
		case '\u200a':
		case '\u2028':
		case '\u2029':
		case '\u202f':
		case '\u205f':
		case '\u3000':
		case '\ufeff':
			return true;
		}
		
		return false;
	}
	
	public static String replaceAll(String regexp, String input, BiFunction<String, List<String>, String> replacer)
	{
		return replaceAll(Pattern.compile(regexp), input, replacer);
	}
	
	public static String replaceAll(Pattern p, String input, BiFunction<String, List<String>, String> replacer)
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
}