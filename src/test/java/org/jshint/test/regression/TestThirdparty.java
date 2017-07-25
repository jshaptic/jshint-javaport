package org.jshint.test.regression;

import org.jshint.LinterGlobals;
import org.jshint.LinterOptions;
import org.jshint.test.helpers.TestHelper;
import org.jshint.utils.JSHintUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestThirdparty extends Assert
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
	
	@Test
	public void testBackbone()
	{	
		String src = th.readFile("src/test/resources/libs/backbone.js");
		
		th.addError(32, "Unnecessary grouping operator.");
	    th.addError(784, "Unnecessary grouping operator.");
	    th.addError(864, "Unnecessary grouping operator.");
	    th.addError(685, "Missing '()' invoking a constructor.");
	    th.addError(764, "Use '===' to compare with '0'.");
	    th.addError(859, "Use '!==' to compare with '0'.");
		th.test(src, new LinterOptions().set("expr", true).set("eqnull", true).set("boss", true).set("regexdash", true).set("singleGroups", true));
	}
	
	@Test
	public void testJQuery()
	{	
		String src = th.readFile("src/test/resources/libs/jquery-1.7.js");
		LinterGlobals globals = new LinterGlobals(false, "DOMParser", "ActiveXObject", "define");
		
		th.addError(551, "'name' is defined but never used.");
	    th.addError(1044, "'actual' is defined but never used.");
	    th.addError(1312, "'pCount' is defined but never used.");
	    th.addError(1369, "'events' is defined but never used.");
	    th.addError(1607, "'table' is defined but never used.");
	    th.addError(1710, "'internalKey' is defined but never used.");
	    th.addError(1813, "'internalKey' is defined but never used.");
	    th.addError(2818, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2822, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(2859, "'rnamespaces' is defined but never used.");
	    th.addError(2861, "'rperiod' is defined but never used.");
	    th.addError(2862, "'rspaces' is defined but never used.");
	    th.addError(2863, "'rescape' is defined but never used.");
	    th.addError(2900, "'quick' is defined but never used.");
	    th.addError(3269, "'related' is defined but never used.");
	    th.addError(3592, "'selector' is defined but never used.");
	    th.addError(4465, "'curLoop' is defined but never used.");
	    th.addError(4560, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(4694, "'cache' is defined but never used.");
	    th.addError(4712, "Expected a 'break' statement before 'case'.");
	    th.addError(4843, "Expected an assignment or function call and instead saw an expression.");
	    th.addError(5635, "'elem' is defined but never used.");
	    th.addError(5675, "'i' is defined but never used.");
	    th.addError(5691, "'i' is defined but never used.");
	    th.addError(7141, "'i' is defined but never used.");
	    th.addError(6061, "'cur' is defined but never used.");
		th.test(src, new LinterOptions().set("undef", true).set("unused", true), globals);
	}
	
	@Test
	public void testPrototype17()
	{	
		String src = th.readFile("src/test/resources/libs/prototype-17.js");
		
		th.addError(22, "Missing semicolon.");
	    th.addError(94, "Unnecessary semicolon.");
	    th.addError(110, "Missing '()' invoking a constructor.");
	    th.addError(253, "'i' is already defined.");
	    th.addError(253, "'length' is already defined.");
	    th.addError(260, "'i' is already defined.");
	    th.addError(260, "'length' is already defined.");
	    th.addError(261, "'key' is already defined.");
	    th.addError(261, "'str' is already defined.");
	    th.addError(319, "Reassignment of 'isArray', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(392, "Missing semicolon.");
	    th.addError(400, "Missing semicolon.");
	    th.addError(409, "Missing semicolon.");
	    th.addError(430, "Missing semicolon.");
	    th.addError(451, "Missing semicolon.");
	    th.addError(633, "Use '!==' to compare with 'undefined'.");
	    th.addError(737, "Use '===' to compare with ''.");
	    th.addError(807, "Use '===' to compare with ''.");
	    th.addError(1137, "Use '===' to compare with '0'.");
	    th.addError(1215, "Missing semicolon.");
	    th.addError(1224, "Unnecessary semicolon.");
	    th.addError(1916, "Missing semicolon.");
	    th.addError(2034, "Missing semicolon.");
	    th.addError(2662, "Missing semicolon.");
	    th.addError(2735, "Missing semicolon.");
	    th.addError(2924, "Missing semicolon.");
	    th.addError(2987, "'tagName' used out of scope.");
	    th.addError(2989, "'tagName' used out of scope.");
	    th.addError(2989, "'tagName' used out of scope.");
	    th.addError(2990, "'tagName' used out of scope.");
	    th.addError(3827, "Reassignment of 'getOffsetParent', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(3844, "Reassignment of 'positionedOffset', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(3860, "Reassignment of 'cumulativeOffset', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(4036, "'ret' is already defined.");
	    th.addError(4072, "'cur' used out of scope.");
	    th.addError(4085, "'i' is already defined.");
	    th.addError(4132, "'match' is already defined.");
	    th.addError(4290, "'i' is already defined.");
	    th.addError(4290, "'l' is already defined.");
	    th.addError(4291, "'elem' is already defined.");
	    th.addError(4312, "'nodeCheck' used out of scope.");
	    th.addError(4322, "'nodeCheck' used out of scope.");
	    th.addError(4520, "'i' is already defined.");
	    th.addError(4538, "Expected a 'break' statement before 'case'.");
	    th.addError(4547, "Use '===' to compare with '0'.");
	    th.addError(4565, "Use '===' to compare with '0'.");
	    th.addError(4566, "Use '===' to compare with '0'.");
	    th.addError(4568, "Use '===' to compare with '0'.");
	    th.addError(4656, "'i' is already defined.");
	    th.addError(4722, "Missing '()' invoking a constructor.");
	    th.addError(4988, "Missing semicolon.");
	    th.addError(4988, "Missing semicolon.");
	    th.addError(5021, "Missing semicolon.");
	    th.addError(5397, "Missing semicolon.");
	    th.addError(5112, "Use '!==' to compare with 'undefined'.");
	    th.addError(5140, "Use '!==' to compare with ''.");
	    th.addError(5224, "'values' is already defined.");
	    th.addError(5495, "Function declarations should not be placed in blocks. Use a function expression or move the statement to the top of the outer function.");
	    th.addError(5545, "The '__proto__' property is deprecated.");
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
	public void testLodash061()
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
		
		th.addError(168, "Possible strict violation.");
	    th.addError(170, "Missing '()' invoking a constructor.");
	    th.addError(632, "Missing semicolon.");
	    th.addError(920, "Reassignment of 'isArguments', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(963, "Reassignment of 'isFunction', which is is a function. Use 'var' or 'let' to declare bindings that may change.");
	    th.addError(1122, "'isArr' used out of scope.");
	    th.addError(1127, "'className' used out of scope.");
	    th.addError(1129, "Use '===' to compare with 'true'.");
	    th.addError(1153, "'isArr' used out of scope.");
	    th.addError(1159, "'isArr' used out of scope.");
	    th.addError(1490, "Use '===' to compare with '0'.");
	    th.addError(1670, "Missing semicolon.");
	    th.addError(3374, "Possible strict violation.");
	    th.addError(3377, "Missing '()' invoking a constructor.");
	    th.addError(3384, "Missing semicolon.");
	    th.addError(3677, "Missing '()' invoking a constructor.");
	    th.addError(3683, "Missing '()' invoking a constructor.");
	    th.addError(3825, "Possible strict violation.");
	    th.addError(4225, "Possible strict violation.");
	    th.addError(4226, "Possible strict violation.");
	    th.addError(4242, "Possible strict violation.");
		th.test(src, options, globals);
	}
	
	@Test
	public void testJson2()
	{	
		String src = th.readFile("src/test/resources/libs/json2.js");
		
		th.addError(177, "'key' is defined but never used.");
	    th.addError(191, "'key' is defined but never used.");
		th.test(src, new LinterOptions().set("singleGroups", true).set("undef", true).set("unused", true).set("laxbreak", true).addPredefineds("-JSON"), new LinterGlobals(true, "JSON"));
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
			.set("-W040", true)  // Ignore possible strict violations.
			.set("-W041", true); // Ignore poor relations warnings.
		
	    th.addError(1342, "Value of 'e' may be overwritten in IE 8 and earlier.");
	    th.addError(1526, "Value of 'e' may be overwritten in IE 8 and earlier.");
	    th.addError(1533, "Value of 'e' may be overwritten in IE 8 and earlier.");
	    th.addError(4093, "Unnecessary semicolon.");
		th.test(src, opt, new LinterGlobals(true, "CodeMirror"));
	}
}