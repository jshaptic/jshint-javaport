package org.jshint.test.test262;

import java.io.File;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.JSHint;
import org.jshint.LinterOptions;
import org.jshint.LinterWarning;
import org.jshint.Reg;
import org.jshint.test.helpers.TestHelper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class Test262 extends Assert
{	
	private TestHelper th = new TestHelper();
	
	private static final String pathTest262 = normalize(System.getProperty("user.dir")) + "/src/test/resources/test262/test262/test";
	private static final String pathExpectations = normalize(System.getProperty("user.dir")) + "/src/test/resources/test262/expectations.txt";
	private static final String pathPortExpectations = normalize(System.getProperty("user.dir")) + "/src/test/resources/test262/portExpectations.txt";
	
	private static final Pattern testName = Pattern.compile("^(?!.*_FIXTURE).*\\.[jJ][sS]");
	
	private static final Pattern modulePattern = Pattern.compile("^\\s*-\\s*module\\s*$|^\\s*flags\\s*:.*\\bmodule\\b", Pattern.MULTILINE);
	private static final Pattern noStrictPattern = Pattern.compile("^\\s*-\\s*noStrict\\s*$|^\\s*flags\\s*:.*\\bnoStrict\\b", Pattern.MULTILINE);
	private static final Pattern onlyStrictPattern = Pattern.compile("^\\s*-\\s*onlyStrict\\s*$|^\\s*flags\\s*:.*\\bonlyStrict\\b", Pattern.MULTILINE);
	
	private boolean hasEarlyError(String src)
	{
		return Reg.test("^\\s*negative:\\s*$", Pattern.MULTILINE, src) && Reg.test("^\\s+phase: early\\s*$", Pattern.MULTILINE, src); 
	}
	
	private RunResult runJshint(String src)
	{
		boolean isModule = Reg.test(modulePattern, src);
		RunResult result = null;
		Exception exception = null;
		
		JSHint jshint = new JSHint();
		try
		{
			jshint.lint(src, new LinterOptions().set("esversion", 6).set("maxerr", Integer.MAX_VALUE).set("module", isModule));
		}
		catch(Exception e)
		{
			exception = e;
		}
		
		result = new RunResult(exception, jshint.generateSummary().getErrors());
		
		result.parseFailure = isFailure(result);
		
		return result;
	}
	
	/**
	 * Given the source of a Test262 test, invoke the provided callback once for
	 * each valid "version" of that program as defined by its meta data.
	 */
	private List<RunResult> forEachVersion(String src)
	{
		boolean onlyStrict = Reg.test(onlyStrictPattern, src);
		boolean noStrict = Reg.test(noStrictPattern, src);
		List<RunResult> results = new ArrayList<RunResult>();
		
		if (!onlyStrict)
		{
			results.add(runJshint(src));
		}
		
		if (!noStrict)
		{
			results.add(runJshint("'use strict';\n" + src));
		}
		
		return results;
	}
	
	/**
	 * JSHint "error" messages generally indicate a parsing failure and "warning"
	 * messages generally indicate more objective problems with technically-valid
	 * programs. This convention is not consistently honored, however, so
	 * interpreting parsing success/failure from message code requires the
	 * following list of "contradictory" codes.
	 */
	private static final Map<String, Boolean> incorrectSeverity = ImmutableMap.<String, Boolean>builder()
		.put("E007", true)
		// E013 describes a runtime error: the modification of a constant binding.
		// Although (unlike the other errors referenced in this object) this
		// condition is guaranteed to produce errors, it is not technically an early
		// error and should therefore be ignored when interpreting Test262 tests.
		.put("E013", true)
		.put("E034", true)
		
		.put("W024", true)
		.put("W025", true)
		.put("W052", true)
		.put("W067", true)
		.put("W076", true)
		.put("W077", true)
		.put("W090", true)
		.put("W094", true)
		.put("W095", true)
		.put("W112", true)
		.put("W115", true)
		.put("W130", true)
		.put("W131", true)
		.put("W133", true)
		.put("W136", true)
		.build();
	
	private boolean isFailure(RunResult result)
	{
		if (result.exception != null)
		{
			return true;
		}
		
		if (result.errors != null)
		{
			for (LinterWarning msg : result.errors)
			{
				if (msg.getCode().startsWith("W"))
				{
					if (incorrectSeverity.containsKey(msg.getCode())) return true;
					continue;
				}
				
				if (!incorrectSeverity.containsKey(msg.getCode())) return true;
			}
		}
		
		return false;
	}
	
	private TestResult test(String src)
	{
		boolean expected = hasEarlyError(src);
		boolean parseFailure = false;
		
		List<RunResult> results = forEachVersion(src);
		
		for (RunResult result : results)
		{
			parseFailure = parseFailure || result.parseFailure;
		}
		
		return new TestResult(expected, parseFailure);
	}
	
	/**
	 * A "test expectations" file contains a newline-separated list of file names
	 * with support for pound-sign (`#`) delimited comments. Translate the contents
	 * of such a file into a lookup table--an object whose keys are file names and
	 * whose key-values are the `true` value.
	 */
	private Map<String, Boolean> parseExpectations(String src)
	{
		Map<String, Boolean> memo = new HashMap<String, Boolean>();
		
		for (String line : src.split("\n"))
		{
			line = line.replaceFirst("\\s*#.*$", "").trim();
			if (line.isEmpty())
			{
				continue;
			}
			memo.put(line, true);
		}
		
		return memo;
	}
	
	private List<String> expectedSuccess = new ArrayList<String>();
	private List<String> expectedFailure = new ArrayList<String>();
	private List<String> expectedFalsePositive = new ArrayList<String>();
	private List<String> expectedFalseNegative = new ArrayList<String>();
	private List<String> unexpectedSuccess = new ArrayList<String>();
	private List<String> unexpectedFailure = new ArrayList<String>();
	private List<String> unexpectedFalsePositive = new ArrayList<String>();
	private List<String> unexpectedFalseNegative = new ArrayList<String>();
	private List<String> unexpectedUnrecognized = null;
	private int totalUnexpected = 0;
	private int totalTests = 0;
	
	/**
	 * Normalize directory name separators to be Unix-like forward slashes. Also
	 * condenses repeated slashes to a single slash.
	 *
	 * Source: https://github.com/jonschlinkert/normalize-path
	 */
	private static String normalize(String filePath)
	{
		return filePath.replaceAll("[\\\\\\/]+", "/");
	}
	
	private void interpretResults(List<TestResult> results, Map<String, Boolean> allowed)
	{
		for (TestResult result : results)
		{
			String normalizedName = normalize(result.name);
			boolean isAllowed = allowed.containsKey(normalizedName) && allowed.get(normalizedName);
			allowed.remove(normalizedName);
			
			if (result.parseFailure == result.expected)
			{
				if (isAllowed)
				{
					if (result.parseFailure)
					{
						unexpectedFailure.add(result.name);
					}
					else
					{
						unexpectedSuccess.add(result.name);
					}
				}
				else
				{
					if (result.parseFailure)
					{
						expectedFailure.add(result.name);
					}
					else
					{
						expectedSuccess.add(result.name);
					}
				}
			}
			else
			{
				if (isAllowed)
				{
					if (result.parseFailure)
					{
						expectedFalsePositive.add(result.name);
					}
					else
					{
						expectedFalseNegative.add(result.name);
					}
				}
				else
				{
					if (result.parseFailure)
					{
						unexpectedFalseNegative.add(result.name);
					}
					else
					{
						unexpectedFalsePositive.add(result.name);
					}
				}
			}
		}
		unexpectedUnrecognized = new ArrayList<String>(allowed.keySet());
		totalUnexpected = unexpectedSuccess.size() + unexpectedFailure.size() +
			unexpectedFalsePositive.size() + unexpectedFalseNegative.size() +
			unexpectedUnrecognized.size();
		
		totalTests = results.size();
	}
	
	private String list(List<String> items, String title)
	{
		if (items.size() == 0)
		{
			return null;
		}
		
		List<String> result = new ArrayList<String>(); 
		result.add(title.replace("#", ""+items.size()));
		for (String item : items)
		{
			result.add("- " + item);
		}
		
		return StringUtils.join(result.iterator(), "\n");
	}
	
	private String report(long duration)
	{
		double seconds = duration / 1000.0;
		
		String[] lines = ArrayUtils.removeAllOccurences(new String[]{
			"Results:",
			"",
			totalTests + " total programs parsed in " + new DecimalFormat("#0.00").format(seconds) + " seconds.",
			"",
			expectedSuccess.size() + " valid programs parsed successfully",
			expectedFailure.size() + " invalid programs produced parsing errors",
			expectedFalsePositive.size() + " invalid programs parsed successfully (in accordance with expectations file)",
			expectedFalseNegative.size() + " valid programs produced parsing errors (in accordance with expectations file)",
			"",
			list(unexpectedSuccess, "# valid programs parsed successfully (in violation of expectations file):"),
			list(unexpectedFailure, "# invalid programs produced parsing errors (in violation of expectations file):"),
			list(unexpectedFalsePositive, "# invalid programs parsed successfully (without a corresponding entry in expectations file):"),
			list(unexpectedFalseNegative, "# valid programs produced parsing errors (without a corresponding entry in expectations file):"),
			list(unexpectedUnrecognized, "# programs were referenced by the expectations file but not parsed in this test run:"),
		}, null);
		
		return StringUtils.join(lines, "\n");
	}
	
	private List<String> findTests(String directoryName)
	{
		List<String> tests = new ArrayList<String>();
		findTests(new File(directoryName), tests);
		return tests;
	}
	
	private void findTests(File directory, List<String> tests)
	{
		File[] fileNames = directory.listFiles();
		
		if (fileNames == null || fileNames.length == 0)
		{
			return;
		}
		
		for (File fileName : fileNames)
		{
			String fullName = normalize(fileName.getAbsolutePath());
			if (fileName.isDirectory())
			{
				findTests(fileName, tests);
				continue;
			}
			
			if (Reg.test(testName, fullName))
			{
				tests.add(fullName);
			}
		}
	}
	
	@Test
	public void test262()
	{
		System.out.println("Indexing test files (searching in " + pathTest262 + ").");
		List<String> testNames = findTests(pathTest262);
		System.out.println("Indexing complete (" + testNames.size() + " files found).");
		System.out.println("Testing...");
		
		long start = new Date().getTime();
		List<TestResult> results = new ArrayList<TestResult>();
		
		int count = 0;
		for (String testName : testNames) //TODO: implement parallel test execution
		{ 
			String src = th.readFile(testName);
			
			count++;
			if (count % 1000 == 0)
			{
				System.out.format("%d/%d (%.2f%%)%n", count, testNames.size(), (100.0*count/testNames.size()));
			}
			
			TestResult result = test(src);
			result.name = normalize(Paths.get(pathTest262).relativize(Paths.get(testName)).toString());
			results.add(result);
		}
		
		String src = th.readFile(pathExpectations);
		Map<String, Boolean> expections = parseExpectations(src);
		
		// add additional expections for current java port
		expections.putAll(parseExpectations(th.readFile(pathPortExpectations)));
		
		interpretResults(results, expections);
		String output = report(new Date().getTime() - start);
		
		if (totalUnexpected == 0)
		{
			System.out.println(output);
		}
		else
		{
			fail(output);
		}
	}
	
	private static class RunResult
	{
		private Exception exception;
		private List<LinterWarning> errors;
		private boolean parseFailure = false;
		
		private RunResult(Exception exception, List<LinterWarning> errors)
		{
			this.exception = exception;
			this.errors = errors;
		}
	}
	
	private static class TestResult
	{
		private String name = "";
		private boolean expected = false;
		private boolean parseFailure = false;
		
		private TestResult(boolean expected, boolean parseFailure)
		{
			this.expected = expected;
			this.parseFailure = parseFailure;
		}
	}
}
