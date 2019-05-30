package org.jshint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jshint.utils.EventContext;

public final class Token implements Cloneable
{
	private String id = "";
	private String value = "";
	private Type type = null;
	private String name = "";
	private ArityType arity = null;
	private String raw_text = "";
	private String body = "";
	private String accessorType = "";
	private BoundaryType ltBoundary = null;
	
	private int lbp = 0; // Left binding power
	private int rbp = 0; // Right binding power
	private int startLine = 0;
	private int line = 0;
	private int from = 0;
	private int character = 0;
	private int depth = 0;
	
	private boolean isIdentifier = false;
	private boolean isInfix = false;
	private boolean isBlock = false;
	private boolean isAssign = false;
	private boolean isExps = false;
	private boolean isImmed = false;
	private boolean isParen = false;
	private boolean inBracelessBlock = false;
	private boolean isForgiveUndef = false;
	private boolean isReach = false;
	private boolean isBeginsStmt = false;
	private boolean isDelim = false; //JSHINT_BUG: this property only used to write values, not to read can be removed
	private boolean isNoSubst = false;
	private boolean isTemplate = false;
	private boolean isTail = false;
	private boolean isUnclosed = false;
	private boolean isCaseFallsThrough = false;
	private boolean isReserved = false;
	private boolean isLabelled = false;
	private boolean isSpecial = false;
	private boolean isProperty = false;
	private boolean isMetaProperty = false;
	private boolean isDeclaration = false;
	private boolean hasComma  = false;
	private boolean hasInitializer  = false;
	private boolean isFunctor = false;
	
	private boolean ignoreUndef = false;
	private boolean ignoreW020 = false;
	private boolean ignoreW021 = false;
	
	private Token left = null;
	private Token right = null;
	private Token tag = null;
	private Token token = null;
	private List<Token> exprs = null;
	private List<Token> cases = null;
	private List<Token> firstTokens = null;
	private List<Token> destructAssign = null;
	private ScopeManager.Scope function = null;
	private Lexer.LexerContext context = null;
	
	private Meta meta = null;
	private Function<Token, IntFunction<IntFunction<Token>>> nud = null; // Null denotation
	private Function<Token, IntFunction<Token>> fud = null; // First null denotation
	private Function<Token, IntFunction<Function<Token, Token>>> led = null; // Left denotation
	private Function<Token, IntPredicate> useFud = null;
	private Function<Token, IntPredicate> isFunc = null;
	private Runnable check = null;
	
	public Token()
	{
		
	}
	
	public Token(Type type)
	{
		setType(type);
	}
	
	public Token(String id, int lbp, int rpb, String value)
	{
		setId(id);
		setLbp(lbp);
		setRbp(rpb);
		setValue(value);
	}
	
	public Token(String name, int line, int character)
	{
		setName(name);
		setLine(line);
		setCharacter(character);
	}
	
	Token(EventContext context)
	{
		setValue(context.getValue());
		setName(context.getName());
		setStartLine(context.getStartLine());
		setLine(context.getLine());
		setFrom(context.getFrom());
		setCharacter(context.getCharacter());
		setProperty(context.isProperty());
	}
	
	Token(String id, Token token)
	{
		setId(id);
		setToken(token);
	}
	
	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = StringUtils.defaultString(id);
	}

	public String getValue()
	{
		return value;
	}

	public void setValue(String value)
	{
		this.value = StringUtils.defaultString(value);
	}

	public Type getType()
	{
		return type;
	}

	public void setType(Type type)
	{
		this.type = type;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = StringUtils.defaultString(name);
	}

	public ArityType getArity()
	{
		return arity;
	}

	public void setArity(ArityType arity)
	{
		this.arity = arity;
	}

	public String getRawText()
	{
		return raw_text;
	}

	public void setRawText(String raw_text)
	{
		this.raw_text = StringUtils.defaultString(raw_text);
	}

	public String getBody()
	{
		return body;
	}

	public void setBody(String body)
	{
		this.body = StringUtils.defaultString(body);
	}

	public String getAccessorType()
	{
		return accessorType;
	}

	public void setAccessorType(String accessorType)
	{
		this.accessorType = StringUtils.defaultString(accessorType);
	}

	public BoundaryType getLtBoundary()
	{
		return ltBoundary;
	}

	public void setLtBoundary(BoundaryType ltBoundary)
	{
		this.ltBoundary = ltBoundary;
	}

	public int getLbp()
	{
		return lbp;
	}

	public void setLbp(int lbp)
	{
		this.lbp = lbp;
	}
	
	public int getRbp()
	{
		return rbp;
	}

	public void setRbp(int rbp)
	{
		this.rbp = rbp;
	}

	public int getStartLine()
	{
		return startLine;
	}

	public void setStartLine(int startLine)
	{
		this.startLine = startLine;
	}

	public int getLine()
	{
		return line;
	}

	public void setLine(int line)
	{
		this.line = line;
	}

	public int getFrom()
	{
		return from;
	}

	public void setFrom(int from)
	{
		this.from = from;
	}

	public int getCharacter()
	{
		return character;
	}

	public void setCharacter(int character)
	{
		this.character = character;
	}

	public int getDepth()
	{
		return depth;
	}

	public void setDepth(int depth)
	{
		this.depth = depth;
	}

	public boolean isIdentifier()
	{
		return isIdentifier;
	}

	public void setIdentifier(boolean isIdentifier)
	{
		this.isIdentifier = isIdentifier;
	}

	public boolean isInfix()
	{
		return isInfix;
	}

	public void setInfix(boolean isInfix)
	{
		this.isInfix = isInfix;
	}

	public boolean isBlock()
	{
		return isBlock;
	}

	public void setBlock(boolean isBlock)
	{
		this.isBlock = isBlock;
	}

	public boolean isAssign()
	{
		return isAssign;
	}

	public void setAssign(boolean isAssign)
	{
		this.isAssign = isAssign;
	}

	public boolean isExps()
	{
		return isExps;
	}

	public void setExps(boolean isExps)
	{
		this.isExps = isExps;
	}

	public boolean isImmed()
	{
		return isImmed;
	}

	public void setImmed(boolean isImmed)
	{
		this.isImmed = isImmed;
	}

	public boolean isParen()
	{
		return isParen;
	}

	public void setParen(boolean isParen)
	{
		this.isParen = isParen;
	}

	public boolean inBracelessBlock()
	{
		return inBracelessBlock;
	}

	public void setInBracelessBlock(boolean inBracelessBlock)
	{
		this.inBracelessBlock = inBracelessBlock;
	}

	public boolean isForgiveUndef()
	{
		return isForgiveUndef;
	}

	public void setForgiveUndef(boolean isForgiveUndef)
	{
		this.isForgiveUndef = isForgiveUndef;
	}

	public boolean isReach()
	{
		return isReach;
	}

	public void setReach(boolean isReach)
	{
		this.isReach = isReach;
	}

	public boolean isBeginsStmt()
	{
		return isBeginsStmt;
	}

	public void setBeginsStmt(boolean isBeginsStmt)
	{
		this.isBeginsStmt = isBeginsStmt;
	}

	public boolean isDelim()
	{
		return isDelim;
	}

	public void setDelim(boolean isDelim)
	{
		this.isDelim = isDelim;
	}

	public boolean isNoSubst()
	{
		return isNoSubst;
	}

	public void setNoSubst(boolean isNoSubst)
	{
		this.isNoSubst = isNoSubst;
	}

	public boolean isTemplate()
	{
		return isTemplate;
	}

	public void setTemplate(boolean isTemplate)
	{
		this.isTemplate = isTemplate;
	}

	public boolean isTail()
	{
		return isTail;
	}

	public void setTail(boolean isTail)
	{
		this.isTail = isTail;
	}

	public boolean isUnclosed()
	{
		return isUnclosed;
	}

	public void setUnclosed(boolean isUnclosed)
	{
		this.isUnclosed = isUnclosed;
	}

	public boolean isCaseFallsThrough()
	{
		return isCaseFallsThrough;
	}

	public void setCaseFallsThrough(boolean isCaseFallsThrough)
	{
		this.isCaseFallsThrough = isCaseFallsThrough;
	}

	public boolean isReserved()
	{
		return isReserved;
	}

	public void setReserved(boolean isReserved)
	{
		this.isReserved = isReserved;
	}

	public boolean isLabelled()
	{
		return isLabelled;
	}

	public void setLabelled(boolean isLabelled)
	{
		this.isLabelled = isLabelled;
	}

	public boolean isSpecial()
	{
		return isSpecial;
	}

	public void setSpecial(boolean isSpecial)
	{
		this.isSpecial = isSpecial;
	}

	public boolean isProperty()
	{
		return isProperty;
	}

	public void setProperty(boolean isProperty)
	{
		this.isProperty = isProperty;
	}

	public boolean isMetaProperty()
	{
		return isMetaProperty;
	}

	public void setMetaProperty(boolean isMetaProperty)
	{
		this.isMetaProperty = isMetaProperty;
	}
	
	public boolean isDeclaration()
	{
		return isDeclaration;
	}

	public void setDeclaration(boolean isDeclaration)
	{
		this.isDeclaration = isDeclaration;
	}
	
	public boolean hasComma()
	{
		return hasComma;
	}

	public void setHasComma(boolean hasComma)
	{
		this.hasComma = hasComma;
	}
	
	public boolean hasInitializer()
	{
		return hasInitializer;
	}

	public void setHasInitializer(boolean hasInitializer)
	{
		this.hasInitializer = hasInitializer;
	}
	
	public boolean isFunctor()
	{
		return isFunctor;
	}
	
	public void setFunctor(boolean isFunctor)
	{
		this.isFunctor = isFunctor;
	}

	public boolean isIgnoreUndef()
	{
		return ignoreUndef;
	}

	public void setIgnoreUndef(boolean ignoreUndef)
	{
		this.ignoreUndef = ignoreUndef;
	}

	public boolean isIgnoreW020()
	{
		return ignoreW020;
	}

	public void setIgnoreW020(boolean ignoreW020)
	{
		this.ignoreW020 = ignoreW020;
	}

	public boolean isIgnoreW021()
	{
		return ignoreW021;
	}

	public void setIgnoreW021(boolean ignoreW021)
	{
		this.ignoreW021 = ignoreW021;
	}

	public Token getLeft()
	{
		return left;
	}

	public void setLeft(Token left)
	{
		this.left = left;
	}

	public Token getRight()
	{
		return right;
	}

	public void setRight(Token right)
	{
		this.right = right;
	}

	public Token getTag()
	{
		return tag;
	}

	public void setTag(Token tag)
	{
		this.tag = tag;
	}
	
	public Token getToken()
	{
		return token;
	}

	public void setToken(Token token)
	{
		this.token = token;
	}

	public List<Token> getExprs()
	{
		return exprs;
	}

	public void setExprs(List<Token> exprs)
	{
		this.exprs = exprs;
	}

	public List<Token> getCases()
	{
		return cases;
	}

	public void setCases(List<Token> cases)
	{
		this.cases = cases;
	}

	public Token getFirstToken()
	{
		return firstTokens != null && firstTokens.size() == 1 ? firstTokens.get(0) : null; 
	}
	
	public List<Token> getFirstTokens()
	{
		return firstTokens != null ? Collections.unmodifiableList(firstTokens) : Collections.<Token>emptyList();
	}
	
	public void setFirstTokens(Token... tokens)
	{
		firstTokens = tokens != null ? new ArrayList<Token>(Arrays.asList(tokens)) : new ArrayList<Token>();
	}
	
	public void setFirstTokens(List<Token> tokens)
	{
		firstTokens = tokens != null ? new ArrayList<Token>(tokens) : new ArrayList<Token>();
	}
	
	public void addFirstTokens(Token... tokens)
	{
		if (firstTokens == null)
		{
			setFirstTokens(tokens);
		}
		else if (tokens != null)
		{
			firstTokens.addAll(Arrays.asList(tokens));
		}
	}
	
	public void addFirstTokens(List<Token> tokens)
	{
		if (firstTokens == null)
		{
			setFirstTokens(tokens);
		}
		else if (tokens != null)
		{
			firstTokens.addAll(tokens);
		}
	}
	
	public List<Token> getDestructAssign()
	{
		return destructAssign;
	}

	public void setDestructAssign(List<Token> destructAssign)
	{
		this.destructAssign = destructAssign;
	}

	public ScopeManager.Scope getFunction()
	{
		return function;
	}

	public void setFunction(ScopeManager.Scope function)
	{
		this.function = function;
	}

	public Lexer.LexerContext getContext()
	{
		return context;
	}

	public void setContext(Lexer.LexerContext context)
	{
		this.context = context;
	}
	
	@Override
    public int hashCode()
	{
        return Objects.hash(
        	id,
        	value,
    		type,
    		name,
    		arity,
    		raw_text,
    		body,
    		accessorType,
    		ltBoundary,
	    	lbp,
    		rbp,
    		startLine,
    		line,
    		from,
    		character,
    		depth);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof Token)) return false;
		if (obj == this) return true;
		
		Token other = (Token) obj;
		return new EqualsBuilder()
			.append(this.id, other.id)
			.append(this.value, other.value)
			.append(this.type, other.type)
			.append(this.name, other.name)
			.append(this.arity, other.arity)
			.append(this.raw_text, other.raw_text)
			.append(this.body, other.body)
			.append(this.accessorType, other.accessorType)
			.append(this.ltBoundary, other.ltBoundary)
			.append(this.lbp, other.lbp)
			.append(this.rbp, other.rbp)
			.append(this.startLine, other.startLine)
			.append(this.line, other.line)
			.append(this.from, other.from)
			.append(this.character, other.character)
			.append(this.depth, other.depth)
			.isEquals();
	}
	
	@Override
	public Token clone()
	{
		try
		{
			return (Token)super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			return null;
		}
	}
	
	// PORT INFO: special setter, which is used to mark, that there is functor object in property 'right'
	void setRight(JSHint.Functor functor)
	{
		Token right = new Token();
		right.setFunctor(true);
		this.right = right;
	}

	Meta getMeta()
	{
		return meta;
	}

	void setMeta(Meta meta)
	{
		this.meta = meta;
	}
	
	static class Meta
	{
		private boolean es5 = false;
		private boolean isFutureReservedWord = false;
		private boolean strictOnly = false;
		private boolean moduleOnly = false;
		private Function<Token, IntFunction<IntFunction<Token>>> nud = null;
		
		Meta()
		{
			
		}
		
		Meta(boolean es5, boolean isFutureReservedWord, boolean strictOnly, boolean moduleOnly, Function<Token, IntFunction<IntFunction<Token>>> nud)
		{
			setES5(es5);
			setFutureReservedWord(isFutureReservedWord);
			setStrictOnly(strictOnly);
			setModuleOnly(moduleOnly);
			setNud(nud);
		}

		boolean isES5()
		{
			return es5;
		}

		void setES5(boolean es5)
		{
			this.es5 = es5;
		}
		
		boolean isFutureReservedWord()
		{
			return isFutureReservedWord;
		}

		void setFutureReservedWord(boolean isFutureReservedWord)
		{
			this.isFutureReservedWord = isFutureReservedWord;
		}

		boolean isStrictOnly()
		{
			return strictOnly;
		}

		void setStrictOnly(boolean strictOnly)
		{
			this.strictOnly = strictOnly;
		}

		boolean isModuleOnly()
		{
			return moduleOnly;
		}

		void setModuleOnly(boolean moduleOnly)
		{
			this.moduleOnly = moduleOnly;
		}

		Function<Token, IntFunction<IntFunction<Token>>> getNud()
		{
			return nud;
		}

		void setNud(Function<Token, IntFunction<IntFunction<Token>>> nud)
		{
			this.nud = nud;
		}
	}
	
	// NULL DENOTATION

	Function<Token, IntFunction<IntFunction<Token>>> getNud()
	{
		return nud;
	}

	void setNud(Function<Token, IntFunction<IntFunction<Token>>> nud)
	{
		this.nud = nud;
	}
	
	Token nud(int context, int rbp) throws JSHintException
	{
		return nud(this, context, rbp);
	}
	
	Token nud(Token _this, int context, int rbp) throws JSHintException
	{
		return nud.apply(_this).apply(context).apply(rbp);
	}
	
	// FIRST NULL DENOTATION

	Function<Token, IntFunction<Token>> getFud()
	{
		return fud;
	}

	void setFud(Function<Token, IntFunction<Token>> fud)
	{
		this.fud = fud;
	}
	
	Token fud(int context) throws JSHintException
	{
		return fud.apply(this).apply(context);
	}
	
	// LEFT DENOTATION

	Function<Token, IntFunction<Function<Token, Token>>> getLed()
	{
		return led;
	}

	void setLed(Function<Token, IntFunction<Function<Token, Token>>> led)
	{
		this.led = led;
	}
	
	Token led(int context, Token t) throws JSHintException
	{
		return led.apply(this).apply(context).apply(t);
	}
	
	// USE FIRST NULL DENOTATION
	
	Function<Token, IntPredicate> getUseFud()
	{
		return useFud;
	}

	void setUseFud(Function<Token, IntPredicate> useFud)
	{
		this.useFud = useFud;
	}
	
	boolean useFud(int context) throws JSHintException
	{
		return useFud.apply(this).test(context);
	}
	
	// IS FUNCTION
	
	Function<Token, IntPredicate> getIsFunc()
	{
		return isFunc;
	}

	void setIsFunc(Function<Token, IntPredicate> isFunc)
	{
		this.isFunc = isFunc;
	}
	
	boolean isFunc(int context) throws JSHintException
	{
		return isFunc.apply(this).test(context);
	}

	Runnable getCheck()
	{
		return check;
	}

	void setCheck(Runnable check)
	{
		this.check = check;
	}
	
	void check() throws JSHintException
	{
		if (check != null) check.run();
	}
	
	public static enum Type
	{
		NONE("(none)"),
		IDENTIFIER("(identifier)"),
		PUNCTUATOR("(punctuator)"),
		STRING("(string)"),
		NUMBER("(number)"),
		NEGATIVE("(negative)"),
		POSITIVE("(positive)"),
		NEGATIVE_WITH_CONTINUE("(negative-with-continue)"),
		TEMPLATE("(template)"),
		TEMPLATEMIDDLE("(template middle)"),
		TEMPLATETAIL("(template tail)"),
		NOSUBSTTEMPLATE("(no subst template)"),
		REGEXP("(regexp)"),
		ENDLINE("(endline)"),
		END("(end)"),
		
		UNDEFINED("undefined"),
		NULL("null"),
		TRUE("true"),
		FALSE("false"),
		
		ABSTRACT("abstract"),
		ARGUMENTS("arguments"),
		AWAIT("await"),
		BOOLEAN("boolean"),
		BYTE("byte"),
		CASE("case"),
		CATCH("catch"),
		CHAR("char"),
		CLASS("class"),
		DEFAULT("default"),
		DOUBLE("double"),
		ELSE("else"),
		ENUM("enum"),
		EVAL("eval"),
		EXPORT("export"),
		EXTENDS("extends"),
		FINAL("final"),
		FINALLY("finally"),
		FLOAT("float"),
		GOTO("goto"),
		IMPLEMENTS("implements"),
		IMPORT("import"),
		INFINITY("Infinity"),
		INT("int"),
		INTERFACE("interface"),
		LONG("long"),
		NATIVE("native"),
		PACKAGE("package"),
		PRIVATE("private"),
		PROTECTED("protected"),
		PUBLIC("public"),
		SHORT("short"),
		STATIC("static"),
		SUPER("super"),
		SYNCHRONIZED("synchronized"),
		THIS("this"),
		TRANSIENT("transient"),
		VOLATILE("volatile"),
		
		PLAIN("plain"),
		JSLINT("jslint"),
		JSHINT("jshint"),
		JSHINT_UNSTABLE("jshint.unstable"),
		GLOBALS("globals"),
		MEMBERS("members"),
		EXPORTED("exported"),
		FALLS_THROUGH("falls through");
		
		private final String value;
		
		Type(String value)
		{
			this.value = value;
		}
		
		@Override
		public String toString()
		{
			return value;
		}
		
		public static Type fromString(String value)
		{
			for (Type t : Type.values())
			{
	            if (t.value.equals(value))
	            {
	                return t;
	            }
	        }
			
			throw new IllegalArgumentException("No token type was found, which corresponds to string " + value);
		}
	}
	
	public static enum ArityType
	{
		UNARY
	}
	
	public enum BoundaryType
	{
		BEFORE,
		AFTER
	}
}