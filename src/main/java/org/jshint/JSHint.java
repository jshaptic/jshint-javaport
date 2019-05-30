package org.jshint;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.utils.EventEmitter;
import org.jshint.utils.JSHintModule;
import org.jshint.LinterOptions.Delimiter;
import org.jshint.utils.EventContext;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

/**
 * Based on JSHint 2.9.5
 *
 */
public class JSHint
{
	// These are operators that should not be used with the ! operator.
	private static final Map<String, Boolean> bang = ImmutableMap.<String, Boolean>builder()
		.put("<", true)
		.put("<=", true)
		.put("==", true)
		.put("===", true)
		.put("!==", true)
		.put("!=", true)
		.put(">", true)
		.put(">=", true)
		.put("+", true)
		.put("-", true)
		.put("*", true)
		.put("/", true)
		.put("%", true)
		.build();
	
	private Map<String, Token> declared = null; // Globals that were declared using /*global ... */ syntax.
	
	private List<Functor> functions = null; // All of the functions
	
	private boolean inblock = false;
	private int indent = 0;
	private List<Token> lookahead = null;
	private Lexer lex = null;
	private Map<String, Integer> member = null;
	private Map<String, Boolean> membersOnly = null;
	private Map<String, Boolean> predefined = null; // Global variables defined by option
	
    private List<String> urls = null;
	
	private List<JSHintModule> extraModules = new ArrayList<JSHintModule>();
	private EventEmitter emitter = new EventEmitter();
	
	private Boolean checkOption(String name, boolean isStable, Token t)
	{
		String type;
		Set<String> validNames;
		
		if (isStable)
		{
			type = "";
			validNames = Options.validNames;
		}
		else
		{
			type = "unstable ";
			validNames = Options.unstableNames;
		}
		
		name = name.trim();
		
		if (Reg.isOptionMessageCode(name)) // PORT INFO: test regexp was moved to Reg class
		{
			return true;
		}
		
		if (!validNames.contains(name))
		{
			if (t.getType() != Token.Type.JSLINT && !Options.removed.containsKey(name))
			{
				error("E001", t, type, name);
				return false;
			}
		}
		
		return true;
	}
	
	private Boolean isIdentifier(Token tkn, String value)
	{
		if (tkn == null)
			return false;
		
		if (!tkn.isIdentifier() || !tkn.getValue().equals(value))
			return false;
		
		return true;
	}
	
	private Boolean isReserved(int context, Token token)
	{
		if (!token.isReserved())
		{
			return false;
		}
		Token.Meta meta = token.getMeta();
		
		if (meta != null && meta.isFutureReservedWord())
		{
			if (State.inES5())
			{
				// ES3 FutureReservedWord in an ES5 environment.
				if (!meta.isES5())
				{
					return false;
				}
				
				// Some ES5 FutureReservedWord identifiers are active only
				// within a strict mode environment.
				if (meta.isStrictOnly())
				{
					if (!State.getOption().test("strict") && !State.isStrict())
					{
						return false;
					}
				}
				
				if (token.isProperty())
				{
					return false;
				}
			}
		}
		if (token.getId().equals("await") && ((context & ProdParams.ASYNC) == 0 && !State.getOption().test("module")))
		{
			return false;
		}
		
		return true;
	}
	
	private String supplant(String str, LinterWarning data)
	{
		// PORT INFO: replace regexp was moved to Reg class
		return Reg.replaceMessageSupplant(str, data);
	}
	
	private void combine(Map<String, Boolean> dest, Map<String, Boolean> src)
	{
		for (String name: src.keySet())
		{
			if (!blacklist.contains(name))
			{
				dest.put(name, src.get(name));
			}
		}
	}
	
	private void processenforceall()
	{
		if (State.getOption().test("enforceall"))
		{
			for (String enforceopt : Options.bool.get("enforcing").keySet())
			{
				if (State.getOption().isUndefined(enforceopt) && 
					BooleanUtils.isNotTrue(Options.noenforceall.get(enforceopt)))
				{
					State.getOption().set(enforceopt, true);
				}
			}
			for (String relaxopt : Options.bool.get("relaxing").keySet())
			{
				if (!State.getOption().has(relaxopt))
				{
					State.getOption().set(relaxopt, false);
				}
			}
		}
	}
	
	/**
	 * Apply all linting options according to the status of the `state` object.
	 */
	private void applyOptions()
	{
		String badESOpt;
		processenforceall();
		
		/**
		 * JSHINT_TODO: Remove in JSHint 3
		 */
		badESOpt = State.inferEsVersion();
		if (StringUtils.isNotEmpty(badESOpt))
		{
			quit("E059", State.nextToken(), "esversion", badESOpt);
		}
		
		if (State.inES5())
		{
			combine(predefined, Vars.ecmaIdentifiers.get(5));
		}
		
		if (State.inES6())
		{
			combine(predefined, Vars.ecmaIdentifiers.get(6));
		}
		
		if (State.inES8())
		{
			combine(predefined, Vars.ecmaIdentifiers.get(8));
		}
		
		/**
	     * Use `in` to check for the presence of any explicitly-specified value for
	     * `globalstrict` because both `true` and `false` should trigger an error.
	     */
		if (State.getOption().get("strict").equals("global") && State.getOption().has("globalstrict"))
		{
			quit("E059", State.nextToken(), "strict", "globalstrict");
		}
		
		if (State.getOption().test("module"))
		{	
			/**
			 * JSHINT_TODO: Extend this restriction to *all* ES6-specific options.
			 */
			if (!State.inES6())
			{
				warning("W134", State.nextToken(), "module", "6");
			}
		}
		
		if (State.getOption().test("regexpu"))
		{	
			/**
			 * JSHINT_TODO: Extend this restriction to *all* ES6-specific options.
			 */
			if (!State.inES6())
			{
				warning("W134", State.nextToken(), "regexpu", "6");
			}
		}
		
		if (State.getOption().test("couch"))
		{
			combine(predefined, Vars.couch);
		}
		
		if (State.getOption().test("qunit"))
		{
			combine(predefined, Vars.qunit);
		}

		if (State.getOption().test("rhino"))
		{
			combine(predefined, Vars.rhino);
		}
		
		if (State.getOption().test("shelljs"))
		{
			combine(predefined, Vars.shelljs);
			combine(predefined, Vars.node);
		}
		
		if (State.getOption().test("typed"))
		{
			combine(predefined, Vars.typed);
		}
		
		if (State.getOption().test("phantom"))
		{
			combine(predefined, Vars.phantom);
		}

		if (State.getOption().test("prototypejs"))
		{
			combine(predefined, Vars.prototypejs);
		}
		
		if (State.getOption().test("node"))
		{
			combine(predefined, Vars.node);
			combine(predefined, Vars.typed);
		}
		
		if (State.getOption().test("devel"))
		{
			combine(predefined, Vars.devel);
		}
		
		if (State.getOption().test("dojo"))
		{
			combine(predefined, Vars.dojo);
		}

		if (State.getOption().test("browser"))
		{
			combine(predefined, Vars.browser);
			combine(predefined, Vars.typed);
		}
		
		if (State.getOption().test("browserify"))
		{
			combine(predefined, Vars.browser);
			combine(predefined, Vars.typed);
			combine(predefined, Vars.browserify);
		}
		
		if (State.getOption().test("nonstandard"))
		{
			combine(predefined, Vars.nonstandard);
		}
		
		if (State.getOption().test("jasmine"))
		{
			combine(predefined, Vars.jasmine);
		}
		
		if (State.getOption().test("jquery"))
		{
			combine(predefined, Vars.jquery);
		}
		
		if (State.getOption().test("mootools"))
		{
			combine(predefined, Vars.mootools);
		}
		
		if (State.getOption().test("worker"))
		{
			combine(predefined, Vars.worker);
		}
		
		if (State.getOption().test("wsh"))
		{
			combine(predefined, Vars.wsh);
		}
		if (State.getOption().test("yui"))
			
		{
			combine(predefined, Vars.yui);
		}
		
		if (State.getOption().test("mocha"))
		{
			combine(predefined, Vars.mocha);
		}
	}
	
	// Produce an error warning.
	private void quit(String code, Token token, String... substitutions)
	{
		int percentage = (int)Math.floor((double)token.getLine() / State.getLines().length * 100);
		String message = Messages.errors.get(code);
		
		LinterWarning w = new LinterWarning();
		w.setLine(token.getLine());
		w.setCharacter(token.getFrom());
		w.setRaw(message);
		w.setCode(code);
		w.setSubstitutions(substitutions);
		
		w.setReason(supplant(message, w) + " (" + percentage + "% scanned).");
		
		throw new JSHintException(w, message + " (" + percentage + "% scanned).");
	}
	
	private void removeIgnoredMessages()
	{
		Map<Integer, Boolean> ignored = State.getIgnoredLines();
		
		if (ignored.isEmpty()) return;
		for (Iterator<LinterWarning> iterator = errors.iterator(); iterator.hasNext();)
		{
			LinterWarning err = iterator.next();
			if (BooleanUtils.isTrue(ignored.get(err.getLine())))
			{
				iterator.remove();
			}
		}
	}
	
	private LinterWarning warning(String code)
	{
		return warning(code, null);
	}
	
	private LinterWarning warning(String code, Token t, String... substitutions)
	{
		String msg = "";
		
		// PORT INFO: test regexp was replaced with straight check
		if (code.startsWith("W") && code.length() == 4 && StringUtils.isNumeric(code.substring(1))) 
		{
			if (State.getIgnored().test(code))
				return null;
			
			msg = Messages.warnings.get(code);
		}
		// PORT INFO: test regexp was replaced with straight check
		else if (code.startsWith("E") && code.length() == 4 && StringUtils.isNumeric(code.substring(1)))
		{
			msg = Messages.errors.get(code);
		}
		// PORT INFO: test regexp was replaced with straight check
		else if (code.startsWith("I") && code.length() == 4 && StringUtils.isNumeric(code.substring(1)))
		{
			msg = Messages.info.get(code);
		}
		
		t = (t != null ? t : (State.nextToken() != null ? State.nextToken() : new Token()));
		if (t.getId().equals("(end)")) // `~
		{
			t = State.currToken();
		}
		
		int l = t.getLine();
		int ch = t.getFrom();
		
		LinterWarning w = new LinterWarning();
		w.setId("(error)");
		w.setRaw(msg);
		w.setCode(code);
		w.setEvidence((l > 0 && State.getLines().length > l-1) ? State.getLines()[l-1] : "");
		w.setLine(l);
		w.setCharacter(ch);
		w.setScope(scriptScope);
		w.setSubstitutions(substitutions);
		
		w.setReason(supplant(msg, w));
		errors.add(w);
		
		removeIgnoredMessages();
		
		if (State.getOption().test("maxerr") && errors.size() >= State.getOption().asInt("maxerr"))
		{
			quit("E043", t);
		}
		
		return w;
	}
	
	private LinterWarning warningAt(String m, int l, int ch, String... substitutions)
	{
		Token t = new Token();
		t.setLine(l);
		t.setFrom(ch);
		
		return warning(m, t, substitutions);
	}
	
	private void error(String m)
	{
		error(m, null);
	}
	
	private void error(String m, Token t, String... substitutions)
	{
		warning(m, t, substitutions);
	}
	
	private void errorAt(String m, int l)
	{
		errorAt(m, l, 0);
	}
	
	private void errorAt(String m, int l, int ch, String... substitutions)
	{
		Token t = new Token();
		t.setLine(l);
		t.setFrom(ch);
		error(m, t, substitutions);
	}
	
	// Tracking of "internal" scripts, like eval containing a static string
	private void addEvalCode(Token elem, Token token)
	{
		// PORT INFO: replace regexp was moved to Reg class
		internals.add(new InternalSource("(internal)", elem, token, Reg.unescapeNewlineChars(token.getValue())));
	}
	
	/**
	 * Process an inline linting directive.
	 * 
	 * @param directiveToken the directive-bearing comment token
	 * @param previous the token that preceeds the directive
	 */
	private void lintingDirective(Token directiveToken, Token previous)
	{
		List<String> body = Splitter.on(",").splitToList(directiveToken.getBody()).stream()
			.map(s -> s.trim()).collect(Collectors.toList());
		Map<String, Boolean> predef = new HashMap<String, Boolean>();
		
		if (directiveToken.getType() == Token.Type.FALLS_THROUGH)
		{
			previous.setCaseFallsThrough(true);
			return;
		}
		
		if (directiveToken.getType() == Token.Type.GLOBALS)
		{
			for (int idx = 0; idx < body.size(); idx++)
			{
				String[] g = body.get(idx).split(":", -1);
				String key = g[0].trim();
				String val = g.length > 1 ? g[1].trim() : "";
				
				if (key.equals("-") || key.length() == 0)
				{
					// Ignore trailing comma
					if (idx > 0 && idx == body.size() - 1)
					{
						continue;
					}
					error("E002", directiveToken);
					continue;
				}
				
				if (key.startsWith("-"))
				{
					key = key.substring(1);
					val = "false";
					
					blacklist.add(key);
					predefined.remove(key);
				}
				else
				{
					predef.put(key, val.equals("true"));
				}
			}
			
			combine(predefined, predef);
			
			for (String key : predef.keySet())
			{
				declared.put(key, directiveToken);
			}
		}
		
		if (directiveToken.getType() == Token.Type.EXPORTED)
		{
			for (int idx = 0; idx < body.size(); idx++)
			{
				String e = body.get(idx);
				if (StringUtils.isEmpty(e))
				{
					// Ignore trailing comma
					if (idx > 0 && idx == body.size() - 1)
					{
						continue;
					}
					error("E002", directiveToken);
					continue;
				}
				
				State.getFunct().getScope().addExported(e);
			}
		}
		
		if (directiveToken.getType() == Token.Type.MEMBERS)
		{
			if (membersOnly == null) membersOnly = new HashMap<String, Boolean>();
			
			for (String m : body)
			{
				char ch1 = m.charAt(0);
				char ch2 = m.charAt(m.length()-1);
				
				if (ch1 == ch2 && (ch1 == '"' || ch1 == '\''))
				{
					m = StringUtils.replace(m.substring(1, m.length() - 1), "\\\"", "\"");
				}
				
				membersOnly.put(m, false);
			}
		}
		
		Set<String> numvals = new HashSet<String>();
		numvals.add("maxstatements");
		numvals.add("maxparams");
		numvals.add("maxdepth");
		numvals.add("maxcomplexity");
		numvals.add("maxerr");
		numvals.add("maxlen");
		numvals.add("indent");
		
		if (directiveToken.getType() == Token.Type.JSHINT || directiveToken.getType() == Token.Type.JSLINT ||
			directiveToken.getType() == Token.Type.JSHINT_UNSTABLE)
		{
			for (int idx = 0; idx < body.size(); idx++)
			{
				String[] g = body.get(idx).split(":", -1);
				String key = g[0].trim();
				String val = g.length > 1 ? g[1].trim() : "";
				
				if (!checkOption(key, directiveToken.getType() != Token.Type.JSHINT_UNSTABLE , directiveToken))
				{
					continue;
				}
				
				if (numvals.contains(key))
				{
					// GH988 - numeric options can be disabled by setting them to `false`
					if (!val.equals("false"))
					{
						Double numval;
						try
						{
							numval = Double.valueOf(val);
						}
						catch (NumberFormatException e)
						{
							error("E032", directiveToken, val);
							continue;
						}
						
						if (numval.isNaN() || numval.isInfinite() || numval <= 0 || Math.floor(numval) != numval)
						{
							error("E032", directiveToken, val);
							continue;
						}
						
						State.getOption().set(key, numval);
					}
					else
					{
						State.getOption().set(key, key.equals("indent") ? 4 : false);
					}
					
					continue;
				}
				
				if (key.equals("validthis"))
				{
					// `validthis` is valid only within a function scope.
					
					if (State.getFunct().isGlobal())
					{
						error("E009");
						continue;
					}
					
					if (!val.equals("true") && !val.equals("false"))
					{
						error("E002", directiveToken);
						continue;
					}
					
					State.getOption().set("validthis", val.equals("true"));
					continue;
				}
				
				if (key.equals("quotmark"))
				{
					switch (val)
					{
					case "true":
					case "false":
						State.getOption().set("quotmark", val.equals("true"));
						break;
					case "double":
					case "single":
						State.getOption().set("quotmark", val);
						break;
					default:
						error("E002", directiveToken);
					}
					continue;
				}
				
				if (key.equals("shadow"))
				{
					switch (val)
					{
					case "true":
						State.getOption().set("shadow", true);
						break;
					case "outer":
						State.getOption().set("shadow", "outer");
						break;
					case "false":
					case "inner":
						State.getOption().set("shadow", "inner");
						break;
					default:
						error("E002", directiveToken);
					}
					continue;
				}
				
				if (key.equals("unused"))
				{
					switch (val)
					{
					case "true":
						State.getOption().set("unused", true);
						break;
					case "false":
						State.getOption().set("unused", false);
						break;
					case "vars":
					case "strict":
						State.getOption().set("unused", val);
						break;
					default:
						error("E002", directiveToken);
					}
					continue;
				}
				
				if (key.equals("latedef"))
				{
					switch (val)
					{
					case "true":
						State.getOption().set("latedef", true);
						break;
					case "false":
						State.getOption().set("latedef", false);
						break;
					case "nofunc":
						State.getOption().set("latedef", "nofunc");
						break;
					default:
						error("E002", directiveToken);
					}
					continue;
				}
				
				if (key.equals("ignore"))
				{
					switch (val)
					{
					case "line":
						State.getIgnoredLines().put(directiveToken.getLine(), true);
						removeIgnoredMessages();
						break;
					default:
						error("E002", directiveToken);
					}
					continue;
				}
				
				if (key.equals("strict"))
				{
					switch (val)
					{
					case "true":
						State.getOption().set("strict", true);
						break;
					case "false":
						State.getOption().set("strict", false);
						break;
					case "global":
					case "implied":
						State.getOption().set("strict", val);
						break;
					default:
						error("E002", directiveToken);
					}
					continue;
				}
				
				if (key.equals("module"))
				{
					/**
					 * JSHINT_TODO: Extend this restriction to *all* "environmental" options.
					 */
					if (!hasParsedCode(State.getFunct()))
					{
						error("E055", directiveToken, "module");
					}
				}
				
				if (key.equals("esversion"))
				{
					switch (val)
					{
					case "3":
					case "5":
					case "6":
					case "7":
					case "8":
					case "9":
						State.getOption().set("moz", false);
						State.getOption().set("esversion", Ints.tryParse(val));
						break;
					case "2015":
					case "2016":
					case "2017":
					case "2018":
						State.getOption().set("moz", false);
						// Translate specification publication year to version number.
						State.getOption().set("esversion", Ints.tryParse(val) - 2009);
						break;
					default:
						error("E002", directiveToken);
					}
					if (!hasParsedCode(State.getFunct()))
					{
						error("E055", directiveToken, "esversion");
					}
					continue;
				}
				
				if (Reg.isOptionMessageCode(key)) // PORT INFO: exec regexp was moved to Reg class
				{
					// ignore for -W..., unignore for +W...
					State.getIgnored().set(key.substring(1), key.startsWith("-"));
					continue;
				}
				
				if (val.equals("true") || val.equals("false"))
				{
					if (directiveToken.getType() == Token.Type.JSLINT)
					{
						String tn = Options.renamed.get(key);
						if (tn == null) tn = key;
						State.getOption().set(tn, val.equals("true"));
						
						if (Options.inverted.get(tn) != null)
						{
							State.getOption().set(tn, !State.getOption().get(tn).test());
						}
					}
					else if (directiveToken.getType() == Token.Type.JSHINT_UNSTABLE)
					{
						State.getOption().get("unstable").set(key, val.equals("true"));
					}
					else
					{
						State.getOption().set(key, val.equals("true"));
					}
					
					continue;
				}
				
				error("E002", directiveToken);
			}
			
			applyOptions();
		}
	}
	
	/**
	 * Invokes {@link #peek(int)} with 0 value.
	 * 
	 * @return next token.
	 * @see #peek(int)
	 */
	private Token peek()
	{
		return peek(0);
	}
	
	/**
	 * Return a token beyond the token available in `state.tokens.next`. If no
	 * such token exists, return the "(end)" token. This function is used to
	 * determine parsing strategies in cases where the value of the next token
	 * does not provide sufficient information, as is the case with `for` loops,
	 * e.g.:
	 * 
	 *     for ( var i in ...
	 * 
	 * 
	 * versus:
	 * 
	 *     for ( var i = ..
	 * 
	 * @param p offset of desired token; defaults to 0
	 * @return next token
	 */
	private Token peek(int p)
	{
		int i = p;
		int j = lookahead.size();
		Token t = null;
		
		if (i < j)
		{
			return lookahead.get(i);
		}
		
		while (j <= i)
		{
			t = lex.token();
			
			// When the lexer is exhausted, this function should produce the "(end)"
		    // token, even in cases where the requested token is beyond the end of
			// the input stream.
			if (t == null)
			{
				// If the lookahead buffer is empty, the expected "(end)" token was
				// already emitted by the most recent invocation of `advance` and is
				// available as the next token.
				if (lookahead.size() == 0)
				{
					return State.nextToken();
				}
				
				return lookahead.get(j-1);
			}
			
			lookahead.add(t);
			j += 1;
		}
		
		return t;
	}
	
	private Token peekIgnoreEOL()
	{
		int i = 0;
		Token t = null;
		do
		{
			t = peek(i++);
		}
		while (t != null && t.getId().equals("(endline)"));
		
		return t;
	}
	
	/**
	 * Consume the next token.
	 */
	private void advance()
	{
		advance(null, null);
	}
	
	/**
	 * Consume the next token.
	 *
	 * @param expected - the expected value of the next token's `id`
	 *                   property (in the case of punctuators) or
	 *                   `value` property (in the case of identifiers
	 *                   and literals); if unspecified, any token will
	 *                   be accepted
	 */
	private void advance(String expected)
	{
		advance(expected, null);
	}
	
	/**
	 * Consume the next token.
	 *
	 * @param expected - the expected value of the next token's `id`
	 *                   property (in the case of punctuators) or
	 *                   `value` property (in the case of identifiers
	 *                   and literals); if unspecified, any token will
	 *                   be accepted
	 * @param relatedToken - the token that informed the expected
	 *                       value, if any (for example: the opening
	 *                       brace when a closing brace is expected);
	 *                       used to produce more meaningful errors
	 */
	private void advance(String expected, Token relatedToken)
	{
		Token nextToken = State.nextToken();
		
		switch (State.currToken().getId())
		{
		case "(number)":
			if (nextToken.getId().equals("."))
			{
				warning("W005", State.currToken());
			}
			break;
		case "-":
			if (nextToken.getId().equals("-") || nextToken.getId().equals("--"))
			{
				warning("W006");
			}
			break;
		case "+":
			if (nextToken.getId().equals("+") || nextToken.getId().equals("++"))
			{
				warning("W007");
			}
			break;
		}
		
		if (StringUtils.isNotEmpty(expected) && !nextToken.getId().equals(expected))
		{
			if (relatedToken != null)
			{
				if (nextToken.getId().equals("(end)"))
				{
					error("E019", relatedToken, relatedToken.getId());
				}
				else
				{
					error("E020", nextToken, expected, relatedToken.getId(),
						String.valueOf(relatedToken.getLine()), nextToken.getValue());
				}
			}
			else if (nextToken.getType() != Token.Type.IDENTIFIER || !nextToken.getValue().equals(expected))
			{
				warning("E021", nextToken, expected, nextToken.getValue());
			}
		}
		
		State.setPrevToken(State.currToken());
		State.setCurrToken(State.nextToken());
		for(;;)
		{
			State.setNextToken(lookahead.size() > 0 ? lookahead.remove(0) : null);
			if (State.nextToken() == null) State.setNextToken(lex.token());
			
			if (State.nextToken() == null) // No more tokens left, give up
			{
				quit("E041", State.currToken());
			}
			
			if (State.nextToken().getId().equals("(end)") || State.nextToken().getId().equals("(error)"))
			{
				return;
			}
			
			if (State.nextToken().getCheck() != null)
			{
				State.nextToken().check();
			}
			
			if (State.nextToken().isSpecial())
			{
				lintingDirective(State.nextToken(), State.currToken());
			}
			else
			{
				if (!State.nextToken().getId().equals("(endline)"))
				{
					break;
				}
			}
		}
	}
	
	/**
	 * Determine whether a given token is an operator.
	 * 
	 * @param token current token.
	 * @return true is current token is operator, false otherwise.
	 */
	private boolean isOperator(Token token)
	{
		return token.getFirstToken() != null || token.getRight() != null || token.getLeft() != null || token.getId().equals("yield") || token.getId().equals("await");
	}
	
	private boolean isEndOfExpr()
	{
		return isEndOfExpr(0, State.currToken(), State.nextToken());
	}
	
	private boolean isEndOfExpr(int context)
	{
		return isEndOfExpr(context, State.currToken(), State.nextToken());
	}
	
	private boolean isEndOfExpr(int context, Token curr, Token next)
	{
		if (next.getId().equals("in") && (context & ProdParams.NOIN) != 0)
		{
			return true;
		}
		
		if (next.getId().equals(";") || next.getId().equals("}") || next.getId().equals(":"))
		{
			return true;
		}
		if (next.isInfix() == curr.isInfix() || curr.getLtBoundary() == Token.BoundaryType.AFTER ||
			next.getLtBoundary() == Token.BoundaryType.BEFORE)
		{
			return curr.getLine() != startLine(next);
		}
		return false;
	}
	
	/**
	 * The `expression` function is the heart of JSHint's parsing behaior. It is
	 * based on the Pratt parser, but it extends that model with a `fud` method.
	 * Short for "first null denotation," it it similar to the `nud` ("null
	 * denotation") function, but it is only used on the first token of a
	 * statement. This simplifies usage in statement-oriented languages like
	 * JavaScript.
	 *
	 * .nud  Null denotation
	 * .fud  First null denotation
	 * .led  Left denotation
	 *  lbp  Left binding power
	 *  rbp  Right binding power
	 *
	 * They are elements of the parsing method called Top Down Operator Precedence.
	 *
	 * In addition to parsing, this function applies a number of linting patterns.
	 *
	 * @param context - the parsing context (a bitfield describing
	 *                  conditions of the current parsing operation
	 *                  which can influence how the next tokens are
	 *                  interpreted); see `prod-params.js` for more
	 *                  detail)
	 * @param rbp - the right-binding power of the token to be consumed
	 */
	private Token expression(int context, int rbp)
	{
		Token left = null;
		boolean isArray = false;
		boolean isObject = false;
		boolean initial = (context & ProdParams.INITIAL) != 0;
		
		context &= ~ProdParams.INITIAL;
		
		State.getNameStack().push();
		
		if (State.nextToken().getId().equals("(end)"))
			error("E006", State.currToken());
		
		advance();
		
		if (initial)
		{
			State.getFunct().setVerb(State.currToken().getValue());
			State.currToken().setBeginsStmt(true);
		}
		
		Token curr = State.currToken();
		
		if (initial && curr.getFud() != null && (curr.getUseFud() == null || curr.useFud(context)))
		{
			left = State.currToken().fud(context);
		}
		else
		{
			if (State.currToken().getNud() != null)
			{
				left = State.currToken().nud(context, rbp);
			}
			else
			{
				error("E030", State.currToken(), State.currToken().getId());
			}
			
			while (rbp < State.nextToken().getLbp() && !isEndOfExpr(context))
			{
				isArray = State.currToken().getValue().equals("Array");
				isObject = State.currToken().getValue().equals("Object");
				
				// #527, new Foo.Array(), Foo.Array(), new Foo.Object(), Foo.Object()
				// Line breaks in IfStatement heads exist to satisfy the checkJSHint
				// "Line too long." error.
				if (left != null && (StringUtils.isNotEmpty(left.getValue()) || (left.getFirstToken() != null && StringUtils.isNotEmpty(left.getFirstToken().getValue()))))
				{
					// If the left.value is not "new", or the left.first.value is a "."
					// then safely assume that this is not "new Array()" and possibly
					// not "new Object()"...
					if (!left.getValue().equals("new") ||
					   (left.getFirstToken() != null && StringUtils.isNotEmpty(left.getFirstToken().getValue()) && left.getFirstToken().getValue().equals(".")))
					{
						isArray = false;
						// ...In the case of Object, if the left.value and State.currToken().value
						// are not equal, then safely assume that this not "new Object()"
						if (!left.getValue().equals(State.currToken().getValue()))
						{
							isObject = false;
						}
					}
				}
				
				advance();
				
				if (isArray && State.currToken().getId().equals("(") && State.nextToken().getId().equals(")"))
				{
					warning("W009", State.currToken());
				}
				
				if (isObject && State.currToken().getId().equals("(") && State.nextToken().getId().equals(")"))
				{
					warning("W010", State.currToken());
				}
				
				if (left != null && State.currToken().getLed() != null)
				{
					left = State.currToken().led(context, left);
				}
				else
				{
					error("E033", State.currToken(), State.currToken().getId());
				}
			}
		}
		
		State.getNameStack().pop();
		
		return left;
	}
	
	// Functions for conformance of style.
	
	private int startLine(Token token)
	{
		return token.getStartLine() != 0 ? token.getStartLine() : token.getLine(); 
	}
	
	private void nobreaknonadjacent(Token left, Token right)
	{
		if (!State.getOption().get("laxbreak").test() && left.getLine() != startLine(right))
		{
			warning("W014", right, right.getValue());
		}
	}
	
	private void nolinebreak(Token t)
	{
		// t = t; JSHINT_BUG: unnecessary assignment
		if (t.getLine() != startLine(State.nextToken()))
		{
			warning("E022", t, t.getValue());
		}
	}
	
	private void nobreakcomma(Token left, Token right)
	{
		if (left.getLine() != right.getLine())
		{
			if (!State.getOption().get("laxcomma").test())
			{
				if (parseCommaFirst)
				{
					warning("I001");
					parseCommaFirst = false;
				}
				warning("W014", left, right.getValue());
			}
		}
	}

	private boolean parseCommaFirst = false;
	
	private boolean parseComma()
	{
		return parseComma(false, false, false);
	}
	
	private boolean parseComma(boolean peek, boolean property, boolean allowTrailing)
	{
		if (!peek)
		{
			nobreakcomma(State.currToken(), State.nextToken());
			advance(",");
		}
		else
		{
			nobreakcomma(State.prevToken(), State.currToken());
		}
		
		if (State.nextToken().isIdentifier() && !(property && State.inES5()))
		{
			// Keywords that cannot follow a comma operator.
			switch (State.nextToken().getValue())
			{
			case "break":
			case "case":
			case "catch":
			case "continue":
			case "default":
			case "do":
			case "else":
			case "finally":
			case "for":
			case "if":
			case "in":
			case "instanceof":
			case "return":
			case "switch":
			case "throw":
			case "try":
			case "var":
			case "let":
			case "while":
			case "with":
				error("E024", State.nextToken(), State.nextToken().getValue());
				return false;
			}
		}
		
		if (State.nextToken().getType() == Token.Type.PUNCTUATOR)
		{
			switch (State.nextToken().getValue())
			{
			case "}":
			case "]":
			case ",":
			case ")":
				if (allowTrailing)
				{
					return true;
				}
				
				error("E024", State.nextToken(), State.nextToken().getValue());
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Factory function for creating "symbols"--objects that will be inherited by
	 * tokens. The objects created by this function are stored in a symbol table
	 * and set as the prototype of the tokens generated by the lexer.
	 *
	 * Note that this definition of "symbol" describes an implementation detail
	 * of JSHint and is not related to the ECMAScript value type introduced in
	 * ES2015.
	 *
	 * @param s - the name of the token; for keywords (e.g. `void`) and
	 *            delimiters (e.g.. `[`), this is the token's text
	 *            representation; for literals (e.g. numbers) and other
	 *            "special" tokens (e.g. the end-of-file marker) this is
	 *            a parenthetical value
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token symbol(String s, int p)
	{
		Token x = State.getSyntax().get(s);
		if (x == null)
		{
			// Symbols that accept a right-hand side do so with a binding power
	        // that is commonly identical to their left-binding power. (This value
	        // is relevant when determining if the grouping operator is necessary
	        // to override the precedence of surrounding operators.) Because the
	        // exponentiation operator's left-binding power and right-binding power
	        // are distinct, the values must be encoded separately.
			State.getSyntax().put(s, x = new Token(s, p, p, s));
		}
		return x;
	}

	/**
	 * Convenience function for defining delimiter symbols.
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token delim(String s)
	{
		Token x = symbol(s, 0);
		x.setDelim(true);
		return x;
	}

	/**
	 * Convenience function for defining statement-denoting symbols.
	 *
	 * @param s - the name of the symbol
	 * @param f - the first null denotation function for the symbol;
	 *            see the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token stmt(String s, Function<Token, IntFunction<Token>> f)
	{
		Token x = delim(s);
		x.setIdentifier(true);
		x.setReserved(true);
		x.setFud(f); 
		return x;
	}

	/**
	 * Convenience function for defining block-statement-denoting symbols.
	 *
	 * A block-statement-denoting symbol is one like 'if' or 'for', which will be
	 * followed by a block and will not have to end with a semicolon.
	 *
	 * @param s - the name of the symbol
	 * @param f - the first null denotation function for the symbol; see
	 *            the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token blockstmt(String s, Function<Token, IntFunction<Token>> f)
	{
		Token x = stmt(s, f);
		x.setBlock(true);
		return x;
	}

	/**
	 * Denote a given JSHint symbol as an identifier and a reserved keyword.
	 *
	 * @param x - a JSHint symbol value
	 *
	 * @return the provided object
	 */
	private Token reserveName(Token x)
	{
		char c = x.getId().charAt(0);
		if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
		{
			x.setIdentifier(true);
			x.setReserved(true);
		}
		return x;
	}
	
	/**
	 * Convenience function for defining "prefix" symbols--operators that accept
	 * expressions as a right-hand side.
	 *
	 * @param s - the name of the symbol
	 * @param f - string value that just describes execution case
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token prefix(String s, String f)
	{
		return prefix(s);
	}
	
	/**
	 * Convenience function for defining "prefix" symbols--operators that accept
	 * expressions as a right-hand side.
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token prefix(String s)
	{
		return prefix(s, _this -> context -> rbp -> {
			_this.setArity(Token.ArityType.UNARY);
			_this.setRight(expression(context, 150));
			
			if (_this.getId().equals("++") || _this.getId().equals("--"))
			{
				if (State.getOption().test("plusplus"))
				{
					warning("W016", _this, _this.getId());
				}
				
				if (_this.getRight() != null)
				{
					checkLeftSideAssign(context, _this.getRight(), _this);
				}
			}
			
			return _this;
		});
	}
	
	/**
	 * Convenience function for defining "prefix" symbols--operators that accept
	 * expressions as a right-hand side.
	 *
	 * @param s - the name of the symbol
	 * @param f - the first null denotation function for the symbol;
	 *            see the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token prefix(String s, Function<Token, IntFunction<IntFunction<Token>>> f)
	{
		Token x = symbol(s, 150);
		reserveName(x);
		
		x.setNud(f);
		
		return x;
	}

	/**
	 * Convenience function for defining "type" symbols--those that describe
	 * literal values.
	 *
	 * @param s - the name of the symbol
	 * @param f - the first null denotation function for the symbol;
	 *            see the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token type(Token.Type s, Function<Token, IntFunction<IntFunction<Token>>> f)
	{
		Token x = delim(s.toString());
		x.setType(s);
		x.setNud(f);
		return x;
	}

	/**
	 * Convenience function for defining JSHint symbols for reserved
	 * keywords--those that are restricted from use as bindings (and as propery
	 * names in ECMAScript 3 environments).
	 *
	 * @param name - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token reserve(Token.Type name)
	{
		return reserve(name, null);
	}
	
	/**
	 * Convenience function for defining JSHint symbols for reserved
	 * keywords--those that are restricted from use as bindings (and as propery
	 * names in ECMAScript 3 environments).
	 *
	 * @param name - the name of the symbol
	 * @param func - the first null denotation function for the
	 *               symbol; see the `expression` function for more
	 *               detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token reserve(Token.Type name, Function<Token, IntFunction<IntFunction<Token>>> func)
	{
		Token x = type(name, func);
		x.setIdentifier(true);
		x.setReserved(true);
		return x;
	}

	/**
	 * Convenience function for defining JSHint symbols for keywords that are
	 * only reserved in some circumstances.
	 *
	 * @param name - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token futureReservedWord(Token.Type name)
	{
		return futureReservedWord(name, null);
	}
	
	/**
	 * Convenience function for defining JSHint symbols for keywords that are
	 * only reserved in some circumstances.
	 *
	 * @param name - the name of the symbol
	 * @param meta - a collection of optional arguments: <br/>
	 * 				 meta.nud - the null denotation function for the symbol;
	 *               see the `expression` function for more detail <br/>
	 *               meta.es5 - `true` if the identifier is reserved
	 *               in ECMAScript 5 or later <br/>
	 *               meta.strictOnly - `true` if the identifier is only
	 *               reserved in strict mode code.
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token futureReservedWord(Token.Type name, Token.Meta meta)
	{
		Token x = type(name, (meta != null && meta.getNud() != null) ? meta.getNud() : _this -> context -> rbp -> _this);
		
		if (meta == null) meta = new Token.Meta();
		meta.setFutureReservedWord(true);
		
		x.setValue(name.toString());
		x.setIdentifier(true);
		x.setReserved(true);
		x.setMeta(meta);
		
		return x;
	}

	/**
	 * Convenience function for defining JSHint symbols for reserved
	 * binding identifiers.
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token reservevar(Token.Type s)
	{
		return reservevar(s, null);
	}
	
	/**
	 * Convenience function for defining JSHint symbols for reserved
	 * binding identifiers.
	 *
	 * @param s - the name of the symbol
	 * @param v - the first null denotation function for the symbol;
	 *            see the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token reservevar(Token.Type s, Consumer<Token> v)
	{
		return reserve(s, _this -> context -> rbp -> {
			if (v != null)
			{
				v.accept(_this);
			}
			return _this;
		});
	}
	
	/**
	 * Convenience function for defining "infix" symbols--operators that require
	 * operands as both "land-hand side" and "right-hand side".
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token infix(String s)
	{
		return infix(s, null, 0, false);
	}
	
	/**
	 * Convenience function for defining "infix" symbols--operators that require
	 * operands as both "land-hand side" and "right-hand side".
	 *
	 * @param s - the name of the symbol
	 * @param f - string value that just describes execution case
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token infix(String s, String f, int p)
	{
		return infix(s, null, p, false);
	}
	
	/**
	 * Convenience function for defining "infix" symbols--operators that require
	 * operands as both "land-hand side" and "right-hand side".
	 *
	 * @param s - the name of the symbol
	 * @param f - a function to be invoked that consumes the
	 *            right-hand side of the operator
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token infix(String s, IntFunction<BiFunction<Token, Token, Token>> f, int p)
	{
		return infix(s, f, p, false);
	}
	
	/**
	 * Convenience function for defining "infix" symbols--operators that require
	 * operands as both "land-hand side" and "right-hand side".
	 *
	 * @param s - the name of the symbol
	 * @param f - a function to be invoked that consumes the
	 *            right-hand side of the operator
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 * @param w - if `true`
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token infix(String s, IntFunction<BiFunction<Token, Token, Token>> f, int p, boolean w)
	{
		Token x = symbol(s, p);
		reserveName(x);
		x.setInfix(true);
		x.setLed(_this -> context -> left -> {
			if (!w)
			{
				nobreaknonadjacent(State.prevToken(), State.currToken());
			}
			if ((s.equals("in") || s.equals("instanceof")) && left.getId().equals("!"))
			{
				warning("W018", left, "!");
			}
			if (f != null)
			{
				return f.apply(context).apply(left, _this);
			}
			else
			{
				_this.setLeft(left);
				_this.setRight(expression(context, p)); 
				return _this;
			}
		});
		
		return x;
	}

	/**
	 * Convenience function for defining the `=>` token as used in arrow
	 * functions.
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token application(String s)
	{
		Token x = symbol(s, 42);
		
		x.setInfix(true);
		x.setLed(_this -> context -> left -> {
			nobreaknonadjacent(State.prevToken(), State.currToken());
			
			_this.setLeft(left);
			_this.setRight(doFunction(
				context,
				null,
				null,
				FunctionType.ARROW,
				left,
				false,
				null,
				false,
				false
			));
			return _this;
		});
		return x;
	}

	/**
	 * Convenience function for defining JSHint symbols for relation operators.
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token relation(String s)
	{
		return relation(s, null);
	}
	
	/**
	 * Convenience function for defining JSHint symbols for relation operators.
	 *
	 * @param s - the name of the symbol
	 * @param f - a function to be invoked to enforce any additional
	 *            linting rules.
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token relation(String s, Function<Token, IntFunction<BiFunction<Token, Token, Token>>> f)
	{
		Token x = symbol(s, 100);
		
		x.setInfix(true);
		x.setLed(_this -> context -> left -> {
			nobreaknonadjacent(State.prevToken(), State.currToken());
			_this.setLeft(left);
			_this.setRight(expression(context, 100));
			Token right = _this.getRight();
			if (isIdentifier(left, "NaN") || isIdentifier(right, "NaN"))
			{
				warning("W019", _this);
			}
			else if (f != null)
			{
				f.apply(_this).apply(context).apply(left, right);
			}
			
			if (left == null  || right == null)
			{
				quit("E041", State.currToken());
			}
			
			if (left.getId().equals("!"))
			{
				warning("W018", left, "!");
			}
			
			if (right.getId().equals("!"))
			{
				warning("W018", right, "!");
			}
			
			return _this;
		});
		
		return x;
	}
	
	/**
	 * Determine if a given token marks the beginning of a UnaryExpression.
	 *
	 * @param token
	 *
	 * @return
	 */
	private boolean beginsUnaryExpression(Token token)
	{
		return token.getArity() == Token.ArityType.UNARY && !token.getId().equals("++") && !token.getId().equals("--");
	}
	
	private static Map<String, List<String>> typeofValues = new HashMap<String, List<String>>();
	static
	{
		typeofValues.put("legacy", ImmutableList.<String>builder()
			// E4X extended the `typeof` operator to return "xml" for the XML and
			// XMLList types it introduced.
			// Ref: 11.3.2 The typeof Operator
			// http://www.ecma-international.org/publications/files/ECMA-ST/Ecma-357.pdf
			.add("xml")
			// IE<9 reports "unknown" when the `typeof` operator is applied to an
			// object existing across a COM+ bridge. In lieu of official documentation
			// (which does not exist), see:
			// http://robertnyman.com/2005/12/21/what-is-typeof-unknown/
			.add("unknown").build());
		typeofValues.put("es3", ImmutableList.<String>builder()
			.add("undefined")
			.add("boolean")
			.add("number")
			.add("string")
			.add("function")
			.add("object")
			.addAll(typeofValues.get("legacy")).build());
		typeofValues.put("es6", ImmutableList.<String>builder()
			.addAll(typeofValues.get("es3"))
			.add("symbol").build());
	}
	
	/**
	 * Validate comparisons between the result of a `typeof` expression and a
	 * string literal.
	 *
	 * @param left - one of the values being compared
	 * @param right - the other value being compared
	 *
	 * @return `false` if the second token describes a `typeof`
	 *         expression and the first token is a string literal
	 *         whose value is never returned by that operator;
	 *         `true` otherwise
	 */
	private boolean isTypoTypeof(Token left, Token right) 
	{
		if (State.getOption().test("notypeof"))
			return false;
		
		if (left == null || right == null)
			return false;
		
		List<String> values = State.inES6() ? typeofValues.get("es6") : typeofValues.get("es3");
		
		if (right.getType() == Token.Type.IDENTIFIER && right.getValue().equals("typeof") && left.getType() == Token.Type.STRING)
			return !values.contains(left.getValue());
		
		return false;
	}
	
	/**
	 * Determine if a given token describes the built-in `eval` function.
	 *
	 * @param left
	 *
	 * @return 
	 */
	private boolean isGlobalEval(Token left) //JSHINT_BUG: unused variable state here
	{
		boolean isGlobal = false;
		
		// permit methods to refer to an "eval" key in their own context
		if (left.getType() == Token.Type.THIS && State.getFunct().getContext() == null)
		{
			isGlobal = true;
		}
		
		// permit use of "eval" members of objects
		else if (left.getType() == Token.Type.IDENTIFIER)
		{
			if (State.getOption().test("node") && left.getValue().equals("global"))
			{
				isGlobal = true;
			}
			
			else if (State.getOption().test("browser") && (left.getValue().equals("window") || left.getValue().equals("document")))
			{
				isGlobal = true;
			}
		}
		
		return isGlobal;
	}

	private Token walkPrototype(Token obj)
	{
		if (obj == null) return null;
		return obj.getRight() != null && obj.getRight().getValue().equals("prototype") ? obj : walkPrototype(obj.getLeft());
	}
	
	private String walkNative(Token obj)
	{
		Set<String> natives = new HashSet<String>();
		natives.add("Array");
		natives.add("ArrayBuffer");
		natives.add("Boolean");
		natives.add("Collator");
		natives.add("DataView");
		natives.add("Date");
		natives.add("DateTimeFormat");
		natives.add("Error");
		natives.add("EvalError");
		natives.add("Float32Array");
		natives.add("Float64Array");
		natives.add("Function");
		natives.add("Infinity");
		natives.add("Intl");
		natives.add("Int16Array");
		natives.add("Int32Array");
		natives.add("Int8Array");
		natives.add("Iterator");
		natives.add("Number");
		natives.add("NumberFormat");
		natives.add("Object");
		natives.add("RangeError");
		natives.add("ReferenceError");
		natives.add("RegExp");
		natives.add("StopIteration");
		natives.add("String");
		natives.add("SyntaxError");
		natives.add("TypeError");
		natives.add("Uint16Array");
		natives.add("Uint32Array");
		natives.add("Uint8Array");
		natives.add("Uint8ClampedArray");
		natives.add("URIError");
		
		while (!obj.isIdentifier() && obj.getLeft() != null)
			obj = obj.getLeft();
		
		if (obj.isIdentifier() && natives.contains(obj.getValue()) && 
			State.getFunct().getScope().isPredefined(obj.getValue()))
		{
			return obj.getValue();
		}
		
		return null;
	}
	
	/**
	 * Determine if a given token describes a property of a built-in object.
	 *
	 * @param left
	 *
	 * @return
	 */
	private String findNativePrototype(Token left)
	{
		Token prototype = walkPrototype(left);
		if (prototype != null) return walkNative(prototype);
		return null;
	}
	
	/**
	 * Checks the left hand side of an assignment for issues, returns if ok
	 * Determine if the given token is a valid assignment target; emit errors
	 * and/or warnings as appropriate
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 * @param left - the left hand side of the assignment
	 *
	 * @return Whether the left hand side is OK
	 */
	private boolean checkLeftSideAssign(int context, Token left)
	{
		return checkLeftSideAssign(context, left, null, false);
	}
	
	/**
	 * Checks the left hand side of an assignment for issues, returns if ok
	 * Determine if the given token is a valid assignment target; emit errors
	 * and/or warnings as appropriate
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 * @param left - the left hand side of the assignment
	 * @param assignToken - the token for the assignment, used for
	 *                      reporting
	 *
	 * @return Whether the left hand side is OK
	 */
	private boolean checkLeftSideAssign(int context, Token left, Token assignToken)
	{
		return checkLeftSideAssign(context, left, assignToken, false);
	}
	
	/**
	 * Checks the left hand side of an assignment for issues, returns if ok
	 * Determine if the given token is a valid assignment target; emit errors
	 * and/or warnings as appropriate
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 * @param left - the left hand side of the assignment
	 * @param assignToken - the token for the assignment, used for
	 *                      reporting
	 * @param allowDestructuring - whether to allow destructuring binding
	 *
	 * @return Whether the left hand side is OK
	 */
	private boolean checkLeftSideAssign(int context, Token left, Token assignToken, boolean allowDestructuring)
	{
		assignToken = ObjectUtils.defaultIfNull(assignToken, left);
		
		if (State.getOption().test("freeze"))
		{
			String nativeObject = findNativePrototype(left);
			if (nativeObject != null)
				warning("W121", left, nativeObject);
		}
		if (checkPunctuator(left, "..."))
		{
			left = left.getRight();
		}
		
		if (left.isIdentifier() && !left.isMetaProperty())
		{
			// The `reassign` method also calls `modify`, but we are specific in
			// order to catch function re-assignment and globals re-assignment
			State.getFunct().getScope().getBlock().reassign(left.getValue(), left);
		}
		
		if (left.getId().equals("."))
		{
			if (left.getLeft() == null || left.getLeft().getValue().equals("arguments") && !State.isStrict())
			{
				warning("W143", assignToken);
			}
			
			State.getNameStack().set(State.prevToken());
			return true;
		}
		else if (left.getId().equals("{") || left.getId().equals("["))
		{
			if (!allowDestructuring || left.getDestructAssign() == null)
			{
				if (left.getId().equals("{") || left.getLeft() == null)
				{
					warning("E031", assignToken);
				}
				else if (left.getLeft().getValue().equals("arguments") && !State.isStrict())
				{
					warning("W143", assignToken);
				}
			}
			
			if (left.getId().equals("["))
			{
				State.getNameStack().set(left.getRight());
			}
			
			return true;
		}
		else if (left.isIdentifier() && !isReserved(context, left) && !left.isMetaProperty() &&
				!left.getValue().equals("eval") && !left.getValue().equals("arguments"))
		{
			if (StringUtils.defaultString(State.getFunct().getScope().labeltype(left.getValue())).equals("exception"))
			{
				warning("W022", left);
			}
			State.getNameStack().set(left);
			return true;
		}
		
		error("E031", assignToken);
		
		return false;
	}

	/**
	 * Convenience function for defining JSHint symbols for assignment operators.
	 *
	 * @param s - the name of the symbol
	 * @param f - string that just describes execution use case
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token assignop(String s, String f, int p)
	{
		return assignop(s, context -> (left, that) -> {
			that.setLeft(left);
			
			checkLeftSideAssign(context, left, that, true);
			
			that.setRight(expression(context, 10));
			
			return that;
		}, p);
	}
	
	/**
	 * Convenience function for defining JSHint symbols for assignment operators.
	 *
	 * @param s - the name of the symbol
	 * @param f - a function to be invoked that consumes the
	 *            right-hand side of the operator (see the `infix`
	 *            function)
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token assignop(String s, IntFunction<BiFunction<Token, Token, Token>> f, int p)
	{
		Token x = infix(s, f, p);
		x.setExps(true);
		x.setAssign(true);
		return x;
	}
	
	/**
	 * Convenience function for defining JSHint symbols for bitwise operators.
	 *
	 * @param s - the name of the symbol
	 * @param f - string that just describes execution use case
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token bitwise(String s, String f, int p)
	{
		return bitwise(s, _this -> context -> left -> {
			if (State.getOption().test("bitwise"))
			{
				warning("W016", _this, _this.getId());
			}
			_this.setLeft(left);
			_this.setRight(expression(context, p));
			return _this;
		}, p);
	}
	
	/**
	 * Convenience function for defining JSHint symbols for bitwise operators.
	 *
	 * @param s - the name of the symbol
	 * @param f - the left denotation function for the symbol; see
	 *            the `expression` function for more detail
	 * @param p - the left-binding power of the token as used by the
	 *            Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token bitwise(String s, Function<Token, IntFunction<Function<Token, Token>>> f, int p)
	{
		Token x = symbol(s, p);
		reserveName(x);
		x.setInfix(true);
		x.setLed(f);
		return x;
	}

	/**
	 * Convenience function for defining JSHint symbols for bitwise assignment
	 * operators. See the `assignop` function for more detail.
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token bitwiseassignop(String s)
	{
		return assignop(s, context -> (left, that) -> {
			if (State.getOption().test("bitwise"))
			{
				warning("W016", that, that.getId());
			}
			
			checkLeftSideAssign(context, left, that);
			
			that.setRight(expression(context, 10));
			
			return that;
		}, 20);
	}

	/**
	 * Convenience function for defining JSHint symbols for those operators which
	 * have a single operand that appears before them in the source code.
	 *
	 * @param s - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token suffix(String s)
	{
		Token x = symbol(s, 150);
		x.setLed(_this -> context -> left -> {
			// this = suffix e.g. "++" punctuator
			// left = symbol operated e.g. "a" identifier or "a.b" punctuator
			if (State.getOption().test("plusplus"))
			{
				warning("W016", _this, _this.getId());
			}
			
			checkLeftSideAssign(context, left, _this);
			
			_this.setLeft(left);
			return _this;
		});
		return x;
	}
	
	/**
	 * Retrieve the value of the current token if it is an identifier and
	 * optionally advance the parser.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 *
	 * @return the value of the identifier, if present
	 */
	private String optionalidentifier(int context)
	{
		return optionalidentifier(context, false, false);
	}

	/**
	 * Retrieve the value of the current token if it is an identifier and
	 * optionally advance the parser.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 * @param prop - `true` if this identifier is that of an object
	 *               property
	 * @param preserve - `true` if the token should not be consumed
	 *
	 * @return the value of the identifier, if present
	 */
	private String optionalidentifier(int context, boolean prop, boolean preserve)
	{
		if (!State.nextToken().isIdentifier())
		{
			return null;
		}
		
		if (!preserve)
		{
			advance();
		}
		
		Token curr = State.currToken();
		String val  = State.currToken().getValue();
		
		if (!isReserved(context, curr))
		{
			return val;
		}
		
		if (prop)
		{
			if (State.inES5())
			{
				return val;
			}
		}
		
		if (val.equals("undefined"))
		{
			return val;
		}
		
		warning("W024", State.currToken(), State.currToken().getId());
		return val;
	}
	
	/**
	 * Consume the "..." token which designates "spread" and "rest" operations if
	 * it is present. If the operator is repeated, consume every repetition, and
	 * issue a single error describing the syntax error.
	 *
	 * @param operation - either "spread" or "rest"
	 *
	 * @returns a value describing whether or not any tokens were
	 *          consumed in this way
	 */
	private boolean spreadrest(String operation)
	{
		if (!checkPunctuator(State.nextToken(), "..."))
		{
			return false;
		}
		
		if (!State.inES6(true))
		{
			warning("W119", State.nextToken(), operation + " operator", "6");
		}
		advance();
		
		if (checkPunctuator(State.nextToken(), "..."))
		{
			warning("E024", State.nextToken(), "...");
			while (checkPunctuator(State.nextToken(), "..."))
			{
				advance();
			}
		}
		
		return true;
	}
	
	/**
	 * Ensure that the current token is an identifier and retrieve its value.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 *
	 * @return the value of the identifier, if present
	 */
	private String identifier(int context)
	{
		return identifier(context, false);
	}
	
	/**
	 * Ensure that the current token is an identifier and retrieve its value.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 * @param prop - `true` if this identifier is that of an object
	 *               property
	 *
	 * @return the value of the identifier, if present
	 */
	private String identifier(int context, boolean prop)
	{
		String i = optionalidentifier(context, prop, false);
		if (StringUtils.isNotEmpty(i))
		{
			return i;
		}
		
		error("E030", State.nextToken(), State.nextToken().getValue());
		
		// The token should be consumed after a warning is issued so the parser
		// can continue as though an identifier were found. The semicolon token
		// should not be consumed in this way so that the parser interprets it as
		// a statement delimeter;
		if (!State.nextToken().getId().equals(";"))
		{
			advance();
		}

		return null;
	}

	/**
	 * Determine if the provided token may be evaluated and emit a linting
	 * warning if this is note the case.
	 *
	 * @param controlToken
	 */
	private void reachable(Token controlToken)
	{
		int i = 0;
		Token t = null;
		
		if (!State.nextToken().getId().equals(";") || controlToken.inBracelessBlock())
		{
			return;
		}
		for (;;)
		{
			do
			{
				t = peek(i);
				i += 1;
			}
			while (!t.getId().equals("(end)") && t.getId().equals("(comment)"));
			
			if (t.isReach())
			{
				return;
			}
			if (!t.getId().equals("(endline)"))
			{
				if (t.getId().equals("function"))
				{
					if (State.getOption().get("latedef").equals(true))
					{
						warning("W026", t);
					}
					break;
				}
				
				warning("W027", t, t.getValue(), controlToken.getValue());
				break;
			}
		}
	}

	/**
	 * Consume the semicolon that delimits the statement currently being parsed,
	 * emitting relevant warnings/errors as appropriate.
	 * 
	 * @param stmt token describing the statement under consideration
	 */
	private void parseFinalSemicolon(Token stmt)
	{
		if (!State.nextToken().getId().equals(";"))
		{
			// don't complain about unclosed templates / strings
			if (State.nextToken().isUnclosed())
			{
				advance();
				return;
			}
			
			boolean sameLine = startLine(State.nextToken()) == State.currToken().getLine() &&
							   !State.nextToken().getId().equals("(end)");
			boolean blockEnd = checkPunctuator(State.nextToken(), "}");
			
			if (sameLine && !blockEnd && !(stmt.getId().equals("do") && State.inES6(true)))
			{
				errorAt("E058", State.currToken().getLine(), State.currToken().getCharacter());
			}
			else if (!State.getOption().test("asi"))
			{
				
				// If this is the last statement in a block that ends on the same line
		        // *and* option lastsemic is on, ignore the warning.  Otherwise, issue
		        // a warning about missing semicolon.
				if (!(blockEnd && sameLine && State.getOption().test("lastsemic")))
				{
					warningAt("W033", State.currToken().getLine(), State.currToken().getCharacter());
				}
			}
		}
		else
		{
			advance(";");
		}
	}

	/**
	 * Consume a statement.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 *
	 * @return the token describing the statement
	 */
	private Token statement(int context)
	{
		int i = indent;
		Token t = State.nextToken();
		boolean hasOwnScope = false;
		
		context |= ProdParams.INITIAL;
		
		if (t.getId().equals(";"))
		{
			advance(";");
			return null;
		}
		
		// Is this a labelled statement?
		boolean res = isReserved(context, t);
		
		// We're being more tolerant here: if someone uses
		// a FutureReservedWord (that is not meant to start a statement)
		// as a label, we warn but proceed anyway.
		
		if (res && t.getMeta() != null && t.getMeta().isFutureReservedWord() && t.getFud() == null)
		{
			warning("W024", t, t.getId());
			res = false;
		}
		
		if (t.isIdentifier() && !res && peek().getId().equals(":"))
		{
			advance();
			advance(":");
			
			hasOwnScope = true;
			State.getFunct().getScope().stack();
			State.getFunct().getScope().getBlock().addBreakLabel(t.getValue(), State.currToken());
			
			if (!State.nextToken().isLabelled() && !State.nextToken().getValue().equals("{"))
			{
				warning("W028", State.nextToken(), t.getValue(), State.nextToken().getValue());
			}
			
			t = State.nextToken();
		}
		
		// Is it a lonely block?
		
		if (t.getId().equals("{"))
		{
			// Is it a switch case block?
			//
			//  switch (foo) {
			//    case bar: { <= here.
			//      ...
			//    }
			//  }
			boolean iscase = (State.getFunct().getVerb().equals("case") && State.currToken().getValue().equals(":"));
			block(context, true, true, false, false, iscase);
			
			if (hasOwnScope)
			{
				State.getFunct().getScope().unstack();
			}
			
			return null;
		}
		
		// Parse the statement.
		
		Token r = expression(context, 0);
		
		if (r != null && !(r.isIdentifier() && r.getValue().equals("function")) &&
			!(r.getType() == Token.Type.PUNCTUATOR && r.getLeft() != null &&
			r.getLeft().isIdentifier() && r.getLeft().getValue().equals("function")))
		{
			if (!State.isStrict() && State.stmtMissingStrict())
			{
				warning("E007");
			}
		}
		
		// Look for the final semicolon.
		if (!t.isBlock())
		{
			if (!State.getOption().get("expr").test() && (r == null || !r.isExps()))
			{
				warning("W030", State.currToken());
			}
			else if (State.getOption().get("nonew").test() && r != null && r.getLeft() != null && r.getId().equals("(") && r.getLeft().getId().equals("new"))
			{
				warning("W031", t);
			}
			parseFinalSemicolon(t);
		}
		
		// Restore the indentation.
		
		indent = i;
		if (hasOwnScope)
		{
			State.getFunct().getScope().unstack();
		}
		return r;
	}

	/**
	 * Consume a series of statements until encountering either the end of the
	 * program or a token that interrupts control flow.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                  more information
	 *
	 * @return the tokens consumed
	 */
	private List<Token> statements(int context)
	{
		List<Token> a = new ArrayList<Token>();
		Token p;
		
		while (!State.nextToken().isReach() && !State.nextToken().getId().equals("(end)"))
		{
			if (State.nextToken().getId().equals(";"))
			{
				p = peek();
				
				if (p == null || (!p.getId().equals("(") && !p.getId().equals("[")))
				{
					warning("W032");
				}
				
				advance(";");
			}
			else
			{
				a.add(statement(context));
			}
		}

		return a;
	}

	/**
	 * Parse any directives in a directive prologue.
	 */
	private void directives()
	{
		Token current = State.nextToken();
		
		while (State.nextToken().getId().equals("(string)"))
		{
			Token next = peekIgnoreEOL();
			if (!isEndOfExpr(0, current, next))
			{
				break;
			}
			current = next;
			
			advance();
			String directive = State.currToken().getValue();
			if (BooleanUtils.isTrue(State.getDirective().get(directive)) ||
				(directive.equals("use strict") && State.getOption().get("strict").equals("implied")))
			{
				warning("W034", State.currToken(), directive);
			}
			
			// From ECMAScript 2016:
			//
			// > 14.1.2 Static Semantics: Early Errors
			// >
			// > [...]
			// > - It is a Syntax Error if ContainsUseStrict of FunctionBody is true
			// >   and IsSimpleParameterList of FormalParameters is false.
			if (directive.equals("use strict") && State.inES7() && 
				!State.getFunct().isGlobal() && State.getFunct().hasSimpleParams() == false)
			{
				error("E065", State.currToken());
			}
			
			// there's no directive negation, so always set to true
			State.getDirective().put(directive, true);
			
			parseFinalSemicolon(current);
		}
		
		if (State.isStrict())
		{
			State.getOption().set("undef", true);
		}
	}
	
	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context - parsing context
	 * @param ordinary - `true` for everything but function bodies and
	 *                   try blocks
	 *
	 * @return the token describing the block
	 */
	private List<Token> block(int context, boolean ordinary)
	{
		return block(context, ordinary, false, false, false, false);
	}
	
	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context - parsing context
	 * @param ordinary - `true` for everything but function bodies and
	 *                   try blocks
	 * @param stmt - `true` if block can be a single statement (e.g.
	 *               in if/for/while)
	 *
	 * @return the token describing the block
	 */
	private List<Token> block(int context, boolean ordinary, boolean stmt)
	{
		return block(context, ordinary, stmt, false, false, false);
	}
	
	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context - parsing context
	 * @param ordinary - `true` for everything but function bodies and
	 *                   try blocks
	 * @param stmt - `true` if block can be a single statement (e.g.
	 *               in if/for/while)
	 * @param isfunc - `true` if block is a function body
	 * @param isfatarrow - `true` if its a body of a fat arrow
	 *                     function
	 *
	 * @return the token describing the block
	 */
	private List<Token> block(int context, boolean ordinary, boolean stmt, boolean isfunc, boolean isfatarrow)
	{
		return block(context, ordinary, stmt, isfunc, isfatarrow, false);
	}
	
	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context - parsing context
	 * @param ordinary - `true` for everything but function bodies and
	 *                   try blocks
	 * @param stmt - `true` if block can be a single statement (e.g.
	 *               in if/for/while)
	 * @param isfunc - `true` if block is a function body
	 * @param isfatarrow - `true` if its a body of a fat arrow
	 *                     function
	 * @param iscase - `true` if block is a switch case block
	 *
	 * @return the token describing the block
	 */
	//JSHINT_BUG: return can be a list of tokens
	private List<Token> block(int context, boolean ordinary, boolean stmt, boolean isfunc, boolean isfatarrow, boolean iscase)
	{
		List<Token> a = new ArrayList<Token>();
		boolean b = inblock;
		int oldIndent = indent;
		Map<String, Boolean> m = null;
		Token t;
		
		inblock = ordinary;
		
		t = State.nextToken();
		
		Metrics metrics = State.getFunct().getMetrics();
		metrics.nestedBlockDepth += 1;
		metrics.verifyMaxNestedBlockDepthPerFunction();
		
		if (State.nextToken().getId().equals("{"))
		{
			advance("{");
			
			// create a new block scope
			State.getFunct().getScope().stack();
			
			if (!State.nextToken().getId().equals("}"))
			{
				indent += State.getOption().asInt("indent");
				while (!ordinary && State.nextToken().getFrom() > indent)
				{
					indent += State.getOption().asInt("indent");
				}
				
				if (isfunc)
				{
					m = new HashMap<String, Boolean>();
					for (String d : State.getDirective().keySet())
					{
						m.put(d, State.getDirective().get(d));
					}
					directives();
					
					State.getFunct().setStrict(State.isStrict());
					
					if (State.getOption().test("strict") && State.getFunct().getContext().isGlobal())
					{
						if (BooleanUtils.isNotTrue(m.get("use strict")) && !State.isStrict())
						{
							warning("E007");
						}
					}
				}
				
				a = statements(context);
				
				metrics.statementCount += a.size();
				
				indent -= State.getOption().asInt("indent");
			}
			else if (isfunc)
			{
				// Ensure property is set for functions with empty bodies.
				State.getFunct().setStrict(State.isStrict());
			}
			
			advance("}", t);
			
			if (isfunc)
			{
				State.getFunct().getScope().validateParams(isfatarrow);
				if (m != null)
				{
					State.setDirective(m);
				}
			}
			
			State.getFunct().getScope().unstack();
			
			indent = oldIndent;
		}
		else if (!ordinary)
		{
			if (isfunc)
			{
				State.getFunct().getScope().stack();
				
				if (stmt && !isfatarrow && !State.inMoz())
				{
					error("W118", State.currToken(), "function closure expressions");
				}
				
				if (isfatarrow)
				{
					State.getFunct().getScope().validateParams(true);
				}
				
				Token expr = expression(context, 10);
				
				if (State.getOption().test("noreturnawait") && (context & ProdParams.ASYNC) != 0 &&
					expr.isIdentifier() && expr.getValue().equals("await"))
				{
					warning("W146", expr);
				}
				
				if (State.getOption().test("strict") && State.getFunct().getContext().isGlobal())
				{
					if (!State.isStrict())
					{
						warning("E007");
					}
				}
				
				State.getFunct().getScope().unstack();
			}
			else
			{
				error("E021", State.nextToken(), "{", State.nextToken().getValue());
			}
		}
		else
		{
			State.getFunct().getScope().stack();
			
			if (!stmt || State.getOption().test("curly"))
			{
				warning("W116", State.nextToken(), "{", State.nextToken().getValue());
			}
			
			// JSHint observes Annex B of the ECMAScript specification by default,
			// where function declarations are permitted in the statement positions
			// of IfStatements.
			boolean supportsFnDecl = State.getFunct().getVerb().equals("if") ||
				State.currToken().getId().equals("else");
			
			State.nextToken().setInBracelessBlock(true);
			indent += State.getOption().asInt("indent");
			// test indentation only if statement is in new line
			a.add(statement(context));
			indent -= State.getOption().asInt("indent");
			
			if (a.size() > 0 && a.get(0) != null && a.get(0).isDeclaration() &&
				!(supportsFnDecl && a.get(0).getId().equals("function")))
			{
				error("E048", a.get(0), StringUtils.capitalize(a.get(0).getId()));
			}
			
			State.getFunct().getScope().unstack();
		}
		
		// Don't clear and let it propagate out if it is "break", "return" or
		// similar in switch case
		switch (State.getFunct().getVerb())
		{
		case "break":
		case "continue":
		case "return":
		case "throw":
			if (iscase)
			{
				break;
			}
			
		default:
			State.getFunct().setVerb("");
		}
		
		inblock = b;
		if (ordinary && State.getOption().get("noempty").test() && a.size() == 0)
		{
			warning("W035", State.prevToken());
		}
		metrics.nestedBlockDepth -= 1;
		return a;
	}

	/**
	 * Update the global state which tracks all statically-identifiable property
	 * names, and emit a warning if the `members` linting directive is in use and
	 * does not include the given name.
	 *
	 * @param m - the property name
	 */
	private void countMember(String m)
	{
		if (membersOnly != null && !membersOnly.containsKey(m))
		{
			warning("W036", State.currToken(), m);
		}
		if (member.get(m) != null)
		{
			member.put(m, member.get(m) + 1);
		}
		else
		{
			member.put(m, 1);
		}
	}
	
	// Build the syntax table by declaring the syntactic elements of the language.
	private void buildSyntaxTable()
	{
		type(Token.Type.NUMBER, _this -> context -> rbp -> _this);
		
		type(Token.Type.STRING, _this -> context -> rbp -> _this);
		
		Token identifier = new Token();
		State.getSyntax().put("(identifier)", identifier);
		identifier.setType(Token.Type.IDENTIFIER);
		identifier.setLbp(0);
		identifier.setIdentifier(true);
		identifier.setNud(_this -> context -> rbp -> {
			String v = _this.getValue();
							
			// If this identifier is the lone parameter to a shorthand "fat arrow"
			// function definition, i.e.
			//
			//		x => x;
			//
			// ...it should not be considered as a variable in the current scope. It
			// will be added to the scope of the new function when the next token is
			// parsed, so it can be safely ignored for now.
			if (State.nextToken().getId().equals("=>"))
			{
				return _this;
			}
			
			if (!State.getFunct().getComparray().check(v))
			{
				State.getFunct().getScope().getBlock().use(v, State.currToken());
			}
			return _this;
		});
		identifier.setLed(_this -> context -> left -> {
			error("E033", State.nextToken(), State.nextToken().getValue());
			return null;
		});
		
		Token template = new Token();
		State.getSyntax().put("(template)", template);
		template.setType(Token.Type.TEMPLATE);
		template.setLbp(155);
		template.setIdentifier(false);
		template.setTemplate(true);
		template.setNud(_this -> context -> rbp -> doTemplateLiteral(_this, context, rbp));
		template.setLed(_this -> context -> left -> doTemplateLiteral(_this, context, left));
		template.setNoSubst(false);
		
		Token templateMiddle = new Token();
		State.getSyntax().put("(template middle)", templateMiddle);
		templateMiddle.setType(Token.Type.TEMPLATEMIDDLE);
		templateMiddle.setLbp(0);
		templateMiddle.setIdentifier(false);
		templateMiddle.setTemplate(true);
		templateMiddle.setNoSubst(false);
		
		Token templateTail = new Token();
		State.getSyntax().put("(template tail)", templateTail);
		templateTail.setType(Token.Type.TEMPLATETAIL);
		templateTail.setLbp(0);
		templateTail.setIdentifier(false);
		templateTail.setTemplate(true);
		templateTail.setTail(true);
		templateTail.setNoSubst(false);
		
		Token noSubstTemplate = new Token();
		State.getSyntax().put("(no subst template)", noSubstTemplate);
		noSubstTemplate.setType(Token.Type.TEMPLATE);
		noSubstTemplate.setLbp(155);
		noSubstTemplate.setIdentifier(false);
		noSubstTemplate.setTemplate(true);
		noSubstTemplate.setNud(_this -> context -> rbp -> doTemplateLiteral(_this, context, rbp));
		noSubstTemplate.setLed(_this -> context -> left -> doTemplateLiteral(_this, context, left));
		noSubstTemplate.setNoSubst(true);
		noSubstTemplate.setTail(true); // mark as tail, since it's always the last component
		
		type(Token.Type.REGEXP, _this -> context -> rbp -> _this);
	}
	
	// ECMAScript parser
	private void ecmaScriptParser()
	{
		Token x;
		
		delim("(endline)");
		x = delim("(begin)");
		x.setFrom(0);
		x.setLine(0);
		delim("(end)").setReach(true);
		delim("(error)").setReach(true);
		delim("}").setReach(true);
		delim(")");
		delim("]");
		delim("\"").setReach(true);
		delim("'").setReach(true);
		delim(";");
		delim(":").setReach(true);
		delim("#");
		
		reserve(Token.Type.ELSE);
		reserve(Token.Type.CASE).setReach(true);
		reserve(Token.Type.CATCH);
		reserve(Token.Type.DEFAULT).setReach(true);
		reserve(Token.Type.FINALLY);
		reserve(Token.Type.TRUE, _this -> context -> rbp -> _this);
		reserve(Token.Type.FALSE, _this -> context -> rbp -> _this);
		reservevar(Token.Type.NULL);
		reservevar(Token.Type.THIS, token -> {
			if (State.isStrict() && !isMethod() && 
				!State.getOption().test("validthis") && ((State.getFunct().getStatement() != null &&
				State.getFunct().getName().charAt(0) > 'Z') || State.getFunct().isGlobal()))
			{
				warning("W040", token);
			}
		});
		reservevar(Token.Type.SUPER, token -> {
			superNud(State.currToken()); //JSHINT_BUG: it's not needed to pass x token here, becase superNud doesn't have parameters
		});
		
		assignop("=", "assign", 20);
		assignop("+=", "assignadd", 20);
		assignop("-=", "assignsub", 20);
		assignop("*=", "assignmult", 20);
		assignop("/=", "assigndiv", 20).setNud(_this -> context -> rbp -> {
			error("E014");
			return null;
		});
		assignop("%=", "assignmod", 20);
		assignop("**=", context -> (left, that) -> {
			if (!State.inES7())
			{
				warning("W119", that, "Exponentiation operator", "7");
			}
			
			that.setLeft(left);
			
			checkLeftSideAssign(context, left, that);
			
			that.setRight(expression(context, 10));
			
			return that;
		}, 20);
		
		bitwiseassignop("&=");
		bitwiseassignop("|=");
		bitwiseassignop("^=");
		bitwiseassignop("<<=");
		bitwiseassignop(">>=");
		bitwiseassignop(">>>=");
		infix(",", context -> (left, that) -> {
			that.setExprs(new ArrayList<Token>());
			that.getExprs().add(left);
			
			if (State.getOption().get("nocomma").test())
			{
				warning("W127");
			}
			
			if (!parseComma(true, false, false))
			{
				return that;
			}
			while (true)
			{
				Token expr = expression(context, 10);
				if (expr == null)
				{
					break;
				}
				that.getExprs().add(expr);
				if (!State.nextToken().getValue().equals(",") || !parseComma())
				{
					break;
				}
			}
			return that;
		}, 10, true);
		
		infix("?", context -> (left, that) -> {
			increaseComplexityCount();
			that.setLeft(left);
			that.setRight(expression(context & ~ProdParams.NOIN, 10));
			advance(":");
			expression(context, 10);
			return that;
		}, 30);
		
		int orPrecendence = 40;
		infix("||", context -> (left, that) -> {
			increaseComplexityCount();
			that.setLeft(left);
			that.setRight(expression(context, orPrecendence));
			return that;
		}, orPrecendence);
		infix("&&", "and", 50);
		// The Exponentiation operator, introduced in ECMAScript 2016
		//
		// ExponentiationExpression[Yield] :
		//   UnaryExpression[?Yield]
		//   UpdateExpression[?Yield] ** ExponentiationExpression[?Yield]
		infix("**", context -> (left, that) -> {
			if (!State.inES7())
			{
				warning("W119", that, "Exponentiation operator", "7");
			}
			
			// Disallow UnaryExpressions which are not wrapped in parenthesis
			if (!left.isParen() && beginsUnaryExpression(left))
			{
				error("E024", that, "**");
			}
			
			that.setLeft(left);
			that.setRight(expression(context, that.getRbp()));
			return that;
		}, 150);
		State.getSyntax().get("**").setRbp(140);
		bitwise("|", "bitor", 70);
		bitwise("^", "bitxor", 80);
		bitwise("&", "bitand", 90);
		relation("==", _this -> context -> (left, right) -> {
			boolean eqnull = State.getOption().test("eqnull") &&
				((left != null && left.getValue().equals("null")) || (right != null && right.getValue().equals("null")));
			
			if (!eqnull && State.getOption().test("eqeqeq"))
			{
				_this.setFrom(_this.getCharacter());
				warning("W116", _this, "===", "==");
			}
			else if (isTypoTypeof(right, left))
			{
				warning("W122", _this, right.getValue());
			}
			else if (isTypoTypeof(left, right))
			{
				warning("W122", _this, left.getValue());
			}
			
			return _this;
		});
		relation("===", _this -> context -> (left, right) -> {
			if (isTypoTypeof(right, left))
			{
				warning("W122", _this, right.getValue());
			}
			else if (isTypoTypeof(left, right))
			{
				warning("W122", _this, left.getValue());
			}
			return _this;
		});
		relation("!=", _this -> context -> (left, right) -> {
			boolean eqnull = State.getOption().test("eqnull") &&
					((left != null && left.getValue().equals("null")) || (right != null && right.getValue().equals("null")));
			
			if (!eqnull && State.getOption().test("eqeqeq"))
			{
				_this.setFrom(_this.getCharacter());
				warning("W116", _this, "!==", "!=");
			}
			else if (isTypoTypeof(right, left))
			{
				warning("W122", _this, right.getValue());
			}
			else if (isTypoTypeof(left, right))
			{
				warning("W122", _this, left.getValue());
			}
			
			return _this;
		});
		relation("!==", _this -> context -> (left, right) -> {
			if (isTypoTypeof(right, left))
			{
				warning("W122", _this, right.getValue());
			}
			else if (isTypoTypeof(left, right))
			{
				warning("W122", _this, left.getValue());
			}
			return _this;
		});
		relation("<");
		relation(">");
		relation("<=");
		relation(">=");
		bitwise("<<", "shiftleft", 120);
		bitwise(">>", "shiftright", 120);
		bitwise(">>>", "shiftrightunsigned", 120);
		infix("in", "in", 120);
		infix("instanceof", context -> (left, token) -> {
			Token right;
			ScopeManager scope = State.getFunct().getScope();
			token.setLeft(left);
			token.setRight(right = expression(context, 120));
			
			// This condition reflects a syntax error which will be reported by the
			// `expression` function.
			if (right == null)
			{
				return token;
			}
			
			if (right.getId().equals("(number)") ||
				right.getId().equals("(string)") ||
				right.getValue().equals("null") ||
				(right.getValue().equals("undefined") && !scope.has("undefined")) ||
				right.getArity() == Token.ArityType.UNARY ||
				right.getId().equals("{") ||
				(right.getId().equals("[") && right.getRight() == null) ||
				right.getId().equals("(regexp)") ||
				(right.getId().equals("(template)") && right.getTag() == null))
			{
				error("E060");
			}
			
			if (right.getId().equals("function"))
			{
				warning("W139");
			}
			
			return token;
		}, 120);
		infix("+", context -> (left, that) -> {
			Token right = null;
			that.setLeft(left);
			that.setRight(right = expression(context, 130));
			
			if (left != null && right != null && left.getId().equals("(string)") && right.getId().equals("(string)"))
			{
				left.setValue(left.getValue() + right.getValue());
				left.setCharacter(right.getCharacter());
				if (!State.getOption().test("scripturl") && Reg.isJavascriptUrl(left.getValue()))
				{
					warning("W050", left);
				}
				return left;
			}
			
			return that;
		}, 130);
		prefix("+", "num");
		infix("-", "sub", 130);
		prefix("-", "neg");
		infix("*", "mult", 140);
		infix("/", "div", 140);
		infix("%", "mod", 140);

		suffix("++");
		prefix("++", "preinc");
		State.getSyntax().get("++").setExps(true);
		State.getSyntax().get("++").setLtBoundary(Token.BoundaryType.BEFORE);

		suffix("--");
		prefix("--", "predec");
		State.getSyntax().get("--").setExps(true);
		State.getSyntax().get("--").setLtBoundary(Token.BoundaryType.BEFORE);
		
		prefix("delete", _this -> context -> rbp -> {
			_this.setArity(Token.ArityType.UNARY);
			Token p = expression(context, 150);
			if (p == null)
			{
				return _this;
			}
			
			if (!p.getId().equals(".") && !p.getId().equals("["))
			{
				warning("W051");
			}
			_this.setFirstTokens(p);
			
			// The `delete` operator accepts unresolvable references when not in strict
			// mode, so the operand may be undefined.
			if (p.isIdentifier() && !State.isStrict())
			{
				p.setForgiveUndef(true);
			}
			return _this;
		}).setExps(true);
		
		prefix("~", _this -> context -> rbp -> {
			if (State.getOption().test("bitwise"))
			{
				warning("W016", _this, "~");
			}
			_this.setArity(Token.ArityType.UNARY);
			_this.setRight(expression(context, 150));
			return _this;
		});
		
		infix("...");
		
		prefix("!", _this -> context -> rbp -> {
			_this.setArity(Token.ArityType.UNARY);
			_this.setRight(expression(context, 150));
			
			if (_this.getRight() == null) // '!' followed by nothing? Give up.
			{
				quit("E041", _this);
			}
			
			if (BooleanUtils.isTrue(bang.get(_this.getRight().getId())))
			{
				warning("W018", _this, "!");
			}
			return _this;
		});
		
		prefix("typeof", _this -> context -> rbp -> {
			_this.setArity(Token.ArityType.UNARY);
			Token p = expression(context, 150);
			_this.setRight(p);
			_this.setFirstTokens(p);
			
			if (p == null) // 'typeof' followed by nothing? Give up.
			{
				quit("E041", _this);
			}
			
			// The `typeof` operator accepts unresolvable references, so the operand
			// may be undefined.
			if (p.isIdentifier())
			{
				p.setForgiveUndef(true);
			}
			return _this;
		});
		prefix("new", _this -> context -> rbp -> {
			Token mp = metaProperty(context, "target", () -> {
				if (!State.inES6(true))
				{
					warning("W119", State.prevToken(), "new.target", "6");
				}
				boolean inFunction = false;
				Functor c = State.getFunct();
				while (c != null)
				{
					inFunction = !c.isGlobal();
					if (!c.isArrow())
					{
						break;
					}
					c = c.getContext();
				}
				if (!inFunction)
				{
					warning("W136", State.prevToken(), "new.target");
				}
			});
			if (mp != null)
			{
				return mp;
			}
			
			Token c = expression(context, 155);
			if (c != null && !c.getId().equals("function"))
			{
				if (c.isIdentifier())
				{
					switch (c.getValue())
					{
					case "Number":
					case "String":
					case "Boolean":
					case "Math":
					case "JSON":
						warning("W053", State.prevToken(), c.getValue());
						break;
					case "Symbol":
						if (State.inES6())
						{
							warning("W053", State.prevToken(), c.getValue());
						}
						break;
					case "Function":
						if (!State.getOption().test("evil"))
						{
							warning("W054");
						}
						break;
					case "Date":
					case "RegExp":
					case "this":
						break;
					default:
						if (!c.getId().equals("function"))
						{
							char i = c.getValue().charAt(0);
							if (State.getOption().get("newcap").test() && (i < 'A' || i > 'Z') &&
								!State.getFunct().getScope().isPredefined(c.getValue()))
							{
								warning("W055", State.currToken());
							}
						}
					}
				}
				else
				{
					if (!c.getId().equals(".") && !c.getId().equals("[") && !c.getId().equals("("))
					{
						warning("W056", State.currToken());
					}
				}
			}
			else
			{
				if (!State.getOption().get("supernew").test())
					warning("W057", _this);
			}
			if (!State.nextToken().getId().equals("(") && !State.getOption().get("supernew").test())
			{
				warning("W058", State.currToken(), State.currToken().getValue());
			}
			_this.setRight(c);
			_this.setFirstTokens(c);
			return _this;
		});
		State.getSyntax().get("new").setExps(true);
		
		// Class statement
		blockstmt("class", _this -> context -> {
			String className = null;
			Token classNameToken = null;
			int inexport = context & ProdParams.EXPORT;
			
			if (!State.inES6())
			{
				warning("W104", State.currToken(), "class", "6");
			}
			State.setInClassBody(true);
			
			// Class Declaration: 'class <Classname>'
			if (State.nextToken().isIdentifier() && !State.nextToken().getValue().equals("extends"))
			{
				classNameToken = State.nextToken();
				className = classNameToken.getValue();
				identifier(context);
				// unintialized, so that the 'extends' clause is parsed while the class is in TDZ
				State.getFunct().getScope().addlabel(className, "class", classNameToken, false);
			}
			
			// Class Declaration: 'class <Classname> extends <Superclass>'
			if (State.nextToken().getValue().equals("extends"))
			{
				advance("extends");
				expression(context, 0);
			}
			
			if (classNameToken != null)
			{
				_this.setName(className);
				State.getFunct().getScope().initialize(className);
				if (inexport != 0)
				{
					State.getFunct().getScope().setExported(className, classNameToken);
				}
			}
			State.getFunct().getScope().stack();
			classBody(_this, context);
			return _this;
		}).setExps(true);
		
		/*
		   Class expression
		   The Block- and Expression- handling for "class" are almost identical, except for the ordering of steps.
		   In an expression:, the name should not be saved into the calling scope, but is still accessible inside the definition, so we open a new scope first, then save the name. We also mark it as used.
		*/
		prefix("class", _this -> context -> rbp -> {
			String className = null;
			Token classNameToken = null;
			
			if (!State.inES6())
			{
				warning("W104", State.currToken(), "class", "6");
			}
			State.setInClassBody(true);
			
			// Class Declaration: 'class <Classname>'
			if (State.nextToken().isIdentifier() && !State.nextToken().getValue().equals("extends"))
			{
				classNameToken = State.nextToken();
				className = classNameToken.getValue();
				identifier(context);
			}
			
			// Class Declaration: 'class <Classname> extends <Superclass>'
			if (State.nextToken().getValue().equals("extends"))
			{
				advance("extends");
				expression(context, 0);
			}
			
			State.getFunct().getScope().stack();
			if (classNameToken != null)
			{
				_this.setName(className);
				State.getFunct().getScope().addlabel(className, "class", classNameToken, true);
				State.getFunct().getScope().getBlock().use(className, classNameToken);
			}
			
			classBody(_this, context);
			return _this;
		});
		
		prefix("void").setExps(true);
		
		infix(".", context -> (left, that) -> {
			String m = identifier(context, true);
			
			if (m != null)
			{
				countMember(m);
			}
			
			that.setLeft(left);
			that.setRight(State.currToken()); //JSHINT_BUG: it's better to use tokens.curr here instread string variable
			
			if (m != null && m.equals("hasOwnProperty") && State.nextToken().getValue().equals("="))
			{
				warning("W001");
			}
			
			if (left != null && left.getValue().equals("arguments") && (m.equals("callee") || m.equals("caller")))
			{
				if (State.getOption().test("noarg"))
					warning("W059", left, m);
				else if (State.isStrict())
					error("E008");
			}
			else if (!State.getOption().test("evil") && left != null && left.getValue().equals("document") && 
					(m.equals("write") || m.equals("writeln")))
			{
				warning("W060", left);
			}
			
			if (!State.getOption().test("evil") && (m.equals("eval") || m.equals("execScript")))
			{
				if (isGlobalEval(left))
				{
					warning("W061");
				}
			}
			
			return that;
		}, 160, true);
		
		infix("(", context -> (left, that) -> {
			if (State.getOption().test("immed") && left != null && !left.isImmed() && left.getId().equals("function"))
			{
				warning("W062");
			}
			
			if (State.getOption().test("asi") && checkPunctuators(State.prevToken(), ")", "]") &&
				State.prevToken().getLine() != startLine(State.currToken()))
			{
				warning("W014", State.currToken(), State.currToken().getId());
			}
			
			int n = 0;
			List<Token> p = new ArrayList<Token>();
			
			if (left != null)
			{
				if (left.getType() == Token.Type.IDENTIFIER)
				{
					if (Reg.test(Reg.UPPERCASE_IDENTIFIER, left.getValue())) // PORT INFO: match regexp was moved to Reg class
					{
						if ("Array Number String Boolean Date Object Error Symbol".indexOf(left.getValue()) == -1)
						{
							if (left.getValue().equals("Math"))
							{
								warning("W063", left);
							}
							else if (State.getOption().test("newcap"))
							{
								warning("W064", left);
							}
						}
					}
				}
			}
			
			if (!State.nextToken().getId().equals(")"))
			{
				for (;;)
				{
					spreadrest("spread");
					
					p.add(expression(context, 10));
					n += 1;
					if (!State.nextToken().getId().equals(","))
					{
						break;
					}
					parseComma(false, false, true);
					
					if (State.nextToken().getId().equals(")"))
					{
						if (!State.inES8())
						{
							warning("W119", State.currToken(), "Trailing comma in arguments lists", "8");
						}
						
						break;
					}
				}
			}
			
			advance(")");
			
			if (left != null)
			{
				if (!State.inES5() && left.getValue().equals("parseInt") && n == 1)
				{
					warning("W065", State.currToken());
				}
				if (!State.getOption().test("evil"))
				{
					if (left.getValue().equals("eval") || left.getValue().equals("Function") ||
						left.getValue().equals("execScript"))
					{
						warning("W061", left);
						
						// This conditional expression was initially implemented with a typo
						// which prevented the branch's execution in all cases. While
						// enabling the code will produce behavior that is consistent with
						// the other forms of code evaluation that follow, such a change is
						// also technically incompatable with prior versions of JSHint (due
						// to the fact that the behavior was never formally documented). This
						// branch should be enabled as part of a major release.
						//if (p.size() > 0 && p.get(0) != null && p.get(0).getId().equals("(string)")) 
						//{
						//	addEvalCode(left, p.get(0));
						//}
					}
					else if (p.size() > 0 && p.get(0) != null && p.get(0).getId().equals("(string)") &&
							 (left.getValue().equals("setTimeout") || 
							 left.getValue().equals("setInterval")))
					{
						warning("W066", left);
						addEvalCode(left, p.get(0));
					}
					
					// window.setTimeout/setInterval
					else if (p.size() > 0 && p.get(0) != null && p.get(0).getId().equals("(string)") &&
							 left.getValue().equals(".") &&
				             left.getLeft().getValue().equals("window") &&
							 (left.getRight() != null && (left.getRight().getValue().equals("setTimeout") ||  //JSHINT_BUG: it's better always use tokens for right and left attributes 
							 left.getRight().getValue().equals("setInterval"))))
					{
						warning("W066", left);
						addEvalCode(left, p.get(0));
					}
				}
				if (!left.isIdentifier() && !left.getId().equals(".") && !left.getId().equals("[") && !left.getId().equals("=>") &&
					!left.getId().equals("(") && !left.getId().equals("&&") && !left.getId().equals("||") && !left.getId().equals("?") &&
					!(State.inES6() && left.isFunctor()))
				{
					warning("W067", that);
				}
			}
			
			that.setLeft(left);
			return that;
		}, 155, true).setExps(true);
		
		prefix("(", _this -> context -> rbp -> {
			Token ret = null;
			boolean triggerFnExpr = false;
			Token first = null;
			Token last = null;
			Token opening = State.currToken();
			Token preceeding = State.prevToken();
			boolean isNecessary = !State.getOption().test("singleGroups");
			Token pn = peekThroughParens(1);
			
			if (State.nextToken().getId().equals("function"))
			{
				State.nextToken().setImmed(true);
				triggerFnExpr = true;
			}
			
			// If the balanced grouping operator is followed by a "fat arrow", the
			// current token marks the beginning of a "fat arrow" function and parsing
			// should proceed accordingly.
			if (pn.getValue().equals("=>"))
			{
				//JSHINT_BUG: this might be an error, that result is saved to funct property 
				doFunction(context,
					null,
					null,
					FunctionType.ARROW,
					null,
					true,
					null,
					false,
					false
				);
				return pn;
			}
			
			List<Token> exprs = new ArrayList<Token>();
			
			if (!State.nextToken().getId().equals(")"))
			{
				for (;;)
				{
					exprs.add(expression(context, 10));
					
					if (!State.nextToken().getId().equals(","))
					{
						break;
					}
					
					if (State.getOption().test("nocomma"))
					{
						warning("W127");
					}
					
					parseComma();
				}
			}
			
			advance(")", _this);
			if (State.getOption().test("immed") && exprs.size() > 0 && exprs.get(0) != null && exprs.get(0).getId().equals("function"))
			{
				if (!State.nextToken().getId().equals("(") && 
					!State.nextToken().getId().equals(".") && !State.nextToken().getId().equals("["))
				{
					warning("W068", _this);
				}
			}
			
			if (exprs.size() == 0)
			{
				return null;
			}
			if (exprs.size() > 1)
			{
				ret = ObjectUtils.defaultIfNull(ObjectUtils.clone(State.getSyntax().get(",")), new Token());
				ret.setExprs(exprs);
				
				first = exprs.get(0);
				last = exprs.get(exprs.size()-1);
			}
			else
			{
				ret = first = last = exprs.get(0);
				
				if (!isNecessary)
				{
					isNecessary = 
						// Used to distinguish from an ExpressionStatement which may not
						// begin with the `{` and `function` tokens
						(opening.isBeginsStmt() && (ret.getId().equals("{") || triggerFnExpr)) ||
						// Used to signal that a function expression is being supplied to
						// some other operator.
						(triggerFnExpr &&
							// For parenthesis wrapping a function expression to be considered
							// necessary, the grouping operator should be the left-hand-side of
							// some other operator--either within the parenthesis or directly
							// following them.
							(!isEndOfExpr() || !State.prevToken().getId().equals("}"))) ||
						// Used to demarcate an arrow function as the left-hand side of some
						// operator.
						(ret.getId().equals("=>") && !isEndOfExpr()) ||
						// Used as the return value of a single-statement arrow function
						(ret.getId().equals("{") && preceeding.getId().equals("=>")) ||
						// Used to cover a unary expression as the left-hand side of the
						// exponentiation operator
						(beginsUnaryExpression(ret) && State.nextToken().getId().equals("**")) ||
						// Used to delineate an integer number literal from a dereferencing
						// punctuator (otherwise interpreted as a decimal point)
						(ret.getType() == Token.Type.NUMBER &&
							checkPunctuator(pn, ".") && StringUtils.isNumeric(ret.getValue())) || // PORT INFO: test regexp /^\d+$/ was replaced with StringUtils method
						// Used to wrap object destructuring assignment
						(opening.isBeginsStmt() && ret.getId().equals("=") && ret.getLeft().getId().equals("{"));
				}
			}
			
			if (ret != null)
			{
				// The operator may be necessary to override the default binding power of
				// neighboring operators (whenever there is an operator in use within the
				// first expression *or* the current group contains multiple expressions)
				if (!isNecessary && (isOperator(first) || ret.getExprs() != null))
				{
					isNecessary = 
						(rbp > first.getLbp()) ||
						(rbp > 0 && rbp == first.getLbp()) ||
						(!isEndOfExpr() && last.getRbp() < State.nextToken().getLbp());
				}
				
				if (!isNecessary)
				{
					warning("W126", opening);
				}
				
				ret.setParen(true);
			}
			
			return ret;
		});
		
		application("=>");
		
		infix("[", context -> (left, that) -> {
			boolean canUseDot = false;
			
			if (State.getOption().test("asi") && checkPunctuators(State.prevToken(), ")", "]") &&
				State.prevToken().getLine() != startLine(State.currToken()))
			{
				warning("W014", State.currToken(), State.currToken().getId());
			}
			
			Token e = expression(context & ~ProdParams.NOIN, 10);
			
			if (e != null && e.getType() == Token.Type.STRING)
			{
				if (!State.getOption().test("evil") && (e.getValue().equals("eval") || e.getValue().equals("execScript")))
				{
					if (isGlobalEval(left))
					{
						warning("W061");
					}
				}
				
				countMember(e.getValue());
				if (!State.getOption().test("sub") && Reg.isIdentifier(e.getValue()))
				{
					Token s = State.getSyntax().get(e.getValue());
					if (s != null)
					{
						canUseDot = !isReserved(context, s);
					}
					else
					{
						// This branch exists to preserve legacy behavior with version 2.9.5
						// and earlier. In those releases, `eval` and `arguments` were
						// incorrectly interpreted as reserved keywords, so Member
						// Expressions such as `object["eval"]` did not trigger warning W069.
						//
						// JSHINT_TODO: Remove in JSHint 3
						canUseDot = !e.getValue().equals("eval") && !e.getValue().equals("arguments");
					}
					
					if (canUseDot)
					{
						warning("W069", State.prevToken(), e.getValue());
					}
				}
			}
			advance("]", that);
			
			if (e != null && e.getValue().equals("hasOwnProperty") && State.nextToken().getValue().equals("="))
			{
				warning("W001");
			}
			
			that.setLeft(left);
			that.setRight(e);
			return that;
		}, 160, true);
		
		prefix("[", _this -> context -> rbp -> {
			LookupBlockType blocktype = new LookupBlockType();
			if (blocktype.isCompArray)
			{
				if (!State.getOption().test("esnext") && !State.inMoz())
				{
					warning("W118", State.currToken(), "array comprehension");
				}
				return comprehensiveArrayExpression(context);
			}
			else if (blocktype.isDestAssign)
			{
				_this.setDestructAssign(destructuringPattern(context, true, true));
				return _this;
			}
			boolean b = State.currToken().getLine() != startLine(State.nextToken());
			_this.setFirstTokens();
			if (b)
			{
				indent += State.getOption().asInt("indent");
				if (State.nextToken().getFrom() == indent + State.getOption().asInt("indent"))
				{
					indent += State.getOption().asInt("indent");
				}
			}
			while (!State.nextToken().getId().equals("(end)"))
			{
				while (State.nextToken().getId().equals(","))
				{
					if (!State.getOption().get("elision").test())
					{
						if (!State.inES5())
						{
							// Maintain compat with old options --- ES5 mode without
							// elision=true will warn once per comma
							warning("W070");
						}
						else
						{
							warning("W128");
							do
							{
								advance(",");
							}
							while(State.nextToken().getId().equals(","));
							continue;
						}
					}
					advance(",");
				}
				
				if (State.nextToken().getId().equals("]"))
				{
					break;
				}
				
				spreadrest("spread");
				
				_this.addFirstTokens(expression(context, 10));
				if (State.nextToken().getId().equals(","))
				{
					parseComma(false, false, true);
					if (State.nextToken().getId().equals("]") && !State.inES5())
					{
						warning("W070", State.currToken());
						break;
					}
				}
				else
				{
					if (State.getOption().test("trailingcomma") && State.inES5())
					{
						warningAt("W140", State.currToken().getLine(), State.currToken().getCharacter());
					}
					break;
				}
			}
			if (b)
			{
				indent -= State.getOption().asInt("indent");
			}
			advance("]", _this);
			return _this;
		});
		
		//object literals
		x = delim("{");
		x.setNud(_this -> context -> rbp -> {
			String i = null;
			boolean isGeneratorMethod = false;
			UniversalContainer props = ContainerFactory.nullContainer().create(); // All properties, including accessors
			boolean isAsyncMethod = false;
			
			boolean b = State.currToken().getLine() != startLine(State.nextToken());
			if (b)
			{
				indent += State.getOption().asInt("indent");
				if (State.nextToken().getFrom() == indent + State.getOption().asInt("indent"))
				{
					indent += State.getOption().asInt("indent");
				}
			}
			
			LookupBlockType blocktype = new LookupBlockType();
			if (blocktype.isDestAssign)
			{
				_this.setDestructAssign(destructuringPattern(context, true, true));
				return _this;
			}
			
			//State.setInObjectBody(true); //JSHINT_BUG: this property is not used anywhere
			for (;;)
			{
				if (State.nextToken().getId().equals("}"))
				{
					break;
				}
				
				String nextVal = State.nextToken().getValue();
				if (State.nextToken().isIdentifier() &&
					(peekIgnoreEOL().getId().equals(",") || peekIgnoreEOL().getId().equals("}")))
				{
					if (!State.inES6())
					{
						warning("W104", State.nextToken(), "object short notation", "6");
					}
					i = propertyName(context, new UniversalContainer(true));
					saveProperty(props, i, State.nextToken(), false, false, false);
					
					expression(context, 10);
				}
				else if (!peek().getId().equals(":") && (nextVal.equals("get") || nextVal.equals("set")))
				{
					advance(nextVal);
					
					if (!State.inES5())
					{
						error("E034");
					}
					
					if (State.nextToken().getId().equals("["))
					{
						//JSHINT_BUG: this returns Token not string
						//JSHINT_BUG: context isn't passed
						i = computedPropertyName(0) != null ? "TOKEN" : "";
					}
					else
					{
						i = propertyName(context);
						
						// ES6 allows for get() {...} and set() {...} method
						// definition shorthand syntax, so we don't produce an error
						// if linting ECMAScript 6 code.
						if (StringUtils.isEmpty(i) && !State.inES6())
						{
							error("E035");
						}
					}
					
					// We don't want to save this getter unless it's an actual getter
					// and not an ES6 concise method
					if (i != null && !i.isEmpty())
					{
						saveAccessor(nextVal, props, i, State.currToken(), false, false);
					}
					
					Token t = State.nextToken();
					Functor f = doFunction(context,
						null,
						null,
						null,
						null,
						false,
						null,
						true,
						false
					);
					List<String> params = f.getParams();
					
					// Don't warn about getter/setter pairs if this is an ES6 concise method
					if (nextVal.equals("get") && StringUtils.isNotEmpty(i) && params.size() != 0)
					{
						warning("W076", t, params.get(0), i);
					}
					else if (nextVal.equals("set") && StringUtils.isNotEmpty(i) && f.getMetrics().arity != 1)
					{
						warning("W077", t, i);
					}
				}
				else if (spreadrest("spread"))
				{
					if (!State.inES9())
					{
						warning("W119", State.nextToken(), "object spread property", "9");
					}
					
					expression(context, 10);
				}
				else
				{
					if (State.nextToken().getId().equals("async") && !checkPunctuators(peek(), "(", ":"))
					{
						if (!State.inES8())
						{
							warning("W119", State.nextToken(), "async functions", "8");
						}
						
						isAsyncMethod = true;
						advance();
						
						nolinebreak(State.currToken());
					}
					else
					{
						isAsyncMethod = false;
					}
					
					if (State.nextToken().getValue().equals("*") && State.nextToken().getType() == Token.Type.PUNCTUATOR)
					{
						if (isAsyncMethod  && !State.inES9())
						{
							warning("W119", State.nextToken(), "async generators", "9");
						}
						else if (!State.inES6())
						{
							warning("W104", State.nextToken(), "generator functions", "6");
						}
						
						advance("*");
						isGeneratorMethod = true;
					}
					else
					{
						isGeneratorMethod = false;
					}
					
					if (State.nextToken().getId().equals("["))
					{
						State.getNameStack().set(computedPropertyName(context));
					}
					else
					{
						State.getNameStack().set(State.nextToken());
						i = propertyName(context);
						saveProperty(props, i, State.nextToken(), false, false, false);
						
						if (i == null) break;
					}
					
					if (State.nextToken().getValue().equals("("))
					{
						if (!State.inES6())
						{
							warning("W104", State.currToken(), "concise methods", "6");
						}
						
						doFunction(isAsyncMethod ? context | ProdParams.PRE_ASYNC : context,
							null,
							null,
							(isGeneratorMethod ? FunctionType.GENERATOR : null),
							null,
							false,
							null,
							true,
							false
						);
					}
					else
					{
						advance(":");
						expression(context, 10);
					}
				}
				
				countMember(i);
				
				if (State.nextToken().getId().equals(","))
				{
					parseComma(false, true, true);
					if (State.nextToken().getId().equals(","))
					{
						warning("W070", State.currToken());
					}
					else if (State.nextToken().getId().equals("}") && !State.inES5())
					{
						warning("W070", State.currToken());
					}
				}
				else
				{
					if (State.getOption().test("trailingcomma") && State.inES5())
					{
						warningAt("W140", State.currToken().getLine(), State.currToken().getCharacter());
					}
					break;
				}
			}
			if (b)
			{
				indent -= State.getOption().asInt("indent");
			}
			advance("}", _this);
			
			checkProperties(props);
			//State.setInObjectBody(false); //JSHINT_BUG: this property is not used anywhere
			
			return _this;
		});
		x.setFud(_this -> context -> {
			error("E036", State.currToken());
			return null;
		});
	}
	
	private void classBody(Token classToken, int context)
	{
		UniversalContainer props = ContainerFactory.createObject();
		String name;
		boolean hasConstructor = false;
		
		if (State.nextToken().getValue().equals("{"))
		{
			advance("{");
		}
		else
		{
			 warning("W116", State.currToken(), "identifier", State.nextToken().getType().toString()); //?
			 advance();
		}
		
		while (!State.nextToken().getValue().equals("}"))
		{
			boolean isStatic = false;
			boolean inGenerator = false;
			context &= ~ProdParams.PRE_ASYNC;
			
			if (State.nextToken().getValue().equals("static"))
			{
				isStatic = true;
				advance();
			}
			
			if (State.nextToken().getValue().equals("async"))
			{
				if (!checkPunctuator(peek(), "("))
				{
					context |= ProdParams.PRE_ASYNC;
					advance();
					
					nolinebreak(State.currToken());
					
					if (checkPunctuator(State.nextToken(), "*"))
					{
						inGenerator = true;
						advance("*");
						
						if (!State.inES9())
						{
							warning("W119", State.nextToken(), "async generators", "9");
						}
					}
					
					if (!State.inES8())
					{
						warning("W119", State.currToken(), "async functions", "8");
					}
				}
			}
			
			if (State.nextToken().getValue().equals("*"))
			{
				inGenerator = true;
				advance();
			}
			
			Token token = State.nextToken();
			switch (token.getValue())
			{
			case ";":
				warning("W032", token);
				advance();
				break;
			case "constructor":
				if (isStatic)
				{
					// treat like a regular method -- static methods can be called 'constructor'
					name = propertyName(context);
					saveProperty(props, name, token, true, isStatic, false);
					doMethod(classToken, context, name, inGenerator);
				}
				else
				{
					if (inGenerator || (context & ProdParams.PRE_ASYNC) != 0)
					{
						error("E024", token, token.getValue());
					}
					if (hasConstructor)
					{
						error("E024", token, token.getValue());
					}
					advance();
					doMethod(classToken, context, State.getNameStack().infer(), false);
					hasConstructor = true;
				}
				break;
			case "set":
			case "get":
				if (inGenerator)
				{
					error("E024", token, token.getValue());
				}
				String accessorType = token.getValue();
				advance();
				
				if (State.nextToken().getValue().equals("["))
				{
					//JSHINT_BUG: this returns Token not string
					name = computedPropertyName(context) != null ? "TOKEN" : "";
					doMethod(classToken, context, name, false);
				}
				else
				{
					name = propertyName(context);
					if (name.equals("prototype") || name.equals("constructor"))
					{
						error("E049", State.currToken(), "class " + accessorType + "ter method", name);
					}
					saveAccessor(accessorType, props, name, State.currToken(), true, isStatic);
					doMethod(classToken, context, State.getNameStack().infer(), false);
				}
				
				break;
			case "[":
				//JSHINT_BUG: this returns Token not string
				name = computedPropertyName(context) != null ? "TOKEN" : "";
				doMethod(classToken, context, name, inGenerator);
				// We don't check names (via calling saveProperty()) of computed expressions like ["Symbol.iterator"]()
				break;
			default:
				name = propertyName(context);
				if (StringUtils.isEmpty(name))
				{
					error("E024", token, token.getValue());
					advance();
					break;
				}
				if (name.equals("prototype"))
				{
					error("E049", token, "class method", name);
				}
				saveProperty(props, name, token, true, isStatic, false);
				doMethod(classToken, context, name, inGenerator);
				break;
			}
		}
		advance("}");
		checkProperties(props);
		
		State.setInClassBody(false);
		State.getFunct().getScope().unstack();
	}
	
	private void doMethod(Token classToken, int context, String name, boolean generator)
	{
		if (generator)
		{
			if (!State.inES6())
			{
				warning("W119", State.currToken(), "function*", "6");
			}
		}
		
		if (!State.nextToken().getValue().equals("("))
		{
			error("E054", State.nextToken(), State.nextToken().getValue());
			advance();
			if (State.nextToken().getValue().equals("{"))
			{
				// manually cheating the test "invalidClasses", which asserts this particular behavior when a class is misdefined.
				advance();
				if (State.nextToken().getValue().equals("}"))
				{
					warning("W116", State.nextToken(), "(", State.nextToken().getValue());
					advance();
					identifier(context);
					advance();
				}
				return;
			}
			else
			{
				while (!State.nextToken().getValue().equals("("))
				{
					advance();
				}
			}
		}
		
		doFunction(context,
			name,
			classToken,
			generator ? FunctionType.GENERATOR : null,
			null,
			false,
			null,
			true,
			false
		);
	}
	
	private Token comprehensiveArrayExpression(int context)
	{
		Token res = new Token();
		res.setExps(true);
		State.getFunct().getComparray().stack();
		
		// Handle reversed for expressions, used in spidermonkey
		boolean reversed = false;
		if (!State.nextToken().getValue().equals("for"))
		{
			reversed = true;
			if (!State.inMoz())
			{
				warning("W116", State.nextToken(), "for", State.nextToken().getValue());
			}
			State.getFunct().getComparray().setState("use");
			res.setRight(expression(context, 10));
		}
		
		advance("for");
		if (State.nextToken().getValue().equals("each"))
		{
			advance("each");
			if (!State.inMoz())
			{
				warning("W118", State.currToken(), "for each");
			}
		}
		advance("(");
		State.getFunct().getComparray().setState("define");
		res.setLeft(expression(context, 130));
		if (State.nextToken().getValue().equals("in") || State.nextToken().getValue().equals("of"))
		{
			advance();
		}
		else
		{
			error("E045", State.currToken());
		}
		State.getFunct().getComparray().setState("generate");
		expression(context, 10);
		
		advance(")");
		if (State.nextToken().getValue().equals("if"))
		{
			advance("if");
			advance("(");
			State.getFunct().getComparray().setState("filter");
			expression(context, 10);
			advance(")");
		}
		
		if (!reversed)
		{
			State.getFunct().getComparray().setState("use");
			res.setRight(expression(context, 10));
		}
		
		advance("]");
		State.getFunct().getComparray().unstack();
		return res;
	}
	
	private Token peekThroughParens(int parens)
	{
		Token pn = State.nextToken();
		int i = -1;
		Token pn1;
		
		do
		{
			if (pn.getValue().equals("("))
			{
				parens += 1;
			}
			else if (pn.getValue().equals(")"))
			{
				parens -= 1;
			}
			
			i += 1;
			pn1 = pn;
			pn = peek(i);
			
		}
		while (!(parens == 0 && pn1.getValue().equals(")")) && pn.getType() != Token.Type.END);
		
		return pn;
	}

	private boolean isMethod()
	{
		return State.getFunct().isMethod();
	}

	private String propertyName(int context)
	{
		return propertyName(context, ContainerFactory.undefinedContainer());
	}
	
	private String propertyName(int context, UniversalContainer preserveOrToken)
	{
		Object id = null;
		boolean preserve = true;
		if (preserveOrToken.valueOf() instanceof Token)
		{
			id = preserveOrToken.valueOf();
		}
		else
		{
			preserve = preserveOrToken.test();
			id = optionalidentifier(context, true, preserve);
		}
		
		if (id == null)
		{
			if (State.nextToken().getId().equals("(string)"))
			{
				id = State.nextToken().getValue();
				if (!preserve)
				{
					advance();
				}
			}
			else if (State.nextToken().getId().equals("(number)"))
			{
				id = State.nextToken().getValue();
				if (!preserve)
				{
					advance();
				}
			}
		}
		else if (id instanceof Token)
		{
			if (((Token)id).getId().equals("(string)") || ((Token)id).getId().equals("(identifier)")) id = ((Token)id).getValue();
			else if (((Token)id).getId().equals("(number)")) id = ((Token)id).getValue();
		}
		
		if (id != null && id.equals("hasOwnProperty"))
		{
			warning("W001");
		}
		
		if (id instanceof Token) return null;
		
		return (String)id;
	}
	
	/**
	 * @param context The parsing context
	 * @param loneArg The argument to the function in cases
	 * 				  where it was defined using the
	 * 				  single-argument shorthand.
	 * @param parsedOpening Whether the opening parenthesis has
	 * 						already been parsed.
	 * @return {{ arity: number, params: Array.<string>, isSimple: boolean}}
	 */
	private UniversalContainer functionparams(int context, Token loneArg, boolean parsedOpening)
	{
		List<String> paramsIds = new ArrayList<String>();
		boolean pastDefault = false;
		boolean pastRest = false;
		int arity = 0;
		boolean hasDestructuring = false;
		
		if (loneArg != null && loneArg.isIdentifier() == true)
		{
			State.getFunct().getScope().addParam(loneArg.getValue(), loneArg);
			return ContainerFactory.createObject(
				"arity", 1,
				"params", ContainerFactory.createArray(loneArg.getValue()),
				"isSimple", true
			);
		}
		
		Token next = State.nextToken();
		
		if (!parsedOpening)
		{
			advance("(");
		}
		
		if (State.nextToken().getId().equals(")"))
		{
			advance(")");
			return ContainerFactory.undefinedContainer();
		}
		
		for (;;)
		{
			arity++;
			// are added to the param scope
			UniversalContainer currentParams = ContainerFactory.createArray();
			
			if (State.nextToken().getId().equals("{") || State.nextToken().getId().equals("["))
			{
				hasDestructuring = true;
				List<Token> tokens = destructuringPattern(context, false, false);
				for (Token t : tokens)
				{
					if (StringUtils.isNotEmpty(t.getId()))
					{
						paramsIds.add(t.getId());
						currentParams.push(ContainerFactory.createArray(t.getId(), t.getToken()));
					}
				}
			}
			else
			{
				if (checkPunctuator(State.nextToken(), "...")) pastRest = true;
				pastRest = spreadrest("rest");
				String ident = identifier(context);
				
				if (StringUtils.isNotEmpty(ident))
				{
					paramsIds.add(ident);
					currentParams.push(ContainerFactory.createArray(ident, State.currToken()));
				}
				else
				{
					// Skip invalid parameter.
					while (!checkPunctuators(State.nextToken(), new String[]{",", ")"})) advance();
				}
			}
			
			// It is valid to have a regular argument after a default argument
		    // since undefined can be used for missing parameters. Still warn as it is
		    // a possible code smell.
			if (pastDefault)
			{
				if (!State.nextToken().getId().equals("="))
				{
					error("W138", State.currToken());
				}
			}
			if (State.nextToken().getId().equals("="))
			{
				if (!State.inES6())
				{
					warning("W119", State.nextToken(), "default parameters", "6");
				}
				
				if (pastRest)
				{
					error("E062", State.nextToken());
				}
				
				advance("=");
				pastDefault = true;
				expression(context, 10);
			}
			
			// now we have evaluated the default expression, add the variable to the param scope
			for (UniversalContainer p : currentParams)
			{
				State.getFunct().getScope().addParam(p.asString(0), p.<Token>valueOf(1));
			}
			
			if (State.nextToken().getId().equals(","))
			{
				if (pastRest)
				{
					warning("W131", State.nextToken());
				}
				parseComma(false, false, true);
			}
			
			if (State.nextToken().getId().equals(")"))
			{
				if (State.currToken().getId().equals(",") && !State.inES8())
				{
					warning("W119", State.currToken(), "Trailing comma in function parameters", "8");
				}
				
				advance(")", next);
				return ContainerFactory.createObject(
					"arity", arity,
					"params", paramsIds,
					"isSimple", !hasDestructuring && !pastRest && !pastDefault
				);
			}
		}
	}

	/**
	 * Factory function for creating objects used to track statistics of function
	 * literals.
	 */
	class Functor
	{
		private Set<FunctorTag> tags = new HashSet<FunctorTag>();
		
		private String name = "";
		private int breakage = 0;
		private int loopage = 0;
		
		private boolean isStrict = false;
		
		private boolean isGlobal = false;
		
		private int line = 0;
		private int character = 0;
		private Metrics metrics = null;
		private Token statement = null;
		private Functor context = null;
		private ScopeManager scope = null;
		private ArrayComprehension comparray = null;
		private String generator = "";
		private boolean isArrow = false;
		private boolean isAsync = false;
		private boolean isMethod = false;
		private boolean hasSimpleParams = false;
		private List<String> params = null;
		private List<String> outerMutables = null;
		
		private int last = 0;
		private int lastcharacter = 0;
		private UniversalContainer unusedOption = ContainerFactory.undefinedContainer();
		private String verb = "";
		
		/**
		 * Factory function for creating objects used to track statistics of function
		 * literals.
		 */
		private Functor()
		{
			
		}
		
		/**
		 * Factory function for creating objects used to track statistics of function
		 * literals.
		 *
		 * @param name - the identifier name to associate with the function
		 * @param token - token responsible for creating the function
		 *                object
		 * @param overwrites - a collection of properties that should
		 *                     override the corresponding default value of
		 *                     the new "functor" object
		 */
		private Functor(String name, Token token, Functor overwrites)
		{
			setName(name);
			setBreakage(0);
			setLoopage(0);
			// The strictness of the function body is tracked via a dedicated
			// property (as opposed to via the global `state` object) so that the
			// value can be referenced after the body has been fully parsed (i.e.
			// when validating the identifier used in function declarations and
			// function expressions).
			setStrict(true);
			
			setGlobal(false);
			
			setLine(0);
			setCharacter(0);
			setMetrics(null);
			setStatement(null);
			setContext(null);
			setScope(null);
			setComparray(null);
			setGenerator(null);
			setArrow(false);
			setMethod(false);
			setParams(null);
		
			if (token != null)
			{
				setLine(token.getLine());
				setCharacter(token.getCharacter());
				setMetrics(new Metrics(token));
			}
			
			overwrite(overwrites);
			
			if (context != null)
			{
				setScope(context.scope);
				setComparray(context.comparray);
			}
		}
		
		String getName()
		{
			return name;
		}
		
		Functor setName(String name)
		{
			this.tags.add(FunctorTag.NAME);
			this.name = StringUtils.defaultString(name);
			return this;
		}
		
		int getBreakage()
		{
			return breakage;
		}
		
		Functor setBreakage(int breakage)
		{
			this.tags.add(FunctorTag.BREAKAGE);
			this.breakage = breakage;
			return this;
		}
		
		void increaseBreakage()
		{
			breakage++;
		}
		
		void decreaseBreakage()
		{
			breakage--;
		}
		
		int getLoopage()
		{
			return loopage;
		}
		
		Functor setLoopage(int loopage)
		{
			this.tags.add(FunctorTag.LOOPAGE);
			this.loopage = loopage;
			return this;
		}
		
		void increaseLoopage()
		{
			loopage++;
		}
		
		void decreaseLoopage()
		{
			loopage--;
		}
		
		boolean isStrict()
		{
			return isStrict;
		}
		
		Functor setStrict(boolean isStrict)
		{
			this.tags.add(FunctorTag.ISSTRICT);
			this.isStrict = isStrict;
			return this;
		}
		
		boolean isGlobal()
		{
			return isGlobal;
		}
		
		Functor setGlobal(boolean isGlobal)
		{
			this.tags.add(FunctorTag.GLOBAL);
			this.isGlobal = isGlobal;
			return this;
		}
		
		int getLine()
		{
			return line;
		}
		
		Functor setLine(int line)
		{
			this.tags.add(FunctorTag.LINE);
			this.line = line;
			return this;
		}
		
		int getCharacter()
		{
			return character;
		}
		
		Functor setCharacter(int character)
		{
			this.tags.add(FunctorTag.CHARACTER);
			this.character = character;
			return this;
		}
		
		private Metrics getMetrics()
		{
			return metrics;
		}
		
		private Functor setMetrics(Metrics metrics)
		{
			this.tags.add(FunctorTag.METRICS);
			this.metrics = metrics;
			return this;
		}
		
		Token getStatement()
		{
			return statement;
		}
		
		Functor setStatement(Token statement)
		{
			this.tags.add(FunctorTag.STATEMENT);
			this.statement = statement;
			return this;
		}
		
		Functor getContext()
		{
			return context;
		}
		
		Functor setContext(Functor context)
		{
			this.tags.add(FunctorTag.CONTEXT);
			this.context = context;
			return this;
		}
		
		ScopeManager getScope()
		{
			return scope;
		}
		
		Functor setScope(ScopeManager scope)
		{
			this.tags.add(FunctorTag.SCOPE);
			this.scope = scope;
			return this;
		}
		
		private ArrayComprehension getComparray()
		{
			return comparray;
		}
		
		private Functor setComparray(ArrayComprehension comparray)
		{
			this.tags.add(FunctorTag.COMPARRAY);
			this.comparray = comparray;
			return this;
		}
		
		String getGenerator()
		{
			return generator;
		}
		
		Functor setGenerator(String generator)
		{
			this.tags.add(FunctorTag.GENERATOR);
			this.generator = StringUtils.defaultString(generator);
			return this;
		}
		
		Functor setGenerator(boolean generator)
		{
			return generator ? setGenerator("true") : setGenerator(null);
		}
		
		boolean isArrow()
		{
			return isArrow;
		}
		
		Functor setArrow(boolean isArrow)
		{
			this.tags.add(FunctorTag.ARROW);
			this.isArrow = isArrow;
			return this;
		}
		
		boolean isAsync()
		{
			return isAsync;
		}
		
		Functor setAsync(boolean isAsync)
		{
			this.tags.add(FunctorTag.ASYNC);
			this.isAsync = isAsync;
			return this;
		}
		
		boolean isMethod()
		{
			return isMethod;
		}
		
		Functor setMethod(boolean isMethod)
		{
			this.tags.add(FunctorTag.METHOD);
			this.isMethod = isMethod;
			return this;
		}
		
		boolean hasSimpleParams()
		{
			return hasSimpleParams;
		}
		
		Functor setHasSimpleParams(boolean hasSimpleParams)
		{
			this.tags.add(FunctorTag.HASSIMPLEPARAMS);
			this.hasSimpleParams = hasSimpleParams;
			return this;
		}
		
		List<String> getParams()
		{
			return params != null ? Collections.unmodifiableList(params) : null;
		}
		
		Functor setParams(List<String> params)
		{
			this.tags.add(FunctorTag.PARAMS);
			if (params == null) this.params = null;
			else this.params = new ArrayList<String>(params);
			return this;
		}
		
		Functor addParams(String... params)
		{
			if (params != null)
			{
				if (this.params == null) this.params = new ArrayList<String>();
				this.params.addAll(Arrays.asList(params));
			}
			return this;
		}
		
		List<String> getOuterMutables()
		{
			return outerMutables != null ? Collections.unmodifiableList(outerMutables) : null;
		}
		
		Functor setOuterMutables(List<String> outerMutables)
		{
			this.tags.add(FunctorTag.OUTERMUTABLES);
			if (outerMutables == null) this.outerMutables = null;
			else this.outerMutables = new ArrayList<String>(outerMutables);
			return this;
		}
		
		Functor addOuterMutables(String... outerMutables)
		{
			if (outerMutables != null)
			{
				if (this.outerMutables == null) this.outerMutables = new ArrayList<String>();
				this.outerMutables.addAll(Arrays.asList(outerMutables));
			}
			return this;
		}
		
		int getLast()
		{
			return last;
		}
		
		Functor setLast(int last)
		{
			this.tags.add(FunctorTag.LAST);
			this.last = last;
			return this;
		}
		
		int getLastCharacter()
		{
			return lastcharacter;
		}
		
		Functor setLastCharacter(int lastcharacter)
		{
			this.tags.add(FunctorTag.LASTCHARACTER);
			this.lastcharacter = lastcharacter;
			return this;
		}
		
		UniversalContainer getUnusedOption()
		{
			return unusedOption;
		}
		
		Functor setUnusedOption(UniversalContainer unusedOption)
		{
			this.tags.add(FunctorTag.UNUSEDOPTION);
			this.unusedOption = ContainerFactory.undefinedContainerIfNull(unusedOption);
			return this;
		}
		
		String getVerb()
		{
			return verb;
		}
		
		Functor setVerb(String verb)
		{
			this.tags.add(FunctorTag.VERB);
			this.verb = StringUtils.defaultString(verb);
			return this;
		}
		
		void overwrite(Functor overwrites)
		{
			if (overwrites.tags.contains(FunctorTag.NAME)) setName(overwrites.name);
			if (overwrites.tags.contains(FunctorTag.BREAKAGE)) setBreakage(overwrites.breakage);
			if (overwrites.tags.contains(FunctorTag.LOOPAGE)) setLoopage(overwrites.loopage);
			if (overwrites.tags.contains(FunctorTag.ISSTRICT)) setStrict(overwrites.isStrict);
			if (overwrites.tags.contains(FunctorTag.GLOBAL)) setGlobal(overwrites.isGlobal);
			if (overwrites.tags.contains(FunctorTag.LINE)) setLine(overwrites.line);
			if (overwrites.tags.contains(FunctorTag.CHARACTER)) setCharacter(overwrites.character);
			if (overwrites.tags.contains(FunctorTag.METRICS)) setMetrics(overwrites.metrics);
			if (overwrites.tags.contains(FunctorTag.STATEMENT)) setStatement(overwrites.statement);
			if (overwrites.tags.contains(FunctorTag.CONTEXT)) setContext(overwrites.context);
			if (overwrites.tags.contains(FunctorTag.SCOPE)) setScope(overwrites.scope);
			if (overwrites.tags.contains(FunctorTag.COMPARRAY)) setComparray(overwrites.comparray);
			if (overwrites.tags.contains(FunctorTag.GENERATOR)) setGenerator(overwrites.generator);
			if (overwrites.tags.contains(FunctorTag.ARROW)) setArrow(overwrites.isArrow);
			if (overwrites.tags.contains(FunctorTag.ASYNC)) setAsync(overwrites.isAsync);
			if (overwrites.tags.contains(FunctorTag.METHOD)) setMethod(overwrites.isMethod);
			if (overwrites.tags.contains(FunctorTag.HASSIMPLEPARAMS)) setHasSimpleParams(overwrites.hasSimpleParams);
			if (overwrites.tags.contains(FunctorTag.PARAMS)) setParams(overwrites.params);
		}
	}
	
	/**
	 * Determine if the parser has begun parsing executable code.
	 * 
	 * @param {Token} funct - The current "functor" token
	 * 
	 * @returns {boolean}
	 */
	private boolean hasParsedCode(Functor funct)
	{
		return funct.isGlobal() && StringUtils.isEmpty(funct.getVerb());
	}
	
	/**
	 * This function is used as both a null-denotation method *and* a
	 * left-denotation method, meaning the first parameter is overloaded.
	 */
	private Token doTemplateLiteral(Token _this, int context, int rbp)
	{
		return doTemplateLiteral(_this, context, null);
	}
	
	/**
	 * This function is used as both a null-denotation method *and* a
	 * left-denotation method, meaning the first parameter is overloaded.
	 */
	private Token doTemplateLiteral(Token _this, int context, Token left)
	{
		Lexer.LexerContext ctx = _this.getContext();
		boolean noSubst = _this.isNoSubst();
		int depth = _this.getDepth();
		
		BooleanSupplier end = () -> {
			if (State.currToken().isTemplate() && State.currToken().isTail() &&
				State.currToken().getContext() == ctx) return true;
			boolean complete = (State.nextToken().isTemplate() && State.nextToken().isTail() && 
								State.nextToken().getContext() == ctx);
			if (complete) advance();
			return complete || State.nextToken().isUnclosed();
		};
		
		if (!noSubst)
		{
			while (!end.getAsBoolean())
			{
				if (!State.nextToken().isTemplate() || State.nextToken().getDepth() > depth)
				{
					expression(context, 0); // should probably have different rbp?
				}
				else 
				{
					// skip template start / middle
					advance();
				}
			}
		}
		
		Token t = new Token();
		t.setId("(template)");
		t.setType(Token.Type.TEMPLATE);
		t.setTag(left);
		return t;
	}
	
	/**
	 * Parse a function literal.
	 * 
	 * @param context The parsing context
	 * @param name The identifier belonging to the function (if
	 * 			   any)
	 * @param statement The statement that triggered creation
	 * 					of the current function.
	 * @param type If specified, either "generator" or "arrow"
	 * @param loneArg The argument to the function in cases
	 * 				  where it was defined using the
	 * 				  single-argument shorthand
	 * @param parsedOpening Whether the opening parenthesis has
	 * 						already been parsed
	 * @param classExprBinding Define a function with this
	 * 						   identifier in the new function's
	 * 						   scope, mimicking the bahavior of
	 * 						   class expression names within
	 * 						   the body of member functions.
	 */
	private Functor doFunction(int context, String name, Token statement, FunctionType type, Token loneArg, boolean parsedOpening, String classExprBinding, boolean isMethod, boolean ignoreLoopFunc)
	{
		Token token = null;
		boolean isGenerator = type == FunctionType.GENERATOR;
		boolean isArrow = type == FunctionType.ARROW;
		UniversalContainer oldOption = State.getOption();
	    UniversalContainer oldIgnored = State.getIgnored();
	    boolean isAsync = (context & ProdParams.PRE_ASYNC) != 0;
	    
	    context &= ~ProdParams.NOIN;
	    context &= ~ProdParams.TRY_CLAUSE;
	    
	    if (isAsync)
	    {
	    	context |= ProdParams.ASYNC;
	    }
	    else
	    {
	    	context &= ~ProdParams.ASYNC;
	    }
	    context &= ~ProdParams.PRE_ASYNC;
	    
	    State.setOption(State.getOption().create());
		State.setIgnored(State.getIgnored().create());
		
		State.setFunct(new Functor(StringUtils.isNotEmpty(name) ? name : State.getNameStack().infer(), State.nextToken(), new Functor()
			.setStatement(statement)
			.setContext(State.getFunct())
			.setArrow(isArrow)
			.setMethod(isMethod)
			.setGenerator(isGenerator)
			.setAsync(isAsync)
		));
		
		Functor f = State.getFunct();
		token = State.currToken();
		
		functions.add(State.getFunct());
		
		// So that the function is available to itself and referencing itself is not
	    // seen as a closure, add the function name to a new scope, but do not
	    // test for unused (unused: false)
	    // it is a new block scope so that params can override it, it can be block scoped
	    // but declarations inside the function don't cause already declared error
		State.getFunct().getScope().stack("functionouter");
		String internallyAccessibleName = StringUtils.defaultString(name, classExprBinding);
		if (!isMethod && StringUtils.isNotEmpty(internallyAccessibleName))
		{
			State.getFunct().getScope().getBlock().add(internallyAccessibleName,
				StringUtils.isNotEmpty(classExprBinding) ? "class" : "function", State.currToken(), false);
		}
		
		if (!isArrow)
		{
			State.getFunct().getScope().getFunct().add("arguments", "var", token, false);
		}
		
		// create the param scope (params added in functionparams)
		State.getFunct().getScope().stack("functionparams");
		
		UniversalContainer paramsInfo = functionparams(context, loneArg, parsedOpening); 
		if (paramsInfo.test())
		{
			State.getFunct().setParams(paramsInfo.get("params").asList(String.class));
			State.getFunct().setHasSimpleParams(paramsInfo.get("isSimple").asBoolean());
			State.getFunct().getMetrics().arity = paramsInfo.asInt("arity");
			State.getFunct().getMetrics().verifyMaxParametersPerFunction();
		}
		else
		{
			State.getFunct().setParams(new ArrayList<String>());
			State.getFunct().getMetrics().arity = 0;
			State.getFunct().setHasSimpleParams(true);
		}
		
		if (isArrow)
		{
			if (!State.inES6(true))
			{
				warning("W119", State.currToken(), "arrow function syntax (=>)", "6");
			}
			
			if (loneArg == null)
			{
				advance("=>");
			}
		}
		
		block(context, false, true, true, isArrow);
		
		if (!State.getOption().test("noyield") && isGenerator && 
			!State.getFunct().getGenerator().equals("yielded"))
		{
			warning("W124", State.currToken());
		}
		
		State.getFunct().getMetrics().verifyMaxStatementsPerFunction();
		State.getFunct().getMetrics().verifyMaxComplexityPerFunction();
		State.getFunct().setUnusedOption(State.getOption().get("unused"));
		State.setOption(oldOption);
		State.setIgnored(oldIgnored);
		State.getFunct().setLast(State.currToken().getLine());
		State.getFunct().setLastCharacter(State.currToken().getCharacter());
		
		// unstack the params scope
		State.getFunct().getScope().unstack(); // also does usage and label checks
		
		// unstack the function outer stack
		State.getFunct().getScope().unstack();
		
		State.setFunct(State.getFunct().getContext());
		
		if (!ignoreLoopFunc && !State.getOption().test("loopfunc") && State.getFunct().getLoopage() != 0)
		{
			// If the function we just parsed accesses any non-local variables
		    // trigger a warning. Otherwise, the function is safe even within
		    // a loop.
			if (f.getOuterMutables() != null)
			{
				warning("W083", token, String.join(", ", f.getOuterMutables()));
			}
		}
		
		return f;
	}

	private class Metrics
	{
		private Token functionStartToken;
		private int statementCount;
		private int nestedBlockDepth;
		private int complexityCount;
		private int arity;
		
		private Metrics(Token functionStartToken)
		{
			this.functionStartToken = functionStartToken;
			this.statementCount = 0;
			this.nestedBlockDepth = -1;
			this.complexityCount = 1;
			this.arity = 0;
		}
		
		private void verifyMaxStatementsPerFunction()
		{
			if (State.getOption().test("maxstatements") && statementCount > State.getOption().asInt("maxstatements"))
			{
				warning("W071", functionStartToken, String.valueOf(statementCount));
			}
		}
		
		private void verifyMaxParametersPerFunction()
		{
			if (State.getOption().isNumber("maxparams") &&
				arity > State.getOption().asInt("maxparams"))
			{
				warning("W072", functionStartToken, String.valueOf(arity));
			}
		}
		
		private void verifyMaxNestedBlockDepthPerFunction()
		{
			if (State.getOption().test("maxdepth") && nestedBlockDepth > 0 && nestedBlockDepth == State.getOption().asInt("maxdepth") + 1)
			{
				warning("W073", null, String.valueOf(nestedBlockDepth));
			}
		}
		
		private void verifyMaxComplexityPerFunction()
		{
			UniversalContainer max = State.getOption().get("maxcomplexity");
			int cc = complexityCount;
			if (max.test() && cc > max.asInt())
			{
				warning("W074", functionStartToken, String.valueOf(cc));
			}
		}
	}

	private void increaseComplexityCount()
	{
		State.getFunct().getMetrics().complexityCount += 1;
	}

	// Parse assignments that were found instead of conditionals.
	// For example: if (a = 1) { ... }
	private void checkCondAssignment(Token expr)
	{
		String id = null;
		boolean paren = false;
		if (expr != null)
		{
			id = expr.getId();
			paren = expr.isParen();
			if (id.equals(","))
			{
				expr = expr.getExprs().get(expr.getExprs().size() - 1);
				if (expr != null)
				{
					id = expr.getId();
					if (!paren) paren = expr.isParen();
				}
			}
		}
		if (id != null)
		{
			switch (id)
			{
			case "=":
		    case "+=":
		    case "-=":
		    case "*=":
		    case "%=":
		    case "&=":
		    case "|=":
		    case "^=":
		    case "/=":
		    	if (!paren && !State.getOption().get("boss").test())
		    	{
		    		warning("W084");
		    	}
			}
		}
	}

	/**
	 * Validate the properties defined within an object literal or class body.
	 * See the `saveAccessor` and `saveProperty` functions for more detail.
	 * 
	 * @param props - Collection of objects describing the properties
	 *                encountered
	 */
	private void checkProperties(UniversalContainer props)
	{
		// Check for lonely setters if in the ES5 mode.
		if (State.inES5())
		{
			for (String name : props.keys())
			{
				if (props.test(name) && props.get(name).test("setterToken") && !props.get(name).test("getterToken") &&
					!props.get(name).test("static"))
				{
					warning("W078", props.get(name).<Token>valueOf("setterToken"));
				}
			}
		}
	}
	
	private Token metaProperty(int context, String name, Runnable c)
	{
		if (checkPunctuator(State.nextToken(), "."))
		{
			String left = State.currToken().getId();
			advance(".");
			String id = identifier(context);
			State.currToken().setMetaProperty(true);
			if (!name.equals(id))
			{
				error("E057", State.prevToken(), left, id);
			}
			else
			{
				c.run();
			}
			return State.currToken();
		}
		
		return null;
	}
	
	private List<Token> destructuringPattern(int context, boolean openingParsed, boolean isAssignment)
	{
		context &= ~ProdParams.NOIN;
		
		if (!State.inES6())
		{
			warning("W104", State.currToken(),
				isAssignment ? "destructuring assignment" : "destructuring binding", "6");
		}
		
		return destructuringPatternRecursive(context, openingParsed, isAssignment);
	}
	
	private void nextInnerDE(int context, boolean openingParsed, boolean isAssignment, List<Token> identifiers)
	{
		List<Token> ids;
		String ident = null;
		
		if (checkPunctuators(State.nextToken(), "[", "{"))
		{
			ids = destructuringPatternRecursive(context, false, isAssignment);
			for (int idx = 0; idx < ids.size(); idx++)
			{
				identifiers.add(new Token(ids.get(idx).getId(), ids.get(idx).getToken()));
			}
		}
		else if (checkPunctuator(State.nextToken(), ","))
		{
			identifiers.add(new Token(null, State.currToken()));
		}
		else if (checkPunctuator(State.nextToken(), "("))
		{
			advance("(");
			nextInnerDE(context, openingParsed, isAssignment, identifiers);
			advance(")");
		}
		else
		{
			if (isAssignment)
			{
				Token assignTarget = expression(context, 20);
				if (assignTarget != null)
				{
					checkLeftSideAssign(context, assignTarget);
					
					// if the target was a simple identifier, add it to the list to return
					if (assignTarget.isIdentifier())
					{
						ident = assignTarget.getValue();
					}
				}
			}
			else
			{
				ident = identifier(context);
			}
			if (StringUtils.isNotEmpty(ident))
			{
				identifiers.add(new Token(ident, State.currToken()));
			}
		}
	}
	
	private List<Token> destructuringPatternRecursive(int context, boolean openingParsed, boolean isAssignment)
	{
		List<Token> identifiers = new ArrayList<Token>();
		Token firstToken = openingParsed ? State.currToken() : State.nextToken();
		
		IntConsumer assignmentProperty = c -> {
			String id = null;
			if (checkPunctuator(State.nextToken(), "["))
			{
				advance("[");
				expression(c, 10);
				advance("]");
				advance(":");
				nextInnerDE(context, openingParsed, isAssignment, identifiers);
			}
			else if (State.nextToken().getId().equals("(string)") ||
					 State.nextToken().getId().equals("(number)"))
			{
				advance();
				advance(":");
				nextInnerDE(context, openingParsed, isAssignment, identifiers);
			}
			else
			{
				// this id will either be the property name or the property name and the assigning identifier
				boolean isRest = spreadrest("rest");
				
				if (isRest)
				{
					if (!State.inES9())
					{
						warning("W119", State.nextToken(), "object rest property", "9");
					}
					
					// Due to visual symmetry with the array rest property (and the early
					// design of the language feature), developers may mistakenly assume
					// any expression is valid in this position.  Parse an expression and
					// issue an error in order to recover more gracefully from this
					// condition.
					Token expr = expression(c, 10);
					
					if (expr.getType() != Token.Type.IDENTIFIER)
					{
						error("E030", expr, expr.getValue());
					}
				}
				else
				{
					id = identifier(c);
				}
				
				if (!isRest && checkPunctuator(State.nextToken(), ":"))
				{
					advance(":");
					nextInnerDE(context, openingParsed, isAssignment, identifiers);
				}
				else if (StringUtils.isNotEmpty(id))
				{
					// in this case we are assigning (not declaring), so check assignment
					if (isAssignment)
					{
						checkLeftSideAssign(c, State.currToken());
					}
					identifiers.add(new Token(id, State.currToken()));
				}
				
				if (isRest && checkPunctuator(State.nextToken(), ","))
				{
					warning("W130", State.nextToken());
				}
			}
		};
		
		Token id, value;
		if (checkPunctuator(firstToken, "["))
		{
			if (!openingParsed)
			{
				advance("[");
			}
			if (checkPunctuator(State.nextToken(), "]"))
			{
				warning("W137", State.currToken());
			}
			boolean element_after_rest = false;
			while (!checkPunctuator(State.nextToken(), "]"))
			{
				boolean isRest = spreadrest("rest");
				
				nextInnerDE(context, openingParsed, isAssignment, identifiers);
				
				if (isRest && !element_after_rest &&
					checkPunctuator(State.nextToken(), ","))
				{
					warning("W130", State.nextToken());
					element_after_rest = true;
				}
				if (!isRest && checkPunctuator(State.nextToken(), "="))
				{
					if (checkPunctuator(State.prevToken(), "..."))
					{
						advance("]");
					}
					else
					{
						advance("=");
					}
					id = State.prevToken();
					value = expression(context, 10);
					if (value != null && value.isIdentifier() && value.getValue().equals("undefined"))
					{
						warning("W080", id, id.getValue());
					}
				}
				if (!checkPunctuator(State.nextToken(), "]"))
				{
					advance(",");
				}
			}
			advance("]");
		}
		else if (checkPunctuator(firstToken, "{"))
		{
			if (!openingParsed)
			{
				advance("{");
			}
			if (checkPunctuator(State.nextToken(), "}"))
			{
				warning("W137", State.currToken());
			}
			while (!checkPunctuator(State.nextToken(), "}"))
			{
				assignmentProperty.accept(context);
				if (checkPunctuator(State.nextToken(), "="))
				{
					advance("=");
					id = State.prevToken();
					value = expression(context, 10);
					if (value != null && value.isIdentifier() && value.getValue().equals("undefined"))
					{
						warning("W080", id, id.getValue());
					}
				}
				if (!checkPunctuator(State.nextToken(), "}"))
				{
					advance(",");
					if (checkPunctuator(State.nextToken(), "}"))
					{
						// Trailing comma
						// ObjectBindingPattern: { BindingPropertyList , }
						break;
					}
				}
			}
			advance("}");
		}
		return identifiers;
	}
	
	private void destructuringPatternMatch(List<Token> tokens, Token value)
	{
		List<Token> first = value.getFirstTokens();
		
		if (first == null)
			return;
		
		int size = Math.max(tokens.size(), first.size());
		for (int i = 0; i < size; i++)
		{
			Token token = tokens.size() > i ? tokens.get(i) : null;
			Token val = first.size() > i ? first.get(i) : null;
			
			if (token != null && val != null)
				token.setFirstTokens(val);
			else if (token != null && token.getFirstToken() != null && val == null)
				warning("W080", token.getFirstToken(), token.getFirstToken().getValue());
		}
	}
	
	private Token blockVariableStatement(String type, Token statement, int context) //JSHINT_BUG: shouldn't context be as first argument?
	{
		// used for both let and const statements
		
		boolean noin = (context & ProdParams.NOIN) != 0;
		boolean inexport = (context & ProdParams.EXPORT) != 0;
		boolean isLet = type.equals("let");
		boolean isConst = type.equals("const");
		List<Token> tokens;
		boolean lone = false;
		boolean letblock = false;
		
		if (!State.inES6())
		{
			warning("W104", State.currToken(), type, "6");
		}
		
		if (isLet && isMozillaLet())
		{
			advance("(");
			State.getFunct().getScope().stack();
			letblock = true;
			statement.setDeclaration(false);
		}
		
		statement.setFirstTokens();
		for (;;)
		{
			List<Token> names = new ArrayList<Token>();
			if (State.nextToken().getValue().equals("{") || State.nextToken().getValue().equals("["))
			{
				tokens = destructuringPattern(context, false, false);
				lone = false;
			}
			else
			{
				tokens = new ArrayList<Token>();
				tokens.add(new Token(identifier(context), State.currToken()));
				lone = true;
			}
			
			// A `const` declaration without an initializer is permissible within the
			// head of for-in and for-of statements. If this binding list is being
			// parsed as part of a `for` statement of any kind, allow the initializer
			// to be omitted. Although this may erroneously allow such forms from
			// "C-style" `for` statements (i.e. `for (;;) {}`, the `for` statement
			// logic includes dedicated logic to issue the error for such cases.
			if (!noin && isConst && !State.nextToken().getId().equals("="))
			{
				warning("E012", State.currToken(), State.currToken().getValue());
			}
			
			for (Token t : tokens)
			{
				// It is a Syntax Error if the BoundNames of BindingList contains
				// "let".
				if (t.getId().equals("let"))
				{
					warning("W024", t.getToken(), t.getId());
				}
				
				if (State.getFunct().getScope().getBlock().isGlobal())
				{
					if (BooleanUtils.isFalse(predefined.get(t.getId())))
					{
						warning("W079", t.getToken(), t.getId());
					}
				}
				if (StringUtils.isNotEmpty(t.getId()))
				{
					State.getFunct().getScope().addlabel(t.getId(), type, t.getToken());
					names.add(t.getToken());
				}
			}
			
			if (State.nextToken().getId().equals("="))
			{
				statement.setHasInitializer(true);
				
				advance("=");
				if (!noin && peek(0).getId().equals("=") && State.nextToken().isIdentifier())
				{
					warning("W120", State.nextToken(), State.nextToken().getValue());
				}
				Token id = State.prevToken();
				Token value = expression(context, 10);
				if (value != null && value.isIdentifier() && value.getValue().equals("undefined"))
				{
					warning("W080", id, id.getValue());
				}
				if (!lone)
				{
					destructuringPatternMatch(names, value);
				}
			}
			
			// Bindings are not immediately initialized in for-in and for-of
			// statements. As with `const` initializers (described above), the `for`
			// statement parsing logic includes
			if (!noin)
			{
				for (Token t : tokens)
				{
					State.getFunct().getScope().initialize(t.getId());
					
					if (lone && inexport)
					{
						State.getFunct().getScope().setExported(t.getToken().getValue(), t.getToken());
					}
				}
			}
			
			statement.addFirstTokens(names);
			
			if (!State.nextToken().getId().equals(","))
			{
				break;
			}
			
			statement.setHasComma(true);
			parseComma();
		}
		if (letblock)
		{
			advance(")");
			block(context, true, true);
			statement.setBlock(true);
			State.getFunct().getScope().unstack();
		}
		
		return statement;
	}
	
	/**
	 * Determine if the current `let` token designates the beginning of a "let
	 * block" or "let expression" as implemented in the Mozilla SpiderMonkey
	 * engine.
	 *
	 * This function will only return `true` if Mozilla extensions have been
	 * enabled. It would be preferable to detect the language feature regardless
	 * of the parser's state because this would allow JSHint to instruct users to
	 * enable the `moz` option where necessary. This is not possible because the
	 * language extension is not compatible with standard JavaScript. For
	 * example, the following program code may describe a "let block" or a
	 * function invocation:
	 * <pre>
	 *     let(x)
	 *     {
	 *       typeof x;
	 *     }
	 * </pre>
	 * @return 
	 */
	private boolean isMozillaLet()
	{
		return State.nextToken().getId().equals("(") && State.inMoz();
	}
	
	/**
	 * Parsing logic for non-standard Mozilla implementation of `yield`
	 * expressions.
	 */
	private Token mozYield(Token _this, int context)
	{
		Token prev = State.prevToken();
		if (State.inES6(true) && StringUtils.isEmpty(State.getFunct().getGenerator()))
		{
			// If it's a yield within a catch clause inside a generator then that's ok
			if (!(State.getFunct().getName().equals("(catch)") && StringUtils.isNotEmpty(State.getFunct().getContext().getGenerator())))
			{
				error("E046", State.currToken(), "yield");
			}
		}
		State.getFunct().setGenerator("yielded");
		boolean delegatingYield = false;
		
		if (State.nextToken().getValue().equals("*"))
		{
			delegatingYield = true;
			advance("*");
		}
		
		if (_this.getLine() == startLine(State.nextToken()))
		{
			if (delegatingYield ||
				(!State.nextToken().getId().equals(";") && !State.getOption().test("asi") &&
				 !State.nextToken().isReach() && State.nextToken().getNud() != null))
			{
				nobreaknonadjacent(State.currToken(), State.nextToken());
				
				Token first = expression(context, 10);
				_this.setFirstTokens(first);
				
				if (first.getType() == Token.Type.PUNCTUATOR && first.getValue().equals("=") && !first.isParen() && !State.getOption().test("boss"))
				{
					warningAt("W093", first.getLine(), first.getCharacter());
				}
			}
			
			if (!State.nextToken().getId().equals(")") && 
				(prev.getLbp() > 30 || (!prev.isAssign() && !isEndOfExpr()) || prev.getId().equals("yield")))
			{
				error("E050", _this);
			}
		}
		else if (!State.getOption().test("asi"))
		{
			nolinebreak(_this); // always warn (Line breaking error)
		}
		return _this;
	}
	
	private void buildStatementTable()
	{
		Token conststatement = stmt("const", _this -> context -> {
			return blockVariableStatement("const", _this, context);
		});
		conststatement.setExps(true);
		conststatement.setDeclaration(true);
		
		Token letstatement = stmt("let", _this -> context -> {
			return blockVariableStatement("let", _this, context);
		});
		letstatement.setNud(_this -> context -> rbp -> {
			if (isMozillaLet())
			{
				// create a new block scope we use only for the current expression
				State.getFunct().getScope().stack();
				advance("(");
				State.prevToken().fud(context);
				advance(")");
				expression(context, rbp);
				State.getFunct().getScope().unstack();
			}
			else
			{
				_this.setExps(false);
				return State.getSyntax().get("(identifier)").nud(_this, context, rbp);
			}
			return null;
		});
		letstatement.setMeta(new Token.Meta(true, true, true, false, null));
		letstatement.setExps(true);
		letstatement.setDeclaration(true);
		letstatement.setUseFud(_this -> context -> {
			Token next = State.nextToken();
			
			if (_this.getLine() != next.getLine() && !State.inES6())
			{
				return false;
			}
			
			// JSHint generally interprets `let` as a reserved word even though it is
			// not considered as such by the ECMAScript specification because doing so
			// simplifies parsing logic. It is special-cased here so that code such as
			//
			//     let
			//     let
			//
			// is correctly interpreted as an invalid LexicalBinding. (Without this
			// consideration, the code above would be parsed as two
			// IdentifierReferences.)
			boolean nextIsBindingName = next.isIdentifier() && (!isReserved(context, next) ||
				next.getId().equals("let"));
			
			return nextIsBindingName || checkPunctuators(next, "{", "[") ||
				isMozillaLet();
		});
		
		Token varstatement = stmt("var", _this -> context -> {
			boolean noin = (context & ProdParams.NOIN) != 0;
			boolean inexport  = (context & ProdParams.EXPORT) != 0;
			List<Token> tokens;
			boolean lone = false;
			
			_this.setFirstTokens();
			for (;;)
			{
				List<Token> names = new ArrayList<Token>();
				if (State.nextToken().getValue().equals("{") || State.nextToken().getValue().equals("["))
				{
					tokens = destructuringPattern(context, false, false);
					lone = false;
				}
				else
				{
					tokens = new ArrayList<Token>();
					String id = identifier(context);
					
					if (StringUtils.isNotEmpty(id))
					{
						tokens.add(new Token(id, State.currToken()));
					}
					
					lone = true;
				}
				
				if (State.getOption().test("varstmt"))
				{
					warning("W132", _this);
				}
				
				for (Token t : tokens)
				{
					if (State.getFunct().isGlobal() && !State.impliedClosure())
					{
						if (BooleanUtils.isFalse(predefined.get(t.getId())))
						{
							warning("W079", t.getToken(), t.getId());
						}
						else if (State.getOption().get("futurehostile").equals(false))
						{
							if ((!State.inES5() && BooleanUtils.isFalse(Vars.ecmaIdentifiers.get(5).get(t.getId()))) ||
								(!State.inES6() && BooleanUtils.isFalse(Vars.ecmaIdentifiers.get(6).get(t.getId()))))
							{
								warning("W129", t.getToken(), t.getId());
							}
						}
					
					}

					if (StringUtils.isNotEmpty(t.getId()))
					{
						State.getFunct().getScope().addlabel(t.getId(), "var", t.getToken());
						
						if (lone && inexport)
						{
							State.getFunct().getScope().setExported(t.getId(), t.getToken());
						}
						names.add(t.getToken());
					}
				}
				
				if (State.nextToken().getId().equals("="))
				{
					_this.setHasInitializer(true);
					
					State.getNameStack().set(State.currToken());
					
					advance("=");
					if (peek(0).getId().equals("=") && State.nextToken().isIdentifier())
					{
						if (!noin &&
							State.getFunct().getParams() == null || 
							!State.getFunct().getParams().contains(State.nextToken().getValue()))
						{
							warning("W120", State.nextToken(), State.nextToken().getValue());
						}
					}
					Token id = State.prevToken();
					// don't accept `in` in expression if prefix is used for ForIn/Of loop.
					Token value = expression(context, 10);
					if (value != null && State.getFunct().getLoopage() == 0 && value.isIdentifier() &&
						 value.getValue().equals("undefined"))
					{
						warning("W080", id, id.getValue());
					}
					if (!lone)
					{
						destructuringPatternMatch(names, value);
					}
				}
				
				_this.addFirstTokens(names);
				
				if (!State.nextToken().getId().equals(","))
				{
					break;
				}
				_this.setHasComma(true);
				parseComma();
			}
			
			return _this;
		});
		varstatement.setExps(true);
		
		blockstmt("function", _this -> context -> {
			boolean inexport = (context & ProdParams.EXPORT) != 0; 
			boolean generator = false;
			boolean isAsync = (context & ProdParams.PRE_ASYNC) != 0;
			String labelType = "";
			
			if (isAsync)
			{
				labelType = "async ";
			}
			
			if (State.nextToken().getValue().equals("*"))
			{
				if (isAsync && !State.inES9())
				{
					warning("W119", State.prevToken(), "async generators", "9");
				}
				else if (!isAsync && !State.inES6(true))
				{
					warning("W119", State.nextToken(), "function*", "6");
				}
				
				advance("*");
				labelType += "generator ";
				generator = true;
			}
			
			labelType += "function";
			
			if (inblock)
			{
				warning("W082", State.currToken());
			}
			Token nameToken = StringUtils.isNotEmpty(optionalidentifier(context)) ? State.currToken() : null;
			
			if (nameToken == null)
			{
				if (!inexport)
				{
					warning("W025");
				}
			}
			else
			{
				State.getFunct().getScope().addlabel(nameToken.getValue(), labelType, State.currToken(), true);
				
				if (inexport)
				{
					State.getFunct().getScope().setExported(nameToken.getValue(), State.prevToken());
				}
			}
			
			Functor f = doFunction(context,
				(nameToken != null ? nameToken.getValue() : null),
				_this,
				(generator ? FunctionType.GENERATOR : null),
				null,
				false,
				null,
				false,
				inblock // a declaration may already have warned
			);
			
			// If the function declaration is strict because the surrounding code is
			// strict, the invalid name will trigger E008 when the scope manager
			// attempts to create a binding in the strict environment record. An error
			// should only be signaled here when the function itself enables strict
			// mode (the scope manager will not report an error because a declaration
			// does not introduce a binding into the function's environment record).
			boolean enablesStrictMode = f.isStrict() && !State.isStrict();
			if (nameToken != null && (f.getName().equals("arguments") || f.getName().equals("eval")) &&
				enablesStrictMode)
			{
				error("E008", nameToken);
			}
			if (State.nextToken().getId().equals("(") && State.nextToken().getLine() == State.currToken().getLine())
			{
				error("E039");
			}
			return _this;
		}).setDeclaration(true);
		
		prefix("function", _this -> context -> rbp -> {
			boolean generator = false;
			boolean isAsync = (context & ProdParams.PRE_ASYNC) != 0;
			
			if (State.nextToken().getValue().equals("*"))
			{
				if (isAsync && !State.inES9())
				{
					warning("W119", State.prevToken(), "async generators", "9");
				}
				else if (!isAsync && !State.inES6(true))
				{
					warning("W119", State.currToken(), "function*", "6");
				}
				
				advance("*");
				generator = true;
			}
			
			// This context modification restricts the use of `await` as the optional
			// BindingIdentifier in async function expressions.
			Token nameToken = StringUtils.isNotEmpty(optionalidentifier(isAsync ? context | ProdParams.ASYNC : context)) ? 
			    State.currToken() : null;
			
			Functor f = doFunction(context,
				(nameToken != null ? nameToken.getValue() : null),
				null,
				(generator ? FunctionType.GENERATOR : null),
				null,
				false,
				null,
				false,
				false
			);
			
			if (nameToken != null && (f.getName().equals("arguments") || f.getName().equals("eval")) &&
				f.isStrict())
			{
				error("E008", nameToken);
			}
			
			return _this;
		});
		
		blockstmt("if", _this -> context -> {
			Token t = State.nextToken();
			increaseComplexityCount();
			State.setCondition(true);
			advance("(");
			Token expr = expression(context, 0);
			
			if (expr == null)
			{
				quit("E041", _this);
			}
			
			checkCondAssignment(expr);
			
			// When the if is within a for-in loop, check if the condition
			// starts with a negation operator
			Token forinifcheck = null;
			if (State.getOption().test("forin") && State.isForinifcheckneeded())
			{
				State.setForinifcheckneeded(false); // We only need to analyze the first if inside the loop
				forinifcheck = State.getForinifchecks().size() > 0 ? State.getForinifchecks().get(State.getForinifchecks().size()-1) : null;
				if (expr.getType() == Token.Type.PUNCTUATOR && expr.getValue().equals("!"))
				{
					forinifcheck.setType(Token.Type.NEGATIVE);
				}
				else
				{
					forinifcheck.setType(Token.Type.POSITIVE);
				}
			}
			
			advance(")", t);
			State.setCondition(false);
			List<Token> s = block(context, true, true);
			
			// When the if is within a for-in loop and the condition has a negative form,
			// check if the body contains nothing but a continue statement
			if (forinifcheck != null && forinifcheck.getType() == Token.Type.NEGATIVE)
			{
				if (s != null && s.size() > 0 && s.get(0) != null && s.get(0).getType() == Token.Type.IDENTIFIER && s.get(0).getValue().equals("continue"))
				{
					forinifcheck.setType(Token.Type.NEGATIVE_WITH_CONTINUE);
				}
			}
			
			if (State.nextToken().getId().equals("else"))
			{
				advance("else");
				if (State.nextToken().getId().equals("if") || State.nextToken().getId().equals("switch"))
				{
					statement(context);
				}
				else
				{
					block(context, true, true);
				}
			}
			return _this;
		});
		
		blockstmt("try", _this -> context -> {
			boolean b = false;
			
			Runnable doCatch = () -> {
				advance("catch");
				advance("(");
				
				State.getFunct().getScope().stack("catchparams");
				
				if (checkPunctuators(State.nextToken(), "[", "{"))
				{
					List<Token> tokens = destructuringPattern(context, false, false);
					for (Token token : tokens)
					{
						if (StringUtils.isNotEmpty(token.getId()))
						{
							State.getFunct().getScope().addParam(token.getId(), token, "exception");
						}
					}
				}
				else if (State.nextToken().getType() != Token.Type.IDENTIFIER)
				{
					warning("E030", State.nextToken(), State.nextToken().getValue());
				}
				else
				{
					// only advance if an identifier is present. This allows JSHint to
					// recover from the case where no value is specified.
					State.getFunct().getScope().addParam(identifier(context), State.currToken(), "exception");
				}
				
				if (State.nextToken().getValue().equals("if"))
				{
					if (!State.inMoz())
					{
						warning("W118", State.currToken(), "catch filter");
					}
					advance("if");
					expression(context, 0);
				}
				
				advance(")");
				
				block(context, false);
				
				State.getFunct().getScope().unstack();
			};
			
			block(context | ProdParams.TRY_CLAUSE, true);
			
			while (State.nextToken().getId().equals("catch"))
			{
				increaseComplexityCount();
				if (b && (!State.inMoz()))
				{
					warning("W118", State.nextToken(), "multiple catch blocks");
				}
				doCatch.run();
				b = true;
			}
			
			if (State.nextToken().getId().equals("finally"))
			{
				advance("finally");
				block(context, true);
				return null;
			}
			
			if (!b)
			{
				error("E021", State.nextToken(), "catch", State.nextToken().getValue());
			}
			
			return _this;
		});
		
		blockstmt("while", _this -> context -> {
			Token t = State.nextToken();
			State.getFunct().increaseBreakage();
			State.getFunct().increaseLoopage();
			increaseComplexityCount();
			advance("(");
			checkCondAssignment(expression(context, 0));
			advance(")", t);
			block(context, true, true);
			State.getFunct().decreaseBreakage();
			State.getFunct().decreaseLoopage();
			return _this;
		}).setLabelled(true);
		
		blockstmt("with", _this -> context -> {
			Token t = State.nextToken();
			if (State.isStrict())
			{
				error("E010", State.currToken());
			}
			else if (!State.getOption().get("withstmt").test())
			{
				warning("W085", State.currToken());
			}
			
			advance("(");
			expression(context, 0);
			advance(")", t);
			block(context, true, true);
			
			return _this;
		});
		
		blockstmt("switch", _this -> context -> {
			Token t = State.nextToken();
			boolean g = false;
			boolean noindent = false;
			
			State.getFunct().increaseBreakage();
			advance("(");
			checkCondAssignment(expression(context, 0));
			advance(")", t);
			t = State.nextToken();
			advance("{");
			State.getFunct().getScope().stack();
			
			if (State.nextToken().getFrom() == indent)
				noindent = true;
			
			if (!noindent)
				indent += State.getOption().asInt("indent");
			
			_this.setCases(new ArrayList<Token>());
			
			for (;;)
			{
				switch (State.nextToken().getId())
				{
				case "case":
					switch (State.getFunct().getVerb())
					{
					case "yield":
			        case "break":
			        case "case":
			        case "continue":
			        case "return":
			        case "switch":
			        case "throw":
			        	break;
			        case "default":
			        	if (State.getOption().test("leanswitch"))
			        	{
			        		warning("W145", State.nextToken());
			        	}
			        	
			        	break;
			        default:
			        	// You can tell JSHint that you don't use break intentionally by
			            // adding a comment /* falls through */ on a line just before
			            // the next `case`.
			        	if (!State.currToken().isCaseFallsThrough())
			        	{
			        		warning("W086", State.currToken(), "case");
			        	}
					}
					
					advance("case");
					_this.getCases().add(expression(context, 0));
					increaseComplexityCount();
					g = true;
					advance(":");
					State.getFunct().setVerb("case");
					break;
				case "default":
					switch (State.getFunct().getVerb())
					{
					case "yield":
			        case "break":
			        case "continue":
			        case "return":
			        case "throw":
			        	break;
			        case "case":
			        	if (State.getOption().test("leanswitch"))
			        	{
			        		warning("W145", State.currToken());
			        	}
			        	
			        	break;
			        default:
			        	// Do not display a warning if 'default' is the first statement or if
			            // there is a special /* falls through */ comment.
			        	if (_this.getCases().size() != 0)
			        	{
			        		if (!State.currToken().isCaseFallsThrough())
				        	{
				        		warning("W086", State.currToken(), "default");
				        	}
			        	}
					}
					
		        	advance("default");
					g = true;
					advance(":");
					State.getFunct().setVerb("default");
					break;
				case "}":
					if (!noindent)
						indent -= State.getOption().asInt("indent");
					
					advance("}", t);
					State.getFunct().getScope().unstack();
					State.getFunct().decreaseBreakage();
					State.getFunct().setVerb("");
					return null;
				case "(end)":
					error("E023", State.nextToken(), "}");
					return null;
				default:
					indent += State.getOption().asInt("indent");
					if (g)
					{
						switch (State.currToken().getId())
						{
						case ",":
							error("E040");
							return null;
						case ":":
							g = false;
							statements(context);
							break;
						default:
							error("E025", State.currToken());
							return null;
						}
					}
					else
					{
						if (State.currToken().getId().equals(":"))
						{
							advance(":");
				            error("E024", State.currToken(), ":");
				            statements(context);
						}
						else
						{
							error("E021", State.nextToken(), "case", State.nextToken().getValue());
				            return null;
						}
					}
					indent -= State.getOption().asInt("indent");
				}
			}
		}).setLabelled(true);
		
		stmt("debugger", _this -> context -> {
			if (!State.getOption().get("debug").test())
			{
				warning("W087", _this);
			}
			return _this;
		}).setExps(true);
		
		{
			Token x = stmt("do", _this -> context -> {
				State.getFunct().increaseBreakage();
				State.getFunct().increaseLoopage();
				increaseComplexityCount();
				
				_this.setFirstTokens(block(context, true, true));
				advance("while");
				Token t = State.nextToken();
				advance("(");
				checkCondAssignment(expression(context, 0));
				advance(")", t);
				State.getFunct().decreaseBreakage();
				State.getFunct().decreaseLoopage();
				return _this;
			});
			x.setLabelled(true);
			x.setExps(true);
		}
		
		blockstmt("for", _this -> context -> {
			Token t = State.nextToken();
			boolean letscope = false;
			boolean isAsync = false;
			Token foreachtok = null;
			
			if (t.getValue().equals("each"))
			{
				foreachtok = t;
				advance("each");
				if (!State.inMoz())
				{
					warning("W118", State.currToken(), "for each");
				}
			}
			
			if (State.nextToken().isIdentifier() && State.nextToken().getValue().equals("await"))
			{
				advance("await");
				isAsync = true;
				
				if ((context & ProdParams.ASYNC) == 0)
				{
					error("E024", State.currToken(), "await");
				}
				else if (!State.inES9())
				{
					warning("W119", State.currToken(), "asynchronous iteration", "9");
				}
			}
			
			increaseComplexityCount();
			advance("(");
			
			// what kind of for(…) statement it is? for(…of…)? for(…in…)? for(…;…;…)?
			Token nextop = null; // contains the token of the "in" or "of" operator
			Token comma = null; // First comma punctuator at level 0
			Token initializer = null; // First initializer at level 0
			int bindingPower = 0;
			Token target = null;
			Token decl = null;
			Token afterNext = peek();
			
			int headContext = context | ProdParams.NOIN;
			
			if (State.nextToken().getId().equals("var"))
			{
				advance("var");
				decl = State.currToken().fud(headContext);
				comma = decl.hasComma() ? decl : null;
				initializer = decl.hasInitializer() ? decl : null;
			}
			else if (State.nextToken().getId().equals("const") ||
				// The "let" keyword only signals a lexical binding if it is followed by
				// an identifier, `{`, or `[`. Otherwise, it should be parsed as an
				// IdentifierReference (i.e. in a subsquent branch).
				(State.nextToken().getId().equals("let") &&
					((afterNext.isIdentifier() && !afterNext.getId().equals("in")) ||
					 checkPunctuators(afterNext, "{", "["))))
			{
				advance(State.nextToken().getId());
				// create a new block scope
				letscope = true;
				State.getFunct().getScope().stack();
				decl = State.currToken().fud(headContext);
				comma = decl.hasComma() ? decl : null;
				initializer = decl.hasInitializer() ? decl : null;
			}
			else if (!checkPunctuator(State.nextToken(), ";"))
			{
				List<Token> targets = new ArrayList<Token>();
				
				while (!State.nextToken().getValue().equals("in") &&
					!State.nextToken().getValue().equals("of") &&
					!checkPunctuator(State.nextToken(), ";"))
				{
					
					if (checkPunctuators(State.nextToken(), "{", "["))
					{
						for (Token elem : destructuringPattern(headContext, false, true))
						{
							targets.add(elem.getToken());
						}
						if (checkPunctuator(State.nextToken(), "="))
						{
							advance("=");
							initializer = State.currToken();
							expression(headContext, 10);
						}
					}
					else
					{
						target = expression(headContext, 10);
						
						if (target != null)
						{
							if (target.getType() == Token.Type.IDENTIFIER)
							{
								targets.add(target);
							}
							else if (checkPunctuator(target, "="))
							{
								initializer = target;
								targets.add(target);
							}
						}
					}
					
					if (checkPunctuator(State.nextToken(), ","))
					{
						advance(",");
						
						if (comma == null)
						{
							comma = State.currToken();
						}
					}
				}
				
				//checkLeftSideAssign(target, nextop);
				
				// In the event of a syntax error, do not issue warnings regarding the
				// implicit creation of bindings.
				if (initializer == null && comma == null)
				{
					for (Token token : targets)
					{
						if (!State.getFunct().getScope().has(token.getValue()))
						{
							warning("W088", token, token.getValue());
						}
					}
				}
			}
			
			nextop = State.nextToken();
			
			if (isAsync && !nextop.getValue().equals("of"))
			{
				error("E066", nextop);
			}
			
			// if we're in a for (… in|of …) statement
			if (nextop.getValue().equals("in") || nextop.getValue().equals("of"))
			{
				if (nextop.getValue().equals("of"))
				{
					bindingPower = 20;
					
					if (!State.inES6())
					{
						warning("W104", nextop, "for of", "6");
					}
				}
				else
				{
					bindingPower = 0;
				}
				if (comma != null)
				{
					error("W133", comma, nextop.getValue(), "more than one ForBinding");
				}
				if (initializer != null)
				{
					error("W133", initializer, nextop.getValue(), "initializer is forbidden");
				}
				if (target != null && comma == null && initializer == null)
				{
					checkLeftSideAssign(context, target, nextop);
				}
			
				advance(nextop.getValue());
				
				// The binding power is variable because for-in statements accept any
			    // Expression in this position, while for-of statements are limited to
			    // AssignmentExpressions. For example:
			    //
			    //     for ( LeftHandSideExpression in Expression ) Statement
			    //     for ( LeftHandSideExpression of AssignmentExpression ) Statement
				expression(context, bindingPower);
				advance(")", t);
				
				if (nextop.getValue().equals("in") && State.getOption().test("forin"))
				{
					State.setForinifcheckneeded(true);
					
					if (State.getForinifchecks() == null)
					{
						State.setForinifchecks(new ArrayList<Token>());
					}
					
					// Push a new for-in-if check onto the stack. The type will be modified
			        // when the loop's body is parsed and a suitable if statement exists.
					State.getForinifchecks().add(new Token(Token.Type.NONE));
				}
				
				State.getFunct().increaseBreakage();
				State.getFunct().increaseLoopage();
				
				List<Token> s = block(context, true, true);
				
				if (nextop.getValue().equals("in") && State.getOption().test("forin"))
				{
					if (State.getForinifchecks() != null && State.getForinifchecks().size() > 0)
					{
						Token check = State.getForinifchecks().remove(State.getForinifchecks().size()-1);
						
						if (// No if statement or not the first statement in loop body
							s != null && s.size() > 0 && (s.get(0) == null || !s.get(0).getValue().equals("if")) ||
							// Positive if statement is not the only one in loop body
							check.getType() == Token.Type.POSITIVE && s.size() > 1 ||
							// Negative if statement but no continue
							check.getType() == Token.Type.NEGATIVE)
						{
							warning("W089", _this);
						}
					}
					
					// Reset the flag in case no if statement was contained in the loop body
					State.setForinifcheckneeded(false);
				}
				
				State.getFunct().decreaseBreakage();
				State.getFunct().decreaseLoopage();
			}
			else
			{
				if (foreachtok != null)
				{
					error("E045", foreachtok);
				}
				nolinebreak(State.currToken());
				advance(";");
				if (decl != null)
				{
					for (Token token : decl.getFirstTokens())
					{
						State.getFunct().getScope().initialize(token.getValue());
					}
				}
				
				// start loopage after the first ; as the next two expressions are executed
			    // on every loop
				State.getFunct().increaseLoopage();
				if (!State.nextToken().getId().equals(";"))
				{
					checkCondAssignment(expression(context, 0));
				}
				nolinebreak(State.currToken());
				advance(";");
				if (State.nextToken().getId().equals(";"))
				{
					error("E021", State.nextToken(), ")", ";");
				}
				if (!State.nextToken().getId().equals(")"))
				{
					for (;;)
					{
						expression(context, 0);
						if (!State.nextToken().getId().equals(","))
						{
							break;
						}
						parseComma();
					}
				}
				advance(")", t);
				State.getFunct().increaseBreakage();
				block(context, true, true);
				State.getFunct().decreaseBreakage();
				State.getFunct().decreaseLoopage();
			}
			
			// unstack loop blockscope
			if (letscope)
			{
				State.getFunct().getScope().unstack();
			}
			return _this;
		}).setLabelled(true);
		
		stmt("break", _this -> context -> {
			String v = State.nextToken().getValue();
			
			if (!State.getOption().test("asi"))
				nolinebreak(_this);
			
			if (!State.nextToken().getId().equals(";") && !State.nextToken().isReach() &&
				State.currToken().getLine() == startLine(State.nextToken()))
			{
				if (!State.getFunct().getScope().getFunct().hasBreakLabel(v))
				{
					warning("W090", State.nextToken(), v);
				}
				_this.setFirstTokens(State.nextToken());
				advance();
			}
			else
			{
				if (State.getFunct().getBreakage() == 0)
					warning("W052", State.nextToken(), _this.getValue());
			}	
			
			reachable(_this);
			
			return _this;
		}).setExps(true);
		
		stmt("continue", _this -> context -> {
			String v = State.nextToken().getValue();
			
			if (State.getFunct().getBreakage() == 0 || State.getFunct().getLoopage() == 0)
			{
				warning("W052", State.nextToken(), _this.getValue());
			}
			
			if (!State.getOption().test("asi"))
				nolinebreak(_this);
			
			if (!State.nextToken().getId().equals(";") && !State.nextToken().isReach())
			{
				if (State.currToken().getLine() == startLine(State.nextToken()))
				{
					if (!State.getFunct().getScope().getFunct().hasBreakLabel(v))
					{
						warning("W090", State.nextToken(), v);
					}
					_this.setFirstTokens(State.nextToken());
					advance();
				}
			}
			
			reachable(_this);
			
			return _this;
		}).setExps(true);
		
		stmt("return", _this -> context -> {
			if (_this.getLine() == startLine(State.nextToken()))
			{
				if (!State.nextToken().getId().equals(";") && !State.nextToken().isReach())
				{
					Token first = expression(context, 0);
					_this.setFirstTokens(first);
					
					if (first != null && first.getType() == Token.Type.PUNCTUATOR && first.getValue().equals("=") &&
						!first.isParen() && !State.getOption().test("boss"))
					{
						warningAt("W093", first.getLine(), first.getCharacter());
					}
					
					if (State.getOption().test("noreturnawait") && (context & ProdParams.ASYNC) != 0 &&
						(context & ProdParams.TRY_CLAUSE) == 0 &&
						first.isIdentifier() && first.getValue().equals("await"))
					{
						warning("W146", first);
					}
				}
			}
			else
			{
				if (State.nextToken().getType() == Token.Type.PUNCTUATOR &&
					(State.nextToken().getValue().equals("[") || State.nextToken().getValue().equals("{") || State.nextToken().getValue().equals("+") || State.nextToken().getValue().equals("-")))
				{
					nolinebreak(_this); // always warn (Line breaking error)
				}
			}
			
			reachable(_this);
			
			return _this;
		}).setExps(true);
		
		prefix("await", _this -> context -> rbp -> {
			if ((context & ProdParams.ASYNC) != 0)
			{
				// If the parameters of the current function scope have not been defined,
				// it is because the current expression is contained within the parameter
				// list.
				if (State.getFunct().getParams() == null)
				{
					error("E024", _this, "await");
				}
				
				expression(context, 0);
				return _this;
			}
			else
			{
				_this.setExps(false);
				return State.getSyntax().get("(identifier)").nud(_this, context, rbp);
			}
		}).setExps(true);
		
		{
			Token asyncSymbol = prefix("async", _this -> c -> rbp -> {
				int context = c;
				if (_this.isFunc(context))
				{
					if (!State.inES8())
					{
						warning("W119", _this, "async functions", "8");
					}
					
					context |= ProdParams.PRE_ASYNC;
					expression(context, rbp); //JSHINT_BUG: assignment to the func property doesn't make sense because it's not used anywhere
					return _this;
				}
				
				_this.setExps(false);
				return State.getSyntax().get("(identifier)").nud(_this, context, rbp);
			});
			
			asyncSymbol.setMeta(new Token.Meta(true, true, true, false, null));
			asyncSymbol.setIsFunc(_this -> context -> {
				Token next = State.nextToken();
				
				if (_this.getLine() != next.getLine())
				{
					return false;
				}
				
				if (next.getId().equals("function"))
				{
					return true;
				}
				
				if (next.getId().equals("("))
				{
					Token afterParens = peekThroughParens(0);
					
					return afterParens.getId().equals("=>");
				}
				
				if (next.isIdentifier())
				{
					return peek().getId().equals("=>");
				}
				
				return false;
			});
			asyncSymbol.setUseFud(asyncSymbol.getIsFunc());
			// async function declaration
			asyncSymbol.setFud(_this -> context -> {
				if (!State.inES8())
				{
					warning("W119", _this, "async functions", "8");
				}
				context |= ProdParams.PRE_ASYNC;
				context |= ProdParams.INITIAL;
				Token func = expression(context, 0); //JSHINT_BUG: assignment to the func property doesn't make sense because it's not used anywhere
				_this.setBlock(func.isBlock());
				_this.setExps(func.isExps());
				return _this;
			});
			asyncSymbol.setExps(true);
			asyncSymbol.setReserved(false);
		}
		
		{
			Token x = prefix("yield", _this -> context -> rbp -> {
				if (State.inMoz())
				{
					return mozYield(_this, context);
				}
				Token prev = State.prevToken();
				
				// If the parameters of the current function scope have not been defined,
				// it is because the current expression is contained within the parameter
				// list.
				if (State.getFunct().getParams() == null)
				{
					error("E024", _this, "yield");
				}
				
				if (!_this.isBeginsStmt() && prev.getLbp() > 30 && !checkPunctuators(prev, "("))
				{
					error("E061", _this);
				}
				
				if (State.inES6(true) && StringUtils.isEmpty(State.getFunct().getGenerator()))
				{
					// If it's a yield within a catch clause inside a generator then that's ok
					if (!(State.getFunct().getName().equals("(catch)") && StringUtils.isNotEmpty(State.getFunct().getContext().getGenerator())))
					{
						error("E046", State.currToken(), "yield");
					}
				}
				else if (!State.inES6())
				{
					warning("W104", State.currToken(), "yield", "6");
				}
				State.getFunct().setGenerator("yielded");
				
				if (State.nextToken().getValue().equals("*"))
				{
					advance("*");
				}
				
				// Parse operand
				if (!isEndOfExpr() && !State.nextToken().getId().equals(","))
				{
					if (State.nextToken().getNud() != null)
					{
						nobreaknonadjacent(State.currToken(), State.nextToken());
						_this.setFirstTokens(expression(context, 10));
						
						if (_this.getFirstToken().getType() == Token.Type.PUNCTUATOR && _this.getFirstToken().getValue().equals("=") &&
							!_this.getFirstToken().isParen() && !State.getOption().test("boss"))
						{
							warningAt("W093", _this.getFirstToken().getLine(), _this.getFirstToken().getCharacter());
						}
					}
					else if (State.nextToken().getLed() != null)
					{
						if (!State.nextToken().getId().equals(","))
						{
							error("W017", State.nextToken());
						}
					}
				}
				
				return _this;
			});
			x.setExps(true);
			x.setLbp(25);
			x.setRbp(25);
			x.setLtBoundary(Token.BoundaryType.AFTER);
		}
		
		stmt("throw", _this -> context -> {
			nolinebreak(_this);
			_this.setFirstTokens(expression(context, 20));
			
			reachable(_this);
			
			return _this;
		}).setExps(true);
		
		stmt("import", _this -> context -> {
			if (!State.getFunct().getScope().getBlock().isGlobal())
			{
				error("E053", State.currToken(), "Import");
			}
			
			if (!State.inES6())
			{
				warning("W119", State.currToken(), "import", "6");
			}
			
			if (State.nextToken().getType() == Token.Type.STRING)
			{
				// ModuleSpecifier :: StringLiteral
				advance("(string)");
				return _this;
			}
			
			if (State.nextToken().isIdentifier())
			{
				// ImportClause :: ImportedDefaultBinding
				_this.setName(identifier(context));
				// Import bindings are immutable (see ES6 8.1.1.5.5)
				State.getFunct().getScope().addlabel(_this.getName(), "import", State.currToken(), true);
				
				if (State.nextToken().getValue().equals(","))
				{
					// ImportClause :: ImportedDefaultBinding , NameSpaceImport
					// ImportClause :: ImportedDefaultBinding , NamedImports
					advance(",");
					// At this point, we intentionally fall through to continue matching
			        // either NameSpaceImport or NamedImports.
			        // Discussion:
			        // https://github.com/jshint/jshint/pull/2144#discussion_r23978406
				}
				else
				{
					advance("from");
					advance("(string)");
					return _this;
				}
			}
			
			if (State.nextToken().getId().equals("*"))
			{
				// ImportClause :: NameSpaceImport
				advance("*");
				advance("as");
				if (State.nextToken().isIdentifier())
				{
					_this.setName(identifier(context));
					// Import bindings are immutable (see ES6 8.1.1.5.5)
					State.getFunct().getScope().addlabel(_this.getName(), "import", State.currToken(), true);
				}
			}
			else
			{
				// ImportClause :: NamedImports
				advance("{");
				for (;;)
				{
					if (State.nextToken().getValue().equals("}"))
					{
						advance("}");
						break;
					}
					String importName;
					if (State.nextToken().getType() == Token.Type.DEFAULT)
					{
						importName = "default";
						advance("default");
					}
					else
					{
						importName = identifier(context);
					}
					if (State.nextToken().getValue().equals("as"))
					{
						advance("as");
						importName = identifier(context);
					}
					
					// Import bindings are immutable (see ES6 8.1.1.5.5)
					State.getFunct().getScope().addlabel(importName, "import", State.currToken(), true);
					
					if (State.nextToken().getValue().equals(","))
					{
						advance(",");
					}
					else if (State.nextToken().getValue().equals("}"))
					{
						advance("}");
						break;
					}
					else
					{
						error("E024", State.nextToken(), State.nextToken().getValue());
						break;
					}
				}
			}
			
			// FromClause
			advance("from");
			advance("(string)");
			
			// Support for ES2015 modules was released without warning for `import`
			// declarations that lack bindings. Issuing a warning would therefor
			// constitute a breaking change.
			// JSHINT_TODO: enable this warning in JSHint 3
			// if (hasBindings) {
			//   warning("W142", this, "import", moduleSpecifier);
			// }	
			
			return _this;
		}).setExps(true);
		
		stmt("export", _this -> context -> {
			boolean ok = true;
			String identifier;
			Token moduleSpecifier = null;
			context = context | ProdParams.EXPORT;
			
			if (!State.inES6())
			{
				warning("W119", State.currToken(), "export", "6");
				ok = false;
			}
			
			if (!State.getFunct().getScope().getBlock().isGlobal())
			{
				error("E053", State.currToken(), "Export");
				ok = false;
			}
			
			if (State.nextToken().getValue().equals("*"))
			{
				// ExportDeclaration :: export * FromClause
				advance("*");
				advance("from");
				advance("(string)");
				return _this;
			}
			
			if (State.nextToken().getType() == Token.Type.DEFAULT)
			{
				// ExportDeclaration ::
				//		export default [lookahead ∉ { function, class }] AssignmentExpression[In] ;
				//		export default HoistableDeclaration
				//		export default ClassDeclaration
				
				// because the 'name' of a default-exported function is, confusingly, 'default'
				// see https://bocoup.com/blog/whats-in-a-function-name
				State.getNameStack().set(State.nextToken());
				
				advance("default");
				String exportType = State.nextToken().getId();
				if (exportType.equals("function"))
				{
					_this.setBlock(true);
					advance("function");
					State.getSyntax().get("function").fud(context);
				}
				else if (exportType.equals("class"))
				{
					_this.setBlock(true);
					advance("class");
					State.getSyntax().get("class").fud(context);
				}
				else
				{
					Token token = expression(context, 10);
					if (token.isIdentifier())
					{
						identifier = token.getValue();
						State.getFunct().getScope().setExported(identifier, token);
					}
				}
				return _this;
			}
			if (State.nextToken().getValue().equals("{"))
			{
				// ExportDeclaration :: export ExportClause
				advance("{");
				List<Token> exportedTokens = new ArrayList<Token>();
				while (!checkPunctuator(State.nextToken(), "}"))
				{
					if (!State.nextToken().isIdentifier())
					{
						error("E030", State.nextToken(), State.nextToken().getValue());
					}
					advance();
					
					exportedTokens.add(State.currToken());
					
					if (State.nextToken().getValue().equals("as"))
					{
						advance("as");
						if (!State.nextToken().isIdentifier())
						{
							error("E030", State.nextToken(), State.nextToken().getValue());
						}
						advance();
					}
					
					if (!checkPunctuator(State.nextToken(), "}"))
					{
						advance(",");
					}
				}
				advance("}");
				if (State.nextToken().getValue().equals("from"))
				{
					// ExportDeclaration :: export ExportClause FromClause
					advance("from");
					moduleSpecifier = State.nextToken();
					advance("(string)");
				}
				else if (ok)
				{
					for (Token token : exportedTokens)
					{
						State.getFunct().getScope().setExported(token.getValue(), token);
					}
				}
				
				if (exportedTokens.size() == 0)
				{
					if (moduleSpecifier != null)
					{
						warning("W142", _this, "export", moduleSpecifier.getValue());
					}
					else
					{
						warning("W141", _this, "export");
					}
				}
				
				
				return _this;
			}
			else if (State.nextToken().getId().equals("var"))
			{
				// ExportDeclaration :: export VariableStatement
				advance("var");
				State.currToken().fud(context);
			}
			else if (State.nextToken().getId().equals("let"))
			{
				// ExportDeclaration :: export VariableStatement
				advance("let");
				State.currToken().fud(context);
			}
			else if (State.nextToken().getId().equals("const"))
			{
				// ExportDeclaration :: export VariableStatement
				advance("const");
				State.currToken().fud(context);
			}
			else if (State.nextToken().getId().equals("function"))
			{
				// ExportDeclaration :: export Declaration
				_this.setBlock(true);
				advance("function");
				State.getSyntax().get("function").fud(context);
			}
			else if (State.nextToken().getId().equals("class"))
			{
				// ExportDeclaration :: export Declaration
				_this.setBlock(true);
				advance("class");
				State.getSyntax().get("class").fud(context);
			}
			else
			{
				error("E024", State.nextToken(), State.nextToken().getValue());
			}
			
		    return _this;
		}).setExps(true);
		
		// Future Reserved Words
		
		futureReservedWord(Token.Type.ABSTRACT);
		futureReservedWord(Token.Type.BOOLEAN);
		futureReservedWord(Token.Type.BYTE);
		futureReservedWord(Token.Type.CHAR);
		futureReservedWord(Token.Type.DOUBLE);
		futureReservedWord(Token.Type.ENUM, new Token.Meta(true, false, false, false, null));
		futureReservedWord(Token.Type.EXPORT, new Token.Meta(true, false, false,false, null));
		futureReservedWord(Token.Type.EXTENDS, new Token.Meta(true, false, false, false, null));
		futureReservedWord(Token.Type.FINAL);
		futureReservedWord(Token.Type.FLOAT);
		futureReservedWord(Token.Type.GOTO);
		futureReservedWord(Token.Type.IMPLEMENTS, new Token.Meta(true, false, true, false, null));
		futureReservedWord(Token.Type.IMPORT, new Token.Meta(true, false, false, false, null));
		futureReservedWord(Token.Type.INT);
		futureReservedWord(Token.Type.INTERFACE, new Token.Meta(true, false, true, false, null));
		futureReservedWord(Token.Type.LONG);
		futureReservedWord(Token.Type.NATIVE);
		futureReservedWord(Token.Type.PACKAGE, new Token.Meta(true, false, true, false, null));
		futureReservedWord(Token.Type.PRIVATE, new Token.Meta(true, false, true, false, null));
		futureReservedWord(Token.Type.PROTECTED, new Token.Meta(true, false, true, false, null));
		futureReservedWord(Token.Type.PUBLIC, new Token.Meta(true, false, true, false, null));
		futureReservedWord(Token.Type.SHORT);
		futureReservedWord(Token.Type.STATIC, new Token.Meta(true, false, true, false, null));
		futureReservedWord(Token.Type.SYNCHRONIZED);
		futureReservedWord(Token.Type.TRANSIENT);
		futureReservedWord(Token.Type.VOLATILE);
	}
	
	/**
	 * Determine if SuperCall or SuperProperty may be used in the current context
	 * (as described by the provided "functor" object).
	 * 
	 * @param type - one of "property" or "call"
	 * @param funct - a "functor" object describing the current function
	 * 				  context
	 * 
	 * @return
	 */
	private boolean supportsSuper(String type, Functor funct)
	{
		if (type.equals("call") && funct.isAsync())
		{
			return false;
		}
		
		if (type.equals("property") && funct.isMethod())
		{
			return true;
		}
		
		if (type.equals("call") && funct.getStatement() != null &&
			funct.getStatement().getId().equals("class"))
		{
			return true;
		}
		
		if (funct.isArrow())
		{
			return supportsSuper(type, funct.getContext());
		}
		
		return false;
	}
	
	private Token superNud(Token _this)
	{
		Token next = State.nextToken();
		
		if(checkPunctuators(next, "[", "."))
		{
			if(!supportsSuper("property", State.getFunct()))
			{
				error("E063", _this);
			}
		}
		else if (checkPunctuator(next, "("))
		{
			if(!supportsSuper("call", State.getFunct()))
			{
				error("E064", _this);
			}
		}
		else
		{
			error("E024", next, StringUtils.defaultIfEmpty(next.getValue(), next.getId()));
		}
		
		return _this;
	};
	
	// this function is used to determine whether a squarebracket or a curlybracket
	// expression is a comprehension array, destructuring assignment or a json value.
	private class LookupBlockType
	{
		private boolean isCompArray = false;
		private boolean notJson = false;
		private boolean isDestAssign = false;
		
		private LookupBlockType()
		{
			Token pn = null, pn1 = null, prev = null;
			int i = -1;
			int bracketStack = 0;
			
			if (checkPunctuators(State.currToken(), "[", "{"))
			{
				bracketStack += 1;
			}
			do
			{
				prev = i == -1 ? State.currToken() : pn;
				pn = i == -1 ? State.nextToken() : peek(i);
				pn1 = peek(i + 1);
				i = i + 1;
				if (checkPunctuators(pn, "[", "{"))
				{
					bracketStack += 1;
				}
				else if (checkPunctuators(pn, "]", "}"))
				{
					bracketStack -= 1;
				}
				if (bracketStack == 1 && pn.isIdentifier() && pn.getValue().equals("for") &&
					!checkPunctuator(prev, "."))
				{
					isCompArray = true;
					notJson = true;
					break;
				}
				if (bracketStack == 0 && checkPunctuators(pn, "}", "]"))
				{
					if (pn1.getValue().equals("="))
					{
						isDestAssign = true;
						notJson = true;
						break;
					}
					else if (pn1.getValue().equals("."))
					{
						notJson = true;
						break;
					}
				}
				if (checkPunctuator(pn, ";"))
				{
					notJson = true;
				}
			}
			while (bracketStack > 0 && !pn.getId().equals("(end)"));
		}
	}
	
	/**
	 * Update an object used to track property names within object initializers
	 * and class bodies. Produce warnings in response to duplicated names.
	 *
	 * @param props - a collection of all properties of the object or
	 *                class to which the current property is being
	 *                assigned
	 * @param name - the property name
	 * @param tkn - the token defining the property
	 * @param isClass - whether the accessor is part of an ES6 Class
	 *                  definition
	 * @param isStatic - whether the accessor is a static method
	 * @param isComputed - whether the property is a computed expression like [Symbol.iterator]
	 */
	private void saveProperty(UniversalContainer props, String name, Token tkn, boolean isClass, boolean isStatic, boolean isComputed)
	{
		if (tkn.isIdentifier())
		{
			name = tkn.getValue();
		}
		String key = name;
		if (isClass && isStatic)
		{
			key = "static " + name;
		}
		
		if (props.test(key) && !name.equals("__proto__") && !isComputed)
		{
			String[] msg = {"key", "class method", "static class method"};
			warning("W075", State.nextToken(), msg[(isClass ? 1:0) + (isStatic ? 1:0)], name);
		}
		else
		{
			props.set(key, ContainerFactory.nullContainer().create());
		}
		
		props.get(key).set("basic", true);
		props.get(key).set("basictkn", tkn);
	}

	/**
	 * Update an object used to track property names within object initializers
	 * and class bodies. Produce warnings in response to duplicated names.
	 * 
	 * @param accessorType - Either "get" or "set"
	 * @param props - a collection of all properties of the object or
	 *                class to which the current accessor is being
	 *                assigned
	 * @param tkn - the identifier token representing the accessor name
	 * @param isClass - whether the accessor is part of an ES6 Class
	 *                  definition
	 * @param isStatic - whether the accessor is a static method
	 */
	private void saveAccessor(String accessorType, UniversalContainer props, String name, Token tkn, boolean isClass, boolean isStatic)
	{
		String flagName = accessorType.equals("get") ? "getterToken" : "setterToken";
		String key = name;
		State.currToken().setAccessorType(accessorType);
		State.getNameStack().set(tkn);
		if (isClass && isStatic)
		{
			key = "static " + name;
		}
		
		if (props.test(key))
		{
			if ((props.get(key).test("basic") || props.get(key).test(flagName)) && !name.equals("__proto__"))
			{
				String msg = "";
				if (isClass)
				{
					if (isStatic)
					{
						msg += "static ";
					}
					msg += accessorType + "ter method";
				}
				else
				{
					msg = "key";
				}
				warning("W075", State.nextToken(), msg, name);
			}
		}
		else
		{
			props.set(key, ContainerFactory.nullContainer().create());
		}
		
		props.get(key).set(flagName, tkn);
		if (isStatic)
		{
			props.get(key).set("static", true);
		}
	}

	/**
	 * Parse a computed property name within object initializers and class bodies
	 * as introduced by ES2015. For example:
	 *
	 *     void {
	 *       [object.method()]: null
	 *     };
	 *
	 * @param context - the parsing context
	 *
	 * @return - the token value that describes the expression which
	 *           defines the property name
	 */
	private Token computedPropertyName(int context)
	{
		advance("[");
		
		// Explicitly reclassify token as a delimeter to prevent its later
		// interpretation as an "infix" operator.
		State.currToken().setDelim(true);
		State.currToken().setLbp(0);
		
		if (!State.inES6())
		{
			warning("W119", State.currToken(), "computed property names", "6");
		}
		Token value = expression(context & ~ProdParams.NOIN, 10);
		advance("]");
		return value;
	}

	/**
	 * Test whether a given token is a punctuator whose `value` property matches
	 * one of the specified values. This function explicitly verifies the token's
	 * `type` property so that like-valued string literals (e.g. `";"`) do not
	 * produce false positives.
	 * 
	 * @param token
	 * @param values
	 * 
	 * @return
	 */
	private boolean checkPunctuators(Token token, String... values)
	{
		if (token.getType() == Token.Type.PUNCTUATOR)
		{
			return ArrayUtils.contains(values, token.getValue());
		}
		return false;
	}
	
	/**
	 * Test whether a given token is a punctuator whose `value` property matches
	 * the specified value. This function explicitly verifies the token's `type`
	 * property so that like-valued string literals (e.g. `";"`) do not produce
	 * false positives.
	 * 
	 * @param token
	 * @param value
	 * 
	 * @return
	 */
	private boolean checkPunctuator(Token token, String value)
	{
		return token.getType() == Token.Type.PUNCTUATOR && token.getValue().equals(value);
	}

	// Check whether this function has been reached for a destructuring assign with undeclared values
	private void destructuringAssignOrJsonValue(int context)
	{
		// lookup for the assignment (ECMAScript 6 only)
		// if it has semicolons, it is a block, so go parse it as a block
		// or it's not a block, but there are assignments, check for undeclared variables
		LookupBlockType block = new LookupBlockType();
		if (block.notJson)
		{
			if (!State.inES6() && block.isDestAssign)
			{
				warning("W104", State.currToken(), "destructuring assignment", "6");
			}
			statements(context);
		}
		// otherwise parse json value
		else
		{
			State.getOption().set("laxbreak", true);
			State.setJsonMode(true);
			jsonValue();
		}
	}
	
	/**
	 * Parse and define the three states of a list comprehension in order to
	 * avoid defining global variables, but keeping them to the list
	 * comprehension scope only. The order of the states are as follows:
	 *
	 * - "use" - which will be the returned iterative part of the list
	 *   comprehension
	 * - "define" - which will define the variables local to the list
	 *   comprehension
	 * - "filter" - which will help filter out values
	 */
	private class ArrayComprehension
	{
		private class Variable
		{
			private Token token;
			private String value;
			private boolean undef;
			private boolean unused;
			
			private Variable(Token token, String value, boolean undef, boolean unused)
			{
				this.token = token;
				this.value = value;
				this.undef = undef;
				this.unused = unused;
			}
		}
		
		private class CompArray
		{
			private String mode = "use";
			private List<Variable> variables = new ArrayList<Variable>();
		}
		
		private List<CompArray> carrays = new ArrayList<CompArray>();
		private CompArray current = null;
		
		private boolean declare(String v)
		{
			int l = 0;
			for (Variable elt : current.variables)
			{
				// if it has, change its undef state
				if (elt.value.equals(v))
				{
					elt.undef = false;
					l++;
				}
			}
			return l != 0;
		}
		
		private boolean use(String v)
		{
			int l = 0;
			for (Variable elt : current.variables)
			{
				// and if it has been defined
				if (elt.value.equals(v) && !elt.undef)
				{
					if (elt.unused == true)
					{
						elt.unused = false;
					}
					l++;
				}
			}
			// otherwise we warn about it
			return (l == 0);
		}
		
		private void stack()
		{
			current = new CompArray();
			carrays.add(current);
		}
		
		private void unstack()
		{
			for (Variable v : current.variables)
			{
				if (v.unused)
					warning("W098", v.token, StringUtils.defaultIfEmpty(v.token.getRawText(), v.value));
				if (v.undef)
					State.getFunct().getScope().getBlock().use(v.value, v.token);
			}
			carrays.remove(carrays.size()-1);
			current = carrays.size() > 0 ? carrays.get(carrays.size()-1) : null;
		}
		
		private void setState(String s)
		{
			if (s.equals("use") || s.equals("define") || s.equals("generate") || s.equals("filter"))
				current.mode = s;
		}
		
		private boolean check(String v)
		{
			if (current == null)
			{
				return false;
			}
			// When we are in "use" state of the list comp, we enqueue that var
			if (current != null && current.mode.equals("use"))
			{
				if (use(v))
				{
					current.variables.add(new Variable(State.currToken(), v, true, false));
				}
				return true;
			}
			// When we are in "define" state of the list comp,
			else if (current != null && current.mode.equals("define"))
			{
				// check if the variable has been used previously
				if (!declare(v))
				{
					current.variables.add(new Variable(State.currToken(), v, false, true));
				}
				return true;
			}
			// When we are in the "generate" state of the list comp,
			else if (current != null && current.mode.equals("generate"))
			{
				State.getFunct().getScope().getBlock().use(v, State.currToken());
				return true;
			}
			// When we are in "filter" state,
			else if (current != null && current.mode.equals("filter"))
			{
				// we check whether current variable has been declared
				if (use(v))
				{
					// if not we warn about it
					State.getFunct().getScope().getBlock().use(v, State.currToken());
				}
				return true;
			}
			return false;
		}
	}
	
	/**
	 * Parse input according to the JSON format.
	 *
	 * http://json.org/
	 */
	private void jsonValue()
	{
		Runnable jsonObject = () -> {
			UniversalContainer o = ContainerFactory.createObject();
			Token t = State.nextToken();
			advance("{");
			if (!State.nextToken().getId().equals("}"))
			{
				for (;;)
				{
					if (State.nextToken().getId().equals("(end)"))
					{
						error("E026", State.nextToken(), String.valueOf(t.getLine()));
					}
					else if (State.nextToken().getId().equals("}"))
					{
						warning("W094", State.currToken());
						break;
					}
					else if (State.nextToken().getId().equals(","))
					{
						error("E028", State.nextToken());
					}
					else if (!State.nextToken().getId().equals("(string)"))
					{
						warning("W095", State.nextToken(), State.nextToken().getValue());
					}
					if (o.get(State.nextToken().getValue()).equals(true))
					{
						warning("W075", State.nextToken(), "key", State.nextToken().getValue());
					}
					else if ((State.nextToken().getValue().equals("__proto__") &&
							 !State.getOption().get("proto").test()) || (State.nextToken().getValue().equals("__iterator__") &&
							 !State.getOption().get("iterator").test()))
					{
						warning("W096", State.nextToken(), State.nextToken().getValue());
					}
					else
					{
						o.set(State.nextToken().getValue(), true);
					}
					advance();
					advance(":");
					jsonValue();
					if (!State.nextToken().getId().equals(","))
					{
						break;
					}
					advance(",");
				}
			}
			advance("}");
		};
		
		Runnable jsonArray = () -> {
			Token t = State.nextToken();
			advance("[");
			if (!State.nextToken().getId().equals("]"))
			{
				for (;;)
				{
					if (State.nextToken().getId().equals("(end)"))
					{
						error("E027", State.nextToken(), String.valueOf(t.getLine()));
					}
					else if (State.nextToken().getId().equals("]"))
					{
						warning("W094", State.currToken());
						break;
					}
					else if (State.nextToken().getId().equals(","))
					{
						error("E028", State.nextToken());
					}
					jsonValue();
					if (!State.nextToken().getId().equals(","))
					{
						break;
					}
					advance(",");
				}
			}
			advance("]");
		};
		
		switch (State.nextToken().getId())
		{
		case "{":
			jsonObject.run();
			break;
		case "[":
			jsonArray.run();
			break;
		case "true":
	    case "false":
	    case "null":
	    case "(number)":
	    case "(string)":
	    	advance();
	    	break;
	    case "-":
	    	advance("-");
	    	advance("(number)");
	    	break;
	    default:
	    	error("E003", State.nextToken());
		}
	}
	
	/**
	 * Lint dynamically-evaluated code, appending any resulting errors/warnings
	 * into the global `errors` array.
	 * 
	 * @param internals collection of "internals" objects describing string tokens that contain evaluated code
	 * @param options linting options to apply
	 * @param globals globally-defined bindings for the evaluated code
	 */
	private void lintEvalCode(List<InternalSource> internals, LinterOptions options, LinterGlobals globals)
	{
		for (int idx = 0; idx < internals.size(); idx += 1)
		{
			InternalSource internal = internals.get(idx);
			options.set("scope", internal.getElem().toString()); //JSHINT_BUG: why token is writen here??
			int priorErrorCount = errors.size();
			
			lint(internal.getCode(), options, globals);
			
			for (int jdx = priorErrorCount; jdx < errors.size(); jdx += 1)
			{
				errors.get(jdx).setLine(errors.get(jdx).getLine() + internal.getToken().getLine() - 1);
			}
		}
	}
	
	private String escapeRegex(String str)
	{
		return Reg.escapeRegexpChars(str); // PORT INFO: replace regexp was moved to Reg class
	}
	
	private List<LinterWarning> errors = new ArrayList<LinterWarning>();
	private List<InternalSource> internals = new ArrayList<InternalSource>(); // "internal" scripts, like eval containing a static string
	private Set<String> blacklist = new HashSet<String>();
	private String scriptScope = "";
	
	public boolean lint(String s) throws JSHintException
	{
		return lint(s, null, null);
	}
	
	public boolean lint(String s, LinterOptions options) throws JSHintException
	{
		return lint(s, options, null);
	}
	
	public boolean lint(String s, LinterOptions options, LinterGlobals globals) throws JSHintException
	{
		LinterOptions o = new LinterOptions(options);
		LinterGlobals g = (globals == null ? new LinterGlobals() : globals);
		
		init(o, g);
		
		if (s == null)
		{
			errorAt("E004", 0);
			return false;
		}
		
		for (Delimiter delimiterPair : o.getIgnoreDelimiters())
		{
			if (StringUtils.isEmpty(delimiterPair.getStart()) || StringUtils.isEmpty(delimiterPair.getEnd()))
				continue;
			
			String reIgnoreStr = escapeRegex(delimiterPair.getStart()) +
								 "[\\s\\S]*?" + 
								 escapeRegex(delimiterPair.getEnd());
			
			Pattern reIgnore = Pattern.compile(reIgnoreStr, Pattern.CASE_INSENSITIVE);
			
			// PORT INFO: replace regexp was moved to Reg class
			s = Reg.blankDelimiteredText(s, reIgnore);
		};
		
		return run(new Lexer(s), o, g);
	}
	
	public boolean lint(String[] s) throws JSHintException
	{
		return lint(s, null, null);
	}
	
	public boolean lint(String[] s, LinterOptions options) throws JSHintException
	{
		return lint(s, options, null);
	}
	
	public boolean lint(String[] s, LinterOptions options, LinterGlobals globals) throws JSHintException
	{
		LinterOptions o = new LinterOptions(options);
		LinterGlobals g = (globals == null ? new LinterGlobals() : globals);
		
		init(o, g);
		
		if (s == null)
		{
			errorAt("E004", 0);
			return false;
		}
		
		//JSHINT_BUG: where is ignore delimiters for source array??
		
		return run(new Lexer(s), o, g);
	}
	
	private void init(LinterOptions o, LinterGlobals g)
	{
		//TODO: move this methods to constructor class when State class will be non-static
		buildSyntaxTable();
		ecmaScriptParser();
		buildStatementTable();
		addModule(new Style());
		
		State.reset();
		
		if (o.hasOption("scope"))
		{
			scriptScope = o.getAsString("scope");
		}
		else
		{
			errors = new ArrayList<LinterWarning>();
			internals = new ArrayList<InternalSource>();
			blacklist = new HashSet<String>();
			scriptScope = "(main)";
		}
		
		predefined = new HashMap<String, Boolean>();
		combine(predefined, Vars.ecmaIdentifiers.get(3));
		combine(predefined, Vars.reservedVars);
		
		declared = new HashMap<String, Token>();
		Map<String, Boolean> exported = new HashMap<String, Boolean>(); // Variables that live outside the current file
		
		o.readPredefineds(predefined, blacklist);
		o.readGlobals(predefined, blacklist);
		o.readExporteds(exported);
		
		State.setOption(o.getOptions(false));
		State.setIgnored(o.getOptions(true));
		
		if (!State.getOption().test("indent")) State.getOption().set("indent", 4);
		if (!State.getOption().test("maxerr")) State.getOption().set("maxerr", 50);
		
		indent = 1;
		
		ScopeManager scopeManagerInst = new ScopeManager(this.predefined, exported, declared);
		scopeManagerInst.on("warning", new LexerEventListener()
			{
				@Override
				public void accept(EventContext ev) throws JSHintException
				{
					warning(ev.getCode(), ev.getToken(), ev.getData());
				}
			});
		
		scopeManagerInst.on("error", new LexerEventListener()
			{
				@Override
				public void accept(EventContext ev) throws JSHintException
				{
					warning(ev.getCode(), ev.getToken(), ev.getData());
				}
			});
		
		State.setFunct(new Functor("(global)", null, new Functor()
			.setGlobal(true)
			.setScope(scopeManagerInst)
			.setComparray(new ArrayComprehension())
			.setMetrics(new Metrics(State.nextToken()))
		));
		
		functions = new ArrayList<Functor>();
		functions.add(State.getFunct());
		urls = new ArrayList<String>();
		member = new HashMap<String, Integer>();
		membersOnly = null;
		inblock = false;
		lookahead = new ArrayList<Token>();
		
		emitter.removeAllListeners();
		for (JSHintModule func : extraModules)
		{
			func.execute(this);
		}
		
		State.setNextToken(State.getSyntax().get("(begin)"));
		State.setCurrToken(State.nextToken());
		State.setPrevToken(State.nextToken());
	}
	
	private boolean run(Lexer l, LinterOptions o, LinterGlobals g)
	{
		lex = l;
		
		lex.on("warning", new LexerEventListener()
		{
			@Override
			public void accept(EventContext ev) throws JSHintException
			{
				warningAt(ev.getCode(), ev.getLine(), ev.getCharacter(), ev.getData());
			}
		});
		
		lex.on("error", new LexerEventListener()
		{
			@Override
			public void accept(EventContext ev) throws JSHintException
			{
				errorAt(ev.getCode(), ev.getLine(), ev.getCharacter(), ev.getData());
			}
		});
		
		lex.on("fatal", new LexerEventListener()
		{
			@Override
			public void accept(EventContext ev) throws JSHintException
			{
				quit("E041", new Token(ev));
			}
		});
		
		lex.on("Identifier", new LexerEventListener()
		{
			@Override
			public void accept(EventContext ev) throws JSHintException
			{
				emitter.emit("Identifier", ev);
			}
		});
		
		lex.on("String", new LexerEventListener()
		{
			@Override
			public void accept(EventContext ev) throws JSHintException
			{
				emitter.emit("String", ev);
			}
		});
		
		lex.on("Number", new LexerEventListener()
		{
			@Override
			public void accept(EventContext ev) throws JSHintException
			{
				emitter.emit("Number", ev);
			}
		});
		
		// check options
		for (String name : o)
		{
			checkOption(name, true, State.currToken());
		}
		for (String name : o.getUnstables())
		{
			checkOption(name, false, State.currToken());
		}
		
		try
		{
			applyOptions();
			
			// combine the passed globals after we've assumed all our options
			combine(predefined, g);
			
			// reset values
			parseCommaFirst = true;
		
			advance();
			switch (State.nextToken().getId())
			{
			case "{":
			case "[":
				destructuringAssignOrJsonValue(0);
				break;
			default:
				directives();
				
				if (BooleanUtils.isTrue(State.getDirective().get("use strict")))
				{
					if (!State.allowsGlobalUsd())
					{
						warning("W097", State.prevToken());
					}
				}
				
				statements(0);
			}
			
			if (!State.nextToken().getId().equals("(end)"))
			{
				quit("E041", State.currToken());
			}
			
			State.getFunct().getScope().unstack();
		}
		catch (JSHintException err)
		{
			Token nt = (State.nextToken() != null ? State.nextToken() : new Token());
			LinterWarning w = new LinterWarning();
			w.setScope("(main)");
			w.setRaw(err.getWarning().getRaw());
			w.setCode(err.getWarning().getCode());
			w.setReason(err.getWarning().getReason());
			w.setLine(err.getWarning().getLine() != 0 ? err.getWarning().getLine() : nt.getLine());
			w.setCharacter(err.getWarning().getCharacter() != 0 ? err.getWarning().getCharacter() : nt.getFrom());
			errors.add(w);
		}
		
		// Loop over the listed "internals", and check them as well.
		if (scriptScope.equals("(main)"))
		{
			lintEvalCode(internals, o, g);
		}
		
		return errors.size() == 0;
	}
	
	//API
	
	public boolean isJson()
	{
		return State.isJsonMode();
	}
	
	protected UniversalContainer getOption(String name)
	{
		return ContainerFactory.nullContainerIfFalse(State.getOption().get(name));
	}
	
	public List<LinterWarning> getErrors()
	{
		return Collections.unmodifiableList(errors);
	}
	
	public List<InternalSource> getInternals()
	{
		return Collections.unmodifiableList(internals);
	}
	
	public Set<String> getBlacklist()
	{
		return Collections.unmodifiableSet(blacklist);
	}
	
	public String getScriptScope()
	{
		return scriptScope;
	}
	
	public String getCache(String name)
	{
		return State.getCache().get(name);
	}
	
	public void setCache(String name, String value)
	{
		State.getCache().put(StringUtils.defaultString(name), StringUtils.defaultString(value));
	}
	
	public void warn(String code, int line, int chr, String... data) throws JSHintException
	{
		warningAt(code, line, chr, data);
	}
	
	public void on(String names, LexerEventListener listener)
	{
		for (String name : names.split(" ", -1))
		{
			emitter.on(name, listener);
		}
	}
	
	// Modules.
	public void addModule(JSHintModule func)
	{
		extraModules.add(func);
	}
	
	// Data summary.
	public DataSummary generateSummary()
	{
		DataSummary data = new DataSummary(State.getOption());
		
		if (errors.size() > 0)
		{
			data.setErrors(errors);
		}
		
		if (State.isJsonMode())
		{
			data.setJson(true);
		}
		
		List<ImpliedGlobal> impliedGlobals = State.getFunct().getScope().getImpliedGlobals();
		if (impliedGlobals.size() > 0)
		{
			data.setImplieds(impliedGlobals);
		}
		
		if (urls.size() > 0)
		{
			data.setUrls(urls);
		}
		
		Set<String> globals = State.getFunct().getScope().getUsedOrDefinedGlobals();
		if (globals.size() > 0)
		{
			data.setGlobals(globals);
		}
		
		for (int i = 1; i < functions.size(); i++)
		{
			Functor f = functions.get(i);
			DataSummary.Function fu = new DataSummary.Function();
			
			fu.setName(f.getName());
			fu.setParam(f.getParams());
			fu.setLine(f.getLine());
			fu.setCharacter(f.getCharacter());
			fu.setLast(f.getLast());
			fu.setLastCharacter(f.getLastCharacter());
			
			fu.setMetrics(new DataSummary.Metrics(
				f.getMetrics().complexityCount,
				f.getMetrics().arity,
				f.getMetrics().statementCount
			));
			
			data.addFunction(fu);
		}
		
		List<Token> unuseds = State.getFunct().getScope().getUnuseds();
		if (unuseds.size() > 0)
		{
			data.setUnused(unuseds);
		}
		
		if (member.size() > 0)
		{
			data.setMember(member);
		}
		
		return data;
	}
	
	private static enum FunctionType
	{
		GENERATOR,
		ARROW
	}
	
	private static enum FunctorTag
	{
		NAME, 				// "(name)"
		BREAKAGE,			// "(breakage)"
		LOOPAGE,			// "(loopage)"
		ISSTRICT,			// "(isStrict)"
		GLOBAL,				// "(global)"
		LINE,				// "(line)"
		CHARACTER,			// "(character)"
		METRICS,			// "(metrics)"
		STATEMENT,			// "(statement)"
		CONTEXT,			// "(context)"
		SCOPE,				// "(scope)"
		COMPARRAY,			// "(comparray)"
		GENERATOR,			// "(generator)"
		ARROW,				// "(arrow)"
		ASYNC,				// "(async)"
		METHOD,				// "(method)"
		HASSIMPLEPARAMS,	// "(hasSimpleParams)"
		PARAMS,				// "(params)"
		OUTERMUTABLES,		// "(outerMutables)"
		LAST,				// "(last)"
		LASTCHARACTER,		// "(lastcharacter)"
		UNUSEDOPTION,		// "(unusedOption)"
		VERB				// "(verb)"
	}
}