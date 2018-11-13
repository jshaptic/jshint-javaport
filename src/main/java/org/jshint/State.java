package org.jshint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

public final class State
{
	private static Map<String, Token> syntax = new HashMap<String, Token>();
	
	private static UniversalContainer option = ContainerFactory.undefinedContainer();
	private static int esVersion = 0;
	private static JSHint.Functor funct = null;
	private static UniversalContainer ignored = ContainerFactory.undefinedContainer();
	private static Map<String, Boolean> directive = null;
	private static boolean jsonMode = false;
	private static String[] lines = null;
	private static String tab = null;
	private static Map<String, String> cache = null;
	private static Map<Integer, Boolean> ignoredLines = null;
	private static boolean forinifcheckneeded = false;
	private static NameStack nameStack = null;
	private static boolean inClassBody = false;
	
	private static boolean condition = false;
	private static List<Token> forinifchecks = null;
	
	private static Token prev = null;
	private static Token next = null;
	private static Token curr = null;
	
	private State ()
	{
		
	}
	
	static Map<String, Token> getSyntax()
	{
		return syntax;
	}

	static UniversalContainer getOption()
	{
		return option;
	}

	static void setOption(UniversalContainer option)
	{
		State.option = option;
	}

	static JSHint.Functor getFunct()
	{
		return funct;
	}

	static void setFunct(JSHint.Functor funct)
	{
		State.funct = funct;
	}

	static UniversalContainer getIgnored()
	{
		return ignored;
	}

	static void setIgnored(UniversalContainer ignored)
	{
		State.ignored = ignored;
	}

	static Map<String, Boolean> getDirective()
	{
		return directive;
	}

	static void setDirective(Map<String, Boolean> directive)
	{
		State.directive = directive;
	}

	static boolean isJsonMode()
	{
		return jsonMode;
	}

	static void setJsonMode(boolean jsonMode)
	{
		State.jsonMode = jsonMode;
	}

	static String[] getLines()
	{
		return lines;
	}

	static void setLines(String[] lines)
	{
		State.lines = lines;
	}

	static String getTab()
	{
		return tab;
	}

	static void setTab(String tab)
	{
		State.tab = StringUtils.defaultString(tab);
	}

	static Map<String, String> getCache()
	{
		return cache;
	}

	static Map<Integer, Boolean> getIgnoredLines()
	{
		return ignoredLines;
	}

	static boolean isForinifcheckneeded()
	{
		return forinifcheckneeded;
	}

	static void setForinifcheckneeded(boolean forinifcheckneeded)
	{
		State.forinifcheckneeded = forinifcheckneeded;
	}

	static NameStack getNameStack()
	{
		return nameStack;
	}

	static boolean isInClassBody()
	{
		return inClassBody;
	}

	static void setInClassBody(boolean inClassBody)
	{
		State.inClassBody = inClassBody;
	}

	static boolean isCondition()
	{
		return condition;
	}

	static void setCondition(boolean condition)
	{
		State.condition = condition;
	}

	static List<Token> getForinifchecks()
	{
		return forinifchecks;
	}

	static void setForinifchecks(List<Token> forinifchecks)
	{
		State.forinifchecks = forinifchecks;
	}
	
	public static Token prevToken()
	{
		return prev;
	}
	
	static void setPrevToken(Token prev)
	{
		State.prev = prev;
	}
	
	public static Token nextToken()
	{
		return next;
	}
	
	static void setNextToken(Token next)
	{
		State.next = next;
	}
	
	public static Token currToken()
	{
		return curr;
	}
	
	static void setCurrToken(Token curr)
	{
		State.curr = curr;
	}

	/**
	 * Determine if the code currently being linted is strict mode code.
	 * 
	 * @return true if code is in strict mod, false otherwise.
	 */
	public static boolean isStrict()
	{
		return BooleanUtils.isTrue(getDirective().get("use strict")) || isInClassBody() ||
			getOption().test("module") || getOption().get("strict").equals("implied");
	}
	
	/**
	 * Determine if the current state warrants a warning for statements outside
	 * of strict mode code.
	 *
	 * While emitting warnings based on function scope would be more intuitive
	 * (and less noisy), JSHint observes statement-based semantics in order to
	 * preserve legacy behavior.
	 *
	 * This method does not take the state of the parser into account, making no
	 * distinction between global code and function code. Because the "missing
	 * 'use strict'" warning is *also* reported at function boundaries, this
	 * function interprets `strict` option values `true` and `undefined` as
	 * equivalent.
	 * 
	 * @return true if code misses use strict directive, false otherwise.
	 */
	public static boolean stmtMissingStrict()
	{
		if (getOption().get("strict").equals("global"))
		{
			return true;
		}
		
		if (getOption().get("strict").equals(false))
		{
			return false;
		}
		
		if (getOption().test("globalstrict"))
		{
			return true;
		}
		
		return false;
	}
	
	public static boolean allowsGlobalUsd()
	{
		return getOption().get("strict").equals("global") || getOption().test("globalstrict") ||
			getOption().test("module") || impliedClosure();
	}
	
	/**
	 * Determine if the current configuration describes an environment that is
	 * wrapped in an immediately-invoked function expression prior to evaluation.
	 *
	 * @return true if environment is wrapped in an immediately-invoked function expression, false otherwise.
	 */
	public static boolean impliedClosure()
	{
		return getOption().test("node") || getOption().test("phantom") || getOption().test("browserify");
	}
	
	// Assumption: chronologically ES3 < ES5 < ES6/ESNext < Moz
	public static boolean inMoz()
	{
		return getOption().test("moz");
	}
	
	/** Checks whether current configuration is ES6 compliant.
	 * 
	 * @return true if current environment is ES6, false otherwise.
	 * @see #inES6(boolean)
	 */
	public static boolean inES6()
	{
		return inES6(false);
	}
	
	/** Checks whether current configuration is ES6 compliant.
	 * 
	 * @param strict When true, only consider ES6 when in "esversion: 6" code.
	 * @return true if current environment is ES6, false otherwise.
	 */
	public static boolean inES6(boolean strict)
	{
		if (strict)
		{
			return esVersion == 6;
		}
		return getOption().test("moz") || esVersion >= 6;
	}
	
	/** Checks whether current configuration is ES5 compliant.
	 * 
	 * @return true if current environment is ES5, false otherwise.
	 * @see #inES5(boolean)
	 */
	public static boolean inES5()
	{
		return inES5(false);
	}
	
	/**
	 * Checks whether current configuration is ES5 compliant.
	 * 
	 * @param strict When true, return true only when esVersion is exactly 5
	 * @return true if current environment is ES5, false otherwise.
	 */
	public static boolean inES5(boolean strict)
	{
		if (strict)
		{
			return (esVersion == 0 || esVersion == 5) && !getOption().test("moz");
		}
		return esVersion == 0 || esVersion >= 5 || getOption().test("moz");
	}
	
	/**
	 * Determine the current version of the input language by inspecting the
	 * value of all ECMAScript-version-related options. This logic is necessary
	 * to ensure compatibility with deprecated options `es3`, `es5`, and
	 * `esnext`, and it may be drastically simplified when those options are
	 * removed.
	 *
	 * @return the name of any incompatible option detected, null otherwise
	 */
	public static String inferEsVersion()
	{
		String badOpt = null;
		
		if (getOption().test("esversion"))
		{
			if (getOption().test("es3"))
			{
				badOpt = "es3";
			}
			else if (getOption().test("es5"))
			{
				badOpt = "es5";
			}
			else if (getOption().test("esnext"))
			{
				badOpt = "esnext";
			}
			
			if (StringUtils.isNotEmpty(badOpt))
			{
				return badOpt;
			}
			
			if (getOption().get("esversion").equals(2015))
			{
				esVersion = 6;
			}
			else
			{
				esVersion = getOption().asInt("esversion");
			}
		}
		else if (getOption().test("es3"))
		{
			esVersion = 3;
		}
		else if (getOption().test("esnext"))
		{
			esVersion = 6;
		}
		
		return null;
	}
	
	public static void reset()
	{
		option = ContainerFactory.createObject();
		esVersion = 5;
		funct = null;
		ignored = ContainerFactory.createObject();
		directive = new HashMap<String, Boolean>();
		jsonMode = false;
		lines = new String[]{};
		tab = "";
		cache = new HashMap<String, String>();
		ignoredLines = new HashMap<Integer, Boolean>();
		forinifcheckneeded = false;
		nameStack = new NameStack();
		inClassBody = false;
	}
}