package org.jshint.test.unit;

import org.jshint.LinterOptions;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestObjRestSpread extends Assert
{
	private TestHelper th = new TestHelper();
	
	@Test
	public void testEnabling()
	{
		th.newTest("Not enabled")
		.addError(1, 11, "'object spread property' is only available in ES9 (use 'esversion: 9').")
		.test("void { ...x };",  new LinterOptions().set("esversion", 8));
		
		th.newTest("Not enabled")
		.addError(1, 7, "'object rest property' is only available in ES9 (use 'esversion: 9').")
		.test("({ ...x } = {});",  new LinterOptions().set("esversion", 8));
	}
	
	@Test
	public void testSpread()
	{
		String[] code = {
			"var o;",
			"o = { ...o };",
			"o = { set x(_) {}, ...o, get x() {} };",
			"o = { *gen() { yield; }, ...o, [o]() {} };",
			"o = { ...o, };"
		};
		
		th.newTest("identifier")
		.test(code,  new LinterOptions().set("esversion", 9));
		
		code = new String[] {
			"var o;",
			"o = { ...this };",
			"o = { ...[] };",
			"o = { ...'string' };",
			"o = { ...o = {} };",
			"o = { ...() => {} };",
			"o = { ...o => {} };"
		};
		
		th.newTest("expression")
		.test(code,  new LinterOptions().set("esversion", 9));
	}
	
	@Test
	public void testRest()
	{
		String[] code = {
			"({ ...x } = {});",
			"({ a, ...x } = {});",
			"({ a = 0, ...x } = {});",
			"({ a: A, ...x } = {});",
			"({ a: A = 0, ...x } = {});"
		};
		
		th.newTest("identifier, final")
		.test(code,  new LinterOptions().set("esversion", 9));
		
		code = new String[] {
			"({ ...x, } = {});",
			"({ a, ...x, b } = {});",
			"({ a = 0, ...x, b = 1 } = {});",
			"({ a: A, ...x, b: B } = {});",
			"({ a: A = 0, ...x, b: B = 0 } = {});"
		};
		
		th.newTest("identifier, not final")
		.addError(1, 8, "Invalid element after rest element.")
		.addError(2, 11, "Invalid element after rest element.")
		.addError(3, 15, "Invalid element after rest element.")
		.addError(4, 14, "Invalid element after rest element.")
		.addError(5, 18, "Invalid element after rest element.")
		.test(code,  new LinterOptions().set("esversion", 9));
		
		code = new String[] {
			"({ ...[a, b, c] } = {});",
			"({ a, ...[b, c, d] } = {});",
			"({ a = 0, ...[b, c, d] } = {});",
			"({ a: A, ...[b, c, d] } = {});",
			"({ a: A = 0, ...[b, c, d] } = {});"
		};
		
		th.newTest("nested array pattern, final")
		.addError(1, 7, "Expected an identifier and instead saw '['.")
		.addError(2, 10, "Expected an identifier and instead saw '['.")
		.addError(3, 14, "Expected an identifier and instead saw '['.")
		.addError(4, 13, "Expected an identifier and instead saw '['.")
		.addError(5, 17, "Expected an identifier and instead saw '['.")
		.test(code,  new LinterOptions().set("esversion", 9));
		
		code = new String[] {
			"({ ...[a, b, c], } = {});",
			"({ a, ...[b, c, d], e } = {});",
			"({ a = 0, ...[b, c, d], e = 0 } = {});",
			"({ a: A, ...[b, c, d], e: E } = {});",
			"({ a: A = 0, ...[b, c, d], e: E = 0 } = {});",
		};
		
		th.newTest("nested array pattern, not final")
		.addError(1, 7, "Expected an identifier and instead saw '['.")
		.addError(1, 16, "Invalid element after rest element.")
		.addError(2, 10, "Expected an identifier and instead saw '['.")
		.addError(2, 19, "Invalid element after rest element.")
		.addError(3, 14, "Expected an identifier and instead saw '['.")
		.addError(3, 23, "Invalid element after rest element.")
		.addError(4, 13, "Expected an identifier and instead saw '['.")
		.addError(4, 22, "Invalid element after rest element.")
		.addError(5, 17, "Expected an identifier and instead saw '['.")
		.addError(5, 26, "Invalid element after rest element.")
		.test(code,  new LinterOptions().set("esversion", 9));
		
		th.newTest("nested array pattern, empty")
		.addError(1, 7, "Expected an identifier and instead saw '['.")
		.test("({ ...[] } = {});",  new LinterOptions().set("esversion", 9));
		
		th.newTest("nested object pattern, empty")
		.addError(1, 7, "Expected an identifier and instead saw '{'.")
		.test("({ ...{} } = {});",  new LinterOptions().set("esversion", 9));
	}
}