package org.jshint;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.utils.EventEmitter;
import org.jshint.utils.JSHintModule;
import org.jshint.utils.EventContext;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
	
	//private Object stack = null; //JSHINT_BUG: stack variable is not used anywhere can be removed
    private List<String> urls = null;
	
	private List<JSHintModule> extraModules = new ArrayList<JSHintModule>();
	private EventEmitter emitter = new EventEmitter();
	
	private Boolean checkOption(String name, Token t)
	{
		name = name.trim();
		
		if (Reg.test("^[+-]W\\d{3}$", name))
		{
			return true;
		}
		
		if (!Options.validNames.contains(name))
		{
			if (t.getType() != TokenType.JSLINT && !Options.removed.containsKey(name))
			{
				error("E001", t, name);
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
	
	private Boolean isReserved(Token token)
	{
		if (!token.isReserved())
		{
			return false;
		}
		Token.Meta meta = token.getMeta();
		
		if (meta != null && meta.isFutureReservedWord())
		{
			if (meta.isModuleOnly() && !State.getOption().test("module"))
			{
				return false;
			}
			
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
		
		return true;
	}
	
	private String supplant(String str, final LinterWarning data)
	{
		return Reg.replaceAll("\\{([^{}]*)\\}", str, new Reg.Replacer()
		{
			@Override
			public String apply(String a, List<String> groups)
			{
				String r = data.getSubstitution(groups.get(0));
				return StringUtils.defaultString(r, a);
			}
		});
	}
	
	private void combine(Map<String, Boolean> dest, Map<String, Boolean> src)
	{
		for (Entry<String, Boolean> entry: src.entrySet())
		{
			if (!blacklist.contains(entry.getKey()))
			{
				dest.put(entry.getKey(), entry.getValue());
			}
		}
	}
	
	private void processenforceall()
	{
		if (State.getOption().test("enforceall"))
		{
			for (Entry<String, Boolean> entry : Options.bool.get("enforcing").entrySet())
			{
				String enforceopt = entry.getKey();
				if (State.getOption().isUndefined(enforceopt) &&
					!(Options.noenforceall.containsKey(enforceopt) && Options.noenforceall.get(enforceopt)))
				{
					State.getOption().set(enforceopt, true);
				}
			}
			for (Entry<String, Boolean> entry : Options.bool.get("relaxing").entrySet())
			{
				String relaxopt = entry.getKey();
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
		
		if (State.getOption().get("couch").test())
		{
			combine(predefined, Vars.couch);
		}
		
		if (State.getOption().get("qunit").test())
		{
			combine(predefined, Vars.qunit);
		}

		if (State.getOption().get("rhino").test())
		{
			combine(predefined, Vars.rhino);
		}
		
		if (State.getOption().get("shelljs").test())
		{
			combine(predefined, Vars.shelljs);
			combine(predefined, Vars.node);
		}
		
		if (State.getOption().get("typed").test())
		{
			combine(predefined, Vars.typed);
		}
		
		if (State.getOption().get("phantom").test())
		{
			combine(predefined, Vars.phantom);
		}

		if (State.getOption().get("prototypejs").test())
		{
			combine(predefined, Vars.prototypejs);
		}
		
		if (State.getOption().get("node").test())
		{
			combine(predefined, Vars.node);
			combine(predefined, Vars.typed);
		}
		
		if (State.getOption().get("devel").test())
		{
			combine(predefined, Vars.devel);
		}
		
		if (State.getOption().get("dojo").test())
		{
			combine(predefined, Vars.dojo);
		}

		if (State.getOption().get("browser").test())
		{
			combine(predefined, Vars.browser);
			combine(predefined, Vars.typed);
		}
		
		if (State.getOption().get("browserify").test())
		{
			combine(predefined, Vars.browser);
			combine(predefined, Vars.typed);
			combine(predefined, Vars.browserify);
		}
		
		if (State.getOption().get("nonstandard").test())
		{
			combine(predefined, Vars.nonstandard);
		}
		
		if (State.getOption().get("jasmine").test())
		{
			combine(predefined, Vars.jasmine);
		}
		
		if (State.getOption().get("jquery").test())
		{
			combine(predefined, Vars.jquery);
		}
		
		if (State.getOption().get("mootools").test())
		{
			combine(predefined, Vars.mootools);
		}
		
		if (State.getOption().get("worker").test())
		{
			combine(predefined, Vars.worker);
		}
		
		if (State.getOption().get("wsh").test())
		{
			combine(predefined, Vars.wsh);
		}
		if (State.getOption().get("yui").test())
			
		{
			combine(predefined, Vars.yui);
		}
		
		if (State.getOption().get("mocha").test())
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
		
		if (Reg.test("^W\\d{3}$", code))
		{
			if (State.getIgnored().test(code))
				return null;
			
			msg = Messages.warnings.get(code);
		}
		else if (Reg.test("E\\d{3}", code))
		{
			msg = Messages.errors.get(code);
		}
		else if (Reg.test("I\\d{3}", code))
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
		internals.add(new InternalSource("(internal)", elem, token, token.getValue().replaceAll("([^\\\\])(\\\\*)\\2\\\\n", "$1\\n")));
	}
	
	/**
	 * Process an inline linting directive.
	 * 
	 * @param directiveToken the directive-bearing comment token
	 * @param previous the token that preceeds the directive
	 */
	private void lintingDirective(Token directiveToken, Token previous)
	{
		String[] body = directiveToken.getBody().trim().split("\\s*,\\s*");
		
		Map<String, Boolean> predef = new HashMap<String, Boolean>();
		
		if (directiveToken.getType() == TokenType.FALLS_THROUGH)
		{
			previous.setCaseFallsThrough(true);
			return;
		}
		
		if (directiveToken.getType() == TokenType.GLOBALS)
		{
			for (int idx = 0; idx < body.length; idx++)
			{
				String[] g = body[idx].split(":");
				String key = g.length > 0 ? g[0].trim() : "";
				String val = g.length > 1 ? g[1].trim() : "";
				
				if (key.equals("-") || key.length() == 0)
				{
					// Ignore trailing comma
					if (idx > 0 && idx == body.length - 1)
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
			
			for (Entry<String, Boolean> entry : predef.entrySet())
			{
				declared.put(entry.getKey(), directiveToken);
			}
		}
		
		if (directiveToken.getType() == TokenType.EXPORTED)
		{
			for (int idx = 0; idx < body.length; idx++)
			{
				String e = body[idx];
				if (StringUtils.isEmpty(e))
				{
					// Ignore trailing comma
					if (idx > 0 && idx == body.length - 1)
					{
						continue;
					}
					error("E002", directiveToken);
					continue;
				}
				
				State.getFunct().getScope().addExported(e);
			}
		}
		
		if (directiveToken.getType() == TokenType.MEMBERS)
		{
			if (membersOnly == null) membersOnly = new HashMap<String, Boolean>();
			
			for (String m : body)
			{
				char ch1 = m.charAt(0);
				char ch2 = m.charAt(m.length()-1);
				
				if (ch1 == ch2 && (ch1 == '"' || ch1 == '\''))
				{
					m = m.substring(1, m.length()-1).replace("\\\"", "\"");
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
		
		if (directiveToken.getType() == TokenType.JSHINT || directiveToken.getType() == TokenType.JSLINT)
		{
			for (String g : body)
			{
				String[] gg = g.split(":");
				String key = gg.length > 0 ? gg[0].trim() : "";
				String val = gg.length > 1 ? gg[1].trim() : "";
				
				if (!checkOption(key, directiveToken))
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
					case "2015":
						State.getOption().set("moz", false);
						State.getOption().set("esversion", Integer.parseInt(val));
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
				
				if (Reg.test("^([+-])(W\\d{3})$", key))
				{
					// ignore for -W..., unignore for +W...
					State.getIgnored().set(key.substring(1), key.startsWith("-"));
					continue;
				}
				
				if (val.equals("true") || val.equals("false"))
				{
					if (directiveToken.getType() == TokenType.JSLINT)
					{
						String tn = Options.renamed.get(key);
						if (tn == null) tn = key;
						State.getOption().set(tn, val.equals("true"));
						
						if (Options.inverted.get(tn) != null)
						{
							State.getOption().set(tn, !State.getOption().get(tn).test());
						}
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
	
	// Produce the next token. It looks for programming errors.
	private void advance()
	{
		advance(null, null);
	}
	private void advance(String id)
	{
		advance(id, null);
	}
	private void advance(String id, Token t)
	{
		switch (State.currToken().getId())
		{
		case "(number)":
			if (State.nextToken().getId().equals("."))
			{
				warning("W005", State.currToken());
			}
			break;
		case "-":
			if (State.nextToken().getId().equals("-") || State.nextToken().getId().equals("--"))
			{
				warning("W006");
			}
			break;
		case "+":
			if (State.nextToken().getId().equals("+") || State.nextToken().getId().equals("++"))
			{
				warning("W007");
			}
			break;
		}
		
		if (StringUtils.isNotEmpty(id) && !State.nextToken().getId().equals(id))
		{
			if (t != null)
			{
				if (State.nextToken().getId().equals("(end)"))
				{
					error("E019", t, t.getId());
				}
				else
				{
					error("E020", State.nextToken(), id, t.getId(), String.valueOf(t.getLine()), State.nextToken().getValue());
				}
			}
			else if (State.nextToken().getType() != TokenType.IDENTIFIER || !State.nextToken().getValue().equals(id))
			{
				warning("W116", State.nextToken(), id, State.nextToken().getValue());
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
		return token.getFirstToken() != null || token.getRight() != null || token.getLeft() != null || token.getId().equals("yield");
	}
	
	private boolean isEndOfExpr()
	{
		return isEndOfExpr(State.currToken(), State.nextToken());
	}
	
	private boolean isEndOfExpr(Token curr, Token next)
	{
		if (next.getId().equals(";") || next.getId().equals("}") || next.getId().equals(":"))
		{
			return true;
		}
		if (next.isInfix() == curr.isInfix() || curr.getLtBoundary() == LtBoundaryType.AFTER ||
			next.getLtBoundary() == LtBoundaryType.BEFORE)
		{
			return curr.getLine() != startLine(next);
		}
		return false;
	}
	
	// This is the heart of JSHINT, the Pratt parser. In addition to parsing, it
	// is looking for ad hoc lint patterns. We add .fud to Pratt's model, which is
	// like .nud except that it is only used on the first token of a statement.
	// Having .fud makes it much easier to define statement-oriented languages like
	// JavaScript. I retained Pratt's nomenclature.

	// .nud  Null denotation
	// .fud  First null denotation
	// .led  Left denotation
	//  lbp  Left binding power
	//  rbp  Right binding power

	// They are elements of the parsing method called Top Down Operator Precedence.
	
	private Token expression(int rbp)
	{
		return expression(rbp, null);
	}
	
	private Token expression(int rbp, String initial)
	{
		Token left = null;
		boolean isArray = false;
		boolean isObject = false;
		boolean isLetExpr = false;
		
		State.getNameStack().push();
		
		// if current expression is a let expression
		if (StringUtils.isEmpty(initial) && State.nextToken().getValue().equals("let") && peek(0).getValue().equals("("))
		{
			if (!State.inMoz())
			{
				warning("W118", State.nextToken(), "let expressions");
			}
			isLetExpr = true;
			// create a new block scope we use only for the current expression
			State.getFunct().getScope().stack();
			advance("let");
			advance("(");
			State.prevToken().fud(ContainerFactory.undefinedContainer());
			advance(")");
		}
		
		if (State.nextToken().getId().equals("(end)"))
			error("E006", State.currToken());
		
		boolean isDangerous =
				State.getOption().test("asi") &&
				State.prevToken().getLine() != startLine(State.currToken()) &&
				(State.prevToken().getId().equals("]") || State.prevToken().getId().equals(")")) &&
				(State.currToken().getId().equals("[") || State.currToken().getId().equals("("));
		
		if (isDangerous)
			warning("W014", State.currToken(), State.currToken().getId());
		
		advance();
		
		if (StringUtils.isNotEmpty(initial))
		{
			State.getFunct().setVerb(State.currToken().getValue());
			State.currToken().setBeginsStmt(true);
		}
		
		if (StringUtils.equals(initial, "true") && State.currToken().getFud() != null)
		{
			left = State.currToken().fud(ContainerFactory.undefinedContainer());
		}
		else
		{
			if (State.currToken().getNud() != null)
			{
				left = State.currToken().nud(rbp);
			}
			else
			{
				error("E030", State.currToken(), State.currToken().getId());
			}
			
			while (rbp < State.nextToken().getLbp() && !isEndOfExpr())
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
					left = State.currToken().led(left);
				}
				else
				{
					error("E033", State.currToken(), State.currToken().getId());
				}
			}
		}
		
		if (isLetExpr)
		{
			State.getFunct().getScope().unstack();
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
		if (left == null) left = State.currToken();
		if (right == null) right = State.nextToken();
		if (!State.getOption().get("laxbreak").test() && left.getLine() != startLine(right))
		{
			warning("W014", right, right.getValue());
		}
	}
	
	private void nolinebreak(Token t)
	{
		if (t == null) t = State.currToken();
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
		
		if (State.nextToken().getType() == TokenType.PUNCTUATOR)
		{
			switch (State.nextToken().getValue())
			{
			case "}":
			case "]":
			case ",":
				if (allowTrailing)
				{
					return true;
				}
			case ")":
				error("E024", State.nextToken(), State.nextToken().getValue());
				return false;
			}
		}
		return true;
	}
	
	// Functional constructors for making the symbols that will be inherited by
	// tokens.
	
	private Token symbol(String s, int p)
	{
		Token x = State.getSyntax().get(s);
		if (x == null)
		{
			x = new Token(s, p, s);
			State.getSyntax().put(s, x);
		}
		return x;
	}

	private Token delim(String s)
	{
		Token x = symbol(s, 0);
		x.setDelim(true);
		return x;
	}

	private Token stmt(String s, Token.FudFunction f)
	{
		Token x = delim(s);
		x.setIdentifier(true);
		x.setReserved(true);
		x.setFud(f); 
		return x;
	}

	private Token blockstmt(String s, Token.FudFunction f)
	{
		Token x = stmt(s, f);
		x.setBlock(true);
		return x;
	}

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
	
	private Token prefix(String s)
	{
		return prefix(s, new Token.NudFunction()
		{	
			@Override
			public Token apply(Token _this, int rbp)
			{
				_this.setArity(TokenArityType.UNARY);
				_this.setRight(expression(150));
				
				if (_this.getId().equals("++") || _this.getId().equals("--"))
				{
					if (State.getOption().get("plusplus").test())
					{
						warning("W016", _this, _this.getId());
					}
					else if (_this.getRight() != null && (!_this.getRight().isIdentifier() || isReserved(_this.getRight())) &&
							!_this.getRight().getId().equals(".") && !_this.getRight().getId().equals("["))
					{
						warning("W017", _this);
					}
					
					if (_this.getRight() != null && _this.getRight().isMetaProperty())
					{
						error("E031", _this);
					}
					// detect increment/decrement of a const
					// in the case of a.b, right will be the "." punctuator
					else if (_this.getRight() != null && _this.getRight().isIdentifier())
					{
						State.getFunct().getScope().getBlock().modify(_this.getRight().getValue(), _this);
					}
				}
				
				return _this;
			}
		});
	}
	
	private Token prefix(String s, String f)
	{
		return prefix(s);
	}
	
	private Token prefix(String s, Token.NudFunction f)
	{
		Token x = symbol(s, 150);
		reserveName(x);
		
		x.setNud(f);
		
		return x;
	}

	private Token type(TokenType s, Token.NudFunction f)
	{
		Token x = delim(s.toString());
		x.setType(s);
		x.setNud(f);
		return x;
	}

	private Token reserve(TokenType name)
	{
		return reserve(name, null);
	}
	private Token reserve(TokenType name, Token.NudFunction func)
	{
		Token x = type(name, func);
		x.setIdentifier(true);
		x.setReserved(true);
		return x;
	}

	private Token futureReservedWord(TokenType name)
	{
		return futureReservedWord(name, null);
	}
	
	private Token futureReservedWord(TokenType name, Token.Meta meta)
	{
		Token x = type(name, (meta != null && meta.getNud() != null) ? meta.getNud() : new Token.NudFunction()
			{
				@Override
				public Token apply(Token _this, int rbp)
				{
					return _this;
				}
			});
		
		if (meta == null) meta = new Token.Meta();
		meta.setFutureReservedWord(true);
		
		x.setValue(name.toString());
		x.setIdentifier(true);
		x.setReserved(true);
		x.setMeta(meta);
		
		return x;
	}

	private Token reservevar(TokenType s)
	{
		return reservevar(s, null);
	}
	
	private Token reservevar(TokenType s, final Token.NudInnerFunction v)
	{
		return reserve(s, new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				if (v != null)
				{
					v.apply(_this);
				}
				return _this;
			}	
		});
	}
	
	private Token infix(String s, String f, int p)
	{
		return infix(s, null, p, false);
	}
	
	private Token infix(String s, Token.LedInnerFunction f, int p)
	{
		return infix(s, f, p, false);
	}
	
	private Token infix(final String s, final Token.LedInnerFunction f, final int p, final boolean w)
	{
		Token x = symbol(s, p);
		reserveName(x);
		x.setInfix(true);
		x.setLed(new Token.LedFunction()
		{
			@Override
			public Token apply(Token _this, Token left)
			{
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
					return f.apply(_this, left, _this);
				}
				else
				{
					_this.setLeft(left);
					_this.setRight(expression(p)); 
					return _this;
				}
			}
			
		});
		
		return x;
	}

	private Token application(String s)
	{
		Token x = symbol(s, 42);
		
		x.setInfix(true);
		x.setLed(new Token.LedFunction()
		{
			@Override
			public Token apply(Token _this, Token left)
			{
				nobreaknonadjacent(State.prevToken(), State.currToken());
				
				_this.setLeft(left);
				_this.setRight(new Token(doFunction(FunctionType.ARROW, left)));
				return _this;
			}
		});
		return x;
	}

	private Token relation(String s)
	{
		return relation(s, null);
	}
	
	private Token relation(String s, final Token.LedInnerFunction f)
	{
		Token x = symbol(s, 100);
		
		x.setInfix(true);
		x.setLed(new Token.LedFunction()
		{
			@Override
			public Token apply(Token _this, Token left)
			{
				nobreaknonadjacent(State.prevToken(), State.currToken());
				_this.setLeft(left);
				_this.setRight(expression(100));
				Token right = _this.getRight();
				if (isIdentifier(left, "NaN") || isIdentifier(right, "NaN"))
				{
					warning("W019", _this);
				}
				else if (f != null)
				{
					f.apply(_this, left, right);
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
			}
		});
		
		return x;
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
	
	// Checks whether the 'typeof' operator is used with the correct
	// value. For docs on 'typeof' see:
	// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/typeof
	private boolean isTypoTypeof(Token left, Token right)
	{
		if (State.getOption().test("notypeof"))
			return false;
		
		if (left == null || right == null)
			return false;
		
		List<String> values = State.inES6() ? typeofValues.get("es6") : typeofValues.get("es3");
		
		if (right.getType() == TokenType.IDENTIFIER && right.getValue().equals("typeof") && left.getType() == TokenType.STRING)
			return !values.contains(left.getValue());
		
		return false;
	}
	
	private boolean isGlobalEval(Token left) //JSHINT_BUG: unused variables here
	{
		boolean isGlobal = false;
		
		// permit methods to refer to an "eval" key in their own context
		if (left.getType() == TokenType.THIS && State.getFunct().getContext() == null)
		{
			isGlobal = true;
		}
		
		// permit use of "eval" members of objects
		else if (left.getType() == TokenType.IDENTIFIER)
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
	
	private String findNativePrototype(Token left)
	{
		Token prototype = walkPrototype(left);
		if (prototype != null) return walkNative(prototype);
		return null;
	}
	
	/**
	 * Checks the left hand side of an assignment for issues, returns if ok
	 * @param {token} left - the left hand side of the assignment
	 * @param {token=} assignToken - the token for the assignment, used for reporting
	 * @param {object=} options - optional object
	 * @param {boolean} options.allowDestructuring - whether to allow destructuting binding
	 * @returns {boolean} Whether the left hand side is OK
	 */
	private boolean checkLeftSideAssign(Token left, Token assignToken, boolean allowDestructuring)
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
			// reassign also calls modify
		    // but we are specific in order to catch function re-assignment
		    // and globals re-assignment
			State.getFunct().getScope().getBlock().reassign(left.getValue(), left);
		}
		
		if (left.getId().equals("."))
		{
			if (left.getLeft() == null || left.getLeft().getValue().equals("arguments") && !State.isStrict())
			{
				warning("E031", assignToken);
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
					warning("E031", assignToken);
				}
			}
			
			if (left.getId().equals("["))
			{
				State.getNameStack().set(left.getRight());
			}
			
			return true;
		}
		else if (left.isIdentifier() && !isReserved(left) && !left.isMetaProperty())
		{
			if (StringUtils.defaultString(State.getFunct().getScope().labeltype(left.getValue())).equals("exception"))
			{
				warning("W022", left);
			}
			State.getNameStack().set(left);
			return true;
		}
		
		if (left == State.getSyntax().get("function"))
		{
			warning("W023", State.currToken());
		}
		else
		{
			error("E031", assignToken);
		}
		
		return false;
	}

	private Token assignop(String s, String f, int p)
	{
		return assignop(s, new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				that.setLeft(left);
				
				checkLeftSideAssign(left, that, true);
				
				that.setRight(expression(10));
				
				return that;
			}
		}, p);
	}
	
	private Token assignop(String s, Token.LedInnerFunction f, int p)
	{
		Token x = infix(s, f, p);
		x.setExps(true);
		x.setAssign(true);
		return x;
	}

	private Token bitwise(String s, String f, final int p)
	{
		return bitwise(s, new Token.LedFunction()
		{
			@Override
			public Token apply(Token _this, Token left) throws JSHintException
			{
				if (State.getOption().test("bitwise"))
				{
					warning("W016", _this, _this.getId());
				}
				_this.setLeft(left);
				_this.setRight(expression(p));
				return _this;
			}
		}, p);
	}
	
	private Token bitwise(String s, Token.LedFunction f, int p)
	{
		Token x = symbol(s, p);
		reserveName(x);
		x.setInfix(true);
		x.setLed(f);
		return x;
	}

	private Token bitwiseassignop(String s)
	{
		return assignop(s, new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				if (State.getOption().test("bitwise"))
				{
					warning("W016", that, that.getId());
				}
				
				checkLeftSideAssign(left, that, false);
				
				that.setRight(expression(10));
				
				return that;
			}
		}, 20);
	}

	private Token suffix(String s)
	{
		Token x = symbol(s, 150);
		x.setLed(new Token.LedFunction()
		{
			@Override
			public Token apply(Token _this, Token left) throws JSHintException
			{
				// this = suffix e.g. "++" punctuator
				// left = symbol operated e.g. "a" identifier or "a.b" punctuator
				if (State.getOption().test("plusplus"))
				{
					warning("W016", _this, _this.getId());
				}
				else if ((!left.isIdentifier() || isReserved(left)) && !left.getId().equals(".") && !left.getId().equals("["))
				{
					warning("W017", _this);
				}
				
				if (left.isMetaProperty())
				{
					error("E031", _this);
				}
				// detect increment/decrement of a const
				// in the case of a.b, left will be the "." punctuator
				else if (left != null && left.isIdentifier())
				{
					State.getFunct().getScope().getBlock().modify(left.getValue(), left);
				}
				
				_this.setLeft(left);
				return _this;
			}
		});
		return x;
	}

	// fnparam means that this identifier is being defined as a function
	// argument (see identifier())
	// prop means that this identifier is that of an object property
	private String optionalidentifier()
	{
		return optionalidentifier(false, false, false);
	}
	
	private String optionalidentifier(boolean fnparam, boolean prop, boolean preserve)
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
		
		if (!isReserved(curr))
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
		
		if (fnparam && val.equals("undefined"))
		{
			return val;
		}
		
		warning("W024", State.currToken(), State.currToken().getId());
		return val;
	}

	// fnparam means that this identifier is being defined as a function
	// argument
	// prop means that this identifier is that of an object property
	private String identifier()
	{
		return identifier(false, false);
	}
	
	private String identifier(boolean fnparam)
	{
		return identifier(fnparam, false);
	}
	
	private String identifier(boolean fnparam, boolean prop)
	{
		String i = optionalidentifier(fnparam, prop, false);
		if (i != null && !i.isEmpty())
		{
			return i;
		}
		
		// parameter destructuring with rest operator
		if (State.nextToken().getValue().equals("..."))
		{
			if (!State.inES6(true))
			{
				warning("W119", State.nextToken(), "spread/rest operator", "6");
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
			
			if (!State.nextToken().isIdentifier())
			{
				warning("E024", State.currToken(), State.nextToken().getId());
				return null;
			}
			
			return identifier(fnparam, prop);
		}
		else
		{
			error("E030", State.nextToken(), State.nextToken().getValue());
			
			// The token should be consumed after a warning is issued so the parser
			// can continue as though an identifier were found. The semicolon token
			// should not be consumed in this way so that the parser interprets it as
			// a statement delimeter;
			if (!State.nextToken().getId().equals(";"))
			{
				advance();
			}
		}

		return null;
	}

	private void reachable(Token controlToken)
	{
		int i = 0;
		Token t = null;
		
		if (!State.nextToken().getId().equals(";") || controlToken.isInBracelessBlock())
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
				// If this is the last statement in a block that ends on
				// the same line *and* option lastsemic is on, ignore the warning.
				// Otherwise, complain about missing semicolon.
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

	private Token statement()
	{
		int i = indent;
		Token t = State.nextToken();
		boolean hasOwnScope = false;
		
		if (t.getId().equals(";"))
		{
			advance(";");
			return null;
		}
		
		// Is this a labelled statement?
		boolean res = isReserved(t);
		
		// We're being more tolerant here: if someone uses
		// a FutureReservedWord as a label, we warn but proceed
		// anyway.
		if (res && t.getMeta() != null && t.getMeta().isFutureReservedWord() && peek().getId().equals(":"))
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
			
			//State.nextToken().label = t.value; //JSHINT_BUG: label is not used anywhere, can be removed
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
			block(true, true, false, false, iscase);
			
			if (hasOwnScope)
			{
				State.getFunct().getScope().unstack();
			}
			
			return null;
		}
		
		// Parse the statement.
		Token r = expression(0, "true");
		
		if (r != null && !(r.isIdentifier() && r.getValue().equals("function")) &&
			!(r.getType() == TokenType.PUNCTUATOR && r.getLeft() != null &&
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

	private List<Token> statements()
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
				a.add(statement());
			}
		}

		return a;
	}

	/*
	 * read all directives
	 */
	private void directives()
	{
		Token current = State.nextToken();
		
		while (State.nextToken().getId().equals("(string)"))
		{
			Token next = peekIgnoreEOL();
			if (!isEndOfExpr(current, next))
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
			
			// there's no directive negation, so always set to true
			State.getDirective().put(directive, true);
			
			parseFinalSemicolon(current);
		}
		
		if (State.isStrict())
		{
			State.getOption().set("undef", true);
		}
	}
	
	/*
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * ordinary   - true for everything but function bodies and try blocks.
	 * stmt       - true if block can be a single statement (e.g. in if/for/while).
	 * isfunc     - true if block is a function body
	 * isfatarrow - true if its a body of a fat arrow function
	 * iscase     - true if block is a switch case block
	 */
	private List<Token> block(boolean ordinary)
	{
		return block(ordinary, false, false, false, false);
	}
	
	private List<Token> block(boolean ordinary, boolean stmt)
	{
		return block(ordinary, stmt, false, false, false);
	}
	
	private List<Token> block(boolean ordinary, boolean stmt, boolean isfunc, boolean isfatarrow)
	{
		return block(ordinary, stmt, isfunc, isfatarrow, false);
	}
	
	private List<Token> block(boolean ordinary, boolean stmt, boolean isfunc, boolean isfatarrow, boolean iscase)
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
			State.getFunct().setNoblockscopedvar(false);
			
			//int line = State.currToken().getLine(); //JSHINT_BUG: variable line can be removed, since it's not used anywhere
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
					
					if (State.getOption().test("strict") && State.getFunct().getContext().isGlobal())
					{
						if (BooleanUtils.isNotTrue(m.get("use strict")) && !State.isStrict())
						{
							warning("E007");
						}
					}
				}
				
				a = statements();
				
				metrics.statementCount += a.size();
				
				indent -= State.getOption().asInt("indent");
			}
			
			advance("}", t);
			
			if (isfunc)
			{
				State.getFunct().getScope().validateParams();
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
				
				m = new HashMap<String, Boolean>();
				if (stmt && !isfatarrow && !State.inMoz())
				{
					error("W118", State.currToken(), "function closure expressions");
				}
				
				if (!stmt)
				{
					for (String d : State.getDirective().keySet())
					{
						m.put(d, State.getDirective().get(d));
					}
				}
				expression(10);
				
				if (State.getOption().test("strict") && State.getFunct().getContext().isGlobal())
				{
					if (BooleanUtils.isNotTrue(m.get("use strict")) && !State.isStrict())
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
			// check to avoid let declaration not within a block
			// though is fine inside for loop initializer section
			State.getFunct().setNoblockscopedvar(!State.nextToken().getId().equals("for"));
			State.getFunct().getScope().stack();
			
			if (!stmt || State.getOption().test("curly"))
			{
				warning("W116", State.nextToken(), "{", State.nextToken().getValue());
			}
			
			State.nextToken().setInBracelessBlock(true);
			indent += State.getOption().asInt("indent");
			// test indentation only if statement is in new line
			a.add(statement());
			indent -= State.getOption().asInt("indent");
			
			State.getFunct().getScope().unstack();
			State.getFunct().setNoblockscopedvar(false);
		}
		
		// Don't clear and let it propagate out if it is "break", "return" or similar in switch case
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
		type(TokenType.NUMBER, new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				return _this;
			}
		});
		
		type(TokenType.STRING, new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				return _this;
			}
		});
		
		Token identifier = new Token();
		State.getSyntax().put("(identifier)", identifier);
		identifier.setType(TokenType.IDENTIFIER);
		identifier.setLbp(0);
		identifier.setIdentifier(true);
		identifier.setNud(new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
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
			}
		});
		identifier.setLed(new Token.LedFunction()
		{
			public Token apply(Token _this, Token t)
			{
				error("E033", State.nextToken(), State.nextToken().getValue());
				return null;
			}
		});
		
		Token template = new Token();
		State.getSyntax().put("(template)", template);
		template.setType(TokenType.TEMPLATE);
		template.setLbp(155);
		template.setIdentifier(false);
		template.setTemplate(true);
		template.setNud(new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				return doTemplateLiteral(_this, rbp);
			}
		});
		template.setLed(new Token.LedFunction()
		{
			@Override
			public Token apply(Token _this, Token t)
			{
				return doTemplateLiteral(_this, t);
			}
		});
		template.setNoSubst(false);
		
		Token templateMiddle = new Token();
		State.getSyntax().put("(template middle)", templateMiddle);
		templateMiddle.setType(TokenType.TEMPLATEMIDDLE);
		templateMiddle.setLbp(0);
		templateMiddle.setIdentifier(false);
		templateMiddle.setTemplate(true);
		//templateMiddle.middle = true; //JSHINT_BUG: middle is not used anywhere, can be removed
		templateMiddle.setNoSubst(false);
		
		Token templateTail = new Token();
		State.getSyntax().put("(template tail)", templateTail);
		templateTail.setType(TokenType.TEMPLATETAIL);
		templateTail.setLbp(0);
		templateTail.setIdentifier(false);
		templateTail.setTemplate(true);
		templateTail.setTail(true);
		templateTail.setNoSubst(false);
		
		Token noSubstTemplate = new Token();
		State.getSyntax().put("(no subst template)", noSubstTemplate);
		noSubstTemplate.setType(TokenType.TEMPLATE);
		noSubstTemplate.setLbp(155);
		noSubstTemplate.setIdentifier(false);
		noSubstTemplate.setTemplate(true);
		noSubstTemplate.setNud(new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				return doTemplateLiteral(_this, rbp);
			}
		});
		noSubstTemplate.setLed(new Token.LedFunction()
		{
			@Override
			public Token apply(Token _this, Token t)
			{
				return doTemplateLiteral(_this, t);
			}
		});
		noSubstTemplate.setNoSubst(true);
		noSubstTemplate.setTail(true); // mark as tail, since it's always the last component
		
		type(TokenType.REGEXP, new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				return _this;
			}
		});
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
		
		reserve(TokenType.ELSE);
		reserve(TokenType.CASE).setReach(true);
		reserve(TokenType.CATCH);
		reserve(TokenType.DEFAULT).setReach(true);
		reserve(TokenType.FINALLY);
		reservevar(TokenType.ARGUMENTS, new Token.NudInnerFunction()
		{
			@Override
			public void apply(Token x) throws JSHintException
			{
				if (State.isStrict() && State.getFunct().isGlobal())
				{
					warning("E008", x);
				}
			}
		});
		reservevar(TokenType.EVAL);
		reservevar(TokenType.FALSE);
		reservevar(TokenType.INFINITY);
		reservevar(TokenType.NULL);
		reservevar(TokenType.THIS, new Token.NudInnerFunction()
		{
			@Override
			public void apply(Token x) throws JSHintException
			{
				if (State.isStrict() && !isMethod() && 
					!State.getOption().test("validthis") && ((State.getFunct().getStatement() != null &&
					State.getFunct().getName().charAt(0) > 'Z') || State.getFunct().isGlobal()))
				{
					warning("W040", x);
				}
			}
		});
		reservevar(TokenType.TRUE);
		reservevar(TokenType.UNDEFINED);
		
		assignop("=", "assign", 20);
		assignop("+=", "assignadd", 20);
		assignop("-=", "assignsub", 20);
		assignop("*=", "assignmult", 20);
		assignop("/=", "assigndiv", 20).setNud(new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				error("E014");
				return null;
			}
		});
		assignop("%=", "assignmod", 20);
		
		bitwiseassignop("&=");
		bitwiseassignop("|=");
		bitwiseassignop("^=");
		bitwiseassignop("<<=");
		bitwiseassignop(">>=");
		bitwiseassignop(">>>=");
		infix(",", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
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
					Token expr = expression(10);
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
			}
		}, 10, true);
		
		infix("?", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				increaseComplexityCount();
				that.setLeft(left);
				that.setRight(expression(10));
				advance(":");
				expression(10); // that.elsee = expression(10); JSHINT_BUG: "else" property is not used anywhere, can be removed
				return that;
			}
		}, 30);
		
		infix("||", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				increaseComplexityCount();
				that.setLeft(left);
				that.setRight(expression(40));
				return that;
			}
		}, 40);
		infix("&&", "and", 50);
		bitwise("|", "bitor", 70);
		bitwise("^", "bitxor", 80);
		bitwise("&", "bitand", 90);
		relation("==", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token right) throws JSHintException
			{
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
			}
		});
		relation("===", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token right) throws JSHintException
			{
				if (isTypoTypeof(right, left))
				{
					warning("W122", _this, right.getValue());
				}
				else if (isTypoTypeof(left, right))
				{
					warning("W122", _this, left.getValue());
				}
				return _this;
			}
		});
		relation("!=", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token right) throws JSHintException
			{
				boolean eqnull = State.getOption().test("eqnull") &&
						((left != null && left.getValue().equals("null")) || (right != null && right.getValue().equals("null")));
				
				if (!eqnull && State.getOption().get("eqeqeq").test())
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
			}
		});
		relation("!==", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token right) throws JSHintException
			{
				if (isTypoTypeof(right, left))
				{
					warning("W122", _this, right.getValue());
				}
				else if (isTypoTypeof(left, right))
				{
					warning("W122", _this, left.getValue());
				}
				return _this;
			}
		});
		relation("<");
		relation(">");
		relation("<=");
		relation(">=");
		bitwise("<<", "shiftleft", 120);
		bitwise(">>", "shiftright", 120);
		bitwise(">>>", "shiftrightunsigned", 120);
		infix("in", "in", 120);
		infix("instanceof", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token token) throws JSHintException
			{
				Token right;
				ScopeManager scope = State.getFunct().getScope();
				token.setLeft(left);
				token.setRight(right = expression(130));
				
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
					right.getArity() == TokenArityType.UNARY ||
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
			}
		}, 120);
		infix("+", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				Token right = null;
				that.setLeft(left);
				that.setRight(right = expression(130));
				
				if (left != null && right != null && left.getId().equals("(string)") && right.getId().equals("(string)"))
				{
					left.setValue(left.getValue() + right.getValue());
					left.setCharacter(right.getCharacter());
					if (!State.getOption().get("scripturl").test() && Reg.test(Reg.JAVASCRIPT_URL, left.getValue()))
					{
						warning("W050", left);
					}
					return left;
				}
				
				return that;
			}
		}, 130);
		prefix("+", "num");
		prefix("+++", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				warning("W007");
				_this.setArity(TokenArityType.UNARY);
				_this.setRight(expression(150));
				return _this;
			}
		});
		infix("+++", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token right) throws JSHintException
			{
				warning("W007");
				_this.setLeft(left);
				_this.setRight(expression(130));
				return _this;
			}
		}, 130);
		infix("-", "sub", 130);
		prefix("-", "neg");
		prefix("---", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				warning("W006");
				_this.setArity(TokenArityType.UNARY);
				_this.setRight(expression(150));
				return _this;
			}
		});
		infix("---", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token right) throws JSHintException
			{
				warning("W006");
				_this.setLeft(left);
				_this.setRight(expression(130));
				return _this;
			}
		}, 130);
		infix("*", "mult", 140);
		infix("/", "div", 140);
		infix("%", "mod", 140);

		suffix("++");
		prefix("++", "preinc");
		State.getSyntax().get("++").setExps(true);
		State.getSyntax().get("++").setLtBoundary(LtBoundaryType.BEFORE);

		suffix("--");
		prefix("--", "predec");
		State.getSyntax().get("--").setExps(true);
		State.getSyntax().get("--").setLtBoundary(LtBoundaryType.BEFORE);
		
		prefix("delete", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				Token p = expression(10);
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
			}
		}).setExps(true);
		
		prefix("~", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				if (State.getOption().test("bitwise"))
				{
					warning("W016", _this, "~");
				}
				_this.setArity(TokenArityType.UNARY);
				_this.setRight(expression(150));
				return _this;
			}
		});
		
		prefix("...", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				if (!State.inES6(true))
				{
					warning("W119", _this, "spread/rest operator", "6");
				}
				
				// JSHINT_TODO: Allow all AssignmentExpression
			    // once parsing permits.
			    //
			    // How to handle eg. number, boolean when the built-in
			    // prototype of may have an @@iterator definition?
			    //
			    // Number.prototype[Symbol.iterator] = function * () {
			    //   yield this.valueOf();
			    // };
			    //
			    // var a = [ ...1 ];
			    // console.log(a); // [1];
			    //
			    // for (let n of [...10]) {
			    //    console.log(n);
			    // }
			    // // 10
			    //
			    //
			    // Boolean.prototype[Symbol.iterator] = function * () {
			    //   yield this.valueOf();
			    // };
			    //
			    // var a = [ ...true ];
			    // console.log(a); // [true];
			    //
			    // for (let n of [...false]) {
			    //    console.log(n);
			    // }
			    // // false
			    //
				
				if (!State.nextToken().isIdentifier() &&
					State.nextToken().getType() != TokenType.STRING &
					!checkPunctuators(State.nextToken(), "[", "("))
				{
					error("E030", State.nextToken(), State.nextToken().getValue());
				}
				_this.setRight(expression(150));
				return _this;
			}
		});
		
		prefix("!", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				_this.setArity(TokenArityType.UNARY);
				_this.setRight(expression(150));
				
				if (_this.getRight() == null) // '!' followed by nothing? Give up.
				{
					quit("E041", _this);
				}
				
				if (bang.containsKey(_this.getRight().getId()) && bang.get(_this.getRight().getId()) == true)
				{
					warning("W018", _this, "!");
				}
				return _this;
			}
		});
		
		prefix("typeof", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				Token p = expression(150);
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
			}
		});
		prefix("new", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				Token mp = metaProperty("target");
				if (mp != null)
				{
					return mp;
				}
				
				Token c = expression(155);
				if (c != null && !c.getId().equals("function"))
				{
					if (c.isIdentifier())
					{
						//c.neww = true; //JSHINT_BUG: "new" property is not used anywhere, can be removed
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
							if (!State.getOption().get("evil").test())
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
			}
		});
		State.getSyntax().get("new").setExps(true);
		
		prefix("void").setExps(true);
		
		infix(".", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				String m = identifier(false, true);
				
				countMember(m);
				
				that.setLeft(left);
				
				// Original JSHint source code use string here, because they can, but in Java it is better to create Token
				that.setRight(new Token("", 0, m)); 
				
				if (m != null && m.equals("hasOwnProperty") && State.nextToken().getValue().equals("="))
				{
					warning("W001");
				}
				
				if (left != null && left.getValue().equals("arguments") && (m.equals("callee") || m.equals("caller")))
				{
					if (State.getOption().get("noarg").test())
						warning("W059", left, m);
					else if (State.isStrict())
						error("E008");
				}
				else if (!State.getOption().get("evil").test() && left != null && left.getValue().equals("document") && 
						(m.equals("write") || m.equals("writeln")))
				{
					warning("W060", left);
				}
				
				if (!State.getOption().get("evil").test() && (m.equals("eval") || m.equals("execScript")))
				{
					if (isGlobalEval(left))
					{
						warning("W061");
					}
				}
				
				return that;
			}
		}, 160, true);
		
		infix("(", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				if (State.getOption().get("immed").test() && left != null && !left.isImmed() && left.getId().equals("function"))
				{
					warning("W062");
				}
				
				int n = 0;
				List<Token> p = new ArrayList<Token>();
				
				if (left != null)
				{
					if (left.getType() == TokenType.IDENTIFIER)
					{
						if (Reg.test("^[A-Z]([A-Z0-9_$]*[a-z][A-Za-z0-9_$]*)?$", left.getValue()))
						{
							if ("Array Number String Boolean Date Object Error Symbol".indexOf(left.getValue()) == -1)
							{
								if (left.getValue().equals("Math"))
								{
									warning("W063", left);
								}
								else if (State.getOption().get("newcap").test())
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
						p.add(expression(10));
						n += 1;
						if (!State.nextToken().getId().equals(","))
						{
							break;
						}
						parseComma();
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
								 (left.getRight() != null && (left.getRight().getValue().equals("setTimeout") ||  //Original JSHint source code doesn't have a bug here, since there is place (see infix(".")) where string is written to right 
								 left.getRight().getValue().equals("setInterval"))))
						{
							warning("W066", left);
							addEvalCode(left, p.get(0));
						}
					}
					if (!left.isIdentifier() && !left.getId().equals(".") && !left.getId().equals("[") && !left.getId().equals("=>") &&
						!left.getId().equals("(") && !left.getId().equals("&&") && !left.getId().equals("||") && !left.getId().equals("?") &&
						!(State.inES6() && isFunctor(left))) //JSHINT_BUG: it's better to use function isFunctor here and move this condition in the first place
					{
						warning("W067", that);
					}
				}
				
				that.setLeft(left);
				return that;
			}
		}, 155, true).setExps(true);
		
		prefix("(", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				Token pn = State.nextToken();
				Token pn1 = null;
				int i = -1;
				Token ret = null;
				boolean triggerFnExpr = false;
				Token first = null;
				Token last = null;
				int parens = 1;
				Token opening = State.currToken();
				Token preceeding = State.prevToken();
				boolean isNecessary = !State.getOption().get("singleGroups").test();
				
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
				while (!(parens == 0 && pn1.getValue().equals(")")) && pn.getType() != TokenType.END);
				
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
					return new Token(doFunction(FunctionType.ARROW, true));
				}
				
				List<Token> exprs = new ArrayList<Token>();
				
				if (!State.nextToken().getId().equals(")"))
				{
					for (;;)
					{
						exprs.add(expression(10));
						
						if (!State.nextToken().getId().equals(","))
						{
							break;
						}
						
						if (State.getOption().get("nocomma").test())
						{
							warning("W127");
						}
						
						parseComma();
					}
				}
				
				advance(")", _this);
				if (State.getOption().get("immed").test() && exprs.size() > 0 && exprs.get(0) != null && exprs.get(0).getId().equals("function"))
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
					ret = State.getSyntax().get(",") != null ? new Token(State.getSyntax().get(",")) : new Token();
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
							(opening.isBeginsStmt() && (ret.getId().equals("{") || triggerFnExpr || isFunctor(ret))) ||
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
							(isFunctor(ret) && !isEndOfExpr()) ||
							// Used as the return value of a single-statement arrow function
							(ret.getId().equals("{") && preceeding.getId().equals("=>")) ||
							// Used to delineate an integer number literal from a dereferencing
							// punctuator (otherwise interpreted as a decimal point)
							(ret.getType() == TokenType.NUMBER &&
							checkPunctuator(pn, ".") && Reg.test("^\\d+$", ret.getValue())) ||
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
							(!isEndOfExpr() && last.getLbp() < State.nextToken().getLbp());
					}
					
					if (!isNecessary)
					{
						warning("W126", opening);
					}
					
					ret.setParen(true);
				}
				
				return ret;
			}
		});
		
		application("=>");
		
		infix("[", new Token.LedInnerFunction()
		{
			@Override
			public Token apply(Token _this, Token left, Token that) throws JSHintException
			{
				Token e = expression(10);
				Token s = null;
				
				if (e != null && e.getType() == TokenType.STRING)
				{
					if (!State.getOption().get("evil").test() && (e.getValue().equals("eval") || e.getValue().equals("execScript")))
					{
						if (isGlobalEval(left))
						{
							warning("W061");
						}
					}
					
					countMember(e.getValue());
					if (!State.getOption().get("sub").test() && Reg.test(Reg.INDENTIFIER, e.getValue()))
					{
						s = State.getSyntax().get(e.getValue());
						if (s == null || !isReserved(s))
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
			}
		}, 160, true);
		
		prefix("[", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				LookupBlockType blocktype = new LookupBlockType();
				if (blocktype.isCompArray)
				{
					if (!State.getOption().test("esnext") && !State.inMoz())
					{
						warning("W118", State.currToken(), "array comprehension");
					}
					return comprehensiveArrayExpression();
				}
				else if (blocktype.isDestAssign)
				{
					_this.setDestructAssign(destructuringPattern(true, true));
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
					
					_this.addFirstTokens(expression(10));
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
			}
		});
		
		x = delim("{");
		x.setNud(new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				String i = null;
				UniversalContainer props = ContainerFactory.nullContainer().create(); // All properties, including accessors
				
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
					_this.setDestructAssign(destructuringPattern(true, true));
					return _this;
				}
				
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
						i = propertyName(new UniversalContainer(true));
						saveProperty(props, i, State.nextToken());
						
						expression(10);
					}
					else if (!peek().getId().equals(":") && (nextVal.equals("get") || nextVal.equals("set")))
					{
						advance(nextVal);
						
						if (!State.inES5())
						{
							error("E034");
						}
						
						i = propertyName();
						
						// ES6 allows for get() {...} and set() {...} method
						// definition shorthand syntax, so we don't produce an error
						// if linting ECMAScript 6 code.
						if ((i == null || i.isEmpty()) && !State.inES6())
						{
							error("E035");
						}
						
						// We don't want to save this getter unless it's an actual getter
						// and not an ES6 concise method
						if (i != null && !i.isEmpty())
						{
							saveAccessor(nextVal, props, i, State.currToken());
						}
						
						Token t = State.nextToken();
						Functor f = doFunction();
						List<String> p = f.getParams();
						
						// Don't warn about getter/setter pairs if this is an ES6 concise method
						if (nextVal.equals("get") && StringUtils.isNotEmpty(i) && p != null)
						{
							warning("W076", t, p.get(0), i);
						}
						else if (nextVal.equals("set") && StringUtils.isNotEmpty(i) && f.getMetrics().arity != 1)
						{
							warning("W077", t, i);
						}
					}
					else
					{
						boolean isGeneratorMethod = false;
						if (State.nextToken().getValue().equals("*") && State.nextToken().getType() == TokenType.PUNCTUATOR)
						{
							if (!State.inES6())
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
							State.getNameStack().set(computedPropertyName());
						}
						else
						{
							State.getNameStack().set(State.nextToken());
							i = propertyName();
							saveProperty(props, i, State.nextToken());
							
							if (i == null) break;
						}
						
						if (State.nextToken().getValue().equals("("))
						{
							if (!State.inES6())
							{
								warning("W104", State.currToken(), "concise methods", "6");
							}
							doFunction(isGeneratorMethod ? FunctionType.GENERATOR : null);
						}
						else
						{
							advance(":");
							expression(10);
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
				
				return _this;
			}
		});
		x.setFud(new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				error("E036", State.currToken());
				return null;
			}
		});
	}
	
	private Token mozYield(Token _this)
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
				
				//JSHINT_BUG: in this place it's better to store token in a separate variable and use it instead of this.first
				Token t = expression(10);
				_this.setFirstTokens(t);
				
				if (t.getType() == TokenType.PUNCTUATOR && t.getValue().equals("=") && !t.isParen() && !State.getOption().test("boss"))
				{
					warningAt("W093", t.getLine(), t.getCharacter());
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
	
	private Token comprehensiveArrayExpression()
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
			res.setRight(expression(10));
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
		res.setLeft(expression(130));
		if (State.nextToken().getValue().equals("in") || State.nextToken().getValue().equals("of"))
		{
			advance();
		}
		else
		{
			error("E045", State.currToken());
		}
		State.getFunct().getComparray().setState("generate");
		expression(10);
		
		advance(")");
		if (State.nextToken().getValue().equals("if"))
		{
			advance("if");
			advance("(");
			State.getFunct().getComparray().setState("filter");
			expression(10); // res.filter = expression(10); JSHINT_BUG: "filter" property is not used anywhere, can be removed
			advance(")");
		}
		
		if (!reversed)
		{
			State.getFunct().getComparray().setState("use");
			res.setRight(expression(10));
		}
		
		advance("]");
		State.getFunct().getComparray().unstack();
		return res;
	}

	private boolean isMethod()
	{
		return State.getFunct().getStatement() != null && State.getFunct().getStatement().getType() == TokenType.CLASS ||
			State.getFunct().getContext() != null && State.getFunct().getContext().getVerb().equals("class");
	}

	private boolean isPropertyName(Token token)
	{
		return token.isIdentifier() || token.getId().equals("(string)") || token.getId().equals("(number)");
	}

	private String propertyName()
	{
		return propertyName(ContainerFactory.undefinedContainer());
	}
	
	private String propertyName(UniversalContainer preserveOrToken)
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
			id = optionalidentifier(false, true, preserve);
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
	 * @param {Object} [options]
	 * @param {token} [options.loneArg] The argument to the function in cases
	 * 									where it was defined using the
	 * 									single-argument shorthand.
	 * @param {bool} [options.parsedOpening] Whether the opening parenthesis has
	 * 										 already been parsed.
	 * @returns {{ arity: number, params: Array.<string>}}
	 */
	private UniversalContainer functionparams(Token loneArg, boolean parsedOpening)
	{
		List<String> paramsIds = new ArrayList<String>();
		boolean pastDefault = false;
		boolean pastRest = false;
		int arity = 0;
		
		if (loneArg != null && loneArg.isIdentifier() == true)
		{
			State.getFunct().getScope().addParam(loneArg.getValue(), loneArg);
			return ContainerFactory.createObject("arity", 1, "params", ContainerFactory.createArray(loneArg.getValue()));
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
				List<Identifier> tokens = destructuringPattern(false, false);
				for (Identifier t : tokens)
				{
					if (StringUtils.isNotEmpty(t.id))
					{
						paramsIds.add(t.id);
						currentParams.push(ContainerFactory.createArray(t.id, t.token));
					}
				}
			}
			else
			{
				if (checkPunctuator(State.nextToken(), "...")) pastRest = true;
				String ident = identifier(true);
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
					error("W138", State.currToken()); //JSHINT_BUG: typo, must be state.currToken() instead of State.currToken()ent
				}
			}
			if (State.nextToken().getId().equals("="))
			{
				if (!State.inES6())
				{
					warning("W119", State.nextToken(), "default parameters", "6");
				}
				advance("=");
				pastDefault = true;
				expression(10);
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
				parseComma();
			}
			else
			{
				advance(")", next);
				return ContainerFactory.createObject("arity", arity, "params", paramsIds);
			}
		}
	}

	class Functor
	{
		private Set<FunctorTag> tags = new HashSet<FunctorTag>();
		
		private String name = "";
		private int breakage = 0;
		private int loopage = 0;
		//private Object tokens = null; //JSHINT_BUG: property "(tokens)" is not used anywhere, can be removed
		//private Object properties = null; //JSHINT_BUG: property "(properties)" is not used anywhere, can be removed
		
		//private boolean isCatch = false; //JSHINT_BUG: property "(catch)" is not used anywhere, can be removed
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
		private List<String> params = null;
		
		private boolean isCapturing = false;
		private boolean isNoblockscopedvar = false;
		private int last = 0;
		private int lastcharacter = 0;
		private UniversalContainer unusedOption = ContainerFactory.undefinedContainer();
		private String verb = "";
		
		private Functor()
		{
			
		}
		
		private Functor(String name, Token token, Functor overwrites)
		{
			setName(name);
			setBreakage(0);
			setLoopage(0);
			
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
		
		List<String> getParams()
		{
			return params != null ? Collections.unmodifiableList(params) : null;
		}
		
		Functor setParams(List<String> params)
		{
			this.tags.add(FunctorTag.PARAMS);
			this.params = params;
			return this;
		}
		
		boolean isCapturing()
		{
			return isCapturing;
		}
		
		Functor setCapturing(boolean isCapturing)
		{
			this.tags.add(FunctorTag.ISCAPTURING);
			this.isCapturing = isCapturing;
			return this;
		}
		
		boolean isNoblockscopedvar()
		{
			return isNoblockscopedvar;
		}
		
		Functor setNoblockscopedvar(boolean isNoblockscopedvar)
		{
			this.tags.add(FunctorTag.NOBLOCKSCOPEDVAR);
			this.isNoblockscopedvar = isNoblockscopedvar;
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
		
		private void overwrite(Functor overwrites)
		{
			if (overwrites.tags.contains(FunctorTag.NAME)) setName(overwrites.name);
			if (overwrites.tags.contains(FunctorTag.BREAKAGE)) setBreakage(overwrites.breakage);
			if (overwrites.tags.contains(FunctorTag.LOOPAGE)) setLoopage(overwrites.loopage);
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
			if (overwrites.tags.contains(FunctorTag.PARAMS)) setParams(overwrites.params);
		}
	}

	private boolean isFunctor(Token token)
	{
		return token.isFunctor();
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
	
	private Token doTemplateLiteral(Token _this, int rbp)
	{
		return doTemplateLiteral(_this, null);
	}
	
	/**
	 * This function is used as both a null-denotation method *and* a
	 * left-denotation method, meaning the first parameter is overloaded.
	 */
	private Token doTemplateLiteral(Token _this, Token left)
	{
		Lexer.LexerContext ctx = _this.getContext();
		boolean noSubst = _this.isNoSubst();
		int depth = _this.getDepth();
		
		if (!noSubst)
		{
			while (!doTemplateLiteralEnd(ctx))
			{
				if (!State.nextToken().isTemplate() || State.nextToken().getDepth() > depth)
				{
					expression(0); // should probably have different rbp?
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
		t.setType(TokenType.TEMPLATE);
		t.setTag(left);
		return t;
	}
	
	private boolean doTemplateLiteralEnd(Lexer.LexerContext ctx)
	{
		if (State.currToken().isTemplate() && State.currToken().isTail() &&
			State.currToken().getContext() == ctx) return true;
		boolean complete = (State.nextToken().isTemplate() && State.nextToken().isTail() && 
							State.nextToken().getContext() == ctx);
		if (complete) advance();
		return complete || State.nextToken().isUnclosed();
	}

	private Functor doFunction()
	{
		return doFunction(null, null, null, null, false, null, false);
	}
	
	private Functor doFunction(FunctionType type)
	{
		return doFunction(null, null, type, null, false, null, false);
	}
	
	private Functor doFunction(FunctionType type, Token loneArg)
	{
		return doFunction(null, null, type, loneArg, false, null, false);
	}
	
	private Functor doFunction(FunctionType type, boolean parsedOpening)
	{
		return doFunction(null, null, type, null, parsedOpening, null, false);
	}
	
	private Functor doFunction(Token statement)
	{
		return doFunction(null, statement, null, null, false, null, false);
	}
	
	private Functor doFunction(Token statement, FunctionType type, String classExprBinding)
	{
		return doFunction(null, statement, type, null, false, classExprBinding, false);
	}
	
	private Functor doFunction(String name, FunctionType type)
	{
		return doFunction(name, null, type, null, false, null, false);
	}
	
	private Functor doFunction(String name, Token statement, FunctionType type, boolean ignoreLoopFunc)
	{
		return doFunction(name, statement, type, null, false, null, ignoreLoopFunc);
	}
	
	/**
	 * @param {Object} [options]
	 * @param {token} [options.name] The identifier belonging to the function (if		JSHINT_BUG: should be string
	 * 							  	 any)
	 * @param {boolean} [options.statement] The statement that triggered creation		JSHINT_BUG: should be token
	 * 										of the current function.
	 * @param {string} [options.type] If specified, either "generator" or "arrow"
	 * @param {token} [options.loneArg] The argument to the function in cases
	 * 									where it was defined using the
	 * 									single-argument shorthand
	 * @param {bool} [options.parsedOpening] Whether the opening parenthesis has
	 * 										 already been parsed
	 * @param {token} [options.classExprBinding] Define a function with this			JSHINT_BUG: should be string
	 * 											 identifier in the new function's
	 * 											 scope, mimicking the bahavior of
	 * 											 class expression names within
	 * 											 the body of member functions.
	 */	
	private Functor doFunction(String name, Token statement, FunctionType type, Token loneArg, boolean parsedOpening, String classExprBinding, boolean ignoreLoopFunc)
	{
		Token token = null;
		boolean isGenerator = type == FunctionType.GENERATOR;
		boolean isArrow = type == FunctionType.ARROW;
		UniversalContainer oldOption = State.getOption();
	    UniversalContainer oldIgnored = State.getIgnored();
	    
	    State.setOption(State.getOption().create());
		State.setIgnored(State.getIgnored().create());
		
		State.setFunct(new Functor(StringUtils.isNotEmpty(name) ? name : State.getNameStack().infer(), State.nextToken(), new Functor()
			.setStatement(statement)
			.setContext(State.getFunct())
			.setArrow(isArrow)
			.setGenerator(isGenerator)
		));
		
		Functor f = State.getFunct();
		token = State.currToken();
		//token.funct = State.funct; // JSHINT_BUG: Looks like this one is not used, can be removed
		
		functions.add(State.getFunct());
		
		// So that the function is available to itself and referencing itself is not
	    // seen as a closure, add the function name to a new scope, but do not
	    // test for unused (unused: false)
	    // it is a new block scope so that params can override it, it can be block scoped
	    // but declarations inside the function don't cause already declared error
		State.getFunct().getScope().stack("functionouter");
		String internallyAccessibleName = StringUtils.defaultString(name, StringUtils.defaultString(classExprBinding));
		if (StringUtils.isNotEmpty(internallyAccessibleName))
		{
			State.getFunct().getScope().getBlock().add(internallyAccessibleName,
				StringUtils.isNotEmpty(classExprBinding) ? "class" : "function", State.currToken(), false);
		}
		
		// create the param scope (params added in functionparams)
		State.getFunct().getScope().stack("functionparams");
		
		UniversalContainer paramsInfo = functionparams(loneArg, parsedOpening); 
		if (paramsInfo.test())
		{
			State.getFunct().setParams(paramsInfo.get("params").asList(String.class));
			State.getFunct().getMetrics().arity = paramsInfo.asInt("arity");
			State.getFunct().getMetrics().verifyMaxParametersPerFunction();
		}
		else
		{
			State.getFunct().getMetrics().arity = 0;
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
		
		block(false, true, true, isArrow);
		
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
			if (f.isCapturing())
			{
				warning("W083", token);
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
	 * @param {object} props Collection of property descriptors for a given
	 *                       object.
	 */
	private void checkProperties(UniversalContainer props)
	{
		// Check for lonely setters if in the ES5 mode.
		if (State.inES5())
		{
			for (String name : props.keys())
			{
				if (props.test(name) && props.get(name).test("setterToken") && !props.get(name).test("getterToken"))
				{
					warning("W078", props.get(name).<Token>valueOf("setterToken"));
				}
			}
		}
	}
	
	private Token metaProperty(String name)
	{
		if (checkPunctuator(State.nextToken(), "."))
		{
			String left = State.currToken().getId();
			advance(".");
			String id = identifier();
			State.currToken().setMetaProperty(true);
			if (!name.equals(id))
			{
				error("E057", State.prevToken(), left, id);
			}
			else
			{
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
			}
			return State.currToken();
		}
		
		return null;
	}
	
	private List<Identifier> destructuringPattern(boolean openingParsed, boolean isAssignment)
	{
		if (!State.inES6())
		{
			warning("W104", State.currToken(),
				isAssignment ? "destructuring assignment" : "destructuring binding", "6");
		}
		
		return destructuringPatternRecursive(openingParsed, isAssignment);
	}
	
	private boolean nextInnerDE(List<Identifier> identifiers, boolean isAssignment)
	{
		List<Identifier> ids;
		String ident = null;
		
		if (checkPunctuators(State.nextToken(), "[", "{"))
		{
			ids = destructuringPatternRecursive(false, isAssignment);
			for (Identifier id : ids)
			{
				identifiers.add(new Identifier(id.id, id.token));
			}
		}
		else if (checkPunctuator(State.nextToken(), ","))
		{
			identifiers.add(new Identifier(null, State.currToken()));
		}
		else if (checkPunctuator(State.nextToken(), "("))
		{
			advance("(");
			nextInnerDE(identifiers, isAssignment);
			advance(")");
		}
		else
		{
			boolean is_rest = checkPunctuator(State.nextToken(), "...");
			
			if (isAssignment)
			{
				Token assignTarget = expression(20);
				if (assignTarget != null)
				{
					checkLeftSideAssign(assignTarget, null, false);
					
					// if the target was a simple identifier, add it to the list to return
					if (assignTarget.isIdentifier())
					{
						ident = assignTarget.getValue();
					}
				}
			}
			else
			{
				ident = identifier();
			}
			if (StringUtils.isNotEmpty(ident))
			{
				identifiers.add(new Identifier(ident, State.currToken()));
			}
			return is_rest;
		}
		
		return false;
	}
	
	private List<Identifier> destructuringPatternRecursive(boolean openingParsed, boolean isAssignment)
	{
		List<Identifier> identifiers = new ArrayList<Identifier>();
		Token firstToken = openingParsed ? State.currToken() : State.nextToken();
		
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
				if (nextInnerDE(identifiers, isAssignment) && !element_after_rest &&
					checkPunctuator(State.nextToken(), ","))
				{
					warning("W130", State.nextToken());
					element_after_rest = true;
				}
				if (checkPunctuator(State.nextToken(), "="))
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
					value = expression(10);
					if (value != null && value.getType() == TokenType.UNDEFINED)
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
				//start assignmentProperty
				String stringId = null;
				if (checkPunctuator(State.nextToken(), "["))
				{
					advance("[");
					expression(10);
					advance("]");
					advance(":");
					nextInnerDE(identifiers, isAssignment);
				}
				else if (State.nextToken().getId().equals("(string)") ||
						 State.nextToken().getId().equals("(number)"))
				{
					advance();
					advance(":");
					nextInnerDE(identifiers, isAssignment);
				}
				else
				{
					// this id will either be the property name or the property name and the assigning identifier
					stringId = identifier();
					if (checkPunctuator(State.nextToken(), ":"))
					{
						advance(":");
						nextInnerDE(identifiers, isAssignment);
					}
					else if (StringUtils.isNotEmpty(stringId))
					{
						// in this case we are assigning (not declaring), so check assignment
						if (isAssignment)
						{
							checkLeftSideAssign(State.currToken(), null, false);
						}
						identifiers.add(new Identifier(stringId, State.currToken()));
					}
				}
				//end assignmentProperty
				
				if (checkPunctuator(State.nextToken(), "="))
				{
					advance("=");
					id = State.prevToken();
					value = expression(10);
					if (value != null && value.getType() == TokenType.UNDEFINED)
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
	
	private Token blockVariableStatement(String type, Token statement, UniversalContainer context)
	{
		// used for both let and const statements
		boolean prefix = context.test() && context.asBoolean("prefix");
		boolean inexport  = context.test() && context.asBoolean("inexport");
		boolean isLet = type.equals("let");
		boolean isConst = type.equals("const");
		List<Identifier> tokens;
		boolean lone = false;
		boolean letblock = false;
		
		if (!State.inES6())
		{
			warning("W104", State.currToken(), type, "6");
		}
		
		if (isLet && State.nextToken().getValue().equals("("))
		{
			if (!State.inMoz())
			{
				warning("W118", State.nextToken(), "let block");
			}
			advance("(");
			State.getFunct().getScope().stack();
			letblock = true;
		}
		else if (State.getFunct().isNoblockscopedvar())
		{
			error("E048", State.currToken(), isConst ? "Const" : "Let");
		}
		
		statement.setFirstTokens();
		for (;;)
		{
			List<Token> names = new ArrayList<Token>();
			if (State.nextToken().getValue().equals("{") || State.nextToken().getValue().equals("["))
			{
				tokens = destructuringPattern(false, false);
				lone = false;
			}
			else
			{
				tokens = new ArrayList<Identifier>();
				tokens.add(new Identifier(identifier(), State.currToken()));
				lone = true;
			}
			
			if (!prefix && isConst && !State.nextToken().getId().equals("="))
			{
				warning("E012", State.currToken(), State.currToken().getValue());
			}
			
			for (Identifier t : tokens)
			{
				if (State.getFunct().getScope().getBlock().isGlobal())
				{
					if (BooleanUtils.isFalse(predefined.get(t.id)))
					{
						warning("W079", t.token, t.id);
					}
				}
				if (StringUtils.isNotEmpty(t.id) && !State.getFunct().isNoblockscopedvar())
				{
					State.getFunct().getScope().addlabel(t.id, type, t.token);
					names.add(t.token);
				}
			}
			
			if (State.nextToken().getId().equals("="))
			{
				advance("=");
				if (!prefix && peek(0).getId().equals("=") && State.nextToken().isIdentifier())
				{
					warning("W120", State.nextToken(), State.nextToken().getValue());
				}
				Token id = State.prevToken();
				// don't accept `in` in expression if prefix is used for ForIn/Of loop.
				Token value = expression(prefix ? 120 : 10);
				if (!prefix && value != null && value.getType() == TokenType.UNDEFINED)
				{
					warning("W080", id, id.getValue());
				}
				if (lone)
				{
					// JSHINT_BUG: this block doesn't make any sense because variable first isn't used anywhere
					//tokens.get(0).first = value;
				}
				else
				{
					destructuringPatternMatch(names, value);
				}
			}
			
			if (!prefix)
			{
				for (Identifier t : tokens)
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
			parseComma();
		}
		if (letblock)
		{
			advance(")");
			block(true, true);
			statement.setBlock(true);
			State.getFunct().getScope().unstack();
		}
		
		return statement;
	}
	
	private Token classdef(Token _this, int rbp, boolean isStatement)
	{
		if (!State.inES6())
		{
			warning("W104", State.currToken(), "class", "6");
		}
		if (isStatement)
		{
			// BindingIdentifier
			_this.setName(identifier());
			
			State.getFunct().getScope().addlabel(_this.getName(), "class", State.currToken());
			
		}
		else if (State.nextToken().isIdentifier() && !State.nextToken().getValue().equals("extends"))
		{
			// BindingIdentifier(opt)
			_this.setName(identifier());
			_this.setNamedExpr(true);
		}
		else
		{
			_this.setName(State.getNameStack().infer());
		}
		
		classtail(_this);
		
		if (isStatement)
		{
			State.getFunct().getScope().initialize(_this.getName());
		}
		
		return _this;
	}

	private void classtail(Token c)
	{
		boolean wasInClassBody = State.isInClassBody();
		
		// ClassHeritage(opt)
		if (State.nextToken().getValue().equals("extends"))
		{
			advance("extends");
			expression(10); // c.heritage = expression(10); JSHINT_BUG: "heritage" property is not used anywhere, can be removed
		}
		
		State.setInClassBody(true);
		advance("{");
		// ClassBody(opt)
		classbody(c);
		c.setBody(""); //JSHINT_BUG: method classbody doesn't return anything, so body always empty
		advance("}");
		State.setInClassBody(wasInClassBody);
	}

	private void classbody(Token c)
	{
		UniversalContainer props = ContainerFactory.nullContainer().create();
		UniversalContainer staticProps = ContainerFactory.nullContainer().create();
		boolean computed = false;
		
		while (!State.nextToken().getId().equals("}")) //JSHINT_BUG: for can be chaged to while, since i variable is not used anyhow
		{
			Token name = State.nextToken();
			boolean isStatic = false;
			boolean isGenerator = false;
			Token getset = null;
			
			// The ES6 grammar for ClassElement includes the `;` token, but it is
			// defined only as a placeholder to facilitate future language
			// extensions. In ES6 code, it serves no purpose.
			if (name.getId().equals(";"))
			{
				warning("W032");
				advance(";");
				continue;
			}
			
			if (name.getId().equals("*"))
			{
				isGenerator = true;
				advance("*");
				name = State.nextToken();
			}
			if (name.getId().equals("["))
			{
				name = computedPropertyName();
				computed = true;
			}
			else if (isPropertyName(name))
			{
				// Non-Computed PropertyName
				advance();
				computed = false;
				if (name.isIdentifier() && name.getValue().equals("static"))
				{
					if (checkPunctuator(State.nextToken(), "*"))
					{
						isGenerator = true;
						advance("*");
					}
					if (isPropertyName(State.nextToken()) || State.nextToken().getId().equals("["))
					{
						computed = State.nextToken().getId().equals("[");
						isStatic = true;
						name = State.nextToken();
						if (State.nextToken().getId().equals("["))
						{
							name = computedPropertyName();
						}
						else
							advance();
					}
				}
				
				if (name.isIdentifier() && (name.getValue().equals("get") || name.getValue().equals("set")))
				{
					if (isPropertyName(State.nextToken()) || State.nextToken().getId().equals("["))
					{
						computed = State.nextToken().getId().equals("[");
						getset = name;
						name = State.nextToken();
						if (State.nextToken().getId().equals("["))
						{
							name = computedPropertyName();
						}
						else
							advance();
					}
				}
			}
			else
			{
				if (State.nextToken().getValue() != null && !State.nextToken().getValue().isEmpty())
					warning("W052", State.nextToken(), State.nextToken().getValue());
				else
					warning("W052", State.nextToken(), State.nextToken().getType().toString());
				advance();
				continue;
			}
			
			if (!checkPunctuator(State.nextToken(), "("))
			{
				// error --- class properties must be methods
				error("E054", State.nextToken(), State.nextToken().getValue());
				while (!State.nextToken().getId().equals("}") && 
						!checkPunctuator(State.nextToken(), "("))
				{
					advance();
				}
				if (!State.nextToken().getValue().equals("("))
				{
					doFunction(c);
				}
			}
			
			if (!computed)
			{
				// We don't know how to determine if we have duplicate computed property names :(
				if (getset != null)
				{
					saveAccessor(getset.getValue(), isStatic ? staticProps : props, name.getValue(), name, true, isStatic);
				}
				else
				{
					if (name.getValue().equals("constructor"))
					{
						State.getNameStack().set(c);
					}
					else
					{
						State.getNameStack().set(name);
					}
					saveProperty(isStatic ? staticProps : props, name.getValue(), name, true, isStatic);
				}
			}
			
			if (getset != null && name.getValue().equals("constructor"))
			{
				String propDesc = getset.getValue().equals("get") ? "class getter method" : "class setter method";
				error("E049", name, propDesc, "constructor");
			}
			else if (name.getValue().equals("prototype"))
			{
				error("E049", name, "class method", "prototype");
			}
			
			propertyName(new UniversalContainer(name));
			
			doFunction(c, isGenerator ? FunctionType.GENERATOR : null, c.isNamedExpr() ? c.getName() : null);
		}
		
		checkProperties(props);
	}
	
	private void buildStatementTable()
	{
		final Token conststatement = stmt("const", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				return blockVariableStatement("const", _this, context);
			}
		});
		conststatement.setExps(true);
		
		final Token letstatement = stmt("let", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				return blockVariableStatement("let", _this, context);
			}
		});
		letstatement.setExps(true);
		
		final Token varstatement = stmt("var", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				boolean prefix = context.test() && context.asBoolean("prefix");
				boolean inexport  = context.test() && context.asBoolean("inexport");
				List<Identifier> tokens;
				boolean lone = false;
				
				_this.setFirstTokens();
				for (;;)
				{
					List<Token> names = new ArrayList<Token>();
					if (State.nextToken().getValue().equals("{") || State.nextToken().getValue().equals("["))
					{
						tokens = destructuringPattern(false, false);
						lone = false;
					}
					else
					{
						tokens = new ArrayList<Identifier>();
						tokens.add(new Identifier(identifier(), State.currToken()));
						lone = true;
					}
					
					if (State.getOption().test("varstmt"))
					{
						warning("W132", _this);
					}
					
					_this.addFirstTokens(names);
					
					for (Identifier t : tokens)
					{
						if (State.getFunct().isGlobal() && !State.impliedClosure())
						{
							if (BooleanUtils.isFalse(predefined.get(t.id)))
							{
								warning("W079", t.token, t.id);
							}
							else if (State.getOption().get("futurehostile").equals(false))
							{
								if ((!State.inES5() && Vars.ecmaIdentifiers.get(5).containsKey(t.id) && Vars.ecmaIdentifiers.get(5).get(t.id).equals(false)) ||
									(!State.inES6() && Vars.ecmaIdentifiers.get(6).containsKey(t.id) && Vars.ecmaIdentifiers.get(6).get(t.id).equals(false)))
								{
									warning("W129", t.token, t.id);
								}
							}
						}
						if (StringUtils.isNotEmpty(t.id))
						{
							State.getFunct().getScope().addlabel(t.id, "var", t.token);
							
							if (lone && inexport)
							{
								State.getFunct().getScope().setExported(t.id, t.token);
							}
							names.add(t.token);
						}
					}
					
					if (State.nextToken().getId().equals("="))
					{
						State.getNameStack().set(State.currToken());
						
						advance("=");
						if (peek(0).getId().equals("=") && State.nextToken().isIdentifier())
						{
							if (!prefix &&
								State.getFunct().getParams() == null || 
								!State.getFunct().getParams().contains(State.nextToken().getValue()))
							{
								warning("W120", State.nextToken(), State.nextToken().getValue());
							}
						}
						Token id = State.prevToken();
						// don't accept `in` in expression if prefix is used for ForIn/Of loop.
						Token value = expression(prefix ? 120 : 10);
						if (value != null && !prefix && State.getFunct().getLoopage() == 0 && value.getType() == TokenType.UNDEFINED)
						{
							warning("W080", id, id.getValue());
						}
						if (lone)
						{
							// JSHINT_BUG: this block doesn't make any sense because variable first isn't used anywhere
							//tokens.get(0).first = value;
						}
						else
						{
							destructuringPatternMatch(names, value);
						}
					}
					
					if (!State.nextToken().getId().equals(","))
					{
						break;
					}
					parseComma();
				}
				return _this;
			}
		});
		varstatement.setExps(true);
		
		blockstmt("class", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer rbp)
			{
				return classdef(_this, rbp.asInt(), true);
			}
		});
		
		blockstmt("function", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				boolean inexport = context != null && context.test() && context.asBoolean("inexport"); 
				boolean generator = false;
				if (State.nextToken().getValue().equals("*"))
				{
					advance("*");
					if (State.inES6(true))
					{
						generator = true;
					}
					else
					{
						warning("W119", State.currToken(), "function*", "6");
					}
				}
				if (inblock)
				{
					warning("W082", State.currToken());
				}
				String i = optionalidentifier();
				
				State.getFunct().getScope().addlabel(i, "function", State.currToken());
				
				if (StringUtils.isEmpty(i))
				{
					warning("W025");
				}
				else if (inexport)
				{
					State.getFunct().getScope().setExported(i, State.prevToken());
				}
				
				doFunction(i, _this, generator ? FunctionType.GENERATOR : null, inblock); // a declaration may already have warned
				if (State.nextToken().getId().equals("(") && State.nextToken().getLine() == State.currToken().getLine())
				{
					error("E039");
				}
				return _this;
			}
		});
		
		prefix("function", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				boolean generator = false;
				
				if (State.nextToken().getValue().equals("*"))
				{
					if (!State.inES6())
					{
						warning("W119", State.currToken(), "function*", "6");
					}
					advance("*");
					generator = true;
				}
				
				String i = optionalidentifier();
				doFunction(i, generator ? FunctionType.GENERATOR : null);
				return _this;
			}
		});
		
		blockstmt("if", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				Token t = State.nextToken();
				increaseComplexityCount();
				State.setCondition(true);
				advance("(");
				Token expr = expression(0);
				
				if (expr == null)
				{
					quit("E041", _this);
				}
				
				checkCondAssignment(expr);
				
				// When the if is within a for-in loop, check if the condition
				// starts with a negation operator
				Token forinifcheck = null;
				if (State.getOption().get("forin").test() && State.isForinifcheckneeded())
				{
					State.setForinifcheckneeded(false); // We only need to analyze the first if inside the loop
					forinifcheck = State.getForinifchecks().size() > 0 ? State.getForinifchecks().get(State.getForinifchecks().size()-1) : null;
					if (expr.getType() == TokenType.PUNCTUATOR && expr.getValue().equals("!"))
					{
						forinifcheck.setType(TokenType.NEGATIVE);
					}
					else
					{
						forinifcheck.setType(TokenType.POSITIVE);
					}
				}
				
				advance(")", t);
				State.setCondition(false);
				List<Token> s = block(true, true);
				
				// When the if is within a for-in loop and the condition has a negative form,
				// check if the body contains nothing but a continue statement
				if (forinifcheck != null && forinifcheck.getType() == TokenType.NEGATIVE)
				{
					if (s != null && s.size() > 0 && s.get(0) != null && s.get(0).getType() == TokenType.IDENTIFIER && s.get(0).getValue().equals("continue"))
					{
						forinifcheck.setType(TokenType.NEGATIVE_WITH_CONTINUE);
					}
				}
				
				if (State.nextToken().getId().equals("else"))
				{
					advance("else");
					if (State.nextToken().getId().equals("if") || State.nextToken().getId().equals("switch"))
					{
						statement();
					}
					else
					{
						block(true, true);
					}
				}
				return _this;
			}
		});
		
		blockstmt("try", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				boolean b = false;
				
				block(true);
				
				while (State.nextToken().getId().equals("catch"))
				{
					increaseComplexityCount();
					if (b && (!State.inMoz()))
					{
						warning("W118", State.nextToken(), "multiple catch blocks");
					}
					
					//start doCatch
					advance("catch");
					advance("(");
					
					State.getFunct().getScope().stack("catchparams");
					
					if (checkPunctuators(State.nextToken(), "[", "{"))
					{
						List<Identifier> tokens = destructuringPattern(false, false);
						for (Identifier token : tokens)
						{
							if (StringUtils.isNotEmpty(token.id))
							{
								State.getFunct().getScope().addParam(token.id, new Token(token), "exception");
							}
						}
					}
					else if (State.nextToken().getType() != TokenType.IDENTIFIER)
					{
						warning("E030", State.nextToken(), State.nextToken().getValue());
					}
					else
					{
						// only advance if we have an identifier so we can continue parsing in the most common error - that no param is given.
						State.getFunct().getScope().addParam(identifier(), State.currToken(), "exception");
					}
					
					if (State.nextToken().getValue().equals("if"))
					{
						if (!State.inMoz())
						{
							warning("W118", State.currToken(), "catch filter");
						}
						advance("if");
						expression(0);
					}
					
					advance(")");
					
					block(false);
					
					State.getFunct().getScope().unstack();
					// end doCatch
					b = true;
				}
				
				if (State.nextToken().getId().equals("finally"))
				{
					advance("finally");
					block(true);
					return null;
				}
				
				if (!b)
				{
					error("E021", State.nextToken(), "catch", State.nextToken().getValue());
				}
				
				return _this;
			}
		});
		
		blockstmt("while", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				Token t = State.nextToken();
				State.getFunct().increaseBreakage();
				State.getFunct().increaseLoopage();
				increaseComplexityCount();
				advance("(");
				checkCondAssignment(expression(0));
				advance(")", t);
				block(true, true);
				State.getFunct().decreaseBreakage();
				State.getFunct().decreaseLoopage();
				return _this;
			}
		}).setLabelled(true);
		
		blockstmt("with", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
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
				expression(0);
				advance(")", t);
				block(true, true);
				
				return _this;
			}
		});
		
		blockstmt("switch", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				Token t = State.nextToken();
				boolean g = false;
				boolean noindent = false;
				
				State.getFunct().increaseBreakage();
				advance("(");
				checkCondAssignment(expression(0));
				advance(")", t);
				t = State.nextToken();
				advance("{");
				
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
						_this.getCases().add(expression(0));
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
						break;
					case "}":
						if (!noindent)
							indent -= State.getOption().asInt("indent");
						
						advance("}", t);
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
								statements();
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
					            statements();
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
				//JSHINT_BUG: no need to return this, because this code is unreachable
			}
		}).setLabelled(true);
		
		stmt("debugger", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				if (!State.getOption().get("debug").test())
				{
					warning("W087", _this);
				}
				return _this;
			}
		}).setExps(true);
		
		Token x = stmt("do", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				State.getFunct().increaseBreakage();
				State.getFunct().increaseLoopage();
				increaseComplexityCount();
				
				_this.setFirstTokens(block(true, true));
				advance("while");
				Token t = State.nextToken();
				advance("(");
				checkCondAssignment(expression(0));
				advance(")", t);
				State.getFunct().decreaseBreakage();
				State.getFunct().decreaseLoopage();
				return _this;
			}
		});
		x.setLabelled(true);
		x.setExps(true);
		
		blockstmt("for", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				Token t = State.nextToken();
				boolean letscope = false;
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
				
				increaseComplexityCount();
				advance("(");
				
				// what kind of for(…) statement it is? for(…of…)? for(…in…)? for(…;…;…)?
				Token nextop = null; // contains the token of the "in" or "of" operator
				int i = 0;
				int level = 0; // BindingPattern "level" --- level 0 === no BindingPattern
				Token comma = null; // First comma punctuator at level 0
				Token initializer = null; // First initializer at level 0
				int bindingPower = 0;
				List<Token> targets;
				Token target;
				
				// If initial token is a BindingPattern, count it as such.
				if (checkPunctuators(State.nextToken(), new String[]{"{", "["})) ++level;
				do
				{
					nextop = peek(i);
					++i;
					if (checkPunctuators(nextop, new String[]{"{", "["})) ++level;
					else if (checkPunctuators(nextop, new String[]{"}", "]"})) --level;
					if (level < 0) break;
					if (level == 0)
					{
						if (comma == null && checkPunctuator(nextop, ",")) comma = nextop;
						else if (initializer == null && checkPunctuator(nextop, "=")) initializer = nextop;
					}
				}
				while (level > 0 || !(nextop.getValue().equals("in") || nextop.getValue().equals("of")) && !nextop.getValue().equals(";") &&
					   nextop.getType() != TokenType.END); // Is this a JSCS bug? This looks really weird.
				
				// if we're in a for (� in|of �) statement
				if (nextop.getValue().equals("in") || nextop.getValue().equals("of"))
				{
					if (nextop.getValue().equals("of"))
					{
						bindingPower = 20;
						if (!State.inES6())
						{
							error("W104", nextop, "for of", "6");
						}
					}
					else
					{
						bindingPower = 0;
					}
					
					if (initializer != null)
					{
						error("W133", comma, nextop.getValue(), "initializer is forbidden");
					}
					
					if (comma != null)
					{
						error("W133", comma, nextop.getValue(), "more than one ForBinding");
					}
					
					if (State.nextToken().getId().equals("var"))
					{
						advance("var");
						State.currToken().fud(ContainerFactory.createObject("prefix", true));
					}
					else if (State.nextToken().getId().equals("let") || State.nextToken().getId().equals("const"))
					{
						advance(State.nextToken().getId());
						// create a new block scope
						letscope = true;
						State.getFunct().getScope().stack();
						State.currToken().fud(ContainerFactory.createObject("prefix", true));
					}
					else
					{
						targets = new ArrayList<Token>();
						
						// The following parsing logic recognizes initializers and the comma
						// operator despite the fact that they are not supported by the
						// grammar. Doing so allows JSHint to emit more a meaningful error
						// message (i.e. W133) in response to a common programming mistake.
						do
						{
							if (checkPunctuators(State.nextToken(), "{", "["))
							{
								for (Identifier elem : destructuringPattern(false, true))
								{
									targets.add(elem.getToken());
								}
							}
							else
							{
								target = expression(120);
								
								if (target.getType() == TokenType.IDENTIFIER)
								{
									targets.add(target);
								}
								
								checkLeftSideAssign(target, nextop, false);
							}
							
							if (checkPunctuator(State.nextToken(), "="))
							{
								advance("=");
								expression(120);
							}
							
							if (checkPunctuator(State.nextToken(), ","))
							{
								advance(",");
							}
						}
						while (State.nextToken() != nextop);
						
						// In the event of a syntax error, do no issue warnings regarding the
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
					
					advance(nextop.getValue());
					// The binding power is variable because for-in statements accept any
				    // Expression in this position, while for-of statements are limited to
				    // AssignmentExpressions. For example:
				    //
				    //     for ( LeftHandSideExpression in Expression ) Statement
				    //     for ( LeftHandSideExpression of AssignmentExpression ) Statement
					expression(bindingPower);
					advance(")", t);
					
					if (nextop.getValue().equals("in") && State.getOption().get("forin").test())
					{
						State.setForinifcheckneeded(true);
						
						if (State.getForinifchecks() == null)
						{
							State.setForinifchecks(new ArrayList<Token>());
						}
						
						// Push a new for-in-if check onto the stack. The type will be modified
				        // when the loop's body is parsed and a suitable if statement exists.
						State.getForinifchecks().add(new Token(TokenType.NONE));
					}
					
					State.getFunct().increaseBreakage();
					State.getFunct().increaseLoopage();
					
					List<Token> s = block(true, true);
					
					if (nextop.getValue().equals("in") && State.getOption().get("forin").test())
					{
						if (State.getForinifchecks() != null && State.getForinifchecks().size() > 0)
						{
							Token check = State.getForinifchecks().remove(State.getForinifchecks().size()-1);
							
							if (// No if statement or not the first statement in loop body
								s != null && s.size() > 0 && (s.get(0) == null || !s.get(0).getValue().equals("if")) ||
								// Positive if statement is not the only one in loop body
								check.getType() == TokenType.POSITIVE && s.size() > 1 ||
								// Negative if statement but no continue
								check.getType() == TokenType.NEGATIVE)
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
					if (!State.nextToken().getId().equals(";"))
					{
						if (State.nextToken().getId().equals("var"))
						{
							advance("var");
							State.currToken().fud(ContainerFactory.undefinedContainer());
						}
						else if (State.nextToken().getId().equals("let"))
						{
							advance("let");
							// create a new block scope
							letscope = true;
							State.getFunct().getScope().stack();
							State.currToken().fud(ContainerFactory.undefinedContainer());
						}
						else
						{
							for (;;)
							{
								expression(0, "for");
								if (!State.nextToken().getId().equals(","))
								{
									break;
								}
								parseComma();
							}
						}
					}
					nolinebreak(State.currToken());
					advance(";");
					
					// start loopage after the first ; as the next two expressions are executed
				    // on every loop
					State.getFunct().increaseLoopage();
					if (!State.nextToken().getId().equals(";"))
					{
						checkCondAssignment(expression(0));
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
							expression(0, "for");
							if (!State.nextToken().getId().equals(","))
							{
								break;
							}
							parseComma();
						}
					}
					advance(")", t);
					State.getFunct().increaseBreakage();
					block(true, true);
					State.getFunct().decreaseBreakage();
					State.getFunct().decreaseLoopage();
				}
				// unstack loop blockscope
				if (letscope)
				{
					State.getFunct().getScope().unstack();
				}
				return _this;
			}
		}).setLabelled(true);
		
		stmt("break", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
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
			}
		}).setExps(true);
		
		stmt("continue", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
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
			}
		}).setExps(true);
		
		stmt("return", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				if (_this.getLine() == startLine(State.nextToken()))
				{
					if (!State.nextToken().getId().equals(";") && !State.nextToken().isReach())
					{
						//JSHINT_BUG: in this place it's better to store token in a separate variable and use it instead of this.first 
						Token t = expression(0);
						_this.setFirstTokens(t);
						
						if (t != null && t.getType() == TokenType.PUNCTUATOR && t.getValue().equals("=") &&
							!t.isParen() && !State.getOption().test("boss"))
						{
							warningAt("W093", t.getLine(), t.getCharacter());
						}
					}
				}
				else
				{
					if (State.nextToken().getType() == TokenType.PUNCTUATOR &&
						(State.nextToken().getValue().equals("[") || State.nextToken().getValue().equals("{") || State.nextToken().getValue().equals("+") || State.nextToken().getValue().equals("-")))
					{
						nolinebreak(_this); // always warn (Line breaking error)
					}
				}
				
				reachable(_this);
				
				return _this;
			}
		}).setExps(true);
		
		x = prefix("yield", new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				if (State.inMoz())
				{
					return mozYield(_this);
				}
				Token prev = State.prevToken();
				
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
					//delegatingYield = true; JSHINT_BUG: this property is not used anywhere can be removed
					advance("*");
				}
				
				// Parse operand
				if (!isEndOfExpr() && !State.nextToken().getId().equals(","))
				{
					if (State.nextToken().getNud() != null)
					{
						nobreaknonadjacent(State.currToken(), State.nextToken());
						_this.setFirstTokens(expression(10));
						
						if (_this.getFirstToken().getType() == TokenType.PUNCTUATOR && _this.getFirstToken().getValue().equals("=") &&
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
			}
		});
		x.setExps(true);
		x.setLbp(25);
		x.setLtBoundary(LtBoundaryType.AFTER);
		
		stmt("throw", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				nolinebreak(_this);
				_this.setFirstTokens(expression(20));
				
				reachable(_this);
				
				return _this;
			}
		}).setExps(true);
		
		stmt("import", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				if (!State.getFunct().getScope().getBlock().isGlobal())
				{
					error("E053", State.currToken(), "Import");
				}
				
				if (!State.inES6())
				{
					warning("W119", State.currToken(), "import", "6");
				}
				
				if (State.nextToken().getType() == TokenType.STRING)
				{
					// ModuleSpecifier :: StringLiteral
					advance("(string)");
					return _this;
				}
				
				if (State.nextToken().isIdentifier())
				{
					// ImportClause :: ImportedDefaultBinding
					_this.setName(identifier());
					// Import bindings are immutable (see ES6 8.1.1.5.5)
					State.getFunct().getScope().addlabel(_this.getName(), "import", true, State.currToken());
					
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
						_this.setName(identifier());
						// Import bindings are immutable (see ES6 8.1.1.5.5)
						State.getFunct().getScope().addlabel(_this.getName(), "import", true, State.currToken());
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
						if (State.nextToken().getType() == TokenType.DEFAULT)
						{
							importName = "default";
							advance("default");
						}
						else
						{
							importName = identifier();
						}
						if (State.nextToken().getValue().equals("as"))
						{
							advance("as");
							importName = identifier();
						}
						
						// Import bindings are immutable (see ES6 8.1.1.5.5)
						State.getFunct().getScope().addlabel(importName, "import", true, State.currToken());
						
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
			    return _this;
			}
		}).setExps(true);
		
		stmt("export", new Token.FudFunction()
		{
			@Override
			public Token apply(Token _this, UniversalContainer context)
			{
				boolean ok = true;
				String identifier;
				
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
				
				if (State.nextToken().getType() == TokenType.DEFAULT)
				{
					// ExportDeclaration ::
					//		export default [lookahead ∉ { function, class }] AssignmentExpression[In] ;
					//		export default HoistableDeclaration
					//		export default ClassDeclaration
					State.getNameStack().set(State.nextToken());
					advance("default");
					String exportType = State.nextToken().getId();
					if (exportType.equals("function") || exportType.equals("class"))
					{
						_this.setBlock(true);
					}

					Token token = peek();
					
					expression(10);
					
					identifier = token.getValue();
					
					if (_this.isBlock())
					{
						State.getFunct().getScope().addlabel(identifier, exportType, true, token);
						
						State.getFunct().getScope().setExported(identifier, token);
					}
					
					return _this;
				}
				
				if (State.nextToken().getValue().equals("{"))
				{
					// ExportDeclaration :: export ExportClause
					advance("{");
					List<Token> exportedTokens = new ArrayList<Token>();
					for (;;)
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
					if (State.nextToken().getValue().equals("from"))
					{
						// ExportDeclaration :: export ExportClause FromClause
						advance("from");
						advance("(string)");
					}
					else if (ok)
					{
						for (Token token : exportedTokens)
						{
							State.getFunct().getScope().setExported(token.getValue(), token);
						}
					}
					return _this;
				}
				
				if (State.nextToken().getId().equals("var"))
				{
					// ExportDeclaration :: export VariableStatement
					advance("var");
					State.currToken().fud(ContainerFactory.createObject("inexport", true));
				}
				else if (State.nextToken().getId().equals("let"))
				{
					// ExportDeclaration :: export VariableStatement
					advance("let");
					State.currToken().fud(ContainerFactory.createObject("inexport", true));
				}
				else if (State.nextToken().getId().equals("const"))
				{
					// ExportDeclaration :: export VariableStatement
					advance("const");
					State.currToken().fud(ContainerFactory.createObject("inexport", true));
				}
				else if (State.nextToken().getId().equals("function"))
				{
					// ExportDeclaration :: export Declaration
					_this.setBlock(true);
					advance("function");
					State.getSyntax().get("function").fud(ContainerFactory.createObject("inexport", true));
				}
				else if (State.nextToken().getId().equals("class"))
				{
					// ExportDeclaration :: export Declaration
					_this.setBlock(true);
					advance("class");
					Token classNameToken = State.nextToken();
					State.getSyntax().get("class").fud(ContainerFactory.undefinedContainer());
					State.getFunct().getScope().setExported(classNameToken.getValue(), classNameToken);
				}
				else
				{
					error("E024", State.nextToken(), State.nextToken().getValue());
				}
				
			    return _this;
			}
		}).setExps(true);
		
		// Future Reserved Words
		
		futureReservedWord(TokenType.ABSTRACT);
		futureReservedWord(TokenType.AWAIT, new Token.Meta(true, false, true, null));
		futureReservedWord(TokenType.BOOLEAN);
		futureReservedWord(TokenType.BYTE);
		futureReservedWord(TokenType.CHAR);
		futureReservedWord(TokenType.CLASS, new Token.Meta(true, false, false, new Token.NudFunction()
		{
			@Override
			public Token apply(Token _this, int rbp)
			{
				return classdef(_this, rbp, false);
			}
		}));
		futureReservedWord(TokenType.DOUBLE);
		futureReservedWord(TokenType.ENUM, new Token.Meta(true, false, false, null));
		futureReservedWord(TokenType.EXPORT, new Token.Meta(true, false, false, null));
		futureReservedWord(TokenType.EXTENDS, new Token.Meta(true, false, false, null));
		futureReservedWord(TokenType.FINAL);
		futureReservedWord(TokenType.FLOAT);
		futureReservedWord(TokenType.GOTO);
		futureReservedWord(TokenType.IMPLEMENTS, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.IMPORT, new Token.Meta(true, false, false, null));
		futureReservedWord(TokenType.INT);
		futureReservedWord(TokenType.INTERFACE, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.LONG);
		futureReservedWord(TokenType.NATIVE);
		futureReservedWord(TokenType.PACKAGE, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.PRIVATE, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.PROTECTED, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.PUBLIC, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.SHORT);
		futureReservedWord(TokenType.STATIC, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.SUPER, new Token.Meta(true, true, false, null));
		futureReservedWord(TokenType.SYNCHRONIZED);
		futureReservedWord(TokenType.TRANSIENT);
		futureReservedWord(TokenType.VOLATILE);
	}
	
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
					//isBlock = true; //JSHINT_BUG: isBlock variable is not used anywhere
					notJson = true;
				}
			}
			while (bracketStack > 0 && !pn.getId().equals("(end)"));
		}
	}

	private void saveProperty(UniversalContainer props, String name, Token tkn)
	{
		saveProperty(props, name, tkn, false, false);
	}
	
	private void saveProperty(UniversalContainer props, String name, Token tkn, boolean isClass, boolean isStatic)
	{
		String[] msgarray = {"key", "class method", "static class method"};
		
		String msg = msgarray[(isClass ? 1:0) + (isStatic ? 1:0)];
		if (tkn.isIdentifier())
		{
			name = tkn.getValue();
		}
		
		if (props.test(name) && !name.equals("__proto__"))
		{
			warning("W075", State.nextToken(), msg, name);
		}
		else
		{
			props.set(name, ContainerFactory.nullContainer().create());
		}
		
		props.get(name).set("basic", true);
		props.get(name).set("basictkn", tkn);
	}

	/**
	 * @param {string} accessorType - Either "get" or "set"
	 * @param {object} props - a collection of all properties of the object to
	 *                         which the current accessor is being assigned
	 * @param {object} tkn - the identifier token representing the accessor name
	 * @param {boolean} isClass - whether the accessor is part of an ES6 Class
	 *                            definition
	 * @param {boolean} isStatic - whether the accessor is a static method
	 */
	private void saveAccessor(String accessorType, UniversalContainer props, String name, Token tkn)
	{
		saveAccessor(accessorType, props, name, tkn, false, false);
	}
	
	private void saveAccessor(String accessorType, UniversalContainer props, String name, Token tkn, boolean isClass, boolean isStatic)
	{
		String flagName = accessorType.equals("get") ? "getterToken" : "setterToken";
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
		
		State.currToken().setAccessorType(accessorType);
		State.getNameStack().set(tkn);
		
		if (props.test(name))
		{
			if ((props.get(name).test("basic") || props.get(name).test(flagName)) && !name.equals("__proto__"))
			{
				warning("W075", State.nextToken(), msg, name);
			}
		}
		else
		{
			props.set(name, ContainerFactory.nullContainer().create());
		}
		
		props.get(name).set(flagName, tkn);
	}

	private Token computedPropertyName()
	{
		advance("[");
		if (!State.inES6())
		{
			warning("W119", State.currToken(), "computed property names", "6");
		}
		Token value = expression(10);
		advance("]");
		return value;
	}

	/**
	 * Test whether a given token is a punctuator matching one of the specified values
	 * @param {Token} token
	 * @param {Array.<string>} values
	 * @returns {boolean}
	 */
	private boolean checkPunctuators(Token token, String... values)
	{
		if (token.getType() == TokenType.PUNCTUATOR)
		{
			return ArrayUtils.contains(values, token.getValue());
		}
		return false;
	}
	
	/**
	 * Test whether a given token is a punctuator matching the specified value
	 * @param {Token} token
	 * @param {string} value
	 * @returns {boolean}
	 */
	private boolean checkPunctuator(Token token, String value)
	{
		return token.getType() == TokenType.PUNCTUATOR && token.getValue().equals(value);
	}

	// Check whether this function has been reached for a destructuring assign with undeclared values
	private void destructuringAssignOrJsonValue()
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
			statements();
		}
		// otherwise parse json value
		else
		{
			State.getOption().set("laxbreak", true);
			State.setJsonMode(true);
			jsonValue();
		}
	}
	
	// array comprehension parsing function
	// parses and defines the three states of the list comprehension in order
	// to avoid defining global variables, but keeping them to the list comprehension scope
	// only. The order of the states are as follows:
	//  * "use" which will be the returned iterative part of the list comprehension
	//  * "define" which will define the variables local to the list comprehension
	//  * "filter" which will help filter out values
	private class ArrayComprehension
	{
		private class Variable
		{
			//private Functor funct; //JSHINT_BUG: is not used anywhere can be removed
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
					warning("W098", v.token, v.value); // JSHINT_BUG: typo, must be v.token.raw_text instead of v.raw_text 
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

	// Parse JSON
	
	private void jsonObject()
	{
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
	}
	private void jsonArray()
	{
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
	}
	private void jsonValue()
	{
		switch (State.nextToken().getId())
		{
		case "{":
			jsonObject();
			break;
		case "[":
			jsonArray();
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
	
	private List<LinterWarning> errors = new ArrayList<LinterWarning>();
	private List<InternalSource> internals = new ArrayList<InternalSource>(); // "internal" scripts, like eval containing a static string
	private Set<String> blacklist = new HashSet<String>();
	private String scriptScope = "";
	
	// PUBLIC CONSTRUCTORS AND FUNCTIONS
	
	public JSHint()
	{
		buildSyntaxTable();
		ecmaScriptParser();
		buildStatementTable();
		addModule(new Style());
	}
	
	public boolean lint(String source) throws JSHintException
	{
		return lint(source, null, null);
	}
	
	public boolean lint(String source, LinterOptions options) throws JSHintException
	{
		return lint(source, options, null);
	}
	
	public boolean lint(String source, LinterOptions options, LinterGlobals globals) throws JSHintException
	{
		String s = StringUtils.defaultString(source);
		LinterOptions o = new LinterOptions(options);
		LinterGlobals g = (globals == null ? new LinterGlobals() : globals);
		
		init(o, g);
		
		s = o.cleanSource(s);
		
		return run(new Lexer(s), o, g);
	}
	
	public boolean lint(String[] source) throws JSHintException
	{
		return lint(source, null, null);
	}
	
	public boolean lint(String[] source, LinterOptions options) throws JSHintException
	{
		return lint(source, options, null);
	}
	
	public boolean lint(String[] source, LinterOptions options, LinterGlobals globals) throws JSHintException
	{
		String[] s = ArrayUtils.nullToEmpty(source);
		LinterOptions o = new LinterOptions(options);
		LinterGlobals g = (globals == null ? new LinterGlobals() : globals);
		
		init(o, g);
		
		//JSHINT_BUG: where is ignore delimiters for source array??
		
		return run(new Lexer(s), o, g);
	}
	
	private void init(LinterOptions o, LinterGlobals g)
	{
		State.reset();
		
		if (o.hasOption("scope"))
		{
			scriptScope = o.getAsString("scope");
		}
		else
		{
			errors = new ArrayList<LinterWarning>();
			//undefs = null; //JSHINT_BUG: variable undef is not used anywhere, can be removed
			internals = new ArrayList<InternalSource>();
			blacklist = new HashSet<String>();
			scriptScope = "(main)";
		}
		
		predefined = new HashMap<String, Boolean>();
		combine(predefined, Vars.ecmaIdentifiers.get(3));
		combine(predefined, Vars.reservedVars);
		
		combine(predefined, g);
		
		declared = new HashMap<String, Token>();
		Map<String, Boolean> exported = new HashMap<String, Boolean>(); // Variables that live outside the current file
		
		o.readPredefineds(predefined, blacklist);
		o.readExporteds(exported);
		
		State.setOption(o.extractNormalOptions());
		State.setIgnored(o.extractIgnoredOptions());
		
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
			checkOption(name, State.currToken());
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
				destructuringAssignOrJsonValue();
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
				
				statements();
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
		for (String name : names.split(" "))
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
			
			//JSHINT_BUG: very strange fors in original code, probably bug or just dead code, and this is the only place where functionicity variable is used, so can also be removed
			
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
	
	static class Identifier
	{
		private String id = "";
		private Token token = null;
		
		private Identifier(String id, Token token)
		{
			setId(id);
			setToken(token);
		}

		String getId()
		{
			return id;
		}

		private void setId(String id)
		{
			this.id = StringUtils.defaultString(id);
		}

		Token getToken()
		{
			return token;
		}

		private void setToken(Token token)
		{
			this.token = token;
		}
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
		PARAMS,				// "(params)"
		ISCAPTURING,		// "(isCapturing)"
		NOBLOCKSCOPEDVAR,	// "(noblockscopedvar)")
		LAST,				// "(last)"
		LASTCHARACTER,		// "(lastcharacter)"
		UNUSEDOPTION,		// "(unusedOption)"
		VERB				// "(verb)"
	}
}