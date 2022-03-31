package org.jshint.test.test262;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.JSHint;
import org.jshint.LinterOptions;
import org.jshint.LinterWarning;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Includes port of Node.JS library [results-interpreter v1.0.0]
 */
public class Test262 extends Assert {
	private static final Pattern commentPattern = Pattern.compile("#.*$");

	/**
	 * JSHint "error" messages generally indicate a parsing failure and "warning"
	 * messages generally indicate more objective problems with technically-valid
	 * programs. This convention is not consistently honored, however, so
	 * interpreting parsing success/failure from message code requires the
	 * following list of "contradictory" codes.
	 */
	private static final String[] incorrectSeverity = {
			"E007",
			// E013 describes a runtime error: the modification of a constant binding.
			// Although (unlike the other errors referenced in this object) this
			// condition is guaranteed to produce errors, it is not technically an early
			// error and should therefore be ignored when interpreting Test262 tests.
			"E013",
			"E034",

			"W024",
			"W025",
			"W052",
			"W076",
			"W077",
			"W090",
			"W094",
			"W095",
			"W112",
			"W115",
			"W130",
			"W131",
			"W133",
			"W136"
	};

	private static final Path expectationsFile = Paths.get(System.getProperty("user.dir"),
			"/src/test/resources/test262/expectations.txt");
	private static final Path javaExpectationsFile = Paths.get(System.getProperty("user.dir"),
			"/src/test/resources/test262/java-expectations.txt");
	private static final Test262Stream stream = new Test262Stream.Builder(
			Paths.get(System.getProperty("user.dir"), "/src/test/resources/test262/test262"))
			.omitRuntime()
			.build();
	private static final AtomicInteger count = new AtomicInteger(0);

	private List<String> whitelist;
	private boolean passed = true;
	private List<String> allowedSuccess = new ArrayList<>();
	private List<String> allowedFailure = new ArrayList<>();
	private List<String> allowedFalsePositive = new ArrayList<>();
	private List<String> allowedFalseNegative = new ArrayList<>();
	private List<String> disallowedSuccess = new ArrayList<>();
	private List<String> disallowedFailure = new ArrayList<>();
	private List<String> disallowedFalsePositive = new ArrayList<>();
	private List<String> disallowedFalseNegative = new ArrayList<>();
	private List<String> unrecognized = Collections.emptyList();

	@Test
	public void test262() throws IOException {
		System.out.println("Now running tests...");

		String contents = new String(Files.readAllBytes(expectationsFile), StandardCharsets.UTF_8);
		String javaContents = new String(Files.readAllBytes(javaExpectationsFile), StandardCharsets.UTF_8);
		whitelist = parseWhitelist(contents, javaContents);

		stream
				.onTest(test -> {
					int c = count.incrementAndGet();
					if (c % 2000 == 0) {
						System.out.println("Completed " + c + " tests.");
					}

					String id = normalizePath(test.getFile()) + "(" + test.getScenario() + ")";
					TestStatus expected = "parse".equals(test.getAttrs().getNegativePhase()) ? TestStatus.FAIL
							: TestStatus.PASS;
					TestStatus actual = runTest(test) ? TestStatus.PASS : TestStatus.FAIL;

					interpret(id, expected, actual);
				})
				.onError(error -> {
					System.out.println(error);
					System.exit(1);
				})
				.onFinish(() -> {
					unrecognized = new ArrayList<>(whitelist);
					if (unrecognized.size() > 0) {
						passed = false;
					}

					report();

					if (!passed) {
						fail("Test262 failed!");
					}
				})
				.run();
	}

	// JSHINT_BUG: in original results-interpreter/src/whitelist.js file this is
	// parsed incorrectly - comment lines are not filtered out
	private List<String> parseWhitelist(String contents, String javaContents) {
		List<String> result = Arrays.stream(contents.split("\n", -1))
				.map(line -> commentPattern.matcher(line).replaceFirst("").trim())
				.filter(line -> line.length() > 0)
				.collect(toList());
		result.addAll(Arrays.stream(javaContents.split("\n", -1))
				.map(line -> commentPattern.matcher(line).replaceFirst("").trim())
				.filter(line -> line.length() > 0)
				.filter(line -> !result.remove(line))
				.collect(toList()));
		return result;
	}

	private String normalizePath(Path path) {
		return StringUtils.replace(path.toString(), "\\", "/");
	}

	private boolean runTest(Test262File test) {
		JSHint jshint = new JSHint();
		boolean isModule = test.getAttrs().isModule();

		try {
			jshint.lint(test.getContents(), new LinterOptions()
					.set("esversion", 11)
					.set("maxerr", Integer.MAX_VALUE)
					.set("module", isModule));
		} catch (Exception e) {
			return false;
		}

		return !isFailure(jshint.generateSummary().getErrors());
	}

	private boolean isFailure(List<LinterWarning> errors) {
		return errors != null && errors.stream().filter(msg -> {
			if (msg.getCode().startsWith("W")) {
				return ArrayUtils.contains(incorrectSeverity, msg.getCode());
			} else if (msg.getCode().startsWith("I")) {
				return false;
			}

			return !ArrayUtils.contains(incorrectSeverity, msg.getCode());
		}).findAny().isPresent();
	}

	private synchronized void interpret(String id, TestStatus expected, TestStatus actual) {
		boolean inWhitelist = whitelist.contains(id);
		boolean isAllowed = false;
		List<String> classification;

		whitelist.remove(id);

		if (expected == TestStatus.PASS) {
			if (actual == TestStatus.PASS) {
				isAllowed = !inWhitelist;
				classification = isAllowed ? allowedSuccess : disallowedSuccess;
			} else {
				isAllowed = inWhitelist;
				classification = isAllowed ? allowedFalseNegative : disallowedFalseNegative;
			}
		} else {
			if (actual == TestStatus.PASS) {
				isAllowed = inWhitelist;
				classification = isAllowed ? allowedFalsePositive : disallowedFalsePositive;
			} else {
				isAllowed = !inWhitelist;
				classification = isAllowed ? allowedFailure : disallowedFailure;
			}
		}

		passed = passed && isAllowed;
		classification.add(id);
	}

	private void report() {
		String[] goodnews = {
				allowedSuccess.size() + " valid programs parsed without error",
				allowedFailure.size() + " invalid programs produced a parsing error",
				allowedFalsePositive.size()
						+ " invalid programs did not produce a parsing error (and allowed by the expectations file)",
				allowedFalseNegative.size()
						+ " valid programs produced a parsing error (and allowed by the expectations file)"
		};
		List<String> badnews = new ArrayList<>();
		List<String> badnewsDetails = new ArrayList<>();

		badnews(disallowedSuccess, "valid programs parsed without error (in violation of the expectations file)",
				badnews, badnewsDetails);
		badnews(disallowedFailure, "invalid programs produced a parsing error (in violation of the expectations file)",
				badnews, badnewsDetails);
		badnews(disallowedFalsePositive,
				"invalid programs did not produce a parsing error (without a corresponding entry in the expectations file)",
				badnews, badnewsDetails);
		badnews(disallowedFalseNegative,
				"valid programs produced a parsing error (without a corresponding entry in the expectations file)",
				badnews, badnewsDetails);
		badnews(unrecognized, "non-existent programs specified in the expectations file", badnews, badnewsDetails);

		System.out.println("Testing complete.");
		System.out.println();
		System.out.println("Summary:");
		Arrays.stream(goodnews).forEach(line -> System.out.println(" v " + line));

		if (!passed) {
			System.out.println("");
			badnews.stream().forEach(line -> System.out.println(" X " + line));
			System.out.println("");
			System.out.println("Details:");
			badnewsDetails.stream().forEach(line -> System.out.println("   " + line));
		}
	}

	private void badnews(List<String> tests, String label, List<String> badnews, List<String> badnewsDetails) {
		if (tests.size() == 0)
			return;

		String desc = tests.size() + " " + label;

		badnews.add(desc);
		badnewsDetails.add(desc + ":");
		badnewsDetails.addAll(tests);
	}

	private static enum TestStatus {
		PASS,
		FAIL
	}
}