package org.jshint.test.unit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jshint.JSHint;
import org.jshint.JSHintException;
import org.jshint.LinterGlobals;
import org.jshint.LinterOptions;
import org.jshint.DataSummary;
import org.jshint.ImpliedGlobal;
import org.jshint.LinterWarning;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

public class TestCore extends Assert
{
	private TestHelper th = new TestHelper();
	
	@BeforeMethod
	private void setupBeforeMethod()
	{
		th.newTest();
	}
	
	/**
	 * JSHint allows you to specify custom globals as a parameter to the JSHINT
	 * function so it is not necessary to spam code with jshint-related comments
	 */
	@Test
	public void testCustomGlobals()
	{	
		JSHint jshint = new JSHint();
		
		String code = "(function (test) { return [ fooGlobal, barGlobal ]; }());";
		
		LinterGlobals custom = new LinterGlobals(false, "fooGlobal", "barGlobal");
		
		assertTrue(jshint.lint(code, new LinterOptions(), custom));
		
		DataSummary report = jshint.generateSummary();
		assertEquals(report.getImplieds().size(), 0);
		assertEquals(report.getGlobals().size(), 2);
		
		Map<String, Boolean> dict = new HashMap<String, Boolean>();
		for (String g : report.getGlobals())
		{
			dict.put(g, true);
		}
		
		Set<String> customKeys = custom.keySet();
		for (String g : customKeys)
		{
			assertTrue(dict.containsKey(g));
		}
		
		// Regression test (GH-665)
		String[] codeArray = {
			"/*global bar*/",
			"foo = {};",
			"bar = {};"
		};
		
		th.addError(2, 1, "Read only.");
		th.addError(3, 1, "Read only.");
		th.test(codeArray, new LinterOptions().set("es3", true).set("unused", true).addPredefined("foo", false));
	}
	
	@Test
	public void testUnusedDefinedGlobals()
	{
		String src = th.readFile("src/test/resources/fixtures/unusedglobals.js");
		
		th.addError(2, 1, "'bar' is defined but never used.");
		th.test(src, new LinterOptions().set("es3", true).set("unused", true));
	}
	
	@Test
	public void testImplieds()
	{
		String[] src = {
			"f = 0;",
			"(function() {",
			"  g = 0;",
			"}());",
			"h = 0;"
		};
		DataSummary report;
		
		th.test(src);
		report = th.getJSHint().generateSummary();
		
		assertEquals(report.getImplieds(), Arrays.asList(
			new ImpliedGlobal("f", 1),
			new ImpliedGlobal("g", 3),
			new ImpliedGlobal("h", 5)
		));
		
		th.test("__proto__ = 0;", new LinterOptions().set("proto", true));
		report = th.getJSHint().generateSummary();
		
		assertEquals(report.getImplieds(), Arrays.asList(
			new ImpliedGlobal("__proto__", 1)
		));
	}
	
	@Test
	public void testExportedDefinedGlobals()
	{
		String[] src = {"/*global foo, bar */",
			"export { bar, foo };"
		};
		
		// Test should pass
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true), new LinterGlobals());
		
		DataSummary report = th.getJSHint().generateSummary();
		assertEquals(report.getGlobals(), ImmutableSet.of("bar", "foo"));
	}
	
	@Test
	public void testGlobalVarDeclarations()
	{
		String src = "var a;";
		
		th.test(src, new LinterOptions().set("es3", true), new LinterGlobals());
		
		DataSummary report = th.getJSHint().generateSummary();
		assertEquals(report.getGlobals(), ImmutableSet.of("a"));
		
		th.test(src, new LinterOptions().set("es3", true).set("node", true), new LinterGlobals());
		
		report = th.getJSHint().generateSummary();
		assertEquals(report.getGlobals(), Collections.emptySet());
		
		th.test("var __proto__;", new LinterOptions().set("proto", true));
		report = th.getJSHint().generateSummary();
		assertEquals(report.getGlobals(), ImmutableSet.of("__proto__"));
	}
	
	@Test
	public void testGlobalDeclarations()
	{
		String src = "exports = module.exports = function (test) {};";
		
		// Test should pass
		th.test(src, new LinterOptions().set("es3", true).set("node", true), new LinterGlobals(true, "exports"));
		
		// Test should pass as well
		src = StringUtils.join(new String[]{
				"/*jshint node:true */",
				"/*global exports:true */",
				"exports = module.exports = function (test) {};"
		}, "\n");
		
		th.test(src);
	}
	
	@Test
	public void testMultilineGlobalDeclarations()
	{
		String src = th.readFile("src/test/resources/fixtures/multiline-global-declarations.js");
		
		th.addError(12, 1, "'pi' is defined but never used.");
		th.test(src, new LinterOptions().set("unused", true));
	}
	
	/** Test that JSHint recognizes `new Array(<expr>)` as a valid expression */
	@Test
	public void testNewArray()
	{
		String code = "new Array(1);";
		String code1 = "new Array(v + 1);";
		String code2 = "new Array(\"hello\", \"there\", \"chaps\");";
		
		th.test(code);
		th.test(code1);
		th.test(code2);
		
		th.addError(1, 10, "The array literal notation [] is preferable.");
		th.test("new Array();");
	}
	
	/** Test that JSHint recognizes `new foo.Array(<expr>)` as a valid expression #527 **/
	@Test
	public void testNewNonNativeArray()
	{
		String code = "new foo.Array();";
		String code1 = "new foo.Array(1);";
		String code2 = "new foo.Array(v + 1);";
		String code3 = "new foo.Array(\"hello\", \"there\", \"chaps\");";
		
		th.test(code);
		th.test(code1);
		th.test(code2);
		th.test(code3);
	}
	
	@Test
	public void testNonNativeArray()
	{
		String code1 = "foo.Array();";
		String code2 = "foo.Array(v + 1);";
		String code3 = "foo.Array(\"hello\", \"there\", \"chaps\");";
		
		th.test(code1);
		th.test(code2);
		th.test(code3);
	}
	
	/** Test that JSHint recognizes `new Object(<expr>)` as a valid expression */
	@Test
	public void testNewObject()
	{
		String code = "Object(1);";
		String code1 = "new Object(1);";
		
		th.test(code);
		th.test(code1);
		
		th.addError(1, 7, "The object literal notation {} is preferable.");
		th.test("Object();");
		
		th.newTest();
		th.addError(1, 11, "The object literal notation {} is preferable.");
		th.test("new Object();");
	}
	
	/** Test that JSHint recognizes `new foo.Object(<expr>)` as a valid expression #527 **/
	@Test
	public void testNewNonNativeObject()
	{
		String code = "new foo.Object();";
		String code1 = "new foo.Object(1);";
		String code2 = "foo.Object();";
		String code3 = "foo.Object(1);";
		
		th.test(code);
		th.test(code1);
		th.test(code2);
		th.test(code3);
	}
	
	/**
	 * Test that JSHint allows `undefined` to be a function parameter.
	 * It is a common pattern to protect against the case when somebody
	 * overwrites undefined. It also helps with minification.
	 *
	 * More info: https://gist.github.com/315916
	 */
	@Test
	public void testUndefinedAsParam()
	{
		String code = "(function (undefined) {}());";
		String code1 = "var undefined = 1;";
		
		th.test(code);
		
		// But it must never tolerate reassigning of undefined
		th.addError(1, 5, "Redefinition of 'undefined'.");
		th.test(code1);
	}
	
	/** Tests that JSHint accepts new line after a dot (.) operator */
	@Test
	public void testNewLineAfterDot()
	{
		th.test(new String[]{"chain().chain().", "chain();"});
	}
	
	/**
	 * JSHint does not tolerate deleting variables.
	 * More info: http://perfectionkills.com/understanding-delete/
	 * */
	@Test
	public void testNoDelete()
	{
		th.addError(1, 21, "Variables should not be deleted.");
		
		th.test("delete NullReference;");
	}
	
	/**
	 * JSHint allows case statement fall through only when it is made explicit
	 * using special comments.
	 */
	@Test
	public void testSwitchFallThrough()
	{
		String src = th.readFile("src/test/resources/fixtures/switchFallThrough.js");
		
		th.addError(3, 18, "Expected a 'break' statement before 'case'.");
		th.addError(18, 7, "Expected a 'break' statement before 'default'.");
		th.addError(40, 12, "Unexpected ':'.");
		th.test(src);
	}
	
	// GH-490: JSHint shouldn't require break before default if default is
	// the first switch statement.
	@Test
	public void testSwitchDefaultFirst()
	{
		String src = th.readFile("src/test/resources/fixtures/switchDefaultFirst.js");
		
		th.addError(5, 16, "Expected a 'break' statement before 'default'.");
		th.test(src);
	}
	
	@Test
	public void testVoid()
	{
		String[] code = {
				"void(0);",
				"void 0;",
				"var a = void(1);"
		};
		
		th.test(code);
	}
	
	@Test
	public void testFunctionScopedOptions()
	{
		String src = th.readFile("src/test/resources/fixtures/functionScopedOptions.js");
		
		th.addError(1, 1, "eval can be harmful.");
		th.addError(8, 1, "eval can be harmful.");
		th.test(src);
	}
	
	/** JSHint should not only read jshint, but also jslint options */
	@Test
	public void testJslintOptions()
	{
		String src = th.readFile("src/test/resources/fixtures/jslintOptions.js");
		
		th.test(src);
	}
	
	@Test
	public void testJslintInverted()
	{
		String src = th.readFile("src/test/resources/fixtures/jslintInverted.js");
		
		th.test(src);
	}
	
	@Test
	public void testJslintRenamed()
	{
		String src = th.readFile("src/test/resources/fixtures/jslintRenamed.js");
		
		th.addError(4, 9, "Expected '===' and instead saw '=='.");
		th.test(src);
	}
	
	@Test
	public void testJslintSloppy()
	{
		String src = "/*jslint sloppy:true */ function test() { return 1; }";
		
		th.test(src);
	}
	
	/** JSHint should ignore unrecognized jslint options */
	@Test
	public void testJslintUnrecognized()
	{
		String src = "/*jslint closure:true */ function test() { return 1; }";
		
		th.test(src);
	}
	
	@Test
	public void testCaseExpressions()
	{
		String src = th.readFile("src/test/resources/fixtures/caseExpressions.js");
		
		th.test(src);
	}
	
	@Test
	public void testReturnStatement()
	{
		String src = th.readFile("src/test/resources/fixtures/return.js");
		
		th.addError(3, 15, "Did you mean to return a conditional instead of an assignment?");
		th.addError(38, 5, "Line breaking error 'return'.");
		th.addError(38, 11, "Missing semicolon.");
		th.addError(39, 7, "Unnecessary semicolon.");
		th.test(src, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testArgsInCatchReused()
	{
		String src = th.readFile("src/test/resources/fixtures/trycatch.js");
		
		th.addError(6, 13, "'e' is already defined.");
		th.addError(12, 9, "Do not assign to the exception parameter.");
		th.addError(13, 9, "Do not assign to the exception parameter.");
		th.addError(24, 9, "'e' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("undef", true));
	}
	
	@Test
	public void testRawOnError()
	{
		JSHint jshint = new JSHint();
		jshint.lint(";", new LinterOptions().set("maxerr", 1));
		
		List<LinterWarning> errors = jshint.getErrors();
		assertEquals(errors.get(0).getRaw(), "Unnecessary semicolon.");
		assertEquals(errors.get(1).getRaw(), "Too many errors.");
		assertEquals(errors.size(), 2);
	}
	
	@Test
	public void testYesEmptyStmt()
	{
		String src = th.readFile("src/test/resources/fixtures/emptystmt.js");
		
		th.addError(1, 4, "Expected an identifier and instead saw ';'.");
		th.addError(6, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(10, 2, "Unnecessary semicolon.");
		th.addError(17, 11, "Unnecessary semicolon.");
		th.test(src, new LinterOptions().set("es3", true).set("curly", false));
		
		th.newTest();
		
		th.addError(1, 4, "Expected an identifier and instead saw ';'.");
		th.addError(10, 2, "Unnecessary semicolon.");
		th.addError(17, 11, "Unnecessary semicolon.");
		th.test(src, new LinterOptions().set("es3", true).set("curly", false).set("expr", true));
	}
	
	@Test
	public void testInsideEval()
	{
		String src = th.readFile("src/test/resources/fixtures/insideEval.js");
		
		th.addError(1, 1, "eval can be harmful.");
		th.addError(3, 1, "eval can be harmful.");
		th.addError(5, 1, "eval can be harmful.");
		th.addError(7, 1, "eval can be harmful.");
		th.addError(9, 1, "eval can be harmful.");
		th.addError(11, 1, "Implied eval. Consider passing a function instead of a string.");
		th.addError(13, 1, "Implied eval. Consider passing a function instead of a string.");
		th.addError(15, 7, "Implied eval. Consider passing a function instead of a string.");
		th.addError(17, 7, "Implied eval. Consider passing a function instead of a string.");
		
		// The "TestRun" class (and these errors) probably needs some
		// facility for checking the expected scope of the error
		th.addError(13, 17, "Unexpected early end of program.");
		th.addError(13, 17, "Unrecoverable syntax error. (100% scanned).");
		th.addError(17, 17, "Unexpected early end of program.");
		th.addError(17, 17, "Unrecoverable syntax error. (100% scanned).");
		
		th.test(src, new LinterOptions().set("es3", true).set("evil", false));
		
		// Regression test for bug GH-714.
		JSHint jshint = new JSHint();
		jshint.lint(src, new LinterOptions().set("evil", false).set("maxerr", 1));
		LinterWarning err = jshint.generateSummary().getErrors().get(1);
		assertEquals(err.getRaw(), "Too many errors.");
		assertEquals(err.getScope(), "(main)");
	}
	
	@Test
	public void testEscapedEvil()
	{
		String[] code = {
				"\\u0065val(\"'test'\");"
		};
		
		th.addError(1, 1, "eval can be harmful.");
		th.test(code, new LinterOptions().set("evil", false));
	}
	
	// Regression test for GH-394.
	@Test
	public void testNoExcOnTooManyUndefined()
	{
		String code = "a(); b();";
		
		JSHint jshint = new JSHint();
		try
		{
			jshint.lint(code, new LinterOptions().set("undef", true).set("maxerr", 1));
		}
		catch (JSHintException e)
		{
			assertTrue(false, "Exception was thrown");
		}
		
		th.addError(1, 1, "'a' is not defined.");
		th.addError(1, 6, "'b' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true));
	}
	
	@Test
	public void testDefensiveSemicolon()
	{
		String src = th.readFile("src/test/resources/fixtures/gh-226.js");
		
		th.addError(16, 1, "Unnecessary semicolon.");
		th.addError(17, 2, "Unnecessary semicolon.");
		th.test(src, new LinterOptions().set("es3", true).set("expr", true).set("laxbreak", true));
	}
	
	// Test different variants of IIFE
	@Test
	public void testIife()
	{
		String iife = StringUtils.join(new String[]{
				"(function (test) { return; }());",
				"(function (test) { return; })();"
		}, "\n");
		
		th.test(iife);
	}
	
	// Tests invalid options when they're passed as function arguments
	// For code that tests /*jshint ... */ see parser.js
	@Test
	public void testInvalidOptions()
	{
		th.addError(0, 0, "Bad option: 'invalid'.");
		th.test("function test() {}", new LinterOptions().set("es3", true).set("devel", true).set("invalid", true));
	}
	
	@Test
	public void testMultilineArray()
	{
		String src = th.readFile("src/test/resources/fixtures/gh-334.js");
		
		th.test(src);
	}
	
	@Test
	public void testInvalidSource()
	{
		// no need to port all tests here
		
		th.addError(0, 0, "Input is neither a string nor an array of strings.");
		th.test((String)null);
		th.test((String[])null);
		
		th.newTest();
		th.test("", new LinterOptions().set("es3", true));
		th.test(new String[]{}, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testConstructor()
	{
		String code = "new Number(5);";
		
		th.addError(1, 1, "Do not use Number as a constructor.");
		th.test(code, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testMissingRadix()
	{
		String code = "parseInt(20);";
		
		th.addError(1, 12, "Missing radix parameter.");
		th.test(code, new LinterOptions().set("es3", true));
		
		th.newTest();
		th.test(code);
	}
	
	@Test
	public void testNumberNaN()
	{
		String code = "(function (test) { return Number.NaN; })();";
		
		th.test(code, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testHtmlEscapement()
	{
		th.test("var a = '<\\!--';", new LinterOptions().set("es3", true));
		th.test("var a = '\\!';", new LinterOptions().set("es3", true));
	}
	
	// GH-551 regression test.
	@Test
	public void testSparseArrays()
	{
		String src = "var arr = ['a',, null,, '',, undefined,,];";
		
		th.addError(1, 16, "Extra comma. (it breaks older versions of IE)");
		th.addError(1, 23, "Extra comma. (it breaks older versions of IE)");
		th.addError(1, 28, "Extra comma. (it breaks older versions of IE)");
		th.addError(1, 40, "Extra comma. (it breaks older versions of IE)");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.newTest();
		th.test(src, new LinterOptions().set("elision", true)); // es5
	}
	
	@Test
	public void testReserved()
	{
		String src = th.readFile("src/test/resources/fixtures/reserved.js");
		
		th.addError(1, 1, "Expected an identifier and instead saw 'volatile' (a reserved word).");
		th.addError(5, 5, "Expected an identifier and instead saw 'let' (a reserved word).");
		th.addError(10, 7, "Expected an identifier and instead saw 'let' (a reserved word).");
		th.addError(13, 13, "Expected an identifier and instead saw 'class' (a reserved word).");
		th.addError(14, 5, "Expected an identifier and instead saw 'else' (a reserved word).");
		th.addError(15, 5, "Expected an identifier and instead saw 'protected' (a reserved word).");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.newTest();
		th.addError(5, 5, "Expected an identifier and instead saw 'let' (a reserved word).");
		th.addError(10, 7, "Expected an identifier and instead saw 'let' (a reserved word).");
		th.test(src); // es5
	}
	
	// GH-744: Prohibit the use of reserved words as non-property
	// identifiers.
	@Test
	public void testES5Reserved()
	{
		String src = th.readFile("src/test/resources/fixtures/es5Reserved.js");
		
		th.addError(2, 3, "Expected an identifier and instead saw 'default' (a reserved word).");
		th.addError(3, 3, "Unexpected 'in'.");
		th.addError(3, 3, "Expected an identifier and instead saw 'in' (a reserved word).");
		th.addError(6, 5, "Expected an identifier and instead saw 'default' (a reserved word).");
		th.addError(7, 10, "Expected an identifier and instead saw 'new' (a reserved word).");
		th.addError(8, 12, "Expected an identifier and instead saw 'class' (a reserved word).");
		th.addError(9, 3, "Expected an identifier and instead saw 'default' (a reserved word).");
		th.addError(10, 3, "Expected an identifier and instead saw 'in' (a reserved word).");
		th.addError(11, 10, "Expected an identifier and instead saw 'in' (a reserved word).");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.newTest();
		th.addError(6, 5, "Expected an identifier and instead saw 'default' (a reserved word).");
		th.addError(7, 10, "Expected an identifier and instead saw 'new' (a reserved word).");
		th.addError(8, 12, "Expected an identifier and instead saw 'class' (a reserved word).");
		th.addError(11, 10, "Expected an identifier and instead saw 'in' (a reserved word).");
		th.test(src, new LinterOptions()); // es5
	}
	
	@Test
	public void testCatchBlocks()
	{
		String src = th.readFile("src/test/resources/fixtures/gh247.js");
		
		th.addError(19, 13, "'w' is already defined.");
		th.addError(35, 19, "'u2' used out of scope.");
		th.addError(36, 19, "'w2' used out of scope.");
		th.test(src, new LinterOptions().set("es3", true).set("undef", true).set("devel", true));
		
		src = th.readFile("src/test/resources/fixtures/gh618.js");
		
		th.newTest();
		th.addError(5, 11, "Value of 'x' may be overwritten in IE 8 and earlier.");
		th.addError(15, 11, "Value of 'y' may be overwritten in IE 8 and earlier.");
		th.test(src, new LinterOptions().set("es3", true).set("undef", true).set("devel", true));
		
		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("undef", true).set("devel", true).set("node", true));
		
		String code = "try {} catch ({ message }) {}";
		th.newTest("destructuring in catch blocks' parameter");
		th.test(code, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testNumericParams()
	{
		th.test("/*jshint maxparams:4, indent:3, maxlen:false */");
		
		th.newTest();
		th.addError(1, 1, "Expected a small integer or 'false' and instead saw 'face'.");
		th.test("/*jshint maxparams:face */");
	}
	
	@Test
	public void testForIn()
	{
		String[] src = {
			"(function (o) {",
			"for (var i in o) { i(); }",
			"}());"
		};
		
		th.test(src, new LinterOptions().set("es3", true));
		
		src = new String[]{
			"(function (o) {",
			"for (i in o) { i(); }",
			"}());"
		};
		
		th.newTest();
		th.addError(2, 6, "Creating global 'for' variable. Should be 'for (var i ...'.");
		th.test(src, new LinterOptions().set("es3", true));
		
		src = new String[]{
			"(function (o) {",
			"for ('i' in o) { i(); }",
			"}());"
		};
		
		th.newTest();
		th.addError(2, 10, "Bad assignment.");
		th.test(src);
		
		src = new String[]{
			"(function (o) {",
			"for (i, j in o) { i(); }",
			"for (var x, u in o) { x(); }",
			"for (z = 0 in o) { z(); }",
			"for (var q = 0 in o) { q(); }",
			"})();"
		};
		
		th.newTest("bad lhs errors");
		th.addError(2, 7, "Invalid for-in loop left-hand-side: more than one ForBinding.");
		th.addError(3, 11, "Invalid for-in loop left-hand-side: more than one ForBinding.");
		th.addError(4, 6, "Invalid for-in loop left-hand-side: initializer is forbidden.");
		th.addError(5, 6, "Invalid for-in loop left-hand-side: initializer is forbidden.");
		th.test(src);
		
		src = new String[]{
				"(function (o) {",
				"for (let i, j in o) { i(); }",
				"for (const x, u in o) { x(); }",
				"for (let z = 0 in o) { z(); }",
				"for (const q = 0 in o) { q(); }",
				"})();"
		};
		
		th.newTest("bad lhs errors (lexical)");
		th.addError(2, 11, "Invalid for-in loop left-hand-side: more than one ForBinding.");
		th.addError(3, 13, "Invalid for-in loop left-hand-side: more than one ForBinding.");
		th.addError(4, 6, "Invalid for-in loop left-hand-side: initializer is forbidden.");
		th.addError(5, 6, "Invalid for-in loop left-hand-side: initializer is forbidden.");
		th.test(src, new LinterOptions().set("esnext", true));
		
		th.newTest("Left-hand side as MemberExpression");
		th.test(new String[]{
			"for (x.y in {}) {}",
			"for (x[z] in {}) {}"
		});
		
		th.newTest("Left-hand side as MemberExpression (invalid)");
		th.addError(1, 10, "Bad assignment.");
		th.addError(2, 13, "Bad assignment.");
		th.test(new String[]{
			"for (x+y in {}) {}",
			"for ((this) in {}) {}"
		});
	}
	
	@Test
	public void testRegexArray()
	{
		String src = th.readFile("src/test/resources/fixtures/regex_array.js");
		
		th.test(src, new LinterOptions().set("es3", true));
	}
	
	// Regression test for GH-1070
	@Test
	public void testUndefinedAssignment()
	{
		String[] src = {
			"var x = undefined;",
			"const y = undefined;",
			"let z = undefined;",
			"for(var a = undefined; a < 9; a++) {",
			"  var b = undefined;", // necessary - see gh-1191
			"  const c = undefined;",
			"  let d = undefined;",
			"  var e = function() {",
			"    var f = undefined;",
			"    const g = undefined;",
			"    let h = undefined;",
			"  };",
			"}",
			"// jshint -W080",
			"var i = undefined;",
			"const j = undefined;",
			"let k = undefined;",
			"// jshint +W080",
			"var l = undefined === 0;",
			"const m = undefined === 0;",
			"let n = undefined === 0;",
			"let [ o = undefined === 0 ] = [];",
			"[ o = undefined === 0] = [];",
			"let { p = undefined === 0, x: q = undefined === 0 } = {};",
			"({ p = undefined === 0, x: q = undefined === 0 } = {});"
		};
		
		th.addError(1, 5, "It's not necessary to initialize 'x' to 'undefined'.");
		th.addError(2, 7, "It's not necessary to initialize 'y' to 'undefined'.");
		th.addError(3, 5, "It's not necessary to initialize 'z' to 'undefined'.");
		th.addError(4, 9, "It's not necessary to initialize 'a' to 'undefined'.");
		th.addError(6, 9, "It's not necessary to initialize 'c' to 'undefined'.");
		th.addError(7, 7, "It's not necessary to initialize 'd' to 'undefined'.");
		th.addError(9, 9, "It's not necessary to initialize 'f' to 'undefined'.");
		th.addError(10, 11, "It's not necessary to initialize 'g' to 'undefined'.");
		th.addError(11, 9, "It's not necessary to initialize 'h' to 'undefined'.");
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6Modules()
	{
		String src = th.readFile("src/test/resources/fixtures/es6-import-export.js");
		
		String[][] importConstErrors = {
			{"51", "1", "Attempting to override '$' which is a constant."},
			{"52", "1", "Attempting to override 'emGet' which is a constant."},
			{"53", "1", "Attempting to override 'one' which is a constant."},
			{"54", "1", "Attempting to override '_' which is a constant."},
			{"55", "1", "Attempting to override 'ember2' which is a constant."},
			{"57", "8", "'$' has already been declared."},
			{"58", "17", "'emGet' has already been declared."},
			{"58", "24", "'set' has already been declared."},
			{"59", "21", "'_' has already been declared."},
			{"60", "13", "'ember2' has already been declared."}
		};
		
		th.addError(74, 1, "Empty export: this is unnecessary and can be removed.");
		th.addError(75, 1, "Empty export: consider replacing with `import 'source';`.");
		for (String[] error : importConstErrors)
		{
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}
		th.test(src, new LinterOptions().set("esnext", true));
		
		th.addError(3, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(5, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(6, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(8, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(9, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(10, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(22, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(26, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(30, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(31, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(32, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(36, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(40, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(44, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(46, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(47, 8, "'class' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(48, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(48, 16, "'class' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(47, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(46, 8, "'class' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(57, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(58, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(59, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(60, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(65, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(67, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(67, 16, "'function*' is only available in ES6 (use 'esversion: 6').");
		th.addError(67, 26, "'yield' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.addError(70, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(71, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.addError(74, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(75, 1, "'export' is only available in ES6 (use 'esversion: 6').");
		th.addError(76, 1, "'import' is only available in ES6 (use 'esversion: 6').");
		th.test(src);
		
		String[] src2 = {
				"var a = {",
				"import: 'foo',",
				"export: 'bar'",
				"};"
		};
		
		th.newTest();
		th.test(src2);
		
		// See gh-3055 "Labels Break JSHint"
		th.newTest("following labeled block");
		th.test(new String[]{
			"label: {}",
			"export function afterLabelExported() {}",
			"import afterLabelImported from 'elsewhere';"
		}, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testES6ModulesNamedExportsAffectUnused()
	{
		// Named Exports should count as used
		String[] src1 = {
			"var a = {",
			"  foo: 'foo',",
			"  bar: 'bar'",
			"};",
			"var x = 23;",
			"var z = 42;",
			"let c = 2;",
			"const d = 7;",
			"export { c, d };",
			"export { a, x };",
			"export var b = { baz: 'baz' };",
			"export function boo() { return z; }",
			"export class MyClass { }",
			"export var varone = 1, vartwo = 2;",
			"export const constone = 1, consttwo = 2;",
			"export let letone = 1, lettwo = 2;",
			"export var v1u, v2u;",
			"export let l1u, l2u;",
			"export const c1u, c2u;",
			"export function* gen() { yield 1; }"
		};
		
		th.addError(19, 14, "const 'c1u' is initialized to 'undefined'.");
		th.addError(19, 19, "const 'c2u' is initialized to 'undefined'.");
		th.test(src1, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testConstRedeclaration()
	{
		// consts cannot be redeclared, but they can shadow
		String[] src = {
				"const a = 1;",
				"const a = 2;",
				"if (a) {",
				"  const a = 3;",
				"}",
				"for(const a in a) {",
				"  const a = 4;",
				"}",
				"function a() {",
				"}",
				"function b() {",
				"}",
				"const b = 1;"
		};
		
		th.addError(2, 7, "'a' has already been declared.");
		th.addError(6, 16, "'a' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(9, 10, "'a' has already been declared.");
		th.addError(13, 7, "'b' has already been declared.");
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testTypeofInTDZ()
	{
		String[] src = {
			"let a = typeof b;", // error, use in TDZ
			"let b;",
			"function d() { return typeof c; }", // d may be called after declaration, no error
			"let c = typeof e;", // e is not in scope, no error
			"{",
			"  let e;",
			"}"
		};
		
		th.addError(2, 5, "'b' was used before it was declared, which is illegal for 'let' variables.");
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testConstModification()
	{
		String[] src = {
				"const a = 1;",
				"const b = { a: 2 };",
				// const errors
				"a = 2;",
				"b = 2;",
				"a++;",
				"--a;",
				"a += 1;",
				"let y = a = 3;",
				// valid const access
				"b.a++;",
				"--b.a;",
				"b.a = 3;",
				"a.b += 1;",
				"const c = () => 1;",
				"c();",
				"const d = [1, 2, 3];",
				"d[0] = 2;",
				"let x = -a;",
				"x = +a;",
				"x = a + 1;",
				"x = a * 2;",
				"x = a / 2;",
				"x = a % 2;",
				"x = a & 1;",
				"x = a ^ 1;",
				"x = a === true;",
				"x = a == 1;",
				"x = a !== true;",
				"x = a != 1;",
				"x = a > 1;",
				"x = a >= 1;",
				"x = a < 1;",
				"x = a <= 1;",
				"x = 1 + a;",
				"x = 2 * a;",
				"x = 2 / a;",
				"x = 2 % a;",
				"x = 1 & a;",
				"x = 1 ^ a;",
				"x = true === a;",
				"x = 1 == a;",
				"x = true !== a;",
				"x = 1 != a;",
				"x = 1 > a;",
				"x = 1 >= a;",
				"x = 1 < a;",
				"x = 1 <= a;",
				"x = typeof a;",
				"x = a.a;",
				"x = a[0];",
				"delete a.a;",
				"delete a[0];",
				"new a();",
				"new a;",
				"function e() {",
				"  f++;",
				"}",
				"const f = 1;",
				"e();"
		};
		
		th.addError(3, 1, "Attempting to override 'a' which is a constant.");
		th.addError(4, 1, "Attempting to override 'b' which is a constant.");
		th.addError(5, 1, "Attempting to override 'a' which is a constant.");
		th.addError(6, 3, "Attempting to override 'a' which is a constant.");
		th.addError(7, 1, "Attempting to override 'a' which is a constant.");
		th.addError(8, 9, "Attempting to override 'a' which is a constant.");
		th.addError(8, 9, "You might be leaking a variable (a) here.");
		th.addError(53, 5, "Missing '()' invoking a constructor.");
		th.addError(55, 3, "Attempting to override 'f' which is a constant.");
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testClassDeclarationExport()
	{
		String source = th.readFile("src/test/resources/fixtures/class-declaration.js");
		
		th.test(source, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testFunctionDeclarationExport()
	{
		String source = th.readFile("src/test/resources/fixtures/function-declaration.js");
		
		th.test(source, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testClassIsBlockScoped()
	{
		String[] code = {
			"new A();", // use in TDZ
			"class A {}",
			"class B extends C {}", // use in TDZ
			"class C {}",
			"new D();", // not defined
			"let E = class D {" +
			"  constructor() { D.static(); }",
			"  myfunc() { return D; }",
			"};",
			"new D();", // not defined
			"if (true) {",
			"  class F {}",
			"}",
			"new F();" // not defined
		};
		
		th.addError(2, 7, "'A' was used before it was declared, which is illegal for 'class' variables.");
		th.addError(4, 7, "'C' was used before it was declared, which is illegal for 'class' variables.");
		th.addError(5, 5, "'D' is not defined.");
		th.addError(9, 5, "'D' is not defined.");
		th.addError(13, 5, "'F' is not defined.");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testES6ModulesNamedExportsAffectUndef()
	{
		// The identifier "foo" is expected to have been defined in the scope
		// of this file in order to be exported.
		// The example below is roughly similar to this Common JS:
		//
		//	   exports.foo = foo;
		//
		// Thus, the "foo" identifier should be seen as undefined.
		String[] src1 = {
			"export { foo };"
		};
		
		th.addError(1, 10, "'foo' is not defined.");
		th.test(src1, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testES6ModulesThroughExportDoNotAffectUnused()
	{
		// "Through" exports do not alter the scope of this file, but instead pass
		// the exports from one source on through this source.
		// The example below is roughly similar to this Common JS:
		//
		//	   var foo;
		//	   exports.foo = require('source').foo;
		//
		// Thus, the "foo" identifier should be seen as unused.
		String[] src1 = {
			"var foo;",
			"export { foo } from \"source\";"
		};
		
		th.addError(1, 5, "'foo' is defined but never used.");
		th.test(src1, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testES6ModulesThroughExportDoNotAffectUndef()
	{
		// "Through" exports do not alter the scope of this file, but instead pass
		// the exports from one source on through this source.
		// The example below is roughly similar to this Common JS:
		//
		//	   exports.foo = require('source').foo;
		//	   var bar = foo;
		//
		// Thus, the "foo" identifier should be seen as undefined.
		String[] src1 = {
			"export { foo } from \"source\";",
			"var bar = foo;"
		};
		
		th.addError(2, 11, "'foo' is not defined.");
		th.test(src1, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testES6ModulesDefaultExportsAffectUnused()
	{
		// Default Exports should count as used
		String[] src1 = {
				"var a = {",
				"  foo: 'foo',",
				"  bar: 'bar'",
				"};",
				"var x = 23;",
				"var z = 42;",
				"export default { a: a, x: x };",
				"export default function boo() { return x + z; }",
				"export default class MyClass { }"
		};
		
		th.test(src1, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testES6ModulesDefaultExportAssignmentExpr()
	{
		// The identifier in the exported AssignmentExpression should not be
		// interpreted as a declaration.
		String[] src = {
			"let x = 1;",
			"export default -x;"
		};
		
		th.test(src, new LinterOptions().set("unused", true).set("esnext", true));
	}
	
	@Test
	public void testES6ModulesNameSpaceImportsAffectUnused()
	{
		String[] src = {
				"import * as angular from 'angular';"
		};
		
		th.addError(1, 13, "'angular' is defined but never used.");
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testES6TemplateLiterals()
	{
		String src = th.readFile("src/test/resources/fixtures/es6-template-literal.js");
		
		th.addError(14, 16, "Octal literals are not allowed in strict mode.");
		th.addError(21, 20, "Unclosed template literal.");
		th.test(src, new LinterOptions().set("esnext", true));
		th.test("/* jshint esnext: true */" + src);
	}
	
	@Test
	public void testES6TaggedTemplateLiterals()
	{
		String src = th.readFile("src/test/resources/fixtures/es6-template-literal-tagged.js");
		
		th.addError(16, 19, "Octal literals are not allowed in strict mode.");
		th.addError(23, 23, "Unclosed template literal.");
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6TemplateLiteralsUnused()
	{
		String[] src = {
				"var a = 'hello';",
				"alert(`${a} world`);"
		};
		
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testES6TaggedTemplateLiteralsUnused()
	{
		String[] src = {
			"function tag() {}",
			"var a = 'hello';",
			"alert(tag`${a} world`);"
		};
		
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
	}
	
	@Test
	public void testES6TemplateLiteralsUndef()
	{
		String[] src = {
				"/* global alert */",
				"alert(`${a} world`);"
		};
		
		th.addError(2, 10, "'a' is not defined.");
		th.test(src, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testES6TaggedTemplateLiteralsUndef()
	{
		String[] src = {
			"/* global alert */",
			"alert(tag`${a} world`);"
		};
		
		th.addError(2, 7, "'tag' is not defined.");
		th.addError(2, 13, "'a' is not defined.");
		th.test(src, new LinterOptions().set("esnext", true).set("undef", true));
	}
	
	@Test
	public void testES6TemplateLiteralMultiline()
	{
		String[] src = {
				"let multiline = `",
				"this string spans",
				"multiple lines",
				"`;"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6TemplateLiteralsAreNotDirectives()
	{
		String[] src = {
			"function fn() {",
			"`use strict`;",
			"return \"\\077\";",
			"}"
		};
		
		th.addError(2, 1, "Expected an assignment or function call and instead saw an expression.");
		th.test(src, new LinterOptions().set("esnext", true));
		
		String[] src2 = {
			"function fn() {",
			"`${\"use strict\"}`;",
			"return \"\\077\";",
			"}"
		};
		
		th.newTest();
		th.addError(2, 16, "Expected an assignment or function call and instead saw an expression.");
		th.test(src2, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6TemplateLiteralReturnValue()
	{
		String[] src = {
			"function sayHello(to) {",
			"  return `Hello, ${to}!`;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
		
		src = new String[]{
			"function* sayHello(to) {",
			"  yield `Hello, ${to}!`;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6TemplateLiteralMultilineReturnValue()
	{
		String[] src = {
			"function sayHello(to) {",
			"  return `Hello, ",
			"	 ${to}!`;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
		
		src = new String[]{
			"function* sayHello(to) {",
			"  yield `Hello, ",
			"	 ${to}!`;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6TaggedTemplateLiteralMultilineReturnValue()
	{
		String[] src = {
			"function tag() {}",
			"function sayHello(to) {",
			"  return tag`Hello, ",
			"	 ${to}!`;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
		
		src = new String[]{
			"function tag() {}",
			"function* sayHello(to) {",
			"  yield tag`Hello, ",
			"	 ${to}!`;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6TemplateLiteralMultilineReturnValueWithFunctionCall()
	{
		String[] src = {
			"function sayHello() {",
			"  return `Helo",
			"	   monkey`",
			"	 .replace(\'l\', \'ll\');",
			"}",
			"print(sayHello());"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testES6TaggedTemplateLiteralMultilineReturnValueWithFunctionCall()
	{
		String[] src = {
			"function tag() {}",
			"function sayHello() {",
			"  return tag`Helo",
			"	 monkey!!`",
			"	 .replace(\'l\', \'ll\');",
			"}",
			"print(sayHello());"
		};
		
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testMultilineReturnValueStringLiteral()
	{
		String[] src = {
			"function sayHello(to) {",
			"  return \"Hello, \\",
			"	 \" + to;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("multistr", true));
		
		src = new String[]{
			"function* sayHello(to) {",
			"  yield \"Hello, \\",
			"	 \" + to;",
			"}",
			"print(sayHello(\"George\"));"
		};
		
		th.test(src, new LinterOptions().set("esnext", true).set("multistr", true));
	}
	
	@Test
	public void testES6ExportStarFrom()
	{
		String src = th.readFile("src/test/resources/fixtures/es6-export-star-from.js");
		
		th.addError(2, 10, "Expected 'from' and instead saw 'foo'.");
		th.addError(2, 13, "Expected '(string)' and instead saw ';'.");
		th.addError(2, 14, "Missing semicolon.");
		th.addError(3, 15, "Expected '(string)' and instead saw '78'.");
		th.test(src, new LinterOptions().set("esnext", true));
	}
	
	@Test
	public void testPotentialVariableLeak()
	{
		String a = th.readFile("src/test/resources/fixtures/leak.js");
		String b = th.readFile("src/test/resources/fixtures/gh1802.js");
		
		// Real Error
		th.addError(2, 11, "You might be leaking a variable (b) here.");
		th.addError(3, 13, "You might be leaking a variable (d) here.");
		th.addError(4, 11, "You might be leaking a variable (f) here.");
		th.test(a, new LinterOptions().set("esnext", true));
		
		th.newTest();
		
		// False Positive
		th.test(b);
	}
	
	@Test
	public void testDefaultArguments()
	{
		String src = th.readFile("src/test/resources/fixtures/default-arguments.js");
		
		th.addError(14, 39, "'bar' is not defined.");
		th.addError(14, 32, "'num3' was used before it was declared, which is illegal for 'param' variables.");
		th.addError(15, 32, "'num4' was used before it was declared, which is illegal for 'param' variables.");
		th.addError(18, 41, "Regular parameters should not come after default parameters.");
		th.addError(27, 10, "'c' is not defined.");
		th.addError(33, 4, "'d' was used before it was defined.");
		th.addError(36, 16, "'e' was used before it was declared, which is illegal for 'param' variables.");
		th.test(src, new LinterOptions().set("esnext", true).set("undef", true).set("latedef", true));
		
		th.newTest();
		th.addError(14, 32, "'num3' was used before it was declared, which is illegal for 'param' variables.");
		th.addError(15, 32, "'num4' was used before it was declared, which is illegal for 'param' variables.");
		th.addError(18, 41, "Regular parameters should not come after default parameters.");
		th.addError(36, 16, "'e' was used before it was declared, which is illegal for 'param' variables.");
		th.test(src, new LinterOptions().set("moz", true));
		
		th.newTest();
		th.addError(7, 27, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(7, 35, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(11, 36, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(12, 37, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(13, 37, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(14, 37, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(14, 32, "'num3' was used before it was declared, which is illegal for 'param' variables.");
		th.addError(15, 37, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(15, 32, "'num4' was used before it was declared, which is illegal for 'param' variables.");
		th.addError(18, 37, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(18, 41, "Regular parameters should not come after default parameters.");
		th.addError(26, 18, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(31, 18, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(33, 6, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(35, 18, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(36, 18, "'default parameters' is only available in ES6 (use 'esversion: 6').");
		th.addError(36, 16, "'e' was used before it was declared, which is illegal for 'param' variables.");
		th.test(src);
	}
	
	@Test
	public void testDuplicateParamNames()
	{
		String[] src = {
			"(function() {",
			"  (function(a, a) { // warns only with shadow",
			"  })();",
			"})();",
			"(function() {",
			"  'use strict';",
			"  (function(a, a) { // errors because of strict mode",
			"  })();",
			"})();",
			"(function() {",
			"  (function(a, a) { // errors because of strict mode",
			"  'use strict';",
			"  })();",
			"})();",
			"(function() {",
			"  'use strict';",
			"  (function(a, a) { // errors *once* because of strict mode",
			"  'use strict';",
			"  })();",
			"})();"
		};
		
		th.addError(7, 13, "'a' has already been declared.");
		th.addError(11, 13, "'a' has already been declared.");
		th.addError(17, 13, "'a' has already been declared.");
		th.addError(18, 3, "Unnecessary directive \"use strict\".");
		th.test(src, new LinterOptions().set("shadow", true));
		
		th.newTest();
		th.addError(2, 13, "'a' is already defined.");
		th.addError(7, 13, "'a' has already been declared.");
		th.addError(11, 13, "'a' has already been declared.");
		th.addError(17, 13, "'a' has already been declared.");
		th.addError(18, 3, "Unnecessary directive \"use strict\".");
		th.test(src, new LinterOptions().set("shadow", "inner"));
		th.test(src, new LinterOptions().set("shadow", "outer"));
		th.test(src, new LinterOptions().set("shadow", false));
		
		src = new String[] {
			"void ((x, x) => null);",
			"void ((x, x) => {});",
			"void ((x, x) => { 'use strict'; });",
			"function f() {",
			"  'use strict';",
			"  void ((x, x) => null);",
			"  void ((x, x) => {});",
			"  void ((x, x) => { 'use strict'; });",
			"}"
		};
		
		th.newTest("Arrow functions - strict mode restriction");
		th.addError(1, 8, "'x' has already been declared.");
		th.addError(2, 8, "'x' has already been declared.");
		th.addError(3, 8, "'x' has already been declared.");
		th.addError(6, 10, "'x' has already been declared.");
		th.addError(7, 10, "'x' has already been declared.");
		th.addError(8, 10, "'x' has already been declared.");
		th.addError(8, 21, "Unnecessary directive \"use strict\".");
		th.test(src, new LinterOptions().set("esversion", 6));
	}
	
	// Issue #1324: Make sure that we're not mutating passed options object.
	@Test
	public void testClonePassedObjects()
	{
		JSHint jshint = new JSHint();
		
		LinterOptions options = new LinterOptions().addPredefineds("sup");
		jshint.lint("", options);
		
		assertTrue(options.getPredefineds().size() == 1);
	}
	
	@Test
	public void testMagicProtoVariable()
	{
		JSHint jshint = new JSHint();
		jshint.lint("__proto__ = 1;");
	}
	
	// Issue #1371: column number at end of non-strict comparison (for usability reasons)
	@Test
	public void testColumnNumAfterNonStrictComparison()
	{
		String src = "if (1 == 1) {\n" +
				"  var foo = 2;\n" +
				"  if (1 != 1){\n" +
				"	 var bar = 3;\n" +
				"  }\n"+
				"}";
		
		th.addError(1, 9, "Expected '===' and instead saw '=='.");
		th.addError(3, 11, "Expected '!==' and instead saw '!='.");
		th.test(src, new LinterOptions().set("eqeqeq", true));
	}
	
	@Test
	public void testArrayPrototypeExtensions()
	{
		JSHint jshint = new JSHint();
		
		String arrayPrototype = "Array.prototype.undefinedPrototypeProperty = undefined;\n";
		jshint.lint(arrayPrototype + "var x = 123;\nlet y = 456;\nconst z = 123;");
	}
	
	// Issue #1446, PR #1688
	@Test
	public void testIncorrectJsonDetection()
	{
		String src = th.readFile("src/test/resources/fixtures/mappingstart.js");
		// Without the bug fix, a JSON lint error will be raised because the parser
		// thinks it is rendering JSON instead of JavaScript.
		th.test(src);
	}
	
	@Test
	public void testEscapedReservedWords()
	{
		String[] code = {
				"var v\u0061r = 42;",
				"alert(va\u0072);"
		};
		
		th.addError(1, 5, "Expected an identifier and instead saw 'var' (a reserved word).");
		th.addError(2, 7, "Expected an identifier and instead saw 'var'.");
		th.test(code);
	}
	
	@Test
	public void testUnnamedFuncStatement()
	{
		th.addError(1, 9, "Missing name in function declaration.");
		th.test("function() {}");
		
		th.newTest("with 'unused' option");
		th.addError(1, 9, "Missing name in function declaration.");
		th.test("function() {}", new LinterOptions().set("unused", true));
	}
	
	// GH-1976 "Fixed set property 'type' of undefined in `if` blockstmt"
	@Test
	public void testUnCleanedForinifcheckneeded()
	{
		JSHint jshint = new JSHint();
		
		String[] forinCode = {
				"for (var key in a) {",
				"  console.log(key);",
				"}"
		};
		
		String[] ifCode = {
				"if(true) {",
				"}"
		};
		
		try
		{
			jshint.lint(forinCode, new LinterOptions().set("maxerr", 1).set("forin", true));
			// Prior to the fix, if the final `forin` check reached the `maxerr` limit,
			// the internal `state.forinifcheckneeded` maintained its previous value
			// and triggered an error in subsequent invocations of JSHint.
			jshint.lint(ifCode, new LinterOptions().set("maxerr", 1).set("forin", true));
		}
		catch (JSHintException e)
		{
			assertTrue(false, "Exception was thrown");
		}
	}
	
	// gh-738 "eval" as an object key should not cause `W061` warnngs
	@Test
	public void testPermitEvalAsKey()
	{
		String srcNode = th.readFile("src/test/resources/fixtures/gh-738-node.js");
		String srcBrowser = th.readFile("src/test/resources/fixtures/gh-738-browser.js");
		// global calls to eval should still cause warning.
		// test a mixture of permitted and disallowed calls
		// `global#eval` in `node:true` should still cause warning
		// `(document|window)#eval` in `browser:true` should still cause warning
		
		// browser globals
		th.addError(17, 1, "eval can be harmful.");
		th.addError(19, 12, "eval can be harmful.");
		th.addError(20, 14, "eval can be harmful.");
		th.addError(22, 14, "eval can be harmful.");
		th.addError(23, 16, "eval can be harmful.");
		th.addError(25, 10, "eval can be harmful.");
		th.test(srcBrowser, new LinterOptions().set("browser", true));
		
		// node globals
		th.newTest();
		th.addError(18, 14, "eval can be harmful.");
		th.addError(19, 12, "eval can be harmful.");
		th.addError(20, 1, "eval can be harmful.");
		th.addError(22, 10, "eval can be harmful.");
		th.test(srcNode, new LinterOptions().set("node", true));
	}
	
	// gh-2194 jshint confusing arrays at beginning of file with JSON
	@Test
	public void testBeginningArraysAreNotJSON()
	{
		String src = th.readFile("src/test/resources/fixtures/gh-2194.js");
		
		th.test(src);
	}
	
	@Test
	public void testLabelsOutOfScope()
	{
		String[] src = {
			"function a() {",
			"  if (true) {",
			"    bar: switch(2) {",
			"    }",
			"    foo: switch(1) {",
			"      case 1:",
			"        (function () {",
			"          baz: switch(3) {",
			"            case 3:",
			"              break foo;",
			"            case 2:",
			"              break bar;",
			"            case 3:",
			"              break doesnotexist;",
			"          }",
			"        })();",
			"        if (true) {",
			"          break foo;",
			"        }",
			"        break foo;",
			"      case 2:",
			"        break bar;",
			"      case 3:",
			"        break baz;",
			"    }",
			"  }",
			"}"
		};
		
		th.addError(10, 21, "'foo' is not a statement label.");
		th.addError(12, 21, "'bar' is not a statement label.");
		th.addError(14, 21, "'doesnotexist' is not a statement label.");
		th.addError(22, 15, "'bar' is not a statement label.");
		th.addError(24, 15, "'baz' is not a statement label.");
		th.test(src);
		
		// See gh-3055 "Labels Break JSHint"
		th.newTest("following labeled block");
		th.addError(2, 7, "'x' is not a statement label.");
		th.test(new String[]{
			"x: {}",
			"break x;"
		});
	}
	
	@Test
	public void testLabelThroughCatch()
	{
		String[] src = {
			"function labelExample() {",
			"  'use strict';",
			"  var i;",
			"  example:",
			"	 for (i = 0; i < 10; i += 1) {",
			"	   try {",
			"		 if (i === 5) {",
			"		   break example;",
			"		 } else {",
			"		   throw new Error();",
			"		 }",
			"	   } catch (e) {",
			"		 continue example;",
			"	   }",
			"	 }",
			"}"
		};
		
		th.test(src);
	}
	
	@Test
	public void testLabelDoesNotExistInGlobalScope()
	{
		String[] src = {
			"switch(1) {",
			"  case 1:",
			"    break nonExistent;",
			"}"
		};
		
		th.addError(3, 11, "'nonExistent' is not a statement label.");
		th.test(src);
	}
	
	@Test
	public void testLabeledBreakWithoutLoop()
	{
		String[] src = {
			"foo: {",
			"  break foo;",
			"}"
		};
		
		th.test(src);
	}
	
	// ECMAScript 5.1 Â§ 12.7: labeled continue must refer to an enclosing
	// IterationStatement, as opposed to labeled break which is only required to
	// refer to an enclosing Statement.
	@Test
	public void testLabeledContinueWithoutLoop()
	{
		String[] src = {
			"foo: switch (i) {",
			"  case 1:",
			"    continue foo;",
			"}"
		};
		
		th.addError(3, 14, "Unexpected 'continue'.");
		th.test(src);
	}
	
	@Test
	public void testUnlabeledBreakWithoutLoop()
	{
		String[] src = {
			"if (1 == 1) {",
			"  break;",
			"}"
		};
		
		th.addError(2, 8, "Unexpected 'break'.");
		th.test(src);
	}
	
	@Test
	public void testUnlabeledContinueWithoutLoop()
	{
		String[] src = {
			"switch (i) {",
			"  case 1:",
			"    continue;", // breakage but not loopage
			"}",
			"continue;"
		};
		
		th.addError(3, 13, "Unexpected 'continue'.");
		th.addError(5, 9, "Unexpected 'continue'.");
		th.test(src);
	}
	
	@Test
	public void testLabelsContinue()
	{
		String[] src = {
			"exists: while(true) {",
			"  if (false) {",
			"	 continue exists;",
			"  }",
			"  continue nonExistent;",
			"}"
		};
		
		th.addError(5, 12, "'nonExistent' is not a statement label.");
		th.test(src);
	}
	
	@Test
	public void testCatchWithNoParam()
	{
		String[] src = {
			"try{}catch(){}"
		};
		
		th.addError(1, 12, "Expected an identifier and instead saw ')'.");
		th.test(src);
	}
	
	@Test
	public void testTryWithoutCatch()
	{
		String[] src = {
			"try{}",
			"if (true) { console.log(); }"
		};
		
		th.addError(2, 1, "Expected 'catch' and instead saw 'if'.");
		th.test(src);
		
		src = new String[] {
			"try{}"
		};
		
		th.newTest();
		th.addError(1, 5, "Expected 'catch' and instead saw ''.");
		th.test(src);
	}
	
	@Test
	public void testGH1920()
	{
		String[] src = {
			"for (var key in objects) {",
			"  if (!objects.hasOwnProperty(key)) {",
			"	 switch (key) {",
			"	 }",
			"  }",
			"}"
		};
		
		th.addError(1, 1, "The body of a for in should be wrapped in an if statement to filter unwanted properties from the prototype.");
		th.test(src, new LinterOptions().set("forin", true));
	}
	
	@Test
	public void testDuplicateProto()
	{
		String[] src = {
			"(function() {",
			"  var __proto__;",
			"  var __proto__;",
			"}());"
		};
		
		// JSHINT_TODO: Enable this expected warning in the next major release
		th.newTest("Duplicate `var`s");
		//th.addError(3, 23232323, "'__proto__' is already defined.");
		th.test(src, new LinterOptions().set("proto", true));
		
		src = new String[] {
			"(function() {",
			"  let __proto__;",
			"  let __proto__;",
			"}());"
		};
		
		th.newTest("Duplicate `let`s");
		th.addError(3, 7, "'__proto__' has already been declared.");
		th.test(src, new LinterOptions().set("proto", true).set("esnext", true));
		
		src = new String[] {
			"(function() {",
			"  const __proto__ = null;",
			"  const __proto__ = null;",
			"}());"
		};
		
		th.newTest("Duplicate `const`s");
		th.addError(3, 9, "'__proto__' has already been declared.");
		th.test(src, new LinterOptions().set("proto", true).set("esnext", true));
		
		src = new String[] {
			"void {",
			"  __proto__: null,",
			"  __proto__: null",
			"};"
		};
		
		// JSHINT_TODO: Enable this expected warning in the next major release
		th.newTest("Duplicate keys (data)");
		//th.addError(3, 23232323, "Duplicate key '__proto__'.");
		th.test(src, new LinterOptions().set("proto", true));
		
		src = new String[] {
			"void {",
			"  __proto__: null,",
			"  get __proto__() {}",
			"};"
		};
		
		// JSHINT_TODO: Enable this expected warning in the next major release
		th.newTest("Duplicate keys (data and accessor)");
		//th.addError(3, 23232323, "Duplicate key '__proto__'.");
		th.test(src, new LinterOptions().set("proto", true));
		
		src = new String[] {
			"__proto__: while (true) {",
			"  __proto__: while (true) {",
			"	 break;",
			"  }",
			"}"
		};
		
		th.newTest("Duplicate labels");
		th.addError(2, 12, "'__proto__' has already been declared.");
		th.test(src, new LinterOptions().set("proto", true));
	}
	
	@Test
	public void testGH2761()
	{
		String[] code = {
			"/* global foo: false */",
			"foo = 2;",
			"// jshint -W020",
			"foo = 3;",
			"// jshint +W020",
			"foo = 4;"
		};
		
		th.newTest("W020");
		th.addError(2, 1, "Read only.");
		th.addError(6, 1, "Read only.");
		th.test(code);
		
		code = new String[] {
			"function a() {}",
			"a = 2;",
			"// jshint -W021",
			"a = 3;",
			"// jshint +W021",
			"a = 4;"
		};
		
		th.newTest("W021");
		th.addError(2, 1, "Reassignment of 'a', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.addError(6, 1, "Reassignment of 'a', which is a function. Use 'var' or 'let' to declare bindings that may change.");
		th.test(code);
	}
	
	@Test
	public void testGH2838()
	{
		String[] code = {
			"function foo() {",
			"  return a + b;",
			"}",
			"function bar() {",
			"  return a + b;",
			"}",
			"let a = 1;",
			"const b = 2;"
		};
		
		th.test(code, new LinterOptions().set("esversion", 6));
		
		code = new String[] {
			"function x() {",
			"  return c;",
			"}",
			"void c;",
			"let c;"
		};
		
		th.newTest("Same-scope reference following sub-scope reference");
		th.addError(5, 5, "'c' was used before it was declared, which is illegal for 'let' variables.");
		th.test(code, new LinterOptions().set("esversion", 6));
		
		code = new String[] {
			"function x() {",
			"  return d;",
			"}",
			"({ d } = {});",
			"let d;"
		};
		
		th.newTest("Same-scope assignment following sub-scope reference");
		th.addError(5, 5, "'d' was used before it was declared, which is illegal for 'let' variables.");
		th.test(code, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testDestructuringInSetterParameter()
	{
		th.test(new String[] {
			"var a = {",
			"  get x() {},",
			"  set x({ a, b }) {}",
			"};"
		}, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testTDZWithinInitializerOfLexicalDeclarations()
	{
		String[] code = {
			"let a = a;",
			"const b = b;",
			"let c = () => c;",
			"const d = () => d;",
			// line 5
			"let e = {",
			"  x: e,",
			"  y: () => e",
			"};",
			"const f = {",
			"  x: f,",
			"  y: () => f",
			"};",
			// line 13
			"let g, h = g;",
			"const i = 0, j = i;",
			"let [ k, l ] = l;",
			"const [ m, n ] = n;",
			// line 17
			"let o = (() => o) + o;",
			"const p = (() => p) + p;"
		};
		
		th.addError(1, 9, "'a' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(2, 11, "'b' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(6, 6, "'e' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(10, 6, "'f' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(15, 16, "'l' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(16, 18, "'n' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(17, 21, "'o' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(18, 23, "'p' was used before it was declared, which is illegal for 'const' variables.");
		th.test(code, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testTDZWithinClassHeritageDefinition()
	{
		String[] code = {
			"let A = class extends A {};",
			"let B = class extends { B } {};",
			"let C = class extends { method() { return C; } } {};",
			// line 4
			"const D = class extends D {};",
			"const E = class extends { E } {};",
			"const F = class extends { method() { return F; } } {};",
			// line 7
			"class G extends G {}",
			"class H extends { H } {}",
			"class I extends { method() { return I; }} {}"
		};
		
		th.addError(1, 23, "'A' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(2, 25, "'B' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(4, 25, "'D' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(5, 27, "'E' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(7, 17, "'G' was used before it was declared, which is illegal for 'class' variables.");
		th.addError(8, 19, "'H' was used before it was declared, which is illegal for 'class' variables.");
		th.test(code, new LinterOptions().set("esversion", 6));
	}
	
	@Test
	public void testTDZWithinForInOfHead()
	{
		String[] code = {
			"for (let a   in a);",
			"for (const b in b);",
			"for (let c   of c);",
			"for (const d of d);",

			// line 5
			"for (let e   in { e });",
			"for (const f in { f });",
			"for (let g   of { g });",
			"for (const h of { h });",

			// line 9
			"for (let i   in { method() { return i; } });",
			"for (const j in { method() { return j; } });",
			"for (let k   of { method() { return k; } });",
			"for (const l of { method() { return l; } });"
		};
		
		th.addError(1, 17, "'a' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(2, 17, "'b' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(3, 17, "'c' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(4, 17, "'d' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(5, 19, "'e' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(6, 19, "'f' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(7, 19, "'g' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(8, 19, "'h' was used before it was declared, which is illegal for 'const' variables.");
		th.test(code, new LinterOptions().set("esversion", 6));
	}
}