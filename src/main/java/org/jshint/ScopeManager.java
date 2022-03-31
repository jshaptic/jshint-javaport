package org.jshint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.utils.EventContext;
import org.jshint.utils.EventEmitter;

/**
 * A scope manager tracks bindings, detecting when variables are referenced
 * (through "usages").
 */
public class ScopeManager {

	// Used to denote membership in lookup tables (a primitive value such as `true`
	// would be silently rejected for the property name "__proto__" in some
	// environments)
	private static final UniversalContainer marker = ContainerFactory.createObject();

	private State state;

	private Map<String, Boolean> exported;
	private Map<String, Token> declared;

	private Scope current;
	private List<Scope> scopeStack;

	private Scope currentFunctBody;

	private UniversalContainer usedPredefinedAndGlobals;
	private Map<String, ImpliedGlobal> impliedGlobals;
	private List<Token> unuseds;
	private List<String> esModuleExports;
	private EventEmitter emitter;

	private Block block;
	private Functor funct;

	/**
	 * Scope manager constructor.
	 *
	 * @param state      the global state object (see `state.js`)
	 * @param predefined a set of binding names for built-in bindings
	 *                   provided by the environment
	 * @param exported   a hash for binding names that are intended to be
	 *                   referenced in contexts beyond the current program
	 *                   code
	 * @param declared   a hash for binding names that were defined as
	 *                   global bindings via linting configuration
	 */
	public ScopeManager(State state, Map<String, Boolean> predefined, Map<String, Boolean> exported,
			Map<String, Token> declared) {
		this.state = state;
		this.exported = exported;
		this.declared = declared;

		this.scopeStack = new ArrayList<>();

		this.newScope("global");
		this.current.predefined = predefined;

		this.currentFunctBody = this.current; // this is the block after the params = function

		this.usedPredefinedAndGlobals = ContainerFactory.nullContainer().create();
		this.impliedGlobals = new HashMap<>();
		this.unuseds = new ArrayList<>();
		this.esModuleExports = new ArrayList<>();
		this.emitter = new EventEmitter();

		this.funct = new Functor();
		this.block = new Block();
	}

	private void newScope(String type) {
		type = StringUtils.defaultString(type);

		Scope newScope = new Scope();
		newScope.bindings = new LinkedHashMap<>();
		newScope.usages = new HashMap<>();
		newScope.labels = new HashMap<>();
		newScope.parent = current;
		newScope.type = type;
		newScope.params = type.equals("functionparams") || type.equals("catchparams") ? new ArrayList<>() : null;
		current = newScope;
		scopeStack.add(current);
	}

	private void warning(String code, Token token, String... data) {
		EventContext context = new EventContext();
		context.setCode(code);
		context.setToken(token);
		context.setData(data);
		emitter.emit("warning", context);
	}

	private void error(String code, Token token, String... data) {
		EventContext context = new EventContext();
		context.setCode(code);
		context.setToken(token);
		context.setData(data);
		emitter.emit("warning", context);
	}

	private void setupUsages(String bindingName) {
		if (!current.usages.containsKey(bindingName)) {
			current.usages.put(bindingName, new Usage());
		}
	}

	private UniversalContainer getUnusedOption(UniversalContainer unused_opt) {
		if (unused_opt.isUndefined()) {
			unused_opt = state.getOption().get("unused");
		}

		if (unused_opt.equals(true)) {
			unused_opt = new UniversalContainer("last-param");
		}

		return unused_opt;
	}

	private void warnUnused(String name, Token tkn, String type) {
		warnUnused(name, tkn, type, ContainerFactory.undefinedContainer());
	}

	private void warnUnused(String name, Token tkn, String type, UniversalContainer unused_opt) {
		int line = tkn.getLine();
		int chr = tkn.getFrom();
		String raw_name = StringUtils.defaultIfEmpty(tkn.getRawText(), name);

		unused_opt = getUnusedOption(unused_opt);

		Map<String, List<String>> warnable_types = new HashMap<String, List<String>>();
		warnable_types.put("vars", Arrays.asList("var"));
		warnable_types.put("last-param", Arrays.asList("var", "param"));
		warnable_types.put("strict", Arrays.asList("var", "param", "last-param"));

		if (unused_opt.test()) {
			if (warnable_types.containsKey(unused_opt.asString())
					&& warnable_types.get(unused_opt.asString()).indexOf(type) != -1) {
				Token t = new Token();
				t.setLine(line);
				t.setFrom(chr);
				warning("W098", t, raw_name);
			}
		}

		// inconsistent - see gh-1894
		if (unused_opt.test() || type.equals("var")) {
			Token t = new Token();
			t.setName(name);
			t.setLine(line);
			t.setCharacter(chr);
			unuseds.add(t);
		}
	}

	/**
	 * Check the current scope for unused identifiers
	 */
	private void checkForUnused() {
		if (!current.type.equals("functionparams")) {
			Map<String, Binding> currentBindings = current.bindings;
			for (String bindingName : currentBindings.keySet()) {
				if (!currentBindings.get(bindingName).type.equals("exception") &&
						currentBindings.get(bindingName).unused) {
					warnUnused(bindingName, currentBindings.get(bindingName).token, "var");
				}
			}
			return;
		}

		// Check the current scope for unused parameters and issue warnings as
		// necessary.
		List<String> params = current.params;

		String param = params.size() > 0 ? params.remove(params.size() - 1) : null;
		UniversalContainer unused_opt = ContainerFactory.undefinedContainer();

		while (param != null) {
			Binding binding = current.bindings.get(param);

			unused_opt = getUnusedOption(state.getFunct().getUnusedOption());

			// 'undefined' is a special case for the common pattern where `undefined`
			// is used as a formal parameter name to defend against global
			// re-assignment, e.g.
			//
			// (function(window, undefined) {
			// })();
			if (param.equals("undefined"))
				return;

			if (binding.unused) {
				warnUnused(param, binding.token, "param", state.getFunct().getUnusedOption());
			} else if (unused_opt.equals("last-param")) {
				return;
			}

			param = params.size() > 0 ? params.remove(params.size() - 1) : null;
		}
	}

	/**
	 * Find the relevant binding's scope. The owning scope is located by first
	 * inspecting the current scope and then moving "downward" through the stack
	 * of scopes.
	 *
	 * @param bindingName the value of the identifier
	 *
	 * @return the scope in which the binding was found
	 */
	private Map<String, Binding> getBinding(String bindingName) {
		for (int i = scopeStack.size() - 1; i >= 0; --i) {
			Map<String, Binding> scopeBindings = scopeStack.get(i).bindings;
			if (scopeBindings.containsKey(bindingName)) {
				return scopeBindings;
			}
		}

		return null;
	}

	/**
	 * Determine if a given binding name has been referenced within the current
	 * function or any function defined within.
	 *
	 * @param bindingName the value of the identifier
	 *
	 * @return
	 */
	private boolean usedSoFarInCurrentFunction(String bindingName) {
		for (int i = scopeStack.size() - 1; i >= 0; i--) {
			Scope current = scopeStack.get(i);
			if (current.usages.containsKey(bindingName)) {
				return true;
			}
			if (current == currentFunctBody) {
				break;
			}
		}
		return false;
	}

	private void checkOuterShadow(String bindingName, Token token) {
		// only check if shadow is outer
		if (!state.getOption().get("shadow").equals("outer")) {
			return;
		}

		boolean isGlobal = currentFunctBody.type.equals("global");
		boolean isNewFunction = current.type.equals("functionparams");

		boolean outsideCurrentFunction = !isGlobal;
		for (int i = 0; i < scopeStack.size(); i++) {
			Scope stackItem = scopeStack.get(i);

			if (!isNewFunction && scopeStack.size() > i + 1 && scopeStack.get(i + 1) == currentFunctBody) {
				outsideCurrentFunction = false;
			}
			if (outsideCurrentFunction && stackItem.bindings.containsKey(bindingName)) {
				warning("W123", token, bindingName);
			}
			if (stackItem.labels.containsKey(bindingName)) {
				warning("W123", token, bindingName);
			}
		}
	}

	private void latedefWarning(String type, String bindingName, Token token) {
		if (state.getOption().test("latedef")) {
			boolean isFunction = type.equals("function") || type.equals("generator function") ||
					type.equals("async function");

			// if either latedef is strict and this is a function
			// or this is not a function
			if ((state.getOption().get("latedef").equals(true) && isFunction) || !isFunction) {
				warning("W003", token, bindingName);
			}
		}
	}

	public void on(String names, LexerEventListener listener) {
		for (String name : names.split(" ", -1)) {
			emitter.on(name, listener);
		}
	}

	public boolean isPredefined(String bindingName) {
		return !has(bindingName) && scopeStack.get(0).predefined.containsKey(bindingName);
	}

	/**
	 * Create a new scope within the current scope. As the topmost value, the
	 * new scope will be interpreted as the current scope until it is
	 * exited--see the `unstack` method.
	 */
	public void stack() {
		stack(null);
	}

	/**
	 * Create a new scope within the current scope. As the topmost value, the
	 * new scope will be interpreted as the current scope until it is
	 * exited--see the `unstack` method.
	 *
	 * @param type - The type of the scope. Valid values are
	 *             "functionparams", "catchparams" and
	 *             "functionouter"
	 */
	public void stack(String type) {
		Scope previousScope = current;
		newScope(type);

		if (StringUtils.isEmpty(type) && previousScope.type.equals("functionparams")) {
			current.funcBody = true;
			currentFunctBody = current;
		}
	}

	/**
	 * Valldate all binding references and declarations in the current scope
	 * and set the next scope on the stack as the active scope.
	 */
	public void unstack() {
		Scope subScope = scopeStack.size() > 1 ? scopeStack.get(scopeStack.size() - 2) : null;
		boolean isUnstackingFunctionBody = current == currentFunctBody;
		boolean isUnstackingFunctionParams = current.type.equals("functionparams");
		boolean isUnstackingFunctionOuter = current.type.equals("functionouter");

		boolean isImmutable = false;
		Map<String, Usage> currentUsages = current.usages;
		Map<String, Binding> currentBindings = current.bindings;
		Set<String> usedBindingNameList = currentUsages.keySet();

		// PORT INFO: checking for __proto__ is not needed in Java implementation

		for (String usedBindingName : usedBindingNameList) {
			Usage usage = currentUsages.get(usedBindingName);
			Binding usedBinding = currentBindings.get(usedBindingName);
			if (usedBinding != null) {
				String usedBindingType = usedBinding.type;
				isImmutable = usedBindingType.equals("const") || usedBindingType.equals("import");

				if (usedBinding.useOutsideOfScope && !state.getOption().test("funcscope")) {
					List<Token> usedTokens = usage.tokens;
					for (int j = 0; j < usedTokens.size(); j++) {
						// Keep the consistency of https://github.com/jshint/jshint/issues/2409
						if (usedBinding.function == usedTokens.get(j).getFunction()) {
							error("W038", usedTokens.get(j), usedBindingName);
						}
					}
				}

				// mark the binding used
				current.bindings.get(usedBindingName).unused = false;

				// check for modifying a const
				if (isImmutable && usage.modified != null) {
					for (int j = 0; j < usage.modified.size(); j++) {
						error("E013", usage.modified.get(j), usedBindingName);
					}
				}

				boolean isFunction = usedBindingType.equals("function") ||
						usedBindingType.equals("generator function") ||
						usedBindingType.equals("async function");

				// check for re-assigning a function declaration
				if ((isFunction || usedBindingType.equals("class")) && usage.reassigned != null) {
					for (int j = 0; j < usage.reassigned.size(); j++) {
						if (!usage.reassigned.get(j).isIgnoreW021()) {
							warning("W021", usage.reassigned.get(j), usedBindingName, usedBindingType);
						}
					}
				}
				continue;
			}

			if (subScope != null) {
				String bindingType = bindingtype(usedBindingName);
				isImmutable = Objects.equals(bindingType, "const") ||
						(bindingType == null
								&& BooleanUtils.isFalse(scopeStack.get(0).predefined.get(usedBindingName)));
				if (isUnstackingFunctionOuter && !isImmutable) {
					if (state.getFunct().getOuterMutables() == null) {
						state.getFunct().setOuterMutables(new ArrayList<>());
					}
					state.getFunct().addOuterMutables(usedBindingName);
				}

				// not exiting the global scope, so copy the usage down in case its an out of
				// scope usage
				if (!subScope.usages.containsKey(usedBindingName)) {
					subScope.usages.put(usedBindingName, usage);
					if (isUnstackingFunctionBody) {
						subScope.usages.get(usedBindingName).onlyUsedSubFunction = true;
					}
				} else {
					Usage subScopeUsage = subScope.usages.get(usedBindingName);
					subScopeUsage.modified.addAll(usage.modified);
					subScopeUsage.tokens.addAll(usage.tokens);
					subScopeUsage.reassigned.addAll(usage.reassigned);
				}
			} else {
				// this is exiting global scope, so we finalise everything here - we are at the
				// end of the file
				if (current.predefined.containsKey(usedBindingName)) {
					// remove the declared token, so we know it is used
					declared.remove(usedBindingName);

					// note it as used so it can be reported
					usedPredefinedAndGlobals.set(usedBindingName, marker);

					// check for re-assigning a read-only (set to false) predefined
					if (current.predefined.get(usedBindingName).equals(false) && usage.reassigned != null) {
						for (int j = 0; j < usage.reassigned.size(); j++) {
							if (!usage.reassigned.get(j).isIgnoreW020()) {
								warning("W020", usage.reassigned.get(j));
							}
						}
					}
				} else {
					// binding usage is not predefined and we have not found a declaration
					// so report as undeclared
					for (int j = 0; j < usage.tokens.size(); j++) {
						Token undefinedToken = usage.tokens.get(j);
						// if its not a forgiven undefined (e.g. typof x)
						if (!undefinedToken.isForgiveUndef()) {
							// if undef is on and undef was on when the token was defined
							if (state.getOption().test("undef") && !undefinedToken.isIgnoreUndef()) {
								warning("W117", undefinedToken, usedBindingName);
							}
							if (impliedGlobals.containsKey(usedBindingName)) {
								impliedGlobals.get(usedBindingName).addLine(undefinedToken.getLine());
							} else {
								impliedGlobals.put(usedBindingName,
										new ImpliedGlobal(usedBindingName, undefinedToken.getLine()));
							}
						}
					}
				}
			}
		}

		// if exiting the global scope, we can warn about declared globals that haven't
		// been used yet
		if (subScope == null) {
			for (String bindingNotUsed : declared.keySet()) {
				warnUnused(bindingNotUsed, declared.get(bindingNotUsed), "var");
			}
		}

		// If this is not a function boundary, transfer function-scoped bindings to
		// the parent block (a rough simulation of variable hoisting). Previously
		// existing bindings in the parent block should take precedence so that
		// prior usages are not discarded.
		if (subScope != null && !isUnstackingFunctionBody &&
				!isUnstackingFunctionParams && !isUnstackingFunctionOuter) {
			for (Iterator<Map.Entry<String, Binding>> it = currentBindings.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Binding> entry = it.next();
				String defBindingName = entry.getKey();
				Binding defBinding = entry.getValue();

				if (!defBinding.blockscoped && !defBinding.type.equals("exception")) {
					Binding shadowed = subScope.bindings.get(defBindingName);

					// Do not overwrite a binding if it exists in the parent scope
					// because it is shared by adjacent blocks. Copy the `unused`
					// property so that any references found within the current block
					// are counted toward that higher-level declaration.
					if (shadowed != null) {
						shadowed.unused = shadowed.unused && defBinding.unused;
					}
					// "Hoist" the variable to the parent block, decorating the binding
					// so that future references, though technically valid, can be
					// reported as "out-of-scope" in the absence of the `funcscope`
					// option.
					else {
						defBinding.useOutsideOfScope =
								// Do not warn about out-of-scope usages in the global scope
								!currentFunctBody.type.equals("global") &&
								// When a higher scope contains a binding for the binding, the
								// binding is a re-declaration and should not prompt "used
								// out-of-scope" warnings.
										!funct.has(defBindingName, false, false, true);

						subScope.bindings.put(defBindingName, defBinding);
					}

					it.remove();
				}
			}
		}

		checkForUnused();

		if (scopeStack.size() > 0)
			scopeStack.remove(scopeStack.size() - 1);
		if (isUnstackingFunctionBody) {
			for (int i = scopeStack.size() - 1; i >= 0; i--) {
				Scope scope = scopeStack.get(i);
				// if function or if global (which is at the bottom so it will only return true
				// if we call back)
				if (scope.funcBody || scope.type.equals("global")) {
					currentFunctBody = scope;
					break;
				}
			}
		}

		current = subScope;
	}

	/**
	 * Add a function parameter to the current scope.
	 * 
	 * @param bindingName the value of the identifier
	 * @param token       indetifier token
	 */
	public void addParam(String bindingName, Token token) {
		addParam(bindingName, token, null);
	}

	/**
	 * Add a function parameter to the current scope.
	 * 
	 * @param bindingName the value of the identifier
	 * @param token       indetifier token
	 * @param type        binding type; defaults to "param"
	 */
	public void addParam(String bindingName, Token token, String type) {
		type = StringUtils.defaultIfBlank(type, "param");

		if (type.equals("exception")) {
			// if defined in the current function
			String previouslyDefinedBindingType = funct.bindingtype(bindingName, false, false, false);
			if (StringUtils.isNotEmpty(previouslyDefinedBindingType)
					&& !previouslyDefinedBindingType.equals("exception")) {
				// and has not been used yet in the current function scope
				if (!state.getOption().test("node")) {
					warning("W002", state.nextToken(), bindingName);
				}
			}

			if (state.isStrict() && (bindingName.equals("arguments") || bindingName.equals("eval"))) {
				warning("E008", token);
			}
		}

		// The variable was declared in the current scope
		if (current.bindings.containsKey(bindingName)) {
			current.bindings.get(bindingName).duplicated = true;
		}
		// The variable was declared in an outer scope
		else {
			// if this scope has the variable defined, it's a re-definition error
			checkOuterShadow(bindingName, token);

			current.bindings.put(bindingName, new Binding(type, token, false, null, true, false));

			current.params.add(bindingName);
		}

		if (current.usages.containsKey(bindingName)) {
			Usage usage = current.usages.get(bindingName);
			// if its in a sub function it is not necessarily an error, just latedef
			if (usage.onlyUsedSubFunction) {
				latedefWarning(type, bindingName, token);
			} else {
				// this is a clear illegal usage, but not a syntax error, so emit a
				// warning and not an error
				warning("W003", token, bindingName);
			}
		}
	}

	public void validateParams(boolean isArrow) {
		// This method only concerns errors for function parameters
		if (currentFunctBody.type.equals("global")) {
			return;
		}

		boolean isStrict = state.isStrict();
		Scope currentFunctParamScope = currentFunctBody.parent;
		// From ECMAScript 2017:
		//
		// > 14.1.2Static Semantics: Early Errors
		// >
		// > [...]
		// > - It is a Syntax Error if IsSimpleParameterList of
		// > FormalParameterList is false and BoundNames of FormalParameterList
		// > contains any duplicate elements.
		boolean isSimple = state.getFunct().hasSimpleParams();
		// Method definitions are defined in terms of UniqueFormalParameters, so
		// they cannot support duplicate parameter names regardless of strict
		// mode.
		boolean isMethod = state.getFunct().isMethod();

		if (currentFunctParamScope.params == null) {
			return;
		}

		for (String bindingName : currentFunctParamScope.params) {
			Binding binding = currentFunctParamScope.bindings.get(bindingName);

			if (binding.duplicated) {
				if (isStrict || isArrow || isMethod || !isSimple) {
					warning("E011", binding.token, bindingName);
				} else if (!state.getOption().get("shadow").equals(true)) {
					warning("W004", binding.token, bindingName);
				}
			}

			if (state.isStrict() && (bindingName.equals("arguments") || bindingName.equals("eval"))) {
				warning("E008", binding.token);
			}
		}
	}

	public Set<String> getUsedOrDefinedGlobals() {
		// PORT INFO: no need to check for __proto__ in Java implementation
		return usedPredefinedAndGlobals.keys();
	}

	/**
	 * Get an array of implied globals
	 * 
	 * @return list of implied globals.
	 */
	public List<ImpliedGlobal> getImpliedGlobals() {
		// PORT INFO: no need to check for __proto__ in Java implementation
		return Collections.unmodifiableList(new ArrayList<>(impliedGlobals.values()));
	}

	/**
	 * Get an array of objects describing unused bindings.
	 * 
	 * @return list of unused bindings.
	 */
	public List<Token> getUnuseds() {
		return Collections.unmodifiableList(unuseds);
	}

	/**
	 * Determine if a given name has been defined in the current scope or any
	 * lower scope.
	 *
	 * @param bindingName the value of the identifier
	 *
	 * @return true if given name is defined, false otherwise
	 */
	public boolean has(String bindingName) {
		return getBinding(bindingName) != null;
	}

	/**
	 * Retrieve binding described by `bindingName` or null
	 *
	 * @param bindingName the value of the identifier
	 *
	 * @return the type of the binding or `null` if no such
	 *         binding exists
	 */
	public String bindingtype(String bindingName) {
		Map<String, Binding> scopeBindings = getBinding(bindingName);
		if (scopeBindings != null) {
			return scopeBindings.get(bindingName).type;
		}
		return null;
	}

	/**
	 * For the exported options, indicating a variable is used outside the file
	 * 
	 * @param bindingName the value of the identifier
	 */
	public void addExported(String bindingName) {
		Map<String, Binding> globalBindings = scopeStack.get(0).bindings;
		if (declared.containsKey(bindingName)) {
			// remove the declared token, so we know it is used
			declared.remove(bindingName);
		} else if (globalBindings.containsKey(bindingName)) {
			globalBindings.get(bindingName).unused = false;
		} else {
			for (int i = 1; i < scopeStack.size(); i++) {
				Scope scope = scopeStack.get(i);
				// if `scope.(type)` is not defined, it is a block scope
				if (StringUtils.isEmpty(scope.type)) {
					if (scope.bindings.containsKey(bindingName) &&
							!scope.bindings.get(bindingName).blockscoped) {
						scope.bindings.get(bindingName).unused = false;
						return;
					}
				} else {
					break;
				}
			}
			exported.put(bindingName, true);
		}
	}

	/**
	 * Mark a binding as "exported" by an ES2015 module
	 * 
	 * @param localName  the value of the identifier
	 * @param exportName identifier token
	 */
	public void setExported(Token localName, Token exportName) {
		if (exportName != null) {
			if (esModuleExports.indexOf(exportName.getValue()) > -1) {
				error("E069", exportName, exportName.getValue());
			}

			esModuleExports.add(exportName.getValue());
		}

		if (localName != null) {
			block.use(localName.getValue(), localName);
		}
	}

	/**
	 * Mark a binding as "initialized." This is necessary to enforce the
	 * "temporal dead zone" (TDZ) of block-scoped bindings which are not
	 * hoisted.
	 *
	 * @param bindingName the value of the identifier
	 */
	public void initialize(String bindingName) {
		if (current.bindings.containsKey(bindingName)) {
			current.bindings.get(bindingName).initialized = true;
		}
	}

	/**
	 * Create a new binding and add it to the current scope. Delegates to the
	 * internal `block.add` or `func.add` methods depending on the type.
	 * Produces warnings and errors as necessary.
	 *
	 * @param bindingName the value of the identifier
	 * @param type        the type of the binding e.g. "param", "var",
	 *                    "let, "const", "import", "function",
	 *                    "generator function", "async function",
	 *                    "async generator function"
	 * @param token       the token pointing at the declaration
	 */
	public void addbinding(String bindingName, String type, Token token) {
		addbinding(bindingName, type, token, false);
	}

	/**
	 * Create a new binding and add it to the current scope. Delegates to the
	 * internal `block.add` or `func.add` methods depending on the type.
	 * Produces warnings and errors as necessary.
	 *
	 * @param bindingName the value of the identifier
	 * @param type        the type of the binding e.g. "param", "var",
	 *                    "let, "const", "import", "function",
	 *                    "generator function", "async function",
	 *                    "async generator function"
	 * @param token       the token pointing at the declaration
	 * @param initialized whether the binding should be
	 *                    created in an "initialized" state.
	 */
	public void addbinding(String bindingName, String type, Token token, boolean initialized) {
		boolean isblockscoped = type.equals("let") || type.equals("const") ||
				type.equals("class") || type.equals("import") || type.equals("generator function") ||
				type.equals("async function") || type.equals("async generator function");
		boolean ishoisted = type.equals("function") || type.equals("generator function") ||
				type.equals("async function") || type.equals("import");
		boolean isexported = (isblockscoped ? current.type.equals("global")
				: currentFunctBody.type.equals("global")) &&
				exported.containsKey(bindingName);

		// outer shadow check (inner is only on non-block scoped)
		checkOuterShadow(bindingName, token);

		if (state.isStrict() && (bindingName.equals("arguments") || bindingName.equals("eval"))) {
			warning("E008", token);
		}

		if (isblockscoped) {
			Binding declaredInCurrentScope = current.bindings.get(bindingName);
			// for block scoped variables, params are seen in the current scope as the root
			// function
			// scope, so check these too.
			if (declaredInCurrentScope == null && current == currentFunctBody &&
					!current.type.equals("global")) {
				declaredInCurrentScope = currentFunctBody.parent.bindings.get(bindingName);
			}

			// if its not already defined (which is an error, so ignore) and is used in TDZ
			if (declaredInCurrentScope == null && current.usages.containsKey(bindingName)) {
				Usage usage = current.usages.get(bindingName);
				// if its in a sub function it is not necessarily an error, just latedef
				if (usage.onlyUsedSubFunction || ishoisted) {
					latedefWarning(type, bindingName, token);
				} else if (!ishoisted) {
					// this is a clear illegal usage for block scoped variables
					warning("E056", token, bindingName, type);
				}
			}

			// If this scope has already declared a binding with the same name,
			// then this represents a redeclaration error if:
			//
			// 1. it is a "hoisted" block-scoped binding within a block. For
			// instance: generator functions may be redeclared in the global
			// scope but not within block statements
			// 2. this is not a "hoisted" block-scoped binding
			if (declaredInCurrentScope != null
					&& (!ishoisted || (!current.type.equals("global") || type.equals("import")))) {
				warning("E011", token, bindingName);
			} else if (state.getOption().get("shadow").equals("outer")) {
				// if shadow is outer, for block scope we want to detect any shadowing within
				// this function
				if (funct.has(bindingName, false, false, false)) {
					warning("W004", token, bindingName);
				}
			}

			block.add(bindingName, type, token, !isexported, initialized);
		} else {
			boolean declaredInCurrentFunctionScope = funct.has(bindingName, false, false, false);

			// check for late definition, ignore if already declared
			if (!declaredInCurrentFunctionScope && usedSoFarInCurrentFunction(bindingName)) {
				latedefWarning(type, bindingName, token);
			}

			// defining with a var or a function when a block scope variable of the same
			// name
			// is in scope is an error
			if (funct.has(bindingName, true, false, false)) {
				warning("E011", token, bindingName);
			} else if (!state.getOption().get("shadow").equals(true)) {
				// now since we didn't get any block scope variables, test for var/function
				// shadowing
				if (declaredInCurrentFunctionScope && !bindingName.equals("__proto__")) {
					// see https://github.com/jshint/jshint/issues/2400
					if (!currentFunctBody.type.equals("global")) {
						warning("W004", token, bindingName);
					}
				}
			}

			funct.add(bindingName, type, token, !isexported);

			if (currentFunctBody.type.equals("global") && !state.impliedClosure()) {
				usedPredefinedAndGlobals.set(bindingName, marker);
			}
		}
	}

	public Functor getFunct() {
		return funct;
	}

	public class Functor {

		/**
		 * Return the type of the provided binding given certain options
		 *
		 * @param bindingName     the value of the identifier
		 * @param onlyBlockscoped only include block scoped
		 *                        bindings
		 * @param excludeParams   exclude the param scope
		 * @param excludeCurrent  exclude the current scope
		 *
		 * @return type of the binding
		 */
		public String bindingtype(String bindingName, boolean onlyBlockscoped, boolean excludeParams,
				boolean excludeCurrent) {
			int currentScopeIndex = scopeStack.size() - (excludeCurrent ? 2 : 1);
			for (int i = currentScopeIndex; i >= 0; i--) {
				Scope current = scopeStack.get(i);
				if (current.bindings.containsKey(bindingName) &&
						(!onlyBlockscoped || current.bindings.get(bindingName).blockscoped)) {
					return current.bindings.get(bindingName).type;
				}
				Scope scopeCheck = excludeParams ? (scopeStack.size() > i - 1 ? scopeStack.get(i - 1) : null) : current;
				if (scopeCheck != null && scopeCheck.type.equals("functionparams")) {
					return null;
				}
			}
			return null;
		}

		/**
		 * Determine whether a `break` statement label exists in the function
		 * scope.
		 *
		 * @param labelName the value of the identifier
		 *
		 * @return true if `break` statement exists, false otherwise
		 */
		public boolean hasLabel(String labelName) {
			for (int i = scopeStack.size() - 1; i >= 0; i--) {
				Scope current = scopeStack.get(i);

				if (current.labels.containsKey(labelName)) {
					return true;
				}
				if (current.type.equals("functionparams")) {
					return false;
				}
			}
			return false;
		}

		/**
		 * Determine if a given name has been defined in the current function
		 * scope.
		 *
		 * @param bindingName     the value of the identifier
		 * @param onlyBlockscoped only include block scoped
		 *                        bindings
		 * @param excludeParams   exclude the param scope
		 * @param excludeCurrent  exclude the current scope
		 *
		 * @return true if given name is defined in the current function, false
		 *         otherwise
		 */
		public boolean has(String bindingName, boolean onlyBlockscoped, boolean excludeParams, boolean excludeCurrent) {
			return StringUtils.isNotEmpty(bindingtype(bindingName, onlyBlockscoped, excludeParams, excludeCurrent));
		}

		/**
		 * Create a new function-scoped binding and add it to the current scope. See the
		 * {@link Block#add} for coresponding logic to create
		 * block-scoped bindings.
		 *
		 * @param bindingName the value of the identifier
		 * @param type        the type of the binding; either "function" or
		 *                    "var"
		 * @param tok         the token that triggered the definition
		 * @param unused      `true` if the binding has not been
		 *                    referenced
		 */
		public void add(String bindingName, String type, Token tok, boolean unused) {
			current.bindings.put(bindingName, new Binding(type, tok, false, currentFunctBody, unused, false));
		}
	}

	public Block getBlock() {
		return block;
	}

	public class Block {

		/**
		 * Determine whether the current block scope is the global scope.
		 * 
		 * @return true if block is global, false otherwise.
		 */
		public boolean isGlobal() {
			return current.type.equals("global");
		}

		/**
		 * Resolve a reference to a binding and mark the corresponding binding as
		 * "used."
		 *
		 * @param bindingName the value of the identifier
		 * @param token       the token value that triggered the reference
		 */
		public void use(String bindingName, Token token) {
			// If the name resolves to a parameter of the current function, then do
			// not store usage. This is because in cases such as the following:
			//
			// function(a) {
			// var a;
			// a = a;
			// }
			//
			// the usage of `a` will resolve to the parameter, not to the unset
			// variable binding.
			Scope paramScope = currentFunctBody.parent;
			if (paramScope != null && paramScope.bindings.containsKey(bindingName) &&
					paramScope.bindings.get(bindingName).type.equals("param")) {
				// then check its not declared by a block scope variable
				if (!funct.has(bindingName, true, true, false)) {
					paramScope.bindings.get(bindingName).unused = false;
				}
			}

			if (token != null && (state.getIgnored().test("W117") || state.getOption().get("undef").equals(false))) {
				token.setIgnoreUndef(true);
			}

			setupUsages(bindingName);

			current.usages.get(bindingName).onlyUsedSubFunction = false;

			if (token != null) {
				token.setFunction(currentFunctBody);
				current.usages.get(bindingName).tokens.add(token);
			}

			// Block-scoped bindings can't be used within their initializer due to
			// "temporal dead zone" (TDZ) restrictions.
			Binding binding = current.bindings.get(bindingName);
			if (binding != null && binding.blockscoped && !binding.initialized) {
				error("E056", token, bindingName, binding.type);
			}
		}

		public void reassign(String bindingName, Token token) {
			token.setIgnoreW020(state.getIgnored().asBoolean("W020"));
			token.setIgnoreW021(state.getIgnored().asBoolean("W021"));

			modify(bindingName, token);

			current.usages.get(bindingName).reassigned.add(token);
		}

		public void modify(String bindingName, Token token) {
			setupUsages(bindingName);

			current.usages.get(bindingName).onlyUsedSubFunction = false;
			current.usages.get(bindingName).modified.add(token);
		}

		/**
		 * Create a new block-scoped binding and add it to the current scope. See the
		 * {@link Functor#add} method for coresponding logic to create
		 * function-scoped bindings.
		 *
		 * @param bindingName the value of the identifier
		 * @param type        the type of the binding; one of "class",
		 *                    "const", "function", "import", or "let"
		 * @param tok         the token that triggered the definition
		 * @param unused      `true` if the binding has not been
		 *                    referenced
		 */
		public void add(String bindingName, String type, Token tok, boolean unused) {
			add(bindingName, type, tok, unused, false);
		}

		/**
		 * Create a new block-scoped binding and add it to the current scope. See the
		 * {@link Functor#add} method for coresponding logic to create
		 * function-scoped bindings.
		 *
		 * @param bindingName the value of the identifier
		 * @param type        the type of the binding; one of "class",
		 *                    "const", "function", "import", or "let"
		 * @param tok         the token that triggered the definition
		 * @param unused      `true` if the binding has not been
		 *                    referenced
		 * @param initialized `true` if the binding has been
		 *                    initialized (as is the case with
		 *                    bindings created via `import`
		 *                    declarations)
		 */
		public void add(String bindingName, String type, Token tok, boolean unused, boolean initialized) {
			current.bindings.put(bindingName, new Binding(type, tok, true, null, unused, initialized));
		}

		public void addLabel(String labelName, Token token) {
			if (funct.hasLabel(labelName)) {
				warning("E011", token, labelName);
			} else if (state.getOption().get("shadow").equals("outer")) {
				if (funct.has(labelName, false, false, false)) {
					warning("W004", token, labelName);
				} else {
					checkOuterShadow(labelName, token);
				}
			}
			current.labels.put(labelName, token);
		}
	}

	protected static class Scope {
		private Map<String, Binding> bindings;
		private Map<String, Usage> usages;
		private Map<String, Token> labels;
		private Scope parent;
		private String type;
		private List<String> params;
		private Map<String, Boolean> predefined;
		private boolean funcBody;
	}

	private static class Binding {
		private String type;
		private Token token;
		private boolean blockscoped;
		private Scope function;
		private boolean unused;
		private boolean initialized;
		private boolean useOutsideOfScope;
		private boolean duplicated;

		private Binding(String type, Token token, boolean blockscoped, Scope function, boolean unused,
				boolean initialized) {
			this.type = type;
			this.token = token;
			this.blockscoped = blockscoped;
			this.function = function;
			this.unused = unused;
			this.initialized = initialized;
		}
	}

	private static class Usage {
		private List<Token> modified = new ArrayList<>();
		private List<Token> reassigned = new ArrayList<>();
		private List<Token> tokens = new ArrayList<>();
		private boolean onlyUsedSubFunction;
	}
}