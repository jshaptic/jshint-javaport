package org.jshint;

/*
 * Lexical analysis and token construction.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.data.NonAsciiIdentifierPartTable;
import org.jshint.data.NonAsciiIdentifierStartTable;
import org.jshint.data.UnicodeData;
import org.jshint.utils.EventEmitter;
import org.jshint.utils.ConsumerFunction;
import org.jshint.utils.EventContext;
import org.jshint.utils.PredicateFunction;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

/*
 * Lexer for JSHint.
 *
 * This object does a char-by-char scan of the provided source code
 * and produces a sequence of tokens.
 *
 *   var lex = new Lexer("var i = 0;");
 *   lex.start();
 *   lex.token(); // returns the next token
 *
 * You have to use the token() method to move the lexer forward
 * but you don't have to use its return value to get tokens. In addition
 * to token() method returning the next token, the Lexer object also
 * emits events.
 *
 *   lex.on("Identifier", function (data) {
 *     if (data.name.indexOf("_") >= 0) {
 *       // Produce a warning.
 *     }
 *   });
 *
 * Note that the token() method returns tokens in a JSLint-compatible
 * format while the event emitter uses a slightly modified version of
 * Mozilla's JavaScript Parser API. Eventually, we will move away from
 * JSLint format.
 */
public class Lexer
{	
	// Some of these token types are from JavaScript Parser API
	// while others are specific to JSHint parser.
	// JS Parser API: https://developer.mozilla.org/en-US/docs/SpiderMonkey/Parser_API
	private static enum LexerTokenType
	{
		NONE,
		IDENTIFIER,
		PUNCTUATOR,
		NUMERICLITERAL,
		STRINGLITERAL,
		COMMENT,
		KEYWORD,
		NULLLITERAL,
		BOOLEANLITERAL,
		REGEXP,
		TEMPLATEHEAD,
		TEMPLATEMIDDLE,
		TEMPLATETAIL,
		NOSUBSTTEMPLATE
	}
	
	public static enum LexerContextType
	{
		BLOCK,
		TEMPLATE
	}
	
	//Object that handles postponed lexing verifications that checks the parsed
	//environment state.
	public static class AsyncTrigger
	{
		private List<ConsumerFunction> checks = new ArrayList<ConsumerFunction>();
		
		private AsyncTrigger()
		{
			
		}
		
		private void push(ConsumerFunction fn)
		{
			checks.add(fn);
		}
		
		private ConsumerFunction check()
		{
			return new ConsumerFunction()
			{
				@Override
				public void accept() throws JSHintException
				{
					for (int check = 0; check < checks.size(); ++check)
					{
						checks.get(check).accept();
					}
					
					checks.clear();
				}
			};
		}
	}
	
	private EventEmitter emitter = null;
	private boolean prereg = false;
	private int line = 0;
	private int character = 0;
	private int from = 0;
	private String input = null;
	private boolean inComment = false;
	private List<LexerContext> context = null;
	private String[] lines = null;
	private List<TemplateStart> templateStarts = null;
	private boolean exhausted = false;
	private boolean ignoringLinterErrors = false;
	
	public Lexer(String source)
	{
		this(source.replaceAll("\r\n", "\n").replaceAll("\r", "\n").split("\n", -1));
	}
	
	public Lexer(String[] lines)
	{
		// If the first line is a shebang (#!), make it a blank and move on.
		// Shebangs are used by Node scripts.
		if (!ArrayUtils.isEmpty(lines) && StringUtils.startsWith(lines[0], "#!"))
		{
			if (lines[0].indexOf("node") != -1)
			{
				State.getOption().set("node", true);
			}
			lines[0] = "";
		}
		
		setEmitter(new EventEmitter());
		setLines(lines);
		setPrereg(true);
		
		setLine(0);
		setCharacter(1);
		setFrom(1);
		setInput("");
		setComment(false);
		setContext(new ArrayList<LexerContext>());
		setTemplateStarts(new ArrayList<TemplateStart>());
		
		for (int i = 0; i < State.getOption().asInt("indent"); i++)
		{
			State.setTab(State.getTab() + " ");
		}
	}
	
	public EventEmitter getEmitter()
	{
		return emitter;
	}

	public void setEmitter(EventEmitter emitter)
	{
		this.emitter = emitter;
	}
	
	public String[] getSource()
	{
		return lines;
	}
	
	public boolean getPrereg()
	{
		return prereg;
	}

	public void setPrereg(boolean prereg)
	{
		this.prereg = prereg;
	}
	
	public int getLine()
	{
		return line;
	}

	public void setLine(int line)
	{
		this.line = line;
	}
	
	public int getCharacter()
	{
		return character;
	}

	public void setCharacter(int character)
	{
		this.character = character;
	}
	
	public int getFrom()
	{
		return from;
	}

	public void setFrom(int from)
	{
		this.from = from;
	}
	
	public String getInput()
	{
		return input;
	}

	public void setInput(String input)
	{
		this.input = StringUtils.defaultString(input);
	}
	
	public boolean inComment()
	{
		return inComment;
	}

	public void setComment(boolean inComment)
	{
		this.inComment = inComment;
	}
	
	public List<LexerContext> getContext()
	{
		return Collections.unmodifiableList(context);
	}

	public void setContext(List<LexerContext> context)
	{
		this.context = context;
	}

	public boolean inContext(LexerContextType ctxType)
	{
		return context.size() > 0 && context.get(context.size() - 1).getType().equals(ctxType);
	}
	
	public void pushContext(LexerContextType ctxType)
	{
		context.add(new LexerContext(ctxType));
	}
	
	public LexerContext popContext()
	{
		return context.size() > 0 ? context.remove(context.size() - 1) : null;
	}
	
	public boolean isContext(LexerContext context)
	{
		return this.context.size() > 0 && this.context.get(this.context.size() - 1) == context;
	}
	
	public LexerContext currentContext()
	{
		return context.size() > 0 ? context.get(context.size() - 1) : null;
	}
	
	public String[] getLines()
	{
		lines = State.getLines();
		return lines;
	}
	
	public void setLines(String[] lines)
	{
		this.lines = lines;
		State.setLines(lines);
	}
	
	public List<TemplateStart> getTemplateStarts()
	{
		return Collections.unmodifiableList(templateStarts);
	}

	public void setTemplateStarts(List<TemplateStart> templateStarts)
	{
		this.templateStarts = templateStarts;
	}
	
	public boolean isExhausted()
	{
		return exhausted;
	}

	public void setExhausted(boolean exhausted)
	{
		this.exhausted = exhausted;
	}
	
	public boolean isIgnoringLinterErrors()
	{
		return ignoringLinterErrors;
	}

	public void setIgnoringLinterErrors(boolean ignoringLinterErrors)
	{
		this.ignoringLinterErrors = ignoringLinterErrors;
	}
	
	public char peek()
	{
		return peek(0);
	}
	
	/*
	 * Return the next i character without actually moving the
	 * char pointer.
	 */
	public char peek(int i)
	{
		return input.length() > i ? input.charAt(i) : '\0';
	}
	
	public void skip()
	{
		skip(1);
	}
	
	/*
	 * Move the char pointer forward i times.
	 */
	public void skip(int i)
	{
		i = i == 0 ? 1 : i;
		character += i;
		input = StringUtils.substring(input, i);
	}
	
	/*
	 * Subscribe to a token event. The API for this method is similar
	 * Underscore.js i.e. you can subscribe to multiple events with
	 * one call:
	 *
	 *   lex.on("Identifier Number", function (data) {
	 *     // ...
	 *   });
	 */
	public void on(String names, LexerEventListener listener)
	{
		for (String name : names.split(" "))
		{
			emitter.on(name, listener);
		}
	}
	
	/*
	 * Trigger a token event. All arguments will be passed to each
	 * listener.
	 */
	public void trigger(String name, EventContext context) throws JSHintException
	{
		emitter.emit(name, context);
	}
	
	/*
	 * Postpone a token event. the checking condition is set as
	 * last parameter, and the trigger function is called in a
	 * stored callback. To be later called using the check() function
	 * by the parser. This avoids parser's peek() to give the lexer
	 * a false context.
	 */
	public void triggerAsync(final String type, final EventContext args, AsyncTrigger checks, final PredicateFunction fn)
	{
		checks.push(new ConsumerFunction()
		{
			@Override
			public void accept() throws JSHintException
			{
				if (fn.test())
				{
					trigger(type, args);
				}
			}
		});
	}
	
	/*
	 * Extract a punctuator out of the next sequence of characters
	 * or return 'null' if its not possible.
	 *
	 * This method's implementation was heavily influenced by the
	 * scanPunctuator function in the Esprima parser's source code.
	 */
	public LexerToken scanPunctuator()
	{
		char ch1 = peek();
		
		switch (ch1)
		{
		// Most common single-character punctuators
		case '.':
			if (Character.isDigit(peek(1)))
			{
				return null;
			}
			if (peek(1) == '.' && peek(2) == '.')
			{
				return new LexerToken(LexerTokenType.PUNCTUATOR, "...");
			}
		case '(':
		case ')':
		case ';':
		case ',':
		case '[':
		case ']':
		case ':':
		case '~':
		case '?':
			return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1));
			
		// A block/object opener
		case '{':
			pushContext(LexerContextType.BLOCK);
			return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1));
			
		// A block/object closer
		case '}':
			if (inContext(LexerContextType.BLOCK))
			{
				popContext();
			}
			return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1));
			
		// A pound sign (for Node shebangs)
		case '#':
			return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1));
			
		// We're at the end of input
		case '\0':
			return null;
		}
		
		// Peek more characters
		char ch2 = peek(1);
		char ch3 = peek(2);
		char ch4 = peek(3);
		
		// 4-character punctuator: >>>=
		
		if (ch1 == '>' && ch2 == '>' && ch3 == '>' && ch4 == '=')
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, ">>>=");
		}
		
		// 3-character punctuators: === !== >>> <<= >>=
		
		if (ch1 == '=' && ch2 == '=' && ch3 == '=')
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, "===");
		}
		
		if (ch1 == '!' && ch2 == '=' && ch3 == '=')
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, "!==");
		}
		
		if (ch1 == '>' && ch2 == '>' && ch3 == '>')
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, ">>>");
		}
		
		if (ch1 == '<' && ch2 == '<' && ch3 == '=')
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, "<<=");
		}
		
		if (ch1 == '>' && ch2 == '>' && ch3 == '=')
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, ">>=");
		}
		
		// Fat arrow punctuator
		
		if (ch1 == '=' && ch2 == '>')
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1) + String.valueOf(ch2));
		}
		
		// 2-character punctuators: <= >= == != ++ -- << >> && ||
	    // += -= *= %= &= |= ^= /=
		
		if (ch1 == ch2 && ("+-<>&|".indexOf(ch1) >= 0))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1) + String.valueOf(ch2));
		}
		
		if ("<>=!+-*%&|^/".indexOf(ch1) >= 0)
		{
			if (ch2 == '=')
			{
				return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1) + String.valueOf(ch2));
			}
			
			return new LexerToken(LexerTokenType.PUNCTUATOR, String.valueOf(ch1));
		}
		
		return null;
	}
	
	// Create a comment token object and make sure it
    // has all the data JSHint needs to work with special
    // comments.
	private LexerToken commentToken(String label, String body, boolean isMultiline, boolean isMalformed)
	{
		String[] special = {"jshint", "jslint", "members", "member", "globals", "global", "exported"};
		boolean isSpecial = false;
		String value = label + body;
		TokenType commentType = TokenType.PLAIN;
		
		if (isMultiline)
		{
			value += "*/";
		}
		
		body = body.replaceAll("\\n", " ");
		
		if (label.equals("/*") && Reg.test(Reg.FALLS_THROUGH, body))
		{
			isSpecial = true;
			commentType = TokenType.FALLS_THROUGH;
		}
		
		for (String str : special)
		{
			if (isSpecial)
			{
				continue;
			}
			
			// Don't recognize any special comments other than jshint for single-line
	        // comments. This introduced many problems with legit comments.
			if (label.equals("//") && !str.equals(TokenType.JSHINT.toString()))
			{
				continue;
			}
			
			if (body.length() > str.length() && body.substring(0, str.length()).equals(str) && body.charAt(str.length()) == ' ')
			{
				isSpecial = true;
				label = label + str;
				body = body.substring(str.length()+1);
			}
			
			if (!isSpecial && body.length() > str.length()+1 && body.charAt(0) == ' ' && body.charAt(str.length()+1) == ' ' &&
				body.substring(1, str.length()+1).equals(str))
			{
				isSpecial = true;
				label = label + " " + str;
				body = body.substring(str.length()+1);
			}
			
			if (!isSpecial)
			{
				continue;
			}
			
			switch (str)
			{
			case "member":
				commentType = TokenType.MEMBERS;
				break;
			case "global":
				commentType = TokenType.GLOBALS;
				break;
			default:
				String[] options = body.split(":", -1);
				for (int i = 0; i < options.length; i++)
				{
					options[i] = options[i].trim();
				}
				
				if (options.length == 2)
				{
					switch (options[0])
					{
					case "ignore":
						switch (options[1])
						{
						case "start":
							ignoringLinterErrors = true;
							isSpecial = false;
							break;
						case "end":
							ignoringLinterErrors = false;
							isSpecial = false;
							break;
						}
					}
				}
				
				switch (str)
				{
					case "jshint":
						commentType = TokenType.JSHINT;
						break;
					case "jslint":
						commentType = TokenType.JSLINT;
						break;
					case "members":
						commentType = TokenType.MEMBERS;
						break;
					case "globals":
						commentType = TokenType.GLOBALS;
						break;
					case "exported":
						commentType = TokenType.EXPORTED;
						break;
				}
			}
		}
		
		LexerToken token = new LexerToken(LexerTokenType.COMMENT, value);
		token.setCommentType(commentType);
		token.setBody(body);
		token.setSpecial(isSpecial);
		//token.setMultiline(isMultiline); //JSHINT_BUG: variable isMultiline is not used anywhere can be removed
		token.setMalformed(isMalformed);
		return token; 
	}
	
	/**
	 * Extract a comment out of the next sequence of characters and/or
	 * lines or return 'null' if its not possible. Since comments can
	 * span across multiple lines this method has to move the char
	 * pointer.
	 *
	 * In addition to normal JavaScript comments (// and /*) this method
	 * also recognizes JSHint- and JSLint-specific comments such as
	 * /*jshint, /*jslint, /*globals and so on.
	 */
	public LexerToken scanComments() throws JSHintException
	{
		EventContext context;
		char ch1 = peek();
		char ch2 = peek(1);
		String rest = input.length() > 2 ? input.substring(2) : "";
		int startLine = line;
		int startChar = character;
		
		// End of unbegun comment. Raise an error and skip that input.
		if (ch1 == '*' && ch2 == '/')
		{
			context = new EventContext();
			context.setCode("E018");
			context.setLine(startLine);
			context.setCharacter(startChar);
			trigger("error", context);
			
			skip(2);
			return null;
		}
		
		// Comments must start either with // or /*
		if (ch1 != '/' || (ch2 != '*' && ch2 != '/'))
		{
			return null;
		}
		
		// One-line comment
		if (ch2 == '/')
		{
			skip(input.length()); // Skip to the EOL.
			return commentToken("//", rest, false, false);
		}
		
		String body = "";
		
		/* Multi-line comment */
		if (ch2 == '*')
		{
			inComment = true;
			skip(2);
			
			while (peek() != '*' || peek(1) != '/')
			{
				if (peek() == '\0') // End of Line
				{
					body += "\n";
					
					// If we hit EOF and our comment is still unclosed,
					// trigger an error and end the comment implicitly.
					if (!nextLine())
					{
						context = new EventContext();
						context.setCode("E017");
						context.setLine(startLine);
						context.setCharacter(startChar);
						trigger("error", context);
						
						inComment = false;
						return commentToken("/*", body, true, true);
					}
				}
				else
				{
					body += peek();
					skip();
				}
			}
			
			skip(2);
			inComment = false;
			return commentToken("/*", body, true, false);
		}
		
		return null;
	}
	
	/**
	 * Extract a keyword out of the next sequence of characters or
	 * return 'null' if its not possible.
	 */
	public LexerToken scanKeyword()
	{
		String[] result = Reg.exec(Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*"), input);
		String[] keywords = {
			"if", "in", "do", "var", "for", "new",
			"try", "let", "this", "else", "case",
			"void", "with", "enum", "while", "break",
			"catch", "throw", "const", "yield", "class",
			"super", "return", "typeof", "delete",
			"switch", "export", "import", "default",
			"finally", "extends", "function", "continue",
			"debugger", "instanceof"
		};
		
		if (result != null && result.length > 0 && ArrayUtils.indexOf(keywords, result[0]) >= 0)
		{
			return new LexerToken(LexerTokenType.KEYWORD, result[0]);
		}
		
		return null;
	}
	
	private boolean isHexDigit(char str)
	{
		return Reg.test("^[0-9a-fA-F]$", String.valueOf(str));
	}
	
	private boolean isDecimalDigit(char str)
	{
		return Reg.test("^[0-9]$", String.valueOf(str));
	}
	
	private boolean isOctalDigit(char str)
	{
		return Reg.test("^[0-7]$", String.valueOf(str));
	}
	
	private boolean isBinaryDigit(char str)
	{
		return Reg.test("^[01]$", String.valueOf(str));
	}
	
	private boolean isAllowedDigit(int base, char str)
	{
		switch (base)
		{
		case 16:
			return isHexDigit(str);
		case 10:
			return isDecimalDigit(str);
		case 8:
			return isOctalDigit(str);
		case 2:
			return isBinaryDigit(str);
		default:
			return false;
		}
	}
	
	private boolean isIdentifierStart(char ch)
	{
		return (ch == '&') || (ch == '_') || (ch == '\\') ||
			   (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
	}
	
	/*
	 * Extract a JavaScript identifier out of the next sequence of
	 * characters or return 'null' if its not possible. In addition,
	 * to Identifier this method can also produce BooleanLiteral
	 * (true/false) and NullLiteral (null).
	 */
	private int identifierIndex;
	public LexerToken scanIdentifier()
	{
		identifierIndex = 0;
		
		String chr = getIdentifierStart();
		if (chr == null)
		{
			return null;
		}
		
		String id = chr;
		for (;;)
		{
			chr = getIdentifierPart();
			
			if (chr == null)
			{
				break;
			}
			
			id += chr;
		}
		
		LexerTokenType type = LexerTokenType.NONE;
		
		switch (id)
		{
		case "true":
		case "false":
			type = LexerTokenType.BOOLEANLITERAL;
			break;
		case "null":
			type = LexerTokenType.NULLLITERAL;
			break;
		default:
			type = LexerTokenType.IDENTIFIER;
		}
		
		LexerToken token = new LexerToken(type, removeEscapeSequences(id));
		token.setText(id);
		token.setTokenLength(id.length());
		return token;
	}
	
	private boolean isNonAsciiIdentifierStart(int code)
	{
		return NonAsciiIdentifierStartTable.indexOf(code) > -1;
	}
	
	private boolean isNonAsciiIdentifierPart(int code)
	{
		return isNonAsciiIdentifierStart(code) || NonAsciiIdentifierPartTable.indexOf(code) > -1;
	}
	
	private String readUnicodeEscapeSequence()
	{
		identifierIndex += 1;
		
		if (peek(identifierIndex) != 'u')
		{
			return null;
		}
		
		char ch1 = peek(identifierIndex+1);
		char ch2 = peek(identifierIndex+2);
		char ch3 = peek(identifierIndex+3);
		char ch4 = peek(identifierIndex+4);
		int code = 0;
		
		if (isHexDigit(ch1) && isHexDigit(ch2) && isHexDigit(ch3) && isHexDigit(ch4))
		{
			code = Integer.parseInt(String.valueOf(ch1) + String.valueOf(ch2) + String.valueOf(ch3) + String.valueOf(ch4), 16);
			
			if ((code < UnicodeData.identifierPartTable.length && UnicodeData.identifierPartTable[code]) || isNonAsciiIdentifierPart(code))
			{
				identifierIndex += 5;
				return "\\u" + String.valueOf(ch1) + String.valueOf(ch2) + String.valueOf(ch3) + String.valueOf(ch4);
			}
			
			return null;
		}
		
		return null;
	}
	
	private String getIdentifierStart()
	{
		char chr = peek(identifierIndex);
		int code = chr;
		
		if (code == 92)
		{
			return readUnicodeEscapeSequence();
		}
		
		if (code < 128)
		{
			if (UnicodeData.identifierStartTable[code])
			{
				identifierIndex += 1;
				return String.valueOf(chr);
			}
			
			return null;
		}
		
		if (isNonAsciiIdentifierStart(code))
		{
			identifierIndex += 1;
			return String.valueOf(chr);
		}
		
		return null;
	}
	
	private String getIdentifierPart()
	{
		char chr = peek(identifierIndex);
		int code = chr;
		
		if (code == 92)
		{
			return readUnicodeEscapeSequence();
		}
		
		if (code < 128)
		{
			if (UnicodeData.identifierPartTable[code])
			{
				identifierIndex += 1;
				return String.valueOf(chr);
			}
			
			return null;
		}
		
		if (isNonAsciiIdentifierPart(code))
		{
			identifierIndex += 1;
			return String.valueOf(chr);
		}
		
		return null;
	}
	
	private String removeEscapeSequences(String id)
	{
		return Reg.replaceAll("\\\\u([0-9a-fA-F]{4})", id, new Reg.Replacer()
		{
			@Override
			public String apply(String str, List<String> groups)
			{
				
				return Character.toString((char)Integer.parseInt(groups.get(0), 16));
			}
		});
	}
	
	/*
	 * Extract a numeric literal out of the next sequence of
	 * characters or return 'null' if its not possible. This method
	 * supports all numeric literals described in section 7.8.3
	 * of the EcmaScript 5 specification.
	 *
	 * This method's implementation was heavily influenced by the
	 * scanNumericLiteral function in the Esprima parser's source code.
	 */
	public LexerToken scanNumericLiteral() throws JSHintException
	{
		EventContext context;
		int index = 0;
		String value = "";
		int length = input.length();
		char chr = peek(index);
		int base = 10;
		boolean isLegacy = false;
		
		// Numbers must start either with a decimal digit or a point.
		if (chr != '.' && !isDecimalDigit(chr))
		{
			return null;
		}
		
		if (chr != '.')
		{
			value = String.valueOf(peek(index));
			index += 1;
			chr = peek(index);
			
			if (value.equals("0"))
			{
				// Base-16 numbers.
				if (chr == 'x' || chr == 'X')
				{
					base = 16;
					
					index += 1;
					value += chr;
				}
				
				// Base-8 numbers.
				if (chr == 'o' || chr == 'O')
				{
					base = 8;
					
					if (!State.inES6(true))
					{
						context = new EventContext();
						context.setCode("W119");
						context.setLine(line);
						context.setCharacter(character);
						context.setData("Octal integer literal", "6");
						trigger("warning", context);
					}
					
					index += 1;
					value += chr;
				}
				
				// Base-2 numbers.
				if (chr == 'b' || chr == 'B')
				{
					base = 2;
					
					if (!State.inES6(true))
					{
						context = new EventContext();
						context.setCode("W119");
						context.setLine(line);
						context.setCharacter(character);
						context.setData("Binary integer literal", "6");
						trigger("warning", context);
					}
					
					index += 1;
					value += chr;
				}
				
				// Legacy base-8 numbers.
				if (isOctalDigit(chr))
				{
					base = 8;
					isLegacy = true;
					//bad = false; //JSHINT_BUG: this variable is not used anywhere, can be removed
					
					index += 1;
					value += chr;
				}
				
				// Decimal numbers that start with '0' such as '09' are illegal
		        // but we still parse them and return as malformed.
				if (!isOctalDigit(chr) && isDecimalDigit(chr))
				{
					index += 1;
					value += chr;
				}
			}
			
			while (index < length)
			{
				chr = peek(index);
				
				if (isLegacy && isDecimalDigit(chr))
				{
					// Numbers like '019' (note the 9) are not valid octals
					// but we still parse them and mark as malformed.
					//bad = true; //JSHINT_BUG: this variable is not used anywhere, can be removed
				}
				else if (!isAllowedDigit(base, chr))
				{
					break;
				}
				value += chr;
				index += 1;
			}
			
			if (base != 10)
			{
				if (!isLegacy && value.length() <= 2) // 0x
				{
					LexerToken token = new LexerToken(LexerTokenType.NUMERICLITERAL, value);
					token.setBase(0);
					token.setMalformed(true);
					return token;
				}
				
				if (index < length)
				{
					chr = peek(index);
					if (isIdentifierStart(chr))
					{
						return null;
					}
				}
				
				LexerToken token = new LexerToken(LexerTokenType.NUMERICLITERAL, value);
				token.setBase(base);
				token.setLegacy(isLegacy);
				token.setMalformed(false);
				return token;
			}
		}
		
		// Decimal digits.
		
		if (chr == '.')
		{
			value += chr;
			index += 1;
			
			while (index < length)
			{
				chr = peek(index);
				if (!isDecimalDigit(chr))
				{
					break;
				}
				value += chr;
				index += 1;
			}
		}
		
		// Exponent part.
		
		if (chr == 'e' || chr == 'E')
		{
			value += chr;
			index += 1;
			chr = peek(index);
			
			if (chr == '+' || chr == '-')
			{
				value += peek(index);
				index += 1;
			}
			
			chr = peek(index);
			if (isDecimalDigit(chr))
			{
				value += chr;
				index += 1;
				
				while (index < length)
				{
					chr = peek(index);
					if (!isDecimalDigit(chr))
					{
						break;
					}
					value += chr;
					index += 1;
				}
			}
			else
			{
				return null;
			}
		}
		
		if (index < length)
		{
			chr = peek(index);
			if (isIdentifierStart(chr))
			{
				return null;
			}
		}
		
		LexerToken token = new LexerToken(LexerTokenType.NUMERICLITERAL, value);
		token.setBase(base);
		token.setLegacy(isLegacy);
		token.setMalformed(Double.isInfinite(Double.valueOf(value)));
		return token;
	}
	
	// Assumes previously parsed character was \ (=== '\\') and was not skipped.
	public UniversalContainer scanEscapeSequence(AsyncTrigger checks) throws JSHintException
	{
		EventContext context;
		boolean allowNewLine = false;
		int jump = 1;
		skip();
		char chr = peek();
		String escChr = "";
		
		switch (chr)
		{
		case '\'':
			context = new EventContext();
			context.setCode("W114");
			context.setLine(line);
			context.setCharacter(character);
			context.setData("\\'");
			triggerAsync("warning", context, checks, new PredicateFunction()
			{
				@Override
				public boolean test()
				{
					return State.isJsonMode();
				}
			});
			
			break;
		case 'b':
			escChr = "\\b";
			break;
		case 'f':
			escChr = "\\f";
			break;
		case 'n':
			escChr = "\\n";
			break;
		case 'r':
			escChr = "\\r";
			break;
		case 't':
			escChr = "\\t";
			break;
		case '0':
			escChr = "\\0";

			// Octal literals fail in strict mode.
			// Check if the number is between 00 and 07.
			try
			{
				final int n = Integer.parseInt(String.valueOf(peek(1)), 10);
				context = new EventContext();
				context.setCode("W115");
				context.setLine(line);
				context.setCharacter(character);
				triggerAsync("warning", context, checks, new PredicateFunction()
					{
						@Override
						public boolean test()
						{
							return n >= 0 && n <= 7 && State.isStrict();
						}
					});
			}
			catch (NumberFormatException e)
			{
				//skip error
			}
			
			break;
		case 'u':
			String hexCode = input.substring(1, 5);
			try
			{
				char code = (char)Integer.parseInt(hexCode, 16);
				escChr = Character.toString(code);
			}
			catch (NumberFormatException e)
			{
				context = new EventContext();
				context.setCode("W052");
				context.setLine(line);
				context.setCharacter(character);
				context.setData("u" + hexCode);
				trigger("warning", context);
				escChr = "u"+ hexCode;
			}
			
			jump = 5;
			break;
		case 'v':
			context = new EventContext();
			context.setCode("W114");
			context.setLine(line);
			context.setCharacter(character);
			context.setData("\\v");
			trigger("warning", context);
			triggerAsync("warning", context, checks, new PredicateFunction()
				{
					@Override
					public boolean test()
					{
						return State.isJsonMode();
					}
				});
			
			escChr = Character.toString('\u000B');
			break;
		case 'x':
			int x = Integer.parseInt(input.substring(1, 2), 16);
			
			context = new EventContext();
			context.setCode("W114");
			context.setLine(line);
			context.setCharacter(character);
			context.setData("\\x-");
			triggerAsync("warning", context, checks, new PredicateFunction()
				{
					@Override
					public boolean test()
					{
						return State.isJsonMode();
					}
				});
			
			escChr = Character.toString((char)x);
			jump = 3;
			break;
		case '\\':
			escChr = "\\\\";
			break;
		case '"':
			escChr = "\\\"";
			break;
		case '/':
			break;
		case '\0':
			allowNewLine = true;
			escChr = "";
			break;
		}
		
		return ContainerFactory.createObject("char", escChr, "jump", jump, "allowNewLine", allowNewLine);
	}
	
	/*
	 * Extract a template literal out of the next sequence of characters
	 * and/or lines or return 'null' if its not possible. Since template
	 * literals can span across multiple lines, this method has to move
	 * the char pointer.
	 */
	public LexerToken scanTemplateLiteral(AsyncTrigger checks) throws JSHintException
	{
		EventContext context;
		LexerTokenType tokenType = LexerTokenType.NONE;
		String value = "";
		char ch = '\0';
		int startLine = line;
		int startChar = character;
		int depth = templateStarts.size();
		
		if (peek() == '`')
		{
			if (!State.inES6(true))
			{
				context = new EventContext();
				context.setCode("W119");
				context.setLine(line);
				context.setCharacter(character);
				context.setData("template literal syntax", "6");
				trigger("warning", context);
			}
			// Template must start with a backtick.
			tokenType = LexerTokenType.TEMPLATEHEAD;
			templateStarts.add(new TemplateStart(line, character));
			depth = templateStarts.size();
			skip(1);
			pushContext(LexerContextType.TEMPLATE);
		}
		else if (inContext(LexerContextType.TEMPLATE) && peek() == '}')
		{
			// If we're in a template context, and we have a '}', lex a TemplateMiddle.
			tokenType = LexerTokenType.TEMPLATEMIDDLE;
		}
		else
		{
			// Go lex something else.
			return null;
		}
		
		while (peek() != '`')
		{
			while ((ch = peek()) == '\0')
			{
				value += "\n";
				if (!nextLine())
				{
					// Unclosed template literal --- point to the starting "`"
					TemplateStart startPos = templateStarts.remove(templateStarts.size()-1);
					context = new EventContext();
					context.setCode("E052");
					context.setLine(startPos.getLine());
					context.setCharacter(startPos.getCharacter());
					trigger("error", context);
					
					LexerToken token = new LexerToken(tokenType, value);
					token.setStartLine(startLine);
					token.setStartChar(startChar);
					token.setUnclosed(true);
					token.setDepth(depth);
					token.setContext(popContext());
					return token;
				}
			}
			
			if (ch == '$' && peek(1) == '{')
			{
				value += "${";
				skip(2);
				
				LexerToken token = new LexerToken(tokenType, value);
				token.setStartLine(startLine);
				token.setStartChar(startChar);
				token.setUnclosed(false);
				token.setDepth(depth);
				token.setContext(currentContext());
				return token;
			}
			else if (ch == '\\')
			{
				UniversalContainer escape = scanEscapeSequence(checks);
				value += escape.asString("char");
				skip(escape.asInt("jump"));
			}
			else if (ch != '`')
			{
				// Otherwise, append the value and continue.
				value += ch;
				skip(1);
			}
		}
		
		// Final value is either NoSubstTemplate or TemplateTail
		tokenType = tokenType == LexerTokenType.TEMPLATEHEAD ? LexerTokenType.NOSUBSTTEMPLATE : LexerTokenType.TEMPLATETAIL;
		skip(1);
		templateStarts.remove(templateStarts.size()-1);
		
		LexerToken token = new LexerToken(tokenType, value);
		token.setStartLine(startLine);
		token.setStartChar(startChar);
		token.setUnclosed(false);
		token.setDepth(depth);
		token.setContext(popContext());
		return token;
	}
	
	/*
	 * Extract a string out of the next sequence of characters and/or
	 * lines or return 'null' if its not possible. Since strings can
	 * span across multiple lines this method has to move the char
	 * pointer.
	 *
	 * This method recognizes pseudo-multiline JavaScript strings:
	 *
	 *   var str = "hello\
	 *   world";
	 */
	public LexerToken scanStringLiteral(AsyncTrigger checks) throws JSHintException
	{
		EventContext context;
		final char quote = peek();
		
		// String must start with a quote.
		if (quote != '"' && quote != '\'')
		{
			return null;
		}
		
		// In JSON strings must always use double quotes.
		context = new EventContext();
		context.setCode("W108");
		context.setLine(line);
		context.setCharacter(character);
		triggerAsync("warning", context, checks, new PredicateFunction()
			{
				@Override
				public boolean test()
				{
					return State.isJsonMode() && quote != '"';
				}
			});
		
		String value = "";
		int startLine = line;
		int startChar = character;
		boolean allowNewLine = false;
		
		skip();
		
		while (peek() != quote)
		{
			if (peek() == '\0') // End Of Line
			{
				// If an EOL is not preceded by a backslash, show a warning
		        // and proceed like it was a legit multi-line string where
		        // author simply forgot to escape the newline symbol.
		        //
		        // Another approach is to implicitly close a string on EOL
		        // but it generates too many false positives.
				
				if (!allowNewLine)
				{
					context = new EventContext();
					context.setCode("W112");
					context.setLine(line);
					context.setCharacter(character);
					trigger("warning", context);
				}
				else
				{
					allowNewLine = false;
					
					// Otherwise show a warning if multistr option was not set.
					// For JSON, show warning no matter what.
					
					context = new EventContext();
					context.setCode("W043");
					context.setLine(line);
					context.setCharacter(character);
					triggerAsync("warning", context, checks, new PredicateFunction()
						{
							@Override
							public boolean test()
							{
								return !State.getOption().test("multistr");
							}
						});
					
					context = new EventContext();
					context.setCode("W042");
					context.setLine(line);
					context.setCharacter(character);
					triggerAsync("warning", context, checks, new PredicateFunction()
						{
							@Override
							public boolean test()
							{
								return State.isJsonMode() && State.getOption().test("multistr");
							}
						});
				}
				
				// If we get an EOF inside of an unclosed string, show an
		        // error and implicitly close it at the EOF point.
				
				if (!nextLine())
				{
					context = new EventContext();
					context.setCode("E029");
					context.setLine(startLine);
					context.setCharacter(startChar);
					trigger("error", context);
					
					LexerToken token = new LexerToken(LexerTokenType.STRINGLITERAL, value);
					token.setStartLine(startLine);
					token.setStartChar(startChar);
					token.setUnclosed(true);
					token.setQuote(quote);
					return token;
				}
			}
			else // Any character other than End Of Line
			{
				allowNewLine = false;
				char chr = peek();
				String escChr = String.valueOf(chr);
				int jump = 1; 	// A length of a jump, after we're done
								// parsing this character.
				
				if (chr < ' ')
				{
					// Warn about a control character in a string.
					context = new EventContext();
					context.setCode("W113");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("<non-printable>");
					trigger("warning", context);
				}
				
				// Special treatment for some escaped characters.
				if (chr == '\\')
				{
					UniversalContainer parsed = scanEscapeSequence(checks);
					escChr = parsed.asString("char");
					jump = parsed.asInt("jump");
					allowNewLine = parsed.asBoolean("allowNewLine");
				}
				
				value += escChr;
				skip(jump);
			}
		}
		
		skip();
		
		LexerToken token = new LexerToken(LexerTokenType.STRINGLITERAL, value);
		token.setStartLine(startLine);
		token.setStartChar(startChar);
		token.setUnclosed(false);
		token.setQuote(quote);
		return token;
	}
	
	/*
	 * Extract a regular expression out of the next sequence of
	 * characters and/or lines or return 'null' if its not possible.
	 *
	 * This method is platform dependent: it accepts almost any
	 * regular expression values but then tries to compile and run
	 * them using system's RegExp object. This means that there are
	 * rare edge cases where one JavaScript engine complains about
	 * your regular expression while others don't.
	 */
	public LexerToken scanRegExp() throws JSHintException
	{
		EventContext context;
		int index = 0;
		int length = input.length();
		char chr = peek();
		String value = String.valueOf(chr);
		String body = "";
		List<Character> flags = new ArrayList<Character>();
		boolean malformed = false;
		boolean isCharSet = false;
		boolean terminated = false;
		String malformedDesc = "";
		
		// Regular expressions must start with '/'
		if (!prereg || chr != '/')
		{
			return null;
		}
		
		index += 1;
		terminated = false;
		
		// Try to get everything in between slashes. A couple of
	    // cases aside (see scanUnexpectedChars) we don't really
	    // care whether the resulting expression is valid or not.
	    // We will check that later using the RegExp object.
		
		while (index < length)
		{
			chr = peek(index);
			value += chr;
			body += chr;
			
			if (isCharSet)
			{
				if (chr == ']')
				{
					if (peek(index-1) != '\\' || peek(index-2) == '\\')
					{
						isCharSet = false;
					}
				}
				
				if (chr == '\\')
				{
					index += 1;
					chr = peek(index);
					body += chr;
					value += chr;
					
					// Unexpected control character
					if (chr < ' ')
					{
						malformed = true;
						context = new EventContext();
						context.setCode("W048");
						context.setLine(line);
						context.setCharacter(character);
						trigger("warning", context);
					}
					
					// Unexpected escaped character
					if (chr == '<')
					{
						malformed = true;
						context = new EventContext();
						context.setCode("W049");
						context.setLine(line);
						context.setCharacter(character);
						context.setData(String.valueOf(chr));
						trigger("warning", context);
					}
				}
				
				index += 1;
				continue;
			}
				
			if (chr == '\\')
			{
				index += 1;
				chr = peek(index);
				body += chr;
				value += chr;
				
				// Unexpected control character
				if (chr < ' ')
				{
					malformed = true;
					context = new EventContext();
					context.setCode("W048");
					context.setLine(line);
					context.setCharacter(character);
					trigger("warning", context);
				}
				
				// Unexpected escaped character
				if (chr == '<')
				{
					malformed = true;
					context = new EventContext();
					context.setCode("W049");
					context.setLine(line);
					context.setCharacter(character);
					context.setData(String.valueOf(chr));
					trigger("warning", context);
				}
				
				if (chr == '/')
				{
					index += 1;
					continue;
				}
				
				if (chr == '[')
				{
					index += 1;
					continue;
				}
			}
			
			if (chr == '[')
			{
				isCharSet = true;
				index += 1;
				continue;
			}
			
			if (chr == '/')
			{
				body = body.substring(0, body.length()-1);
				terminated = true;
				index += 1;
				break;
			}
			
			index += 1;
		}
		
		// A regular expression that was never closed is an
	    // error from which we cannot recover.
		
		if (!terminated)
		{
			context = new EventContext();
			context.setCode("E015");
			context.setLine(line);
			context.setCharacter(from);
			trigger("error", context);
			
			context = new EventContext();
			context.setLine(line);
			context.setCharacter(from);
			trigger("fatal", context);
			return null;
		}
		
		// Parse flags (if any).
		
		while (index < length)
		{
			chr = peek(index);
			if (!Reg.test("[gimy]", String.valueOf(chr)))
			{
				break;
			}
			if (chr == 'y')
			{
				if (!State.inES6(true))
				{
					context = new EventContext();
					context.setCode("W119");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("Sticky RegExp flag", "6");
					trigger("warning", context);
				}
				if (value.indexOf("y") > -1)
				{
					malformedDesc = "Duplicate RegExp flag";
				}
			}
			else
			{
				flags.add(chr);
			}
			value += chr;
			index += 1;
		}
		
		// Check regular expression for correctness.
		// FIXME: 1) forced to use rhino javascript engine to be able to parse regular expressions
		// FIXME: 2) added temp variable to hold original regexp value and for parsing "y" flag is removed
		// FIXME: 3) added check for flags duplication, since java doesn't throw error on such cases
		String temp = value;
		String fstr = value.substring(value.lastIndexOf("/"));
		try
		{
			org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
			
			if (fstr.indexOf("g") != fstr.lastIndexOf("g") || fstr.indexOf("y") != fstr.lastIndexOf("y"))
				throw new Exception("Invalid regular expression flags");
			
			value = value.substring(0, value.lastIndexOf("/")) + fstr.replaceAll("y", "");
			
			
			cx.evaluateString(cx.initStandardObjects(), value, "", 1, null);
		}
		catch (Exception err)
		{
			/**
		     * Because JSHint relies on the current engine's RegExp parser to
		     * validate RegExp literals, the description (exposed as the "data"
		     * property on the error object) is platform dependent.
		     */
			malformedDesc = err.getMessage();
		}
		finally
		{
			org.mozilla.javascript.Context.exit();
			value = temp;
		}
		
		if (StringUtils.isNotEmpty(malformedDesc))
		{
			malformed = true;
			context = new EventContext();
			context.setCode("E016");
			context.setLine(line);
			context.setCharacter(character);
			context.setData(malformedDesc);
			trigger("error", context);
		}
		
		LexerToken token = new LexerToken(LexerTokenType.REGEXP, value);
		//token.setFlags(flags) //JSHINT_BUG: variable flags is not used anywhere, can be removed
		token.setMalformed(malformed);
		return token;
	}
	
	/*
	 * Scan for any occurrence of non-breaking spaces. Non-breaking spaces
	 * can be mistakenly typed on OS X with option-space. Non UTF-8 web
	 * pages with non-breaking pages produce syntax errors.
	 */
	public int scanNonBreakingSpaces()
	{
		return State.getOption().test("nonbsp") ? input.indexOf('\u00A0') : -1;
	}
	
	/*
	 * Scan for characters that get silently deleted by one or more browsers.
	 */
	public int scanUnsafeChars()
	{
		return Reg.indexOf(Reg.UNSAFE_CHARS, input);
	}
	
	/*
	 * Produce the next raw token or return 'null' if no tokens can be matched.
	 * This method skips over all space characters.
	 */
	public LexerToken next(AsyncTrigger checks) throws JSHintException
	{
		from = character;
		
		// Move to the next non-space character.
		while (Reg.test("[\\p{Z}\\s]", String.valueOf(peek())))
		{
			from += 1;
			skip();
		}
		
		// Methods that work with multi-line structures and move the
	    // character pointer.
		
		LexerToken match = scanComments();
		if (match == null) match = scanStringLiteral(checks);
		if (match == null) match = scanTemplateLiteral(checks);
		
		if (match != null)
		{
			return match;
		}
		
		// Methods that don't move the character pointer.
		
		match = scanRegExp();
		if (match == null) match = scanPunctuator();
		if (match == null) match = scanKeyword();
		if (match == null) match = scanIdentifier();
		if (match == null) match = scanNumericLiteral();
		
		if (match != null)
		{
			skip(match.getTokenLength() != 0 ? match.getTokenLength() : match.getValue().length());
			return match;
		}
		
		// No token could be matched, give up.
		return null;
	}
	
	/*
	 * Switch to the next line and reset all char pointers. Once
	 * switched, this method also checks for other minor warnings.
	 */
	public boolean nextLine() throws JSHintException
	{
		EventContext context;
		
		if (line >= getLines().length)
		{
			return false;
		}
		
		input = getLines()[line];
		line += 1;
		character = 1;
	    from = 1;
	    
	    String inputTrimmed = input.trim();
	    
	    // If we are ignoring linter errors, replace the input with empty string
	    // if it doesn't already at least start or end a multi-line comment
	    if (ignoringLinterErrors == true)
	    {
	    	if (!(inputTrimmed.startsWith("/*") || inputTrimmed.startsWith("//")) && !(inComment && inputTrimmed.endsWith("*/")))
	    	{
	    		input = "";
	    	}
	    }
	    
	    int chr = scanNonBreakingSpaces();
	    if (chr >= 0)
	    {
	    	context = new EventContext();
			context.setCode("W125");
			context.setLine(line);
			context.setCharacter(chr+1);
	    	trigger("warning", context);
	    }
	    
	    input = input.replaceAll("\t", State.getTab());
	    chr = scanUnsafeChars();
	    
	    if (chr >= 0)
	    {
	    	context = new EventContext();
			context.setCode("W100");
			context.setLine(line);
			context.setCharacter(chr);
	    	trigger("warning", context);
	    }
	    
	    // If there is a limit on line length, warn when lines get too
	    // long.
	    if (!ignoringLinterErrors && State.getOption().test("maxlen") &&
	    	State.getOption().asInt("maxlen") < input.length())
	    {
	    	boolean inComment = this.inComment ||
	    			inputTrimmed.startsWith("//") ||
	    			inputTrimmed.startsWith("/*");
	    	
	    	boolean shouldTriggerError = !inComment || !Reg.test(Reg.MAXLEN_EXCEPTION, inputTrimmed);
	    	
	    	if (shouldTriggerError)
	    	{
	    		context = new EventContext();
				context.setCode("W101");
				context.setLine(line);
				context.setCharacter(input.length());
	    		trigger("warning", context);
	    	}
	    }
	    
	    return true;
	}
	
	/*
	 * This is simply a synonym for nextLine() method with a friendlier
	 * public name.
	 */
	public void start() throws JSHintException
	{
		nextLine();
	}
	
	private boolean isReserved(Token token, boolean isProperty)
	{
		if (!token.isReserved())
		{
			return false;
		}
		
		Token.Meta meta = token.getMeta();
		
		if (meta != null && meta.isFutureReservedWord() && State.inES5())
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
				if (!State.getOption().get("strict").test() && !State.isStrict())
				{
					return false;
				}
			}
			
			if (isProperty)
			{
				return false;
			}
		}
		
		return true;
	}
	
	// Produce a token object.
	private Token create(TokenType type, String value, AsyncTrigger checks)
	{
		return create(type, value, false, null, checks);
	}
	
	private Token create(TokenType type, String value, boolean isProperty, LexerToken token, AsyncTrigger checks)
	{
		Token obj = null;
		
		if (type != TokenType.ENDLINE && type != TokenType.END)
		{
			prereg = false;
		}
		
		if (type == TokenType.PUNCTUATOR)
		{
			switch (value)
			{
			case ".":
	        case ")":
	        case "~":
	        case "#":
	        case "]":
	        case "++":
	        case "--":
	        	prereg = false;
	        	break;
	        default:
	        	prereg = true;
			}
			
			if (State.getSyntax().get(value) != null) obj = new Token(State.getSyntax().get(value));
			else if (State.getSyntax().get("(error)") != null) obj = new Token(State.getSyntax().get("(error)"));
			else obj = new Token();
		}
		
		if (type == TokenType.IDENTIFIER)
		{
			if (value.equals("return") || value.equals("case") ||
				value.equals("typeof") || value.equals("instanceof"))
			{
				prereg = true;
			}
			
			if (State.getSyntax().containsKey(value))
			{
				if (State.getSyntax().get(value) != null) obj = new Token(State.getSyntax().get(value));
				else if (State.getSyntax().get("(error)") != null) obj = new Token(State.getSyntax().get("(error)"));
				else obj = new Token();
				
				// If this can't be a reserved keyword, reset the object.
				if (!isReserved(obj, isProperty && type == TokenType.IDENTIFIER))
				{
					obj = null;
				}
			}
		}
		
		if (type == TokenType.TEMPLATE || type == TokenType.TEMPLATEMIDDLE)
		{
			prereg = true;
		}
		
		if (obj == null)
		{
			obj = State.getSyntax().get(type.toString()) != null ? new Token(State.getSyntax().get(type.toString())) : new Token();
		}
		
		obj.setIdentifier(type == TokenType.IDENTIFIER);
		obj.setType(ObjectUtils.defaultIfNull(obj.getType(), type));
		obj.setValue(value);
		obj.setLine(line);
		obj.setCharacter(character);
		obj.setFrom(from);
		if (obj.isIdentifier() && token != null) obj.setRawText(StringUtils.defaultIfEmpty(token.getText(), token.getValue()));
		if (token != null && token.getStartLine() > 0 && token.getStartLine() != line)
		{
			obj.setStartLine(token.getStartLine());
		}
		
		if (token != null && token.getContext() != null)
		{
			// Context of current token
			obj.setContext(token.getContext());
		}
		if (token != null && token.getDepth() != 0)
		{
			// Nested template depth
			obj.setDepth(token.getDepth());
		}
		if (token != null && token.isUnclosed())
		{
			// Mark token as unclosed string / template literal
			obj.setUnclosed(token.isUnclosed());
		}
		
		if (isProperty && obj.isIdentifier())
		{
			obj.setProperty(isProperty);
		}
		
		obj.setCheck(checks.check());
		
		return obj;
	}
	
	/*
	 * Produce the next token. This function is called by advance() to get
	 * the next token. It returns a token in a JSLint-compatible format.
	 */
	public Token token() throws JSHintException
	{
		EventContext context;
		AsyncTrigger checks = new AsyncTrigger();
		
		for (;;)
		{
			if (input.length() == 0)
			{
				if (nextLine())
				{
					return create(TokenType.ENDLINE, "", checks);
				}
				
				if (exhausted)
				{
					return null;
				}
				
				exhausted = true;
				return create(TokenType.END, "", checks);
			}
			
			final LexerToken token = next(checks);
			
			if (token == null)
			{	
				if (input.length() != 0)
				{
					// Unexpected character.
					context = new EventContext();
					context.setCode("E024");
					context.setLine(line);
					context.setCharacter(character);
					context.setData(String.valueOf(peek()));
					trigger("error", context);
					
					input = "";
				}
				
				continue;
			}
			
			switch (token.getType())
			{
			case STRINGLITERAL:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				context.setQuote(token.getQuote());
				triggerAsync("String", context, checks, new PredicateFunction()
					{
						@Override
						public boolean test()
						{
							return true;
						}
					});
				return create(TokenType.STRING, token.getValue(), false, token, checks);
			case TEMPLATEHEAD:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("TemplateHead", context);
				return create(TokenType.TEMPLATE, token.getValue(), false, token, checks);
			case TEMPLATEMIDDLE:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("TemplateMiddle", context);
				return create(TokenType.TEMPLATEMIDDLE, token.getValue(), false, token, checks);
			case TEMPLATETAIL:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("TemplateTail", context);
				return create(TokenType.TEMPLATETAIL, token.getValue(), false, token, checks);
			case NOSUBSTTEMPLATE:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("NoSubstTemplate", context);
				return create(TokenType.NOSUBSTTEMPLATE, token.getValue(), false, token, checks);
			case IDENTIFIER:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setName(token.getValue());
				context.setRawName(token.getText());
				context.setProperty(State.currToken().getId().equals("."));
				triggerAsync("Identifier", context, checks, new PredicateFunction()
					{
						@Override
						public boolean test()
						{
							return true;
						}
					});
			case KEYWORD:
			case NULLLITERAL:
			case BOOLEANLITERAL:
				return create(TokenType.IDENTIFIER, token.getValue(), State.currToken().getId().equals("."), token, checks);
			case NUMERICLITERAL:
				if (token.isMalformed())
				{
					context = new EventContext();
					context.setCode("W045");
					context.setLine(line);
					context.setCharacter(character);
					context.setData(token.getValue());
					trigger("warning", context);
				}
				
				context = new EventContext();
				context.setCode("W114");
				context.setLine(line);
				context.setCharacter(character);
				context.setData("0x-");
				triggerAsync("warning", context, checks, new PredicateFunction()
					{
						@Override
						public boolean test()
						{
							return token.getBase() == 16 && State.isJsonMode();
						}
					});
				
				context = new EventContext();
				context.setCode("W115");
				context.setLine(line);
				context.setCharacter(character);
				triggerAsync("warning", context, checks, new PredicateFunction()
					{
						@Override
						public boolean test()
						{
							return State.isStrict() && token.getBase() == 8 && token.isLegacy();
						}
					});
				
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setValue(token.getValue());
				context.setBase(token.getBase());
				context.setMalformed(token.isMalformed()); //JSHINT_BUG: typo, must be isMalformed intead of malformed
				trigger("Number", context);
				
				return create(TokenType.NUMBER, token.getValue(), checks);
			case REGEXP:
				return create(TokenType.REGEXP, token.getValue(), checks);
			case COMMENT:
				//State.tokens.curr.comment = true; //JSHINT_BUG: "comment" property is not used anywhere, can be removed
				if (token.isSpecial())
				{
					Token t = new Token();
					t.setId("(comment)");
					t.setValue(token.getValue());
					t.setBody(token.getBody());
					t.setType(token.getCommentType());
					t.setSpecial(token.isSpecial());
					t.setLine(line);
					t.setCharacter(character);
					t.setFrom(from);
					return t;
				}
				break;
			case NONE:
				break;
			default:
				return create(TokenType.PUNCTUATOR, token.getValue(), checks);
			}
		}
	}
	
	public static class LexerToken
	{	
		private LexerTokenType type = LexerTokenType.NONE;
		private String value = null;
		private TokenType commentType = null;
		private String body = null;
		private boolean isSpecial = false;
		private boolean isMalformed = false;
		private String text = null;
		private int tokenLength = 0;
		private int base = 0;
		private boolean isLegacy = false;
		private boolean isUnclosed = false;
		private char quote;
		private int startLine = 0;
		private int startChar = 0;
		private int depth = 0;
		private LexerContext context = null;
		
		private LexerToken(LexerTokenType type, String value)
		{
			setType(type);
			setValue(value);
		}

		public LexerTokenType getType()
		{
			return type;
		}

		private void setType(LexerTokenType type)
		{
			this.type = type;
		}

		public String getValue()
		{
			return value;
		}

		private void setValue(String value)
		{
			this.value = StringUtils.defaultString(value);
		}

		public TokenType getCommentType()
		{
			return commentType;
		}

		private void setCommentType(TokenType commentType)
		{
			this.commentType = commentType;
		}

		public String getBody()
		{
			return body;
		}

		private void setBody(String body)
		{
			this.body = StringUtils.defaultString(body);
		}

		public boolean isSpecial()
		{
			return isSpecial;
		}

		private void setSpecial(boolean isSpecial)
		{
			this.isSpecial = isSpecial;
		}

		public boolean isMalformed()
		{
			return isMalformed;
		}

		private void setMalformed(boolean isMalformed)
		{
			this.isMalformed = isMalformed;
		}

		public String getText()
		{
			return text;
		}

		private void setText(String text)
		{
			this.text = StringUtils.defaultString(text);
		}

		public int getTokenLength()
		{
			return tokenLength;
		}

		private void setTokenLength(int tokenLength)
		{
			this.tokenLength = tokenLength;
		}

		public int getBase()
		{
			return base;
		}

		private void setBase(int base)
		{
			this.base = base;
		}

		public boolean isLegacy()
		{
			return isLegacy;
		}

		private void setLegacy(boolean isLegacy)
		{
			this.isLegacy = isLegacy;
		}

		public boolean isUnclosed()
		{
			return isUnclosed;
		}

		private void setUnclosed(boolean isUnclosed)
		{
			this.isUnclosed = isUnclosed;
		}

		public char getQuote()
		{
			return quote;
		}

		private void setQuote(char quote)
		{
			this.quote = quote;
		}

		public int getStartLine()
		{
			return startLine;
		}

		private void setStartLine(int startLine)
		{
			this.startLine = startLine;
		}

		public int getStartChar()
		{
			return startChar;
		}

		private void setStartChar(int startChar)
		{
			this.startChar = startChar;
		}

		public int getDepth()
		{
			return depth;
		}

		private void setDepth(int depth)
		{
			this.depth = depth;
		}

		public LexerContext getContext()
		{
			return context;
		}

		private void setContext(LexerContext context)
		{
			this.context = context;
		}
	}
	
	public static class LexerContext
	{	
		private LexerContextType type;
		
		private LexerContext(LexerContextType type)
		{
			this.type = type;
		}
		
		public LexerContextType getType()
		{
			return type; 
		}
	}
	
	public static class TemplateStart
	{
		private int line;
		private int character;
		
		private TemplateStart(int line, int character)
		{
			this.line = line;
			this.character = character;
		}

		public int getLine()
		{
			return line;
		}

		public int getCharacter()
		{
			return character;
		}
	}
}