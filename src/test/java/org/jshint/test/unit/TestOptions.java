package org.jshint.test.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jshint.JSHint;
import org.jshint.LinterOptions;
import org.apache.commons.lang3.ArrayUtils;
import org.jshint.DataSummary;
import org.jshint.ImpliedGlobal;
import org.jshint.Token;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for all non-environmental options. Non-environmental options are
 * options that change how JSHint behaves instead of just pre-defining a set
 * of global variables.
 */
public class TestOptions extends Assert {
	private TestHelper th = new TestHelper();

	@BeforeMethod
	private void setupBeforeMethod() {
		th.newTest();
	}

	/**
	 * Option `shadow` allows you to re-define variables later in code.
	 *
	 * E.g.:
	 * var a = 1;
	 * if (cond == true)
	 * var a = 2; // Variable a has been already defined on line 1.
	 *
	 * More often than not it is a typo, but sometimes people use it.
	 */
	@Test
	public void testShadow() {
		String src = th.readFile("src/test/resources/fixtures/redef.js");

		// Do not tolerate variable shadowing by default
		th.addError(5, 13, "'a' is already defined.");
		th.addError(10, 9, "'foo' is already defined.");
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src, new LinterOptions().set("es3", true).set("shadow", false));
		th.test(src, new LinterOptions().set("es3", true).set("shadow", "inner"));

		// Allow variable shadowing when shadow is true
		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("shadow", true));

		String[] src2 = {
				"function f() {",
				"  function inner() {}",
				"}"
		};
		th.newTest("nested functions - 'shadowed' `arguments` - true");
		th.test(src2, new LinterOptions().set("shadow", true));
		th.newTest("nested functions - 'shadowed' `arguments` - false");
		th.test(src2, new LinterOptions().set("shadow", false));
		th.newTest("nested functions - 'shadowed' `arguments` - 'inner'");
		th.test(src2, new LinterOptions().set("shadow", "inner"));
	}

	/**
	 * Option `shadow:outer` allows you to re-define variables later in inner
	 * scopes.
	 *
	 * E.g.:
	 * var a = 1;
	 * function foo() {
	 * var a = 2;
	 * }
	 */
	@Test
	public void testShadowouter() {
		String src = th.readFile("src/test/resources/fixtures/scope-redef.js");

		// Do not tolarate inner scope variable shadowing by default
		th.addError(5, 13, "'a' is already defined in outer scope.");
		th.addError(12, 18, "'b' is already defined in outer scope.");
		th.addError(20, 18, "'bar' is already defined in outer scope.");
		th.addError(26, 14, "'foo' is already defined.");
		th.test(src, new LinterOptions().set("es3", true).set("shadow", "outer"));
	}

	@Test
	public void testShadowInline() {
		String src = th.readFile("src/test/resources/fixtures/shadow-inline.js");

		th.addError(6, 18, "'a' is already defined in outer scope.");
		th.addError(7, 13, "'a' is already defined.");
		th.addError(7, 13, "'a' is already defined in outer scope.");
		th.addError(17, 13, "'a' is already defined.");
		th.addError(27, 13, "'a' is already defined.");
		th.addError(42, 5, "Bad option value.");
		th.addError(47, 13, "'a' is already defined.");
		th.test(src);
	}

	@Test
	public void testShadowEs6() {
		String src = th.readFile("src/test/resources/fixtures/redef-es6.js");

		String[][] commonErrors = {
				{ "2", "5", "'ga' has already been declared." },
				{ "5", "7", "'gb' has already been declared." },
				{ "14", "9", "'gd' has already been declared." },
				{ "24", "9", "'gf' has already been declared." },
				{ "110", "5", "'gx' has already been declared." },
				{ "113", "7", "'gy' has already been declared." },
				{ "116", "7", "'gz' has already been declared." },
				{ "119", "5", "'gza' has already been declared." },
				{ "122", "5", "'gzb' has already been declared." },
				{ "132", "5", "'gzd' has already been declared." },
				{ "147", "7", "'gzf' has already been declared." },
				{ "156", "9", "'a' has already been declared." },
				{ "159", "11", "'b' has already been declared." },
				{ "168", "13", "'d' has already been declared." },
				{ "178", "13", "'f' has already been declared." },
				{ "264", "9", "'x' has already been declared." },
				{ "267", "11", "'y' has already been declared." },
				{ "270", "11", "'z' has already been declared." },
				{ "273", "9", "'za' has already been declared." },
				{ "276", "9", "'zb' has already been declared." },
				{ "286", "9", "'zd' has already been declared." },
				{ "301", "11", "'zf' has already been declared." },
				{ "317", "11", "'zi' has already been declared." },
				{ "344", "9", "'zzi' has already been declared." },
				{ "345", "11", "'zzj' has already been declared." },
				{ "349", "24", "'zzl' has already been declared." },
				{ "349", "30", "'zzl' was used before it was declared, which is illegal for 'const' variables." },
				{ "350", "22", "'zzm' has already been declared." },
				{ "350", "28", "'zzm' was used before it was declared, which is illegal for 'let' variables." },
				{ "364", "7", "'zj' has already been declared." }
		};

		String[][] innerErrors = {
				{ "343", "9", "'zzh' is already defined." },
				{ "348", "22", "'zzk' is already defined." }
		};

		String[][] outerErrors = {
				/* block scope variables shadowing out of scope */
				{ "9", "9", "'gc' is already defined." },
				{ "19", "11", "'ge' is already defined." },
				{ "28", "9", "'gg' is already defined in outer scope." },
				{ "32", "11", "'gh' is already defined in outer scope." },
				{ "36", "9", "'gi' is already defined in outer scope." },
				{ "40", "3", "'gj' is already defined." },
				{ "44", "3", "'gk' is already defined." },
				{ "48", "3", "'gl' is already defined." },
				{ "53", "7", "'gm' is already defined." },
				{ "59", "7", "'gn' is already defined." },
				{ "65", "7", "'go' is already defined." },
				{ "71", "9", "'gp' is already defined." },
				{ "76", "9", "'gq' is already defined." },
				{ "81", "11", "'gr' is already defined." },
				{ "86", "11", "'gs' is already defined." },
				{ "163", "13", "'c' is already defined." },
				{ "173", "15", "'e' is already defined." },
				{ "182", "13", "'g' is already defined in outer scope." },
				{ "186", "15", "'h' is already defined in outer scope." },
				{ "190", "13", "'i' is already defined in outer scope." },
				{ "194", "6", "'j' is already defined." },
				{ "198", "6", "'k' is already defined." },
				{ "202", "6", "'l' is already defined." },
				{ "207", "10", "'m' is already defined." },
				{ "213", "10", "'n' is already defined." },
				{ "219", "10", "'o' is already defined." },
				{ "225", "13", "'p' is already defined." },
				{ "230", "13", "'q' is already defined." },
				{ "235", "15", "'r' is already defined." },
				{ "240", "15", "'s' is already defined." },
				/* variables shadowing outside of function scope */
				{ "91", "9", "'gt' is already defined in outer scope." },
				{ "96", "9", "'gu' is already defined in outer scope." },
				{ "101", "11", "'gv' is already defined in outer scope." },
				{ "106", "9", "'gw' is already defined in outer scope." },
				{ "245", "13", "'t' is already defined in outer scope." },
				{ "250", "13", "'u' is already defined in outer scope." },
				{ "255", "15", "'v' is already defined in outer scope." },
				{ "260", "13", "'w' is already defined in outer scope." },
				/* variables shadowing outside multiple function scopes */
				{ "332", "17", "'zza' is already defined in outer scope." },
				{ "333", "17", "'zzb' is already defined in outer scope." },
				{ "334", "17", "'zzc' is already defined in outer scope." },
				{ "335", "17", "'zzd' is already defined in outer scope." },
				{ "336", "17", "'zze' is already defined in outer scope." },
				{ "337", "17", "'zzf' is already defined in outer scope." },
				{ "358", "9", "'zzn' is already defined in outer scope." }
		};

		for (String[] error : commonErrors) {
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("shadow", true));

		for (String[] error : innerErrors) {
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("shadow", "inner").set("maxerr", 100));

		for (String[] error : outerErrors) {
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("shadow", "outer").set("maxerr", 100));
	}

	/**
	 * Option `latedef` allows you to prohibit the use of variable before their
	 * definitions.
	 *
	 * E.g.:
	 * fn(); // fn will be defined later in code
	 * function fn() {};
	 *
	 * Since JavaScript has function-scope only, you can define variables and
	 * functions wherever you want. But if you want to be more strict, use
	 * this option.
	 */
	@Test
	public void testLatedef() {
		String src = th.readFile("src/test/resources/fixtures/latedef.js");
		String src1 = th.readFile("src/test/resources/fixtures/redef.js");
		String esnextSrc = th.readFile("src/test/resources/fixtures/latedef-esnext.js");

		// By default, tolerate the use of variable before its definition
		th.test(src, new LinterOptions().set("es3", true).set("funcscope", true));

		th.addError(10, 5, "'i' was used before it was declared, which is illegal for 'let' variables.");
		th.test(esnextSrc, new LinterOptions().set("esnext", true));

		// However, JSHint must complain if variable is actually missing
		th.newTest();
		th.addError(1, 1, "'fn' is not defined.");
		th.test("fn();", new LinterOptions().set("es3", true).set("undef", true));

		// And it also must complain about the redefinition (see option `shadow`)
		th.newTest();
		th.addError(5, 13, "'a' is already defined.");
		th.addError(10, 9, "'foo' is already defined.");
		th.test(src1, new LinterOptions().set("es3", true));

		// When latedef is true, JSHint must not tolerate the use before definition
		th.newTest();
		th.addError(10, 9, "'vr' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", "nofunc"));

		th.newTest();
		th.test(new String[] {
				"if(true) { var a; }",
				"if (a) { a(); }",
				"var a;"
		}, new LinterOptions().set("es3", true).set("latedef", "nofunc"));

		// When latedef_func is true, JSHint must not tolerate the use before definition
		// for functions
		th.newTest();
		th.addError(2, 10, "'fn' was used before it was defined.");
		th.addError(6, 14, "'fn1' was used before it was defined.");
		th.addError(10, 9, "'vr' was used before it was defined.");
		th.addError(18, 12, "'bar' was used before it was defined.");
		th.addError(18, 3, "Inner functions should be listed at the top of the outer function.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", true));

		th.newTest();
		th.addError(4, 5, "'c' was used before it was defined.");
		th.addError(6, 7, "'e' was used before it was defined.");
		th.addError(8, 5, "'h' was used before it was defined.");
		th.addError(10, 5, "'i' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(15, 9, "'ai' was used before it was defined.");
		th.addError(20, 13, "'ai' was used before it was defined.");
		th.addError(31, 9, "'bi' was used before it was defined.");
		th.addError(48, 13, "'ci' was used before it was defined.");
		th.addError(75, 10, "'importedName' was used before it was defined.");
		th.addError(76, 8, "'importedModule' was used before it was defined.");
		th.addError(77, 13, "'importedNamespace' was used before it was defined.");
		th.test(esnextSrc, new LinterOptions().set("esversion", 2015).set("latedef", true));
		th.test(esnextSrc, new LinterOptions().set("esversion", 2015).set("latedef", "nofunc"));

		th.newTest("shouldn't warn when marking a var as exported");
		th.test("var a;", new LinterOptions().setExporteds("a").set("latedef", true));
	}

	@Test
	public void testLatedefInline() {
		String src = th.readFile("src/test/resources/fixtures/latedef-inline.js");

		th.addError(4, 14, "'foo' was used before it was defined.");
		th.addError(6, 9, "'a' was used before it was defined.");
		th.addError(22, 9, "'a' was used before it was defined.");
		th.addError(26, 5, "Bad option value.");
		th.test(src);

		th.newTest("shouldn't warn when marking a var as exported");
		th.test("/*exported a*/var a;", new LinterOptions().set("latedef", true));
	}

	@Test
	public void testNotypeof() {
		String src = th.readFile("src/test/resources/fixtures/typeofcomp.js");

		th.addError(1, 17, "Invalid typeof value 'funtion'");
		th.addError(2, 14, "Invalid typeof value 'double'");
		th.addError(3, 17, "Invalid typeof value 'bool'");
		th.addError(4, 11, "Invalid typeof value 'obj'");
		th.addError(13, 17, "Invalid typeof value 'symbol'");
		th.addError(14, 21, "'BigInt' is only available in ES11 (use 'esversion: 11').");
		th.test(src);

		th.newTest();
		th.addError(1, 17, "Invalid typeof value 'funtion'");
		th.addError(2, 14, "Invalid typeof value 'double'");
		th.addError(3, 17, "Invalid typeof value 'bool'");
		th.addError(4, 11, "Invalid typeof value 'obj'");
		th.addError(14, 21, "'BigInt' is only available in ES11 (use 'esversion: 11').");
		th.test(src, new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(1, 17, "Invalid typeof value 'funtion'");
		th.addError(2, 14, "Invalid typeof value 'double'");
		th.addError(3, 17, "Invalid typeof value 'bool'");
		th.addError(4, 11, "Invalid typeof value 'obj'");
		th.test(src, new LinterOptions().set("esversion", 11));

		th.newTest();
		th.test(src, new LinterOptions().set("notypeof", true));
		th.test(src, new LinterOptions().set("notypeof", true).set("esnext", true));
	}

	@Test
	public void testCombinationOfLatedefAndUndef() {
		String src = th.readFile("src/test/resources/fixtures/latedefundef.js");

		// Assures that when `undef` is set to true, it'll report undefined variables
		// and late definitions won't be reported as `latedef` is set to false.
		th.addError(29, 1, "'hello' is not defined.");
		th.addError(35, 5, "'world' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", false).set("undef", true));

		// When we suppress `latedef` and `undef` then we get no warnings.
		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("latedef", false).set("undef", false));

		// If we warn on `latedef` but suppress `undef` we only get the
		// late definition warnings.
		th.newTest();
		th.addError(5, 10, "'func2' was used before it was defined.");
		th.addError(12, 10, "'foo' was used before it was defined.");
		th.addError(18, 14, "'fn1' was used before it was defined.");
		th.addError(26, 10, "'baz' was used before it was defined.");
		th.addError(34, 14, "'fn' was used before it was defined.");
		th.addError(41, 9, "'q' was used before it was defined.");
		th.addError(46, 5, "'h' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", true).set("undef", false));

		// But we get all the functions warning if we disable latedef func
		th.newTest();
		th.addError(41, 9, "'q' was used before it was defined.");
		th.addError(46, 5, "'h' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", "nofunc").set("undef", false));

		// If we warn on both options we get all the warnings.
		th.newTest();
		th.addError(5, 10, "'func2' was used before it was defined.");
		th.addError(12, 10, "'foo' was used before it was defined.");
		th.addError(18, 14, "'fn1' was used before it was defined.");
		th.addError(26, 10, "'baz' was used before it was defined.");
		th.addError(29, 1, "'hello' is not defined.");
		th.addError(34, 14, "'fn' was used before it was defined.");
		th.addError(35, 5, "'world' is not defined.");
		th.addError(41, 9, "'q' was used before it was defined.");
		th.addError(46, 5, "'h' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", true).set("undef", true));

		// If we remove latedef_func, we don't get the functions warning
		th.newTest();
		th.addError(29, 1, "'hello' is not defined.");
		th.addError(35, 5, "'world' is not defined.");
		th.addError(41, 9, "'q' was used before it was defined.");
		th.addError(46, 5, "'h' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", "nofunc").set("undef", true));
	}

	@Test
	public void testUndefwstrict() {
		String src = th.readFile("src/test/resources/fixtures/undefstrict.js");
		th.test(src, new LinterOptions().set("es3", true).set("undef", false));
	}

	// Regression test for GH-431
	@Test
	public void testImpliedAnUnusedShouldRespectHoisting() {
		String src = th.readFile("src/test/resources/fixtures/gh431.js");

		th.addError(14, 5, "'fun4' is not defined.");
		th.test(src, new LinterOptions().set("undef", true)); // es5

		JSHint jshint = new JSHint();
		jshint.lint(src, new LinterOptions().set("undef", true));
		DataSummary report = jshint.generateSummary();

		List<ImpliedGlobal> implieds = report.getImplieds();
		assertEquals(implieds.size(), 1);
		assertEquals(implieds.get(0).getName(), "fun4");
		assertEquals(implieds.get(0).getLines(), Arrays.asList(14));

		assertEquals(report.getUnused().size(), 3);
	}

	/**
	 * The `proto` and `iterator` options allow you to prohibit the use of the
	 * special `__proto__` and `__iterator__` properties, respectively.
	 */
	@Test
	public void testProtoAndIterator() {
		String source = th.readFile("src/test/resources/fixtures/protoiterator.js");
		String json = "{\"__proto__\": true, \"__iterator__\": false, \"_identifier\": null, \"property\": 123}";

		// JSHint should not allow the `__proto__` and
		// `__iterator__` properties by default
		th.addError(7, 34, "The '__proto__' property is deprecated.");
		th.addError(8, 19, "The '__proto__' property is deprecated.");
		th.addError(10, 19, "The '__proto__' property is deprecated.");
		th.addError(27, 29, "The '__iterator__' property is deprecated.");
		th.addError(27, 53, "The '__iterator__' property is deprecated.");
		th.addError(33, 19, "The '__proto__' property is deprecated.");
		th.addError(37, 29, "The '__proto__' property is deprecated.");
		th.test(source, new LinterOptions().set("es3", true));

		th.newTest();
		th.addError(1, 2, "The '__proto__' key may produce unexpected results.");
		th.addError(1, 21, "The '__iterator__' key may produce unexpected results.");
		th.test(json, new LinterOptions().set("es3", true));

		// Should not report any errors when proto and iterator
		// options are on
		th.newTest();
		th.test(source, new LinterOptions().set("es3", true).set("proto", true).set("iterator", true));
		th.test(json, new LinterOptions().set("es3", true).set("proto", true).set("iterator", true));
	}

	/**
	 * The `camelcase` option allows you to enforce use of the camel case
	 * convention.
	 */
	@Test
	public void testCamelcase() {
		String source = th.readFile("src/test/resources/fixtures/camelcase.js");

		// By default, tolerate arbitrary identifiers
		th.test(source, new LinterOptions().set("es3", true));

		// Require identifiers in camel case if camelcase is true
		th.newTest();
		th.addError(5, 17, "Identifier 'Foo_bar' is not in camel case.");
		th.addError(5, 25, "Identifier 'test_me' is not in camel case.");
		th.addError(6, 15, "Identifier 'test_me' is not in camel case.");
		th.addError(6, 25, "Identifier 'test_me' is not in camel case.");
		th.addError(13, 26, "Identifier 'test_1' is not in camel case.");
		th.test(source, new LinterOptions().set("es3", true).set("camelcase", true));
	}

	/**
	 * Option `curly` allows you to enforce the use of curly braces around
	 * control blocks. JavaScript allows one-line blocks to go without curly
	 * braces but some people like to always use curly bracse. This option is
	 * for them.
	 *
	 * E.g.:
	 * if (cond) return;
	 * vs.
	 * if (cond) { return; }
	 */
	@Test
	public void testCurly() {
		String src = th.readFile("src/test/resources/fixtures/curly.js");
		String src1 = th.readFile("src/test/resources/fixtures/curly2.js");

		// By default, tolerate one-line blocks since they are valid JavaScript
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src1, new LinterOptions().set("es3", true));

		// Require all blocks to be wrapped with curly braces if curly is true
		th.newTest();
		th.addError(2, 5, "Expected '{' and instead saw 'return'.");
		th.addError(5, 5, "Expected '{' and instead saw 'doSomething'.");
		th.addError(8, 5, "Expected '{' and instead saw 'doSomething'.");
		th.addError(11, 5, "Expected '{' and instead saw 'doSomething'.");
		th.test(src, new LinterOptions().set("es3", true).set("curly", true));

		th.newTest();
		th.test(src1, new LinterOptions().set("es3", true).set("curly", true));
	}

	/** Option `noempty` prohibits the use of empty blocks. */
	@Test
	public void testNoempty() {
		String[] code = {
				"for (;;) {}",
				"if (true) {",
				"}",
				"foo();"
		};

		// By default, tolerate empty blocks since they are valid JavaScript
		th.test(code, new LinterOptions().set("es3", true));

		// Do not tolerate, when noempty is true
		th.newTest();
		th.addError(1, 10, "Empty block.");
		th.addError(2, 11, "Empty block.");
		th.test(code, new LinterOptions().set("es3", true).set("noempty", true));
	}

	/**
	 * Option `noarg` prohibits the use of arguments.callee and arguments.caller.
	 * JSHint allows them by default but you have to know what you are doing since:
	 * - They are not supported by all JavaScript implementations
	 * - They might prevent an interpreter from doing some optimization tricks
	 * - They are prohibited in the strict mode
	 */
	@Test
	public void testNoarg() {
		String src = th.readFile("src/test/resources/fixtures/noarg.js");

		// By default, tolerate both arguments.callee and arguments.caller
		th.test(src, new LinterOptions().set("es3", true));

		// Do not tolerate both .callee and .caller when noarg is true
		th.newTest();
		th.addError(2, 12, "Avoid arguments.callee.");
		th.addError(6, 12, "Avoid arguments.caller.");
		th.test(src, new LinterOptions().set("es3", true).set("noarg", true));
	}

	/** Option `nonew` prohibits the use of constructors for side-effects */
	@Test
	public void testNonew() {
		String code = "new Thing();";
		String code1 = "var obj = new Thing();";

		th.test(code, new LinterOptions().set("es3", true));
		th.test(code1, new LinterOptions().set("es3", true));

		th.newTest();
		th.addError(1, 1, "Do not use 'new' for side effects.");
		th.test(code, new LinterOptions().set("es3", true).set("nonew", true));
	}

	// Option `asi` allows you to use automatic-semicolon insertion
	@Test
	public void testAsi() {
		String src = th.readFile("src/test/resources/fixtures/asi.js");

		th.newTest("1");
		th.addError(2, 13, "Missing semicolon.");
		th.addError(4, 21, "Missing semicolon.");
		th.addError(5, 14, "Missing semicolon.");
		th.addError(9, 26, "Missing semicolon.");
		th.addError(10, 14, "Missing semicolon.");
		th.addError(11, 23, "Missing semicolon.");
		th.addError(12, 14, "Missing semicolon.");
		th.addError(16, 23, "Missing semicolon.");
		th.addError(17, 19, "Missing semicolon.");
		th.addError(19, 18, "Missing semicolon.");
		th.addError(21, 18, "Missing semicolon.");
		th.addError(25, 6, "Missing semicolon.");
		th.addError(26, 10, "Missing semicolon.");
		th.addError(27, 12, "Missing semicolon.");
		th.addError(28, 12, "Missing semicolon.");
		th.test(src, new LinterOptions().set("es3", true));

		th.newTest("2");
		th.test(src, new LinterOptions().set("es3", true).set("asi", true));

		String[] code = {
				"function a() { 'code' }",
				"function b() { 'code'; 'code' }",
				"function c() { 'code', 'code' }",
				"function d() {",
				"  'code' }",
				"function e() { 'code' 'code' }"
		};

		th.newTest("gh-2714");
		th.addError(2, 24, "Unnecessary directive \"code\".");
		th.addError(3, 24, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 22, "E058", "Missing semicolon.");
		th.addError(6, 16, "Expected an assignment or function call and instead saw an expression.");
		th.addError(6, 23, "Expected an assignment or function call and instead saw an expression.");
		th.test(code, new LinterOptions().set("asi", true));
	}

	// Option `asi` extended for safety -- warn in scenarios that would be unsafe
	// when using asi.
	@Test
	public void testSafeasi() {
		String src = th.readFile("src/test/resources/fixtures/safeasi.js");

		// TOOD consider setting an option to suppress these errors so that
		// the tests don't become tightly interdependent
		th.newTest("1");
		th.addError(10, 5, "Misleading line break before '/'; readers may interpret this as an expression boundary.");
		th.addError(10, 8, "Expected an identifier and instead saw '.'.");
		th.addError(10, 8, "Expected an assignment or function call and instead saw an expression.");
		th.addError(10, 9, "Missing semicolon.");
		th.addError(10, 30, "Missing semicolon.");
		th.addError(11, 5, "Missing semicolon.");
		th.addError(21, 2, "Missing semicolon.");
		th.test(src, new LinterOptions());

		th.newTest("2");
		th.addError(5, 1, "Misleading line break before '('; readers may interpret this as an expression boundary.");
		th.addError(8, 5, "Misleading line break before '('; readers may interpret this as an expression boundary.");
		th.addError(10, 5, "Misleading line break before '/'; readers may interpret this as an expression boundary.");
		th.addError(10, 8, "Expected an identifier and instead saw '.'.");
		th.addError(10, 8, "Expected an assignment or function call and instead saw an expression.");
		th.addError(10, 9, "Missing semicolon.");
		th.test(src, new LinterOptions().set("asi", true));

		String[] afterBracket = {
				"x = []",
				"[1];",
				"x[0]",
				"(2);"
		};

		th.newTest("following bracket (asi: false)");
		th.test(afterBracket);

		th.newTest("following bracket (asi: true)");
		th.addError(2, 1, "Misleading line break before '['; readers may interpret this as an expression boundary.");
		th.addError(4, 1, "Misleading line break before '('; readers may interpret this as an expression boundary.");
		th.test(afterBracket, new LinterOptions().set("asi", true));

		String[] asClause = {
				"if (true)",
				"  ({x} = {});",
				"if (true)",
				"  [x] = [0];",
				"while (false)",
				"  ({x} = {});",
				"while (false)",
				"  [x] = [0];"
		};

		// Regression tests for gh-3304
		th.newTest("as clause (asi: false)");
		th.test(asClause, new LinterOptions().set("esversion", 6));
		th.newTest("as clause (asi: true)");
		th.test(asClause, new LinterOptions().set("esversion", 6).set("asi", true));
	}

	@Test
	public void testMissingSemicolonsNotInfluencedByAsi() {
		// These tests are taken from
		// http://www.ecma-international.org/ecma-262/6.0/index.html#sec-11.9.2

		String[] code = {
				"void 0;", // Not JSON
				"{ 1 2 } 3"
		};

		th.addError(2, 4, "E058", "Missing semicolon.");
		th.test(code, new LinterOptions().set("expr", true).set("asi", true));

		code = new String[] {
				"void 0;",
				"{ 1",
				"2 } 3"
		};

		th.newTest();
		th.test(code, new LinterOptions().set("expr", true).set("asi", true));

		String codeStr = "do {} while (false) var a;";

		th.newTest("do-while as es5");
		th.addError(1, 20, "E058", "Missing semicolon.");
		th.test(codeStr);

		th.newTest("do-while as es5+moz");
		th.addError(1, 20, "E058", "Missing semicolon.");
		th.test(codeStr, new LinterOptions().set("moz", true));

		th.newTest("do-while as es6");
		th.addError(1, 20, "W033", "Missing semicolon.");
		th.test(codeStr, new LinterOptions().set("esversion", 6));

		th.newTest("do-while as es6 with asi");
		th.test(codeStr, new LinterOptions().set("esversion", 6).set("asi", true));

		th.newTest("do-while false positive");
		th.addError(1, 5, "E058", "Missing semicolon.");
		th.test("'do' var x;", new LinterOptions().set("esversion", 6).set("expr", true));
	}

	/**
	 * Option `lastsemic` allows you to skip the semicolon after last statement in a
	 * block,
	 * if that statement is followed by the closing brace on the same line.
	 */
	@Test
	public void testLastsemic() {
		String src = th.readFile("src/test/resources/fixtures/lastsemic.js");

		// without lastsemic
		th.addError(2, 11, "Missing semicolon."); // missing semicolon in the middle of a block
		th.addError(4, 45, "Missing semicolon."); // missing semicolon in a one-liner function
		th.addError(5, 12, "Missing semicolon."); // missing semicolon at the end of a block
		th.test(src, new LinterOptions().set("es3", true));

		// with lastsemic
		th.newTest();
		th.addError(2, 11, "Missing semicolon.");
		th.addError(5, 12, "Missing semicolon.");
		th.test(src, new LinterOptions().set("es3", true).set("lastsemic", true));
		// this line is valid now: [1, 2, 3].forEach(function(i) { print(i) });
		// line 5 isn't, because the block doesn't close on the same line
	}

	/**
	 * Option `expr` allows you to use ExpressionStatement as a Program code.
	 *
	 * Even though ExpressionStatement as a Program (i.e. without assingment
	 * of its result) is a valid JavaScript, more often than not it is a typo.
	 * That's why by default JSHint complains about it. But if you know what
	 * are you doing, there is nothing wrong with it.
	 */
	@Test
	public void testExpr() {
		String[][] exps = {
				{ "33", "obj && obj.method && obj.method();" },
				{ "20", "myvar && func(myvar);" },
				{ "1", "1;" },
				{ "1", "true;" },
				{ "19", "+function (test) {};" }
		};

		for (String[] exp : exps) {
			th.newTest();
			th.addError(1, Integer.parseInt(exp[0]),
					"Expected an assignment or function call and instead saw an expression.");
			th.test(exp[1], new LinterOptions().set("es3", true));
		}

		for (String[] exp : exps) {
			th.newTest();
			th.test(exp[1], new LinterOptions().set("es3", true).set("expr", true));
		}
	}

	// Option `undef` requires you to always define variables you use.
	@Test
	public void testUndef() {
		String src = th.readFile("src/test/resources/fixtures/undef.js");

		// Make sure there are no other errors
		th.test(src, new LinterOptions().set("es3", true));

		// Make sure it fails when undef is true
		th.newTest();
		th.addError(1, 1, "'undef' is not defined.");
		th.addError(5, 12, "'undef' is not defined.");
		th.addError(6, 12, "'undef' is not defined.");
		th.addError(8, 12, "'undef' is not defined.");
		th.addError(9, 12, "'undef' is not defined.");
		th.addError(13, 5, "'localUndef' is not defined.");
		th.addError(18, 16, "'localUndef' is not defined.");
		th.addError(19, 16, "'localUndef' is not defined.");
		th.addError(21, 16, "'localUndef' is not defined.");
		th.addError(22, 16, "'localUndef' is not defined.");
		th.addError(32, 12, "'undef' is not defined.");
		th.addError(33, 13, "'undef' is not defined.");
		th.addError(34, 12, "'undef' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("undef", true));

		// block scope cannot use themselves in the declaration
		th.newTest();
		th.addError(1, 9, "'a' was used before it was declared, which is illegal for 'let' variables.");
		th.addError(2, 11, "'b' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(5, 7, "'e' is already defined.");
		th.test(new String[] {
				"let a = a;",
				"const b = b;",
				"var c = c;",
				"function f(e) {",
				"  var e;", // the var does not overwrite the param, the param is used
				"  e = e || 2;",
				"  return e;",
				"}"
		}, new LinterOptions().set("esnext", true).set("undef", true));

		// Regression test for GH-668.
		src = th.readFile("src/test/resources/fixtures/gh668.js");

		JSHint jshint = new JSHint();

		assertTrue(jshint.lint(src, new LinterOptions().set("undef", true)));
		assertTrue(jshint.generateSummary().getImplieds().size() == 0);

		assertTrue(jshint.lint(src));
		assertTrue(jshint.generateSummary().getImplieds().size() == 0);

		jshint.lint("if (typeof foobar) {}", new LinterOptions().set("undef", true));

		assertTrue(jshint.generateSummary().getImplieds().size() == 0);

		// See gh-3055 "Labels Break JSHint"
		th.newTest("following labeled block");
		th.addError(4, 6, "'x' is not defined.");
		th.test(new String[] {
				"label: {",
				"  let x;",
				"}",
				"void x;"
		}, new LinterOptions().set("esversion", 6).set("undef", true));

		th.newTest();
		th.addError(1, 1, "'foo' is not defined.");
		th.test(new String[] {
				"foo.call();",
				"/* exported foo, bar */"
		}, new LinterOptions().set("undef", true));

		th.newTest("arguments - ES5");
		th.addError(6, 6, "'arguments' is not defined.");
		th.test(new String[] {
				"function f() { return arguments; }",
				"void function() { return arguments; };",
				"void function f() { return arguments; };",
				"void { get g() { return arguments; } };",
				"void { get g() {}, set g(_) { return arguments; } };",
				"void arguments;"
		}, new LinterOptions().set("undef", true));

		th.newTest("arguments - ES2015");
		th.addError(47, 11, "'arguments' is not defined.");
		th.addError(48, 21, "'arguments' is not defined.");
		th.addError(49, 12, "'arguments' is not defined.");
		th.test(new String[] {
				"function f(_ = arguments) {}",
				"void function (_ = arguments) {};",
				"void function f(_ = arguments) {};",
				"function* g(_ = arguments) { yield; }",
				"void function* (_ = arguments) { yield; };",
				"void function* g(_ = arguments) { yield; };",
				"function* g() { yield arguments; }",
				"void function* () { yield arguments; };",
				"void function* g() { yield arguments; };",
				"void { method(_ = arguments) {} };",
				"void { method() { return arguments; } };",
				"void { *method(_ = arguments) { yield; } };",
				"void { *method() { yield arguments; } };",
				"class C0 { constructor(_ = arguments) {} }",
				"class C1 { constructor() { return arguments; } }",
				"class C2 { method(_ = arguments) {} }",
				"class C3 { method() { return arguments; } }",
				"class C4 { *method(_ = arguments) { yield; } }",
				"class C5 { *method() { yield arguments; } }",
				"class C6 { static method(_ = arguments) {} }",
				"class C7 { static method() { return arguments; } }",
				"class C8 { static *method(_ = arguments) { yield; } }",
				"class C9 { static *method() { yield arguments; } }",
				"void class { constructor(_ = arguments) {} };",
				"void class { constructor() { return arguments; } };",
				"void class { method(_ = arguments) {} };",
				"void class { method() { return arguments; } };",
				"void class { *method(_ = arguments) { yield; } };",
				"void class { *method() { yield arguments; } };",
				"void class { static method(_ = arguments) {} };",
				"void class { static method() { return arguments; } };",
				"void class { static *method(_ = arguments) { yield; } };",
				"void class { static *method() { yield arguments; } };",
				"void class C { constructor(_ = arguments) {} };",
				"void class C { constructor() { return arguments; } };",
				"void class C { method(_ = arguments) {} };",
				"void class C { method() { return arguments; } };",
				"void class C { *method(_ = arguments) { yield; } };",
				"void class C { *method() { yield arguments; } };",
				"void class C { static method(_ = arguments) {} };",
				"void class C { static method() { return arguments; } };",
				"void class C { static *method(_ = arguments) { yield; } };",
				"void class C { static *method() { yield arguments; } };",
				"void function() { void (_ = arguments) => _; };",
				"void function() { void () => { return arguments; }; };",
				"void function() { void () => arguments; };",
				"void (_ = arguments) => _;",
				"void () => { return arguments; };",
				"void () => arguments;"
		}, new LinterOptions().set("undef", true).set("esversion", 6));
	}

	@Test
	public void testUndefToOpMethods() {
		th.addError(2, 12, "'undef' is not defined.");
		th.addError(3, 12, "'undef' is not defined.");
		th.test(new String[] {
				"var obj;",
				"obj.delete(undef);",
				"obj.typeof(undef);"
		}, new LinterOptions().set("undef", true));
	}

	/**
	 * In strict mode, the `delete` operator does not accept unresolvable
	 * references:
	 *
	 * http://es5.github.io/#x11.4.1
	 *
	 * This will only be apparent in cases where the user has suppressed warnings
	 * about deleting variables.
	 */
	@Test
	public void testUndefDeleteStrict() {
		th.addError(3, 10, "'aNullReference' is not defined.");
		th.test(new String[] {
				"(function() {",
				"  'use strict';",
				"  delete aNullReference;",
				"}());"
		}, new LinterOptions().set("undef", true).set("-W051", false));
	}

	@Test(groups = { "unused" })
	public void testUnusedBasic() {
		String src = th.readFile("src/test/resources/fixtures/unused.js");

		String[][] allErrors = {
				{ "22", "7", "'i' is defined but never used." },
				{ "101", "5", "'inTry2' used out of scope." },
				{ "117", "13", "'inTry9' was used before it was declared, which is illegal for 'let' variables." },
				{ "118", "15", "'inTry10' was used before it was declared, which is illegal for 'const' variables." }
		};

		for (String[] error : allErrors) {
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}
		th.test(src, new LinterOptions().set("esnext", true));

		String[][] var_errors = ArrayUtils.addAll(allErrors, new String[][] {
				{ "1", "5", "'a' is defined but never used." },
				{ "7", "9", "'c' is defined but never used." },
				{ "15", "10", "'foo' is defined but never used." },
				{ "20", "10", "'bar' is defined but never used." },
				{ "36", "7", "'cc' is defined but never used." },
				{ "39", "9", "'dd' is defined but never used." },
				{ "58", "11", "'constUsed' is defined but never used." },
				{ "62", "9", "'letUsed' is defined but never used." },
				{ "63", "9", "'anotherUnused' is defined but never used." },
				{ "91", "9", "'inTry6' is defined but never used." },
				{ "94", "9", "'inTry9' is defined but never used." },
				{ "95", "11", "'inTry10' is defined but never used." },
				{ "99", "13", "'inTry4' is defined but never used." },
				{ "122", "10", "'unusedRecurringFunc' is defined but never used." }
		});

		String[][] last_param_errors = {
				{ "6", "18", "'f' is defined but never used." },
				{ "28", "14", "'a' is defined but never used." },
				{ "28", "17", "'b' is defined but never used." },
				{ "28", "20", "'c' is defined but never used." },
				{ "68", "5", "'y' is defined but never used." },
				{ "69", "6", "'y' is defined but never used." },
				{ "70", "9", "'z' is defined but never used." }
		};

		String[][] all_param_errors = {
				{ "15", "14", "'err' is defined but never used." },
				{ "71", "6", "'y' is defined but never used." }
		};

		th.newTest();
		for (String[] error : ArrayUtils.addAll(var_errors, last_param_errors)) {
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}

		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
		JSHint jshint = new JSHint();
		assertTrue(!jshint.lint(src, new LinterOptions().set("esnext", true).set("unused", true)));

		// Test checking all function params via unused="strict"
		th.newTest();
		for (String[] error : ArrayUtils.addAll(ArrayUtils.addAll(var_errors, last_param_errors), all_param_errors)) {
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("unused", "strict"));

		// Test checking everything except function params
		th.newTest();
		for (String[] error : var_errors) {
			th.addError(Integer.parseInt(error[0]), Integer.parseInt(error[1]), error[2]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("unused", "vars"));

		List<Token> unused = th.getJSHint().generateSummary().getUnused();
		assertEquals(24, unused.size());

		boolean some = false;
		for (Token err : unused) {
			if (err.getLine() == 1 && err.getCharacter() == 5 && err.getName().equals("a")) {
				some = true;
				break;
			}
		}
		assertTrue(some);

		some = false;
		for (Token err : unused) {
			if (err.getLine() == 6 && err.getCharacter() == 18 && err.getName().equals("f")) {
				some = true;
				break;
			}
		}
		assertTrue(some);

		some = false;
		for (Token err : unused) {
			if (err.getLine() == 7 && err.getCharacter() == 9 && err.getName().equals("c")) {
				some = true;
				break;
			}
		}
		assertTrue(some);

		some = false;
		for (Token err : unused) {
			if (err.getLine() == 15 && err.getCharacter() == 10 && err.getName().equals("foo")) {
				some = true;
				break;
			}
		}
		assertTrue(some);

		some = false;
		for (Token err : unused) {
			if (err.getLine() == 68 && err.getCharacter() == 5 && err.getName().equals("y")) {
				some = true;
				break;
			}
		}
		assertTrue(some);
	}

	// Regression test for gh-3362
	@Test(groups = { "unused" })
	public void testUnusedEs3Reserved() {
		th.addError(1, 5, "'abstract' is defined but never used.");
		th.addError(1, 15, "'boolean' is defined but never used.");
		th.addError(1, 24, "'byte' is defined but never used.");
		th.addError(1, 30, "'char' is defined but never used.");
		th.addError(1, 36, "'double' is defined but never used.");
		th.addError(1, 44, "'final' is defined but never used.");
		th.addError(1, 51, "'float' is defined but never used.");
		th.addError(1, 58, "'goto' is defined but never used.");
		th.addError(1, 64, "'int' is defined but never used.");
		th.addError(2, 5, "'long' is defined but never used.");
		th.addError(2, 11, "'native' is defined but never used.");
		th.addError(2, 19, "'short' is defined but never used.");
		th.addError(2, 26, "'synchronized' is defined but never used.");
		th.addError(2, 40, "'transient' is defined but never used.");
		th.addError(2, 51, "'volatile' is defined but never used.");
		th.test(new String[] {
				"var abstract, boolean, byte, char, double, final, float, goto, int;",
				"var long, native, short, synchronized, transient, volatile;",
		}, new LinterOptions().set("unused", true));

		th.newTest();
		th.test(new String[] {
				"var abstract, boolean, byte, char, double, final, float, goto, int;",
				"var long, native, short, synchronized, transient, volatile;",
				"void (abstract + boolean + byte + char + double + final + float + loat + goto + int);",
				"void (long + native + short + synchronized + transient + volatile);"
		}, new LinterOptions().set("unused", true));
	}

	// Regression test for gh-2784
	@Test(groups = { "unused" })
	public void testUnusedUsedThroughShadowedDeclaration() {
		String[] code = {
				"(function() {",
				"  var x;",
				"  {",
				"    var x;",
				"    void x;",
				"  }",
				"}());"
		};

		th.addError(4, 9, "'x' is already defined.");
		th.test(code, new LinterOptions().set("unused", true));
	}

	@Test(groups = { "unused" })
	public void testUnusedUnusedThroughShadowedDeclaration() {
		String[] code = {
				"(function() {",
				"  {",
				"      var x;",
				"      void x;",
				"  }",
				"  {",
				"      var x;",
				"  }",
				"})();"
		};

		th.addError(7, 11, "'x' is already defined.");
		th.test(code, new LinterOptions().set("unused", true));
	}

	@Test(groups = { "unused" })
	public void testUnusedHoisted() {
		String[] code = {
				"(function() {",
				"  {",
				"    var x;",
				"  }",
				"  {",
				"    var x;",
				"  }",
				"  void x;",
				"}());"
		};

		th.addError(6, 9, "'x' is already defined.");
		th.addError(8, 8, "'x' used out of scope.");
		th.test(code, new LinterOptions().set("unused", true));
	}

	@Test(groups = { "unused" })
	public void testUnusedCrossBlocks() {
		String code = th.readFile("src/test/resources/fixtures/unused-cross-blocks.js");

		th.addError(15, 9, "'func4' is already defined.");
		th.addError(18, 9, "'func5' is already defined.");
		th.addError(41, 11, "'topBlock6' is already defined.");
		th.addError(44, 11, "'topBlock7' is already defined.");
		th.addError(56, 13, "'topBlock3' is already defined.");
		th.addError(59, 13, "'topBlock4' is already defined.");
		th.addError(9, 7, "'unusedFunc' is defined but never used.");
		th.addError(27, 9, "'unusedTopBlock' is defined but never used.");
		th.addError(52, 11, "'unusedNestedBlock' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true));

		th.newTest();
		th.addError(15, 9, "'func4' is already defined.");
		th.addError(18, 9, "'func5' is already defined.");
		th.addError(41, 11, "'topBlock6' is already defined.");
		th.addError(44, 11, "'topBlock7' is already defined.");
		th.addError(56, 13, "'topBlock3' is already defined.");
		th.addError(59, 13, "'topBlock4' is already defined.");
		th.test(code);
	}

	// Regression test for gh-3354
	@Test(groups = { "unused" })
	public void testUnusedMethodNames() {
		th.newTest("object methods - ES5");
		th.test(new String[] {
				"var p;",
				"void {",
				"  get p() { void p; },",
				"  set p(_) { void p; void _; }",
				"};"
		}, new LinterOptions().set("unused", true).set("esversion", 5));

		th.newTest("object methods - ES6");
		th.test(new String[] {
				"var m, g;",
				"void {",
				"  m() { void m; },",
				"  *g() { yield g; }",
				"};"
		}, new LinterOptions().set("unused", true).set("esversion", 6));

		th.newTest("object methods - ES8");
		th.test(new String[] {
				"var m;",
				"void {",
				"  async m() { void m; }",
				"};"
		}, new LinterOptions().set("unused", true).set("esversion", 8));

		th.newTest("object methods - ES9");
		th.test(new String[] {
				"var m;",
				"void {",
				"  async * m() { yield m; }",
				"};"
		}, new LinterOptions().set("unused", true).set("esversion", 9));

		th.newTest("class methods - ES6");
		th.test(new String[] {
				"var m, g, p, s;",
				"void class {",
				"  m() { void m; }",
				"  *g() { yield g; }",
				"  get p() { void p; }",
				"  set p() { void p; }",
				"  static s() { void s; }",
				"};"
		}, new LinterOptions().set("unused", true).set("esversion", 6));

		th.newTest("class methods - ES8");
		th.test(new String[] {
				"var m;",
				"void class {",
				"  async m() { void m; }",
				"};"
		}, new LinterOptions().set("unused", true).set("esversion", 8));

		th.newTest("class methods - ES9");
		th.test(new String[] {
				"var m;",
				"void class {",
				"  async * m() { yield m; }",
				"};"
		}, new LinterOptions().set("unused", true).set("esversion", 9));
	}

	@Test
	public void testParamOverridesFunctionNameExpression() {
		th.test(new String[] {
				"var A = function B(B) {",
				"  return B;",
				"};",
				"A();"
		}, new LinterOptions().set("undef", true).set("unused", "strict"));
	}

	@Test
	public void testLetCanReuseFunctionAndClassName() {
		th.test(new String[] {
				"var A = function B(C) {",
				"  let B = C;",
				"  return B;",
				"};",
				"A();",
				"var D = class E { constructor(F) { let E = F; return E; }};",
				"D();"
		}, new LinterOptions().set("undef", true).set("unused", "strict").set("esnext", true));
	}

	@Test
	public void testUnusedWithParamDestructuring() {
		String[] code = {
				"let b = ([...args], a) => a;",
				"b = args => true;",
				"b = function([...args], a) { return a; };",
				"b = function([args], a) { return a; };",
				"b = function({ args }, a) { return a; };",
				"b = function({ a: args }, a) { return a; };",
				"b = function({ a: [args] }, a) { return a; };",
				"b = function({ a: [args] }, a) { return a; };"
		};

		th.addError(2, 5, "'args' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));

		th.newTest();
		th.addError(1, 14, "'args' is defined but never used.");
		th.addError(2, 5, "'args' is defined but never used.");
		th.addError(3, 18, "'args' is defined but never used.");
		th.addError(4, 15, "'args' is defined but never used.");
		th.addError(5, 16, "'args' is defined but never used.");
		th.addError(6, 19, "'args' is defined but never used.");
		th.addError(7, 20, "'args' is defined but never used.");
		th.addError(8, 20, "'args' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));
	}

	@Test
	public void testUnusedDataWithOptions() {
		// see gh-1894 for discussion on this test

		String[] code = {
				"function func(placeHolder1, placeHolder2, used, param) {",
				"  used = 1;",
				"}"
		};

		List<Token> expectedVarUnused = Arrays.asList(new Token("func", 1, 10));
		List<Token> expectedParamUnused = Arrays.asList(new Token("param", 1, 49));
		List<Token> expectedPlaceholderUnused = Arrays.asList(new Token("placeHolder2", 1, 29),
				new Token("placeHolder1", 1, 15));

		List<Token> expectedAllUnused = new ArrayList<>(expectedParamUnused);
		expectedAllUnused.addAll(expectedPlaceholderUnused);
		expectedAllUnused.addAll(expectedVarUnused);
		List<Token> expectedVarAndParamUnused = new ArrayList<>(expectedParamUnused);
		expectedVarAndParamUnused.addAll(expectedVarUnused);

		// true
		th.addError(1, 10, "'func' is defined but never used.");
		th.addError(1, 49, "'param' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", true));

		List<Token> unused = th.getJSHint().generateSummary().getUnused();
		assertEquals(expectedVarAndParamUnused, unused);

		// false
		th.newTest();
		th.test(code, new LinterOptions().set("unused", false));

		unused = th.getJSHint().generateSummary().getUnused();
		assertEquals(expectedVarUnused, unused);

		// strict
		th.newTest();
		th.addError(1, 10, "'func' is defined but never used.");
		th.addError(1, 15, "'placeHolder1' is defined but never used.");
		th.addError(1, 29, "'placeHolder2' is defined but never used.");
		th.addError(1, 49, "'param' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", "strict"));

		unused = th.getJSHint().generateSummary().getUnused();
		assertEquals(expectedAllUnused, unused);

		// vars
		th.newTest();
		th.addError(1, 10, "'func' is defined but never used.");
		th.test(code, new LinterOptions().set("unused", "vars"));

		unused = th.getJSHint().generateSummary().getUnused();
		assertEquals(expectedAllUnused, unused);
	}

	@Test
	public void testUnusedWithGlobalOverride() {
		String[] code = {
				"alert();",
				"function alert() {}"
		};

		th.test(code,
				new LinterOptions().set("unused", true).set("undef", true).set("devel", true).set("latedef", false));
	}

	// Regressions for "unused" getting overwritten via comment (GH-778)
	@Test
	public void testUnusedOverrides() {
		String[] code;

		code = new String[] { "function foo(a) {", "/*jshint unused:false */", "}", "foo();" };
		th.test(code, new LinterOptions().set("es3", true).set("unused", true));

		code = new String[] { "function foo(a, b, c) {", "/*jshint unused:vars */", "var i = b;", "}", "foo();" };
		th.newTest();
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true));

		code = new String[] { "function foo(a, b, c) {", "/*jshint unused:true */", "var i = b;", "}", "foo();" };
		th.newTest();
		th.addError(1, 20, "'c' is defined but never used.");
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));

		code = new String[] { "function foo(a, b, c) {", "/*jshint unused:strict */", "var i = b;", "}", "foo();" };
		th.newTest();
		th.addError(1, 14, "'a' is defined but never used.");
		th.addError(1, 20, "'c' is defined but never used.");
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true));

		code = new String[] { "/*jshint unused:vars */", "function foo(a, b) {}", "foo();" };
		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));

		code = new String[] { "/*jshint unused:vars */", "function foo(a, b) {", "var i = 3;", "}", "foo();" };
		th.newTest();
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));

		code = new String[] { "/*jshint unused:badoption */", "function foo(a, b) {", "var i = 3;", "}", "foo();" };
		th.newTest();
		th.addError(1, 1, "Bad option value.");
		th.addError(2, 17, "'b' is defined but never used.");
		th.addError(2, 14, "'a' is defined but never used.");
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));
	}

	@Test
	public void testUnusedOverridesEsnext() {
		String[] code;

		code = new String[] { "function foo(a) {", "/*jshint unused:false */", "}", "foo();" };
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));

		code = new String[] { "function foo(a, b, c) {", "/*jshint unused:vars */", "let i = b;", "}", "foo();" };
		th.newTest();
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));

		code = new String[] { "function foo(a, b, c) {", "/*jshint unused:true */", "let i = b;", "}", "foo();" };
		th.newTest();
		th.addError(1, 20, "'c' is defined but never used.");
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));

		code = new String[] { "function foo(a, b, c) {", "/*jshint unused:strict */", "let i = b;", "}", "foo();" };
		th.newTest();
		th.addError(1, 14, "'a' is defined but never used.");
		th.addError(1, 20, "'c' is defined but never used.");
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));

		code = new String[] { "/*jshint unused:vars */", "function foo(a, b) {", "let i = 3;", "}", "foo();" };
		th.newTest();
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));

		code = new String[] { "/*jshint unused:badoption */", "function foo(a, b) {", "let i = 3;", "}", "foo();" };
		th.newTest();
		th.addError(1, 1, "Bad option value.");
		th.addError(2, 17, "'b' is defined but never used.");
		th.addError(2, 14, "'a' is defined but never used.");
		th.addError(3, 5, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));
	}

	@Test
	public void testUnusedWithLatedefFunction() {
		// Regression for gh-2363, gh-2282, gh-2191
		String[] code = {
				"exports.a = a;",
				"function a() {}",
				"exports.b = function() { b(); };",
				"function b() {}",
				"(function() {",
				"  function c() { d(); }",
				"  window.c = c;",
				"  function d() {}",
				"})();",
				"var e;",
				"(function() {",
				"  e();",
				"  function e(){}",
				"})();",
				""
		};

		th.addError(10, 5, "'e' is defined but never used.");
		th.test(code, new LinterOptions().set("undef", false).set("unused", true).set("node", true));
	}

	// Regression test for `undef` to make sure that ...
	@Test
	public void testUndefInFunctionScope() {
		String src = th.readFile("src/test/resources/fixtures/undef_func.js");

		// Make sure that the lint is clean with and without undef.
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src, new LinterOptions().set("es3", true).set("undef", true));
	}

	/** Option `scripturl` allows the use of javascript-type URLs */
	@Test
	public void testScripturl() {
		String[] code = {
				"var foo = { 'count': 12, 'href': 'javascript:' };",
				"foo = 'javascript:' + 'xyz';"
		};
		String src = th.readFile("src/test/resources/fixtures/scripturl.js");

		// Make sure there is an error
		th.addError(1, 47, "Script URL.");
		th.addError(2, 20, "Script URL."); // 2 times?
		th.addError(2, 7, "JavaScript URL.");
		th.test(code, new LinterOptions().set("es3", true));

		// Make sure the error goes away when javascript URLs are tolerated
		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("scripturl", true));

		// Make sure an error does not exist for labels that look like URLs (GH-1013)
		th.newTest();
		th.test(src, new LinterOptions().set("es3", true));
	}

	/**
	 * Option `forin` disallows the use of for in loops without hasOwnProperty.
	 *
	 * The for in statement is used to loop through the names of properties
	 * of an object, including those inherited through the prototype chain.
	 * The method hasOwnPropery is used to check if the property belongs to
	 * an object or was inherited through the prototype chain.
	 */
	@Test
	public void testForin() {
		String src = th.readFile("src/test/resources/fixtures/forin.js");
		String msg = "The body of a for in should be wrapped in an if statement to filter unwanted " +
				"properties from the prototype.";

		// Make sure there are no other errors
		th.test(src, new LinterOptions().set("es3", true));

		// Make sure it fails when forin is true
		th.addError(15, 1, msg);
		th.addError(23, 1, msg);
		th.addError(37, 3, msg);
		th.addError(43, 9, msg);
		th.addError(73, 1, msg);
		th.test(src, new LinterOptions().set("es3", true).set("forin", true));
	}

	/**
	 * Option `loopfunc` allows you to use function expression in the loop.
	 * E.g.:
	 * while (true) x = function (test) {};
	 *
	 * This is generally a bad idea since it is too easy to make a
	 * closure-related mistake.
	 */
	@Test
	public void testLoopfunc() {
		String src = th.readFile("src/test/resources/fixtures/loopfunc.js");

		// By default, not functions are allowed inside loops
		th.addError(4, 13,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (v)");
		th.addError(8, 13,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (v)");
		th.addError(20, 11,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (nonExistent)");
		th.addError(25, 13,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (p)");
		th.addError(12, 5, "Function declarations should not be placed in blocks. Use a function " +
				"expression or move the statement to the top of the outer function.");
		th.addError(42, 7,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (i)");
		th.test(src, new LinterOptions().set("es3", true));

		// When loopfunc is true, only function declaration should fail.
		// Expressions are okay.
		th.newTest();
		th.addError(12, 5, "Function declarations should not be placed in blocks. Use a function " +
				"expression or move the statement to the top of the outer function.");
		th.test(src, new LinterOptions().set("es3", true).set("loopfunc", true));

		String[] es6LoopFuncSrc = {
				"for (var i = 0; i < 5; i++) {",
				"  var y = w => i;",
				"}",
				"for (i = 0; i < 5; i++) {",
				"  var z = () => i;",
				"}",
				"for (i = 0; i < 5; i++) {",
				"  y = i => i;", // not capturing
				"}",
				"for (i = 0; i < 5; i++) {",
				"  y = { a() { return i; } };",
				"}",
				"for (i = 0; i < 5; i++) {",
				"  y = class { constructor() { this.i = i; }};",
				"}",
				"for (i = 0; i < 5; i++) {",
				"  y = { a() { return () => i; } };",
				"}"
		};

		th.newTest();
		th.addError(2, 13,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (i)");
		th.addError(5, 11,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (i)");
		th.addError(11, 9,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (i)");
		th.addError(14, 15,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (i)");
		th.addError(17, 9,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (i)");
		th.test(es6LoopFuncSrc, new LinterOptions().set("esnext", true));

		// functions declared in the expressions that loop should warn
		String[] src2 = {
				"for(var i = 0; function a(){return i;}; i++) { break; }",
				"var j;",
				"while(function b(){return j;}){}",
				"for(var c = function(){return j;};;){c();}"
		};

		th.newTest();
		th.addError(1, 25,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (i)");
		th.addError(3, 16,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (j)");
		th.test(src2, new LinterOptions().set("es3", true).set("loopfunc", false).set("boss", true));

		th.newTest("Allows closing over immutable bindings (ES5)");
		th.addError(6, 8,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (outerVar)");
		th.addError(7, 8,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (innerVar)");
		th.test(new String[] {
				"var outerVar;",
				"",
				"while (false) {",
				"  var innerVar;",
				"",
				"  void function() { var localVar; return outerVar; };",
				"  void function() { var localVar; return innerVar; };",
				"  void function() { var localVar; return localVar; };",
				"",
				"}"
		});

		th.newTest("Allows closing over immutable bindings (globals)");
		th.addError(8, 8,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (mutableGlobal)");
		th.addError(15, 10,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (immutableGlobal)");
		th.test(new String[] {
				"/* globals immutableGlobal: false, mutableGlobal: true */",
				"while (false) {",
				"  void function() { return eval; };",
				"  void function() { return Infinity; };",
				"  void function() { return NaN; };",
				"  void function() { return undefined; };",
				"  void function() { return immutableGlobal; };",
				"  void function() { return mutableGlobal; };",
				"}",
				"",
				"// Should recognize shadowing",
				"(function() {",
				"  var immutableGlobal;",
				"  while (false) {",
				"    void function() { return immutableGlobal; };",
				"  }",
				"}());"
		});

		th.newTest("Allows closing over immutable bindings (ES2015)");
		th.addError(10, 8,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (outerLet)");
		th.addError(11, 8,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (innerLet)");
		th.addError(18, 8,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (OuterClass)");
		th.addError(19, 8,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (InnerClass)");
		th.test(new String[] {
				"let outerLet;",
				"const outerConst = 0;",
				"class OuterClass {}",
				"",
				"while (false) {",
				"  let innerLet;",
				"  const innerConst = 0;",
				"  class InnerClass {}",
				"",
				"  void function() { let localLet; return outerLet; };",
				"  void function() { let localLet; return innerLet; };",
				"  void function() { let localLet; return localLet; };",
				"",
				"  void function() { const localConst = 0; return outerConst; };",
				"  void function() { const localConst = 0; return innerConst; };",
				"  void function() { const localConst = 0; return localConst; };",
				"",
				"  void function() { class LocalClass {} return OuterClass; };",
				"  void function() { class LocalClass {} return InnerClass; };",
				"  void function() { class LocalClass {} return LocalClass; };",
				"}"
		}, new LinterOptions().set("esversion", 2015));

		th.newTest("W083 lists multiple outer scope variables");
		th.addError(3, 11,
				"Functions declared within loops referencing an outer scoped variable may lead to confusing semantics. (a, b)");
		th.test(new String[] {
				"var a, b;",
				"for (;;) {",
				"  var f = function() {",
				"    return a + b;",
				"  };",
				"}"
		});
	}

	/** Option `boss` unlocks some useful but unsafe features of JavaScript. */
	@Test
	public void testBoss() {
		String src = th.readFile("src/test/resources/fixtures/boss.js");

		// By default, warn about suspicious assignments
		th.addError(1, 7, "Expected a conditional expression and instead saw an assignment.");
		th.addError(4, 12, "Expected a conditional expression and instead saw an assignment.");
		th.addError(7, 15, "Expected a conditional expression and instead saw an assignment.");
		th.addError(12, 12, "Expected a conditional expression and instead saw an assignment.");

		// GH-657
		th.addError(14, 7, "Expected a conditional expression and instead saw an assignment.");
		th.addError(17, 12, "Expected a conditional expression and instead saw an assignment.");
		th.addError(20, 15, "Expected a conditional expression and instead saw an assignment.");
		th.addError(25, 12, "Expected a conditional expression and instead saw an assignment.");

		// GH-670
		th.addError(28, 12, "Did you mean to return a conditional instead of an assignment?");
		th.addError(32, 14, "Did you mean to return a conditional instead of an assignment?");
		th.test(src, new LinterOptions().set("es3", true));

		// But if you are the boss, all is good
		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("boss", true));
	}

	/**
	 * Options `eqnull` allows you to use '== null' comparisons.
	 * It is useful when you want to check if value is null _or_ undefined.
	 */
	@Test
	public void testEqnull() {
		String[] code = {
				"if (e == null) doSomething();",
				"if (null == e) doSomething();",
				"if (e != null) doSomething();",
				"if (null != e) doSomething();"
		};

		// By default, warn about `== null` comparison
		/**
		 * This test previously asserted the issuance of warning W041.
		 * W041 has since been removed, but the test is maintained in
		 * order to discourage regressions.
		 */
		th.test(code, new LinterOptions().set("es3", true));

		// But when `eqnull` is true, no questions asked
		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("eqnull", true));

		// Make sure that `eqnull` has precedence over `eqeqeq`
		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("eqeqeq", true).set("eqnull", true));
	}

	/**
	 * Option `supernew` allows you to use operator `new` with anonymous functions
	 * and objects without invocation.
	 *
	 * Ex.:
	 * new function (test) { ... };
	 * new Date;
	 */
	@Test
	public void testSupernew() {
		String src = th.readFile("src/test/resources/fixtures/supernew.js");

		th.addError(1, 9, "Weird construction. Is 'new' necessary?");
		th.addError(9, 1, "Missing '()' invoking a constructor.");
		th.addError(11, 13, "Missing '()' invoking a constructor.");
		th.test(src, new LinterOptions().set("es3", true));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("supernew", true));
	}

	/** Option `bitwise` disallows the use of bitwise operators. */
	@Test
	public void testBitwise() {
		String[] unOps = { "~" };
		String[] binOps = { "&", "|", "^", "<<", ">>", ">>>" };
		String[] modOps = { "&=", "|=", "^=", "<<=", ">>=", ">>>=" };

		// By default allow bitwise operators
		for (int i = 0; i < unOps.length; i++) {
			String op = unOps[i];

			th.newTest();
			th.test("var b = " + op + "a;", new LinterOptions().set("es3", true));

			th.addError(1, 9, "Unexpected use of '" + op + "'.");
			th.test("var b = " + op + "a;", new LinterOptions().set("es3", true).set("bitwise", true));
		}

		for (int i = 0; i < binOps.length; i++) {
			String op = binOps[i];

			th.newTest();
			th.test("var c = a " + op + " b;", new LinterOptions().set("es3", true));

			th.addError(1, 11, "Unexpected use of '" + op + "'.");
			th.test("var c = a " + op + " b;", new LinterOptions().set("es3", true).set("bitwise", true));
		}

		for (int i = 0; i < modOps.length; i++) {
			String op = modOps[i];

			th.newTest();
			th.test("b " + op + " a;", new LinterOptions().set("es3", true));

			th.addError(1, 3, "Unexpected use of '" + op + "'.");
			th.test("b " + op + " a;", new LinterOptions().set("es3", true).set("bitwise", true));
		}
	}

	/** Option `debug` allows the use of debugger statements. */
	@Test
	public void testDebug1() {
		String code = "function test () { debugger; return true; }";

		// By default disallow debugger statements.
		th.addError(1, 20, "Forgotten 'debugger' statement?");
		th.test(code, new LinterOptions().set("es3", true));

		// But allow them if debug is true.
		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("debug", true));
	}

	/** `debugger` statements without semicolons are found on the correct line */
	@Test
	public void testDebuggerWithoutSemicolons() {
		String[] src = {
				"function test () {",
				"debugger",
				"return true; }"
		};

		// Ensure we mark the correct line when finding debugger statements
		th.addError(2, 1, "Forgotten 'debugger' statement?");
		th.test(src, new LinterOptions().set("es3", true).set("asi", true));
	}

	/** Option `eqeqeq` requires you to use === all the time. */
	@Test
	public void testEqeqeq() {
		String src = th.readFile("src/test/resources/fixtures/eqeqeq.js");

		/**
		 * This test previously asserted the issuance of warning W041.
		 * W041 has since been removed, but the test is maintained in
		 * order to discourage regressions.
		 */
		th.test(src, new LinterOptions().set("es3", true));

		th.newTest();
		th.addError(2, 13, "Expected '===' and instead saw '=='.");
		th.addError(5, 13, "Expected '!==' and instead saw '!='.");
		th.addError(8, 13, "Expected '===' and instead saw '=='.");
		th.test(src, new LinterOptions().set("es3", true).set("eqeqeq", true));
	}

	/** Option `evil` allows the use of eval. */
	@Test
	public void testEvil() {
		String[] src = {
				"eval('hey();');",
				"document.write('');",
				"document.writeln('');",
				"window.execScript('xyz');",
				"new Function('xyz();');",
				"setTimeout('xyz();', 2);",
				"setInterval('xyz();', 2);",
				"var t = document['eval']('xyz');"
		};

		th.addError(1, 1, "eval can be harmful.");
		th.addError(2, 1, "document.write can be a form of eval.");
		th.addError(3, 1, "document.write can be a form of eval.");
		th.addError(4, 18, "eval can be harmful.");
		th.addError(5, 13, "The Function constructor is a form of eval.");
		th.addError(6, 1, "Implied eval. Consider passing a function instead of a string.");
		th.addError(7, 1, "Implied eval. Consider passing a function instead of a string.");
		th.addError(8, 24, "eval can be harmful.");
		th.test(src, new LinterOptions().set("es3", true).set("browser", true));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("evil", true).set("browser", true));
	}

	/**
	 * Option `immed` forces you to wrap immediate invocations in parens.
	 *
	 * Functions in JavaScript can be immediately invoce but that can confuse
	 * readers of your code. To make it less confusing, wrap the invocations in
	 * parens.
	 *
	 * E.g. (note the parens):
	 * var a = (function (test) {
	 * return 'a';
	 * }());
	 * console.log(a); // --> 'a'
	 */
	@Test
	public void testImmed() {
		String src = th.readFile("src/test/resources/fixtures/immed.js");

		th.test(src, new LinterOptions().set("es3", true));

		th.newTest();
		th.addError(3, 3, "Wrap an immediate function invocation in parens " +
				"to assist the reader in understanding that the expression " +
				"is the result of a function, and not the function itself.");
		th.addError(13, 9, "Wrapping non-IIFE function literals in parens is unnecessary.");
		th.test(src, new LinterOptions().set("es3", true).set("immed", true));

		// Regression for GH-900
		th.newTest();
		// .addError(1, 23232323, "Expected an assignment or function call and instead
		// saw an expression.")
		th.addError(1, 31, "Expected an identifier and instead saw ')'.");
		th.addError(1, 30, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 14, "Unmatched '{'.");
		th.addError(1, 31, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 31, "Missing semicolon.");
		th.addError(1, 32, "Unrecoverable syntax error. (100% scanned).");
		th.test("(function () { if (true) { }());", new LinterOptions().set("es3", true).set("immed", true));
	}

	/** Option `plusplus` prohibits the use of increments/decrements. */
	@Test
	public void testPlusplus() {
		String[] ops = { "++", "--" };

		// By default allow bitwise operators
		for (int i = 0; i < ops.length; i++) {
			String op = ops[i];
			th.test("var i = j" + op + ";", new LinterOptions().set("es3", true));
			th.test("var i = " + op + "j;", new LinterOptions().set("es3", true));
		}

		for (int i = 0; i < ops.length; i++) {
			String op = ops[i];
			th.newTest();
			th.addError(1, 10, "Unexpected use of '" + op + "'.");
			th.test("var i = j" + op + ";", new LinterOptions().set("es3", true).set("plusplus", true));

			th.newTest();
			th.addError(1, 9, "Unexpected use of '" + op + "'.");
			th.test("var i = " + op + "j;", new LinterOptions().set("es3", true).set("plusplus", true));
		}
	}

	/**
	 * Option `newcap` requires constructors to be capitalized.
	 *
	 * Constructors are functions that are designed to be used with the `new`
	 * statement.
	 * `new` creates a new object and binds it to the implied this parameter.
	 * A constructor executed without new will have its this assigned to a global
	 * object,
	 * leading to errors.
	 *
	 * Unfortunately, JavaScript gives us absolutely no way of telling if a function
	 * is a
	 * constructor. There is a convention to capitalize all constructor names to
	 * prevent
	 * those mistakes. This option enforces that convention.
	 */
	@Test
	public void testNewcap() {
		String src = th.readFile("src/test/resources/fixtures/newcap.js");

		th.test(src, new LinterOptions().set("es3", true)); // By default, everything is fine

		// When newcap is true, enforce the conventions
		th.newTest();
		th.addError(1, 15, "A constructor name should start with an uppercase letter.");
		th.addError(5, 7, "Missing 'new' prefix when invoking a constructor.");
		th.addError(10, 15, "A constructor name should start with an uppercase letter.");
		th.addError(14, 13, "A constructor name should start with an uppercase letter.");
		th.test(src, new LinterOptions().set("es3", true).set("newcap", true));
	}

	/** Option `sub` allows all forms of subscription. */
	@Test
	public void testSub() {
		th.addError(1, 17, "['prop'] is better written in dot notation.");
		th.test("window.obj = obj['prop'];", new LinterOptions().set("es3", true));

		th.newTest();
		th.test("window.obj = obj['prop'];", new LinterOptions().set("es3", true).set("sub", true));
	}

	/** Option `strict` requires you to use "use strict"; */
	@Test
	public void testStrict() {
		String code = "(function (test) { return; }());";
		String code1 = "(function (test) { \"use strict\"; return; }());";
		String code2 = "var obj = Object({ foo: 'bar' });";
		String code3 = "'use strict'; \n function hello() { return; }";
		String src = th.readFile("src/test/resources/fixtures/strict_violations.js");
		String src2 = th.readFile("src/test/resources/fixtures/strict_incorrect.js");
		String src3 = th.readFile("src/test/resources/fixtures/strict_newcap.js");

		th.test(code, new LinterOptions().set("es3", true));
		th.test(code1, new LinterOptions().set("es3", true));

		th.addError(1, 20, "Missing \"use strict\" statement.");
		th.test(code, new LinterOptions().set("es3", true).set("strict", true));
		th.addError(1, 26, "Missing \"use strict\" statement.");
		th.test(code, new LinterOptions().set("es3", true).set("strict", "global"));
		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("strict", "implied"));

		th.test(code1, new LinterOptions().set("es3", true).set("strict", true));
		th.test(code1, new LinterOptions().set("es3", true).set("strict", "global"));
		th.addError(1, 20, "Unnecessary directive \"use strict\".");
		th.test(code1, new LinterOptions().set("es3", true).set("strict", "implied"));

		// Test for strict mode violations
		th.newTest();
		th.addError(4, 36,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.addError(7, 52, "Strict violation.");
		th.addError(8, 52, "Strict violation.");
		th.test(src, new LinterOptions().set("es3", true).set("strict", true));
		th.test(src, new LinterOptions().set("es3", true).set("strict", "global"));

		th.newTest();
		th.addError(4, 5, "Expected an assignment or function call and instead saw an expression.");
		th.addError(9, 17, "Missing semicolon.");
		th.addError(28, 9, "Expected an assignment or function call and instead saw an expression.");
		th.addError(53, 9, "Expected an assignment or function call and instead saw an expression.");
		th.test(src2, new LinterOptions().set("es3", true).set("strict", false));

		th.newTest();
		th.test(src3, new LinterOptions().set("es3", true));

		th.newTest();
		th.test(code2, new LinterOptions().set("es3", true).set("strict", true));
		th.addError(1, 33, "Missing \"use strict\" statement.");
		th.test(code2, new LinterOptions().set("es3", true).set("strict", "global"));

		th.newTest();
		th.test(code3, new LinterOptions().set("strict", "global"));
		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.test(code3, new LinterOptions().set("strict", true));
		th.addError(1, 1, "Unnecessary directive \"use strict\".");
		th.test(code3, new LinterOptions().set("strict", "implied"));

		JSHint jshint = new JSHint();
		for (String val : new String[] { "true", "false", "global", "implied" }) {
			jshint.lint("/*jshint strict: " + val + " */");
			assertEquals(jshint.generateSummary().getOptions().asString("strict"), val);
		}

		th.newTest();
		th.addError(1, 1, "Bad option value.");
		th.test("/*jshint strict: foo */");

		th.newTest("environments have precedence over 'strict: true'");
		th.test(code3, new LinterOptions().set("strict", true).set("node", true));

		th.newTest("gh-2668");
		th.addError(1, 6, "Missing \"use strict\" statement.");
		th.test("a = 2;", new LinterOptions().set("strict", "global"));

		th.newTest("Warning location, missing semicolon (gh-3528)");
		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.addError(1, 13, "Missing semicolon.");
		th.test("'use strict'\n");

		th.newTest("Warning location among other directives");
		th.addError(2, 1, "Use the function form of \"use strict\".");
		th.test(new String[] {
				"'use another-directive';",
				"'use strict';",
				"'use a-third-directive';"
		});
	}

	/**
	 * This test asserts sub-optimal behavior.
	 *
	 * In the "browserify", "node" and "phantomjs" environments, user code is not
	 * executed in the global scope directly. This means that top-level `use
	 * strict` directives, although seemingly global, do *not* enable ES5 strict
	 * mode for other scripts executed in the same environment. Because of this,
	 * the `strict` option should enforce a top-level `use strict` directive in
	 * those environments.
	 *
	 * The `strict` option was implemented without consideration for these
	 * environments, so the sub-optimal behavior must be preserved for backwards
	 * compatability.
	 *
	 * JSHINT_TODO: Interpret `strict: true` as `strict: global` in the Browserify,
	 * Node.js, and PhantomJS environments, and remove this test in JSHint 3
	 */
	@Test
	public void testStrictEnvs() {
		String[] partialStrict = {
				"void 0;",
				"(function() { void 0; }());",
				"(function() { 'use strict'; void 0; }());"
		};

		th.addError(2, 15, "Missing \"use strict\" statement.");
		th.test(partialStrict, new LinterOptions().set("strict", true).set("browserify", true));
		th.test(partialStrict, new LinterOptions().set("strict", true).set("node", true));
		th.test(partialStrict, new LinterOptions().set("strict", true).set("phantom", true));

		partialStrict = new String[] {
				"(() =>",
				"  void 0",
				")();"
		};

		th.newTest("Block-less arrow functions in the Browserify env");
		th.addError(3, 1, "Missing \"use strict\" statement.");
		th.test(partialStrict, new LinterOptions().set("esversion", 6).set("strict", true).set("browserify", true));

		th.newTest("Block-less arrow function in the Node.js environment");
		th.addError(3, 1, "Missing \"use strict\" statement.");
		th.test(partialStrict, new LinterOptions().set("esversion", 6).set("strict", true).set("node", true));

		th.newTest("Block-less arrow function in the PhantomJS environment");
		th.addError(3, 1, "Missing \"use strict\" statement.");
		th.test(partialStrict, new LinterOptions().set("esversion", 6).set("strict", true).set("phantom", true));
	}

	/**
	 * The following test asserts sub-optimal behavior.
	 *
	 * Through the `strict` and `globalstrict` options, JSHint can be configured to
	 * issue warnings when code is not in strict mode. Historically, JSHint has
	 * issued these warnings on a per-statement basis in global code, leading to
	 * "noisy" output through the repeated reporting of the missing directive.
	 */
	@Test
	public void testStrictNoise() {
		th.newTest("global scope");
		th.addError(1, 7, "Missing \"use strict\" statement.");
		th.addError(2, 7, "Missing \"use strict\" statement.");
		th.test(new String[] {
				"void 0;",
				"void 0;"
		}, new LinterOptions().set("strict", true).set("globalstrict", true));

		th.newTest("function scope");
		th.addError(2, 3, "Missing \"use strict\" statement.");
		th.test(new String[] {
				"(function() {",
				"  void 0;",
				"  void 0;",
				"}());"
		}, new LinterOptions().set("strict", true));

		th.test(new String[] {
				"(function() {",
				"  (function() {",
				"    void 0;",
				"    void 0;",
				"  }());",
				"}());"
		}, new LinterOptions().set("strict", true));
	}

	/** Option `globalstrict` allows you to use global "use strict"; */
	@Test
	public void testGlobalstrict() {
		String[] code = {
				"\"use strict\";",
				"function hello() { return; }"
		};

		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.test(code, new LinterOptions().set("es3", true).set("strict", true));

		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("globalstrict", true));

		// Check that globalstrict also enabled strict
		th.newTest();
		th.addError(1, 26, "Missing \"use strict\" statement.");
		th.test(code[1], new LinterOptions().set("es3", true).set("globalstrict", true));

		// Don't enforce "use strict"; if strict has been explicitly set to false
		th.newTest();
		th.test(code[1], new LinterOptions().set("es3", true).set("globalstrict", true).set("strict", false));

		th.newTest("co-occurence with 'strict: global' (via configuration)");
		th.addError(0, 0, "Incompatible values for the 'strict' and 'globalstrict' linting options. (0% scanned).");
		th.test("this is not JavaScript", new LinterOptions().set("strict", "global").set("globalstrict", false));
		th.test("this is not JavaScript", new LinterOptions().set("strict", "global").set("globalstrict", true));

		th.newTest("co-occurence with 'strict: global' (via in-line directive");
		th.addError(2, 1, "Incompatible values for the 'strict' and 'globalstrict' linting options. (66% scanned).");
		th.test(new String[] {
				"",
				"// jshint globalstrict: true",
				"this is not JavaScript"
		}, new LinterOptions().set("strict", "global"));
		th.test(new String[] {
				"",
				"// jshint globalstrict: false",
				"this is not JavaScript"
		}, new LinterOptions().set("strict", "global"));
		th.test(new String[] {
				"",
				"// jshint strict: global",
				"this is not JavaScript"
		}, new LinterOptions().set("globalstrict", true));
		th.test(new String[] {
				"",
				"// jshint strict: global",
				"this is not JavaScript"
		}, new LinterOptions().set("globalstrict", false));

		th.newTest("co-occurence with internally-set 'strict: gobal' (module code)");
		th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("esnext", true)
				.set("module", true));

		th.newTest("co-occurence with internally-set 'strict: gobal' (Node.js code)");
		th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("node", true));

		th.newTest("co-occurence with internally-set 'strict: gobal' (Phantom.js code)");
		th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("phantom", true));

		th.newTest("co-occurence with internally-set 'strict: gobal' (Browserify code)");
		th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("browserify", true));

		// Check that we can detect missing "use strict"; statement for code that is
		// not inside a function
		code = new String[] {
				"var a = 1;",
				"a += 1;",
				"function func() {}"
		};
		th.newTest();
		th.addError(1, 10, "Missing \"use strict\" statement.");
		th.addError(2, 7, "Missing \"use strict\" statement.");
		th.test(code, new LinterOptions().set("globalstrict", true).set("strict", true));

		// globalscript does not prevent you from using only the function-mode
		// "use strict";
		th.newTest();
		th.test("(function (test) { \"use strict\"; return; }());",
				new LinterOptions().set("globalstrict", true).set("strict", true));

		th.newTest("gh-2661");
		th.test("'use strict';", new LinterOptions().set("strict", false).set("globalstrict", true));

		th.newTest("gh-2836 (1)");
		th.test(new String[] {
				"// jshint globalstrict: true",
				// The specific option set by the following directive is not relevant.
				// Any option set by another directive will trigger the regression.
				"// jshint undef: true"
		});

		th.newTest("gh-2836 (2)");
		th.test(new String[] {
				"// jshint strict: true, globalstrict: true",
				// The specific option set by the following directive is not relevant.
				// Any option set by another directive will trigger the regression.
				"// jshint undef: true"
		});
	}

	/** Option `laxbreak` allows you to insert newlines before some operators. */
	@Test
	public void testLaxbreak() {
		String src = th.readFile("src/test/resources/fixtures/laxbreak.js");

		th.addError(2, 5, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(3, 3, "Comma warnings can be turned off with 'laxcomma'.");
		th.addError(12, 10, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.test(src, new LinterOptions().set("es3", true));

		String[] ops = { "||", "&&", "*", "/", "%", "+", "-", ">=", "==", "===", "!=", "!==", ">", "<", "<=",
				"instanceof" };

		for (int i = 0; i < ops.length; i++) {
			String op = ops[i];
			String[] code = { "var a = b ", op + " c;" };

			th.newTest();
			th.addError(2, 1,
					"Misleading line break before '" + op + "'; readers may interpret this as an expression boundary.");
			th.test(code, new LinterOptions().set("es3", true));

			th.newTest();
			th.test(code, new LinterOptions().set("es3", true).set("laxbreak", true));
		}

		String[] code = { "var a = b ", "? c : d;" };
		th.newTest();
		th.addError(2, 1, "Misleading line break before '?'; readers may interpret this as an expression boundary.");
		th.test(code, new LinterOptions().set("es3", true));

		th.newTest();
		th.test(code, new LinterOptions().set("es3", true).set("laxbreak", true));
	}

	@Test
	public void testValidthis() {
		String src = th.readFile("src/test/resources/fixtures/strict_this.js");

		th.addError(8, 9,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.addError(9, 19,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.addError(11, 19,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(src, new LinterOptions().set("es3", true));

		src = th.readFile("src/test/resources/fixtures/strict_this2.js");
		th.newTest();
		th.test(src, new LinterOptions().set("es3", true));

		// Test for erroneus use of validthis

		String[] code = { "/*jshint validthis:true */", "hello();" };
		th.newTest();
		th.addError(1, 1, "Option 'validthis' can't be used in a global scope.");
		th.test(code, new LinterOptions().set("es3", true));

		code = new String[] { "function x() {", "/*jshint validthis:heya */", "hello();", "}" };
		th.newTest();
		th.addError(2, 1, "Bad option value.");
		th.test(code, new LinterOptions().set("es3", true));
	}

	/**
	 * Test string relevant options
	 * multistr allows multiline strings
	 */
	@Test
	public void testStrings() {
		String src = th.readFile("src/test/resources/fixtures/strings.js");

		th.addError(9, 20, "Unclosed string.");
		th.addError(10, 1, "Unclosed string.");
		th.addError(15, 1, "Unclosed string.");
		th.addError(25, 16, "Octal literals are not allowed in strict mode.");
		th.test(src, new LinterOptions().set("es3", true).set("multistr", true));

		th.newTest();
		th.addError(3, 21, "Bad escaping of EOL. Use option multistr if needed.");
		th.addError(4, 2, "Bad escaping of EOL. Use option multistr if needed.");
		th.addError(9, 20, "Unclosed string.");
		th.addError(10, 1, "Unclosed string.");
		th.addError(14, 21, "Bad escaping of EOL. Use option multistr if needed.");
		th.addError(15, 1, "Unclosed string.");
		th.addError(25, 16, "Octal literals are not allowed in strict mode.");
		th.addError(29, 36, "Bad escaping of EOL. Use option multistr if needed.");
		th.test(src, new LinterOptions().set("es3", true));
	}

	/**
	 * Test the `quotmark` option
	 * quotmark quotation mark or true (=ensure consistency)
	 */
	@Test
	public void testQuotes() {
		String src = th.readFile("src/test/resources/fixtures/quotes.js");
		String src2 = th.readFile("src/test/resources/fixtures/quotes2.js");

		th.test(src, new LinterOptions().set("es3", true));

		th.addError(3, 21, "Mixed double and single quotes.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", true));

		th.newTest();
		th.addError(3, 21, "Strings must use singlequote.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", "single"));

		th.newTest();
		th.addError(2, 21, "Strings must use doublequote.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", "double"));

		// test multiple runs (must have the same result)
		th.newTest();
		th.addError(3, 21, "Mixed double and single quotes.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", true));
		th.test(src2, new LinterOptions().set("es3", true).set("quotmark", true));
	}

	// Test the `quotmark` option when defined as a JSHint comment.
	@Test
	public void testQuotesInline() {
		th.addError(6, 24, "Strings must use doublequote.");
		th.addError(14, 24, "Strings must use singlequote.");
		th.addError(21, 24, "Mixed double and single quotes.");
		th.addError(32, 5, "Bad option value.");
		th.test(th.readFile("src/test/resources/fixtures/quotes3.js"));
	}

	// Test the `quotmark` option along with TemplateLiterals.
	@Test
	public void testQuotesAndTemplateLiterals() {
		String src = th.readFile("src/test/resources/fixtures/quotes4.js");

		// Without esnext
		th.addError(2, 13, "'template literal syntax' is only available in ES6 (use 'esversion: 6').");
		th.test(src);

		// With esnext
		th.newTest();
		th.test(src, new LinterOptions().set("esnext", true));

		// With esnext and single quotemark
		th.newTest();
		th.test(src, new LinterOptions().set("esnext", true).set("quotmark", "single"));

		// With esnext and double quotemark
		th.newTest();
		th.addError(1, 21, "Strings must use doublequote.");
		th.test(src, new LinterOptions().set("esnext", true).set("quotmark", "double"));
	}

	@Test(groups = { "scope" })
	public void testScopeBasic() {
		String src = th.readFile("src/test/resources/fixtures/scope.js");

		th.newTest("1");
		th.addError(11, 14, "'j' used out of scope.");
		th.addError(11, 21, "'j' used out of scope.");
		th.addError(11, 28, "'j' used out of scope.");
		th.addError(12, 13, "'x' used out of scope.");
		th.addError(20, 9, "'aa' used out of scope.");
		th.addError(27, 9, "'bb' used out of scope.");
		th.addError(37, 9, "'cc' is not defined.");
		th.addError(42, 5, "'bb' is not defined.");
		th.addError(53, 5, "'xx' used out of scope.");
		th.addError(54, 5, "'yy' used out of scope.");
		th.test(src, new LinterOptions().set("es3", true));

		th.newTest("2");
		th.addError(37, 9, "'cc' is not defined.");
		th.addError(42, 5, "'bb' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("funcscope", true));
	}

	@Test(groups = { "scope" })
	public void testScopeCrossBlocks() {
		String code = th.readFile("src/test/resources/fixtures/scope-cross-blocks.js");

		th.addError(3, 8, "'topBlockBefore' used out of scope.");
		th.addError(4, 8, "'nestedBlockBefore' used out of scope.");
		th.addError(11, 10, "'nestedBlockBefore' used out of scope.");
		th.addError(27, 10, "'nestedBlockAfter' used out of scope.");
		th.addError(32, 8, "'nestedBlockAfter' used out of scope.");
		th.addError(33, 8, "'topBlockAfter' used out of scope.");
		th.test(code);

		th.newTest();
		th.test(code, new LinterOptions().set("funcscope", true));
	}

	/**
	 * Tests `esnext` and `moz` options.
	 *
	 * This test simply makes sure that options are recognizable
	 * and do not reset ES5 mode (see GH-1068)
	 *
	 */
	@Test
	public void testEsnext() {
		String src = th.readFile("src/test/resources/fixtures/const.js");

		String[] code = {
				"const myConst = true;",
				"const foo = 9;",
				"var myConst = function (test) { };",
				"foo = \"hello world\";",
				"var a = { get x() {} };"
		};

		th.addError(21, 7, "const 'immutable4' is initialized to 'undefined'.");
		th.test(src, new LinterOptions().set("esnext", true));
		th.test(src, new LinterOptions().set("moz", true));

		th.newTest();
		th.addError(3, 5, "'myConst' has already been declared.");
		th.addError(4, 1, "Attempting to override 'foo' which is a constant.");
		th.test(code, new LinterOptions().set("esnext", true));
		th.test(code, new LinterOptions().set("moz", true));
	}

	// The `moz` option should not preclude ES6
	@Test
	public void testMozAndEs6() {
		String[] src = {
				"var x = () => {};",
				"function* toArray(...rest) {",
				"  void new.target;",
				"  yield rest;",
				"}",
				"var y = [...x];"
		};

		th.test(src, new LinterOptions().set("esversion", 6));
		th.test(src, new LinterOptions().set("esversion", 6).set("moz", true));
	}

	/**
	 * Tests the `maxlen` option
	 */
	@Test
	public void testMaxlen() {
		String src = th.readFile("src/test/resources/fixtures/maxlen.js");

		th.addError(3, 24, "Line is too long.");
		th.addError(4, 29, "Line is too long.");
		th.addError(5, 40, "Line is too long.");
		th.addError(6, 46, "Line is too long.");
		// line 7 and more are exceptions and won't trigger the error
		th.test(src, new LinterOptions().set("es3", true).set("maxlen", 23));
	}

	/**
	 * Tests the `laxcomma` option
	 */
	@Test
	public void testLaxcomma() {
		String src = th.readFile("src/test/resources/fixtures/laxcomma.js");

		// All errors.
		th.addError(1, 9, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(2, 3, "Comma warnings can be turned off with 'laxcomma'.");
		th.addError(2, 9, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(6, 10, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(10, 3, "Misleading line break before '&&'; readers may interpret this as an expression boundary.");
		th.addError(15, 9, "Misleading line break before '?'; readers may interpret this as an expression boundary.");
		th.test(src, new LinterOptions().set("es3", true));

		// Allows bad line breaking, but not on commas.
		th.newTest();
		th.addError(1, 9, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(2, 3, "Comma warnings can be turned off with 'laxcomma'.");
		th.addError(2, 9, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.addError(6, 10, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.test(src, new LinterOptions().set("es3", true).set("laxbreak", true));

		// Allows comma-first style but warns on bad line breaking
		th.newTest();
		th.addError(10, 3, "Misleading line break before '&&'; readers may interpret this as an expression boundary.");
		th.addError(15, 9, "Misleading line break before '?'; readers may interpret this as an expression boundary.");
		th.test(src, new LinterOptions().set("es3", true).set("laxcomma", true));

		// No errors if both laxbreak and laxcomma are turned on
		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("laxbreak", true).set("laxcomma", true));
	}

	@Test
	public void testUnnecessarySemicolon() {
		String[] code = {
				"function foo() {",
				"    var a;;",
				"}"
		};

		th.addError(2, 11, "Unnecessary semicolon.");
		th.test(code, new LinterOptions().set("es3", true));
	}

	@Test
	public void testBlacklist() {
		String src = th.readFile("src/test/resources/fixtures/browser.js");
		String[] code = {
				"/*jshint browser: true */",
				"/*global -event, bar, -btoa */",
				"var a = event.hello();",
				"var c = foo();",
				"var b = btoa(1);",
				"var d = bar();"
		};

		// make sure everything is ok
		th.test(src, new LinterOptions().set("es3", true).set("undef", true).set("browser", true));

		// disallow Node in a predef Object
		th.addError(15, 19, "'Node' is not defined.");
		th.test(src, new LinterOptions().set("undef", true).set("browser", true).addPredefined("-Node", false));

		// disallow Node and NodeFilter in a predef Array
		th.newTest();
		th.addError(14, 20, "'NodeFilter' is not defined.");
		th.addError(15, 19, "'Node' is not defined.");
		th.test(src,
				new LinterOptions().set("undef", true).set("browser", true).setPredefineds("-Node", "-NodeFilter"));

		th.newTest();
		th.addError(3, 9, "'event' is not defined.");
		th.addError(4, 9, "'foo' is not defined.");
		th.addError(5, 9, "'btoa' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true));
	};

	/**
	 * Tests the `maxstatements` option
	 */
	@Test
	public void testMaxstatements() {
		String src = th.readFile("src/test/resources/fixtures/max-statements-per-function.js");

		th.addError(1, 33, "This function has too many statements. (8)");
		th.test(src, new LinterOptions().set("es3", true).set("maxstatements", 7));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("maxstatements", 8));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true));
	};

	/**
	 * Tests the `maxdepth` option
	 */
	@Test
	public void testMaxdepth() {
		String src = th.readFile("src/test/resources/fixtures/max-nested-block-depth-per-function.js");

		th.addError(5, 27, "Blocks are nested too deeply. (2)");
		th.addError(14, 26, "Blocks are nested too deeply. (2)");
		th.test(src, new LinterOptions().set("es3", true).set("maxdepth", 1));

		th.newTest();
		th.addError(9, 28, "Blocks are nested too deeply. (3)");
		th.test(src, new LinterOptions().set("es3", true).set("maxdepth", 2));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("maxdepth", 3));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true));
	};

	/**
	 * Tests the `maxparams` option
	 */
	@Test
	public void testMaxparams() {
		String src = th.readFile("src/test/resources/fixtures/max-parameters-per-function.js");

		th.addError(4, 33, "This function has too many parameters. (3)");
		th.addError(10, 10, "This function has too many parameters. (3)");
		th.addError(16, 11, "This function has too many parameters. (3)");
		th.test(src, new LinterOptions().set("esnext", true).set("maxparams", 2));

		th.newTest();
		th.test(src, new LinterOptions().set("esnext", true).set("maxparams", 3));

		th.newTest();
		th.addError(4, 33, "This function has too many parameters. (3)");
		th.addError(8, 10, "This function has too many parameters. (1)");
		th.addError(9, 14, "This function has too many parameters. (1)");
		th.addError(10, 10, "This function has too many parameters. (3)");
		th.addError(11, 10, "This function has too many parameters. (1)");
		th.addError(13, 11, "This function has too many parameters. (2)");
		th.addError(16, 11, "This function has too many parameters. (3)");
		th.test(src, new LinterOptions().set("esnext", true).set("maxparams", 0));

		th.newTest();
		th.test(src, new LinterOptions().set("esnext", true));

		List<DataSummary.Function> functions = th.getJSHint().generateSummary().getFunctions();
		assertEquals(functions.size(), 9);
		assertEquals(functions.get(0).getMetrics().getParameters(), 0);
		assertEquals(functions.get(1).getMetrics().getParameters(), 3);
		assertEquals(functions.get(2).getMetrics().getParameters(), 0);
		assertEquals(functions.get(3).getMetrics().getParameters(), 1);
		assertEquals(functions.get(4).getMetrics().getParameters(), 1);
		assertEquals(functions.get(5).getMetrics().getParameters(), 3);
		assertEquals(functions.get(6).getMetrics().getParameters(), 1);
		assertEquals(functions.get(7).getMetrics().getParameters(), 2);
		assertEquals(functions.get(8).getMetrics().getParameters(), 3);
	};

	/**
	 * Tests the `maxcomplexity` option
	 */
	@Test
	public void testMaxcomplexity() {
		String src = th.readFile("src/test/resources/fixtures/max-cyclomatic-complexity-per-function.js");

		th.addError(8, 44, "This function's cyclomatic complexity is too high. (2)");
		th.addError(15, 44, "This function's cyclomatic complexity is too high. (2)");
		th.addError(25, 54, "This function's cyclomatic complexity is too high. (2)");
		th.addError(47, 44, "This function's cyclomatic complexity is too high. (8)");
		th.addError(76, 66, "This function's cyclomatic complexity is too high. (2)");
		th.addError(80, 60, "This function's cyclomatic complexity is too high. (2)");
		th.addError(84, 61, "This function's cyclomatic complexity is too high. (2)");
		th.addError(88, 68, "This function's cyclomatic complexity is too high. (2)");
		th.test(src, new LinterOptions().set("es3", true).set("maxcomplexity", 1));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("maxcomplexity", 8));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true));

		th.newTest("nullish coalescing operator");
		th.addError(1, 11, "This function's cyclomatic complexity is too high. (2)");
		th.test(new String[] {
				"function f() { 0 ?? 0; }"
		}, new LinterOptions().set("esversion", 11).set("expr", true).set("maxcomplexity", 1));
	};

	// Metrics output per function.
	@Test
	public void testFnmetrics() {
		JSHint jshint = new JSHint();

		jshint.lint(new String[] {
				"function foo(a, b) { if (a) return b; }",
				"function bar() { var a = 0; a += 1; return a; }",
				"function hasTryCatch() { try { } catch(e) { }}",
				"try { throw e; } catch(e) {}"
		});

		assertEquals(jshint.generateSummary().getFunctions().size(), 3);

		assertEquals(jshint.generateSummary().getFunctions().get(0).getMetrics(), new DataSummary.Metrics(2, 2, 1));
		assertEquals(jshint.generateSummary().getFunctions().get(1).getMetrics(), new DataSummary.Metrics(1, 0, 3));
		assertEquals(jshint.generateSummary().getFunctions().get(2).getMetrics(), new DataSummary.Metrics(2, 0, 1));
	};

	/**
	 * Tests ignored warnings.
	 */
	@Test
	public void testIgnored() {
		String src = th.readFile("src/test/resources/fixtures/ignored.js");

		th.addError(4, 12, "A trailing decimal point can be confused with a dot: '12.'.");
		th.addError(12, 11, "Missing semicolon.");
		th.test(src, new LinterOptions().set("es3", true));

		th.newTest();
		th.addError(12, 11, "Missing semicolon.");
		th.test(src, new LinterOptions().set("es3", true).set("-W047", true));
	};

	/**
	 * Tests ignored warnings being unignored.
	 */
	@Test
	public void testUnignored() {
		String src = th.readFile("src/test/resources/fixtures/unignored.js");

		th.addError(5, 12, "A leading decimal point can be confused with a dot: '.12'.");
		th.test(src, new LinterOptions().set("es3", true));
	};

	/**
	 * Tests that the W117 and undef can be toggled per line.
	 */
	@Test
	public void testPerLineUndefW117() {
		String src = th.readFile("src/test/resources/fixtures/ignore-w117.js");

		th.addError(5, 3, "'c' is not defined.");
		th.addError(11, 3, "'c' is not defined.");
		th.addError(15, 3, "'c' is not defined.");

		th.test(src, new LinterOptions().set("undef", true));
	}

	/**
	 * Tests the `freeze` option -- Warn if native object prototype is assigned to.
	 */
	@Test
	public void testFreeze() {
		String src = th.readFile("src/test/resources/fixtures/nativeobject.js");

		th.addError(3, 16, "Extending prototype of native object: 'Array'.");
		th.addError(13, 8, "Extending prototype of native object: 'Boolean'.");
		th.test(src, new LinterOptions().set("freeze", true).set("esversion", 6));

		th.newTest();
		th.test(src, new LinterOptions().set("esversion", 6));
	};

	@Test
	public void testNonbsp() {
		String src = th.readFile("src/test/resources/fixtures/nbsp.js");

		th.test(src, new LinterOptions().set("sub", true));

		th.addError(1, 19, "This line contains non-breaking spaces: http://jshint.com/docs/options/#nonbsp");
		th.test(src, new LinterOptions().set("nonbsp", true).set("sub", true));
	};

	/** Option `nocomma` disallows the use of comma operator. */
	@Test
	public void testNocomma() {
		// By default allow comma operator
		th.newTest("nocomma off by default");
		th.test("return 2, 5;", new LinterOptions());

		th.newTest("nocomma main case");
		th.addError(1, 9, "Unexpected use of a comma operator.");
		th.test("return 2, 5;", new LinterOptions().set("nocomma", true));

		th.newTest("nocomma in an expression");
		th.addError(1, 3, "Unexpected use of a comma operator.");
		th.test("(2, 5);", new LinterOptions().set("expr", true).set("nocomma", true));

		th.newTest("avoid nocomma false positives in value literals");
		th.test("return { a: 2, b: [1, 2] };", new LinterOptions().set("nocomma", true));

		th.newTest("avoid nocomma false positives in for statements");
		th.test("for(;;) { return; }", new LinterOptions().set("nocomma", true));

		th.newTest("avoid nocomma false positives in function expressions");
		th.test("return function(a, b) {};", new LinterOptions().set("nocomma", true));

		th.newTest("avoid nocomma false positives in arrow function expressions");
		th.test("return (a, b) => a;", new LinterOptions().set("esnext", true).set("nocomma", true));

		th.newTest("avoid nocomma false positives in destructuring arrays");
		th.test("var [a, b] = [1, 2];", new LinterOptions().set("esnext", true).set("nocomma", true));

		th.newTest("avoid nocomma false positives in destructuring objects");
		th.test("var {a, b} = {a:1, b:2};", new LinterOptions().set("esnext", true).set("nocomma", true));
	};

	@Test
	public void testEnforceall() {
		String src = th.readFile("src/test/resources/fixtures/enforceall.js");

		// Throws errors not normally on be default
		th.addError(1, 19, "This line contains non-breaking spaces: http://jshint.com/docs/options/#nonbsp");
		th.addError(1, 18, "['key'] is better written in dot notation.");
		th.addError(1, 15, "'obj' is not defined.");
		th.addError(1, 32, "Missing semicolon.");
		th.test(src, new LinterOptions().set("enforceall", true));

		// Can override default hard
		th.newTest();
		th.test(src, new LinterOptions().set("enforceall", true).set("nonbsp", false).set("bitwise", false)
				.set("sub", true).set("undef", false).set("unused", false).set("asi", true));

		th.newTest("Does not enable 'regexpu'.");
		th.test("void /./;", new LinterOptions().set("enforceall", true));
	};

	@Test
	public void testRemoveGlobal() {
		String src = th.readFile("src/test/resources/fixtures/removeglobals.js");

		th.addError(1, 1, "'JSON' is not defined.");
		th.test(src, new LinterOptions().set("undef", true).setPredefineds("-JSON", "myglobal"));
	};

	@Test
	public void testIgnoreDelimiters() {
		String src = th.readFile("src/test/resources/fixtures/ignoreDelimiters.js");

		// make sure line/column are still reported properly
		th.addError(6, 37, "Missing semicolon.");
		th.test(src, new LinterOptions()
				.addIgnoreDelimiter("<%=", "%>")
				.addIgnoreDelimiter("<%", "%>")
				.addIgnoreDelimiter("<?php", "?>")
				// make sure single tokens are ignored
				.addIgnoreDelimiter("foo", null)
				.addIgnoreDelimiter(null, "bar"));
	};

	@Test
	public void testEsnextPredefs() {
		String[] code = {
				"/* global alert: true */",
				"var mySym = Symbol(\"name\");",
				"var myBadSym = new Symbol(\"name\");",
				"alert(Reflect);"
		};

		th.addError(3, 16, "Do not use Symbol as a constructor.");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsLoneIdentifier() {
		String[] code = {
				"if ((a)) {}",
				"if ((a) + b + c) {}",
				"if (a + (b) + c) {}",
				"if (a + b + (c)) {}"
		};

		th.addError(1, 5, "Unnecessary grouping operator.");
		th.addError(2, 5, "Unnecessary grouping operator.");
		th.addError(3, 9, "Unnecessary grouping operator.");
		th.addError(4, 13, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsNeighborless() {
		String[] code = {
				"if ((a instanceof b)) {}",
				"if ((a in b)) {}",
				"if ((a + b)) {}"
		};

		th.addError(1, 5, "Unnecessary grouping operator.");
		th.addError(2, 5, "Unnecessary grouping operator.");
		th.addError(3, 5, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};

	@Test(groups = { "singleGroups", "bindingPower" })
	public void testSingleGroupsBindingPowerSingleExpr() {
		String[] code = {
				"var a = !(a instanceof b);",
				"var b = !(a in b);",
				"var c = !!(a && a.b);",
				"var d = (1 - 2) * 3;",
				"var e = 3 * (1 - 2);",
				"var f = a && (b || c);",
				"var g = ~(a * b);",
				"var h = void (a || b);",
				"var i = 2 * (3 - 4 - 5) * 6;",
				"var j = (a = 1) + 2;",
				"var j = (a += 1) / 2;",
				"var k = 'foo' + ('bar' ? 'baz' : 'qux');",
				"var l = 1 + (0 || 3);",
				"var u = a / (b * c);",
				"var v = a % (b / c);",
				"var w = a * (b * c);",
				"var x = z === (b === c);",
				"x = typeof (a + b);",
				// Invalid forms:
				"var j = 2 * ((3 - 4) - 5) * 6;",
				"var l = 2 * ((3 - 4 - 5)) * 6;",
				"var m = typeof(a.b);",
				"var n = 1 - (2 * 3);",
				"var o = (3 * 1) - 2;",
				"var p = ~(Math.abs(a));",
				"var q = -(a[b]);",
				"var r = +(a.b);",
				"var s = --(a.b);",
				"var t = ++(a[b]);",
				"if (a in c || (b in c)) {}",
				"if ((a in c) || b in c) {}",
				"if ((a in c) || (b in c)) {}",
				"if ((a * b) * c) {}",
				"if (a + (b * c)) {}",
				"(a ? a : (a=[])).push(b);",
				"if (a || (1 / 0 == 1 / 0)) {}"
		};

		th.addError(19, 14, "Unnecessary grouping operator.");
		th.addError(20, 14, "Unnecessary grouping operator.");
		th.addError(21, 15, "Unnecessary grouping operator.");
		th.addError(22, 13, "Unnecessary grouping operator.");
		th.addError(23, 9, "Unnecessary grouping operator.");
		th.addError(24, 10, "Unnecessary grouping operator.");
		th.addError(25, 10, "Unnecessary grouping operator.");
		th.addError(26, 10, "Unnecessary grouping operator.");
		th.addError(27, 11, "Unnecessary grouping operator.");
		th.addError(28, 11, "Unnecessary grouping operator.");
		th.addError(29, 15, "Unnecessary grouping operator.");
		th.addError(30, 5, "Unnecessary grouping operator.");
		th.addError(31, 5, "Unnecessary grouping operator.");
		th.addError(31, 17, "Unnecessary grouping operator.");
		th.addError(32, 5, "Unnecessary grouping operator.");
		th.addError(33, 9, "Unnecessary grouping operator.");
		th.addError(34, 10, "Unnecessary grouping operator.");
		th.addError(35, 10, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));

		code = new String[] {
				"var x;",
				"x = (printA || printB)``;",
				"x = (printA || printB)`${}`;",
				"x = (new X)``;",
				"x = (new X)`${}`;",
				// Should warn:
				"x = (x.y)``;",
				"x = (x.y)`${}`;",
				"x = (x[x])``;",
				"x = (x[x])`${}`;",
				"x = (x())``;",
				"x = (x())`${}`;"
		};

		th.newTest();
		th.addError(6, 5, "Unnecessary grouping operator.");
		th.addError(7, 5, "Unnecessary grouping operator.");
		th.addError(8, 5, "Unnecessary grouping operator.");
		th.addError(9, 5, "Unnecessary grouping operator.");
		th.addError(10, 5, "Unnecessary grouping operator.");
		th.addError(11, 5, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("esversion", 6).set("supernew", true));
	};

	@Test(groups = { "singleGroups", "bindingPower" })
	public void testSingleGroupsBindingPowerMultiExpr() {
		String[] code = {
				"var j = (a, b);",
				"var k = -(a, b);",
				"var i = (1, a = 1) + 2;",
				"var k = a ? (b, c) : (d, e);",
				"var j = (a, b + c) * d;",
				"if (a, (b * c)) {}",
				"if ((a * b), c) {}",
				"if ((a, b, c)) {}",
				"if ((a + 1)) {}"
		};

		th.addError(6, 8, "Unnecessary grouping operator.");
		th.addError(7, 5, "Unnecessary grouping operator.");
		th.addError(8, 5, "Unnecessary grouping operator.");
		th.addError(9, 5, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsMultiExpr() {
		String[] code = {
				"var a = (1, 2);",
				"var b = (true, false) ? 1 : 2;",
				"var c = true ? (1, 2) : false;",
				"var d = true ? false : (1, 2);",
				"foo((1, 2));"
		};

		th.test(code, new LinterOptions().set("singleGroups", true));
	};

	// Although the following form is redundant in purely mathematical terms, type
	// coercion semantics in JavaScript make it impossible to statically determine
	// whether the grouping operator is necessary. JSHint should err on the side of
	// caution and allow this form.
	@Test(groups = { "singleGroups" })
	public void testSingleGroupsConcatenation() {
		String[] code = {
				"var a = b + (c + d);",
				"var e = (f + g) + h;"
		};

		th.addError(2, 9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsFunctionExpression() {
		String[] code = {
				"(function() {})();",
				"(function() {}).call();",
				"(function() {}());",
				"(function() {}.call());",
				"if (true) {} (function() {}());",
				"(function() {}());",
				// These usages are not technically necessary, but parenthesis are commonly
				// used to signal that a function expression is going to be invoked
				// immediately.
				"var a = (function() {})();",
				"var b = (function() {}).call();",
				"var c = (function() {}());",
				"var d = (function() {}.call());",
				"var e = { e: (function() {})() };",
				"var f = { f: (function() {}).call() };",
				"var g = { g: (function() {}()) };",
				"var h = { h: (function() {}.call()) };",
				"if ((function() {})()) {}",
				"if ((function() {}).call()) {}",
				"if ((function() {}())) {}",
				"if ((function() {}.call())) {}",
				// Invalid forms:
				"var i = (function() {});"
		};

		th.addError(19, 9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("asi", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsGeneratorExpression() {
		String[] code = {
				"(function*() { yield; })();",
				"(function*() { yield; }).call();",
				"(function*() { yield; }());",
				"(function*() { yield; }.call());",
				"if (true) {} (function*() { yield; }());",
				"(function*() { yield; }());",
				// These usages are not technically necessary, but parenthesis are commonly
				// used to signal that a function expression is going to be invoked
				// immediately.
				"var a = (function*() { yield; })();",
				"var b = (function*() { yield; }).call();",
				"var c = (function*() { yield; }());",
				"var d = (function*() { yield; }.call());",
				"var e = { e: (function*() { yield; })() };",
				"var f = { f: (function*() { yield; }).call() };",
				"var g = { g: (function*() { yield; }()) };",
				"var h = { h: (function*() { yield; }.call()) };",
				"if ((function*() { yield; })()) {}",
				"if ((function*() { yield; }).call()) {}",
				"if ((function*() { yield; }())) {}",
				"if ((function*() { yield; }.call())) {}",
				// Invalid forms:
				"var i = (function*() { yield; });"
		};

		th.addError(19, 9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("asi", true).set("esnext", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsYield() {
		th.newTest("otherwise-invalid position");
		th.test(new String[] {
				"function* g() {",
				"  var x;",
				"  x = 0 || (yield);",
				"  x = 0 || (yield 0);",
				"  x = 0 && (yield);",
				"  x = 0 && (yield 0);",
				"  x = !(yield);",
				"  x = !(yield 0);",
				"  x = !!(yield);",
				"  x = !!(yield 0);",
				"  x = 0 + (yield);",
				"  x = 0 + (yield 0);",
				"  x = 0 - (yield);",
				"  x = 0 - (yield 0);",
				"}"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 6));

		th.newTest("operand delineation");
		th.test(new String[] {
				"function* g() {",
				"  (yield).x = 0;",
				"  x = (yield) ? 0 : 0;",
				"  x = (yield 0) ? 0 : 0;",
				"  x = (yield) / 0;",
				"}"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 6));

		th.addError(2, 3, "Unnecessary grouping operator.");
		th.addError(3, 3, "Unnecessary grouping operator.");
		th.addError(4, 11, "Unnecessary grouping operator.");
		th.addError(5, 11, "Unnecessary grouping operator.");
		th.addError(6, 7, "Unnecessary grouping operator.");
		th.addError(7, 7, "Unnecessary grouping operator.");
		th.addError(8, 8, "Unnecessary grouping operator.");
		th.addError(9, 8, "Unnecessary grouping operator.");
		th.addError(10, 8, "Unnecessary grouping operator.");
		th.addError(11, 8, "Unnecessary grouping operator.");
		th.addError(12, 8, "Unnecessary grouping operator.");
		th.addError(13, 8, "Unnecessary grouping operator.");
		th.addError(14, 8, "Unnecessary grouping operator.");
		th.addError(15, 8, "Unnecessary grouping operator.");
		th.addError(16, 8, "Unnecessary grouping operator.");
		th.addError(17, 8, "Unnecessary grouping operator.");
		th.addError(18, 9, "Unnecessary grouping operator.");
		th.addError(19, 9, "Unnecessary grouping operator.");
		th.addError(20, 9, "Unnecessary grouping operator.");
		th.addError(21, 9, "Unnecessary grouping operator.");
		th.addError(22, 10, "Unnecessary grouping operator.");
		th.addError(23, 10, "Unnecessary grouping operator.");
		th.addError(24, 8, "Unnecessary grouping operator.");
		th.addError(25, 8, "Unnecessary grouping operator.");
		th.addError(26, 8, "Unnecessary grouping operator.");
		th.addError(27, 8, "Unnecessary grouping operator.");
		th.addError(28, 8, "Unnecessary grouping operator.");
		th.addError(29, 8, "Unnecessary grouping operator.");
		th.addError(30, 11, "Unnecessary grouping operator.");
		th.addError(31, 11, "Unnecessary grouping operator.");
		th.addError(32, 15, "Unnecessary grouping operator.");
		th.addError(33, 15, "Unnecessary grouping operator.");
		th.addError(34, 9, "Unnecessary grouping operator.");
		th.test(new String[] {
				"function* g() {",
				"  (yield);",
				"  (yield 0);",
				"  var x = (yield);",
				"  var y = (yield 0);",
				"  x = (yield);",
				"  x = (yield 0);",
				"  x += (yield);",
				"  x += (yield 0);",
				"  x -= (yield);",
				"  x -= (yield 0);",
				"  x *= (yield);",
				"  x *= (yield 0);",
				"  x /= (yield);",
				"  x /= (yield 0);",
				"  x %= (yield);",
				"  x %= (yield 0);",
				"  x <<= (yield 0);",
				"  x <<= (yield);",
				"  x >>= (yield);",
				"  x >>= (yield 0);",
				"  x >>>= (yield);",
				"  x >>>= (yield 0);",
				"  x &= (yield);",
				"  x &= (yield 0);",
				"  x ^= (yield);",
				"  x ^= (yield 0);",
				"  x |= (yield);",
				"  x |= (yield 0);",
				"  x = 0 ? (yield) : 0;",
				"  x = 0 ? (yield 0) : 0;",
				"  x = 0 ? 0 : (yield);",
				"  x = 0 ? 0 : (yield 0);",
				"  yield (yield);",
				"}"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 6));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsArrowFunctions() {
		String[] code = {
				"var a = () => ({});",
				"var b = (c) => {};",
				"var g = (() => 3)();",
				"var h = (() => ({}))();",
				"var i = (() => 3).length;",
				"var j = (() => ({})).length;",
				"var k = (() => 3)[prop];",
				"var l = (() => ({}))[prop];",
				"var m = (() => 3) || 3;",
				"var n = (() => ({})) || 3;",
				"var o = (() => {})();",
				"var p = (() => {})[prop];",
				"var q = (() => {}) || 3;",
				"(() => {})();",
				// Invalid forms:
				"var d = () => (e);",
				"var f = () => (3);",
				"var r = (() => 3);",
				"var s = (() => {});"
		};

		th.addError(15, 15, "Unnecessary grouping operator.");
		th.addError(16, 15, "Unnecessary grouping operator.");
		th.addError(17, 9, "Unnecessary grouping operator.");
		th.addError(18, 9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("esnext", true));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsExponentiation() {
		th.newTest();
		th.addError(1, 1, "Unnecessary grouping operator.");
		th.addError(2, 6, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(2) ** 2;",
				"2 ** (2);"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 7));

		th.newTest("UpdateExpression");
		th.addError(2, 1, "Unnecessary grouping operator.");
		th.addError(3, 1, "Unnecessary grouping operator.");
		th.addError(4, 1, "Unnecessary grouping operator.");
		th.addError(5, 1, "Unnecessary grouping operator.");
		th.addError(6, 6, "Unnecessary grouping operator.");
		th.addError(7, 6, "Unnecessary grouping operator.");
		th.addError(8, 6, "Unnecessary grouping operator.");
		th.addError(9, 6, "Unnecessary grouping operator.");
		th.test(new String[] {
				"var x;",
				"(++x) ** 2;",
				"(x++) ** 2;",
				"(--x) ** 2;",
				"(x--) ** 2;",
				"2 ** (++x);",
				"2 ** (x++);",
				"2 ** (--x);",
				"2 ** (x--);"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 7));

		th.newTest("UnaryExpression");
		th.addError(1, 16, "Variables should not be deleted.");
		th.addError(8, 10, "Variables should not be deleted.");
		th.test(new String[] {
				"delete (2 ** 3);",
				"void (2 ** 3);",
				"typeof (2 ** 3);",
				"+(2 ** 3);",
				"-(2 ** 3);",
				"~(2 ** 3);",
				"!(2 ** 3);",
				"(delete 2) ** 3;",
				"(void 2) ** 3;",
				"(typeof 2) ** 3;",
				"(+2) ** 3;",
				"(-2) ** 3;",
				"(~2) ** 3;",
				"(!2) ** 3;"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 7));

		th.newTest("MultiplicativeExpression");
		th.addError(2, 5, "Unnecessary grouping operator.");
		th.addError(4, 1, "Unnecessary grouping operator.");
		th.addError(6, 5, "Unnecessary grouping operator.");
		th.addError(8, 1, "Unnecessary grouping operator.");
		th.addError(10, 5, "Unnecessary grouping operator.");
		th.addError(12, 1, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(2 * 3) ** 4;",
				"2 * (3 ** 4);",
				"2 ** (3 * 4);",
				"(2 ** 3) * 4;",
				"(2 / 3) ** 4;",
				"2 / (3 ** 4);",
				"2 ** (3 / 4);",
				"(2 ** 3) / 4;",
				"(2 % 3) ** 4;",
				"2 % (3 ** 4);",
				"2 ** (3 % 4);",
				"(2 ** 3) % 4;"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 7));

		th.newTest("AdditiveExpression");
		th.addError(2, 5, "Unnecessary grouping operator.");
		th.addError(4, 1, "Unnecessary grouping operator.");
		th.addError(6, 5, "Unnecessary grouping operator.");
		th.addError(8, 1, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(2 + 3) ** 4;",
				"2 + (3 ** 4);",
				"2 ** (3 + 4);",
				"(2 ** 3) + 4;",
				"(2 - 3) ** 4;",
				"2 - (3 ** 4);",
				"2 ** (3 - 4);",
				"(2 ** 3) - 4;"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 7));

		th.newTest("Exponentiation");
		th.addError(2, 6, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(2 ** 3) ** 4;",
				"2 ** (3 ** 4);"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 7));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsAsyncFunction() {
		th.newTest("Async Function Expression");
		th.test(new String[] {
				"(async function() {})();",
				"(async function a() {})();"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 8));

		th.newTest("Async Generator Function Expression");
		th.test(new String[] {
				"(async function * () { yield; })();",
				"(async function * a() { yield; })();"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 9));

		th.newTest("Async Arrow Function");
		th.test(new String[] {
				"(async () => {})();",
				"(async x => x)();"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 8));

		th.newTest("async identifier");
		th.addError(1, 1, "Unnecessary grouping operator.");
		th.addError(2, 1, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(async());",
				"(async(x, y, z));"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 8));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsAwait() {
		th.newTest("MultiplicativeExpression");
		th.addError(2, 3, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(async function() {",
				"  (await 2) * 3;",
				"})();"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 8));

		th.newTest("ExponentiationExpression");
		th.addError(2, 3, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(async function() {",
				"  (await 2) ** 3;",
				"})();"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 8));

		th.newTest("CallExpression");
		th.test(new String[] {
				"(async function() {",
				"  (await 2)();",
				"})();"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 8));

		th.newTest("EqualityExpression");
		th.addError(2, 3, "Unnecessary grouping operator.");
		th.addError(3, 3, "Unnecessary grouping operator.");
		th.addError(4, 3, "Unnecessary grouping operator.");
		th.addError(5, 3, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(async function() {",
				"  (await 2) == 2;",
				"  (await 2) != 2;",
				"  (await 2) === 2;",
				"  (await 2) !== 2;",
				"})();"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 8));

		th.newTest("Expression");
		th.addError(2, 3, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(async function() {",
				"  (await 0), 0;",
				"})();"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 8));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsObjectLiterals() {
		String[] code = {
				"({}).method();",
				"if(true) {} ({}).method();",
				"g(); ({}).method();",

				// Invalid forms
				"var a = ({}).method();",
				"if (({}).method()) {}",
				"var b = { a: ({}).method() };",
				"for (({}); ;) {}",
				"for (; ;({})) {}"
		};

		th.newTest("grouping operator not required");
		th.addError(4, 9, "Unnecessary grouping operator.");
		th.addError(5, 5, "Unnecessary grouping operator.");
		th.addError(6, 14, "Unnecessary grouping operator.");
		th.addError(7, 6, "Unnecessary grouping operator.");
		th.addError(8, 9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsNewLine() {
		String[] code = {
				"function x() {",
				"  return f",
				"    ();",
				"}",
				"x({ f: null });"
		};

		th.test(code, new LinterOptions().set("singleGroups", true));
	};

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsLineNumber() {
		String[] code = {
				"var x = (",
				"  1",
				")",
				";"
		};

		th.addError(1, 9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsUnary() {
		String[] code = {
				"(-3).toString();",
				"(+3)[methodName]();",
				"(!3).toString();",
				"(~3).toString();",
				"(typeof x).toString();",
				"(new x).method();",

				// Unnecessary:
				"x = (-3) + 5;",
				"x = (+3) - 5;",
				"x = (!3) / 5;",
				"x = (~3) << 5;",
				"x = (typeof x) === 'undefined';",
				"x = (new x) + 4;"
		};

		th.addError(6, 6, "Missing '()' invoking a constructor.");
		th.addError(7, 5, "Unnecessary grouping operator.");
		th.addError(8, 5, "Unnecessary grouping operator.");
		th.addError(9, 5, "Unnecessary grouping operator.");
		th.addError(10, 5, "Unnecessary grouping operator.");
		th.addError(11, 5, "Unnecessary grouping operator.");
		th.addError(12, 5, "Unnecessary grouping operator.");
		th.addError(12, 10, "Missing '()' invoking a constructor.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsNumberLiterals() {
		String[] code = {
				"(3).toString();",
				"(3.1).toString();",
				"(.3).toString();",
				"(3.).toString();",
				"(1e3).toString();",
				"(1e-3).toString();",
				"(1.1e3).toString();",
				"(1.1e-3).toString();",
				"(3)[methodName]();",
				"var x = (3) + 3;",
				"('3').toString();"
		};

		th.addError(2, 1, "Unnecessary grouping operator.");
		th.addError(3, 1, "Unnecessary grouping operator.");
		th.addError(3, 4, "A leading decimal point can be confused with a dot: '.3'.");
		th.addError(4, 1, "Unnecessary grouping operator.");
		th.addError(4, 4, "A trailing decimal point can be confused with a dot: '3.'.");
		th.addError(5, 1, "Unnecessary grouping operator.");
		th.addError(6, 1, "Unnecessary grouping operator.");
		th.addError(7, 1, "Unnecessary grouping operator.");
		th.addError(8, 1, "Unnecessary grouping operator.");
		th.addError(9, 1, "Unnecessary grouping operator.");
		th.addError(10, 9, "Unnecessary grouping operator.");
		th.addError(11, 1, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsPostfix() {
		String[] code = {
				"var x;",
				"(x++).toString();",
				"(x--).toString();"
		};

		th.test(code, new LinterOptions().set("singleGroups", true));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsDestructuringAssign() {
		String[] code = {
				// statements
				"({ x } = { x : 1 });",
				"([ x ] = [ 1 ]);",
				// expressions
				"1, ({ x } = { x : 1 });",
				"1, ([ x ] = [ 1 ]);",
				"for (({ x } = { X: 1 }); ;) {}",
				"for (; ;({ x } = { X: 1 })) {}"
		};

		th.addError(2, 1, "Unnecessary grouping operator.");
		th.addError(3, 4, "Unnecessary grouping operator.");
		th.addError(4, 4, "Unnecessary grouping operator.");
		th.addError(5, 6, "Unnecessary grouping operator.");
		th.addError(6, 9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("esversion", 6).set("singleGroups", true).set("expr", true));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsNullishCoalescing() {
		th.addError(1, 1, "Unnecessary grouping operator.");
		th.addError(2, 6, "Unnecessary grouping operator.");
		th.test(new String[] {
				"(0) ?? 0;",
				"0 ?? (0);"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 11));

		th.newTest();
		th.test(new String[] {
				"0 ?? (0 || 0);",
				"(0 ?? 0) || 0;",
				"0 ?? (0 && 0);",
				"(0 ?? 0) && 0;"
		}, new LinterOptions().set("singleGroups", true).set("expr", true).set("esversion", 11));
	}

	@Test(groups = { "singleGroups" })
	public void testSingleGroupsOptionalChaining() {
		String[] code = {
				"new ({}?.constructor)();",
				"({}?.toString)``;",
				// Invalid forms:
				"([])?.x;",
				"([]?.x).x;",
				"([]?.x)?.x;"
		};

		th.addError(1, 21, "Bad constructor.");
		th.addError(2, 15, "Expected an assignment or function call and instead saw an expression.");
		th.addError(3, 1, "Unnecessary grouping operator.");
		th.addError(3, 7, "Expected an assignment or function call and instead saw an expression.");
		th.addError(4, 1, "Unnecessary grouping operator.");
		th.addError(4, 9, "Expected an assignment or function call and instead saw an expression.");
		th.addError(5, 1, "Unnecessary grouping operator.");
		th.addError(5, 10, "Expected an assignment or function call and instead saw an expression.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("esversion", 11));
	}

	@Test
	public void testElision() {
		String[] code = {
				"var a = [1,,2];",
				"var b = [1,,,,2];",
				"var c = [1,2,];",
				"var d = [,1,2];",
				"var e = [,,1,2];"
		};

		th.newTest("elision=false ES5");
		th.addError(1, 12, "Empty array elements require elision=true.");
		th.addError(2, 12, "Empty array elements require elision=true.");
		th.addError(4, 10, "Empty array elements require elision=true.");
		th.addError(5, 10, "Empty array elements require elision=true.");
		th.test(code, new LinterOptions().set("elision", false).set("es3", false));

		th.newTest("elision=false ES3");
		th.addError(1, 12, "Extra comma. (it breaks older versions of IE)");
		th.addError(2, 12, "Extra comma. (it breaks older versions of IE)");
		th.addError(2, 13, "Extra comma. (it breaks older versions of IE)");
		th.addError(2, 14, "Extra comma. (it breaks older versions of IE)");
		th.addError(3, 13, "Extra comma. (it breaks older versions of IE)");
		th.addError(4, 10, "Extra comma. (it breaks older versions of IE)");
		th.addError(5, 10, "Extra comma. (it breaks older versions of IE)");
		th.addError(5, 11, "Extra comma. (it breaks older versions of IE)");
		th.test(code, new LinterOptions().set("elision", false).set("es3", true));

		th.newTest("elision=true ES5");
		th.test(code, new LinterOptions().set("elision", true).set("es3", false));

		th.newTest("elision=true ES3");
		th.addError(3, 13, "Extra comma. (it breaks older versions of IE)");
		th.test(code, new LinterOptions().set("elision", true).set("es3", true));
	};

	@Test
	public void testBadInlineOptionValue() {
		String[] src = { "/* jshint bitwise:batcrazy */" };

		th.addError(1, 1, "Bad option value.");
		th.test(src);
	};

	@Test
	public void testFutureHostile() {
		String[] code = {
				"var JSON = {};",
				"var Map = function() {};",
				"var Promise = function() {};",
				"var Proxy = function() {};",
				"var Reflect = function() {};",
				"var Set = function() {};",
				"var Symbol = function() {};",
				"var WeakMap = function() {};",
				"var WeakSet = function() {};",
				"var ArrayBuffer = function() {};",
				"var DataView = function() {};",
				"var Int8Array = function() {};",
				"var Int16Array = function() {};",
				"var Int32Array = function() {};",
				"var Uint8Array = function() {};",
				"var Uint16Array = function() {};",
				"var Uint32Array = function() {};",
				"var Uint8ClampedArray = function() {};",
				"var Float32Array = function() {};",
				"var Float64Array = function() {};"
		};

		th.newTest("ES3 without option");
		th.addError(1, 5,
				"'JSON' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(2, 5,
				"'Map' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(3, 5,
				"'Promise' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(4, 5,
				"'Proxy' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(5, 5,
				"'Reflect' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(6, 5,
				"'Set' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(7, 5,
				"'Symbol' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(8, 5,
				"'WeakMap' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(9, 5,
				"'WeakSet' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(10, 5,
				"'ArrayBuffer' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(11, 5,
				"'DataView' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(12, 5,
				"'Int8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(13, 5,
				"'Int16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(14, 5,
				"'Int32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(15, 5,
				"'Uint8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(16, 5,
				"'Uint16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(17, 5,
				"'Uint32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(18, 5,
				"'Uint8ClampedArray' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(19, 5,
				"'Float32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(20, 5,
				"'Float64Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.test(code, new LinterOptions().set("es3", true).set("es5", false).set("futurehostile", false));

		th.newTest("ES3 with option");
		th.test(code, new LinterOptions().set("es3", true).set("es5", false));

		th.newTest("ES5 without option");
		th.addError(1, 5, "Redefinition of 'JSON'.");
		th.addError(2, 5,
				"'Map' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(3, 5,
				"'Promise' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(4, 5,
				"'Proxy' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(5, 5,
				"'Reflect' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(6, 5,
				"'Set' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(7, 5,
				"'Symbol' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(8, 5,
				"'WeakMap' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(9, 5,
				"'WeakSet' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(10, 5,
				"'ArrayBuffer' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(11, 5,
				"'DataView' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(12, 5,
				"'Int8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(13, 5,
				"'Int16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(14, 5,
				"'Int32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(15, 5,
				"'Uint8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(16, 5,
				"'Uint16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(17, 5,
				"'Uint32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(18, 5,
				"'Uint8ClampedArray' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(19, 5,
				"'Float32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.addError(20, 5,
				"'Float64Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.test(code, new LinterOptions().set("futurehostile", false));

		th.newTest("ES5 with option");
		th.addError(1, 5, "Redefinition of 'JSON'.");
		th.test(code, new LinterOptions());

		th.newTest("ES5 with opt-out");
		th.test(code, new LinterOptions().setPredefineds("-JSON"));

		th.newTest("ESNext without option");
		th.addError(1, 5, "Redefinition of 'JSON'.");
		th.addError(2, 5, "Redefinition of 'Map'.");
		th.addError(3, 5, "Redefinition of 'Promise'.");
		th.addError(4, 5, "Redefinition of 'Proxy'.");
		th.addError(5, 5, "Redefinition of 'Reflect'.");
		th.addError(6, 5, "Redefinition of 'Set'.");
		th.addError(7, 5, "Redefinition of 'Symbol'.");
		th.addError(8, 5, "Redefinition of 'WeakMap'.");
		th.addError(9, 5, "Redefinition of 'WeakSet'.");
		th.addError(10, 5, "Redefinition of 'ArrayBuffer'.");
		th.addError(11, 5, "Redefinition of 'DataView'.");
		th.addError(12, 5, "Redefinition of 'Int8Array'.");
		th.addError(13, 5, "Redefinition of 'Int16Array'.");
		th.addError(14, 5, "Redefinition of 'Int32Array'.");
		th.addError(15, 5, "Redefinition of 'Uint8Array'.");
		th.addError(16, 5, "Redefinition of 'Uint16Array'.");
		th.addError(17, 5, "Redefinition of 'Uint32Array'.");
		th.addError(18, 5, "Redefinition of 'Uint8ClampedArray'.");
		th.addError(19, 5, "Redefinition of 'Float32Array'.");
		th.addError(20, 5, "Redefinition of 'Float64Array'.");
		th.test(code, new LinterOptions().set("esnext", true).set("futurehostile", false));

		th.newTest("ESNext with option");
		th.addError(1, 5, "Redefinition of 'JSON'.");
		th.addError(2, 5, "Redefinition of 'Map'.");
		th.addError(3, 5, "Redefinition of 'Promise'.");
		th.addError(4, 5, "Redefinition of 'Proxy'.");
		th.addError(5, 5, "Redefinition of 'Reflect'.");
		th.addError(6, 5, "Redefinition of 'Set'.");
		th.addError(7, 5, "Redefinition of 'Symbol'.");
		th.addError(8, 5, "Redefinition of 'WeakMap'.");
		th.addError(9, 5, "Redefinition of 'WeakSet'.");
		th.addError(10, 5, "Redefinition of 'ArrayBuffer'.");
		th.addError(11, 5, "Redefinition of 'DataView'.");
		th.addError(12, 5, "Redefinition of 'Int8Array'.");
		th.addError(13, 5, "Redefinition of 'Int16Array'.");
		th.addError(14, 5, "Redefinition of 'Int32Array'.");
		th.addError(15, 5, "Redefinition of 'Uint8Array'.");
		th.addError(16, 5, "Redefinition of 'Uint16Array'.");
		th.addError(17, 5, "Redefinition of 'Uint32Array'.");
		th.addError(18, 5, "Redefinition of 'Uint8ClampedArray'.");
		th.addError(19, 5, "Redefinition of 'Float32Array'.");
		th.addError(20, 5, "Redefinition of 'Float64Array'.");
		th.test(code, new LinterOptions().set("esnext", true));

		th.newTest("ESNext with opt-out");
		th.test(code, new LinterOptions()
				.set("esnext", true)
				.set("futurehostile", false)
				.setPredefineds(
						"-JSON",
						"-Map",
						"-Promise",
						"-Proxy",
						"-Reflect",
						"-Set",
						"-Symbol",
						"-WeakMap",
						"-WeakSet",
						"-WeakSet",
						"-ArrayBuffer",
						"-DataView",
						"-Int8Array",
						"-Int16Array",
						"-Int32Array",
						"-Uint8Array",
						"-Uint16Array",
						"-Uint32Array",
						"-Uint8ClampedArray",
						"-Float32Array",
						"-Float64Array"));

		code = new String[] {
				"let JSON = {};",
				"let Map = function() {};",
				"let Promise = function() {};",
				"let Proxy = function() {};",
				"let Reflect = function() {};",
				"let Set = function() {};",
				"let Symbol = function() {};",
				"let WeakMap = function() {};",
				"let WeakSet = function() {};",
				"let ArrayBuffer = function() {};",
				"let DataView = function() {};",
				"let Int8Array = function() {};",
				"let Int16Array = function() {};",
				"let Int32Array = function() {};",
				"let Uint8Array = function() {};",
				"let Uint16Array = function() {};",
				"let Uint32Array = function() {};",
				"let Uint8ClampedArray = function() {};",
				"let Float32Array = function() {};",
				"let Float64Array = function() {};"
		};

		th.newTest("ESNext with option");
		th.addError(1, 5, "Redefinition of 'JSON'.");
		th.addError(2, 5, "Redefinition of 'Map'.");
		th.addError(3, 5, "Redefinition of 'Promise'.");
		th.addError(4, 5, "Redefinition of 'Proxy'.");
		th.addError(5, 5, "Redefinition of 'Reflect'.");
		th.addError(6, 5, "Redefinition of 'Set'.");
		th.addError(7, 5, "Redefinition of 'Symbol'.");
		th.addError(8, 5, "Redefinition of 'WeakMap'.");
		th.addError(9, 5, "Redefinition of 'WeakSet'.");
		th.addError(10, 5, "Redefinition of 'ArrayBuffer'.");
		th.addError(11, 5, "Redefinition of 'DataView'.");
		th.addError(12, 5, "Redefinition of 'Int8Array'.");
		th.addError(13, 5, "Redefinition of 'Int16Array'.");
		th.addError(14, 5, "Redefinition of 'Int32Array'.");
		th.addError(15, 5, "Redefinition of 'Uint8Array'.");
		th.addError(16, 5, "Redefinition of 'Uint16Array'.");
		th.addError(17, 5, "Redefinition of 'Uint32Array'.");
		th.addError(18, 5, "Redefinition of 'Uint8ClampedArray'.");
		th.addError(19, 5, "Redefinition of 'Float32Array'.");
		th.addError(20, 5, "Redefinition of 'Float64Array'.");
		th.test(code, new LinterOptions().set("esnext", true));

		th.newTest("ESNext with opt-out");
		th.test(code, new LinterOptions()
				.set("esnext", true)
				.set("futurehostile", false)
				.setPredefineds(
						"-JSON",
						"-Map",
						"-Promise",
						"-Proxy",
						"-Reflect",
						"-Set",
						"-Symbol",
						"-WeakMap",
						"-WeakSet",
						"-ArrayBuffer",
						"-DataView",
						"-Int8Array",
						"-Int16Array",
						"-Int32Array",
						"-Uint8Array",
						"-Uint16Array",
						"-Uint32Array",
						"-Uint8ClampedArray",
						"-Float32Array",
						"-Float64Array"));

		code = new String[] {
				"const JSON = {};",
				"const Map = function() {};",
				"const Promise = function() {};",
				"const Proxy = function() {};",
				"const Reflect = function() {};",
				"const Set = function() {};",
				"const Symbol = function() {};",
				"const WeakMap = function() {};",
				"const WeakSet = function() {};",
				"const ArrayBuffer = function() {};",
				"const DataView = function() {};",
				"const Int8Array = function() {};",
				"const Int16Array = function() {};",
				"const Int32Array = function() {};",
				"const Uint8Array = function() {};",
				"const Uint16Array = function() {};",
				"const Uint32Array = function() {};",
				"const Uint8ClampedArray = function() {};",
				"const Float32Array = function() {};",
				"const Float64Array = function() {};"
		};

		th.newTest("ESNext with option");
		th.addError(1, 7, "Redefinition of 'JSON'.");
		th.addError(2, 7, "Redefinition of 'Map'.");
		th.addError(3, 7, "Redefinition of 'Promise'.");
		th.addError(4, 7, "Redefinition of 'Proxy'.");
		th.addError(5, 7, "Redefinition of 'Reflect'.");
		th.addError(6, 7, "Redefinition of 'Set'.");
		th.addError(7, 7, "Redefinition of 'Symbol'.");
		th.addError(8, 7, "Redefinition of 'WeakMap'.");
		th.addError(9, 7, "Redefinition of 'WeakSet'.");
		th.addError(10, 7, "Redefinition of 'ArrayBuffer'.");
		th.addError(11, 7, "Redefinition of 'DataView'.");
		th.addError(12, 7, "Redefinition of 'Int8Array'.");
		th.addError(13, 7, "Redefinition of 'Int16Array'.");
		th.addError(14, 7, "Redefinition of 'Int32Array'.");
		th.addError(15, 7, "Redefinition of 'Uint8Array'.");
		th.addError(16, 7, "Redefinition of 'Uint16Array'.");
		th.addError(17, 7, "Redefinition of 'Uint32Array'.");
		th.addError(18, 7, "Redefinition of 'Uint8ClampedArray'.");
		th.addError(19, 7, "Redefinition of 'Float32Array'.");
		th.addError(20, 7, "Redefinition of 'Float64Array'.");
		th.test(code, new LinterOptions().set("esnext", true));

		th.newTest("ESNext with opt-out");
		th.test(code, new LinterOptions()
				.set("esnext", true)
				.set("futurehostile", false)
				.setPredefineds(
						"-JSON",
						"-Map",
						"-Promise",
						"-Proxy",
						"-Reflect",
						"-Set",
						"-Symbol",
						"-WeakMap",
						"-WeakSet",
						"-ArrayBuffer",
						"-DataView",
						"-Int8Array",
						"-Int16Array",
						"-Int32Array",
						"-Uint8Array",
						"-Uint16Array",
						"-Uint32Array",
						"-Uint8ClampedArray",
						"-Float32Array",
						"-Float64Array"));
	};

	@Test
	public void testVarstmt() {
		String[] code = {
				"var x;",
				"var y = 5;",
				"var fn = function() {",
				"  var x;",
				"  var y = 5;",
				"};",
				"for (var a in x);"
		};

		th.addError(1, 1, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(2, 1, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(3, 1, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(4, 3, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(5, 3, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(7, 6, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.test(code, new LinterOptions().set("varstmt", true));
	}

	@Test(groups = { "module" })
	public void testModuleBehavior() {
		String[] code = {
				"var package = 3;",
				"function f() { return this; }"
		};

		th.test(code, new LinterOptions());

		th.newTest();
		th.addError(0, 0, "The 'module' option is only available when linting ECMAScript 6 code.");
		th.addError(1, 5, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(2, 23,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code, new LinterOptions().set("module", true));

		th.newTest();
		th.addError(1, 5, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(2, 23,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code, new LinterOptions().set("module", true).set("esnext", true));

		code = new String[] {
				"/* jshint module: true */",
				"var package = 3;",
				"function f() { return this; }"
		};

		th.newTest();
		th.addError(1, 1, "The 'module' option is only available when linting ECMAScript 6 code.");
		th.addError(2, 5, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(3, 23,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code);

		code[0] = "/* jshint module: true, esnext: true */";

		th.newTest();
		th.addError(2, 5, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(3, 23,
				"If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code);
	}

	@Test(groups = { "module" })
	public void testModuleDeclarationRestrictions() {
		th.addError(2, 3, "The 'module' option cannot be set after any executable code.");
		th.test(new String[] {
				"(function() {",
				"  /* jshint module: true */",
				"})();"
		}, new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(2, 1, "The 'module' option cannot be set after any executable code.");
		th.test(new String[] {
				"void 0;",
				"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));

		th.newTest();
		th.addError(3, 1, "The 'module' option cannot be set after any executable code.");
		th.test(new String[] {
				"void 0;",
				"// hide",
				"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));

		th.newTest("First line (following statement)");
		th.addError(1, 20, "The 'module' option cannot be set after any executable code.");
		th.test(new String[] {
				"(function() {})(); /* jshint module: true */"
		}, new LinterOptions().set("esnext", true));

		th.newTest("First line (within statement)");
		th.addError(1, 15, "The 'module' option cannot be set after any executable code.");
		th.test(new String[] {
				"(function() { /* jshint module: true */",
				"})();"
		}, new LinterOptions().set("esnext", true));

		th.newTest("First line (before statement)");
		th.test(new String[] {
				"/* jshint module: true */ (function() {",
				"})();"
		}, new LinterOptions().set("esnext", true));

		th.newTest("First line (within expression)");
		th.addError(1, 10, "The 'module' option cannot be set after any executable code.");
		th.test("Math.abs(/*jshint module: true */4);", new LinterOptions().set("esnext", true));

		th.newTest("Following single-line comment");
		th.test(new String[] {
				"// License boilerplate",
				"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));

		th.newTest("Following multi-line comment");
		th.test(new String[] {
				"/**",
				" * License boilerplate",
				" */",
				"  /* jshint module: true */"
		}, new LinterOptions().set("esnext", true));

		th.newTest("Following shebang");
		th.test(new String[] {
				"#!/usr/bin/env node",
				"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));

		th.newTest("Not re-applied with every directive (gh-2560)");
		th.test(new String[] {
				"/* jshint module:true */",
				"function bar() {",
				"  /* jshint validthis:true */",
				"}"
		}, new LinterOptions().set("esnext", true));
	}

	@Test(groups = { "module" })
	public void testModuleNewcap() {
		String[] code = {
				"var ctor = function() {};",
				"var Ctor = function() {};",
				"var c1 = new ctor();",
				"var c2 = Ctor();"
		};

		th.newTest("The `newcap` option is not automatically enabled for module code.");
		th.test(code, new LinterOptions().set("esversion", 6).set("module", true));
	}

	@Test(groups = { "module" })
	public void testModuleAwait() {
		String[] allPositions = {
				"var await;",
				"function await() {}",
				"await: while (false) {}",
				"void { await: null };",
				"void {}.await;"
		};

		th.test(allPositions, new LinterOptions().set("esversion", 3));
		th.test(allPositions);
		th.test(allPositions, new LinterOptions().set("esversion", 6));

		th.addError(1, 5, "Expected an identifier and instead saw 'await' (a reserved word).");
		th.test("var await;", new LinterOptions().set("esversion", 6).set("module", true));

		th.newTest();
		th.addError(1, 10, "Expected an identifier and instead saw 'await' (a reserved word).");
		th.test("function await() {}", new LinterOptions().set("esversion", 6).set("module", true));

		th.newTest();
		th.addError(1, 1, "Expected an assignment or function call and instead saw an expression.");
		th.addError(1, 1, "Expected an identifier and instead saw 'await' (a reserved word).");
		th.addError(1, 6, "Missing semicolon.");
		th.addError(1, 1, "Unrecoverable syntax error. (100% scanned).");
		th.test("await: while (false) {}", new LinterOptions().set("esversion", 6).set("module", true));

		th.newTest();
		th.test(new String[] {
				"void { await: null };",
				"void {}.await;"
		}, new LinterOptions().set("esversion", 6).set("module", true));
	}

	@Test
	public void testEsversion() {
		String[] code = {
				"// jshint esversion: 3",
				"// jshint esversion: 4",
				"// jshint esversion: 5",
				"// jshint esversion: 6",
				"// jshint esversion: 2015",
				"// jshint esversion: 7",
				"// jshint esversion: 2016",
				"// jshint esversion: 8",
				"// jshint esversion: 2017",
				"// jshint esversion: 9",
				"// jshint esversion: 2018",
				"// jshint esversion: 10",
				"// jshint esversion: 2019",
				"// jshint esversion: 11",
				"// jshint esversion: 2020",
				"// jshint esversion: 12",
				"// jshint esversion: 2021"
		};

		th.newTest("Value");
		th.addError(2, 1, "Bad option value.");
		th.addError(16, 1, "Bad option value.");
		th.addError(17, 1, "Bad option value.");
		th.test(code);

		String[] es5code = {
				"var a = {",
				"  get b() {}",
				"};"
		};

		th.newTest("ES5 syntax as ES3");
		th.addError(2, 7, "get/set are ES5 features.");
		th.test(es5code, new LinterOptions().set("esversion", 3));

		th.newTest("ES5 syntax as ES5");
		th.test(es5code); // esversion: 5 (default)

		th.newTest("ES5 syntax as ES6");
		th.test(es5code, new LinterOptions().set("esversion", 6));

		String[] es6code = {
				"var a = {",
				"  ['b']: 1",
				"};",
				"var b = () => {};"
		};

		th.newTest("ES6 syntax as ES3");
		th.addError(2, 3, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 10, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.test(es6code, new LinterOptions().set("esversion", 3));

		th.newTest("ES6 syntax as ES5");
		th.addError(2, 3, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, 10, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.test(es6code); // esversion: 5 (default)

		th.newTest("ES6 syntax as ES6");
		th.test(es6code, new LinterOptions().set("esversion", 6));

		th.newTest("ES6 syntax as ES6 (via option value `2015`)");
		th.test(es5code, new LinterOptions().set("esversion", 2015)); // JSHINT_BUG: is it correct that es5code passed
																		// here?

		th.newTest("ES6 syntax as ES7");
		th.test(es6code, new LinterOptions().set("esversion", 7));

		th.newTest("ES6 syntax as ES8");
		th.test(es6code, new LinterOptions().set("esversion", 8));

		// Array comprehensions aren't defined in ECMAScript 6,
		// but they can be enabled using the `esnext` option
		String[] arrayComprehension = {
				"var a = [ 1, 2, 3 ];",
				"var b = [ for (i of a) i ];"
		};

		th.newTest("array comprehensions - esversion: 6");
		th.addError(2, 9, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(arrayComprehension, new LinterOptions().set("esversion", 6));

		th.newTest("array comprehensions - esversion: 7");
		th.addError(2, 9, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(arrayComprehension, new LinterOptions().set("esversion", 7));

		th.newTest("array comprehensions - esversion: 8");
		th.addError(2, 9, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(arrayComprehension, new LinterOptions().set("esversion", 8));

		th.newTest("array comprehensions - esnext: true");
		th.test(arrayComprehension, new LinterOptions().set("esnext", true));

		// JSHINT_TODO: Remove in JSHint 3
		th.newTest("incompatibility with `es3`");
		th.addError(0, 0, "Incompatible values for the 'esversion' and 'es3' linting options. (0% scanned).");
		th.test(es6code, new LinterOptions().set("esversion", 6).set("es3", true));

		// JSHINT_TODO: Remove in JSHint 3
		th.newTest("incompatibility with `es5`");
		th.addError(0, 0, "Incompatible values for the 'esversion' and 'es5' linting options. (0% scanned).");
		th.test(es6code, new LinterOptions().set("esversion", 6).set("es5", true));

		// JSHINT_TODO: Remove in JSHint 3
		th.newTest("incompatibility with `esnext`");
		th.addError(0, 0, "Incompatible values for the 'esversion' and 'esnext' linting options. (0% scanned).");
		th.test(es6code, new LinterOptions().set("esversion", 3).set("esnext", true));

		th.newTest("imcompatible option specified in-line");
		th.addError(2, 1, "Incompatible values for the 'esversion' and 'es3' linting options. (66% scanned).");
		th.test(new String[] { "", "// jshint esversion: 3", "" }, new LinterOptions().set("es3", true));
		th.test(new String[] { "", "// jshint es3: true", "" }, new LinterOptions().set("esversion", 3));

		th.newTest("compatible option specified in-line");
		th.addError(3, 1, "'class' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(new String[] { "", "// jshint esversion: 3", "class A {}" }, new LinterOptions().set("esversion", 3));
		th.test(new String[] { "", "// jshint esversion: 3", "class A {}" }, new LinterOptions().set("esversion", 6));

		th.newTest("compatible option specified in-line");
		th.test(new String[] { "", "// jshint esversion: 6", "class A {}" }, new LinterOptions().set("esversion", 6));

		String[] code2 = ArrayUtils.addAll(new String[] { // JSHINT_TODO: Remove in JSHint 3
				"/* jshint esversion: 3, esnext: true */"
		}, es6code);

		th.newTest("incompatible options specified in-line"); // JSHINT_TODO: Remove in JSHint 3
		th.addError(1, 1, "Incompatible values for the 'esversion' and 'esnext' linting options. (20% scanned).");
		th.test(code2);

		String[] code3 = {
				"var someCode;",
				"// jshint esversion: 3"
		};

		th.newTest("cannot be set after any executable code");
		th.addError(2, 1, "The 'esversion' option cannot be set after any executable code.");
		th.test(code3);

		String[] code4 = {
				"#!/usr/bin/env node",
				"/**",
				" * License",
				" */",
				"// jshint esversion: 3",
				"// jshint esversion: 6"
		};

		th.newTest("can follow shebang or comments");
		th.test(code4);

		String[] code5 = {
				"// jshint moz: true",
				"// jshint esversion: 3",
				"var x = {",
				"  get a() {}",
				"};",
				"// jshint moz: true",
				"var x = {",
				"  get a() {}",
				"};"
		};

		th.newTest("correctly swap between moz and esversion");
		th.addError(4, 7, "get/set are ES5 features.");
		th.test(code5);
	}

	// Option `trailingcomma` requires a comma after each element in an array or
	// object literal.
	@Test
	public void testTrailingcomma() {
		String[] code = {
				"var a = [];",
				"var b = [1];",
				"var c = [1,];",
				"var d = [1,2];",
				"var e = [1,2,];",
				"var f = {};",
				"var g = {a: 1};",
				"var h = {a: 1,};",
				"var i = {a: 1, b: 2};",
				"var j = {a: 1, b: 2,};"
		};

		th.newTest("trailingcomma=true ES6");
		th.addError(2, 11, "Missing comma.");
		th.addError(4, 13, "Missing comma.");
		th.addError(7, 14, "Missing comma.");
		th.addError(9, 20, "Missing comma.");
		th.test(code, new LinterOptions().set("trailingcomma", true).set("esversion", 6));

		th.newTest("trailingcomma=true ES5");
		th.addError(2, 11, "Missing comma.");
		th.addError(4, 13, "Missing comma.");
		th.addError(7, 14, "Missing comma.");
		th.addError(9, 20, "Missing comma.");
		th.test(code, new LinterOptions().set("trailingcomma", true));

		th.newTest("trailingcomma=true ES3");
		th.addError(3, 11, "Extra comma. (it breaks older versions of IE)");
		th.addError(5, 13, "Extra comma. (it breaks older versions of IE)");
		th.addError(8, 14, "Extra comma. (it breaks older versions of IE)");
		th.addError(10, 20, "Extra comma. (it breaks older versions of IE)");
		th.test(code, new LinterOptions().set("trailingcomma", true).set("es3", true));
	}

	@Test
	public void testUnstable() {
		th.newTest("Accepts programmatic configuration.");
		th.test("", new LinterOptions().setUnstables());

		th.newTest("Accepts empty in-line directive (single-line comment).");
		th.test("// jshint.unstable");

		th.newTest("Rejects empty in-line directive (multi-line comment).");
		th.addError(1, 1, "Bad unstable option: ''.");
		th.test("/* jshint.unstable */");

		th.newTest("Rejects non-existent names specified via programmatic configuration.");
		th.addError(0, 0, "Bad unstable option: 'nonExistentOptionName'.");
		th.test("", new LinterOptions().setUnstables("nonExistentOptionName"));

		th.newTest("Rejects non-existent names specified via in-line directive (single-line comment).");
		th.addError(1, 1, "Bad unstable option: 'nonExistentOptionName'.");
		th.test("// jshint.unstable nonExistentOptionName: true");

		th.newTest("Rejects non-existent names specified via in-line directive (multi-line comment).");
		th.addError(1, 1, "Bad unstable option: 'nonExistentOptionName'.");
		th.test("/* jshint.unstable nonExistentOptionName: true */");

		th.newTest("Rejects stable names specified via programmatic configuration.");
		th.addError(0, 0, "Bad unstable option: 'undef'.");
		th.test("", new LinterOptions().setUnstables("undef"));

		th.newTest("Rejects stable names specified via in-line directive (single-line comment).");
		th.addError(1, 1, "Bad unstable option: 'undef'.");
		th.test("// jshint.unstable undef: true");

		th.newTest("Rejects stable names specified via in-line directive (multi-line comment).");
		th.addError(1, 1, "Bad unstable option: 'undef'.");
		th.test("/* jshint.unstable undef: true */");
	}

	@Test
	public void testLeanswitch() {
		String[] code = {
				"switch (0) {",
				"  case 0:",
				"  default:",
				"    break;",
				"}"
		};
		th.newTest("empty case clause followed by default");
		th.test(code);
		th.newTest("empty case clause followed by default");
		th.addError(2, 9, "Superfluous 'case' clause.");
		th.test(code, new LinterOptions().set("leanswitch", true));

		code = new String[] {
				"switch (0) {",
				"  case 0:",
				"  case 1:",
				"    break;",
				"}"
		};
		th.newTest("empty case clause followed by case");
		th.test(code);
		th.newTest("empty case clause followed by case");
		th.test(code, new LinterOptions().set("leanswitch", true));

		code = new String[] {
				"switch (0) {",
				"  default:",
				"  case 0:",
				"    break;",
				"}"
		};
		th.newTest("empty default clause followed by case");
		th.test(code);
		th.newTest("empty default clause followed by case");
		th.addError(3, 3, "Superfluous 'case' clause.");
		th.test(code, new LinterOptions().set("leanswitch", true));

		code = new String[] {
				"switch (0) {",
				"  case 0:",
				"    void 0;",
				"  default:",
				"    break;",
				"}"
		};
		th.newTest("non-empty case clause followed by default");
		th.addError(3, 11, "Expected a 'break' statement before 'default'.");
		th.test(code);
		th.newTest("non-empty case clause followed by default");
		th.addError(3, 11, "Expected a 'break' statement before 'default'.");
		th.test(code, new LinterOptions().set("leanswitch", true));

		code = new String[] {
				"switch (0) {",
				"  case 0:",
				"    void 0;",
				"  case 1:",
				"    break;",
				"}"
		};
		th.newTest("non-empty case clause followed by case");
		th.addError(3, 11, "Expected a 'break' statement before 'case'.");
		th.test(code);
		th.newTest("non-empty case clause followed by case");
		th.addError(3, 11, "Expected a 'break' statement before 'case'.");
		th.test(code, new LinterOptions().set("leanswitch", true));

		code = new String[] {
				"switch (0) {",
				"  default:",
				"    void 0;",
				"  case 0:",
				"    break;",
				"}"
		};
		th.newTest("non-empty default clause followed by case");
		th.addError(3, 11, "Expected a 'break' statement before 'case'.");
		th.test(code);
		th.newTest("non-empty default clause followed by case");
		th.addError(3, 11, "Expected a 'break' statement before 'case'.");
		th.test(code, new LinterOptions().set("leanswitch", true));
	}

	@Test
	public void testNoreturnawait() {
		String[] code = {
				"void function() {",
				"  return await;",
				"};",
				"void function() {",
				"  return await(null);",
				"};",
				"void async function() {",
				"  return null;",
				"};",
				"void async function() {",
				"  return 'await';",
				"};",
				"void async function() {",
				"  try {",
				"    return await null;",
				"  } catch (err) {}",
				"};",
				"void async function() {",
				"  try {",
				"    void async function() {",
				"      return await null;",
				"    };",
				"  } catch (err) {}",
				"};",
				"void async function() {",
				"  return await null;",
				"};"
		};

		th.newTest("function expression (disabled)");
		th.test(code, new LinterOptions().set("esversion", 8));

		th.newTest("function expression (enabled)");
		th.addError(21, 14, "Unnecessary `await` expression.");
		th.addError(26, 10, "Unnecessary `await` expression.");
		th.test(code, new LinterOptions().set("esversion", 8).set("noreturnawait", true));

		code = new String[] {
				"void (() => await);",
				"void (() => await(null));",
				"void (async () => null);",
				"void (async () => 'await');",
				"void (async () => await null);",
				"void (async () => { await null; });"
		};

		th.newTest("arrow function (disabled)");
		th.test(code, new LinterOptions().set("esversion", 8));

		th.newTest("arrow function (enabled)");
		th.addError(5, 19, "Unnecessary `await` expression.");
		th.test(code, new LinterOptions().set("esversion", 8).set("noreturnawait", true));
	}

	@Test
	public void testRegexpu() {
		th.newTest("restricted outside of ES6 - via API");
		th.addError(0, 0, "The 'regexpu' option is only available when linting ECMAScript 6 code.");
		th.test("void 0;", new LinterOptions().set("regexpu", true));

		th.newTest("restricted outside of ES6 - via directive");
		th.addError(1, 1, "The 'regexpu' option is only available when linting ECMAScript 6 code.");
		th.test(new String[] {
				"// jshint regexpu: true",
				"void 0;"
		});

		th.newTest("missing");
		th.addError(1, 6, "Regular expressions should include the 'u' flag.");
		th.addError(2, 6, "Regular expressions should include the 'u' flag.");
		th.addError(3, 6, "Regular expressions should include the 'u' flag.");
		th.test(new String[] {
				"void /./;",
				"void /./g;",
				"void /./giym;"
		}, new LinterOptions().set("regexpu", true).set("esversion", 6));

		th.newTest("in use");
		th.test(new String[] {
				"void /./u;",
				"void /./ugiym;",
				"void /./guiym;",
				"void /./giuym;",
				"void /./giyum;",
				"void /./giymu;"
		}, new LinterOptions().set("esversion", 6));

		th.newTest("missing - option set when parsing precedes option enablement");
		th.addError(3, 8, "Regular expressions should include the 'u' flag.");
		th.test(new String[] {
				"(function() {",
				"  // jshint regexpu: true",
				"  void /./;",
				"}());"
		}, new LinterOptions().set("esversion", 6));
	}
}