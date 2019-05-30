package org.jshint;

/*
 * Lexical analysis and token construction.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.data.ES5IdentifierNames;
import org.jshint.data.NonAsciiIdentifierPartTable;
import org.jshint.data.NonAsciiIdentifierStartTable;
import org.jshint.data.UnicodeData;
import org.jshint.utils.EventEmitter;
import org.jshint.utils.EventContext;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;
import com.google.common.primitives.Ints;

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
	// PORT INFO: Static javascript engine, which is used to validate regexps
	private static final ScriptEngine jsEngine = new ScriptEngineManager().getEngineByName("nashorn");
	
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
	
	// PORT INFO: test regexp /^[0-9a-fA-F]+$/ was replaced with straight check
	private boolean isHex(String str)
	{
		if (str == null || str.length() == 0) return false;
		
		for (int i = 0; i < str.length(); i++)
		{
			char c = str.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))
				return false;
		}
		
		return true;
	}
	
	private boolean isHexDigit(String str)
	{
		return str.length() == 1 && isHex(str);
	}
	
	// Object that handles postponed lexing verifications that checks the parsed
	// environment state.
	public static class AsyncTrigger
	{
		private List<Runnable> checks = new ArrayList<Runnable>();
		
		private AsyncTrigger() {}
		
		private void push(Runnable fn)
		{
			checks.add(fn);
		}
		
		private void check()
		{
			for (int check = 0; check < checks.size(); ++check)
			{
				checks.get(check).run();
			}
			
			checks.clear();
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
		this(Reg.splitByEOL(source)); // PORT INFO: split regexp was moved to Reg class
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
	
	public String peek()
	{
		return peek(0);
	}
	
	/*
	 * Return the next i character without actually moving the
	 * char pointer.
	 */
	public String peek(int i)
	{
		return input.length() > i ? String.valueOf(input.charAt(i)) : "";
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
		for (String name : names.split(" ", -1))
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
	public void triggerAsync(String type, EventContext args, AsyncTrigger checks, BooleanSupplier fn)
	{
		checks.push(() -> {
			if (fn.getAsBoolean())
			{
				trigger(type, args);
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
		String ch1 = peek();
		
		switch (ch1)
		{
		// Most common single-character punctuators
		case ".":
			if (isDecimalDigit(peek(1))) // PORT INFO: test regexp /^[0-9]$/ was replaced with isDecimalDigit method
			{
				return null;
			}
			if (peek(1).equals(".") && peek(2).equals("."))
			{
				return new LexerToken(LexerTokenType.PUNCTUATOR, "...");
			}
		case "(":
		case ")":
		case ";":
		case ",":
		case "[":
		case "]":
		case ":":
		case "~":
		case "?":
			return new LexerToken(LexerTokenType.PUNCTUATOR, ch1);
			
		// A block/object opener
		case "{":
			pushContext(LexerContextType.BLOCK);
			return new LexerToken(LexerTokenType.PUNCTUATOR, ch1);
			
		// A block/object closer
		case "}":
			if (inContext(LexerContextType.BLOCK))
			{
				popContext();
			}
			return new LexerToken(LexerTokenType.PUNCTUATOR, ch1);
			
		// A pound sign (for Node shebangs)
		case "#":
			return new LexerToken(LexerTokenType.PUNCTUATOR, ch1);
			
		// We're at the end of input
		case "":
			return null;
		}
		
		// Peek more characters
		String ch2 = peek(1);
		String ch3 = peek(2);
		String ch4 = peek(3);
		
		// 4-character punctuator: >>>=
		
		if (ch1.equals(">") && ch2.equals(">") && ch3.equals(">") && ch4.equals("="))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, ">>>=");
		}
		
		// 3-character punctuators: === !== >>> <<= >>=
		
		if (ch1.equals("=") && ch2.equals("=") && ch3.equals("="))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, "===");
		}
		
		if (ch1.equals("!") && ch2.equals("=") && ch3.equals("="))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, "!==");
		}
		
		if (ch1.equals(">") && ch2.equals(">") && ch3.equals(">"))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, ">>>");
		}
		
		if (ch1.equals("<") && ch2.equals("<") && ch3.equals("="))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, "<<=");
		}
		
		if (ch1.equals(">") && ch2.equals(">") && ch3.equals("="))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, ">>=");
		}
		
		// Fat arrow punctuator
		
		if (ch1.equals("=") && ch2.equals(">"))
		{
			return new LexerToken(LexerTokenType.PUNCTUATOR, ch1 + ch2);
		}
		
		// 2-character punctuators: ++ -- << >> && || **
		if (ch1.equals(ch2) && ("+-<>&|*".indexOf(ch1) >= 0))
		{
			if (ch1.equals("*") && ch3.equals("="))
			{
				return new LexerToken(LexerTokenType.PUNCTUATOR, ch1 + ch2 + ch3);
			}
			
			return new LexerToken(LexerTokenType.PUNCTUATOR, ch1 + ch2);
		}
		
		// <= >= != += -= *= %= &= |= ^= /=
		if ("<>=!+-*%&|^/".indexOf(ch1) >= 0)
		{
			if (ch2.equals("="))
			{
				return new LexerToken(LexerTokenType.PUNCTUATOR, ch1 + ch2);
			}
			
			return new LexerToken(LexerTokenType.PUNCTUATOR, ch1);
		}
		
		return null;
	}
	
	// Create a comment token object and make sure it
    // has all the data JSHint needs to work with special
    // comments.
	private LexerToken commentToken(String label, String body, boolean isMultiline, boolean isMalformed)
	{
		String[] special = {
			"jshint", "jshint.unstable", "jslint", "members", "member", "globals",
			"global", "exported"
		};
		boolean isSpecial = false;
		String value = label + body;
		Token.Type commentType = Token.Type.PLAIN;
		
		if (isMultiline)
		{
			value += "*/";
		}
		
		body = StringUtils.replace(body, "\n", " ");
		
		if (label.equals("/*") && Reg.isFallsThrough(body))
		{
			isSpecial = true;
			commentType = Token.Type.FALLS_THROUGH;
		}
		
		for (String str : special)
		{
			if (isSpecial)
			{
				continue;
			}
			
			// Don't recognize any special comments other than jshint for single-line
	        // comments. This introduced many problems with legit comments.
			if (label.equals("//") && !str.equals("jshint") && !str.equals("jshint.unstable"))
			{
				continue;
			}
			
			if (body.length() > str.length() && body.charAt(str.length()) == ' ' && body.substring(0, str.length()).equals(str))
			{
				isSpecial = true;
				label = label + str;
				body = body.substring(str.length()+1);
			}
			
			if (!isSpecial && body.length() > 0 && body.charAt(0) == ' ' && body.length() > str.length() + 1 && body.charAt(str.length() + 1) == ' ' &&
				body.substring(1, str.length() + 1).equals(str))
			{
				isSpecial = true;
				label = label + " " + str;
				body = body.substring(str.length()+1);
			}
			
			// To handle rarer case when special word is separated from label by
			// multiple spaces or tabs
			int strIndex = body.indexOf(str);
			if (!isSpecial && strIndex >= 0 && body.length() > strIndex + str.length() && body.charAt(strIndex + str.length()) == ' ')
			{
				boolean isAllWhitespace = body.substring(0, strIndex).trim().length() == 0;
				if (isAllWhitespace)
				{
					isSpecial = true;
					body = body.substring(str.length()+strIndex);
				}
			}
			
			if (!isSpecial)
			{
				continue;
			}
			
			switch (str)
			{
			case "member":
				commentType = Token.Type.MEMBERS;
				break;
			case "global":
				commentType = Token.Type.GLOBALS;
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
				
				commentType = Token.Type.fromString(str);
			}
		}
		
		LexerToken token = new LexerToken(LexerTokenType.COMMENT, value);
		token.setCommentType(commentType);
		token.setBody(body);
		token.setSpecial(isSpecial);
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
	 * 
	 * @param checks list of checks that should be executed during scanning.
	 * @return lexer token.
	 */
	public LexerToken scanComments(AsyncTrigger checks) throws JSHintException
	{
		EventContext context;
		String ch1 = peek();
		String ch2 = peek(1);
		String rest = input.length() > 2 ? input.substring(2) : "";
		int startLine = line;
		int startChar = character;
		
		// End of unbegun comment. Raise an error and skip that input.
		if (ch1.equals("*") && ch2.equals("/"))
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
		if (!ch1.equals("/") || (!ch2.equals("*") && !ch2.equals("/")))
		{
			return null;
		}
		
		// One-line comment
		if (ch2.equals("/"))
		{
			skip(input.length()); // Skip to the EOL.
			return commentToken("//", rest, false, false);
		}
		
		String body = "";
		
		/* Multi-line comment */
		if (ch2.equals("*"))
		{
			inComment = true;
			skip(2);
			
			while (!peek().equals("*") || !peek(1).equals("/"))
			{
				if (peek().equals("")) // End of Line
				{
					body += "\n";
					
					// If we hit EOF and our comment is still unclosed,
					// trigger an error and end the comment implicitly.
					if (!nextLine(checks))
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
	 * 
	 * @return lexer token.
	 */
	public LexerToken scanKeyword()
	{
		String result = Reg.getIdentifier(input); // PORT INFO: exec regexp was moved to Reg class
		String[] keywords = {
			"if", "in", "do", "var", "for", "new",
			"try", "let", "this", "else", "case",
			"void", "with", "enum", "while", "break",
			"catch", "throw", "const", "yield", "class",
			"super", "return", "typeof", "delete",
			"switch", "export", "import", "default",
			"finally", "extends", "function", "continue",
			"debugger", "instanceof", "true", "false", "null", "async", "await"
		};
		
		if (!result.isEmpty() && ArrayUtils.indexOf(keywords, result) >= 0)
		{
			return new LexerToken(LexerTokenType.KEYWORD, result);
		}
		
		return null;
	}
	
	// PORT INFO: test regexp /^[0-9]$/ was replaced with straight check
	private boolean isDecimalDigit(String str)
	{
		if (str.length() != 1) return false;
		char c = str.charAt(0);
		return c >= '0' && c <= '9';
	}
	
	// PORT INFO: test regexp /^[0-7]$/ was replaced with straight check
	private boolean isOctalDigit(String str)
	{
		if (str.length() != 1) return false;
		char c = str.charAt(0);
		return c >= '0' && c <= '7';
	}
	
	// PORT INFO: test regexp /^[01]$/ was replaced with straight check
	private boolean isBinaryDigit(String str)
	{
		if (str.length() != 1) return false;
		char c = str.charAt(0);
		return c == '0' || c == '1';
	}
	
	private boolean isIdentifierStart(String ch)
	{
		if (ch.length() != 1) return false;
		char c = ch.charAt(0);
		return (c == '&') || (c == '_') || (c == '\\') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}
	
	/*
	 * Extract a JavaScript identifier out of the next sequence of
	 * characters or return 'null' if its not possible.
	 */
	private int identifierIndex;
	public LexerToken scanIdentifier(AsyncTrigger checks)
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
		
		String value = removeEscapeSequences(id);
		
		if (!State.inES6(true))
		{
			if (!ES5IdentifierNames.test(value))
			{
				EventContext context = new EventContext();
				context.setCode("W119");
				context.setLine(line);
				context.setCharacter(character);
				context.setData("unicode 8", "6");
				triggerAsync("warning", context, checks, () -> true);
			}
		}
		
		
		LexerToken token = new LexerToken(LexerTokenType.IDENTIFIER, value);
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
		
		if (!peek(identifierIndex).equals("u"))
		{
			return null;
		}
		
		String sequence = peek(identifierIndex + 1) + peek(identifierIndex + 2) +
			peek(identifierIndex + 3) + peek(identifierIndex + 4);
		
		if (isHex(sequence))
		{
			Integer code = Ints.tryParse(sequence, 16);
			
			if (code != null)
			{
				if ((code < UnicodeData.identifierPartTable.length && UnicodeData.identifierPartTable[code]) || isNonAsciiIdentifierPart(code))
				{
					identifierIndex += 5;
					return "\\u" + sequence;
				}
			}
			
			return null;
		}
		
		return null;
	}
	
	private String getIdentifierStart()
	{
		String chr = peek(identifierIndex);
		if (chr.length() == 0) return null;
		int code = chr.charAt(0);
		
		if (code == 92)
		{
			return readUnicodeEscapeSequence();
		}
		
		if (code < 128)
		{
			if (UnicodeData.identifierStartTable[code])
			{
				identifierIndex += 1;
				return chr;
			}
			
			return null;
		}
		
		if (isNonAsciiIdentifierStart(code))
		{
			identifierIndex += 1;
			return chr;
		}
		
		return null;
	}
	
	private String getIdentifierPart()
	{
		String chr = peek(identifierIndex);
		if (chr.length() == 0) return null;
		int code = chr.charAt(0);
		
		if (code == 92)
		{
			return readUnicodeEscapeSequence();
		}
		
		if (code < 128)
		{
			if (UnicodeData.identifierPartTable[code])
			{
				identifierIndex += 1;
				return chr;
			}
			
			return null;
		}
		
		if (isNonAsciiIdentifierPart(code))
		{
			identifierIndex += 1;
			return chr;
		}
		
		return null;
	}
	
	// PORT INFO: replace regexp /\\u([0-9a-fA-F]{4})/g was replaced with straight logic
	private String removeEscapeSequences(String id)
	{
		StringBuilder result = new StringBuilder(id);
		int uIndex = result.indexOf("\\u");
		int cIndex = uIndex + 2;
		
		while (uIndex != -1 && result.length() >= cIndex + 4)
		{
			boolean replace = true;
			for (int i = 0; i < 4; i++)
			{
				char c = result.charAt(cIndex + i);
				if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
					continue;
				replace = false;
				break;
			}
			
			if (replace)
				result.replace(uIndex, cIndex + 4, String.valueOf((char)Integer.parseInt(result.substring(cIndex, cIndex + 4), 16)));
			
			uIndex = result.indexOf("\\u", uIndex + 1);
			cIndex = uIndex + 2;
		}
		
		return result.toString();
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
	public LexerToken scanNumericLiteral(AsyncTrigger checks) throws JSHintException
	{
		EventContext context;
		int index = 0;
		String value = "";
		int length = input.length();
		String chr = peek(index);
		Predicate<String> isAllowedDigit = this::isDecimalDigit;
		int base = 10;
		boolean isLegacy = false;
		
		// Numbers must start either with a decimal digit or a point.
		
		if (!chr.equals(".") && !isDecimalDigit(chr))
		{
			return null;
		}
		
		if (!chr.equals("."))
		{
			value = peek(index);
			index += 1;
			chr = peek(index);
			
			if (value.equals("0"))
			{
				// Base-16 numbers.
				if (chr.equals("x") || chr.equals("X"))
				{
					isAllowedDigit = this::isHexDigit;
					base = 16;
					
					index += 1;
					value += chr;
				}
				
				// Base-8 numbers.
				if (chr.equals("o") || chr.equals("O"))
				{
					isAllowedDigit = this::isOctalDigit;
					base = 8;
					
					if (!State.inES6(true))
					{
						context = new EventContext();
						context.setCode("W119");
						context.setLine(line);
						context.setCharacter(character);
						context.setData("Octal integer literal", "6");
						triggerAsync("warning", context, checks, () -> true);
					}
					
					index += 1;
					value += chr;
				}
				
				// Base-2 numbers.
				if (chr.equals("b") || chr.equals("B"))
				{
					isAllowedDigit = this::isBinaryDigit;
					base = 2;
					
					if (!State.inES6(true))
					{
						context = new EventContext();
						context.setCode("W119");
						context.setLine(line);
						context.setCharacter(character);
						context.setData("Binary integer literal", "6");
						triggerAsync("warning", context, checks, () -> true);
					}
					
					index += 1;
					value += chr;
				}
				
				// Legacy base-8 numbers.
				if (isOctalDigit(chr))
				{
					isAllowedDigit = this::isOctalDigit;
					base = 8;
					isLegacy = true;
					
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
				
				// Numbers like '019' (note the 9) are not valid octals
				// but we still parse them and mark as malformed.
				if (!(isLegacy && isDecimalDigit(chr)) && !isAllowedDigit.test(chr))
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
		
		if (chr.equals("."))
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
		
		if (chr.equals("e") || chr.equals("E"))
		{
			value += chr;
			index += 1;
			chr = peek(index);
			
			if (chr.equals("+") || chr.equals("-"))
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
		String chr = peek();
		
		switch (chr)
		{
		case "'":
			context = new EventContext();
			context.setCode("W114");
			context.setLine(line);
			context.setCharacter(character);
			context.setData("\\'");
			triggerAsync("warning", context, checks, () -> State.isJsonMode());
			break;
		case "b":
			chr = "\\b";
			break;
		case "f":
			chr = "\\f";
			break;
		case "n":
			chr = "\\n";
			break;
		case "r":
			chr = "\\r";
			break;
		case "t":
			chr = "\\t";
			break;
		case "0":
			chr = "\\0";

			// Octal literals fail in strict mode.
			// Check if the number is between 00 and 07.
			final Integer n = Ints.tryParse(peek(1), 10);
			context = new EventContext();
			context.setCode("W115");
			context.setLine(line);
			context.setCharacter(character);
			triggerAsync("warning", context, checks, () -> n != null && n >= 0 && n <= 7 && State.isStrict());
			break;
		case "1":
		case "2":
		case "3":
		case "4":
		case "5":
		case "6":
		case "7":
			chr = "\\" + chr;
			context = new EventContext();
			context.setCode("W115");
			context.setLine(line);
			context.setCharacter(character);
			triggerAsync("warning", context, checks, () -> State.isStrict());
			break;
		case "u":
			String sequence = input.substring(1, 5);
			Integer code = Ints.tryParse(sequence, 16);
			if (!isHex(sequence))
			{
				// This condition unequivocally describes a syntax error.
				// JSHINT_TODO: Re-factor as an "error" (not a "warning").
				context = new EventContext();
				context.setCode("W052");
				context.setLine(line);
				context.setCharacter(character);
				context.setData("u" + sequence);
				trigger("warning", context);
			}
			
			chr = (code != null ? Character.toString((char)code.intValue()) : "\0");
			jump = 5;
			break;
		case "v":
			context = new EventContext();
			context.setCode("W114");
			context.setLine(line);
			context.setCharacter(character);
			context.setData("\\v");
			triggerAsync("warning", context, checks, () -> State.isJsonMode());
			
			chr = "\u000B";
			break;
		case "x":
			Integer x = Ints.tryParse(input.substring(1, 2), 16);
			
			context = new EventContext();
			context.setCode("W114");
			context.setLine(line);
			context.setCharacter(character);
			context.setData("\\x-");
			triggerAsync("warning", context, checks, () -> State.isJsonMode());
			
			chr = (x != null ? Character.toString((char)x.intValue()) : "\0");
			jump = 3;
			break;
		case "\\":
			chr = "\\\\";
			break;
		case "\"":
			chr = "\\\"";
			break;
		case "/":
			break;
		case "":
			allowNewLine = true;
			chr = "";
			break;
		}
		
		return ContainerFactory.createObject("char", chr, "jump", jump, "allowNewLine", allowNewLine);
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
		String ch = "";
		int startLine = line;
		int startChar = character;
		int depth = templateStarts.size();
		
		if (peek().equals("`"))
		{
			if (!State.inES6(true))
			{
				context = new EventContext();
				context.setCode("W119");
				context.setLine(line);
				context.setCharacter(character);
				context.setData("template literal syntax", "6");
				triggerAsync("warning", context, checks, () -> true);
			}
			// Template must start with a backtick.
			tokenType = LexerTokenType.TEMPLATEHEAD;
			templateStarts.add(new TemplateStart(line, character));
			depth = templateStarts.size();
			skip(1);
			pushContext(LexerContextType.TEMPLATE);
		}
		else if (inContext(LexerContextType.TEMPLATE) && peek().equals("}"))
		{
			// If we're in a template context, and we have a '}', lex a TemplateMiddle.
			tokenType = LexerTokenType.TEMPLATEMIDDLE;
		}
		else
		{
			// Go lex something else.
			return null;
		}
		
		while (!peek().equals("`"))
		{
			while ((ch = peek()).equals(""))
			{
				value += "\n";
				if (!nextLine(checks))
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
			
			if (ch.equals("$") && peek(1).equals("{"))
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
			else if (ch.equals("\\"))
			{
				UniversalContainer escape = scanEscapeSequence(checks);
				value += escape.asString("char");
				skip(escape.asInt("jump"));
			}
			else if (!ch.equals("`"))
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
		String quote = peek();
		
		// String must start with a quote.
		if (!quote.equals("\"") && !quote.equals("'"))
		{
			return null;
		}
		
		// In JSON strings must always use double quotes.
		context = new EventContext();
		context.setCode("W108");
		context.setLine(line);
		context.setCharacter(character);
		triggerAsync("warning", context, checks, () -> State.isJsonMode() && !quote.equals("\""));
		
		String value = "";
		int startLine = line;
		int startChar = character;
		boolean allowNewLine = false;
		
		skip();
		
		while (!peek().equals(quote))
		{
			if (peek().equals("")) // End Of Line
			{
				// If an EOL is not preceded by a backslash, show a warning
		        // and proceed like it was a legit multi-line string where
		        // author simply forgot to escape the newline symbol.
		        //
		        // Another approach is to implicitly close a string on EOL
		        // but it generates too many false positives.
				
				if (!allowNewLine)
				{
					// This condition unequivocally describes a syntax error.
					// JSHINT_TODO: Emit error E029 and remove W112.
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
					triggerAsync("warning", context, checks, () -> !State.getOption().test("multistr"));
					
					context = new EventContext();
					context.setCode("W042");
					context.setLine(line);
					context.setCharacter(character);
					triggerAsync("warning", context, checks, () -> State.isJsonMode() && State.getOption().test("multistr"));
				}
				
				// If we get an EOF inside of an unclosed string, show an
		        // error and implicitly close it at the EOF point.
				
				if (!nextLine(checks))
				{	
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
				String chr = peek();
				int jump = 1; 	// A length of a jump, after we're done
								// parsing this character.
				
				if (chr.length() > 0 && chr.charAt(0) < ' ')
				{
					// Warn about a control character in a string.
					context = new EventContext();
					context.setCode("W113");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("<non-printable>");
					triggerAsync("warning", context, checks, () -> true);
				}
				
				// Special treatment for some escaped characters.
				if (chr.equals("\\"))
				{
					UniversalContainer parsed = scanEscapeSequence(checks);
					chr = parsed.asString("char");
					jump = parsed.asInt("jump");
					allowNewLine = parsed.asBoolean("allowNewLine");
				}
				
				// If char is the empty string, end of the line has been reached. In
				// this case, `this.char` should not be incremented so that warnings
				// and errors reported in the subsequent loop iteration have the
				// correct character column offset.
				if (!chr.equals("")) {
					value += chr;
					skip(jump);
				}
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
	public LexerToken scanRegExp(AsyncTrigger checks) throws JSHintException
	{
		AtomicInteger index = new AtomicInteger(0);
		int length = input.length();
		AtomicReference<String> chr = new AtomicReference<String>(peek());
		StringBuilder value = new StringBuilder(chr.get());
		StringBuilder body = new StringBuilder();
		List<Integer> groupReferences = new ArrayList<Integer>();
		StringBuilder allFlags = new StringBuilder();
		StringBuilder es5Flags = new StringBuilder();
		AtomicBoolean malformed = new AtomicBoolean();
		boolean isCharSet = false;
		boolean isCharSetRange = false;
		//JSHINT_BUG: isGroup variable isn't used anywhere
		boolean isQuantifiable = false;
		boolean hasInvalidQuantifier = false;
		BooleanSupplier hasUFlag = () -> allFlags.indexOf("u") > -1;
		StringBuilder escapedChars = new StringBuilder();
		AtomicInteger groupCount = new AtomicInteger(0);
		boolean terminated = false;
		String malformedDesc = "";
		
		Supplier<String> scanRegexpEscapeSequence = () -> {
			index.addAndGet(1);
			chr.set(peek(index.get()));
			
			if (Reg.isNonzeroDigit(chr.get()))
			{
				StringBuilder sequence = new StringBuilder(chr.get());
				String next = peek(index.get() + 1);
				while (Reg.isNonzeroDigit(next) || next.equals("0"))
				{
					index.addAndGet(1);
					chr.set(next);
					sequence.append(chr.get());
					body.append(chr.get());
					value.append(chr.get());
					next = peek(index.get() + 1);
				}
				groupReferences.add(Ints.tryParse(sequence.toString()));
				return sequence.toString();
			}
			
			escapedChars.append(chr.get());
			
			if (chr.get().equals("u") && peek(index.get() + 1).equals("{"))
			{
				int x = index.get() + 2;
				StringBuilder sequence = new StringBuilder("u{");
				String next = peek(x);
				while (isHex(next))
				{
					sequence.append(next);
					x += 1;
					next = peek(x);
				}
				
				if (!next.equals("}"))
				{
					EventContext context = new EventContext();
					context.setCode("E016");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("Invalid Unicode escape sequence");
					triggerAsync("error", context, checks, hasUFlag);
				}
				else if (sequence.length() > 2)
				{
					sequence.append("}");
					body.append(sequence);
					value.append(sequence);
					index.set(x + 1);
					return sequence.toString();
				}
			}
			
			// Unexpected control character
			if (chr.get().length() > 0 && chr.get().charAt(0) < ' ')
			{
				malformed.set(true);
				EventContext context = new EventContext();
				context.setCode("W048");
				context.setLine(line);
				context.setCharacter(character);
				triggerAsync("warning", context, checks, () -> true);
			}
			
			// Unexpected escaped character
			if (chr.get().equals("<"))
			{
				malformed.set(true);
				EventContext context = new EventContext();
				context.setCode("W049");
				context.setLine(line);
				context.setCharacter(character);
				context.setData(chr.get());
				triggerAsync("warning", context, checks, () -> true);
			}
			
			index.addAndGet(1);
			body.append(chr.get());
			value.append(chr.get());
			
			return chr.get();
		};
		
		BooleanSupplier checkQuantifier = () -> {
			int lookahead = index.get();
			StringBuilder lowerBound = new StringBuilder();
			StringBuilder upperBound = new StringBuilder();
			
			String next = peek(lookahead + 1);
			
			while (Reg.isDecimalDigit(next))
			{
				lookahead += 1;
				lowerBound.append(next);
				next = peek(lookahead + 1);
			}
			
			if (lowerBound.length() == 0)
			{
				return false;
			}
			
			if (next.equals("}"))
			{
				return true;
			}
			
			if (!next.equals(","))
			{
				return false;
			}
			
			lookahead += 1;
			next = peek(lookahead + 1);
			
			while (Reg.isDecimalDigit(next))
			{
				lookahead += 1;
				upperBound.append(next);
				next = peek(lookahead + 1);
			}
			
			if (!next.equals("}"))
			{
				return false;
			}
			
			if (upperBound.length() != 0)
			{
				return Ints.tryParse(lowerBound.toString()) <= Ints.tryParse(upperBound.toString());
			}
			
			return true;
		};
		
		Function<String, String> translateUFlag = b -> {
			// The BMP character to use as a replacement for astral symbols when
			// translating an ES6 "u"-flagged pattern to an ES5-compatible
			// approximation.
			// Note: replacing with '\uFFFF' enables false positives in unlikely
			// scenarios. For example, `[\\u{1044f}-\\u{10440}]` is an invalid pattern
			// that would not be detected by this substitution.
			String astralSubstitute = "\uFFFF";
			
			return Reg.replaceAllPairedSurrogate(Reg.replaceAllUnicodeEscapeSequence(b,
				// Replace every Unicode escape sequence with the equivalent BMP
				// character or a constant ASCII code point in the case of astral
				// symbols. (See the above note on `astralSubstitute` for more
				// information.)
				(g0, g1) -> {
					Integer codePoint = Ints.tryParse(g1, 16);
					
					if (codePoint == null || codePoint > 0x10FFFF)
					{
						malformed.set(true);
						EventContext context = new EventContext();
						context.setCode("E016");
						context.setLine(line);
						context.setCharacter(character);
						context.setData(String.valueOf(character));
						trigger("error", context);
						
						return "";
					}
					String literal = new String(Character.toChars(codePoint));
					
					if (Reg.isSyntaxChars(literal))
					{
						return g0;
					}
					
					if (codePoint <= 0xFFFF)
					{
						return new String(Character.toChars(codePoint)); //JSHINT_BUG: there is already literal variable it can be used here
					}					
					return astralSubstitute;
				}),
				// Replace each paired surrogate with a single ASCII symbol to avoid
				// throwing on regular expressions that are only valid in combination
				// with the "u" flag.
				astralSubstitute);
		};
		
		// Regular expressions must start with '/'
		if (!prereg || !chr.get().equals("/"))
		{
			return null;
		}
		
		index.addAndGet(1);
		terminated = false;
		
		// Try to get everything in between slashes. A couple of
	    // cases aside (see scanRegexpEscapeSequence) we don't really
	    // care whether the resulting expression is valid or not.
	    // We will check that later using the RegExp object.
		
		while (index.get() < length)
		{
			// Because an iteration of this loop may terminate in a number of
			// distinct locations, `isCharSetRange` is re-set at the onset of
			// iteration.
			isCharSetRange &= chr.get().equals("-");
			chr.set(peek(index.get()));
			value.append(chr.get());
			body.append(chr.get());
			
			if (isCharSet)
			{
				if (chr.get().equals("]"))
				{
					if (!peek(index.get() - 1).equals("\\") || peek(index.get() - 2).equals("\\"))
					{
						isCharSet = false;
					}
				}
				else if (chr.get().equals("-"))
				{
					isCharSetRange = true;
				}
			}
				
			if (chr.get().equals("\\"))
			{
				String escapeSequence = scanRegexpEscapeSequence.get();
				
				if (isCharSet && (peek(index.get()).equals("-") || isCharSetRange) &&
					Reg.isCharClasses(escapeSequence))
				{
					EventContext context = new EventContext();
					context.setCode("E016");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("Character class used in range");
					triggerAsync("error", context, checks, hasUFlag);
				}
				
				continue;
			}
				
			if (chr.get().equals("{") && !hasInvalidQuantifier)
			{
				hasInvalidQuantifier = !checkQuantifier.getAsBoolean();
			}
			
			if (chr.get().equals("["))
			{
				isCharSet = true;
				index.addAndGet(1);
				continue;
			}
			else if (chr.get().equals("("))
			{
				if (peek(index.get() + 1).equals("?") &&
					(peek(index.get() + 2).equals("=") || peek(index.get() + 2).equals("!")))
				{
					isQuantifiable = true;
				}
			}
			else if (chr.get().equals(")"))
			{
				if (isQuantifiable)
				{
					isQuantifiable = false;
					
					if (Reg.isQuantifiers(peek(index.get() + 1)))
					{
						EventContext context = new EventContext();
						context.setCode("E016");
						context.setLine(line);
						context.setCharacter(character);
						context.setData("Quantified quantifiable");
						triggerAsync("error", context, checks, hasUFlag);
					}
				}
				else
				{
					groupCount.addAndGet(1);
				}
			}
			else if (chr.get().equals("/"))
			{
				body.deleteCharAt(body.length() - 1);
				terminated = true;
				index.addAndGet(1);
				break;
			}
			
			index.addAndGet(1);
		}
		
		// A regular expression that was never closed is an
	    // error from which we cannot recover.
		
		if (!terminated)
		{
			EventContext context = new EventContext();
			context.setCode("E015");
			context.setLine(line);
			context.setCharacter(from);
			trigger("error", context);
			
			context = new EventContext();
			context.setLine(line);
			context.setFrom(from);
			trigger("fatal", context);
			return null;
		}
		
		// Parse flags (if any).
		
		while (index.get() < length)
		{
			chr.set(peek(index.get()));
			
			if (!Reg.isRegexpFlag(chr.get()))
			{
				break;
			}
			if (chr.get().equals("y"))
			{
				if (!State.inES6(true))
				{
					EventContext context = new EventContext();
					context.setCode("W119");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("Sticky RegExp flag", "6");
					triggerAsync("warning", context, checks, () -> true);
				}
			}
			else if (chr.get().equals("u"))
			{
				if (!State.inES6(true))
				{
					EventContext context = new EventContext();
					context.setCode("W119");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("Unicode RegExp flag", "6");
					triggerAsync("warning", context, checks, () -> true);
				}
				
				boolean hasInvalidEscape = false;
				
				boolean hasInvalidGroup = groupReferences.stream().anyMatch(groupReference -> {
					if (groupReference > groupCount.get())
					{
						return true;
					}
					return false;
				});
				
				if (hasInvalidGroup)
				{
					hasInvalidEscape = true;
				}
				else
				{
					hasInvalidEscape = !escapedChars.chars().allMatch(c -> {
						char escapedChar = (char)c;
						return escapedChar == 'u' ||
							escapedChar == '/' ||
							Reg.isCharClasses(String.valueOf(escapedChar)) ||
							Reg.isSyntaxChars(String.valueOf(escapedChar));
					});
				}
				
				if (hasInvalidEscape)
				{
					malformedDesc = "Invalid escape";
				}
				else if (hasInvalidQuantifier)
				{
					malformedDesc = "Invalid quantifier";
				}
				
				body.replace(0, body.length(), translateUFlag.apply(body.toString()));
			}
			else if (chr.get().equals("s"))
			{
				if (!State.inES9())
				{
					EventContext context = new EventContext();
					context.setCode("W119");
					context.setLine(line);
					context.setCharacter(character);
					context.setData("DotAll RegExp flag", "9");
					triggerAsync("warning", context, checks, () -> true);
				}
				if (value.indexOf("s") > -1)
				{
					malformedDesc = "Duplicate RegExp flag";
				}
			}
			else
			{
				es5Flags.append(chr.get());
			}
			
			if (allFlags.indexOf(chr.get()) > -1)
			{
				malformedDesc = "Duplicate RegExp flag";
			}
			allFlags.append(chr.get());
			
			value.append(chr.get());
			allFlags.append(chr.get());
			index.addAndGet(1);
		}
		
		if (allFlags.indexOf("u") == -1)
		{
			EventContext context = new EventContext();
			context.setCode("W147");
			context.setLine(line);
			context.setCharacter(character);
			triggerAsync("warning", context, checks, () -> State.getOption().test("regexpu"));
		}
		
		// Check regular expression for correctness.
		
		// PORT INFO: tried several JAVA RE engines to parse javascript regexps:
		// 	* org.jruby.joni:joni:2.1.25 - produced infinite loop on regexps like /\u0000/
		//  * com.google.re2j:re2j:1.2 - there were a lot of errors on valid js regexps
		//	* com.basistech.tclre:tcl-regex:0.14.0 - there were many errors on valid js regexps, but much better than re2j
		//	* com.github.florianingerl.util:regex:1.1.6 - slightly better than tcl-regex
		//	* xerces:xercesImpl:2.12.0 - slightly better than florianingerl.util.regex
		//	* java.util.regex.Pattern - slightly better than xercesImpl
		//	* org.mozilla:rhino:1.7.10 - very good compatibility, only several valid js regexps weren't parsed
		//	* javax.script.ScriptEngine - slightly better than rhino
		// 
		// RegExp engine inside Nashorn engine very close to original JS RegExp engine
		// and it doesn't require external dependency so it's used as a validator of javascript regular expressions
		try
		{
			jsEngine.eval("/" + body.toString() + "/" + es5Flags);
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
		
		if (StringUtils.isNotEmpty(malformedDesc))
		{
			malformed.set(true);
			EventContext context = new EventContext();
			context.setCode("E016");
			context.setLine(line);
			context.setCharacter(character);
			context.setData(malformedDesc);
			trigger("error", context);
		}
		else if (allFlags.indexOf("s") > -1 && !Reg.isDot(body.toString()))
		{
			EventContext context = new EventContext();
			context.setCode("W148");
			context.setLine(line);
			context.setCharacter(character);
			trigger("warning", context);
		}
		
		LexerToken token = new LexerToken(LexerTokenType.REGEXP, value.toString());
		token.setMalformed(malformed.get());
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
	 * Produce the next raw token or return 'null' if no tokens can be matched.
	 * This method skips over all space characters.
	 */
	public LexerToken next(AsyncTrigger checks) throws JSHintException
	{
		from = character;
		
		// Move to the next non-space character.
		while (Reg.isWhitespace(peek()))
		{
			from += 1;
			skip();
		}
		
		// Methods that work with multi-line structures and move the
	    // character pointer.
		
		LexerToken match = scanComments(checks);
		if (match == null) match = scanStringLiteral(checks);
		if (match == null) match = scanTemplateLiteral(checks);
		
		if (match != null)
		{
			return match;
		}
		
		// Methods that don't move the character pointer.
		
		match = scanRegExp(checks);
		if (match == null) match = scanPunctuator();
		if (match == null) match = scanKeyword();
		if (match == null) match = scanIdentifier(checks);
		if (match == null) match = scanNumericLiteral(checks);
		
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
	public boolean nextLine(AsyncTrigger checks) throws JSHintException
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
			triggerAsync("warning", context, checks, () -> true);
	    }
	    
	    input = StringUtils.replace(input, "\t", State.getTab());
	    
	    // If there is a limit on line length, warn when lines get too
	    // long.
	    if (!ignoringLinterErrors && State.getOption().test("maxlen") &&
	    	State.getOption().asInt("maxlen") < input.length())
	    {
	    	boolean inComment = this.inComment ||
	    			inputTrimmed.startsWith("//") ||
	    			inputTrimmed.startsWith("/*");
	    	
	    	boolean shouldTriggerError = !inComment || !Reg.isMaxlenException(inputTrimmed);
	    	
	    	if (shouldTriggerError)
	    	{
	    		context = new EventContext();
				context.setCode("W101");
				context.setLine(line);
				context.setCharacter(input.length());
				triggerAsync("warning", context, checks, () -> true);
	    	}
	    }
	    
	    return true;
	}
	
	// Produce a token object.
	private Token create(Token.Type type, String value, AsyncTrigger checks)
	{
		return create(type, value, false, null, checks);
	}
	
	private Token create(Token.Type type, String value, boolean isProperty, LexerToken token, AsyncTrigger checks)
	{
		Token obj = null;
		
		if (type != Token.Type.ENDLINE && type != Token.Type.END)
		{
			prereg = false;
		}
		
		if (type == Token.Type.PUNCTUATOR)
		{
			switch (value)
			{
			case ".":
	        case ")":
	        case "~":
	        case "#":
	        case "]":
	        case "}":
	        case "++":
	        case "--":
	        	prereg = false;
	        	break;
	        default:
	        	prereg = true;
			}
			
			obj = ObjectUtils.defaultIfNull(ObjectUtils.clone(ObjectUtils.defaultIfNull(State.getSyntax().get(value), State.getSyntax().get("(error)"))), new Token());
		}
		
		if (type == Token.Type.IDENTIFIER)
		{
			if (value.equals("return") || value.equals("case") || value.equals("yield") ||
				value.equals("typeof") || value.equals("instanceof") || value.equals("void") ||
				value.equals("await"))
			{
				prereg = true;
			}
			
			if (State.getSyntax().containsKey(value))
			{
				obj = ObjectUtils.defaultIfNull(ObjectUtils.clone(ObjectUtils.defaultIfNull(State.getSyntax().get(value), State.getSyntax().get("(error)"))), new Token());
			}
		}
		
		if (type == Token.Type.TEMPLATE || type == Token.Type.TEMPLATEMIDDLE)
		{
			prereg = true;
		}
		
		if (obj == null)
		{
			obj = ObjectUtils.defaultIfNull(ObjectUtils.clone(State.getSyntax().get(type.toString())), new Token());
		}
		
		obj.setIdentifier(type == Token.Type.IDENTIFIER);
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
		
		obj.setCheck(checks::check);
		
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
				if (nextLine(checks))
				{
					return create(Token.Type.ENDLINE, "", checks);
				}
				
				if (exhausted)
				{
					return null;
				}
				
				exhausted = true;
				return create(Token.Type.END, "", checks);
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
					context.setData(peek());
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
				triggerAsync("String", context, checks, () -> true);
				return create(Token.Type.STRING, token.getValue(), false, token, checks);
			case TEMPLATEHEAD:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("TemplateHead", context);
				return create(Token.Type.TEMPLATE, token.getValue(), false, token, checks);
			case TEMPLATEMIDDLE:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("TemplateMiddle", context);
				return create(Token.Type.TEMPLATEMIDDLE, token.getValue(), false, token, checks);
			case TEMPLATETAIL:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("TemplateTail", context);
				return create(Token.Type.TEMPLATETAIL, token.getValue(), false, token, checks);
			case NOSUBSTTEMPLATE:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setStartLine(token.getStartLine());
				context.setStartChar(token.getStartChar());
				context.setValue(token.getValue());
				trigger("NoSubstTemplate", context);
				return create(Token.Type.NOSUBSTTEMPLATE, token.getValue(), false, token, checks);
			case IDENTIFIER:
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setName(token.getValue());
				context.setRawName(token.getText());
				context.setProperty(State.currToken().getId().equals("."));
				triggerAsync("Identifier", context, checks, () -> true);
			case KEYWORD:
				return create(Token.Type.IDENTIFIER, token.getValue(), State.currToken().getId().equals("."), token, checks);
			case NUMERICLITERAL:
				if (token.isMalformed())
				{
					// This condition unequivocally describes a syntax error.
					// JSHINT_TODO: Re-factor as an "error" (not a "warning").
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
				triggerAsync("warning", context, checks, () -> token.getBase() == 16 && State.isJsonMode());
				
				context = new EventContext();
				context.setCode("W115");
				context.setLine(line);
				context.setCharacter(character);
				triggerAsync("warning", context, checks, () -> State.isStrict() && token.getBase() == 8 && token.isLegacy());
				
				context = new EventContext();
				context.setLine(line);
				context.setCharacter(character);
				context.setFrom(from);
				context.setValue(token.getValue());
				context.setBase(token.getBase());
				context.setMalformed(token.isMalformed());
				trigger("Number", context);
				
				return create(Token.Type.NUMBER, token.getValue(), checks);
			case REGEXP:
				return create(Token.Type.REGEXP, token.getValue(), checks);
			case COMMENT:
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
			default:
				return create(Token.Type.PUNCTUATOR, token.getValue(), checks);
			}
		}
	}
	
	public static class LexerToken
	{	
		private LexerTokenType type = LexerTokenType.NONE;
		private String value = null;
		private Token.Type commentType = null;
		private String body = null;
		private boolean isSpecial = false;
		private boolean isMalformed = false;
		private String text = null;
		private int tokenLength = 0;
		private int base = 0;
		private boolean isLegacy = false;
		private boolean isUnclosed = false;
		private String quote;
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

		public Token.Type getCommentType()
		{
			return commentType;
		}

		private void setCommentType(Token.Type commentType)
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

		public String getQuote()
		{
			return quote;
		}

		private void setQuote(String quote)
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