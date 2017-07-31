package org.jshint.utils;

import java.nio.file.FileSystems;
import java.nio.file.Paths;

public class Minimatch
{
	public final static int NO_OPTIONS = 0;
	public final static int NO_CASE = 1;
	public final static int MATCH_BASE = 2;
	
	public static boolean test(String path, String pattern)
	{
		return test(path, pattern, NO_OPTIONS);
	}
	
	public static boolean test(String path, String pattern, int options)
	{
		pattern = preparePattern(pattern, options);
		path = preparePath(path, pattern, options);
		return FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(Paths.get(path));
	}
	
	private static String preparePattern(String pattern, int options)
	{
		String p = JSHintUtils.path.convertSepToPosix(pattern).trim(); 
		p = (options & NO_CASE) == NO_CASE ? p.toLowerCase() : p;
		return p;
	}
	
	private static String preparePath(String path, String pattern, int options)
	{
		String p = JSHintUtils.path.convertSepToPosix(path).trim(); 
		if ((options & MATCH_BASE) == MATCH_BASE && pattern.split("/").length == 1) p = p.substring(Math.max(p.lastIndexOf('/'), 0));
		
		p = (options & NO_CASE) == NO_CASE ? p.toLowerCase() : p;
		return p;
	}
}