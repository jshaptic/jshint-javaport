/*!
 * Java implementation of JSHint.
 *
 * Licensed under the MIT license.
 *
 * Java implementation of JSHint is a derivative work of JSLint:
 *
 *   Copyright (c) 2002 Douglas Crockford  (www.JSLint.com)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining
 *   a copy of this software and associated documentation files (the "Software"),
 *   to deal in the Software without restriction, including without limitation
 *   the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *   and/or sell copies of the Software, and to permit persons to whom
 *   the Software is furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included
 *   in all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *   FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 *   DEALINGS IN THE SOFTWARE.
 *
 */

package org.jshint;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.LinterOptions.Delimiter;
import org.jshint.Token.ArityType;
import org.jshint.utils.EventContext;
import org.jshint.utils.EventEmitter;
import org.jshint.utils.JSHintModule;

/**
 * Based on JSHint 2.13.3
 */
public class JSHint {

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

	private State state = new State();

	private Map<String, Token> declared = null; // Globals that were declared using /*global ... */ syntax.

	private List<Functor> functions = null; // All of the functions

	private boolean inblock = false;
	private int indent = 0;
	private List<Token> lookahead = null;
	private Lexer lex = null;
	private Map<String, Integer> member = null;
	private Map<String, Boolean> membersOnly = null;
	private Map<String, Boolean> predefined = null; // Global variables defined by option

	private List<JSHintModule> extraModules = new ArrayList<>();
	private EventEmitter emitter = new EventEmitter();

	private Boolean checkOption(String name, boolean isStable, Token t) {
		String type;
		Set<String> validNames;

		if (isStable) {
			type = "";
			validNames = Options.validNames;
		} else {
			type = "unstable ";
			validNames = Options.unstableNames;
		}

		name = name.trim();

		if (Reg.isOptionMessageCode(name)) // PORT INFO: test regexp was moved to Reg class
		{
			return true;
		}

		if (!validNames.contains(name)) {
			if (t.getType() != Token.Type.JSLINT && !Options.removed.containsKey(name)) {
				error("E001", t, type, name);
				return false;
			}
		}

		return true;
	}

	private Boolean isIdentifier(Token tkn, String value) {
		if (tkn == null)
			return false;

		if (!tkn.isIdentifier() || !tkn.getValue().equals(value))
			return false;

		return true;
	}

	/**
	 * ES3 defined a set of "FutureReservedWords" in order "to allow for the
	 * possibility of future adoption of [proposed] extensions."
	 *
	 * ES5 reduced the set of FutureReservedWords, in some cases by using them to
	 * define new syntactic forms (e.g. `class` and `const`) and in other cases
	 * by simply allowing their use as Identifiers (e.g. `int` and `goto`).
	 * Separately, ES5 introduced new restrictions on certain tokens, but limited
	 * the restriction to strict mode code (e.g. `let` and `yield`).
	 *
	 * This function determines if a given token describes a reserved word
	 * according to the current state of the parser.
	 *
	 * @param context the parsing context; see `prod-params.js` for
	 *                more information
	 * @param token
	 */
	private Boolean isReserved(int context, Token token) {
		if (!token.isReserved()) {
			return false;
		}
		Token.Meta meta = token.getMeta();

		if (meta != null && meta.isFutureReservedWord()) {
			if (state.inES5()) {
				// ES3 FutureReservedWord in an ES5 environment.
				if (!meta.isES5()) {
					return false;
				}

				if (token.isProperty()) {
					return false;
				}
			}
		} else if (meta != null && meta.isES5() && !state.inES5()) {
			return false;
		}

		// Some identifiers are reserved only within a strict mode environment.
		if (meta != null && meta.isStrictOnly() && state.inES5()) {
			if (!state.getOption().test("strict") && !state.isStrict()) {
				return false;
			}
		}

		if (token.getId().equals("await") && ((context & ProdParams.ASYNC) == 0 && !state.getOption().test("module"))) {
			return false;
		}

		if (token.getId().equals("yield") && (context & ProdParams.YIELD) == 0) {
			return state.isStrict();
		}

		return true;
	}

	private String supplant(String str, LinterWarning data) {
		// PORT INFO: replace regexp was moved to Reg class
		return Reg.replaceMessageSupplant(str, data);
	}

	private void combine(Map<String, Boolean> dest, Map<String, Boolean> src) {
		for (String name : src.keySet()) {
			if (!blacklist.contains(name)) {
				dest.put(name, src.get(name));
			}
		}
	}

	private void processenforceall() {
		if (state.getOption().test("enforceall")) {
			for (String enforceopt : Options.bool.get("enforcing").keySet()) {
				if (state.getOption().isUndefined(enforceopt) &&
						BooleanUtils.isNotTrue(Options.noenforceall.get(enforceopt))) {
					state.getOption().set(enforceopt, true);
				}
			}
			for (String relaxopt : Options.bool.get("relaxing").keySet()) {
				if (!state.getOption().has(relaxopt)) {
					state.getOption().set(relaxopt, false);
				}
			}
		}
	}

	/**
	 * Apply all linting options according to the status of the `state` object.
	 */
	private void applyOptions() {
		String badESOpt;
		processenforceall();

		/**
		 * JSHINT_TODO: Remove in JSHint 3
		 */
		badESOpt = state.inferEsVersion();
		if (StringUtils.isNotEmpty(badESOpt)) {
			quit("E059", state.nextToken(), "esversion", badESOpt);
		}

		if (state.inES5()) {
			combine(predefined, Vars.ecmaIdentifiers.get(5));
		}

		if (state.inES6()) {
			combine(predefined, Vars.ecmaIdentifiers.get(6));
		}

		if (state.inES8()) {
			combine(predefined, Vars.ecmaIdentifiers.get(8));
		}

		if (state.inES11()) {
			combine(predefined, Vars.ecmaIdentifiers.get(11));
		}

		/**
		 * Use `in` to check for the presence of any explicitly-specified value for
		 * `globalstrict` because both `true` and `false` should trigger an error.
		 */
		if (state.getOption().get("strict").equals("global") && state.getOption().has("globalstrict")) {
			quit("E059", state.nextToken(), "strict", "globalstrict");
		}

		if (state.getOption().test("module")) {
			/**
			 * JSHINT_TODO: Extend this restriction to *all* ES6-specific options.
			 */
			if (!state.inES6()) {
				warning("W134", state.nextToken(), "module", "6");
			}
		}

		if (state.getOption().test("regexpu")) {
			/**
			 * JSHINT_TODO: Extend this restriction to *all* ES6-specific options.
			 */
			if (!state.inES6()) {
				warning("W134", state.nextToken(), "regexpu", "6");
			}
		}

		if (state.getOption().test("couch")) {
			combine(predefined, Vars.couch);
		}

		if (state.getOption().test("qunit")) {
			combine(predefined, Vars.qunit);
		}

		if (state.getOption().test("rhino")) {
			combine(predefined, Vars.rhino);
		}

		if (state.getOption().test("shelljs")) {
			combine(predefined, Vars.shelljs);
			combine(predefined, Vars.node);
		}

		if (state.getOption().test("typed")) {
			combine(predefined, Vars.typed);
		}

		if (state.getOption().test("phantom")) {
			combine(predefined, Vars.phantom);
		}

		if (state.getOption().test("prototypejs")) {
			combine(predefined, Vars.prototypejs);
		}

		if (state.getOption().test("node")) {
			combine(predefined, Vars.node);
			combine(predefined, Vars.typed);
		}

		if (state.getOption().test("devel")) {
			combine(predefined, Vars.devel);
		}

		if (state.getOption().test("dojo")) {
			combine(predefined, Vars.dojo);
		}

		if (state.getOption().test("browser")) {
			combine(predefined, Vars.browser);
			combine(predefined, Vars.typed);
		}

		if (state.getOption().test("browserify")) {
			combine(predefined, Vars.browser);
			combine(predefined, Vars.typed);
			combine(predefined, Vars.browserify);
		}

		if (state.getOption().test("nonstandard")) {
			combine(predefined, Vars.nonstandard);
		}

		if (state.getOption().test("jasmine")) {
			combine(predefined, Vars.jasmine);
		}

		if (state.getOption().test("jquery")) {
			combine(predefined, Vars.jquery);
		}

		if (state.getOption().test("mootools")) {
			combine(predefined, Vars.mootools);
		}

		if (state.getOption().test("worker")) {
			combine(predefined, Vars.worker);
		}

		if (state.getOption().test("wsh")) {
			combine(predefined, Vars.wsh);
		}
		if (state.getOption().test("yui"))

		{
			combine(predefined, Vars.yui);
		}

		if (state.getOption().test("mocha")) {
			combine(predefined, Vars.mocha);
		}
	}

	// Produce an error warning.
	private void quit(String code, Token token, String... substitutions) {
		int percentage = (int) Math.floor((double) token.getLine() / state.getLines().length * 100);
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

	private void removeIgnoredMessages() {
		Map<Integer, Boolean> ignored = state.getIgnoredLines();

		if (ignored.isEmpty())
			return;
		for (Iterator<LinterWarning> iterator = errors.iterator(); iterator.hasNext();) {
			LinterWarning err = iterator.next();
			if (BooleanUtils.isTrue(ignored.get(err.getLine()))) {
				iterator.remove();
			}
		}
	}

	private LinterWarning warning(String code) {
		return warning(code, null);
	}

	private LinterWarning warning(String code, Token t, String... substitutions) {
		String msg = "";

		// PORT INFO: test regexp was replaced with straight check
		if (code.startsWith("W") && code.length() == 4 && StringUtils.isNumeric(code.substring(1))) {
			if (state.getIgnored().test(code))
				return null;

			msg = Messages.warnings.get(code);
		}
		// PORT INFO: test regexp was replaced with straight check
		else if (code.startsWith("E") && code.length() == 4 && StringUtils.isNumeric(code.substring(1))) {
			msg = Messages.errors.get(code);
		}
		// PORT INFO: test regexp was replaced with straight check
		else if (code.startsWith("I") && code.length() == 4 && StringUtils.isNumeric(code.substring(1))) {
			msg = Messages.info.get(code);
		}

		t = (t != null ? t : (state.nextToken() != null ? state.nextToken() : new Token()));
		if (t.getId().equals("(end)")) { // `~
			t = state.currToken();
		}

		int l = t.getLine();
		int ch = t.getFrom();

		LinterWarning w = new LinterWarning();
		w.setId("(error)");
		w.setRaw(msg);
		w.setCode(code);
		w.setEvidence((l > 0 && state.getLines().length > l - 1) ? state.getLines()[l - 1] : "");
		w.setLine(l);
		w.setCharacter(ch);
		w.setScope(scriptScope);
		w.setSubstitutions(substitutions);

		w.setReason(supplant(msg, w));
		this.errors.add(w);

		removeIgnoredMessages();

		List<LinterWarning> errors = this.errors.stream().filter(e -> e.getCode().startsWith("E")
				&& e.getCode().length() == 4 && StringUtils.isNumeric(e.getCode().substring(1))).collect(toList());
		if (state.getOption().test("maxerr") && errors.size() >= state.getOption().asInt("maxerr")) {
			quit("E043", t);
		}

		return w;
	}

	private LinterWarning warningAt(String m, int l, int ch, String... substitutions) {
		Token t = new Token();
		t.setLine(l);
		t.setFrom(ch);

		return warning(m, t, substitutions);
	}

	private void error(String m) {
		error(m, null);
	}

	private void error(String m, Token t, String... substitutions) {
		warning(m, t, substitutions);
	}

	private void errorAt(String m, int l) {
		errorAt(m, l, 0);
	}

	private void errorAt(String m, int l, int ch, String... substitutions) {
		Token t = new Token();
		t.setLine(l);
		t.setFrom(ch);
		error(m, t, substitutions);
	}

	// Tracking of "internal" scripts, like eval containing a static string
	private void addEvalCode(Token elem, Token token) {
		// PORT INFO: replace regexp was moved to Reg class
		internals.add(new InternalSource("(internal)", elem, token, Reg.unescapeNewlineChars(token.getValue())));
	}

	/**
	 * Process an inline linting directive.
	 * 
	 * @param directiveToken the directive-bearing comment token
	 * @param previous       the token that preceeds the directive
	 */
	private void lintingDirective(Token directiveToken, Token previous) {
		List<String> body = Splitter.on(",").splitToList(directiveToken.getBody()).stream()
				.map(s -> s.trim()).collect(Collectors.toList());
		Map<String, Boolean> predef = new HashMap<>();

		if (directiveToken.getType() == Token.Type.FALLS_THROUGH) {
			previous.setCaseFallsThrough(true);
			return;
		}

		if (directiveToken.getType() == Token.Type.GLOBALS) {
			for (int idx = 0; idx < body.size(); idx++) {
				String[] parts = body.get(idx).split(":", -1);
				String key = parts[0].trim();

				if (key.equals("-") || key.length() == 0) {
					// Ignore trailing comma
					if (idx > 0 && idx == body.size() - 1) {
						continue;
					}
					error("E002", directiveToken);
					continue;
				}

				if (key.startsWith("-")) {
					key = key.substring(1);

					blacklist.add(key);
					predefined.remove(key);
				} else {
					predef.put(key, parts.length > 1 && parts[1].trim().equals("true"));
				}
			}

			combine(predefined, predef);

			for (String key : predef.keySet()) {
				declared.put(key, directiveToken);
			}
		}

		if (directiveToken.getType() == Token.Type.EXPORTED) {
			for (int idx = 0; idx < body.size(); idx++) {
				String e = body.get(idx);
				if (StringUtils.isEmpty(e)) {
					// Ignore trailing comma
					if (idx > 0 && idx == body.size() - 1) {
						continue;
					}
					error("E002", directiveToken);
					continue;
				}

				state.getFunct().getScope().addExported(e);
			}
		}

		if (directiveToken.getType() == Token.Type.MEMBERS) {
			if (membersOnly == null)
				membersOnly = new HashMap<>();

			for (String m : body) {
				char ch1 = m.charAt(0);
				char ch2 = m.charAt(m.length() - 1);

				if (ch1 == ch2 && (ch1 == '"' || ch1 == '\'')) {
					m = StringUtils.replace(m.substring(1, m.length() - 1), "\\\"", "\"");
				}

				membersOnly.put(m, false);
			}
		}

		Set<String> numvals = new HashSet<>();
		numvals.add("maxstatements");
		numvals.add("maxparams");
		numvals.add("maxdepth");
		numvals.add("maxcomplexity");
		numvals.add("maxerr");
		numvals.add("maxlen");
		numvals.add("indent");

		if (directiveToken.getType() == Token.Type.JSHINT || directiveToken.getType() == Token.Type.JSLINT ||
				directiveToken.getType() == Token.Type.JSHINT_UNSTABLE) {
			for (int idx = 0; idx < body.size(); idx++) {
				String[] parts = body.get(idx).split(":", -1);
				String key = parts[0].trim();
				String val = parts.length > 1 ? parts[1].trim() : "";
				Double numberVal;

				if (!checkOption(key, directiveToken.getType() != Token.Type.JSHINT_UNSTABLE, directiveToken)) {
					continue;
				}

				if (numvals.contains(key)) {
					// GH988 - numeric options can be disabled by setting them to `false`
					if (!val.equals("false")) {
						try {
							numberVal = Double.valueOf(val);

							if (numberVal.isNaN() || numberVal.isInfinite() ||
									numberVal <= 0 || Math.floor(numberVal) != numberVal) {
								error("E032", directiveToken, val);
								continue;
							}
						} catch (NumberFormatException e) {
							error("E032", directiveToken, val);
							continue;
						}

						state.getOption().set(key, numberVal);
					} else {
						state.getOption().set(key, key.equals("indent") ? 4 : false);
					}

					continue;
				}

				if (key.equals("validthis")) {
					// `validthis` is valid only within a function scope.

					if (state.getFunct().isGlobal()) {
						error("E009");
						continue;
					}

					if (!val.equals("true") && !val.equals("false")) {
						error("E002", directiveToken);
						continue;
					}

					state.getOption().set("validthis", val.equals("true"));
					continue;
				}

				if (key.equals("quotmark")) {
					switch (val) {
						case "true":
						case "false":
							state.getOption().set("quotmark", val.equals("true"));
							break;
						case "double":
						case "single":
							state.getOption().set("quotmark", val);
							break;
						default:
							error("E002", directiveToken);
					}
					continue;
				}

				if (key.equals("shadow")) {
					switch (val) {
						case "true":
							state.getOption().set("shadow", true);
							break;
						case "outer":
							state.getOption().set("shadow", "outer");
							break;
						case "false":
						case "inner":
							state.getOption().set("shadow", "inner");
							break;
						default:
							error("E002", directiveToken);
					}
					continue;
				}

				if (key.equals("unused")) {
					switch (val) {
						case "true":
							state.getOption().set("unused", true);
							break;
						case "false":
							state.getOption().set("unused", false);
							break;
						case "vars":
						case "strict":
							state.getOption().set("unused", val);
							break;
						default:
							error("E002", directiveToken);
					}
					continue;
				}

				if (key.equals("latedef")) {
					switch (val) {
						case "true":
							state.getOption().set("latedef", true);
							break;
						case "false":
							state.getOption().set("latedef", false);
							break;
						case "nofunc":
							state.getOption().set("latedef", "nofunc");
							break;
						default:
							error("E002", directiveToken);
					}
					continue;
				}

				if (key.equals("ignore")) {
					switch (val) {
						case "line":
							state.getIgnoredLines().put(directiveToken.getLine(), true);
							removeIgnoredMessages();
							break;
						default:
							error("E002", directiveToken);
					}
					continue;
				}

				if (key.equals("strict")) {
					switch (val) {
						case "true":
							state.getOption().set("strict", true);
							break;
						case "false":
							state.getOption().set("strict", false);
							break;
						case "global":
						case "implied":
							state.getOption().set("strict", val);
							break;
						default:
							error("E002", directiveToken);
					}
					continue;
				}

				if (key.equals("module")) {
					/**
					 * JSHINT_TODO: Extend this restriction to *all* "environmental" options.
					 */
					if (!hasParsedCode(state.getFunct())) {
						error("E055", directiveToken, "module");
					}
				}

				if (key.equals("esversion")) {
					switch (val) {
						case "3":
						case "5":
						case "6":
						case "7":
						case "8":
						case "9":
						case "10":
						case "11":
							state.getOption().set("moz", false);
							state.getOption().set("esversion", Ints.tryParse(val));
							break;
						case "2015":
						case "2016":
						case "2017":
						case "2018":
						case "2019":
						case "2020":
							state.getOption().set("moz", false);
							// Translate specification publication year to version number.
							state.getOption().set("esversion", Ints.tryParse(val) - 2009);
							break;
						default:
							error("E002", directiveToken);
					}
					if (!hasParsedCode(state.getFunct())) {
						error("E055", directiveToken, "esversion");
					}
					continue;
				}

				if (Reg.isOptionMessageCode(key)) // PORT INFO: exec regexp was moved to Reg class
				{
					// ignore for -W..., unignore for +W...
					state.getIgnored().set(key.substring(1), key.startsWith("-"));
					continue;
				}

				if (val.equals("true") || val.equals("false")) {
					if (directiveToken.getType() == Token.Type.JSLINT) {
						String tn = Options.renamed.get(key);
						if (tn == null)
							tn = key;
						state.getOption().set(tn, val.equals("true"));

						if (Options.inverted.get(tn) != null) {
							state.getOption().set(tn, !state.getOption().get(tn).test());
						}
					} else if (directiveToken.getType() == Token.Type.JSHINT_UNSTABLE) {
						state.getOption().get("unstable").set(key, val.equals("true"));
					} else {
						state.getOption().set(key, val.equals("true"));
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
	private Token peek() {
		return peek(0);
	}

	/**
	 * Return a token beyond the token available in `state.tokens.next`. If no
	 * such token exists, return the "(end)" token. This function is used to
	 * determine parsing strategies in cases where the value of the next token
	 * does not provide sufficient information, as is the case with `for` loops,
	 * e.g.:
	 * 
	 * for ( var i in ...
	 * 
	 * 
	 * versus:
	 * 
	 * for ( var i = ..
	 * 
	 * @param p offset of desired token; defaults to 0
	 * @return next token
	 */
	private Token peek(int p) {
		int i = p;
		int j = lookahead.size();
		Token t = null;

		if (i < j) {
			return lookahead.get(i);
		}

		while (j <= i) {
			t = lex.token();

			// When the lexer is exhausted, this function should produce the "(end)"
			// token, even in cases where the requested token is beyond the end of
			// the input stream.
			if (t == null) {
				// If the lookahead buffer is empty, the expected "(end)" token was
				// already emitted by the most recent invocation of `advance` and is
				// available as the next token.
				if (lookahead.size() == 0) {
					return state.nextToken();
				}

				return lookahead.get(j - 1);
			}

			lookahead.add(t);
			j += 1;
		}

		return t;
	}

	private Token peekIgnoreEOL() {
		int i = 0;
		Token t = null;
		do {
			t = peek(i++);
		} while (t != null && t.getId().equals("(endline)"));

		return t;
	}

	/**
	 * Consume the next token.
	 */
	private void advance() {
		advance(null, null);
	}

	/**
	 * Consume the next token.
	 *
	 * @param expected - the expected value of the next token's `id`
	 *                 property (in the case of punctuators) or
	 *                 `value` property (in the case of identifiers
	 *                 and literals); if unspecified, any token will
	 *                 be accepted
	 */
	private void advance(String expected) {
		advance(expected, null);
	}

	/**
	 * Consume the next token.
	 *
	 * @param expected     the expected value of the next token's `id`
	 *                     property (in the case of punctuators) or
	 *                     `value` property (in the case of identifiers
	 *                     and literals); if unspecified, any token will
	 *                     be accepted
	 * @param relatedToken the token that informed the expected
	 *                     value, if any (for example: the opening
	 *                     brace when a closing brace is expected);
	 *                     used to produce more meaningful errors
	 */
	private void advance(String expected, Token relatedToken) {
		Token nextToken = state.nextToken();

		if (StringUtils.isNotEmpty(expected) && !nextToken.getId().equals(expected)) {
			if (relatedToken != null) {
				if (nextToken.getId().equals("(end)")) {
					error("E019", relatedToken, relatedToken.getId());
				} else {
					error(
							"E020",
							nextToken,
							expected,
							relatedToken.getId(),
							String.valueOf(relatedToken.getLine()),
							nextToken.getValue());
				}
			} else if (nextToken.getType() != Token.Type.IDENTIFIER || !nextToken.getValue().equals(expected)) {
				warning("E021", nextToken, expected, nextToken.getValue());
			}
		}

		state.setPrevToken(state.currToken());
		state.setCurrToken(state.nextToken());
		for (;;) {
			state.setNextToken(lookahead.size() > 0 ? lookahead.remove(0) : null);
			if (state.nextToken() == null)
				state.setNextToken(lex.token());

			if (state.nextToken() == null) // No more tokens left, give up
			{
				quit("E041", state.currToken());
			}

			if (state.nextToken().getId().equals("(end)") || state.nextToken().getId().equals("(error)")) {
				return;
			}

			if (state.nextToken().getCheck() != null) {
				state.nextToken().check();
			}

			if (state.nextToken().isSpecial()) {
				lintingDirective(state.nextToken(), state.currToken());
			} else {
				if (!state.nextToken().getId().equals("(endline)")) {
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
	private boolean isOperator(Token token) {
		return token.getFirstToken() != null || token.getRight() != null || token.getLeft() != null
				|| token.getId().equals("yield") || token.getId().equals("await");
	}

	private boolean isEndOfExpr() {
		return isEndOfExpr(0, state.currToken(), state.nextToken());
	}

	private boolean isEndOfExpr(int context) {
		return isEndOfExpr(context, state.currToken(), state.nextToken());
	}

	private boolean isEndOfExpr(int context, Token curr, Token next) {
		if (next.getId().equals("in") && (context & ProdParams.NOIN) != 0) {
			return true;
		}

		if (next.getId().equals(";") || next.getId().equals("}") || next.getId().equals(":")) {
			return true;
		}

		if (next.isInfix() == curr.isInfix() ||
		// Infix operators which follow `yield` should only be consumed as part
		// of the current expression if allowed by the syntactic grammar. In
		// effect, this prevents automatic semicolon insertion when `yield` is
		// followed by a newline and a comma operator (without enabling it when
		// `yield` is followed by a newline and a `[` token).
				(curr.getId().equals("yield") && curr.getRbp() < next.getRbp())) {
			return !sameLine(curr, next);
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
	 * .nud Null denotation
	 * .fud First null denotation
	 * .led Left denotation
	 * lbp Left binding power
	 * rbp Right binding power
	 *
	 * They are elements of the parsing method called Top Down Operator Precedence.
	 *
	 * In addition to parsing, this function applies a number of linting patterns.
	 *
	 * @param context - the parsing context (a bitfield describing
	 *                conditions of the current parsing operation
	 *                which can influence how the next tokens are
	 *                interpreted); see `prod-params.js` for more
	 *                detail)
	 * @param rbp     - the right-binding power of the token to be consumed
	 */
	private Token expression(int context, int rbp) {
		Token left = null;
		boolean isArray = false;
		boolean isObject = false;
		boolean initial = (context & ProdParams.INITIAL) != 0;

		context &= ~ProdParams.INITIAL;

		state.getNameStack().push();

		if (state.nextToken().getId().equals("(end)"))
			error("E006", state.currToken());

		advance();

		if (initial) {
			state.getFunct().setVerb(state.currToken().getValue());
			state.currToken().setBeginsStmt(true);
		}

		Token curr = state.currToken();

		if (initial && curr.getFud() != null && (curr.getUseFud() == null || curr.useFud(context))) {
			left = state.currToken().fud(context);
		} else {
			if (state.currToken().getNud() != null) {
				left = state.currToken().nud(context, rbp);
			} else {
				error("E030", state.currToken(), state.currToken().getId());
			}

			while (rbp < state.nextToken().getLbp() && !isEndOfExpr(context)) {
				isArray = state.currToken().getValue().equals("Array");
				isObject = state.currToken().getValue().equals("Object");

				// #527, new Foo.Array(), Foo.Array(), new Foo.Object(), Foo.Object()
				// Line breaks in IfStatement heads exist to satisfy the checkJSHint
				// "Line too long." error.
				if (left != null && (StringUtils.isNotEmpty(left.getValue())
						|| (left.getFirstToken() != null && StringUtils.isNotEmpty(left.getFirstToken().getValue())))) {
					// If the left.value is not "new", or the left.first.value is a "."
					// then safely assume that this is not "new Array()" and possibly
					// not "new Object()"...
					if (!left.getValue().equals("new") ||
							(left.getFirstToken() != null && StringUtils.isNotEmpty(left.getFirstToken().getValue())
									&& left.getFirstToken().getValue().equals("."))) {
						isArray = false;
						// ...In the case of Object, if the left.value and state.currToken().value
						// are not equal, then safely assume that this not "new Object()"
						if (!left.getValue().equals(state.currToken().getValue())) {
							isObject = false;
						}
					}
				}

				advance();

				if (isArray && state.currToken().getId().equals("(")
						&& state.nextToken().getId().equals(")")) {
					warning("W009", state.currToken());
				}

				if (isObject && state.currToken().getId().equals("(")
						&& state.nextToken().getId().equals(")")) {
					warning("W010", state.currToken());
				}

				if (left != null && state.currToken().getLed() != null) {
					left = state.currToken().led(context, left);
				} else {
					error("E033", state.currToken(), state.currToken().getId());
				}
			}
		}

		state.getNameStack().pop();

		return left;
	}

	// Functions for conformance of style.

	private boolean sameLine(Token first, Token second) {
		return first.getLine() == (second.getStartLine() > 0 ? second.getStartLine() : second.getLine());
	}

	private void nobreaknonadjacent(Token left, Token right) {
		if (!state.getOption().get("laxbreak").test() && !sameLine(left, right)) {
			warning("W014", right, right.getValue());
		}
	}

	private void nolinebreak(Token t) {
		if (!sameLine(t, state.nextToken())) {
			warning("E022", t, t.getValue());
		}
	}

	private boolean checkCommaFirst = false;

	/**
	 * Validate the comma token in the "current" position of the token stream.
	 *
	 * @returns flag indicating the validity of the current comma
	 *          token; `false` if the token directly causes a syntax
	 *          error, `true` otherwise
	 */
	private boolean checkComma() {
		return checkComma(false, false);
	}

	/**
	 * Validate the comma token in the "current" position of the token stream.
	 *
	 * @param property      flag indicating whether the current
	 *                      comma token is situated directly within
	 *                      an object initializer
	 * @param allowTrailing flag indicating whether the
	 *                      current comma token may appear
	 *                      directly before a delimiter
	 *
	 * @returns flag indicating the validity of the current comma
	 *          token; `false` if the token directly causes a syntax
	 *          error, `true` otherwise
	 */
	private boolean checkComma(boolean property, boolean allowTrailing) {
		Token prev = state.prevToken();
		Token curr = state.currToken();
		if (!sameLine(prev, curr)) {
			if (!state.getOption().test("laxcomma")) {
				if (checkCommaFirst) {
					warning("I001", curr);
					checkCommaFirst = false;
				}
				warning("W014", prev, curr.getValue());
			}
		}

		if (state.nextToken().isIdentifier() && !(property && state.inES5())) {
			// Keywords that cannot follow a comma operator.
			switch (state.nextToken().getValue()) {
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
					error("E024", state.nextToken(), state.nextToken().getValue());
					return false;
			}
		}

		if (state.nextToken().getType() == Token.Type.PUNCTUATOR) {
			switch (state.nextToken().getValue()) {
				case "}":
				case "]":
				case ",":
				case ")":
					if (allowTrailing) {
						return true;
					}

					error("E024", state.nextToken(), state.nextToken().getValue());
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
	 *          delimiters (e.g.. `[`), this is the token's text
	 *          representation; for literals (e.g. numbers) and other
	 *          "special" tokens (e.g. the end-of-file marker) this is
	 *          a parenthetical value
	 * @param p - the left-binding power of the token as used by the
	 *          Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token symbol(String s, int p) {
		Token x = state.getSyntax().get(s);
		if (x == null) {
			// Symbols that accept a right-hand side do so with a binding power
			// that is commonly identical to their left-binding power. (This value
			// is relevant when determining if the grouping operator is necessary
			// to override the precedence of surrounding operators.) Because the
			// exponentiation operator's left-binding power and right-binding power
			// are distinct, the values must be encoded separately.
			state.getSyntax().put(s, x = new Token(s, p, p, s));
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
	private Token delim(String s) {
		Token x = symbol(s, 0);
		x.setDelim(true);
		return x;
	}

	/**
	 * Convenience function for defining statement-denoting symbols.
	 *
	 * @param s - the name of the symbol
	 * @param f - the first null denotation function for the symbol;
	 *          see the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token stmt(String s, Function<Token, IntFunction<Token>> f) {
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
	 *          the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token blockstmt(String s, Function<Token, IntFunction<Token>> f) {
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
	private Token reserveName(Token x) {
		char c = x.getId().charAt(0);
		if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
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
	private Token prefix(String s, String f) {
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
	private Token prefix(String s) {
		return prefix(s, _this -> context -> rbp -> {
			_this.setArity(Token.ArityType.UNARY);
			_this.setRight(expression(context, 150));

			if (_this.getId().equals("++") || _this.getId().equals("--")) {
				if (state.getOption().test("plusplus")) {
					warning("W016", _this, _this.getId());
				}

				if (_this.getRight() != null) {
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
	 *          see the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token prefix(String s, Function<Token, IntFunction<IntFunction<Token>>> f) {
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
	 *          see the `expression` function for more detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token type(Token.Type s, Function<Token, IntFunction<IntFunction<Token>>> f) {
		Token x = symbol(s.toString(), 0);
		x.setType(s);
		x.setNud(f);
		return x;
	}

	/**
	 * Convenience function for defining JSHint symbols for reserved
	 * keywords--those that are restricted from use as bindings (and as property
	 * names in ECMAScript 3 environments).
	 *
	 * @param name - the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token reserve(Token.Type name) {
		return reserve(name, null);
	}

	/**
	 * Convenience function for defining JSHint symbols for reserved
	 * keywords--those that are restricted from use as bindings (and as propery
	 * names in ECMAScript 3 environments).
	 *
	 * @param name - the name of the symbol
	 * @param func - the first null denotation function for the
	 *             symbol; see the `expression` function for more
	 *             detail
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token reserve(Token.Type name, Function<Token, IntFunction<IntFunction<Token>>> func) {
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
	private Token futureReservedWord(Token.Type name) {
		return futureReservedWord(name, null);
	}

	/**
	 * Convenience function for defining JSHint symbols for keywords that are
	 * only reserved in some circumstances.
	 *
	 * @param name - the name of the symbol
	 * @param meta - a collection of optional arguments: <br/>
	 *             meta.nud - the null denotation function for the symbol;
	 *             see the `expression` function for more detail <br/>
	 *             meta.es5 - `true` if the identifier is reserved
	 *             in ECMAScript 5 or later <br/>
	 *             meta.strictOnly - `true` if the identifier is only
	 *             reserved in strict mode code.
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token futureReservedWord(Token.Type name, Token.Meta meta) {
		Token x = type(name, state.getSyntax().get("(identifier)").getNud());

		if (meta == null)
			meta = new Token.Meta();
		meta.setFutureReservedWord(true);

		x.setValue(name.toString());
		x.setIdentifier(true);
		x.setReserved(true);
		x.setMeta(meta);

		return x;
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
	private Token infix(String s) {
		return infix(s, null, 0, false);
	}

	/**
	 * Convenience function for defining "infix" symbols--operators that require
	 * operands as both "land-hand side" and "right-hand side".
	 *
	 * @param s - the name of the symbol
	 * @param f - string value that just describes execution case
	 * @param p - the left-binding power of the token as used by the
	 *          Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token infix(String s, String f, int p) {
		return infix(s, null, p, false);
	}

	/**
	 * Convenience function for defining "infix" symbols--operators that require
	 * operands as both "land-hand side" and "right-hand side".
	 *
	 * @param s - the name of the symbol
	 * @param f - a function to be invoked that consumes the
	 *          right-hand side of the operator
	 * @param p - the left-binding power of the token as used by the
	 *          Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token infix(String s, IntFunction<BiFunction<Token, Token, Token>> f, int p) {
		return infix(s, f, p, false);
	}

	/**
	 * Convenience function for defining "infix" symbols--operators that require
	 * operands as both "land-hand side" and "right-hand side".
	 *
	 * @param s - the name of the symbol
	 * @param f - a function to be invoked that consumes the
	 *          right-hand side of the operator
	 * @param p - the left-binding power of the token as used by the
	 *          Pratt parsing semantics
	 * @param w - if `true`
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token infix(String s, IntFunction<BiFunction<Token, Token, Token>> f, int p, boolean w) {
		Token x = symbol(s, p);
		reserveName(x);
		x.setInfix(true);
		x.setLed(_this -> context -> left -> {
			if (!w) {
				nobreaknonadjacent(state.prevToken(), state.currToken());
			}
			if ((s.equals("in") || s.equals("instanceof")) && left.getId().equals("!")) {
				warning("W018", left, "!");
			}
			if (f != null) {
				return f.apply(context).apply(left, _this);
			} else {
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
	private Token application(String s) {
		Token x = symbol(s, 42);

		x.setInfix(true);
		x.setLed(_this -> context -> left -> {
			nobreaknonadjacent(state.prevToken(), state.currToken());

			_this.setLeft(left);
			_this.setRight(
					doFunction(
							context,
							null,
							null,
							FunctionType.ARROW,
							left,
							false,
							null,
							false,
							false));
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
	private Token relation(String s) {
		return relation(s, null);
	}

	/**
	 * Convenience function for defining JSHint symbols for relation operators.
	 *
	 * @param s - the name of the symbol
	 * @param f - a function to be invoked to enforce any additional
	 *          linting rules.
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token relation(String s, Function<Token, IntFunction<BiFunction<Token, Token, Token>>> f) {
		Token x = symbol(s, 100);

		x.setInfix(true);
		x.setLed(_this -> context -> left -> {
			nobreaknonadjacent(state.prevToken(), state.currToken());
			_this.setLeft(left);
			_this.setRight(expression(context, 100));
			Token right = _this.getRight();
			if (isIdentifier(left, "NaN") || isIdentifier(right, "NaN")) {
				warning("W019", _this);
			} else if (f != null) {
				f.apply(_this).apply(context).apply(left, right);
			}

			if (left == null || right == null) {
				quit("E041", state.currToken());
			}

			if (left.getId().equals("!")) {
				warning("W018", left, "!");
			}

			if (right.getId().equals("!")) {
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
	private boolean beginsUnaryExpression(Token token) {
		return token.getArity() == Token.ArityType.UNARY && !token.getId().equals("++") && !token.getId().equals("--");
	}

	private static Map<String, List<String>> typeofValues = new HashMap<String, List<String>>();
	static {
		typeofValues.put(
				"legacy",
				ImmutableList.<String>builder()
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
		typeofValues.put(
				"es3",
				ImmutableList.<String>builder()
						.add("undefined")
						.add("boolean")
						.add("number")
						.add("string")
						.add("function")
						.add("object")
						.addAll(typeofValues.get("legacy")).build());
		typeofValues.put(
				"es6",
				ImmutableList.<String>builder()
						.addAll(typeofValues.get("es3"))
						.add("symbol").add("bigint").build());
	}

	/**
	 * Validate comparisons between the result of a `typeof` expression and a
	 * string literal.
	 *
	 * @param left  - one of the values being compared
	 * @param right - the other value being compared
	 *
	 * @return `false` if the second token describes a `typeof`
	 *         expression and the first token is a string literal
	 *         whose value is never returned by that operator;
	 *         `true` otherwise
	 */
	private boolean isTypoTypeof(Token left, Token right) {
		if (state.getOption().test("notypeof"))
			return false;

		if (left == null || right == null)
			return false;

		List<String> values = state.inES6() ? typeofValues.get("es6") : typeofValues.get("es3");

		if (right.getType() == Token.Type.IDENTIFIER && right.getValue().equals("typeof")
				&& left.getType() == Token.Type.STRING) {
			if (left.getValue().equals("bigint")) {
				if (!state.inES11()) {
					warning("W119", left, "BigInt", "11");
				}

				return false;
			}

			return !values.contains(left.getValue());
		}

		return false;
	}

	/**
	 * Determine if a given token describes the built-in `eval` function.
	 *
	 * @param left
	 *
	 * @return
	 */
	private boolean isGlobalEval(Token left) // JSHINT_BUG: unused variable state here
	{
		boolean isGlobal = false;

		// permit methods to refer to an "eval" key in their own context
		if (left.getType() == Token.Type.THIS && state.getFunct().getContext() == null) {
			isGlobal = true;
		}

		// permit use of "eval" members of objects
		else if (left.getType() == Token.Type.IDENTIFIER) {
			if (state.getOption().test("node") && left.getValue().equals("global")) {
				isGlobal = true;
			}

			else if (state.getOption().test("browser")
					&& (left.getValue().equals("window") || left.getValue().equals("document"))) {
				isGlobal = true;
			}
		}

		return isGlobal;
	}

	private Token walkPrototype(Token obj) {
		if (obj == null)
			return null;
		return obj.getRight() != null && obj.getRight().getValue().equals("prototype") ? obj
				: walkPrototype(obj.getLeft());
	}

	private String walkNative(Token obj) {
		Set<String> natives = new HashSet<>();
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
				state.getFunct().getScope().isPredefined(obj.getValue())) {
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
	private String findNativePrototype(Token left) {
		Token prototype = walkPrototype(left);
		if (prototype != null)
			return walkNative(prototype);
		return null;
	}

	/**
	 * Checks the left hand side of an assignment for issues, returns if ok
	 * Determine if the given token is a valid assignment target; emit errors
	 * and/or warnings as appropriate
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                more information
	 * @param left    - the left hand side of the assignment
	 *
	 * @return Whether the left hand side is OK
	 */
	private boolean checkLeftSideAssign(int context, Token left) {
		return checkLeftSideAssign(context, left, null, false);
	}

	/**
	 * Checks the left hand side of an assignment for issues, returns if ok
	 * Determine if the given token is a valid assignment target; emit errors
	 * and/or warnings as appropriate
	 *
	 * @param context     - the parsing context; see `prod-params.js` for
	 *                    more information
	 * @param left        - the left hand side of the assignment
	 * @param assignToken - the token for the assignment, used for
	 *                    reporting
	 *
	 * @return Whether the left hand side is OK
	 */
	private boolean checkLeftSideAssign(int context, Token left, Token assignToken) {
		return checkLeftSideAssign(context, left, assignToken, false);
	}

	/**
	 * Checks the left hand side of an assignment for issues, returns if ok
	 * Determine if the given token is a valid assignment target; emit errors
	 * and/or warnings as appropriate
	 *
	 * @param context            - the parsing context; see `prod-params.js` for
	 *                           more information
	 * @param left               - the left hand side of the assignment
	 * @param assignToken        - the token for the assignment, used for
	 *                           reporting
	 * @param allowDestructuring - whether to allow destructuring binding
	 *
	 * @return Whether the left hand side is OK
	 */
	private boolean checkLeftSideAssign(int context, Token left, Token assignToken, boolean allowDestructuring) {
		assignToken = ObjectUtils.defaultIfNull(assignToken, left);

		if (state.getOption().test("freeze")) {
			String nativeObject = findNativePrototype(left);
			if (nativeObject != null)
				warning("W121", left, nativeObject);
		}

		if (left.isIdentifier() && !left.isMetaProperty()) {
			// The `reassign` method also calls `modify`, but we are specific in
			// order to catch function re-assignment and globals re-assignment
			state.getFunct().getScope().getBlock().reassign(left.getValue(), left);
		}

		if (left.getId().equals(".")) {
			if (left.getLeft() == null || left.getLeft().getValue().equals("arguments") && !state.isStrict()) {
				warning("W143", assignToken);
			}

			state.getNameStack().set(state.prevToken());
			return true;
		} else if (left.getId().equals("{") || left.getId().equals("[")) {
			if (!allowDestructuring || left.getDestructAssign() == null) {
				if (left.getId().equals("{") || left.getLeft() == null) {
					warning("E031", assignToken);
				} else if (left.getLeft().getValue().equals("arguments") && !state.isStrict()) {
					warning("W143", assignToken);
				}
			}

			if (left.getId().equals("[")) {
				state.getNameStack().set(left.getRight());
			}

			return true;
		} else if (left.isIdentifier() && !isReserved(context, left) && !left.isMetaProperty()) {
			if (Objects.equals(state.getFunct().getScope().bindingtype(left.getValue()), "exception")) {
				warning("W022", left);
			}

			if (left.getValue().equals("eval") && state.isStrict()) {
				error("E031", assignToken);
				return false;
			} else if (left.getValue().equals("arguments")) {
				if (!state.isStrict()) {
					warning("W143", assignToken);
				} else {
					error("E031", assignToken);
					return false;
				}
			}
			state.getNameStack().set(left);
			return true;
		}

		error("E031", assignToken);

		return false;
	}

	/**
	 * Convenience function for defining JSHint symbols for assignment operators.
	 *
	 * @param s the name of the symbol
	 * @param f a function to be invoked that consumes the
	 *          right-hand side of the operator (see the `infix`
	 *          function)
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token assignop(String s, IntFunction<BiFunction<Token, Token, Token>> f) {
		Token x = infix(s, f, 20);

		x.setExps(true);
		x.setAssign(true);

		return x;
	}

	/**
	 * Convenience function for defining JSHint symbols for assignment operators.
	 *
	 * @param s the name of the symbol
	 * @param f string that just describes execution use case
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token assignop(String s, String f) {
		return assignop(s, context -> (left, that) -> {
			that.setLeft(left);

			checkLeftSideAssign(context, left, that, true);

			that.setRight(expression(context, 10));

			return that;
		});
	}

	/**
	 * Convenience function for defining JSHint symbols for bitwise operators.
	 *
	 * @param s the name of the symbol
	 * @param f string that just describes execution use case
	 * @param p the left-binding power of the token as used by the
	 *          Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token bitwise(String s, String f, int p) {
		return bitwise(s, _this -> context -> left -> {
			if (state.getOption().test("bitwise")) {
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
	 * @param s the name of the symbol
	 * @param f the left denotation function for the symbol; see
	 *          the `expression` function for more detail
	 * @param p the left-binding power of the token as used by the
	 *          Pratt parsing semantics
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token bitwise(String s, Function<Token, IntFunction<Function<Token, Token>>> f, int p) {
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
	 * @param s the name of the symbol
	 *
	 * @return the object describing the JSHint symbol (provided to
	 *         support cases where further refinement is necessary)
	 */
	private Token bitwiseassignop(String s) {
		symbol(s, 20).setExps(true);
		return infix(s, context -> (left, that) -> {
			if (state.getOption().test("bitwise")) {
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
	private Token suffix(String s) {
		Token x = symbol(s, 150);
		x.setLed(_this -> context -> left -> {
			// this = suffix e.g. "++" punctuator
			// left = symbol operated e.g. "a" identifier or "a.b" punctuator
			if (state.getOption().test("plusplus")) {
				warning("W016", _this, _this.getId());
			}

			checkLeftSideAssign(context, left, _this);

			_this.setLeft(left);
			return _this;
		});
		return x;
	}

	/**
	 * Retrieve the value of the next token if it is an identifier and optionally
	 * advance the parser.
	 *
	 * @param context  the parsing context; see `prod-params.js` for
	 *                 more information
	 * @param isName   `true` if an IdentifierName should be consumed
	 *                 (e.g. object properties)
	 * @param preserve `true` if the token should not be consumed
	 *
	 * @return the value of the identifier, if present
	 */
	private String optionalidentifier(int context, boolean isName, boolean preserve) {
		if (!state.nextToken().isIdentifier()) {
			return null;
		}

		if (!preserve) {
			advance();
		}

		Token curr = state.currToken();
		if (isReserved(context, curr) && !(isName && state.inES5())) {
			warning("W024", state.currToken(), state.currToken().getId());
		}

		return curr.getValue();
	}

	/**
	 * Retrieve the value of the next token if it is an identifier and optionally
	 * advance the parser.
	 *
	 * @param context the parsing context; see `prod-params.js` for
	 *                more information
	 * @param isName  `true` if an IdentifierName should be consumed
	 *                (e.g. object properties)
	 *
	 * @return the value of the identifier, if present
	 */
	private String optionalidentifier(int context, boolean isName) {
		return optionalidentifier(context, isName, false);
	}

	/**
	 * Retrieve the value of the next token if it is an identifier and optionally
	 * advance the parser.
	 *
	 * @param context the parsing context; see `prod-params.js` for
	 *                more information
	 *
	 * @return the value of the identifier, if present
	 */
	private String optionalidentifier(int context) {
		return optionalidentifier(context, false, false);
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
	private boolean spreadrest(String operation) {
		if (!checkPunctuator(state.nextToken(), "...")) {
			return false;
		}

		if (!state.inES6(true)) {
			warning("W119", state.nextToken(), operation + " operator", "6");
		}
		advance();

		if (checkPunctuator(state.nextToken(), "...")) {
			warning("E024", state.nextToken(), "...");
			while (checkPunctuator(state.nextToken(), "...")) {
				advance();
			}
		}

		return true;
	}

	/**
	 * Ensure that the current token is an identifier and retrieve its value.
	 *
	 * @param context the parsing context; see `prod-params.js` for
	 *                more information
	 * @param isName  `true` if an IdentifierName should be consumed
	 *                (e.g. object properties
	 *
	 * @return the value of the identifier, if present
	 */
	private String identifier(int context, boolean isName) {
		String i = optionalidentifier(context, isName, false);
		if (StringUtils.isNotEmpty(i)) {
			return i;
		}

		error("E030", state.nextToken(), state.nextToken().getValue());

		// The token should be consumed after a warning is issued so the parser
		// can continue as though an identifier were found. The semicolon token
		// should not be consumed in this way so that the parser interprets it as
		// a statement delimiter;
		if (!state.nextToken().getId().equals(";")) {
			advance();
		}

		return null;
	}

	/**
	 * Ensure that the current token is an identifier and retrieve its value.
	 *
	 * @param context the parsing context; see `prod-params.js` for
	 *                more information
	 *
	 * @return the value of the identifier, if present
	 */
	private String identifier(int context) {
		return identifier(context, false);
	}

	/**
	 * Determine if the provided token may be evaluated and emit a linting
	 * warning if this is note the case.
	 *
	 * @param controlToken
	 */
	private void reachable(Token controlToken) {
		int i = 0;
		Token t = null;

		if (!state.nextToken().getId().equals(";") || controlToken.inBracelessBlock()) {
			return;
		}
		for (;;) {
			do {
				t = peek(i);
				i += 1;
			} while (!t.getId().equals("(end)") && t.getId().equals("(comment)"));

			if (t.isReach()) {
				return;
			}
			if (!t.getId().equals("(endline)")) {
				if (t.getId().equals("function")) {
					if (state.getOption().get("latedef").equals(true)) {
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
	private void parseFinalSemicolon(Token stmt) {
		if (!state.nextToken().getId().equals(";")) {
			// don't complain about unclosed templates / strings
			if (state.nextToken().isUnclosed()) {
				advance();
				return;
			}

			boolean isSameLine = sameLine(state.currToken(), state.nextToken()) &&
					!state.nextToken().getId().equals("(end)");
			boolean blockEnd = checkPunctuator(state.nextToken(), "}");

			if (isSameLine && !blockEnd && !(stmt.getId().equals("do") && state.inES6(true))) {
				errorAt("E058", state.currToken().getLine(), state.currToken().getCharacter());
			} else if (!state.getOption().test("asi")) {

				// If this is the last statement in a block that ends on the same line
				// *and* option lastsemic is on, ignore the warning. Otherwise, issue
				// a warning about missing semicolon.
				if (!(blockEnd && isSameLine && state.getOption().test("lastsemic"))) {
					warningAt("W033", state.currToken().getLine(), state.currToken().getCharacter());
				}
			}
		} else {
			advance(";");
		}
	}

	/**
	 * Consume a statement.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                more information
	 *
	 * @return the token describing the statement
	 */
	private Token statement(int context) {
		int i = indent;
		Token t = state.nextToken();
		boolean hasOwnScope = false;

		context |= ProdParams.INITIAL;

		if (t.getId().equals(";")) {
			advance(";");
			return null;
		}

		// Is this a labelled statement?
		boolean res = isReserved(context, t);

		// We're being more tolerant here: if someone uses
		// a FutureReservedWord (that is not meant to start a statement)
		// as a label, we warn but proceed anyway.

		if (res && t.getMeta() != null && t.getMeta().isFutureReservedWord() && t.getFud() == null) {
			warning("W024", t, t.getId());
			res = false;
		}

		if (t.isIdentifier() && !res && peek().getId().equals(":")) {
			advance();
			advance(":");

			hasOwnScope = true;
			state.getFunct().getScope().stack();
			state.getFunct().getScope().getBlock().addLabel(t.getValue(), state.currToken());

			if (!state.nextToken().isLabelled() && !state.nextToken().getValue().equals("{")) {
				warning("W028", state.nextToken(), t.getValue(), state.nextToken().getValue());
			}

			t = state.nextToken();
		}

		// Is it a lonely block?

		if (t.getId().equals("{")) {
			// Is it a switch case block?
			//
			// switch (foo) {
			// case bar: { <= here.
			// ...
			// }
			// }
			boolean iscase = (state.getFunct().getVerb().equals("case")
					&& state.currToken().getValue().equals(":"));
			block(context, true, true, false, false, iscase);

			if (hasOwnScope) {
				state.getFunct().getScope().unstack();
			}

			return null;
		}

		// Parse the statement.

		Token r = expression(context, 0);

		if (r != null && !(r.isIdentifier() && r.getValue().equals("function")) &&
				!(r.getType() == Token.Type.PUNCTUATOR && r.getLeft() != null &&
						r.getLeft().isIdentifier() && r.getLeft().getValue().equals("function"))) {
			if (!state.isStrict() && state.stmtMissingStrict()) {
				warning("E007");
			}
		}

		// Look for the final semicolon.
		if (!t.isBlock()) {
			if (!state.getOption().get("expr").test() && (r == null || !r.isExps())) {
				warning("W030", state.currToken());
			} else if (state.getOption().get("nonew").test() && r != null && r.getLeft() != null
					&& r.getId().equals("(") && r.getLeft().getId().equals("new")) {
				warning("W031", t);
			}

			parseFinalSemicolon(t);
		}

		// Restore the indentation.

		indent = i;
		if (hasOwnScope) {
			state.getFunct().getScope().unstack();
		}
		return r;
	}

	/**
	 * Consume a series of statements until encountering either the end of the
	 * program or a token that interrupts control flow.
	 *
	 * @param context - the parsing context; see `prod-params.js` for
	 *                more information
	 *
	 * @return the tokens consumed
	 */
	private List<Token> statements(int context) {
		List<Token> a = new ArrayList<>();
		Token p;

		while (!state.nextToken().isReach() && !state.nextToken().getId().equals("(end)")) {
			if (state.nextToken().getId().equals(";")) {
				p = peek();

				if (p == null || (!p.getId().equals("(") && !p.getId().equals("["))) {
					warning("W032");
				}

				advance(";");
			} else {
				a.add(statement(context));
			}
		}

		return a;
	}

	/**
	 * Parse any directives in a directive prologue.
	 */
	private void directives() {
		Token current = state.nextToken();

		while (state.nextToken().getId().equals("(string)")) {
			Token next = peekIgnoreEOL();
			if (!isEndOfExpr(0, current, next)) {
				break;
			}
			current = next;

			advance();
			String directive = state.currToken().getValue();
			if (state.getDirective().get(directive) != null ||
					(directive.equals("use strict") && state.getOption().get("strict").equals("implied"))) {
				warning("W034", state.currToken(), directive);
			}

			// From ECMAScript 2016:
			//
			// > 14.1.2 Static Semantics: Early Errors
			// >
			// > [...]
			// > - It is a Syntax Error if ContainsUseStrict of FunctionBody is true
			// > and IsSimpleParameterList of FormalParameters is false.
			if (directive.equals("use strict") && state.inES7() &&
					!state.getFunct().isGlobal() && state.getFunct().hasSimpleParams() == false) {
				error("E065", state.currToken());
			}

			state.getDirective().put(directive, state.currToken());

			parseFinalSemicolon(current);
		}

		if (state.isStrict()) {
			state.getOption().set("undef", true);
		}
	}

	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context  - parsing context
	 * @param ordinary - `true` for everything but function bodies and
	 *                 try blocks
	 *
	 * @return the token describing the block
	 */
	private List<Token> block(int context, boolean ordinary) {
		return block(context, ordinary, false, false, false, false);
	}

	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context  - parsing context
	 * @param ordinary - `true` for everything but function bodies and
	 *                 try blocks
	 * @param stmt     - `true` if block can be a single statement (e.g.
	 *                 in if/for/while)
	 *
	 * @return the token describing the block
	 */
	private List<Token> block(int context, boolean ordinary, boolean stmt) {
		return block(context, ordinary, stmt, false, false, false);
	}

	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context    - parsing context
	 * @param ordinary   - `true` for everything but function bodies and
	 *                   try blocks
	 * @param stmt       - `true` if block can be a single statement (e.g.
	 *                   in if/for/while)
	 * @param isfunc     - `true` if block is a function body
	 * @param isfatarrow - `true` if its a body of a fat arrow
	 *                   function
	 *
	 * @return the token describing the block
	 */
	private List<Token> block(int context, boolean ordinary, boolean stmt, boolean isfunc, boolean isfatarrow) {
		return block(context, ordinary, stmt, isfunc, isfatarrow, false);
	}

	/**
	 * Parses a single block. A block is a sequence of statements wrapped in
	 * braces.
	 *
	 * @param context    - parsing context
	 * @param ordinary   - `true` for everything but function bodies and
	 *                   try blocks
	 * @param stmt       - `true` if block can be a single statement (e.g.
	 *                   in if/for/while)
	 * @param isfunc     - `true` if block is a function body
	 * @param isfatarrow - `true` if its a body of a fat arrow
	 *                   function
	 * @param iscase     - `true` if block is a switch case block
	 *
	 * @return the token describing the block
	 */
	// JSHINT_BUG: return can be a list of tokens
	private List<Token> block(int context, boolean ordinary, boolean stmt, boolean isfunc, boolean isfatarrow,
			boolean iscase) {
		List<Token> a = new ArrayList<>();
		boolean b = inblock;
		int oldIndent = indent;
		Map<String, Token> m = null;
		Token t;

		inblock = ordinary;

		t = state.nextToken();

		Metrics metrics = state.getFunct().getMetrics();
		metrics.nestedBlockDepth += 1;
		metrics.verifyMaxNestedBlockDepthPerFunction();

		if (state.nextToken().getId().equals("{")) {
			advance("{");

			// create a new block scope
			state.getFunct().getScope().stack();

			if (!state.nextToken().getId().equals("}")) {
				indent += state.getOption().asInt("indent");
				while (!ordinary && state.nextToken().getFrom() > indent) {
					indent += state.getOption().asInt("indent");
				}

				if (isfunc) {
					m = new HashMap<>();
					for (String d : state.getDirective().keySet()) {
						m.put(d, state.getDirective().get(d));
					}
					directives();

					state.getFunct().setStrict(state.isStrict());

					if (state.getOption().test("strict") && state.getFunct().getContext().isGlobal()) {
						if (m.get("use strict") == null && !state.isStrict()) {
							warning("E007");
						}
					}
				}

				a = statements(context);

				metrics.statementCount += a.size();

				indent -= state.getOption().asInt("indent");
			} else if (isfunc) {
				// Ensure property is set for functions with empty bodies.
				state.getFunct().setStrict(state.isStrict());
			}

			advance("}", t);

			if (isfunc) {
				state.getFunct().getScope().validateParams(isfatarrow);
				if (m != null) {
					state.setDirective(m);
				}
			}

			state.getFunct().getScope().unstack();

			indent = oldIndent;
		} else if (!ordinary) {
			if (isfunc) {
				state.getFunct().getScope().stack();

				if (stmt && !isfatarrow && !state.inMoz()) {
					error("W118", state.currToken(), "function closure expressions");
				}

				if (isfatarrow) {
					state.getFunct().getScope().validateParams(true);
				}

				Token expr = expression(context, 10);

				if (state.getOption().test("noreturnawait") && (context & ProdParams.ASYNC) != 0 &&
						expr.isIdentifier() && expr.getValue().equals("await")) {
					warning("W146", expr);
				}

				if (state.getOption().test("strict") && state.getFunct().getContext().isGlobal()) {
					if (!state.isStrict()) {
						warning("E007");
					}
				}

				state.getFunct().getScope().unstack();
			} else {
				error("E021", state.nextToken(), "{", state.nextToken().getValue());
			}
		} else {
			state.getFunct().getScope().stack();

			if (!stmt || state.getOption().test("curly")) {
				warning("W116", state.nextToken(), "{", state.nextToken().getValue());
			}

			// JSHint observes Annex B of the ECMAScript specification by default,
			// where function declarations are permitted in the statement positions
			// of IfStatements.
			boolean supportsFnDecl = state.getFunct().getVerb().equals("if") ||
					state.currToken().getId().equals("else");

			state.nextToken().setInBracelessBlock(true);
			indent += state.getOption().asInt("indent");
			// test indentation only if statement is in new line
			a.add(statement(context));
			indent -= state.getOption().asInt("indent");

			if (a.size() > 0 && a.get(0) != null && a.get(0).isDeclaration() &&
					!(supportsFnDecl && a.get(0).getId().equals("function"))) {
				error("E048", a.get(0), StringUtils.capitalize(a.get(0).getId()));
			}

			state.getFunct().getScope().unstack();
		}

		// Don't clear and let it propagate out if it is "break", "return" or
		// similar in switch case
		switch (state.getFunct().getVerb()) {
			case "break":
			case "continue":
			case "return":
			case "throw":
				if (iscase) {
					break;
				}

			default:
				state.getFunct().setVerb("");
		}

		inblock = b;
		if (ordinary && state.getOption().get("noempty").test() && a.size() == 0) {
			warning("W035", state.prevToken());
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
	private void countMember(String m) {
		if (membersOnly != null && !membersOnly.containsKey(m)) {
			warning("W036", state.currToken(), m);
		}
		if (member.get(m) != null) {
			member.put(m, member.get(m) + 1);
		} else {
			member.put(m, 1);
		}
	}

	// Build the syntax table by declaring the syntactic elements of the language.
	private void buildSyntaxTable() {
		type(Token.Type.NUMBER, _this -> context -> rbp -> {
			if (state.nextToken().getId().equals(".")) {
				warning("W005", _this);
			}
			return _this;
		});

		type(Token.Type.STRING, _this -> context -> rbp -> _this);

		Token identifier = new Token();
		state.getSyntax().put("(identifier)", identifier);
		identifier.setType(Token.Type.IDENTIFIER);
		identifier.setLbp(0);
		identifier.setIdentifier(true);
		identifier.setNud(_this -> context -> rbp -> {
			String v = _this.getValue();
			// If this identifier is the lone parameter to a shorthand "fat arrow"
			// function definition, i.e.
			//
			// x => x;
			//
			// ...it should not be considered as a variable in the current scope. It
			// will be added to the scope of the new function when the next token is
			// parsed, so it can be safely ignored for now.
			boolean isLoneArrowParam = state.nextToken().getId().equals("=>");

			if (isReserved(context, _this)) {
				warning("W024", _this, v);
			} else if (!isLoneArrowParam && !state.getFunct().getComparray().check(v)) {
				state.getFunct().getScope().getBlock().use(v, state.currToken());
			}

			return _this;
		});
		identifier.setLed(_this -> context -> left -> {
			error("E033", state.nextToken(), state.nextToken().getValue());
			return null;
		});

		Token template = new Token();
		state.getSyntax().put("(template)", template);
		template.setType(Token.Type.TEMPLATE);
		template.setLbp(155);
		template.setIdentifier(false);
		template.setTemplate(true);
		template.setNud(_this -> context -> rbp -> doTemplateLiteral(_this, context, rbp));
		template.setLed(_this -> context -> left -> doTemplateLiteral(_this, context, left));
		template.setNoSubst(false);

		Token templateMiddle = new Token();
		state.getSyntax().put("(template middle)", templateMiddle);
		templateMiddle.setType(Token.Type.TEMPLATEMIDDLE);
		templateMiddle.setLbp(0);
		templateMiddle.setIdentifier(false);
		templateMiddle.setTemplate(true);
		templateMiddle.setNoSubst(false);

		Token templateTail = new Token();
		state.getSyntax().put("(template tail)", templateTail);
		templateTail.setType(Token.Type.TEMPLATETAIL);
		templateTail.setLbp(0);
		templateTail.setIdentifier(false);
		templateTail.setTemplate(true);
		templateTail.setTail(true);
		templateTail.setNoSubst(false);

		Token noSubstTemplate = new Token();
		state.getSyntax().put("(no subst template)", noSubstTemplate);
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
	private void ecmaScriptParser() {
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
		reserve(Token.Type.NULL, _this -> context -> rbp -> _this);
		reserve(Token.Type.THIS, _this -> context -> rbp -> {
			if (state.isStrict() && !isMethod() &&
					!state.getOption().test("validthis") && ((state.getFunct().getStatement() != null &&
							state.getFunct().getName().charAt(0) > 'Z') || state.getFunct().isGlobal())) {
				warning("W040", _this);
			}

			return _this;
		});
		reserve(Token.Type.SUPER, _this -> context -> rbp -> {
			// JSHINT_BUG: it's not needed to pass this, becase superNud doesn't have
			// parameters
			superNud(state.currToken());

			return _this;
		}).setRbp(161);

		assignop("=", "assign");
		assignop("+=", "assignadd");
		assignop("-=", "assignsub");
		assignop("*=", "assignmult");
		assignop("/=", "assigndiv").setNud(_this -> context -> rbp -> {
			error("E014");
			return null;
		});
		assignop("%=", "assignmod");
		assignop("**=", context -> (left, that) -> {
			if (!state.inES7()) {
				warning("W119", that, "Exponentiation operator", "7");
			}

			that.setLeft(left);

			checkLeftSideAssign(context, left, that);

			that.setRight(expression(context, 10));

			return that;
		});

		bitwiseassignop("&=");
		bitwiseassignop("|=");
		bitwiseassignop("^=");
		bitwiseassignop("<<=");
		bitwiseassignop(">>=");
		bitwiseassignop(">>>=");
		infix(",", context -> (left, that) -> {
			if (state.getOption().test("nocomma")) {
				warning("W127", that);
			}

			that.setLeft(left);

			if (checkComma()) {
				that.setRight(expression(context, 10));
			} else {
				that.setRight((Token) null);
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

		infix("||", context -> (left, that) -> {
			increaseComplexityCount();
			that.setLeft(left);
			that.setRight(expression(context, 40));
			return that;
		}, 40);

		int andPrecedence = 50;
		infix("&&", context -> (left, that) -> {
			increaseComplexityCount();
			that.setLeft(left);
			that.setRight(expression(context, andPrecedence));
			return that;
		}, andPrecedence);

		infix("??", context -> (left, that) -> {
			if (!left.isParen() && (left.getId().equals("||") || left.getId().equals("&&"))) {
				error("E024", that, "??");
			}

			if (!state.inES11()) {
				warning("W119", that, "nullish coalescing", "11");
			}

			increaseComplexityCount();
			that.setLeft(left);
			Token right = expression(context, 39);
			that.setRight(right);

			if (right == null) {
				error("E024", state.nextToken(), state.nextToken().getId());
			} else if (!right.isParen() && (right.getId().equals("||") || right.getId().equals("&&"))) {
				error("E024", that.getRight(), that.getRight().getId());
			}

			return that;
		}, 39);

		// The Exponentiation operator, introduced in ECMAScript 2016
		//
		// ExponentiationExpression[Yield] :
		// UnaryExpression[?Yield]
		// UpdateExpression[?Yield] ** ExponentiationExpression[?Yield]
		infix("**", context -> (left, that) -> {
			if (!state.inES7()) {
				warning("W119", that, "Exponentiation operator", "7");
			}

			// Disallow UnaryExpressions which are not wrapped in parenthesis
			if (!left.isParen() && beginsUnaryExpression(left)) {
				error("E024", that, "**");
			}

			that.setLeft(left);
			that.setRight(expression(context, that.getRbp()));
			return that;
		}, 150);
		state.getSyntax().get("**").setRbp(140);
		bitwise("|", "bitor", 70);
		bitwise("^", "bitxor", 80);
		bitwise("&", "bitand", 90);
		relation("==", _this -> context -> (left, right) -> {
			boolean eqnull = state.getOption().test("eqnull") &&
					((left != null && left.getValue().equals("null"))
							|| (right != null && right.getValue().equals("null")));

			if (!eqnull && state.getOption().test("eqeqeq")) {
				_this.setFrom(_this.getCharacter());
				warning("W116", _this, "===", "==");
			} else if (isTypoTypeof(right, left)) {
				warning("W122", _this, right.getValue());
			} else if (isTypoTypeof(left, right)) {
				warning("W122", _this, left.getValue());
			}

			return _this;
		});
		relation("===", _this -> context -> (left, right) -> {
			if (isTypoTypeof(right, left)) {
				warning("W122", _this, right.getValue());
			} else if (isTypoTypeof(left, right)) {
				warning("W122", _this, left.getValue());
			}
			return _this;
		});
		relation("!=", _this -> context -> (left, right) -> {
			boolean eqnull = state.getOption().test("eqnull") &&
					((left != null && left.getValue().equals("null"))
							|| (right != null && right.getValue().equals("null")));

			if (!eqnull && state.getOption().test("eqeqeq")) {
				_this.setFrom(_this.getCharacter());
				warning("W116", _this, "!==", "!=");
			} else if (isTypoTypeof(right, left)) {
				warning("W122", _this, right.getValue());
			} else if (isTypoTypeof(left, right)) {
				warning("W122", _this, left.getValue());
			}

			return _this;
		});
		relation("!==", _this -> context -> (left, right) -> {
			if (isTypoTypeof(right, left)) {
				warning("W122", _this, right.getValue());
			} else if (isTypoTypeof(left, right)) {
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
			ScopeManager scope = state.getFunct().getScope();
			token.setLeft(left);
			token.setRight(right = expression(context, 120));

			// This condition reflects a syntax error which will be reported by the
			// `expression` function.
			if (right == null) {
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
					(right.getId().equals("(template)") && right.getTag() == null)) {
				error("E060");
			}

			if (right.getId().equals("function")) {
				warning("W139");
			}

			return token;
		}, 120);
		infix("+", context -> (left, that) -> {
			Token next = state.nextToken();
			Token right = expression(context, 130);
			that.setLeft(left);
			that.setRight(right);

			if (left != null && right != null && left.getId().equals("(string)") && right.getId().equals("(string)")) {
				left.setValue(left.getValue() + right.getValue());
				left.setCharacter(right.getCharacter());
				if (!state.getOption().test("scripturl") && Reg.isJavascriptUrl(left.getValue())) {
					warning("W050", left);
				}
				return left;
			}

			if (next.getId().equals("+") || next.getId().equals("++")) {
				warning("W007", that.getRight());
			}

			return that;
		}, 130);
		prefix("+", _this -> context -> rbp -> {
			Token next = state.nextToken();
			_this.setArity(ArityType.UNARY);
			_this.setRight(expression(context, 150));

			if (next.getId().equals("+") || next.getId().equals("++")) {
				warning("W007", _this.getRight());
			}

			return _this;
		});
		infix("-", context -> (left, that) -> {
			Token next = state.nextToken();
			that.setLeft(left);
			that.setRight(expression(context, 130));

			if (next.getId().equals("-") || next.getId().equals("--")) {
				warning("W006", that.getRight());
			}

			return that;
		}, 130);
		prefix("-", _this -> context -> rbp -> {
			Token next = state.nextToken();
			_this.setArity(ArityType.UNARY);
			_this.setRight(expression(context, 150));

			if (next.getId().equals("-") || next.getId().equals("--")) {
				warning("W006", _this.getRight());
			}

			return _this;
		});
		infix("*", "mult", 140);
		infix("/", "div", 140);
		infix("%", "mod", 140);

		suffix("++");
		prefix("++", "preinc");
		state.getSyntax().get("++").setExps(true);

		suffix("--");
		prefix("--", "predec");
		state.getSyntax().get("--").setExps(true);

		prefix("delete", _this -> context -> rbp -> {
			_this.setArity(Token.ArityType.UNARY);
			Token p = expression(context, 150);
			if (p == null) {
				return _this;
			}

			if (!p.getId().equals(".") && !p.getId().equals("[")) {
				warning("W051");
			}
			_this.setFirstTokens(p);

			// The `delete` operator accepts unresolvable references when not in strict
			// mode, so the operand may be undefined.
			if (p.isIdentifier() && !state.isStrict()) {
				p.setForgiveUndef(true);
			}
			return _this;
		}).setExps(true);

		prefix("~", _this -> context -> rbp -> {
			if (state.getOption().test("bitwise")) {
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

			if (BooleanUtils.isTrue(bang.get(_this.getRight().getId()))) {
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
			if (p.isIdentifier()) {
				p.setForgiveUndef(true);
			}
			return _this;
		});
		prefix("new", _this -> context -> rbp -> {
			Token mp = metaProperty(context, "target", () -> {
				if (!state.inES6(true)) {
					warning("W119", state.prevToken(), "new.target", "6");
				}
				boolean inFunction = false;
				Functor c = state.getFunct();
				while (c != null) {
					inFunction = !c.isGlobal();
					if (!c.isArrow()) {
						break;
					}
					c = c.getContext();
				}
				if (!inFunction) {
					warning("W136", state.prevToken(), "new.target");
				}
			});
			if (mp != null) {
				return mp;
			}

			Token opening = state.nextToken();
			Token c = expression(context, 155);

			if (c == null) {
				return _this;
			}

			if (!c.isParen() && c.getRbp() > 160) {
				error("E024", opening, opening.getValue());
			}

			if (!c.getId().equals("function")) {
				if (c.isIdentifier()) {
					switch (c.getValue()) {
						case "Number":
						case "String":
						case "Boolean":
						case "Math":
						case "JSON":
							warning("W053", state.prevToken(), c.getValue());
							break;
						case "Symbol":
							if (state.inES6()) {
								warning("W053", state.prevToken(), c.getValue());
							}
							break;
						case "Function":
							if (!state.getOption().test("evil")) {
								warning("W054");
							}
							break;
						case "Date":
						case "RegExp":
						case "this":
							break;
						default:
							char i = c.getValue().charAt(0);
							if (state.getOption().test("newcap") && (i < 'A' || i > 'Z') &&
									!state.getFunct().getScope().isPredefined(c.getValue())) {
								warning("W055", state.currToken());
							}
					}
				} else {
					if (c.getId().equals("?.") && !c.isParen()) {
						error("E024", c, "?.");
					} else if (!c.getId().equals(".") && !c.getId().equals("[") && !c.getId().equals("(")) {
						warning("W056", state.currToken());
					}
				}
			} else {
				if (!state.getOption().get("supernew").test())
					warning("W057", _this);
			}
			if (!state.nextToken().getId().equals("(") && !state.getOption().get("supernew").test()) {
				warning("W058", state.currToken(), state.currToken().getValue());
			}
			_this.setRight(c);
			_this.setFirstTokens(c);
			return _this;
		});
		state.getSyntax().get("new").setExps(true);

		Token classDeclaration = blockstmt("class", _this -> context -> {
			String className = null;
			Token classNameToken = null;

			if (!state.inES6()) {
				warning("W104", state.currToken(), "class", "6");
			}
			state.setInClassBody(true);

			// Class Declaration: 'class <Classname>'
			if (state.nextToken().isIdentifier() && !state.nextToken().getValue().equals("extends")) {
				classNameToken = state.nextToken();
				className = classNameToken.getValue();
				identifier(context);
				// unintialized, so that the 'extends' clause is parsed while the class is in
				// TDZ
				state.getFunct().getScope().addbinding(className, "class", classNameToken, false);
			}

			// Class Declaration: 'class <Classname> extends <Superclass>'
			if (state.nextToken().getValue().equals("extends")) {
				advance("extends");
				expression(context, 0);
			}

			if (classNameToken != null) {
				_this.setTokenName(classNameToken);
				state.getFunct().getScope().initialize(className);
			} else {
				_this.setTokenName(null);
			}
			state.getFunct().getScope().stack();
			classBody(_this, context);
			return _this;
		});
		classDeclaration.setExps(true);
		classDeclaration.setDeclaration(true);

		/*
		 * Class expression
		 * The Block- and Expression- handling for "class" are almost identical, except
		 * for the ordering of steps.
		 * In an expression:, the name should not be saved into the calling scope, but
		 * is still accessible inside the definition, so we open a new scope first, then
		 * save the name. We also mark it as used.
		 */
		prefix("class", _this -> context -> rbp -> {
			String className = null;
			Token classNameToken = null;

			if (!state.inES6()) {
				warning("W104", state.currToken(), "class", "6");
			}
			state.setInClassBody(true);

			// Class Declaration: 'class <Classname>'
			if (state.nextToken().isIdentifier() && !state.nextToken().getValue().equals("extends")) {
				classNameToken = state.nextToken();
				className = classNameToken.getValue();
				identifier(context);
			}

			// Class Declaration: 'class <Classname> extends <Superclass>'
			if (state.nextToken().getValue().equals("extends")) {
				advance("extends");
				expression(context, 0);
			}

			state.getFunct().getScope().stack();
			if (classNameToken != null) {
				_this.setTokenName(classNameToken);
				state.getFunct().getScope().addbinding(className, "class", classNameToken, true);
				state.getFunct().getScope().getBlock().use(className, classNameToken);
			}

			classBody(_this, context);
			return _this;
		});

		prefix("void").setExps(true);

		infix(".", context -> (left, that) -> {
			String m = identifier(context, true);

			if (m != null) {
				countMember(m);
			}

			that.setLeft(left);
			that.setRight(state.currToken()); // JSHINT_BUG: it's better to use tokens.curr here instread string
												// variable

			if (m != null && m.equals("hasOwnProperty") && state.nextToken().getValue().equals("=")) {
				warning("W001");
			}

			if (left != null && left.getValue().equals("arguments") && (m.equals("callee") || m.equals("caller"))) {
				if (state.getOption().test("noarg"))
					warning("W059", left, m);
				else if (state.isStrict())
					error("E008");
			} else if (!state.getOption().test("evil") && left != null && left.getValue().equals("document") &&
					(m.equals("write") || m.equals("writeln"))) {
				warning("W060", left);
			}

			if (!state.getOption().test("evil") && (m.equals("eval") || m.equals("execScript"))) {
				if (isGlobalEval(left)) {
					warning("W061");
				}
			}

			return that;
		}, 160, true);

		infix("?.", context -> (left, that) -> {
			if (!state.inES11()) {
				warning("W119", state.currToken(), "Optional chaining", "11");
			}

			if (checkPunctuator(state.nextToken(), "[")) {
				that.setLeft(left);
				advance();
				that.setRight(state.currToken().led(context, left));
			} else if (checkPunctuator(state.nextToken(), "(")) {
				that.setLeft(left);
				advance();
				that.setRight(state.currToken().led(context, left));
				that.setExps(true);
			} else {
				state.getSyntax().get(".").led(that, context, left);
			}

			if (state.nextToken().getType().equals(Token.Type.TEMPLATE)) {
				error("E024", state.nextToken(), "`");
			}

			return that;
		}, 160, true);

		infix("(", context -> (left, that) -> {
			if (state.getOption().test("immed") && left != null && !left.isImmed()
					&& left.getId().equals("function")) {
				warning("W062");
			}

			if (state.getOption().test("asi") && checkPunctuators(state.prevToken(), ")", "]") &&
					!sameLine(state.prevToken(), state.currToken())) {
				warning("W014", state.currToken(), state.currToken().getId());
			}

			int n = 0;
			List<Token> p = new ArrayList<>();

			if (left != null) {
				if (left.getType() == Token.Type.IDENTIFIER) {
					String[] newCapIgnore = { "Array", "Boolean", "Date", "Error", "Function", "Number",
							"Object", "RegExp", "String", "Symbol" };
					// PORT INFO: match regexp was moved to Reg class
					if (Reg.test(Reg.UPPERCASE_IDENTIFIER, left.getValue())
							&& ArrayUtils.indexOf(newCapIgnore, left.getValue()) == -1) {
						if (left.getValue().equals("Math")) {
							warning("W063", left);
						} else if (state.getOption().test("newcap")) {
							warning("W064", left);
						}
					}
				}
			}

			if (!state.nextToken().getId().equals(")")) {
				for (;;) {
					spreadrest("spread");

					p.add(expression(context, 10));
					n += 1;
					if (!state.nextToken().getId().equals(",")) {
						break;
					}
					advance(",");
					checkComma(false, true);

					if (state.nextToken().getId().equals(")")) {
						if (!state.inES8()) {
							warning("W119", state.currToken(), "Trailing comma in arguments lists", "8");
						}

						break;
					}
				}
			}

			advance(")");

			if (left != null) {
				if (!state.inES5() && left.getValue().equals("parseInt") && n == 1) {
					warning("W065", state.currToken());
				}
				if (!state.getOption().test("evil")) {
					if (left.getValue().equals("eval") || left.getValue().equals("Function") ||
							left.getValue().equals("execScript")) {
						warning("W061", left);

						// This conditional expression was initially implemented with a typo
						// which prevented the branch's execution in all cases. While
						// enabling the code will produce behavior that is consistent with
						// the other forms of code evaluation that follow, such a change is
						// also technically incompatable with prior versions of JSHint (due
						// to the fact that the behavior was never formally documented). This
						// branch should be enabled as part of a major release.
						// if (p.size() > 0 && p.get(0) != null && p.get(0).getId().equals("(string)"))
						// {
						// addEvalCode(left, p.get(0));
						// }
					} else if (p.size() > 0 && p.get(0) != null && p.get(0).getId().equals("(string)") &&
							(left.getValue().equals("setTimeout") ||
									left.getValue().equals("setInterval"))) {
						warning("W066", left);
						addEvalCode(left, p.get(0));
					}

					// window.setTimeout/setInterval
					else if (p.size() > 0 && p.get(0) != null && p.get(0).getId().equals("(string)") &&
							left.getValue().equals(".") &&
							left.getLeft().getValue().equals("window") &&
							(left.getRight() != null && (left.getRight().getValue().equals("setTimeout") || // JSHINT_BUG:
																											// it's
																											// better
																											// always
																											// use
																											// tokens
																											// for right
																											// and left
																											// attributes
									left.getRight().getValue().equals("setInterval")))) {
						warning("W066", left);
						addEvalCode(left, p.get(0));
					}
				}
				if (!isTypicalCallExpression(left)) {
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
			Token opening = state.currToken();
			Token preceeding = state.prevToken();
			boolean isNecessary = !state.getOption().test("singleGroups");
			Token pn = peekThroughParens(1);

			if (state.nextToken().getId().equals("function")) {
				state.nextToken().setImmed(true);
				triggerFnExpr = true;
			}

			// If the balanced grouping operator is followed by a "fat arrow", the
			// current token marks the beginning of a "fat arrow" function and parsing
			// should proceed accordingly.
			if (pn.getValue().equals("=>")) {
				// JSHINT_BUG: this might be an error, that result is saved to funct property
				doFunction(
						context,
						null,
						null,
						FunctionType.ARROW,
						null,
						true,
						null,
						false,
						false);
				return pn;
			}

			// The ECMA262 grammar requires an expression between the "opening
			// parenthesis" and "close parenthesis" tokens of the grouping operator.
			// However, the "ignore" directive is commonly used to inject values that
			// are not included in the token stream. For example:
			//
			// return (
			// /*jshint ignore:start */
			// <div></div>
			// /*jshint ignore:end */
			// );
			//
			// The "empty" grouping operator is permitted in order to tolerate this
			// pattern.

			if (state.nextToken().getId().equals(")")) {
				advance(")");
				return null;
			}

			ret = expression(context, 0);

			advance(")", _this);

			if (ret == null) {
				return null;
			}

			ret.setParen(true);

			if (state.getOption().test("immed") && ret != null && ret.getId().equals("function")) {
				if (!state.nextToken().getId().equals("(") &&
						!state.nextToken().getId().equals(".") && !state.nextToken().getId().equals("[")) {
					warning("W068", _this);
				}
			}

			if (ret.getId().equals(",")) {
				first = ret.getLeft();
				while (first.getId().equals(",")) {
					first = first.getLeft();
				}

				last = ret.getRight();
			} else {
				first = last = ret;

				if (!isNecessary) {
					// async functions are identified after parsing due to the complexity
					// of disambiguating the `async` keyword.
					if (!triggerFnExpr) {
						triggerFnExpr = ret.getId().equals("async");
					}

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
											(!isEndOfExpr() || !state.prevToken().getId().equals("}")))
									||
					// Used to demarcate an arrow function as the left-hand side of some
					// operator.
									(ret.getId().equals("=>") && !isEndOfExpr()) ||
					// Used as the return value of a single-statement arrow function
									(ret.getId().equals("{") && preceeding.getId().equals("=>")) ||
					// Used to cover a unary expression as the left-hand side of the
					// exponentiation operator
									(beginsUnaryExpression(ret) && state.nextToken().getId().equals("**")) ||
					// Used to cover a logical operator as the right-hand side of the
					// nullish coalescing operator
									(preceeding.getId().equals("??")
											&& (ret.getId().equals("&&") || ret.getId().equals("||")))
									||
					// Used to delineate an integer number literal from a dereferencing
					// punctuator (otherwise interpreted as a decimal point)
									(ret.getType() == Token.Type.NUMBER &&
											checkPunctuator(pn, ".") && StringUtils.isNumeric(ret.getValue()))
									|| // PORT INFO: test regexp /^\d+$/ was replaced with StringUtils method
					// Used to wrap object destructuring assignment
									(opening.isBeginsStmt() && ret.getId().equals("=")
											&& ret.getLeft().getId().equals("{"))
									||
					// Used to allow optional chaining with other language features which
					// are otherwise restricted.
									(ret.getId().equals("?.") && (preceeding.getId().equals("new")
											|| state.nextToken().getType() == Token.Type.TEMPLATE));
				}
			}

			// The operator may be necessary to override the default binding power of
			// neighboring operators (whenever there is an operator in use within the
			// first expression *or* the current group contains multiple expressions)
			if (!isNecessary && (isOperator(first) || first != last)) {
				isNecessary = (rbp > first.getLbp()) ||
						(rbp > 0 && rbp == first.getLbp()) ||
						(!isEndOfExpr() && last.getRbp() < state.nextToken().getLbp());
			}

			if (!isNecessary) {
				warning("W126", opening);
			}

			return ret;
		});

		application("=>").setRbp(161);

		infix("[", context -> (left, that) -> {
			boolean canUseDot = false;

			if (state.getOption().test("asi") && checkPunctuators(state.prevToken(), ")", "]") &&
					!sameLine(state.prevToken(), state.currToken())) {
				warning("W014", state.currToken(), state.currToken().getId());
			}

			Token e = expression(context & ~ProdParams.NOIN, 0);

			if (e != null && e.getType() == Token.Type.STRING) {
				if (!state.getOption().test("evil")
						&& (e.getValue().equals("eval") || e.getValue().equals("execScript"))) {
					if (isGlobalEval(left)) {
						warning("W061");
					}
				}

				countMember(e.getValue());
				if (!state.getOption().test("sub") && Reg.isIdentifier(e.getValue())) {
					Token s = state.getSyntax().get(e.getValue());
					if (s != null) {
						canUseDot = !isReserved(context, s);
					} else {
						// This branch exists to preserve legacy behavior with version 2.9.5
						// and earlier. In those releases, `eval` and `arguments` were
						// incorrectly interpreted as reserved keywords, so Member
						// Expressions such as `object["eval"]` did not trigger warning W069.
						//
						// JSHINT_TODO: Remove in JSHint 3
						canUseDot = !e.getValue().equals("eval") && !e.getValue().equals("arguments");
					}

					if (canUseDot) {
						warning("W069", state.prevToken(), e.getValue());
					}
				}
			}
			advance("]", that);

			if (e != null && e.getValue().equals("hasOwnProperty") && state.nextToken().getValue().equals("=")) {
				warning("W001");
			}

			that.setLeft(left);
			that.setRight(e);
			return that;
		}, 160, true);

		prefix("[", _this -> context -> rbp -> {
			LookupBlockType blocktype = new LookupBlockType();
			if (blocktype.isCompArray) {
				if (!state.getOption().test("esnext") && !state.inMoz()) {
					warning("W118", state.currToken(), "array comprehension");
				}
				return comprehensiveArrayExpression(context);
			} else if (blocktype.isDestAssign) {
				_this.setDestructAssign(destructuringPattern(context, true, true));
				return _this;
			}
			boolean b = !sameLine(state.currToken(), state.nextToken());
			_this.setFirstTokens();
			if (b) {
				indent += state.getOption().asInt("indent");
				if (state.nextToken().getFrom() == indent + state.getOption().asInt("indent")) {
					indent += state.getOption().asInt("indent");
				}
			}
			while (!state.nextToken().getId().equals("(end)")) {
				while (state.nextToken().getId().equals(",")) {
					if (!state.getOption().get("elision").test()) {
						if (!state.inES5()) {
							// Maintain compat with old options --- ES5 mode without
							// elision=true will warn once per comma
							warning("W070");
						} else {
							warning("W128");
							do {
								advance(",");
							} while (state.nextToken().getId().equals(","));
							continue;
						}
					}
					advance(",");
				}

				if (state.nextToken().getId().equals("]")) {
					break;
				}

				spreadrest("spread");

				_this.addFirstTokens(expression(context, 10));
				if (state.nextToken().getId().equals(",")) {
					advance(",");
					checkComma(false, true);
					if (state.nextToken().getId().equals("]") && !state.inES5()) {
						warning("W070", state.currToken());
						break;
					}
				} else {
					if (state.getOption().test("trailingcomma") && state.inES5()) {
						warningAt("W140", state.currToken().getLine(), state.currToken().getCharacter());
					}
					break;
				}
			}
			if (b) {
				indent -= state.getOption().asInt("indent");
			}
			advance("]", _this);
			return _this;
		});

		// object literals
		x = delim("{");
		x.setNud(_this -> context -> rbp -> {
			String i = null;
			boolean isGeneratorMethod = false;
			UniversalContainer props = ContainerFactory.nullContainer().create(); // All properties, including accessors
			boolean isAsyncMethod = false;

			boolean b = !sameLine(state.currToken(), state.nextToken());
			if (b) {
				indent += state.getOption().asInt("indent");
				if (state.nextToken().getFrom() == indent + state.getOption().asInt("indent")) {
					indent += state.getOption().asInt("indent");
				}
			}

			LookupBlockType blocktype = new LookupBlockType();
			if (blocktype.isDestAssign) {
				_this.setDestructAssign(destructuringPattern(context, true, true));
				return _this;
			}

			// state.setInObjectBody(true); //JSHINT_BUG: this property is not used
			// anywhere
			for (;;) {
				if (state.nextToken().getId().equals("}")) {
					break;
				}

				String nextVal = state.nextToken().getValue();
				if (state.nextToken().isIdentifier() &&
						(peekIgnoreEOL().getId().equals(",") || peekIgnoreEOL().getId().equals("}"))) {
					if (!state.inES6()) {
						warning("W104", state.nextToken(), "object short notation", "6");
					}
					Token t = expression(context, 10);
					i = t != null ? t.getValue() : null;
					if (t != null) {
						saveProperty(props, i, t, false, false, false);
					}

				} else if (!peek().getId().equals(":") && (nextVal.equals("get") || nextVal.equals("set"))) {
					advance(nextVal);

					if (!state.inES5()) {
						error("E034");
					}

					if (state.nextToken().getId().equals("[")) {
						// JSHINT_BUG: this returns Token not string
						i = computedPropertyName(context) != null ? "TOKEN" : "";
					} else {
						i = propertyName(context);

						// ES6 allows for get() {...} and set() {...} method
						// definition shorthand syntax, so we don't produce an error
						// if linting ECMAScript 6 code.
						if (StringUtils.isEmpty(i) && !state.inES6()) {
							error("E035");
						}
					}

					// We don't want to save this getter unless it's an actual getter
					// and not an ES6 concise method
					if (i != null && !i.isEmpty()) {
						saveAccessor(nextVal, props, i, state.currToken(), false, false);
					}

					Token t = state.nextToken();
					Functor f = doFunction(
							context,
							null,
							null,
							null,
							null,
							false,
							null,
							true,
							false);
					List<String> params = f.getParams();

					// Don't warn about getter/setter pairs if this is an ES6 concise method
					if (nextVal.equals("get") && StringUtils.isNotEmpty(i) && params.size() != 0) {
						warning("W076", t, params.get(0), i);
					} else if (nextVal.equals("set") && StringUtils.isNotEmpty(i) && f.getMetrics().arity != 1) {
						warning("W077", t, i);
					}
				} else if (spreadrest("spread")) {
					if (!state.inES9()) {
						warning("W119", state.nextToken(), "object spread property", "9");
					}

					expression(context, 10);
				} else {
					if (state.nextToken().getId().equals("async") && !checkPunctuators(peek(), "(", ":")) {
						if (!state.inES8()) {
							warning("W119", state.nextToken(), "async functions", "8");
						}

						isAsyncMethod = true;
						advance();

						nolinebreak(state.currToken());
					} else {
						isAsyncMethod = false;
					}

					if (state.nextToken().getValue().equals("*")
							&& state.nextToken().getType() == Token.Type.PUNCTUATOR) {
						if (isAsyncMethod && !state.inES9()) {
							warning("W119", state.nextToken(), "async generators", "9");
						} else if (!state.inES6()) {
							warning("W104", state.nextToken(), "generator functions", "6");
						}

						advance("*");
						isGeneratorMethod = true;
					} else {
						isGeneratorMethod = false;
					}

					if (state.nextToken().getId().equals("[")) {
						state.getNameStack().set(computedPropertyName(context));
					} else {
						state.getNameStack().set(state.nextToken());
						i = propertyName(context);
						saveProperty(props, i, state.nextToken(), false, false, false);

						if (i == null)
							break;
					}

					if (state.nextToken().getValue().equals("(")) {
						if (!state.inES6()) {
							warning("W104", state.currToken(), "concise methods", "6");
						}

						doFunction(
								isAsyncMethod ? context | ProdParams.PRE_ASYNC : context,
								null,
								null,
								(isGeneratorMethod ? FunctionType.GENERATOR : null),
								null,
								false,
								null,
								true,
								false);
					} else {
						advance(":");
						expression(context, 10);
					}
				}

				countMember(i);

				if (state.nextToken().getId().equals(",")) {
					advance(",");
					checkComma(true, true);
					if (state.nextToken().getId().equals(",")) {
						warning("W070", state.currToken());
					} else if (state.nextToken().getId().equals("}") && !state.inES5()) {
						warning("W070", state.currToken());
					}
				} else {
					if (state.getOption().test("trailingcomma") && state.inES5()) {
						warningAt("W140", state.currToken().getLine(), state.currToken().getCharacter());
					}
					break;
				}
			}
			if (b) {
				indent -= state.getOption().asInt("indent");
			}
			advance("}", _this);

			checkProperties(props);
			// state.setInObjectBody(false); //JSHINT_BUG: this property is not used
			// anywhere

			return _this;
		});
		x.setFud(_this -> context -> {
			error("E036", state.currToken());
			return null;
		});
	}

	private void classBody(Token classToken, int context) {
		UniversalContainer props = ContainerFactory.nullContainer().create();
		String name;
		String accessorType;
		boolean hasConstructor = false;

		if (state.nextToken().getValue().equals("{")) {
			advance("{");
		} else {
			warning("W116", state.currToken(), "identifier", state.nextToken().getType().toString()); // ?
			advance();
		}

		while (!state.nextToken().getValue().equals("}")) {
			boolean isStatic = false;
			boolean inGenerator = false;
			context &= ~ProdParams.PRE_ASYNC;

			if (state.nextToken().getValue().equals("static") &&
					!checkPunctuator(peek(), "(")) {
				isStatic = true;
				advance();
			}

			if (state.nextToken().getValue().equals("async")) {
				if (!checkPunctuator(peek(), "(")) {
					context |= ProdParams.PRE_ASYNC;
					advance();

					nolinebreak(state.currToken());

					if (checkPunctuator(state.nextToken(), "*")) {
						inGenerator = true;
						advance("*");

						if (!state.inES9()) {
							warning("W119", state.nextToken(), "async generators", "9");
						}
					}

					if (!state.inES8()) {
						warning("W119", state.currToken(), "async functions", "8");
					}
				}
			}

			if (state.nextToken().getValue().equals("*")) {
				inGenerator = true;
				advance();
			}

			Token token = state.nextToken();

			if ((token.getValue().equals("set") || token.getValue().equals("get")) && !checkPunctuator(peek(), "(")) {
				if (inGenerator) {
					error("E024", token, token.getValue());
				}
				accessorType = token.getValue();
				advance();
				token = state.nextToken();

				if (!isStatic && token.getValue().equals("constructor")) {
					error("E049", token, "class " + accessorType + "ter method", token.getValue());
				} else if (isStatic && token.getValue().equals("prototype")) {
					error("E049", token, "static class " + accessorType + "ter method", token.getValue());
				}
			} else {
				accessorType = null;
			}

			switch (token.getValue()) {
				case ";":
					warning("W032", token);
					advance();
					break;
				case "constructor":
					if (isStatic) {
						// treat like a regular method -- static methods can be called 'constructor'
						name = propertyName(context);
						saveProperty(props, name, token, true, isStatic, false);
						doMethod(classToken, context, name, inGenerator);
					} else {
						if (inGenerator || (context & ProdParams.PRE_ASYNC) != 0) {
							error("E024", token, token.getValue());
						} else if (hasConstructor) {
							error("E024", token, token.getValue());
						} else {
							hasConstructor = StringUtils.isEmpty(accessorType) && !isStatic;
						}
						advance();
						doMethod(classToken, context, state.getNameStack().infer(), false);
					}

					break;
				case "[":
					// JSHINT_BUG: this returns Token not string
					name = computedPropertyName(context) != null ? "TOKEN" : "";
					doMethod(classToken, context, name, inGenerator);
					// We don't check names (via calling saveProperty()) of computed expressions
					// like ["Symbol.iterator"]()
					break;
				default:
					name = propertyName(context);
					if (name == null) {
						error("E024", token, token.getValue());
						advance();
						break;
					}

					if (StringUtils.isNotEmpty(accessorType)) {
						saveAccessor(accessorType, props, name, token, true, isStatic);
						name = state.getNameStack().infer();
					} else {
						if (isStatic && name.equals("prototype")) {
							error("E049", token, "static class method", name);
						}

						saveProperty(props, name, token, true, isStatic, false);
					}

					doMethod(classToken, context, name, inGenerator);
					break;
			}
		}
		advance("}");
		checkProperties(props);

		state.setInClassBody(false);
		state.getFunct().getScope().unstack();
	}

	private void doMethod(Token classToken, int context, String name, boolean generator) {
		if (generator) {
			if (!state.inES6()) {
				warning("W119", state.currToken(), "function*", "6");
			}
		}

		if (!state.nextToken().getValue().equals("(")) {
			error("E054", state.nextToken(), state.nextToken().getValue());
			advance();
			if (state.nextToken().getValue().equals("{")) {
				// manually cheating the test "invalidClasses", which asserts this particular
				// behavior when a class is misdefined.
				advance();
				if (state.nextToken().getValue().equals("}")) {
					warning("W116", state.nextToken(), "(", state.nextToken().getValue());
					advance();
					identifier(context);
					advance();
				}
				return;
			} else {
				while (!state.nextToken().getValue().equals("(")) {
					advance();
				}
			}
		}

		doFunction(
				context,
				name,
				classToken,
				generator ? FunctionType.GENERATOR : null,
				null,
				false,
				null,
				true,
				false);
	}

	/**
	 * Determine if a CallExpression's "base" is a type of expression commonly
	 * used in this position.
	 *
	 * @param token token describing the "base" of the CallExpression
	 * @returns
	 */
	private boolean isTypicalCallExpression(Token token) {
		return token.isIdentifier() || token.getId().equals(".") || token.getId().equals("[") ||
				token.getId().equals("=>") || token.getId().equals("(") || token.getId().equals("&&") ||
				token.getId().equals("||") || token.getId().equals("?") || token.getId().equals("async") ||
				token.getId().equals("?.") || (state.inES6() && token.isFunctor());
	}

	private Token comprehensiveArrayExpression(int context) {
		Token res = new Token();
		res.setExps(true);
		state.getFunct().getComparray().stack();

		// Handle reversed for expressions, used in spidermonkey
		boolean reversed = false;
		if (!state.nextToken().getValue().equals("for")) {
			reversed = true;
			if (!state.inMoz()) {
				warning("W116", state.nextToken(), "for", state.nextToken().getValue());
			}
			state.getFunct().getComparray().setState("use");
			res.setRight(expression(context, 10));
		}

		advance("for");
		if (state.nextToken().getValue().equals("each")) {
			advance("each");
			if (!state.inMoz()) {
				warning("W118", state.currToken(), "for each");
			}
		}
		advance("(");
		state.getFunct().getComparray().setState("define");
		res.setLeft(expression(context, 130));
		if (state.nextToken().getValue().equals("in") || state.nextToken().getValue().equals("of")) {
			advance();
		} else {
			error("E045", state.currToken());
		}
		state.getFunct().getComparray().setState("generate");
		expression(context, 10);

		advance(")");
		if (state.nextToken().getValue().equals("if")) {
			advance("if");
			advance("(");
			state.getFunct().getComparray().setState("filter");
			expression(context, 10);
			advance(")");
		}

		if (!reversed) {
			state.getFunct().getComparray().setState("use");
			res.setRight(expression(context, 10));
		}

		advance("]");
		state.getFunct().getComparray().unstack();
		return res;
	}

	private Token peekThroughParens(int parens) {
		Token pn = state.nextToken();
		int i = -1;
		Token pn1;

		do {
			if (pn.getValue().equals("(")) {
				parens += 1;
			} else if (pn.getValue().equals(")")) {
				parens -= 1;
			}

			i += 1;
			pn1 = pn;
			pn = peek(i);

		} while (!(parens == 0 && pn1.getValue().equals(")")) && pn.getType() != Token.Type.END);

		return pn;
	}

	private boolean isMethod() {
		return state.getFunct().isMethod();
	}

	/**
	 * Retrieve the value of the next token if it is a valid LiteralPropertyName
	 * and optionally advance the parser.
	 *
	 * @param context the parsing context; see `prod-params.js` for
	 *                more information
	 *
	 * @returns the value of the identifier, if present
	 */
	private String propertyName(int context) {
		String id = optionalidentifier(context, true);

		if (id == null) {
			if (state.nextToken().getId().equals("(string)")) {
				id = state.nextToken().getValue();
				advance();
			} else if (state.nextToken().getId().equals("(number)")) {
				id = state.nextToken().getValue();
				advance();
			}
		}

		if (id != null && id.equals("hasOwnProperty")) {
			warning("W001");
		}

		return id;
	}

	/**
	 * @param context       The parsing context
	 * @param loneArg       The argument to the function in cases
	 *                      where it was defined using the
	 *                      single-argument shorthand.
	 * @param parsedOpening Whether the opening parenthesis has
	 *                      already been parsed.
	 * @return {{ arity: number, params: Array.<string>, isSimple: boolean}}
	 */
	private UniversalContainer functionparams(int context, Token loneArg, boolean parsedOpening) {
		List<String> paramsIds = new ArrayList<>();
		boolean pastDefault = false;
		boolean pastRest = false;
		int arity = 0;
		boolean hasDestructuring = false;

		if (loneArg != null && loneArg.isIdentifier() == true) {
			state.getFunct().getScope().addParam(loneArg.getValue(), loneArg);
			return ContainerFactory.createObject(
					"arity",
					1,
					"params",
					ContainerFactory.createArray(loneArg.getValue()),
					"isSimple",
					true);
		}

		Token next = state.nextToken();

		if (!parsedOpening) {
			advance("(");
		}

		if (state.nextToken().getId().equals(")")) {
			advance(")");
			return ContainerFactory.undefinedContainer();
		}

		for (;;) {
			arity++;
			// are added to the param scope
			UniversalContainer currentParams = ContainerFactory.createArray();

			pastRest = spreadrest("rest");

			if (state.nextToken().getId().equals("{") || state.nextToken().getId().equals("[")) {
				hasDestructuring = true;
				List<Token> tokens = destructuringPattern(context, false, false);
				for (Token t : tokens) {
					if (StringUtils.isNotEmpty(t.getId())) {
						paramsIds.add(t.getId());
						currentParams.push(ContainerFactory.createArray(t.getId(), t.getToken()));
					}
				}
			} else {
				String ident = identifier(context);

				if (StringUtils.isNotEmpty(ident)) {
					paramsIds.add(ident);
					currentParams.push(ContainerFactory.createArray(ident, state.currToken()));
				} else {
					// Skip invalid parameter.
					while (!checkPunctuators(state.nextToken(), new String[] { ",", ")" }))
						advance();
				}
			}

			// It is valid to have a regular argument after a default argument
			// since undefined can be used for missing parameters. Still warn as it is
			// a possible code smell.
			if (pastDefault) {
				if (!state.nextToken().getId().equals("=")) {
					error("W138", state.currToken());
				}
			}
			if (state.nextToken().getId().equals("=")) {
				if (!state.inES6()) {
					warning("W119", state.nextToken(), "default parameters", "6");
				}

				if (pastRest) {
					error("E062", state.nextToken());
				}

				advance("=");
				pastDefault = true;
				expression(context, 10);
			}

			// now we have evaluated the default expression, add the variable to the param
			// scope
			for (UniversalContainer p : currentParams) {
				state.getFunct().getScope().addParam(p.asString(0), p.<Token>valueOf(1));
			}

			if (state.nextToken().getId().equals(",")) {
				if (pastRest) {
					warning("W131", state.nextToken());
				}
				advance(",");
				checkComma(false, true);
			}

			if (state.nextToken().getId().equals(")")) {
				if (state.currToken().getId().equals(",") && !state.inES8()) {
					warning("W119", state.currToken(), "Trailing comma in function parameters", "8");
				}

				advance(")", next);
				return ContainerFactory.createObject(
						"arity",
						arity,
						"params",
						paramsIds,
						"isSimple",
						!hasDestructuring && !pastRest && !pastDefault);
			}
		}
	}

	/**
	 * Factory function for creating objects used to track statistics of function
	 * literals.
	 */
	class Functor {

		private Set<FunctorTag> tags = new HashSet<>();

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
		private boolean isYielded = false;
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
		private Functor() {

		}

		/**
		 * Factory function for creating objects used to track statistics of function
		 * literals.
		 *
		 * @param name       - the identifier name to associate with the function
		 * @param token      - token responsible for creating the function
		 *                   object
		 * @param overwrites - a collection of properties that should
		 *                   override the corresponding default value of
		 *                   the new "functor" object
		 */
		private Functor(String name, Token token, Functor overwrites) {
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
			setYielded(false);
			setArrow(false);
			setMethod(false);
			setParams(null);

			if (token != null) {
				setLine(token.getLine());
				setCharacter(token.getCharacter());
				setMetrics(new Metrics(token));
			}

			overwrite(overwrites);

			if (context != null) {
				setScope(context.scope);
				setComparray(context.comparray);
			}
		}

		String getName() {
			return name;
		}

		Functor setName(String name) {
			this.tags.add(FunctorTag.NAME);
			this.name = StringUtils.defaultString(name);
			return this;
		}

		int getBreakage() {
			return breakage;
		}

		Functor setBreakage(int breakage) {
			this.tags.add(FunctorTag.BREAKAGE);
			this.breakage = breakage;
			return this;
		}

		void increaseBreakage() {
			breakage++;
		}

		void decreaseBreakage() {
			breakage--;
		}

		int getLoopage() {
			return loopage;
		}

		Functor setLoopage(int loopage) {
			this.tags.add(FunctorTag.LOOPAGE);
			this.loopage = loopage;
			return this;
		}

		void increaseLoopage() {
			loopage++;
		}

		void decreaseLoopage() {
			loopage--;
		}

		boolean isStrict() {
			return isStrict;
		}

		Functor setStrict(boolean isStrict) {
			this.tags.add(FunctorTag.ISSTRICT);
			this.isStrict = isStrict;
			return this;
		}

		boolean isGlobal() {
			return isGlobal;
		}

		Functor setGlobal(boolean isGlobal) {
			this.tags.add(FunctorTag.GLOBAL);
			this.isGlobal = isGlobal;
			return this;
		}

		int getLine() {
			return line;
		}

		Functor setLine(int line) {
			this.tags.add(FunctorTag.LINE);
			this.line = line;
			return this;
		}

		int getCharacter() {
			return character;
		}

		Functor setCharacter(int character) {
			this.tags.add(FunctorTag.CHARACTER);
			this.character = character;
			return this;
		}

		private Metrics getMetrics() {
			return metrics;
		}

		private Functor setMetrics(Metrics metrics) {
			this.tags.add(FunctorTag.METRICS);
			this.metrics = metrics;
			return this;
		}

		Token getStatement() {
			return statement;
		}

		Functor setStatement(Token statement) {
			this.tags.add(FunctorTag.STATEMENT);
			this.statement = statement;
			return this;
		}

		Functor getContext() {
			return context;
		}

		Functor setContext(Functor context) {
			this.tags.add(FunctorTag.CONTEXT);
			this.context = context;
			return this;
		}

		ScopeManager getScope() {
			return scope;
		}

		Functor setScope(ScopeManager scope) {
			this.tags.add(FunctorTag.SCOPE);
			this.scope = scope;
			return this;
		}

		private ArrayComprehension getComparray() {
			return comparray;
		}

		private Functor setComparray(ArrayComprehension comparray) {
			this.tags.add(FunctorTag.COMPARRAY);
			this.comparray = comparray;
			return this;
		}

		boolean isYielded() {
			return isYielded;
		}

		Functor setYielded(boolean isYielded) {
			this.tags.add(FunctorTag.YIELDED);
			this.isYielded = isYielded;
			return this;
		}

		boolean isArrow() {
			return isArrow;
		}

		Functor setArrow(boolean isArrow) {
			this.tags.add(FunctorTag.ARROW);
			this.isArrow = isArrow;
			return this;
		}

		boolean isAsync() {
			return isAsync;
		}

		Functor setAsync(boolean isAsync) {
			this.tags.add(FunctorTag.ASYNC);
			this.isAsync = isAsync;
			return this;
		}

		boolean isMethod() {
			return isMethod;
		}

		Functor setMethod(boolean isMethod) {
			this.tags.add(FunctorTag.METHOD);
			this.isMethod = isMethod;
			return this;
		}

		boolean hasSimpleParams() {
			return hasSimpleParams;
		}

		Functor setHasSimpleParams(boolean hasSimpleParams) {
			this.tags.add(FunctorTag.HASSIMPLEPARAMS);
			this.hasSimpleParams = hasSimpleParams;
			return this;
		}

		List<String> getParams() {
			return params != null ? Collections.unmodifiableList(params) : null;
		}

		Functor setParams(List<String> params) {
			this.tags.add(FunctorTag.PARAMS);
			if (params == null)
				this.params = null;
			else
				this.params = new ArrayList<>(params);
			return this;
		}

		Functor addParams(String... params) {
			if (params != null) {
				if (this.params == null)
					this.params = new ArrayList<>();
				this.params.addAll(Arrays.asList(params));
			}
			return this;
		}

		List<String> getOuterMutables() {
			return outerMutables != null ? Collections.unmodifiableList(outerMutables) : null;
		}

		Functor setOuterMutables(List<String> outerMutables) {
			this.tags.add(FunctorTag.OUTERMUTABLES);
			if (outerMutables == null)
				this.outerMutables = null;
			else
				this.outerMutables = new ArrayList<>(outerMutables);
			return this;
		}

		Functor addOuterMutables(String... outerMutables) {
			if (outerMutables != null) {
				if (this.outerMutables == null)
					this.outerMutables = new ArrayList<>();
				this.outerMutables.addAll(Arrays.asList(outerMutables));
			}
			return this;
		}

		int getLast() {
			return last;
		}

		Functor setLast(int last) {
			this.tags.add(FunctorTag.LAST);
			this.last = last;
			return this;
		}

		int getLastCharacter() {
			return lastcharacter;
		}

		Functor setLastCharacter(int lastcharacter) {
			this.tags.add(FunctorTag.LASTCHARACTER);
			this.lastcharacter = lastcharacter;
			return this;
		}

		UniversalContainer getUnusedOption() {
			return unusedOption;
		}

		Functor setUnusedOption(UniversalContainer unusedOption) {
			this.tags.add(FunctorTag.UNUSEDOPTION);
			this.unusedOption = ContainerFactory.undefinedContainerIfNull(unusedOption);
			return this;
		}

		String getVerb() {
			return verb;
		}

		Functor setVerb(String verb) {
			this.tags.add(FunctorTag.VERB);
			this.verb = StringUtils.defaultString(verb);
			return this;
		}

		void overwrite(Functor overwrites) {
			if (overwrites.tags.contains(FunctorTag.NAME))
				setName(overwrites.name);
			if (overwrites.tags.contains(FunctorTag.BREAKAGE))
				setBreakage(overwrites.breakage);
			if (overwrites.tags.contains(FunctorTag.LOOPAGE))
				setLoopage(overwrites.loopage);
			if (overwrites.tags.contains(FunctorTag.ISSTRICT))
				setStrict(overwrites.isStrict);
			if (overwrites.tags.contains(FunctorTag.GLOBAL))
				setGlobal(overwrites.isGlobal);
			if (overwrites.tags.contains(FunctorTag.LINE))
				setLine(overwrites.line);
			if (overwrites.tags.contains(FunctorTag.CHARACTER))
				setCharacter(overwrites.character);
			if (overwrites.tags.contains(FunctorTag.METRICS))
				setMetrics(overwrites.metrics);
			if (overwrites.tags.contains(FunctorTag.STATEMENT))
				setStatement(overwrites.statement);
			if (overwrites.tags.contains(FunctorTag.CONTEXT))
				setContext(overwrites.context);
			if (overwrites.tags.contains(FunctorTag.SCOPE))
				setScope(overwrites.scope);
			if (overwrites.tags.contains(FunctorTag.COMPARRAY))
				setComparray(overwrites.comparray);
			if (overwrites.tags.contains(FunctorTag.YIELDED))
				setYielded(overwrites.isYielded);
			if (overwrites.tags.contains(FunctorTag.ARROW))
				setArrow(overwrites.isArrow);
			if (overwrites.tags.contains(FunctorTag.ASYNC))
				setAsync(overwrites.isAsync);
			if (overwrites.tags.contains(FunctorTag.METHOD))
				setMethod(overwrites.isMethod);
			if (overwrites.tags.contains(FunctorTag.HASSIMPLEPARAMS))
				setHasSimpleParams(overwrites.hasSimpleParams);
			if (overwrites.tags.contains(FunctorTag.PARAMS))
				setParams(overwrites.params);
		}
	}

	/**
	 * Determine if the parser has begun parsing executable code.
	 * 
	 * @param {Token} funct - The current "functor" token
	 * 
	 * @returns {boolean}
	 */
	private boolean hasParsedCode(Functor funct) {
		return funct.isGlobal() && StringUtils.isEmpty(funct.getVerb());
	}

	/**
	 * This function is used as both a null-denotation method *and* a
	 * left-denotation method, meaning the first parameter is overloaded.
	 */
	private Token doTemplateLiteral(Token _this, int context, int rbp) {
		return doTemplateLiteral(_this, context, null);
	}

	/**
	 * This function is used as both a null-denotation method *and* a
	 * left-denotation method, meaning the first parameter is overloaded.
	 */
	private Token doTemplateLiteral(Token _this, int context, Token left) {
		Lexer.LexerContext ctx = _this.getContext();
		boolean noSubst = _this.isNoSubst();
		int depth = _this.getDepth();

		BooleanSupplier end = () -> {
			if (state.currToken().isTemplate() && state.currToken().isTail() &&
					state.currToken().getContext() == ctx) {
				return true;
			}
			boolean complete = (state.nextToken().isTemplate() && state.nextToken().isTail() &&
					state.nextToken().getContext() == ctx);
			if (complete)
				advance();
			return complete || state.nextToken().isUnclosed();
		};

		if (!noSubst) {
			while (!end.getAsBoolean()) {
				if (!state.nextToken().isTemplate() || state.nextToken().getDepth() > depth) {
					expression(context, 0); // should probably have different rbp?
				} else {
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
	 * @param context          The parsing context
	 * @param name             The identifier belonging to the function (if
	 *                         any)
	 * @param statement        The statement that triggered creation
	 *                         of the current function.
	 * @param type             If specified, either "generator" or "arrow"
	 * @param loneArg          The argument to the function in cases
	 *                         where it was defined using the
	 *                         single-argument shorthand
	 * @param parsedOpening    Whether the opening parenthesis has
	 *                         already been parsed
	 * @param classExprBinding Define a function with this
	 *                         identifier in the new function's
	 *                         scope, mimicking the bahavior of
	 *                         class expression names within
	 *                         the body of member functions.
	 */
	private Functor doFunction(int context, String name, Token statement, FunctionType type, Token loneArg,
			boolean parsedOpening, String classExprBinding, boolean isMethod, boolean ignoreLoopFunc) {
		Token token = null;
		boolean isGenerator = type == FunctionType.GENERATOR;
		boolean isArrow = type == FunctionType.ARROW;
		UniversalContainer oldOption = state.getOption();
		UniversalContainer oldIgnored = state.getIgnored();
		boolean isAsync = (context & ProdParams.PRE_ASYNC) != 0;

		context &= ~ProdParams.NOIN;
		context &= ~ProdParams.TRY_CLAUSE;

		if (isAsync) {
			context |= ProdParams.ASYNC;
		} else {
			context &= ~ProdParams.ASYNC;
		}

		if (isGenerator) {
			context |= ProdParams.YIELD;
		} else if (!isArrow) {
			context &= ~ProdParams.YIELD;
		}
		context &= ~ProdParams.PRE_ASYNC;

		state.setOption(state.getOption().create());
		state.setIgnored(state.getIgnored().create());

		state.setFunct(
				new Functor(StringUtils.isNotEmpty(name) ? name : state.getNameStack().infer(),
						state.nextToken(), new Functor()
								.setStatement(statement)
								.setContext(state.getFunct())
								.setArrow(isArrow)
								.setMethod(isMethod)
								.setAsync(isAsync)));

		Functor f = state.getFunct();
		token = state.currToken();

		functions.add(state.getFunct());

		// So that the function is available to itself and referencing itself is not
		// seen as a closure, add the function name to a new scope, but do not
		// test for unused (unused: false)
		// it is a new block scope so that params can override it, it can be block
		// scoped
		// but declarations inside the function don't cause already declared error
		state.getFunct().getScope().stack("functionouter");
		String internallyAccessibleName = StringUtils.defaultString(name, classExprBinding);
		if (!isMethod && StringUtils.isNotEmpty(internallyAccessibleName)) {
			state.getFunct().getScope().getBlock().add(
					internallyAccessibleName,
					StringUtils.isNotEmpty(classExprBinding) ? "class" : "function",
					state.currToken(),
					false);
		}

		if (!isArrow) {
			state.getFunct().getScope().getFunct().add("arguments", "var", token, false);
		}

		// create the param scope (params added in functionparams)
		state.getFunct().getScope().stack("functionparams");

		UniversalContainer paramsInfo = functionparams(context, loneArg, parsedOpening);
		if (paramsInfo.test()) {
			state.getFunct().setParams(paramsInfo.get("params").asList(String.class));
			state.getFunct().setHasSimpleParams(paramsInfo.get("isSimple").asBoolean());
			state.getFunct().getMetrics().arity = paramsInfo.asInt("arity");
			state.getFunct().getMetrics().verifyMaxParametersPerFunction();
		} else {
			state.getFunct().setParams(new ArrayList<>());
			state.getFunct().getMetrics().arity = 0;
			state.getFunct().setHasSimpleParams(true);
		}

		if (isArrow) {
			context &= ~ProdParams.YIELD;

			if (!state.inES6(true)) {
				warning("W119", state.currToken(), "arrow function syntax (=>)", "6");
			}

			if (loneArg == null) {
				advance("=>");
			}
		}

		block(context, false, true, true, isArrow);

		if (!state.getOption().test("noyield") && isGenerator && !state.getFunct().isYielded()) {
			warning("W124", state.currToken());
		}

		state.getFunct().getMetrics().verifyMaxStatementsPerFunction();
		state.getFunct().getMetrics().verifyMaxComplexityPerFunction();
		state.getFunct().setUnusedOption(state.getOption().get("unused"));
		state.setOption(oldOption);
		state.setIgnored(oldIgnored);
		state.getFunct().setLast(state.currToken().getLine());
		state.getFunct().setLastCharacter(state.currToken().getCharacter());

		// unstack the params scope
		state.getFunct().getScope().unstack(); // also does usage and label checks

		// unstack the function outer stack
		state.getFunct().getScope().unstack();

		state.setFunct(state.getFunct().getContext());

		if (!ignoreLoopFunc && !state.getOption().test("loopfunc") && state.getFunct().getLoopage() != 0) {
			// If the function we just parsed accesses any non-local variables
			// trigger a warning. Otherwise, the function is safe even within
			// a loop.
			if (f.getOuterMutables() != null) {
				warning("W083", token, String.join(", ", f.getOuterMutables()));
			}
		}

		return f;
	}

	private class Metrics {

		private Token functionStartToken;
		private int statementCount;
		private int nestedBlockDepth;
		private int complexityCount;
		private int arity;

		private Metrics(Token functionStartToken) {
			this.functionStartToken = functionStartToken;
			this.statementCount = 0;
			this.nestedBlockDepth = -1;
			this.complexityCount = 1;
			this.arity = 0;
		}

		private void verifyMaxStatementsPerFunction() {
			if (state.getOption().test("maxstatements") && statementCount > state.getOption().asInt("maxstatements")) {
				warning("W071", functionStartToken, String.valueOf(statementCount));
			}
		}

		private void verifyMaxParametersPerFunction() {
			if (state.getOption().isNumber("maxparams") &&
					arity > state.getOption().asInt("maxparams")) {
				warning("W072", functionStartToken, String.valueOf(arity));
			}
		}

		private void verifyMaxNestedBlockDepthPerFunction() {
			if (state.getOption().test("maxdepth") && nestedBlockDepth > 0
					&& nestedBlockDepth == state.getOption().asInt("maxdepth") + 1) {
				warning("W073", null, String.valueOf(nestedBlockDepth));
			}
		}

		private void verifyMaxComplexityPerFunction() {
			UniversalContainer max = state.getOption().get("maxcomplexity");
			int cc = complexityCount;
			if (max.test() && cc > max.asInt()) {
				warning("W074", functionStartToken, String.valueOf(cc));
			}
		}
	}

	private void increaseComplexityCount() {
		state.getFunct().getMetrics().complexityCount += 1;
	}

	// Parse assignments that were found instead of conditionals.
	// For example: if (a = 1) { ... }

	private void checkCondAssignment(Token token) {
		if (token == null || token.isParen()) {
			return;
		}

		if (token.getId().equals(",")) {
			checkCondAssignment(token.getRight());
			return;
		}

		switch (token.getId()) {
			case "=":
			case "+=":
			case "-=":
			case "*=":
			case "%=":
			case "&=":
			case "|=":
			case "^=":
			case "/=":
				if (!state.getOption().test("boss")) {
					warning("W084", token);
				}
		}
	}

	/**
	 * Validate the properties defined within an object literal or class body.
	 * See the `saveAccessor` and `saveProperty` functions for more detail.
	 * 
	 * @param props - Collection of objects describing the properties
	 *              encountered
	 */
	private void checkProperties(UniversalContainer props) {
		// Check for lonely setters if in the ES5 mode.
		if (state.inES5()) {
			for (String name : props.keys()) {
				if (props.test(name) && props.get(name).test("setterToken") && !props.get(name).test("getterToken") &&
						!props.get(name).test("static")) {
					warning("W078", props.get(name).<Token>valueOf("setterToken"));
				}
			}
		}
	}

	private Token metaProperty(int context, String name, Runnable c) {
		if (checkPunctuator(state.nextToken(), ".")) {
			String left = state.currToken().getId();
			advance(".");
			String id = identifier(context);
			state.currToken().setMetaProperty(true);
			if (!name.equals(id)) {
				error("E057", state.prevToken(), left, id);
			} else {
				c.run();
			}
			return state.currToken();
		}

		return null;
	}

	private List<Token> destructuringPattern(int context, boolean openingParsed, boolean isAssignment) {
		context &= ~ProdParams.NOIN;

		if (!state.inES6()) {
			warning(
					"W104",
					state.currToken(),
					isAssignment ? "destructuring assignment" : "destructuring binding",
					"6");
		}

		return destructuringPatternRecursive(context, openingParsed, isAssignment);
	}

	private void nextInnerDE(int context, boolean openingParsed, boolean isAssignment, List<Token> identifiers) {
		List<Token> ids;
		String ident = null;

		if (checkPunctuators(state.nextToken(), "[", "{")) {
			ids = destructuringPatternRecursive(context, false, isAssignment);
			for (int idx = 0; idx < ids.size(); idx++) {
				identifiers.add(new Token(ids.get(idx).getId(), ids.get(idx).getToken()));
			}
		} else if (checkPunctuator(state.nextToken(), ",")) {
			identifiers.add(new Token(null, state.currToken()));
		} else if (checkPunctuator(state.nextToken(), "(")) {
			advance("(");
			nextInnerDE(context, openingParsed, isAssignment, identifiers);
			advance(")");
		} else {
			if (isAssignment) {
				Token assignTarget = expression(context, 20);
				if (assignTarget != null) {
					checkLeftSideAssign(context, assignTarget);

					// if the target was a simple identifier, add it to the list to return
					if (assignTarget.isIdentifier()) {
						ident = assignTarget.getValue();
					}
				}
			} else {
				ident = identifier(context);
			}
			if (StringUtils.isNotEmpty(ident)) {
				identifiers.add(new Token(ident, state.currToken()));
			}
		}
	}

	private List<Token> destructuringPatternRecursive(int context, boolean openingParsed, boolean isAssignment) {
		List<Token> identifiers = new ArrayList<>();
		Token firstToken = openingParsed ? state.currToken() : state.nextToken();

		IntConsumer assignmentProperty = c -> {
			String id = null;
			if (checkPunctuator(state.nextToken(), "[")) {
				advance("[");
				expression(c, 10);
				advance("]");
				advance(":");
				nextInnerDE(context, openingParsed, isAssignment, identifiers);
			} else if (state.nextToken().getId().equals("(string)") ||
					state.nextToken().getId().equals("(number)")) {
				advance();
				advance(":");
				nextInnerDE(context, openingParsed, isAssignment, identifiers);
			} else {
				// this id will either be the property name or the property name and the
				// assigning identifier
				boolean isRest = spreadrest("rest");

				if (isRest) {
					if (!state.inES9()) {
						warning("W119", state.nextToken(), "object rest property", "9");
					}

					// Due to visual symmetry with the array rest property (and the early
					// design of the language feature), developers may mistakenly assume
					// any expression is valid in this position. If the next token is not
					// an identifier, attempt to parse an expression and issue an error.
					// order to recover more gracefully from this condition.
					if (state.nextToken().getType() == Token.Type.IDENTIFIER) {
						id = identifier(c);
					} else {
						Token expr = expression(c, 10);
						error("E030", expr, expr.getValue());
					}
				} else {
					id = identifier(c);
				}

				if (!isRest && checkPunctuator(state.nextToken(), ":")) {
					advance(":");
					nextInnerDE(context, openingParsed, isAssignment, identifiers);
				} else if (StringUtils.isNotEmpty(id)) {
					// in this case we are assigning (not declaring), so check assignment
					if (isAssignment) {
						checkLeftSideAssign(c, state.currToken());
					}
					identifiers.add(new Token(id, state.currToken()));
				}

				if (isRest && checkPunctuator(state.nextToken(), ",")) {
					warning("W130", state.nextToken());
				}
			}
		};

		Token id, value;
		if (checkPunctuator(firstToken, "[")) {
			if (!openingParsed) {
				advance("[");
			}
			if (checkPunctuator(state.nextToken(), "]")) {
				warning("W137", state.currToken());
			}
			boolean element_after_rest = false;
			while (!checkPunctuator(state.nextToken(), "]")) {
				boolean isRest = spreadrest("rest");

				nextInnerDE(context, openingParsed, isAssignment, identifiers);

				if (isRest && !element_after_rest &&
						checkPunctuator(state.nextToken(), ",")) {
					warning("W130", state.nextToken());
					element_after_rest = true;
				}
				if (!isRest && checkPunctuator(state.nextToken(), "=")) {
					if (checkPunctuator(state.prevToken(), "...")) {
						advance("]");
					} else {
						advance("=");
					}
					id = state.prevToken();
					value = expression(context, 10);
					if (value != null && value.isIdentifier() && value.getValue().equals("undefined")) {
						warning("W080", id, id.getValue());
					}
				}
				if (!checkPunctuator(state.nextToken(), "]")) {
					advance(",");
				}
			}
			advance("]");
		} else if (checkPunctuator(firstToken, "{")) {
			if (!openingParsed) {
				advance("{");
			}
			if (checkPunctuator(state.nextToken(), "}")) {
				warning("W137", state.currToken());
			}
			while (!checkPunctuator(state.nextToken(), "}")) {
				assignmentProperty.accept(context);
				if (checkPunctuator(state.nextToken(), "=")) {
					advance("=");
					id = state.prevToken();
					value = expression(context, 10);
					if (value != null && value.isIdentifier() && value.getValue().equals("undefined")) {
						warning("W080", id, id.getValue());
					}
				}
				if (!checkPunctuator(state.nextToken(), "}")) {
					advance(",");
					if (checkPunctuator(state.nextToken(), "}")) {
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

	private void destructuringPatternMatch(List<Token> tokens, Token value) {
		List<Token> first = value.getFirstTokens();

		if (first == null)
			return;

		int size = Math.max(tokens.size(), first.size());
		for (int i = 0; i < size; i++) {
			Token token = tokens.size() > i ? tokens.get(i) : null;
			Token val = first.size() > i ? first.get(i) : null;

			if (token != null && val != null)
				token.setFirstTokens(val);
			else if (token != null && token.getFirstToken() != null && val == null)
				warning("W080", token.getFirstToken(), token.getFirstToken().getValue());
		}
	}

	// JSHINT_BUG: shouldn't context be as first argument?
	private Token blockVariableStatement(String type, Token statement, int context) {
		// used for both let and const statements

		boolean noin = (context & ProdParams.NOIN) != 0;
		boolean isLet = type.equals("let");
		boolean isConst = type.equals("const");
		List<Token> tokens;
		boolean lone = false;
		boolean letblock = false;

		if (!state.inES6()) {
			warning("W104", state.currToken(), type, "6");
		}

		if (isLet && isMozillaLet()) {
			advance("(");
			state.getFunct().getScope().stack();
			letblock = true;
			statement.setDeclaration(false);
		}

		statement.setFirstTokens();
		for (;;) {
			List<Token> names = new ArrayList<>();
			if (state.nextToken().getValue().equals("{") || state.nextToken().getValue().equals("[")) {
				tokens = destructuringPattern(context, false, false);
				lone = false;
			} else {
				tokens = new ArrayList<>();
				tokens.add(new Token(identifier(context), state.currToken()));
				lone = true;
			}

			// A `const` declaration without an initializer is permissible within the
			// head of for-in and for-of statements. If this binding list is being
			// parsed as part of a `for` statement of any kind, allow the initializer
			// to be omitted. Although this may erroneously allow such forms from
			// "C-style" `for` statements (i.e. `for (const x;;) {}`, the `for`
			// statement logic includes dedicated logic to issue the error for such
			// cases.
			if (!noin && isConst && !state.nextToken().getId().equals("=")) {
				warning("E012", state.currToken(), state.currToken().getValue());
			}

			for (Token t : tokens) {
				// It is a Syntax Error if the BoundNames of BindingList contains
				// "let".
				if (t.getId().equals("let")) {
					warning("W024", t.getToken(), t.getId());
				}

				if (state.getFunct().getScope().getBlock().isGlobal()) {
					if (BooleanUtils.isFalse(predefined.get(t.getId()))) {
						warning("W079", t.getToken(), t.getId());
					}
				}
				if (StringUtils.isNotEmpty(t.getId())) {
					state.getFunct().getScope().addbinding(t.getId(), type, t.getToken());
					names.add(t.getToken());
				}
			}

			if (state.nextToken().getId().equals("=")) {
				statement.setHasInitializer(true);

				advance("=");
				if (!noin && peek(0).getId().equals("=") && state.nextToken().isIdentifier()) {
					warning("W120", state.nextToken(), state.nextToken().getValue());
				}
				Token id = state.prevToken();
				Token value = expression(context, 10);
				if (value != null) {
					if (value.isIdentifier() && value.getValue().equals("undefined")) {
						warning("W080", id, id.getValue());
					}
					if (!lone) {
						destructuringPatternMatch(names, value);
					}
				}
			}

			// Bindings are not immediately initialized in for-in and for-of
			// statements. As with `const` initializers (described above), the `for`
			// statement parsing logic includes
			if (!state.nextToken().getValue().equals("in") && !state.nextToken().getValue().equals("of")) {
				for (Token t : tokens) {
					state.getFunct().getScope().initialize(t.getId());
				}
			}

			statement.addFirstTokens(names);

			if (!state.nextToken().getId().equals(",")) {
				break;
			}

			statement.setHasComma(true);
			advance(",");
			checkComma();
		}
		if (letblock) {
			advance(")");
			block(context, true, true);
			statement.setBlock(true);
			state.getFunct().getScope().unstack();
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
	 * 
	 * <pre>
	 *     let(x)
	 *     {
	 *       typeof x;
	 *     }
	 * </pre>
	 * 
	 * @return
	 */
	private boolean isMozillaLet() {
		return state.nextToken().getId().equals("(") && state.inMoz();
	}

	/**
	 * Parsing logic for non-standard Mozilla implementation of `yield`
	 * expressions.
	 */
	private Token mozYield(Token _this, int context) {
		Token prev = state.prevToken();
		if (state.inES6(true) && (context & ProdParams.YIELD) == 0) {
			error("E046", state.currToken(), "yield");
		}
		state.getFunct().setYielded(true);
		boolean delegatingYield = false;

		if (state.nextToken().getValue().equals("*")) {
			delegatingYield = true;
			advance("*");
		}

		if (sameLine(_this, state.nextToken())) {
			if (delegatingYield ||
					(!state.nextToken().getId().equals(";") && !state.getOption().test("asi") &&
							!state.nextToken().isReach() && state.nextToken().getNud() != null)) {
				nobreaknonadjacent(state.currToken(), state.nextToken());

				Token first = expression(context, 10);
				_this.setFirstTokens(first);

				if (first.getType() == Token.Type.PUNCTUATOR && first.getValue().equals("=") && !first.isParen()
						&& !state.getOption().test("boss")) {
					warning("W093", first);
				}
			}
			if (!state.nextToken().getId().equals(")") &&
					(prev.getLbp() > 30 || (!prev.isAssign() && !isEndOfExpr()))) {
				error("E050", _this);
			}
		} else if (!state.getOption().test("asi")) {
			nolinebreak(_this); // always warn (Line breaking error)
		}
		return _this;
	}

	private void buildStatementTable() {
		Token conststatement = stmt("const", _this -> context -> {
			return blockVariableStatement("const", _this, context);
		});
		conststatement.setExps(true);
		conststatement.setDeclaration(true);

		Token letstatement = stmt("let", _this -> context -> {
			return blockVariableStatement("let", _this, context);
		});
		letstatement.setNud(_this -> context -> rbp -> {
			if (isMozillaLet()) {
				// create a new block scope we use only for the current expression
				state.getFunct().getScope().stack();
				advance("(");
				state.prevToken().fud(context);
				advance(")");
				expression(context, rbp);
				state.getFunct().getScope().unstack();
			} else {
				_this.setExps(false);
				return state.getSyntax().get("(identifier)").nud(_this, context, rbp);
			}
			return null;
		});
		letstatement.setMeta(new Token.Meta(true, false, true, false, null));
		letstatement.setExps(true);
		letstatement.setDeclaration(true);
		letstatement.setUseFud(_this -> context -> {
			Token next = state.nextToken();

			if (_this.getLine() != next.getLine() && !state.inES6()) {
				return false;
			}

			// JSHint generally interprets `let` as a reserved word even though it is
			// not considered as such by the ECMAScript specification because doing so
			// simplifies parsing logic. It is special-cased here so that code such as
			//
			// let
			// let
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
			List<Token> tokens;
			boolean lone = false;

			_this.setFirstTokens();
			for (;;) {
				List<Token> names = new ArrayList<>();
				if (state.nextToken().getValue().equals("{") || state.nextToken().getValue().equals("[")) {
					tokens = destructuringPattern(context, false, false);
					lone = false;
				} else {
					tokens = new ArrayList<>();
					String id = identifier(context);

					if (StringUtils.isNotEmpty(id)) {
						tokens.add(new Token(id, state.currToken()));
					}

					lone = true;
				}

				if (state.getOption().test("varstmt")) {
					warning("W132", _this);
				}

				for (Token t : tokens) {
					if (state.getFunct().isGlobal() && !state.impliedClosure()) {
						if (BooleanUtils.isFalse(predefined.get(t.getId()))) {
							warning("W079", t.getToken(), t.getId());
						} else if (state.getOption().get("futurehostile").equals(false)) {
							if ((!state.inES5()
									&& BooleanUtils.isFalse(Vars.ecmaIdentifiers.get(5).get(t.getId()))) ||
									(!state.inES6()
											&& BooleanUtils.isFalse(Vars.ecmaIdentifiers.get(6).get(t.getId())))) {
								warning("W129", t.getToken(), t.getId());
							}
						}

					}

					if (StringUtils.isNotEmpty(t.getId())) {
						state.getFunct().getScope().addbinding(t.getId(), "var", t.getToken());

						names.add(t.getToken());
					}
				}

				if (state.nextToken().getId().equals("=")) {
					_this.setHasInitializer(true);

					state.getNameStack().set(state.currToken());

					advance("=");
					if (peek(0).getId().equals("=") && state.nextToken().isIdentifier()) {
						if (!noin &&
								state.getFunct().getParams() == null ||
								!state.getFunct().getParams().contains(state.nextToken().getValue())) {
							warning("W120", state.nextToken(), state.nextToken().getValue());
						}
					}
					Token id = state.prevToken();
					Token value = expression(context, 10);
					if (value != null) {
						if (state.getFunct().getLoopage() == 0 && value.isIdentifier() &&
								value.getValue().equals("undefined")) {
							warning("W080", id, id.getValue());
						}
						if (!lone) {
							destructuringPatternMatch(names, value);
						}
					}
				}

				_this.addFirstTokens(names);

				if (!state.nextToken().getId().equals(",")) {
					break;
				}
				_this.setHasComma(true);
				advance(",");
				checkComma();
			}

			return _this;
		});
		varstatement.setExps(true);

		blockstmt("function", _this -> context -> {
			boolean inexport = (context & ProdParams.EXPORT) != 0;
			boolean generator = false;
			boolean isAsync = (context & ProdParams.PRE_ASYNC) != 0;
			String labelType = "";

			if (isAsync) {
				labelType = "async ";
			}

			if (state.nextToken().getValue().equals("*")) {
				if (isAsync && !state.inES9()) {
					warning("W119", state.prevToken(), "async generators", "9");
				} else if (!isAsync && !state.inES6(true)) {
					warning("W119", state.nextToken(), "function*", "6");
				}

				advance("*");
				labelType += "generator ";
				generator = true;
			}

			labelType += "function";

			if (inblock) {
				warning("W082", state.currToken());
			}
			// JSHINT_BUG: this.name must be string not Token, better to use dedeicated
			// variable for this
			_this.setTokenName(StringUtils.isNotEmpty(optionalidentifier(context)) ? state.currToken() : null);

			if (_this.getTokenName() == null) {
				if (!inexport) {
					warning("W025");
				}
			} else {
				state.getFunct().getScope().addbinding(_this.getTokenName().getValue(), labelType, state.currToken(),
						true);
			}

			Functor f = doFunction(
					context,
					(_this.getTokenName() != null ? _this.getTokenName().getValue() : null),
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
			boolean enablesStrictMode = f.isStrict() && !state.isStrict();
			if (_this.getTokenName() != null && (f.getName().equals("arguments") || f.getName().equals("eval")) &&
					enablesStrictMode) {
				error("E008", _this.getTokenName());
			}

			// Although the parser correctly recognizes the statement boundary in this
			// condition, it's support for the invalid "empty grouping" expression
			// makes it tolerant of productions such as `function f() {}();`.
			if (state.nextToken().getId().equals("(") && peek().getId().equals(")") && !peek(1).getId().equals("=>") &&
					state.nextToken().getLine() == state.currToken().getLine()) {
				error("E039");
			}
			return _this;
		}).setDeclaration(true);

		prefix("function", _this -> context -> rbp -> {
			boolean generator = false;
			boolean isAsync = (context & ProdParams.PRE_ASYNC) != 0;

			if (state.nextToken().getValue().equals("*")) {
				if (isAsync && !state.inES9()) {
					warning("W119", state.prevToken(), "async generators", "9");
				} else if (!isAsync && !state.inES6(true)) {
					warning("W119", state.currToken(), "function*", "6");
				}

				advance("*");
				generator = true;
			}

			// This context modification restricts the use of `await` as the optional
			// BindingIdentifier in async function expressions.
			// JSHINT_BUG: this.name must be string not Token, better to use dedeicated
			// variable for this
			_this.setTokenName(
					StringUtils.isNotEmpty(optionalidentifier(isAsync ? context | ProdParams.ASYNC : context))
							? state.currToken()
							: null);

			Functor f = doFunction(
					context,
					(_this.getTokenName() != null ? _this.getTokenName().getValue() : null),
					null,
					(generator ? FunctionType.GENERATOR : null),
					null,
					false,
					null,
					false,
					false);

			if (generator && _this.getTokenName() != null && _this.getTokenName().getValue().equals("yield")) {
				error("E024", _this.getTokenName(), "yield");
			}

			if (_this.getTokenName() != null && (f.getName().equals("arguments") || f.getName().equals("eval")) &&
					f.isStrict()) {
				error("E008", _this.getTokenName());
			}

			return _this;
		});

		blockstmt("if", _this -> context -> {
			Token t = state.nextToken();
			increaseComplexityCount();
			advance("(");
			Token expr = expression(context, 0);

			if (expr == null) {
				quit("E041", _this);
			}

			checkCondAssignment(expr);

			// When the if is within a for-in loop, check if the condition
			// starts with a negation operator
			Token forinifcheck = null;
			if (state.getOption().test("forin") && state.isForinifcheckneeded()) {
				state.setForinifcheckneeded(false); // We only need to analyze the first if inside the loop
				forinifcheck = state.getForinifchecks().size() > 0
						? state.getForinifchecks().get(state.getForinifchecks().size() - 1)
						: null;
				if (expr.getType() == Token.Type.PUNCTUATOR && expr.getValue().equals("!")) {
					forinifcheck.setType(Token.Type.NEGATIVE);
				} else {
					forinifcheck.setType(Token.Type.POSITIVE);
				}
			}

			advance(")", t);
			List<Token> s = block(context, true, true);

			// When the if is within a for-in loop and the condition has a negative form,
			// check if the body contains nothing but a continue statement
			if (forinifcheck != null && forinifcheck.getType() == Token.Type.NEGATIVE) {
				if (s != null && s.size() > 0 && s.get(0) != null && s.get(0).getType() == Token.Type.IDENTIFIER
						&& s.get(0).getValue().equals("continue")) {
					forinifcheck.setType(Token.Type.NEGATIVE_WITH_CONTINUE);
				}
			}

			if (state.nextToken().getId().equals("else")) {
				advance("else");
				if (state.nextToken().getId().equals("if") || state.nextToken().getId().equals("switch")) {
					statement(context);
				} else {
					block(context, true, true);
				}
			}
			return _this;
		});

		blockstmt("try", _this -> context -> {
			boolean b = false;
			boolean hasParameter = false;

			Runnable catchParameter = () -> {
				advance("(");

				if (checkPunctuators(state.nextToken(), "[", "{")) {
					List<Token> tokens = destructuringPattern(context, false, false);
					for (Token token : tokens) {
						if (StringUtils.isNotEmpty(token.getId())) {
							state.getFunct().getScope().addParam(token.getId(), token.getToken(), "exception");
						}
					}
				} else if (state.nextToken().getType() != Token.Type.IDENTIFIER) {
					warning("E030", state.nextToken(), state.nextToken().getValue());
				} else {
					// only advance if an identifier is present. This allows JSHint to
					// recover from the case where no value is specified.
					state.getFunct().getScope().addParam(identifier(context), state.currToken(), "exception");
				}

				if (state.nextToken().getValue().equals("if")) {
					if (!state.inMoz()) {
						warning("W118", state.currToken(), "catch filter");
					}
					advance("if");
					expression(context, 0);
				}

				advance(")");
			};

			block(context | ProdParams.TRY_CLAUSE, true);

			while (state.nextToken().getId().equals("catch")) {
				increaseComplexityCount();
				if (b && (!state.inMoz())) {
					warning("W118", state.nextToken(), "multiple catch blocks");
				}
				advance("catch");
				if (!state.nextToken().getId().equals("{")) {
					state.getFunct().getScope().stack("catchparams");
					hasParameter = true;
					catchParameter.run();
				} else if (!state.inES10()) {
					warning("W119", state.currToken(), "optional catch binding", "10");
				}
				block(context, false);

				if (hasParameter) {
					state.getFunct().getScope().unstack();
					hasParameter = false;
				}
				b = true;
			}

			if (state.nextToken().getId().equals("finally")) {
				advance("finally");
				block(context, true);
				return null;
			}

			if (!b) {
				error("E021", state.nextToken(), "catch", state.nextToken().getValue());
			}

			return _this;
		});

		blockstmt("while", _this -> context -> {
			Token t = state.nextToken();
			state.getFunct().increaseBreakage();
			state.getFunct().increaseLoopage();
			increaseComplexityCount();
			advance("(");
			checkCondAssignment(expression(context, 0));
			advance(")", t);
			block(context, true, true);
			state.getFunct().decreaseBreakage();
			state.getFunct().decreaseLoopage();
			return _this;
		}).setLabelled(true);

		blockstmt("with", _this -> context -> {
			Token t = state.nextToken();
			if (state.isStrict()) {
				error("E010", state.currToken());
			} else if (!state.getOption().get("withstmt").test()) {
				warning("W085", state.currToken());
			}

			advance("(");
			expression(context, 0);
			advance(")", t);
			block(context, true, true);

			return _this;
		});

		blockstmt("switch", _this -> context -> {
			Token t = state.nextToken();
			boolean g = false;
			boolean noindent = false;
			boolean seenCase = false;

			state.getFunct().increaseBreakage();
			advance("(");
			checkCondAssignment(expression(context, 0));
			advance(")", t);
			t = state.nextToken();
			advance("{");
			state.getFunct().getScope().stack();

			if (state.nextToken().getFrom() == indent)
				noindent = true;

			if (!noindent)
				indent += state.getOption().asInt("indent");

			for (;;) {
				switch (state.nextToken().getId()) {
					case "case":
						switch (state.getFunct().getVerb()) {
							case "yield":
							case "break":
							case "case":
							case "continue":
							case "return":
							case "switch":
							case "throw":
								break;
							case "default":
								if (state.getOption().test("leanswitch")) {
									warning("W145", state.nextToken());
								}

								break;
							default:
								// You can tell JSHint that you don't use break intentionally by
								// adding a comment /* falls through */ on a line just before
								// the next `case`.
								if (!state.currToken().isCaseFallsThrough()) {
									warning("W086", state.currToken(), "case");
								}
						}

						advance("case");
						expression(context, 0);
						seenCase = true;
						increaseComplexityCount();
						g = true;
						advance(":");
						state.getFunct().setVerb("case");
						break;
					case "default":
						switch (state.getFunct().getVerb()) {
							case "yield":
							case "break":
							case "continue":
							case "return":
							case "throw":
								break;
							case "case":
								if (state.getOption().test("leanswitch")) {
									warning("W145", state.currToken());
								}

								break;
							default:
								// Do not display a warning if 'default' is the first statement or if
								// there is a special /* falls through */ comment.
								if (seenCase && !state.currToken().isCaseFallsThrough()) {
									warning("W086", state.currToken(), "default");
								}
						}

						advance("default");
						g = true;
						advance(":");
						state.getFunct().setVerb("default");
						break;
					case "}":
						if (!noindent)
							indent -= state.getOption().asInt("indent");

						advance("}", t);
						state.getFunct().getScope().unstack();
						state.getFunct().decreaseBreakage();
						state.getFunct().setVerb("");
						return null;
					case "(end)":
						error("E023", state.nextToken(), "}");
						return null;
					default:
						indent += state.getOption().asInt("indent");
						if (g) {
							switch (state.currToken().getId()) {
								case ",":
									error("E040");
									return null;
								case ":":
									g = false;
									statements(context);
									break;
								default:
									error("E025", state.currToken());
									return null;
							}
						} else {
							if (state.currToken().getId().equals(":")) {
								advance(":");
								error("E024", state.currToken(), ":");
								statements(context);
							} else {
								error("E021", state.nextToken(), "case", state.nextToken().getValue());
								return null;
							}
						}
						indent -= state.getOption().asInt("indent");
				}
			}
		}).setLabelled(true);

		stmt("debugger", _this -> context -> {
			if (!state.getOption().get("debug").test()) {
				warning("W087", _this);
			}
			return _this;
		}).setExps(true);

		{
			Token x = stmt("do", _this -> context -> {
				state.getFunct().increaseBreakage();
				state.getFunct().increaseLoopage();
				increaseComplexityCount();

				_this.setFirstTokens(block(context, true, true));
				advance("while");
				Token t = state.nextToken();
				advance("(");
				checkCondAssignment(expression(context, 0));
				advance(")", t);
				state.getFunct().decreaseBreakage();
				state.getFunct().decreaseLoopage();
				return _this;
			});
			x.setLabelled(true);
			x.setExps(true);
		}

		blockstmt("for", _this -> context -> {
			Token t = state.nextToken();
			boolean letscope = false;
			boolean isAsync = false;
			Token foreachtok = null;

			if (t.getValue().equals("each")) {
				foreachtok = t;
				advance("each");
				if (!state.inMoz()) {
					warning("W118", state.currToken(), "for each");
				}
			}

			if (state.nextToken().isIdentifier() && state.nextToken().getValue().equals("await")) {
				advance("await");
				isAsync = true;

				if ((context & ProdParams.ASYNC) == 0) {
					error("E024", state.currToken(), "await");
				} else if (!state.inES9()) {
					warning("W119", state.currToken(), "asynchronous iteration", "9");
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

			if (state.nextToken().getId().equals("var")) {
				advance("var");
				decl = state.currToken().fud(headContext);
				comma = decl.hasComma() ? decl : null;
				initializer = decl.hasInitializer() ? decl : null;
			} else if (state.nextToken().getId().equals("const") ||
			// The "let" keyword only signals a lexical binding if it is followed by
			// an identifier, `{`, or `[`. Otherwise, it should be parsed as an
			// IdentifierReference (i.e. in a subsquent branch).
					(state.nextToken().getId().equals("let") &&
							((afterNext.isIdentifier() && !afterNext.getId().equals("in")) ||
									checkPunctuators(afterNext, "{", "[")))) {
				advance(state.nextToken().getId());
				// create a new block scope
				letscope = true;
				state.getFunct().getScope().stack();
				decl = state.currToken().fud(headContext);
				comma = decl.hasComma() ? decl : null;
				initializer = decl.hasInitializer() ? decl : null;
			} else if (!checkPunctuator(state.nextToken(), ";")) {
				List<Token> targets = new ArrayList<>();

				while (!state.nextToken().getValue().equals("in") &&
						!state.nextToken().getValue().equals("of") &&
						!checkPunctuator(state.nextToken(), ";")) {

					if (checkPunctuators(state.nextToken(), "{", "[")) {
						for (Token elem : destructuringPattern(headContext, false, true)) {
							targets.add(elem.getToken());
						}
						if (checkPunctuator(state.nextToken(), "=")) {
							advance("=");
							initializer = state.currToken();
							expression(headContext, 10);
						}
					} else {
						target = expression(headContext, 10);

						if (target != null) {
							if (target.getType() == Token.Type.IDENTIFIER) {
								targets.add(target);
							} else if (checkPunctuator(target, "=")) {
								initializer = target;
								targets.add(target);
							}
						}
					}

					if (checkPunctuator(state.nextToken(), ",")) {
						advance(",");

						if (comma == null) {
							comma = state.currToken();
						}
					}
				}

				// checkLeftSideAssign(target, nextop);

				// In the event of a syntax error, do not issue warnings regarding the
				// implicit creation of bindings.
				if (initializer == null && comma == null) {
					for (Token token : targets) {
						if (!state.getFunct().getScope().has(token.getValue())) {
							warning("W088", token, token.getValue());
						}
					}
				}
			}

			nextop = state.nextToken();

			if (isAsync && !nextop.getValue().equals("of")) {
				error("E066", nextop);
			}

			// if we're in a for (… in|of …) statement
			if (nextop.getValue().equals("in") || nextop.getValue().equals("of")) {
				if (nextop.getValue().equals("of")) {
					bindingPower = 20;

					if (!state.inES6()) {
						warning("W104", nextop, "for of", "6");
					}
				} else {
					bindingPower = 0;
				}
				if (comma != null) {
					error("W133", comma, nextop.getValue(), "more than one ForBinding");
				}
				if (initializer != null) {
					error("W133", initializer, nextop.getValue(), "initializer is forbidden");
				}
				if (target != null && comma == null && initializer == null) {
					checkLeftSideAssign(context, target, nextop);
				}

				advance(nextop.getValue());

				// The binding power is variable because for-in statements accept any
				// Expression in this position, while for-of statements are limited to
				// AssignmentExpressions. For example:
				//
				// for ( LeftHandSideExpression in Expression ) Statement
				// for ( LeftHandSideExpression of AssignmentExpression ) Statement
				expression(context, bindingPower);
				advance(")", t);

				if (nextop.getValue().equals("in") && state.getOption().test("forin")) {
					state.setForinifcheckneeded(true);

					if (state.getForinifchecks() == null) {
						state.setForinifchecks(new ArrayList<>());
					}

					// Push a new for-in-if check onto the stack. The type will be modified
					// when the loop's body is parsed and a suitable if statement exists.
					state.getForinifchecks().add(new Token(Token.Type.NONE));
				}

				state.getFunct().increaseBreakage();
				state.getFunct().increaseLoopage();

				List<Token> s = block(context, true, true);

				if (nextop.getValue().equals("in") && state.getOption().test("forin")) {
					if (state.getForinifchecks() != null && state.getForinifchecks().size() > 0) {
						Token check = state.getForinifchecks().remove(state.getForinifchecks().size() - 1);

						if (// No if statement or not the first statement in loop body
						s != null && s.size() > 0 && (s.get(0) == null || !s.get(0).getValue().equals("if")) ||
						// Positive if statement is not the only one in loop body
								check.getType() == Token.Type.POSITIVE && s.size() > 1 ||
						// Negative if statement but no continue
								check.getType() == Token.Type.NEGATIVE) {
							warning("W089", _this);
						}
					}

					// Reset the flag in case no if statement was contained in the loop body
					state.setForinifcheckneeded(false);
				}

				state.getFunct().decreaseBreakage();
				state.getFunct().decreaseLoopage();
			} else {
				if (foreachtok != null) {
					error("E045", foreachtok);
				}

				advance(";");
				if (decl != null && decl.getFirstTokens() != null && decl.getFirstTokens().size() > 0) {
					if (decl.getValue().equals("const") && !decl.hasInitializer()) {
						warning("E012", decl, decl.getFirstTokens().get(0).getValue());
					}
					for (Token token : decl.getFirstTokens()) {
						state.getFunct().getScope().initialize(token.getValue());
					}
				}

				// start loopage after the first ; as the next two expressions are executed
				// on every loop
				state.getFunct().increaseLoopage();
				if (!state.nextToken().getId().equals(";")) {
					checkCondAssignment(expression(context, 0));
				}

				advance(";");
				if (state.nextToken().getId().equals(";")) {
					error("E021", state.nextToken(), ")", ";");
				}
				if (!state.nextToken().getId().equals(")")) {
					for (;;) {
						expression(context, 0);
						if (!state.nextToken().getId().equals(",")) {
							break;
						}
						advance(",");
						checkComma();
					}
				}
				advance(")", t);
				state.getFunct().increaseBreakage();
				block(context, true, true);
				state.getFunct().decreaseBreakage();
				state.getFunct().decreaseLoopage();
			}

			// unstack loop blockscope
			if (letscope) {
				state.getFunct().getScope().unstack();
			}
			return _this;
		}).setLabelled(true);

		stmt("break", _this -> context -> {
			String v = state.nextToken().getValue();

			if (state.nextToken().isIdentifier() &&
					sameLine(state.currToken(), state.nextToken())) {
				if (!state.getFunct().getScope().getFunct().hasLabel(v)) {
					warning("W090", state.nextToken(), v);
				}
				_this.setFirstTokens(state.nextToken());
				advance();
			} else {
				if (state.getFunct().getBreakage() == 0)
					warning("W052", state.nextToken(), _this.getValue());
			}

			reachable(_this);

			return _this;
		}).setExps(true);

		stmt("continue", _this -> context -> {
			String v = state.nextToken().getValue();

			if (state.getFunct().getBreakage() == 0 || state.getFunct().getLoopage() == 0) {
				warning("W052", state.nextToken(), _this.getValue());
			}

			if (state.nextToken().isIdentifier()) {
				if (sameLine(state.currToken(), state.nextToken())) {
					if (!state.getFunct().getScope().getFunct().hasLabel(v)) {
						warning("W090", state.nextToken(), v);
					}
					_this.setFirstTokens(state.nextToken());
					advance();
				}
			}

			reachable(_this);

			return _this;
		}).setExps(true);

		stmt("return", _this -> context -> {
			if (sameLine(_this, state.nextToken())) {
				if (!state.nextToken().getId().equals(";") && !state.nextToken().isReach()) {
					Token first = expression(context, 0);
					_this.setFirstTokens(first);

					if (first != null && first.getType() == Token.Type.PUNCTUATOR && first.getValue().equals("=") &&
							!first.isParen() && !state.getOption().test("boss")) {
						warning("W093", first);
					}

					if (state.getOption().test("noreturnawait") && (context & ProdParams.ASYNC) != 0 &&
							(context & ProdParams.TRY_CLAUSE) == 0 &&
							first.isIdentifier() && first.getValue().equals("await")) {
						warning("W146", first);
					}
				}
			} else {
				if (state.nextToken().getType() == Token.Type.PUNCTUATOR &&
						(state.nextToken().getValue().equals("[") || state.nextToken().getValue().equals("{")
								|| state.nextToken().getValue().equals("+")
								|| state.nextToken().getValue().equals("-"))) {
					nolinebreak(_this); // always warn (Line breaking error)
				}
			}

			reachable(_this);

			return _this;
		}).setExps(true);

		prefix("await", _this -> context -> rbp -> {
			if ((context & ProdParams.ASYNC) != 0) {
				// If the parameters of the current function scope have not been defined,
				// it is because the current expression is contained within the parameter
				// list.
				if (state.getFunct().getParams() == null) {
					error("E024", _this, "await");
				}

				expression(context, 10);
				return _this;
			} else {
				_this.setExps(false);
				return state.getSyntax().get("(identifier)").nud(_this, context, rbp);
			}
		}).setExps(true);

		{
			Token asyncSymbol = prefix("async", _this -> c -> rbp -> {
				int context = c;
				if (_this.isFunc(context)) {
					if (!state.inES8()) {
						warning("W119", _this, "async functions", "8");
					}

					context |= ProdParams.PRE_ASYNC;
					expression(context, rbp); // JSHINT_BUG: assignment to the func property doesn't make sense because
												// it's not used anywhere
					_this.setIdentifier(false);
					return _this;
				}

				_this.setExps(false);
				return state.getSyntax().get("(identifier)").nud(_this, context, rbp);
			});

			asyncSymbol.setMeta(new Token.Meta(true, true, true, false, null));
			asyncSymbol.setIsFunc(_this -> context -> {
				Token next = state.nextToken();

				if (_this.getLine() != next.getLine()) {
					return false;
				}

				if (next.getId().equals("function")) {
					return true;
				}

				if (next.getId().equals("(")) {
					Token afterParens = peekThroughParens(0);

					return afterParens.getId().equals("=>");
				}

				if (next.isIdentifier()) {
					return peek().getId().equals("=>");
				}

				return false;
			});
			asyncSymbol.setUseFud(asyncSymbol.getIsFunc());
			// async function declaration
			asyncSymbol.setFud(_this -> context -> {
				if (!state.inES8()) {
					warning("W119", _this, "async functions", "8");
				}
				context |= ProdParams.PRE_ASYNC;
				context |= ProdParams.INITIAL;
				Token func = expression(context, 0); // JSHINT_BUG: assignment to the func property doesn't make sense
														// because it's not used anywhere
				_this.setBlock(func.isBlock());
				_this.setExps(func.isExps());
				return _this;
			});
			asyncSymbol.setExps(true);
			asyncSymbol.setReserved(false);
		}

		{
			Token yieldSymbol = prefix("yield", _this -> context -> rbp -> {
				if (state.inMoz()) {
					return mozYield(_this, context);
				}

				if ((context & ProdParams.YIELD) == 0) {
					_this.setExps(false);
					return state.getSyntax().get("(identifier)").nud(_this, context, rbp);
				}

				Token prev = state.prevToken();

				// If the parameters of the current function scope have not been defined,
				// it is because the current expression is contained within the parameter
				// list.
				if (state.getFunct().getParams() == null) {
					error("E024", _this, "yield");
				}

				if (!_this.isBeginsStmt() && prev.getLbp() > 30 && !checkPunctuators(prev, "(")) {
					error("E061", _this);
				}

				if (!state.inES6()) {
					warning("W104", state.currToken(), "yield", "6");
				}
				state.getFunct().setYielded(true);

				if (state.nextToken().getValue().equals("*")) {
					advance("*");
				}

				// Parse operand
				if (state.currToken().getValue().equals("*") || sameLine(state.currToken(), state.nextToken())) {
					if (state.nextToken().getNud() != null) {
						nobreaknonadjacent(state.currToken(), state.nextToken());
						_this.setFirstTokens(expression(context, 10));

						if (_this.getFirstToken().getType() == Token.Type.PUNCTUATOR
								&& _this.getFirstToken().getValue().equals("=") &&
								!_this.getFirstToken().isParen() && !state.getOption().test("boss")) {
							warning("W093", _this.getFirstToken());
						}
					} else if (state.nextToken().getLed() != null) {
						if (!state.nextToken().getId().equals(",")) {
							error("W017", state.nextToken());
						}
					}
				}

				return _this;
			});
			yieldSymbol.setRbp(25);
			yieldSymbol.setLbp(25);
			yieldSymbol.setExps(true);
		}

		stmt("throw", _this -> context -> {
			nolinebreak(_this);
			_this.setFirstTokens(expression(context, 20));

			reachable(_this);

			return _this;
		}).setExps(true);

		prefix("import", _this -> context -> rbp -> {
			Token mp = metaProperty(context, "meta", () -> {
				// JSHINT_BUG: inES11 doesn't have arguments, need to be deleted
				if (!state.inES11()) {
					warning("W119", state.prevToken(), "import.meta", "11");
				}
				if (!state.getOption().test("module")) {
					error("E070", state.prevToken());
				}
			});

			if (mp != null) {
				return mp;
			}

			if (!checkPunctuator(state.nextToken(), "(")) {
				// JSHINT_BUG: rbp is not set for nd function
				return state.getSyntax().get("(identifier)").nud(_this, context, 0);
			}

			if (!state.inES11()) {
				warning("W119", state.currToken(), "dynamic import", "11");
			}

			advance("(");
			expression(context, 10);
			advance(")");
			return _this;
		});

		Token importSymbol = stmt("import", _this -> context -> {
			if (!state.getFunct().getScope().getBlock().isGlobal()) {
				error("E053", state.currToken(), "Import");
			}

			if (!state.inES6()) {
				warning("W119", state.currToken(), "import", "6");
			}

			if (state.nextToken().getType() == Token.Type.STRING) {
				// ModuleSpecifier :: StringLiteral
				advance("(string)");
				return _this;
			}

			if (state.nextToken().isIdentifier()) {
				// ImportClause :: ImportedDefaultBinding
				_this.setName(identifier(context));
				// Import bindings are immutable (see ES6 8.1.1.5.5)
				state.getFunct().getScope().addbinding(_this.getName(), "import", state.currToken(), true);

				if (state.nextToken().getValue().equals(",")) {
					// ImportClause :: ImportedDefaultBinding , NameSpaceImport
					// ImportClause :: ImportedDefaultBinding , NamedImports
					advance(",");
					// At this point, we intentionally fall through to continue matching
					// either NameSpaceImport or NamedImports.
					// Discussion:
					// https://github.com/jshint/jshint/pull/2144#discussion_r23978406
				} else {
					advance("from");
					advance("(string)");
					return _this;
				}
			}

			if (state.nextToken().getId().equals("*")) {
				// ImportClause :: NameSpaceImport
				advance("*");
				advance("as");
				if (state.nextToken().isIdentifier()) {
					_this.setName(identifier(context));
					// Import bindings are immutable (see ES6 8.1.1.5.5)
					state.getFunct().getScope().addbinding(_this.getName(), "import", state.currToken(), true);
				}
			} else {
				// ImportClause :: NamedImports
				advance("{");
				for (;;) {
					if (state.nextToken().getValue().equals("}")) {
						advance("}");
						break;
					}
					String importName;
					if (peek().getValue().equals("as")) {
						identifier(context, true);
						advance("as");
						importName = identifier(context);
					} else {
						importName = identifier(context);
					}

					// Import bindings are immutable (see ES6 8.1.1.5.5)
					state.getFunct().getScope().addbinding(importName, "import", state.currToken(), true);

					if (state.nextToken().getValue().equals(",")) {
						advance(",");
					} else if (state.nextToken().getValue().equals("}")) {
						advance("}");
						break;
					} else {
						error("E024", state.nextToken(), state.nextToken().getValue());
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
			// warning("W142", this, "import", moduleSpecifier);
			// }

			return _this;
		});
		importSymbol.setExps(true);
		importSymbol.setReserved(true);
		importSymbol.setMeta(new Token.Meta(true, true, false, false, null));
		importSymbol.setUseFud(_this -> context -> {
			return !(checkPunctuators(state.nextToken(), ".", "("));
		});
		importSymbol.setRbp(161);

		stmt("export", _this -> context -> {
			boolean ok = true;
			Token moduleSpecifier = null;
			context = context | ProdParams.EXPORT;

			if (!state.inES6()) {
				warning("W119", state.currToken(), "export", "6");
				ok = false;
			}

			if (!state.getFunct().getScope().getBlock().isGlobal()) {
				error("E053", state.currToken(), "Export");
				ok = false;
			}

			if (state.nextToken().getValue().equals("*")) {
				// ExportDeclaration :: export * FromClause
				// ExportDeclaration :: export * as IdentifierName FromClause
				advance("*");

				if (state.nextToken().getValue().equals("as")) {
					if (!state.inES11()) {
						warning("W119", state.currToken(), "export * as ns from", "11");
					}
					advance("as");
					identifier(context, true);
					state.getFunct().getScope().setExported(null, state.currToken());
				}

				advance("from");
				advance("(string)");
				return _this;
			}

			if (state.nextToken().getType() == Token.Type.DEFAULT) {
				// ExportDeclaration ::
				// export default [lookahead ∉ { function, class }] AssignmentExpression[In] ;
				// export default HoistableDeclaration
				// export default ClassDeclaration

				// because the 'name' of a default-exported function is, confusingly, 'default'
				// see https://bocoup.com/blog/whats-in-a-function-name
				state.getNameStack().set(state.nextToken());

				advance("default");
				Token def = state.currToken();
				String exportType = state.nextToken().getId();
				if (exportType.equals("function")) {
					_this.setBlock(true);
					advance("function");
					Token token = state.getSyntax().get("function").fud(context);
					state.getFunct().getScope().setExported(token.getTokenName(), def);
				} else if (exportType.equals("async") && peek().getId().equals("function")) {
					_this.setBlock(true);
					advance("async");
					advance("function");
					Token token = state.getSyntax().get("function").fud(context | ProdParams.PRE_ASYNC);
					state.getFunct().getScope().setExported(token.getTokenName(), def);
				} else if (exportType.equals("class")) {
					_this.setBlock(true);
					advance("class");
					Token token = state.getSyntax().get("class").fud(context);
					state.getFunct().getScope().setExported(token.getTokenName(), def);
				} else {
					expression(context, 10);
					state.getFunct().getScope().setExported(null, def);
				}
				return _this;
			}
			if (state.nextToken().getValue().equals("{")) {
				// ExportDeclaration :: export ExportClause
				advance("{");
				List<ExportedToken> exportedTokens = new ArrayList<>();
				while (!checkPunctuator(state.nextToken(), "}")) {
					if (!state.nextToken().isIdentifier()) {
						error("E030", state.nextToken(), state.nextToken().getValue());
					}
					advance();

					if (state.nextToken().getValue().equals("as")) {
						advance("as");
						if (!state.nextToken().isIdentifier()) {
							error("E030", state.nextToken(), state.nextToken().getValue());
						}
						exportedTokens.add(new ExportedToken(state.prevToken(), state.nextToken()));
						advance();
					} else {
						exportedTokens.add(new ExportedToken(state.currToken(), state.currToken()));
					}

					if (!checkPunctuator(state.nextToken(), "}")) {
						advance(",");
					}
				}
				advance("}");
				if (state.nextToken().getValue().equals("from")) {
					// ExportDeclaration :: export ExportClause FromClause
					advance("from");
					moduleSpecifier = state.nextToken();
					advance("(string)");
				} else if (ok) {
					for (ExportedToken x : exportedTokens) {
						state.getFunct().getScope().setExported(x.local, x.export);
					}
				}

				if (exportedTokens.size() == 0) {
					if (moduleSpecifier != null) {
						warning("W142", _this, "export", moduleSpecifier.getValue());
					} else {
						warning("W141", _this, "export");
					}
				}

				return _this;
			} else if (state.nextToken().getId().equals("var")) {
				// ExportDeclaration :: export VariableStatement
				advance("var");
				Token token = state.currToken().fud(context);
				token.getFirstTokens().forEach(binding -> {
					state.getFunct().getScope().setExported(binding, binding);
				});
			} else if (state.nextToken().getId().equals("let")) {
				// ExportDeclaration :: export VariableStatement
				advance("let");
				Token token = state.currToken().fud(context);
				token.getFirstTokens().forEach(binding -> {
					state.getFunct().getScope().setExported(binding, binding);
				});
			} else if (state.nextToken().getId().equals("const")) {
				// ExportDeclaration :: export VariableStatement
				advance("const");
				Token token = state.currToken().fud(context);
				token.getFirstTokens().forEach(binding -> {
					state.getFunct().getScope().setExported(binding, binding);
				});
			} else if (state.nextToken().getId().equals("function")) {
				// ExportDeclaration :: export Declaration
				_this.setBlock(true);
				advance("function");
				Token token = state.getSyntax().get("function").fud(context);
				state.getFunct().getScope().setExported(token.getTokenName(), token.getTokenName());
			} else if (state.nextToken().getId().equals("async") && peek().getId().equals("function")) {
				// ExportDeclaration :: export Declaration
				_this.setBlock(true);
				advance("async");
				advance("function");
				Token token = state.getSyntax().get("function").fud(context | ProdParams.PRE_ASYNC);
				state.getFunct().getScope().setExported(token.getTokenName(), token.getTokenName());
			} else if (state.nextToken().getId().equals("class")) {
				// ExportDeclaration :: export Declaration
				_this.setBlock(true);
				advance("class");
				Token token = state.getSyntax().get("class").fud(context);
				state.getFunct().getScope().setExported(token.getTokenName(), token.getTokenName());
			} else {
				error("E024", state.nextToken(), state.nextToken().getValue());
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
		futureReservedWord(Token.Type.EXPORT, new Token.Meta(true, false, false, false, null));
		futureReservedWord(Token.Type.EXTENDS, new Token.Meta(true, false, false, false, null));
		futureReservedWord(Token.Type.FINAL);
		futureReservedWord(Token.Type.FLOAT);
		futureReservedWord(Token.Type.GOTO);
		futureReservedWord(Token.Type.IMPLEMENTS, new Token.Meta(true, false, true, false, null));
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
	 * @param type  - one of "property" or "call"
	 * @param funct - a "functor" object describing the current function
	 *              context
	 * 
	 * @return
	 */
	private boolean supportsSuper(String type, Functor funct) {
		if (type.equals("call") && funct.isAsync()) {
			return false;
		}

		if (type.equals("property") && funct.isMethod()) {
			return true;
		}

		if (type.equals("call") && funct.getStatement() != null &&
				funct.getStatement().getId().equals("class")) {
			return true;
		}

		if (funct.isArrow()) {
			return supportsSuper(type, funct.getContext());
		}

		return false;
	}

	private Token superNud(Token _this) {
		Token next = state.nextToken();

		if (checkPunctuators(next, "[", ".")) {
			if (!supportsSuper("property", state.getFunct())) {
				error("E063", _this);
			}
		} else if (checkPunctuator(next, "(")) {
			if (!supportsSuper("call", state.getFunct())) {
				error("E064", _this);
			}
		} else {
			error("E024", next, StringUtils.defaultIfEmpty(next.getValue(), next.getId()));
		}

		return _this;
	};

	// this function is used to determine whether a squarebracket or a curlybracket
	// expression is a comprehension array, destructuring assignment or a json
	// value.
	private class LookupBlockType {

		private boolean isCompArray = false;
		private boolean notJson = false;
		private boolean isDestAssign = false;

		private LookupBlockType() {
			Token pn = null, pn1 = null, prev = null;
			int i = -1;
			int bracketStack = 0;

			if (checkPunctuators(state.currToken(), "[", "{")) {
				bracketStack += 1;
			}
			do {
				prev = i == -1 ? state.currToken() : pn;
				pn = i == -1 ? state.nextToken() : peek(i);
				pn1 = peek(i + 1);
				i = i + 1;
				if (checkPunctuators(pn, "[", "{")) {
					bracketStack += 1;
				} else if (checkPunctuators(pn, "]", "}")) {
					bracketStack -= 1;
				}
				if (bracketStack == 1 && pn.isIdentifier() && pn.getValue().equals("for") &&
						!checkPunctuator(prev, ".")) {
					isCompArray = true;
					notJson = true;
					break;
				}
				if (bracketStack == 0 && checkPunctuators(pn, "}", "]")) {
					if (pn1.getValue().equals("=")) {
						isDestAssign = true;
						notJson = true;
						break;
					} else if (pn1.getValue().equals(".")) {
						notJson = true;
						break;
					}
				}
				if (checkPunctuator(pn, ";")) {
					notJson = true;
				}
			} while (bracketStack > 0 && !pn.getId().equals("(end)"));
		}
	}

	/**
	 * Update an object used to track property names within object initializers
	 * and class bodies. Produce warnings in response to duplicated names.
	 *
	 * @param props      - a collection of all properties of the object or
	 *                   class to which the current property is being
	 *                   assigned
	 * @param name       - the property name
	 * @param tkn        - the token defining the property
	 * @param isClass    - whether the accessor is part of an ES6 Class
	 *                   definition
	 * @param isStatic   - whether the accessor is a static method
	 * @param isComputed - whether the property is a computed expression like
	 *                   [Symbol.iterator]
	 */
	private void saveProperty(UniversalContainer props, String name, Token tkn, boolean isClass, boolean isStatic,
			boolean isComputed) {
		if (tkn.isIdentifier()) {
			name = tkn.getValue();
		}
		String key = name;
		if (isClass && isStatic) {
			key = "static " + name;
		}

		if (props.test(key) && !name.equals("__proto__") && !isComputed) {
			String[] msg = { "key", "class method", "static class method" };
			warning("W075", state.nextToken(), msg[(isClass ? 1 : 0) + (isStatic ? 1 : 0)], name);
		} else {
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
	 * @param props        - a collection of all properties of the object or
	 *                     class to which the current accessor is being
	 *                     assigned
	 * @param tkn          - the identifier token representing the accessor name
	 * @param isClass      - whether the accessor is part of an ES6 Class
	 *                     definition
	 * @param isStatic     - whether the accessor is a static method
	 */
	private void saveAccessor(String accessorType, UniversalContainer props, String name, Token tkn, boolean isClass,
			boolean isStatic) {
		String flagName = accessorType.equals("get") ? "getterToken" : "setterToken";
		String key = name;
		state.currToken().setAccessorType(accessorType);
		state.getNameStack().set(tkn);
		if (isClass && isStatic) {
			key = "static " + name;
		}

		if (props.test(key)) {
			if ((props.get(key).test("basic") || props.get(key).test(flagName)) && !name.equals("__proto__")) {
				String msg = "";
				if (isClass) {
					if (isStatic) {
						msg += "static ";
					}
					msg += accessorType + "ter method";
				} else {
					msg = "key";
				}
				warning("W075", state.nextToken(), msg, name);
			}
		} else {
			props.set(key, ContainerFactory.nullContainer().create());
		}

		props.get(key).set(flagName, tkn);
		if (isStatic) {
			props.get(key).set("static", true);
		}
	}

	/**
	 * Parse a computed property name within object initializers and class bodies
	 * as introduced by ES2015. For example:
	 *
	 * void {
	 * [object.method()]: null
	 * };
	 *
	 * @param context - the parsing context
	 *
	 * @return - the token value that describes the expression which
	 *         defines the property name
	 */
	private Token computedPropertyName(int context) {
		advance("[");

		// Explicitly reclassify token as a delimeter to prevent its later
		// interpretation as an "infix" operator.
		state.currToken().setDelim(true);
		state.currToken().setLbp(0);

		if (!state.inES6()) {
			warning("W119", state.currToken(), "computed property names", "6");
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
	private boolean checkPunctuators(Token token, String... values) {
		if (token.getType() == Token.Type.PUNCTUATOR) {
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
	private boolean checkPunctuator(Token token, String value) {
		return token.getType() == Token.Type.PUNCTUATOR && token.getValue().equals(value);
	}

	// Check whether this function has been reached for a destructuring assign with
	// undeclared values
	private void destructuringAssignOrJsonValue(int context) {
		// lookup for the assignment (ECMAScript 6 only)
		// if it has semicolons, it is a block, so go parse it as a block
		// or it's not a block, but there are assignments, check for undeclared
		// variables
		LookupBlockType block = new LookupBlockType();
		if (block.notJson) {
			if (!state.inES6() && block.isDestAssign) {
				warning("W104", state.currToken(), "destructuring assignment", "6");
			}
			statements(context);
		}
		// otherwise parse json value
		else {
			state.getOption().set("laxbreak", true);
			state.setJsonMode(true);
			jsonValue();
		}
	}

	/**
	 * Parse and define the three states of a list comprehension in order to
	 * avoid defining global variables, but keeping them to the list
	 * comprehension scope only. The order of the states are as follows:
	 *
	 * - "use" - which will be the returned iterative part of the list
	 * comprehension
	 * - "define" - which will define the variables local to the list
	 * comprehension
	 * - "filter" - which will help filter out values
	 */
	private class ArrayComprehension {

		private class Variable {

			private Token token;
			private String value;
			private boolean undef;
			private boolean unused;

			private Variable(Token token, String value, boolean undef, boolean unused) {
				this.token = token;
				this.value = value;
				this.undef = undef;
				this.unused = unused;
			}
		}

		private class CompArray {

			private String mode = "use";
			private List<Variable> variables = new ArrayList<>();
		}

		private List<CompArray> carrays = new ArrayList<>();
		private CompArray current = null;

		private boolean declare(String v) {
			int l = 0;
			for (Variable elt : current.variables) {
				// if it has, change its undef state
				if (elt.value.equals(v)) {
					elt.undef = false;
					l++;
				}
			}
			return l != 0;
		}

		private boolean use(String v) {
			int l = 0;
			for (Variable elt : current.variables) {
				// and if it has been defined
				if (elt.value.equals(v) && !elt.undef) {
					if (elt.unused == true) {
						elt.unused = false;
					}
					l++;
				}
			}
			// otherwise we warn about it
			return (l == 0);
		}

		private void stack() {
			current = new CompArray();
			carrays.add(current);
		}

		private void unstack() {
			for (Variable v : current.variables) {
				if (v.unused)
					warning("W098", v.token, StringUtils.defaultIfEmpty(v.token.getRawText(), v.value));
				if (v.undef)
					state.getFunct().getScope().getBlock().use(v.value, v.token);
			}
			carrays.remove(carrays.size() - 1);
			current = carrays.size() > 0 ? carrays.get(carrays.size() - 1) : null;
		}

		private void setState(String s) {
			if (s.equals("use") || s.equals("define") || s.equals("generate") || s.equals("filter"))
				current.mode = s;
		}

		private boolean check(String v) {
			if (current == null) {
				return false;
			}
			// When we are in "use" state of the list comp, we enqueue that var
			if (current != null && current.mode.equals("use")) {
				if (use(v)) {
					current.variables.add(new Variable(state.currToken(), v, true, false));
				}
				return true;
			}
			// When we are in "define" state of the list comp,
			else if (current != null && current.mode.equals("define")) {
				// check if the variable has been used previously
				if (!declare(v)) {
					current.variables.add(new Variable(state.currToken(), v, false, true));
				}
				return true;
			}
			// When we are in the "generate" state of the list comp,
			else if (current != null && current.mode.equals("generate")) {
				state.getFunct().getScope().getBlock().use(v, state.currToken());
				return true;
			}
			// When we are in "filter" state,
			else if (current != null && current.mode.equals("filter")) {
				// we check whether current variable has been declared
				if (use(v)) {
					// if not we warn about it
					state.getFunct().getScope().getBlock().use(v, state.currToken());
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
	private void jsonValue() {
		Runnable jsonObject = () -> {
			UniversalContainer o = ContainerFactory.createObject();
			Token t = state.nextToken();
			advance("{");
			if (!state.nextToken().getId().equals("}")) {
				for (;;) {
					if (state.nextToken().getId().equals("(end)")) {
						error("E026", state.nextToken(), String.valueOf(t.getLine()));
					} else if (state.nextToken().getId().equals("}")) {
						warning("W094", state.currToken());
						break;
					} else if (state.nextToken().getId().equals(",")) {
						error("E028", state.nextToken());
					} else if (!state.nextToken().getId().equals("(string)")) {
						warning("W095", state.nextToken(), state.nextToken().getValue());
					}
					if (o.get(state.nextToken().getValue()).equals(true)) {
						warning("W075", state.nextToken(), "key", state.nextToken().getValue());
					} else if ((state.nextToken().getValue().equals("__proto__") &&
							!state.getOption().get("proto").test())
							|| (state.nextToken().getValue().equals("__iterator__") &&
									!state.getOption().get("iterator").test())) {
						warning("W096", state.nextToken(), state.nextToken().getValue());
					} else {
						o.set(state.nextToken().getValue(), true);
					}
					advance();
					advance(":");
					jsonValue();
					if (!state.nextToken().getId().equals(",")) {
						break;
					}
					advance(",");
				}
			}
			advance("}");
		};

		Runnable jsonArray = () -> {
			Token t = state.nextToken();
			advance("[");
			if (!state.nextToken().getId().equals("]")) {
				for (;;) {
					if (state.nextToken().getId().equals("(end)")) {
						error("E027", state.nextToken(), String.valueOf(t.getLine()));
					} else if (state.nextToken().getId().equals("]")) {
						warning("W094", state.currToken());
						break;
					} else if (state.nextToken().getId().equals(",")) {
						error("E028", state.nextToken());
					}
					jsonValue();
					if (!state.nextToken().getId().equals(",")) {
						break;
					}
					advance(",");
				}
			}
			advance("]");
		};

		switch (state.nextToken().getId()) {
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
				error("E003", state.nextToken());
		}
	}

	/**
	 * Lint dynamically-evaluated code, appending any resulting errors/warnings
	 * into the global `errors` array.
	 * 
	 * @param internals collection of "internals" objects describing string tokens
	 *                  that contain evaluated code
	 * @param options   linting options to apply
	 * @param globals   globally-defined bindings for the evaluated code
	 */
	private void lintEvalCode(List<InternalSource> internals, LinterOptions options, LinterGlobals globals) {
		for (int idx = 0; idx < internals.size(); idx += 1) {
			InternalSource internal = internals.get(idx);
			options.set("scope", internal.getElem().toString()); // JSHINT_BUG: why token is writen here??
			int priorErrorCount = errors.size();

			lint(internal.getCode(), options, globals);

			for (int jdx = priorErrorCount; jdx < errors.size(); jdx += 1) {
				errors.get(jdx).setLine(errors.get(jdx).getLine() + internal.getToken().getLine() - 1);
			}
		}
	}

	private String escapeRegex(String str) {
		return Reg.escapeRegexpChars(str); // PORT INFO: replace regexp was moved to Reg class
	}

	private List<LinterWarning> errors = new ArrayList<>();
	private List<InternalSource> internals = new ArrayList<>(); // "internal" scripts, like eval
																// containing a static string
	private Set<String> blacklist = new HashSet<>();
	private String scriptScope = "";

	public boolean lint(String s) throws JSHintException {
		return lint(s, null, null);
	}

	public boolean lint(String s, LinterOptions options) throws JSHintException {
		return lint(s, options, null);
	}

	public boolean lint(String s, LinterOptions options, LinterGlobals globals) throws JSHintException {
		LinterOptions o = new LinterOptions(options);
		LinterGlobals g = (globals == null ? new LinterGlobals() : globals);

		init(o, g);

		if (s == null) {
			errorAt("E004", 0);
			return false;
		}

		for (Delimiter delimiterPair : o.getIgnoreDelimiters()) {
			if (StringUtils.isEmpty(delimiterPair.getStart()) || StringUtils.isEmpty(delimiterPair.getEnd()))
				continue;

			String reIgnoreStr = escapeRegex(delimiterPair.getStart()) +
					"[\\s\\S]*?" +
					escapeRegex(delimiterPair.getEnd());

			Pattern reIgnore = Pattern.compile(reIgnoreStr, Pattern.CASE_INSENSITIVE);

			// PORT INFO: replace regexp was moved to Reg class
			s = Reg.blankDelimiteredText(s, reIgnore);
		}
		;

		return run(new Lexer(state, s), o, g);
	}

	public boolean lint(String[] s) throws JSHintException {
		return lint(s, null, null);
	}

	public boolean lint(String[] s, LinterOptions options) throws JSHintException {
		return lint(s, options, null);
	}

	public boolean lint(String[] s, LinterOptions options, LinterGlobals globals) throws JSHintException {
		LinterOptions o = new LinterOptions(options);
		LinterGlobals g = (globals == null ? new LinterGlobals() : globals);

		init(o, g);

		if (s == null) {
			errorAt("E004", 0);
			return false;
		}

		// JSHINT_BUG: where is ignore delimiters for source array??

		return run(new Lexer(state, s), o, g);
	}

	private void init(LinterOptions o, LinterGlobals g) {
		// TODO: move this methods to constructor class when State class will be
		// non-static
		buildSyntaxTable();
		ecmaScriptParser();
		buildStatementTable();
		addModule(new Style());

		state.reset();

		if (o.hasOption("scope")) {
			scriptScope = o.getAsString("scope");
		} else {
			errors = new ArrayList<>();
			internals = new ArrayList<>();
			blacklist = new HashSet<>();
			scriptScope = "(main)";
		}

		predefined = new HashMap<>();
		combine(predefined, Vars.ecmaIdentifiers.get(3));
		combine(predefined, Vars.reservedVars);

		declared = new HashMap<>();
		Map<String, Boolean> exported = new HashMap<>(); // Variables that live outside the current file

		o.readPredefineds(predefined, blacklist);
		o.readGlobals(predefined, blacklist);
		o.readExporteds(exported);

		state.setOption(o.getOptions(state, false));
		state.setIgnored(o.getOptions(state, true));

		if (!state.getOption().test("indent"))
			state.getOption().set("indent", 4);
		if (!state.getOption().test("maxerr"))
			state.getOption().set("maxerr", 50);

		indent = 1;

		ScopeManager scopeManagerInst = new ScopeManager(state, this.predefined, exported, declared);
		scopeManagerInst.on("warning", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				warning(ev.getCode(), ev.getToken(), ev.getData());
			}
		});

		scopeManagerInst.on("error", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				warning(ev.getCode(), ev.getToken(), ev.getData());
			}
		});

		state.setFunct(
				new Functor("(global)", null, new Functor()
						.setGlobal(true)
						.setScope(scopeManagerInst)
						.setComparray(new ArrayComprehension())
						.setMetrics(new Metrics(state.nextToken()))));

		functions = new ArrayList<>();
		functions.add(state.getFunct());
		member = new HashMap<>();
		membersOnly = null;
		inblock = false;
		lookahead = new ArrayList<>();

		emitter.removeAllListeners();
		for (JSHintModule func : extraModules) {
			func.execute(this);
		}

		state.setNextToken(state.getSyntax().get("(begin)"));
		state.setCurrToken(state.nextToken());
		state.setPrevToken(state.nextToken());
	}

	private boolean run(Lexer l, LinterOptions o, LinterGlobals g) {
		lex = l;

		lex.on("warning", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				warningAt(ev.getCode(), ev.getLine(), ev.getCharacter(), ev.getData());
			}
		});

		lex.on("error", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				errorAt(ev.getCode(), ev.getLine(), ev.getCharacter(), ev.getData());
			}
		});

		lex.on("fatal", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				quit("E041", new Token(ev));
			}
		});

		lex.on("Identifier", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				emitter.emit("Identifier", ev);
			}
		});

		lex.on("String", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				emitter.emit("String", ev);
			}
		});

		lex.on("Number", new LexerEventListener() {

			@Override
			public void accept(EventContext ev) throws JSHintException {
				emitter.emit("Number", ev);
			}
		});

		// check options
		for (String name : o) {
			checkOption(name, true, state.currToken());
		}
		for (String name : o.getUnstables()) {
			checkOption(name, false, state.currToken());
		}

		try {
			applyOptions();

			// combine the passed globals after we've assumed all our options
			combine(predefined, g);

			// reset values
			checkCommaFirst = true;

			advance();
			switch (state.nextToken().getId()) {
				case "{":
				case "[":
					destructuringAssignOrJsonValue(0);
					break;
				default:
					directives();

					if (state.getDirective().get("use strict") != null) {
						if (!state.allowsGlobalUsd()) {
							warning("W097", state.getDirective().get("use strict"));
						}
					}

					statements(0);
			}

			if (!state.nextToken().getId().equals("(end)")) {
				quit("E041", state.currToken());
			}

			state.getFunct().getScope().unstack();
		} catch (JSHintException err) {
			Token nt = (state.nextToken() != null ? state.nextToken() : new Token());
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
		if (scriptScope.equals("(main)")) {
			lintEvalCode(internals, o, g);
		}

		return errors.size() == 0;
	}

	// API

	public boolean isJson() {
		return state.isJsonMode();
	}

	protected UniversalContainer getOption(String name) {
		return ContainerFactory.nullContainerIfFalse(state.getOption().get(name));
	}

	public List<LinterWarning> getErrors() {
		return Collections.unmodifiableList(errors);
	}

	public List<InternalSource> getInternals() {
		return Collections.unmodifiableList(internals);
	}

	public Set<String> getBlacklist() {
		return Collections.unmodifiableSet(blacklist);
	}

	public String getScriptScope() {
		return scriptScope;
	}

	public String getCache(String name) {
		return state.getCache().get(name);
	}

	public void setCache(String name, String value) {
		state.getCache().put(StringUtils.defaultString(name), StringUtils.defaultString(value));
	}

	public void warn(String code, int line, int chr, String... data) throws JSHintException {
		warningAt(code, line, chr, data);
	}

	public void on(String names, LexerEventListener listener) {
		for (String name : names.split(" ", -1)) {
			emitter.on(name, listener);
		}
	}

	// Modules.
	public void addModule(JSHintModule func) {
		extraModules.add(func);
	}

	// Data summary.
	public DataSummary generateSummary() {
		DataSummary data = new DataSummary(state.getOption());

		if (errors.size() > 0) {
			data.setErrors(errors);
		}

		if (state.isJsonMode()) {
			data.setJson(true);
		}

		List<ImpliedGlobal> impliedGlobals = state.getFunct().getScope().getImpliedGlobals();
		if (impliedGlobals.size() > 0) {
			data.setImplieds(impliedGlobals);
		}

		Set<String> globals = state.getFunct().getScope().getUsedOrDefinedGlobals();
		if (globals.size() > 0) {
			data.setGlobals(globals);
		}

		for (int i = 1; i < functions.size(); i++) {
			Functor f = functions.get(i);
			DataSummary.Function fu = new DataSummary.Function();

			fu.setName(f.getName());
			fu.setParam(f.getParams());
			fu.setLine(f.getLine());
			fu.setCharacter(f.getCharacter());
			fu.setLast(f.getLast());
			fu.setLastCharacter(f.getLastCharacter());

			fu.setMetrics(
					new DataSummary.Metrics(
							f.getMetrics().complexityCount,
							f.getMetrics().arity,
							f.getMetrics().statementCount));

			data.addFunction(fu);
		}

		List<Token> unuseds = state.getFunct().getScope().getUnuseds();
		if (unuseds.size() > 0) {
			data.setUnused(unuseds);
		}

		if (member.size() > 0) {
			data.setMember(member);
		}

		return data;
	}

	private static class ExportedToken {
		Token local;
		Token export;

		private ExportedToken(Token local, Token export) {
			this.local = local;
			this.export = export;
		}
	}

	private static enum FunctionType {
		GENERATOR,
		ARROW
	}

	private static enum FunctorTag {
		NAME, // "(name)"
		BREAKAGE, // "(breakage)"
		LOOPAGE, // "(loopage)"
		ISSTRICT, // "(isStrict)"
		GLOBAL, // "(global)"
		LINE, // "(line)"
		CHARACTER, // "(character)"
		METRICS, // "(metrics)"
		STATEMENT, // "(statement)"
		CONTEXT, // "(context)"
		SCOPE, // "(scope)"
		COMPARRAY, // "(comparray)"
		YIELDED, // "(yielded)"
		ARROW, // "(arrow)"
		ASYNC, // "(async)"
		METHOD, // "(method)"
		HASSIMPLEPARAMS, // "(hasSimpleParams)"
		PARAMS, // "(params)"
		OUTERMUTABLES, // "(outerMutables)"
		LAST, // "(last)"
		LASTCHARACTER, // "(lastcharacter)"
		UNUSEDOPTION, // "(unusedOption)"
		VERB // "(verb)"
	}
}