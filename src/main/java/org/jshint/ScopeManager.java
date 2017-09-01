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

import org.apache.commons.lang3.StringUtils;
import org.jshint.utils.EventEmitter;
import org.jshint.utils.EventContext;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

/**
 * Creates a scope manager that handles variables and labels, storing usages
 * and resolving when variables are used and undefined
 */
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
	 * Checks the current scope for unused identifiers
	 */
	private void checkForUnused()
	{
		// function params are handled specially
	    // assume that parameters are the only thing declared in the param scope
		if (current.getType().equals("functionparams"))
		{
			checkParams();
			return;
		}
		Map<String, Label> curentLabels = current.getLabels();
		for (String labelName : curentLabels.keySet())
		{
			if (curentLabels.containsKey(labelName))
			{
				if (!curentLabels.get(labelName).getType().equals("exception") &&
					curentLabels.get(labelName).isUnused())
				{
					warnUnused(labelName, curentLabels.get(labelName).getToken(), "var");
				}
			}
		}
	}
	
	/**
	 * Checks the current scope for unused parameters
	 * Must be called in a function parameter scope
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
			
			// 'undefined' is a special case for (function(window, undefined) { ... })();
		    // patterns.
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
	 * Finds the relevant label's scope, searching from nearest outwards
	 * @returns {Object} the scope the label was found in
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
	
	private boolean usedSoFarInCurrentFunction(String labelName)
	{
		// used so far in this whole function and any sub functions
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
			// if either latedef is strict and this is a function
		    //    or this is not a function
			if ((State.getOption().get("latedef").equals(true) && type.equals("function")) ||
				!type.equals("function"))
			{
				warning("W003", token, labelName);
			}
		}
	}
	
	public void on(String names, LexerEventListener listener)
	{
		for (String name : names.split(" "))
		{
			emitter.on(name, listener);
		}
	}
	
	public boolean isPredefined(String labelName)
	{
		return !has(labelName) && scopeStack.get(0).getPredefined().containsKey(labelName);
	}
	
	public void stack()
	{
		stack(null);
	}
	
	/**
     * Tell the manager we are entering a new block of code.
     * 
     * @param type The type of the block. Valid values are "functionparams", "catchparams" and "functionouter".
     */
	public void stack(String type)
	{
		Scope previousScope = current;
		newScope(type);
		
		if (StringUtils.isEmpty(type) && previousScope.getType().equals("functionparams"))
		{
			current.setFuncBody(true);
			//current.setContext(currentFunctBody); //JSHINT_BUG: property "(context)" is not used anywhere, can be removed
			currentFunctBody = current;
		}
	}
	
	public void unstack()
	{
		Scope subScope = scopeStack.size() > 1 ? scopeStack.get(scopeStack.size() - 2) : null;
		boolean isUnstackingFunctionBody = current == currentFunctBody;
		boolean isUnstackingFunctionParams = current.getType().equals("functionparams");
		boolean isUnstackingFunctionOuter = current.getType().equals("functionouter");
		
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
				boolean isImmutable = usedLabelType.equals("const") || usedLabelType.equals("import");
				
				if (usedLabel.isUseOutsideOfScope() && !State.getOption().test("funcscope"))
				{
					List<Token> usedTokens = usage.getTokens();
					if (usedTokens != null)
					{
						for (int j = 0; j < usedTokens.size(); j++)
						{
							// Keep the consistency of https://github.com/jshint/jshint/issues/2409
							if (usedLabel.getFunction() == usedTokens.get(j).getFunction())
							{
								error("W038", usedTokens.get(j), usedLabelName);
							}
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
				
				// check for re-assigning a function declaration
				if ((usedLabelType.equals("function") || usedLabelType.equals("class")) &&
					usage.getReassigned() != null)
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
			
			if (isUnstackingFunctionOuter)
			{
				State.getFunct().setCapturing(true);
			}
			
			if (subScope != null)
			{
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
					if (usage.getTokens() != null)
					{
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
     * Add a param to the current scope.
     * 
     * @param labelName name of the label
     * @param token current token
     */
	public void addParam(String labelName, Token token)
	{
		addParam(labelName, token, null);
	}
	
	/**
     * Add a param to the current scope.
     * 
     * @param labelName name of the label
     * @param token current token
     * @param type type of the parameter token
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
			checkOuterShadow(labelName, token); //JSHINT_BUG: third parameter is not defined in function checkOuterShadow
			
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
	
	public void validateParams()
	{
		// This method only concerns errors for function parameters
		if (currentFunctBody.getType().equals("global"))
		{
			return;
		}
		
		boolean isStrict = State.isStrict();
		Scope currentFunctParamScope = currentFunctBody.getParent();
		
		if (currentFunctParamScope.getParams() == null)
		{
			return;
		}
		
		for (String labelName : currentFunctParamScope.getParams())
		{
			Label label = currentFunctParamScope.getLabels().get(labelName);
			
			if (label != null && label.isDuplicated())
			{
				if (isStrict)
				{
					warning("E011", label.getToken(), labelName);
				}
				else if (!State.getOption().get("shadow").equals(true))
				{
					warning("W004", label.getToken(), labelName);
				}
			}
		}
	}
	
	public Set<String> getUsedOrDefinedGlobals()
	{
		return usedPredefinedAndGlobals.keys();
	}
	
	/**
     * Gets an array of implied globals.
     * 
     * @return list of globals.
     */
	public List<ImpliedGlobal> getImpliedGlobals()
	{
		return Collections.unmodifiableList(new ArrayList<ImpliedGlobal>(impliedGlobals.values()));
	}
	
	/**
     * Returns a list of unused variables.
     * 
     * @return list of unused variables.
     */
	public List<Token> getUnuseds()
	{
		return Collections.unmodifiableList(unuseds);
	}
	
	public boolean has(String labelName)
	{
		return getLabel(labelName) != null;
	}
	
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
     * For the exported options, indicating a variable is used outside the file.
     * 
     * @param labelName name of the label.
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
     * Mark an indentifier as es6 module exported.
     * 
     * @param labelName name of the label
     * @param token current token
     */
	public void setExported(String labelName, Token token)
	{
		block.use(labelName, token);
	}
	
	public void initialize(String labelName)
	{
		if (current.getLabels().containsKey(labelName))
		{
			current.getLabels().get(labelName).setInitialized(true);
		}
	}
	
	/**
     * Adds an indentifier to the relevant current scope and creates warnings/errors as necessary.
     * 
     * @param labelName name of the label
     * @param type the type of the label e.g. "param", "var", "let, "const", "import", "function".
     * @param token the token pointing at the declaration.
     */
	public void addlabel(String labelName, String type, Token token)
	{
		addlabel(labelName, type, false, token);
	}
	
	/**
     * Adds an indentifier to the relevant current scope and creates warnings/errors as necessary.
     * 
     * @param labelName name of the label
     * @param type the type of the label e.g. "param", "var", "let, "const", "import", "function".
     * @param initialized whether the binding should be created in an "initialized" state.
     * @param token the token pointing at the declaration.
     */
	public void addlabel(String labelName, String type, boolean initialized, Token token)
	{
		boolean isblockscoped = type.equals("let") || type.equals("const") ||
			type.equals("class") || type.equals("import");
		boolean ishoisted = type.equals("function") || type.equals("import");
		boolean isexported = (isblockscoped ? current.getType().equals("global") : currentFunctBody.getType().equals("global")) && 
							 exported.containsKey(labelName);
		
		// outer shadow check (inner is only on non-block scoped)
		checkOuterShadow(labelName, token); //JSHINT_BUG: third parameter is not defined in function checkOuterShadow
		
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
			
			// if this scope has the variable defined, its a re-definition error
			if (declaredInCurrentScope != null)
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
	     * Returns the label type given certain options.
	     * 
	     * @param labelName name of the label.
	     * @param onlyBlockscoped only include block scoped labels.
	     * @param excludeParams exclude the param scope.
	     * @param excludeCurrent exclude the current scope.
	     * @return label type string
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
	     * Returns if a break label exists in the function scope.
	     * 
	     * @param labelName name of the label.
	     * @return true if break label exists, false otherwise.
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
	     * Returns if the label is in the current function scope.
	     * 
	     * @param labelName name of the label.
	     * @param onlyBlockscoped only include block scoped labels.
	     * @param excludeParams exclude the param scope.
	     * @param excludeCurrent exclude the current scope.
	     * @return true if label in the current scope, false otherwise.
	     * 
	     * @see #labeltype(String, boolean, boolean, boolean) for options
	     */
		public boolean has(String labelName, boolean onlyBlockscoped, boolean excludeParams, boolean excludeCurrent)
		{
			return StringUtils.isNotEmpty(labeltype(labelName, onlyBlockscoped, excludeParams, excludeCurrent));
		}
		
		/**
	     * Adds a new function scoped variable.
	     * 
	     * @param labelName name of the label.
	     * @param type type of the label.
	     * @param tok current token.
	     * @param unused whether variable is unused or not.
	     * 
	     * @see Block#add(String, String, Token, boolean) for block scoped.
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
	     * Is the current block global?
	     * 
	     * @return true if block is global, false otherwise.
	     */
		public boolean isGlobal()
		{
			return current.getType().equals("global");
		}
		
		public void use(String labelName, Token token)
		{
			// if resolves to current function params, then do not store usage just resolve
	        // this is because function(a) { var a; a = a; } will resolve to the param, not
	        // to the unset var
	        // first check the param is used
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
			
			// blockscoped vars can't be used within their initializer (TDZ)
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
	     * Adds a new variable.
	     * 
	     * @param labelName name of the label.
	     * @param type type of the label.
	     * @param tok current token.
	     * @param unused whether label is unused or not.
	     */
		public void add(String labelName, String type, Token tok, boolean unused)
		{
			add(labelName, type, tok, unused, false);
		}
		
		/**
	     * Adds a new variable.
	     * 
	     * @param labelName name of the label.
	     * @param type type of the label.
	     * @param tok current token.
	     * @param unused whether label is unused or not.
	     * @param initialized whether label is initialized or not.
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