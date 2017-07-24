package org.jshint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jshint.utils.ConsumerFunction;
import org.jshint.utils.EventContext;
import com.github.jshaptic.js4j.UniversalContainer;

public class Token
{
	private String id = "";
	private String value = "";
	private TokenType type = null;
	private String name = "";
	private TokenArityType arity = null;
	private String raw_text = "";
	private String body = "";
	private String accessorType = "";
	private LtBoundaryType ltBoundary = null;
	
	private int lbp = 0; // Left binding power
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
	private boolean isDelim = false;
	private boolean isNamedExpr = false;
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
	
	private boolean ignoreUndef = false;
	private boolean ignoreW020 = false;
	private boolean ignoreW021 = false;
	
	private Token left = null;
	private Token right = null;
	private Token tag = null;
	private List<Token> exprs = null;
	private List<Token> cases = null;
	private List<Token> firstTokens = null;
	private List<JSHint.Identifier> destructAssign = null;
	private ScopeManager.Scope function = null;
	private Lexer.LexerContext context = null;
	
	private boolean isFunctor = false;
	
	private Meta meta = null;
	private NudFunction nud = null; // Null denotation
	private FudFunction fud = null; // First null denotation
	private LedFunction led = null; // Left denotation
	private ConsumerFunction check = null;
	
	public Token()
	{
		
	}
	
	public Token(TokenType type)
	{
		setType(type);
	}
	
	public Token(String id, int lbp, String value)
	{
		setId(id);
		setLbp(lbp);
		setValue(value);
	}
	
	public Token(String name, int line, int character)
	{
		setName(name);
		setLine(line);
		setCharacter(character);
	}
	
	public Token(Token original)
	{
		setId(original.getId());
		setValue(original.getValue());
		setType(original.getType());
		setName(original.getName());
		setArity(original.getArity());
		setRawText(original.getRawText());
		setBody(original.getBody());
		setAccessorType(original.getAccessorType());
		setLtBoundary(original.getLtBoundary());
		
		setLbp(original.getLbp());
		setStartLine(original.getStartLine());
		setLine(original.getLine());
		setFrom(original.getFrom());
		setCharacter(original.getCharacter());
		setDepth(original.getDepth());
		
		setIdentifier(original.isIdentifier());
		setInfix(original.isInfix());
		setBlock(original.isBlock());
		setAssign(original.isAssign());
		setExps(original.isExps());
		setImmed(original.isImmed());
		setParen(original.isParen());
		setInBracelessBlock(original.isInBracelessBlock());
		setForgiveUndef(original.isForgiveUndef());
		setReach(original.isReach());
		setBeginsStmt(original.isBeginsStmt());
		setDelim(original.isDelim());
		setNamedExpr(original.isNamedExpr());
		setNoSubst(original.isNoSubst());
		setTemplate(original.isTemplate());
		setTail(original.isTail());
		setUnclosed(original.isUnclosed());
		setCaseFallsThrough(original.isCaseFallsThrough());
		setReserved(original.isReserved());
		setLabelled(original.isLabelled());
		setSpecial(original.isSpecial());
		setProperty(original.isProperty());
		setMetaProperty(original.isMetaProperty());
		
		setIgnoreUndef(original.isIgnoreUndef());
		setIgnoreW020(original.isIgnoreW020());
		setIgnoreW021(original.isIgnoreW021());
		
		setLeft(original.getLeft());
		setRight(original.getRight());
		setTag(original.getTag());
		setExprs(original.getExprs());
		setCases(original.getCases());
		setFirstTokens(original.getFirstTokens());
		setDestructAssign(original.getDestructAssign());
		setFunction(original.getFunction());
		setContext(original.getContext());
		
		setFunctor(original.isFunctor());
		
		setMeta(original.getMeta());
		setNud(original.getNud());
		setFud(original.getFud());
		setLed(original.getLed());
		setCheck(original.getCheck());
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
	
	Token(JSHint.Functor functor)
	{
		setFunctor(true);
	}
	
	Token(JSHint.Identifier identifier)
	{
		setId(identifier.getId());
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

	public TokenType getType()
	{
		return type;
	}

	public void setType(TokenType type)
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

	public TokenArityType getArity()
	{
		return arity;
	}

	public void setArity(TokenArityType arity)
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

	public LtBoundaryType getLtBoundary()
	{
		return ltBoundary;
	}

	public void setLtBoundary(LtBoundaryType ltBoundary)
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

	public boolean isInBracelessBlock()
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

	public boolean isNamedExpr()
	{
		return isNamedExpr;
	}

	public void setNamedExpr(boolean isNamedExpr)
	{
		this.isNamedExpr = isNamedExpr;
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
	
	public List<JSHint.Identifier> getDestructAssign()
	{
		return destructAssign;
	}

	public void setDestructAssign(List<JSHint.Identifier> destructAssign)
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
        return new HashCodeBuilder(17, 31) // two randomly chosen prime numbers
            .append(id)
            .append(value)
            .append(type)
            .append(name)
            .append(arity)
            .append(raw_text)
            .append(body)
            .append(accessorType)
            .append(lbp)
            .append(startLine)
            .append(line)
            .append(from)
            .append(character)
            .append(depth)
            .append(isIdentifier)
            .append(isInfix)
            .append(isBlock)
            .append(isAssign)
            .append(isExps)
            .append(isImmed)
            .append(isParen)
            .append(inBracelessBlock)
            .append(isReach)
            .append(isBeginsStmt)
            .append(isDelim)
            .append(isNamedExpr)
            .append(isNoSubst)
            .append(isTemplate)
            .append(isTail)
            .append(isUnclosed)
            .append(isCaseFallsThrough)
            .append(isReserved)
            .append(isLabelled)
            .append(isSpecial)
            .append(isProperty)
            .append(isMetaProperty)
            .append(ignoreUndef)
            .append(ignoreW020)
            .append(ignoreW021)
            .append(left)
            .append(right)
            .append(exprs)
            .append(cases)
            .append(firstTokens)
            .append(destructAssign)
            .append(function)
            .append(context)
            .toHashCode();
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
			.append(this.lbp, other.lbp)
			.append(this.startLine, other.startLine)
			.append(this.line, other.line)
			.append(this.from, other.from)
			.append(this.character, other.character)
			.append(this.depth, other.depth)
			.append(this.isIdentifier, other.isIdentifier)
			.append(this.isInfix, other.isInfix)
			.append(this.isBlock, other.isBlock)
			.append(this.isAssign, other.isAssign)
			.append(this.isExps, other.isExps)
			.append(this.isImmed, other.isImmed)
			.append(this.isParen, other.isParen)
			.append(this.inBracelessBlock, other.inBracelessBlock)
			.append(this.isReach, other.isReach)
			.append(this.isBeginsStmt, other.isBeginsStmt)
			.append(this.isDelim, other.isDelim)
			.append(this.isNamedExpr, other.isNamedExpr)
			.append(this.isNoSubst, other.isNoSubst)
			.append(this.isTemplate, other.isTemplate)
			.append(this.isTail, other.isTail)
			.append(this.isUnclosed, other.isUnclosed)
			.append(this.isCaseFallsThrough, other.isCaseFallsThrough)
			.append(this.isReserved, other.isReserved)
			.append(this.isLabelled, other.isLabelled)
			.append(this.isSpecial, other.isSpecial)
			.append(this.isProperty, other.isProperty)
			.append(this.isMetaProperty, other.isMetaProperty)
			.append(this.ignoreUndef, other.ignoreUndef)
			.append(this.ignoreW020, other.ignoreW020)
			.append(this.ignoreW021, other.ignoreW021)
			.append(this.left, other.left)
			.append(this.right, other.right)
			.append(this.exprs, other.exprs)
			.append(this.cases, other.cases)
			.append(this.firstTokens, other.firstTokens)
			.append(this.destructAssign, other.destructAssign)
			.append(this.function, other.function)
			.append(this.context, other.context)
			.isEquals();
	}
	
	boolean isFunctor()
	{
		return isFunctor;
	}
	
	void setFunctor(boolean isFunctor)
	{
		this.isFunctor = isFunctor;
	}

	Meta getMeta()
	{
		return meta;
	}

	void setMeta(Meta meta)
	{
		this.meta = meta;
	}

	NudFunction getNud()
	{
		return nud;
	}

	void setNud(NudFunction nud)
	{
		this.nud = nud;
	}

	FudFunction getFud()
	{
		return fud;
	}

	void setFud(FudFunction fud)
	{
		this.fud = fud;
	}

	LedFunction getLed()
	{
		return led;
	}

	void setLed(LedFunction led)
	{
		this.led = led;
	}

	ConsumerFunction getCheck()
	{
		return check;
	}

	void setCheck(ConsumerFunction check)
	{
		this.check = check;
	}
	
	static interface NudFunction
	{
		public Token apply(Token _this, int rbp) throws JSHintException;
	}
	
	static interface NudInnerFunction
	{
		public void apply(Token x) throws JSHintException;
	}
	
	Token nud(int rbp) throws JSHintException
	{
		return nud != null ? nud.apply(this, rbp) : null;
	}
	
	static interface FudFunction
	{
		public Token apply(Token _this, UniversalContainer context) throws JSHintException;
	}
	
	Token fud(UniversalContainer context) throws JSHintException
	{
		return fud != null ? fud.apply(this, context) : null;
	}
	
	static interface LedFunction
	{
		public Token apply(Token _this, Token t) throws JSHintException;
	}
	
	static interface LedInnerFunction
	{
		public Token apply(Token _this, Token left, Token right) throws JSHintException;
	}
	
	Token led(Token t) throws JSHintException
	{
		return led != null ? led.apply(this, t) : null;
	}
	
	void check() throws JSHintException
	{
		if (check != null) check.accept();
	}
	
	static class Meta
	{
		private boolean isFutureReservedWord = false;
		private boolean es5 = false;
		private boolean strictOnly = false;
		private boolean moduleOnly = false;
		private NudFunction nud = null;
		
		Meta()
		{
			
		}
		
		Meta(boolean es5, boolean strictOnly, boolean moduleOnly, NudFunction nud)
		{
			setES5(es5);
			setStrictOnly(strictOnly);
			setModuleOnly(moduleOnly);
			setNud(nud);
		}

		boolean isFutureReservedWord()
		{
			return isFutureReservedWord;
		}

		void setFutureReservedWord(boolean isFutureReservedWord)
		{
			this.isFutureReservedWord = isFutureReservedWord;
		}

		boolean isES5()
		{
			return es5;
		}

		void setES5(boolean es5)
		{
			this.es5 = es5;
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

		NudFunction getNud()
		{
			return nud;
		}

		void setNud(NudFunction nud)
		{
			this.nud = nud;
		}
	}
}