package org.jshint.test.regression;

import org.jshint.LinterGlobals;
import org.jshint.LinterOptions;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestThirdparty extends Assert
{
	private TestHelper th = new TestHelper();
	
	@BeforeMethod
	private void setupBeforeMethod()
	{
		th.newTest();
	}
	
	@Test
	public void testBackbone_0_5_3()
	{	
		String src = th.readFile("src/test/resources/libs/backbone.js");
		
		th.addError(32, 13, "Unnecessary grouping operator.");
	    th.addError(784, 31, "Unnecessary grouping operator.");
	    th.addError(864, 28, "Unnecessary grouping operator.");
	    th.addError(685, 60, "Missing '()' invoking a constructor.");
		th.test(src, new LinterOptions().set("expr", true).set("eqnull", true).set("boss", true).set("regexdash", true).set("singleGroups", true));
	}
	
	@Test
	public void testJQuery_1_7()
	{	
		String src = th.readFile("src/test/resources/libs/jquery-1.7.js");
		LinterGlobals globals = new LinterGlobals(false, "DOMParser", "ActiveXObject", "define");
		
		th.addError(551, 19, "'name' is defined but never used.");
	    th.addError(1044, 17, "'actual' is defined but never used.");
	    th.addError(1312, 13, "'pCount' is defined but never used.");
	    th.addError(1369, 9, "'events' is defined but never used.");
	    th.addError(1607, 38, "'table' is defined but never used.");
	    th.addError(1710, 13, "'internalKey' is defined but never used.");
	    th.addError(1813, 13, "'internalKey' is defined but never used.");
	    th.addError(2818, 24, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2822, 39, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2859, 5, "'rnamespaces' is defined but never used.");
	    th.addError(2861, 5, "'rperiod' is defined but never used.");
	    th.addError(2862, 5, "'rspaces' is defined but never used.");
	    th.addError(2863, 5, "'rescape' is defined but never used.");
	    th.addError(2900, 26, "'quick' is defined but never used.");
	    th.addError(3269, 78, "'related' is defined but never used.");
	    th.addError(3592, 17, "'selector' is defined but never used.");
	    th.addError(4465, 31, "'curLoop' is defined but never used.");
	    th.addError(4560, 33, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(4694, 35, "'cache' is defined but never used.");
	    th.addError(4712, 32, "Expected a 'break' statement before 'case'.");
	    th.addError(4843, 77, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5635, 38, "'elem' is defined but never used.");
	    th.addError(5675, 54, "'i' is defined but never used.");
	    th.addError(5691, 50, "'i' is defined but never used.");
	    th.addError(7141, 53, "'i' is defined but never used.");
	    th.addError(6061, 22, "'cur' is defined but never used.");
		th.test(src, new LinterOptions().set("undef", true).set("unused", true), globals);
	}
	
	@Test
	public void testPrototype_1_7()
	{	
		String src = th.readFile("src/test/resources/libs/prototype-17.js");
		
		th.addError(22, 6, "Missing semicolon.");
	    th.addError(94, 25, "Unnecessary semicolon.");
	    th.addError(110, 29, "Missing '()' invoking a constructor.");
	    th.addError(253, 20, "'i' is already defined.");
	    th.addError(253, 27, "'length' is already defined.");
	    th.addError(260, 20, "'i' is already defined.");
	    th.addError(260, 27, "'length' is already defined.");
	    th.addError(261, 17, "'key' is already defined.");
	    th.addError(261, 32, "'str' is already defined.");
	    th.addError(319, 5, "Reassignment of 'isArray', which is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(392, 6, "Missing semicolon.");
	    th.addError(400, 6, "Missing semicolon.");
	    th.addError(409, 6, "Missing semicolon.");
	    th.addError(430, 6, "Missing semicolon.");
	    th.addError(451, 4, "Missing semicolon.");
	    th.addError(1215, 10, "Missing semicolon.");
	    th.addError(1224, 2, "Unnecessary semicolon.");
	    th.addError(1916, 2, "Missing semicolon.");
	    th.addError(2034, 40, "Missing semicolon.");
	    th.addError(2662, 6, "Missing semicolon.");
	    th.addError(2735, 8, "Missing semicolon.");
	    th.addError(2924, 8, "Missing semicolon.");
	    th.addError(2987, 8, "'tagName' used out of scope.");
	    th.addError(2989, 24, "'tagName' used out of scope.");
	    th.addError(2989, 34, "'tagName' used out of scope.");
	    th.addError(2990, 17, "'tagName' used out of scope.");
	    th.addError(3827, 5, "Reassignment of 'getOffsetParent', which is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(3844, 5, "Reassignment of 'positionedOffset', which is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(3860, 5, "Reassignment of 'cumulativeOffset', which is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(4036, 17, "'ret' is already defined.");
	    th.addError(4072, 60, "'cur' used out of scope.");
	    th.addError(4085, 23, "'i' is already defined.");
	    th.addError(4132, 35, "'match' is already defined.");
	    th.addError(4290, 27, "'i' is already defined.");
	    th.addError(4290, 34, "'l' is already defined.");
	    th.addError(4291, 25, "'elem' is already defined.");
	    th.addError(4312, 61, "'nodeCheck' used out of scope.");
	    th.addError(4322, 66, "'nodeCheck' used out of scope.");
	    th.addError(4520, 27, "'i' is already defined.");
	    th.addError(4538, 32, "Expected a 'break' statement before 'case'.");
	    th.addError(4656, 27, "'i' is already defined.");
	    th.addError(4722, 30, "Missing '()' invoking a constructor.");
	    th.addError(4988, 8, "Missing semicolon.");
	    th.addError(5021, 7, "Missing semicolon.");
	    th.addError(5397, 8, "Missing semicolon.");
	    th.addError(5224, 21, "'values' is already defined.");
	    th.addError(5495, 5, "Function declarations should not be placed in blocks. Use a function expression or move the statement to the top of the outer function.");
	    th.addError(5545, 93, "The '__proto__' property is deprecated.");
		th.test(src, new LinterOptions()
			.set("sub", true)
			.set("lastsemic", true)
			.set("loopfunc", true)
			.set("evil", true)
			.set("eqnull", true)
			.set("laxbreak", true)
			.set("boss", true)
			.set("expr", true)
			.set("maxerr", 9001)
		);
	}
	
	@Test
	public void testLodash_0_6_1()
	{	
		String src = th.readFile("src/test/resources/libs/lodash.js");
		LinterGlobals globals = new LinterGlobals(false, "_", "define");
		LinterOptions options = new LinterOptions()
			.set("unused", true)
			.set("expr", true)
			.set("eqnull", true)
			.set("boss", true)
			.set("regexdash", true)
			.set("proto", true)
			.set("laxbreak", true)
			.set("newcap", false)
			.set("node", true)
			.set("evil", true)
			.set("laxcomma", true);
		
		th.addError(168, 23, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(170, 26, "Missing '()' invoking a constructor.");
	    th.addError(632, 6, "Missing semicolon.");
	    th.addError(920, 5, "Reassignment of 'isArguments', which is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(963, 5, "Reassignment of 'isFunction', which is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(1122, 12, "'isArr' used out of scope.");
	    th.addError(1127, 13, "'className' used out of scope.");
	    th.addError(1153, 18, "'isArr' used out of scope.");
	    th.addError(1159, 9, "'isArr' used out of scope.");
	    th.addError(1670, 66, "Missing semicolon.");
	    th.addError(3374, 11, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(3377, 27, "Missing '()' invoking a constructor.");
	    th.addError(3384, 24, "Missing semicolon.");
	    th.addError(3677, 24, "Missing '()' invoking a constructor.");
	    th.addError(3683, 21, "Missing '()' invoking a constructor.");
	    th.addError(3825, 12, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(4225, 5, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(4226, 12, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
	    th.addError(4242, 12, "If a strict mode function is executed using function invocation, its 'this' value will be undefined.");
		th.test(src, options, globals);
	}
	
	@Test
	public void testJson2()
	{	
		String src = th.readFile("src/test/resources/libs/json2.js");
		
		th.addError(177, 43, "'key' is defined but never used.");
	    th.addError(191, 50, "'key' is defined but never used.");
		th.test(src, new LinterOptions().set("singleGroups", true).set("undef", true).set("unused", true).set("laxbreak", true).setPredefineds("-JSON"), new LinterGlobals(true, "JSON"));
	}
	
	@Test
	public void testCodemirror3()
	{	
		String src = th.readFile("src/test/resources/libs/codemirror3.js");
		LinterOptions opt = new LinterOptions()
			.set("newcap", false)
			.set("undef", true)
			.set("unused", true)
			.set("eqnull", true)
			.set("boss", true)
			.set("laxbreak", true)
			.set("shadow", true)
			.set("loopfunc", true)
			.set("browser", true)
			.set("supernew", true)
			.set("-W008", true)  // Ignore warnings about leading dots in numbers.
			.set("-W038", true)  // Ignore scope warnings.
			.set("-W040", true);  // Ignore possible strict violations.
		
	    th.addError(1342, 51, "Value of 'e' may be overwritten in IE 8 and earlier.");
	    th.addError(1526, 14, "Value of 'e' may be overwritten in IE 8 and earlier.");
	    th.addError(1533, 12, "Value of 'e' may be overwritten in IE 8 and earlier.");
	    th.addError(4093, 63, "Unnecessary semicolon.");
		th.test(src, opt, new LinterGlobals(true, "CodeMirror"));
	}
}