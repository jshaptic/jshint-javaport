package org.jshint.test.unit;

import org.jshint.LinterOptions;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestDynamicImport extends Assert {
    private TestHelper th = new TestHelper();

    @BeforeMethod
    private void setupBeforeMethod() {
        th.newTest();
    }

    @Test
    public void testValid() {
        th.test(new String[] {
                "import(0);",
                "import(0 ? 0 : 0);",
                "(function * () { ",
                "  import(yield);",
                "})();",
                "import(() => {});",
                "import(async () => {});",
                "import(x = 0);",
                "new (import(0))();",
                "import(import(0));"
        }, new LinterOptions().set("esversion", 11));
    }

    @Test
    public void testInvalidValid() {
        th.newTest("empty");
        th.addError(1, 8, "Expected an identifier and instead saw ')'.");
        th.addError(1, 9, "Expected ')' and instead saw ';'.");
        th.addError(1, 10, "Missing semicolon.");
        th.test("import();", new LinterOptions().set("esversion", 11));

        th.newTest("expression");
        th.addError(1, 11, "Expected ')' and instead saw ','.");
        th.addError(1, 12, "Missing semicolon.");
        th.addError(1, 13, "Expected an assignment or function call and instead saw an expression.");
        th.addError(1, 16, "Missing semicolon.");
        th.addError(1, 16, "Expected an identifier and instead saw ')'.");
        th.addError(1, 16, "Expected an assignment or function call and instead saw an expression.");
        th.test("import('a', 'b');", new LinterOptions().set("esversion", 11));

        th.newTest("NewExpression");
        th.addError(1, 5, "Unexpected 'import'.");
        th.addError(1, 13, "Missing '()' invoking a constructor.");
        th.test("new import(0);", new LinterOptions().set("esversion", 11));
    }

    @Test
    public void testEsversion() {
        th.addError(1, 1, "'dynamic import' is only available in ES11 (use 'esversion: 11').");
        th.test("import(0);", new LinterOptions().set("esversion", 10));
    }
}
