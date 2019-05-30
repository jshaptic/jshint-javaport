package org.jshint.utils;

import java.nio.file.FileSystems;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Port of a minimatch library. Based on version 3.0.2
 */
//TODO: extract this class as a separate project and add testcases from minimatch library
public class Minimatch
{
	public final static int NO_OPTIONS = 0;
	public final static int DEBUG = 1;
	public final static int NO_BRACE = 2;
	public final static int NO_GLOBSTAR = 4;
	public final static int DOT = 8;
	public final static int NO_EXT = 16;
	public final static int NO_CASE = 32;
	public final static int NO_NULL = 64;
	public final static int MATCH_BASE = 128;
	public final static int NO_COMMENT = 256;
	public final static int NO_NEGATE = 512;
	public final static int FLIP_NEGATE = 1024;
	
	private final static String SEP = FileSystems.getDefault().getSeparator();
	private final static GlobParsedPattern GLOBSTAR = new GlobParsedPattern("GLOBSTAR");
	
	// brace-expansion markers
	private final static String escSlash = "\0SLASH" + Math.random() + "\0";
	private final static String escOpen = "\0OPEN" + Math.random() + "\0";
	private final static String escClose = "\0CLOSE" + Math.random() + "\0";
	private final static String escComma = "\0COMMA" + Math.random() + "\0";
	private final static String escPeriod = "\0PERIOD" + Math.random() + "\0";
	
	// any single thing other than /
	// don't need to escape / when using new RegExp()
	private final static String qmark = "[^/]";
	
	// * => any number of characters
	private final static String star = qmark + "*?";
	
	// characters that need to be escaped in RegExp.
	private final static char[] reSpecials = {'(', ')', '.', '*', '{', '}', '+', '?', '[', ']', '^', '$', '\\', '!'};
	
	// normalizes slashes.
	private final static Pattern slashSplit = Pattern.compile("\\/+");
	
	private final static Pattern numericSequence = Pattern.compile("^-?\\d+\\.\\.-?\\d+(?:\\.\\.-?\\d+)?$");
	private final static Pattern alphaSequence = Pattern.compile("^[a-zA-Z]\\.\\.[a-zA-Z](?:\\.\\.-?\\d+)?$");
	private final static Pattern tailNormalizer = Pattern.compile("((?:\\\\{2}){0,64})(\\\\?)\\|");
	private final static Pattern nestedParensNormalizer = Pattern.compile("\\)[+*?]?");
	
	private Minimatch() {}
	
	private static String[] balanced(String a, String b, String pattern)
	{
		String[] result = null;
		
		int[] r = range(a, b, pattern);
		
		if (r != null)
		{
			result = new String[] {
				pattern.substring(0, r[0]),						// pre
				pattern.substring(r[0] + a.length(), r[1]),		// body
				pattern.substring(r[1] + b.length())			// post
			};
		}
		
		return result;
	}
	
	private static int numeric(String pattern)
	{
		try
		{
			return Integer.parseInt(pattern, 10);
		}
		catch (NumberFormatException e)
		{
			return pattern.codePointAt(0);
		}
	}
	
	private static String escapeBraces(String pattern)
	{
		return StringUtils.replaceEach(pattern, new String[]{"\\\\", "\\{", "\\}", "\\,", "\\."},
			new String[]{escSlash, escOpen, escClose, escComma, escPeriod});
	}
	
	private static String unescapeBraces(String pattern)
	{
		return StringUtils.replaceEach(pattern, new String[]{escSlash, escOpen, escClose, escComma, escPeriod},
			new String[]{"\\", "{", "}", ",", "."});
	}
	
	// Basically just str.split(","), but handling cases
	// where we have nested braced sections, which should be
	// treated as individual members, like {a,{b,c},d}
	private static String[] parseCommaParts(String pattern)
	{
		if (pattern.isEmpty()) return new String[] {""};
		
		String[] m = balanced("{", "}", pattern);
		
		if (m == null) return pattern.split(",", -1);
		
		String pre = m[0];
		String body = m[1];
		String post = m[2];
		String[] p = pre.split(",", -1);
		
		p[p.length-1] += "{" + body + "}";
		String[] postParts = parseCommaParts(post);
		if (post.length() > 0 && postParts.length > 0)
		{
			p[p.length-1] += postParts[0];
			p = Arrays.copyOf(p, p.length + postParts.length - 1);
			System.arraycopy(postParts, 1, p, p.length - (postParts.length - 1), postParts.length - 1);
		}
		
		return p;
	}
	
	private static String embrace(String pattern)
	{
		return "{" + pattern + "}";
	}
	
	private static boolean isPadded(String el)
	{
		return (el.length() >= 3 && el.startsWith("-0") && Character.isDigit(el.charAt(2))) ||
			(el.length() >= 2 && el.startsWith("0") && Character.isDigit(el.charAt(1)));
	}
	
	private static boolean testLteOrGte(int i, int y, boolean reversed)
	{
		return reversed ? i >= y : i <= y;
	}
	
	private static String[] expand(String pattern)
	{
		// I don't know why Bash 4.3 does this, but it does.
		// Anything starting with {} will have the first two bytes preserved
		// but *only* at the top level, so {},a}b will not expand to anything,
		// but a{},b}c will be expanded to [a}c,abc].
		// One could argue that this is a bug in Bash, but since the goal of
		// this module is to match Bash's rules, we escape a leading {}
		if (pattern.startsWith("{}"))
		{
			pattern = "\\{\\}" + pattern.substring(2);
		}
		
		String[] expansions = expand(escapeBraces(pattern), true);
		for (int i = 0; i < expansions.length; i++)
		{
			expansions[i] = unescapeBraces(expansions[i]);
		}
		
		return expansions;
	}
	
	private static String[] expand(String pattern, boolean isTop)
	{
		String[] m = balanced("{", "}", pattern);
		
		if (m == null || m[0].endsWith("$")) return new String[]{pattern};
		
		String pre = m[0];
		String body = m[1];
		String post = m[2];
		
		boolean isNumericSequence = numericSequence.matcher(body).find();
		boolean isAlphaSequence = alphaSequence.matcher(body).find();
		boolean isSequence = isNumericSequence || isAlphaSequence;
		boolean isOptions = body.indexOf(',') >= 0;
		if (!isSequence && !isOptions)
		{
			// {a},b}
			if (post.indexOf(',') < post.indexOf('}'))
			{
				pattern = pre + "{" + body + escClose + post;
				return expand(pattern, false);
			}
			return new String[]{pattern};
		}
		
		String[] n;
		String[] p = !post.isEmpty() ? expand(post, false) : new String[] {""};
		if (isSequence)
		{
			n = body.split("..", -1);
		}
		else
		{
			n = parseCommaParts(body);
			if (n.length == 1)
			{
				// x{{a,b}}y ==> x{a}y x{b}y
				n = expand(n[0], false);
				for (int i = 0; i < n.length; i++) n[i] = embrace(n[i]);
				if (n.length == 1)
				{
					for (int i = 0; i < p.length; i++)
					{
						p[i] = pre + n[0] + p[i];
					}
					return p;
				}
			}
		}
		
		// at this point, n is the parts, and we know it's not a comma set
		// with a single entry.
		
		String[] nn;
		
		if (isSequence)
		{
			int x = numeric(n[0]);
			int y = numeric(n[1]);
			int width = Math.max(n[0].length(), n[1].length());
			int incr = n.length == 3 ? Math.abs(numeric(n[2])) : 1;
			boolean reverse = y < x;
			if (reverse) incr *= -1;
			boolean pad = false;
			for (int i = 0; i < n.length; i++)
			{
				if (isPadded(n[i]))
				{
					pad = true;
					break;
				}
			}
			
			nn = new String[x - Math.abs(incr)*y + 1]; 
			
			for (int i = x, j = 0; testLteOrGte(i, y, reverse); i += incr, j++)
			{
				String c = "";
				if (isAlphaSequence)
				{
					c = Character.toString((char)i);
					if (c.equals("\\")) c = "";
				}
				else
				{
					c = Integer.toString(i);
					if (pad)
					{
						int need = width - c.length();
						if (need > 0)
						{
							String z = String.join("", Collections.nCopies(need + 1, "0"));
							if (i < 0)
								c = "-" + z + c.substring(1);
							else
								c = z + c;
						}
					}
				}
				nn[j] = c;
			}
		}
		else
		{
			nn = new String[0];
			for (int i = 0; i < n.length; i++)
			{
				String[] nnn = expand(n[i], false);
				nn = Arrays.copyOf(nn, nn.length + nnn.length);
				System.arraycopy(nnn, 0, nn,  nn.length - nnn.length, nnn.length);
			}
		}
		
		String[] expansions = new String[nn.length * p.length];
		
		int i = 0;
		for (int j = 0; j < nn.length; j++)
		{
		    for (int k = 0; k < p.length; k++)
		    {
		    	String expansion = pre + nn[j] + p[k];
		    	if (!isTop || isSequence || !expansion.isEmpty())
		    	{
		    		expansions[i] = expansion;
		    		i++;
		    	}
		    }
		}
		
		return Arrays.copyOf(expansions, i);
	}
	
	private static boolean isOption(int options, int o)
	{
		return (options & o) == o;
	}
	
	private static GlobParsedPattern[][] make(int options, StringBuilder pattern, AtomicBoolean negate)
	{
		// step 1: figure out negation, etc.
		parseNegate(options, pattern, negate);
		
		// step 2: expand braces
		String[] braceExpandedSet = braceExpand(options, pattern.toString());
		
		debug(options, false, pattern, Arrays.toString(braceExpandedSet));
		
		// step 3: now we have a set, so turn each one into a series of path-portion
		// matching patterns.
		// These will be regexps, except in the case of "**", which is
		// set to the GLOBSTAR object for globstar behavior,
		// and will not contain any / characters
		String[][] slashSplittedSet = new String[braceExpandedSet.length][];
		for (int i = 0; i < slashSplittedSet.length; i++)
		{
			slashSplittedSet[i] = slashSplit.split(braceExpandedSet[i], -1);
		}
		
		debug(options, false, pattern, Arrays.deepToString(slashSplittedSet));
		
		// glob --> regexps
		GlobParsedPattern[][] set = new GlobParsedPattern[slashSplittedSet.length][];
		for (int i = 0; i < slashSplittedSet.length; i++)
		{
			set[i] = new GlobParsedPattern[slashSplittedSet[i].length];
			for (int j = 0; j < slashSplittedSet[i].length; j++)
			{
				set[i][j] = parse(slashSplittedSet[i][j], options, false);
			}
		}
		
		debug(options, false, pattern, Arrays.deepToString(set));
		
		return set;
	}
	
	private static void parseNegate(int options, StringBuilder pattern, AtomicBoolean negate)
	{
		int negateOffset = 0;
		
		if(isOption(options, NO_NEGATE)) return;
		
		for (int i = 0; i < pattern.length() && pattern.charAt(i) == '!'; i++)
		{
			negate.set(!negate.get());
			negateOffset++;
		}
		
		if (negateOffset > 0) pattern.delete(0, negateOffset + 1);
	}
	
	// Brace expansion:
	// a{b,c}d -> abd acd
	// a{b,}c -> abc ac
	// a{0..3}d -> a0d a1d a2d a3d
	// a{b,c{d,e}f}g -> abg acdfg acefg
	// a{b,c}d{e,f}g -> abdeg acdeg abdeg abdfg
	//
	// Invalid sets are not expanded.
	// a{2..}b -> a{2..}b
	// a{b}c -> a{b}c
	private static String[] braceExpand(int options, String pattern)
	{
		if (isOption(options, NO_BRACE) || !(pattern.indexOf('{') < pattern.indexOf('}')))
		{
			// shortcut. no need to expand.
			return new String[]{pattern};
		}
		
		return expand(pattern);
	}
	
	private static int[] range(String a, String b, String pattern)
	{
		int[] result = null;
		Deque<Integer> begs = null;
		int ai = pattern.indexOf(a);
		int bi = pattern.indexOf(b, ai + 1);
		int i = ai;
		
		if (ai >= 0 && bi >= 0)
		{
			begs = new ArrayDeque<Integer>();
			int left = pattern.length();
			int right = -1;
			
			while (i >= 0 && result == null)
			{
				if (i == ai)
				{
					begs.push(i);
					ai = pattern.indexOf(a, i + 1);
				}
				else if (begs.size() == 1)
				{
					result = new int[]{begs.pop(), bi};
				}
				else
				{
					int beg = begs.pop();
					if (beg < left)
					{
						left = beg;
						right = bi;
					}
					
					bi = pattern.indexOf(b, i + 1);
				}
				
				i = ai < bi && ai >= 0 ? ai : bi;
			}
			
			if (begs.size() > 0)
			{
				result = new int[]{left, right};
			}
		}
		
		return result;
	}
	
	public static boolean match(String path, String pattern)
	{
		return match(path, pattern, NO_OPTIONS);
	}
	
	public static boolean match(String path, String pattern, int options)
	{
		if (pattern == null) throw new RuntimeException("glob pattern string required");
		
		path = path.trim();
		pattern = pattern.trim();
		
		// "" only matches ""
		if (pattern.isEmpty()) return path.isEmpty();
		// shortcut: comments match nothing.
		if (!isOption(options, NO_COMMENT) && pattern.charAt(0) == '#') return false;
		
		// windows support: need to use /, not \
		if (!SEP.equals("/"))
		{
			path = StringUtils.replace(path, SEP, "/");
			pattern = StringUtils.replace(pattern, SEP, "/");
		}
		
		final StringBuilder pat = new StringBuilder(pattern);
		final AtomicBoolean negate = new AtomicBoolean(false);
		final GlobParsedPattern[][] set = make(options, pat, negate);
		
		pattern = pat.toString();
		
		debug(options, false, "match", path, pattern);
		
		String[] f = slashSplit.split(path, -1);
		debug(options, false, pattern, "split", Arrays.toString(f));
		
		// just ONE of the pattern sets in this.set needs to match
		// in order for it to be valid.  If negating, then just one
		// match means that we have failed.
		// Either way, return on the first hit.
		
		// Find the basename of the path by looking for the last non-empty segment
		String filename = "";
		for (int i = f.length - 1; i >= 0; i--)
		{
			filename = f[i];
			if (!filename.isEmpty()) break;
		}
		
		for (int i = 0; i < set.length; i++)
		{
			GlobParsedPattern[] p = set[i];
			String[] file = f;
			if (isOption(options, MATCH_BASE) && p.length == 1)
			{
				file = new String[]{filename};
			}
			boolean hit = matchOne(file, p, options);
			if (hit)
			{
				if (isOption(options, FLIP_NEGATE)) return true;
				return !negate.get();
			}
		}
		
		// didn't get any hits.  this is success if it's a negative
		// pattern, failure otherwise.
		if (isOption(options, FLIP_NEGATE)) return true;
		return negate.get();
	}
	
	// set partial to true to test if, for example,
	// "/a/b" matches the start of "/*/b/*/d"
	// Partial means, if you run out of file before you run
	// out of pattern, then that's fine, as long as all
	// the parts match.
	private static boolean matchOne(String[] file, GlobParsedPattern[] pattern, int options)
	{
		debug(options, "matchOne \n  file: %s\n  pattern: %s", Arrays.toString(file), Arrays.toString(pattern));
		debug(options, false, "matchOne", file.length, pattern.length);
		
		int fi = 0, pi = 0, fl = file.length, pl = pattern.length; 
		for (; (fi < fl) && (pi < pl); fi++, pi++)
		{
			debug(options, false, "matchOne loop");
			GlobParsedPattern p = pattern[pi];
			String f = file[fi];
			
			debug(options, false, Arrays.toString(pattern), p, f);
			if (p == GLOBSTAR)
			{
				debug(options, false, "GLOBSTAR", Arrays.toString(pattern), p, f);
				// "**"
				// a/**/b/**/c would match the following:
				// a/b/x/y/z/c
				// a/x/y/z/b/c
				// a/b/x/b/x/c
				// a/b/c
				// To do this, take the rest of the pattern after
				// the **, and see if it would match the file remainder.
				// If so, return success.
				// If not, the ** "swallows" a segment, and try again.
				// This is recursively awful.
				//
				// a/**/b/**/c matching a/b/x/y/z/c
				// - a matches a
				// - doublestar
				//   - matchOne(b/x/y/z/c, b/**/c)
				//     - b matches b
				//	   - doublestar
				//		 - matchOne(x/y/z/c, c) -> no
				//		 - matchOne(y/z/c, c) -> no
				//		 - matchOne(z/c, c) -> no
				//		 - matchOne(c, c) yes, hit
				int fr = fi;
				int pr = pi + 1;
				if (pr == pl)
				{
					debug(options, false, "** at the end");
					// a ** at the end will just swallow the rest.
					// We have found a match.
					// however, it will not swallow /.x, unless
					// options.dot is set.
					// . and .. are *never* matched by **, for explosively
					// exponential reasons.
					for (; fi < fl; fi++)
					{
						if (file[fi].equals(".") || file[fi].equals("..") ||
							(!isOption(options, DOT) && file[fi].charAt(0) == '.')) return false;
					}
					return true;
				}
				
				// ok, let's see if we can swallow whatever we can.
				while (fr < fl)
				{
					String swallowee = file[fr];
					
					debug(options, false, "globstar while", Arrays.toString(file), fr, Arrays.toString(pattern), pr, swallowee);
					
					// MINIMATCH_XXX remove this slice.  Just pass the start index.
					if (matchOne(Arrays.copyOfRange(file, fr, file.length), Arrays.copyOfRange(pattern, pr, pattern.length), options))
					{
						debug(options, false, "globstar found match!", fr, fl, swallowee);
						// found a match.
						return true;
					}
					else
					{
						// can't swallow "." or ".." ever.
						// can only swallow ".foo" when explicitly asked.
						if (swallowee.equals(".") || swallowee.equals("..") ||
							(!isOption(options, DOT) && swallowee.startsWith(".")))
						{
							debug(options, false, "dot detected!", Arrays.toString(file), fr, Arrays.toString(pattern), pr);
							break;
						}
						
						// ** swallows a segment, and continue.
						debug(options, false, "globstar swallow a segment, and continue");
						fr++;
					}
				}
				
				return false;
			}
			
			// something other than **
			// non-magic patterns just have to match exactly
			// patterns with magic have been turned into regexps.
			boolean hit = false;
			
			if (p.isLiteral)
			{
				if (isOption(options, NO_CASE))
				{
					hit = f.equalsIgnoreCase(p.pattern);
				}
				else
				{
					hit = f.equals(p.pattern);
				}
				debug(options, false, "string match", p, f, hit);
			}
			else
			{
				hit = Pattern.compile(p.pattern, isOption(options, NO_CASE) ? Pattern.CASE_INSENSITIVE : 0).matcher(f).matches();
				debug(options, false, "pattern match", p, f, hit);
			}
			
			if (!hit) return false;
		}
		
		// Note: ending in / means that we'll get a final ""
		// at the end of the pattern.  This can only match a
		// corresponding "" at the end of the file.
		// If the file ends in /, then it can only match a
		// a pattern that ends in /, unless the pattern just
		// doesn't have any more for it. But, a/b/ should *not*
		// match "a/b/*", even though "" matches against the
		// [^/]*? pattern, except in partial mode, where it might
		// simply not be reached yet.
		// However, a/b/ should still satisfy a/*

		// now either we fell off the end of the pattern, or we're done.
		if (fi == fl && pi == pl)
		{
			// ran out of pattern and filename at the same time.
			// an exact hit!
			return true;
		}
		else if (fi == fl)
		{
			// ran out of file, but still had pattern left.
			// this is ok if we're doing the match as part of
			// a glob fs traversal.
			return false;
		}
		else if (pi == pl)
		{
			// ran out of pattern, still have file left.
			// this is only acceptable if we're on the very last
			// empty segment of a file with a trailing slash.
			// a/* should match a/b/
			boolean emptyFileEnd = (fi == fl - 1) && (file[fi].isEmpty());
			return emptyFileEnd;
		}
		
		// should be unreachable.
		throw new RuntimeException("wtf?");
	}
	
	// parse a component of the expanded set.
	// At this point, no pattern may contain "/" in it
	// so we're going to return a 2d array, where each entry is the full
	// pattern, split on '/', and then turned into a regular expression.
	// A regexp is made at the end which joins each array with an
	// escaped /, and another full one which joins each regexp with |.
	//
	// Following the lead of Bash 4.1, note that "**" only has special meaning
	// when it is the *only* thing in a path portion.  Otherwise, any series
	// of * is equivalent to a single *.  Globstar behavior is enabled by
	// default, and can be disabled by setting options.noglobstar.
	private static GlobParsedPattern parse(String pattern, int options, boolean isSub)
	{
		if (pattern.length() > 1024*64) throw new RuntimeException("pattern is too long");
		
		// shortcuts
		if (!isOption(options, NO_GLOBSTAR) && pattern.equals("**")) return GLOBSTAR;
		if (pattern.isEmpty()) return new GlobParsedPattern("");
		
		final StringBuilder re = new StringBuilder();
		final AtomicBoolean hasMagic = new AtomicBoolean(isOption(options, NO_CASE));
		boolean escaping = false;
		// ? => one single character
		Deque<PLTypePattern> patternListStack = new ArrayDeque<PLTypePattern>();
		Deque<PLTypePattern> negativeLists = new ArrayDeque<PLTypePattern>();
		char plType;
		final AtomicReference<Character> stateChar = new AtomicReference<Character>();
		boolean inClass = false;
		int reClassStart = -1;
		int classStart = -1;
		// . and .. never match anything that doesn't start with .,
		// even when options.dot is set.
		String patternStart = pattern.charAt(0) == '.' ? "" // anything
			// not (start or / followed by . or .. followed by / or end)
			: isOption(options, DOT) ? "(?!(?:^|\\/)\\.{1,2}(?:$|\\/))"
			: "(?!\\.)";
		
		Runnable clearStateChar = () ->
		{
			if (stateChar.get() != null)
			{
				// we had some state-tracking character
				// that wasn't consumed by this pass.
				switch(stateChar.get())
				{
				case '*':
					re.append(star);
					hasMagic.set(true);
					break;
				case '?':
					re.append(qmark);
					hasMagic.set(true);
					break;
				default:
					re.append("\\" + stateChar);
					break;
				}
				debug(options, "clearStateChar '%s' %s", stateChar, re);
				stateChar.set(null);
			}
		};
		
		for (int i = 0; i < pattern.length(); i++)
		{
			char c = pattern.charAt(i);
			debug(options, "%-8s%s %s '%s'", pattern, i, re, c);
			
			// skip over any that are escaped.
			if (escaping && ArrayUtils.indexOf(reSpecials, c) >= 0)
			{
				re.append("\\" + c);
				escaping = false;
				continue;
			}
			
			switch(c)
			{
			case '/':
				// completely not allowed, even escaped.
				// Should already be path-split by now.
				return null;
				
			case '\\':
				clearStateChar.run();
				escaping = true;
				continue;
				
			// the various stateChar values
			// for the "extglob" stuff.
			case '?':
			case '*':
			case '+':
			case '@':
			case '!':
				debug(options, "%-8s%s %s '%s' <-- stateChar", pattern, i, re, c);
				
				// all of those are literals inside a class, except that
				// the glob [!a] means [^a] in regexp
				if (inClass)
				{
					debug(options, "  in class");
					if (c == '!' && i == classStart + 1) c = '^';
					re.append(c);
					continue;
				}
				
				// if we already have a stateChar, then it means
				// that there was something like ** or +? in there.
				// Handle the stateChar, then proceed with this one.
				debug(options, "call clearStateChar '%s'", stateChar);
				clearStateChar.run();
				stateChar.set(c);
				// if extglob is disabled, then +(asdf|foo) isn't a thing.
				// just clear the statechar *now*, rather than even diving into
				// the patternList stuff.
				if (isOption(options, NO_EXT)) clearStateChar.run();
				continue;
				
			case '(':
				if (inClass)
				{
					re.append("(");
					continue;
				}
				
				if (stateChar.get() == null)
				{
					re.append("\\(");
					continue;
				}
				
				plType = stateChar.get();
				patternListStack.push(new PLTypePattern(plType, re.length()));
				// negation is (?:(?!js)[^/]*)
				re.append(stateChar.get() == '!' ? "(?:(?!(?:" : "(?:");
				debug(options, "plType '%s' %s", stateChar, re);
				stateChar.set(null);
				continue;
				
			case ')':
				if (inClass || patternListStack.size() == 0)
				{
					re.append("\\)");
					continue;
				}
				
				clearStateChar.run();
				hasMagic.set(true);
				re.append(")");
				PLTypePattern pl = patternListStack.pop();
				plType = pl.type;
				// negation is (?:(?!js)[^/]*)
				// The others are (?:<pattern>)<type>
				switch(plType)
				{
				case '!':
					negativeLists.push(pl);
					re.append(")[^/]*?)");
					pl.reEnd = re.length();
					break;
				case '?':
				case '+':
				case '*':
					re.append(plType);
					break;
				case '@': break; // the default anyway
				}
				continue;
				
			case '|':
				if (inClass || patternListStack.size() == 0 || escaping)
				{
					re.append("\\|");
					escaping = false;
					continue;
				}
				
				clearStateChar.run();
				re.append("|");
				continue;
				
			// these are mostly the same in regexp and glob
			case '[':
				// swallow any state-tracking char before the [
				clearStateChar.run();
				
				if (inClass)
				{
					re.append("\\" + c);
					continue;
				}
				
				inClass = true;
				classStart = i;
				reClassStart = re.length();
				re.append(c);
				continue;
				
			case ']':
				//  a right bracket shall lose its special
				//  meaning and represent itself in
				//  a bracket expression if it occurs
				//  first in the list.  -- POSIX.2 2.8.3.2
				if (i == classStart + 1 || !inClass)
				{
					re.append("\\" + c);
					escaping = false;
					continue;
				}
				
				// handle the case where we left a class open.
				// "[z-a]" is valid, equivalent to "\[z-a\]"
				if (inClass)
				{
					// split where the last [ was, make sure we don't have
					// an invalid re. if so, re-walk the contents of the
					// would-be class to re-translate any characters that
					// were passed through as-is
					// MINIMATCH_TODO: It would probably be faster to determine this
					// without a try/catch and a new RegExp, but it's tricky
					// to do safely.  For now, this is safe and works.
					String cs = pattern.substring(classStart + 1, i);
					try
					{
						Pattern.compile("[" + cs + "]");
					}
					catch (PatternSyntaxException er)
					{
						// not a valid class!
						GlobParsedPattern sp = parse(cs, options, true);
						re.replace(0, re.length(), re.substring(0, reClassStart) + "\\[" + sp.re.charAt(0) + "\\]");
						hasMagic.set(hasMagic.get() || sp.hasMagic);
						inClass = false;
						continue;
					}
				}
				
				// finish up the class.
				hasMagic.set(true);
				inClass = false;
				re.append(c);
				continue;
				
			default:
				// swallow any state char that wasn't consumed
				clearStateChar.run();
				
				if (escaping)
				{
					// no need
					escaping = false;
				}
				else if (ArrayUtils.indexOf(reSpecials, c) >= 0 && !(c == '^' && inClass))
				{
					re.append("\\");
				}
				
				re.append(c);
			} //switch
		} //for
		
		// handle the case where we left a class open.
		// "[abc" is valid, equivalent to "\[abc"
		if (inClass)
		{
			// split where the last [ was, and escape it
			// this is a huge pita.  We now have to re-walk
			// the contents of the would-be class to re-translate
			// any characters that were passed through as-is
			String cs = pattern.substring(classStart + 1);
			GlobParsedPattern sp = parse(cs, options, true);
			re.replace(0, re.length(), re.substring(0, reClassStart) + "\\[" + sp.re.charAt(0));
			hasMagic.set(hasMagic.get() || sp.hasMagic);
		}
		
		// handle the case where we had a +( thing at the *end*
		// of the pattern.
		// each pattern list stack adds 3 chars, and we need to go through
		// and escape any | chars that were passed through as-is for the regexp.
		// Go through and escape them, taking care not to double-escape any
		// | chars that were already escaped.
		while (patternListStack.size() > 0)
		{
			PLTypePattern pl = patternListStack.pop();
			String tail = re.substring(pl.reStart + 3);
			// maybe some even number of \, then maybe 1 \, followed by a |
			tail = normalizeTail(tail);
			
			debug(options, "tail=%s\n   %s", tail, tail);
			String t = pl.type == '*' ? star
				: pl.type == '?' ? qmark
				: "\\" + pl.type;
			
			hasMagic.set(true);
			re.replace(0, re.length(), re.substring(0, pl.reStart) + t + "\\(" + tail);
		}
		
		// handle trailing things that only matter at the very end.
		clearStateChar.run();
		if (escaping)
		{
			// trailing \\
			re.append("\\\\");
		}
		
		// only need to apply the nodot start if the re starts with
		// something that could conceivably capture a dot
		boolean addPatternStart = false;
		switch(re.charAt(0))
		{
		case '.':
		case '[':
		case '(':
			addPatternStart = true;
		}
		
		// Hack to work around lack of negative lookbehind in JS
		// A pattern like: *.!(x).!(y|z) needs to ensure that a name
		// like 'a.xyz.yz' doesn't match.  So, the first negative
		// lookahead, has to look ALL the way ahead, to the end of
		// the pattern.
		for (Iterator<PLTypePattern> iterator = negativeLists.iterator(); iterator.hasNext();)
		{
			PLTypePattern nl = iterator.next();
			
			String nlBefore = re.substring(0, nl.reStart);
			String nlFirst = re.substring(nl.reStart, nl.reEnd - 8);
			String nlLast = re.substring(nl.reEnd - 8, nl.reEnd);
			String nlAfter = re.substring(nl.reEnd);
			
			nlLast += nlAfter;
			
			// Handle nested stuff like *(*.js|!(*.json)), where open parens
			// mean that we should *not* include the ) in the bit that is considered
			// "after" the negated section.
			int openParensBefore = nlBefore.split("(").length - 1;
			String cleanAfter = nlAfter;
			for (int i = 0; i < openParensBefore; i++)
			{
				cleanAfter = nestedParensNormalizer.matcher(cleanAfter).replaceFirst("");
			}
			nlAfter = cleanAfter;
			
			String dollar = "";
			if (nlAfter.isEmpty() && !isSub)
			{
				dollar = "$";
			}
			String newRe = nlBefore + nlFirst + nlAfter + dollar + nlLast;
			re.replace(0, re.length(), newRe);
		}
		
		// if the re is not "" at this point, then we need to make sure
		// it doesn't match against an empty path part.
		// Otherwise a/* will match a/, which it should not.
		if (re.length() > 0 && hasMagic.get())
		{
			re.insert(0, "(?=.)");
		}
		
		if (addPatternStart)
		{
			re.insert(0, patternStart);
		}
		
		// parsing just a piece of a larger pattern.
		if (isSub)
		{
			return new GlobParsedPattern(re, hasMagic.get());
		}
		
		// skip the regexp for non-magical patterns
		// unescape anything in it, though, so that it'll be
		// an exact match against a file etc.
		if (!hasMagic.get())
		{
			return new GlobParsedPattern(globUnescape(pattern));
		}
		
		try
		{
			return new GlobParsedPattern("^" + re + "$", false);
		}
		catch (PatternSyntaxException er)
		{
			// If it was an invalid regular expression, then it can't match
			// anything.  This trick looks for a character after the end of
			// the string, which is of course impossible, except in multi-line
			// mode, but it's not a /m regex.
			return new GlobParsedPattern("$.", false);
		}
	}
	
	private static String normalizeTail(String text)
	{
		Function<Matcher, String> replacer = m -> {
			String g1 = m.group(0);
			String g2 = m.group(1);
			
			if (g2.isEmpty())
			{
				// the | isn't already escaped, so escape it.
				g2 = "\\";
			}
			
			// need to escape all those slashes *again*, without escaping the
			// one that we need for escaping the | character.  As it works out,
			// escaping an even number of slashes can be done by simply repeating
			// it exactly after itself.  That's why this trick works.
			//
			// I am sorry that you have to see this.
			return g1 + g1 + g2 + "|";
		};
		
		Matcher matcher = tailNormalizer.matcher(text);
		StringBuffer result = new StringBuffer();
		while (matcher.find())
			matcher.appendReplacement(result, replacer.apply(matcher));
		matcher.appendTail(result);
		
		return result.toString();
	}
	
	// replace stuff like \* with *
	private static String globUnescape(String s)
	{
		StringBuilder sb = new StringBuilder();
		int start = 0;
		for (int i = 0; i < s.length(); i++)
		{
			if (s.charAt(i) == '\\')
			{
				if (i != 0)
					sb.append(s.substring(start, i));
				i++;
				if (i < s.length())
					sb.append(s.charAt(i));
				else
					sb.append(s.charAt(i - 1));
				start = i + 1;
			}
		}
		
		if (start < s.length())
			sb.append(s.substring(start));
		
		return sb.toString();
	}
	
	private static void debug(int options, boolean hasLogline, Object... vars)
	{
		if (isOption(options, DEBUG))
		{
			String logline = "";
			if (hasLogline)
			{
				Object[] v = {};
				if (vars != null && vars.length > 0 && vars[0] instanceof String)
				{
					logline = vars[0].toString();
					if (vars.length > 1)
					{
						v = new Object[vars.length-2];
						for (int i = 0; i < v.length; i++) v[i] = vars[i+1];
					}
				}
				System.out.format("%s: ", Thread.currentThread().getStackTrace()[2].getLineNumber());
				System.out.format(logline + "\n", v);
			}
			else if (vars != null)
			{
				for (int i = 0; i < vars.length; i++) logline += "%s ";
				System.out.format("%s: ", Thread.currentThread().getStackTrace()[2].getLineNumber());
				System.out.format(logline.trim() + "\n", vars);
			}
		}
	}
	
	private static void debug(int options, String logline, Object... vars)
	{
		if (isOption(options, DEBUG))
		{
			System.out.format("%s: ", Thread.currentThread().getStackTrace()[2].getLineNumber());
			System.out.format(logline + "\n", vars);
		}
	}
	
	private static class PLTypePattern
	{
		Character type;
		int reStart;
		int reEnd;
		
		PLTypePattern(Character type, int reStart)
		{
			this.type = type;
			this.reStart = reStart;
		}
	}
	
	private static class GlobParsedPattern
	{
		boolean isLiteral;
		String pattern;
		
		StringBuilder re;
		boolean hasMagic;
		
		GlobParsedPattern(String pattern)
		{
			this.isLiteral = true;
			this.pattern = pattern;
		}
		
		GlobParsedPattern(String pattern, boolean isLiteral)
		{
			this.isLiteral = isLiteral;
			this.pattern = pattern;
		}
		
		GlobParsedPattern(StringBuilder re, boolean hasMagic)
		{
			this.re = re;
			this.hasMagic = hasMagic;
		}
		
		@Override
		public String toString()
		{
			return pattern;
		}
	}
}