package org.jshint.test.unit;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.jshint.JSHint;
import org.jshint.LinterGlobals;
import org.jshint.LinterOptions;
import org.jshint.Token;
import org.jshint.DataSummary;
import org.jshint.ImpliedGlobal;
import org.jshint.test.helpers.TestHelper;

import com.github.jshaptic.js4j.UniversalContainer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for the parser/tokenizer
 */
public class TestParser extends Assert {
	private TestHelper th = new TestHelper();

	@BeforeMethod
	private void setupBeforeMethod() {
		th.newTest();
	}

	/**
	 * The warning for this input was intentionally disabled after research into
	 * its justification produced no results.
	 */
	@Test
	public void testUnsafe() {
		String[] code = {
				"var a\n = 'Here is a unsafe character';"
		};

		th.test(code, new LinterOptions().set("es3", true));
	}

	@Test
	public void testPeekOverDirectives() {
		String code = th.readFile("src/test/resources/fixtures/peek-over-directives.js");

		// Within object literal
		th.addError(18, 14, "Unexpected control character in regular expression.");
		th.addError(19, 14, "Unexpected escaped character '<' in regular expression.");
		th.addError(20, 81, "Line is too long.");
		th.addError(21, 15, "Control character in string: <non-printable>.");
		th.addError(22, 14, "'Octal integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(23, 14, "'Binary integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(24, 14, "'template literal syntax' is only available in ES6 (use 'esversion: 6').");
		th.addError(25, 14, "'Sticky RegExp flag' is only available in ES6 (use 'esversion: 6').");

		// Within array literal:
		th.addError(44, 3, "Unexpected control character in regular expression.");
		th.addError(45, 3, "Unexpected escaped character '<' in regular expression.");
		th.addError(46, 81, "Line is too long.");
		th.addError(47, 4, "Control character in string: <non-printable>.");
		th.addError(48, 3, "'Octal integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(49, 3, "'Binary integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(50, 3, "'template literal syntax' is only available in ES6 (use 'esversion: 6').");
		th.addError(51, 3, "'Sticky RegExp flag' is only available in ES6 (use 'esversion: 6').");

		// Within grouping operator:
		th.addError(70, 3, "Unexpected control character in regular expression.");
		th.addError(71, 3, "Unexpected escaped character '<' in regular expression.");
		th.addError(72, 81, "Line is too long.");
		th.addError(73, 4, "Control character in string: <non-printable>.");
		th.addError(74, 3, "'Octal integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(75, 3, "'Binary integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(76, 3, "'template literal syntax' is only available in ES6 (use 'esversion: 6').");
		th.addError(77, 3, "'Sticky RegExp flag' is only available in ES6 (use 'esversion: 6').");
		th.test(code);
	}

	@Test
	public void testOther() {
		String[] code = {
				"\\",
				"!"
		};

		th.addError(1, 1, "Unexpected '\\'.");
		th.addError(2, 1, "Unexpected early end of program.");
		th.addError(2, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test(code, new LinterOptions().set("es3", true));

		// GH-818
		th.newTest();
		th.addError(1, 15, "Expected an identifier and instead saw ')'.");
		th.addError(1, 15, "Unrecoverable syntax error. (100% scanned).");
		th.test("if (product < ) {}", new LinterOptions().set("es3", true));

		// GH-2506
		th.newTest();
		th.addError(1, 7, "Expected an identifier and instead saw ';'.");
		th.addError(1, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test("typeof;");

		th.newTest();
		th.addError(1, 1, "Unrecoverable syntax error. (0% scanned).");
		th.test("}");
	}

	@Test
	public void testConfusingOps() {
		String[] code = {
				"var a = 3 - -3;",
				"var b = 3 + +3;",
				"a = a - --a;",
				"a = b + ++b;",
				"a = a-- - 3;", // this is not confusing?!
				"a = a++ + 3;" // this is not confusing?!
		};

		th.newTest("AdditiveExpressions");
		th.addError(1, 13, "Confusing minuses.");
		th.addError(2, 13, "Confusing plusses.");
		th.addError(3, 9, "Confusing minuses.");
		th.addError(4, 9, "Confusing plusses.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));

		th.newTest("UnaryExpressions");
		th.addError(1, 8, "Confusing minuses.");
		th.addError(2, 8, "Confusing minuses.");
		th.addError(3, 8, "Confusing plusses.");
		th.addError(4, 8, "Confusing plusses.");
		th.test(new String[] {
				"void - -x;",
				"void - --x;",
				"void + +x;",
				"void + ++x;"
		});
	}

	@Test
	public void testDivision() {
		String[] code = {
				"var a=4,b=4,i=2;",
				"a/=b+2;",
				"a/=b/2;",
				"a/=b/i;",
				"/*jshint expr:true*/",
				"/=b/i;" // valid standalone RegExp expression
		};

		th.test(code);
	}

	@Test
	public void testPlusplus() {
		String[] code = {
				"var a = ++[2];",
				"var b = --(2);",
				"var c = [2]++;",
				"var d = (2)--;"
		};

		th.addError(1, 9, "Unexpected use of '++'.");
		th.addError(1, 9, "Bad assignment.");
		th.addError(2, 9, "Unexpected use of '--'.");
		th.addError(2, 9, "Bad assignment.");
		th.addError(3, 12, "Unexpected use of '++'.");
		th.addError(3, 12, "Bad assignment.");
		th.addError(4, 12, "Unexpected use of '--'.");
		th.addError(4, 12, "Bad assignment.");
		th.test(code, new LinterOptions().set("plusplus", true).set("es3", true));
		th.test(code, new LinterOptions().set("plusplus", true)); // es5
		th.test(code, new LinterOptions().set("plusplus", true).set("esnext", true));
		th.test(code, new LinterOptions().set("plusplus", true).set("moz", true));

		th.newTest();
		th.addError(1, 9, "Bad assignment.");
		th.addError(2, 9, "Bad assignment.");
		th.addError(3, 12, "Bad assignment.");
		th.addError(4, 12, "Bad assignment.");
		th.test(code, new LinterOptions().set("plusplus", false).set("es3", true));
		th.test(code, new LinterOptions().set("plusplus", false)); // es5
		th.test(code, new LinterOptions().set("plusplus", false).set("esnext", true));
		th.test(code, new LinterOptions().set("plusplus", false).set("moz", true));
	}

	@Test
	public void testAssignment() {
		String[] code = {
				"function test() {",
				"  arguments.length = 2;",
				"  arguments[0] = 3;",
				"  arguments.length &= 2;",
				"  arguments[0] >>= 3;",
				"}",
				"function test2() {",
				"  \"use strict\";",
				"  arguments.length = 2;",
				"  arguments[0] = 3;",
				"  arguments.length &= 2;",
				"  arguments[0] >>= 3;",
				"}",
				"a() = 2;"
		};

		th.addError(2, 20,
				"Assignment to properties of a mapped arguments object may cause unexpected changes to formal parameters.");
		th.addError(3, 16,
				"Assignment to properties of a mapped arguments object may cause unexpected changes to formal parameters.");
		th.addError(4, 20,
				"Assignment to properties of a mapped arguments object may cause unexpected changes to formal parameters.");
		th.addError(5, 16,
				"Assignment to properties of a mapped arguments object may cause unexpected changes to formal parameters.");
		th.addError(14, 5, "Bad assignment.");
		th.test(code, new LinterOptions().set("plusplus", true).set("es3", true));
		th.test(code, new LinterOptions().set("plusplus", true)); // es5
		th.test(code, new LinterOptions().set("plusplus", true).set("esnext", true));
		th.test(code, new LinterOptions().set("plusplus", true).set("moz", true));

		th.newTest("assignment to `eval` outside of strict mode code");
		th.test(new String[] {
				"(function() {",
				"  var eval = 3;",
				"}());"
		});

		th.newTest("assignment to `eval` within strict mode code");
		th.addError(5, 10, "Bad assignment.");
		th.test(new String[] {
				"(function() {",
				"  var eval;",
				"  (function() {",
				"    'use strict';",
				"    eval = 3;",
				"  }());",
				"}());"
		});

		th.newTest("assignment to `arguments` outside of strict mode code");
		th.addError(2, 13,
				"Assignment to properties of a mapped arguments object may cause unexpected changes to formal parameters.");
		th.test(new String[] {
				"(function() {",
				"  arguments = 3;",
				"}());"
		});

		th.newTest("assignment to `arguments` within strict mode code");
		th.addError(3, 13, "Bad assignment.");
		th.test(new String[] {
				"(function() {",
				"  'use strict';",
				"  arguments = 3;",
				"}());"
		});
	}

	@Test
	public void testRelations() {
		String[] code = {
				"var a = 2 === NaN;",
				"var b = NaN == 2;",
				"var c = !2 < 3;",
				"var c = 2 < !3;",
				"var d = (!'x' in obj);",
				"var e = (!a === b);",
				"var f = (a === !'hi');",
				"var g = (!2 === 1);",
				"var h = (![1, 2, 3] === []);",
				"var i = (!([]) instanceof Array);"
		};

		th.addError(1, 11, "Use the isNaN function to compare with NaN.");
		th.addError(2, 13, "Use the isNaN function to compare with NaN.");
		th.addError(3, 9, "Confusing use of '!'.");
		th.addError(4, 13, "Confusing use of '!'.");
		th.addError(5, 10, "Confusing use of '!'.");
		th.addError(6, 10, "Confusing use of '!'.");
		th.addError(7, 16, "Confusing use of '!'.");
		th.addError(8, 10, "Confusing use of '!'.");
		th.addError(9, 10, "Confusing use of '!'.");
		th.addError(10, 10, "Confusing use of '!'.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));

		th.newTest("No suitable expression following logical NOT.");
		th.addError(1, 7, "Expected an identifier and instead saw ';'.");
		th.addError(1, 6, "Unrecoverable syntax error. (100% scanned).");
		th.test("void !;");

		th.newTest("Logical NOT in combination with 'infix' operators.");
		th.addError(3, 6, "Confusing use of '!'.");
		th.addError(4, 6, "Confusing use of '!'.");
		th.addError(5, 6, "Confusing use of '!'.");
		th.addError(6, 6, "Confusing use of '!'.");
		th.addError(7, 6, "Confusing use of '!'.");
		th.addError(8, 6, "Confusing use of '!'.");
		th.addError(9, 6, "Confusing use of '!'.");
		th.addError(10, 6, "Confusing use of '!'.");
		th.addError(11, 6, "Confusing use of '!'.");
		th.addError(12, 6, "Confusing use of '!'.");
		th.addError(13, 6, "Confusing use of '!'.");
		th.addError(14, 6, "Confusing use of '!'.");
		th.addError(15, 6, "Confusing use of '!'.");
		th.test(new String[] {
				"void !'-';",
				"void !'+';",
				"void !(0 < 0);",
				"void !(0 <= 0);",
				"void !(0 == 0);",
				"void !(0 === 0);",
				"void !(0 !== 0);",
				"void !(0 != 0);",
				"void !(0 > 0);",
				"void !(0 >= 0);",
				"void !(0 + 0);",
				"void !(0 - 0);",
				"void !(0 * 0);",
				"void !(0 / 0);",
				"void !(0 % 0);",
		});

		th.newTest("Logical NOT in combination with other unary operators.");
		th.addError(3, 6, "Confusing use of '!'.");
		th.addError(4, 6, "Confusing use of '!'.");
		th.test(new String[] {
				"void !'-';",
				"void !'+';",
				"void !+0;",
				"void !-0;"
		});
	}

	@Test
	public void testOptions() {
		String[] code = {
				"/*member a*/",
				"/*members b*/",
				"var x; x.a.b.c();",
				"/*jshint ++ */",
				"/*jslint indent: 0 */",
				"/*jslint indent: -2 */",
				"/*jslint indent: 100.4 */",
				"/*jslint maxlen: 200.4 */",
				"/*jslint maxerr: 300.4 */",
				"/*jslint maxerr: 0 */",
				"/*jslint maxerr: 20 */",
				"/*member c:true */",
				"/*jshint d:no */",
				"/*global xxx*/",
				"xxx = 2;",
				"/*jshint relaxing: true */",
				"/*jshint toString: true */"
		};

		th.addError(3, 14, "Unexpected /*member 'c'.");
		th.addError(4, 1, "Bad option: '++'.");
		th.addError(5, 1, "Expected a small integer or 'false' and instead saw '0'.");
		th.addError(6, 1, "Expected a small integer or 'false' and instead saw '-2'.");
		th.addError(7, 1, "Expected a small integer or 'false' and instead saw '100.4'.");
		th.addError(8, 1, "Expected a small integer or 'false' and instead saw '200.4'.");
		th.addError(9, 1, "Expected a small integer or 'false' and instead saw '300.4'.");
		th.addError(10, 1, "Expected a small integer or 'false' and instead saw '0'.");
		th.addError(13, 1, "Bad option: 'd'.");
		th.addError(15, 1, "Read only.");
		th.addError(16, 1, "Bad option: 'relaxing'.");
		th.addError(17, 1, "Bad option: 'toString'.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));

		th.newTest();
		th.test(th.readFile("src/test/resources/fixtures/gh988.js"));
	}

	@Test
	public void testEmptyDirectives() {
		th.addError(1, 1, "Bad option value.");
		th.test("/* global */");
		th.test("/* global : */");
		th.test("/* global -: */");

		th.newTest();
		th.test("/* global foo, bar, baz, */");

		th.addError(1, 1, "Bad option value.");
		th.test("/* globals */");
		th.test("/* globals : */");
		th.test("/* globals -: */");

		th.newTest();
		th.test("/* globals foo, bar, baz, */");

		th.addError(1, 1, "Bad option value.");
		th.test("/* exported */");

		th.newTest();
		th.test("/* exported foo, bar, baz, */");
	}

	@Test
	public void testJshintOptionCommentsSingleLine() {
		String src = th.readFile("src/test/resources/fixtures/gh1768-1.js");

		th.test(src);
	}

	@Test
	public void testJshintOptionCommentsSingleLineLeadingAndTrailingSpace() {
		String src = th.readFile("src/test/resources/fixtures/gh1768-2.js");

		th.test(src);
	}

	@Test
	public void testJshintOptionCommentsMultiLine() {
		String src = th.readFile("src/test/resources/fixtures/gh1768-3.js");

		th.test(src);
	}

	@Test
	public void testJshintOptionCommentsMultiLineLeadingAndTrailingSpace() {
		String src = th.readFile("src/test/resources/fixtures/gh1768-4.js");

		th.addError(4, 1, "'foo' is not defined.");
		th.test(src);
	}

	@Test
	public void testJshintOptionCommentsMultiLineOption() {
		String src = th.readFile("src/test/resources/fixtures/gh1768-5.js");

		th.addError(3, 1, "'foo' is not defined.");
		th.test(src);
	}

	@Test
	public void testJshintOptionCommentsMultiLineOptionLeadingAndTrailingSpace() {
		String src = th.readFile("src/test/resources/fixtures/gh1768-6.js");

		th.addError(4, 1, "'foo' is not defined.");
		th.test(src);
	}

	@Test
	public void testJshintOptionInlineCommentsLeadingAndTrailingTabsAndSpaces() {
		String src = th.readFile("src/test/resources/fixtures/inline-tabs-spaces.js");

		th.addError(3, 9, "'x' is defined but never used.");
		th.addError(10, 9, "'y' is defined but never used.");
		th.addError(17, 9, "'z' is defined but never used.");
		th.test(src);
	}

	@Test
	public void testShebang() {
		String[] code = {
				"#!test",
				"var a = 'xxx';",
				"#!test"
		};

		th.addError(3, 1, "Expected an identifier and instead saw '#'.");
		th.addError(3, 2, "Expected an operator and instead saw '!'.");
		th.addError(3, 2, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 3, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 3, "Missing semicolon.");
		th.addError(3, 7, "Missing semicolon.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}

	@Test
	public void testShebangImpliesNode() {
		String[] code = {
				"#!usr/bin/env node",
				"require('module');"
		};

		th.test(code);
	}

	@Test
	public void testNumbers() {
		String[] code = {
				"var a = 10e307;",
				"var b = 10e308;",
				"var c = 0.03 + 0.3 + 3.0 + 30.00;",
				"var d = 03;",
				"var e = .3;",
				"var f = 0xAAg;",
				"var g = 0033;",
				"var h = 3.;",
				"var i = 3.7.toString();",
				"var j = 1e-10;", // GH-821
				"var k = 0o1234567;",
				"var l = 0b101;",
				"var m = 0x;",
				"var n = 09;",
				"var o = 1e-A;",
				"var p = 1/;",
				"var q = 1x;"
		};

		th.addError(2, 15,
				"Value described by numeric literal cannot be accurately represented with a number value: '10e308'.");
		th.addError(5, 11, "A leading decimal point can be confused with a dot: '.3'.");
		th.addError(6, 9, "Unexpected '0'.");
		th.addError(7, 1, "Expected an identifier and instead saw 'var'.");
		th.addError(7, 4, "Missing semicolon.");
		th.addError(7, 13, "Don't use extra leading zeros '0033'.");
		th.addError(8, 11, "A trailing decimal point can be confused with a dot: '3.'.");
		th.addError(9, 9, "A dot following a number can be confused with a decimal point.");
		th.addError(11, 9, "'Octal integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(12, 9, "'Binary integer literal' is only available in ES6 (use 'esversion: 6').");
		th.addError(13, 11, "Malformed numeric literal: '0x'.");
		th.addError(15, 9, "Unexpected '1'.");
		th.addError(16, 11, "Expected an identifier and instead saw ';'.");
		th.addError(16, 1, "Expected an identifier and instead saw 'var'.");
		th.addError(16, 4, "Missing semicolon.");
		th.addError(16, 12, "Missing semicolon.");
		th.addError(17, 9, "Unexpected '1'.");
		th.addError(17, 7, "Unexpected early end of program.");
		th.addError(17, 9, "Unrecoverable syntax error. (100% scanned).");
		th.test(code, new LinterOptions().set("es3", true));

		// Octals are prohibited in strict mode.
		th.newTest();
		th.addError(3, 11, "Octal literals are not allowed in strict mode.");
		th.test(new String[] {
				"(function () {",
				"'use strict';",
				"return 045;",
				"}());"
		});

		th.newTest();
		th.test(new String[] {
				"void 08;",
				"void 0181;"
		});

		th.newTest();
		th.addError(3, 10, "Decimals with leading zeros are not allowed in strict mode.");
		th.test(new String[] {
				"(function () {",
				"'use strict';",
				"return 08;",
				"}());"
		});

		th.newTest();
		th.addError(3, 12, "Decimals with leading zeros are not allowed in strict mode.");
		th.test(new String[] {
				"(function () {",
				"'use strict';",
				"return 0181;",
				"}());"
		});

		// GitHub #751 - an expression containing a number with a leading decimal point
		// should be parsed in its entirety
		th.newTest();
		th.addError(1, 11, "A leading decimal point can be confused with a dot: '.3'.");
		th.addError(2, 15, "A leading decimal point can be confused with a dot: '.3'.");
		th.test(new String[] {
				"var a = .3 + 1;",
				"var b = 1 + .3;"
		});

		th.newTest();
		th.addError(5, 18, "Missing semicolon.");
		th.addError(5, 18, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 14, "Missing semicolon.");
		th.addError(6, 14, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"var a = 0o1234567;",
				"var b = 0O1234567;",
				"var c = 0b101;",
				"var d = 0B101;",
				"var e = 0o12345678;",
				"var f = 0b1012;"
		}, new LinterOptions().set("esnext", true));

		th.newTest();
		th.test(new String[] {
				"// jshint esnext: true",
				"var a = 0b0 + 0o0;"
		});
	}

	@Test
	public void testComments() {
		String[] code = {
				"/*",
				"/* nested */",
				"*/",
				"/* unclosed .."
		};

		th.addError(3, 1, "Unbegun comment.");
		th.addError(4, 1, "Unclosed comment.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));

		String src = "/* this is a comment /* with nested slash-start */";
		th.newTest();
		th.test(src);
		th.test(th.readFile("src/test/resources/fixtures/gruntComment.js"));

		th.newTest();
		th.addError(1, 2, "Unmatched '{'.");
		th.addError(1, 2, "Unrecoverable syntax error. (100% scanned).");
		th.test("({");
	}

	@Test(groups = { "regexp" })
	public void testRegexpBasic() {
		String[] code = {
				"var a1 = /\\\u001f/;",
				"var a2 = /[\\\u001f]/;",
				"var b1 = /\\</;", // only \< is unexpected?!
				"var b2 = /[\\<]/;", // only \< is unexpected?!
				"var c = /(?(a)b)/;",
				"var d = /)[--aa-b-cde-]/;",
				"var e = /[]/;",
				"var f = /[^]/;",
				"var g = /[a^[]/;",

				// JSHINT_FIXME: Firefox doesn't handle [a-\\s] well.
				// See https://bugzilla.mozilla.org/show_bug.cgi?id=813249
				"", // "var h = /[a-\\s-\\w-\\d\\x10-\\x20--]/;",

				"var i = /[/-a1-/]/;",
				"var j = /[a-<<-3]./;",
				"var k = /]}/;",
				"var l = /?(*)(+)({)/;",
				"var m = /a{b}b{2,c}c{3,2}d{4,?}x{30,40}/;",
				"var n = /a??b+?c*?d{3,4}? a?b+c*d{3,4}/;",
				"var o = /a\\/*  [a-^-22-]/;",
				"var p = /(?:(?=a|(?!b)))/;",
				"var q = /=;/;",
				"var r = /(/;",
				"var s = /(((/;",
				"var t = /x/* 2;",
				"var u = /x/;",
				"var w = v + /s/;",
				"var x = w - /s/;",
				"var y = typeof /[a-z]/;", // GH-657
				"var z = /a/ instanceof /a/.constructor;", // GH-2773
				"void /./;",
				"var v = /dsdg;"
		};

		th.addError(1, 10, "Unexpected control character in regular expression.");
		th.addError(2, 10, "Unexpected control character in regular expression.");
		th.addError(3, 10, "Unexpected escaped character '<' in regular expression.");
		th.addError(4, 10, "Unexpected escaped character '<' in regular expression.");
		th.addError(5, 9, "Invalid regular expression.");
		th.addError(6, 9, "Invalid regular expression.");
		th.addError(11, 9, "Invalid regular expression.");
		th.addError(12, 9, "Invalid regular expression.");
		th.addError(14, 9, "Invalid regular expression.");
		th.addError(15, 9, "Invalid regular expression.");
		th.addError(17, 9, "Invalid regular expression.");
		th.addError(20, 9, "Invalid regular expression.");
		th.addError(21, 9, "Invalid regular expression.");
		th.addError(29, 9, "Unclosed regular expression.");
		th.addError(29, 9, "Unrecoverable syntax error. (100% scanned).");

		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));

		th.newTest();
		th.test("var a = `${/./}${/./}`;", new LinterOptions().set("esversion", 6));

		// Pre Regular Expression Punctuation
		// (See: token method, create function in lex.js)
		//
		// "."
		th.newTest();
		th.addError(1, 12, "A trailing decimal point can be confused with a dot: '10.'.");
		th.test("var y = 10. / 1;", new LinterOptions().set("es3", true));
		th.test("var y = 10. / 1;", new LinterOptions()); // es5
		th.test("var y = 10. / 1;", new LinterOptions().set("esnext", true));
		th.test("var y = 10. / 1;", new LinterOptions().set("moz", true));

		// ")"
		th.newTest();
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions().set("es3", true));
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions()); // es5
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions().set("esnext", true));
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions().set("moz", true));

		// "~"
		th.newTest();
		th.test("var y = ~16 / 180;", new LinterOptions().set("es3", true));
		th.test("var y = ~16 / 180;", new LinterOptions()); // es5
		th.test("var y = ~16 / 180;", new LinterOptions().set("esnext", true));
		th.test("var y = ~16 / 180;", new LinterOptions().set("moz", true));

		// "]" (GH-803)
		th.newTest();
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions().set("es3", true));
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions()); // es5
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions().set("esnext", true));
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions().set("moz", true));

		// "++" (GH-1787)
		th.newTest();
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions().set("es3", true));
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions()); // es5
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions().set("esnext", true));
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions().set("moz", true));

		// "--" (GH-1787)
		th.newTest();
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions().set("es3", true));
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions()); // es5
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions().set("esnext", true));
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions().set("moz", true));

		th.newTest("gh-3308");
		th.test("void (function() {} / 0);");

		th.newTest();
		th.addError(1, 9, "Invalid regular expression.");
		th.test("var a = /.*/ii;");

		th.newTest("Invalid Decimal Escape Sequence tolerated without `u` flag");
		th.test(new String[] {
				"void /\\00/;",
				"void /\\01/;",
				"void /\\02/;",
				"void /\\03/;",
				"void /\\04/;",
				"void /\\05/;",
				"void /\\06/;",
				"void /\\07/;",
				"void /\\08/;",
				"void /\\09/;"
		});

		th.newTest("following `new`");
		th.addError(1, 5, "Bad constructor.");
		th.addError(1, 5, "Missing '()' invoking a constructor.");
		th.test("new /./;");

		th.newTest("following `delete`");
		th.addError(1, 11, "Variables should not be deleted.");
		th.test("delete /./;");

		th.newTest("following `extends`");
		th.test("class R extends /./ {}", new LinterOptions().set("esversion", 6));

		th.newTest("following `default`");
		th.test("export default /./;", new LinterOptions().set("esversion", 6).set("module", true));
	}

	@Test(groups = { "regexp" })
	public void testRegexpUFlag() {
		// Flag validity
		th.newTest();
		th.addError(1, 9, "'Unicode RegExp flag' is only available in ES6 (use 'esversion: 6').");
		th.test("var a = /.*/u;");

		th.newTest();
		th.test("var a = /.*/u;", new LinterOptions().set("esversion", 6));

		// Hexidecimal limits
		th.newTest();
		th.addError(3, 5, "Invalid regular expression.");
		th.test(new String[] {
				"var a = /\\u{0}/u;",
				"a = /\\u{10FFFF}/u;",
				"a = /\\u{110000}/u;"
		}, new LinterOptions().set("esnext", true));

		// PORT INFO: this test is not possible to implement at the moment due to
		// different RegExp engine
		// th.newTest("Guard against regression from escape sequence substitution");
		// th.test("void /\\u{3f}/u;", new LinterOptions().set("esversion", 6));

		// Hexidecimal in range patterns
		th.newTest();
		th.addError(3, 5, "Invalid regular expression.");
		th.addError(4, 5, "Invalid regular expression.");
		th.test(new String[] {
				"var a = /[\\u{61}-b]/u;",
				"a = /[\\u{061}-b]/u;",
				"a = /[\\u{63}-b]/u;",
				"a = /[\\u{0063}-b]/u;",
		}, new LinterOptions().set("esnext", true));

		th.newTest();
		th.test("var x = /[\uD834\uDF06-\uD834\uDF08a-z]/u;", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /.{}/u;", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.test(new String[] {
				"void /.{0}/;",
				"void /.{0}/u;",
				"void /.{9}/;",
				"void /.{9}/u;",
				"void /.{23}/;",
				"void /.{23}/u;"
		}, new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /.{,2}/u;", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /.{2,1}/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid group reference");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /(.)(.)\\3/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Valid group reference - multiple digits");
		th.test(new String[] {
				"void /(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)\\10/u;",
				"void /(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)\\10a/u;",
				"void /(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)\\11/u;",
				"void /(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)\\11a/u;"
		}, new LinterOptions().set("esversion", 6));

		// A negative syntax test is the only way to verify JSHint is interpeting the
		// adjacent digits as one reference as opposed to a reference followed by a
		// literal digit.
		th.newTest("Invalid group reference - two digits");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /(.)\\10/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid group reference - three digits");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)\\100/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid group reference (permitted without flag)");
		th.test("void /(.)(.)\\3/;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid unicode escape sequence");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\u{123,}/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid unicode escape sequence (permitted without flag)");
		th.test("void /\\u{123,}/;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid character escape");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\m/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid character escape (permitted without flag)");
		th.test("void /\\m/;", new LinterOptions().set("esversion", 6));

		// PORT INFO: this test is not possible to implement at the moment due to
		// different RegExp engine
		// th.newTest("Invalid quantifed group");
		// th.addError(1, 6, "Invalid regular expression.");
		// th.test("void /(?=.)?/u;", new LinterOptions().set("esversion", 6));

		// PORT INFO: this test is not possible to implement at the moment due to
		// different RegExp engine
		// th.newTest("Invalid quantifed group (permitted without flag)");
		// th.test("void /(?=.)?/;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid quantifier - unclosed");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /.{1/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid quantifier - unclosed (permitted without flag)");
		th.test("void /.{1/;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid quantifier - unclosed with comma");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /.{1,/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid quantifier - unclosed with comma (permitted without flag)");
		th.test("void /.{1,/;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid quantifier - unclosed with upper bound");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /.{1,2/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid quantifier - unclosed with upper bound (permitted without flag)");
		th.test("void /.{1,2/;", new LinterOptions().set("esversion", 6));

		th.newTest("Character class in lower bound of range (permitted without flag)");
		th.test("void /[\\s-1]/;", new LinterOptions().set("esversion", 6));

		th.newTest("Character class in lower bound of range");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /[\\s-1]/u;", new LinterOptions().set("esversion", 6));

		// PORT INFO: this test is not possible to implement at the moment due to
		// different RegExp engine
		// th.newTest("Character class in upper bound of range (permitted without
		// flag)");
		// th.test("void /[1-\\W]/;", new LinterOptions().set("esversion", 6));

		// PORT INFO: this test is not possible to implement at the moment due to
		// different RegExp engine
		// th.newTest("Character class in upper bound of range");
		// th.addError(1, 6, "Invalid regular expression.");
		// th.test("void /[1-\\W]/u;", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.test("void /[\\s0-1\\s2-3\\s]/u;", new LinterOptions().set("esversion", 6));

		th.newTest("Null CharacterEscape");
		th.test(new String[] {
				"void /\\0/u;",
				"void /\\0a/u;"
		}, new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\00/u;", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\01/u;", new LinterOptions().set("esversion", 6));

		th.newTest("ControlEscape");
		th.test(new String[] {
				"void /\\f/u;",
				"void /\\n/u;",
				"void /\\r/u;",
				"void /\\t/u;",
				"void /\\v/u;"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test(groups = { "regexp" })
	public void testRegexpYFlag() {
		// Flag validity
		th.newTest();
		th.addError(1, 9, "'Sticky RegExp flag' is only available in ES6 (use 'esversion: 6').");
		th.test("var a = /.*/y;");

		th.newTest();
		th.test("var a = /.*/y;", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 9, "Invalid regular expression.");
		th.test("var a = /.*/yiy;", new LinterOptions().set("esversion", 6));
	}

	@Test(groups = { "regexp" })
	public void testRegexpDotAll() {
		th.newTest("flag presence - disallowed in editions prior to 2018");
		th.addError(1, 6, "'DotAll RegExp flag' is only available in ES9 (use 'esversion: 9').");
		th.addError(2, 6, "'DotAll RegExp flag' is only available in ES9 (use 'esversion: 9').");
		th.addError(3, 6, "'DotAll RegExp flag' is only available in ES9 (use 'esversion: 9').");
		th.test(new String[] {
				"void /./s;",
				"void /./gs;",
				"void /./sg;"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("flag presence - allowed in 2018");
		th.test(new String[] {
				"void /./s;",
				"void /./gs;",
				"void /./sg;"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("duplicate flag");
		th.addError(1, 6, "Invalid regular expression.");
		th.addError(2, 6, "Invalid regular expression.");
		th.test(new String[] {
				"void /./ss;",
				"void /./sgs;"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("missing dot");
		th.addError(1, 6, "Unnecessary RegExp 's' flag.");
		th.addError(2, 6, "Unnecessary RegExp 's' flag.");
		th.addError(3, 6, "Unnecessary RegExp 's' flag.");
		th.addError(4, 6, "Unnecessary RegExp 's' flag.");
		th.addError(5, 6, "Unnecessary RegExp 's' flag.");
		th.test(new String[] {
				"void /dotall flag without dot/s;",
				"void /literal period \\./s;",
				"void /\\. literal period/s;",
				"void /literal period \\\\\\./s;",
				"void /\\\\\\. literal period/s;"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("dot following escape");
		th.test(new String[] {
				"void /RegExp dot \\\\./s;",
				"void /\\\\. RegExp dot/s;",
				"void /RegExp dot \\\\\\\\./s;",
				"void /\\\\\\\\. RegExp dot/s;"
		}, new LinterOptions().set("esversion", 9));
	}

	@Test(groups = { "regexp" })
	public void testRegexpUnicodePropertyEscape() {
		th.newTest("requires `esversion: 9`");
		th.addError(1, 6, "'Unicode property escape' is only available in ES9 (use 'esversion: 9').");
		th.test("void /\\p{Any}/u;", new LinterOptions().set("esversion", 8));

		th.newTest("restricted in character class ranges");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /[--\\p{Any}]/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects missing delimiter");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p /u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects missing delimiter");
		th.addError(1, 6, "Unclosed regular expression.");
		th.addError(1, 6, "Unrecoverable syntax error. (100% scanned).");
		th.test("void /\\p{Any/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects unterminated sequence"); // JSHINT_BUG: duplicate test, probably copy-paste bug
		th.addError(1, 6, "Unclosed regular expression.");
		th.addError(1, 6, "Unrecoverable syntax error. (100% scanned).");
		th.test("void /\\p{Any/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects invalid General_Category values");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p{General_Category=Adlam}/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects invalid Script values");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p{Script=Cased_Letter}/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects invalid Script_Extensions values");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p{Script_Extensions=Cased_Letter}/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects Script values as shorthand");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p{Adlam}/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects invalid names");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p{hasOwnProperty=Cased_Letter}/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects invalid values");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p{hasOwnProperty}/u;", new LinterOptions().set("esversion", 9));

		th.newTest("rejects invalid values");
		th.addError(1, 6, "Invalid regular expression.");
		th.test("void /\\p{General_Category=hasOwnProperty}/u;", new LinterOptions().set("esversion", 9));

		th.newTest("tolerates errors without `u` flag");
		th.test(new String[] {
				"void /\\p/;",
				"void /\\p{}/;"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("accepts valid binary aliases");
		th.test(new String[] {
				"void /\\p{ASCII}/u;",
				"void /\\P{ASCII}/u;",
		}, new LinterOptions().set("esversion", 9));

		th.newTest("accepts valid General_Category values");
		th.test(new String[] {
				"void /\\p{General_Category=Cased_Letter}/u;",
				"void /\\p{gc=Cased_Letter}/u;",
				"void /\\P{General_Category=Cased_Letter}/u;",
				"void /\\P{gc=Cased_Letter}/u;"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("accepts General_Category values shorthand");
		th.test(new String[] {
				"void /\\p{Cased_Letter}/u;",
				"void /\\P{Cased_Letter}/u;"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("accepts valid Script values");
		th.test(new String[] {
				"void /\\p{Script=Adlam}/u;",
				"void /\\p{sc=Adlam}/u;",
				"void /\\P{Script=Adlam}/u;",
				"void /\\P{sc=Adlam}/u;"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("accepts valid Script_Extensions values");
		th.test(new String[] {
				"void /\\p{Script_Extensions=Adlam}/u;",
				"void /\\p{scx=Adlam}/u;",
				"void /\\P{Script_Extensions=Adlam}/u;",
				"void /\\P{scx=Adlam}/u;"
		}, new LinterOptions().set("esversion", 9));
	}

	@Test(groups = { "regexp" })
	public void testRegexpRegressions() {
		// GH-536
		th.test("str /= 5;", new LinterOptions().set("es3", true), new LinterGlobals(true, "str"));
		th.test("str /= 5;", new LinterOptions(), new LinterGlobals(true, "str")); // es5
		th.test("str /= 5;", new LinterOptions().set("esnext", true), new LinterGlobals(true, "str"));
		th.test("str /= 5;", new LinterOptions().set("moz", true), new LinterGlobals(true, "str"));

		th.test("str = str.replace(/=/g, '');", new LinterOptions().set("es3", true), new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=/g, '');", new LinterOptions(), new LinterGlobals(true, "str")); // es5
		th.test("str = str.replace(/=/g, '');", new LinterOptions().set("esnext", true),
				new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=/g, '');", new LinterOptions().set("moz", true), new LinterGlobals(true, "str"));

		th.test("str = str.replace(/=abc/g, '');", new LinterOptions().set("es3", true),
				new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=abc/g, '');", new LinterOptions(), new LinterGlobals(true, "str")); // es5
		th.test("str = str.replace(/=abc/g, '');", new LinterOptions().set("esnext", true),
				new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=abc/g, '');", new LinterOptions().set("moz", true),
				new LinterGlobals(true, "str"));

		// GH-538
		th.test("var exp = /function(.*){/gi;", new LinterOptions().set("es3", true));
		th.test("var exp = /function(.*){/gi;", new LinterOptions()); // es5
		th.test("var exp = /function(.*){/gi;", new LinterOptions().set("esnext", true));
		th.test("var exp = /function(.*){/gi;", new LinterOptions().set("moz", true));

		th.test("var exp = /\\[\\]/;", new LinterOptions().set("es3", true));
		th.test("var exp = /\\[\\]/;", new LinterOptions()); // es5
		th.test("var exp = /\\[\\]/;", new LinterOptions().set("esnext", true));
		th.test("var exp = /\\[\\]/;", new LinterOptions().set("moz", true));

		// GH-3356
		th.test("void /[/]/;");
		th.test("void /[{]/u;", new LinterOptions().set("esversion", 6));
		th.test("void /[(?=)*]/u;", new LinterOptions().set("esversion", 6));
		th.test("void /[(?!)+]/u;", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testRegexpSticky() {
		th.addError(1, 11, "'Sticky RegExp flag' is only available in ES6 (use 'esversion: 6').");
		th.test("var exp = /./y;", new LinterOptions().set("esversion", 5));

		th.newTest();
		th.test("var exp = /./y;", new LinterOptions().set("esversion", 6));
		th.test("var exp = /./gy;", new LinterOptions().set("esversion", 6));
		th.test("var exp = /./yg;", new LinterOptions().set("esversion", 6));

		th.newTest("Invalid due to repetition");
		th.addError(1, 11, "Invalid regular expression.");
		th.addError(2, 11, "Invalid regular expression.");
		th.test(new String[] {
				"var exp = /./yy;",
				"var exp = /./ygy;"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("Invalid due to other conditions");
		th.addError(1, 11, "Invalid regular expression.");
		th.addError(2, 11, "Invalid regular expression.");
		th.test(new String[] {
				"var exp = /./gyg;",
				"var exp = /?/y;"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testStrings() {
		String[] code = {
				"var a = '\u0012\\r';",
				"var b = \'\\g\';",
				"var c = '\\u0022\\u0070\\u005C';",
				"var d = '\\\\';",
				"var e = '\\x6b..\\x6e';",
				"var f = '\\b\\f\\n\\/';",
				"var g = 'ax"
		};

		th.addError(1, 10, "Control character in string: <non-printable>.");
		th.addError(7, 12, "Unclosed string.");
		th.addError(7, 12, "Missing semicolon.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}

	@Test
	public void testBadStrings() {
		String[] code = {
				"var a = '\\uNOTHEX';",
				"void '\\u0000';",
				"void '\\ug000';",
				"void '\\u0g00';",
				"void '\\u00g0';",
				"void '\\u000g';"
		};

		th.addError(1, 11, "Unexpected 'uNOTH'.");
		th.addError(3, 8, "Unexpected 'ug000'.");
		th.addError(4, 8, "Unexpected 'u0g00'.");
		th.addError(5, 8, "Unexpected 'u00g0'.");
		th.addError(6, 8, "Unexpected 'u000g'.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}

	@Test
	public void testOwnProperty() {
		String[] code = {
				"var obj = { hasOwnProperty: false };",
				"obj.hasOwnProperty = true;",
				"obj['hasOwnProperty'] = true;",
				"function test() { var hasOwnProperty = {}.hasOwnProperty; }"
		};

		th.addError(1, 27, "'hasOwnProperty' is a really bad name.");
		th.addError(2, 20, "'hasOwnProperty' is a really bad name.");
		th.addError(3, 23, "'hasOwnProperty' is a really bad name.");
		th.addError(3, 4, "['hasOwnProperty'] is better written in dot notation.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}

	@Test(groups = { "json" })
	public void testJsonDflt() {
		String[] code = {
				"{",
				"  a: 2,",
				"  'b': \"hallo\\\"\\v\\x12\\'world\",",
				"  \"c\\\"\\v\\x12\": '4',",
				"  \"d\": \"4\\",
				"  \",",
				"  \"e\": 0x332,",
				"  \"x\": 0",
				"}"
		};

		th.addError(2, 3, "Expected a string and instead saw a.");
		th.addError(3, 3, "Strings must use doublequote.");
		th.addError(3, 17, "Avoid \\v.");
		th.addError(3, 19, "Avoid \\x-.");
		th.addError(3, 23, "Avoid \\'.");
		th.addError(4, 8, "Avoid \\v.");
		th.addError(4, 10, "Avoid \\x-.");
		th.addError(4, 16, "Strings must use doublequote.");
		th.addError(5, 11, "Avoid EOL escaping.");
		th.addError(7, 13, "Avoid 0x-.");
		th.test(code, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(code, new LinterOptions().set("multistr", true)); // es5
		th.test(code, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(code, new LinterOptions().set("multistr", true).set("moz", true));
	}

	@Test(groups = { "json" })
	public void testJsonDeep() {
		String[] code = {
				"{",
				"  \"key\" : {",
				"    \"deep\" : [",
				"      \"value\",",
				"      { \"num\" : 123, \"array\": [] }",
				"    ]",
				"  },",
				"  \"single\": [\"x\"],",
				"  \"negative\": -1.23e2",
				"}"
		};

		th.test(code, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(code, new LinterOptions().set("multistr", true)); // es5
		th.test(code, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(code, new LinterOptions().set("multistr", true).set("moz", true));
	}

	@Test(groups = { "json" })
	public void testJsonErrors() {
		String[] objTrailingComma = {
				"{ \"key\" : \"value\", }"
		};

		th.addError(1, 18, "Unexpected comma.");
		th.test(objTrailingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objTrailingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(objTrailingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objTrailingComma, new LinterOptions().set("multistr", true).set("moz", true));

		String[] arrayTrailingComma = {
				"{ \"key\" : [,] }"
		};

		th.newTest();
		th.addError(1, 12, "Illegal comma.");
		th.addError(1, 12, "Expected a JSON value.");
		th.addError(1, 12, "Unexpected comma.");
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true).set("moz", true));

		String[] objMissingComma = {
				"{ \"k1\":\"v1\" \"k2\":\"v2\" }"
		};

		th.newTest();
		th.addError(1, 13, "Expected '}' and instead saw 'k2'.");
		th.addError(1, 13, "Unrecoverable syntax error. (100% scanned).");
		th.test(objMissingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objMissingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(objMissingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objMissingComma, new LinterOptions().set("multistr", true).set("moz", true));

		String[] arrayMissingComma = {
				"[ \"v1\" \"v2\" ]"
		};

		th.newTest();
		th.addError(1, 8, "Expected ']' and instead saw 'v2'.");
		th.addError(1, 8, "Unrecoverable syntax error. (100% scanned).");
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true).set("moz", true));

		String[] objDoubleComma = {
				"{ \"k1\":\"v1\",, \"k2\":\"v2\" }"
		};

		th.newTest();
		th.addError(1, 13, "Illegal comma.");
		th.addError(1, 15, "Expected ':' and instead saw 'k2'.");
		th.addError(1, 19, "Expected a JSON value.");
		th.addError(1, 19, "Expected '}' and instead saw ':'.");
		th.addError(1, 19, "Unrecoverable syntax error. (100% scanned).");
		th.test(objDoubleComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objDoubleComma, new LinterOptions().set("multistr", true)); // es5
		th.test(objDoubleComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objDoubleComma, new LinterOptions().set("multistr", true).set("moz", true));

		String[] arrayDoubleComma = {
				"[ \"v1\",, \"v2\" ]"
		};

		th.newTest();
		th.addError(1, 8, "Illegal comma.");
		th.addError(1, 8, "Expected a JSON value.");
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true).set("moz", true));

		String[] objUnclosed = {
				"{ \"k1\":\"v1\""
		};

		th.newTest();
		th.addError(1, 8, "Expected '}' and instead saw ''.");
		th.addError(1, 8, "Unrecoverable syntax error. (100% scanned).");
		th.test(objUnclosed, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objUnclosed, new LinterOptions().set("multistr", true)); // es5
		th.test(objUnclosed, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objUnclosed, new LinterOptions().set("multistr", true).set("moz", true));

		String[] arrayUnclosed = {
				"[ \"v1\""
		};

		th.newTest();
		th.addError(1, 3, "Expected ']' and instead saw ''.");
		th.addError(1, 3, "Unrecoverable syntax error. (100% scanned).");
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true).set("moz", true));

		String[] objUnclosed2 = {
				"{"
		};

		th.newTest();
		th.addError(1, 1, "Missing '}' to match '{' from line 1.");
		th.addError(1, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test(objUnclosed2, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objUnclosed2, new LinterOptions().set("multistr", true)); // es5
		th.test(objUnclosed2, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objUnclosed2, new LinterOptions().set("multistr", true).set("moz", true));

		String[] arrayUnclosed2 = {
				"["
		};

		th.newTest();
		th.addError(1, 1, "Missing ']' to match '[' from line 1.");
		th.addError(1, 1, "Expected a JSON value.");
		th.addError(1, 1, "Expected ']' and instead saw ''.");
		th.addError(1, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true).set("moz", true));

		String[] objDupKeys = {
				"{ \"k1\":\"v1\", \"k1\":\"v1\" }"
		};

		th.newTest();
		th.addError(1, 14, "Duplicate key 'k1'.");
		th.test(objDupKeys, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objDupKeys, new LinterOptions().set("multistr", true)); // es5
		th.test(objDupKeys, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objDupKeys, new LinterOptions().set("multistr", true).set("moz", true));

		String[] objBadKey = {
				"{ k1:\"v1\" }"
		};

		th.newTest();
		th.addError(1, 3, "Expected a string and instead saw k1.");
		th.test(objBadKey, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objBadKey, new LinterOptions().set("multistr", true)); // es5
		th.test(objBadKey, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objBadKey, new LinterOptions().set("multistr", true).set("moz", true));

		String[] objBadValue = {
				"{ \"noRegexpInJSON\": /$^/ }"
		};

		th.newTest();
		th.addError(1, 21, "Expected a JSON value.");
		th.addError(1, 21, "Expected '}' and instead saw '/$^/'.");
		th.addError(1, 21, "Unrecoverable syntax error. (100% scanned).");
		th.test(objBadValue, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objBadValue, new LinterOptions().set("multistr", true)); // es5
		th.test(objBadValue, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objBadValue, new LinterOptions().set("multistr", true).set("moz", true));
	}

	// Regression test for gh-2488
	@Test(groups = { "json" })
	public void testJsonSemicolon() {
		th.test("{ \"attr\": \";\" }");

		th.test("[\";\"]");
	}

	@Test
	public void testComma() {
		String src = th.readFile("src/test/resources/fixtures/comma.js");

		th.addError(2, 92, "Expected an assignment or function call and instead saw an expression.");
		th.addError(15, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(15, 5, "Missing semicolon.");
		th.addError(15, 6, "Expected an assignment or function call and instead saw an expression.");
		th.addError(15, 10, "Missing semicolon.");
		th.addError(15, 29, "Missing semicolon.");
		th.addError(20, 45, "Expected an assignment or function call and instead saw an expression.");
		th.addError(30, 69, "Expected an assignment or function call and instead saw an expression.");
		th.addError(35, 8, "Expected an assignment or function call and instead saw an expression.");
		th.addError(35, 9, "Missing semicolon.");
		th.addError(36, 3, "Unexpected 'if'.");
		th.addError(43, 10, "Expected an assignment or function call and instead saw an expression.");
		th.addError(43, 11, "Missing semicolon.");
		th.addError(44, 1, "Unexpected '}'.");
		th.test(src, new LinterOptions().set("es3", true));

		// Regression test (GH-56)
		th.newTest();
		th.addError(4, 65, "Expected an assignment or function call and instead saw an expression.");
		th.test(th.readFile("src/test/resources/fixtures/gh56.js"));

		// Regression test (GH-363)
		th.newTest();
		th.addError(1, 11, "Extra comma. (it breaks older versions of IE)");
		th.test("var f = [1,];", new LinterOptions().set("es3", true));

		th.newTest();
		th.addError(3, 6, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 7, "Missing semicolon.");
		th.addError(3, 8, "Unexpected 'break'.");
		th.test(new String[] {
				"var a;",
				"while(true) {",
				"  a=1, break;",
				"}"
		}, new LinterOptions());

		th.newTest("within MemberExpression");
		th.test("void [][0, 0];");
	}

	@Test
	public void testGH2587() {
		th.addError(1, 9, "Expected an identifier and instead saw 'if'.");
		th.addError(1, 9, "Unrecoverable syntax error. (100% scanned).");
		th.addError(1, 8, "Expected '===' and instead saw '=='.");
		th.test(new String[] {
				"true == if"
		}, new LinterOptions().set("eqeqeq", true).set("eqnull", true));

		th.newTest();
		th.addError(1, 9, "Expected an identifier and instead saw 'if'.");
		th.addError(1, 9, "Unrecoverable syntax error. (100% scanned).");
		th.addError(1, 8, "Expected '!==' and instead saw '!='.");
		th.test(new String[] {
				"true != if"
		}, new LinterOptions().set("eqeqeq", true).set("eqnull", true));

		th.newTest();
		th.addError(1, 9, "Expected an identifier and instead saw 'if'.");
		th.addError(1, 9, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[] {
				"true == if"
		}, new LinterOptions());

		th.newTest();
		th.addError(1, 9, "Expected an identifier and instead saw 'if'.");
		th.addError(1, 9, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[] {
				"true != if"
		}, new LinterOptions());

		th.newTest();
		th.addError(1, 10, "Expected an identifier and instead saw 'if'.");
		th.addError(1, 10, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[] {
				"true === if"
		}, new LinterOptions());
		th.test(new String[] {
				"true !== if"
		}, new LinterOptions());

		th.newTest();
		th.addError(1, 8, "Expected an identifier and instead saw 'if'.");
		th.addError(1, 8, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[] {
				"true > if"
		}, new LinterOptions());
		th.test(new String[] {
				"true < if"
		}, new LinterOptions());

		th.newTest();
		th.addError(1, 9, "Expected an identifier and instead saw 'if'.");
		th.addError(1, 9, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[] {
				"true >= if"
		}, new LinterOptions());
		th.test(new String[] {
				"true <= if"
		}, new LinterOptions());
	}

	@Test
	public void testBadAssignments() {
		th.addError(1, 5, "Bad assignment.");
		th.test(new String[] {
				"a() = 1;"
		}, new LinterOptions());

		th.newTest();
		th.addError(1, 7, "Bad assignment.");
		th.test(new String[] {
				"a.a() = 1;"
		}, new LinterOptions());

		th.newTest();
		th.addError(1, 16, "Bad assignment.");
		th.test(new String[] {
				"(function(){}) = 1;"
		}, new LinterOptions());

		th.newTest();
		th.addError(1, 7, "Bad assignment.");
		th.test(new String[] {
				"a.a() &= 1;"
		}, new LinterOptions());
	}

	@Test
	public void testWithStatement() {
		String src = th.readFile("src/test/resources/fixtures/with.js");

		th.addError(5, 1, "Don't use 'with'.");
		th.addError(13, 5, "'with' is not allowed in strict mode.");
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src); // es5
		th.test(src, new LinterOptions().set("esnext", true));
		th.test(src, new LinterOptions().set("moz", true));

		th.newTest();
		th.addError(13, 5, "'with' is not allowed in strict mode.");
		th.test(src, new LinterOptions().set("withstmt", true).set("es3", true));
		th.test(src, new LinterOptions().set("withstmt", true)); // es5
		th.test(src, new LinterOptions().set("withstmt", true).set("esnext", true));
		th.test(src, new LinterOptions().set("withstmt", true).set("moz", true));
	}

	@Test
	public void testBlocks() {
		String src = th.readFile("src/test/resources/fixtures/blocks.js");

		th.addError(31, 5, "Unmatched \'{\'.");
		th.addError(32, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src, new LinterOptions()); // es5
		th.test(src, new LinterOptions().set("esnext", true));
		th.test(src, new LinterOptions().set("moz", true));
	}

	@Test
	public void testFunctionCharacterLocation() {
		String src = th.readFile("src/test/resources/fixtures/nestedFunctions.js");
		UniversalContainer locations = th.getNestedFunctionsLocations();

		JSHint jshint = new JSHint();
		jshint.lint(src);
		List<DataSummary.Function> report = jshint.generateSummary().getFunctions();

		assertTrue(locations.getLength() == report.size());
		for (int i = 0; i < locations.getLength(0); i++) {
			assertEquals(locations.get(i).asString("name"), report.get(i).getName());
			assertEquals(locations.get(i).asInt("line"), report.get(i).getLine());
			assertEquals(locations.get(i).asInt("character"), report.get(i).getCharacter());
			assertEquals(locations.get(i).asInt("last"), report.get(i).getLast());
			assertEquals(locations.get(i).asInt("lastcharacter"), report.get(i).getLastCharacter());
		}
	}

	@Test
	public void testExported() {
		String src = th.readFile("src/test/resources/fixtures/exported.js");

		th.addError(5, 7, "'unused' is defined but never used.");
		th.addError(6, 7, "'isDog' is defined but never used.");
		th.addError(13, 10, "'unusedDeclaration' is defined but never used.");
		th.addError(14, 5, "'unusedExpression' is defined but never used.");
		th.addError(17, 12, "'cannotBeExported' is defined but never used.");

		th.test(src, new LinterOptions().set("es3", true).set("unused", true));
		th.test(src, new LinterOptions().set("unused", true)); // es5
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
		th.test(src, new LinterOptions().set("moz", true).set("unused", true));
		th.test(src, new LinterOptions().set("unused", true).set("latedef", true));

		th.newTest();
		th.addError(1, 5, "'unused' is defined but never used.");
		th.test("var unused = 1; var used = 2;", new LinterOptions().setExporteds("used").set("unused", true));

		th.newTest("exported vars aren't used before definition");
		th.test("var a;", new LinterOptions().setExporteds("a").set("latedef", true));

		String[] code = {
				"/* exported a, b */",
				"if (true) {",
				"  /* exported c, d */",
				"  let a, c, e, g;",
				"  const [b, d, f, h] = [];",
				"  /* exported e, f */",
				"}",
				"/* exported g, h */"
		};
		th.newTest("blockscoped variables");
		th.addError(4, 7, "'a' is defined but never used.");
		th.addError(4, 10, "'c' is defined but never used.");
		th.addError(4, 13, "'e' is defined but never used.");
		th.addError(4, 16, "'g' is defined but never used.");
		th.addError(5, 10, "'b' is defined but never used.");
		th.addError(5, 13, "'d' is defined but never used.");
		th.addError(5, 16, "'f' is defined but never used.");
		th.addError(5, 19, "'h' is defined but never used.");
		th.test(code, new LinterOptions().set("esversion", 6).set("unused", true));

		th.newTest("Does not export bindings which are not accessible on the top level.");
		th.addError(2, 7, "'Moo' is defined but never used.");
		th.test(new String[] {
				"(function() {",
				"  var Moo;",
				"  /* exported Moo */",
				"})();"
		}, new LinterOptions().set("unused", true));
	}

	@Test
	public void testIdentifiers() {
		String src = th.readFile("src/test/resources/fixtures/identifiers.js");

		th.test(src, new LinterOptions().set("es3", true));

		th.addError(1, 5, "'ascii' is defined but never used.");
		th.addError(2, 5, "'num1' is defined but never used.");
		th.addError(3, 5, "'lifé' is defined but never used.");
		th.addError(4, 5, "'π' is defined but never used.");
		th.addError(5, 5, "'привет' is defined but never used.");
		th.addError(6, 5, "'\\u1d44' is defined but never used.");
		th.addError(7, 5, "'encoded\\u1d44' is defined but never used.");
		th.addError(8, 5, "'\\uFF38' is defined but never used.");
		th.addError(9, 5, "'\\uFF58' is defined but never used.");
		th.addError(10, 5, "'\\u1FBC' is defined but never used.");
		th.addError(11, 5, "'\\uFF70' is defined but never used.");
		th.addError(12, 5, "'\\u4DB3' is defined but never used.");
		th.addError(13, 5, "'\\u97CA' is defined but never used.");
		th.addError(14, 5, "'\\uD7A1' is defined but never used.");
		th.addError(15, 5, "'\\uFFDA' is defined but never used.");
		th.addError(16, 5, "'\\uA6ED' is defined but never used.");
		th.addError(17, 5, "'\\u0024' is defined but never used.");
		th.addError(18, 5, "'\\u005F' is defined but never used.");
		th.addError(19, 5, "'\\u0024\\uFF38' is defined but never used.");
		th.addError(20, 5, "'\\u0024\\uFF58' is defined but never used.");
		th.addError(21, 5, "'\\u0024\\u1FBC' is defined but never used.");
		th.addError(22, 5, "'\\u0024\\uFF70' is defined but never used.");
		th.addError(23, 5, "'\\u0024\\u4DB3' is defined but never used.");
		th.addError(24, 5, "'\\u0024\\u97CA' is defined but never used.");
		th.addError(25, 5, "'\\u0024\\uD7A1' is defined but never used.");
		th.addError(26, 5, "'\\u0024\\uFFDA' is defined but never used.");
		th.addError(27, 5, "'\\u0024\\uA6ED' is defined but never used.");
		th.addError(28, 5, "'\\u0024\\uFE24' is defined but never used.");
		th.addError(29, 5, "'\\u0024\\uABE9' is defined but never used.");
		th.addError(30, 5, "'\\u0024\\uFF17' is defined but never used.");
		th.addError(31, 5, "'\\u0024\\uFE4E' is defined but never used.");
		th.addError(32, 5, "'\\u0024\\u200C' is defined but never used.");
		th.addError(33, 5, "'\\u0024\\u200D' is defined but never used.");
		th.addError(34, 5, "'\\u0024\\u0024' is defined but never used.");
		th.addError(35, 5, "'\\u0024\\u005F' is defined but never used.");

		th.test(src, new LinterOptions().set("es3", true).set("unused", true));
		th.test(src, new LinterOptions().set("unused", true)); // es5
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
		th.test(src, new LinterOptions().set("moz", true).set("unused", true));
	}

	@Test
	public void testBadIdentifiers() {
		String[] badUnicode = {
				"var \\uNOTHEX;"
		};

		th.addError(1, 5, "Unexpected '\\'.");
		th.addError(1, 1, "Expected an identifier and instead saw ''.");
		th.addError(1, 5, "Unrecoverable syntax error. (100% scanned).");
		th.test(badUnicode, new LinterOptions().set("es3", true));
		th.test(badUnicode, new LinterOptions()); // es5
		th.test(badUnicode, new LinterOptions().set("esnext", true));
		th.test(badUnicode, new LinterOptions().set("moz", true));

		String[] invalidUnicodeIdent = {
				"var \\u0000;"
		};

		th.newTest();
		th.addError(1, 5, "Unexpected '\\'.");
		th.addError(1, 1, "Expected an identifier and instead saw ''.");
		th.addError(1, 5, "Unrecoverable syntax error. (100% scanned).");
		th.test(invalidUnicodeIdent, new LinterOptions().set("es3", true));
		th.test(invalidUnicodeIdent, new LinterOptions()); // es5
		th.test(invalidUnicodeIdent, new LinterOptions().set("esnext", true));
		th.test(invalidUnicodeIdent, new LinterOptions().set("moz", true));
	}

	@Test
	public void testRegressionForGH878() {
		String src = th.readFile("src/test/resources/fixtures/gh878.js");

		th.test(src, new LinterOptions().set("es3", true));
	}

	@Test
	public void testRegressionForGH910() {
		String src = "(function () { if (true) { foo.bar + } })();";

		th.addError(1, 38, "Expected an identifier and instead saw '}'.");
		th.addError(1, 38, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 39, "Missing semicolon.");
		th.addError(1, 41, "Expected an identifier and instead saw ')'.");
		th.addError(1, 42, "Expected an operator and instead saw '('.");
		th.addError(1, 42, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 43, "Missing semicolon.");
		th.addError(1, 43, "Expected an identifier and instead saw ')'.");
		th.addError(1, 43, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 14, "Unmatched '{'.");
		th.addError(1, 44, "Unrecoverable syntax error. (100% scanned).");
		th.test(src, new LinterOptions().set("es3", true).set("nonew", true));
	}

	@Test
	public void testHtml() {
		String html = "<html><body>Hello World</body></html>";

		th.addError(1, 1, "Expected an identifier and instead saw '<'.");
		th.addError(1, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 2, "Missing semicolon.");
		th.addError(1, 7, "Expected an identifier and instead saw '<'.");
		th.addError(1, 7, "Unrecoverable syntax error. (100% scanned).");
		th.test(html, new LinterOptions());
	}

	@Test
	public void testDestructuringVarInFunctionScope() {
		String[] code = {
				"function foobar() {",
				"  var [ a, b, c ] = [ 1, 2, 3 ];",
				"  var [ a ] = [ 1 ];",
				"  var [ a ] = [ z ];",
				"  var [ h, w ] = [ 'hello', 'world' ]; ",
				"  var [ o ] = [ { o : 1 } ];",
				"  var [ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"  var { foo : bar } = { foo : 1 };",
				"  var [ a, { foo : bar } ] = [ 2, { foo : 1 } ];",
				"  var [ 1 ] = [ a ];",
				"  var [ a, b; c ] = [ 1, 2, 3 ];",
				"  var [ a, b, c ] = [ 1, 2; 3 ];",
				"}"
		};

		th.addError(1, 10, "'foobar' is defined but never used.");
		th.addError(3, 9, "'a' is already defined.");
		th.addError(4, 9, "'a' is already defined.");
		th.addError(7, 9, "'a' is already defined.");
		th.addError(7, 18, "'b' is already defined.");
		th.addError(7, 23, "'c' is already defined.");
		th.addError(9, 9, "'a' is already defined.");
		th.addError(9, 20, "'bar' is already defined.");
		th.addError(10, 9, "Expected an identifier and instead saw '1'.");
		th.addError(11, 13, "Expected ',' and instead saw ';'.");
		th.addError(11, 9, "'a' is already defined.");
		th.addError(11, 12, "'b' is already defined.");
		th.addError(11, 15, "'c' is already defined.");
		th.addError(12, 9, "'a' is already defined.");
		th.addError(12, 12, "'b' is already defined.");
		th.addError(12, 15, "'c' is already defined.");
		th.addError(12, 27, "Expected ']' to match '[' from line 12 and instead saw ';'.");
		th.addError(12, 28, "Missing semicolon.");
		th.addError(12, 29, "Expected an assignment or function call and instead saw an expression.");
		th.addError(12, 30, "Missing semicolon.");
		th.addError(12, 31, "Expected an identifier and instead saw ']'.");
		th.addError(12, 31, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 17, "'z' is not defined.");
		th.addError(12, 12, "'b' is defined but never used.");
		th.addError(12, 15, "'c' is defined but never used.");
		th.addError(5, 9, "'h' is defined but never used.");
		th.addError(5, 12, "'w' is defined but never used.");
		th.addError(6, 9, "'o' is defined but never used.");
		th.addError(7, 28, "'d' is defined but never used.");
		th.addError(9, 20, "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringVarAsMoz() {
		String[] code = {
				"var [ a, b, c ] = [ 1, 2, 3 ];",
				"var [ a ] = [ 1 ];",
				"var [ a ] = [ z ];",
				"var [ h, w ] = [ 'hello', 'world' ]; ",
				"var [ o ] = [ { o : 1 } ];",
				"var [ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"var { foo : bar } = { foo : 1 };",
				"var [ a, { foo : bar } ] = [ 2, { foo : 1 } ];"
		};

		th.addError(3, 15, "'z' is not defined.");
		th.addError(8, 7, "'a' is defined but never used.");
		th.addError(6, 16, "'b' is defined but never used.");
		th.addError(6, 21, "'c' is defined but never used.");
		th.addError(4, 7, "'h' is defined but never used.");
		th.addError(4, 10, "'w' is defined but never used.");
		th.addError(5, 7, "'o' is defined but never used.");
		th.addError(6, 26, "'d' is defined but never used.");
		th.addError(8, 18, "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringVarAsEsnext() {
		String[] code = {
				"var [ a, b, c ] = [ 1, 2, 3 ];",
				"var [ a ] = [ 1 ];",
				"var [ a ] = [ z ];",
				"var [ h, w ] = [ 'hello', 'world' ]; ",
				"var [ o ] = [ { o : 1 } ];",
				"var [ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"var { foo : bar } = { foo : 1 };",
				"var [ a, { foo : bar } ] = [ 2, { foo : 1 } ];"
		};

		th.addError(3, 15, "'z' is not defined.");
		th.addError(8, 7, "'a' is defined but never used.");
		th.addError(6, 16, "'b' is defined but never used.");
		th.addError(6, 21, "'c' is defined but never used.");
		th.addError(4, 7, "'h' is defined but never used.");
		th.addError(4, 10, "'w' is defined but never used.");
		th.addError(5, 7, "'o' is defined but never used.");
		th.addError(6, 26, "'d' is defined but never used.");
		th.addError(8, 18, "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringVarAsEs5() {
		String[] code = {
				"var [ a, b, c ] = [ 1, 2, 3 ];",
				"var [ a ] = [ 1 ];",
				"var [ a ] = [ z ];",
				"var [ h, w ] = [ 'hello', 'world' ]; ",
				"var [ o ] = [ { o : 1 } ];",
				"var [ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"var { foo : bar } = { foo : 1 };",
				"var [ a, { foo : bar } ] = [ 2, { foo : 1 } ];"
		};

		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 15, "'z' is not defined.");
		th.addError(8, 7, "'a' is defined but never used.");
		th.addError(6, 16, "'b' is defined but never used.");
		th.addError(6, 21, "'c' is defined but never used.");
		th.addError(4, 7, "'h' is defined but never used.");
		th.addError(4, 10, "'w' is defined but never used.");
		th.addError(5, 7, "'o' is defined but never used.");
		th.addError(6, 26, "'d' is defined but never used.");
		th.addError(8, 18, "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true)); // es5
	}

	@Test
	public void testDestructuringVarAsLegacyJS() {
		String[] code = {
				"var [ a, b, c ] = [ 1, 2, 3 ];",
				"var [ a ] = [ 1 ];",
				"var [ a ] = [ z ];",
				"var [ h, w ] = [ 'hello', 'world' ]; ",
				"var [ o ] = [ { o : 1 } ];",
				"var [ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"var { foo : bar } = { foo : 1 };",
				"var [ a, { foo : bar } ] = [ 2, { foo : 1 } ];"
		};

		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 15, "'z' is not defined.");
		th.addError(8, 7, "'a' is defined but never used.");
		th.addError(6, 16, "'b' is defined but never used.");
		th.addError(6, 21, "'c' is defined but never used.");
		th.addError(4, 7, "'h' is defined but never used.");
		th.addError(4, 10, "'w' is defined but never used.");
		th.addError(5, 7, "'o' is defined but never used.");
		th.addError(6, 26, "'d' is defined but never used.");
		th.addError(8, 18, "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringVarErrors() {
		String[] code = {
				"var [ a, b, c ] = [ 1, 2, 3 ];",
				"var [ a ] = [ 1 ];",
				"var [ a ] = [ z ];",
				"var [ h, w ] = [ 'hello', 'world' ]; ",
				"var [ o ] = [ { o : 1 } ];",
				"var [ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"var { foo : bar } = { foo : 1 };",
				"var [ a, { foo : bar } ] = [ 2, { foo : 1 } ];",
				"var [ 1 ] = [ a ];",
				"var [ a, b; c ] = [ 1, 2, 3 ];",
				"var [ a, b, c ] = [ 1, 2; 3 ];"
		};

		th.addError(9, 7, "Expected an identifier and instead saw '1'.");
		th.addError(10, 11, "Expected ',' and instead saw ';'.");
		th.addError(11, 25, "Expected ']' to match '[' from line 11 and instead saw ';'.");
		th.addError(11, 26, "Missing semicolon.");
		th.addError(11, 27, "Expected an assignment or function call and instead saw an expression.");
		th.addError(11, 28, "Missing semicolon.");
		th.addError(11, 29, "Expected an identifier and instead saw ']'.");
		th.addError(11, 29, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 15, "'z' is not defined.");
		th.addError(11, 10, "'b' is defined but never used.");
		th.addError(11, 13, "'c' is defined but never used.");
		th.addError(4, 7, "'h' is defined but never used.");
		th.addError(4, 10, "'w' is defined but never used.");
		th.addError(5, 7, "'o' is defined but never used.");
		th.addError(6, 26, "'d' is defined but never used.");
		th.addError(8, 18, "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringConstAsMoz() {
		String[] code = {
				"const [ a, b, c ] = [ 1, 2, 3 ];",
				"const [ d ] = [ 1 ];",
				"const [ e ] = [ z ];",
				"const [ hel, wor ] = [ 'hello', 'world' ]; ",
				"const [ o ] = [ { o : 1 } ];",
				"const [ f, [ [ [ g ], h ], i ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"const { foo : bar } = { foo : 1 };",
				"const [ j, { foo : foobar } ] = [ 2, { foo : 1 } ];",
				"const [ aa, bb ] = yield func();"
		};

		th.addError(1, 9, "'a' is defined but never used.");
		th.addError(1, 12, "'b' is defined but never used.");
		th.addError(1, 15, "'c' is defined but never used.");
		th.addError(2, 9, "'d' is defined but never used.");
		th.addError(3, 9, "'e' is defined but never used.");
		th.addError(4, 9, "'hel' is defined but never used.");
		th.addError(4, 14, "'wor' is defined but never used.");
		th.addError(5, 9, "'o' is defined but never used.");
		th.addError(6, 9, "'f' is defined but never used.");
		th.addError(6, 18, "'g' is defined but never used.");
		th.addError(6, 23, "'h' is defined but never used.");
		th.addError(6, 28, "'i' is defined but never used.");
		th.addError(7, 15, "'bar' is defined but never used.");
		th.addError(8, 9, "'j' is defined but never used.");
		th.addError(8, 20, "'foobar' is defined but never used.");
		th.addError(9, 9, "'aa' is defined but never used.");
		th.addError(9, 13, "'bb' is defined but never used.");
		th.addError(3, 17, "'z' is not defined.");
		th.addError(9, 26, "'func' is not defined.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringConstAsEsnext() {
		String[] code = {
				"const [ a, b, c ] = [ 1, 2, 3 ];",
				"const [ d ] = [ 1 ];",
				"const [ e ] = [ z ];",
				"const [ hel, wor ] = [ 'hello', 'world' ]; ",
				"const [ o ] = [ { o : 1 } ];",
				"const [ f, [ [ [ g ], h ], i ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"const { foo : bar } = { foo : 1 };",
				"const [ j, { foo : foobar } ] = [ 2, { foo : 1 } ];",
				"[j] = [1];",
				"[j.a] = [1];",
				"[j['a']] = [1];"
		};

		th.addError(1, 9, "'a' is defined but never used.");
		th.addError(1, 12, "'b' is defined but never used.");
		th.addError(1, 15, "'c' is defined but never used.");
		th.addError(2, 9, "'d' is defined but never used.");
		th.addError(3, 9, "'e' is defined but never used.");
		th.addError(4, 9, "'hel' is defined but never used.");
		th.addError(4, 14, "'wor' is defined but never used.");
		th.addError(5, 9, "'o' is defined but never used.");
		th.addError(6, 9, "'f' is defined but never used.");
		th.addError(6, 18, "'g' is defined but never used.");
		th.addError(6, 23, "'h' is defined but never used.");
		th.addError(6, 28, "'i' is defined but never used.");
		th.addError(7, 15, "'bar' is defined but never used.");
		th.addError(8, 20, "'foobar' is defined but never used.");
		th.addError(9, 2, "Attempting to override 'j' which is a constant.");
		th.addError(11, 3, "['a'] is better written in dot notation.");
		th.addError(3, 17, "'z' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringConstAsEs5() {
		String[] code = {
				"const [ a, b, c ] = [ 1, 2, 3 ];",
				"const [ d ] = [ 1 ];",
				"const [ e ] = [ z ];",
				"const [ hel, wor ] = [ 'hello', 'world' ]; ",
				"const [ o ] = [ { o : 1 } ];",
				"const [ f, [ [ [ g ], h ], i ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"const { foo : bar } = { foo : 1 };",
				"const [ j, { foo : foobar } ] = [ 2, { foo : 1 } ];"
		};

		th.addError(1, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 9, "'a' is defined but never used.");
		th.addError(1, 12, "'b' is defined but never used.");
		th.addError(1, 15, "'c' is defined but never used.");
		th.addError(2, 9, "'d' is defined but never used.");
		th.addError(3, 9, "'e' is defined but never used.");
		th.addError(4, 9, "'hel' is defined but never used.");
		th.addError(4, 14, "'wor' is defined but never used.");
		th.addError(5, 9, "'o' is defined but never used.");
		th.addError(6, 9, "'f' is defined but never used.");
		th.addError(6, 18, "'g' is defined but never used.");
		th.addError(6, 23, "'h' is defined but never used.");
		th.addError(6, 28, "'i' is defined but never used.");
		th.addError(7, 15, "'bar' is defined but never used.");
		th.addError(8, 9, "'j' is defined but never used.");
		th.addError(8, 20, "'foobar' is defined but never used.");
		th.addError(3, 17, "'z' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true)); // es5
	}

	@Test
	public void testDestructuringConstAsLegacyJS() {
		String[] code = {
				"const [ a, b, c ] = [ 1, 2, 3 ];",
				"const [ d ] = [ 1 ];",
				"const [ e ] = [ z ];",
				"const [ hel, wor ] = [ 'hello', 'world' ]; ",
				"const [ o ] = [ { o : 1 } ];",
				"const [ f, [ [ [ g ], h ], i ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"const { foo : bar } = { foo : 1 };",
				"const [ j, { foo : foobar } ] = [ 2, { foo : 1 } ];"
		};

		th.addError(1, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 9, "'a' is defined but never used.");
		th.addError(1, 12, "'b' is defined but never used.");
		th.addError(1, 15, "'c' is defined but never used.");
		th.addError(2, 9, "'d' is defined but never used.");
		th.addError(3, 9, "'e' is defined but never used.");
		th.addError(4, 9, "'hel' is defined but never used.");
		th.addError(4, 14, "'wor' is defined but never used.");
		th.addError(5, 9, "'o' is defined but never used.");
		th.addError(6, 9, "'f' is defined but never used.");
		th.addError(6, 18, "'g' is defined but never used.");
		th.addError(6, 23, "'h' is defined but never used.");
		th.addError(6, 28, "'i' is defined but never used.");
		th.addError(7, 15, "'bar' is defined but never used.");
		th.addError(8, 9, "'j' is defined but never used.");
		th.addError(8, 20, "'foobar' is defined but never used.");
		th.addError(3, 17, "'z' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringConstErrors() {
		String[] code = {
				"const [ a, b, c ] = [ 1, 2, 3 ];",
				"const [ a, b, c ] = [ 1, 2, 3 ];",
				"const [ 1 ] = [ a ];",
				"const [ k, l; m ] = [ 1, 2, 3 ];",
				"const [ n, o, p ] = [ 1, 2; 3 ];",
				"const q = {};",
				"[q.a] = [1];",
				"({a:q.a} = {a:1});"
		};

		th.addError(2, 12, "'b' is defined but never used.");
		th.addError(2, 15, "'c' is defined but never used.");
		th.addError(4, 9, "'k' is defined but never used.");
		th.addError(4, 12, "'l' is defined but never used.");
		th.addError(4, 15, "'m' is defined but never used.");
		th.addError(5, 9, "'n' is defined but never used.");
		th.addError(5, 12, "'o' is defined but never used.");
		th.addError(5, 15, "'p' is defined but never used.");
		th.addError(2, 9, "'a' has already been declared.");
		th.addError(2, 12, "'b' has already been declared.");
		th.addError(2, 15, "'c' has already been declared.");
		th.addError(3, 9, "Expected an identifier and instead saw '1'.");
		th.addError(4, 13, "Expected ',' and instead saw ';'.");
		th.addError(5, 27, "Expected ']' to match '[' from line 5 and instead saw ';'.");
		th.addError(5, 28, "Missing semicolon.");
		th.addError(5, 29, "Expected an assignment or function call and instead saw an expression.");
		th.addError(5, 30, "Missing semicolon.");
		th.addError(5, 31, "Expected an identifier and instead saw ']'.");
		th.addError(5, 31, "Expected an assignment or function call and instead saw an expression.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringGlobalsAsMoz() {
		String[] code = {
				"var a, b, c, d, h, w, o;",
				"[ a, b, c ] = [ 1, 2, 3 ];",
				"[ a ] = [ 1 ];",
				"[ a ] = [ z ];",
				"[ h, w ] = [ 'hello', 'world' ]; ",
				"[ o ] = [ { o : 1 } ];",
				"[ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"[ a, { foo : b } ] = [ 2, { foo : 1 } ];",
				"[ a.b ] = [1];",
				"[ { a: a.b } ] = [{a:1}];",
				"[ { a: a['b'] } ] = [{a:1}];",
				"[a['b']] = [1];",
				"[,...a.b] = [1];"
		};

		th.addError(4, 11, "'z' is not defined.");
		th.addError(11, 9, "['b'] is better written in dot notation.");
		th.addError(12, 3, "['b'] is better written in dot notation.");
		th.addError(13, 3, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringGlobalsAsEsnext() {
		String[] code = {
				"var a, b, c, d, h, i, w, o;",
				"[ a, b, c ] = [ 1, 2, 3 ];",
				"[ a ] = [ 1 ];",
				"[ a ] = [ z ];",
				"[ h, w ] = [ 'hello', 'world' ]; ",
				"[ o ] = [ { o : 1 } ];",
				"[ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"[ a, { foo : b } ] = [ 2, { foo : 1 } ];",
				"[ a.b ] = [1];",
				"[ { a: a.b } ] = [{a:1}];",
				"[ { a: a['b'] } ] = [{a:1}];",
				"[a['b']] = [1];",
				"[,...a.b] = [1];",
				"[...i] = [1];",
				"[notDefined1] = [];",
				"[...notDefined2] = [];"
		};

		th.addError(4, 11, "'z' is not defined.");
		th.addError(11, 9, "['b'] is better written in dot notation.");
		th.addError(12, 3, "['b'] is better written in dot notation.");
		th.addError(15, 2, "'notDefined1' is not defined.");
		th.addError(16, 5, "'notDefined2' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringGlobalsAsEs5() {
		String[] code = {
				"var a, b, c, d, h, w, o;",
				"[ a, b, c ] = [ 1, 2, 3 ];",
				"[ a ] = [ 1 ];",
				"[ a ] = [ z ];",
				"[ h, w ] = [ 'hello', 'world' ]; ",
				"[ o ] = [ { o : 1 } ];",
				"[ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"[ a, { foo : b } ] = [ 2, { foo : 1 } ];",
				"[ a.b ] = [1];",
				"[ { a: a.b } ] = [{a:1}];",
				"[ { a: a['b'] } ] = [{a:1}];",
				"[a['b']] = [1];",
				"[,...a.b] = [1];"
		};

		th.addError(4, 11, "'z' is not defined.");
		th.addError(2, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(10, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 9, "['b'] is better written in dot notation.");
		th.addError(11, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(12, 3, "['b'] is better written in dot notation.");
		th.addError(12, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 3, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true)); // es5
	}

	@Test
	public void testDestructuringGlobalsAsLegacyJS() {
		String[] code = {
				"var a, b, c, d, h, w, o;",
				"[ a, b, c ] = [ 1, 2, 3 ];",
				"[ a ] = [ 1 ];",
				"[ a ] = [ z ];",
				"[ h, w ] = [ 'hello', 'world' ]; ",
				"[ o ] = [ { o : 1 } ];",
				"[ a, [ [ [ b ], c ], d ] ] = [ 1, [ [ [ 2 ], 3], 4 ] ];",
				"[ a, { foo : b } ] = [ 2, { foo : 1 } ];",
				"[ a.b ] = [1];",
				"[ { a: a.b } ] = [{a:1}];",
				"[ { a: a['b'] } ] = [{a:1}];",
				"[a['b']] = [1];",
				"[,...a.b] = [1];"
		};

		th.addError(4, 11, "'z' is not defined.");
		th.addError(2, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(10, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 9, "['b'] is better written in dot notation.");
		th.addError(11, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(12, 3, "['b'] is better written in dot notation.");
		th.addError(12, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 1,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 3, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringGlobalsWithSyntaxErrors() {
		String[] code = {
				"var a, b, c;",
				"[ a ] = [ z ];",
				"[ 1 ] = [ a ];",
				"[ a, b; c ] = [ 1, 2, 3 ];",
				"[ a, b, c ] = [ 1, 2; 3 ];",
				"[ a ] += [ 1 ];",
				"[ { a.b } ] = [{a:1}];",
				"[ a() ] = [];",
				"[ { a: a() } ] = [];"
		};

		th.addError(3, 3, "Bad assignment.");
		th.addError(4, 7, "Expected ',' and instead saw ';'.");
		th.addError(5, 21, "Expected ']' to match '[' from line 5 and instead saw ';'.");
		th.addError(5, 22, "Missing semicolon.");
		th.addError(5, 23, "Expected an assignment or function call and instead saw an expression.");
		th.addError(5, 24, "Missing semicolon.");
		th.addError(5, 25, "Expected an identifier and instead saw ']'.");
		th.addError(5, 25, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 7, "Bad assignment.");
		th.addError(7, 6, "Expected ',' and instead saw '.'.");
		th.addError(8, 4, "Bad assignment.");
		th.addError(9, 9, "Bad assignment.");
		th.addError(2, 11, "'z' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));

		th.newTest();
		th.addError(1, 6, "Expected ',' and instead saw '['.");
		th.addError(1, 10, "Expected ':' and instead saw ']'.");
		th.addError(1, 12, "Expected an identifier and instead saw '}'.");
		th.addError(1, 14, "Expected ',' and instead saw ']'.");
		th.addError(1, 18, "Expected ',' and instead saw '['.");
		th.addError(1, 19, "Expected an identifier and instead saw '{'.");
		th.addError(1, 20, "Expected ',' and instead saw 'a'.");
		th.addError(1, 21, "Expected an identifier and instead saw ':'.");
		th.addError(1, 22, "Expected ',' and instead saw '1'.");
		th.addError(1, 24, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 16, "Expected an identifier and instead saw '='.");
		th.test("[ { a['b'] } ] = [{a:1}];",
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));

		th.newTest();
		th.addError(1, 6, "Expected ',' and instead saw '('.");
		th.addError(1, 7, "Expected an identifier and instead saw ')'.");
		th.test("[ { a() } ] = [];", new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));

		th.newTest();
		th.addError(1, 19, "Extending prototype of native object: 'Number'.");
		th.addError(3, 9, "Bad assignment.");
		th.addError(4, 14,
				"Assignment to properties of a mapped arguments object may cause unexpected changes to formal parameters.");
		th.addError(6, 7, "Do not assign to the exception parameter.");
		th.addError(7, 6, "Do not assign to the exception parameter.");
		th.addError(9, 9, "Bad assignment.");
		th.addError(10, 13, "Bad assignment.");
		th.test(new String[] {
				"[ Number.prototype.toString ] = [function(){}];",
				"function a() {",
				"  [ new.target ] = [];",
				"  [ arguments.anything ] = [];",
				"  try{} catch(e) {",
				"    ({e} = {e});",
				"    [e] = [];",
				"  }",
				"  ({ x: null } = {});",
				"  ({ y: [...this] } = {});",
				"  ({ y: [...z] } = {});",
				"}"
		}, new LinterOptions().set("esnext", true).set("freeze", true));
	}

	@Test
	public void testDestructuringAssignOfEmptyValuesAsMoz() {
		String[] code = {
				"var [ a ] = [ 1, 2 ];",
				"var [ c, d ] = [ 1 ];",
				"var [ e, , f ] = [ 3, , 4 ];"
		};

		th.addError(1, 7, "'a' is defined but never used.");
		th.addError(2, 7, "'c' is defined but never used.");
		th.addError(2, 10, "'d' is defined but never used.");
		th.addError(3, 7, "'e' is defined but never used.");
		th.addError(3, 12, "'f' is defined but never used.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).set("laxcomma", true)
				.set("elision", true));
	}

	@Test
	public void testDestructuringAssignOfEmptyValuesAsEsnext() {
		String[] code = {
				"var [ a ] = [ 1, 2 ];",
				"var [ c, d ] = [ 1 ];",
				"var [ e, , f ] = [ 3, , 4 ];"
		};

		th.addError(1, 7, "'a' is defined but never used.");
		th.addError(2, 7, "'c' is defined but never used.");
		th.addError(2, 10, "'d' is defined but never used.");
		th.addError(3, 7, "'e' is defined but never used.");
		th.addError(3, 12, "'f' is defined but never used.");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).set("elision", true));
	}

	@Test
	public void testDestructuringAssignOfEmptyValuesAsEs5() {
		String[] code = {
				"var [ a ] = [ 1, 2 ];",
				"var [ c, d ] = [ 1 ];",
				"var [ e, , f ] = [ 3, , 4 ];"
		};

		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 7, "'a' is defined but never used.");
		th.addError(2, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 7, "'c' is defined but never used.");
		th.addError(2, 10, "'d' is defined but never used.");
		th.addError(3, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 7, "'e' is defined but never used.");
		th.addError(3, 12, "'f' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).set("elision", true)); // es5
	}

	@Test
	public void testDestructuringAssignOfEmptyValuesAsJSLegacy() {
		String[] code = {
				"var [ a ] = [ 1, 2 ];",
				"var [ c, d ] = [ 1 ];",
				"var [ e, , f ] = [ 3, , 4 ];"
		};

		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 7, "'a' is defined but never used.");
		th.addError(2, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 7, "'c' is defined but never used.");
		th.addError(2, 10, "'d' is defined but never used.");
		th.addError(3, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 7, "'e' is defined but never used.");
		th.addError(3, 12, "'f' is defined but never used.");
		th.addError(3, 23, "Extra comma. (it breaks older versions of IE)");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}

	@Test
	public void testDestructuringAssignmentDefaultValues() {
		String[] code = {
				"var [ a = 3, b ] = [];",
				"var [ c, d = 3 ] = [];",
				"var [ [ e ] = [ 3 ] ] = [];",
				"var [ f = , g ] = [];",
				"var { g, h = 3 } = {};",
				"var { i = 3, j } = {};",
				"var { k, l: m = 3 } = {};",
				"var { n: o = 3, p } = {};",
				"var { q: { r } = { r: 3 } } = {};",
				"var { s = , t } = {};",
				"var [ u = undefined ] = [];",
				"var { v = undefined } = {};",
				"var { w: x = undefined } = {};",
				"var [ ...y = 3 ] = [];"
		};

		th.addError(4, 11, "Expected an identifier and instead saw ','.");
		th.addError(4, 13, "Expected ',' and instead saw 'g'.");
		th.addError(10, 11, "Expected an identifier and instead saw ','.");
		th.addError(10, 13, "Expected ',' and instead saw 't'.");
		th.addError(11, 7, "It's not necessary to initialize 'u' to 'undefined'.");
		th.addError(12, 7, "It's not necessary to initialize 'v' to 'undefined'.");
		th.addError(13, 10, "It's not necessary to initialize 'x' to 'undefined'.");
		th.addError(14, 12, "Expected ',' and instead saw '='.");
		th.addError(14, 14, "Expected an identifier and instead saw '3'.");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testDestructuringAssignmentOfValidSimpleAssignmentTargets() {
		th.test(new String[] {
				"[ foo().attr ] = [];",
				"[ function() {}.attr ] = [];",
				"[ function() { return {}; }().attr ] = [];",
				"[ new Ctor().attr ] = [];"
		}, new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 6, "Bad assignment.");
		th.test("[ foo() ] = [];", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 10, "Bad assignment.");
		th.test("({ x: foo() } = {});", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 8, "Bad assignment.");
		th.test("[ true ? x : y ] = [];", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 12, "Bad assignment.");
		th.test("({ x: true ? x : y } = {});", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 5, "Bad assignment.");
		th.test("[ x || y ] = [];", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 9, "Bad assignment.");
		th.test("({ x: x || y } = {});", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 11, "Bad assignment.");
		th.test("[ new Ctor() ] = [];", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 15, "Bad assignment.");
		th.test("({ x: new Ctor() } = {});", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testGH3408() {
		th.newTest("var statement");
		th.addError(1, 9, "Expected an identifier and instead saw ';'.");
		th.addError(1, 10, "Missing semicolon.");
		th.test("var [x]=;", new LinterOptions().set("esversion", 6));

		th.newTest("let statement");
		th.addError(1, 9, "Expected an identifier and instead saw ';'.");
		th.addError(1, 10, "Missing semicolon.");
		th.test("let [x]=;", new LinterOptions().set("esversion", 6));

		th.newTest("const statement");
		th.addError(1, 11, "Expected an identifier and instead saw ';'.");
		th.addError(1, 12, "Missing semicolon.");
		th.test("const [x]=;", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testNonIdentifierPropertyNamesInObjectDestructuring() {
		String[] code = {
				"var { ['x' + 2]: a = 3, 0: b } = { x2: 1, 0: 2 };",
				"var { c, '': d, 'x': e } = {};",
				"function fn({ 0: f, 'x': g, ['y']: h}) {}"
		};

		th.addError(1, 18, "'a' is defined but never used.");
		th.addError(1, 28, "'b' is defined but never used.");
		th.addError(2, 7, "'c' is defined but never used.");
		th.addError(2, 14, "'d' is defined but never used.");
		th.addError(2, 22, "'e' is defined but never used.");
		th.addError(3, 10, "'fn' is defined but never used.");
		th.addError(3, 18, "'f' is defined but never used.");
		th.addError(3, 26, "'g' is defined but never used.");
		th.addError(3, 36, "'h' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
	}

	@Test
	public void testEmptyDestructuring() {
		String[] code = {
				"var {} = {};",
				"var [] = [];",
				"function a({}, []) {}",
				"var b = ({}) => ([]) => ({});"
		};

		th.addError(1, 5, "Empty destructuring: this is unnecessary and can be removed.");
		th.addError(2, 5, "Empty destructuring: this is unnecessary and can be removed.");
		th.addError(3, 12, "Empty destructuring: this is unnecessary and can be removed.");
		th.addError(3, 16, "Empty destructuring: this is unnecessary and can be removed.");
		th.addError(4, 10, "Empty destructuring: this is unnecessary and can be removed.");
		th.addError(4, 18, "Empty destructuring: this is unnecessary and can be removed.");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testArrayElementAssignmentInsideArray() {
		String[] code = {
				"var a1 = {};",
				"var a2 = [function f() {a1[0] = 1;}];"
		};

		th.test(code);
	}

	@Test
	public void testLetStatementAsMoz() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"    print(x + ' ' + y + ' ' + z);",
				"  }",
				"  print(x + ' ' + y);",
				"}",
				"print(x);"
		};

		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementAsEsnext() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"    print(x + ' ' + y + ' ' + z);",
				"  }",
				"  print(x + ' ' + y);",
				"}",
				"print(x);",
				"let",
				"y;",
				"print(y);"
		};

		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementAsEs5() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"    print(x + ' ' + y + ' ' + z);",
				"  }",
				"  print(x + ' ' + y);",
				"}",
				"print(x);"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testLetStatementAsJSLegacy() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"    print(x + ' ' + y + ' ' + z);",
				"  }",
				"  print(x + ' ' + y);",
				"}",
				"print(x);"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementOutOfScopeAsMoz() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"  }",
				"  print(z);",
				"}",
				"print(y);"
		};

		th.addError(1, 5, "'x' is defined but never used.");
		th.addError(5, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(7, 9, "'z' is not defined.");
		th.addError(9, 7, "'y' is not defined.");
		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementOutOfScopeAsEsnext() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"  }",
				"  print(z);",
				"}",
				"print(y);"
		};

		th.addError(1, 5, "'x' is defined but never used.");
		th.addError(5, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(7, 9, "'z' is not defined.");
		th.addError(9, 7, "'y' is not defined.");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementOutOfScopeAsEs5() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"  }",
				"  print(z);",
				"}",
				"print(y);"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 5, "'x' is defined but never used.");
		th.addError(5, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(7, 9, "'z' is not defined.");
		th.addError(9, 7, "'y' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testLetStatementOutOfScopeAsJSLegacy() {
		String[] code = {
				"let x = 1;",
				"{",
				"  let y = 3 ;",
				"  {",
				"    let z = 2;",
				"  }",
				"  print(z);",
				"}",
				"print(y);"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 5, "'x' is defined but never used.");
		th.addError(5, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(7, 9, "'z' is not defined.");
		th.addError(9, 7, "'y' is not defined.");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementInFunctionsAsMoz() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  function bar() {",
				"    let z = 2;",
				"    print(x);",
				"    print(z);",
				"  }",
				"  print(y);",
				"  bar();",
				"}",
				"foo();"
		};

		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementInFunctionsAsEsnext() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  function bar() {",
				"    let z = 2;",
				"    print(x);",
				"    print(z);",
				"  }",
				"  print(y);",
				"  bar();",
				"}",
				"foo();"
		};

		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementInFunctionsAsEs5() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  function bar() {",
				"    let z = 2;",
				"    print(x);",
				"    print(z);",
				"  }",
				"  print(y);",
				"  bar();",
				"}",
				"foo();"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testLetStatementInFunctionsAsLegacyJS() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  function bar() {",
				"    let z = 2;",
				"    print(x);",
				"    print(z);",
				"  }",
				"  print(y);",
				"  bar();",
				"}",
				"foo();"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementNotInScopeAsMoz() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  let bar = function () {",
				"    print(x);",
				"    let z = 2;",
				"  };",
				"  print(z);",
				"}",
				"print(y);",
				"bar();",
				"foo();"
		};

		th.addError(6, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(4, 7, "'bar' is defined but never used.");
		th.addError(8, 9, "'z' is not defined.");
		th.addError(10, 7, "'y' is not defined.");
		th.addError(11, 1, "'bar' is not defined.");
		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementNotInScopeAsEsnext() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  let bar = function () {",
				"    print(x);",
				"    let z = 2;",
				"  };",
				"  print(z);",
				"}",
				"print(y);",
				"bar();",
				"foo();"
		};

		th.addError(6, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(4, 7, "'bar' is defined but never used.");
		th.addError(8, 9, "'z' is not defined.");
		th.addError(10, 7, "'y' is not defined.");
		th.addError(11, 1, "'bar' is not defined.");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementNotInScopeAsEs5() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  let bar = function () {",
				"    print(x);",
				"    let z = 2;",
				"  };",
				"  print(z);",
				"}",
				"print(y);",
				"bar();",
				"foo();"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(4, 7, "'bar' is defined but never used.");
		th.addError(8, 9, "'z' is not defined.");
		th.addError(10, 7, "'y' is not defined.");
		th.addError(11, 1, "'bar' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testLetStatementNotInScopeAsLegacyJS() {
		String[] code = {
				"let x = 1;",
				"function foo() {",
				"  let y = 3 ;",
				"  let bar = function () {",
				"    print(x);",
				"    let z = 2;",
				"  };",
				"  print(z);",
				"}",
				"print(y);",
				"bar();",
				"foo();"
		};

		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 9, "'z' is defined but never used.");
		th.addError(3, 7, "'y' is defined but never used.");
		th.addError(4, 7, "'bar' is defined but never used.");
		th.addError(8, 9, "'z' is not defined.");
		th.addError(10, 7, "'y' is not defined.");
		th.addError(11, 1, "'bar' is not defined.");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementInForLoopAsMoz() {
		String[] code = {
				"var obj={foo: 'bar', bar: 'foo'};",
				"for ( let [n, v] in Iterator(obj) ) {",
				"  print('Name: ' + n + ', Value: ' + v);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}",
				"for (let i=0 ; i < 10 ; i++ ) {",
				"print(i);",
				"}"
		};

		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true)
				.setPredefineds("print", "Iterator"));
	}

	@Test
	public void testLetStatementInForLoopAsEsnext() {
		String[] code = {
				"var obj={foo: 'bar', bar: 'foo'};",
				"for ( let [n, v] in Iterator(obj) ) {",
				"  print('Name: ' + n + ', Value: ' + v);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}",
				"for (let i=0 ; i < 10 ; i++ ) {",
				"print(i);",
				"}",
				"for (let of; false; false) {",
				"  print(of);",
				"}"
		};

		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true)
				.setPredefineds("print", "Iterator"));
	}

	@Test
	public void testLetStatementInForLoopAsEs5() {
		String[] code = {
				"var obj={foo: 'bar', bar: 'foo'};",
				"for ( let [n, v] in Iterator(obj) ) {",
				"  print('Name: ' + n + ', Value: ' + v);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}",
				"for (let i=0 ; i < 10 ; i++ ) {",
				"print(i);",
				"}"
		};

		th.addError(2, 7, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print", "Iterator")); // es5
	}

	@Test
	public void testLetStatementInForLoopAsLegacyJS() {
		String[] code = {
				"var obj={foo: 'bar', bar: 'foo'};",
				"for ( let [n, v] in Iterator(obj) ) {",
				"  print('Name: ' + n + ', Value: ' + v);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i in [1, 2, 3, 4]) {",
				"  print(i);",
				"}",
				"for (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}",
				"for (let i=0 ; i < 10 ; i++ ) {",
				"print(i);",
				"}"
		};

		th.addError(2, 7, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true)
				.setPredefineds("print", "Iterator"));
	}

	@Test
	public void testLetStatementInDestructuredForLoopAsMoz() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"var people = [",
				"{",
				"  name: 'Mike Smith',",
				"  family: {",
				"  mother: 'Jane Smith',",
				"  father: 'Harry Smith',",
				"  sister: 'Samantha Smith'",
				"  },",
				"  age: 35",
				"},",
				"{",
				"  name: 'Tom Jones',",
				"  family: {",
				"  mother: 'Norah Jones',",
				"  father: 'Richard Jones',",
				"  brother: 'Howard Jones'",
				"  },",
				"  age: 25",
				"}",
				"];",
				"for (let {name: n, family: { father: f } } in people) {",
				"print('Name: ' + n + ', Father: ' + f);",
				"}"
		};

		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementInDestructuredForLoopAsEsnext() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"var people = [",
				"{",
				"  name: 'Mike Smith',",
				"  family: {",
				"  mother: 'Jane Smith',",
				"  father: 'Harry Smith',",
				"  sister: 'Samantha Smith'",
				"  },",
				"  age: 35",
				"},",
				"{",
				"  name: 'Tom Jones',",
				"  family: {",
				"  mother: 'Norah Jones',",
				"  father: 'Richard Jones',",
				"  brother: 'Howard Jones'",
				"  },",
				"  age: 25",
				"}",
				"];",
				"for (let {name: n, family: { father: f } } in people) {",
				"print('Name: ' + n + ', Father: ' + f);",
				"}"
		};

		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementInDestructuredForLoopAsEs5() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"var people = [",
				"{",
				"  name: 'Mike Smith',",
				"  family: {",
				"  mother: 'Jane Smith',",
				"  father: 'Harry Smith',",
				"  sister: 'Samantha Smith'",
				"  },",
				"  age: 35",
				"},",
				"{",
				"  name: 'Tom Jones',",
				"  family: {",
				"  mother: 'Norah Jones',",
				"  father: 'Richard Jones',",
				"  brother: 'Howard Jones'",
				"  },",
				"  age: 25",
				"}",
				"];",
				"for (let {name: n, family: { father: f } } in people) {",
				"print('Name: ' + n + ', Father: ' + f);",
				"}"
		};

		th.addError(21, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(21, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testLetStatementInDestructuredForLoopAsLegacyJS() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"var people = [",
				"{",
				"  name: 'Mike Smith',",
				"  family: {",
				"  mother: 'Jane Smith',",
				"  father: 'Harry Smith',",
				"  sister: 'Samantha Smith'",
				"  },",
				"  age: 35",
				"},",
				"{",
				"  name: 'Tom Jones',",
				"  family: {",
				"  mother: 'Norah Jones',",
				"  father: 'Richard Jones',",
				"  brother: 'Howard Jones'",
				"  },",
				"  age: 25",
				"}",
				"];",
				"for (let {name: n, family: { father: f } } in people) {",
				"print('Name: ' + n + ', Father: ' + f);",
				"}"
		};

		th.addError(21, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(21, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetStatementAsSeenInJetpack() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"const { Cc, Ci } = require('chrome');",
				"// add a text/unicode flavor (html converted to plain text)",
				"let (str = Cc['@mozilla.org/supports-string;1'].",
				"            createInstance(Ci.nsISupportsString),",
				"    converter = Cc['@mozilla.org/feed-textconstruct;1'].",
				"                createInstance(Ci.nsIFeedTextConstruct))",
				"{",
				"converter.type = 'html';",
				"converter.text = options.data;",
				"str.data = converter.plainText();",
				"xferable.addDataFlavor('text/unicode');",
				"xferable.setTransferData('text/unicode', str, str.data.length * 2);",
				"}"
		};

		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true)
				.setPredefineds("require", "xferable", "options"));
	}

	@Test
	public void testLetStatementAsSeenInJetpackAsEsnext() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"const { Cc, Ci } = require('chrome');",
				"// add a text/unicode flavor (html converted to plain text)",
				"let (str = Cc['@mozilla.org/supports-string;1'].",
				"            createInstance(Ci.nsISupportsString),",
				"    converter = Cc['@mozilla.org/feed-textconstruct;1'].",
				"                createInstance(Ci.nsIFeedTextConstruct))",
				"{",
				"converter.type = 'html';",
				"converter.text = options.data;",
				"str.data = converter.plainText();",
				"xferable.addDataFlavor('text/unicode');",
				"xferable.setTransferData('text/unicode', str, str.data.length * 2);",
				"}"
		};

		th.addError(6, 57, "Missing semicolon.");
		th.addError(3, 1, "'let' is not defined.");
		th.addError(3, 6, "'str' is not defined.");
		th.addError(10, 1, "'str' is not defined.");
		th.addError(12, 42, "'str' is not defined.");
		th.addError(12, 47, "'str' is not defined.");
		th.addError(5, 5, "'converter' is not defined.");
		th.addError(8, 1, "'converter' is not defined.");
		th.addError(9, 1, "'converter' is not defined.");
		th.addError(10, 12, "'converter' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true)
				.setPredefineds("require", "xferable", "options"));
	}

	@Test
	public void testLetStatementAsSeenInJetpackAsEs5() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"const { Cc, Ci } = require('chrome');",
				"// add a text/unicode flavor (html converted to plain text)",
				"let (str = Cc['@mozilla.org/supports-string;1'].",
				"            createInstance(Ci.nsISupportsString),",
				"    converter = Cc['@mozilla.org/feed-textconstruct;1'].",
				"                createInstance(Ci.nsIFeedTextConstruct))",
				"{",
				"converter.type = 'html';",
				"converter.text = options.data;",
				"str.data = converter.plainText();",
				"xferable.addDataFlavor('text/unicode');",
				"xferable.setTransferData('text/unicode', str, str.data.length * 2);",
				"}"
		};

		th.addError(1, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 57, "Missing semicolon.");
		th.addError(3, 1, "'let' is not defined.");
		th.addError(3, 6, "'str' is not defined.");
		th.addError(10, 1, "'str' is not defined.");
		th.addError(12, 42, "'str' is not defined.");
		th.addError(12, 47, "'str' is not defined.");
		th.addError(5, 5, "'converter' is not defined.");
		th.addError(8, 1, "'converter' is not defined.");
		th.addError(9, 1, "'converter' is not defined.");
		th.addError(10, 12, "'converter' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("require", "xferable",
				"options")); // es5
	}

	@Test
	public void testLetStatementAsSeenInJetpackAsLegacyJS() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"const { Cc, Ci } = require('chrome');",
				"// add a text/unicode flavor (html converted to plain text)",
				"let (str = Cc['@mozilla.org/supports-string;1'].",
				"            createInstance(Ci.nsISupportsString),",
				"    converter = Cc['@mozilla.org/feed-textconstruct;1'].",
				"                createInstance(Ci.nsIFeedTextConstruct))",
				"{",
				"converter.type = 'html';",
				"converter.text = options.data;",
				"str.data = converter.plainText();",
				"xferable.addDataFlavor('text/unicode');",
				"xferable.setTransferData('text/unicode', str, str.data.length * 2);",
				"}"
		};

		th.addError(1, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 1,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 57, "Missing semicolon.");
		th.addError(3, 1, "'let' is not defined.");
		th.addError(3, 6, "'str' is not defined.");
		th.addError(10, 1, "'str' is not defined.");
		th.addError(12, 42, "'str' is not defined.");
		th.addError(12, 47, "'str' is not defined.");
		th.addError(5, 5, "'converter' is not defined.");
		th.addError(8, 1, "'converter' is not defined.");
		th.addError(9, 1, "'converter' is not defined.");
		th.addError(10, 12, "'converter' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true)
				.setPredefineds("require", "xferable", "options"));
	}

	@Test
	public void testLetBlockAndLetExpression() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"let (x=1, y=2, z=3)",
				"{",
				"  let(t=4) print(x, y, z, t);",
				"  print(let(u=4) u,x);",
				"}",
				"for (; ; let (x = 1) x) {}"
		};

		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetBlockAndLetExpressionAsEsnext() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"let (x=1, y=2, z=3)",
				"{",
				"  let(t=4) print(x, y, z, t);",
				"  print(let(u=4) u,x);",
				"}",
				"for (; ; let (x = 1) x) {}"
		};

		th.addError(1, 20, "Missing semicolon.");
		th.addError(3, 11, "Missing semicolon.");
		th.addError(4, 18, "Expected ')' and instead saw 'u'.");
		th.addError(4, 20, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 21, "Missing semicolon.");
		th.addError(4, 21, "Expected an identifier and instead saw ')'.");
		th.addError(4, 21, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 1, "'let' is not defined.");
		th.addError(3, 3, "'let' is not defined.");
		th.addError(4, 9, "'let' is not defined.");
		th.addError(1, 6, "'x' is not defined.");
		th.addError(3, 18, "'x' is not defined.");
		th.addError(4, 20, "'x' is not defined.");
		th.addError(1, 11, "'y' is not defined.");
		th.addError(3, 21, "'y' is not defined.");
		th.addError(1, 16, "'z' is not defined.");
		th.addError(3, 24, "'z' is not defined.");
		th.addError(3, 7, "'t' is not defined.");
		th.addError(3, 27, "'t' is not defined.");
		th.addError(4, 13, "'u' is not defined.");
		th.addError(6, 22, "Expected ')' to match '(' from line 6 and instead saw 'x'.");
		th.addError(6, 23, "Expected an identifier and instead saw ')'.");
		th.addError(6, 23, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 24, "Missing semicolon.");
		th.addError(6, 10, "'let' is not defined.");
		th.addError(6, 15, "'x' is not defined.");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testLetBlockAndLetExpressionAsEs5() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"let (x=1, y=2, z=3)",
				"{",
				"  let(t=4) print(x, y, z, t);",
				"  print(let(u=4) u,x);",
				"}"
		};

		th.addError(1, 20, "Missing semicolon.");
		th.addError(3, 11, "Missing semicolon.");
		th.addError(4, 18, "Expected ')' and instead saw 'u'.");
		th.addError(4, 20, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 21, "Missing semicolon.");
		th.addError(4, 21, "Expected an identifier and instead saw ')'.");
		th.addError(4, 21, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 1, "'let' is not defined.");
		th.addError(3, 3, "'let' is not defined.");
		th.addError(4, 9, "'let' is not defined.");
		th.addError(1, 6, "'x' is not defined.");
		th.addError(3, 18, "'x' is not defined.");
		th.addError(4, 20, "'x' is not defined.");
		th.addError(1, 11, "'y' is not defined.");
		th.addError(3, 21, "'y' is not defined.");
		th.addError(1, 16, "'z' is not defined.");
		th.addError(3, 24, "'z' is not defined.");
		th.addError(3, 7, "'t' is not defined.");
		th.addError(3, 27, "'t' is not defined.");
		th.addError(4, 13, "'u' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testLetBlockAndLetExpressionAsLegacyJS() {
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
				"let (x=1, y=2, z=3)",
				"{",
				"  let(t=4) print(x, y, z, t);",
				"  print(let(u=4) u,x);",
				"}"
		};

		th.addError(1, 20, "Missing semicolon.");
		th.addError(3, 11, "Missing semicolon.");
		th.addError(4, 18, "Expected ')' and instead saw 'u'.");
		th.addError(4, 20, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 21, "Missing semicolon.");
		th.addError(4, 21, "Expected an identifier and instead saw ')'.");
		th.addError(4, 21, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 1, "'let' is not defined.");
		th.addError(3, 3, "'let' is not defined.");
		th.addError(4, 9, "'let' is not defined.");
		th.addError(1, 6, "'x' is not defined.");
		th.addError(3, 18, "'x' is not defined.");
		th.addError(4, 20, "'x' is not defined.");
		th.addError(1, 11, "'y' is not defined.");
		th.addError(3, 21, "'y' is not defined.");
		th.addError(1, 16, "'z' is not defined.");
		th.addError(3, 24, "'z' is not defined.");
		th.addError(3, 7, "'t' is not defined.");
		th.addError(3, 27, "'t' is not defined.");
		th.addError(4, 13, "'u' is not defined.");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMakeSureLetVariablesAreNotTreatedAsGlobals() {
		// This is a regression test for GH-1362
		String[] code = {
				"function sup() {",
				"if (true) {",
				"let closed = 1;",
				"closed = 2;",
				"}",

				"if (true) {",
				"if (true) {",
				"let closed = 1;",
				"closed = 2;",
				"}",
				"}",
				"}"
		};

		th.test(code, new LinterOptions().set("esnext", true).set("browser", true));
	}

	@Test
	public void testMakeSureVarVariablesCanShadowLetVariables() {
		// This is a regression test for GH-1394
		String[] code = {
				"let a = 1;",
				"let b = 2;",
				"var c = 3;",

				"function sup(a) {",
				"var b = 4;",
				"let c = 5;",
				"let d = 6;",
				"if (false) {",
				"var d = 7;",
				"}",
				"return b + c + a + d;",
				"}",

				"sup();"
		};

		th.addError(1, 5, "'a' is defined but never used.");
		th.addError(2, 5, "'b' is defined but never used.");
		th.addError(3, 5, "'c' is defined but never used.");
		th.addError(9, 5, "'d' has already been declared.");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).set("funcscope", true));
	}

	@Test
	public void testMakeSureLetVariablesInClosureOfFunctionsShadowPredefinedGlobals() {
		String[] code = {
				"function x() {",
				"  let foo;",
				"  function y() {",
				"    foo = {};",
				"  }",
				"}"
		};

		th.test(code, new LinterOptions().set("esnext", true).addPredefined("foo", false));
	}

	@Test
	public void testMakeSureLetVariablesInClosureOfBlocksShadowPredefinedGlobals() {
		String[] code = {
				"function x() {",
				"  let foo;",
				"  {",
				"    foo = {};",
				"  }",
				"}"
		};

		th.test(code, new LinterOptions().set("esnext", true).addPredefined("foo", false));
	}

	@Test
	public void testMakeSureVariablesMayShadowGlobalsInFunctionsAfterTheyAreReferenced() {
		String[] code = {
				"var foo;",
				"function x() {",
				"  foo();",
				"  var foo;",
				"}"
		};

		th.test(code);
	}

	@Test
	public void testBlockScopeRedefinesGlobalsOnlyOutsideOfBlocks() {
		String[] code = {
				"{",
				"  let Map = true;",
				"}",
				"let Map = false;"
		};

		th.addError(4, 5, "Redefinition of 'Map'.");
		th.test(code, new LinterOptions().set("esnext", true).set("browser", true));
	}

	@Test
	public void testDestructuringFunctionAsMoz() {
		// Example from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function userId({id}) {",
				"  return id;",
				"}",
				"function whois({displayName: displayName, fullName: {firstName: name}}) {",
				"  print(displayName + ' is ' + name);",
				"}",
				"var user = {id: 42, displayName: 'jdoe', fullName: {firstName: 'John', lastName: 'Doe'}};",
				"print('userId: ' + userId(user));",
				"whois(user);"
		};

		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testDestructuringFunctionAsEsnext() {
		// Example from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function userId({id}) {",
				"  return id;",
				"}",
				"function whois({displayName: displayName, fullName: {firstName: name}}) {",
				"  print(displayName + ' is ' + name);",
				"}",
				"var user = {id: 42, displayName: 'jdoe', fullName: {firstName: 'John', lastName: 'Doe'}};",
				"print('userId: ' + userId(user));",
				"whois(user);"
		};

		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testDestructuringFunctionAsEs5() {
		// Example from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function userId({id}) {",
				"  return id;",
				"}",
				"function whois({displayName: displayName, fullName: {firstName: name}}) {",
				"  print(displayName + ' is ' + name);",
				"}",
				"var user = {id: 42, displayName: 'jdoe', fullName: {firstName: 'John', lastName: 'Doe'}};",
				"print('userId: ' + userId(user));",
				"whois(user);"
		};

		th.addError(1, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 15,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testDestructuringFunctionAsLegacyJS() {
		// Example from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function userId({id}) {",
				"  return id;",
				"}",
				"function whois({displayName: displayName, fullName: {firstName: name}}) {",
				"  print(displayName + ' is ' + name);",
				"}",
				"var user = {id: 42, displayName: 'jdoe', fullName: {firstName: 'John', lastName: 'Doe'}};",
				"print('userId: ' + userId(user));",
				"whois(user);"
		};

		th.addError(1, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 15,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testDestructuringFunctionDefaultValues() {
		String[] code = {
				"function a([ b = 2, c = 2 ] = []) {}",
				"function d([ f = 2 ], g, [ e = 2 ] = []) {}",
				"function h({ i = 2 }, { j = 2 } = {}) {}",
				"function k({ l: m = 2, n = 2 }) {}",
				"let o = (p, [ q = 2, r = 2 ]) => {};",
				"let s = ({ t = 2 } = {}, [ u = 2 ] = []) => {};",
				"let v = ({ w: x = 2, y = 2 }) => {};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testNonSimpleParameterListStrictTransition() {
		String[] noTransitionNonStrict = {
				"function f() {}",
				"function f(x) {}",
				"var a = x => {};",
				"function f({ x }) {}",
				"function f([ x ]) {}",
				"function f(...x) {}",
				"function f(x = 0) {}"
		};

		th.newTest("no transition: ES6 & non-strict mode");
		th.test(noTransitionNonStrict, new LinterOptions().set("esversion", 6));
		th.newTest("no transition: ES7 & non-strict mode");
		th.test(noTransitionNonStrict, new LinterOptions().set("esversion", 7));

		String[] noTransitionStrict = {
				"'use strict';",
				"function f() {",
				"  'use strict';",
				"}",
				"function f(x) {",
				"  'use strict';",
				"}",
				"var a = x => {",
				"  'use strict';",
				"};",
				"function f({ x }) {",
				"  'use strict';",
				"}",
				"function f([ x ]) {",
				"  'use strict';",
				"}",
				"function f(...x) {",
				"  'use strict';",
				"}",
				"function f(x = 0) {",
				"  'use strict';",
				"}"
		};

		th.newTest("no transition: ES6 & strict mode");
		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.addError(3, 3, "Unnecessary directive \"use strict\".");
		th.addError(6, 3, "Unnecessary directive \"use strict\".");
		th.addError(9, 3, "Unnecessary directive \"use strict\".");
		th.addError(12, 3, "Unnecessary directive \"use strict\".");
		th.addError(15, 3, "Unnecessary directive \"use strict\".");
		th.addError(18, 3, "Unnecessary directive \"use strict\".");
		th.addError(21, 3, "Unnecessary directive \"use strict\".");
		th.test(noTransitionStrict, new LinterOptions().set("esversion", 6));
		th.newTest("no transition: ES7 & strict mode");
		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.addError(3, 3, "Unnecessary directive \"use strict\".");
		th.addError(6, 3, "Unnecessary directive \"use strict\".");
		th.addError(9, 3, "Unnecessary directive \"use strict\".");
		th.addError(12, 3, "Unnecessary directive \"use strict\".");
		th.addError(12, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.addError(15, 3, "Unnecessary directive \"use strict\".");
		th.addError(15, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.addError(18, 3, "Unnecessary directive \"use strict\".");
		th.addError(18, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.addError(21, 3, "Unnecessary directive \"use strict\".");
		th.addError(21, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.test(noTransitionStrict, new LinterOptions().set("esversion", 7));

		String[] directTransition = {
				"function f() {",
				"  'use strict';",
				"}",
				"function f(x) {",
				"  'use strict';",
				"}",
				"var a = x => {",
				"  'use strict';",
				"};",
				"function f({ x }) {",
				"  'use strict';",
				"}",
				"function f([ x ]) {",
				"  'use strict';",
				"}",
				"function f(...x) {",
				"  'use strict';",
				"}",
				"function f(x = 0) {",
				"  'use strict';",
				"}"
		};

		th.newTest("direct transition: ES6");
		th.test(directTransition, new LinterOptions().set("esversion", 6));

		th.newTest("direct transition: ES7");
		th.addError(11, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.addError(14, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.addError(17, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.addError(20, 3,
				"Functions defined outside of strict mode with non-simple parameter lists may not enable strict mode.");
		th.test(directTransition, new LinterOptions().set("esversion", 7));

		String[] indirectTransition = {
				"function f() {",
				"  function g() {",
				"    'use strict';",
				"  }",
				"}",
				"function f(x) {",
				"  function g() {",
				"    'use strict';",
				"  }",
				"}",
				"var a = x => {",
				"  function g() {",
				"    'use strict';",
				"  }",
				"};",
				"function f({ x }) {",
				"  function g() {",
				"    'use strict';",
				"  }",
				"}",
				"function f([ x ]) {",
				"  function g() {",
				"    'use strict';",
				"  }",
				"}",
				"function f(...x) {",
				"  function g() {",
				"    'use strict';",
				"  }",
				"}",
				"function f(x = 0) {",
				"  function g() {",
				"    'use strict';",
				"  }",
				"}"
		};

		th.newTest("indirect transition: ES6");
		th.test(indirectTransition, new LinterOptions().set("esversion", 6));
		th.newTest("indirect transition: ES7");
		th.test(indirectTransition, new LinterOptions().set("esversion", 7));
	}

	@Test
	public void testNonVarDestructuringAssignmentStatement() {
		String[] codeValid = {
				"let b;",
				"[b] = b;",
				"([b] = b);",
				"({b} = b);",
				"let c = {b} = b;",
				"c = [b] = b;",
				"c = ({b} = b);",
				"c = ([b] = b);"
		};

		String[] codeInvalid = {
				"let b;",
				"{b} = b;",
				"({b}) = b;",
				"([b]) = b;",
				"[{constructor(){}}] = b;",
				"([{constructor(){}}] = b);",
				"let c = ({b}) = b;",
				"c = ([b]) = b;"
		};

		th.test(codeValid, new LinterOptions().set("esnext", true));

		th.addError(2, 2, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 3, "Missing semicolon.");
		th.addError(2, 5, "Expected an identifier and instead saw '='.");
		th.addError(2, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 6, "Missing semicolon.");
		th.addError(2, 7, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 7, "Bad assignment.");
		th.addError(4, 7, "Bad assignment.");
		th.addError(5, 14, "Expected ',' and instead saw '('.");
		th.addError(5, 15, "Expected an identifier and instead saw ')'.");
		th.addError(5, 16, "Expected ',' and instead saw '{'.");
		th.addError(5, 18, "Expected ',' and instead saw '}'.");
		th.addError(6, 15, "Expected ',' and instead saw '('.");
		th.addError(6, 16, "Expected an identifier and instead saw ')'.");
		th.addError(6, 17, "Expected ',' and instead saw '{'.");
		th.addError(6, 19, "Expected ',' and instead saw '}'.");
		th.addError(7, 15, "Bad assignment.");
		th.addError(8, 11, "Bad assignment.");
		th.test(codeInvalid, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testInvalidForEach() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"for each (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}"
		};

		th.addError(1, 5, "Invalid for each loop.");
		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testInvalidForEachAsEsnext() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"for each (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}"
		};

		th.addError(1, 5, "Invalid for each loop.");
		th.addError(1, 5, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testInvalidForEachAsEs5() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"for each (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}"
		};

		th.addError(1, 5, "Invalid for each loop.");
		th.addError(1, 5, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 11, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testInvalidForEachAsLegacyJS() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"for each (let i = 0; i<15; ++i) {",
				"  print(i);",
				"}"
		};

		th.addError(1, 5, "Invalid for each loop.");
		th.addError(1, 5, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 11, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testEsnextGenerator() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function* fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",

				"var g = fib();",
				"for (var i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));

		th.newTest("YieldExpression in parameters - declaration");
		th.addError(1, 18, "Unexpected 'yield'.");
		th.test("function * g(x = yield) { yield; }", new LinterOptions().set("esversion", 6));

		th.newTest("YieldExpression in parameters - expression");
		th.addError(1, 22, "Unexpected 'yield'.");
		th.test("void function * (x = yield) { yield; };", new LinterOptions().set("esversion", 6));

		th.newTest("YieldExpression in parameters - method");
		th.addError(1, 16, "Unexpected 'yield'.");
		th.test("void { * g(x = yield) { yield; } };", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testEsnextGeneratorAsMozExtentsion() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function* fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",

				"var g = fib();",
				"for (var i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testEsnextGeneratorAsEs5() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function* fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",

				"var g = fib();",
				"for (var i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 5, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testEsnextGeneratorAsLegacyJS() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function* fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",

				"var g = fib();",
				"for (var i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 5, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testEsnextGeneratorWithoutYield() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function* fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    [i, j] = [j, i + j];",
				"    return i;",
				"  }",
				"}",

				"var g = fib();",
				"for (let i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.addError(7, 1, "A generator function should contain at least one yield expression.");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testEsnextGeneratorWithoutYieldAndCheckTurnedOff() {
		String[] code = {
				"function* emptyGenerator() {}",

				"emptyGenerator();"
		};

		th.test(code, new LinterOptions().set("esnext", true).set("noyield", true).set("unused", true)
				.set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testEsnextGeneratorWithYieldDelegationGH1544() {
		String[] code = {
				"function* G() {",
				"  yield* (function*(){})();",
				"}"
		};

		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 11, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 23, "A generator function should contain at least one yield expression.");
		th.test(code);

		th.newTest();
		th.test(code, new LinterOptions().set("esnext", true).set("noyield", true));

	}

	@Test
	public void testMozillaGenerator() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",
				"var g = fib();",
				"for (let i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true)
				.setPredefineds("print", "Iterator"));
	}

	@Test
	public void testMozillaGeneratorAsEsnext() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",
				"var g = fib();",
				"for (let i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.addError(4, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 10, "Missing semicolon.");
		th.addError(4, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 5, "'yield' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true)
				.setPredefineds("print", "Iterator"));

		th.newTest();
		th.addError(4, 5, "Yield expressions may only occur within generator functions.");
		th.test(code, new LinterOptions().set("esnext", true).set("moz", true));
	}

	@Test
	public void testYieldExpressionWithinTryCatch() {
		// see issue: https://github.com/jshint/jshint/issues/1505
		String[] code = {
				"function* fib() {",
				"  try {",
				"    yield 1;",
				"  } catch (err) {",
				"    yield err;",
				"  }",
				"}",
				"var g = fib();",
				"for (let i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true)
				.setPredefineds("print", "Iterator"));
	}

	@Test
	public void testCatchBlockNoCurlies() {
		String[] code = {
				"try {} catch(e) e.toString();"
		};
		th.addError(1, 17, "Expected '{' and instead saw 'e'.");
		th.test(code, new LinterOptions());
	}

	@Test
	public void testOptionalCatch() {
		String code = "try {} catch {}";

		th.addError(1, 8, "'optional catch binding' is only available in ES10 (use 'esversion: 10').");
		th.test(code);

		th.newTest();
		th.test(code, new LinterOptions().set("esversion", 10));
	}

	@Test
	public void testStrictViolationUseOfArgumentsAndEval() {
		String[] code = {
				"'use strict';",
				"var arguments;",
				"(function() {",
				"  var eval;",
				"}());"
		};
		th.addError(2, 5, "Strict violation.");
		th.addError(4, 7, "Strict violation.");
		th.test(code, new LinterOptions().set("strict", "global"));

		th.newTest("via `catch` clause binding (valid)");
		th.test(new String[] {
				"try {} catch (arguments) {}",
				"try {} catch (eval) {}"
		});

		th.newTest("via `catch` clause binding (invalid)");
		th.addError(2, 15, "Strict violation.");
		th.addError(3, 15, "Strict violation.");
		th.test(new String[] {
				"'use strict';",
				"try {} catch (arguments) {}",
				"try {} catch (eval) {}"
		}, new LinterOptions().set("strict", "global"));

		th.newTest("via parameter (valid)");
		th.test(new String[] {
				"function f1(arguments) {}",
				"function f2(eval) {}"
		});

		th.newTest("via parameter - (invalid)");
		th.addError(1, 13, "Strict violation.");
		th.addError(2, 13, "Strict violation.");
		th.test(new String[] {
				"function f1(arguments) { 'use strict'; }",
				"function f2(eval) { 'use strict'; }"
		});

		th.newTest("as function binding (valid)");
		th.test(new String[] {
				"function arguments() {}",
				"function eval() {}",
				"void function arguments() {};",
				"void function eval() {};"
		});

		th.newTest("as function bindings for expressions with inferred names (valid)");
		th.test(new String[] {
				"var arguments = function() {};",
				"(function() {",
				"  var eval = function() {};",
				"}());"
		});

		th.newTest("as function declaration binding (invalid)");
		th.addError(1, 10, "Strict violation.");
		th.addError(2, 10, "Strict violation.");
		th.addError(5, 12, "Strict violation.");
		th.addError(6, 12, "Strict violation.");
		th.addError(10, 12, "Strict violation.");
		th.addError(10, 26, "Unnecessary directive \"use strict\".");
		th.addError(11, 12, "Strict violation.");
		th.addError(11, 21, "Unnecessary directive \"use strict\".");
		th.test(new String[] {
				"function arguments() { 'use strict'; }",
				"function eval() { 'use strict'; }",
				"(function() {",
				"  'use strict';",
				"  function arguments() {}",
				"  function eval() {}",
				"}());",
				"(function() {",
				"  'use strict';",
				"  function arguments() { 'use strict'; }",
				"  function eval() { 'use strict'; }",
				"}());"
		});

		th.newTest("as function expression binding (invalid)");
		th.addError(1, 15, "Strict violation.");
		th.addError(2, 15, "Strict violation.");
		th.addError(5, 17, "Strict violation.");
		th.addError(6, 17, "Strict violation.");
		th.addError(10, 17, "Strict violation.");
		th.addError(10, 31, "Unnecessary directive \"use strict\".");
		th.addError(11, 17, "Strict violation.");
		th.addError(11, 26, "Unnecessary directive \"use strict\".");
		th.test(new String[] {
				"void function arguments() { 'use strict'; };",
				"void function eval() { 'use strict'; };",
				"(function() {",
				"  'use strict';",
				"  void function arguments() {};",
				"  void function eval() {};",
				"}());",
				"(function() {",
				"  'use strict';",
				"  void function arguments() { 'use strict'; };",
				"  void function eval() { 'use strict'; };",
				"}());"
		});
	}

	@Test
	public void testMozillaGeneratorAsEs5() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",
				"var g = fib();",
				"for (let i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.addError(4, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 10, "Missing semicolon.");
		th.addError(4, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 5, "'yield' is not defined.");
		th.addError(5, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print", "Iterator")); // es5
	}

	@Test
	public void testMozillaGeneratorAsLegacyJS() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function fib() {",
				"  var i = 0, j = 1;",
				"  while (true) {",
				"    yield i;",
				"    [i, j] = [j, i + j];",
				"  }",
				"}",
				"var g = fib();",
				"for (let i = 0; i < 10; i++)",
				"  print(g.next());"
		};

		th.addError(4, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 10, "Missing semicolon.");
		th.addError(4, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 5, "'yield' is not defined.");
		th.addError(5, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true)
				.setPredefineds("print", "Iterator"));
	}

	@Test
	public void testArrayComprehension() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function *range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [for (i of range(0, 10)) i * i];",
				"var evens = [for (i of range(0, 21)) if (i % 2 === 0) i];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);",
				"(function() {",
				"  var ten_squares = [for (i of range(0, 10)) i * i];",
				"  print('squares:', ten_squares);",
				"}());"
		};

		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayComprehensionUnusedAndUndefined() {
		String[] code = {
				"var range = [1, 2];",
				"var a = [for (i of range) if (i % 2 === 0) i];",
				"var b = [for (j of range) doesnotexist];",
				"var c = [for (\\u0024 of range) 1];"
		};

		th.addError(2, 5, "'a' is defined but never used.");
		th.addError(3, 15, "'j' is defined but never used.");
		th.addError(3, 27, "'doesnotexist' is not defined.");
		th.addError(3, 5, "'b' is defined but never used.");
		th.addError(4, 15, "'\\u0024' is defined but never used.");
		th.addError(4, 5, "'c' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));

		List<Token> unused = th.getJSHint().generateSummary().getUnused();
		assertEquals(Arrays.asList(
				new Token("a", 2, 5),
				new Token("b", 3, 5),
				new Token("c", 4, 5)
		// new Token("j", 3, 15) // see gh-2440
		), unused);

		List<ImpliedGlobal> implieds = th.getJSHint().generateSummary().getImplieds();
		assertEquals(Arrays.asList(new ImpliedGlobal("doesnotexist", 3)), implieds);
	}

	@Test
	public void testGH1856MistakenlyIdentifiedAsArrayComprehension() {
		String[] code = {
				"function main(value) {",
				"  var result = ['{'],",
				"      key;",
				"  for (key in value) {",
				"    result.push(key);",
				"  }",
				"  return result;",
				"}",
				"main({abc:true});"
		};

		th.test(code);
	}

	@Test
	public void testGH1413WronglyDetectedAsArrayComprehension() {
		String[] code = {
				"var a = {};",
				"var b = [ a.for ];"
		};

		th.test(code, new LinterOptions().set("unused", false));
	}

	@Test
	public void testMozStyleArrayComprehension() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [i * i for each (i in range(0, 10))];",
				"var evens = [i for each (i in range(0, 21)) if (i % 2 === 0)];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayComprehensionWithForOf() {
		// example adapted from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function *range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [for (i of range(0, 10)) i * i];",
				"var evens = [for (i of range(0, 21)) if (i % 2 === 0) i];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionWithForOf() {
		// example adapted from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [i * i for (i of range(0, 10))];",
				"var evens = [i for (i of range(0, 21)) if (i % 2 === 0)];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayComprehensionWithUnusedVariables() {
		String[] code = {
				"var ret = [for (i of unknown) i];",
				"print('ret:', ret);"
		};

		th.addError(1, 22, "'unknown' is not defined.");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionWithUnusedVariables() {
		String[] code = {
				"var ret = [i for (i of unknown)];",
				"print('ret:', ret);"
		};

		th.addError(1, 24, "'unknown' is not defined.");
		th.test(code,
				new LinterOptions().set("moz", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionAsEsnext() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [i * i for each (i in range(0, 10))];",
				"var evens = [i for each (i in range(0, 21)) if (i % 2 === 0)];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.addError(3, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 10, "Missing semicolon.");
		th.addError(3, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 5, "'yield' is not defined.");
		th.addError(6, 20, "Expected 'for' and instead saw 'i'.");
		th.addError(6, 30, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(7, 14, "Expected 'for' and instead saw 'i'.");
		th.addError(7, 20, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code,
				new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).setPredefineds("print"));

		th.newTest();
		th.addError(3, 5, "Yield expressions may only occur within generator functions.");
		th.test(code, new LinterOptions().set("esnext", true).set("moz", true));
	}

	@Test
	public void testArrayComprehensionAsEs5() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function *range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [for (i of range(0, 10)) i * i];",
				"var evens = [for (i of range(0, 21)) if (i % 2 === 0) i];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.addError(1, 10, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 5, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 19,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(7, 13,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testMozStyleArrayComprehensionAsEs5() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [i * i for each (i in range(0, 10))];",
				"var evens = [i for each (i in range(0, 21)) if (i % 2 === 0)];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.addError(2, 8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 10, "Missing semicolon.");
		th.addError(3, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 5, "'yield' is not defined.");
		th.addError(6, 19,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(6, 20, "Expected 'for' and instead saw 'i'.");
		th.addError(6, 30, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(7, 13,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(7, 14, "Expected 'for' and instead saw 'i'.");
		th.addError(7, 20, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testArrayComprehensionAsLegacyJS() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [for (i of range(0, 10)) i * i];",
				"var evens = [for (i of range(0, 21)) if (i % 2 === 0) i];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.addError(2, 8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 10, "Missing semicolon.");
		th.addError(3, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 5, "'yield' is not defined.");
		th.addError(6, 19,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(7, 13,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionAsLegacyJS() {
		// example taken from
		// https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
				"function range(begin, end) {",
				"  for (let i = begin; i < end; ++i) {",
				"    yield i;",
				"  }",
				"}",
				"var ten_squares = [i * i for each (i in range(0, 10))];",
				"var evens = [i for each (i in range(0, 21)) if (i % 2 === 0)];",
				"print('squares:', ten_squares);",
				"print('evens:', evens);"
		};

		th.addError(2, 8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 10, "Missing semicolon.");
		th.addError(3, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 5, "'yield' is not defined.");

		th.addError(6, 19,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(6, 20, "Expected 'for' and instead saw 'i'.");
		th.addError(6, 30, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(7, 13,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(7, 14, "Expected 'for' and instead saw 'i'.");
		th.addError(7, 20, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code,
				new LinterOptions().set("es3", true).set("unused", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayComprehensionWithDestArrayAtGlobalScope() {
		String[] code = {
				"[for ([i, j] of [[0,0], [1,1], [2,2]]) [i, j] ];",
				"var destarray_comparray_1 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, [j, j] ]];",
				"var destarray_comparray_2 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, {i: [i, j]} ]];"
		};

		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScope() {
		String[] code = {
				"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};

		th.test(code, new LinterOptions().set("moz", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScopeAsEsnext() {
		String[] code = {
				"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};

		th.addError(1, 3, "Expected 'for' and instead saw '['.");
		th.addError(1, 14, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(2, 31, "Expected 'for' and instead saw '['.");
		th.addError(2, 48, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(3, 31, "Expected 'for' and instead saw '['.");
		th.addError(3, 53, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayComprehensionWithDestArrayAtGlobalScopeAsEs5() {
		String[] code = {
				"[for ([i, j] of [[0,0], [1,1], [2,2]]) [i, j] ];",
				"var destarray_comparray_1 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, [j, j] ] ];",
				"var destarray_comparray_2 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, {i: [i, j]} ] ];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(2, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(3, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScopeAsEs5() {
		String[] code = {
				"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 3, "Expected 'for' and instead saw '['.");
		th.addError(1, 14, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(2, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(2, 31, "Expected 'for' and instead saw '['.");
		th.addError(2, 48, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(3, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(3, 31, "Expected 'for' and instead saw '['.");
		th.addError(3, 53, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testArrayComprehensionWithDestArrayAtGlobalScopeAsJSLegacy() {
		String[] code = {
				"[for ([i, j] of [[0,0], [1,1], [2,2]]) [i, j] ];",
				"var destarray_comparray_1 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, [j, j] ] ];",
				"var destarray_comparray_2 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, {i: [i, j]} ] ];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(2, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(3, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScopeAsJSLegacy() {
		String[] code = {
				"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
				"var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 3, "Expected 'for' and instead saw '['.");
		th.addError(1, 14, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(2, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(2, 31, "Expected 'for' and instead saw '['.");
		th.addError(2, 48, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(3, 29,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(3, 31, "Expected 'for' and instead saw '['.");
		th.addError(3, 53, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayComprehensionImbricationWithDestArray() {
		String[] code = {
				"[for ([i, j] of [for ([a, b] of [[2,2], [3,4]]) [a, b] ]) [i, j] ];"
		};

		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArray() {
		String[] code = {
				"[ [i, j] for ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};

		th.test(code, new LinterOptions().set("moz", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayUsingForOf() {
		String[] code = {
				"[ [i, j] for ([i, j] of [[a, b] for ([a, b] of [[2,2], [3,4]])]) ];"
		};

		th.test(code, new LinterOptions().set("moz", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayAsEsnext() {
		String[] code = {
				"[ [i, j] for each ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};

		th.addError(1, 3, "Expected 'for' and instead saw '['.");
		th.addError(1, 14, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 31, "Expected 'for' and instead saw '['.");
		th.addError(1, 42, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayComprehensionImbricationWithDestArrayAsEs5() {
		String[] code = {
				"[for ([i, j] of [for ([a, b] of [[2,2], [3,4]]) [a, b] ]) [i, j] ];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 17,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayAsEs5() {
		String[] code = {
				"[for ([i, j] of [for ([a, b] of [[2,2], [3,4]]) [a, b] ]) [i, j] ];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 17,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testArrayComprehensionImbricationWithDestArrayAsLegacyJS() {
		String[] code = {
				"[ [i, j] for each ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 30,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 3, "Expected 'for' and instead saw '['.");
		th.addError(1, 31, "Expected 'for' and instead saw '['.");
		th.addError(1, 14, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 42, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayAsLegacyJS() {
		String[] code = {
				"[ [i, j] for each ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};

		th.addError(1, 1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 30,
				"'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 3, "Expected 'for' and instead saw '['.");
		th.addError(1, 31, "Expected 'for' and instead saw '['.");
		th.addError(1, 14, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, 42, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testNoFalsePositiveArrayComprehension() {
		String[] code = {
				"var foo = []; for (let i in [1,2,3]) { print(i); }"
		};

		th.test(code, new LinterOptions().set("moz", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testTryCatchFilters() {
		String[] code = {
				"try {",
				"  throw {name: 'foo', message: 'bar'};",
				"}",
				"catch (e if e.name === 'foo') {",
				"  print (e.message);",
				"}"
		};

		th.test(code, new LinterOptions().set("moz", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testTryCatchFiltersAsEsnext() {
		String[] code = {
				"try {",
				"  throw {name: 'foo', message: 'bar'};",
				"}",
				"catch (e if e.name === 'foo') {",
				"  print (e.message);",
				"}"
		};

		th.addError(4, 8, "'catch filter' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testTryCatchFiltersAsEs5() {
		String[] code = {
				"try {",
				"  throw {name: 'foo', message: 'bar'};",
				"}",
				"catch (e if e.name === 'foo') {",
				"  print (e.message);",
				"}"
		};

		th.addError(4, 8, "'catch filter' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testTryCatchFiltersAsLegacyJS() {
		String[] code = {
				"try {",
				"  throw {name: 'foo', message: 'bar'};",
				"}",
				"catch (e if e.name === 'foo') {",
				"  print (e.message);",
				"}"
		};

		th.addError(4, 8, "'catch filter' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testFunctionClosureExpression() {
		String[] code = {
				"let (arr = [1,2,3]) {",
				"  arr.every(function (o) o instanceof Object);",
				"}"
		};

		th.test(code, new LinterOptions().set("es3", true).set("moz", true).set("undef", true));
	}

	@Test
	public void testFunctionClosureExpressionAsEsnext() {
		String[] code = {
				"var arr = [1,2,3];",
				"arr.every(function (o) o instanceof Object);"
		};

		th.addError(2, 22,
				"'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true));
	}

	@Test
	public void testFunctionClosureExpressionAsEs5() {
		String[] code = {
				"var arr = [1,2,3];",
				"arr.every(function (o) o instanceof Object);"
		};

		th.addError(2, 22,
				"'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true)); // es5
	}

	@Test
	public void testFunctionClosureExpressionAsLegacyJS() {
		String[] code = {
				"var arr = [1,2,3];",
				"arr.every(function (o) o instanceof Object);"
		};

		th.addError(2, 22,
				"'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true));
	}

	@Test
	public void testForOfAsEsnext() {
		String[] code = {
				"for (let x of [1,2,3,4]) {",
				"    print(x);",
				"}",
				"for (let x of [1,2,3,4]) print(x);",
				"for (const x of [1,2,3,4]) print(x);",
				"var xg, yg;",
				"for (xg = 1 of [1,2,3,4]) print(xg);",
				"for (xg, yg of [1,2,3,4]) print(xg + yg);",
				"for (xg = 1, yg = 2 of [1,2,3,4]) print(xg + yg);",
				"for (var xv = 1 of [1,2,3,4]) print(xv);",
				"for (var xv, yv of [1,2,3,4]) print(xv + yv);",
				"for (var xv = 1, yv = 2 of [1,2,3,4]) print(xv + yv);",
				"for (let x = 1 of [1,2,3,4]) print(x);",
				"for (let x, y of [1,2,3,4]) print(x + y);",
				"for (let x = 1, y = 2 of [1,2,3,4]) print(x + y);",
				"for (const x = 1 of [1,2,3,4]) print(x);",
				"for (const x, y of [1,2,3,4]) print(x + y);",
				"for (const x = 1, y = 2 of [1,2,3,4]) print(x + y);"
		};

		th.addError(7, 9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(8, 8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, 17, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(10, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(11, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(12, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(12, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(13, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(14, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(15, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(15, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(16, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(17, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(18, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(18, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));

		th.newTest("Left-hand side as MemberExpression");
		th.test(new String[] {
				"for (x.y of []) {}",
				"for (x[z] of []) {}"
		}, new LinterOptions().set("esversion", 2015));

		th.newTest("Left-hand side as MemberExpression (invalid)");
		th.addError(1, 10, "Bad assignment.");
		th.addError(2, 13, "Bad assignment.");
		th.test(new String[] {
				"for (x+y of {}) {}",
				"for ((this) of {}) {}"
		}, new LinterOptions().set("esversion", 2015));

		th.newTest("let binding named `of`");
		th.test("for (let of of []) {}", new LinterOptions().set("esversion", 2015));
	}

	@Test
	public void testForOfAsEs5() {
		String[] code = {
				"for (let x of [1,2,3,4]) {",
				"    print(x);",
				"}",
				"for (let x of [1,2,3,4]) print(x);",
				"for (const x of [1,2,3,4]) print(x);",
				"for (x = 1 of [1,2,3,4]) print(x);",
				"for (x, y of [1,2,3,4]) print(x + y);",
				"for (x = 1, y = 2 of [1,2,3,4]) print(x + y);",
				"for (var x = 1 of [1,2,3,4]) print(x);",
				"for (var x, y of [1,2,3,4]) print(x + y);",
				"for (var x = 1, y = 2 of [1,2,3,4]) print(x + y);",
				"for (let x = 1 of [1,2,3,4]) print(x);",
				"for (let x, y of [1,2,3,4]) print(x + y);",
				"for (let x = 1, y = 2 of [1,2,3,4]) print(x + y);",
				"for (const x = 1 of [1,2,3,4]) print(x);",
				"for (const x, y of [1,2,3,4]) print(x + y);",
				"for (const x = 1, y = 2 of [1,2,3,4]) print(x + y);"
		};

		th.addError(1, 12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 14, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 8, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, 11, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 7, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 19, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 11, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 15, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 16, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(10, 15, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(10, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(11, 23, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(11, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(12, 16, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(12, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(12, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 15, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(13, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 23, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(14, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(15, 18, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(15, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(15, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(16, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(17, 25, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(17, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(17, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(17, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testForOfAsLegacyJS() {
		String[] code = {
				"for (let x of [1,2,3,4]) {",
				"    print(x);",
				"}",
				"for (let x of [1,2,3,4]) print(x);",
				"for (const x of [1,2,3,4]) print(x);",
				"for (x = 1 of [1,2,3,4]) print(x);",
				"for (x, y of [1,2,3,4]) print(x + y);",
				"for (x = 1, y = 2 of [1,2,3,4]) print(x + y);",
				"for (var x = 1 of [1,2,3,4]) print(x);",
				"for (var x, y of [1,2,3,4]) print(x + y);",
				"for (var x = 1, y = 2 of [1,2,3,4]) print(x + y);",
				"for (let x = 1 of [1,2,3,4]) print(x);",
				"for (let x, y of [1,2,3,4]) print(x + y);",
				"for (let x = 1, y = 2 of [1,2,3,4]) print(x + y);",
				"for (const x = 1 of [1,2,3,4]) print(x);",
				"for (const x, y of [1,2,3,4]) print(x + y);",
				"for (const x = 1, y = 2 of [1,2,3,4]) print(x + y);"
		};

		th.addError(1, 12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 14, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 8, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, 11, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 7, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 19, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 11, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 15, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 16, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(10, 15, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(10, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(11, 23, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(11, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(12, 16, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(12, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(12, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 15, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(13, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 23, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(14, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(15, 18, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(15, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(15, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(16, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(17, 25, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(17, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(17, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(17, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayDestructuringForOfAsEsnext() {
		String[] basic = {
				"for ([i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);"
		};

		th.newTest("basic");
		th.addError(1, 7, "Creating global 'for' variable. Should be 'for (var i ...'.");
		th.addError(1, 10, "Creating global 'for' variable. Should be 'for (var v ...'.");
		th.test(basic, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));

		String[] bad = {
				"for ([i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for ([i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for ([i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};

		th.newTest("errors #1");
		th.addError(1, 13, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(2, 12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, 21, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, 12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));

		String[] bad2 = {
				"for (let [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};

		th.newTest("errors #2");
		th.addError(1, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(2, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad2, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testArrayDestructuringForOfAsEs5() {
		String[] basic = {
				"for ([i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);"
		};

		th.newTest("basic");
		th.addError(1, 13, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 7, "Creating global 'for' variable. Should be 'for (var i ...'.");
		th.addError(1, 10, "Creating global 'for' variable. Should be 'for (var v ...'.");
		th.addError(2, 17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 19, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("undef", true).setPredefineds("print")); // es5

		String[] bad = {
				"for ([i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for ([i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for ([i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};

		th.newTest("errors #1");
		th.addError(1, 22, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 13, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 21, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 12,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 30, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, 21, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 12,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 26, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 25, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("undef", true).setPredefineds("print")); // es5

		String[] bad2 = {
				"for (let [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};

		th.newTest("errors #2");
		th.addError(1, 26, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 25, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 28, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 27, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 18,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 18,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad2, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testArrayDestructuringForOfAsLegacyJS() {
		String[] basic = {
				"for ([i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);"
		};

		th.newTest("basic");
		th.addError(1, 13, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 7, "Creating global 'for' variable. Should be 'for (var i ...'.");
		th.addError(1, 10, "Creating global 'for' variable. Should be 'for (var v ...'.");
		th.addError(2, 17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 19, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print")); // es3

		String[] bad = {
				"for ([i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for ([i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for ([i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (var [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};

		th.newTest("errors #1");
		th.addError(1, 22, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 13, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 21, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 12,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 30, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, 21, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 12,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 26, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 25, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print")); // es3

		String[] bad2 = {
				"for (let [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (let [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
				"for (const [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};

		th.newTest("errors #2");
		th.addError(1, 26, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 25, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 16,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 28, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 27, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 18,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 18,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad2, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print")); // es3
	}

	@Test
	public void testObjectDestructuringForOfAsEsnext() {
		String[] basic = {
				"var obj1 = { key: 'a', data: { value: 1 } };",
				"var obj2 = { key: 'b', data: { value: 2 } };",
				"var arr = [obj1, obj2];",
				"for ({key, data: { value } } of arr) print(key + '=' + value);",
				"for (var {key, data: { value } } of arr) print(key + '=' + value);",
				"for (let {key, data: { value } } of arr) print(key + '=' + value);",
				"for (const {key, data: { value } } of arr) print(key + '=' + value);"
		};

		th.newTest("basic");
		th.addError(4, 7, "Creating global 'for' variable. Should be 'for (var key ...'.");
		th.addError(4, 20, "Creating global 'for' variable. Should be 'for (var value ...'.");
		th.test(basic, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));

		String[] bad = {
				"var obj1 = { key: 'a', data: { val: 1 } };",
				"var obj2 = { key: 'b', data: { val: 2 } };",
				"var arr = [obj1, obj2];",
				"for ({key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for ({key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for ({key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);",
				"for (var {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (var {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (var {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);"
		};

		th.newTest("errors #1");
		th.addError(4, 25, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, 24, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 33, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 24, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(7, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(8, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));

		String[] bad2 = {
				"var obj1 = { key: 'a', data: { val: 1 } };",
				"var obj2 = { key: 'b', data: { val: 2 } };",
				"var arr = [obj1, obj2];",
				"for (let {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (let {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (let {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);",
				"for (const {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (const {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (const {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);"
		};

		th.newTest("errors #2");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(7, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(8, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad2, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testObjectDestructuringForOfAsEs5() {
		String[] basic = {
				"var obj1 = { key: 'a', data: { value: 1 } };",
				"var obj2 = { key: 'b', data: { value: 2 } };",
				"var arr = [obj1, obj2];",
				"for ({key, data: { value } } of arr) print(key + '=' + value);",
				"for (var {key, data: { value } } of arr) print(key + '=' + value);",
				"for (let {key, data: { value } } of arr) print(key + '=' + value);",
				"for (const {key, data: { value } } of arr) print(key + '=' + value);"
		};

		th.newTest("basic");
		th.addError(4, 30, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 7, "Creating global 'for' variable. Should be 'for (var key ...'.");
		th.addError(4, 20, "Creating global 'for' variable. Should be 'for (var value ...'.");
		th.addError(5, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("undef", true).setPredefineds("print")); // es5

		String[] bad = {
				"var obj1 = { key: 'a', data: { val: 1 } };",
				"var obj2 = { key: 'b', data: { val: 2 } };",
				"var arr = [obj1, obj2];",
				"for ({key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for ({key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for ({key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);",
				"for (var {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (var {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (var {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);"
		};

		th.newTest("errors #1");
		th.addError(4, 32, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 25, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 33, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 24, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 24,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 40, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 24, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 33, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 24,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 37, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 44, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("undef", true).setPredefineds("print")); // es5

		String[] bad2 = {
				"var obj1 = { key: 'a', data: { val: 1 } };",
				"var obj2 = { key: 'b', data: { val: 2 } };",
				"var arr = [obj1, obj2];",
				"for (let {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (let {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (let {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);",
				"for (const {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (const {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (const {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);"
		};

		th.newTest("errors #2");
		th.addError(4, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 37, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 44, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 38, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 39, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 30,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 46, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 30,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad2, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testObjectDestructuringForOfAsLegacyJS() {
		String[] basic = {
				"var obj1 = { key: 'a', data: { value: 1 } };",
				"var obj2 = { key: 'b', data: { value: 2 } };",
				"var arr = [obj1, obj2];",
				"for ({key, data: { value } } of arr) print(key + '=' + value);",
				"for (var {key, data: { value } } of arr) print(key + '=' + value);",
				"for (let {key, data: { value } } of arr) print(key + '=' + value);",
				"for (const {key, data: { value } } of arr) print(key + '=' + value);"
		};

		th.newTest("basic");
		th.addError(4, 30, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 7, "Creating global 'for' variable. Should be 'for (var key ...'.");
		th.addError(4, 20, "Creating global 'for' variable. Should be 'for (var value ...'.");
		th.addError(5, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 34, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print")); // es3

		String[] bad = {
				"var obj1 = { key: 'a', data: { val: 1 } };",
				"var obj2 = { key: 'b', data: { val: 2 } };",
				"var arr = [obj1, obj2];",
				"for ({key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for ({key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for ({key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);",
				"for (var {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (var {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (var {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);"
		};

		th.newTest("errors #1");
		th.addError(4, 32, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 25, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 33, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 24, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 24,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 40, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 24, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 33, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 5,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 24,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 37, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 44, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print")); // es3

		String[] bad2 = {
				"var obj1 = { key: 'a', data: { val: 1 } };",
				"var obj2 = { key: 'b', data: { val: 2 } };",
				"var arr = [obj1, obj2];",
				"for (let {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (let {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (let {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);",
				"for (const {key, data: {val}} = obj1 of arr) print(key + '=' + val);",
				"for (const {key, data: {val}}, {a, b} of arr) print(key + '=' + val);",
				"for (const {key, data: {val}}, {a, b} = obj1 of arr) print(key + '=' + val);"
		};

		th.newTest("errors #2");
		th.addError(4, 36, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 37, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 44, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, 6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 28,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 38, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 39, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 30,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 46, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, 6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, 6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 6,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, 30,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad2, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print")); // es3
	}

	@Test
	public void testTryMultiCatchForMozExtensions() {
		String[] code = {
				"try {",
				"    print('X');",
				"} catch (err) {",
				"    print(err);",
				"} catch (err) {",
				"    print(err);",
				"} finally {",
				"    print('Z');",
				"}"
		};

		th.test(code, new LinterOptions().set("moz", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testTryMultiCatchAsEsnext() {
		String[] code = {
				"try {",
				"    print('X');",
				"} catch (err) {",
				"    print(err);",
				"} catch (err) {",
				"    print(err);",
				"} finally {",
				"    print('Z');",
				"}"
		};

		th.addError(5, 3,
				"'multiple catch blocks' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testTryMultiCatchAsEs5() {
		String[] code = {
				"try {",
				"    print('X');",
				"} catch (err) {",
				"    print(err);",
				"} catch (err) {",
				"    print(err);",
				"} finally {",
				"    print('Z');",
				"}"
		};

		th.addError(5, 3,
				"'multiple catch blocks' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).setPredefineds("print")); // es5
	}

	@Test
	public void testTryMultiCatchAsLegacyJS() {
		String[] code = {
				"try {",
				"    print('X');",
				"} catch (err) {",
				"    print(err);",
				"} catch (err) {",
				"    print(err);",
				"} finally {",
				"    print('Z');",
				"}"
		};

		th.addError(5, 3,
				"'multiple catch blocks' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).setPredefineds("print"));
	}

	@Test
	public void testNoLetNotDirectlyWithinBlock() {
		String[] code = {
				"if (true) let x = 1;",
				"function foo() {",
				"   if (true)",
				"       let x = 1;",
				"}",
				"for (let x = 0; x < 42; ++x) let a = 1;",
				"for (let x in [1, 2, 3, 4] ) let a = 1;",
				"for (let x of [1, 2, 3, 4] ) let a = 1;",
				"while (true) let a = 1;",
				"if (false) let a = 1; else if (true) let a = 1; else let a = 2;",
				"if (true) if (false) let x = 1;",
				"if (true) if (false) { let x = 1; }",
				"if (true) try { let x = 1; } catch (e) { let x = 1; }"
		};

		th.addError(1, 11, "Let declaration not directly within block.");
		th.addError(4, 8, "Let declaration not directly within block.");
		th.addError(6, 30, "Let declaration not directly within block.");
		th.addError(7, 30, "Let declaration not directly within block.");
		th.addError(8, 30, "Let declaration not directly within block.");
		th.addError(9, 14, "Let declaration not directly within block.");
		th.addError(10, 12, "Let declaration not directly within block.");
		th.addError(10, 38, "Let declaration not directly within block.");
		th.addError(10, 54, "Let declaration not directly within block.");
		th.addError(11, 22, "Let declaration not directly within block.");
		th.test(code, new LinterOptions().set("esversion", 6));
		th.test(code, new LinterOptions().set("moz", true));

		// Don't warn about let expressions
		th.newTest();
		th.test("if (true) let (x = 1) print(x);", new LinterOptions().set("moz", true).setPredefineds("print"));
	}

	@Test
	public void testNoConstNotDirectlyWithinBlock() {
		String[] code = {
				"if (true) const x = 1;",
				"function foo() {",
				"   if (true)",
				"       const x = 1;",
				"}",
				"for (let x = 0; x < 42; ++x) const a = 1;",
				"while (true) const a = 1;",
				"if (false) const a = 1; else if (true) const a = 1; else const a = 2;",
				"if (true) if (false) { const a = 1; }"
		};

		th.addError(1, 11, "Const declaration not directly within block.");
		th.addError(4, 8, "Const declaration not directly within block.");
		th.addError(6, 30, "Const declaration not directly within block.");
		th.addError(7, 14, "Const declaration not directly within block.");
		th.addError(8, 12, "Const declaration not directly within block.");
		th.addError(8, 40, "Const declaration not directly within block.");
		th.addError(8, 58, "Const declaration not directly within block.");
		th.test(code, new LinterOptions().setPredefineds("print").set("esnext", true));
	}

	@Test
	public void testLetDeclaredDirectlyWithinBlock() {
		String[] code = {
				"for (let i;;){",
				"  console.log(i);",
				"}"
		};

		th.test(code, new LinterOptions().set("esnext", true));

		code = new String[] {
				"for (let i;;)",
				"  console.log(i);"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testLetIsDirectlyWithinNestedBlock() {
		String[] code = {
				"if(true) {",
				"  for (let i;;)",
				"    console.log(i);",
				"}"
		};

		th.test(code, new LinterOptions().set("esnext", true));

		code = new String[] {
				"if(true)",
				"  for (let i;;)",
				"    console.log(i);"
		};

		th.test(code, new LinterOptions().set("esnext", true));

		code = new String[] {
				"if(true) {",
				"  for (let i;;){",
				"    console.log(i);",
				"  }",
				"}"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testRegressionForCrashFromGH964() {
		String[] code = {
				"function test(a, b) {",
				"  return a[b] || a[b] = new A();",
				"}"
		};

		th.addError(2, 23, "Bad assignment.");
		th.addError(2, 23, "Did you mean to return a conditional instead of an assignment?");
		th.test(code);
	}

	@Test(groups = { "ASI" })
	public void testASIGH950() {
		String[] code = {
				"var a = b",
				"instanceof c;",

				"var a = { b: 'X' }",
				"delete a.b",

				"var y = true",
				"           && true && false;",

				"function test() {",
				"  return",
				"      { a: 1 }",
				"}",

				"a",
				"++",
				"a",
				"a",
				"--",
				"a"
		};

		th.addError(2, 1,
				"Misleading line break before 'instanceof'; readers may interpret this as an expression boundary.");
		th.addError(6, 12, "Misleading line break before '&&'; readers may interpret this as an expression boundary.");
		th.addError(8, 3, "Line breaking error 'return'.");
		th.addError(9, 12, "Label 'a' on 1 statement.");
		th.addError(9, 12, "Expected an assignment or function call and instead saw an expression.");
		th.addError(11, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(14, 1, "Expected an assignment or function call and instead saw an expression.");

		th.test(code, new LinterOptions().set("es3", true).set("asi", true));
		th.test(code, new LinterOptions().set("asi", true)); // es5
		th.test(code, new LinterOptions().set("esnext", true).set("asi", true));
		th.test(code, new LinterOptions().set("moz", true).set("asi", true));

		th.newTest();
		th.addError(2, 1,
				"Misleading line break before 'instanceof'; readers may interpret this as an expression boundary.");
		th.addError(3, 19, "Missing semicolon.");
		th.addError(4, 11, "Missing semicolon.");
		th.addError(6, 12, "Misleading line break before '&&'; readers may interpret this as an expression boundary.");
		th.addError(8, 3, "Line breaking error 'return'.");
		th.addError(8, 9, "Missing semicolon.");
		th.addError(9, 12, "Label 'a' on 1 statement.");
		th.addError(9, 12, "Expected an assignment or function call and instead saw an expression.");
		th.addError(9, 13, "Missing semicolon.");
		th.addError(11, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(11, 2, "Missing semicolon.");
		th.addError(13, 2, "Missing semicolon.");
		th.addError(14, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(14, 2, "Missing semicolon.");
		th.addError(16, 2, "Missing semicolon.");

		th.test(code, new LinterOptions().set("es3", true).set("asi", false));
		th.test(code, new LinterOptions().set("asi", false)); // es5
		th.test(code, new LinterOptions().set("esnext", true).set("asi", false));
		th.test(code, new LinterOptions().set("moz", true).set("asi", false));
	}

	// gh-3037 - weird behaviour (yield related)
	// https://github.com/jshint/jshint/issues/3037
	@Test(groups = { "ASI" })
	public void testASIFollowingYield() {
		String[] code = {
				"function* g() {",
				"  void 0",
				"  yield;",
				"}"
		};

		th.addError(2, 9, "Missing semicolon.");
		th.test(code, new LinterOptions().set("esversion", 6));

		th.newTest();
		th.test(code, new LinterOptions().set("esversion", 6).set("asi", true));
	}

	@Test(groups = { "ASI" })
	public void testASIFollowingPostfix() {
		String[] code = {
				"x++",
				"void 0;",
				"x--",
				"void 0;"
		};

		th.addError(1, 4, "Missing semicolon.");
		th.addError(3, 4, "Missing semicolon.");
		th.test(code);

		th.newTest();
		th.test(code, new LinterOptions().set("asi", true));
	}

	@Test(groups = { "ASI" })
	public void testASIFollowingContinue() {
		String[] code = {
				"while (false) {",
				"  continue",
				"}"
		};

		th.addError(2, 11, "Missing semicolon.");
		th.test(code);

		th.newTest();
		th.test(code, new LinterOptions().set("asi", true));
	}

	@Test(groups = { "ASI" })
	public void testASIFollowingBreak() {
		String[] code = {
				"while (false) {",
				"  break",
				"}"
		};

		th.addError(2, 8, "Missing semicolon.");
		th.test(code);

		th.newTest();
		th.test(code, new LinterOptions().set("asi", true));
	}

	@Test(groups = { "ASI" })
	public void testASICStyleFor() {
		th.newTest("following first expression");
		th.test(new String[] {
				"for (false",
				";;){}"
		});

		th.newTest("following second expression");
		th.test(new String[] {
				"for (false;",
				";){}"
		});
	}

	@Test
	public void testFatArrowsSupport() {
		String[] code = {
				"let empty = () => {};",
				"let identity = x => x;",
				"let square = x => x * x;",
				"let key_maker = val => ({key: val});",
				"let odds = evens.map(v => v + 1);",
				"let fives = []; nats.forEach(v => { if (v % 5 === 0) fives.push(v); });",

				"let block = (x,y, { z: t }) => {",
				"  print(x,y,z);",
				"  print(j, t);",
				"};",

				// using lexical this
				"const obj = {",
				"  method: function () {",
				"    return () => this;",
				"  }",
				"};",

				"let retnObj = () => ({});",
				"let assgnRetnObj = (() => ({}))();",
				"let retnObjLong = () => { return {}; };",
				"let assgnRetnObjLong = (() => { return {}; })();",

				"let objFns = {",
				"  retnObj: () => ({}),",
				"  assgnRetnObj: (() => ({}))(),",
				"  retnObjLong: () => { return {}; },",
				"  assgnRetnObjLong: (() => { return {}; })()",
				"};",

				// GH-2351
				"let immediatelyInvoked = (x => {})(0);"
		};

		th.addError(5, 12, "'evens' is not defined.");
		th.addError(6, 17, "'nats' is not defined.");
		th.addError(8, 3, "'print' is not defined.");
		th.addError(9, 3, "'print' is not defined.");
		th.addError(9, 9, "'j' is not defined.");
		th.addError(8, 13, "'z' is not defined.");

		th.test(code, new LinterOptions().set("undef", true).set("esnext", true));
		th.test(code, new LinterOptions().set("undef", true).set("esversion", 2016));
		th.test(code, new LinterOptions().set("undef", true).set("esversion", 2017));

		th.newTest();
		th.addError(1, 14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(3, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 21, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 12, "'evens' is not defined.");
		th.addError(6, 32, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(6, 17, "'nats' is not defined.");
		th.addError(7, 27, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(8, 3, "'print' is not defined.");
		th.addError(8, 13, "'z' is not defined.");
		th.addError(9, 3, "'print' is not defined.");
		th.addError(9, 9, "'j' is not defined.");
		th.addError(13, 13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(16, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(17, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(18, 20, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(19, 26, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(21, 13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(22, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(23, 17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(24, 23, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(26, 29, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");

		th.test(code, new LinterOptions().set("undef", true).set("moz", true));

		th.newTest();
		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(3, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 21, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(6, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 32, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 17,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 27, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(13, 13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(16, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(17, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(17, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(18, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(18, 20, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(19, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(19, 26, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(20, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(21, 13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(22, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(23, 17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(24, 23, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(26, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(26, 29, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");

		th.test(code); // es5
		th.test(code, new LinterOptions().set("es3", true));
	}

	@Test
	public void testFatArrowNestedFunctionScoping() {
		String[] code = {
				"(() => {",
				"  for (var i = 0; i < 10; i++) {",
				"    var x;",
				"  }",
				"  var arrow = (x) => {",
				"    return x;",
				"  };",
				"  var arrow2 = (x) => x;",
				"  arrow();",
				"  arrow2();",
				"})();",
				"(function() {",
				"  for (var i = 0; i < 10; i++) {",
				"    var x;",
				"  }",
				"  var arrow = (x) => {",
				"    return x;",
				"  };",
				"  var arrow2 = (x) => x;",
				"  arrow();",
				"  arrow2();",
				"})();"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testDefaultArgumentsInFatArrowNestedFunctions() {
		th.test("(x = 0) => { return x; };",
				new LinterOptions().set("expr", true).set("unused", true).set("esnext", true));
	}

	@Test
	public void testExressionsInPlaceOfArrowFunctionParameters() {
		th.addError(1, 2, "Expected an identifier and instead saw '1'.");
		th.test("(1) => {};", new LinterOptions().set("expr", true).set("esnext", true));
	}

	@Test
	public void testArrowFunctionParameterContainingSemicolonGH3002() {
		th.addError(1, 19, "Unnecessary semicolon.");
		th.addError(1, 27, "Expected an assignment or function call and instead saw an expression.");
		th.test("(x = function() { ; }) => 0;", new LinterOptions().set("esversion", 6));
	}

	@Test(groups = { "conciseMethods" })
	public void testConciseMethodsBasicSupport() {
		String[] code = {
				"var foobar = {",
				"  foo () {",
				"    return 'foo';",
				"  },",
				"  *bar () {",
				"    yield 'bar';",
				"  }",
				"};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));

		th.addError(2, 3,
				"'concise methods' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 3,
				"'generator functions' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 4,
				"'concise methods' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 5, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");

		th.test(code); // es5
		th.test(code, new LinterOptions().set("es3", true));
	}

	@Test(groups = { "conciseMethods" })
	public void testConciseMethodsGetAndSet() {
		String[] code = {
				"var a = [1, 2, 3, 4, 5];",
				"var strange = {",
				"  get (i) {",
				"    return a[i];",
				"  },",
				"  set () {",
				"    a.forEach(function(v, i, l) { l[i] = v++; });",
				"  }",
				"};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test(groups = { "conciseMethods" })
	public void testConciseMethodsGetWithoutSet() {
		String[] code = {
				"var a = [1, 2, 3, 4, 5];",
				"var strange = {",
				"  get () {",
				"    return a;",
				"  }",
				"};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test(groups = { "conciseMethods" })
	public void testConciseMethodsSetWithoutGet() {
		String[] code = {
				"var a = [1, 2, 3, 4, 5];",
				"var strange = {",
				"  set (v) {",
				"    a = v;",
				"  }",
				"};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	// GH-2022: "Concise method names are colliding with params/variables"
	@Test(groups = { "conciseMethods" })
	public void testConciseMethodsNameIsNotLocalVar() {
		String[] code = {
				"var obj = {",
				"  foo(foo) {},",
				"  bar() { var bar; }",
				"};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test(groups = { "conciseMethods" })
	public void testConciseMethodsUniqueFormalParameters() {
		th.newTest("adjacent");
		th.addError(1, 15, "'a' has already been declared.");
		th.test("void { method(a, a) {} };", new LinterOptions().set("esversion", 6));

		th.newTest("separated");
		th.addError(1, 15, "'b' has already been declared.");
		th.test("void { method(b, c, b) {} };", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testObjectShortNotationBasic() {
		String[] code = {
				"var foo = 42;",
				"var bar = {foo};",
				"var baz = {foo, bar};",
				"var biz = {",
				"  foo,",
				"  bar",
				"};"
		};

		th.newTest("1");
		th.test(code, new LinterOptions().set("esnext", true));

		th.newTest("2");
		th.addError(2, 12,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 12,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 17,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 3,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 3,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code);
	}

	@Test
	public void testObjectShortNotationMixed() {
		String[] code = {
				"var b = 1, c = 2;",
				"var o1 = {a: 1, b, c};",
				"var o2 = {b, a: 1, c};"
		};

		th.test(code, new LinterOptions().set("esnext", true));

		th.addError(2, 17,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 20,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 11,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 20,
				"'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code);
	}

	@Test
	public void testObjectComputedPropertyName() {
		String[] code = {
				"function fn(obj) {}",
				"function p() { return 'key'; }",
				"var vals = [1];",
				"var a = 7;",
				"var o1 = {",
				"[a++]: true,",
				"obj: { [a++ + 1]: true },",
				"[a + 3]() {},",
				"[p()]: true,",
				"[vals[0]]: true,",
				"[(1)]: true,",
				"};",
				"fn({ [a / 7]: true });",
				"var b = { '[': 1 };",
				"var c = { [b]: 1 };",
				"var d = { 0: 1 };",
				"var e = { ['s']: 1 };",
				"var f = { get [0]() {} };",
				"var g = { set [0](_) {} };"
		};

		th.addError(19, 17, "Setter is defined without getter.");
		th.test(code, new LinterOptions().set("esnext", true));

		th.newTest("regression test for gh-3381");
		th.test(new String[] {
				"void {",
				"  set() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(6, 1, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 8, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(8, 7,
				"'concise methods' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 1, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(9, 1, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 1, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 1, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(13, 6, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(15, 11, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(17, 11, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(18, 15, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(19, 15, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(19, 17, "Setter is defined without getter.");
		th.test(code);

		th.newTest("YieldExpression");
		th.test(new String[] {
				"(function * () {",
				"  void {",
				"    [yield]: 0,",
				"    [yield 0]: 0,",
				"    [yield * 0]: 0",
				"  };",
				"}());"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testSpreadRestOperatorSupport() {
		String[] code = {
				// 1
				// Spread Identifier
				"foo(...args);",

				// 2
				// Spread Array Literal
				"foo(...[]);",

				// 3, 4
				// Spread String Literal
				"foo(...'');",
				"foo(...\"\");",

				// 5
				// Spread Group
				"foo(...([]));",

				// 6, 7, 8
				// Spread Operator
				"let initial = [ 1, 2, 3, 4, 5 ];",
				"let extended = [ ...initial, 6, 7, 8, 9 ];",
				"let nest = [ ...[], 6, 7, 8, 9 ];",

				// 9
				// Rest Operator
				"function foo(...args) {}",

				// 10
				// Rest Operator (Fat Arrow Params)
				"let bar = (...args) => args;",

				// 11
				"foo(...[].entries());",

				// 12
				"foo(...(new Map()).set('a', 1).values());"
		};

		th.test(code, new LinterOptions().set("esnext", true));

		th.addError(1, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(3, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 18, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(8, 14, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(9, 14, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 12, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(12, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("moz", true));

		th.newTest();
		th.addError(1, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(3, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(6, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");

		th.addError(7, 18, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(8, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 14, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(9, 14, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(10, 12, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(12, 5, "'spread operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code);
	}

	@Test
	public void testParameterDestructuringWithRest() {
		String[] code = {
				// 1
				// parameter destructuring with rest operator, solo
				"let b = ([...args]) => args;",

				// 2
				// ...in function expression
				"let c = function([...args]) { return args; };",

				// 3
				// ...in function declaration
				"function d([...args]) { return args; }",

				// 4
				// default destructuring with rest operator, with leading param
				"let e = ([first, ...args]) => args;",

				// 5
				// ...in function expression
				"let f = function([first, ...args]) { return args; };",

				// 6
				// ...in function declaration
				"function g([first, ...args]) { return args; }",

				// 7
				// Just rest
				"let h = (...args) => args;",

				// 8
				// ...in function expression
				"let i = function(...args) { return args; };",

				// 9
				// ...in function declaration
				"function j(...args) { return args; }"
		};

		th.test(code, new LinterOptions().set("esnext", true));

		th.addError(1, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 26, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 10, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(1, 11, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 19, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(3, 13, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 18, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 26, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(6, 20, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(8, 18, "'rest operator' is only available in ES6 (use 'esversion: 6').");
		th.addError(9, 12, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.test(code, new LinterOptions().set("moz", true));

		th.newTest();
		th.addError(1, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(1, 9,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 11, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(2, 17,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, 19, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(3, 11,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 13, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(4, 26, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 9,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 18, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(5, 17,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 26, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(6, 11,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 20, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(7, 17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 10, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(8, 1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 18, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.addError(9, 12, "'rest operator' is only available in ES6 (use 'esversion: 6').");

		th.test(code);
	}

	@Test
	public void testGH1010() {
		String[] code = {
				"var x = 20, y, z; if(x < 30) y=7, z=2; else y=5;"
		};

		th.test(code, new LinterOptions().set("expr", true).set("es3", true));
		th.test(code, new LinterOptions().set("expr", true)); // es5
		th.test(code, new LinterOptions().set("expr", true).set("esnext", true));
		th.test(code, new LinterOptions().set("expr", true).set("moz", true));
	}

	@Test
	public void testClasses() {
		String cdecl = "// cdecl";
		String cexpr = "// cexpr";
		String cdeclAssn = "// cdeclAssn";
		String cexprAssn = "// cexprAssn";

		String[] code = {
				"var Bar;",

				// class declarations
				cdecl,
				"class Foo0 {}",
				"class Foo1 extends Bar {}",
				"class protected {",
				"  constructor(package) {}",
				"}",
				"class Foo3 extends interface {",
				"  constructor() {}",
				"}",
				"class Foo4 extends Bar {",
				"  constructor() {",
				"    super();",
				"  }",
				"}",
				"class Foo5 {",
				"  constructor() {",
				"  }",
				"  static create() {",
				"  }",
				"}",
				"class Foo6 extends Bar {",
				"  constructor() {",
				"    super();",
				"  }",
				"  static create() {",
				"  }",
				"}",

				// class expressions
				cexpr,
				"var Foo7 = class {};",
				"let Foo8 = class extends Bar {};",
				"var static = class protected {",
				"  constructor(package) {}",
				"};",
				"var Foo10 = class extends interface {",
				"  constructor() {}",
				"};",
				"var Foo11 = class extends Bar {",
				"  constructor() {",
				"    super();",
				"  }",
				"};",
				"var Foo12 = class {",
				"  constructor() {",
				"  }",
				"  static create() {",
				"  }",
				"};",
				"let Foo13 = class extends Bar {",
				"  constructor() {",
				"    super();",
				"  }",
				"  static create() {",
				"  }",
				"};",

				// mark these as used
				"void (Foo1, Foo3, Foo4, Foo5, Foo6);",
				"void (Foo8, Foo10, Foo11, Foo12, Foo13);",

				// class declarations: extends AssignmentExpression
				cdeclAssn,
				"class Foo14 extends Bar[42] {}",
				"class Foo15 extends { a: function() { return 42; } } {}",
				"class Foo16 extends class Foo15 extends Bar {} {}",
				"class Foo17 extends Foo15 = class Foo16 extends Bar {} {}",
				"class Foo18 extends function () {} {}",
				"class Foo19 extends class extends function () {} {} {}",
				"class Foo20 extends Foo18 = class extends Foo17 = function () {} {} {}",

				// class expressions: extends AssignmentExpression
				cexprAssn,
				"let Foo21 = class extends Bar[42] {};",
				"let Foo22 = class extends { a() { return 42; } } {};",
				"let Foo23 = class extends class Foo15 extends Bar {} {};",
				"let Foo24 = class extends Foo15 = class Foo16 extends Bar {} {};",
				"let Foo25 = class extends function () {} {};",
				"let Foo26 = class extends class extends function () {} {} {};",
				"let Foo27 = class extends Foo18 = class extends Foo17 = function () {} {} {};",

				// mark these as used
				"void (Foo14, Foo15, Foo16, Foo17, Foo18, Foo19, Foo20);",
				"void (Foo21, Foo22, Foo23, Foo24, Foo25, Foo26, Foo27);"
		};

		int icdecl = ArrayUtils.indexOf(code, cdecl) + 1;
		int icexpr = ArrayUtils.indexOf(code, cexpr) + 1;
		int icdeclAssn = ArrayUtils.indexOf(code, cdeclAssn) + 1;
		int icexprAssn = ArrayUtils.indexOf(code, cexprAssn) + 1;

		th.addError(icdecl + 3, 7, "Expected an identifier and instead saw 'protected' (a reserved word).");
		th.addError(icexpr + 3, 20, "Expected an identifier and instead saw 'protected' (a reserved word).");
		th.addError(icdecl + 4, 15, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(icexpr + 4, 15, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(icdecl + 6, 20, "Expected an identifier and instead saw 'interface' (a reserved word).");
		th.addError(icexpr + 6, 27, "Expected an identifier and instead saw 'interface' (a reserved word).");
		th.addError(icdeclAssn + 4, 21,
				"Reassignment of 'Foo15', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(icdeclAssn + 7, 21,
				"Reassignment of 'Foo18', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(icdeclAssn + 7, 43,
				"Reassignment of 'Foo17', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(icexprAssn + 4, 27,
				"Reassignment of 'Foo15', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(icexprAssn + 7, 27,
				"Reassignment of 'Foo18', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(icexprAssn + 7, 49,
				"Reassignment of 'Foo17', which is a class. Use 'var' or 'let' to declare bindings that may change.");

		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));

		th.addError(icdecl + 1, 7, "'Foo0' is defined but never used.");
		th.addError(icdecl + 3, 7, "'protected' is defined but never used.");
		th.addError(icdecl + 4, 15, "'package' is defined but never used.");

		th.addError(icexpr + 1, 5, "'Foo7' is defined but never used.");
		th.addError(icexpr + 3, 5, "Expected an identifier and instead saw 'static' (a reserved word).");
		th.addError(icexpr + 3, 5, "'static' is defined but never used.");
		th.addError(icexpr + 4, 15, "'package' is defined but never used.");

		code[0] = "'use strict';" + code[0];
		th.test(code, new LinterOptions().set("unused", true).set("globalstrict", true).set("esnext", true));
		th.test(code, new LinterOptions().set("unused", true).set("globalstrict", true).set("moz", true));
	}

	@Test
	public void testClassAndMethodNaming() {
		String[] code = {
				"class eval {}",
				"class arguments {}",
				"class C {",
				"  get constructor() {}",
				"  set constructor(x) {}",
				"  prototype() {}",
				"  an extra identifier 'in' methodName() {}",
				"  get foo extraIdent1() {}",
				"  set foo extraIdent2() {}",
				"  static some extraIdent3() {}",
				"  static get an extraIdent4() {}",
				"  static set an extraIdent5() {}",
				"  get dupgetter() {}",
				"  get dupgetter() {}",
				"  set dupsetter() {}",
				"  set dupsetter() {}",
				"  static get dupgetter() {}",
				"  static get dupgetter() {}",
				"  static set dupsetter() {}",
				"  static set dupsetter() {}",
				"  dupmethod() {}",
				"  dupmethod() {}",
				"  static dupmethod() {}",
				"  static dupmethod() {}",
				"  ['computed method']() {}",
				"  static ['computed static']() {}",
				"  get ['computed getter']() {}",
				"  set ['computed setter']() {}",
				"  (typo() {}",
				"  set lonely() {}",
				"  set lonel2",
				"            () {}",
				"  *validGenerator() { yield; }",
				"  static *validStaticGenerator() { yield; }",
				"  *[1]() { yield; }",
				"  static *[1]() { yield; }",
				"  * ['*']() { yield; }",
				"  static *['*']() { yield; }",
				"  * [('*')]() { yield; }",
				"  static *[('*')]() { yield; }",
				"}"
		};

		th.addError(1, 7, "Strict violation.");
		th.addError(2, 7, "Strict violation.");
		th.addError(4, 7, "A class getter method cannot be named 'constructor'.");
		th.addError(5, 7, "A class setter method cannot be named 'constructor'.");
		th.addError(7, 6, "Class properties must be methods. Expected '(' but instead saw 'extra'.");
		th.addError(8, 11, "Class properties must be methods. Expected '(' but instead saw 'extraIdent1'.");
		th.addError(9, 11, "Class properties must be methods. Expected '(' but instead saw 'extraIdent2'.");
		th.addError(10, 15, "Class properties must be methods. Expected '(' but instead saw 'extraIdent3'.");
		th.addError(11, 17, "Class properties must be methods. Expected '(' but instead saw 'extraIdent4'.");
		th.addError(12, 17, "Class properties must be methods. Expected '(' but instead saw 'extraIdent5'.");
		th.addError(14, 16, "Duplicate getter method 'dupgetter'.");
		th.addError(16, 16, "Duplicate setter method 'dupsetter'.");
		th.addError(16, 7, "Setter is defined without getter.");
		th.addError(18, 23, "Duplicate static getter method 'dupgetter'.");
		th.addError(20, 23, "Duplicate static setter method 'dupsetter'.");
		th.addError(22, 12, "Duplicate class method 'dupmethod'.");
		th.addError(24, 19, "Duplicate static class method 'dupmethod'.");
		th.addError(29, 3, "Unexpected '('.");
		th.addError(30, 7, "Setter is defined without getter.");
		th.addError(31, 7, "Setter is defined without getter.");

		th.test(code, new LinterOptions().set("esnext", true));

		th.newTest("valid uses of name `constructor`");
		th.test(new String[] {
				"void class {",
				"  constructor() {}",
				"};",
				"void class {",
				"  static constructor() {}",
				"};",
				"void class {",
				"  static get constructor() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("valid uses of name `prototype`");
		th.test(new String[] {
				"void class {",
				"  get prototype() {}",
				"};",
				"void class {",
				"  get prototype() {}",
				"  set prototype(x) {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("valid uses of name `static`");
		th.test(new String[] {
				"void class {",
				"  static() {}",
				"  static static() {}",
				"  static ['static']() {}",
				"};",
				"void class {",
				"  * static() { yield; }",
				"  static * static() { yield; }",
				"  static * ['static']() { yield; }",
				"};",
				"void class {",
				"  get static() {}",
				"  set static(x) {}",
				"  static get static() {}",
				"  static set static(x) {}",
				"  static get ['static']() {}",
				"  static set ['static'](x) {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("invalid use of name `prototype`: static method");
		th.addError(2, 10, "A static class method cannot be named 'prototype'.");
		th.test(new String[] {
				"void class {",
				"  static prototype() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("invalid use of name `prototype`: static accessor method");
		th.addError(2, 14, "A static class getter method cannot be named 'prototype'.");
		th.test(new String[] {
				"void class {",
				"  static get prototype() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("regression test for gh-3381");
		th.test(new String[] {
				"void class {",
				"  set() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("hazardous method names (see gh-3358)");
		th.addError(3, 17, "'hasOwnProperty' is a really bad name.");
		th.test(new String[] {
				"void class {",
				"  constructor() {}",
				"  hasOwnProperty() {}",
				"  toString() {}",
				"  toLocaleString() {}",
				"  valueOf() {}",
				"  isPrototypeOf() {}",
				"  propertyIsEnumerable() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("hazardous method names -- true duplicate (see gh-3358)");
		th.addError(4, 11, "Duplicate class method 'toString'.");
		th.test(new String[] {
				"void class {",
				"  toString() {}",
				"  x() {}",
				"  toString() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("hazardous static method names (see gh-3358)");
		th.addError(3, 24, "'hasOwnProperty' is a really bad name.");
		th.test(new String[] {
				"void class {",
				"  static constructor() {}",
				"  static hasOwnProperty() {}",
				"  static toString() {}",
				"  static toLocaleString() {}",
				"  static valueOf() {}",
				"  static isPrototypeOf() {}",
				"  static propertyIsEnumerable() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("hazardous static method names -- true duplicate (see gh-3358)");
		th.addError(4, 18, "Duplicate static class method 'toString'.");
		th.test(new String[] {
				"void class {",
				"  static toString() {}",
				"  static x() {}",
				"  static toString() {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("hazardous accessor method names (see gh-3358)");
		th.addError(2, 21, "'hasOwnProperty' is a really bad name.");
		th.addError(3, 21, "'hasOwnProperty' is a really bad name.");
		th.test(new String[] {
				"void class {",
				"  get hasOwnProperty() {}",
				"  set hasOwnProperty(_) {}",
				"  get toString() {}",
				"  set toString(_) {}",
				"  get toLocaleString() {}",
				"  set toLocaleString(_) {}",
				"  get valueOf() {}",
				"  set valueOf(_) {}",
				"  get isPrototypeOf() {}",
				"  set isPrototypeOf(_) {}",
				"  get propertyIsEnumerable() {}",
				"  set propertyIsEnumerable(_) {}",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("hazardous accessor method names -- true duplicate (see gh-3358)");
		th.addError(5, 15, "Duplicate getter method 'toString'.");
		th.addError(6, 15, "Duplicate setter method 'toString'.");
		th.test(new String[] {
				"void class {",
				"  get toString() {}",
				"  set toString(_) {}",
				"  static x() {}",
				"  get toString() {}",
				"  set toString(_) {}",
				"};"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testComputedClassMethodsArentDuplicate() {
		String[] code = {
				"const obj = {};",
				"class A {",
				"  [Symbol()]() {}",
				"  [Symbol()]() {}",
				"  [obj.property]() {}",
				"  [obj.property]() {}",
				"  [obj[0]]() {}",
				"  [obj[0]]() {}",
				"  [`template`]() {}",
				"  [`template2`]() {}",
				"}"
		};

		// JSHint shouldn't throw a "Duplicate class method" warning with computed
		// method names
		// GH-2350
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testClassMethodUniqueFormalParameters() {
		th.newTest("adjacent");
		th.addError(1, 18, "'a' has already been declared.");
		th.test("class C { method(a, a) {} }", new LinterOptions().set("esversion", 6));

		th.newTest("separated");
		th.addError(1, 18, "'b' has already been declared.");
		th.test("class C { method(b, c, b) {} }", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testClassMethodThis() {
		String[] code = {
				"class C {",
				"  constructor(x) {",
				"    this._x = x;",
				"  }",
				"  x() { return this._x; }",
				"  static makeC(x) { return new this(x); }",
				"  0() { return this._x + 0; }",
				"  ['foo']() { return this._x + 6; }",
				"  'test'() { return this._x + 'test'; }",
				"  bar() { function notCtor() { return this; } notCtor(); }",
				"}"
		};

		th.addError(10, 39,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testClassNewCap() {
		String[] code = {
				"class C {",
				"  m() {",
				"    var ctor = function() {};",
				"    var Ctor = function() {};",
				"    var c1 = new ctor();",
				"    var c2 = Ctor();",
				"  }",
				"}"
		};

		th.newTest("The `newcap` option is not automatically enabled within class bodies.");
		th.test(code, new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testClassExpression() {
		String[] code = {
				"void class MyClass {",
				"  constructor() { MyClass = null; }",
				"  method() { MyClass = null; }",
				"  static method() { MyClass = null; }",
				"  get accessor() { MyClass = null; }",
				"  set accessor() { MyClass = null; }",
				"  method2() { MyClass &= null; }",
				"};",
				"void MyClass;"
		};

		th.addError(2, 19,
				"Reassignment of 'MyClass', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(3, 14,
				"Reassignment of 'MyClass', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(4, 21,
				"Reassignment of 'MyClass', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(5, 20,
				"Reassignment of 'MyClass', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(6, 20,
				"Reassignment of 'MyClass', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(7, 15,
				"Reassignment of 'MyClass', which is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(9, 6, "'MyClass' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true));
	}

	@Test(groups = { "super" })
	public void testSuperInvalid() {
		th.newTest("as identifier");
		th.addError(3, 10, "Unexpected ';'.");
		th.addError(3, 5, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"void {",
				"  m() {",
				"    super;",
				"  }",
				"};"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test(groups = { "super" })
	public void testSuperSuperProperty() {
		th.newTest("bracket notation");
		th.addError(3, 14, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 20, "['ab'] is better written in dot notation.");
		th.test(new String[] {
				"void {",
				"  m() {",
				"    super['4'];",
				"    void super['a' + 'b'];",
				"  }",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("dot operator");
		th.addError(3, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 26, "'hasOwnProperty' is a really bad name.");
		th.test(new String[] {
				"void {",
				"  m() {",
				"    super.x;",
				"    super.hasOwnProperty = 0;",
				"  }",
				"};"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("within arrow functions");
		th.test(new String[] {
				"void {",
				"  m() {",
				"    void (() => {",
				"      void (() => {",
				"        void super.x;",
				"      });",
				"    });",
				"  },",
				"  *g() {",
				"    void (() => {",
				"      void (() => {",
				"        void super.x;",
				"      });",
				"    });",
				"    yield;",
				"  }",
				"};",
				"class C {",
				"  m() {",
				"    void (() => {",
				"      void (() => {",
				"        void super.x;",
				"      });",
				"    });",
				"  }",
				"  static m() {",
				"    void (() => {",
				"      void (() => {",
				"        void super.x;",
				"      });",
				"    });",
				"  }",
				"}"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("outside of method");
		th.addError(5, 14, "Super property may only be used within method bodies.");
		th.addError(6, 14, "Super property may only be used within method bodies.");
		th.addError(12, 8, "Super property may only be used within method bodies.");
		th.addError(13, 8, "Super property may only be used within method bodies.");
		th.addError(15, 6, "Super property may only be used within method bodies.");
		th.addError(16, 6, "Super property may only be used within method bodies.");
		th.test(new String[] {
				"void {",
				"  m() {",
				"    function f() {",
				"      void (() => {",
				"        void super.x;",
				"        void super[x];",
				"      });",
				"    }",
				"  }",
				"};",
				"function f() {",
				"  void super.x;",
				"  void super[x];",
				"}",
				"void super.x;",
				"void super[x];"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test(groups = { "super" })
	public void testSuperSuperCall() {
		th.test(new String[] {
				"class C {",
				"  m() {",
				"    super();",
				"    super(1);",
				"    super(...x);",
				"  }",
				"}"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("within arrow functions");
		th.test(new String[] {
				"class C {",
				"  m() {",
				"    void (() => {",
				"      void (() => {",
				"        super();",
				"      });",
				"    });",
				"  }",
				"  *g() {",
				"    void (() => {",
				"      void (() => {",
				"        super();",
				"      });",
				"    });",
				"    yield;",
				"  }",
				"  static m() {",
				"    void (() => {",
				"      void (() => {",
				"        super();",
				"      });",
				"    });",
				"  }",
				"}"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("outside of class method");
		th.addError(5, 9, "Super call may only be used within class method bodies.");
		th.addError(14, 9, "Super call may only be used within class method bodies.");
		th.addError(20, 3, "Super call may only be used within class method bodies.");
		th.addError(22, 1, "Super call may only be used within class method bodies.");
		th.test(new String[] {
				"class C {",
				"  m() {",
				"    function f() {",
				"      void (() => {",
				"        super();",
				"      });",
				"    }",
				"  }",
				"}",
				"void {",
				"  m() {",
				"    function f() {",
				"      void (() => {",
				"        super();",
				"      });",
				"    }",
				"  }",
				"};",
				"function f() {",
				"  super();",
				"}",
				"super();"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("within async method");
		th.addError(3, 5, "Super call may only be used within class method bodies.");
		th.test(new String[] {
				"class C {",
				"  async m() {",
				"    super();",
				"  }",
				"}"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("as operand to `new`");
		th.addError(3, 9, "Unexpected 'super'.");
		th.test(new String[] {
				"class C {",
				"  constructor() {",
				"    new super();",
				"  }",
				"}"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testFunctionReassignment() {
		String[] src = {
				"function f() {}",
				"f = null;",
				"f ^= null;",
				"function g() {",
				"  g = null;",
				"  g &= null;",
				"}",
				"(function h() {",
				"  h = null;",
				"  h |= null;",
				"}());"
		};

		th.addError(2, 1,
				"Reassignment of 'f', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(3, 1,
				"Reassignment of 'f', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(5, 3,
				"Reassignment of 'g', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(6, 3,
				"Reassignment of 'g', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(9, 3,
				"Reassignment of 'h', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(10, 3,
				"Reassignment of 'h', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.test(src);

		th.newTest("generator functions");
		th.addError(2, 1,
				"Reassignment of 'g', which is a generator function. Use 'var' or 'let' to declare bindings that may change.");
		th.test(new String[] {
				"function * g () { yield; }",
				"g = null;"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("async functions");
		th.addError(2, 1,
				"Reassignment of 'a', which is a async function. Use 'var' or 'let' to declare bindings that may change.");
		th.test(new String[] {
				"async function a () {}",
				"a = null;"
		}, new LinterOptions().set("esversion", 8));
	}

	@Test
	public void testFunctionNotOverwritten() {
		String[] code = {
				"function x() {",
				"  x = 1;",
				"  var x;",
				"}"
		};

		th.test(code, new LinterOptions().set("shadow", true));
	}

	@Test
	public void testClassExpressionThis() {
		String[] code = {
				"void class MyClass {",
				"  constructor() { return this; }",
				"  method() { return this; }",
				"  static method() { return this; }",
				"  get accessor() { return this; }",
				"  set accessor() { return this; }",
				"};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testClassElementEmpty() {
		String[] code = {
				"class A {",
				"  ;",
				"  method() {}",
				"  ;",
				"  *methodB() { yield; }",
				"  ;;",
				"  methodC() {}",
				"  ;",
				"}"
		};

		th.addError(2, 3, "Unnecessary semicolon.");
		th.addError(4, 3, "Unnecessary semicolon.");
		th.addError(6, 3, "Unnecessary semicolon.");
		th.addError(6, 4, "Unnecessary semicolon.");
		th.addError(8, 3, "Unnecessary semicolon.");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testInvalidClasses() {
		// Regression test for GH-2324
		th.addError(1, 11, "Class properties must be methods. Expected '(' but instead saw ''.");
		th.addError(1, 11, "Unrecoverable syntax error. (100% scanned).");
		th.test("class a { b", new LinterOptions().set("esnext", true));

		// Regression test for GH-2339
		th.newTest();
		th.addError(2, 14, "Class properties must be methods. Expected '(' but instead saw ':'.");
		th.addError(3, 3, "Expected '(' and instead saw '}'.");
		th.addError(4, 1, "Expected an identifier and instead saw '}'.");
		th.addError(4, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[] {
				"class Test {",
				"  constructor: {",
				"  }",
				"}"
		}, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testGH1018() {
		String[] code = {
				"if (a = 42) {}",
				"else if (a = 42) {}",
				"while (a = 42) {}",
				"for (a = 42; a = 42; a += 42) {}",
				"do {} while (a = 42);",
				"switch (a = 42) {}"
		};

		th.test(code, new LinterOptions().set("boss", true));

		th.addError(1, 7, "Expected a conditional expression and instead saw an assignment.");
		th.addError(2, 12, "Expected a conditional expression and instead saw an assignment.");
		th.addError(3, 10, "Expected a conditional expression and instead saw an assignment.");
		th.addError(4, 16, "Expected a conditional expression and instead saw an assignment.");
		th.addError(5, 16, "Expected a conditional expression and instead saw an assignment.");
		th.addError(6, 11, "Expected a conditional expression and instead saw an assignment.");
		th.test(code);
	}

	@Test
	public void testWarningsForAssignmentInConditionals() {
		String[] code = {
				"if (a = b) { }",
				"if ((a = b)) { }",
				"if (a = b, a) { }",
				"if (a = b, b = c) { }",
				"if ((a = b, b = c)) { }",
				"if (a = b, (b = c)) { }"
		};

		th.addError(1, 7, "Expected a conditional expression and instead saw an assignment.");
		th.addError(4, 14, "Expected a conditional expression and instead saw an assignment.");

		th.test(code); // es5
	}

	@Test
	public void testGH1089() {
		String[] code = {
				"function foo() {",
				"    'use strict';",
				"    Object.defineProperty(foo, 'property', {",
				"        get: function() foo,",
				"        set: function(value) {},",
				"        enumerable: true",
				"    });",
				"}",
				"foo;"
		};

		th.addError(9, 1, "Expected an assignment or function call and instead saw an expression.");

		th.test(code, new LinterOptions().set("moz", true));

		th.addError(4, 23,
				"'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code);
	}

	@Test
	public void testGH1105() {
		String[] code = {
				"while (true) {",
				"    if (true) { break }",
				"}"
		};

		th.addError(2, 22, "Missing semicolon.");
		th.test(code);

		code = new String[] {
				"while (true) {",
				"    if (true) { continue }",
				"}"
		};

		th.newTest();
		th.addError(2, 25, "Missing semicolon.");
		th.test(code);
	}

	@Test
	public void testForCrashWithInvalidCondition() {
		String[] code = {
				"do {} while ();",
				"do {} while (,);",
				"do {} while (a,);",
				"do {} while (,b);",
				"do {} while (());",
				"do {} while ((,));",
				"do {} while ((a,));",
				"do {} while ((,b));"
		};

		// As long as jshint doesn't crash, it doesn't matter what these errors
		// are. Feel free to adjust these if they don't match the output.
		th.addError(1, 14, "Expected an identifier and instead saw ')'.");
		th.addError(1, 15, "Expected ')' to match '(' from line 1 and instead saw ';'.");
		th.addError(2, 14, "Expected an identifier and instead saw ','.");
		th.addError(3, 16, "Unexpected ')'.");
		th.addError(4, 14, "Expected an identifier and instead saw ','.");
		th.addError(4, 15, "Expected ')' to match '(' from line 4 and instead saw 'b'.");
		th.addError(4, 16, "Expected an identifier and instead saw ')'.");
		th.addError(4, 16, "Missing semicolon.");
		th.addError(6, 15, "Expected an identifier and instead saw ','.");
		th.addError(7, 17, "Unexpected ')'.");
		th.addError(8, 15, "Expected an identifier and instead saw ','.");
		th.addError(8, 16, "Expected ')' to match '(' from line 8 and instead saw 'b'.");
		th.addError(8, 18, "Expected an identifier and instead saw ')'.");
		th.addError(8, 18, "Missing semicolon.");

		th.test(code, new LinterOptions().set("asi", true).set("expr", true));
	}

	@Test
	public void testYieldInCompoundExpressions() {
		String code = th.readFile("src/test/resources/fixtures/yield-expressions.js");

		th.addError(22, 14, "Did you mean to return a conditional instead of an assignment?");
		th.addError(23, 22, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(31, 14, "Did you mean to return a conditional instead of an assignment?");
		th.addError(32, 20, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(32, 17, "Bad operand.");
		th.addError(51, 10, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(53, 10, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(54, 16, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(57, 10, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(58, 11, "Bad operand.");
		th.addError(59, 10, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(59, 16, "Bad operand.");
		th.addError(60, 11, "Bad operand.");
		th.addError(60, 14, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(64, 6, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(65, 7, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(66, 6, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(67, 7, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(70, 6, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(71, 7, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(77, 11, "Bad operand.");
		th.addError(78, 11, "Bad operand.");
		th.addError(78, 19, "Bad operand.");
		th.addError(79, 11, "Bad operand.");
		th.addError(79, 19, "Bad operand.");
		th.addError(79, 47, "Bad operand.");
		th.addError(82, 11, "Bad operand.");
		th.addError(83, 11, "Bad operand.");
		th.addError(83, 19, "Bad operand.");
		th.addError(84, 11, "Bad operand.");
		th.addError(84, 19, "Bad operand.");
		th.addError(84, 43, "Bad operand.");
		th.test(code, new LinterOptions().set("maxerr", 1000).set("expr", true).set("esnext", true));

		th.newTest();
		th.addError(22, 14, "Did you mean to return a conditional instead of an assignment?");
		th.addError(31, 14, "Did you mean to return a conditional instead of an assignment?");

		// These are line-column pairs for the Mozilla paren errors.
		int[][] needparen = {
				// comma
				{ 5, 5 }, { 6, 8 }, { 7, 5 }, { 11, 5 }, { 12, 8 }, { 13, 5 },
				// yield in yield
				{ 20, 11 }, { 20, 5 }, { 21, 11 }, { 21, 5 },
				{ 23, 22 }, { 29, 11 }, { 29, 5 }, { 30, 11 }, { 30, 5 },
				{ 32, 11 }, { 32, 20 },
				// infix
				{ 51, 10 }, { 53, 10 }, { 54, 16 }, { 57, 10 }, { 58, 5 }, { 59, 10 }, { 60, 5 }, { 60, 14 },
				// prefix
				{ 64, 6 }, { 65, 7 }, { 66, 6 }, { 67, 7 }, { 70, 6 }, { 71, 7 },
				// ternary
				{ 77, 5 }, { 78, 5 }, { 78, 13 }, { 79, 5 }, { 79, 13 }, { 79, 41 }, { 82, 5 }, { 83, 5 }, { 83, 13 },
				{ 84, 5 }, { 84, 13 }, { 84, 37 }
		};

		for (int[] lc : needparen) {
			th.addError(lc[0], lc[1], "Mozilla requires the yield expression to be parenthesized here.");
		}

		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(74, 9, "'function*' is only available in ES6 (use 'esversion: 6').");

		th.test(code, new LinterOptions().set("maxerr", 1000).set("expr", true).set("moz", true));
	}

	@Test
	public void testYieldInInvalidPositions() {
		th.newTest("as an invalid operand");
		th.addError(1, 25, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");

		th.test("function* g() { null || yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null || yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null || yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null && yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null && yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null && yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("as an invalid operand");
		th.addError(1, 18, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");

		th.test("function* g() { !yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("as an invalid operand");
		th.addError(1, 19, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");

		th.test("function* g() { !!yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !!yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !!yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("as an invalid operand");
		th.addError(1, 21, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");

		th.test("function* g() { 1 + yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 + yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 + yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 - yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 - yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 - yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("with an invalid operand");
		th.addError(1, 22, "Bad operand.");
		th.test("function* g() { yield.x; }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("with an invalid operand");
		th.addError(1, 23, "Bad operand.");

		th.test("function* g() { yield*.x; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { yield ? null : null; }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("with an invalid operand");
		th.addError(1, 24, "Bad operand.");

		th.test("function* g() { yield* ? null : null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { (yield ? 1 : 1); }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest();
		th.addError(1, 25, "Bad operand.");
		th.test("function* g() { (yield* ? 1 : 1); }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest();
		th.addError(1, 24, "Unclosed regular expression.");
		th.addError(1, 24, "Unrecoverable syntax error. (100% scanned).");
		th.test("function* g() { yield* / 1; }", new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("as a valid operand");
		th.test(new String[] {
				"function* g() {",
				"  (yield);",
				"  var x = yield;",
				"  x = yield;",
				"  x = (yield, null);",
				"  x = (null, yield);",
				"  x = (null, yield, null);",
				"  x += yield;",
				"  x -= yield;",
				"  x *= yield;",
				"  x /= yield;",
				"  x %= yield;",
				"  x <<= yield;",
				"  x >>= yield;",
				"  x >>>= yield;",
				"  x &= yield;",
				"  x ^= yield;",
				"  x |= yield;",
				"  x = (yield) ? 0 : 0;",
				"  x = yield 0 ? 0 : 0;",
				"  x = 0 ? yield : 0;",
				"  x = 0 ? 0 : yield;",
				"  x = 0 ? yield : yield;",
				"  yield yield;",
				"}"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("with a valid operand");
		th.test(new String[] {
				"function *g() {",
				"  yield g;",
				"  yield{};",
				// Misleading cases; potential future warning.
				"  yield + 1;",
				"  yield - 1;",
				"  yield[0];",
				"}"
		}, new LinterOptions().set("esversion", 6));

		String[] code = {
				"function* g() {",
				"  var x;",
				"  x++",
				"  yield;",
				"  x--",
				"  yield;",
				"}"
		};

		th.newTest("asi");
		th.addError(3, 6, "Missing semicolon.");
		th.addError(5, 6, "Missing semicolon.");
		th.test(code, new LinterOptions().set("esversion", 6).set("expr", true));

		th.newTest("asi (ignoring warnings)");
		th.test(code, new LinterOptions().set("esversion", 6).set("expr", true).set("asi", true));

		th.newTest("name of a generator expression");
		th.addError(1, 13, "Unexpected 'yield'.");
		th.test(new String[] {
				"(function * yield() {",
				"  yield;",
				"})();"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testGH387() {
		String[] code = {
				"var foo = a",
				"delete foo.a;"
		};

		th.addError(1, 12, "Missing semicolon.");

		th.test(code); // es5
	}

	@Test
	public void testForLineBreaksWithYield() {
		String[] code = {
				"function* F() {",
				"    a = b + (yield",
				"    c",
				"    );",
				"    d = yield",
				"    + e;",
				"    f = (yield",
				"    , g);",
				"    h = yield",
				"    ? i : j;",
				"    k = l ? yield",
				"    : m;",
				"    n = o ? p : yield",
				"    + r;",
				"}"
		};

		th.addError(3, 5, "Expected ')' to match '(' from line 2 and instead saw 'c'.");
		th.addError(3, 6, "Missing semicolon.");
		th.addError(4, 5, "Expected an identifier and instead saw ')'.");
		th.addError(4, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(5, 14, "Missing semicolon.");
		th.addError(6, 7, "Expected an assignment or function call and instead saw an expression.");
		th.addError(7, 10, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(8, 5, "Comma warnings can be turned off with 'laxcomma'.");
		th.addError(9, 14, "Missing semicolon.");
		th.addError(10, 5, "Expected an identifier and instead saw '?'.");
		th.addError(10, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(10, 6, "Missing semicolon.");
		th.addError(10, 11, "Label 'i' on j statement.");
		th.addError(10, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(13, 22, "Missing semicolon.");
		th.addError(14, 7, "Expected an assignment or function call and instead saw an expression.");

		th.test(code, new LinterOptions().set("esnext", true));

		// Mozilla assumes the statement has ended if there is a line break
		// following a `yield`. This naturally causes havoc with the subsequent
		// parse.
		//
		// Note: there is one exception to the line-breaking rule:
		// ```js
		// a ? yield
		// : b;
		// ```
		th.newTest();
		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(3, 5, "Expected ')' to match '(' from line 2 and instead saw 'c'.");
		th.addError(4, 5, "Expected an identifier and instead saw ')'.");
		th.addError(4, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 7, "Expected an assignment or function call and instead saw an expression.");
		th.addError(8, 5, "Comma warnings can be turned off with 'laxcomma'.");
		th.addError(7, 10, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(10, 5, "Expected an identifier and instead saw '?'.");
		th.addError(10, 6, "Missing semicolon.");
		th.addError(10, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(10, 11, "Label 'i' on j statement.");
		th.addError(10, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(14, 7, "Expected an assignment or function call and instead saw an expression.");

		th.test(code, new LinterOptions().set("moz", true).set("asi", true));

		th.addError(2, 14, "Line breaking error 'yield'.");
		th.addError(3, 6, "Missing semicolon.");
		th.addError(5, 9, "Line breaking error 'yield'.");
		th.addError(5, 14, "Missing semicolon.");
		th.addError(7, 10, "Line breaking error 'yield'.");
		th.addError(9, 9, "Line breaking error 'yield'.");
		th.addError(9, 14, "Missing semicolon.");
		th.addError(11, 13, "Line breaking error 'yield'.");
		th.addError(13, 17, "Line breaking error 'yield'.");
		th.addError(13, 22, "Missing semicolon.");

		th.test(code, new LinterOptions().set("moz", true));

		String[] code2 = {
				"function* gen() {",
				"  yield",
				"  fn();",
				"  yield*",
				"  fn();",
				"}"
		};

		th.newTest("gh-2530 (asi: true)");
		th.addError(5, 3, "Misleading line break before 'fn'; readers may interpret this as an expression boundary.");
		th.test(code2, new LinterOptions().set("esnext", true).set("undef", false).set("asi", true));

		th.newTest("gh-2530 (asi: false)");
		th.addError(2, 8, "Missing semicolon.");
		th.addError(5, 3, "Misleading line break before 'fn'; readers may interpret this as an expression boundary.");
		th.test(code2, new LinterOptions().set("esnext", true).set("undef", false));
	}

	// Regression test for gh-2956
	@Test
	public void testYieldRegExp() {
		String[] code = {
				"function* g() {",
				"  yield /./;",
				"  yield/./;",
				"  yield",
				"  /./;",
				"  yield /* comment */;",
				"  yield /* comment *//./;",
				"  yield 1 / 1;",
				"}"
		};

		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 8, "Missing semicolon.");
		th.addError(5, 3, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, 3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code);

		th.newTest();
		th.addError(4, 8, "Missing semicolon.");
		th.addError(5, 3, "Expected an assignment or function call and instead saw an expression.");
		th.test(code, new LinterOptions().set("esversion", 6));

		code = new String[] {
				"function* g() {",
				"  yield / 2;",
				"}"
		};

		th.newTest();
		th.addError(1, 9, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(2, 9, "Unclosed regular expression.");
		th.addError(2, 9, "Unrecoverable syntax error. (66% scanned).");
		th.test(code);

		th.newTest();
		th.addError(2, 9, "Unclosed regular expression.");
		th.addError(2, 9, "Unrecoverable syntax error. (66% scanned).");
		th.test(code, new LinterOptions().set("esversion", 6));
	}

	@Test(groups = { "unreachable" })
	public void testUnreachableRegressionForGH1227() {
		String src = th.readFile("src/test/resources/fixtures/gh1227.js");

		th.addError(14, 3, "Unreachable 'return' after 'return'.");
		th.test(src);
	}

	@Test(groups = { "unreachable" })
	public void testUnreachableBreak() {
		String[] src = {
				"var i = 0;",
				"foo: while (i) {",
				"  break foo;",
				"  i--;",
				"}"
		};

		th.addError(4, 3, "Unreachable 'i' after 'break'.");
		th.test(src);
	}

	@Test(groups = { "unreachable" })
	public void testUnreachableContinue() {
		String[] src = {
				"var i = 0;",
				"while (i) {",
				"  continue;",
				"  i--;",
				"}"
		};

		th.addError(4, 3, "Unreachable 'i' after 'continue'.");
		th.test(src);
	}

	@Test(groups = { "unreachable" })
	public void testUnreachableReturn() {
		String[] src = {
				"(function() {",
				"  var x = 0;",
				"  return;",
				"  x++;",
				"}());"
		};

		th.addError(4, 3, "Unreachable 'x' after 'return'.");
		th.test(src);
	}

	@Test(groups = { "unreachable" })
	public void testUnreachableThrow() {
		String[] src = {
				"throw new Error();",
				"var x;"
		};

		th.addError(2, 1, "Unreachable 'var' after 'throw'.");
		th.test(src);
	}

	@Test(groups = { "unreachable" })
	public void testUnreachableBraceless() {
		String[] src = {
				"(function() {",
				"  var x;",
				"  if (x)",
				"    return;",
				"  return;",
				"}());"
		};

		th.test(src);
	}

	// Regression test for GH-1387 "false positive: Unreachable 'x' after 'return'"
	@Test(groups = { "unreachable" })
	public void testUnreachableNestedBraceless() {
		String[] src = {
				"(function() {",
				"  var x;",
				"  if (!x)",
				"    return function() {",
				"      if (!x) x = 0;",
				"      return;",
				"    };",
				"  return;",
				"}());"
		};

		th.test(src);
	}

	@Test
	public void testForBreakInSwitchCaseCurlyBraces() {
		String[] code = {
				"switch (foo) {",
				"  case 1: { break; }",
				"  case 2: { return; }",
				"  case 3: { throw 'Error'; }",
				"  case 11: {",
				"    while (true) {",
				"      break;",
				"    }",
				"  }",
				"  default: break;",
				"}"
		};

		// No error for case 1, 2, 3.
		th.addError(9, 3, "Expected a 'break' statement before 'default'.");
		th.test(code);
	}

	@Test
	public void testForBreakInSwitchCaseInLoopCurlyBraces() {
		String[] code = {
				"while (true) {",
				"  switch (foo) {",
				"    case 1: { break; }",
				"    case 2: { return; }",
				"    case 3: { throw 'Error'; }",
				"    case 4: { continue; }",
				"    case 11: {",
				"      while (true) {",
				"        break;",
				"      }",
				"    }",
				"    default: break;",
				"  }",
				"}"
		};

		// No error for case 1, 2, 3, 4.
		th.addError(11, 5, "Expected a 'break' statement before 'default'.");
		th.test(code);
	}

	@Test
	public void testAllowExpressionWithCommaInSwitchCaseCondition() {
		String[] code = {
				"switch (false) {",
				"  case x = 1, y = x: { break; }",
				"}"
		};

		th.test(code);
	}

	@Test(groups = { "ignoreDirective" })
	public void testIgnoreDirectiveShouldBeGoodOptionAndOnlyAcceptStartEndOrLineAsValues() {
		String[] code = {
				"/*jshint ignore:start*/",
				"/*jshint ignore:end*/",
				"/*jshint ignore:line*/",
				"/*jshint ignore:badvalue*/"
		};

		th.addError(4, 1, "Bad option value.");
		th.test(code);
	}

	@Test(groups = { "ignoreDirective" })
	public void testIgnoreDirectiveShouldAllowLinterToSkipBlockedOutLinesToContinueFindingErrorsInRestOfCode() {
		String code = th.readFile("src/test/resources/fixtures/gh826.js");

		/**
		 * This test previously asserted the issuance of warning W041.
		 * W041 has since been removed, but the test is maintained in
		 * order to discourage regressions.
		 */
		th.test(code);
	}

	@Test(groups = { "ignoreDirective" })
	public void testIgnoreDirectiveShouldIgnoreLinesThatAppearToEndWithMultilineCommentEndingsGH1691() {
		String[] code = {
				"/*jshint ignore: start*/",
				"var a = {",
				// The following line ends in a sequence of characters that, if parsed
				// naively, could be interpreted as an "end multiline comment" token.
				"  a: /\\s*/",
				"};",
				"/*jshint ignore: end*/"
		};

		th.test(code);
	}

	@Test(groups = { "ignoreDirective" })
	public void testIgnoreDirectiveShouldIgnoreLinesThatEndWithMultilineCommentGH1396() {
		String[] code = {
				"/*jshint ignore:start */",
				"var a; /* following comment */",
				"/*jshint ignore:end */"
		};

		th.test(code, new LinterOptions().set("unused", true));
	}

	@Test(groups = { "ignoreDirective" })
	public void testIgnoreDirectiveShouldIgnoreMultilineComments() {
		String[] code = {
				"/*jshint ignore:start */",
				"/*",
				"following comment",
				"*/",
				"var a;",
				"/*jshint ignore:end */"
		};

		th.test(code, new LinterOptions().set("unused", true));
	}

	@Test(groups = { "ignoreDirective" })
	public void testIgnoreDirectiveShouldBeDetectedEvenWithLeadingAndOrTrailingWhitespace() {
		String[] code = {
				"  /*jshint ignore:start */", // leading whitespace
				"   if (true) { alert('sup') }", // should be ignored
				"  /*jshint ignore:end */  ", // leading and trailing whitespace
				"   if (true) { alert('sup') }", // should not be ignored
				"  /*jshint ignore:start */   ", // leading and trailing whitespace
				"   if (true) { alert('sup') }", // should be ignored
				"  /*jshint ignore:end */   " // leading and trailing whitespace
		};

		th.addError(4, 28, "Missing semicolon.");
		th.test(code);
	}

	// gh-2411 /* jshint ignore:start */ stopped working.
	@Test(groups = { "ignoreDirective" })
	public void testIgnoreDirectiveShouldApplyToLinesLexedDuringLookaheadOperations() {
		String[] code = {
				"void [function () {",
				"  /* jshint ignore:start */",
				"  ?",
				"  /* jshint ignore:end */",
				"}];"
		};

		th.test(code);

		code = new String[] {
				"(function () {",
				"  /* jshint ignore:start */",
				"  ?",
				"  /* jshint ignore:end */",
				"}());"
		};

		th.test(code);
	}

	@Test
	public void testJshintIgnoreShouldBeAbleToIgnoreSingleLineWithTrailingComment() {
		String code = th.readFile("src/test/resources/fixtures/gh870.js");

		th.test(code, new LinterOptions().set("unused", true));
	}

	@Test
	public void testRegressionForGH1431() {
		// The code is invalid but it should not crash JSHint.
		th.addError(1, 25, "Expected ';' and instead saw ')'.");
		th.addError(1, 26, "Expected ')' and instead saw ';'.");
		th.addError(1, 26, "Expected an identifier and instead saw ';'.");
		th.addError(1, 28, "Expected ')' to match '(' from line 1 and instead saw 'i'.");
		th.addError(1, 31, "Expected an identifier and instead saw ')'.");
		th.test("for (i=0; (arr[i])!=null); i++);");
	}

	@Test
	public void testJshintIgnoreStartEndShouldBeDetectedUsingSingleLineComments() {
		String[] code = {
				"// jshint ignore:start",
				"var a;",
				"// jshint ignore:end",
				"var b;"
		};

		th.addError(4, 5, "'b' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true));
	}

	@Test
	public void testDestructuringFunctionParametersAsEs5() {
		String src = th.readFile("src/test/resources/fixtures/destparam.js");

		th.addError(4, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(6, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 14,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(10, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 20, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(14, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 11, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(15, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(15, 14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(16, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 17,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(17, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(17, 20, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(18, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(18, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(21, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(21, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(22, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(22, 25, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(23, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(23, 28, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(24, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(24, 30, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(27, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(27, 13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(28, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(28, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(29, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(29, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(30, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(30, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(31, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(31, 24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.test(src, new LinterOptions().set("unused", true).set("undef", true).set("maxerr", 100));
	}

	@Test
	public void testDestructuringFunctionParametersAsLegacyJS() {
		String src = th.readFile("src/test/resources/fixtures/destparam.js");

		th.addError(4, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, 14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, 18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(6, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 14,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, 24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(10, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(11, 20, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(14, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(14, 11, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(15, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(15, 14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(16, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 17,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(16, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(17, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(17, 20, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(18, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(18, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(21, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(21, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(22, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(22, 25, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(23, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(23, 28, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(24, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(24, 30, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(27, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(27, 13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(28, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(28, 16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(29, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(29, 19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(30, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(30, 22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.addError(31, 7,
				"'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(31, 24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.test(src, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).set("maxerr", 100));
	}

	@Test
	public void testForParenthesesInOddNumberedToken() {
		String[] code = {
				"let f, b;",
				"let a = x => ({ f: f(x) });",
				"b = x => x;"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testRegressionCrashFromGH1573() {
		th.addError(1, 2, "Expected an identifier and instead saw 'var'.");
		th.addError(1, 6, "Expected ']' to match '[' from line 1 and instead saw 'foo'.");
		th.addError(1, 14, "Expected an identifier and instead saw ']'.");
		th.addError(1, 14, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 15, "Missing semicolon.");
		th.addError(1, 10, "Bad assignment.");
		th.test("[var foo = 1;]");
	}

	@Test
	public void testMakeSureWeDontThrowErrorsOnRemovedOptions() {
		th.test("a();",
				new LinterOptions().set("nomen", true).set("onevar", true).set("passfail", true).set("white", true));
	}

	@Test
	public void testForOfShouldntBeSubjectToForInRules() {
		th.test("for (let x of [1, 2, 3]) { console.log(x); }",
				new LinterOptions().set("forin", true).set("esnext", true));
	}

	// See gh-3099, "TypeError: Cannot read property 'type' of undefined"
	@Test
	public void testEnforcementOfForinOptionShouldBeTolerantOfInvalidSyntax() {
		th.addError(1, 6, "Creating global 'for' variable. Should be 'for (var x ...'.");
		th.addError(2, 3, "Unrecoverable syntax error. (66% scanned).");
		th.addError(3, 1, "Expected an identifier and instead saw '}'.");
		th.test(new String[] {
				"for (x in x) {",
				"  if (",
				"}"
		}, new LinterOptions().set("forin", true));
	}

	@Test
	public void testIgnoreStringsContainingBracesWithinArrayLiteralDeclarations() {
		th.test("var a = [ '[' ];");
	}

	@Test
	public void testGH1016DontIssueW088IfIdentifierIsOutsideOfBlockscope() {
		String[] code = {
				"var globalKey;",
				"function x() {",
				"  var key;",
				"  var foo = function () {",
				"      alert(key);",
				"  };",
				"  for (key in {}) {",
				"      foo();",
				"  }",
				"  function y() {",
				"    for (key in {}) {",
				"      foo();",
				"    }",
				"    for (globalKey in {}) {",
				"      foo();",
				"    }",
				"    for (nonKey in {}) {",
				"      foo();",
				"    }",
				"  }",
				"}"
		};

		th.addError(17, 10, "Creating global 'for' variable. Should be 'for (var nonKey ...'.");
		th.test(code);
	}

	@Test
	public void testES6UnusedExports() {
		String[] code = {
				"export {",
				"  varDefinedLater,",
				"  letDefinedLater,",
				"  constDefinedLater",
				"};",
				"var unusedGlobalVar = 41;",
				"let unusedGlobalLet = 41;",
				"const unusedGlobalConst = 41;",
				"function unusedGlobalFunc() {}",
				"class unusedGlobalClass {}",
				"export let globalExportLet = 42;",
				"export var globalExportVar = 43;",
				"export const globalExportConst = 44;",
				"export function unusedFn() {}",
				"export class unusedClass {}",
				"export {",
				"  unusedGlobalVar,",
				"  unusedGlobalLet,",
				"  unusedGlobalConst,",
				"  unusedGlobalFunc,",
				"  unusedGlobalClass",
				"};",
				"var varDefinedLater = 60;",
				"let letDefinedLater = 61;",
				"const constDefinedLater = 62;"
		};

		th.addError(24, 5, "'letDefinedLater' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(25, 7,
				"'constDefinedLater' was used before it was declared, which is illegal for 'const' variables.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
	}

	@Test
	public void testES6BlockExports() {
		String[] code = {
				"var broken = true;",
				"var broken2 = false;",
				"function funcScope() {",
				"  export let exportLet = 42;",
				"  export var exportVar = 43;",
				"  export const exportConst = 44;",
				"  export function exportedFn() {}",
				"  export {",
				"    broken,",
				"    broken2",
				"  };",
				"}",
				"if (true) {",
				"  export let conditionalExportLet = 42;",
				"  export var conditionalExportVar = 43;",
				"  export const conditionalExportConst = 44;",
				"  export function conditionalExportedFn() {}",
				"  export {",
				"    broken,",
				"    broken2",
				"  };",
				"}",
				"funcScope();"
		};

		th.addError(1, 5, "'broken' is defined but never used.");
		th.addError(2, 5, "'broken2' is defined but never used.");
		th.addError(4, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(5, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(6, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(7, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(8, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(14, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(15, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(16, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(17, 3, "Export declarations are only allowed at the top level of module scope.");
		th.addError(17, 10,
				"Function declarations should not be placed in blocks. Use a function expression or move the statement to the top of the outer function.");
		th.addError(18, 3, "Export declarations are only allowed at the top level of module scope.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
	}

	@Test
	public void testES6BlockImports() {
		String[] code = {
				"{",
				" import x from './m.js';",
				"}",
				"function limitScope(){",
				" import {x} from './m.js';",
				"}",
				"(function(){",
				" import './m.js';",
				"}());",
				"{",
				" import {x as y} from './m.js';",
				"}",
				"limitScope();"
		};

		th.addError(2, 2, "Import declarations are only allowed at the top level of module scope.");
		th.addError(5, 2, "Import declarations are only allowed at the top level of module scope.");
		th.addError(8, 2, "Import declarations are only allowed at the top level of module scope.");
		th.addError(11, 2, "Import declarations are only allowed at the top level of module scope.");
		th.test(code, new LinterOptions().set("esversion", 6).set("module", true));
	}

	@Test
	public void testStrictDirectiveASI() {
		LinterOptions options = new LinterOptions().set("strict", true).set("asi", true).set("globalstrict", true);

		th.newTest("1");
		th.test("'use strict'\nfunction fn() {}\nfn();", options);

		th.newTest("2");
		th.test("'use strict'\n;function fn() {}\nfn();", options);

		th.newTest("3");
		th.test("'use strict';function fn() {} fn();", options);

		th.newTest("4");
		th.addError(2, 1, "Unorthodox function invocation.");
		th.addError(2, 21, "Missing \"use strict\" statement.");
		th.test("'use strict'\n(function fn() {})();", options);

		th.newTest("5");
		th.addError(2, 10, "Missing \"use strict\" statement.");
		th.test("'use strict'\n[0] = '6';", options);

		th.newTest("6");
		th.addError(1, 29, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 1, "Missing \"use strict\" statement.");
		th.addError(2, 5, "Missing \"use strict\" statement.");
		th.test("'use strict',function fn() {}\nfn();", options);

		th.newTest("7");
		th.addError(1, 24, "Missing \"use strict\" statement.");
		th.test("'use strict'.split(' ');", options);

		th.newTest("8");
		th.addError(1, 15, "Missing \"use strict\" statement.");
		th.test("(function() { var x; \"use strict\"; return x; }());",
				new LinterOptions().set("strict", true).set("expr", true));

		th.newTest("9");
		th.addError(1, 27, "Missing \"use strict\" statement.");
		th.addError(1, 15, "Expected an assignment or function call and instead saw an expression.");
		th.test("'use strict', 'use strict';", options);

		th.newTest("10");
		th.addError(1, 28, "Missing \"use strict\" statement.");
		th.addError(1, 16, "Expected an assignment or function call and instead saw an expression.");
		th.test("'use strict' * 'use strict';", options);

		th.newTest("11");
		th.addError(2, 2, "Expected an assignment or function call and instead saw an expression.");
		th.test("'use strict'\n!x;", options, new LinterGlobals(true, "x"));

		th.newTest("12");
		th.addError(2, 1, "Misleading line break before '+'; readers may interpret this as an expression boundary.");
		th.addError(2, 3, "Missing \"use strict\" statement.");
		th.addError(2, 2, "Expected an assignment or function call and instead saw an expression.");
		th.test("'use strict'\n+x;", options, new LinterGlobals(true, "x"));

		th.newTest("13");
		th.test("'use strict'\n++x;", options, new LinterGlobals(true, "x"));

		th.newTest("14");
		th.addError(1, 13, "Bad assignment.");
		th.addError(2, 1, "Missing \"use strict\" statement.");
		th.addError(2, 2, "Missing \"use strict\" statement.");
		th.addError(2, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test("'use strict'++\nx;", options, new LinterGlobals(true, "x"));

		th.newTest("15");
		th.addError(1, 13, "Bad assignment.");
		th.addError(1, 15, "Missing \"use strict\" statement.");
		th.test("'use strict'++;", options, new LinterGlobals(true, "x"));

		th.newTest("16");
		th.addError(1, 9, "Missing \"use strict\" statement.");
		th.test("(() => 1)();", new LinterOptions().set("strict", true).set("esnext", true));

		th.newTest("17");
		th.test("(() => { \"use strict\"; })();", new LinterOptions().set("strict", true).set("esnext", true));

		th.newTest("18");
		th.test("(() => {})();", new LinterOptions().set("strict", true).set("esnext", true));

		th.newTest("19");
		th.addError(1, 10, "Missing \"use strict\" statement.");
		th.test("(() => { return 1; })();", new LinterOptions().set("strict", true).set("esnext", true));

		th.newTest("20");
		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.test(new String[] {
				"'use strict';",
				"(() => { return 1; })();"
		}, new LinterOptions().set("strict", true).set("esnext", true));
	}

	@Test
	public void testDereferenceDelete() {
		th.addError(1, 7, "Expected an identifier and instead saw '.'.");
		th.addError(1, 8, "Missing semicolon.");
		th.test("delete.foo();");
	}

	@Test
	public void testTrailingCommaInObjectBindingPattern() {
		String[] code = {
				"function fn(O) {",
				"  var {a, b, c,} = O;",
				"}",
				"fn({ a: 1, b: 2, c: 3 });"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testTrailingCommaInObjectBindingPatternParameters() {
		String[] code = {
				"function fn({a, b, c,}) { }",
				"fn({ a: 1, b: 2, c: 3 });"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testTrailingCommaInArrayBindingPattern() {
		String[] code = {
				"function fn(O) {",
				"  var [a, b, c,] = O;",
				"}",
				"fn([1, 2, 3]);"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testTrailingCommaInArrayBindingPatternParameters() {
		String[] code = {
				"function fn([a, b, c,]) { }",
				"fn([1, 2, 3]);"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testGH1879() {
		String[] code = {
				"function Foo() {",
				"  return;",
				"  // jshint ignore:start",
				"  return [];",
				"  // jshint ignore:end",
				"}"
		};

		th.test(code);
	}

	@Test
	public void testCommaAfterRestElementInArrayBindingPattern() {
		String[] code = {
				"function fn(O) {",
				"  var [a, b, ...c,] = O;",
				"  var [...d,] = O;",
				"}",
				"fn([1, 2, 3]);"
		};

		th.addError(2, 18, "Invalid element after rest element.");
		th.addError(3, 12, "Invalid element after rest element.");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testCommaAfterRestElementInArrayBindingPatternParameters() {
		String[] code = {
				"function fn([a, b, ...c,]) { }",
				"function fn2([...c,]) { }",
				"fn([1, 2, 3]);",
				"fn2([1,2,3]);"
		};

		th.addError(1, 24, "Invalid element after rest element.");
		th.addError(2, 19, "Invalid element after rest element.");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testCommaAfterRestParameter() {
		String[] code = {
				"function fn(a, b, ...c, d) { }",
				"function fn2(...a, b) { }",
				"fn(1, 2, 3);",
				"fn2(1, 2, 3);"
		};

		th.addError(1, 23, "Invalid parameter after rest parameter.");
		th.addError(2, 18, "Invalid parameter after rest parameter.");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testRestParameterWithDefault() {
		th.addError(1, 17, "Rest parameter does not a support default value.");
		th.test("function f(...x = 0) {}", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testExtraRestOperator() {
		th.newTest();
		th.addError(1, 23, "Unexpected '...'.");
		th.test("function fn([a, b, ......c]) { }", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 18, "Unexpected '...'.");
		th.test("function fn2([......c]) { }", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 23, "Unexpected '...'.");
		th.addError(1, 26, "Expected an identifier and instead saw ')'.");
		th.addError(1, 30, "Unrecoverable syntax error. (100% scanned).");
		th.test("function fn3(a, b, ......) { }", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 17, "Unexpected '...'.");
		th.addError(1, 20, "Expected an identifier and instead saw ')'.");
		th.addError(1, 24, "Unrecoverable syntax error. (100% scanned).");
		th.test("function fn4(......) { }", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 9, "Unexpected '...'.");
		th.test("var [......a] = [1, 2, 3];", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 16, "Unexpected '...'.");
		th.test("var [a, b, ... ...c] = [1, 2, 3];", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 17, "Unexpected '...'.");
		th.test("var arrow = (......a) => a;", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 24, "Unexpected '...'.");
		th.test("var arrow2 = (a, b, ......c) => c;", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 19, "Unexpected '...'.");
		th.test("var arrow3 = ([......a]) => a;", new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 25, "Unexpected '...'.");
		th.test("var arrow4 = ([a, b, ......c]) => c;", new LinterOptions().set("esnext", true));
	}

	@Test
	public void testRestOperatorWithoutIdentifier() {
		String[] code = {
				"function fn([a, b, ...]) { }",
				"function fn2([...]) { }",
				"function fn3(a, b, ...) { }",
				"function fn4(...) { }",
				"var [...] = [1, 2, 3];",
				"var [a, b, ...] = [1, 2, 3];",
				"var arrow = (...) => void 0;",
				"var arrow2 = (a, b, ...) => a;",
				"var arrow3 = ([...]) => void 0;",
				"var arrow4 = ([a, b, ...]) => a;",
				"fn([1, 2, 3]);",
				"fn2([1, 2, 3]);",
				"fn3(1, 2, 3);",
				"fn3(1, 2, 3);",
				"arrow(1, 2, 3);",
				"arrow2(1, 2, 3);",
				"arrow3([1, 2, 3]);",
				"arrow4([1, 2, 3]);"
		};

		th.addError(1, 23, "Expected an identifier and instead saw ']'.");
		th.addError(1, 24, "Expected ',' and instead saw ')'.");
		th.addError(1, 26, "Empty destructuring: this is unnecessary and can be removed.");
		th.addError(2, 1, "Expected ',' and instead saw 'function'.");
		th.addError(2, 13, "Expected ',' and instead saw '('.");
		th.addError(2, 18, "Expected an identifier and instead saw ']'.");
		th.addError(2, 19, "Expected ',' and instead saw ')'.");
		th.addError(2, 21, "Empty destructuring: this is unnecessary and can be removed.");
		th.addError(3, 1, "Expected ',' and instead saw 'function'.");
		th.addError(3, 13, "Expected ',' and instead saw '('.");
		th.addError(3, 23, "Expected an identifier and instead saw ')'.");
		th.addError(3, 25, "Expected ',' and instead saw '{'.");
		th.addError(3, 27, "Expected an identifier and instead saw '}'.");
		th.addError(4, 1, "Expected ',' and instead saw 'function'.");
		th.addError(4, 13, "Expected ',' and instead saw '('.");
		th.addError(4, 17, "Expected an identifier and instead saw ')'.");
		th.addError(4, 19, "Expected ',' and instead saw '{'.");
		th.addError(4, 21, "Expected an identifier and instead saw '}'.");
		th.addError(5, 1, "Expected ',' and instead saw 'var'.");
		th.addError(5, 9, "Expected an identifier and instead saw ']'.");
		th.addError(5, 11, "Expected ',' and instead saw '='.");
		th.addError(5, 14, "Expected an identifier and instead saw '1'.");
		th.addError(5, 17, "Expected an identifier and instead saw '2'.");
		th.addError(5, 20, "Expected an identifier and instead saw '3'.");
		th.addError(5, 22, "Expected ',' and instead saw ';'.");
		th.addError(6, 1, "Expected an identifier and instead saw 'var' (a reserved word).");
		th.addError(6, 5, "Expected ',' and instead saw '['.");
		th.addError(6, 15, "Expected an identifier and instead saw ']'.");
		th.addError(6, 17, "Expected ',' and instead saw '='.");
		th.addError(6, 20, "Expected an identifier and instead saw '1'.");
		th.addError(6, 23, "Expected an identifier and instead saw '2'.");
		th.addError(6, 26, "Expected an identifier and instead saw '3'.");
		th.addError(6, 28, "Expected ',' and instead saw ';'.");
		th.addError(7, 1, "Expected an identifier and instead saw 'var' (a reserved word).");
		th.addError(7, 5, "Expected ',' and instead saw 'arrow'.");
		th.addError(7, 11, "Expected an identifier and instead saw '='.");
		th.addError(7, 13, "Expected ',' and instead saw '('.");
		th.addError(7, 17, "Expected an identifier and instead saw ')'.");
		th.addError(7, 19, "Expected ',' and instead saw '=>'.");
		th.addError(7, 22, "Expected an identifier and instead saw 'void' (a reserved word).");
		th.addError(7, 27, "Expected ',' and instead saw '0'.");
		th.addError(7, 28, "Expected an identifier and instead saw ';'.");
		th.addError(7, 28, "Expected ',' and instead saw ';'.");
		th.addError(8, 1, "Expected an identifier and instead saw 'var' (a reserved word).");
		th.addError(8, 5, "Expected ',' and instead saw 'arrow2'.");
		th.addError(8, 12, "Expected an identifier and instead saw '='.");
		th.addError(8, 14, "Expected ',' and instead saw '('.");
		th.addError(8, 24, "Expected an identifier and instead saw ')'.");
		th.addError(8, 26, "Expected ',' and instead saw '=>'.");
		th.addError(8, 30, "Expected ',' and instead saw ';'.");
		th.addError(9, 1, "Expected an identifier and instead saw 'var' (a reserved word).");
		th.addError(9, 5, "Expected ',' and instead saw 'arrow3'.");
		th.addError(9, 12, "Expected an identifier and instead saw '='.");
		th.addError(9, 14, "Expected ',' and instead saw '('.");
		th.addError(9, 19, "Expected an identifier and instead saw ']'.");
		th.addError(9, 20, "Expected ',' and instead saw ')'.");
		th.addError(9, 22, "Expected an identifier and instead saw '=>'.");
		th.addError(9, 22, "Too many errors. (50% scanned).");
		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testInvalidSpread() {
		th.addError(1, 6, "Expected an identifier and instead saw '...'.");
		th.addError(1, 9, "Missing semicolon.");
		th.addError(1, 9, "Expected an assignment or function call and instead saw an expression.");
		th.test("void ...x;", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testGetAsIdentifierProp() {
		th.test("var get; var obj = { get };", new LinterOptions().set("esnext", true));

		th.test("var set; var obj = { set };", new LinterOptions().set("esnext", true));

		th.test("var get, set; var obj = { get, set };", new LinterOptions().set("esnext", true));

		th.test("var get, set; var obj = { set, get };", new LinterOptions().set("esnext", true));

		th.test("var get; var obj = { a: null, get };", new LinterOptions().set("esnext", true));

		th.test("var get; var obj = { a: null, get, b: null };", new LinterOptions().set("esnext", true));

		th.test("var get; var obj = { get, b: null };", new LinterOptions().set("esnext", true));

		th.test("var get; var obj = { get, get a() {} };", new LinterOptions().set("esnext", true));

		th.test(new String[] {
				"var set;",
				"var obj = { set, get a() {}, set a(_) {} };"
		}, new LinterOptions().set("esnext", true));

	}

	@Test
	public void testInvalidParams() {
		th.addError(1, 11, "Expected an identifier and instead saw '!'.");
		th.addError(1, 11, "Unrecoverable syntax error. (100% scanned).");
		th.test("(function(!", new LinterOptions().set("esnext", true));
	}

	// Regression test for gh-2362
	@Test
	public void testFunctionKeyword() {
		th.addError(1, 1, "Missing name in function declaration.");
		th.addError(1, 1, "Expected '(' and instead saw ''.");
		th.addError(1, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test("function");
	}

	@Test
	public void testNonGeneratorAfterGenerator() {
		String[] code = {
				"var obj = {",
				"  *gen() {",
				"    yield 1;",
				"  },",
				// non_gen shouldn't be parsed as a generator method here, and parser
				// shouldn't report an error about a generator without a yield expression.
				"  non_gen() {",
				"  }",
				"};"
		};

		th.test(code, new LinterOptions().set("esnext", true));
	}

	@Test
	public void testNewTarget() {
		String[] code = {
				"class A {",
				"  constructor() {",
				"    return new.target;",
				"  }",
				"}"
		};

		th.newTest("only in ES6");
		th.addError(1, 1, "'class' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, 15, "'new.target' is only available in ES6 (use 'esversion: 6').");
		th.test(code);

		th.newTest("only in ES6").test(code, new LinterOptions().set("esnext", true));
		th.newTest("ES7").test(code, new LinterOptions().set("esversion", 7));
		th.newTest("ES8").test(code, new LinterOptions().set("esversion", 8));

		String[] code2 = {
				"var a = new.target;",
				"var b = () => {",
				"  var c = () => {",
				"    return new.target;",
				"  };",
				"  return new.target;",
				"};",
				"var d = function() {",
				"  return new.target;",
				"};",
				"function e() {",
				"  var f = () => {",
				"    return new.target;",
				"  };",
				"  return new.target;",
				"}",
				"class g {",
				"  constructor() {",
				"    return new.target;",
				"  }",
				"}"
		};

		th.newTest("must be in function scope");
		th.addError(1, 12, "'new.target' must be in function scope.");
		th.addError(4, 15, "'new.target' must be in function scope.");
		th.addError(6, 13, "'new.target' must be in function scope.");
		th.test(code2, new LinterOptions().set("esnext", true));

		th.newTest("must be in function scope");
		th.addError(1, 12, "'new.target' must be in function scope.");
		th.addError(4, 15, "'new.target' must be in function scope.");
		th.addError(6, 13, "'new.target' must be in function scope.");
		th.test(code2, new LinterOptions().set("esversion", 2016));

		th.newTest("must be in function scope");
		th.addError(1, 12, "'new.target' must be in function scope.");
		th.addError(4, 15, "'new.target' must be in function scope.");
		th.addError(6, 13, "'new.target' must be in function scope.");
		th.test(code2, new LinterOptions().set("esversion", 2017));

		String[] code3 = {
				"var x = new.meta;"
		};

		th.newTest("invalid meta property");
		th.addError(1, 12, "Invalid meta property: 'new.meta'.");
		th.test(code3);

		String[] code4 = {
				"class A {",
				"  constructor() {",
				"    new.target = 2;",
				"    new.target += 2;",
				"    new.target &= 2;",
				"    new.target++;",
				"    ++new.target;",
				"  }",
				"}"
		};

		th.newTest("can't assign to new.target");
		th.addError(3, 16, "Bad assignment.");
		th.addError(4, 16, "Bad assignment.");
		th.addError(5, 16, "Bad assignment.");
		th.addError(6, 15, "Bad assignment.");
		th.addError(7, 5, "Bad assignment.");
		th.test(code4, new LinterOptions().set("esnext", true));
	}

	// gh2656: "[Regression] 2.9.0 warns about proto deprecated even if proto:true"
	@Test
	public void testLazyIdentifierChecks() {
		String[] src = {
				"var o = [",
				"  function() {",
				"    // jshint proto: true",
				"    o.__proto__ = null;",
				"  }",
				"];",
				"o.__proto__ = null;"
		};

		th.addError(7, 12, "The '__proto__' property is deprecated.");
		th.test(src);

		src = new String[] {
				"var o = {",
				"  p: function() {",
				"    // jshint proto: true, iterator: true",
				"    o.__proto__ = null;",
				"    o.__iterator__ = null;",
				"  }",
				"};",
				"o.__proto__ = null;",
				"o.__iterator__ = null;"
		};

		th.newTest();
		th.addError(8, 12, "The '__proto__' property is deprecated.");
		th.addError(9, 15, "The '__iterator__' property is deprecated.");
		th.test(src);
	}

	@Test
	public void testParsingCommas() {
		String src = th.readFile("src/test/resources/fixtures/parsingCommas.js");

		th.addError(2, 12, "Expected an identifier and instead saw ','.");
		th.addError(6, 9, "Unexpected ','.");
		th.addError(6, 9, "Comma warnings can be turned off with 'laxcomma'.");
		th.addError(5, 12, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(6, 10, "Unexpected ')'.");
		th.addError(6, 10, "Expected an identifier and instead saw ')'.");
		th.addError(6, 12, "Expected ')' to match '(' from line 5 and instead saw '{'.");
		th.addError(6, 13, "Expected an identifier and instead saw '}'.");
		th.addError(6, 13, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 14, "Missing semicolon.");
		th.test(src);
	}

	@Test
	public void testInstanceOfLiterals() {
		String[] code = {
				"var x;",
				"var y = [x];",

				// okay
				"function Y() {}",
				"function template() { return Y; }",
				"var a = x instanceof Y;",
				"a = new X() instanceof function() { return X; }();",
				"a = x instanceof template``;",
				"a = x instanceof /./.constructor;",
				"a = x instanceof \"\".constructor;",
				"a = x instanceof [y][0];",
				"a = x instanceof {}[constructor];",
				"function Z() {",
				"  let undefined = function() {};",
				"  a = x instanceof undefined;",
				"}",

				// error: literals and unary operators cannot be used
				"a = x instanceof +x;",
				"a = x instanceof -x;",
				"a = x instanceof 0;",
				"a = x instanceof '';",
				"a = x instanceof null;",
				"a = x instanceof undefined;",
				"a = x instanceof {};",
				"a = x instanceof [];",
				"a = x instanceof /./;",
				"a = x instanceof ``;",
				"a = x instanceof `${x}`;",

				// warning: functions declarations should not be used
				"a = x instanceof function() {};",
				"a = x instanceof function MyUnusableFunction() {};"
		};

		String errorMessage = "Non-callable values cannot be used as the second operand to instanceof.";
		String warningMessage = "Function expressions should not be used as the second operand to instanceof.";

		th.addError(16, 20, errorMessage);
		th.addError(17, 20, errorMessage);
		th.addError(18, 19, errorMessage);
		th.addError(19, 20, errorMessage);
		th.addError(20, 22, errorMessage);
		th.addError(21, 27, errorMessage);
		th.addError(22, 20, errorMessage);
		th.addError(23, 20, errorMessage);
		th.addError(24, 21, errorMessage);
		th.addError(25, 20, errorMessage);
		th.addError(26, 24, errorMessage);
		th.addError(27, 31, warningMessage);
		th.addError(28, 50, warningMessage);

		th.test(code, new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 13, "Expected an identifier and instead saw ';'.");
		th.addError(1, 13, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 14, "Missing semicolon.");
		th.test("0 instanceof;");
	}

	@Test
	public void testForInExpr() {
		th.test(new String[] {
				"for (var x in [], []) {}"
		});

		th.addError(2, 17, "Expected ')' to match '(' from line 2 and instead saw ','.");
		th.addError(2, 21, "Expected an identifier and instead saw ')'.");
		th.addError(2, 21, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 22, "Missing semicolon.");
		th.test(new String[] {
				"for (var x in [], []) {}",
				"for (var x of {}, {}) {}"
		}, new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testOctalEscape() {
		th.addError(3, 8, "Octal literals are not allowed in strict mode.");
		th.addError(4, 8, "Octal literals are not allowed in strict mode.");
		th.addError(5, 8, "Octal literals are not allowed in strict mode.");
		th.addError(6, 8, "Octal literals are not allowed in strict mode.");
		th.addError(7, 8, "Octal literals are not allowed in strict mode.");
		th.addError(8, 8, "Octal literals are not allowed in strict mode.");
		th.addError(9, 8, "Octal literals are not allowed in strict mode.");
		th.test(new String[] {
				"'use strict';",
				"void '\\0';",
				"void '\\1';",
				"void '\\2';",
				"void '\\3';",
				"void '\\4';",
				"void '\\5';",
				"void '\\6';",
				"void '\\7';",
				"void '\\8';",
				"void '\\9';",
		}, new LinterOptions().set("strict", "global"));

		th.newTest();
		th.test(new String[] {
				"void '\\0';",
				"void '\\1';",
				"void '\\2';",
				"void '\\3';",
				"void '\\4';",
				"void '\\5';",
				"void '\\6';",
				"void '\\7';",
				"void '\\8';",
				"void '\\9';",
		});
	}

	// See gh-3004, "Starting jsdoc comment causes 'Unclosed regular expression'
	// error"
	@Test
	public void testLookaheadBeyondEnd() {
		th.addError(1, 7, "Unmatched '{'.");
		th.addError(1, 7, "Unrecoverable syntax error. (100% scanned).");
		th.test("({ a: {");
	}

	// In releases prior to 2.9.6, JSHint would not terminate when given the source
	// code in the following tests.
	@Test
	public void testRegressionTestForGH3230() {
		th.newTest("as originally reported");
		th.addError(1, 12, "Expected ';' and instead saw ')'.");
		th.addError(1, 13, "Unmatched '{'.");
		th.addError(1, 13, "Unrecoverable syntax error. (100% scanned).");
		th.test("for(var i=1){");

		th.newTest("further simplified (unclosed brace)");
		th.addError(1, 4,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 5, "Expected an identifier and instead saw ''.");
		th.addError(1, 5, "Unrecoverable syntax error. (100% scanned).");
		th.test("for({");

		th.newTest("further simplified (unclosed bracket)");
		th.addError(1, 4,
				"'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, 5, "Unexpected early end of program.");
		th.addError(1, 5, "Unrecoverable syntax error. (100% scanned).");
		th.test("for([");
	}

	@Test
	public void testUnicode8() {
		th.addError(1, 5, "'unicode 8' is only available in ES6 (use 'esversion: 6').");
		th.test("var Ϳ;", new LinterOptions().set("esversion", 5));

		th.newTest();
		th.test("var Ϳ;", new LinterOptions().set("esversion", 6));
	}

	@Test(groups = { "exponentiation" })
	public void testExponentiationEsversion() {
		String src = "x = 2 ** 3;";

		th.newTest();
		th.addError(1, 7, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.test(src);

		th.newTest();
		th.addError(1, 7, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.test(src, new LinterOptions().set("esversion", 6));

		th.newTest();
		th.test(src, new LinterOptions().set("esversion", 7));
	}

	@Test(groups = { "exponentiation" })
	public void testExponentiationWhitespace() {
		th.newTest();
		th.test(new String[] {
				"2 ** 3;",
				"2** 3;",
				"2 **3;",
		}, new LinterOptions().set("expr", true).set("esversion", 7));

		th.newTest("newlines");
		th.addError(2, 1, "Misleading line break before '**'; readers may interpret this as an expression boundary.");
		th.test(new String[] {
				"2",
				"** 3;",
				"2 **",
				"3;"
		}, new LinterOptions().set("expr", true).set("esversion", 7));

		th.newTest("invalid");
		th.addError(1, 5, "Expected an identifier and instead saw '*'.");
		th.addError(1, 6, "Missing semicolon.");
		th.test(new String[] {
				"2 * * 3;"
		}, new LinterOptions().set("expr", true).set("esversion", 7));
	}

	@Test(groups = { "exponentiation" })
	public void testExponentiationLeftPrecedence() {
		th.newTest("UpdateExpressions");
		th.test(new String[] {
				"++x ** y;",
				"--x ** y;",
				"x++ ** y;",
				"x-- ** y;"
		}, new LinterOptions().set("expr", true).set("esversion", 7));

		th.newTest("UnaryExpressions");
		th.addError(1, 10, "Variables should not be deleted.");
		th.addError(1, 10, "Unexpected '**'.");
		th.addError(2, 8, "Unexpected '**'.");
		th.addError(3, 10, "Unexpected '**'.");
		th.addError(4, 4, "Unexpected '**'.");
		th.addError(5, 4, "Unexpected '**'.");
		th.addError(6, 4, "Unexpected '**'.");
		th.addError(7, 4, "Unexpected '**'.");
		th.test(new String[] {
				"delete 2 ** 3;",
				"void 2 ** 3;",
				"typeof 2 ** 3;",
				"+2 ** 3;",
				"-2 ** 3;",
				"~2 ** 3;",
				"!2 ** 3;"
		}, new LinterOptions().set("expr", true).set("esversion", 7));

		th.newTest("Grouping");
		th.addError(1, 10, "Variables should not be deleted.");
		th.test(new String[] {
				"(delete 2) ** 3;",
				"(void 2) ** 3;",
				"(typeof 2) ** 3;",
				"(+2) ** 3;",
				"(-2) ** 3;",
				"(~2) ** 3;",
				"(!2) ** 3;"
		}, new LinterOptions().set("expr", true).set("esversion", 7));
	}

	@Test(groups = { "exponentiation" })
	public void testExponentiationRightPrecedence() {
		th.newTest("ExponentiationExpression");
		th.test(new String[] {
				"x ** x ** y;",
				"x ** ++x ** y;",
				"x ** --x ** y;",
				"x ** x++ ** y;",
				"x ** x-- ** y;"
		}, new LinterOptions().set("expr", true).set("esversion", 7));

		th.newTest("UnaryExpression");
		th.test(new String[] {
				"x ** delete x.y;",
				"x ** void y;",
				"x ** typeof y;",
				"x ** +y;",
				"x ** -y;",
				"x ** ~y;",
				"x ** !y;"
		}, new LinterOptions().set("expr", true).set("esversion", 7));
	}

	@Test(groups = { "exponentiation" })
	public void testExponentiationСompoundAssignment() {
		String[] src = {
				"x **= x;",
				"x**=x;",
				"x **= -2;",
				"x **= 2 ** 4;"
		};

		th.newTest("valid (esversion: 6)"); // JSHINT_BUG: should be 5
		th.addError(1, 3, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(2, 2, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(3, 3, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(4, 3, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(4, 9, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.test(src, new LinterOptions().set("esversion", 5));

		th.newTest("valid (esversion: 6)");
		th.addError(1, 3, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(2, 2, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(3, 3, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(4, 3, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.addError(4, 9, "'Exponentiation operator' is only available in ES7 (use 'esversion: 7').");
		th.test(src, new LinterOptions().set("esversion", 6));

		th.newTest("valid (esversion: 7)");
		th.test(src, new LinterOptions().set("esversion", 7));

		th.newTest("invalid syntax - whitespace 1");
		th.addError(1, 5, "Expected an identifier and instead saw '*='.");
		th.addError(1, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 7, "Missing semicolon.");
		th.test("x * *= x;", new LinterOptions().set("esversion", 7));

		th.newTest("invalid syntax - whitespace 2"); // JSHINT_BUG: looks like this test is total duplicate of previous
														// one
		th.addError(1, 5, "Expected an identifier and instead saw '*='.");
		th.addError(1, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 7, "Missing semicolon.");
		th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
		th.test("x * *= x;", new LinterOptions().set("esversion", 7));

		th.newTest("invalid syntax - newline 1");
		th.addError(2, 1, "Expected an identifier and instead saw '*='.");
		th.addError(2, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 3, "Missing semicolon.");
		th.addError(2, 4, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"x *",
				"*= x;"
		}, new LinterOptions().set("esversion", 7));

		th.newTest("invalid syntax - newline 2");
		th.addError(2, 1, "Expected an identifier and instead saw '='.");
		th.addError(2, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 2, "Missing semicolon.");
		th.addError(2, 3, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"x **",
				"= x;"
		}, new LinterOptions().set("esversion", 7));

		th.newTest("invalid assignment target");
		th.addError(1, 3, "Bad assignment.");
		th.addError(2, 6, "Bad assignment.");
		th.test(new String[] {
				"0 **= x;",
				"this **= x;"
		}, new LinterOptions().set("esversion", 7));
	}

	@Test
	public void testLetAsIdentifier() {
		th.newTest("variable binding");
		th.test(new String[] {
				"var let;",
				"function f(let) {}"
		});

		th.newTest("function binding");
		th.test("function let(let) {}");

		String[] src = {
				"var let;",
				"var x = let;",
				"let;",
				"void let;",
				"let();",
				"let(let);",
				"let(let());",
				"for (let; false; false) {}",
				"for (let in {}) {}",
				"for (let = 0; false; false) {}",
				"for (let || 0; false; false) {}"
		};

		th.newTest("identifier reference (ES5)");
		th.addError(3, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(src, new LinterOptions().set("esversion", 5));

		th.newTest("identifier reference (ES2015)");
		th.addError(3, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(src, new LinterOptions().set("esversion", 6));

		// The same source code is expected to be parsed as a `let` declaration in
		// ES2015 and later.
		th.newTest("identifier reference with ASI");
		th.addError(1, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 4, "Missing semicolon.");
		th.addError(2, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 4, "Missing semicolon.");
		th.addError(6, 1, "Misleading line break before '='; readers may interpret this as an expression boundary.");
		th.test(new String[] {
				"let",
				"x;",
				"let",
				"void 0;",
				"let",
				"= 0;"
		}, new LinterOptions().set("esversion", 5));

		// The same source code is expected to be parsed as a `let` declaration in
		// ES2015 and later.
		th.newTest("identifier reference with ASI");
		th.addError(1, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 4, "Missing semicolon.");
		th.addError(4, 1, "Misleading line break before '='; readers may interpret this as an expression boundary.");
		th.test(new String[] {
				"let",
				"void 0;",
				"let",
				"= 0;"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("other uses");
		th.test(new String[] {
				"let: while (false) {",
				"  break let;",
				"}",
				"void { let: 0 };",
				"void {}.let;"
		});
	}

	@Test
	public void testTrailingParameterComma() {
		String code = "function f(x,) {}";

		th.newTest("declaration in ES5");
		th.addError(1, 13, "'Trailing comma in function parameters' is only available in ES8 (use 'esversion: 8').");
		th.test(code, new LinterOptions().set("esversion", 5));
		th.newTest("declaration in ES6");
		th.addError(1, 13, "'Trailing comma in function parameters' is only available in ES8 (use 'esversion: 8').");
		th.test(code, new LinterOptions().set("esversion", 6));
		th.newTest("declaration in ES7");
		th.addError(1, 13, "'Trailing comma in function parameters' is only available in ES8 (use 'esversion: 8').");
		th.test(code, new LinterOptions().set("esversion", 7));
		th.newTest("declaration in ES8");
		th.test(code, new LinterOptions().set("esversion", 8));
	}

	@Test
	public void testTrailingArgumentsComma() {
		th.newTest("valid - not supported in ES7");
		th.addError(1, 4, "'Trailing comma in arguments lists' is only available in ES8 (use 'esversion: 8').");
		th.test("f(0,);", new LinterOptions().set("esversion", 7));

		th.newTest("valid - supported in ES8");
		th.test(new String[] {
				"f(0,);",
				"f(0, 0,);"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("invalid - zero expressions");
		th.addError(1, 3, "Expected an identifier and instead saw ','.");
		th.test(new String[] {
				"f(,);"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("invalid - zero expressions, multiple commas");
		th.addError(1, 3, "Expected an identifier and instead saw ','.");
		th.test(new String[] {
				"f(,,);",
		}, new LinterOptions().set("esversion", 8));

		th.newTest("invalid - multiple commas");
		th.addError(1, 5, "Expected an identifier and instead saw ','.");
		th.test(new String[] {
				"f(0,,);",
		}, new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsAsyncIdentifier() {
		String[] code = {
				"var async;",
				"{ let async; }",
				"{ const async = null; }",
				"async: while (false) {}",
				"void { async };",
				"void { async: 0 };",
				"void { async() {} };",
				"void { get async() {} };",
				"async();",
				"async(async);",
				"async(async());"
		};

		String[] strictCode = ArrayUtils.addAll(new String[] { "'use strict';" }, code);

		th.newTest();
		th.test(code, new LinterOptions().set("esversion", 7));

		th.newTest();
		th.test(strictCode, new LinterOptions().set("esversion", 7).set("strict", "global"));

		th.newTest();
		th.test(code, new LinterOptions().set("esversion", 8));

		th.newTest();
		th.test(strictCode, new LinterOptions().set("esversion", 8).set("strict", "global"));

		th.newTest();
		th.addError(1, 9, "Expected an assignment or function call and instead saw an expression.");
		th.test("async=>{};", new LinterOptions().set("esversion", 6));

		th.newTest();
		th.addError(1, 9, "Expected an assignment or function call and instead saw an expression.");
		th.test("async=>{};", new LinterOptions().set("esversion", 8));

		th.newTest("Line termination");
		th.addError(1, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 6, "Missing semicolon.");
		th.addError(3, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 6, "Missing semicolon.");
		th.addError(4, 7, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"async",
				"function f() {}",
				"async",
				"x => {};"
		}, new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsAwaitIdentifier() {
		String[] code = {
				"var await;",
				"{ let await; }",
				"{ const await = null; }",
				"await: while (false) {}",
				"void { await };",
				"void { await: 0 };",
				"void { await() {} };",
				"void { get await() {} };",
				"await();",
				"await(await);",
				"await(await());",
				"await;"
		};

		String[] functionCode = ArrayUtils.addAll(ArrayUtils.addAll(new String[] { "(function() {" }, code),
				new String[] { "}());" });
		String[] strictCode = ArrayUtils.addAll(new String[] { "'use strict';" }, code);

		th.newTest();
		th.addError(12, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(code, new LinterOptions().set("esversion", 7));
		th.newTest();
		th.addError(13, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(functionCode, new LinterOptions().set("esversion", 7));
		th.newTest();
		th.addError(13, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(strictCode, new LinterOptions().set("esversion", 7).set("strict", "global"));

		th.newTest();
		th.addError(12, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(code, new LinterOptions().set("esversion", 8));
		th.newTest();
		th.addError(13, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(functionCode, new LinterOptions().set("esversion", 8));
		th.newTest();
		th.addError(13, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(strictCode, new LinterOptions().set("esversion", 8).set("strict", "global"));

		th.newTest("nested inside a non-async function");
		th.addError(3, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(9, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(13, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(17, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(21, 7, "Expected an assignment or function call and instead saw an expression.");
		th.addError(24, 7, "Expected an assignment or function call and instead saw an expression.");
		th.addError(27, 7, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"async function a() {",
				"  function f() {",
				"    await;",
				"  }",
				"  void function() {",
				"    await;",
				"  };",
				"  function* g() {",
				"    await;",
				"    yield 0;",
				"  }",
				"  void function*() {",
				"    await;",
				"    yield 0;",
				"  };",
				"  void (() => {",
				"    await;",
				"  });",
				"  void {",
				"    get a() {",
				"      await;",
				"    },",
				"    m() {",
				"      await;",
				"    },",
				"    *g() {",
				"      await;",
				"      yield 0;",
				"    }",
				"  };",
				"}"
		}, new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsExpression() {
		th.newTest("Statement position");
		th.addError(1, 15, "Missing name in function declaration.");
		th.test("async function() {}", new LinterOptions().set("esversion", 8));

		th.newTest("Expression position (disallowed prior to ES8)");
		th.addError(1, 6, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.test("void async function() {};", new LinterOptions().set("esversion", 7));

		th.newTest("Expression position");
		th.test("void async function() {};", new LinterOptions().set("esversion", 8));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 26, "Unexpected 'await'.");
		th.test("void async function (x = await 0) {};", new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsAwaitOperator() {
		th.newTest("Operands");
		th.test(new String[] {
				"void async function() {",
				"  await 0;",
				"  await /(?:)/;",
				"  await await 0;",
				"  await async function() {};",
				"};"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("missing operands");
		th.addError(2, 8, "Expected an identifier and instead saw ';'.");
		th.addError(2, 9, "Missing semicolon.");
		th.test(new String[] {
				"void async function() {",
				"  await;",
				"};"
		}, new LinterOptions().set("esversion", 8));

		// Regression test for gh-3395
		th.newTest("within object initializer");
		th.test(new String[] {
				"void async function() {",
				"  void {",
				"    x: await 0,",
				"    [await 0]: 0,",
				"    get [await 0]() {},",
				"    set [await 0](_) {},",
				"  };",
				"};"
		}, new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsArrow() {
		th.newTest("Statement position");
		th.addError(1, 14, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 13, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 15, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 18, "Expected an assignment or function call and instead saw an expression.");
		th.addError(5, 24, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"async () => {};",
				"async x => {};",
				"async (x) => {};",
				"async (x, y) => {};",
				"async (x, y = x()) => {};"
		}, new LinterOptions().set("esversion", 8));

		String[] expressions = {
				"void (async () => {});",
				"void (async x => {});",
				"void (async (x) => {});",
				"void (async (x, y) => {});",
				"void (async (x, y = x()) => {});"
		};

		th.newTest("Expression position (disallowed prior to ES8)");
		th.addError(1, 7, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(2, 7, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(3, 7, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(4, 7, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(5, 7, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.test(expressions, new LinterOptions().set("esversion", 7));

		th.newTest("Expression position");
		th.test(expressions, new LinterOptions().set("esversion", 8));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 18, "Unexpected 'await'.");
		th.test("void (async (x = await 0) => {});", new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsDeclaration() {
		th.newTest();
		th.addError(1, 1, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.test("async function f() {}", new LinterOptions().set("esversion", 7));

		th.newTest();
		th.test("async function f() {}", new LinterOptions().set("esversion", 8));

		th.newTest();
		th.addError(1, 22, "Unnecessary semicolon.");
		th.test("async function f() {};", new LinterOptions().set("esversion", 8));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 22, "Unexpected 'await'.");
		th.test("async function f(x = await 0) {}", new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsObjectMethod() {
		String[] code = {
				"void { async m() {} };",
				"void { async 'm'() {} };",
				"void { async ['m']() {} };"
		};

		th.newTest("Disallowed prior to ES8");
		th.addError(1, 8, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(2, 8, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(3, 8, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.test(code, new LinterOptions().set("esversion", 7));

		th.newTest("Allowed in ES8");
		th.test(code, new LinterOptions().set("esversion", 8));

		th.newTest();
		th.test(new String[] {
				"void { async m() { await 0; } };"
		}, new LinterOptions().set("esversion", 8));

		th.newTest();
		th.addError(3, 9, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 14, "Missing semicolon.");
		th.addError(3, 15, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"void {",
				"  async m() { await 0; },",
				"  n() { await 0; },",
				"};"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 20, "Unexpected 'await'.");
		th.test("void { async m(x = await 0) {} };", new LinterOptions().set("esversion", 9));

		th.newTest("Illegal line break");
		th.addError(2, 3, "Line breaking error 'async'.");
		th.test(new String[] {
				"void {",
				"  async",
				"  m() {}",
				"};"
		}, new LinterOptions().set("esversion", 9));
	}

	@Test(groups = { "asyncFunctions" })
	public void testAsyncFunctionsClassMethod() {
		String[] code = {
				"void class { async m() {} };",
				"void class { async 'm'() {} };",
				"void class { async ['m']() {} };"
		};

		th.newTest("Disallowed prior to ES8");
		th.addError(1, 14, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(2, 14, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.addError(3, 14, "'async functions' is only available in ES8 (use 'esversion: 8').");
		th.test(code, new LinterOptions().set("esversion", 7));

		th.newTest("Allowed in ES8");
		th.test(code, new LinterOptions().set("esversion", 8));

		th.newTest();
		th.test(new String[] {
				"class C { async m() { await 0; } }"
		}, new LinterOptions().set("esversion", 8));

		th.newTest();
		th.addError(3, 9, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 14, "Missing semicolon.");
		th.addError(3, 15, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"class C {",
				"  async m() { await 0; }",
				"  n() { await 0; }",
				"}"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 23, "Unexpected 'await'.");
		th.test("class C { async m(x = await 0) {} }", new LinterOptions().set("esversion", 8));

		th.newTest("Illegal line break");
		th.addError(2, 3, "Line breaking error 'async'.");
		th.test(new String[] {
				"class C {",
				"  async",
				"  m() {}",
				"}"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("Illegal constructor");
		th.addError(1, 17, "Unexpected 'constructor'.");
		th.test("class C { async constructor() {} }", new LinterOptions().set("esversion", 8));
	}

	@Test(groups = { "asyncGenerators" })
	public void testAsyncGeneratorsExpression() {
		th.newTest("Statement position");
		th.addError(1, 18, "Missing name in function declaration.");
		th.test("async function * () { await 0; yield 0; }", new LinterOptions().set("esversion", 9));

		th.newTest("Expression position (disallowed prior to ES9)");
		th.addError(1, 6, "'async generators' is only available in ES9 (use 'esversion: 9').");
		th.test("void async function * () { await 0; yield 0; };", new LinterOptions().set("esversion", 8));

		th.newTest("Expression position");
		th.test("void async function * () { await 0; yield 0; };", new LinterOptions().set("esversion", 9));

		th.newTest("YieldExpression in parameter list");
		th.addError(1, 28, "Unexpected 'yield'.");
		th.test("void async function * (x = yield) { await 0; yield 0; };", new LinterOptions().set("esversion", 9));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 28, "Unexpected 'await'.");
		th.test("void async function * (x = await 0) { await 0; yield 0; };", new LinterOptions().set("esversion", 9));
	}

	@Test(groups = { "asyncGenerators" })
	public void testAsyncGeneratorsDeclaration() {
		th.newTest();
		th.addError(1, 1, "'async generators' is only available in ES9 (use 'esversion: 9').");
		th.test("async function * f() { await 0; yield 0; }", new LinterOptions().set("esversion", 8));

		th.newTest();
		th.test("async function * f() { await 0; yield 0; }", new LinterOptions().set("esversion", 9));

		th.newTest();
		th.addError(1, 43, "Unnecessary semicolon.");
		th.test("async function * f() { await 0; yield 0; };", new LinterOptions().set("esversion", 9));

		th.newTest("YieldExpression in parameter list");
		th.addError(1, 24, "Unexpected 'yield'.");
		th.test("async function * f(x = yield) { await 0; yield 0; }", new LinterOptions().set("esversion", 9));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 24, "Unexpected 'await'.");
		th.test("async function * f(x = await 0) { await 0; yield 0; }", new LinterOptions().set("esversion", 9));
	}

	@Test(groups = { "asyncGenerators" })
	public void testAsyncGeneratorsMethod() {
		th.newTest();
		th.addError(1, 14, "'async generators' is only available in ES9 (use 'esversion: 9').");
		th.test("void { async * m() { await 0; yield 0; } };", new LinterOptions().set("esversion", 8));

		th.newTest();
		th.test("void { async * m() { await 0; yield 0; } };", new LinterOptions().set("esversion", 9));

		th.newTest("YieldExpression in parameter list");
		th.addError(1, 22, "Unexpected 'yield'.");
		th.test("void { async * m(x = yield) { await 0; yield 0; } };", new LinterOptions().set("esversion", 9));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 22, "Unexpected 'await'.");
		th.test("void { async * m(x = await 0) { await 0; yield 0; } };", new LinterOptions().set("esversion", 9));
	}

	@Test(groups = { "asyncGenerators" })
	public void testAsyncGeneratorsClassMethod() {
		th.newTest();
		th.addError(1, 19, "'async generators' is only available in ES9 (use 'esversion: 9').");
		th.test("class C { async * m() { await 0; yield 0; } }", new LinterOptions().set("esversion", 8));

		th.newTest();
		th.test("class C { async * m() { await 0; yield 0; } }", new LinterOptions().set("esversion", 9));

		th.newTest("YieldExpression in parameter list");
		th.addError(1, 25, "Unexpected 'yield'.");
		th.test("class C { async * m(x = yield) { await 0; yield 0; } }", new LinterOptions().set("esversion", 9));

		th.newTest("AwaitExpression in parameter list");
		th.addError(1, 25, "Unexpected 'await'.");
		th.test("class C { async * m(x = await 0) { await 0; yield 0; } }", new LinterOptions().set("esversion", 9));

		th.newTest("Illegal constructor");
		th.addError(1, 19, "Unexpected 'constructor'.");
		th.addError(1, 44, "Expected an identifier and instead saw 'yield' (a reserved word).");
		th.addError(1, 44, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 49, "Missing semicolon.");
		th.addError(1, 50, "Expected an assignment or function call and instead saw an expression.");
		th.test("class C { async * constructor() { await 0; yield 0; } }", new LinterOptions().set("esversion", 9));
	}

	@Test
	public void testAsyncIteration() {
		th.newTest("unavailability in prior editions");
		th.addError(2, 7, "'asynchronous iteration' is only available in ES9 (use 'esversion: 9').");
		th.addError(3, 7, "'asynchronous iteration' is only available in ES9 (use 'esversion: 9').");
		th.addError(4, 7, "'asynchronous iteration' is only available in ES9 (use 'esversion: 9').");
		th.addError(5, 7, "'asynchronous iteration' is only available in ES9 (use 'esversion: 9').");
		th.test(new String[] {
				"async function f() {",
				"  for await (var x of []) {}",
				"  for await (let x of []) {}",
				"  for await (const x of []) {}",
				"  for await (x of []) {}",
				"}"
		}, new LinterOptions().set("esversion", 8));

		th.newTest("typical usage");
		th.test(new String[] {
				"async function f() {",
				"  for await (var x of []) {}",
				"  for await (let x of []) {}",
				"  for await (const x of []) {}",
				"  for await (x of []) {}",
				"}"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("unavailability in synchronous contexts");
		th.addError(2, 7, "Unexpected 'await'.");
		th.test(new String[] {
				"function f() {",
				"  for await (var x of []) {}",
				"}"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("unavailability with for-in statements");
		th.addError(2, 20, "Asynchronous iteration is only available with for-of loops.");
		th.test(new String[] {
				"async function f() {",
				"  for await (var x in []) {}",
				"}"
		}, new LinterOptions().set("esversion", 9));

		th.newTest("unavailability with C-style for statements");
		th.addError(2, 20, "Asynchronous iteration is only available with for-of loops.");
		th.test(new String[] {
				"async function f() {",
				"  for await (var x ; ;) {}",
				"}"
		}, new LinterOptions().set("esversion", 9));
	}

	@Test
	public void testParensAfterDeclaration() {
		th.addError(1, 17, "Function declarations are not invocable. Wrap the whole function invocation in parens.");
		th.addError(1, 18, "Expected an assignment or function call and instead saw an expression.");
		th.test("function f () {}();");

		th.newTest();
		th.addError(1, 19, "Expected an assignment or function call and instead saw an expression.");
		th.test("function f () {}(0);");

		th.newTest();
		th.addError(1, 24, "Expected an assignment or function call and instead saw an expression.");
		th.test("function f () {}() => {};", new LinterOptions().set("esversion", 6));
	}

	@Test
	public void testImportMeta() {
		th.addError(1, 6, "Expected an identifier and instead saw 'import' (a reserved word).");
		th.test("void import;");

		th.newTest();
		th.addError(1, 6, "Expected an identifier and instead saw 'import' (a reserved word).");
		th.test("void import;", new LinterOptions().set("esversion", 11));

		th.newTest();
		th.addError(1, 12, "'import.meta' is only available in ES11 (use 'esversion: 11').");
		th.addError(1, 12, "import.meta may only be used in module code.");
		th.test("void import.meta;", new LinterOptions().set("esversion", 10));

		th.newTest();
		th.addError(1, 12, "import.meta may only be used in module code.");
		th.test("void import.meta;", new LinterOptions().set("esversion", 11));

		th.newTest("valid usage (expression position)");
		th.test("void import.meta;", new LinterOptions().set("esversion", 11).set("module", true));

		th.newTest("valid usage (statement position)");
		th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
		th.test("import.meta;", new LinterOptions().set("esversion", 11).set("module", true));

		th.newTest("Other property name (expression position)");
		th.addError(1, 12, "Invalid meta property: 'import.target'.");
		th.test("void import.target;", new LinterOptions().set("esversion", 11).set("module", true));

		th.newTest("Other property name (statement position)");
		th.addError(1, 7, "Invalid meta property: 'import.target'.");
		th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
		th.test("import.target;", new LinterOptions().set("esversion", 11).set("module", true));
	}

	@Test(groups = { "nullishCoalescing" })
	public void testNullishCoalescingPositive() {
		th.newTest("requires esversion: 11");
		th.addError(1, 3, "'nullish coalescing' is only available in ES11 (use 'esversion: 11').");
		th.test(new String[] {
				"0 ?? 0;"
		}, new LinterOptions().set("esversion", 10).set("expr", true));

		th.newTest("does not stand alone");
		th.addError(1, 6, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"0 ?? 0;"
		}, new LinterOptions().set("esversion", 11));

		th.newTest("precedence with bitwise OR");
		th.test(new String[] {
				"0 | 0 ?? 0;"
		}, new LinterOptions().set("esversion", 11).set("expr", true));

		th.newTest("precedence with conditional expression");
		th.test(new String[] {
				"0 ?? 0 ? 0 ?? 0 : 0 ?? 0;"
		}, new LinterOptions().set("esversion", 11).set("expr", true));

		th.newTest("precedence with expression");
		th.test(new String[] {
				"0 ?? 0, 0 ?? 0;"
		}, new LinterOptions().set("esversion", 11).set("expr", true));

		th.newTest("covered");
		th.test(new String[] {
				"0 || (0 ?? 0);",
				"(0 || 0) ?? 0;",
				"(0 ?? 0) || 0;",
				"0 ?? (0 || 0);",
				"0 && (0 ?? 0);",
				"(0 && 0) ?? 0;",
				"(0 ?? 0) && 0;",
				"0 ?? (0 && 0);"
		}, new LinterOptions().set("esversion", 11).set("expr", true));
	}

	@Test(groups = { "nullishCoalescing" })
	public void testNullishCoalescingNegative() {
		th.newTest("precedence with logical OR");
		th.addError(1, 8, "Unexpected '??'.");
		th.test(new String[] {
				"0 || 0 ?? 0;"
		}, new LinterOptions().set("esversion", 11).set("expr", true));

		th.newTest("precedence with logical OR");
		th.addError(1, 8, "Unexpected '||'.");
		th.test(new String[] {
				"0 ?? 0 || 0;"
		}, new LinterOptions().set("esversion", 11).set("expr", true));

		th.newTest("precedence with logical AND");
		th.addError(1, 8, "Unexpected '??'.");
		th.test(new String[] {
				"0 && 0 ?? 0;"
		}, new LinterOptions().set("esversion", 11).set("expr", true));

		th.newTest("precedence with logical AND");
		th.addError(1, 8, "Unexpected '&&'.");
		th.test(new String[] {
				"0 ?? 0 && 0;"
		}, new LinterOptions().set("esversion", 11).set("expr", true));
	}

	@Test
	public void testOptionalChaining() {
		th.newTest("prior language editions");
		th.addError(1, 5, "'Optional chaining' is only available in ES11 (use 'esversion: 11').");
		th.addError(1, 7, "Expected an assignment or function call and instead saw an expression.");
		th.test("true?.x;", new LinterOptions().set("esversion", 10));

		th.newTest("literal property name");
		th.addError(1, 7, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 7, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"true?.x;",
				"[]?.x;",
				"({}?.x);"
		}, new LinterOptions().set("esversion", 11));

		th.newTest("literal property name restriction");
		th.addError(1, 40, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 46, "Strict violation.");
		th.test("(function() { 'use strict'; arguments?.callee; })();", new LinterOptions().set("esversion", 11));

		th.newTest("dynamic property name");
		th.addError(1, 14, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 11, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 7, "['x'] is better written in dot notation.");
		th.test(new String[] {
				"true?.[void 0];",
				"true?.['x'];"
		}, new LinterOptions().set("esversion", 11));

		th.newTest("arguments");
		th.test(new String[] {
				"true.x?.();",
				"true.x?.(true);",
				"true.x?.(true, true);",
				"true.x?.(...[]);"
		}, new LinterOptions().set("esversion", 11));

		th.newTest("new");
		th.addError(1, 7, "Unexpected '?.'.");
		th.test("new {}?.constructor();", new LinterOptions().set("esversion", 11));

		th.newTest("template invocation - literal property name");
		th.addError(1, 15, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 15, "Unexpected '`'.");
		th.test("true?.toString``;", new LinterOptions().set("esversion", 11));

		th.newTest("template invocation - dynamic property name");
		th.addError(1, 15, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 15, "Unexpected '`'.");
		th.test("true?.[void 0]``;", new LinterOptions().set("esversion", 11));

		th.newTest("ternary");
		th.addError(1, 8, "A leading decimal point can be confused with a dot: '.1'.");
		th.addError(1, 11, "Expected an assignment or function call and instead saw an expression.");
		th.test("true?.1 : null;", new LinterOptions().set("esversion", 11));

		th.newTest("CallExpression");
		th.test("true?.false();", new LinterOptions().set("esversion", 11));
	}

	// gh-3556: "Crash parsing: throw new"
	@Test
	public void testLoneNew() {
		th.newTest("as reported");
		th.addError(4, 3, "Expected an identifier and instead saw '}'.");
		th.addError(4, 4, "Missing semicolon.");
		th.addError(1, 21, "Unmatched '{'.");
		th.addError(5, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[] {
				"function code(data) {",
				"  if (data.request === 'foo') {",
				"    throw new ",
				"  }",
				"}"
		});

		th.newTest("simplified");
		th.addError(1, 4, "Expected an identifier and instead saw ';'.");
		th.addError(1, 5, "Missing semicolon.");
		th.test("new;");
	}

	// gh-3560: "Logical nullish assignment (??=) throwing error"
	@Test
	public void testLoneNullishCoalescing() {
		th.newTest("as reported");
		th.addError(2, 8, "Expected an identifier and instead saw '='.");
		th.addError(2, 10, "Unexpected '(number)'.");
		th.addError(2, 8, "Expected an assignment or function call and instead saw an expression.");
		th.addError(2, 9, "Missing semicolon.");
		th.addError(2, 10, "Expected an assignment or function call and instead saw an expression.");
		th.test(new String[] {
				"let a = [1,2];",
				"a[0] ??= 0;"
		}, new LinterOptions().set("esversion", 11));

		th.newTest("simplified");
		th.addError(1, 4, "Expected an identifier and instead saw ';'.");
		th.addError(1, 4, "Unexpected '(end)'.");
		th.addError(1, 4, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 5, "Missing semicolon.");
		th.test("0??;", new LinterOptions().set("esversion", 11));
	}

	// gh-3571: "Cannot read properties of undefined at Object.e.nud"
	@Test
	public void testKeywordAsShorthandObjectProperty() {
		th.newTest("as reported");
		th.addError(1, 12, "Expected an identifier and instead saw 'do'.");
		th.addError(1, 15, "Missing semicolon.");
		th.test("const a = {do}", new LinterOptions().set("esversion", 6));

		th.newTest("simplified");
		th.addError(1, 7, "Expected an identifier and instead saw 'do'.");
		th.test("void {do};", new LinterOptions().set("esversion", 6));

		th.newTest("alternate - penultimate member");
		th.addError(1, 7, "Expected an identifier and instead saw 'for'.");
		th.test("void {for, baz};", new LinterOptions().set("esversion", 6));
	}
}