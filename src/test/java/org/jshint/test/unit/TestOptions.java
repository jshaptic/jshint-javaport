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
import org.jshint.utils.JSHintUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for all non-environmental options. Non-environmental options are
 * options that change how JSHint behaves instead of just pre-defining a set
 * of global variables.
 */
public class TestOptions extends Assert
{
	private TestHelper th = new TestHelper();
	
	@BeforeClass
	private void setupBeforeClass()
	{
		JSHintUtils.reset();
	}
	
	@BeforeMethod
	private void setupBeforeMethod()
	{
		th.reset();
	}
	
	/**
	 * Option `shadow` allows you to re-define variables later in code.
	 *
	 * E.g.:
	 *   var a = 1;
	 *   if (cond == true)
	 *     var a = 2; // Variable a has been already defined on line 1.
	 *
	 * More often than not it is a typo, but sometimes people use it.
	 */
	@Test
	public void testShadow()
	{
		String src = th.readFile("src/test/resources/fixtures/redef.js");
		
		// Do not tolerate variable shadowing by default
		th.addError(5, "'a' is already defined.");
	    th.addError(10, "'foo' is already defined.");
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src, new LinterOptions().set("es3", true).set("shadow", false));
		th.test(src, new LinterOptions().set("es3", true).set("shadow", "inner"));
	    
		// Allow variable shadowing when shadow is true
	    th.reset();
	    th.test(src, new LinterOptions().set("es3", true).set("shadow", true));
	}
	
	/**
	 * Option `shadow:outer` allows you to re-define variables later in inner scopes.
	 *
	 *  E.g.:
	 *    var a = 1;
	 *    function foo() {
	 *        var a = 2;
	 *    }
	 */
	@Test
	public void testShadowouter()
	{
		String src = th.readFile("src/test/resources/fixtures/scope-redef.js");
		
		// Do not tolarate inner scope variable shadowing by default
		th.addError(5, "'a' is already defined in outer scope.");
	    th.addError(12, "'b' is already defined in outer scope.");
	    th.addError(20, "'bar' is already defined in outer scope.");
	    th.addError(26, "'foo' is already defined.");
		th.test(src, new LinterOptions().set("es3", true).set("shadow", "outer"));
	}
	
	@Test
	public void testShadowInline()
	{
		String src = th.readFile("src/test/resources/fixtures/shadow-inline.js");
		
		th.addError(6, "'a' is already defined in outer scope.");
	    th.addError(7, "'a' is already defined.");
	    th.addError(7, "'a' is already defined in outer scope.");
	    th.addError(17, "'a' is already defined.");
	    th.addError(27, "'a' is already defined.");
	    th.addError(42, "Bad option value.");
	    th.addError(47, "'a' is already defined.");
		th.test(src);
	}
	
	@Test
	public void testShadowEs6()
	{
		String src = th.readFile("src/test/resources/fixtures/redef-es6.js");
		
		String[][] commonErrors = {
			{"2", "'ga' has already been declared."},
		    {"5", "'gb' has already been declared."},
		    {"14", "'gd' has already been declared."},
		    {"24", "'gf' has already been declared."},
		    {"110", "'gx' has already been declared."},
		    {"113", "'gy' has already been declared."},
		    {"116", "'gz' has already been declared."},
		    {"119", "'gza' has already been declared."},
		    {"122", "'gzb' has already been declared."},
		    {"132", "'gzd' has already been declared."},
		    {"147", "'gzf' has already been declared."},
		    {"156", "'a' has already been declared."},
		    {"159", "'b' has already been declared."},
		    {"168", "'d' has already been declared."},
		    {"178", "'f' has already been declared."},
		    {"264", "'x' has already been declared."},
		    {"267", "'y' has already been declared."},
		    {"270", "'z' has already been declared."},
		    {"273", "'za' has already been declared."},
		    {"276", "'zb' has already been declared."},
		    {"286", "'zd' has already been declared."},
		    {"301", "'zf' has already been declared."},
		    {"317", "'zi' has already been declared."},
		    {"344", "'zzi' has already been declared."},
		    {"345", "'zzj' has already been declared."},
		    {"349", "'zzl' has already been declared."},
		    {"349", "'zzl' was used before it was declared, which is illegal for 'const' variables."},
		    {"350", "'zzm' has already been declared."},
		    {"350", "'zzm' was used before it was declared, which is illegal for 'let' variables."},
		    {"364", "'zj' has already been declared."}
		};
		
		String[][] innerErrors = {
			{"343", "'zzh' is already defined."},
		    {"348", "'zzk' is already defined."}
		};
		
		String[][] outerErrors = {
			/* block scope variables shadowing out of scope */
			{"9", "'gc' is already defined."},
			{"19", "'ge' is already defined."},
			{"28", "'gg' is already defined in outer scope."},
			{"32", "'gh' is already defined in outer scope."},
			{"36", "'gi' is already defined in outer scope."},
			{"40", "'gj' is already defined."},
			{"44", "'gk' is already defined."},
			{"48", "'gl' is already defined."},
			{"53", "'gm' is already defined."},
			{"59", "'gn' is already defined."},
			{"65", "'go' is already defined."},
			{"71", "'gp' is already defined."},
			{"76", "'gq' is already defined."},
			{"81", "'gr' is already defined."},
			{"86", "'gs' is already defined."},
			{"163", "'c' is already defined."},
			{"173", "'e' is already defined."},
			{"182", "'g' is already defined in outer scope."},
			{"186", "'h' is already defined in outer scope."},
			{"190", "'i' is already defined in outer scope."},
			{"194", "'j' is already defined."},
			{"198", "'k' is already defined."},
			{"202", "'l' is already defined."},
			{"207", "'m' is already defined."},
			{"213", "'n' is already defined."},
			{"219", "'o' is already defined."},
			{"225", "'p' is already defined."},
			{"230", "'q' is already defined."},
			{"235", "'r' is already defined."},
			{"240", "'s' is already defined."},
			/* variables shadowing outside of function scope */
			{"91", "'gt' is already defined in outer scope."},
			{"96", "'gu' is already defined in outer scope."},
			{"101", "'gv' is already defined in outer scope."},
			{"106", "'gw' is already defined in outer scope."},
			{"245", "'t' is already defined in outer scope."},
			{"250", "'u' is already defined in outer scope."},
			{"255", "'v' is already defined in outer scope."},
			{"260", "'w' is already defined in outer scope."},
			/* variables shadowing outside multiple function scopes */
			{"332", "'zza' is already defined in outer scope."},
			{"333", "'zzb' is already defined in outer scope."},
			{"334", "'zzc' is already defined in outer scope."},
			{"335", "'zzd' is already defined in outer scope."},
			{"336", "'zze' is already defined in outer scope."},
			{"337", "'zzf' is already defined in outer scope."},
			{"358", "'zzn' is already defined in outer scope."}
		};
		
		for (String[] error : commonErrors)
		{
			th.addError(Integer.parseInt(error[0]), error[1]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("shadow", true));
		
		for (String[] error : innerErrors)
		{
			th.addError(Integer.parseInt(error[0]), error[1]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("shadow", "inner").set("maxerr", 100));
		
		for (String[] error : outerErrors)
		{
			th.addError(Integer.parseInt(error[0]), error[1]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("shadow", "outer").set("maxerr", 100));
	}
	
	/**
	 * Option `latedef` allows you to prohibit the use of variable before their
	 * definitions.
	 *
	 * E.g.:
	 *   fn(); // fn will be defined later in code
	 *   function fn() {};
	 *
	 * Since JavaScript has function-scope only, you can define variables and
	 * functions wherever you want. But if you want to be more strict, use
	 * this option.
	 */
	@Test
	public void testLatedef()
	{
		String src = th.readFile("src/test/resources/fixtures/latedef.js");
		String src1 = th.readFile("src/test/resources/fixtures/redef.js");
		String esnextSrc = th.readFile("src/test/resources/fixtures/latedef-esnext.js");
		
		// By default, tolerate the use of variable before its definition
		th.test(src, new LinterOptions().set("es3", true).set("funcscope", true));
		
		th.addError(10, "'i' was used before it was declared, which is illegal for 'let' variables.");
		th.test(esnextSrc, new LinterOptions().set("esnext", true));
		
		// However, JSHint must complain if variable is actually missing
		th.reset();
		th.addError(1, "'fn' is not defined.");
		th.test("fn();", new LinterOptions().set("es3", true).set("undef", true));
		
		// And it also must complain about the redefinition (see option `shadow`)
		th.reset();
		th.addError(5, "'a' is already defined.");
	    th.addError(10, "'foo' is already defined.");
		th.test(src1, new LinterOptions().set("es3", true));
		
		// When latedef is true, JSHint must not tolerate the use before definition
		th.reset();
	    th.addError(10, "'vr' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", "nofunc"));
		
		th.reset();
		th.test(new String[] {
			"if(true) { var a; }",
		    "if (a) { a(); }",
		    "var a;"
		}, new LinterOptions().set("es3", true).set("latedef", "nofunc"));
	    
		// When latedef_func is true, JSHint must not tolerate the use before definition for functions
	    th.reset();
	    th.addError(2, "'fn' was used before it was defined.");
	    th.addError(6, "'fn1' was used before it was defined.");
	    th.addError(10, "'vr' was used before it was defined.");
	    th.addError(18, "'bar' was used before it was defined.");
	    th.addError(18, "Inner functions should be listed at the top of the outer function.");
	    th.test(src, new LinterOptions().set("es3", true).set("latedef", true));
	    
	    th.reset();
	    th.addError(4, "'c' was used before it was defined.");
	    th.addError(6, "'e' was used before it was defined.");
	    th.addError(8, "'h' was used before it was defined.");
	    th.addError(10, "'i' was used before it was declared, which is illegal for 'let' variables.");
	    th.addError(15, "'ai' was used before it was defined.");
	    th.addError(20, "'ai' was used before it was defined.");
	    th.addError(31, "'bi' was used before it was defined.");
	    th.addError(48, "'ci' was used before it was defined.");
	    th.addError(75, "'importedName' was used before it was defined.");
	    th.addError(76, "'importedModule' was used before it was defined.");
	    th.addError(77, "'importedNamespace' was used before it was defined.");
	    th.test(esnextSrc, new LinterOptions().set("esversion", 2015).set("latedef", true));
	    th.test(esnextSrc, new LinterOptions().set("esversion", 2015).set("latedef", "nofunc"));
	    
	    th.reset();
	    th.test("var a;", new LinterOptions().addExporteds("a").set("latedef", true));
	}
	
	@Test
	public void testLatedefInline()
	{
		String src = th.readFile("src/test/resources/fixtures/latedef-inline.js");
		
		th.addError(4, "'foo' was used before it was defined.");
	    th.addError(6, "'a' was used before it was defined.");
	    th.addError(22, "'a' was used before it was defined.");
	    th.addError(26, "Bad option value.");
		th.test(src);
		
		th.reset();
		th.test("/*exported a*/var a;", new LinterOptions().set("latedef", true));
	}
	
	@Test
	public void testNotypeof()
	{
		String src = th.readFile("src/test/resources/fixtures/typeofcomp.js");
		
		th.addError(1, "Invalid typeof value 'funtion'");
		th.addError(2, "Invalid typeof value 'double'");
		th.addError(3, "Invalid typeof value 'bool'");
		th.addError(4, "Invalid typeof value 'obj'");
		th.addError(13, "Invalid typeof value 'symbol'");
		th.test(src);
		
		th.reset();
		th.addError(1, "Invalid typeof value 'funtion'");
		th.addError(2, "Invalid typeof value 'double'");
		th.addError(3, "Invalid typeof value 'bool'");
		th.addError(4, "Invalid typeof value 'obj'");
		th.test(src, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.test(src, new LinterOptions().set("notypeof", true));
	}
	
	@Test
	public void testCombinationOfLatedefAndUndef()
	{
		String src = th.readFile("src/test/resources/fixtures/latedefundef.js");
		
		// Assures that when `undef` is set to true, it'll report undefined variables
		// and late definitions won't be reported as `latedef` is set to false.
		th.addError(29, "'hello' is not defined.");
		th.addError(35, "'world' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", false).set("undef", true));
		
		// When we suppress `latedef` and `undef` then we get no warnings.
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("latedef", false).set("undef", false));
		
		// If we warn on `latedef` but suppress `undef` we only get the
		// late definition warnings.
		th.reset();
		th.addError(5, "'func2' was used before it was defined.");
	    th.addError(12, "'foo' was used before it was defined.");
	    th.addError(18, "'fn1' was used before it was defined.");
	    th.addError(26, "'baz' was used before it was defined.");
	    th.addError(34, "'fn' was used before it was defined.");
	    th.addError(41, "'q' was used before it was defined.");
	    th.addError(46, "'h' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", true).set("undef", false));
		
		// But we get all the functions warning if we disable latedef func
		th.reset();
		th.addError(41, "'q' was used before it was defined.");
	    th.addError(46, "'h' was used before it was defined.");
		th.test(src, new LinterOptions().set("es3", true).set("latedef", "nofunc").set("undef", false));
	    
		// If we warn on both options we get all the warnings.
	    th.reset();
	    th.addError(5, "'func2' was used before it was defined.");
	    th.addError(12, "'foo' was used before it was defined.");
	    th.addError(18, "'fn1' was used before it was defined.");
	    th.addError(26, "'baz' was used before it was defined.");
	    th.addError(29, "'hello' is not defined.");
	    th.addError(34, "'fn' was used before it was defined.");
	    th.addError(35, "'world' is not defined.");
	    th.addError(41, "'q' was used before it was defined.");
	    th.addError(46, "'h' was used before it was defined.");
	    th.test(src, new LinterOptions().set("es3", true).set("latedef", true).set("undef", true));
	    
	    // If we remove latedef_func, we don't get the functions warning
	    th.reset();
	    th.addError(29, "'hello' is not defined.");
	    th.addError(35, "'world' is not defined.");
	    th.addError(41, "'q' was used before it was defined.");
	    th.addError(46, "'h' was used before it was defined.");
	    th.test(src, new LinterOptions().set("es3", true).set("latedef", "nofunc").set("undef", true));
	}
	
	@Test
	public void testUndefwstrict()
	{
		String src = th.readFile("src/test/resources/fixtures/undefstrict.js");
		th.test(src, new LinterOptions().set("es3", true).set("undef", false));
	}
	
	// Regression test for GH-431
	@Test
	public void testImpliedAnUnusedShouldRespectHoisting()
	{
		String src = th.readFile("src/test/resources/fixtures/gh431.js");
		
		th.addError(14, "'fun4' is not defined.");
		th.test(src, new LinterOptions().set("undef", true)); // es5
		
		JSHint jshint = new JSHint();
		//JSHINT_BUG: seems flag is not used anywhere, can be removed
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
	public void testProtoAndIterator()
	{
		String source = th.readFile("src/test/resources/fixtures/protoiterator.js");
		String json = "{\"__proto__\": true, \"__iterator__\": false, \"_identifier\": null, \"property\": 123}";
		
		// JSHint should not allow the `__proto__` and
		// `__iterator__` properties by default
		th.addError(7, "The '__proto__' property is deprecated.");
	    th.addError(8, "The '__proto__' property is deprecated.");
	    th.addError(10, "The '__proto__' property is deprecated.");
	    th.addError(27, "The '__iterator__' property is deprecated.");
	    th.addError(33, "The '__proto__' property is deprecated.");
	    th.addError(37, "The '__proto__' property is deprecated.");
		th.test(source, new LinterOptions().set("es3", true));
		
		th.reset();
		th.addError(1, "The '__proto__' key may produce unexpected results.");
	    th.addError(1, "The '__iterator__' key may produce unexpected results.");
	    th.test(json, new LinterOptions().set("es3", true));
	    
	    // Should not report any errors when proto and iterator
	    // options are on
	    th.reset();
	    th.test(source, new LinterOptions().set("es3", true).set("proto", true).set("iterator", true));
	    th.test(json, new LinterOptions().set("es3", true).set("proto", true).set("iterator", true));
	}
	
	/**
	 * The `camelcase` option allows you to enforce use of the camel case convention.
	 */
	@Test
	public void testCamelcase()
	{
		String source = th.readFile("src/test/resources/fixtures/camelcase.js");
		
		// By default, tolerate arbitrary identifiers
		th.test(source, new LinterOptions().set("es3", true));
		
		// Require identifiers in camel case if camelcase is true
		th.reset();
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
	 *   if (cond) return;
	 *     vs.
	 *   if (cond) { return; }
	 */
	@Test
	public void testCurly()
	{
		String src = th.readFile("src/test/resources/fixtures/curly.js");
		String src1 = th.readFile("src/test/resources/fixtures/curly2.js");
		
		// By default, tolerate one-line blocks since they are valid JavaScript
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src1, new LinterOptions().set("es3", true));
		
		// Require all blocks to be wrapped with curly braces if curly is true
		th.reset();
		th.addError(2, "Expected '{' and instead saw 'return'.");
	    th.addError(5, "Expected '{' and instead saw 'doSomething'.");
	    th.addError(8, "Expected '{' and instead saw 'doSomething'.");
	    th.addError(11, "Expected '{' and instead saw 'doSomething'.");
		th.test(src, new LinterOptions().set("es3", true).set("curly", true));
		
		th.reset();
		th.test(src1, new LinterOptions().set("es3", true).set("curly", true));
	}
	
	/** Option `noempty` prohibits the use of empty blocks. */
	@Test
	public void testNoempty()
	{
		String[] code = {
			"for (;;) {}",
			"if (true) {",
			"}",
			"foo();"
		};
		
		// By default, tolerate empty blocks since they are valid JavaScript
		th.test(code, new LinterOptions().set("es3", true));
		
		// Do not tolerate, when noempty is true
		th.reset();
		th.addError(1, "Empty block.");
	    th.addError(2, "Empty block.");
		th.test(code, new LinterOptions().set("es3", true).set("noempty", true));
	}
	
	/**
	 * Option `noarg` prohibits the use of arguments.callee and arguments.caller.
	 * JSHint allows them by default but you have to know what you are doing since:
	 *  - They are not supported by all JavaScript implementations
	 *  - They might prevent an interpreter from doing some optimization tricks
	 *  - They are prohibited in the strict mode
	 */
	@Test
	public void testNoarg()
	{
		String src = th.readFile("src/test/resources/fixtures/noarg.js");
		
		// By default, tolerate both arguments.callee and arguments.caller
		th.test(src, new LinterOptions().set("es3", true));
		
		// Do not tolerate both .callee and .caller when noarg is true
		th.reset();
		th.addError(2, "Avoid arguments.callee.");
	    th.addError(6, "Avoid arguments.caller.");
		th.test(src, new LinterOptions().set("es3", true).set("noarg", true));
	}
	
	/** Option `nonew` prohibits the use of constructors for side-effects */
	@Test
	public void testNonew()
	{
		String code = "new Thing();";
		String code1 = "var obj = new Thing();";
		
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code1, new LinterOptions().set("es3", true));
		
		th.reset();
		th.addError(1, 1, "Do not use 'new' for side effects.");
		th.test(code, new LinterOptions().set("es3", true).set("nonew", true));
	}
	
	@Test
	public void testShelljs()
	{
		String src = th.readFile("src/test/resources/fixtures/shelljs.js");
		
		th.addError(1, "'target' is not defined.");
	    th.addError(3, "'echo' is not defined.");
	    th.addError(4, "'exit' is not defined.");
	    th.addError(5, "'cd' is not defined.");
	    th.addError(6, "'pwd' is not defined.");
	    th.addError(7, "'ls' is not defined.");
	    th.addError(8, "'find' is not defined.");
	    th.addError(9, "'cp' is not defined.");
	    th.addError(10, "'rm' is not defined.");
	    th.addError(11, "'mv' is not defined.");
	    th.addError(12, "'mkdir' is not defined.");
	    th.addError(13, "'test' is not defined.");
	    th.addError(14, "'cat' is not defined.");
	    th.addError(15, "'sed' is not defined.");
	    th.addError(16, "'grep' is not defined.");
	    th.addError(17, "'which' is not defined.");
	    th.addError(18, "'dirs' is not defined.");
	    th.addError(19, "'pushd' is not defined.");
	    th.addError(20, "'popd' is not defined.");
	    th.addError(21, "'env' is not defined.");
	    th.addError(22, "'exec' is not defined.");
	    th.addError(23, "'chmod' is not defined.");
	    th.addError(24, "'config' is not defined.");
	    th.addError(25, "'error' is not defined.");
	    th.addError(26, "'tempdir' is not defined.");
	    th.addError(29, "'require' is not defined.");
	    th.addError(30, "'module' is not defined.");
	    th.addError(31, "'process' is not defined.");
		th.test(src, new LinterOptions().set("undef", true));
		
		th.reset();
		th.test(src, new LinterOptions().set("undef", true).set("shelljs", true));
	}
	
	// Option `asi` allows you to use automatic-semicolon insertion
	@Test
	public void testAsi()
	{
		String src = th.readFile("src/test/resources/fixtures/asi.js");
		
		th.addError(2, "Missing semicolon.");
	    th.addError(4, "Missing semicolon.");
	    th.addError(5, "Missing semicolon.");
	    th.addError(9, "Line breaking error 'continue'.");
	    th.addError(9, "Missing semicolon.");
	    th.addError(10, "Missing semicolon.");
	    th.addError(11, "Line breaking error 'break'.");
	    th.addError(11, "Missing semicolon.");
	    th.addError(12, "Missing semicolon.");
	    th.addError(16, "Missing semicolon.");
	    th.addError(17, "Missing semicolon.");
	    th.addError(19, "Line breaking error 'break'.");
	    th.addError(19, "Missing semicolon.");
	    th.addError(21, "Line breaking error 'break'.");
	    th.addError(21, "Missing semicolon.");
	    th.addError(25, "Missing semicolon.");
	    th.addError(26, 10, "Missing semicolon.");
	    th.addError(27, 12, "Missing semicolon.");
	    th.addError(28, 12, "Missing semicolon.");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("asi", true));
		
		String[] code = {
			"function a() { 'code' }",
		    "function b() { 'code'; 'code' }",
		    "function c() { 'code', 'code' }",
		    "function d() {",
		    "  'code' }",
		    "function e() { 'code' 'code' }"
		};
		
		th.reset();
		th.addError(2, "Unnecessary directive \"code\".");
	    th.addError(3, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(6, "E058", "Missing semicolon.");
	    th.addError(6, 16, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(6, 23, "Expected an assignment or function call and instead saw an expression.");
	    th.test(code, new LinterOptions().set("asi", true));
	}
	
	// Option `asi` extended for safety -- warn in scenarios that would be unsafe when using asi.
	@Test
	public void testSafeasi()
	{
		String src = th.readFile("src/test/resources/fixtures/safeasi.js");
		
		// JSHINT_TODO consider setting an option to suppress these errors so that
	    // the tests don't become tightly interdependent
		th.addError(10, "Misleading line break before '/'; readers may interpret this as an expression boundary.");
	    th.addError(10, "Expected an identifier and instead saw '.'.");
	    th.addError(10, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(10, "Missing semicolon.");
	    th.addError(10, "Missing semicolon.");
	    th.addError(11, "Missing semicolon.");
	    th.addError(21, "Missing semicolon.");
		th.test(src, new LinterOptions());
		
		th.reset();
		th.addError(5, "Misleading line break before '('; readers may interpret this as an expression boundary.");
	    th.addError(8, "Misleading line break before '('; readers may interpret this as an expression boundary.");
	    th.addError(10, "Misleading line break before '/'; readers may interpret this as an expression boundary.");
	    th.addError(10, "Misleading line break before '/'; readers may interpret this as an expression boundary.");
	    th.addError(10, "Expected an identifier and instead saw '.'.");
	    th.addError(10, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(10, "Missing semicolon.");
		th.test(src, new LinterOptions().set("asi", true));
	}
	
	@Test
	public void testMissingSemicolonsNotInfluencedByAsi()
	{
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
		
		th.reset();
		th.test(code, new LinterOptions().set("expr", true).set("asi", true));
		
		String codeStr = "do {} while (false) var a;";
		
		th.reset();
		th.addError(1, "E058", "Missing semicolon.");
		th.test(codeStr);
		th.test(codeStr, new LinterOptions().set("moz", true));
		
		th.reset();
		th.addError(1, "W033", "Missing semicolon.");
		th.test(codeStr, new LinterOptions().set("esversion", 6));
		
		th.reset();
		th.test(codeStr, new LinterOptions().set("esversion", 6).set("asi", true));
		
		th.reset();
		th.addError(1, "E058", "Missing semicolon.");
		th.test("'do' var x;", new LinterOptions().set("esversion", 6).set("expr", true));
	}
	
	/** Option `lastsemic` allows you to skip the semicolon after last statement in a block,
	 * if that statement is followed by the closing brace on the same line.
	 */
	@Test
	public void testLastsemic()
	{
		String src = th.readFile("src/test/resources/fixtures/lastsemic.js");
		
		// without lastsemic
		th.addError(2, "Missing semicolon."); // missing semicolon in the middle of a block
	    th.addError(4, "Missing semicolon."); // missing semicolon in a one-liner function
	    th.addError(5, "Missing semicolon."); // missing semicolon at the end of a block
		th.test(src, new LinterOptions().set("es3", true));
		
		// with lastsemic
		th.reset();
		th.addError(2, "Missing semicolon.");
	    th.addError(5, "Missing semicolon.");
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
	public void testExpr()
	{
		String[] exps = {
			"obj && obj.method && obj.method();",
			"myvar && func(myvar);",
			"1;",
			"true;",
			"+function (test) {};"
		};
		
		for (String exp : exps)
		{
			th.reset();
			th.addError(1, "Expected an assignment or function call and instead saw an expression.");
			th.test(exp, new LinterOptions().set("es3", true));
		}
		
		for (String exp : exps)
		{
			th.reset();
			th.test(exp, new LinterOptions().set("es3", true).set("expr", true));
		}
	}
	
	// Option `undef` requires you to always define variables you use.
	@Test
	public void testUndef()
	{
		String src = th.readFile("src/test/resources/fixtures/undef.js");
		
		// Make sure there are no other errors
		th.test(src, new LinterOptions().set("es3", true));
		
		// Make sure it fails when undef is true
		th.reset();
		th.addError(1, "'undef' is not defined.");
	    th.addError(5, "'undef' is not defined.");
	    th.addError(6, "'undef' is not defined.");
	    th.addError(8, "'undef' is not defined.");
	    th.addError(9, "'undef' is not defined.");
	    th.addError(13, "'localUndef' is not defined.");
	    th.addError(18, "'localUndef' is not defined.");
	    th.addError(19, "'localUndef' is not defined.");
	    th.addError(21, "'localUndef' is not defined.");
	    th.addError(22, "'localUndef' is not defined.");
	    th.addError(32, "'undef' is not defined.");
	    th.addError(33, "'undef' is not defined.");
	    th.addError(34, "'undef' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("undef", true));
		
		// block scope cannot use themselves in the declaration
		th.reset();
	    th.addError(1, "'a' was used before it was declared, which is illegal for 'let' variables.");
	    th.addError(2, "'b' was used before it was declared, which is illegal for 'const' variables.");
		th.addError(5, "'e' is already defined.");
		th.test(new String[] {
			"let a = a;",
			"const b = b;",
			"var c = c;",
			"function f(e) {",
			"  var e;",         // the var does not overwrite the param, the param is used
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
		th.reset();
	    th.addError(4, "'x' is not defined.");
	    th.test(new String[]{
	    	"label: {",
	    	"  let x;",
	    	"}",
	    	"void x;"
	    }, new LinterOptions().set("esversion", 6).set("undef", true));
	}
	
	@Test
	public void testUndefToOpMethods()
	{
		th.addError(2, "'undef' is not defined.");
	    th.addError(3, "'undef' is not defined.");
		th.test(new String[]{
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
	public void testUndefDeleteStrict()
	{
		th.addError(3, "'aNullReference' is not defined.");
		th.test(new String[]{
				"(function() {",
				"  'use strict';",
				"  delete aNullReference;",
				"}());"
		}, new LinterOptions().set("undef", true).set("-W051", false));
	}
	
	@Test(groups = {"unused"})
	public void testUnusedBasic()
	{
		String src = th.readFile("src/test/resources/fixtures/unused.js");
		
		String[][] allErrors = {
			{"22", "'i' is defined but never used."},
			{"101", "'inTry2' used out of scope."},
			{"117", "'inTry9' was used before it was declared, which is illegal for 'let' variables."},
			{"118", "'inTry10' was used before it was declared, which is illegal for 'const' variables."}
		};
		
		for (String[] error : allErrors)
		{
			th.addError(Integer.parseInt(error[0]), error[1]);
		}
		th.test(src, new LinterOptions().set("esnext", true));
		
		String [][] var_errors = ArrayUtils.addAll(allErrors, new String[][]{
			{"1", "'a' is defined but never used."},
			{"7", "'c' is defined but never used."},
			{"15", "'foo' is defined but never used."},
			{"20", "'bar' is defined but never used."},
			{"36", "'cc' is defined but never used."},
			{"39", "'dd' is defined but never used."},
			{"58", "'constUsed' is defined but never used."},
			{"62", "'letUsed' is defined but never used."},
			{"63", "'anotherUnused' is defined but never used."},
			{"63", "'anotherUnused' is defined but never used."},
			{"91", "'inTry6' is defined but never used."},
			{"94", "'inTry9' is defined but never used."},
			{"95", "'inTry10' is defined but never used."},
			{"99", "'inTry4' is defined but never used."},
			{"122", "'unusedRecurringFunc' is defined but never used."}
		});
		
		String[][] last_param_errors = {
			{"6", "'f' is defined but never used."},
			{"28", "'a' is defined but never used."},
			{"28", "'b' is defined but never used."},
			{"28", "'c' is defined but never used."},
			{"68", "'y' is defined but never used."},
			{"69", "'y' is defined but never used."},
			{"70", "'z' is defined but never used."}
		};
		
		String[][] all_param_errors = {
			{"15", "'err' is defined but never used."},
			{"28", "'a' is defined but never used."},
			{"28", "'b' is defined but never used."},
			{"28", "'c' is defined but never used."},
			{"71", "'y' is defined but never used."}
		};
		
		th.reset();
		for (String[] error : ArrayUtils.addAll(var_errors, last_param_errors))
		{
			th.addError(Integer.parseInt(error[0]), error[1]);
		}
		
		th.test(src, new LinterOptions().set("esnext", true).set("unused", true));
		JSHint jshint = new JSHint();
		assertTrue(!jshint.lint(src, new LinterOptions().set("esnext", true).set("unused", true)));
		
		// Test checking all function params via unused="strict"
		th.reset();
		for (String[] error : ArrayUtils.addAll(ArrayUtils.addAll(var_errors, last_param_errors), all_param_errors))
		{
			th.addError(Integer.parseInt(error[0]), error[1]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("unused", "strict"));
		
		// Test checking everything except function params
		th.reset();
		for (String[] error : var_errors)
		{
			th.addError(Integer.parseInt(error[0]), error[1]);
		}
		th.test(src, new LinterOptions().set("esnext", true).set("unused", "vars"));
		
		List<Token> unused = th.getJSHint().generateSummary().getUnused();
		assertEquals(24, unused.size());
		
		boolean some = false;
		for (Token err : unused)
		{
			if (err.getLine() == 1 && err.getCharacter() == 5 && err.getName().equals("a"))
			{
				some = true;
				break;
			}
		}
		assertTrue(some);
		
		some = false;
		for (Token err : unused)
		{
			if (err.getLine() == 6 && err.getCharacter() == 18 && err.getName().equals("f"))
			{
				some = true;
				break;
			}
		}
		assertTrue(some);
		
		some = false;
		for (Token err : unused)
		{
			if (err.getLine() == 7 && err.getCharacter() == 9 && err.getName().equals("c"))
			{
				some = true;
				break;
			}
		}
		assertTrue(some);
		
		some = false;
		for (Token err : unused)
		{
			if (err.getLine() == 15 && err.getCharacter() == 10 && err.getName().equals("foo"))
			{
				some = true;
				break;
			}
		}
		assertTrue(some);
		
		some = false;
		for (Token err : unused)
		{
			if (err.getLine() == 68 && err.getCharacter() == 5 && err.getName().equals("y"))
			{
				some = true;
				break;
			}
		}
		assertTrue(some);
	}
	
	// Regression test for gh-2784
	@Test(groups = {"unused"})
	public void testUnusedUsedThroughShadowedDeclaration()
	{
		String[] code = {
			"(function() {",
		    "  var x;",
		    "  {",
		    "    var x;",
		    "    void x;",
		    "  }",
		    "}());"
		};
		
		th.addError(4, "'x' is already defined.");
		th.test(code, new LinterOptions().set("unused", true));
	}
	
	@Test(groups = {"unused"})
	public void testUnusedUnusedThroughShadowedDeclaration()
	{
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
		
		th.addError(7, "'x' is already defined.");
		th.test(code, new LinterOptions().set("unused", true));
	}
	
	@Test(groups = {"unused"})
	public void testUnusedHoisted()
	{
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
		
		th.addError(6, "'x' is already defined.");
	    th.addError(8, "'x' used out of scope.");
	    th.test(code, new LinterOptions().set("unused", true));
	}
	
	@Test(groups = {"unused"})
	public void testUnusedCrossBlocks()
	{
		String code = th.readFile("src/test/resources/fixtures/unused-cross-blocks.js");
		
		th.addError(15, "'func4' is already defined.");
	    th.addError(18, "'func5' is already defined.");
	    th.addError(41, "'topBlock6' is already defined.");
	    th.addError(44, "'topBlock7' is already defined.");
	    th.addError(56, "'topBlock3' is already defined.");
	    th.addError(59, "'topBlock4' is already defined.");
	    th.addError(9, "'unusedFunc' is defined but never used.");
	    th.addError(27, "'unusedTopBlock' is defined but never used.");
	    th.addError(52, "'unusedNestedBlock' is defined but never used.");
	    th.test(code, new LinterOptions().set("unused", true));
	    
	    th.reset();
	    th.addError(15, "'func4' is already defined.");
	    th.addError(18, "'func5' is already defined.");
	    th.addError(41, "'topBlock6' is already defined.");
	    th.addError(44, "'topBlock7' is already defined.");
	    th.addError(56, "'topBlock3' is already defined.");
	    th.addError(59, "'topBlock4' is already defined.");
	    th.test(code);
	}
	
	@Test
	public void testParamOverridesFunctionNameExpression()
	{
		th.test(new String[] {
			"var A = function B(B) {",
		    "  return B;",
		    "};",
		    "A();"
		}, new LinterOptions().set("undef", true).set("unused", "strict"));
	}
	
	@Test
	public void testLetCanReuseFunctionAndClassName()
	{
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
	public void testUnusedWithParamDestructuring()
	{
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
		
		th.addError(2, "'args' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
		
		th.reset();
		th.addError(1, "'args' is defined but never used.");
	    th.addError(2, "'args' is defined but never used.");
	    th.addError(3, "'args' is defined but never used.");
	    th.addError(4, "'args' is defined but never used.");
	    th.addError(5, "'args' is defined but never used.");
	    th.addError(6, "'args' is defined but never used.");
	    th.addError(7, "'args' is defined but never used.");
	    th.addError(8, "'args' is defined but never used.");
	    th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));
	}
	
	@Test
	public void testUnusedDataWithOptions()
	{
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
		
		List<Token> expectedAllUnused = new ArrayList<Token>(expectedParamUnused);
		expectedAllUnused.addAll(expectedPlaceholderUnused);
		expectedAllUnused.addAll(expectedVarUnused);
		List<Token> expectedVarAndParamUnused = new ArrayList<Token>(expectedParamUnused);
		expectedVarAndParamUnused.addAll(expectedVarUnused);
		
		//true
		th.addError(1, "'func' is defined but never used.");
	    th.addError(1, "'param' is defined but never used.");
	    th.test(code, new LinterOptions().set("unused", true));
	    
	    List<Token> unused = th.getJSHint().generateSummary().getUnused();
	    assertEquals(expectedVarAndParamUnused, unused);
	    
	    // false
	    th.reset();
	    th.test(code, new LinterOptions().set("unused", false));
	    
	    unused = th.getJSHint().generateSummary().getUnused();
	    assertEquals(expectedVarUnused, unused);
	    
	    // strict
	    th.reset();
	    th.addError(1, "'func' is defined but never used.");
	    th.addError(1, "'placeHolder1' is defined but never used.");
	    th.addError(1, "'placeHolder2' is defined but never used.");
	    th.addError(1, "'param' is defined but never used.");
	    th.test(code, new LinterOptions().set("unused", "strict"));
	    
	    unused = th.getJSHint().generateSummary().getUnused();
	    assertEquals(expectedAllUnused, unused);
	    
	    // vars
	    th.reset();
	    th.addError(1, "'func' is defined but never used.");
	    th.test(code, new LinterOptions().set("unused", "vars"));
	    
	    unused = th.getJSHint().generateSummary().getUnused();
	    assertEquals(expectedAllUnused, unused);
	}
	
	@Test
	public void testUnusedWithGlobalOverride()
	{
		String[] code = {
			"alert();",
		    "function alert() {}"
		};
		
		th.test(code, new LinterOptions().set("unused", true).set("undef", true).set("devel", true).set("latedef", false));
	}
		
	// Regressions for "unused" getting overwritten via comment (GH-778)
	@Test
	public void testUnusedOverrides()
	{
		String[] code;
		
		code = new String[]{"function foo(a) {", "/*jshint unused:false */", "}", "foo();"};
		th.test(code, new LinterOptions().set("es3", true).set("unused", true));
		
		code = new String[]{"function foo(a, b, c) {", "/*jshint unused:vars */", "var i = b;", "}", "foo();"};
		th.reset();
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true));
		
		code = new String[]{"function foo(a, b, c) {", "/*jshint unused:true */", "var i = b;", "}", "foo();"};
		th.reset();
		th.addError(1, "'c' is defined but never used.");
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));
		
		code = new String[]{"function foo(a, b, c) {", "/*jshint unused:strict */", "var i = b;", "}", "foo();"};
		th.reset();
		th.addError(1, "'a' is defined but never used.");
		th.addError(1, "'c' is defined but never used.");
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", true));
		
		code = new String[]{"/*jshint unused:vars */", "function foo(a, b) {}", "foo();"};
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));
		
		code = new String[]{"/*jshint unused:vars */", "function foo(a, b) {", "var i = 3;", "}", "foo();"};
		th.reset();
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));
		
		code = new String[]{"/*jshint unused:badoption */", "function foo(a, b) {", "var i = 3;", "}", "foo();"};
		th.reset();
		th.addError(1, "Bad option value.");
		th.addError(2, "'b' is defined but never used.");
		th.addError(2, "'a' is defined but never used.");
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("es3", true).set("unused", "strict"));
	}
	
	@Test
	public void testUnusedOverridesEsnext()
	{
		String[] code;
		
		code = new String[]{"function foo(a) {", "/*jshint unused:false */", "}", "foo();"};
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
		
		code = new String[]{"function foo(a, b, c) {", "/*jshint unused:vars */", "let i = b;", "}", "foo();"};
		th.reset();
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
		
		code = new String[]{"function foo(a, b, c) {", "/*jshint unused:true */", "let i = b;", "}", "foo();"};
		th.reset();
		th.addError(1, "'c' is defined but never used.");
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));
		
		code = new String[]{"function foo(a, b, c) {", "/*jshint unused:strict */", "let i = b;", "}", "foo();"};
		th.reset();
		th.addError(1, "'a' is defined but never used.");
		th.addError(1, "'c' is defined but never used.");
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", true));
		
		code = new String[]{"/*jshint unused:vars */", "function foo(a, b) {", "let i = 3;", "}", "foo();"};
		th.reset();
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));
		
		code = new String[]{"/*jshint unused:badoption */", "function foo(a, b) {", "let i = 3;", "}", "foo();"};
		th.reset();
		th.addError(1, "Bad option value.");
		th.addError(2, "'b' is defined but never used.");
		th.addError(2, "'a' is defined but never used.");
		th.addError(3, "'i' is defined but never used.");
		th.test(code, new LinterOptions().set("esnext", true).set("unused", "strict"));
	}
	
	@Test
	public void testUnusedWithLatedefFunction()
	{
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
		
		th.addError(10, "'e' is defined but never used.");
		th.test(code, new LinterOptions().set("undef", false).set("unused", true).set("node", true));
	}
	
	// Regression test for `undef` to make sure that ...
	@Test
	public void testUndefInFunctionScope()
	{
		String src = th.readFile("src/test/resources/fixtures/undef_func.js");
		
		// Make sure that the lint is clean with and without undef.
		th.test(src, new LinterOptions().set("es3", true));
		th.test(src, new LinterOptions().set("es3", true).set("undef", true));
	}
	
	/** Option `scripturl` allows the use of javascript-type URLs */
	@Test
	public void testScripturl()
	{
		String[] code = {
			"var foo = { 'count': 12, 'href': 'javascript:' };",
			"foo = 'javascript:' + 'xyz';"
		};
		String src = th.readFile("src/test/resources/fixtures/scripturl.js");
		
		// Make sure there is an error
		th.addError(1, "Script URL.");
		th.addError(2, "Script URL."); // 2 times?
		th.addError(2, "JavaScript URL.");
		th.test(code, new LinterOptions().set("es3", true));
		
		// Make sure the error goes away when javascript URLs are tolerated
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("scripturl", true));
		
		// Make sure an error does not exist for labels that look like URLs (GH-1013)
		th.reset();
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
	public void testForin()
	{
		String src = th.readFile("src/test/resources/fixtures/forin.js");
		String msg = "The body of a for in should be wrapped in an if statement to filter unwanted " +
					 "properties from the prototype.";
		
		// Make sure there are no other errors
		th.test(src, new LinterOptions().set("es3", true));
		
		// Make sure it fails when forin is true
		th.addError(15, msg);
		th.addError(23, msg);
		th.addError(37, msg);
		th.addError(43, msg);
		th.addError(73, msg);
		th.test(src, new LinterOptions().set("es3", true).set("forin", true));
	}
	
	/**
	 * Option `loopfunc` allows you to use function expression in the loop.
	 * E.g.:
	 *   while (true) x = function (test) {};
	 *
	 * This is generally a bad idea since it is too easy to make a
	 * closure-related mistake.
	 */
	@Test
	public void testLoopfunc()
	{
		String src = th.readFile("src/test/resources/fixtures/loopfunc.js");
		
		// By default, not functions are allowed inside loops
		th.addError(4, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(8, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(20, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(25, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(12, "Function declarations should not be placed in blocks. Use a function " +
	            		"expression or move the statement to the top of the outer function.");
	    th.addError(42, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
		th.test(src, new LinterOptions().set("es3", true));
		
		// When loopfunc is true, only function declaration should fail.
		// Expressions are okay.
		th.reset();
		th.addError(12, "Function declarations should not be placed in blocks. Use a function " +
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
		
		th.reset();
		th.addError(2, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(5, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(11, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(14, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(17, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.test(es6LoopFuncSrc, new LinterOptions().set("esnext", true));
	    
	    // functions declared in the expressions that loop should warn
	    String[] src2 = {
	    	"for(var i = 0; function a(){return i;}; i++) { break; }",
	        "var j;",
	        "while(function b(){return j;}){}",
	        "for(var c = function(){return j;};;){c();}"
	    };
	    
	    th.reset();
	    th.addError(1, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.addError(3, "Functions declared within loops referencing an outer scoped variable may lead to confusing semantics.");
	    th.test(src2, new LinterOptions().set("es3", true).set("loopfunc", false).set("boss", true));
	}
	
	/** Option `boss` unlocks some useful but unsafe features of JavaScript. */
	@Test
	public void testBoss()
	{
		String src = th.readFile("src/test/resources/fixtures/boss.js");
		
		// By default, warn about suspicious assignments
		th.addError(1, "Expected a conditional expression and instead saw an assignment.");
	    th.addError(4, "Expected a conditional expression and instead saw an assignment.");
	    th.addError(7, "Expected a conditional expression and instead saw an assignment.");
	    th.addError(12, "Expected a conditional expression and instead saw an assignment.");

	    // GH-657
	    th.addError(14, "Expected a conditional expression and instead saw an assignment.");
	    th.addError(17, "Expected a conditional expression and instead saw an assignment.");
	    th.addError(20, "Expected a conditional expression and instead saw an assignment.");
	    th.addError(25, "Expected a conditional expression and instead saw an assignment.");

	    // GH-670
	    th.addError(28, "Did you mean to return a conditional instead of an assignment?");
	    th.addError(32, "Did you mean to return a conditional instead of an assignment?");
		th.test(src, new LinterOptions().set("es3", true));
		
		// But if you are the boss, all is good
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("boss", true));
	}
	
	/**
	 * Options `eqnull` allows you to use '== null' comparisons.
	 * It is useful when you want to check if value is null _or_ undefined.
	 */
	@Test
	public void testEqnull()
	{
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
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("eqnull", true));
		
		// Make sure that `eqnull` has precedence over `eqeqeq`
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("eqeqeq", true).set("eqnull", true));
	}
	
	/**
	 * Option `supernew` allows you to use operator `new` with anonymous functions
	 * and objects without invocation.
	 *
	 * Ex.:
	 *   new function (test) { ... };
	 *   new Date;
	 */
	@Test
	public void testSupernew()
	{
		String src = th.readFile("src/test/resources/fixtures/supernew.js");
		
		th.addError(1, "Weird construction. Is 'new' necessary?");
	    th.addError(9, 1, "Missing '()' invoking a constructor.");
	    th.addError(11, 13, "Missing '()' invoking a constructor.");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("supernew", true));
	}
	
	/** Option `bitwise` disallows the use of bitwise operators. */
	@Test
	public void testBitwise()
	{
		String[] unOps = {"~"};
		String[] binOps = {"&",  "|",  "^",  "<<",  ">>",  ">>>"};
		String[] modOps = {"&=", "|=", "^=", "<<=", ">>=", ">>>="};
		
		// By default allow bitwise operators
		for (int i = 0; i < unOps.length; i++)
		{
			String op = unOps[i];
			
			th.reset();
			th.test("var b = " + op + "a;", new LinterOptions().set("es3", true));
			
			th.addError(1, "Unexpected use of '" + op + "'.");
			th.test("var b = " + op + "a;", new LinterOptions().set("es3", true).set("bitwise", true));
		}
		
		for (int i = 0; i < binOps.length; i++)
		{
			String op = binOps[i];
			
			th.reset();
			th.test("var c = a " + op + " b;", new LinterOptions().set("es3", true));
			
			th.addError(1, "Unexpected use of '" + op + "'.");
			th.test("var c = a " + op + " b;", new LinterOptions().set("es3", true).set("bitwise", true));
		}
		
		for (int i = 0; i < modOps.length; i++)
		{
			String op = modOps[i];
			
			th.reset();
			th.test("b " + op + " a;", new LinterOptions().set("es3", true));
			
			th.addError(1, "Unexpected use of '" + op + "'.");
			th.test("b " + op + " a;", new LinterOptions().set("es3", true).set("bitwise", true));
		}
	}
	
	/** Option `debug` allows the use of debugger statements. */
	@Test
	public void testDebug1()
	{
		String code = "function test () { debugger; return true; }";
		
		// By default disallow debugger statements.
		th.addError(1, "Forgotten 'debugger' statement?");
		th.test(code, new LinterOptions().set("es3", true));
		
		// But allow them if debug is true.
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("debug", true));
	}
	
	//JSHINT_BUG: copy paste bug, two tests with the same name
	/** `debugger` statements without semicolons are found on the correct line */
	@Test
	public void testDebug2()
	{
		String[] src = {
			"function test () {",
			"debugger",
			"return true; }"
		};
		
		// Ensure we mark the correct line when finding debugger statements
		th.addError(2, "Forgotten 'debugger' statement?");
		th.test(src, new LinterOptions().set("es3", true).set("asi", true));
	}
	
	/** Option `eqeqeq` requires you to use === all the time. */
	@Test
	public void testEqeqeq()
	{
		String src = th.readFile("src/test/resources/fixtures/eqeqeq.js");
		
		/**
		 * This test previously asserted the issuance of warning W041.
		 * W041 has since been removed, but the test is maintained in 
		 * order to discourage regressions.
		 */
		th.test(src, new LinterOptions().set("es3", true));
		
		th.reset();
		th.addError(2, "Expected '===' and instead saw '=='.");
	    th.addError(5, "Expected '!==' and instead saw '!='.");
	    th.addError(8, "Expected '===' and instead saw '=='.");
		th.test(src, new LinterOptions().set("es3", true).set("eqeqeq", true));	
	}
	
	/** Option `evil` allows the use of eval. */
	@Test
	public void testEvil()
	{
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
		
		th.addError(1, "eval can be harmful.");
	    th.addError(2, "document.write can be a form of eval.");
	    th.addError(3, "document.write can be a form of eval.");
	    th.addError(4, "eval can be harmful.");
	    th.addError(5, "The Function constructor is a form of eval.");
	    th.addError(6, "Implied eval. Consider passing a function instead of a string.");
	    th.addError(7, "Implied eval. Consider passing a function instead of a string.");
	    th.addError(8, "eval can be harmful.");
		th.test(src, new LinterOptions().set("es3", true).set("browser", true));
		
		th.reset();
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
	 *   var a = (function (test) {
	 *     return 'a';
	 *   }());
	 *   console.log(a); // --> 'a'
	 */
	@Test
	public void testImmed()
	{
		String src = th.readFile("src/test/resources/fixtures/immed.js");
		
		th.test(src, new LinterOptions().set("es3", true));
		
		th.reset();
		th.addError(3, "Wrap an immediate function invocation in parens " +
		           	   "to assist the reader in understanding that the expression " +
		           	   "is the result of a function, and not the function itself.");
		th.addError(13, "Wrapping non-IIFE function literals in parens is unnecessary.");
		th.test(src, new LinterOptions().set("es3", true).set("immed", true));
		
		// Regression for GH-900
		th.reset();
		th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Missing semicolon.");
	    th.addError(1, "Expected an identifier and instead saw ')'.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Unmatched '{'.");
	    th.addError(1, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(1, "Missing semicolon.");
	    th.addError(1, "Unrecoverable syntax error. (100% scanned).");
		th.test("(function () { if (true) { }());", new LinterOptions().set("es3", true).set("immed", true));	
	}
	
	/** Option `plusplus` prohibits the use of increments/decrements. */
	@Test
	public void testPlusplus()
	{
		String[] ops = {"++", "--"};
		
		// By default allow bitwise operators
		for (int i = 0; i < ops.length; i++)
		{
			String op = ops[i];
			th.test("var i = j" + op + ";", new LinterOptions().set("es3", true));
			th.test("var i = " + op + "j;", new LinterOptions().set("es3", true));
		}
		
		for (int i = 0; i < ops.length; i++)
		{
			String op = ops[i];
			th.reset();
			th.addError(1, "Unexpected use of '" + op + "'.");
			th.test("var i = j" + op + ";", new LinterOptions().set("es3", true).set("plusplus", true));
			
			th.reset();
			th.addError(1, "Unexpected use of '" + op + "'.");
			th.test("var i = " + op + "j;", new LinterOptions().set("es3", true).set("plusplus", true));
		}
	}
	
	/**
	 * Option `newcap` requires constructors to be capitalized.
	 *
	 * Constructors are functions that are designed to be used with the `new` statement.
	 * `new` creates a new object and binds it to the implied this parameter.
	 * A constructor executed without new will have its this assigned to a global object,
	 * leading to errors.
	 *
	 * Unfortunately, JavaScript gives us absolutely no way of telling if a function is a
	 * constructor. There is a convention to capitalize all constructor names to prevent
	 * those mistakes. This option enforces that convention.
	 */
	@Test
	public void testNewcap()
	{
		String src = th.readFile("src/test/resources/fixtures/newcap.js");
		
		th.test(src, new LinterOptions().set("es3", true));  // By default, everything is fine
		
		// When newcap is true, enforce the conventions
		th.reset();
		th.addError(1, "A constructor name should start with an uppercase letter.");
	    th.addError(5, "Missing 'new' prefix when invoking a constructor.");
	    th.addError(10, "A constructor name should start with an uppercase letter.");
	    th.addError(14, "A constructor name should start with an uppercase letter.");
		th.test(src, new LinterOptions().set("es3", true).set("newcap", true));	
	}
	
	/** Option `sub` allows all forms of subscription. */
	@Test
	public void testSub()
	{
		th.addError(1, 17, "['prop'] is better written in dot notation.");
		th.test("window.obj = obj['prop'];", new LinterOptions().set("es3", true));
		
		th.reset();
		th.test("window.obj = obj['prop'];", new LinterOptions().set("es3", true).set("sub", true));	
	}
	
	/** Option `strict` requires you to use "use strict"; */
	@Test
	public void testStrict()
	{
		String code = "(function (test) { return; }());";
		String code1 = "(function (test) { \"use strict\"; return; }());";
		String code2 = "var obj = Object({ foo: 'bar' });";
		String code3 = "'use strict'; \n function hello() { return; }";
		String src = th.readFile("src/test/resources/fixtures/strict_violations.js");
		String src2 = th.readFile("src/test/resources/fixtures/strict_incorrect.js");
		String src3 = th.readFile("src/test/resources/fixtures/strict_newcap.js");
		
		th.test(code, new LinterOptions().set("es3", true));
		th.test(code1, new LinterOptions().set("es3", true));
		
		th.addError(1, "Missing \"use strict\" statement.");
		th.test(code, new LinterOptions().set("es3", true).set("strict", true));
		th.test(code, new LinterOptions().set("es3", true).set("strict", "global"));
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("strict", "implied"));
		
		th.test(code1, new LinterOptions().set("es3", true).set("strict", true));
		th.test(code1, new LinterOptions().set("es3", true).set("strict", "global"));
		th.addError(1, "Unnecessary directive \"use strict\".");
		th.test(code1, new LinterOptions().set("es3", true).set("strict", "implied"));
		
		// Test for strict mode violations
		th.reset();
		th.addError(4, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(7, "Strict violation.");
	    th.addError(8, "Strict violation.");
		th.test(src, new LinterOptions().set("es3", true).set("strict", true));
		th.test(src, new LinterOptions().set("es3", true).set("strict", "global"));
		
		th.reset();
		th.addError(4, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(9, "Missing semicolon.");
	    th.addError(28, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(53, "Expected an assignment or function call and instead saw an expression.");
		th.test(src2, new LinterOptions().set("es3", true).set("strict", false));
		
		th.reset();
		th.test(src3, new LinterOptions().set("es3", true));
		
		th.reset();
		th.test(code2, new LinterOptions().set("es3", true).set("strict", true));
		th.addError(1, "Missing \"use strict\" statement.");
		th.test(code2, new LinterOptions().set("es3", true).set("strict", "global"));
		
		th.reset();
		th.test(code3, new LinterOptions().set("strict", "global"));
		th.addError(1, "Use the function form of \"use strict\".");
		th.test(code3, new LinterOptions().set("strict", true));
		th.addError(1, "Unnecessary directive \"use strict\".");
		th.test(code3, new LinterOptions().set("strict", "implied"));
		
		JSHint jshint = new JSHint();
		for (String val : new String[]{"true", "false", "global", "implied"})
		{
			jshint.lint("/*jshint strict: " + val + " */");
			assertEquals(jshint.generateSummary().getOptions().asString("strict"), val);
		}
		
		th.reset();
		th.addError(1, "Bad option value.");
	    th.test("/*jshint strict: foo */");
	    
	    th.reset();
	    th.test(code3, new LinterOptions().set("strict", true).set("node", true));
	    
	    th.addError(1, "Missing \"use strict\" statement.");
	    th.test("a = 2;", new LinterOptions().set("strict", "global"));
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
	public void testStrictEnvs()
	{
		String[] partialStrict = {
			"void 0;",
		    "(function() { void 0; }());",
		    "(function() { 'use strict'; void 0; }());"
		};
		
		th.addError(2, "Missing \"use strict\" statement.");
		th.test(partialStrict, new LinterOptions().set("strict", true).set("browserify", true));
		th.test(partialStrict, new LinterOptions().set("strict", true).set("node", true));
		th.test(partialStrict, new LinterOptions().set("strict", true).set("phantom", true));
		
		partialStrict = new String[] {
			"(() =>",
		    "  void 0",
		    ")();"
		};
		
		th.reset();
		th.addError(3, "Missing \"use strict\" statement.");
		th.test(partialStrict, new LinterOptions().set("esversion", 6).set("strict", true).set("browserify", true));
		th.test(partialStrict, new LinterOptions().set("esversion", 6).set("strict", true).set("node", true));
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
	public void testStrictNoise()
	{
		th.addError(1, "Missing \"use strict\" statement.");
	    th.addError(2, "Missing \"use strict\" statement.");
	    th.test(new String[] {
	    	"void 0;",
	        "void 0;"
	    }, new LinterOptions().set("strict", true).set("globalstrict", true));
	    
	    th.reset();
	    th.addError(2, "Missing \"use strict\" statement.");
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
	public void testGlobalstrict()
	{
		String[] code = {
			"\"use strict\";",
			"function hello() { return; }"
		};
		
		th.addError(1, "Use the function form of \"use strict\".");
		th.test(code, new LinterOptions().set("es3", true).set("strict", true));
		
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("globalstrict", true));
		
		// Check that globalstrict also enabled strict
		th.reset();
		th.addError(1, "Missing \"use strict\" statement.");
		th.test(code[1], new LinterOptions().set("es3", true).set("globalstrict", true));
		
		// Don't enforce "use strict"; if strict has been explicitly set to false
		th.reset();
		th.test(code[1], new LinterOptions().set("es3", true).set("globalstrict", true).set("strict", false));
		
		th.reset();
		th.addError(0, "Incompatible values for the 'strict' and 'globalstrict' linting options. (0% scanned).");
	    th.test("this is not JavaScript", new LinterOptions().set("strict", "global").set("globalstrict", false));
	    th.test("this is not JavaScript", new LinterOptions().set("strict", "global").set("globalstrict", true));
	    
	    th.reset();
	    th.addError(2, "Incompatible values for the 'strict' and 'globalstrict' linting options. (66% scanned).");
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
	    
	    th.reset();
	    th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("esnext", true).set("module", true));
	    th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("node", true));
	    th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("phantom", true));
	    th.test(code, new LinterOptions().set("strict", true).set("globalstrict", false).set("browserify", true));
		
		// Check that we can detect missing "use strict"; statement for code that is
		// not inside a function
		code = new String[] {
			"var a = 1;",
			"a += 1;",
			"function func() {}"
		};
		th.reset();
		th.addError(1, "Missing \"use strict\" statement.");
		th.addError(2, "Missing \"use strict\" statement.");
		th.test(code, new LinterOptions().set("globalstrict", true).set("strict", true));
		
		// globalscript does not prevent you from using only the function-mode
		// "use strict";
		th.reset();
		th.test("(function (test) { \"use strict\"; return; }());", new LinterOptions().set("globalstrict", true).set("strict", true));
		
		th.test("'use strict';", new LinterOptions().set("strict", false).set("globalstrict", true));
		
		th.test(new String[] {
			"// jshint globalstrict: true",
		    // The specific option set by the following directive is not relevant.
		    // Any option set by another directive will trigger the regression.
		    "// jshint undef: true"
		});
		
		th.test(new String[] {
			"// jshint strict: true, globalstrict: true",
		    // The specific option set by the following directive is not relevant.
		    // Any option set by another directive will trigger the regression.
		    "// jshint undef: true"
		});
	}
	
	/** Option `laxbreak` allows you to insert newlines before some operators. */
	@Test
	public void testLaxbreak()
	{
		String src = th.readFile("src/test/resources/fixtures/laxbreak.js");
		
		th.addError(2, "Misleading line break before ','; readers may interpret this as an expression boundary.");
	    th.addError(3, "Comma warnings can be turned off with 'laxcomma'.");
	    th.addError(12, "Misleading line break before ','; readers may interpret this as an expression boundary.");
		th.test(src, new LinterOptions().set("es3", true));
		
		String[] ops = {"||", "&&", "*", "/", "%", "+", "-", ">=", "==", "===", "!=", "!==", ">", "<", "<=", "instanceof"};
		
		for (int i = 0; i < ops.length; i++)
		{
			String op = ops[i];
			String[] code = {"var a = b ", op + " c;"};
			
			th.reset();
			th.addError(2, "Misleading line break before '" + op + "'; readers may interpret this as an expression boundary.");
			th.test(code, new LinterOptions().set("es3", true));
			
			th.reset();
			th.test(code, new LinterOptions().set("es3", true).set("laxbreak", true));
		}
		
		String[] code = {"var a = b ", "? c : d;"};
		th.reset();
		th.addError(2, "Misleading line break before '?'; readers may interpret this as an expression boundary.");
		th.test(code, new LinterOptions().set("es3", true));
		
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("laxbreak", true));
	}
	
	@Test
	public void testValidthis()
	{
		String src = th.readFile("src/test/resources/fixtures/strict_this.js");
		
		th.addError(8, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(9, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(11, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(src, new LinterOptions().set("es3", true));
		
		src = th.readFile("src/test/resources/fixtures/strict_this2.js");
		th.reset();
		th.test(src, new LinterOptions().set("es3", true));
		
		// Test for erroneus use of validthis
		
		String[] code = {"/*jshint validthis:true */", "hello();"};
		th.reset();
		th.addError(1, "Option 'validthis' can't be used in a global scope.");
		th.test(code, new LinterOptions().set("es3", true));
		
		code = new String[] {"function x() {", "/*jshint validthis:heya */", "hello();", "}"};
		th.reset();
		th.addError(2, "Bad option value.");
		th.test(code, new LinterOptions().set("es3", true));
	}
	

	/**
	 * Test string relevant options
	 * multistr   allows multiline strings
	 */
	@Test
	public void testStrings()
	{
		String src = th.readFile("src/test/resources/fixtures/strings.js");
		
		th.addError(9, "Unclosed string.");
	    th.addError(10, "Unclosed string.");
	    th.addError(15, "Unclosed string.");
	    th.addError(25, "Octal literals are not allowed in strict mode.");
		th.test(src, new LinterOptions().set("es3", true).set("multistr", true));
		
		th.reset();
		th.addError(3, "Bad escaping of EOL. Use option multistr if needed.");
	    th.addError(4, "Bad escaping of EOL. Use option multistr if needed.");
	    th.addError(9, "Unclosed string.");
	    th.addError(10, "Unclosed string.");
	    th.addError(14, "Bad escaping of EOL. Use option multistr if needed.");
	    th.addError(15, "Unclosed string.");
	    th.addError(25, "Octal literals are not allowed in strict mode.");
	    th.addError(29, "Bad escaping of EOL. Use option multistr if needed.");
		th.test(src, new LinterOptions().set("es3", true));
	}
	
	/**
	 * Test the `quotmark` option
	 * quotmark   quotation mark or true (=ensure consistency)
	 */
	@Test
	public void testQuotes()
	{
		String src = th.readFile("src/test/resources/fixtures/quotes.js");
		String src2 = th.readFile("src/test/resources/fixtures/quotes2.js");
		
		th.test(src, new LinterOptions().set("es3", true));
		
		th.addError(3, "Mixed double and single quotes.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", true));
		
		th.reset();
		th.addError(3, "Strings must use singlequote.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", "single"));
		
		th.reset();
		th.addError(2, "Strings must use doublequote.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", "double"));
		
		// test multiple runs (must have the same result)
		th.reset();
		th.addError(3, "Mixed double and single quotes.");
		th.test(src, new LinterOptions().set("es3", true).set("quotmark", true));
		th.test(src2, new LinterOptions().set("es3", true).set("quotmark", true));
	}
	
	// Test the `quotmark` option when defined as a JSHint comment.
	@Test
	public void testQuotesInline()
	{
		th.addError(6, "Strings must use doublequote.");
	    th.addError(14, "Strings must use singlequote.");
	    th.addError(21, "Mixed double and single quotes.");
	    th.addError(32, "Bad option value.");
		th.test(th.readFile("src/test/resources/fixtures/quotes3.js"));
	}
	
	// Test the `quotmark` option along with TemplateLiterals.
	@Test
	public void testQuotesAndTemplateLiterals()
	{
		String src = th.readFile("src/test/resources/fixtures/quotes4.js");
		
		// Without esnext
		th.addError(2, "'template literal syntax' is only available in ES6 (use 'esversion: 6').");
		th.test(src);
		
		// With esnext
		th.reset();
		th.test(src, new LinterOptions().set("esnext", true));
		
		// With esnext and single quotemark
		th.reset();
		th.test(src, new LinterOptions().set("esnext", true).set("quotmark", "single"));
		
		// With esnext and double quotemark
		th.reset();
		th.addError(1, "Strings must use doublequote.");
		th.test(src, new LinterOptions().set("esnext", true).set("quotmark", "double"));
	}
	
	@Test(groups = {"scope"})
	public void testScopeBasic()
	{
		String src = th.readFile("src/test/resources/fixtures/scope.js");
		
		th.addError(11, "'j' used out of scope."); // 3x
	    th.addError(12, "'x' used out of scope.");
	    th.addError(20, "'aa' used out of scope.");
	    th.addError(27, "'bb' used out of scope.");
	    th.addError(37, "'cc' is not defined.");
	    th.addError(42, "'bb' is not defined.");
	    th.addError(53, "'xx' used out of scope.");
	    th.addError(54, "'yy' used out of scope.");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.reset();
		th.addError(37, "'cc' is not defined.");
	    th.addError(42, "'bb' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("funcscope", true));
	}
	
	@Test(groups = {"scope"})
	public void testScopeCrossBlocks()
	{
		String code = th.readFile("src/test/resources/fixtures/scope-cross-blocks.js");
		
		th.addError(3, "'topBlockBefore' used out of scope.");
	    th.addError(4, "'nestedBlockBefore' used out of scope.");
	    th.addError(11, "'nestedBlockBefore' used out of scope.");
	    th.addError(27, "'nestedBlockAfter' used out of scope.");
	    th.addError(32, "'nestedBlockAfter' used out of scope.");
	    th.addError(33, "'topBlockAfter' used out of scope.");
	    th.test(code);
	    
	    th.reset();
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
	public void testEsnext()
	{
		String src = th.readFile("src/test/resources/fixtures/const.js");
		
		String[] code = {
			"const myConst = true;",
			"const foo = 9;",
			"var myConst = function (test) { };",
			"foo = \"hello world\";",
			"var a = { get x() {} };"
		};
		
		th.addError(21, "const 'immutable4' is initialized to 'undefined'.");
		th.test(src, new LinterOptions().set("esnext", true));
		th.test(src, new LinterOptions().set("moz", true));
		
		th.reset();
		th.addError(3, "'myConst' has already been declared.");
	    th.addError(4, "Attempting to override 'foo' which is a constant.");
	    th.test(code, new LinterOptions().set("esnext", true));
	    th.test(code, new LinterOptions().set("moz", true));
	}
	
	// The `moz` option should not preclude ES6
	@Test
	public void testMozAndEs6()
	{
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
	public void testMaxlen()
	{
		String src = th.readFile("src/test/resources/fixtures/maxlen.js");
		
		th.addError(3, "Line is too long.");
	    th.addError(4, "Line is too long.");
	    th.addError(5, "Line is too long.");
	    th.addError(6, "Line is too long.");
	    // line 7 and more are exceptions and won't trigger the error
		th.test(src, new LinterOptions().set("es3", true).set("maxlen", 23));
	}
	
	/**
	 * Tests the `laxcomma` option
	 */
	@Test
	public void testLaxcomma()
	{
		String src = th.readFile("src/test/resources/fixtures/laxcomma.js");
		
		// All errors.
		th.addError(1, "Misleading line break before ','; readers may interpret this as an expression boundary.");
	    th.addError(2, "Comma warnings can be turned off with 'laxcomma'.");
	    th.addError(2, "Misleading line break before ','; readers may interpret this as an expression boundary.");
	    th.addError(6, "Misleading line break before ','; readers may interpret this as an expression boundary.");
	    th.addError(10, "Misleading line break before '&&'; readers may interpret this as an expression boundary.");
	    th.addError(15, "Misleading line break before '?'; readers may interpret this as an expression boundary.");
		th.test(src, new LinterOptions().set("es3", true));
		
		// Allows bad line breaking, but not on commas.
		th.reset();
		th.addError(1, "Misleading line break before ','; readers may interpret this as an expression boundary.");
	    th.addError(2, "Comma warnings can be turned off with 'laxcomma'.");
	    th.addError(2, "Misleading line break before ','; readers may interpret this as an expression boundary.");
	    th.addError(6, "Misleading line break before ','; readers may interpret this as an expression boundary.");
	    th.test(src, new LinterOptions().set("es3", true).set("laxbreak", true));
	    
	    // Allows comma-first style but warns on bad line breaking
	    th.reset();
	    th.addError(10, "Misleading line break before '&&'; readers may interpret this as an expression boundary.");
	    th.addError(15, "Misleading line break before '?'; readers may interpret this as an expression boundary.");
	    th.test(src, new LinterOptions().set("es3", true).set("laxcomma", true));
	    
	    // No errors if both laxbreak and laxcomma are turned on
	    th.reset();
	    th.test(src, new LinterOptions().set("es3", true).set("laxbreak", true).set("laxcomma", true));
	}
	
	/**
	 * Tests the `browser` option
	 */
	@Test
	public void testBrowser()
	{
		String src = th.readFile("src/test/resources/fixtures/browser.js");
		
		th.addError(2, "'atob' is not defined.");
	    th.addError(3, "'btoa' is not defined.");
	    th.addError(6, "'DOMParser' is not defined.");
	    th.addError(10, "'XMLSerializer' is not defined.");
	    th.addError(14, "'NodeFilter' is not defined.");
	    th.addError(15, "'Node' is not defined.");
	    th.addError(18, "'MutationObserver' is not defined.");
	    th.addError(21, "'SVGElement' is not defined.");
	    th.addError(24, "'Comment' is not defined.");
	    th.addError(25, "'DocumentFragment' is not defined.");
	    th.addError(26, "'Range' is not defined.");
	    th.addError(27, "'Text' is not defined.");
	    th.addError(31, "'document' is not defined.");
	    th.addError(32, "'fetch' is not defined.");
	    th.addError(35, "'URL' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("undef", true));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("browser", true).set("undef", true));
	}
	
	@Test
	public void testUnnecessarySemicolon()
	{
		String[] code = {
			"function foo() {",
			"    var a;;",
			"}"
		};
		
		th.addError(2, "Unnecessary semicolon.");
		th.test(code, new LinterOptions().set("es3", true));
	}
	
	@Test
	public void testBlacklist()
	{
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
		th.addError(15, "'Node' is not defined.");
		th.test(src, new LinterOptions().set("undef", true).set("browser", true).addPredefined("-Node", false));
		
		// disallow Node and NodeFilter in a predef Array
		th.reset();
		th.addError(14, "'NodeFilter' is not defined.");
		th.addError(15, "'Node' is not defined.");
		th.test(src, new LinterOptions().set("undef", true).set("browser", true).addPredefineds("-Node", "-NodeFilter"));
		
		th.reset();
		th.addError(3, "'event' is not defined.");
	    th.addError(4, "'foo' is not defined.");
	    th.addError(5, "'btoa' is not defined.");
		th.test(code, new LinterOptions().set("es3", true).set("undef", true));
	};
	
	/**
	 * Tests the `maxstatements` option
	 */
	@Test
	public void testMaxstatements()
	{
		String src = th.readFile("src/test/resources/fixtures/max-statements-per-function.js");
		
		th.addError(1, "This function has too many statements. (8)");
		th.test(src, new LinterOptions().set("es3", true).set("maxstatements", 7));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("maxstatements", 8));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true));
	};
	
	/**
	 * Tests the `maxdepth` option
	 */
	@Test
	public void testMaxdepth()
	{
		String src = th.readFile("src/test/resources/fixtures/max-nested-block-depth-per-function.js");
		
		th.addError(5, "Blocks are nested too deeply. (2)");
	    th.addError(14, "Blocks are nested too deeply. (2)");
		th.test(src, new LinterOptions().set("es3", true).set("maxdepth", 1));
		
		th.reset();
		th.addError(9, "Blocks are nested too deeply. (3)");
		th.test(src, new LinterOptions().set("es3", true).set("maxdepth", 2));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("maxdepth", 3));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true));
	};
	
	/**
	 * Tests the `maxparams` option
	 */
	@Test
	public void testMaxparams()
	{
		String src = th.readFile("src/test/resources/fixtures/max-parameters-per-function.js");
		
		th.addError(4, "This function has too many parameters. (3)");
		th.addError(10, "This function has too many parameters. (3)");
	    th.addError(16, "This function has too many parameters. (3)");
		th.test(src, new LinterOptions().set("esnext", true).set("maxparams", 2));
		
		th.reset();
		th.test(src, new LinterOptions().set("esnext", true).set("maxparams", 3));
		
		th.reset();
		th.addError(4, "This function has too many parameters. (3)");
		th.addError(8, "This function has too many parameters. (1)");
	    th.addError(9, "This function has too many parameters. (1)");
	    th.addError(10, "This function has too many parameters. (3)");
	    th.addError(11, "This function has too many parameters. (1)");
	    th.addError(13, "This function has too many parameters. (2)");
	    th.addError(16, "This function has too many parameters. (3)");
		th.test(src, new LinterOptions().set("esnext", true).set("maxparams", 0));
		
		th.reset();
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
	public void testMaxcomplexity()
	{
		String src = th.readFile("src/test/resources/fixtures/max-cyclomatic-complexity-per-function.js");
		
		th.addError(8, "This function's cyclomatic complexity is too high. (2)");
	    th.addError(15, "This function's cyclomatic complexity is too high. (2)");
	    th.addError(25, "This function's cyclomatic complexity is too high. (2)");
	    th.addError(47, "This function's cyclomatic complexity is too high. (8)");
	    th.addError(76, "This function's cyclomatic complexity is too high. (2)");
	    th.addError(80, "This function's cyclomatic complexity is too high. (2)");
		th.test(src, new LinterOptions().set("es3", true).set("maxcomplexity", 1));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true).set("maxcomplexity", 8));
		
		th.reset();
		th.test(src, new LinterOptions().set("es3", true));
	};
	
	// Metrics output per function.
	@Test
	public void testFnmetrics()
	{
		JSHint jshint = new JSHint();
		
		jshint.lint(new String[]{
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
	public void testIgnored()
	{
		String src = th.readFile("src/test/resources/fixtures/ignored.js");
		
		th.addError(4, "A trailing decimal point can be confused with a dot: '12.'.");
	    th.addError(12, "Missing semicolon.");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.reset();
		th.addError(12, "Missing semicolon.");
		th.test(src, new LinterOptions().set("es3", true).set("-W047", true));
	};
	
	/**
	 * Tests ignored warnings being unignored.
	 */
	@Test
	public void testUnignored()
	{
		String src = th.readFile("src/test/resources/fixtures/unignored.js");
		
		th.addError(5, "A leading decimal point can be confused with a dot: '.12'.");
		th.test(src, new LinterOptions().set("es3", true));
	};
	
	/**
	 * Tests that the W117 and undef can be toggled per line.
	 */
	@Test
	public void testPerLineUndefW117()
	{
		String src = th.readFile("src/test/resources/fixtures/ignore-w117.js");
		
		th.addError(5, "'c' is not defined.");
		th.addError(11, "'c' is not defined.");
		th.addError(15, "'c' is not defined.");
		
		th.test(src, new LinterOptions().set("undef", true));
	}
	
	/**
	 * Tests the `freeze` option -- Warn if native object prototype is assigned to.
	 */
	@Test
	public void testFreeze()
	{
		String src = th.readFile("src/test/resources/fixtures/nativeobject.js");
		
		th.addError(3, "Extending prototype of native object: 'Array'.");
	    th.addError(13, "Extending prototype of native object: 'Boolean'.");
		th.test(src, new LinterOptions().set("freeze", true).set("esversion", 6));
		
		th.reset();
		th.test(src, new LinterOptions().set("esversion", 6));
	};
	
	@Test
	public void testNonbsp()
	{
		String src = th.readFile("src/test/resources/fixtures/nbsp.js");
		
		th.test(src, new LinterOptions().set("sub", true));
		
		th.addError(1, "This line contains non-breaking spaces: http://jshint.com/docs/options/#nonbsp");
		th.test(src, new LinterOptions().set("nonbsp", true).set("sub", true));
	};
	
	/** Option `nocomma` disallows the use of comma operator. */
	@Test
	public void testNocomma()
	{
		// By default allow comma operator
		th.test("return 2, 5;", new LinterOptions());
		
		th.addError(1, "Unexpected use of a comma operator.");
		th.test("return 2, 5;", new LinterOptions().set("nocomma", true));
		
		th.test("(2, 5);", new LinterOptions().set("expr", true).set("nocomma", true));
		
		th.reset();
		th.test("return { a: 2, b: [1, 2] };", new LinterOptions().set("nocomma", true));
		
		th.test("for(;;) { return; }", new LinterOptions().set("nocomma", true));
		
		th.test("return function(a, b) {};", new LinterOptions().set("nocomma", true));
		
		th.test("return (a, b) => a;", new LinterOptions().set("esnext", true).set("nocomma", true));
		
		th.test("var [a, b] = [1, 2];", new LinterOptions().set("esnext", true).set("nocomma", true));
		
		th.test("var {a, b} = {a:1, b:2};", new LinterOptions().set("esnext", true).set("nocomma", true));
	};
	
	@Test
	public void testEnforceall()
	{
		String src = th.readFile("src/test/resources/fixtures/enforceall.js");
		
		// Throws errors not normally on be default
		th.addError(1, "This line contains non-breaking spaces: http://jshint.com/docs/options/#nonbsp");
		th.addError(1, "['key'] is better written in dot notation.");
	    th.addError(1, "'obj' is not defined.");
	    th.addError(1, "Missing semicolon.");
		th.test(src, new LinterOptions().set("enforceall", true));
		
		// Can override default hard
		th.reset();
		th.test(src, new LinterOptions().set("enforceall", true).set("nonbsp", false).set("bitwise", false).set("sub", true).set("undef", false).set("unused", false).set("asi", true));
	};
	
	@Test
	public void testRemoveGlobal()
	{
		String src = th.readFile("src/test/resources/fixtures/removeglobals.js");
		
		th.addError(1, "'JSON' is not defined.");
		th.test(src, new LinterOptions().set("undef", true).addPredefineds("-JSON", "myglobal"));
	};
	
	@Test
	public void testIgnoreDelimiters()
	{
		String src = th.readFile("src/test/resources/fixtures/ignoreDelimiters.js");
		
		// make sure line/column are still reported properly
		th.addError(6, 37, "Missing semicolon.");
		th.test(src, new LinterOptions()
			.addIgnoreDelimiter("<%=", "%>")
			.addIgnoreDelimiter("<%", "%>")
			.addIgnoreDelimiter("<?php", "?>")
			// make sure single tokens are ignored
			.addIgnoreDelimiter("foo", null)
			.addIgnoreDelimiter(null, "bar")
		);
	};
	
	@Test
	public void testEsnextPredefs()
	{
		String[] code = {
			"/* global alert: true */",
		    "var mySym = Symbol(\"name\");",
		    "var myBadSym = new Symbol(\"name\");",
		    "alert(Reflect);"
		};
		
		th.addError(3, "Do not use Symbol as a constructor.");
		th.test(code, new LinterOptions().set("esnext", true).set("undef", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsLoneIdentifier()
	{
		String[] code = {
			"if ((a)) {}",
			"if ((a) + b + c) {}",
			"if (a + (b) + c) {}",
			"if (a + b + (c)) {}"
		};
		
		th.addError(1, "Unnecessary grouping operator.");
		th.addError(2, "Unnecessary grouping operator.");
		th.addError(3, "Unnecessary grouping operator.");
		th.addError(4, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsNeighborless()
	{
		String[] code = {
			"if ((a instanceof b)) {}",
			"if ((a in b)) {}",
			"if ((a + b)) {}"
		};
		
		th.addError(1, "Unnecessary grouping operator.");
		th.addError(2, "Unnecessary grouping operator.");
		th.addError(3, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};
	
	@Test(groups = {"singleGroups", "bindingPower"})
	public void testSingleGroupsBindingPowerSingleExpr()
	{
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
		
	    th.addError(19, "Unnecessary grouping operator.");
	    th.addError(20, "Unnecessary grouping operator.");
	    th.addError(21, "Unnecessary grouping operator.");
	    th.addError(22, "Unnecessary grouping operator.");
	    th.addError(23, "Unnecessary grouping operator.");
	    th.addError(24, "Unnecessary grouping operator.");
	    th.addError(25, "Unnecessary grouping operator.");
	    th.addError(26, "Unnecessary grouping operator.");
	    th.addError(27, "Unnecessary grouping operator.");
	    th.addError(28, "Unnecessary grouping operator.");
	    th.addError(29, "Unnecessary grouping operator.");
	    th.addError(30, "Unnecessary grouping operator.");
	    th.addError(31, "Unnecessary grouping operator.");
	    th.addError(32, "Unnecessary grouping operator.");
	    th.addError(33, "Unnecessary grouping operator.");
	    th.addError(34, "Unnecessary grouping operator.");
	    th.addError(35, "Unnecessary grouping operator.");
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
		
		th.reset();
		th.addError(6, "Unnecessary grouping operator.");
	    th.addError(7, "Unnecessary grouping operator.");
	    th.addError(8, "Unnecessary grouping operator.");
	    th.addError(9, "Unnecessary grouping operator.");
	    th.addError(10, "Unnecessary grouping operator.");
	    th.addError(11, "Unnecessary grouping operator.");
	    th.test(code, new LinterOptions().set("singleGroups", true).set("esversion", 6).set("supernew", true));
	};
	
	@Test(groups = {"singleGroups", "bindingPower"})
	public void testSingleGroupsBindingPowerMultiExpr()
	{
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
		
		th.addError(6, "Unnecessary grouping operator.");
	    th.addError(7, "Unnecessary grouping operator.");
	    th.addError(8, "Unnecessary grouping operator.");
	    th.addError(9, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsMultiExpr()
	{
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
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsConcatenation()
	{
		String[] code = {
			"var a = b + (c + d);",
		    "var e = (f + g) + h;"
		};
		
		th.addError(2, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsFunctionExpression()
	{
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
		
	    th.addError(19, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("asi", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsGeneratorExpression()
	{
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
		
	    th.addError(19, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("asi", true).set("esnext", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsYield()
	{
		th.test(new String[]{
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
		
		th.test(new String[] {
			"function* g() {",
		      "  (yield).x = 0;",
		      "  x = (yield) ? 0 : 0;",
		      "  x = (yield 0) ? 0 : 0;",
		      "  x = (yield) / 0;",
		      "}"
		}, new LinterOptions().set("singleGroups", true).set("esversion", 6));
		
		th.addError(2, "Unnecessary grouping operator.");
		th.addError(3, "Unnecessary grouping operator.");
		th.addError(4, "Unnecessary grouping operator.");
		th.addError(5, "Unnecessary grouping operator.");
		th.addError(6, "Unnecessary grouping operator.");
		th.addError(7, "Unnecessary grouping operator.");
		th.addError(8, "Unnecessary grouping operator.");
		th.addError(9, "Unnecessary grouping operator.");
		th.addError(10, "Unnecessary grouping operator.");
		th.addError(11, "Unnecessary grouping operator.");
		th.addError(12, "Unnecessary grouping operator.");
		th.addError(13, "Unnecessary grouping operator.");
		th.addError(14, "Unnecessary grouping operator.");
		th.addError(15, "Unnecessary grouping operator.");
		th.addError(16, "Unnecessary grouping operator.");
		th.addError(17, "Unnecessary grouping operator.");
		th.addError(18, "Unnecessary grouping operator.");
		th.addError(19, "Unnecessary grouping operator.");
		th.addError(20, "Unnecessary grouping operator.");
		th.addError(21, "Unnecessary grouping operator.");
		th.addError(22, "Unnecessary grouping operator.");
		th.addError(23, "Unnecessary grouping operator.");
		th.addError(24, "Unnecessary grouping operator.");
		th.addError(25, "Unnecessary grouping operator.");
		th.addError(26, "Unnecessary grouping operator.");
		th.addError(27, "Unnecessary grouping operator.");
		th.addError(28, "Unnecessary grouping operator.");
		th.addError(29, "Unnecessary grouping operator.");
		th.addError(30, "Unnecessary grouping operator.");
		th.addError(31, "Unnecessary grouping operator.");
		th.addError(32, "Unnecessary grouping operator.");
		th.addError(33, "Unnecessary grouping operator.");
		th.addError(34, "Unnecessary grouping operator.");
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
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsArrowFunctions()
	{
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
		
		th.addError(15, "Unnecessary grouping operator.");
	    th.addError(16, "Unnecessary grouping operator.");
	    th.addError(17, "Unnecessary grouping operator.");
	    th.addError(18, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true).set("esnext", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsObjectLiterals()
	{
		String[] code = {
			"({}).method();",
		    "if(true) {} ({}).method();",
		    "g(); ({}).method();",

		    // Invalid forms
		    "var a = ({}).method();",
		    "if (({}).method()) {}",
		    "var b = { a: ({}).method() };"
		};
		
		th.addError(4, "Unnecessary grouping operator.");
	    th.addError(5, "Unnecessary grouping operator.");
	    th.addError(6, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsNewLine()
	{
		String[] code = {
			"function x() {",
		    "  return f",
		    "    ();",
		    "}",
		    "x({ f: null });"
		};
		
		th.test(code, new LinterOptions().set("singleGroups", true));
	};
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsLineNumber()
	{
		String[] code = {
			"var x = (",
			"  1",
			")",
			";"
		};
		
		th.addError(1, "Unnecessary grouping operator.");
		th.test(code, new LinterOptions().set("singleGroups", true));
	}
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsUnary()
	{
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
		
		th.addError(6, "Missing '()' invoking a constructor.");
	    th.addError(7, "Unnecessary grouping operator.");
	    th.addError(8, "Unnecessary grouping operator.");
	    th.addError(9, "Unnecessary grouping operator.");
	    th.addError(10, "Unnecessary grouping operator.");
	    th.addError(11, "Unnecessary grouping operator.");
	    th.addError(12, "Unnecessary grouping operator.");
	    th.addError(12, "Missing '()' invoking a constructor.");
	    th.test(code, new LinterOptions().set("singleGroups", true));
	}
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsNumberLiterals()
	{
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
		
		th.addError(2, "Unnecessary grouping operator.");
	    th.addError(3, "Unnecessary grouping operator.");
	    th.addError(3, "A leading decimal point can be confused with a dot: '.3'.");
	    th.addError(4, "Unnecessary grouping operator.");
	    th.addError(4, "A trailing decimal point can be confused with a dot: '3.'.");
	    th.addError(5, "Unnecessary grouping operator.");
	    th.addError(6, "Unnecessary grouping operator.");
	    th.addError(7, "Unnecessary grouping operator.");
	    th.addError(8, "Unnecessary grouping operator.");
	    th.addError(9, "Unnecessary grouping operator.");
	    th.addError(10, "Unnecessary grouping operator.");
	    th.addError(11, "Unnecessary grouping operator.");
	    th.test(code, new LinterOptions().set("singleGroups", true));
	}
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsPostfix()
	{
		String[] code = {
			"var x;",
		    "(x++).toString();",
		    "(x--).toString();"
		};
		
		th.test(code, new LinterOptions().set("singleGroups", true));
	}
	
	@Test(groups = {"singleGroups"})
	public void testSingleGroupsDestructuringAssign()
	{
		String[] code = {
			// statements
		    "({ x } = { x : 1 });",
		    "([ x ] = [ 1 ]);",
		    // expressions
		    "1, ({ x } = { x : 1 });",
		    "1, ([ x ] = [ 1 ]);"
		};
		
		th.addError(2, "Unnecessary grouping operator.");
	    th.addError(3, "Unnecessary grouping operator.");
	    th.addError(4, "Unnecessary grouping operator.");
	    th.test(code, new LinterOptions().set("esversion", 6).set("singleGroups", true).set("expr", true));
	}
	
	@Test
	public void testElision()
	{
		String[] code = {
			"var a = [1,,2];",
			"var b = [1,,,,2];",
			"var c = [1,2,];",
			"var d = [,1,2];",
			"var e = [,,1,2];"
		};
		
		th.addError(1, "Empty array elements require elision=true.");
		th.addError(2, "Empty array elements require elision=true.");
		th.addError(4, "Empty array elements require elision=true.");
		th.addError(5, "Empty array elements require elision=true.");
		th.test(code, new LinterOptions().set("elision", false).set("es3", false));
		
		th.reset();
		th.addError(1, "Extra comma. (it breaks older versions of IE)");
		th.addError(2, "Extra comma. (it breaks older versions of IE)");
		th.addError(2, "Extra comma. (it breaks older versions of IE)");
		th.addError(2, "Extra comma. (it breaks older versions of IE)");
		th.addError(3, "Extra comma. (it breaks older versions of IE)");
		th.addError(4, "Extra comma. (it breaks older versions of IE)");
		th.addError(5, "Extra comma. (it breaks older versions of IE)");
		th.addError(5, "Extra comma. (it breaks older versions of IE)");
		th.test(code, new LinterOptions().set("elision", false).set("es3", true));
		
		th.reset();
		th.test(code, new LinterOptions().set("elision", true).set("es3", false));
		
		th.reset();
		th.addError(3, "Extra comma. (it breaks older versions of IE)");
		th.test(code, new LinterOptions().set("elision", true).set("es3", true));
	};
	
	@Test
	public void testBadInlineOptionValue()
	{
		String[] src = {"/* jshint bitwise:batcrazy */"};
		
		th.addError(1, "Bad option value.");
		th.test(src);
	};
	
	@Test
	public void testFutureHostile()
	{
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
		    "var Uint8ClampledArray = function() {};",
		    "var Float32Array = function() {};",
		    "var Float64Array = function() {};"
		};
		
		th.addError(1, "'JSON' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(2, "'Map' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(3, "'Promise' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(4, "'Proxy' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(5, "'Reflect' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(6, "'Set' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(7, "'Symbol' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(8, "'WeakMap' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(9, "'WeakSet' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(10, "'ArrayBuffer' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(11, "'DataView' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(12, "'Int8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(13, "'Int16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(14, "'Int32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(15, "'Uint8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(16, "'Uint16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(17, "'Uint32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(18, "'Uint8ClampledArray' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(19, "'Float32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(20, "'Float64Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
		th.test(code, new LinterOptions().set("es3", true).set("es5", false).set("futurehostile", false));
		
		th.reset();
		th.test(code, new LinterOptions().set("es3", true).set("es5", false));
		
		th.reset();
		th.addError(1, "Redefinition of 'JSON'.");
	    th.addError(2, "'Map' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(3, "'Promise' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(4, "'Proxy' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(5, "'Reflect' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(6, "'Set' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(7, "'Symbol' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(8, "'WeakMap' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(9, "'WeakSet' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(10, "'ArrayBuffer' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(11, "'DataView' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(12, "'Int8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(13, "'Int16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(14, "'Int32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(15, "'Uint8Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(16, "'Uint16Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(17, "'Uint32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(18, "'Uint8ClampledArray' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(19, "'Float32Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.addError(20, "'Float64Array' is defined in a future version of JavaScript. Use a different variable name to avoid migration issues.");
	    th.test(code, new LinterOptions().set("futurehostile", false));
	    
	    th.reset();
	    th.addError(1, "Redefinition of 'JSON'.");
		th.test(code, new LinterOptions());
		
		th.reset();
		th.test(code, new LinterOptions().addPredefineds("-JSON"));
		
		th.reset();
	    th.addError(1, "Redefinition of 'JSON'.");
	    th.addError(2, "Redefinition of 'Map'.");
	    th.addError(3, "Redefinition of 'Promise'.");
	    th.addError(4, "Redefinition of 'Proxy'.");
	    th.addError(5, "Redefinition of 'Reflect'.");
	    th.addError(6, "Redefinition of 'Set'.");
	    th.addError(7, "Redefinition of 'Symbol'.");
	    th.addError(8, "Redefinition of 'WeakMap'.");
	    th.addError(9, "Redefinition of 'WeakSet'.");
	    th.addError(10, "Redefinition of 'ArrayBuffer'.");
	    th.addError(11, "Redefinition of 'DataView'.");
	    th.addError(12, "Redefinition of 'Int8Array'.");
	    th.addError(13, "Redefinition of 'Int16Array'.");
	    th.addError(14, "Redefinition of 'Int32Array'.");
	    th.addError(15, "Redefinition of 'Uint8Array'.");
	    th.addError(16, "Redefinition of 'Uint16Array'.");
	    th.addError(17, "Redefinition of 'Uint32Array'.");
	    th.addError(18, "Redefinition of 'Uint8ClampledArray'.");
	    th.addError(19, "Redefinition of 'Float32Array'.");
	    th.addError(20, "Redefinition of 'Float64Array'.");
		th.test(code, new LinterOptions().set("esnext", true).set("futurehostile", false));
		
		th.test(code, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.test(code, new LinterOptions()
			.set("esnext", true)
			.set("futurehostile", false)
			.addPredefineds(
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
		        "-Uint8ClampledArray",
		        "-Float32Array",
		        "-Float64Array"
			)
		);
		
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
		    "let Uint8ClampledArray = function() {};",
		    "let Float32Array = function() {};",
		    "let Float64Array = function() {};"
		};
		
		th.reset();
		th.addError(1, "Redefinition of 'JSON'.");
	    th.addError(2, "Redefinition of 'Map'.");
	    th.addError(3, "Redefinition of 'Promise'.");
	    th.addError(4, "Redefinition of 'Proxy'.");
	    th.addError(5, "Redefinition of 'Reflect'.");
	    th.addError(6, "Redefinition of 'Set'.");
	    th.addError(7, "Redefinition of 'Symbol'.");
	    th.addError(8, "Redefinition of 'WeakMap'.");
	    th.addError(9, "Redefinition of 'WeakSet'.");
	    th.addError(10, "Redefinition of 'ArrayBuffer'.");
	    th.addError(11, "Redefinition of 'DataView'.");
	    th.addError(12, "Redefinition of 'Int8Array'.");
	    th.addError(13, "Redefinition of 'Int16Array'.");
	    th.addError(14, "Redefinition of 'Int32Array'.");
	    th.addError(15, "Redefinition of 'Uint8Array'.");
	    th.addError(16, "Redefinition of 'Uint16Array'.");
	    th.addError(17, "Redefinition of 'Uint32Array'.");
	    th.addError(18, "Redefinition of 'Uint8ClampledArray'.");
	    th.addError(19, "Redefinition of 'Float32Array'.");
	    th.addError(20, "Redefinition of 'Float64Array'.");
	    th.test(code, new LinterOptions().set("esnext", true));
	    
	    th.reset();
		th.test(code, new LinterOptions()
			.set("esnext", true)
			.set("futurehostile", false)
			.addPredefineds(
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
		        "-Uint8ClampledArray",
		        "-Float32Array",
		        "-Float64Array"
		    )
		);
		
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
		    "const Uint8ClampledArray = function() {};",
		    "const Float32Array = function() {};",
		    "const Float64Array = function() {};"
		};
		
		th.reset();
		th.addError(1, "Redefinition of 'JSON'.");
	    th.addError(2, "Redefinition of 'Map'.");
	    th.addError(3, "Redefinition of 'Promise'.");
	    th.addError(4, "Redefinition of 'Proxy'.");
	    th.addError(5, "Redefinition of 'Reflect'.");
	    th.addError(6, "Redefinition of 'Set'.");
	    th.addError(7, "Redefinition of 'Symbol'.");
	    th.addError(8, "Redefinition of 'WeakMap'.");
	    th.addError(9, "Redefinition of 'WeakSet'.");
	    th.addError(10, "Redefinition of 'ArrayBuffer'.");
	    th.addError(11, "Redefinition of 'DataView'.");
	    th.addError(12, "Redefinition of 'Int8Array'.");
	    th.addError(13, "Redefinition of 'Int16Array'.");
	    th.addError(14, "Redefinition of 'Int32Array'.");
	    th.addError(15, "Redefinition of 'Uint8Array'.");
	    th.addError(16, "Redefinition of 'Uint16Array'.");
	    th.addError(17, "Redefinition of 'Uint32Array'.");
	    th.addError(18, "Redefinition of 'Uint8ClampledArray'.");
	    th.addError(19, "Redefinition of 'Float32Array'.");
	    th.addError(20, "Redefinition of 'Float64Array'.");
	    th.test(code, new LinterOptions().set("esnext", true));
	    
	    th.reset();
		th.test(code, new LinterOptions()
			.set("esnext", true)
			.set("futurehostile", false)
			.addPredefineds(
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
		        "-Uint8ClampledArray",
		        "-Float32Array",
		        "-Float64Array"
		    )
		);
	};
	
	@Test
	public void testVarstmt()
	{
		String[] code = {
			"var x;",
			"var y = 5;",
			"var fn = function() {",
			"  var x;",
			"  var y = 5;",
			"};",
			"for (var a in x);"
		};
		
		th.addError(1, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(2, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(3, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(4, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(5, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.addError(7, "`var` declarations are forbidden. Use `let` or `const` instead.");
		th.test(code, new LinterOptions().set("varstmt", true));
	}
	
	@Test(groups = {"module"})
	public void testModuleBehavior()
	{
		String[] code = {
				"var package = 3;",
				"function f() { return this; }"
		};
		
		th.test(code, new LinterOptions());
		
		th.reset();
		th.addError(0, "The 'module' option is only available when linting ECMAScript 6 code.");
		th.addError(1, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(2, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code, new LinterOptions().set("module", true));
		
		th.reset();
		th.addError(1, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(2, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code, new LinterOptions().set("module", true).set("esnext", true));
		
		code = new String[]{
				"/* jshint module: true */",
				"var package = 3;",
				"function f() { return this; }"
		};
		
		th.reset();
		th.addError(1, "The 'module' option is only available when linting ECMAScript 6 code.");
		th.addError(2, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(3, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code);
		
		code[0] = "/* jshint module: true, esnext: true */";
		
		th.reset();
		th.addError(2, "Expected an identifier and instead saw 'package' (a reserved word).");
		th.addError(3, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(code);
	}
	
	@Test(groups = {"module"})
	public void testModuleDeclarationRestrictions()
	{
		th.addError(2, "The 'module' option cannot be set after any executable code.");
		th.test(new String[]{
			"(function() {",
			"  /* jshint module: true */",
			"})();"
		}, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.addError(2, "The 'module' option cannot be set after any executable code.");
		th.test(new String[]{
			"void 0;",
			"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.addError(3, "The 'module' option cannot be set after any executable code.");
		th.test(new String[]{
			"void 0;",
			"// hide",
			"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.addError(1, "The 'module' option cannot be set after any executable code.");
		th.test(new String[]{
			"(function() {})(); /* jshint module: true */"
		}, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.addError(1, "The 'module' option cannot be set after any executable code.");
		th.test(new String[]{
			"(function() { /* jshint module: true */",
			"})();"
		}, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.test(new String[]{
			"/* jshint module: true */ (function() {",
			"})();"
		}, new LinterOptions().set("esnext", true));
		
		th.reset();
		th.addError(1, "The 'module' option cannot be set after any executable code.");
		th.test("Math.abs(/*jshint module: true */4);", new LinterOptions().set("esnext", true));
		
		th.reset();
		th.test(new String[]{
			"// License boilerplate",
			"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));
		
		th.test(new String[]{
			"/**",
			" * License boilerplate",
			" */",
			"  /* jshint module: true */"
		}, new LinterOptions().set("esnext", true));
		
		th.test(new String[]{
			"#!/usr/bin/env node",
			"/* jshint module: true */"
		}, new LinterOptions().set("esnext", true));
		
		th.test(new String[]{
			"/* jshint module:true */",
		    "function bar() {",
		    "  /* jshint validthis:true */",
		    "}"
		}, new LinterOptions().set("esnext", true));
	}
	
	@Test(groups = {"module"})
	public void testModuleNewcap()
	{
		String[] code = {
			"var ctor = function() {};",
		    "var Ctor = function() {};",
		    "var c1 = new ctor();",
		    "var c2 = Ctor();"
		};
		
		th.test(code, new LinterOptions().set("esversion", 6).set("module", true));
	}
	
	@Test(groups = {"module"})
	public void testModuleAwait()
	{
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
		
		th.addError(1, "Expected an identifier and instead saw 'await' (a reserved word).");
		th.test("var await;", new LinterOptions().set("esversion", 6).set("module", true));
		th.test("function await() {}", new LinterOptions().set("esversion", 6).set("module", true));
		th.test("await: while (false) {}", new LinterOptions().set("esversion", 6).set("module", true));
		
		th.reset();
		th.test(new String[] {
			"void { await: null };",
		    "void {}.await;"
		}, new LinterOptions().set("esversion", 6).set("module", true));
	}
	
	@Test
	public void testEsversion()
	{
		String[] code = {
			"// jshint esversion: 3",
		    "// jshint esversion: 4",
		    "// jshint esversion: 5",
		    "// jshint esversion: 6",
		    "// jshint esversion: 2015"
		};
		
		th.addError(2, "Bad option value.");
		th.test(code);
		
		String[] es5code = {
			"var a = {",
		    "  get b() {}",
		    "};"
		};
		
		th.reset();
		th.addError(2, "get/set are ES5 features.");
		th.test(es5code, new LinterOptions().set("esversion", 3));
		
		th.reset();
		th.test(es5code); // esversion: 5 (default)
		th.test(es5code, new LinterOptions().set("esversion", 6));
		
		String[] es6code = {
			"var a = {",
		    "  ['b']: 1",
		    "};",
		    "var b = () => {};"
		};
		
		th.reset();
		th.addError(2, "'computed property names' is only available in ES6 (use 'esversion: 6').");
		th.addError(4, "'arrow function syntax (=>)' is only available in ES6 (use 'esversion: 6').");
		th.test(es6code, new LinterOptions().set("esversion", 3));
		th.test(es6code); // esversion: 5 (default)
		
		th.reset();
		th.test(es6code, new LinterOptions().set("esversion", 6));
		th.test(es5code, new LinterOptions().set("esversion", 2015));
		
		// Array comprehensions aren't defined in ECMAScript 6,
		// but they can be enabled using the `esnext` option
		String[] arrayComprehension = {
			"var a = [ 1, 2, 3 ];",
		    "var b = [ for (i of a) i ];"
		};
		
		th.addError(2, "'array comprehension' is only available in Mozilla JavaScript extensions (use moz option).");
		th.test(arrayComprehension, new LinterOptions().set("esversion", 6));
		
		th.reset();
		th.test(arrayComprehension, new LinterOptions().set("esnext", true));
		
		// JSHINT_TODO: Remove in JSHint 3
		th.addError(0, "Incompatible values for the 'esversion' and 'es3' linting options. (0% scanned).");
		th.test(es6code, new LinterOptions().set("esversion", 6).set("es3", true));
		
		// JSHINT_TODO: Remove in JSHint 3
		th.reset();
		th.addError(0, "Incompatible values for the 'esversion' and 'es5' linting options. (0% scanned).");
		th.test(es6code, new LinterOptions().set("esversion", 6).set("es5", true));
		
		// JSHINT_TODO: Remove in JSHint 3
		th.reset();
		th.addError(0, "Incompatible values for the 'esversion' and 'esnext' linting options. (0% scanned).");
		th.test(es6code, new LinterOptions().set("esversion", 3).set("esnext", true));
		
		th.reset();
		th.addError(2, "Incompatible values for the 'esversion' and 'es3' linting options. (66% scanned).");
		th.test(new String[]{"", "// jshint esversion: 3", ""}, new LinterOptions().set("es3", true));
		th.test(new String[]{"", "// jshint es3: true", ""}, new LinterOptions().set("esversion", 3));
		
		th.reset();
		th.addError(3, "'class' is available in ES6 (use 'esversion: 6') or Mozilla JS extensions (use moz).");
		th.test(new String[]{"", "// jshint esversion: 3", "class A {}"}, new LinterOptions().set("esversion", 3));
		th.test(new String[]{"", "// jshint esversion: 3", "class A {}"}, new LinterOptions().set("esversion", 6));
		
		th.reset();
		th.test(new String[]{"", "// jshint esversion: 6", "class A {}"}, new LinterOptions().set("esversion", 6));
		
		String[] code2 = ArrayUtils.addAll(new String[] { // JSHINT_TODO: Remove in JSHint 3
			"/* jshint esversion: 3, esnext: true */"
		}, es6code);
		
		// JSHINT_TODO: Remove in JSHint 3
		th.addError(1, "Incompatible values for the 'esversion' and 'esnext' linting options. (20% scanned).");
	    th.test(code2);
	    
	    String[] code3 = {
	    	"var someCode;",
	        "// jshint esversion: 3"
	    };
	    
	    th.reset();
	    th.addError(2, "The 'esversion' option cannot be set after any executable code.");
	    th.test(code3);
	    
	    String[] code4 = {
	    	"#!/usr/bin/env node",
	        "/**",
	        " * License",
	        " */",
	        "// jshint esversion: 3",
	        "// jshint esversion: 6"
	    };
	    
	    th.reset();
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
	    
	    th.addError(4, "get/set are ES5 features.");
	    th.test(code5);
	}
	
	// Option `trailingcomma` requires a comma after each element in an array or
	// object literal.
	@Test
	public void testTrailingcomma()
	{
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
		
		th.addError(2, "Missing comma.");
	    th.addError(4, "Missing comma.");
	    th.addError(7, "Missing comma.");
	    th.addError(9, "Missing comma.");
	    th.test(code, new LinterOptions().set("trailingcomma", true).set("esversion", 6));
	    th.test(code, new LinterOptions().set("trailingcomma", true));
	    
	    th.reset();
	    th.addError(3, "Extra comma. (it breaks older versions of IE)");
	    th.addError(5, "Extra comma. (it breaks older versions of IE)");
	    th.addError(8, "Extra comma. (it breaks older versions of IE)");
	    th.addError(10, "Extra comma. (it breaks older versions of IE)");
	    th.test(code, new LinterOptions().set("trailingcomma", true).set("es3", true));
	}
}