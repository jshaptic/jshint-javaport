package org.jshint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.utils.EventEmitter;
import org.jshint.utils.EventContext;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

public class ScopeManager
{
	// Used to denote membership in lookup tables (a primitive value such as `true`
	// would be silently rejected for the property name "__proto__" in some
	// environments)
	private static final UniversalContainer marker = ContainerFactory.createObject();
	
	private Map<String, Boolean> exported;
	private Map<String, Token> declared;
	
	private Scope current;
	private List<Scope> scopeStack;
	
	private Scope currentFunctBody;
	
	private UniversalContainer usedPredefinedAndGlobals;
	private Map<String, ImpliedGlobal> impliedGlobals;
	private List<Token> unuseds;
	private EventEmitter emitter;
	
	private Block block;
	private Functor funct;
	
	/**
	 * A factory function for creating scope managers. A scope manager tracks
	 * variables and JSHint "labels", detecting when variables are referenced
	 * (through "usages").
	 *
	 * Note that in this context, the term "label" describes an implementation
	 * detail of JSHint and is not related to the ECMAScript language construct of
	 * the same name. Where possible, the former is referred to as a "JSHint label"
	 * to avoid confusion.
	 *
	 * @param predefined - a set of JSHint label names for built-in
	 *                     bindings provided by the environment
	 * @param exported - a hash for JSHint label names that are intended
	 *                   to be referenced in contexts beyond the current
	 *                   program code
	 * @param declared - a hash for JSHint label names that were defined
	 *                   as global bindings via linting configuration
	 *
	 * @return a scope manager
	 */
	public ScopeManager(Map<String, Boolean> predefined, Map<String, Boolean> exported, Map<String, Token> declared)
	{
		this.exported = exported;
		this.declared = declared;
		
		this.scopeStack = new ArrayList<Scope>();
		
		this.newScope("global");
		this.current.setPredefined(predefined);
		
		this.currentFunctBody = this.current; // this is the block after the params = function
		
		this.usedPredefinedAndGlobals = ContainerFactory.nullContainer().create();
		this.impliedGlobals = new HashMap<String, ImpliedGlobal>();
		this.unuseds = new ArrayList<Token>();
		this.emitter = new EventEmitter();
		
		this.funct = new Functor();
		this.block = new Block();
	}
	
	private void newScope(String type)
	{
		type = StringUtils.defaultString(type);
		
		Scope newScope = new Scope();
		newScope.setLabels(new LinkedHashMap<String, Label>());
		newScope.setUsages(new HashMap<String, Usage>());
		newScope.setBreakLabels(new HashMap<String, Token>());
		newScope.setParent(current);
		newScope.setType(type);
		newScope.setParams(type.equals("functionparams") || type.equals("catchparams") ? new ArrayList<String>() : null);
		current = newScope;
		scopeStack.add(current);
	}
	
	private void warning(String code, Token token, String... data)
	{
		EventContext context = new EventContext();
		context.setCode(code);
		context.setToken(token);
		context.setData(data);
		emitter.emit("warning", context);
	}
	
	private void error(String code, Token token, String... data)
	{
		EventContext context = new EventContext();
		context.setCode(code);
		context.setToken(token);
		context.setData(data);
		emitter.emit("warning", context);
	}
	
	private void setupUsages(String labelName)
	{
		if (!current.getUsages().containsKey(labelName))
		{
			current.getUsages().put(labelName, new Usage());
		}
	}
	
	private UniversalContainer getUnusedOption(UniversalContainer unused_opt)
	{
		if (unused_opt.isUndefined())
		{
			unused_opt = State.getOption().get("unused");
		}
		
		if (unused_opt.equals(true))
		{
			unused_opt = new UniversalContainer("last-param");
		}
		
		return unused_opt;
	}
	
	private void warnUnused(String name, Token tkn, String type)
	{
		warnUnused(name, tkn, type, ContainerFactory.undefinedContainer());
	}
	
	private void warnUnused(String name, Token tkn, String type, UniversalContainer unused_opt)
	{
		int line = tkn.getLine();
		int chr = tkn.getFrom();
		String raw_name = StringUtils.defaultIfEmpty(tkn.getRawText(), name);
		
		unused_opt = getUnusedOption(unused_opt);
		
		Map<String, List<String>> warnable_types = new HashMap<String, List<String>>();
		warnable_types.put("vars", Arrays.asList("var"));
		warnable_types.put("last-param", Arrays.asList("var", "param"));
		warnable_types.put("strict", Arrays.asList("var", "param", "last-param"));
		
		if (unused_opt.test())
		{
			if (warnable_types.containsKey(unused_opt.asString()) && warnable_types.get(unused_opt.asString()).indexOf(type) != -1)
			{
				Token t = new Token();
				t.setLine(line);
				t.setFrom(chr);
				warning("W098", t, raw_name);
			}
		}
		
		// inconsistent - see gh-1894
		if (unused_opt.test() || type.equals("var"))
		{
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
	private void checkForUnused()
	{
		// function parameters are validated by a dedicated function
	    // assume that parameters are the only thing declared in the param scope
		if (current.getType().equals("functionparams"))
		{
			checkParams();
			return;
		}
		Map<String, Label> curentLabels = current.getLabels();
		for (String labelName : curentLabels.keySet())
		{
			if (!curentLabels.get(labelName).getType().equals("exception") &&
				curentLabels.get(labelName).isUnused())
			{
				warnUnused(labelName, curentLabels.get(labelName).getToken(), "var");
			}
		}
	}
	
	/**
	 * Check the current scope for unused parameters and issue warnings as
	 * necessary. This function may only be invoked when the current scope is a
	 * "function parameter" scope.
	 */
	private void checkParams()
	{
		List<String> params = current.getParams();
		
		if (params == null)
		{
			return;
		}
		
		String param = params.size() > 0 ? params.remove(params.size()-1) : null;
		UniversalContainer unused_opt = ContainerFactory.undefinedContainer();
		
		while (param != null)
		{
			Label label = current.getLabels().get(param);
			
			unused_opt = getUnusedOption(State.getFunct().getUnusedOption());
			
			// 'undefined' is a special case for the common pattern where `undefined`
			// is used as a formal parameter name to defend against global
			// re-assignment, e.g.
			//
			//     (function(window, undefined) {
			//     })();
			if (param.equals("undefined"))
				return;
			
			if (label.isUnused())
			{
				warnUnused(param, label.getToken(), "param", State.getFunct().getUnusedOption());
			}
			else if (unused_opt.equals("last-param"))
			{
				return;
			}
			
			param = params.size() > 0 ? params.remove(params.size()-1) : null;
		}
	}
	
	/**
	 * Find the relevant JSHint label's scope. The owning scope is located by
	 * first inspecting the current scope and then moving "downward" through the
	 * stack of scopes.
	 *
	 * @param labelName - the value of the identifier
	 *
	 * @return the scope in which the JSHint label was found
	 */
	private Map<String, Label> getLabel(String labelName)
	{
		for (int i = scopeStack.size() - 1; i >= 0; --i)
		{
			Map<String, Label> scopeLabels = scopeStack.get(i).getLabels();
			if (scopeLabels.containsKey(labelName))
			{
				return scopeLabels;
			}
		}
		
		return null;
	}
	
	/**
	 * Determine if a given JSHint label name has been referenced within the
	 * current function or any function defined within.
	 *
	 * @param labelName - the value of the identifier
	 *
	 * @return 
	 */
	private boolean usedSoFarInCurrentFunction(String labelName)
	{
		for (int i = scopeStack.size() - 1; i >= 0; i--)
		{
			Scope current = scopeStack.get(i);
			if (current.getUsages().containsKey(labelName))
			{
				return true;
			}
			if (current == currentFunctBody)
			{
				break;
			}
		}
		return false;
	}
	
	private void checkOuterShadow(String labelName, Token token)
	{
		// only check if shadow is outer
		if (!State.getOption().get("shadow").equals("outer"))
		{
			return;
		}
		
		boolean isGlobal = currentFunctBody.getType().equals("global");
		boolean isNewFunction = current.getType().equals("functionparams");
		
		boolean outsideCurrentFunction = !isGlobal;
		for (int i = 0; i < scopeStack.size(); i++)
		{
			Scope stackItem = scopeStack.get(i);
			
			if (!isNewFunction && scopeStack.size() > i + 1 && scopeStack.get(i + 1) == currentFunctBody)
			{
				outsideCurrentFunction = false;
			}
			if (outsideCurrentFunction && stackItem.getLabels().containsKey(labelName))
			{
				warning("W123", token, labelName);
			}
			if (stackItem.getBreakLabels().containsKey(labelName))
			{
				warning("W123", token, labelName);
			}
		}
	}
	
	private void latedefWarning(String type, String labelName, Token token)
	{
		if (State.getOption().test("latedef"))
		{
			boolean isFunction = type.equals("function") || type.equals("generator function") ||
				type.equals("async function");
			
			// if either latedef is strict and this is a function
		    //    or this is not a function
			if ((State.getOption().get("latedef").equals(true) && isFunction) || !isFunction)
			{
				warning("W003", token, labelName);
			}
		}
	}
	
	public void on(String names, LexerEventListener listener)
	{
		for (String name : names.split(" ", -1))
		{
			emitter.on(name, listener);
		}
	}
	
	public boolean isPredefined(String labelName)
	{
		return !has(labelName) && scopeStack.get(0).getPredefined().containsKey(labelName);
	}
	
	/**
	 * Create a new scope within the current scope. As the topmost value, the
	 * new scope will be interpreted as the current scope until it is
	 * exited--see the `unstack` method.
	 */
	public void stack()
	{
		stack(null);
	}
	
	/**
	 * Create a new scope within the current scope. As the topmost value, the
	 * new scope will be interpreted as the current scope until it is
	 * exited--see the `unstack` method.
	 *
	 * @param type - The type of the scope. Valid values are
	 *               "functionparams", "catchparams" and
	 *               "functionouter"
	 */
	public void stack(String type)
	{
		Scope previousScope = current;
		newScope(type);
		
		if (StringUtils.isEmpty(type) && previousScope.getType().equals("functionparams"))
		{
			current.setFuncBody(true);
			currentFunctBody = current;
		}
	}
	
	/**
	 * Valldate all binding references and declarations in the current scope
	 * and set the next scope on the stack as the active scope.
	 */
	public void unstack()
	{
		Scope subScope = scopeStack.size() > 1 ? scopeStack.get(scopeStack.size() - 2) : null;
		boolean isUnstackingFunctionBody = current == currentFunctBody;
		boolean isUnstackingFunctionParams = current.getType().equals("functionparams");
		boolean isUnstackingFunctionOuter = current.getType().equals("functionouter");
		
		boolean isImmutable = false;
		Map<String, Usage> currentUsages = current.getUsages();
		Map<String, Label> currentLabels = current.getLabels();
		Set<String> usedLabelNameList = currentUsages.keySet();
		
		for (String usedLabelName : usedLabelNameList)
		{
			Usage usage = currentUsages.get(usedLabelName);
			Label usedLabel = currentLabels.get(usedLabelName);
			if (usedLabel != null)
			{
				String usedLabelType = usedLabel.getType();
				isImmutable = usedLabelType.equals("const") || usedLabelType.equals("import");
				
				if (usedLabel.isUseOutsideOfScope() && !State.getOption().test("funcscope"))
				{
					List<Token> usedTokens = usage.getTokens();
					for (int j = 0; j < usedTokens.size(); j++)
					{
						// Keep the consistency of https://github.com/jshint/jshint/issues/2409
						if (usedLabel.getFunction() == usedTokens.get(j).getFunction())
						{
							error("W038", usedTokens.get(j), usedLabelName);
						}
					}
				}
				
				// mark the label used
				current.getLabels().get(usedLabelName).setUnused(false);
				
				// check for modifying a const
				if (isImmutable && usage.getModified() != null)
				{
					for (int j = 0; j < usage.getModified().size(); j++)
					{
						error("E013", usage.getModified().get(j), usedLabelName);
					}
				}
				
				boolean isFunction = usedLabelType.equals("function") ||
					usedLabelType.equals("generator function") ||
					usedLabelType.equals("async fuction");
				
				// check for re-assigning a function declaration
				if ((isFunction || usedLabelType.equals("class")) && usage.getReassigned() != null)
				{
					for (int j = 0; j < usage.getReassigned().size(); j++)
					{
						if (!usage.getReassigned().get(j).isIgnoreW021())
						{
							warning("W021", usage.getReassigned().get(j), usedLabelName, usedLabelType);
						}
					}
				}
				continue;
			}
			
			if (subScope != null)
			{
				String labelType = labeltype(usedLabelName);
				isImmutable = StringUtils.defaultString(labelType).equals("const") ||
					(labelType == null && BooleanUtils.isFalse(scopeStack.get(0).getPredefined().get(usedLabelName)));
				if (isUnstackingFunctionOuter  && !isImmutable)
				{
					if (State.getFunct().getOuterMutables() == null)
					{
						State.getFunct().setOuterMutables(new ArrayList<String>());
					}
					State.getFunct().addOuterMutables(usedLabelName);
				}
				
				// not exiting the global scope, so copy the usage down in case its an out of scope usage
				if (!subScope.getUsages().containsKey(usedLabelName))
				{
					subScope.getUsages().put(usedLabelName, usage);
					if (isUnstackingFunctionBody)
					{
						subScope.getUsages().get(usedLabelName).setOnlyUsedSubFunction(true);
					}
				}
				else
				{
					Usage subScopeUsage = subScope.getUsages().get(usedLabelName);
					subScopeUsage.getModified().addAll(usage.getModified());
					subScopeUsage.getTokens().addAll(usage.getTokens());
					subScopeUsage.getReassigned().addAll(usage.getReassigned());
				}
			}
			else
			{
				// this is exiting global scope, so we finalise everything here - we are at the end of the file
				if (current.getPredefined().containsKey(usedLabelName))
				{
					// remove the declared token, so we know it is used
					declared.remove(usedLabelName);
					
					// note it as used so it can be reported
					usedPredefinedAndGlobals.set(usedLabelName, marker);
					
					// check for re-assigning a read-only (set to false) predefined
					if (current.getPredefined().get(usedLabelName).equals(false) && usage.getReassigned() != null)
					{
						for (int j = 0; j < usage.getReassigned().size(); j++)
						{
							if (!usage.getReassigned().get(j).isIgnoreW020())
							{
								warning("W020", usage.getReassigned().get(j));
							}
						}
					}
				}
				else
				{
					// label usage is not predefined and we have not found a declaration
		            // so report as undeclared
					for (int j = 0; j < usage.getTokens().size(); j++)
					{
						Token undefinedToken = usage.getTokens().get(j);
						// if its not a forgiven undefined (e.g. typof x)
						if (!undefinedToken.isForgiveUndef())
						{
							// if undef is on and undef was on when the token was defined
							if (State.getOption().test("undef") && !undefinedToken.isIgnoreUndef())
							{
								warning("W117", undefinedToken, usedLabelName);
							}
							if (impliedGlobals.containsKey(usedLabelName))
							{
								impliedGlobals.get(usedLabelName).addLine(undefinedToken.getLine());
							}
							else
							{
								impliedGlobals.put(usedLabelName, new ImpliedGlobal(usedLabelName, undefinedToken.getLine()));
							}
						}
					}
				}
			}
		}
		
		// if exiting the global scope, we can warn about declared globals that haven't been used yet
		if (subScope == null)
		{
			for (String labelNotUsed : declared.keySet())
			{
				warnUnused(labelNotUsed, declared.get(labelNotUsed), "var");
			}
		}
		
		// If this is not a function boundary, transfer function-scoped labels to
	    // the parent block (a rough simulation of variable hoisting). Previously
	    // existing labels in the parent block should take precedence so that things and stuff.
		if (subScope != null && !isUnstackingFunctionBody &&
			!isUnstackingFunctionParams && !isUnstackingFunctionOuter)
		{
			for (Iterator<Map.Entry<String, Label>> it = currentLabels.entrySet().iterator(); it.hasNext(); )
			{
				Map.Entry<String, Label> entry = it.next();
				String defLabelName = entry.getKey();
				Label defLabel = entry.getValue();
				
				if (!defLabel.isBlockscoped() && !defLabel.getType().equals("exception"))
				{
					Label shadowed = subScope.getLabels().get(defLabelName);
					
					// Do not overwrite a label if it exists in the parent scope
		            // because it is shared by adjacent blocks. Copy the `unused`
		            // property so that any references found within the current block
		            // are counted toward that higher-level declaration.
					if (shadowed != null)
					{
						shadowed.setUnused(shadowed.isUnused() && defLabel.isUnused());
					}
					// "Hoist" the variable to the parent block, decorating the label
		            // so that future references, though technically valid, can be
		            // reported as "out-of-scope" in the absence of the `funcscope`
		            // option.
					else
					{
						defLabel.setUseOutsideOfScope(
							// Do not warn about out-of-scope usages in the global scope
							!currentFunctBody.getType().equals("global") &&
							// When a higher scope contains a binding for the label, the
			                // label is a re-declaration and should not prompt "used
			                // out-of-scope" warnings.
							!funct.has(defLabelName, false, false, true));
						
						subScope.getLabels().put(defLabelName, defLabel);
					}
					
					it.remove();
				}
			}
		}
		
		checkForUnused();
		
		if (scopeStack.size() > 0) scopeStack.remove(scopeStack.size()-1);
		if (isUnstackingFunctionBody)
		{
			for (int i = scopeStack.size() - 1; i >= 0; i--)
			{
				Scope scope = scopeStack.get(i);
				// if function or if global (which is at the bottom so it will only return true if we call back)
				if (scope.isFuncBody() || scope.getType().equals("global"))
				{
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
	 * @param labelName - the value of the identifier
	 * @param token
	 */
	public void addParam(String labelName, Token token)
	{
		addParam(labelName, token, null);
	}
	
	/**
	 * Add a function parameter to the current scope.
	 * 
	 * @param labelName - the value of the identifier
	 * @param token
	 * @param type - JSHint label type; defaults to "param"
	 */
	public void addParam(String labelName, Token token, String type)
	{
		type = StringUtils.defaultIfBlank(type, "param");
		
		if (type.equals("exception"))
		{
			// if defined in the current function
			String previouslyDefinedLabelType = funct.labeltype(labelName, false, false, false);
			if (StringUtils.isNotEmpty(previouslyDefinedLabelType) && !previouslyDefinedLabelType.equals("exception"))
			{
				// and has not been used yet in the current function scope
				if (!State.getOption().test("node"))
				{
					warning("W002", State.nextToken(), labelName);
				}
			}
			
			if (State.isStrict() && (labelName.equals("arguments") || labelName.equals("eval")))
			{
				warning("E008", token);
			}
		}
		
		// The variable was declared in the current scope
		if (current.getLabels().containsKey(labelName))
		{
			current.getLabels().get(labelName).setDuplicated(true);
		}
		// The variable was declared in an outer scope
		else
		{
			// if this scope has the variable defined, it's a re-definition error
			checkOuterShadow(labelName, token);
			
			current.getLabels().put(labelName, new Label(type, token, false, null, true, false));
			
			current.getParams().add(labelName);
		}
		
		if (current.getUsages().containsKey(labelName))
		{
			Usage usage = current.getUsages().get(labelName);
			// if its in a sub function it is not necessarily an error, just latedef
			if (usage.isOnlyUsedSubFunction())
			{
				latedefWarning(type, labelName, token);
			}
			else
			{
				// this is a clear illegal usage for block scoped variables
		        warning("E056", token, labelName, type);
			}
		}
	}
	
	public void validateParams(boolean isArrow)
	{
		// This method only concerns errors for function parameters
		if (currentFunctBody.getType().equals("global"))
		{
			return;
		}
		
		boolean isStrict = State.isStrict();
		Scope currentFunctParamScope = currentFunctBody.getParent();
		// From ECMAScript 2017:
		//
		// > 14.1.2Static Semantics: Early Errors
		// >
		// > [...]
		// > - It is a Syntax Error if IsSimpleParameterList of
		// >   FormalParameterList is false and BoundNames of FormalParameterList
		// >   contains any duplicate elements.
		boolean isSimple = State.getFunct().hasSimpleParams();
		// Method definitions are defined in terms of UniqueFormalParameters, so
		// they cannot support duplicate parameter names regardless of strict
		// mode.
		boolean isMethod = State.getFunct().isMethod();
		
		if (currentFunctParamScope.getParams() == null)
		{
			return;
		}
		
		for (String labelName : currentFunctParamScope.getParams())
		{
			Label label = currentFunctParamScope.getLabels().get(labelName);
			
			if (label.isDuplicated())
			{
				if (isStrict || isArrow || isMethod || !isSimple)
				{
					warning("E011", label.getToken(), labelName);
				}
				else if (!State.getOption().get("shadow").equals(true))
				{
					warning("W004", label.getToken(), labelName);
				}
			}
			
			if (State.isStrict() && (labelName.equals("arguments") || labelName.equals("eval")))
			{
				warning("E008", label.getToken());
			}
		}
	}
	
	public Set<String> getUsedOrDefinedGlobals()
	{
		return usedPredefinedAndGlobals.keys();
	}
	
	/**
     * Get an array of implied globals
     * 
     * @return list of implied globals.
     */
	public List<ImpliedGlobal> getImpliedGlobals()
	{
		return Collections.unmodifiableList(new ArrayList<ImpliedGlobal>(impliedGlobals.values()));
	}
	
	/**
     * Get an array of objects describing unused bindings.
     * 
     * @return list of unused bindings.
     */
	public List<Token> getUnuseds()
	{
		return Collections.unmodifiableList(unuseds);
	}
	
	/**
	 * Determine if a given name has been defined in the current scope or any
	 * lower scope.
	 *
	 * @param labelName - the value of the identifier
	 *
	 * @return
	 */
	public boolean has(String labelName)
	{
		return getLabel(labelName) != null;
	}
	
	/**
	 * Retrieve  described by `labelName` or null
	 *
	 * @param labelName - the value of the identifier
	 *
	 * @return the type of the JSHint label or `null` if no
	 *         such label exists
	 */
	public String labeltype(String labelName)
	{
		// returns a labels type or null if not present
		Map<String, Label> scopeLabels = getLabel(labelName);
		if (scopeLabels != null)
		{
			return scopeLabels.get(labelName).getType();
		}
		return null;
	}
	
	/**
     * For the exported options, indicating a variable is used outside the file
     * 
     * @param labelName - the value of the identifier
     */
	public void addExported(String labelName)
	{
		Map<String, Label> globalLabels = scopeStack.get(0).getLabels();
		if (declared.containsKey(labelName))
		{
			// remove the declared token, so we know it is used
			declared.remove(labelName);
		}
		else if (globalLabels.containsKey(labelName))
		{
			globalLabels.get(labelName).setUnused(false);
		}
		else
		{
			for (int i = 1; i < scopeStack.size(); i++)
			{
				Scope scope = scopeStack.get(i);
				// if `scope.(type)` is not defined, it is a block scope
				if (StringUtils.isEmpty(scope.getType()))
				{
					if (scope.getLabels().containsKey(labelName) &&
						!scope.getLabels().get(labelName).isBlockscoped())
					{
						scope.getLabels().get(labelName).setUnused(false);
						return;
					}
				}
				else
				{
					break;
				}
			}
			exported.put(labelName, true);
		}
	}
	
	/**
     * Mark a JSHint label as "exported" by an ES2015 module
     * 
     * @param labelName - the value of the identifier
     * @param token
     */
	public void setExported(String labelName, Token token)
	{
		block.use(labelName, token);
	}
	
	/**
	 * Mark a JSHint label as "initialized." This is necessary to enforce the
	 * "temporal dead zone" (TDZ) of block-scoped bindings which are not
	 * hoisted.
	 *
	 * @param labelName - the value of the identifier
	 */
	public void initialize(String labelName)
	{
		if (current.getLabels().containsKey(labelName))
		{
			current.getLabels().get(labelName).setInitialized(true);
		}
	}
	
	/**
	 * Create a new JSHint label and add it to the current scope. Delegates to
	 * the internal `block.add` or `func.add` methods depending on the type.
	 * Produces warnings and errors as necessary.
	 *
	 * @param labelName
	 * @param type - the type of the label e.g. "param", "var",
	 *               "let, "const", "import", "function",
	 *               "generator function", "async function",
	 *               "async generator function"
	 * @param token - the token pointing at the declaration
	 */
	public void addlabel(String labelName, String type, Token token)
	{
		addlabel(labelName, type, token, false);
	}
	
	/**
	 * Create a new JSHint label and add it to the current scope. Delegates to
	 * the internal `block.add` or `func.add` methods depending on the type.
	 * Produces warnings and errors as necessary.
	 *
	 * @param labelName
	 * @param type - the type of the label e.g. "param", "var",
	 *               "let, "const", "import", "function",
	 *               "generator function", "async function",
	 *               "async generator function"
	 * @param token - the token pointing at the declaration
	 * @param initialized - whether the binding should be
	 *                      created in an "initialized" state.
	 */
	public void addlabel(String labelName, String type, Token token, boolean initialized)
	{
		boolean isblockscoped = type.equals("let") || type.equals("const") ||
			type.equals("class") || type.equals("import") || type.equals("generator function") ||
			type.equals("async function") || type.equals("async generator function");
		boolean ishoisted = type.equals("function") || type.equals("generator function") ||
			type.equals("async function") || type.equals("import");
		boolean isexported = (isblockscoped ? current.getType().equals("global") : currentFunctBody.getType().equals("global")) && 
							 exported.containsKey(labelName);
		
		// outer shadow check (inner is only on non-block scoped)
		checkOuterShadow(labelName, token);
		
		if (State.isStrict() && (labelName.equals("arguments") || labelName.equals("eval")))
		{
			warning("E008", token);
		}
		
		if (isblockscoped)
		{
			Label declaredInCurrentScope = current.getLabels().get(labelName);
			// for block scoped variables, params are seen in the current scope as the root function
	        // scope, so check these too.
			if (declaredInCurrentScope == null && current == currentFunctBody &&
				!current.getType().equals("global"))
			{
				declaredInCurrentScope = currentFunctBody.getParent().getLabels().get(labelName);
			}
			
			// if its not already defined (which is an error, so ignore) and is used in TDZ
			if (declaredInCurrentScope == null && current.getUsages().containsKey(labelName))
			{
				Usage usage = current.getUsages().get(labelName);
				// if its in a sub function it is not necessarily an error, just latedef
				if (usage.isOnlyUsedSubFunction() || ishoisted)
				{
					latedefWarning(type, labelName, token);
				}
				else if (!ishoisted)
				{
					// this is a clear illegal usage for block scoped variables
					warning("E056", token, labelName, type);
				}
			}
			
			// If this scope has already declared a binding with the same name,
			// then this represents a redeclaration error if:
			//
			// 1. it is a "hoisted" block-scoped binding within a block. For
			//		    instance: generator functions may be redeclared in the global
			//		    scope but not within block statements
			// 2. this is not a "hoisted" block-scoped binding
			if (declaredInCurrentScope != null && (!ishoisted || (!current.getType().equals("global") || type.equals("import"))))
			{
				warning("E011", token, labelName);
			}
			else if (State.getOption().get("shadow").equals("outer"))
			{
				// if shadow is outer, for block scope we want to detect any shadowing within this function
				if (funct.has(labelName, false, false, false))
				{
					warning("W004", token, labelName);
				}
			}
			
			block.add(labelName, type, token, !isexported, initialized);
		}
		else
		{
			boolean declaredInCurrentFunctionScope = funct.has(labelName, false, false, false);
			
			// check for late definition, ignore if already declared
			if (!declaredInCurrentFunctionScope && usedSoFarInCurrentFunction(labelName))
			{
				latedefWarning(type, labelName, token);
			}
			
			// defining with a var or a function when a block scope variable of the same name
	        // is in scope is an error
			if (funct.has(labelName, true, false, false))
			{
				warning("E011", token, labelName);
			}
			else if (!State.getOption().get("shadow").equals(true))
			{
				// now since we didn't get any block scope variables, test for var/function
		        // shadowing
				if (declaredInCurrentFunctionScope && !labelName.equals("__proto__"))
				{
					// see https://github.com/jshint/jshint/issues/2400
					if (!currentFunctBody.getType().equals("global"))
					{
						warning("W004", token, labelName);
					}
				}
			}
			
			funct.add(labelName, type, token, !isexported);
			
			if (currentFunctBody.getType().equals("global") && !State.impliedClosure())
			{
				usedPredefinedAndGlobals.set(labelName, marker);
			}
		}
	}
	
	public Functor getFunct()
	{
		return funct;
	}
	
	public class Functor
	{
		/**
	     * Return the type of the provided JSHint label given certain options
	     *
	     * @param labelName
	     * @param onlyBlockscoped - only include block scoped
	     *                          labels
	     * @param excludeParams - exclude the param scope
	     * @param excludeCurrent - exclude the current scope
	     *
	     * @return
	     */
		public String labeltype(String labelName, boolean onlyBlockscoped, boolean excludeParams, boolean excludeCurrent)
		{
			int currentScopeIndex = scopeStack.size() - (excludeCurrent ? 2 : 1);
			for (int i = currentScopeIndex; i >= 0; i--)
			{
				Scope current = scopeStack.get(i);
				if (current.getLabels().containsKey(labelName) &&
					(!onlyBlockscoped || current.getLabels().get(labelName).isBlockscoped()))
				{
					return current.getLabels().get(labelName).getType();
				}
				Scope scopeCheck = excludeParams ? (scopeStack.size() > i-1 ? scopeStack.get(i-1) : null) : current;
				if (scopeCheck != null && scopeCheck.getType().equals("functionparams"))
				{
					return null;
				}
			}
			return null;
		}
		
		/**
	     * Determine whether a `break` statement label exists in the function
	     * scope.
	     *
	     * @param labelName - the value of the identifier
	     *
	     * @return
	     */
		public boolean hasBreakLabel(String labelName)
		{
			for (int i = scopeStack.size() - 1; i >= 0; i--)
			{
				Scope current = scopeStack.get(i);
				
				if (current.getBreakLabels().containsKey(labelName))
				{
					return true;
				}
				if (current.getType().equals("functionparams"))
				{
					return false;
				}
			}
			return false;
		}
		
		/**
	     * Determine if a given name has been defined in the current function
	     * scope.
	     *
	     * @param labelName - the value of the identifier
	     * @param options - options as supported by the
	     *                  `funct.labeltype` method
	     *
	     * @return
	     */
		public boolean has(String labelName, boolean onlyBlockscoped, boolean excludeParams, boolean excludeCurrent)
		{
			return StringUtils.isNotEmpty(labeltype(labelName, onlyBlockscoped, excludeParams, excludeCurrent));
		}
		
		/**
	     * Create a new function-scoped JSHint label and add it to the current
	     * scope. See the {@link Block#add} for coresponding logic to create
	     * block-scoped JSHint labels
	     *
	     * @param labelName - the value of the identifier
	     * @param type - the type of the JSHint label; either "function"
	     *               or "var"
	     * @param tok - the token that triggered the definition
	     * @param unused - `true` if the JSHint label has not been
	     *                 referenced
	     */
		public void add(String labelName, String type, Token tok, boolean unused)
		{
			current.getLabels().put(labelName, new Label(type, tok, false, currentFunctBody, unused, false));
		}
	}
	
	public Block getBlock()
	{
		return block;
	}
	
	public class Block
	{
		/**
	     * Determine whether the current block scope is the global scope.
	     * 
	     * @return true if block is global, false otherwise.
	     */
		public boolean isGlobal()
		{
			return current.getType().equals("global");
		}
		
		/**
		 * Resolve a reference to a binding and mark the corresponding JSHint
		 * label as "used."
		 *
		 * @param token - the token value that triggered the reference
		 */
		public void use(String labelName, Token token)
		{
			// If the name resolves to a parameter of the current function, then do
			// not store usage. This is because in cases such as the following:
			//
			//	function(a) {
			//		var a;
			//		a = a;
			//	}
			//
			// the usage of `a` will resolve to the parameter, not to the unset
			// variable binding.
			Scope paramScope = currentFunctBody.getParent();
			if (paramScope != null && paramScope.getLabels().containsKey(labelName) &&
				paramScope.getLabels().get(labelName).getType().equals("param"))
			{
				// then check its not declared by a block scope variable
				if (!funct.has(labelName, true, true, false))
				{
					paramScope.getLabels().get(labelName).setUnused(false);
				}
			}
			
			if (token != null && (State.getIgnored().test("W117") || State.getOption().get("undef").equals(false)))
			{
				token.setIgnoreUndef(true);
			}
			
			setupUsages(labelName);
			
			current.getUsages().get(labelName).setOnlyUsedSubFunction(false);
			
			if (token != null)
			{
				token.setFunction(currentFunctBody);
				current.getUsages().get(labelName).getTokens().add(token);
			}
			
			// Block-scoped bindings can't be used within their initializer due to
			// "temporal dead zone" (TDZ) restrictions.
			Label label = current.getLabels().get(labelName);
			if (label != null && label.isBlockscoped() && !label.isInitialized())
			{
				error("E056", token, labelName, label.getType());
			}
		}
		
		public void reassign(String labelName, Token token)
		{
			token.setIgnoreW020(State.getIgnored().asBoolean("W020"));
			token.setIgnoreW021(State.getIgnored().asBoolean("W021"));
			
			modify(labelName, token);
			
			current.getUsages().get(labelName).getReassigned().add(token);
		}
		
		public void modify(String labelName, Token token)
		{
			setupUsages(labelName);
			
			current.getUsages().get(labelName).setOnlyUsedSubFunction(false);
			current.getUsages().get(labelName).getModified().add(token);
		}
		
		/**
		 * Create a new block-scoped JSHint label and add it to the current
		 * scope. See the {@link Functor#add} method for coresponding logic to create
		 * function-scoped JSHint labels.
		 *
		 * @param labelName - the value of the identifier
		 * @param type - the type of the JSHint label; one of "class",
		 *               "const", "function", "import", or "let"
		 * @param tok - the token that triggered the definition
		 * @param unused - `true` if the JSHint label has not been
		 *                 referenced
		 */
		public void add(String labelName, String type, Token tok, boolean unused)
		{
			add(labelName, type, tok, unused, false);
		}
		
		/**
		 * Create a new block-scoped JSHint label and add it to the current
		 * scope. See the {@link Functor#add} method for coresponding logic to create
		 * function-scoped JSHint labels.
		 *
		 * @param labelName - the value of the identifier
		 * @param type - the type of the JSHint label; one of "class",
		 *               "const", "function", "import", or "let"
		 * @param tok - the token that triggered the definition
		 * @param unused - `true` if the JSHint label has not been
		 *                 referenced
		 * @param initialized - `true` if the JSHint label has been
		 *                      initialized (as is the case with JSHint
		 *                      labels created via `import`
		 *                      declarations)
		 */
		public void add(String labelName, String type, Token tok, boolean unused, boolean initialized)
		{
			current.getLabels().put(labelName, new Label(type, tok, true, null, unused, initialized));
		}
		
		public void addBreakLabel(String labelName, Token token)
		{
			if (funct.hasBreakLabel(labelName))
			{
				warning("E011", token, labelName);
			}
			else if (State.getOption().get("shadow").equals("outer"))
			{
				if (funct.has(labelName, false, false, false))
				{
					warning("W004", token, labelName);
				}
				else
				{
					checkOuterShadow(labelName, token);
				}
			}
			current.getBreakLabels().put(labelName, token);
		}
	}
	
	protected static class Scope
	{
		private Map<String, Label> labels;
		private Map<String, Usage> usages;
		private Map<String, Token> breakLabels;
		private Scope parent;
		private String type;
		private List<String> params;
		private Map<String, Boolean> predefined;
		private boolean funcBody= false;
		
		private Map<String, Label> getLabels()
		{
			return labels;
		}
		
		private void setLabels(Map<String, Label> labels)
		{
			this.labels = labels;
		}
		
		private Map<String, Usage> getUsages()
		{
			return usages;
		}
		
		private void setUsages(Map<String, Usage> usages)
		{
			this.usages = usages;
		}
		
		private Map<String, Token> getBreakLabels()
		{
			return breakLabels;
		}
		
		private void setBreakLabels(Map<String, Token> breakLabels)
		{
			this.breakLabels = breakLabels;
		}
		
		private Scope getParent()
		{
			return parent;
		}
		
		private void setParent(Scope parent)
		{
			this.parent = parent;
		}
		
		private String getType()
		{
			return type;
		}
		
		private void setType(String type)
		{
			this.type = type;
		}
		
		private List<String> getParams()
		{
			return params;
		}
		
		private void setParams(List<String> params)
		{
			this.params = params;
		}
		
		private Map<String, Boolean> getPredefined()
		{
			return predefined;
		}
		
		private void setPredefined(Map<String, Boolean> predefined)
		{
			this.predefined = predefined;
		}

		private boolean isFuncBody()
		{
			return funcBody;
		}

		private void setFuncBody(boolean isFuncBody)
		{
			this.funcBody = isFuncBody;
		}
	}
	
	private static class Label
	{
		private String type;
		private Token token;
		private boolean blockscoped = false;
		private Scope function;
		private boolean unused = false;
		private boolean initialized = false;
		
		private boolean useOutsideOfScope = false;
		private boolean duplicated = false;
		
		private Label(String type, Token token, boolean blockscoped, Scope function, boolean unused, boolean initialized)
		{
			this.type = type;
			this.token = token;
			this.blockscoped = blockscoped;
			this.function = function;
			this.unused = unused;
			this.initialized = initialized;
		}
		
		private String getType()
		{
			return type;
		}
		
		private Token getToken()
		{
			return token;
		}
		
		private boolean isBlockscoped()
		{
			return blockscoped;
		}
		
		private Scope getFunction()
		{
			return function;
		}
		
		private boolean isUnused()
		{
			return unused;
		}
		
		private void setUnused(boolean unused)
		{
			this.unused = unused;
		}
		
		private boolean isInitialized()
		{
			return initialized;
		}
		
		private void setInitialized(boolean initialized)
		{
			this.initialized = initialized;
		}

		private boolean isUseOutsideOfScope()
		{
			return useOutsideOfScope;
		}

		private void setUseOutsideOfScope(boolean useOutsideOfScope)
		{
			this.useOutsideOfScope = useOutsideOfScope;
		}

		private boolean isDuplicated()
		{
			return duplicated;
		}

		private void setDuplicated(boolean duplicated)
		{
			this.duplicated = duplicated;
		}
	}
	
	private static class Usage
	{
		private List<Token> modified;
		private List<Token> reassigned;
		private List<Token> tokens;
		
		private boolean onlyUsedSubFunction = false;
		
		private Usage()
		{
			this.modified = new ArrayList<Token>();
			this.reassigned = new ArrayList<Token>();
			this.tokens = new ArrayList<Token>();
		}
		
		private List<Token> getModified()
		{
			return modified;
		}
		
		private List<Token> getReassigned()
		{
			return reassigned;
		}
		
		private List<Token> getTokens()
		{
			return tokens;
		}
		
		private boolean isOnlyUsedSubFunction()
		{
			return onlyUsedSubFunction;
		}
		
		private void setOnlyUsedSubFunction(boolean onlyUsedSubFunction)
		{
			this.onlyUsedSubFunction = onlyUsedSubFunction;
		}
	}
}