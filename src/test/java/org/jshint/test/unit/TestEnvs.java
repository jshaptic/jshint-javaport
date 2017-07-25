package org.jshint.test.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jshint.JSHint;
import org.jshint.LinterOptions;
import org.jshint.DataSummary;
import org.jshint.ImpliedGlobal;
import org.jshint.test.helpers.TestHelper;
import org.jshint.utils.JSHintUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for the environmental (browser, jquery, etc.) options
 */
public class TestEnvs extends Assert
{
	private TestHelper th = new TestHelper();
	
	private String wrap(String[] globals)
	{
		return "(function () { return [ " + StringUtils.join(globals, ",") + " ]; }());";
	}
	
	private void globalsKnown(String[] globals, LinterOptions options)
	{
		JSHint jshint = new JSHint();
		
		jshint.lint(wrap(globals), options);
		DataSummary report = jshint.generateSummary();
		
		//JSHINT_BUG: typo, must be implieds instead of implied
		assertEquals(report.getGlobals().size(), globals.length);
		
		Map<String, Boolean> glbls = new HashMap<String, Boolean>();
		for (String g : report.getGlobals())
		{
			glbls.put(g, true);
		}
		
		for (String g : globals)
		{
			assertTrue(glbls.containsKey(g));
		}
	}
	
	private void globalsImplied(String[] globals)
	{
		globalsImplied(globals, null);
	}
	
	private void globalsImplied(String[] globals, LinterOptions options)
	{
		JSHint jshint = new JSHint();
		
		jshint.lint(wrap(globals), options != null ? options : new LinterOptions());
		DataSummary report = jshint.generateSummary();
		
		assertTrue(report.getImplieds().size() > 0);
		assertTrue(report.getGlobals().size() == 0);
		
		List<String> implieds = new ArrayList<String>();
		for (int i = 0; i < report.getImplieds().size(); i++)
		{
			ImpliedGlobal warn = report.getImplieds().get(i);
			if (warn == null) break;
			implieds.add(warn.getName());
		}
		
		assertEquals(implieds.size(), globals.length);
	}
	
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
	
	/*
	 * Option `node` predefines Node.js (v 0.5.9) globals
	 *
	 * More info:
	 * + http://nodejs.org/docs/v0.5.9/api/globals.html
	 */
	@Test
	public void testNode()
	{
		// Node environment assumes `globalstrict`
		String globalStrict = StringUtils.join(new String[]{
				"\"use strict\";",
				"function test() { return; }"
		}, "\n");
		
		th.addError(1, "Use the function form of \"use strict\".");
		th.test(globalStrict, new LinterOptions().set("es3", true).set("strict", true));
		
		th.reset();
		th.test(globalStrict, new LinterOptions().set("es3", true).set("node", true).set("strict", true));
		th.test(globalStrict, new LinterOptions().set("es3", true).set("browserify", true).set("strict", true));
		
		// Don't assume strict:true for Node environments. See bug GH-721.
		th.test("function test() { return; }", new LinterOptions().set("es3", true).set("node", true));
		th.test("function test() { return; }", new LinterOptions().set("es3", true).set("browserify", true));
		
		// Make sure that we can do fancy Node export
		
		String[] overwrites = {
				"global = {};",
				"Buffer = {};",
				"exports = module.exports = {};"
		};
		
		th.addError(1, "Read only.");
		th.test(overwrites, new LinterOptions().set("es3", true).set("node", true));
		th.test(overwrites, new LinterOptions().set("es3", true).set("browserify", true));
		
		th.reset();
		th.test("'use strict';var a;", new LinterOptions().set("node", true));
	}
	
	@Test
	public void testTyped()
	{
		String[] globals = {
				"ArrayBuffer",
				"ArrayBufferView",
				"DataView",
				"Float32Array",
				"Float64Array",
				"Int16Array",
				"Int32Array",
				"Int8Array",
				"Uint16Array",
				"Uint32Array",
				"Uint8Array",
				"Uint8ClampedArray"
		};
		
		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("browser", true));
		globalsKnown(globals, new LinterOptions().set("node", true));
		globalsKnown(globals, new LinterOptions().set("typed", true));
	}
	
	@Test
	public void testEs5()
	{
		String src = th.readFile("src/test/resources/fixtures/es5.js");
		
		th.addError(3, "Extra comma. (it breaks older versions of IE)");
	    th.addError(8, "Extra comma. (it breaks older versions of IE)");
	    th.addError(15, "get/set are ES5 features.");
	    th.addError(16, "get/set are ES5 features.");
	    th.addError(20, "get/set are ES5 features.");
	    th.addError(22, "get/set are ES5 features.");
	    th.addError(26, "get/set are ES5 features.");
	    th.addError(30, "get/set are ES5 features.");
	    th.addError(31, "get/set are ES5 features.");
	    th.addError(36, "get/set are ES5 features.");
	    th.addError(41, "get/set are ES5 features.");
	    th.addError(42, "get/set are ES5 features.");
	    th.addError(43, "Duplicate key 'x'.");
	    th.addError(47, "get/set are ES5 features.");
	    th.addError(48, "get/set are ES5 features.");
	    th.addError(48, "Duplicate key 'x'.");
	    th.addError(52, "get/set are ES5 features.");
	    th.addError(53, "get/set are ES5 features.");
	    th.addError(54, "get/set are ES5 features.");
	    th.addError(54, "Duplicate key 'x'.");
	    th.addError(58, "get/set are ES5 features.");
	    th.addError(58, "Unexpected parameter 'a' in get x function.");
	    th.addError(59, "get/set are ES5 features.");
	    th.addError(59, "Unexpected parameter 'a' in get y function.");
	    th.addError(60, "get/set are ES5 features.");
	    th.addError(62, "get/set are ES5 features.");
	    th.addError(62, "Expected a single parameter in set x function.");
	    th.addError(63, "get/set are ES5 features.");
	    th.addError(64, "get/set are ES5 features.");
	    th.addError(64, "Expected a single parameter in set z function.");
	    th.addError(68, "get/set are ES5 features.");
	    th.addError(69, "get/set are ES5 features.");
	    th.addError(68, "Missing property name.");
	    th.addError(69, "Missing property name.");
	    th.addError(75, "get/set are ES5 features.");
	    th.addError(76, "get/set are ES5 features.");
	    th.addError(80, "get/set are ES5 features.");
		th.test(src, new LinterOptions().set("es3", true));
		
		th.reset();
		th.addError(36, "Setter is defined without getter.");
	    th.addError(43, "Duplicate key 'x'.");
	    th.addError(48, "Duplicate key 'x'.");
	    th.addError(54, "Duplicate key 'x'.");
	    th.addError(58, "Unexpected parameter 'a' in get x function.");
	    th.addError(59, "Unexpected parameter 'a' in get y function.");
	    th.addError(62, "Expected a single parameter in set x function.");
	    th.addError(64, "Expected a single parameter in set z function.");
	    th.addError(68, "Missing property name.");
	    th.addError(69, "Missing property name.");
	    th.addError(80, "Setter is defined without getter.");
		th.test(src, new LinterOptions()); // es5
		
		// JSHint should not throw "Missing property name" error on nameless getters/setters
		// using Method Definition Shorthand if esnext flag is enabled.
		th.reset();
		th.addError(36, "Setter is defined without getter.");
	    th.addError(43, "Duplicate key 'x'.");
	    th.addError(48, "Duplicate key 'x'.");
	    th.addError(54, "Duplicate key 'x'.");
	    th.addError(58, "Unexpected parameter 'a' in get x function.");
	    th.addError(59, "Unexpected parameter 'a' in get y function.");
	    th.addError(62, "Expected a single parameter in set x function.");
	    th.addError(64, "Expected a single parameter in set z function.");
	    th.addError(80, "Setter is defined without getter.");
	    th.test(src, new LinterOptions().set("esnext", true));
	    
	    // Make sure that JSHint parses getters/setters as function expressions
	    // (https://github.com/jshint/jshint/issues/96)
	    src = th.readFile("src/test/resources/fixtures/es5.funcexpr.js");
	    th.reset();
	    th.test(src, new LinterOptions()); // es5
	}
	
	@Test
	public void testPhantom()
	{
		// Phantom environment assumes `globalstrict`
		String globalStrict = StringUtils.join(new String[]{
				"\"use strict\";",
				"function test() { return; }",
		}, "\n");
		
		th.addError(1, "Use the function form of \"use strict\".");
		th.test(globalStrict, new LinterOptions().set("es3", true).set("strict", true));
		
		th.reset();
		th.test(globalStrict, new LinterOptions().set("es3", true).set("phantom", true).set("strict", true));
	}
	
	@Test
	public void testGlobals()
	{
		String[] src = {
			"/* global first */",
		    "var first;"
		};
		
		th.addError(2, "Redefinition of 'first'.");
	    th.test(src);
	    th.reset();
	    th.test(src, new LinterOptions().set("browserify", true));
	    th.test(src, new LinterOptions().set("node", true));
	    th.test(src, new LinterOptions().set("phantom", true));
	    
	    th.test(new String[]{
	    	"/* global first */",
	        "void 0;",
	        "// jshint browserify: true",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"// jshint browserify: true",
	        "/* global first */",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"/* global first */",
	        "// jshint browserify: true",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"/* global first */",
	        "void 0;",
	        "// jshint node: true",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"// jshint node: true",
	        "/* global first */",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"/* global first */",
	        "// jshint node: true",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"/* global first */",
	        "void 0;",
	        "// jshint phantom: true",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"// jshint phantom: true",
	        "/* global first */",
	        "var first;"
	    });
	    
	    th.test(new String[]{
	    	"/* global first */",
	        "// jshint phantom: true",
	        "var first;"
	    });
	}
}