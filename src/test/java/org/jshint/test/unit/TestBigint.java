package org.jshint.test.unit;

import org.jshint.LinterOptions;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestBigint extends Assert {
    private TestHelper th = new TestHelper();

    @BeforeMethod
    private void setupBeforeMethod() {
        th.newTest();
    }

    @Test
    public void testEnabling() {
        th.newTest("Not enabled");
        th.addError(1, 6, "'BigInt' is only available in ES11 (use 'esversion: 11').");
        th.test("void 1n;", new LinterOptions().set("esversion", 10));

        th.newTest("Enabled via inline directive");
        th.test(new String[] {
                "// jshint esversion: 11",
                "void 1n;"
        }, new LinterOptions().set("esversion", 10));

        th.newTest("Enabled via configuration object");
        th.test(new String[] {
                "void 1n;"
        }, new LinterOptions().set("esversion", 11));
    }

    @Test
    public void testValidUsage() {
        th.test(new String[] {
                "void 0n;",
                "void 9n;",
                "void 0x0n;",
                "void 0xfn;",
                "void 0o0n;",
                "void 0o7n;",
                "void 0b0n;",
                "void 0b1n;",
                "void 0b01n;",
                "void 0b10n;"
        }, new LinterOptions().set("esversion", 11));

        th.newTest("No warnings for values that would otherwise coerce to Infinity");
        th.test(new String[] {
                "void " +
                        "1000000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000000" +
                        "000000000000000000000000000000000000000000n;"
        }, new LinterOptions().set("esversion", 11));
    }

    @Test
    public void testInvalid() {
        th.newTest("preceding decimal point");
        th.addError(1, 10, "A leading decimal point can be confused with a dot: '.1'.");
        th.addError(1, 8, "Missing semicolon.");
        th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
        th.test("void 1n.1;", new LinterOptions().set("esversion", 11));

        th.newTest("following decimal point");
        th.addError(1, 6, "Unexpected '1'.");
        th.addError(1, 1, "Unexpected early end of program.");
        th.addError(1, 6, "Unrecoverable syntax error. (100% scanned).");
        th.test("void 1.1n;", new LinterOptions().set("esversion", 11));

        th.newTest("preceding exponent");
        th.addError(1, 6, "Unexpected '1'.");
        th.addError(1, 1, "Unexpected early end of program.");
        th.addError(1, 6, "Unrecoverable syntax error. (100% scanned).");
        th.test("void 1ne3;", new LinterOptions().set("esversion", 11));

        th.newTest("following exponent");
        th.addError(1, 6, "Unexpected '1'.");
        th.addError(1, 1, "Unexpected early end of program.");
        th.addError(1, 6, "Unrecoverable syntax error. (100% scanned).");
        th.test("void 1e3n;", new LinterOptions().set("esversion", 11));

        th.newTest("invalid legacy octal");
        th.addError(1, 6, "Malformed numeric literal: '01n'.");
        th.test("void 01n;", new LinterOptions().set("esversion", 11));

        th.newTest("invalid leading 0");
        th.addError(1, 6, "Malformed numeric literal: '08n'.");
        th.test("void 08n;", new LinterOptions().set("esversion", 11));

        th.newTest("invalid hex digit");
        th.addError(1, 8, "Malformed numeric literal: '0x'.");
        th.addError(1, 8, "Missing semicolon.");
        th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
        th.test("void 0xgn;", new LinterOptions().set("esversion", 11));

        th.newTest("invalid binary digit");
        th.addError(1, 8, "Malformed numeric literal: '0b'.");
        th.addError(1, 8, "Missing semicolon.");
        th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
        th.test("void 0b2n;", new LinterOptions().set("esversion", 11));

        th.newTest("invalid octal digit");
        th.addError(1, 8, "Malformed numeric literal: '0o'.");
        th.addError(1, 8, "Missing semicolon.");
        th.addError(1, 8, "Expected an assignment or function call and instead saw an expression.");
        th.test("void 0o8n;", new LinterOptions().set("esversion", 11));
    }

}
