package org.jshint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.CharMatcher;

/**
 * Regular expressions. Some of these are stupidly long.
 */
public class Reg {
	private Reg() {
	}

	// Unsafe comment or string (ax)
	// JSHINT_BUG: this regular expression can be removed, not used anywhere
	public static final Pattern UNSAFE_STRING = Pattern.compile("@cc|<\\/?|script|\\]\\s*\\]|<\\s*!|&lt",
			Pattern.CASE_INSENSITIVE);

	// Characters in strings that need escaping (nx and nxg)
	// JSHINT_BUG: this regular expression can be removed, not used anywhere
	public static final Pattern NEED_ESC = Pattern.compile(
			"[\\u0000-\\u001f&<\"\\/\\\\\\u007f-\\u009f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufeff\\ufff0-\\uffff]");

	// Star slash (lx)
	// JSHINT_BUG: this regular expression can be removed, not used anywhere
	public static final Pattern STAR_SLASH = Pattern.compile("\\*\\/");

	// PORT INFO: moved regexp from infix("(")
	public static final Pattern UPPERCASE_IDENTIFIER = Pattern.compile("^[A-Z]([A-Z0-9_$]*[a-z][A-Za-z0-9_$]*)?$");

	// Identifier (ix)
	// PORT INFO: replacement for regexp /^([a-zA-Z_$][a-zA-Z0-9_$]*)$/
	public static boolean isIdentifier(String input) {
		if (input == null || input.length() == 0)
			return false;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$')
				continue;
			if (i != 0 && c >= '0' && c <= '9')
				continue;
			return false;
		}

		return true;
	}

	// JavaScript URL (jx)
	// PORT INFO: replacement for regexp
	// /^(?:javascript|jscript|ecmascript|vbscript|livescript)\s*:/i
	public static boolean isJavascriptUrl(String input) {
		if (input == null || input.length() == 0 || Character.isWhitespace(input.charAt(0)) || input.indexOf(":") == -1)
			return false;

		switch (input.substring(0, input.indexOf(':')).trim().toLowerCase()) {
			case "javascript":
			case "jscript":
			case "ecmascript":
			case "vbscript":
			case "livescript":
				return true;
		}

		return false;
	}

	// Catches /* falls through */ comments (ft)
	// PORT INFO: replacement for regexp /^\s*falls?\sthrough\s*$/
	public static boolean isFallsThrough(String input) {
		if (input == null || input.length() == 0)
			return false;

		input = input.trim();
		if (input.startsWith("falls") && Character.isWhitespace(input.charAt(5))
				&& input.substring(6).equals("through"))
			return true;
		if (input.startsWith("fall") && Character.isWhitespace(input.charAt(4)) && input.substring(5).equals("through"))
			return true;

		return false;
	}

	// very conservative rule (eg: only one space between the start of the comment
	// and the first character)
	// to relax the maxlen option
	// PORT INFO: replacement for regexp /^(?:(?:\/\/|\/\*|\*) ?)?[^ ]+$/
	public static boolean isMaxlenException(String input) {
		if (input == null || input.length() == 0 || input.charAt(0) == ' ')
			return false;

		if (input.equals("//") || input.equals("/*") || input.equals("*"))
			return true;

		int i = 0;
		if (input.startsWith("//") || input.startsWith("/*"))
			i = 2;
		else if (input.startsWith("*"))
			i = 1;
		if (i > 0 && input.charAt(i) == ' ')
			i = i + 1;

		return i < input.length() && input.substring(i).indexOf(' ') == -1;
	}

	// Node.js releases prior to version 8 include a version of the V8 engine which
	// incorrectly interprets the character class escape `\s`. The following
	// regular expression may be replaced with `/\s/` when JSHint removes support
	// for Node.js versions prior to 8.
	// Source:
	// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
	// PORT INFO: replacement for regexp /[
	// \f\n\r\t\v\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]/
	public static boolean isWhitespace(String input) {
		if (input == null || input.length() == 0)
			return false;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == ' ' || c == '\f' || c == '\n' || c == '\r' || c == '\t' || c == '\u000b' || c == '\u00a0'
					|| c == '\u1680' || (c >= '\u2000' && c <= '\u200a') || c == '\u2028' || c == '\u2029'
					|| c == '\u202f' || c == '\u205f' || c == '\u3000' || c == '\ufeff')
				return true;
		}

		return false;
	}

	// PORT INFO: replacement for regexp /^[1-9]$/
	public static boolean isNonzeroDigit(String input) {
		if (input == null || input.length() != 1)
			return false;

		char c = input.charAt(0);
		return c >= '1' && c <= '9';
	}

	// PORT INFO: replacement for regexp /^[0-9]$/
	public static boolean isDecimalDigit(String input) {
		if (input == null || input.length() != 1)
			return false;

		char c = input.charAt(0);
		return c >= '0' && c <= '9';
	}

	// PORT INFO: replacement for regexp /[\^$\\.*+?()[\]{}|]/
	public static boolean isSyntaxChars(String input) {
		if (input == null || input.length() == 0)
			return false;

		return StringUtils.containsAny(input, '^', '$', '\\', '.', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|');
	}

	// PORT INFO: replacement for regexp /[*+?{]/
	public static boolean isQuantifiers(String input) {
		if (input == null || input.length() == 0)
			return false;

		return StringUtils.containsAny(input, '*', '+', '?', '{');
	}

	// PORT INFO: replacement for regexp /[fnrtv]/
	public static boolean isControlEscapes(String input) {
		if (input == null || input.length() == 0)
			return false;

		return StringUtils.containsAny(input, 'f', 'n', 'r', 't', 'v');
	}

	// PORT INFO: replacement for regexp /[dDsSwW]/
	public static boolean isCharClasses(String input) {
		if (input == null || input.length() == 0)
			return false;

		return StringUtils.containsAny(input, 'd', 'D', 's', 'S', 'w', 'W', 'p', 'P');
	}

	// Identifies the "dot" atom in regular expressions
	// PORT INFO: replacement for regexp /(^|[^\\])(\\\\)*\./
	public static boolean isDot(String input) {
		if (input == null || input.length() == 0 || input.indexOf('.') == -1)
			return false;

		int i, j;
		for (j = input.indexOf('.'); j != -1; j = input.indexOf('.', j + 1)) {
			i = 0;
			while (j - i - 1 >= 0 && input.charAt(j - i - 1) == '\\')
				i++;

			if (j == 0 || i % 2 == 0)
				return true;
		}

		return false;
	}

	// PORT INFO: CUSTOM FUNCTIONS, WHICH ARE CREATED TO REPLACE REGULAR EXPRESSIONS
	// TO IMPROVE PERFORMANCE

	// PORT INFO: moved regexp from JSHint.checkOption and JSHint.lintingDirective
	// functions, replacement for regexp /^[+-]W\d{3}$/g
	public static boolean isOptionMessageCode(String input) {
		if (input == null || input.length() != 5)
			return false;

		return (input.charAt(0) == '+' || input.charAt(0) == '-') && input.charAt(1) == 'W'
				&& StringUtils.isNumeric(input.substring(2));
	}

	// PORT INFO: moved regexp from Style.linter.on("Identifier") function,
	// replacement for regexp /^[A-Z0-9_]*$/
	public static boolean isUppercaseIdentifier(String input) {
		if (input == null)
			return false;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || c == '_'))
				return false;
		}

		return true;
	}

	// PORT INFO: moved regexp from Cli.isIgnored function, replacement for
	// /^[^\/\\]*[\/\\]?$/
	public static boolean isEndsWithOneOrZeroSlash(String input) {
		int slashPos = input.indexOf('/');
		int backslashPos = input.indexOf('\\');
		return (slashPos == -1 || slashPos == input.length() - 1)
				&& (backslashPos == -1 || backslashPos == input.length() - 1);
	}

	// PORT INFO: moved regexp from Lexer.scanRegExp function, replacement for
	// /[gimyus]/
	public static boolean isRegexpFlag(String input) {
		if (input == null || input.length() == 0)
			return false;

		return StringUtils.containsAny(input, 'g', 'i', 'm', 'y', 'u', 's');
	}

	// PORT INFO: created handy short test function to execute regexp patterns
	// against strings
	public static boolean test(Pattern p, String input) {
		return p.matcher(StringUtils.defaultString(input)).find();
	}

	// PORT INFO: moved regexp from Lexer.scanKeyword function, replacement for
	// regexp /^[a-zA-Z_$][a-zA-Z0-9_$]*/
	public static String getIdentifier(String input) {
		if (input == null || input.length() == 0)
			return "";

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$')
				continue;
			if (i != 0 && c >= '0' && c <= '9')
				continue;
			return input.substring(0, i);
		}

		return "";
	}

	// PORT INFO: moved regexp from Cli.extract and Cli.extractOffsets functions,
	// replacement for regexp /^(\s*)/
	public static String getLeftWhitespace(String input) {
		if (input == null || input.length() == 0)
			return "";

		for (int i = 0; i < input.length(); i++)
			if (!Character.isWhitespace(input.charAt(i)))
				return input.substring(0, i);

		return "";
	}

	// PORT INFO: moved regexp from Style.linter.on("Identifier") function,
	// replacement for regexp /^_+|_+$/g
	public static String trimUnderscores(String input) {
		if (input == null || input.length() == 0)
			return input;

		int i = 0, j = input.length() - 1;
		while (input.charAt(i) == '_')
			i++;
		while (input.charAt(j) == '_')
			j--;

		return input.substring(i, j + 1);
	}

	// PORT INFO: moved replace regexp logic from JSHint.lint function
	public static String blankDelimiteredText(String input, Pattern p) {
		if (input == null || input.length() == 0)
			return input;

		Matcher m = p.matcher(input);
		StringBuffer buffer = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(buffer, "");
			buffer.append(CharMatcher.breakingWhitespace().negate().replaceFrom(m.group(0), ' '));
		}
		m.appendTail(buffer);

		return buffer.toString();
	}

	// PORT INFO: moved regexp from JHint.supplant function, replacement for regexp
	// /\{([^{}]*)\}/g
	public static String replaceMessageSupplant(String input, LinterWarning data) {
		if (input == null || input.length() == 0)
			return input;

		StringBuilder output = new StringBuilder();

		int i = 0, j = 0;
		boolean isSupplant = false;
		for (; i < input.length(); i++) {
			if (input.charAt(i) == '{') {
				isSupplant = true;
				output.append(input.substring(j, i));
				j = i;
			} else if (input.charAt(i) == '}' && isSupplant) {
				isSupplant = false;
				output.append(StringUtils.defaultString(data.getSubstitution(input.substring(j + 1, i)),
						input.substring(j, i + 1)));
				j = i + 1;
			}
		}

		if (j < input.length()) {
			output.append(input.substring(j));
		}

		return output.toString();
	}

	// PORT INFO: moved regexp from Lexer.scanRegExp function, replacement for
	// regexp /[\uD800-\uDBFF][\uDC00-\uDFFF]/g
	public static String replaceAllUnicodeEscapeSequence(String input, BiFunction<String, String, String> replacer) {
		if (input == null || input.isEmpty() || input.indexOf("\\u") == -1)
			return input;

		StringBuilder output = new StringBuilder();
		int i, j;
		for (i = 0, j = input.indexOf("\\u"); j != -1; j = input.indexOf("\\u", j + 1)) {
			output.append(input.substring(i, j));
			i = j;

			int startPos = j + 2;
			int endPos = startPos + 4;
			if (startPos >= input.length())
				break;

			String g0;
			if (input.charAt(startPos) == '{') {
				startPos++;
				endPos = input.indexOf('}', startPos);
				if (endPos < 0 || startPos == endPos) {
					j = startPos;
					continue;
				}
				g0 = input.substring(j, endPos + 1);
			} else {
				if (endPos > input.length())
					break;
				g0 = input.substring(j, endPos);
			}

			String g1 = input.substring(startPos, endPos);
			if (StringUtils.containsOnly(g1, "0123456789abcdefABCDEF")) {
				output.append(replacer.apply(g0, g1));
				i = j + g0.length();
			}
		}
		output.append(input.substring(i));

		return output.toString();
	}

	// PORT INFO: moved regexp from Lexer.scanRegExp function, replacement for
	// regexp /\\u\{([0-9a-fA-F]+)\}|\\u([a-fA-F0-9]{4})/g
	public static String replaceAllPairedSurrogate(String input, String replacement) {
		if (input == null || input.isEmpty())
			return input;

		StringBuilder output = new StringBuilder();
		int i, j;
		for (i = 0, j = 0; j < input.length() - 1; j++) {
			if (Character.isSurrogatePair(input.charAt(j), input.charAt(j + 1))) {
				output.append(input.substring(i, j));
				output.append(replacement);
				j++;
				i = j + 1;
			}
		}
		output.append(input.substring(i));

		return output.toString();
	}

	// PORT INFO: moved regexp from JHint.escapeRegex function, replacement for
	// regexp /[-\/\\^$*+?.()|[\]{}]/g
	public static String escapeRegexpChars(String input) {
		if (input == null || input.length() == 0)
			return input;

		StringBuilder output = new StringBuilder();

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '-' || c == '/' || c == '\\' || c == '^' || c == '$' || c == '*' || c == '+' || c == '?'
					|| c == '.' || c == '(' || c == ')' || c == '|' || c == '[' || c == ']' || c == '{' || c == '}')
				output.append("\\");
			output.append(c);
		}

		return output.toString();
	}

	// PORT INFO: moved regexp from JHint.addEvalCode function, replacement for
	// regexp /([^\\])(\\*)\2\\n/g
	public static String unescapeNewlineChars(String input) {
		if (input == null || input.length() == 0 || input.indexOf("\\n") == -1)
			return input;

		StringBuilder output = new StringBuilder();

		int i, j, k;
		for (i = 0, j = input.indexOf("\\n"), k = j; j != -1; j = input.indexOf("\\n", k + 1), k = j) {
			while (input.charAt(j) == '\\')
				j--;

			if (j < 0 || ((k - j) % 2) == 0)
				continue;
			output.append(input.substring(i, j + 1)).append("\n");
			i = k + 2;
		}

		if (i < input.length())
			output.append(input.substring(i));

		return output.toString();
	}

	// PORT INFO: moved regexp from Cli.extractOffsets, Cli.extract and
	// Lexer(String)
	// replacement for regexp /\r\n|\n|\r/ and replace(/\r\n/g, "\n").replace(/\r/g,
	// "\n").split("\n");
	public static String[] splitByEOL(String input) {
		if (input == null || input.length() == 0)
			return new String[] { "" };

		List<String> output = new ArrayList<>();

		int start = 0;
		int end = 0;
		char prevChar = '\n';
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			end = i;

			if (prevChar == '\r' && c == '\n') {
				start = i + 1;
			} else if (c == '\n' || c == '\r') {
				if (start == end)
					output.add("");
				else
					output.add(input.substring(start, end));

				start = i + 1;
			}

			prevChar = c;
		}

		if (start >= input.length())
			output.add("");
		else
			output.add(input.substring(start));

		return output.toArray(new String[output.size()]);
	}

	// PORT INFO: moved regexp from Cli.mergeCliPrereq function, replacement for
	// regexp /\s*,\s*/
	public static String[] splitByComma(String input) {
		if (input == null || input.length() == 0)
			return new String[] { "" };

		List<String> output = new ArrayList<>();

		int i, j, k;
		for (i = 0, j = 0, k = 0; j < input.length(); j++) {
			if (input.charAt(j) == ',') {
				if (i == j)
					output.add("");
				else
					output.add(input.substring(i, j - k));

				while (j + 1 < input.length() && Character.isWhitespace(input.charAt(j + 1)))
					j++;

				i = j + 1;
			} else if (Character.isWhitespace(input.charAt(j)))
				k++;
			else
				k = 0;
		}

		if (i >= input.length())
			output.add("");
		else
			output.add(input.substring(i));

		return output.toArray(new String[output.size()]);
	}
}