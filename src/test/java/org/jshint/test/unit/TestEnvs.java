package org.jshint.test.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jshint.JSHint;
import org.jshint.LinterOptions;
import org.jshint.DataSummary;
import org.jshint.ImpliedGlobal;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for the environmental (browser, jquery, etc.) options
 */
public class TestEnvs extends Assert {
	private TestHelper th = new TestHelper();

	private String wrap(String[] globals) {
		return "void [ " + StringUtils.join(globals, ",") + " ]";
	}

	private void globalsKnown(String[] globals, LinterOptions options) {
		JSHint jshint = new JSHint();

		jshint.lint(wrap(globals), options);
		DataSummary report = jshint.generateSummary();

		assertTrue(report.getImplieds() == Collections.EMPTY_LIST);
		assertEquals(report.getGlobals().size(), globals.length);

		Map<String, Boolean> glbls = new HashMap<>();
		for (String g : report.getGlobals()) {
			glbls.put(g, true);
		}

		for (String g : globals) {
			assertTrue(glbls.containsKey(g));
		}
	}

	private void globalsImplied(String[] globals) {
		globalsImplied(globals, null);
	}

	private void globalsImplied(String[] globals, LinterOptions options) {
		JSHint jshint = new JSHint();

		jshint.lint(wrap(globals), options != null ? options : new LinterOptions());
		DataSummary report = jshint.generateSummary();

		assertTrue(report.getImplieds().size() > 0);
		assertTrue(report.getGlobals().size() == 0);

		List<String> implieds = new ArrayList<>();
		for (int i = 0; i < report.getImplieds().size(); i++) {
			ImpliedGlobal warn = report.getImplieds().get(i);
			if (warn == null)
				break;
			implieds.add(warn.getName());
		}

		assertEquals(implieds.size(), globals.length);
	}

	@BeforeMethod
	private void setupBeforeMethod() {
		th.newTest();
	}

	/*
	 * Option `node` predefines Node.js (v 0.5.9) globals
	 *
	 * More info:
	 * + http://nodejs.org/docs/v0.5.9/api/globals.html
	 */
	@Test
	public void testNode() {
		// Node environment assumes `globalstrict`
		String globalStrict = StringUtils.join(new String[] {
				"\"use strict\";",
				"function test() { return; }"
		}, "\n");

		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.test(globalStrict, new LinterOptions().set("es3", true).set("strict", true));

		th.newTest();
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

		th.addError(1, 1, "Read only.");
		th.test(overwrites, new LinterOptions().set("es3", true).set("node", true));
		th.test(overwrites, new LinterOptions().set("es3", true).set("browserify", true));

		th.newTest("gh-2657");
		th.test("'use strict';var a;", new LinterOptions().set("node", true));

		th.newTest("`arguments` binding");
		th.test("void arguments;", new LinterOptions().set("node", true).set("undef", true));
	}

	@Test
	public void testTyped() {
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
	public void testEs5() {
		String src = th.readFile("src/test/resources/fixtures/es5.js");

		th.addError(3, 6, "Extra comma. (it breaks older versions of IE)");
		th.addError(8, 9, "Extra comma. (it breaks older versions of IE)");
		th.addError(15, 13, "get/set are ES5 features.");
		th.addError(16, 13, "get/set are ES5 features.");
		th.addError(20, 13, "get/set are ES5 features.");
		th.addError(22, 13, "get/set are ES5 features.");
		th.addError(26, 13, "get/set are ES5 features.");
		th.addError(30, 13, "get/set are ES5 features.");
		th.addError(31, 13, "get/set are ES5 features.");
		th.addError(36, 13, "get/set are ES5 features.");
		th.addError(41, 13, "get/set are ES5 features.");
		th.addError(42, 13, "get/set are ES5 features.");
		th.addError(43, 10, "Duplicate key 'x'.");
		th.addError(47, 13, "get/set are ES5 features.");
		th.addError(48, 13, "get/set are ES5 features.");
		th.addError(48, 14, "Duplicate key 'x'.");
		th.addError(52, 13, "get/set are ES5 features.");
		th.addError(53, 13, "get/set are ES5 features.");
		th.addError(54, 13, "get/set are ES5 features.");
		th.addError(54, 14, "Duplicate key 'x'.");
		th.addError(58, 13, "get/set are ES5 features.");
		th.addError(58, 14, "Unexpected parameter 'a' in get x function.");
		th.addError(59, 13, "get/set are ES5 features.");
		th.addError(59, 14, "Unexpected parameter 'a' in get y function.");
		th.addError(60, 13, "get/set are ES5 features.");
		th.addError(62, 13, "get/set are ES5 features.");
		th.addError(62, 14, "Expected a single parameter in set x function.");
		th.addError(63, 13, "get/set are ES5 features.");
		th.addError(64, 13, "get/set are ES5 features.");
		th.addError(64, 14, "Expected a single parameter in set z function.");
		th.addError(68, 13, "get/set are ES5 features.");
		th.addError(69, 13, "get/set are ES5 features.");
		th.addError(68, 13, "Missing property name.");
		th.addError(69, 13, "Missing property name.");
		th.addError(75, 13, "get/set are ES5 features.");
		th.addError(76, 13, "get/set are ES5 features.");
		th.addError(80, 13, "get/set are ES5 features.");
		th.test(src, new LinterOptions().set("es3", true));

		th.newTest();
		th.addError(36, 13, "Setter is defined without getter.");
		th.addError(43, 10, "Duplicate key 'x'.");
		th.addError(48, 14, "Duplicate key 'x'.");
		th.addError(54, 14, "Duplicate key 'x'.");
		th.addError(58, 14, "Unexpected parameter 'a' in get x function.");
		th.addError(59, 14, "Unexpected parameter 'a' in get y function.");
		th.addError(62, 14, "Expected a single parameter in set x function.");
		th.addError(64, 14, "Expected a single parameter in set z function.");
		th.addError(68, 13, "Missing property name.");
		th.addError(69, 13, "Missing property name.");
		th.addError(80, 13, "Setter is defined without getter.");
		th.test(src, new LinterOptions()); // es5

		// JSHint should not throw "Missing property name" error on nameless
		// getters/setters
		// using Method Definition Shorthand if esnext flag is enabled.
		th.newTest();
		th.addError(36, 13, "Setter is defined without getter.");
		th.addError(43, 10, "Duplicate key 'x'.");
		th.addError(48, 14, "Duplicate key 'x'.");
		th.addError(54, 14, "Duplicate key 'x'.");
		th.addError(58, 14, "Unexpected parameter 'a' in get x function.");
		th.addError(59, 14, "Unexpected parameter 'a' in get y function.");
		th.addError(62, 14, "Expected a single parameter in set x function.");
		th.addError(64, 14, "Expected a single parameter in set z function.");
		th.addError(80, 13, "Setter is defined without getter.");
		th.test(src, new LinterOptions().set("esnext", true));

		// Make sure that JSHint parses getters/setters as function expressions
		// (https://github.com/jshint/jshint/issues/96)
		src = th.readFile("src/test/resources/fixtures/es5.funcexpr.js");
		th.newTest();
		th.test(src, new LinterOptions()); // es5
	}

	@Test
	public void testPhantom() {
		// Phantom environment assumes `globalstrict`
		String globalStrict = StringUtils.join(new String[] {
				"\"use strict\";",
				"function test() { return; }",
		}, "\n");

		th.addError(1, 1, "Use the function form of \"use strict\".");
		th.test(globalStrict, new LinterOptions().set("es3", true).set("strict", true));

		th.newTest();
		th.test(globalStrict, new LinterOptions().set("es3", true).set("phantom", true).set("strict", true));
	}

	@Test
	public void testGlobals() {
		String[] src = {
				"/* global first */",
				"var first;"
		};

		th.addError(2, 5, "Redefinition of 'first'.");
		th.test(src);
		th.newTest();
		th.test(src, new LinterOptions().set("browserify", true));
		th.test(src, new LinterOptions().set("node", true));
		th.test(src, new LinterOptions().set("phantom", true));

		th.newTest("Late configuration of `browserify`");
		th.test(new String[] {
				"/* global first */",
				"void 0;",
				"// jshint browserify: true",
				"var first;"
		});

		th.test(new String[] {
				"// jshint browserify: true",
				"/* global first */",
				"var first;"
		});

		th.test(new String[] {
				"/* global first */",
				"// jshint browserify: true",
				"var first;"
		});

		th.newTest("Late configuration of `node`");
		th.test(new String[] {
				"/* global first */",
				"void 0;",
				"// jshint node: true",
				"var first;"
		});

		th.test(new String[] {
				"// jshint node: true",
				"/* global first */",
				"var first;"
		});

		th.test(new String[] {
				"/* global first */",
				"// jshint node: true",
				"var first;"
		});

		th.newTest("Late configuration of `phantom`");
		th.test(new String[] {
				"/* global first */",
				"void 0;",
				"// jshint phantom: true",
				"var first;"
		});

		th.test(new String[] {
				"// jshint phantom: true",
				"/* global first */",
				"var first;"
		});

		th.test(new String[] {
				"/* global first */",
				"// jshint phantom: true",
				"var first;"
		});
	}

	@Test
	public void testShelljs() {
		String src = th.readFile("src/test/resources/fixtures/shelljs.js");

		th.newTest("1");
		th.addError(1, 1, "'target' is not defined.");
		th.addError(3, 1, "'echo' is not defined.");
		th.addError(4, 1, "'exit' is not defined.");
		th.addError(5, 1, "'cd' is not defined.");
		th.addError(6, 1, "'pwd' is not defined.");
		th.addError(7, 1, "'ls' is not defined.");
		th.addError(8, 1, "'find' is not defined.");
		th.addError(9, 1, "'cp' is not defined.");
		th.addError(10, 1, "'rm' is not defined.");
		th.addError(11, 1, "'mv' is not defined.");
		th.addError(12, 1, "'mkdir' is not defined.");
		th.addError(13, 1, "'test' is not defined.");
		th.addError(14, 1, "'cat' is not defined.");
		th.addError(15, 1, "'sed' is not defined.");
		th.addError(16, 1, "'grep' is not defined.");
		th.addError(17, 1, "'which' is not defined.");
		th.addError(18, 1, "'dirs' is not defined.");
		th.addError(19, 1, "'pushd' is not defined.");
		th.addError(20, 1, "'popd' is not defined.");
		th.addError(21, 1, "'env' is not defined.");
		th.addError(22, 1, "'exec' is not defined.");
		th.addError(23, 1, "'chmod' is not defined.");
		th.addError(24, 1, "'config' is not defined.");
		th.addError(25, 1, "'error' is not defined.");
		th.addError(26, 1, "'tempdir' is not defined.");
		th.addError(29, 1, "'require' is not defined.");
		th.addError(30, 1, "'module' is not defined.");
		th.addError(31, 1, "'process' is not defined.");
		th.test(src, new LinterOptions().set("undef", true));

		th.newTest("2");
		th.test(src, new LinterOptions().set("undef", true).set("shelljs", true));
	}

	@Test
	public void testBrowser() {
		String src = th.readFile("src/test/resources/fixtures/browser.js");

		th.addError(2, 9, "'atob' is not defined.");
		th.addError(3, 9, "'btoa' is not defined.");
		th.addError(6, 14, "'DOMParser' is not defined.");
		th.addError(10, 14, "'XMLSerializer' is not defined.");
		th.addError(14, 20, "'NodeFilter' is not defined.");
		th.addError(15, 19, "'Node' is not defined.");
		th.addError(18, 28, "'MutationObserver' is not defined.");
		th.addError(21, 16, "'SVGElement' is not defined.");
		th.addError(24, 19, "'Comment' is not defined.");
		th.addError(25, 14, "'DocumentFragment' is not defined.");
		th.addError(26, 17, "'Range' is not defined.");
		th.addError(27, 16, "'Text' is not defined.");
		th.addError(31, 15, "'document' is not defined.");
		th.addError(32, 1, "'fetch' is not defined.");
		th.addError(35, 19, "'URL' is not defined.");
		th.test(src, new LinterOptions().set("es3", true).set("undef", true));

		th.newTest();
		th.test(src, new LinterOptions().set("es3", true).set("browser", true).set("undef", true));
	}

	@Test
	public void testCouch() {
		String[] globals = {
				"require",
				"respond",
				"getRow",
				"emit",
				"send",
				"start",
				"sum",
				"log",
				"exports",
				"module",
				"provides"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("couch", true));
	}

	@Test
	public void testQunit() {
		String[] globals = {
				"asyncTest",
				"deepEqual",
				"equal",
				"expect",
				"module",
				"notDeepEqual",
				"notEqual",
				"notPropEqual",
				"notStrictEqual",
				"ok",
				"propEqual",
				"QUnit",
				"raises",
				"start",
				"stop",
				"strictEqual",
				"test",
				"throws"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("qunit", true));
	}

	@Test
	public void testRhino() {
		String[] globals = {
				"arguments",
				"defineClass",
				"deserialize",
				"gc",
				"help",
				"importClass",
				"importPackage",
				"java",
				"load",
				"loadClass",
				"Packages",
				"print",
				"quit",
				"readFile",
				"readUrl",
				"runCommand",
				"seal",
				"serialize",
				"spawn",
				"sync",
				"toint32",
				"version"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("rhino", true));
	}

	@Test
	public void testPrototypejs() {
		String[] globals = {
				"$",
				"$$",
				"$A",
				"$F",
				"$H",
				"$R",
				"$break",
				"$continue",
				"$w",
				"Abstract",
				"Ajax",
				"Class",
				"Enumerable",
				"Element",
				"Event",
				"Field",
				"Form",
				"Hash",
				"Insertion",
				"ObjectRange",
				"PeriodicalExecuter",
				"Position",
				"Prototype",
				"Selector",
				"Template",
				"Toggle",
				"Try",
				"Autocompleter",
				"Builder",
				"Control",
				"Draggable",
				"Draggables",
				"Droppables",
				"Effect",
				"Sortable",
				"SortableObserver",
				"Sound",
				"Scriptaculous"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("prototypejs", true));
	}

	@Test
	public void testDojo() {
		String[] globals = {
				"dojo",
				"dijit",
				"dojox",
				"define",
				"require"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("dojo", true));
	}

	@Test
	public void testNonstandard() {
		String[] globals = {
				"escape",
				"unescape"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("nonstandard", true));
	}

	@Test
	public void testJasmine() {
		String[] globals = {
				"jasmine",
				"describe",
				"xdescribe",
				"it",
				"xit",
				"beforeEach",
				"afterEach",
				"setFixtures",
				"loadFixtures",
				"spyOn",
				"spyOnProperty",
				"expect",
				"runs",
				"waitsFor",
				"waits",
				"beforeAll",
				"afterAll",
				"fail",
				"fdescribe",
				"fit"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("jasmine", true));
	}

	@Test
	public void testMootols() {
		String[] globals = {
				"$",
				"$$",
				"Asset",
				"Browser",
				"Chain",
				"Class",
				"Color",
				"Cookie",
				"Core",
				"Document",
				"DomReady",
				"DOMEvent",
				"DOMReady",
				"Drag",
				"Element",
				"Elements",
				"Event",
				"Events",
				"Fx",
				"Group",
				"Hash",
				"HtmlTable",
				"IFrame",
				"IframeShim",
				"InputValidator",
				"instanceOf",
				"Keyboard",
				"Locale",
				"Mask",
				"MooTools",
				"Native",
				"Options",
				"OverText",
				"Request",
				"Scroller",
				"Slick",
				"Slider",
				"Sortables",
				"Spinner",
				"Swiff",
				"Tips",
				"Type",
				"typeOf",
				"URI",
				"Window"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("mootools", true));
	}

	@Test
	public void testWorker() {
		String[] globals = {
				"importScripts",
				"postMessage",
				"self",
				"FileReaderSync"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("worker", true));
	}

	@Test
	public void testWsh() {
		String[] globals = {
				"ActiveXObject",
				"Enumerator",
				"GetObject",
				"ScriptEngine",
				"ScriptEngineBuildVersion",
				"ScriptEngineMajorVersion",
				"ScriptEngineMinorVersion",
				"VBArray",
				"WSH",
				"WScript",
				"XDomainRequest"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("wsh", true));
	}

	@Test
	public void testYui() {
		String[] globals = {
				"YUI",
				"Y",
				"YUI_config"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("yui", true));
	}

	@Test
	public void testMocha() {
		String[] globals = {
				"mocha",
				"describe",
				"xdescribe",
				"it",
				"xit",
				"context",
				"xcontext",
				"before",
				"after",
				"beforeEach",
				"afterEach",
				"suite",
				"test",
				"setup",
				"teardown",
				"suiteSetup",
				"suiteTeardown"
		};

		globalsImplied(globals);
		globalsKnown(globals, new LinterOptions().set("mocha", true));
	}
}