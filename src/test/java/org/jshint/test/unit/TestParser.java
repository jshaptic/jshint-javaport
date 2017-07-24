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
public class TestParser extends Assert
{
	private TestHelper th = new TestHelper();
	
	@BeforeMethod
	public void setup()
	{
		th.reset();
	}
	
	@Test
	public void testUnsafe()
	{
		String[] code = {
			"var a\n = 'Here is a unsafe character';"
		};
		
		th.addError(1, "This character may get silently deleted by one or more browsers.");
		th.test(code, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testOther()
	{
		String[] code = {
			"\\",
			"!"
		};
		
		th.addError(1, "Unexpected '\\'.");
	    th.addError(2, "Unexpected early end of program.");
	    th.addError(2, "Unrecoverable syntax error. (100% scanned).");
		th.test(code, new LinterOptions().set("es3", true));
		
		// GH-818
		th.reset();
		th.addError(1, "Expected an identifier and instead saw ')'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.test("if (product < ) {}", new LinterOptions().set("es3", true));
	    
	    // GH-2506
	    th.reset();
	    th.addError(1, "Expected an identifier and instead saw ';'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.test("typeof;");
	    
	    th.reset();
	    th.addError(1, "Unrecoverable syntax error. (0% scanned).");
	    th.test("}");
	}
	
	@Test
	public void testConfusingOps()
	{
		String[] code = {
			"var a = 3 - -3;",
		    "var b = 3 + +3;",
		    "a = a - --a;",
		    "a = b + ++b;",
		    "a = a-- - 3;", // this is not confusing?!
		    "a = a++ + 3;"  // this is not confusing?!
		};
		
		th.addError(1, "Confusing minuses.");
	    th.addError(2, "Confusing plusses.");
	    th.addError(3, "Confusing minuses.");
	    th.addError(4, "Confusing plusses.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testDivision()
	{
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
	public void testPlusplus()
	{
		String[] code = {
			"var a = ++[2];",
		    "var b = --(2);"
		};
		
		th.addError(1, "Unexpected use of '++'.");
	    th.addError(2, "Unexpected use of '--'.");
		th.test(code, new LinterOptions().set("plusplus", true).set("es3", true));
		th.test(code, new LinterOptions().set("plusplus", true)); // es5
		th.test(code, new LinterOptions().set("plusplus", true).set("esnext", true));
		th.test(code, new LinterOptions().set("plusplus", true).set("moz", true));
		
		th.reset();
		th.addError(2, "Bad operand.");
		th.test(code, new LinterOptions().set("plusplus", false).set("es3", true));
		th.test(code, new LinterOptions().set("plusplus", false)); // es5
		th.test(code, new LinterOptions().set("plusplus", false).set("esnext", true));
		th.test(code, new LinterOptions().set("plusplus", false).set("moz", true));
	}
	
	@Test
	public void testAssignment()
	{
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
		
		th.addError(2, "Bad assignment.");
	    th.addError(3, "Bad assignment.");
	    th.addError(4, "Bad assignment.");
	    th.addError(5, "Bad assignment.");
	    th.addError(14, "Bad assignment.");
		th.test(code, new LinterOptions().set("plusplus", true).set("es3", true));
		th.test(code, new LinterOptions().set("plusplus", true)); // es5
		th.test(code, new LinterOptions().set("plusplus", true).set("esnext", true));
		th.test(code, new LinterOptions().set("plusplus", true).set("moz", true));
	}
	
	@Test
	public void testRelations()
	{
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
		
		th.addError(1, "Use the isNaN function to compare with NaN.");
	    th.addError(2, "Use the isNaN function to compare with NaN.");
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
	}
	
	@Test
	public void testOptions()
	{
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
		
		th.addError(3, "Unexpected /*member 'c'.");
	    th.addError(4, "Bad option: '++'.");
	    th.addError(5, "Expected a small integer or 'false' and instead saw '0'.");
	    th.addError(6, "Expected a small integer or 'false' and instead saw '-2'.");
	    th.addError(7, "Expected a small integer or 'false' and instead saw '100.4'.");
	    th.addError(8, "Expected a small integer or 'false' and instead saw '200.4'.");
	    th.addError(9, "Expected a small integer or 'false' and instead saw '300.4'.");
	    th.addError(10, "Expected a small integer or 'false' and instead saw '0'.");
	    th.addError(13, "Bad option: 'd'.");
	    th.addError(15, "Read only.");
	    th.addError(16, "Bad option: 'relaxing'.");
	    th.addError(17, "Bad option: 'toString'.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
		
		th.reset();
		th.test(th.readFile("src/test/resources/fixtures/gh988.js"));
	}
	
	@Test
	public void testEmptyDirectives()
	{
	    th.addError(1, "Bad option value.");
	    th.test("/* global */");
	    th.test("/* global : */");
	    th.test("/* global -: */");
	    
	    th.reset();
	    th.test("/* global foo, bar, baz, */");
	    
	    th.addError(1, "Bad option value.");
	    th.test("/* globals */");
	    th.test("/* globals : */");
	    th.test("/* globals -: */");
	    
	    th.reset();
	    th.test("/* globals foo, bar, baz, */");

	    th.addError(1, "Bad option value.");
	    th.test("/* exported */");

	    th.reset();
	    th.test("/* exported foo, bar, baz, */");
	}
	
	@Test
	public void testJshintOptionCommentsSingleLine()
	{
		String src = th.readFile("src/test/resources/fixtures/gh1768-1.js");
		
		th.test(src);
	}
	
	@Test
	public void testJshintOptionCommentsSingleLineLeadingAndTrailingSpace()
	{
		String src = th.readFile("src/test/resources/fixtures/gh1768-2.js");
		
		th.test(src);
	}
	
	@Test
	public void testJshintOptionCommentsMultiLine()
	{
		String src = th.readFile("src/test/resources/fixtures/gh1768-3.js");
		
		th.test(src);
	}
	
	@Test
	public void testJshintOptionCommentsMultiLineLeadingAndTrailingSpace()
	{
		String src = th.readFile("src/test/resources/fixtures/gh1768-4.js");
		
		th.addError(4, "'foo' is not defined.");
		th.test(src);
	}
	
	@Test
	public void testJshintOptionCommentsMultiLineOption()
	{
		String src = th.readFile("src/test/resources/fixtures/gh1768-5.js");
		
		th.addError(3, "'foo' is not defined.");
		th.test(src);
	}
	
	@Test
	public void testJshintOptionCommentsMultiLineOptionLeadingAndTrailingSpace()
	{
		String src = th.readFile("src/test/resources/fixtures/gh1768-6.js");
		
		th.addError(4, "'foo' is not defined.");
		th.test(src);
	}
	
	@Test
	public void testShebang()
	{
		String[] code = {
			"#!test",
		    "var a = 'xxx';",
		    "#!test"
		};
		
		th.addError(3, "Expected an identifier and instead saw '#'.");
	    th.addError(3, "Expected an operator and instead saw '!'.");
	    th.addError(3, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(3, "Missing semicolon.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testShebangImpliesNode()
	{
		String[] code = {
			"#!usr/bin/env node",
		    "require('module');"
		};
		
		th.test(code);
	}
	
	@Test
	public void testNumbers()
	{
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
		
		th.addError(2, "Bad number '10e308'.");
	    th.addError(5, "A leading decimal point can be confused with a dot: '.3'.");
	    th.addError(6, "Unexpected '0'.");
	    th.addError(7, "Expected an identifier and instead saw 'var'.");
	    th.addError(7, "Missing semicolon.");
	    th.addError(7, "Don't use extra leading zeros '0033'.");
	    th.addError(8, "A trailing decimal point can be confused with a dot: '3.'.");
	    th.addError(9, "A dot following a number can be confused with a decimal point.");
	    th.addError(11, "'Octal integer literal' is only available in ES6 (use 'esversion: 6').");
	    th.addError(12, "'Binary integer literal' is only available in ES6 (use 'esversion: 6').");
	    th.addError(13, "Bad number '0x'.");
	    th.addError(15, "Unexpected '1'.");
	    th.addError(16, "Expected an identifier and instead saw ';'.");
	    th.addError(16, "Expected an identifier and instead saw 'var'.");
	    th.addError(16, "Missing semicolon.");
	    th.addError(17, "Unexpected '1'.");
	    th.addError(17, "Unexpected early end of program.");
	    th.addError(17, "Unrecoverable syntax error. (100% scanned).");
		th.test(code, new LinterOptions().set("es3", true));
		
		// Octals are prohibited in strict mode.
				
		th.reset();
		th.addError(3, "Octal literals are not allowed in strict mode.");
		th.test(new String[]{
			"(function () {",
		    "'use strict';",
		    "return 045;",
		    "}());"
		});
		
		// GitHub #751 - an expression containing a number with a leading decimal point should be parsed in its entirety
		th.reset();
		th.addError(1, "A leading decimal point can be confused with a dot: '.3'.");
	    th.addError(2, "A leading decimal point can be confused with a dot: '.3'.");
	    th.test(new String[]{
    		"var a = .3 + 1;",
    	    "var b = 1 + .3;"
		});
	    
	    th.reset();
	    th.addError(5, "Missing semicolon.");
	    th.addError(5, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(6, "Missing semicolon.");
	    th.addError(6, "Expected an assignment or function call and instead saw an expression.");
	    th.test(new String[]{
    		"var a = 0o1234567;",
    	    "var b = 0O1234567;",
    	    "var c = 0b101;",
    	    "var d = 0B101;",
    	    "var e = 0o12345678;",
    	    "var f = 0b1012;"
		}, new LinterOptions().set("esnext", true));
	    
	    th.reset();
	    th.test(new String[] {
	    	"// jshint esnext: true",
	        "var a = 0b0 + 0o0;"
	    });
	}
	
	@Test
	public void testComments()
	{
		String[] code = {
			"/*",
		    "/* nested */",
		    "*/",
		    "/* unclosed .."
		};
		
		th.addError(3, "Unbegun comment.");
	    th.addError(4, "Unclosed comment.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
		
		String src = "/* this is a comment /* with nested slash-start */";
		th.reset();
		th.test(src);
		th.test(th.readFile("src/test/resources/fixtures/gruntComment.js"));
		
		th.reset();
		th.addError(1, "Unmatched '{'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.test("({");
	}
	
	@Test
	public void testRegexp()
	{
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
		    "var v = /dsdg;"
		};
		
		th.addError(1, "This character may get silently deleted by one or more browsers.");
	    th.addError(1, "Unexpected control character in regular expression.");
	    th.addError(2, "This character may get silently deleted by one or more browsers.");
	    th.addError(2, "Unexpected control character in regular expression.");
	    th.addError(3, "Unexpected escaped character '<' in regular expression.");
	    th.addError(4, "Unexpected escaped character '<' in regular expression.");
	    th.addError(5, "Invalid regular expression.");
	    th.addError(6, "Invalid regular expression.");
	    th.addError(11, "Invalid regular expression.");
	    th.addError(12, "Invalid regular expression.");
	    th.addError(14, "Invalid regular expression.");
	    th.addError(15, "Invalid regular expression.");
	    th.addError(17, "Invalid regular expression.");
	    th.addError(20, "Invalid regular expression.");
	    th.addError(21, "Invalid regular expression.");
	    th.addError(28, "Unclosed regular expression.");
	    th.addError(28, "Unrecoverable syntax error. (100% scanned).");
	    
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
		
		th.reset();
		th.test("var a = `${/./}${/./}`;", new LinterOptions().set("esversion", 6));
		
		// Pre Regular Expression Punctuation
		//  (See: token method, create function in lex.js)
		//
		// "."
		th.reset();
		th.addError(1, "A trailing decimal point can be confused with a dot: '10.'.");
		th.test("var y = 10. / 1;", new LinterOptions().set("es3", true));
		th.test("var y = 10. / 1;", new LinterOptions()); // es5
		th.test("var y = 10. / 1;", new LinterOptions().set("esnext", true));
		th.test("var y = 10. / 1;", new LinterOptions().set("moz", true));
		
		// ")"
		th.reset();
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions().set("es3", true));
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions()); // es5
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions().set("esnext", true));
		th.test("var y = Math.sqrt(16) / 180;", new LinterOptions().set("moz", true));
		
		// "~"
		//JSHINT_BUG: duplicate code, tilde must be tested
		th.reset();
		th.test("var y = ~16 / 180;", new LinterOptions().set("es3", true));
		th.test("var y = ~16 / 180;", new LinterOptions()); // es5
		th.test("var y = ~16 / 180;", new LinterOptions().set("esnext", true));
		th.test("var y = ~16 / 180;", new LinterOptions().set("moz", true));
		
		// "]" (GH-803)
		th.reset();
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions().set("es3", true));
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions()); // es5
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions().set("esnext", true));
		th.test("var x = [1]; var y = x[0] / 180;", new LinterOptions().set("moz", true));
		
		// "++" (GH-1787)
		th.reset();
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions().set("es3", true));
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions()); // es5
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions().set("esnext", true));
		th.test("var a = 1; var b = a++ / 10;", new LinterOptions().set("moz", true));
		
		// "--" (GH-1787)
		th.reset();
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions().set("es3", true));
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions()); // es5
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions().set("esnext", true));
		th.test("var a = 1; var b = a-- / 10;", new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testRegexRegressions()
	{
		// GH-536
		th.test("str /= 5;", new LinterOptions().set("es3", true), new LinterGlobals(true, "str"));
		th.test("str /= 5;", new LinterOptions(), new LinterGlobals(true, "str")); // es5
		th.test("str /= 5;", new LinterOptions().set("esnext", true), new LinterGlobals(true, "str"));
		th.test("str /= 5;", new LinterOptions().set("moz", true), new LinterGlobals(true, "str"));
		
		th.test("str = str.replace(/=/g, '');", new LinterOptions().set("es3", true), new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=/g, '');", new LinterOptions(), new LinterGlobals(true, "str")); // es5
		th.test("str = str.replace(/=/g, '');", new LinterOptions().set("esnext", true), new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=/g, '');", new LinterOptions().set("moz", true), new LinterGlobals(true, "str"));
		
		th.test("str = str.replace(/=abc/g, '');", new LinterOptions().set("es3", true), new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=abc/g, '');", new LinterOptions(), new LinterGlobals(true, "str")); // es5
		th.test("str = str.replace(/=abc/g, '');", new LinterOptions().set("esnext", true), new LinterGlobals(true, "str"));
		th.test("str = str.replace(/=abc/g, '');", new LinterOptions().set("moz", true), new LinterGlobals(true, "str"));
		
		// GH-538
		th.test("var exp = /function(.*){/gi;", new LinterOptions().set("es3", true));
		th.test("var exp = /function(.*){/gi;", new LinterOptions()); // es5
		th.test("var exp = /function(.*){/gi;", new LinterOptions().set("esnext", true));
		th.test("var exp = /function(.*){/gi;", new LinterOptions().set("moz", true));
		
		th.test("var exp = /\\[\\]/;", new LinterOptions().set("es3", true));
		th.test("var exp = /\\[\\]/;", new LinterOptions()); // es5
		th.test("var exp = /\\[\\]/;", new LinterOptions().set("esnext", true));
		th.test("var exp = /\\[\\]/;", new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testRegexpSticky()
	{
		th.addError(1, "'Sticky RegExp flag' is only available in ES6 (use 'esversion: 6').");
		th.test("var exp = /./y;", new LinterOptions().set("esversion", 5));
		
		th.reset();
		th.test("var exp = /./y;", new LinterOptions().set("esversion", 6));
		th.test("var exp = /./gy;", new LinterOptions().set("esversion", 6));
		th.test("var exp = /./yg;", new LinterOptions().set("esversion", 6));
		
		th.addError(1, "Invalid regular expression.");
		th.addError(2, "Invalid regular expression.");
		th.test(new String[] {
			"var exp = /./yy;",
		    "var exp = /./ygy;"
		}, new LinterOptions().set("esversion", 6));
		
		th.test(new String[] {
			"var exp = /./gyg;",
		    "var exp = /?/y;"
		}, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testStrings()
	{
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
	    th.addError(1, "This character may get silently deleted by one or more browsers.");
	    th.addError(7, "Unclosed string.");
	    th.addError(7, "Missing semicolon.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testBadStrings()
	{
		String[] code = {
			"var a = '\\uNOTHEX';"
		};
		
		th.addError(1, "Unexpected 'uNOTH'.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testOwnProperty()
	{
		String[] code = {
			"var obj = { hasOwnProperty: false };",
		    "obj.hasOwnProperty = true;",
		    "obj['hasOwnProperty'] = true;",
		    "function test() { var hasOwnProperty = {}.hasOwnProperty; }"
		};
		
		th.addError(1, "'hasOwnProperty' is a really bad name.");
	    th.addError(2, "'hasOwnProperty' is a really bad name.");
	    th.addError(3, "'hasOwnProperty' is a really bad name.");
	    th.addError(3, "['hasOwnProperty'] is better written in dot notation.");
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code, new LinterOptions()); // es5
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}
	
	@Test(groups = {"json"})
	public void testJsonDflt()
	{
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
		
		th.addError(2, "Expected a string and instead saw a.");
	    th.addError(3, "Strings must use doublequote.");
	    th.addError(3, "Avoid \\v.");
	    th.addError(3, "Avoid \\x-.");
	    th.addError(3, "Avoid \\'.");
	    th.addError(4, "Avoid \\v.");
	    th.addError(4, "Avoid \\x-.");
	    th.addError(4, "Strings must use doublequote.");
	    th.addError(5, "Avoid EOL escaping.");
	    th.addError(7, "Avoid 0x-.");
		th.test(code, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(code, new LinterOptions().set("multistr", true)); // es5
		th.test(code, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(code, new LinterOptions().set("multistr", true).set("moz", true));
	}
	
	@Test(groups = {"json"})
	public void testJsonDeep()
	{
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
	
	@Test(groups = {"json"})
	public void testJsonBad()
	{
		String[] objTrailingComma = {
			"{ \"key\" : \"value\", }"
		};
		
		th.addError(1, "Unexpected comma.");
		th.test(objTrailingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objTrailingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(objTrailingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objTrailingComma, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] arrayTrailingComma = {
			"{ \"key\" : [,] }"
		};
		
		th.reset();
		th.addError(1, "Illegal comma.");
		th.addError(1, "Expected a JSON value.");
		th.addError(1, "Unexpected comma.");
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayTrailingComma, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] objMissingComma = {
			"{ \"k1\":\"v1\" \"k2\":\"v2\" }"
		};
		
		th.reset();
		th.addError(1, "Expected '}' and instead saw 'k2'.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(objMissingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objMissingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(objMissingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objMissingComma, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] arrayMissingComma = {
			"[ \"v1\" \"v2\" ]"
		};
		
		th.reset();
		th.addError(1, "Expected ']' and instead saw 'v2'.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayMissingComma, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] objDoubleComma = {
			"{ \"k1\":\"v1\",, \"k2\":\"v2\" }"
		};
		
		th.reset();
		th.addError(1, "Illegal comma.");
		th.addError(1, "Expected ':' and instead saw 'k2'.");
		th.addError(1, "Expected a JSON value.");
		th.addError(1, "Expected '}' and instead saw ':'.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(objDoubleComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objDoubleComma, new LinterOptions().set("multistr", true)); // es5
		th.test(objDoubleComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objDoubleComma, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] arrayDoubleComma = {
			"[ \"v1\",, \"v2\" ]"
		};
		
		th.reset();
		th.addError(1, "Illegal comma.");
		th.addError(1, "Expected a JSON value.");
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayDoubleComma, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] objUnclosed = {
			"{ \"k1\":\"v1\""
		};
		
		th.reset();
		th.addError(1, "Expected '}' and instead saw ''.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(objUnclosed, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objUnclosed, new LinterOptions().set("multistr", true)); // es5
		th.test(objUnclosed, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objUnclosed, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] arrayUnclosed = {
			"[ \"v1\""
		};
		
		th.reset();
		th.addError(1, "Expected ']' and instead saw ''.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayUnclosed, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] objUnclosed2 = {
			"{"
		};
		
		th.reset();
		th.addError(1, "Missing '}' to match '{' from line 1.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(objUnclosed2, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objUnclosed2, new LinterOptions().set("multistr", true)); // es5
		th.test(objUnclosed2, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objUnclosed2, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] arrayUnclosed2 = {
			"["
		};
		
		th.reset();
		th.addError(1, "Missing ']' to match '[' from line 1.");
		th.addError(1, "Expected a JSON value.");
		th.addError(1, "Expected ']' and instead saw ''.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true)); // es5
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(arrayUnclosed2, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] objDupKeys = {
			"{ \"k1\":\"v1\", \"k1\":\"v1\" }"
		};
		
		th.reset();
		th.addError(1, "Duplicate key 'k1'.");
		th.test(objDupKeys, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objDupKeys, new LinterOptions().set("multistr", true)); // es5
		th.test(objDupKeys, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objDupKeys, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] objBadKey = {
			"{ k1:\"v1\" }"
		};
		
		th.reset();
		th.addError(1, "Expected a string and instead saw k1.");
		th.test(objBadKey, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objBadKey, new LinterOptions().set("multistr", true)); // es5
		th.test(objBadKey, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objBadKey, new LinterOptions().set("multistr", true).set("moz", true));
		
		String[] objBadValue = {
			"{ \"noRegexpInJSON\": /$^/ }"
		};
		
		th.reset();
		th.addError(1, "Expected a JSON value.");
		th.addError(1, "Expected '}' and instead saw '/$^/'.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(objBadValue, new LinterOptions().set("multistr", true).set("es3", true));
		th.test(objBadValue, new LinterOptions().set("multistr", true)); // es5
		th.test(objBadValue, new LinterOptions().set("multistr", true).set("esnext", true));
		th.test(objBadValue, new LinterOptions().set("multistr", true).set("moz", true));
	}
	
	// Regression test for gh-2488
	@Test(groups = {"json"})
	public void testJsonSemicolon()
	{
		th.test("{ \"attr\": \";\" }");
		
		th.test("[\";\"]");
	}
	
	@Test
	public void testComma()
	{
		String src = th.readFile("src/test/resources/fixtures/comma.js");
		
		th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(15, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(15, "Missing semicolon.");
	    th.addError(20, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(30, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(35, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(35, "Missing semicolon.");
	    th.addError(36, "Unexpected 'if'.");
	    th.addError(43, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(43, "Missing semicolon.");
	    th.addError(44, "Unexpected '}'.");
		th.test(src, new LinterOptions().set("es3", true));
		
		// Regression test (GH-56)
		th.reset();
		th.addError(4, "Expected an assignment or function call and instead saw an expression.");
		th.test(th.readFile("src/test/resources/fixtures/gh56.js"));
		
		// Regression test (GH-363)
		th.reset();
		th.addError(1, "Extra comma. (it breaks older versions of IE)");
		th.test("var f = [1,];", new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testGH2587()
	{
		th.addError(1, "Expected an identifier and instead saw 'if'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.addError(1, "Expected '===' and instead saw '=='.");
	    th.test(new String[] {
	    	"true == if"
	    }, new LinterOptions().set("eqeqeq", true).set("eqnull", true));
	    
	    th.reset();
	    th.addError(1, "Expected an identifier and instead saw 'if'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.addError(1, "Expected '!==' and instead saw '!='.");
	    th.test(new String[] {
	    	"true != if"
	    }, new LinterOptions().set("eqeqeq", true).set("eqnull", true));
	    
	    th.reset();
	    th.addError(1, "Expected an identifier and instead saw 'if'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.addError(1, "Use '===' to compare with 'true'.");
	    th.test(new String[] {
	    	"true == if"
	    }, new LinterOptions());
	    
	    th.reset();
	    th.addError(1, "Expected an identifier and instead saw 'if'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.addError(1, "Use '!==' to compare with 'true'.");
	    th.test(new String[] {
	    	"true != if"
	    }, new LinterOptions());
	    
	    th.reset();
	    th.addError(1, "Expected an identifier and instead saw 'if'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.test(new String[] {
	    	"true === if"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"true !== if"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"true > if"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"true < if"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"true >= if"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"true <= if"
	    }, new LinterOptions());
	}
	
	@Test
	public void testBadAssignments()
	{
		th.addError(1, "Bad assignment.");
	    th.test(new String[] {
	    	"a() = 1;"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"a.a() = 1;"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"(function(){}) = 1;"
	    }, new LinterOptions());
	    th.test(new String[] {
	    	"a.a() &= 1;"
	    }, new LinterOptions());
	}
	
	@Test
	public void testWithStatement()
	{
		String src = th.readFile("src/test/resources/fixtures/with.js");
		
		th.addError(5, "Don't use 'with'.");
	    th.addError(13, "'with' is not allowed in strict mode.");
	    th.test(src, new LinterOptions().set("es3", true));
		th.test(src); // es5
		th.test(src, new LinterOptions().set("esnext", true));
		th.test(src, new LinterOptions().set("moz", true));
		
		th.reset();
		th.addError(13, "'with' is not allowed in strict mode.");
	    th.test(src, new LinterOptions().set("withstmt", true).set("es3", true));
		th.test(src, new LinterOptions().set("withstmt", true)); // es5
		th.test(src, new LinterOptions().set("withstmt", true).set("esnext", true));
		th.test(src, new LinterOptions().set("withstmt", true).set("moz", true));
	}
	
	@Test
	public void testBlocks()
	{
		String src = th.readFile("src/test/resources/fixtures/blocks.js");
		
		th.addError(31, "Unmatched \'{\'.");
	    th.addError(32, "Unrecoverable syntax error. (100% scanned).");
	    th.test(src, new LinterOptions().set("es3", true));
		th.test(src, new LinterOptions()); // es5
		th.test(src, new LinterOptions().set("esnext", true));
		th.test(src, new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testFunctionCharacterLocation()
	{
		String src = th.readFile("src/test/resources/fixtures/nestedFunctions.js");
		UniversalContainer locations = th.getNestedFunctionsLocations();
		
		JSHint jshint = new JSHint();
		jshint.lint(src);
		List<DataSummary.Function> report = jshint.generateSummary().getFunctions();
		
		assertTrue(locations.getLength() == report.size());
		for (int i = 0; i < locations.getLength(0); i++)
		{
			assertEquals(locations.get(i).asString("name"), report.get(i).getName());
			assertEquals(locations.get(i).asInt("line"), report.get(i).getLine());
			assertEquals(locations.get(i).asInt("character"), report.get(i).getCharacter());
			assertEquals(locations.get(i).asInt("last"), report.get(i).getLast());
			assertEquals(locations.get(i).asInt("lastcharacter"), report.get(i).getLastCharacter());
		}
	}
	
	@Test
	public void testExported()
	{
		String src = th.readFile("src/test/resources/fixtures/exported.js");
		
		th.addError(5, "'unused' is defined but never used.");
	    th.addError(6, "'isDog' is defined but never used.");
	    th.addError(13, "'unusedDeclaration' is defined but never used.");
	    th.addError(14, "'unusedExpression' is defined but never used.");
	    th.addError(17, "'cannotBeExported' is defined but never used.");
	    
	    th.test(src, new LinterOptions().set("es3", true).set("unused", true));
		th.test(src, new LinterOptions().set("unused", true)); // es5
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
		th.test(src, new LinterOptions().set("moz", true).set("unused", true));
		th.test(src, new LinterOptions().set("unused", true).set("latedef", true));
		
		th.reset();
		th.addError(1, "'unused' is defined but never used.");
		th.test("var unused = 1; var used = 2;", new LinterOptions().addExporteds("used").set("unused", true));
		
		th.reset();
		th.test("var a;", new LinterOptions().addExporteds("a").set("latedef", true));
		
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
		th.reset();
		th.addError(4, "'a' is defined but never used.");
	    th.addError(4, "'c' is defined but never used.");
	    th.addError(4, "'e' is defined but never used.");
	    th.addError(4, "'g' is defined but never used.");
	    th.addError(5, "'b' is defined but never used.");
	    th.addError(5, "'d' is defined but never used.");
	    th.addError(5, "'f' is defined but never used.");
	    th.addError(5, "'h' is defined but never used.");
	    th.test(code, new LinterOptions().set("esversion", 6).set("unused", true));
	}
	
	@Test
	public void testIdentifiers()
	{
		String src = th.readFile("src/test/resources/fixtures/identifiers.js");
		
		th.test(src, new LinterOptions().set("es3", true));
		
		th.addError(1, "'ascii' is defined but never used.");
	    th.addError(2, "'num1' is defined but never used.");
	    th.addError(3, "'lifé' is defined but never used.");
	    th.addError(4, "'π' is defined but never used.");
	    th.addError(5, "'привет' is defined but never used.");
	    th.addError(6, "'\\u1d44' is defined but never used.");
	    th.addError(7, "'encoded\\u1d44' is defined but never used.");
	    th.addError(8, "'\\uFF38' is defined but never used.");
	    th.addError(9, "'\\uFF58' is defined but never used.");
	    th.addError(10, "'\\u1FBC' is defined but never used.");
	    th.addError(11, "'\\uFF70' is defined but never used.");
	    th.addError(12, "'\\u4DB3' is defined but never used.");
	    th.addError(13, "'\\u97CA' is defined but never used.");
	    th.addError(14, "'\\uD7A1' is defined but never used.");
	    th.addError(15, "'\\uFFDA' is defined but never used.");
	    th.addError(16, "'\\uA6ED' is defined but never used.");
	    th.addError(17, "'\\u0024' is defined but never used.");
	    th.addError(18, "'\\u005F' is defined but never used.");
	    th.addError(19, "'\\u0024\\uFF38' is defined but never used.");
	    th.addError(20, "'\\u0024\\uFF58' is defined but never used.");
	    th.addError(21, "'\\u0024\\u1FBC' is defined but never used.");
	    th.addError(22, "'\\u0024\\uFF70' is defined but never used.");
	    th.addError(23, "'\\u0024\\u4DB3' is defined but never used.");
	    th.addError(24, "'\\u0024\\u97CA' is defined but never used.");
	    th.addError(25, "'\\u0024\\uD7A1' is defined but never used.");
	    th.addError(26, "'\\u0024\\uFFDA' is defined but never used.");
	    th.addError(27, "'\\u0024\\uA6ED' is defined but never used.");
	    th.addError(28, "'\\u0024\\uFE24' is defined but never used.");
	    th.addError(29, "'\\u0024\\uABE9' is defined but never used.");
	    th.addError(30, "'\\u0024\\uFF17' is defined but never used.");
	    th.addError(31, "'\\u0024\\uFE4E' is defined but never used.");
	    th.addError(32, "'\\u0024\\u200C' is defined but never used.");
	    th.addError(33, "'\\u0024\\u200D' is defined but never used.");
	    th.addError(34, "'\\u0024\\u0024' is defined but never used.");
	    th.addError(35, "'\\u0024\\u005F' is defined but never used.");
	    
	    th.test(src, new LinterOptions().set("es3", true).set("unused", true));
		th.test(src, new LinterOptions().set("unused", true)); // es5
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
		th.test(src, new LinterOptions().set("moz", true).set("unused", true));
		
		th.reset();
		th.addError(1, "'unused' is defined but never used.");
		th.test("var unused = 1; var used = 2;", new LinterOptions().addExporteds("used").set("unused", true));
	}
	
	@Test
	public void testBadIdentifiers()
	{
		String[] badUnicode = {
			"var \\uNOTHEX;"
		};
		
		th.addError(1, "Unexpected '\\'.");
		th.addError(1, "Expected an identifier and instead saw ''.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(badUnicode, new LinterOptions().set("es3", true));
		th.test(badUnicode, new LinterOptions()); // es5
		th.test(badUnicode, new LinterOptions().set("esnext", true));
		th.test(badUnicode, new LinterOptions().set("moz", true));
		
		String[] invalidUnicodeIdent = {
			"var \\u0000;"
		};
		
		th.addError(1, "Unexpected '\\'.");
		th.addError(1, "Expected an identifier and instead saw ''.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(invalidUnicodeIdent, new LinterOptions().set("es3", true));
		th.test(invalidUnicodeIdent, new LinterOptions()); // es5
		th.test(invalidUnicodeIdent, new LinterOptions().set("esnext", true));
		th.test(invalidUnicodeIdent, new LinterOptions().set("moz", true));
	}
	
	@Test
	public void testRegressionForGH878()
	{
		String src = th.readFile("src/test/resources/fixtures/gh878.js");
		
		th.test(src, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testRegressionForGH910()
	{
		String src = "(function () { if (true) { foo.bar + } })();";
		
		th.addError(1, "Expected an identifier and instead saw '}'.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Missing semicolon.");
	    th.addError(1, "Expected an identifier and instead saw ')'.");
	    th.addError(1, "Expected an operator and instead saw '('.");
	    th.addError(1, "Unmatched '{'.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Missing semicolon.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(src, new LinterOptions().set("es3", true).set("nonew", true));
	}
	
	@Test
	public void testHtml()
	{
		String html = "<html><body>Hello World</body></html>";
		
		th.addError(1, "Expected an identifier and instead saw '<'.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Missing semicolon.");
	    th.addError(1, "Expected an identifier and instead saw '<'.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test(html, new LinterOptions());
	}
	
	@Test
	public void testDestructuringVarInFunctionScope()
	{
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
		
		th.addError(1,  "'foobar' is defined but never used.");
	    th.addError(3,  "'a' is already defined.");
	    th.addError(4,  "'a' is already defined.");
	    th.addError(7,  "'a' is already defined.");
	    th.addError(7,  "'b' is already defined.");
	    th.addError(7,  "'c' is already defined.");
	    th.addError(9,  "'a' is already defined.");
	    th.addError(9,  "'bar' is already defined.");
	    th.addError(10,  "Expected an identifier and instead saw '1'.");
	    th.addError(11, "Expected ',' and instead saw ';'.");
	    th.addError(11, "'a' is already defined.");
	    th.addError(11, "'b' is already defined.");
	    th.addError(11, "'c' is already defined.");
	    th.addError(12, "'a' is already defined.");
	    th.addError(12, "'b' is already defined.");
	    th.addError(12, "'c' is already defined.");
	    th.addError(12, "Expected ']' to match '[' from line 12 and instead saw ';'.");
	    th.addError(12, "Missing semicolon.");
	    th.addError(12, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(12, "Missing semicolon.");
	    th.addError(12, "Expected an identifier and instead saw ']'.");
	    th.addError(12, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(4,  "'z' is not defined.");
	    th.addError(12, "'b' is defined but never used.");
	    th.addError(12, "'c' is defined but never used.");
	    th.addError(5,  "'h' is defined but never used.");
	    th.addError(5,  "'w' is defined but never used.");
	    th.addError(6,  "'o' is defined but never used.");
	    th.addError(7,  "'d' is defined but never used.");
	    th.addError(9,  "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringVarAsMoz()
	{
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
		
		th.addError(3,  "'z' is not defined.");
	    th.addError(8,  "'a' is defined but never used.");
	    th.addError(6,  "'b' is defined but never used.");
	    th.addError(6,  "'c' is defined but never used.");
	    th.addError(4,  "'h' is defined but never used.");
	    th.addError(4,  "'w' is defined but never used.");
	    th.addError(5,  "'o' is defined but never used.");
	    th.addError(6,  "'d' is defined but never used.");
	    th.addError(8,  "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringVarAsEsnext()
	{
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
		
		th.addError(3,  "'z' is not defined.");
	    th.addError(8,  "'a' is defined but never used.");
	    th.addError(6,  "'b' is defined but never used.");
	    th.addError(6,  "'c' is defined but never used.");
	    th.addError(4,  "'h' is defined but never used.");
	    th.addError(4,  "'w' is defined but never used.");
	    th.addError(5,  "'o' is defined but never used.");
	    th.addError(6,  "'d' is defined but never used.");
	    th.addError(8,  "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringVarAsEs5()
	{
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
		
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3,  "'z' is not defined.");
	    th.addError(8,  "'a' is defined but never used.");
	    th.addError(6,  "'b' is defined but never used.");
	    th.addError(6,  "'c' is defined but never used.");
	    th.addError(4,  "'h' is defined but never used.");
	    th.addError(4,  "'w' is defined but never used.");
	    th.addError(5,  "'o' is defined but never used.");
	    th.addError(6,  "'d' is defined but never used.");
	    th.addError(8,  "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true)); // es5
	}
	
	@Test
	public void testDestructuringVarAsLegacyJS()
	{
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
		
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3,  "'z' is not defined.");
	    th.addError(8,  "'a' is defined but never used.");
	    th.addError(6,  "'b' is defined but never used.");
	    th.addError(6,  "'c' is defined but never used.");
	    th.addError(4,  "'h' is defined but never used.");
	    th.addError(4,  "'w' is defined but never used.");
	    th.addError(5,  "'o' is defined but never used.");
	    th.addError(6,  "'d' is defined but never used.");
	    th.addError(8,  "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringVarErrors()
	{
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
		
		th.addError(9,  "Expected an identifier and instead saw '1'.");
	    th.addError(10, "Expected ',' and instead saw ';'.");
	    th.addError(11, "Expected ']' to match '[' from line 11 and instead saw ';'.");
	    th.addError(11, "Missing semicolon.");
	    th.addError(11, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(11, "Missing semicolon.");
	    th.addError(11, "Expected an identifier and instead saw ']'.");
	    th.addError(11, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(3,  "'z' is not defined.");
	    th.addError(11, "'b' is defined but never used.");
	    th.addError(11, "'c' is defined but never used.");
	    th.addError(4,  "'h' is defined but never used.");
	    th.addError(4,  "'w' is defined but never used.");
	    th.addError(5,  "'o' is defined but never used.");
	    th.addError(6,  "'d' is defined but never used.");
	    th.addError(8,  "'bar' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringConstAsMoz()
	{
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
		
		th.addError(1, "'a' is defined but never used.");
	    th.addError(1, "'b' is defined but never used.");
	    th.addError(1, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(4, "'hel' is defined but never used.");
	    th.addError(4, "'wor' is defined but never used.");
	    th.addError(5, "'o' is defined but never used.");
	    th.addError(6, "'f' is defined but never used.");
	    th.addError(6, "'g' is defined but never used.");
	    th.addError(6, "'h' is defined but never used.");
	    th.addError(6, "'i' is defined but never used.");
	    th.addError(7, "'bar' is defined but never used.");
	    th.addError(8, "'j' is defined but never used.");
	    th.addError(8, "'foobar' is defined but never used.");
	    th.addError(9, "'aa' is defined but never used.");
	    th.addError(9, "'bb' is defined but never used.");
	    th.addError(3, "'z' is not defined.");
	    th.addError(9, "'func' is not defined.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringConstAsEsnext()
	{
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
		
		th.addError(1, "'a' is defined but never used.");
	    th.addError(1, "'b' is defined but never used.");
	    th.addError(1, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(4, "'hel' is defined but never used.");
	    th.addError(4, "'wor' is defined but never used.");
	    th.addError(5, "'o' is defined but never used.");
	    th.addError(6, "'f' is defined but never used.");
	    th.addError(6, "'g' is defined but never used.");
	    th.addError(6, "'h' is defined but never used.");
	    th.addError(6, "'i' is defined but never used.");
	    th.addError(7, "'bar' is defined but never used.");
	    th.addError(8, "'foobar' is defined but never used.");
	    th.addError(9, "Attempting to override 'j' which is a constant.");
	    th.addError(11, "['a'] is better written in dot notation.");
	    th.addError(3, "'z' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringConstAsEs5()
	{
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
		
		th.addError(1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'a' is defined but never used.");
	    th.addError(1, "'b' is defined but never used.");
	    th.addError(1, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(4, "'hel' is defined but never used.");
	    th.addError(4, "'wor' is defined but never used.");
	    th.addError(5, "'o' is defined but never used.");
	    th.addError(6, "'f' is defined but never used.");
	    th.addError(6, "'g' is defined but never used.");
	    th.addError(6, "'h' is defined but never used.");
	    th.addError(6, "'i' is defined but never used.");
	    th.addError(7, "'bar' is defined but never used.");
	    th.addError(8, "'j' is defined but never used.");
	    th.addError(8, "'foobar' is defined but never used.");
	    th.addError(3, "'z' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true)); // es5
	}
	
	@Test
	public void testDestructuringConstAsLegacyJS()
	{
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
		
		th.addError(1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'a' is defined but never used.");
	    th.addError(1, "'b' is defined but never used.");
	    th.addError(1, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(4, "'hel' is defined but never used.");
	    th.addError(4, "'wor' is defined but never used.");
	    th.addError(5, "'o' is defined but never used.");
	    th.addError(6, "'f' is defined but never used.");
	    th.addError(6, "'g' is defined but never used.");
	    th.addError(6, "'h' is defined but never used.");
	    th.addError(6, "'i' is defined but never used.");
	    th.addError(7, "'bar' is defined but never used.");
	    th.addError(8, "'j' is defined but never used.");
	    th.addError(8, "'foobar' is defined but never used.");
	    th.addError(3, "'z' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringConstErrors()
	{
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
		
		th.addError(2, "'b' is defined but never used.");
	    th.addError(2, "'c' is defined but never used.");
	    th.addError(4, "'k' is defined but never used.");
	    th.addError(4, "'l' is defined but never used.");
	    th.addError(4, "'m' is defined but never used.");
	    th.addError(5, "'n' is defined but never used.");
	    th.addError(5, "'o' is defined but never used.");
	    th.addError(5, "'p' is defined but never used.");
	    th.addError(2, "'a' has already been declared.");
	    th.addError(2, "'b' has already been declared.");
	    th.addError(2, "'c' has already been declared.");
	    th.addError(3, "Expected an identifier and instead saw '1'.");
	    th.addError(4, "Expected ',' and instead saw ';'.");
	    th.addError(5, "Expected ']' to match '[' from line 5 and instead saw ';'.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(5, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(5, "Expected an identifier and instead saw ']'.");
	    th.addError(5, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5, "Missing semicolon.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringGlobalsAsMoz()
	{
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
		
		th.addError(4,  "'z' is not defined.");
		th.addError(11, "['b'] is better written in dot notation.");
	    th.addError(12, "['b'] is better written in dot notation.");
	    th.addError(13, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringGlobalsAsEsnext()
	{
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
		
		th.addError(4,  "'z' is not defined.");
		th.addError(11, "['b'] is better written in dot notation.");
	    th.addError(12, "['b'] is better written in dot notation.");
	    th.addError(15, "'notDefined1' is not defined.");
	    th.addError(16, "'notDefined2' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringGlobalsAsEs5()
	{
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
		
		th.addError(4,  "'z' is not defined.");
	    th.addError(2, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(9, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(10, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "['b'] is better written in dot notation.");
	    th.addError(11, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(12, "['b'] is better written in dot notation.");
	    th.addError(12, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true)); // es5
	}
	
	@Test
	public void testDestructuringGlobalsAsLegacyJS()
	{
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
		
		th.addError(4,  "'z' is not defined.");
	    th.addError(2, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(9, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(10, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "['b'] is better written in dot notation.");
	    th.addError(11, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(12, "['b'] is better written in dot notation.");
	    th.addError(12, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringGlobalsWithSyntaxErrors()
	{
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
		
		th.addError(3, "Bad assignment.");
	    th.addError(4, "Expected ',' and instead saw ';'.");
	    th.addError(5, "Expected ']' to match '[' from line 5 and instead saw ';'.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(5, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(5, "Expected an identifier and instead saw ']'.");
	    th.addError(5, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(6, "Bad assignment.");
	    th.addError(7, "Expected ',' and instead saw '.'.");
	    th.addError(8, "Bad assignment.");
	    th.addError(9, "Bad assignment.");
	    th.addError(2,  "'z' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
		
		th.reset();
		th.addError(1, "Expected ',' and instead saw '['.");
	    th.addError(1, "Expected ':' and instead saw ']'.");
	    th.addError(1, "Expected an identifier and instead saw '}'.");
	    th.addError(1, "Expected ',' and instead saw ']'.");
	    th.addError(1, "Expected an identifier and instead saw '{'.");
	    th.addError(1, "Expected ',' and instead saw 'a'.");
	    th.addError(1, "Expected an identifier and instead saw ':'.");
	    th.addError(1, "Expected ',' and instead saw '1'.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Expected an identifier and instead saw '='.");
	    th.test("[ { a['b'] } ] = [{a:1}];", new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	    
	    th.reset();
	    th.addError(1, "Expected ',' and instead saw '('.");
	    th.addError(1, "Expected an identifier and instead saw ')'.");
	    th.test("[ { a() } ] = [];", new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	    
	    th.reset();
	    th.addError(1, "Extending prototype of native object: 'Number'.");
	    th.addError(3, "Bad assignment.");
	    th.addError(4, "Bad assignment.");
	    th.addError(6, "Do not assign to the exception parameter.");
	    th.addError(7, "Do not assign to the exception parameter.");
	    th.addError(9, "Bad assignment.");
	    th.addError(10, "Bad assignment.");
	    th.test(new String[]{
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
	public void testDestructuringAssignOfEmptyValuesAsMoz()
	{
		String[] code = {
			"var [ a ] = [ 1, 2 ];",
		    "var [ c, d ] = [ 1 ];",
		    "var [ e, , f ] = [ 3, , 4 ];"
		};
		
		th.addError(1, "'a' is defined but never used.");
	    th.addError(2, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(3, "'f' is defined but never used.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).set("laxcomma", true).set("elision", true));
	}
	
	@Test
	public void testDestructuringAssignOfEmptyValuesAsEsnext()
	{
		String[] code = {
			"var [ a ] = [ 1, 2 ];",
		    "var [ c, d ] = [ 1 ];",
		    "var [ e, , f ] = [ 3, , 4 ];"
		};
		
		th.addError(1, "'a' is defined but never used.");
	    th.addError(2, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(3, "'f' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).set("elision", true));
	}
	
	@Test
	public void testDestructuringAssignOfEmptyValuesAsEs5()
	{
		String[] code = {
			"var [ a ] = [ 1, 2 ];",
		    "var [ c, d ] = [ 1 ];",
		    "var [ e, , f ] = [ 3, , 4 ];"
		};
		
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'a' is defined but never used.");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(3, "'f' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).set("elision", true)); // es5
	}
	
	@Test
	public void testDestructuringAssignOfEmptyValuesAsJSLegacy()
	{
		String[] code = {
			"var [ a ] = [ 1, 2 ];",
		    "var [ c, d ] = [ 1 ];",
		    "var [ e, , f ] = [ 3, , 4 ];"
		};
		
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'a' is defined but never used.");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'e' is defined but never used.");
	    th.addError(3, "'f' is defined but never used.");
	    th.addError(3, "Extra comma. (it breaks older versions of IE)");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true));
	}
	
	@Test
	public void testDestructuringAssignmentDefaultValues()
	{
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
		
		th.addError(4, "Expected an identifier and instead saw ','.");
	    th.addError(4, "Expected ',' and instead saw 'g'.");
	    th.addError(10, "Expected an identifier and instead saw ','.");
	    th.addError(10, "Expected ',' and instead saw 't'.");
	    th.addError(11, "It's not necessary to initialize 'u' to 'undefined'.");
	    th.addError(12, "It's not necessary to initialize 'v' to 'undefined'.");
	    th.addError(13, "It's not necessary to initialize 'x' to 'undefined'.");
	    th.addError(14, "Expected ']' and instead saw '='.");
	    th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testDestructuringAssignmentOfValidSimpleAssignmentTargets()
	{
		th.test(new String[]{
			"[ foo().attr ] = [];",
		    "[ function() {}.attr ] = [];",
		    "[ function() { return {}; }().attr ] = [];",
		    "[ new Ctor().attr ] = [];"
		}, new LinterOptions().set("esversion", 6));
		
		th.addError(1, "Bad assignment.");
	    th.test("[ foo() ] = [];", new LinterOptions().set("esversion", 6));
	    th.test("({ x: foo() } = {});", new LinterOptions().set("esversion", 6));
	    th.test("[ true ? x : y ] = [];", new LinterOptions().set("esversion", 6));
	    th.test("({ x: true ? x : y } = {});", new LinterOptions().set("esversion", 6));
	    th.test("[ x || y ] = [];", new LinterOptions().set("esversion", 6));
	    th.test("({ x: x || y } = {});", new LinterOptions().set("esversion", 6));
	    th.test("[ new Ctor() ] = [];", new LinterOptions().set("esversion", 6));
	    th.test("({ x: new Ctor() } = {});", new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testNonIdentifierPropertyNamesInObjectDestructuring()
	{
		String[] code = {
			"var { ['x' + 2]: a = 3, 0: b } = { x2: 1, 0: 2 };",
		    "var { c, '': d, 'x': e } = {};",
		    "function fn({ 0: f, 'x': g, ['y']: h}) {}"
		};
		
		th.addError(1, "'a' is defined but never used.");
	    th.addError(1, "'b' is defined but never used.");
	    th.addError(2, "'c' is defined but never used.");
	    th.addError(2, "'d' is defined but never used.");
	    th.addError(2, "'e' is defined but never used.");
	    th.addError(3, "'fn' is defined but never used.");
	    th.addError(3, "'f' is defined but never used.");
	    th.addError(3, "'g' is defined but never used.");
	    th.addError(3, "'h' is defined but never used.");
	    th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testEmptyDestructuring()
	{
		String[] code = {
			"var {} = {};",
		    "var [] = [];",
		    "function a({}, []) {}",
		    "var b = ({}) => ([]) => ({});"
		};
		
		th.addError(1, "Empty destructuring.");
	    th.addError(2, "Empty destructuring.");
	    th.addError(3, "Empty destructuring.");
	    th.addError(3, "Empty destructuring.");
	    th.addError(4, "Empty destructuring.");
	    th.addError(4, "Empty destructuring.");
	    th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testArrayElementAssignmentInsideArray()
	{
		String[] code = {
			"var a1 = {};",
		    "var a2 = [function f() {a1[0] = 1;}];"
		};
		
		th.test(code);
	}
	
	@Test
	public void testLetStatementAsMoz()
	{
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementAsEsnext()
	{
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementAsEs5()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testLetStatementAsJSLegacy()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementOutOfScopeAsMoz()
	{
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
		
		th.addError(1, "'x' is defined but never used.");
	    th.addError(5, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(7, "'z' is not defined.");
	    th.addError(9, "'y' is not defined.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementOutOfScopeAsEsnext()
	{
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
		
		th.addError(1, "'x' is defined but never used.");
	    th.addError(5, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(7, "'z' is not defined.");
	    th.addError(9, "'y' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementOutOfScopeAsEs5()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'x' is defined but never used.");
	    th.addError(5, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(7, "'z' is not defined.");
	    th.addError(9, "'y' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testLetStatementOutOfScopeAsJSLegacy()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'x' is defined but never used.");
	    th.addError(5, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(7, "'z' is not defined.");
	    th.addError(9, "'y' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementInFunctionsAsMoz()
	{
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementInFunctionsAsEsnext()
	{
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementInFunctionsAsEs5()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testLetStatementInFunctionsAsLegacyJS()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementNotInScopeAsMoz()
	{
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
		
		th.addError(6, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(4, "'bar' is defined but never used.");
	    th.addError(8, "'z' is not defined.");
	    th.addError(10, "'y' is not defined.");
	    th.addError(11, "'bar' is not defined.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementNotInScopeAsEsnext()
	{
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
		
		th.addError(6, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(4, "'bar' is defined but never used.");
	    th.addError(8, "'z' is not defined.");
	    th.addError(10, "'y' is not defined.");
	    th.addError(11, "'bar' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementNotInScopeAsEs5()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(4, "'bar' is defined but never used.");
	    th.addError(8, "'z' is not defined.");
	    th.addError(10, "'y' is not defined.");
	    th.addError(11, "'bar' is not defined.");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testLetStatementNotInScopeAsLegacyJS()
	{
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
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'z' is defined but never used.");
	    th.addError(3, "'y' is defined but never used.");
	    th.addError(4, "'bar' is defined but never used.");
	    th.addError(8, "'z' is not defined.");
	    th.addError(10, "'y' is not defined.");
	    th.addError(11, "'bar' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementInForLoopAsMoz()
	{
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print", "Iterator"));
	}
	
	@Test
	public void testLetStatementInForLoopAsEsnext()
	{
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print", "Iterator"));
	}
	
	@Test
	public void testLetStatementInForLoopAsEs5()
	{
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
		
		th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print", "Iterator")); // es5
	}
	
	@Test
	public void testLetStatementInForLoopAsLegacyJS()
	{
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
		
		th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print", "Iterator"));
	}
	
	@Test
	public void testLetStatementInDestructuredForLoopAsMoz()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementInDestructuredForLoopAsEsnext()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementInDestructuredForLoopAsEs5()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(21, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(21, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testLetStatementInDestructuredForLoopAsLegacyJS()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(21, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(21, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetStatementAsSeenInJetpack()
	{
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("require", "xferable", "options"));
	}
	
	@Test
	public void testLetStatementAsSeenInJetpackAsEsnext()
	{
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
		
		th.addError(3, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("require", "xferable", "options"));
	}
	
	@Test
	public void testLetStatementAsSeenInJetpackAsEs5()
	{
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
		
		th.addError(1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("require", "xferable", "options")); // es5
	}
	
	@Test
	public void testLetStatementAsSeenInJetpackAsLegacyJS()
	{
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
		
		th.addError(1, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("require", "xferable", "options"));
	}
	
	@Test
	public void testLetBlockAndLetExpression()
	{
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
			"let (x=1, y=2, z=3)",
		    "{",
		    "  let(t=4) print(x, y, z, t);",
		    "  print(let(u=4) u,x);",
		    "}"
		};
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetBlockAndLetExpressionAsEsnext()
	{
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
			"let (x=1, y=2, z=3)",
		    "{",
		    "  let(t=4) print(x, y, z, t);",
		    "  print(let(u=4) u,x);",
		    "}"
		};
		
		th.addError(1, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(4, "'let expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testLetBlockAndLetExpressionAsEs5()
	{
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
			"let (x=1, y=2, z=3)",
		    "{",
		    "  let(t=4) print(x, y, z, t);",
		    "  print(let(u=4) u,x);",
		    "}"
		};
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(4, "'let expressions' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testLetBlockAndLetExpressionAsLegacyJS()
	{
		// Example taken from jetpack/addons sdk library from Mozilla project
		String[] code = {
			"let (x=1, y=2, z=3)",
		    "{",
		    "  let(t=4) print(x, y, z, t);",
		    "  print(let(u=4) u,x);",
		    "}"
		};
		
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'let block' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(4, "'let expressions' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMakeSureLetVariablesAreNotTreatedAsGlobals()
	{
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
	public void testMakeSureVarVariablesCanShadowLetVariables()
	{
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
		
		th.addError(1, "'a' is defined but never used.");
	    th.addError(2, "'b' is defined but never used.");
	    th.addError(3, "'c' is defined but never used.");
	    th.addError(9, "'d' has already been declared.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).set("funcscope", true));
	}
	
	@Test
	public void testMakeSureLetVariablesInClosureOfFunctionsShadowPredefinedGlobals()
	{
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
	public void testMakeSureLetVariablesInClosureOfBlocksShadowPredefinedGlobals()
	{
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
	public void testMakeSureVariablesMayShadowGlobalsInFunctionsAfterTheyAreReferenced()
	{
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
	public void testBlockScopeRedefinesGlobalsOnlyOutsideOfBlocks()
	{
		String[] code = {
			"{",
		    "  let Map = true;",
		    "}",
		    "let Map = false;"
		};
		
		th.addError(4, "Redefinition of 'Map'.");
	    th.test(code, new LinterOptions().set("esnext", true).set("browser", true));
	}
	
	@Test
	public void testDestructuringFunctionAsMoz()
	{
		// Example from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testDestructuringFunctionAsEsnext()
	{
		// Example from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testDestructuringFunctionAsEs5()
	{
		// Example from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testDestructuringFunctionAsLegacyJS()
	{
		// Example from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testDestructuringFunctionDefaultValues()
	{
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
	public void testNonVarDestructuringAssignmentStatement()
	{
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
		
		th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2, "Missing semicolon.");
	    th.addError(2, "Expected an identifier and instead saw '='.");
	    th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2, "Missing semicolon.");
	    th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(3, "Bad assignment.");
	    th.addError(4, "Bad assignment.");
	    th.addError(5, "Expected ',' and instead saw '('.");
	    th.addError(5, "Expected an identifier and instead saw ')'.");
	    th.addError(5, "Expected ',' and instead saw '{'.");
	    th.addError(5, "Expected ',' and instead saw '}'.");
	    th.addError(6, "Expected ',' and instead saw '('.");
	    th.addError(6, "Expected an identifier and instead saw ')'.");
	    th.addError(6, "Expected ',' and instead saw '{'.");
	    th.addError(6, "Expected ',' and instead saw '}'.");
	    th.addError(7, "Bad assignment.");
	    th.addError(8, "Bad assignment.");
	    th.test(codeInvalid, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testInvalidForEach()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
			"for each (let i = 0; i<15; ++i) {",
		    "  print(i);",
		    "}"
		};
		
		th.addError(1, "Invalid for each loop.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testInvalidForEachAsEsnext()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
			"for each (let i = 0; i<15; ++i) {",
		    "  print(i);",
		    "}"
		};
		
		th.addError(1, "Invalid for each loop.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testInvalidForEachAsEs5()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
			"for each (let i = 0; i<15; ++i) {",
		    "  print(i);",
		    "}"
		};
		
		th.addError(1, "Invalid for each loop.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testInvalidForEachAsLegacyJS()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
		String[] code = {
			"for each (let i = 0; i<15; ++i) {",
		    "  print(i);",
		    "}"
		};
		
		th.addError(1, "Invalid for each loop.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testEsnextGenerator()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testEsnextGeneratorAsMozExtentsion()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(1, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testEsnextGeneratorAsEs5()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(1, "'function*' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testEsnextGeneratorAsLegacyJS()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(1, "'function*' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testEsnextGeneratorWithoutYield()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(7, "A generator function shall contain a yield statement.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testEsnextGeneratorWithoutYieldAndCheckTurnedOff()
	{
		String[] code = {
			"function* emptyGenerator() {}",

		    "emptyGenerator();"
		};
		
		th.test(code, new LinterOptions().set("esnext", true).set("noyield", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testEsnextGeneratorWithYieldDelegationGH1544()
	{
		String[] code = {
			"function* G() {",
		    "  yield* (function*(){})();",
		    "}"
		};
		
		th.addError(1, "'function*' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'function*' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "A generator function shall contain a yield statement.");
		th.test(code);
		
		th.reset();
		th.test(code, new LinterOptions().set("esnext", true).set("noyield", true));
		
	}
	
	@Test
	public void testMozillaGenerator()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print", "Iterator"));
	}
	
	@Test
	public void testMozillaGeneratorAsEsnext()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(4, "A yield statement shall be within a generator function (with syntax: `function*`)");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print", "Iterator"));
		
		th.test(code, new LinterOptions().set("esnext", true).set("moz", true));
	}
	
	@Test
	public void testYieldStatementWithinTryCatch()
	{
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print", "Iterator"));
	}
	
	@Test
	public void testMozillaGeneratorAsEs5()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(4, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(9, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print", "Iterator")); // es5
	}
	
	@Test
	public void testMozillaGeneratorAsLegacyJS()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(4, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'destructuring assignment' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(9, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print", "Iterator"));
	}
	
	@Test
	public void testArrayComprehension()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayComprehensionUnusedAndUndefined()
	{
		String[] code = {
			"var range = [1, 2];",
		    "var a = [for (i of range) if (i % 2 === 0) i];",
		    "var b = [for (j of range) doesnotexist];"
		};
		
		th.addError(2, "'a' is defined but never used.");
	    th.addError(3, "'j' is defined but never used.");
	    th.addError(3, "'doesnotexist' is not defined.");
	    th.addError(3, "'b' is defined but never used.");
	    th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true));
	    
	    List<Token> unused = th.getJSHint().generateSummary().getUnused();
	    assertEquals(Arrays.asList(
	    	new Token("a", 2, 5),
	    	new Token("b", 3, 5)
	    	//new Token("j", 3, 15) // see gh-2440
	    ), unused);
	    
	    List<ImpliedGlobal> implieds = th.getJSHint().generateSummary().getImplieds();
	    assertEquals(Arrays.asList(new ImpliedGlobal("doesnotexist", 3)), implieds);
	}
	
	@Test
	public void testGH1856MistakenlyIdentifiedAsArrayComprehension()
	{
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
	public void testGH1413WronglyDetectedAsArrayComprehension()
	{
		String[] code = {
			"var a = {};",
		    "var b = [ a.for ];"
		};
		
		th.test(code, new LinterOptions().set("unused", false));
	}
	
	@Test
	public void testMozStyleArrayComprehension()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayComprehensionWithForOf()
	{
		// example adapted from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionWithForOf()
	{
		// example adapted from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayComprehensionWithUnusedVariables()
	{
		String[] code = {
			"var ret = [for (i of unknown) i];",
		    "print('ret:', ret);"
		};
		
		th.addError(1, "'unknown' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionWithUnusedVariables()
	{
		String[] code = {
			"var ret = [i for (i of unknown)];",
		    "print('ret:', ret);"
		};
		
		th.addError(1, "'unknown' is not defined.");
		th.test(code, new LinterOptions().set("moz", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionAsEsnext()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(3, "A yield statement shall be within a generator function (with syntax: `function*`)");
	    th.addError(6, "Expected 'for' and instead saw 'i'.");
	    th.addError(6, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(7, "Expected 'for' and instead saw 'i'.");
	    th.addError(7, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true).set("undef", true).addPredefineds("print"));
		
		th.reset();
		th.addError(3, "A yield statement shall be within a generator function (with syntax: `function*`)");
		th.test(code, new LinterOptions().set("esnext", true).set("moz", true));
	}
	
	@Test
	public void testArrayComprehensionAsEs5()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(1, "'function*' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(7, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testMozStyleArrayComprehensionAsEs5()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(6, "Expected 'for' and instead saw 'i'.");
	    th.addError(6, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(7, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(7, "Expected 'for' and instead saw 'i'.");
	    th.addError(7, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testArrayComprehensionAsLegacyJS()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(7, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionAsLegacyJS()
	{
		// example taken from https://developer.mozilla.org/en-US/docs/JavaScript/New_in_JavaScript/1.7
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
		
		th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(6, "Expected 'for' and instead saw 'i'.");
	    th.addError(6, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(7, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(7, "Expected 'for' and instead saw 'i'.");
	    th.addError(7, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayComprehensionWithDestArrayAtGlobalScope()
	{
		String[] code = {
			"[for ([i, j] of [[0,0], [1,1], [2,2]]) [i, j] ];",
		    "var destarray_comparray_1 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, [j, j] ]];",
		    "var destarray_comparray_2 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, {i: [i, j]} ]];"
		};
		
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScope()
	{
		String[] code = {
			"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};
		
		th.test(code, new LinterOptions().set("moz", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScopeAsEsnext()
	{
		String[] code = {
			"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};
		
		th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(2, "Expected 'for' and instead saw '['.");
	    th.addError(2, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "Expected 'for' and instead saw '['.");
	    th.addError(3, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayComprehensionWithDestArrayAtGlobalScopeAsEs5()
	{
		String[] code = {
			"[for ([i, j] of [[0,0], [1,1], [2,2]]) [i, j] ];",
		    "var destarray_comparray_1 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, [j, j] ] ];",
		    "var destarray_comparray_2 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, {i: [i, j]} ] ];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(2, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScopeAsEs5()
	{
		String[] code = {
			"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(2, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(2, "Expected 'for' and instead saw '['.");
	    th.addError(2, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "Expected 'for' and instead saw '['.");
	    th.addError(3, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testArrayComprehensionWithDestArrayAtGlobalScopeAsJSLegacy()
	{
		String[] code = {
			"[for ([i, j] of [[0,0], [1,1], [2,2]]) [i, j] ];",
		    "var destarray_comparray_1 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, [j, j] ] ];",
		    "var destarray_comparray_2 = [for ([i, j] of [[0,0], [1,1], [2,2]]) [i, {i: [i, j]} ] ];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(2, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionWithDestArrayAtGlobalScopeAsJSLegacy()
	{
		String[] code = {
			"[ [i, j] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_1 = [ [i, [j, j] ] for each ([i, j] in [[0,0], [1,1], [2,2]])];",
		    "var destarray_comparray_2 = [ [i, {i: [i, j]} ] for each ([i, j] in [[0,0], [1,1], [2,2]])];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(2, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(2, "Expected 'for' and instead saw '['.");
	    th.addError(2, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(3, "Expected 'for' and instead saw '['.");
	    th.addError(3, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayComprehensionImbricationWithDestArray()
	{
		String[] code = {
			"[for ([i, j] of [for ([a, b] of [[2,2], [3,4]]) [a, b] ]) [i, j] ];"
		};
		
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArray()
	{
		String[] code = {
			"[ [i, j] for ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};
		
		th.test(code, new LinterOptions().set("moz", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayUsingForOf()
	{
		String[] code = {
			"[ [i, j] for ([i, j] of [[a, b] for ([a, b] of [[2,2], [3,4]])]) ];"
		};
		
		th.test(code, new LinterOptions().set("moz", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayAsEsnext()
	{
		String[] code = {
			"[ [i, j] for each ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};
		
		th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayComprehensionImbricationWithDestArrayAsEs5()
	{
		String[] code = {
			"[for ([i, j] of [for ([a, b] of [[2,2], [3,4]]) [a, b] ]) [i, j] ];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayAsEs5()
	{
		String[] code = {
			"[for ([i, j] of [for ([a, b] of [[2,2], [3,4]]) [a, b] ]) [i, j] ];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testArrayComprehensionImbricationWithDestArrayAsLegacyJS()
	{
		String[] code = {
			"[ [i, j] for each ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testMozStyleArrayComprehensionImbricationWithDestArrayAsLegacyJS()
	{
		String[] code = {
			"[ [i, j] for each ([i, j] in [[a, b] for each ([a, b] in [[2,2], [3,4]])]) ];"
		};
		
		th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "Expected 'for' and instead saw '['.");
	    th.addError(1, "'for each' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testNoFalsePositiveArrayComprehension()
	{
		String[] code = {
			"var foo = []; for (let i in [1,2,3]) { print(i); }"
		};
		
		th.test(code, new LinterOptions().set("moz", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testTryCatchFilters()
	{
		String[] code = {
			"try {",
		    "  throw {name: 'foo', message: 'bar'};",
		    "}",
		    "catch (e if e.name === 'foo') {",
		    "  print (e.message);",
		    "}"
		};
		
		th.test(code, new LinterOptions().set("moz", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testTryCatchFiltersAsEsnext()
	{
		String[] code = {
			"try {",
		    "  throw {name: 'foo', message: 'bar'};",
		    "}",
		    "catch (e if e.name === 'foo') {",
		    "  print (e.message);",
		    "}"
		};
		
		th.addError(4, "'catch filter' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testTryCatchFiltersAsEs5()
	{
		String[] code = {
			"try {",
		    "  throw {name: 'foo', message: 'bar'};",
		    "}",
		    "catch (e if e.name === 'foo') {",
		    "  print (e.message);",
		    "}"
		};
		
		th.addError(4, "'catch filter' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testTryCatchFiltersAsLegacyJS()
	{
		String[] code = {
			"try {",
		    "  throw {name: 'foo', message: 'bar'};",
		    "}",
		    "catch (e if e.name === 'foo') {",
		    "  print (e.message);",
		    "}"
		};
		
		th.addError(4, "'catch filter' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testFunctionClosureExpression()
	{
		String[] code = {
			"let (arr = [1,2,3]) {",
		    "  arr.every(function (o) o instanceof Object);",
		    "}"
		};
		
		th.test(code, new LinterOptions().set("es3", true).set("moz", true).set("undef", true));
	}
	
	@Test
	public void testFunctionClosureExpressionAsEsnext()
	{
		String[] code = {
			"var arr = [1,2,3];",
		    "arr.every(function (o) o instanceof Object);"
		};
		
		th.addError(2, "'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testFunctionClosureExpressionAsEs5()
	{
		String[] code = {
			"var arr = [1,2,3];",
		    "arr.every(function (o) o instanceof Object);"
		};
		
		th.addError(2, "'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true)); // es5
	}
	
	@Test
	public void testFunctionClosureExpressionAsLegacyJS()
	{
		String[] code = {
			"var arr = [1,2,3];",
		    "arr.every(function (o) o instanceof Object);"
		};
		
		th.addError(2, "'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true));
	}
	
	@Test
	public void testForOfAsEsnext()
	{
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
		
		th.addError(7, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(9, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(10, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(11, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(12, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(12, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(13, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(14, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(15, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(15, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(16, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(17, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(18, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(18, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testForOfAsEs5()
	{
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
		
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(8, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(9, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(10, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(10, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(11, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(11, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(12, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(12, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(13, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(14, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(15, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(15, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(15, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(16, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(17, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(17, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(17, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");

		th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testForOfAsLegacyJS()
	{
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
		
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(8, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(9, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(10, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(10, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(11, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(11, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(12, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(12, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(12, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(13, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(14, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(15, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(15, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(15, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(16, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(17, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(17, "Invalid for-of loop left-hand-side: initializer is forbidden.");
	    th.addError(17, "Invalid for-of loop left-hand-side: more than one ForBinding.");
	    th.addError(17, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");

	    //JSHINT_BUG: copy paste bug, to check legacy code es3 parameter must be passed
	    th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testArrayDestructuringForOfAsEsnext()
	{
		String[] basic = {
			"for ([i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);"
		};
		
		th.addError(1, "Creating global 'for' variable. Should be 'for (var i ...'.");
	    th.addError(1, "Creating global 'for' variable. Should be 'for (var v ...'.");
		th.test(basic, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
		
		String[] bad = {
			"for ([i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for ([i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for ([i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};
		
		th.reset();
		th.addError(1, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(2, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
		
		String[] bad2 = {
			"for (let [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};
		
		th.reset();
		th.addError(1, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(2, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad2, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testArrayDestructuringForOfAsEs5()
	{
		String[] basic = {
			"for ([i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);"
		};
		
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "Creating global 'for' variable. Should be 'for (var i ...'.");
		th.addError(1, "Creating global 'for' variable. Should be 'for (var v ...'.");
		th.addError(2, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
		
		String[] bad = {
			"for ([i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for ([i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for ([i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};
		
		th.reset();
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
		
		String[] bad2 = {
			"for (let [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};
		
		th.reset();
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");

		th.test(bad2, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testArrayDestructuringForOfAsLegacyJS()
	{
		String[] basic = {
			"for ([i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);"
		};
		
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "Creating global 'for' variable. Should be 'for (var i ...'.");
		th.addError(1, "Creating global 'for' variable. Should be 'for (var v ...'.");
		th.addError(2, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print")); //es3
		
		String[] bad = {
			"for ([i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for ([i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for ([i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (var [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};
		
		th.reset();
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print")); //es3
		
		String[] bad2 = {
			"for (let [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (let [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v], [a, b] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
			"for (const [i, v], [a, b] = [1, 2] of [[0, 1],[1, 2],[2, 3],[3, 4]]) print(i + '=' + v);",
		};
		
		th.reset();
		th.addError(1, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(3, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad2, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print")); //es3
	}
	
	@Test
	public void testObjectDestructuringForOfAsEsnext()
	{
		String[] basic = {
			"var obj1 = { key: 'a', data: { value: 1 } };",
			"var obj2 = { key: 'b', data: { value: 2 } };",
			"var arr = [obj1, obj2];",
			"for ({key, data: { value } } of arr) print(key + '=' + value);",
			"for (var {key, data: { value } } of arr) print(key + '=' + value);",
			"for (let {key, data: { value } } of arr) print(key + '=' + value);",
			"for (const {key, data: { value } } of arr) print(key + '=' + value);"
		};
		
		th.addError(4, "Creating global 'for' variable. Should be 'for (var key ...'.");
	    th.addError(4, "Creating global 'for' variable. Should be 'for (var value ...'.");
		th.test(basic, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
		
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
		
		th.reset();
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(7, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
		
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
		
		th.reset();
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(7, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.test(bad2, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testObjectDestructuringForOfAsEs5()
	{
		String[] basic = {
			"var obj1 = { key: 'a', data: { value: 1 } };",
			"var obj2 = { key: 'b', data: { value: 2 } };",
			"var arr = [obj1, obj2];",
			"for ({key, data: { value } } of arr) print(key + '=' + value);",
			"for (var {key, data: { value } } of arr) print(key + '=' + value);",
			"for (let {key, data: { value } } of arr) print(key + '=' + value);",
			"for (const {key, data: { value } } of arr) print(key + '=' + value);"
		};
		
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Creating global 'for' variable. Should be 'for (var key ...'.");
		th.addError(4, "Creating global 'for' variable. Should be 'for (var value ...'.");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
		
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
		
		th.reset();
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
		
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
		
		th.reset();
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad2, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testObjectDestructuringForOfAsLegacyJS()
	{
		String[] basic = {
			"var obj1 = { key: 'a', data: { value: 1 } };",
			"var obj2 = { key: 'b', data: { value: 2 } };",
			"var arr = [obj1, obj2];",
			"for ({key, data: { value } } of arr) print(key + '=' + value);",
			"for (var {key, data: { value } } of arr) print(key + '=' + value);",
			"for (let {key, data: { value } } of arr) print(key + '=' + value);",
			"for (const {key, data: { value } } of arr) print(key + '=' + value);"
		};
		
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Creating global 'for' variable. Should be 'for (var key ...'.");
		th.addError(4, "Creating global 'for' variable. Should be 'for (var value ...'.");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(basic, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print")); // es3
		
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
		
		th.reset();
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print")); // es3
		
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
		
		th.reset();
		th.addError(4, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(6, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(7, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(8, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(8, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'for of' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "Invalid for-of loop left-hand-side: initializer is forbidden.");
		th.addError(9, "Invalid for-of loop left-hand-side: more than one ForBinding.");
		th.addError(9, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(9, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(bad2, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print")); // es3
	}
	
	@Test
	public void testTryMultiCatchForMozExtensions()
	{
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
		
		th.test(code, new LinterOptions().set("moz", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testTryMultiCatchAsEsnext()
	{
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
		
		th.addError(5, "'multiple catch blocks' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testTryMultiCatchAsEs5()
	{
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
		
		th.addError(5, "'multiple catch blocks' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("undef", true).addPredefineds("print")); // es5
	}
	
	@Test
	public void testTryMultiCatchAsLegacyJS()
	{
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
		
		th.addError(5, "'multiple catch blocks' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true).addPredefineds("print"));
	}
	
	@Test
	public void testNoLetNotDirectlyWithinBlock()
	{
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
		
		th.addError(1, "Let declaration not directly within block.");
	    th.addError(4, "Let declaration not directly within block.");
	    th.addError(6, "Let declaration not directly within block.");
	    th.addError(7, "Let declaration not directly within block.");
	    th.addError(8, "Let declaration not directly within block.");
	    th.addError(9, "Let declaration not directly within block.");
	    th.addError(10, "Let declaration not directly within block.");
	    th.addError(10, "Let declaration not directly within block.");
	    th.addError(10, "Let declaration not directly within block.");
	    th.addError(11, "Let declaration not directly within block.");
		th.test(code, new LinterOptions().set("esversion", 6));
		th.test(code, new LinterOptions().set("moz", true));
		
		// Don't warn about let expressions
		th.reset();
		th.test("if (true) let (x = 1) print(x);", new LinterOptions().set("moz", true).setPredefineds("print"));
	}
	
	@Test
	public void testNoConstNotDirectlyWithinBlock()
	{
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
		
		th.addError(1, "Const declaration not directly within block.");
		th.addError(4, "Const declaration not directly within block.");
		th.addError(6, "Const declaration not directly within block.");
		th.addError(7, "Const declaration not directly within block.");
		th.addError(8, "Const declaration not directly within block.");
		th.addError(8, "Const declaration not directly within block.");
		th.addError(8, "Const declaration not directly within block.");
		th.test(code, new LinterOptions().addPredefineds("print").set("esnext", true));
	}
	
	@Test
	public void testLetDeclaredDirectlyWithinBlock()
	{
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
	public void testLetIsDirectlyWithinNestedBlock()
	{
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
	public void testRegressionForCrashFromGH964()
	{
		String[] code = {
			"function test(a, b) {",
		    "  return a[b] || a[b] = new A();",
		    "}"
		};
		
		th.addError(2, "Bad assignment.");
	    th.addError(2, "Did you mean to return a conditional instead of an assignment?");
		th.test(code);
	}
	
	@Test
	public void testAutomaticCommaInsertionGH950()
	{
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
		
		th.addError(2, "Bad line breaking before 'instanceof'.");
	    th.addError(6, "Bad line breaking before '&&'.");
	    th.addError(8, "Line breaking error 'return'.");
	    th.addError(9, "Label 'a' on 1 statement.");
	    th.addError(9, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(11, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(14, "Expected an assignment or function call and instead saw an expression.");
	    
	    th.test(code, new LinterOptions().set("es3", true).set("asi", true));
		th.test(code, new LinterOptions().set("asi", true)); // es5
		th.test(code, new LinterOptions().set("esnext", true).set("asi", true));
		th.test(code, new LinterOptions().set("moz", true).set("asi", true));
		
		th.reset();
		th.addError(2, "Bad line breaking before 'instanceof'.");
	    th.addError(3, "Missing semicolon.");
	    th.addError(4, "Missing semicolon.");
	    th.addError(6, "Bad line breaking before '&&'.");
	    th.addError(8, "Line breaking error 'return'.");
	    th.addError(8, "Missing semicolon.");
	    th.addError(9, "Label 'a' on 1 statement.");
	    th.addError(9, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(9, "Missing semicolon.");
	    th.addError(11, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(11, "Missing semicolon.");
	    th.addError(13, "Missing semicolon.");
	    th.addError(14, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(14, "Missing semicolon.");
	    th.addError(16, "Missing semicolon.");
	    
	    th.test(code, new LinterOptions().set("es3", true).set("asi", false));
		th.test(code, new LinterOptions().set("asi", false)); // es5
		th.test(code, new LinterOptions().set("esnext", true).set("asi", false));
		th.test(code, new LinterOptions().set("moz", true).set("asi", false));
	}
	
	@Test
	public void testFatArrowSupport()
	{
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
		
		th.addError(5, "'evens' is not defined.");
	    th.addError(6, "'nats' is not defined.");
	    th.addError(8, "'print' is not defined.");
	    th.addError(9, "'print' is not defined.");
	    th.addError(9, "'j' is not defined.");
	    th.addError(8, "'z' is not defined.");
	    
	    th.test(code, new LinterOptions().set("undef", true).set("esnext", true));
	    
	    th.reset();
	    th.addError(1, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(3, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'evens' is not defined.");
	    th.addError(6, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(6, "'nats' is not defined.");
	    th.addError(7, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(8, "'print' is not defined.");
	    th.addError(8, "'z' is not defined.");
	    th.addError(9, "'print' is not defined.");
	    th.addError(9, "'j' is not defined.");
	    th.addError(13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(21, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(23, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(26, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    
	    th.test(code, new LinterOptions().set("undef", true).set("moz", true));
		
		th.reset();
		th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(3, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(11, "'const' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(13, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(16, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(17, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(17, "Bad invocation.");
	    th.addError(18, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(19, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(19, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(19, "Bad invocation.");
	    th.addError(20, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(21, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(22, "Bad invocation.");
	    th.addError(23, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(24, "Bad invocation.");
	    th.addError(26, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(26, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    
	    th.test(code); // es5
	    th.test(code, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testFatArrowNestedFunctionScoping()
	{
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
	public void testDefaultArgumentsInFatArrowNestedFunctions()
	{
	    th.test("(x = 0) => { return x; };", new LinterOptions().set("expr", true).set("unused", true).set("esnext", true));
	}
	
	@Test
	public void testExressionsInPlaceOfArrowFunctionParameters()
	{
		th.addError(1, "Expected an identifier and instead saw '1'.");
		th.test("(1) => {};", new LinterOptions().set("expr", true).set("esnext", true));
	}
	
	@Test
	public void testArrowFunctionParameterContainingSemicolonGH3002()
	{
		th.addError(1, 19, "Unnecessary semicolon.");
	    th.addError(1, 27, "Expected an assignment or function call and instead saw an expression.");
	    th.test("(x = function() { ; }) => 0;", new LinterOptions().set("esversion", 6));
	}
	
	@Test(groups = {"conciseMethods"})
	public void testConciseMethodsBasicSupport()
	{
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
		
		th.addError(2, "'concise methods' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'generator functions' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'concise methods' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    
	    th.test(code); // es5
		th.test(code, new LinterOptions().set("es3", true));
	}
	
	@Test(groups = {"conciseMethods"})
	public void testConciseMethodsGetAndSet()
	{
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
	
	@Test(groups = {"conciseMethods"})
	public void testConciseMethodsGetWithoutSet()
	{
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
	
	//JSHINT_BUG: copy paste bug, method should be called setWithoutGet
	@Test(groups = {"conciseMethods"})
	public void testConciseMethodsSetWithoutGet()
	{
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
	@Test(groups = {"conciseMethods"})
	public void testConciseMethodsNameIsNotLocalVar()
	{
		String[] code = {
			"var obj = {",
		    "  foo(foo) {},",
		    "  bar() { var bar; }",
		    "};"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testObjectShortNotationBasic()
	{
		String[] code = {
			"var foo = 42;",
		    "var bar = {foo};",
		    "var baz = {foo, bar};",
		    "var biz = {",
		    "  foo,",
		    "  bar",
		    "};"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
		
		th.addError(2, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.test(code);
	}
	
	@Test
	public void testObjectShortNotationMixed()
	{
		String[] code = {
			"var b = 1, c = 2;",
		    "var o1 = {a: 1, b, c};",
		    "var o2 = {b, a: 1, c};"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
		
		th.addError(2, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'object short notation' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.test(code);
	}
	
	@Test
	public void testObjectComputedPropertyName()
	{
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
		    "var e = { ['s']: 1 };"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
		
		th.addError(6, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(8, "'concise methods' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(9, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(10, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(11, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(13, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(15, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.addError(17, "'computed property names' is only available in ES6 (use 'esversion: 6').");
	    th.test(code);
	}
	
	@Test
	public void testSpreadRestOperatorSupport()
	{
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
		
	    th.addError(1, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(3, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(8, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(9, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(10, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(10, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(11, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(12, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    
	    th.test(code, new LinterOptions().set("moz", true));
	    
	    th.reset();
	    th.addError(1, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(3, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(6, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    
	    th.addError(7, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(9, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(10, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(10, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(10, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(11, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(12, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    
	    th.test(code);   
	}
	
	@Test
	public void testParameterDestructuringWithRest()
	{
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
		
		th.addError(1, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(1, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(1, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(1, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(2, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(3, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(3, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(6, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(6, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(8, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    th.addError(9, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    
	    th.test(code, new LinterOptions().set("moz", true));
	    
	    th.reset();
	    th.addError(1, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(1, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(1, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(2, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(2, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(3, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(4, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(7, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(8, "'let' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(8, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");

	    th.addError(9, "'spread/rest operator' is only available in ES6 (use 'esversion: 6').");
	    
	    
	    th.test(code);   
	}
	
	@Test
	public void testGH1010()
	{
		String[] code = {
			"var x = 20, y, z; if(x < 30) y=7, z=2; else y=5;"
		};
	    
	    th.test(code, new LinterOptions().set("expr", true).set("es3", true));
		th.test(code, new LinterOptions().set("expr", true)); // es5
		th.test(code, new LinterOptions().set("expr", true).set("esnext", true));
		th.test(code, new LinterOptions().set("expr", true).set("moz", true));
	}
	
	@Test
	public void testClasses()
	{
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
		
		th.addError(icdecl + 4, "Expected an identifier and instead saw 'package' (a reserved word).");
	    th.addError(icexpr + 4, "Expected an identifier and instead saw 'package' (a reserved word).");
	    th.addError(icdeclAssn + 4, "Reassignment of 'Foo15', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icdeclAssn + 7, "Reassignment of 'Foo18', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icdeclAssn + 7, "Reassignment of 'Foo17', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icexprAssn + 4, "Reassignment of 'Foo15', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icexprAssn + 7, "Reassignment of 'Foo18', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icexprAssn + 7, "Reassignment of 'Foo17', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    
	    th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
		
		th.addError(icdecl + 1, "'Foo0' is defined but never used.");
	    th.addError(icdecl + 3, "Expected an identifier and instead saw 'protected' (a reserved word).");
	    th.addError(icdecl + 3, "'protected' is defined but never used.");
	    th.addError(icdecl + 4, "'package' is defined but never used.");
	    th.addError(icexpr + 1, "'Foo7' is defined but never used.");
	    th.addError(icexpr + 3, "Expected an identifier and instead saw 'static' (a reserved word).");
	    th.addError(icexpr + 3, "'static' is defined but never used.");
	    th.addError(icexpr + 3, "Expected an identifier and instead saw 'protected' (a reserved word).");
	    th.addError(icexpr + 4, "'package' is defined but never used.");
	    
	    th.addError(icdeclAssn + 4, "Reassignment of 'Foo15', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icdeclAssn + 7, "Reassignment of 'Foo18', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icdeclAssn + 7, "Reassignment of 'Foo17', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icexprAssn + 4, "Reassignment of 'Foo15', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icexprAssn + 7, "Reassignment of 'Foo18', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(icexprAssn + 7, "Reassignment of 'Foo17', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
		
	    code[0] = "'use strict';" + code[0];
	    th.test(code, new LinterOptions().set("unused", true).set("globalstrict", true).set("esnext", true));
		th.test(code, new LinterOptions().set("unused", true).set("globalstrict", true).set("moz", true));
	}
	
	@Test
	public void testClassAndMethodNaming()
	{
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
		
		th.addError(1, "Expected an identifier and instead saw 'eval' (a reserved word).");
	    th.addError(2, "Expected an identifier and instead saw 'arguments' (a reserved word).");
	    th.addError(4, "A class getter method cannot be named 'constructor'.");
	    th.addError(5, "A class setter method cannot be named 'constructor'.");
	    th.addError(6, "A class method cannot be named 'prototype'.");
	    th.addError(7, "Class properties must be methods. Expected '(' but instead saw 'extra'.");
	    th.addError(8, "Class properties must be methods. Expected '(' but instead saw 'extraIdent1'.");
	    th.addError(9, "Class properties must be methods. Expected '(' but instead saw 'extraIdent2'.");
	    th.addError(10, "Class properties must be methods. Expected '(' but instead saw 'extraIdent3'.");
	    th.addError(11, "Class properties must be methods. Expected '(' but instead saw 'extraIdent4'.");
	    th.addError(12, "Class properties must be methods. Expected '(' but instead saw 'extraIdent5'.");
	    th.addError(14, "Duplicate getter method 'dupgetter'.");
	    th.addError(16, "Duplicate setter method 'dupsetter'.");
	    th.addError(16, "Setter is defined without getter.");
	    th.addError(18, "Duplicate static getter method 'dupgetter'.");
	    th.addError(20, "Duplicate static setter method 'dupsetter'.");
	    th.addError(22, "Duplicate class method 'dupmethod'.");
	    th.addError(24, "Duplicate static class method 'dupmethod'.");
	    th.addError(29, "Unexpected '('.");
	    th.addError(30, "Setter is defined without getter.");
	    th.addError(31, "Setter is defined without getter.");
	    
	    th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testComputedClassMethodsArentDuplicate()
	{
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
		
		// JSHint shouldn't throw a "Duplicate class method" warning with computed method names
		// GH-2350
	    th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testClassMethodThis()
	{
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
		
		th.addError(10, "Possible strict violation.");
	    th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testClassNewCap()
	{
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
		
		th.test(code, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testClassExpression()
	{
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
		
		th.addError(2, "Reassignment of 'MyClass', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(3, "Reassignment of 'MyClass', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(4, "Reassignment of 'MyClass', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(5, "Reassignment of 'MyClass', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(6, "Reassignment of 'MyClass', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(7, "Reassignment of 'MyClass', which is is a class. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(9, "'MyClass' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testFunctionReassignment()
	{
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
		
		th.addError(2, "Reassignment of 'f', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(3, "Reassignment of 'f', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(5, "Reassignment of 'g', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(6, "Reassignment of 'g', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(9, "Reassignment of 'h', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(10, "Reassignment of 'h', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.test(src);
	}
	
	@Test
	public void testFunctionNotOverwritten()
	{
		String[] code = {
			"function x() {",
		    "  x = 1;",
		    "  var x;",
		    "}"
		};
		
		th.test(code, new LinterOptions().set("shadow", true));
	}
	
	@Test
	public void testClassExpressionThis()
	{
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
	public void testClassElementEmpty()
	{
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
		
		th.addError(2, "Unnecessary semicolon.");
		th.addError(4, "Unnecessary semicolon.");
		th.addError(6, "Unnecessary semicolon.");
		th.addError(6, "Unnecessary semicolon.");
		th.addError(8, "Unnecessary semicolon.");
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testInvalidClasses()
	{
		// Regression test for GH-2324
		th.addError(1, "Class properties must be methods. Expected '(' but instead saw ''.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test("class a { b", new LinterOptions().set("esnext", true));
		
		// Regression test for GH-2339
		th.reset();
		th.addError(2, "Class properties must be methods. Expected '(' but instead saw ':'.");
		th.addError(3, "Expected '(' and instead saw '}'.");
		th.addError(4, "Expected an identifier and instead saw '}'.");
		th.addError(4, "Unrecoverable syntax error. (100% scanned).");
		th.test(new String[]{
				"class Test {",
				"  constructor: {",
				"  }",
				"}"
		}, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testGH1018()
	{
		String[] code = {
			"if (a = 42) {}",
		    "else if (a = 42) {}",
		    "while (a = 42) {}",
		    "for (a = 42; a = 42; a += 42) {}",
		    "do {} while (a = 42);",
		    "switch (a = 42) {}"
		};
		
	    th.test(code, new LinterOptions().set("boss", true));
	    
	    for (int i = 0; i < code.length; i++)
	    {
	    	th.addError(i + 1, "Expected a conditional expression and instead saw an assignment.");
	    }
	    
	    th.test(code);
	}
	
	@Test
	public void testWarningsForAssignmentInConditionals()
	{
		String[] code = {
			"if (a = b) { }",
		    "if ((a = b)) { }",
		    "if (a = b, a) { }",
		    "if (a = b, b = c) { }",
		    "if ((a = b, b = c)) { }",
		    "if (a = b, (b = c)) { }"
		};
		
		th.addError(1, "Expected a conditional expression and instead saw an assignment.");
	    th.addError(4, "Expected a conditional expression and instead saw an assignment.");
	    
	    th.test(code); // es5
	}
	
	@Test
	public void testGH1089()
	{
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
		
		th.addError(9, "Expected an assignment or function call and instead saw an expression.");
		
		th.test(code, new LinterOptions().set("moz", true));
	    
		th.addError(4, "'function closure expressions' is only available in Mozilla JavaScript extensions (use moz option).");
	    th.test(code);
	}
	
	@Test
	public void testGH1105()
	{
		String[] code = {
			"while (true) {",
		    "    if (true) { break }",
		    "}"
		};
		
		th.addError(2, "Missing semicolon.");
		
	    th.test(code);
	}
	
	@Test
	public void testForCrashWithInvalidCondition()
	{
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
		th.addError(1, "Expected an identifier and instead saw ')'.");
	    th.addError(1, "Expected ')' to match '(' from line 1 and instead saw ';'.");
	    th.addError(2, "Expected an identifier and instead saw ','.");
	    th.addError(3, "Unexpected ')'.");
	    th.addError(4, "Expected an identifier and instead saw ','.");
	    th.addError(4, "Expected ')' to match '(' from line 4 and instead saw 'b'.");
	    th.addError(4, "Expected an identifier and instead saw ')'.");
	    th.addError(4, "Missing semicolon.");
	    th.addError(6, "Expected an identifier and instead saw ','.");
	    th.addError(7, "Unexpected ')'.");
	    th.addError(7, "Expected an identifier and instead saw ')'.");
	    th.addError(7, "Expected ')' to match '(' from line 7 and instead saw ';'.");
	    th.addError(8, "Expected an identifier and instead saw ','.");
	    th.addError(8, "Expected ')' to match '(' from line 8 and instead saw 'b'.");
	    th.addError(8, "Expected an identifier and instead saw ')'.");
	    th.addError(8, "Missing semicolon.");
		
		th.test(code, new LinterOptions().set("asi", true).set("expr", true));
	}
	
	@Test
	public void testYieldInCompoundExpressions()
	{
		String code = th.readFile("src/test/resources/fixtures/yield-expressions.js");
		
		th.addError(22, "Did you mean to return a conditional instead of an assignment?");
		th.addError(23, 22, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		th.addError(31, "Did you mean to return a conditional instead of an assignment?");
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
	    
	    th.reset();
	    th.addError(22, "Did you mean to return a conditional instead of an assignment?");
	    th.addError(31, "Did you mean to return a conditional instead of an assignment?");
	    
	    // These are line-column pairs for the Mozilla paren errors.
	    int[][] needparen = {
	    	// comma
	    	{ 5,  5}, { 6,  8}, { 7,  5}, {11,  5}, {12,  8}, {13,  5},
	    	// yield in yield
	    	{18, 11}, {19, 17}, {19, 11}, {20, 11}, {20,  5}, {21, 11}, {21,  5}, {21, 26}, {22, 22},
	    	{23, 22}, {23, 11}, {27, 11}, {28, 17}, {28, 11}, {29, 11}, {29,  5}, {30, 11}, {30,  5},
	    	{30, 24}, {31, 22}, {32, 11}, {32, 20},
	    	// infix
	    	{51, 10}, {53, 10}, {54, 16}, {57, 10}, {58,  5}, {59, 10}, {60,  5}, {60, 14},
	    	// prefix
	    	{64,  6}, {65,  7}, {66,  6}, {67,  7}, {70,  6}, {71,  7},
	    	// ternary
	    	{77,  5}, {78,  5}, {78, 13}, {79,  5}, {79, 13}, {79, 41}, {82,  5}, {83,  5}, {83, 13},
	    	{84,  5}, {84, 13}, {84, 37}
	    };
	    
	    for (int[] lc : needparen)
	    {
	    	th.addError(lc[0], lc[1], "Mozilla requires the yield expression to be parenthesized here.");
	    }
	    
	    th.addError(1, "'function*' is only available in ES6 (use 'esversion: 6').");
	    th.addError(74, "'function*' is only available in ES6 (use 'esversion: 6').");
	    
	    th.test(code, new LinterOptions().set("maxerr", 1000).set("expr", true).set("moz", true));
	}
	
	@Test
	public void testYieldInInvalidPositions()
	{
		th.addError(1, "Invalid position for 'yield' expression (consider wrapping in parenthesis).");
		
		th.test("function* g() { null || yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null || yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null || yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null && yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null && yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { null && yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !!yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !!yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { !!yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 + yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 + yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 + yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 - yield; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 - yield null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { 1 - yield* g(); }", new LinterOptions().set("esversion", 6).set("expr", true));
		
		th.reset();
		th.addError(1, "Bad operand.");
		
		th.test("function* g() { yield.x; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { yield*.x; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { yield ? null : null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { yield* ? null : null; }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { (yield ? 1 : 1); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { (yield* ? 1 : 1); }", new LinterOptions().set("esversion", 6).set("expr", true));
		th.test("function* g() { yield / 1; }", new LinterOptions().set("esversion", 6).set("expr", true));
		
		th.reset();
		th.addError(1, "Unclosed regular expression.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
	    th.test("function* g() { yield* / 1; }", new LinterOptions().set("esversion", 6).set("expr", true));
	    
	    th.reset();
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
	    
	    th.reset();
	    th.addError(3, "Missing semicolon.");
	    th.addError(5, "Missing semicolon.");
	    th.test(code, new LinterOptions().set("esversion", 6).set("expr", true));
	    
	    th.reset();
	    th.test(code, new LinterOptions().set("esversion", 6).set("expr", true).set("asi", true));
	}
	
	@Test
	public void testGH387()
	{
		String[] code = {
			"var foo = a",
		    "delete foo.a;"
		};
		
		th.addError(1, "Missing semicolon.");
		
	    th.test(code); // es5
	}
	
	@Test
	public void testForLineBreaksWithYield()
	{
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
		
		th.addError(3, "Expected ')' to match '(' from line 2 and instead saw 'c'.");
		th.addError(3, "Missing semicolon.");
	    th.addError(4, "Expected an identifier and instead saw ')'.");
	    th.addError(4, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(6, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(7, "Bad line breaking before ','.");
	    th.addError(8, "Comma warnings can be turned off with 'laxcomma'.");
	    th.addError(9, "Missing semicolon.");
	    th.addError(10, "Expected an identifier and instead saw '?'.");
	    th.addError(10, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(10, "Missing semicolon.");
	    th.addError(10, "Label 'i' on j statement.");
	    th.addError(10, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(13, "Missing semicolon.");
	    th.addError(14, "Expected an assignment or function call and instead saw an expression.");
		
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
	    th.reset();
	    th.addError(1, "'function*' is only available in ES6 (use 'esversion: 6').");
	    th.addError(3, "Expected ')' to match '(' from line 2 and instead saw 'c'.");
	    th.addError(4, "Expected an identifier and instead saw ')'.");
	    th.addError(4, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(6, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(8, "Comma warnings can be turned off with 'laxcomma'.");
	    th.addError(7, "Bad line breaking before ','.");
	    th.addError(10, "Expected an identifier and instead saw '?'.");
	    th.addError(10, "Missing semicolon.");
	    th.addError(10, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(10, "Label 'i' on j statement.");
	    th.addError(10, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(14, "Expected an assignment or function call and instead saw an expression.");
	    
	    th.test(code, new LinterOptions().set("moz", true).set("asi", true));
	    
	    th.addError(2, "Line breaking error 'yield'.");
	    th.addError(3, "Missing semicolon.");
	    th.addError(5, "Line breaking error 'yield'.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(7, "Line breaking error 'yield'.");
	    th.addError(9, "Line breaking error 'yield'.");
	    th.addError(9, "Missing semicolon.");
	    th.addError(11, "Line breaking error 'yield'.");
	    th.addError(13, "Line breaking error 'yield'.");
	    th.addError(13, "Missing semicolon.");
	    
	    th.test(code, new LinterOptions().set("moz", true));
	    
	    String[] code2 = {
	    	"function* gen() {",
	        "  yield",
	        "  fn();",
	        "  yield*",
	        "  fn();",
	        "}"
	    };
	    
	    th.reset();
	    th.addError(5, "Bad line breaking before 'fn'.");
	    th.test(code2, new LinterOptions().set("esnext", true).set("undef", false).set("asi", true));
	    
	    th.reset();
	    th.addError(2, "Missing semicolon.");
	    th.addError(5, "Bad line breaking before 'fn'.");
	    th.test(code2, new LinterOptions().set("esnext", true).set("undef", false));
	}
	
	@Test
	public void testUnreachableRegressionForGH1227()
	{
		String src = th.readFile("src/test/resources/fixtures/gh1227.js");
		
		th.addError(14, "Unreachable 'return' after 'return'.");
	    th.test(src);
	}
	
	@Test
	public void testUnreachableBreak()
	{
		String[] src = {
			"var i = 0;",
		    "foo: while (i) {",
		    "  break foo;",
		    "  i--;",
		    "}"
		};
		
		th.addError(4, "Unreachable 'i' after 'break'.");
	    th.test(src);
	}
	
	@Test
	public void testUnreachableContinue()
	{
		String[] src = {
			"var i = 0;",
		    "while (i) {",
		    "  continue;",
		    "  i--;",
		    "}"
		};
		
		th.addError(4, "Unreachable 'i' after 'continue'.");
	    th.test(src);
	}
	
	@Test
	public void testUnreachableReturn()
	{
		String[] src = {
			"(function() {",
		    "  var x = 0;",
		    "  return;",
		    "  x++;",
		    "}());"
		};
		
		th.addError(4, "Unreachable 'x' after 'return'.");
	    th.test(src);
	}
	
	@Test
	public void testUnreachableThrow()
	{
		String[] src = {
			"throw new Error();",
		    "var x;"
		};
		
		th.addError(2, "Unreachable 'var' after 'throw'.");
	    th.test(src);
	}
	
	@Test
	public void testUnreachableBraceless()
	{
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
	@Test
	public void testUnreachableNestedBraceless()
	{
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
	public void testForBreakInSwitchCaseCurlyBraces()
	{
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
		th.addError(9, "Expected a 'break' statement before 'default'.");
		th.test(code);
	}
	
	@Test
	public void testForBreakInSwitchCaseInLoopCurlyBraces()
	{
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
		th.addError(11, "Expected a 'break' statement before 'default'.");
		th.test(code);
	}
	
	@Test
	public void testAllowExpressionWithCommaInSwitchCaseCondition()
	{
		String[] code = {
			"switch (false) {",
		    "  case x = 1, y = x: { break; }",
		    "}"
		};
		
		th.test(code);
	}
	
	@Test(groups = {"ignoreDirective"})
	public void testIgnoreDirectiveShouldBeGoodOptionAndOnlyAcceptStartEndOrLineAsValues()
	{
		String[] code = {
			"/*jshint ignore:start*/",
		    "/*jshint ignore:end*/",
		    "/*jshint ignore:line*/",
		    "/*jshint ignore:badvalue*/"
		};
		
		th.addError(4, "Bad option value.");
		th.test(code);
	}
	
	@Test(groups = {"ignoreDirective"})
	public void testIgnoreDirectiveShouldAllowLinterToSkipBlockedOutLinesToContinueFindingErrorsInRestOfCode()
	{
		String code = th.readFile("src/test/resources/fixtures/gh826.js");
		
		th.addError(34, "Use '===' to compare with '0'.");
		th.test(code);
	}
	
	@Test(groups = {"ignoreDirective"})
	public void testIgnoreDirectiveShouldIgnoreLinesThatAppearToEndWithMultilineCommentEndingsGH1691()
	{
		String[] code = {
			"/*jshint ignore: start*/",
		    "var a = {",
		    // The following line ends in a sequence of characters that, if parsed
		    // naively, could be interpreted as an "end multiline comment" token.
		    "  a: /\\s*/", //JSHINT_BUG: \s doesn't work as expected, should be \\s
		    "};",
		    "/*jshint ignore: end*/"
		};
		
		th.test(code);
	}
	
	@Test(groups = {"ignoreDirective"})
	public void testIgnoreDirectiveShouldIgnoreLinesThatEndWithMultilineCommentGH1396()
	{
		String[] code = {
			"/*jshint ignore:start */",
		    "var a; /* following comment */",
		    "/*jshint ignore:end */"
		};
		
		th.test(code, new LinterOptions().set("unused", true));
	}
	
	@Test(groups = {"ignoreDirective"})
	public void testIgnoreDirectiveShouldIgnoreMultilineComments()
	{
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
	
	@Test(groups = {"ignoreDirective"})
	public void testIgnoreDirectiveShouldBeDetectedEvenWithLeadingAndOrTrailingWhitespace()
	{
		String[] code = {
			"  /*jshint ignore:start */",     // leading whitespace
		    "   if (true) { alert('sup') }", // should be ignored
		    "  /*jshint ignore:end */  ",     // leading and trailing whitespace
		    "   if (true) { alert('sup') }", // should not be ignored
		    "  /*jshint ignore:start */   ",  // leading and trailing whitespace
		    "   if (true) { alert('sup') }", // should be ignored
		    "  /*jshint ignore:end */   "     // leading and trailing whitespace
		};
		
		th.addError(4, "Missing semicolon.");
		th.test(code);
	}
	
	// gh-2411 /* jshint ignore:start */ stopped working.
	@Test(groups = {"ignoreDirective"})
	public void testIgnoreDirectiveShouldApplyToLinesLexedDuringLookaheadOperations()
	{
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
	public void testJshintIgnoreShouldBeAbleToIgnoreSingleLineWithTrailingComment()
	{
		String code = th.readFile("src/test/resources/fixtures/gh870.js");
		
		th.test(code, new LinterOptions().set("unused", true));
	}
	
	@Test
	public void testRegressionForGH1431()
	{
		// The code is invalid but it should not crash JSHint.
		th.addError(1, "Use '!==' to compare with 'null'.");
	    th.addError(1, "Expected ';' and instead saw ')'.");
	    th.addError(1, "Expected ')' and instead saw ';'.");
	    th.addError(1, "Expected an identifier and instead saw ';'.");
	    th.addError(1, "Expected ')' to match '(' from line 1 and instead saw 'i'.");
	    th.addError(1, "Expected an identifier and instead saw ')'.");
		th.test("for (i=0; (arr[i])!=null); i++);");
	}
	
	@Test
	public void testJshintIgnoreStartEndShouldBeDetectedUsingSingleLineComments()
	{
		String[] code = {
			"// jshint ignore:start",
		    "var a;",
		    "// jshint ignore:end",
		    "var b;"
		};
		
		th.addError(4, "'b' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true));
	}
	
	@Test
	public void testDestructuringFunctionParametersAsEs5()
	{
		String src = th.readFile("src/test/resources/fixtures/destparam.js");
		
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(10, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(10, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(11, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(14, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(15, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(15, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(16, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(17, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(18, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(21, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(21, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(21, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(22, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(22, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(23, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(23, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(23, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(24, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(24, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(27, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(27, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(27, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(28, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(28, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(28, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(29, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(29, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(29, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(30, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(30, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(30, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(31, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(31, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(31, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.test(src, new LinterOptions().set("unused", true).set("undef", true).set("maxerr", 100));
	}
	
	@Test
	public void testDestructuringFunctionParametersAsLegacyJS()
	{
		String src = th.readFile("src/test/resources/fixtures/destparam.js");
		
		th.addError(4, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(4, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(5, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(5, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(6, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(7, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(10, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(10, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(11, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(11, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(14, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(14, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(15, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(15, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(16, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(16, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(17, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(17, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(18, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(18, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(21, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(21, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(21, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(22, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(22, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(22, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(23, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(23, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(23, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(24, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(24, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(24, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(27, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(27, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(27, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(28, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(28, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(28, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(29, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(29, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(29, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(30, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(30, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(30, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
	    th.addError(31, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(31, "'destructuring binding' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(31, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.test(src, new LinterOptions().set("es3", true).set("unused", true).set("undef", true).set("maxerr", 100));
	}
	
	@Test
	public void testForParenthesesInOddNumberedToken()
	{
		String[] code = {
			"let f, b;",
		    "let a = x => ({ f: f(x) });",
		    "b = x => x;"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testRegressionCrashFromGH1573()
	{
		th.addError(1, "Expected an identifier and instead saw 'var'.");
	    th.addError(1, "Expected ']' to match '[' from line 1 and instead saw 'foo'.");
	    th.addError(1, "Expected an identifier and instead saw ']'.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Missing semicolon.");
	    th.addError(1, "Bad assignment.");
		th.test("[var foo = 1;]");
	}
	
	@Test
	public void testMakeSureWeDontThrowErrorsOnRemovedOptions()
	{
		th.test("a();", new LinterOptions().set("nomen", true).set("onevar", true).set("passfail", true).set("white", true));
	}
	
	@Test
	public void testForOfShouldntBeSubjectToForInRules()
	{
		th.test("for (let x of [1, 2, 3]) { console.log(x); }", new LinterOptions().set("forin", true).set("esnext", true));
	}
	
	@Test
	public void testIgnoreStringsContainingBracesWithinArrayLiteralDeclarations()
	{
		th.test("var a = [ '[' ];");
	}
	
	@Test
	public void testGH1016DontIssueW088IfIdentifierIsOutsideOfBlockscope()
	{
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
		
		th.addError(17, "Creating global 'for' variable. Should be 'for (var nonKey ...'.");
		th.test(code);
	}
	
	@Test
	public void testES6UnusedExports()
	{
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
		
		th.addError(24, "'letDefinedLater' was used before it was declared, which is illegal for 'let' variables.");
	    th.addError(25, "'constDefinedLater' was used before it was declared, which is illegal for 'const' variables.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testES6BlockExports()
	{
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
		
		th.addError(1, "'broken' is defined but never used.");
	    th.addError(2, "'broken2' is defined but never used.");
	    th.addError(4, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(5, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(6, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(7, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(8, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(14, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(15, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(16, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(17, "Export declarations are only allowed at the top level of module scope.");
	    th.addError(17, "Function declarations should not be placed in blocks. Use a function expression or move the statement to the top of the outer function.");
	    th.addError(18, "Export declarations are only allowed at the top level of module scope.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testES6BlockImports()
	{
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
		
		th.addError(2, "Import declarations are only allowed at the top level of module scope.");
	    th.addError(5, "Import declarations are only allowed at the top level of module scope.");
	    th.addError(8, "Import declarations are only allowed at the top level of module scope.");
	    th.addError(11, "Import declarations are only allowed at the top level of module scope.");
	    th.test(code, new LinterOptions().set("esversion", 6).set("module", true));
	}
	
	@Test
	public void testStrictDirectiveASI()
	{
		LinterOptions options = new LinterOptions().set("strict", true).set("asi", true).set("globalstrict", true).setPredefineds("x");
		
		th.test("'use strict'\nfunction fn() {}\nfn();", options);
		
		th.test("'use strict'\n;function fn() {}\nfn();", options);
		
		th.test("'use strict';function fn() {} fn();", options);
		
		th.addError(2, "Bad invocation.");
	    th.addError(2, "Missing \"use strict\" statement.");
		th.test("'use strict'\n(function fn() {})();", options);
		
		th.reset();
		th.addError(2, "Missing \"use strict\" statement.");
		th.test("'use strict'\n[0] = '6';", options);
		
		th.reset();
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2, "Missing \"use strict\" statement.");
	    th.test("'use strict',function fn() {}\nfn();", options);
	    
	    th.reset();
	    th.addError(1, "Missing \"use strict\" statement.");
		th.test("'use strict'.split(' ');", options);
		
		th.reset();
		th.addError(1, "Missing \"use strict\" statement.");
		th.test("(function() { var x; \"use strict\"; return x; }());", new LinterOptions().set("strict", true).set("expr", true));
		
		th.reset();
		th.addError(1, "Missing \"use strict\" statement.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.test("'use strict', 'use strict';", options);
	    
	    th.reset();
	    th.addError(1, "Missing \"use strict\" statement.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.test("'use strict' * 'use strict';", options);
	    
	    th.reset();
	    th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.test("'use strict'\n!x;", options);
	    
	    th.reset();
	    th.addError(2, "Bad line breaking before '+'.");
	    th.addError(2, "Missing \"use strict\" statement.");
	    th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.test("'use strict'\n+x;", options);
	    
	    th.reset();
	    th.test("'use strict'\n++x;", options);
	    
	    th.reset();
	    th.addError(1, "Bad operand.");
	    th.addError(2, "Missing \"use strict\" statement.");
	    th.addError(2, "Missing \"use strict\" statement.");
	    th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.test("'use strict'++\nx;", options);
	    
	    th.reset();
	    th.addError(1, "Bad operand.");
	    th.addError(1, "Missing \"use strict\" statement.");
	    th.test("'use strict'++;", options);
	}
	
	@Test
	public void testDereferenceDelete()
	{
		th.addError(1, "Expected an identifier and instead saw '.'.");
		th.addError(1, "Missing semicolon.");
		th.test("delete.foo();");
	}
	
	@Test
	public void testTrailingCommaInObjectBindingPattern()
	{
		String[] code = {
			"function fn(O) {",
		    "  var {a, b, c,} = O;",
		    "}",
		    "fn({ a: 1, b: 2, c: 3 });"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testTrailingCommaInObjectBindingPatternParameters()
	{
		String[] code = {
			"function fn({a, b, c,}) { }",
		    "fn({ a: 1, b: 2, c: 3 });"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testTrailingCommaInArrayBindingPattern()
	{
		String[] code = {
			"function fn(O) {",
		    "  var [a, b, c,] = O;",
		    "}",
		    "fn([1, 2, 3]);"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testTrailingCommaInArrayBindingPatternParameters()
	{
		String[] code = {
			"function fn([a, b, c,]) { }",
		    "fn([1, 2, 3]);"
		};
		
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testGH1879()
	{
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
	public void testCommaAfterRestElementInArrayBindingPattern()
	{
		String[] code = {
			"function fn(O) {",
		    "  var [a, b, ...c,] = O;",
		    "  var [...d,] = O;",
		    "}",
		    "fn([1, 2, 3]);"
		};
		
		th.addError(2, "Invalid element after rest element.");
		th.addError(3, "Invalid element after rest element.");
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testCommaAfterRestElementInArrayBindingPatternParameters()
	{
		String[] code = {
			"function fn([a, b, ...c,]) { }",
		    "function fn2([...c,]) { }",
		    "fn([1, 2, 3]);",
		    "fn2([1,2,3]);"
		};
		
		th.addError(1, "Invalid element after rest element.");
		th.addError(2, "Invalid element after rest element.");
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testCommaAfterRestParameter()
	{
		String[] code = {
			"function fn(a, b, ...c, d) { }",
		    "function fn2(...a, b) { }",
		    "fn(1, 2, 3);",
		    "fn2(1, 2, 3);"
		};
		
		th.addError(1, "Invalid parameter after rest parameter.");
		th.addError(2, "Invalid parameter after rest parameter.");
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testExtraRestOperator()
	{
		String[] code = {
			"function fn([a, b, ......c]) { }",
		    "function fn2([......c]) { }",
		    "function fn3(a, b, ......) { }",
		    "function fn4(......) { }",
		    "var [......a] = [1, 2, 3];",
		    "var [a, b, ... ...c] = [1, 2, 3];",
		    "var arrow = (......a) => a;",
		    "var arrow2 = (a, b, ......c) => c;",
		    "var arrow3 = ([......a]) => a;",
		    "var arrow4 = ([a, b, ......c]) => c;",
		    "fn([1, 2, 3]);",
		    "fn2([1, 2, 3]);",
		    "fn3(1, 2, 3);",
		    "fn4(1, 2, 3);",
		    "arrow(1, 2, 3);",
		    "arrow2(1, 2, 3);",
		    "arrow3([1, 2, 3]);",
		    "arrow4([1, 2, 3]);"
		};
		
		th.addError(1, "Unexpected '...'.");
		th.addError(2, "Unexpected '...'.");
		th.addError(3, "Unexpected '...'.");
		th.addError(4, "Unexpected '...'.");
		th.addError(5, "Unexpected '...'.");
		th.addError(6, "Unexpected '...'.");
		th.addError(7, "Unexpected '...'.");
		th.addError(8, "Unexpected '...'.");
		th.addError(9, "Unexpected '...'.");
		th.addError(10, "Unexpected '...'.");
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testRestOperatorWithoutIdentifier()
	{
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
		
		th.addError(1, "Unexpected '...'.");
		th.addError(2, "Unexpected '...'.");
		th.addError(3, "Unexpected '...'.");
		th.addError(4, "Unexpected '...'.");
		th.addError(5, "Unexpected '...'.");
		th.addError(6, "Unexpected '...'.");
		th.addError(7, "Unexpected '...'.");
		th.addError(8, "Unexpected '...'.");
		th.addError(9, "Unexpected '...'.");
		th.addError(10, "Unexpected '...'.");
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testGetAsIdentifierProp()
	{
		th.test("var get; var obj = { get };", new LinterOptions().set("esnext", true));
		
		th.test("var set; var obj = { set };", new LinterOptions().set("esnext", true));
		
		th.test("var get, set; var obj = { get, set };", new LinterOptions().set("esnext", true));
		
		th.test("var get, set; var obj = { set, get };", new LinterOptions().set("esnext", true));
		
		th.test("var get; var obj = { a: null, get };", new LinterOptions().set("esnext", true));
		
		th.test("var get; var obj = { a: null, get, b: null };", new LinterOptions().set("esnext", true));
		
		th.test("var get; var obj = { get, b: null };", new LinterOptions().set("esnext", true));
		
		th.test("var get; var obj = { get, get a() {} };", new LinterOptions().set("esnext", true));
		
		th.test(new String[]{
			"var set;",
			"var obj = { set, get a() {}, set a(_) {} };"
		}, new LinterOptions().set("esnext", true));
		
	}
	
	@Test
	public void testInvalidParams()
	{
		th.addError(1, "Expected an identifier and instead saw '!'.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test("(function(!", new LinterOptions().set("esnext", true));
	}
	
	// Regression test for gh-2362
	@Test
	public void testFunctionKeyword()
	{
		th.addError(1, "Missing name in function declaration.");
		th.addError(1, "Expected '(' and instead saw ''.");
		th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test("function");
	}
	
	@Test
	public void testNonGeneratorAfterGenerator()
	{
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
	public void testNewTarget()
	{
		String[] code = {
			"class A {",
		    "  constructor() {",
		    "    return new.target;",
		    "  }",
		    "}"
		};
		
		th.addError(1, "'class' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
	    th.addError(3, "'new.target' is only available in ES6 (use 'esversion: 6').");
	    th.test(code);
	    
	    th.reset();
	    th.test(code, new LinterOptions().set("esnext", true));
	    
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
	    
	    th.reset();
	    th.addError(1, "'new.target' must be in function scope.");
	    th.addError(4, "'new.target' must be in function scope.");
	    th.addError(6, "'new.target' must be in function scope.");
	    th.test(code2, new LinterOptions().set("esnext", true));
	    
	    String[] code3 = {
	    	"var x = new.meta;"
	    };
	    
	    th.reset();
	    th.addError(1, "Invalid meta property: 'new.meta'.");
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
	    
	    th.reset();
	    th.addError(3, "Bad assignment.");
	    th.addError(4, "Bad assignment.");
	    th.addError(5, "Bad assignment.");
	    th.addError(6, "Bad assignment.");
	    th.addError(7, "Bad assignment.");
	    th.test(code4, new LinterOptions().set("esnext", true));
	}
	
	// gh2656: "[Regression] 2.9.0 warns about proto deprecated even if proto:true"
	@Test
	public void testLazyIdentifierChecks()
	{
		String[] src = {
			"var o = [",
		    "  function() {",
		    "    // jshint proto: true",
		    "    o.__proto__ = null;",
		    "  }",
		    "];",
		    "o.__proto__ = null;"
		};
		
		th.addError(7, "The '__proto__' property is deprecated.");
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
	    
	    th.reset();
	    th.addError(8, "The '__proto__' property is deprecated.");
	    th.addError(9, "The '__iterator__' property is deprecated.");
	    th.test(src);
	}
	
	@Test
	public void testParsingCommas()
	{
		String src = th.readFile("src/test/resources/fixtures/parsingCommas.js");
		
		th.addError(2, "Unexpected ','.");
	    th.addError(2, "Comma warnings can be turned off with 'laxcomma'.");
	    th.addError(1, "Bad line breaking before ','.");
	    th.addError(2, "Expected an identifier and instead saw ';'.");
	    th.addError(2, "Expected an identifier and instead saw ')'.");
	    th.addError(2, "Expected ';' and instead saw '{'.");
	    th.addError(2, "Expected an identifier and instead saw '}'.");
	    th.addError(5, "Expected ')' to match '(' from line 1 and instead saw 'for'.");
	    th.addError(5, "Expected an identifier and instead saw ';'.");
	    th.addError(5, "Expected ')' to match '(' from line 5 and instead saw ';'.");
	    th.addError(5, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(6, "Unexpected ','.");
	    th.addError(5, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(6, "Expected an identifier and instead saw ','.");
	    th.addError(6, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(6, "Missing semicolon.");
	    th.addError(6, "Expected an identifier and instead saw ')'.");
	    th.addError(6, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(6, "Missing semicolon.");
	    th.test(src);
	}
	
	@Test
	public void testInstanceOfLiterals()
	{
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
		
		th.addError(13, "Expected an identifier and instead saw 'undefined' (a reserved word).");
	    th.addError(16, errorMessage);
	    th.addError(17, errorMessage);
	    th.addError(18, errorMessage);
	    th.addError(19, errorMessage);
	    th.addError(20, errorMessage);
	    th.addError(21, errorMessage);
	    th.addError(22, errorMessage);
	    th.addError(23, errorMessage);
	    th.addError(24, errorMessage);
	    th.addError(25, errorMessage);
	    th.addError(26, errorMessage);
	    th.addError(27, warningMessage);
	    th.addError(28, warningMessage);
	    
	    th.test(code, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testForInExpr()
	{
		th.test(new String[]{
			"for (var x in [], []) {}"
		});
		
		th.addError(2, "Expected ')' to match '(' from line 2 and instead saw ','.");
	    th.addError(2, "Expected an identifier and instead saw ')'.");
	    th.addError(2, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2, "Missing semicolon.");
	    th.test(new String[]{
	    	"for (var x in [], []) {}",
	        "for (var x of {}, {}) {}"
		}, new LinterOptions().set("esversion", 6));
	}
}