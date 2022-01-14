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

	private Map<String, Token>		syntax				= new HashMap<String, Token>();

	private UniversalContainer		option				= ContainerFactory.undefinedContainer();
	private int						esVersion			= 0;
	private JSHint.Functor			funct				= null;
	private UniversalContainer		ignored				= ContainerFactory.undefinedContainer();
	private Map<String, Boolean>	directive			= null;
	private boolean					jsonMode			= false;
	private String[]				lines				= null;
	private String					tab					= null;
	private Map<String, String>		cache				= null;
	private Map<Integer, Boolean>	ignoredLines		= null;
	private boolean					forinifcheckneeded	= false;
	private NameStack				nameStack			= null;
	private boolean					inClassBody			= false;

	private boolean					condition			= false;
	private List<Token>				forinifchecks		= null;

	private Token					prev				= null;
	private Token					next				= null;
	private Token					curr				= null;

	public State()
	{
	}



	Map<String, Token> getSyntax()
	{
		return syntax;
	}



	UniversalContainer getOption()
	{
		return option;
	}



	void setOption( UniversalContainer option )
	{
		this.option = option;
	}



	JSHint.Functor getFunct()
	{
		return funct;
	}



	void setFunct( JSHint.Functor funct )
	{
		this.funct = funct;
	}



	UniversalContainer getIgnored()
	{
		return ignored;
	}



	void setIgnored( UniversalContainer ignored )
	{
		this.ignored = ignored;
	}



	Map<String, Boolean> getDirective()
	{
		return directive;
	}



	void setDirective( Map<String, Boolean> directive )
	{
		this.directive = directive;
	}



	boolean isJsonMode()
	{
		return jsonMode;
	}



	void setJsonMode( boolean jsonMode )
	{
		this.jsonMode = jsonMode;
	}



	String[] getLines()
	{
		return lines;
	}



	void setLines( String[] lines )
	{
		this.lines = lines;
	}



	String getTab()
	{
		return tab;
	}



	void setTab( String tab )
	{
		this.tab = StringUtils.defaultString( tab );
	}



	Map<String, String> getCache()
	{
		return cache;
	}



	Map<Integer, Boolean> getIgnoredLines()
	{
		return ignoredLines;
	}



	boolean isForinifcheckneeded()
	{
		return forinifcheckneeded;
	}



	void setForinifcheckneeded( boolean forinifcheckneeded )
	{
		this.forinifcheckneeded = forinifcheckneeded;
	}



	NameStack getNameStack()
	{
		return nameStack;
	}



	boolean isInClassBody()
	{
		return inClassBody;
	}



	void setInClassBody( boolean inClassBody )
	{
		this.inClassBody = inClassBody;
	}



	boolean isCondition()
	{
		return condition;
	}



	void setCondition( boolean condition )
	{
		this.condition = condition;
	}



	List<Token> getForinifchecks()
	{
		return forinifchecks;
	}



	void setForinifchecks( List<Token> forinifchecks )
	{
		this.forinifchecks = forinifchecks;
	}



	public Token prevToken()
	{
		return prev;
	}



	void setPrevToken( Token prev )
	{
		this.prev = prev;
	}



	public Token nextToken()
	{
		return next;
	}



	void setNextToken( Token next )
	{
		this.next = next;
	}



	public Token currToken()
	{
		return curr;
	}



	void setCurrToken( Token curr )
	{
		this.curr = curr;
	}



	/**
	 * Determine if the code currently being linted is strict mode code.
	 * 
	 * @return true if code is in strict mod, false otherwise.
	 */
	public boolean isStrict()
	{
		return BooleanUtils.isTrue( getDirective().get( "use strict" ) ) || isInClassBody() ||
				getOption().test( "module" ) || getOption().get( "strict" ).equals( "implied" );
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
	public boolean stmtMissingStrict()
	{
		if( getOption().get( "strict" ).equals( "global" ) )
		{
			return true;
		}

		if( getOption().get( "strict" ).equals( false ) )
		{
			return false;
		}

		if( getOption().test( "globalstrict" ) )
		{
			return true;
		}

		return false;
	}



	public boolean allowsGlobalUsd()
	{
		return getOption().get( "strict" ).equals( "global" ) || getOption().test( "globalstrict" ) ||
				getOption().test( "module" ) || impliedClosure();
	}



	/**
	 * Determine if the current configuration describes an environment that is
	 * wrapped in an immediately-invoked function expression prior to evaluation.
	 *
	 * @return true if environment is wrapped in an immediately-invoked function expression, false otherwise.
	 */
	public boolean impliedClosure()
	{
		return getOption().test( "node" ) || getOption().test( "phantom" ) || getOption().test( "browserify" );
	}



	// Assumption: chronologically ES3 < ES5 < ES6/ESNext < Moz
	public boolean inMoz()
	{
		return getOption().test( "moz" );
	}



	/**
	 * Determine if constructs introduced in ECMAScript 8 should be accepted.
	 * 
	 * @return true if constructs introduced in ECMAScript 8 should be accepted, false otherwise
	 */
	public boolean inES9()
	{
		return esVersion >= 9;
	}



	/**
	 * Determine if constructs introduced in ECMAScript 8 should be accepted.
	 *
	 * @return true if constructs introduced in ECMAScript 8 should be accepted, false otherwise
	 */
	public boolean inES8()
	{
		return esVersion >= 8;
	}



	/**
	 * Determine if constructs introduced in ECMAScript 7 should be accepted.
	 *
	 * @return true if constructs introduced in ECMAScript 7 should be accepted, false otherwise
	 */
	public boolean inES7()
	{
		return esVersion >= 7;
	}



	/**
	 * Determine if constructs introduced in ECMAScript 6 should be accepted.
	 * 
	 * @return true if constructs introduced in ECMAScript 6 should be accepted, false otherwise
	 */
	public boolean inES6()
	{
		return inES6( false );
	}



	/**
	 * Determine if constructs introduced in ECMAScript 6 should be accepted.
	 * 
	 * @param strict - When `true`, do not interpret the `moz` option
	 * 				   as ECMAScript 6
	 * 
	 * @return true if constructs introduced in ECMAScript 6 should be accepted, false otherwise
	 */
	public boolean inES6( boolean strict )
	{
		if( ! strict && getOption().test( "moz" ) )
		{
			return true;
		}

		return esVersion >= 6;
	}



	/**
	 * Determine if constructs introduced in ECMAScript 5 should be accepted.
	 * 
	 * @return true if constructs introduced in ECMAScript 5 should be accepted, false otherwise
	 */
	public boolean inES5()
	{
		return esVersion == 0 || esVersion >= 5 || getOption().test( "moz" );
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
	public String inferEsVersion()
	{
		String badOpt = null;

		if( getOption().test( "esversion" ) )
		{
			if( getOption().test( "es3" ) )
			{
				badOpt = "es3";
			}
			else if( getOption().test( "es5" ) )
			{
				badOpt = "es5";
			}
			else if( getOption().test( "esnext" ) )
			{
				badOpt = "esnext";
			}

			if( StringUtils.isNotEmpty( badOpt ) )
			{
				return badOpt;
			}

			if( getOption().get( "esversion" ).equals( 2015 ) )
			{
				esVersion = 6;
			}
			else
			{
				esVersion = getOption().asInt( "esversion" );
			}
		}
		else if( getOption().test( "es3" ) )
		{
			esVersion = 3;
		}
		else if( getOption().test( "esnext" ) )
		{
			esVersion = 6;
		}

		return null;
	}



	public void reset()
	{
		prev = null;
		next = null;
		curr = null;
		option = ContainerFactory.createObject( "unstable", ContainerFactory.createObject() );
		esVersion = 5;
		funct = null;
		ignored = ContainerFactory.createObject();
		directive = new HashMap<String, Boolean>();
		jsonMode = false;
		lines = new String[] {};
		tab = "";
		cache = new HashMap<String, String>();
		ignoredLines = new HashMap<Integer, Boolean>();
		forinifcheckneeded = false;
		nameStack = new NameStack();
		inClassBody = false;
	}
}