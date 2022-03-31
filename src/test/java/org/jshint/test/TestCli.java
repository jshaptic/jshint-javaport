package org.jshint.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.commons.lang3.StringUtils;
import org.jshint.Cli;
import org.jshint.Cli.RunOptions;
import org.jshint.JSHintException;
import org.jshint.DataSummary;
import org.jshint.LinterWarning;
import org.jshint.Reg;
import org.jshint.reporters.CheckstyleReporter;
import org.jshint.reporters.JSHintReporter;
import org.jshint.reporters.JslintXmlReporter;
import org.jshint.reporters.NonErrorReporter;
import org.jshint.reporters.ReporterResult;
import org.jshint.test.helpers.CliWrapper;
import org.jshint.utils.IOUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestCli extends Assert {
	@BeforeClass
	public void compileSimpleReporter() {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		compiler.run(null, null, null, "src/test/resources/examples/SimpleReporter.java");
	}

	public CliWrapper setUpGroupCli() {
		CliWrapper cli = new CliWrapper();
		cli.stubExit();
		return cli;
	}

	public CliWrapper setUpGroupConfigCli() {
		CliWrapper cli = setUpGroupCli();

		cli.stubCat(path -> {
			if (path.endsWith("file.js"))
				return "var a = function () {}; a();";
			if (path.endsWith("file1.json"))
				return "wat";
			if (path.endsWith("file2.json"))
				return "{\"node\":true,\"globals\":{\"foo\":true,\"bar\":true}}";
			if (path.endsWith("file4.json"))
				return "{\"extends\":\"file3.json\"}";
			if (path.endsWith("file5.json"))
				return "{\"extends\":\"file2.json\"}";
			if (path.endsWith("file6.json"))
				return "{\"extends\":\"file2.json\",\"node\":false}";
			if (path.endsWith("file7.json"))
				return "{\"extends\":\"file2.json\",\"globals\":{\"bar\":false,\"baz\":true}}";
			if (path.endsWith("file8.json"))
				return "{\"extends\":\"file7.json\",\"overrides\":{\"file.js\":{\"globals\":{\"foo\":true,\"bar\":true}}}}";
			if (path.endsWith("file9.json"))
				return "{\"extends\":\"file8.json\",\"overrides\":{\"file.js\":{\"globals\":{\"baz\":true,\"bar\":false}}}}";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("file.js"))
				return true;
			if (path.endsWith("file1.json"))
				return true;
			if (path.endsWith("file2.json"))
				return true;
			if (path.endsWith("file3.json"))
				return false;
			if (path.endsWith("file4.json"))
				return true;
			if (path.endsWith("file5.json"))
				return true;
			if (path.endsWith("file6.json"))
				return true;
			return false;
		});

		return cli;
	}

	public CliWrapper setUpExtractCli() {
		CliWrapper cli = new CliWrapper();
		cli.stubExit();
		return cli;
	}

	public CliWrapper setUpUseStdinCli() {
		CliWrapper cli = new CliWrapper();
		cli.stubStdin();
		cli.stubExit();
		return cli;
	}

	@Test(groups = { "group", "config" })
	public void testGroupConfigNormal() {
		CliWrapper cli = setUpGroupConfigCli();
		cli.stubRun();

		// Merges existing valid files
		cli.interpret("file.js", "--config", "file5.json");
		assertEquals(cli.getConfig().asBoolean("node"), true);
		assertFalse(cli.getConfig().test("extends"));

		// Overwrites options after extending
		cli.interpret("file.js", "--config", "file6.json");
		assertEquals(cli.getConfig().asBoolean("node"), false);

		// Valid config
		cli.interpret("file.js", "--config", "file2.json");

		// Performs a deep merge of configuration
		cli.interpret("file2.js", "--config", "file7.json");
		assertEquals(cli.getConfig().get("globals").asBoolean("foo"), true);
		assertEquals(cli.getConfig().get("globals").asBoolean("bar"), false);
		assertEquals(cli.getConfig().get("globals").asBoolean("baz"), true);

		// Performs a deep merge of configuration with overrides
		cli.interpret("file.js", "--config", "file8.json");
		assertEquals(cli.getConfig().get("overrides").get("file.js").get("globals").asBoolean("foo"), true);
		assertEquals(cli.getConfig().get("overrides").get("file.js").get("globals").asBoolean("bar"), true);

		// Performs a deep merge of configuration with overrides for the same glob
		cli.interpret("file.js", "--config", "file9.json");
		assertEquals(cli.getConfig().get("overrides").get("file.js").get("globals").asBoolean("foo"), true);
		assertEquals(cli.getConfig().get("overrides").get("file.js").get("globals").asBoolean("bar"), false);
		assertEquals(cli.getConfig().get("overrides").get("file.js").get("globals").asBoolean("baz"), true);
	}

	@Test(groups = { "group", "config" })
	public void testGroupConfigFailure() {
		CliWrapper cli = setUpGroupConfigCli();
		cli.restoreExit();

		String msg;

		// File doesn't exist.
		cli.interpret("file.js", "--config", "file3.json");
		msg = cli.getErrorMessages().get(0);
		assertEquals(msg.substring(0, 23), "Can't find config file:");
		assertEquals(msg.substring(msg.length() - 10), "file3.json");

		// Invalid config
		cli.interpret("file.js", "--config", "file1.json");
		msg = cli.getErrorMessages().get(1);
		assertEquals(msg.substring(0, 24), "Can't parse config file:");
		assertEquals(msg.substring(25, 35), "file1.json");

		// Invalid merged filed
		cli.interpret("file.js", "--config", "file4.json");
		msg = cli.getErrorMessages().get(2);
		assertEquals(msg.substring(0, 23), "Can't find config file:");
		assertEquals(msg.substring(msg.length() - 10), "file3.json");
	}

	@Test(groups = { "group" })
	public void testGroupPrereq() {
		CliWrapper cli = setUpGroupCli();

		cli.stubCat(path -> {
			if (path.endsWith("file.js"))
				return "a();";
			if (path.endsWith("prereq.js"))
				return "var a = 1;";
			if (path.endsWith("config.json"))
				return "{\"undef\":true,\"prereq\":[\"prereq.js\", \"prereq2.js\"]}";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("file.js"))
				return true;
			if (path.endsWith("prereq.js"))
				return true;
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		cli.interpret("file.js", "--config", "config.json");
		assertNotEquals(cli.getExitCode(), 2, "ProcessExit");
	}

	// CLI prereqs
	@Test(groups = { "group" })
	public void testGroupPrereqCLIOption() {
		CliWrapper cli = setUpGroupCli();

		cli.stubCat(path -> {
			if (path.endsWith("file.js"))
				return "a();";
			if (path.endsWith("prereq.js"))
				return "var a = 1;";
			if (path.endsWith("config.json"))
				return "{\"undef\":true}";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("file.js"))
				return true;
			if (path.endsWith("prereq.js"))
				return true;
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		cli.interpret("file.js", "--config", "config.json", "--prereq", "prereq.js  , prereq2.js");
		assertNotEquals(cli.getExitCode(), 2, "ProcessExit");
	}

	// CLI prereqs should get merged with config prereqs
	@Test(groups = { "group" })
	public void testGroupPrereqBothConfigAndCLIOption() {
		CliWrapper cli = setUpGroupCli();

		cli.stubCat(path -> {
			if (path.endsWith("file.js"))
				return "a(); b();";
			if (path.endsWith("prereq.js"))
				return "var a = 1;";
			if (path.endsWith("prereq2.js"))
				return "var b = 2;";
			if (path.endsWith("config.json"))
				return "{\"undef\":true,\"prereq\":[\"prereq.js\"]}";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("file.js"))
				return true;
			if (path.endsWith("prereq.js"))
				return true;
			if (path.endsWith("prereq2.js"))
				return true;
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		cli.interpret("file.js", "--config", "config.json", "--prereq", "prereq2.js,prereq3.js");
		assertNotEquals(cli.getExitCode(), 2, "ProcessExit");
	}

	// Overrides should work for files in the current directory
	@Test(groups = { "group" })
	public void testGroupOverrides() {
		CliWrapper cli = setUpGroupCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		String config = "{ \"asi\": true, \"overrides\": { \"bar.js\": { \"asi\": false } } }";

		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("foo.js"))
				return "a()";
			if (path.endsWith("bar.js"))
				return "a()";
			if (path.endsWith("config.json"))
				return config;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("foo.js"))
				return true;
			if (path.endsWith("bar.js"))
				return true;
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		// Test successful file
		cli.interpret("foo.js", "--config", "config.json", "--reporter", "SimpleReporter");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);

		// Test overridden, failed file
		cli.interpret("bar.js", "--config", "config.json", "--reporter", "SimpleReporter");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");
	}

	// Overrides should work for implicit relative paths (without a leading ./)
	@Test(groups = { "group" })
	public void testGroupOverridesMatchesImplicitRelativePaths() {
		CliWrapper cli = setUpGroupCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		String config = "{ \"asi\": true, \"overrides\": { \"src/bar.js\": { \"asi\": false } } }";

		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("foo.js"))
				return "a()";
			if (path.endsWith("bar.js"))
				return "a()";
			if (path.endsWith("config.json"))
				return config;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("foo.js"))
				return true;
			if (path.endsWith("bar.js"))
				return true;
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		// Test successful file
		cli.interpret("src/foo.js", "--config", "config.json", "--reporter", "SimpleReporter");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);

		// Test overridden, failed file
		cli.interpret("src/bar.js", "--config", "config.json", "--reporter", "SimpleReporter");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");
	}

	// Overrides should work for explicit relative paths (with a leading ./)
	@Test(groups = { "group" })
	public void testGroupOverridesMatchesExlicitRelativePaths() {
		CliWrapper cli = setUpGroupCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		String config = "{ \"asi\": true, \"overrides\": { \"src/bar.js\": { \"asi\": false } } }";

		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("foo.js"))
				return "a()";
			if (path.endsWith("bar.js"))
				return "a()";
			if (path.endsWith("config.json"))
				return config;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("foo.js"))
				return true;
			if (path.endsWith("bar.js"))
				return true;
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		// Test successful file
		cli.interpret("./src/foo.js", "--config", "config.json", "--reporter", "SimpleReporter");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);

		// Test overridden, failed file
		cli.interpret("./src/bar.js", "--config", "config.json", "--reporter", "SimpleReporter");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");
	}

	@Test(groups = { "group" })
	public void testGroupReporter() {
		CliWrapper cli = setUpGroupCli();

		cli.stubRun();
		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		cli.restoreExit();
		cli.stubStdout();

		// Test failed attempt.
		cli.interpret("file.js", "--reporter", "invalid");
		String msg = cli.getErrorMessages().get(0);
		assertEquals(msg.substring(0, 25), "Can't load reporter file:");
		assertEquals(msg.substring(msg.length() - 7), "invalid");

		// Test successful attempt.
		cli.restoreRun();
		cli.restoreStdout();

		cli.stubExists(path -> {
			if (path.endsWith("file.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			if (path.endsWith("file.js"))
				return "func()";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js", "--reporter", "SimpleReporter");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getRaw(), "Missing semicolon.");
		assertTrue(cli.getTestReporter().isCalledOnce());
	}

	@Test(groups = { "group" })
	public void testGroupJSLintReporter() {
		CliWrapper cli = setUpGroupCli();
		cli.stubRun();

		cli.interpret("file.js", "--reporter", "jslint");
		assertEquals(cli.getReporter().getClass(), JslintXmlReporter.class);

		cli.interpret("file.js", "--jslint-reporter");
		assertEquals(cli.getReporter().getClass(), JslintXmlReporter.class);
	}

	@Test(groups = { "group" })
	public void testGroupCheckStyleReporter() {
		CliWrapper cli = setUpGroupCli();
		cli.stubRun();

		cli.interpret("file.js", "--reporter", "checkstyle");
		assertEquals(cli.getReporter().getClass(), CheckstyleReporter.class);

		cli.interpret("file.js", "--checkstyle-reporter");
		assertEquals(cli.getReporter().getClass(), CheckstyleReporter.class);
	}

	@Test(groups = { "group" })
	public void testGroupShowNonErrors() {
		CliWrapper cli = setUpGroupCli();
		cli.stubRun();

		cli.interpret("file.js", "--show-non-errors");
		assertEquals(cli.getReporter().getClass(), NonErrorReporter.class);
	}

	@Test(groups = { "group" })
	public void testGroupExtensions() {
		CliWrapper cli = setUpGroupCli();
		cli.stubRun();

		cli.interpret("file.js");
		assertEquals(cli.getExtensions(), "");

		cli.interpret("file.js", "--extra-ext", ".json");
		assertEquals(cli.getExtensions(), ".json");
	}

	@Test(groups = { "group" })
	public void testGroupMalformedNpmFile() {
		CliWrapper cli = setUpGroupCli();

		cli.stubCwd(() -> System.getProperty("user.dir"));
		String localNpm = IOUtils.getPathUtils().normalize(System.getProperty("user.dir") + "/package.json");
		String localRc = IOUtils.getPathUtils().normalize(System.getProperty("user.dir") + "/.jshintrc");

		cli.stubExists(path -> {
			if (path.equals(localRc))
				return true;
			if (path.equals(localNpm))
				return true;
			if (path.endsWith("file.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			// stub rc file
			if (path.equals(localRc))
				return "{\"evil\": true}";
			// stub npm file
			if (path.equals(localNpm))
				return "{"; // malformed package.json
			// stub src file
			if (path.endsWith("file.js"))
				return "eval('a=2');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // lint with wrong package.json
	}

	@Test(groups = { "group" })
	public void testGroupRcFile() {
		CliWrapper cli = setUpGroupCli();

		cli.stubCwd(() -> System.getProperty("user.dir"));
		String localRc = IOUtils.getPathUtils().normalize(System.getProperty("user.dir") + "/.jshintrc");

		cli.stubExists(path -> {
			if (path.equals(localRc))
				return true;
			if (path.endsWith("file.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			// stub rc file
			if (path.equals(localRc))
				return "{\"evil\": true}";
			// stub src file
			if (path.endsWith("file.js"))
				return "eval('a=2');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}

	@Test(groups = { "group" })
	public void testGroupHomeRcFile() {
		CliWrapper cli = setUpGroupCli();

		String homeRc = IOUtils.getPathUtils().join(System.getProperty("user.home"), ".jshintrc");

		cli.stubExists(path -> {
			if (path.equals(homeRc))
				return true;
			if (path.endsWith("/file.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			// stub rc file
			if (path.equals(homeRc))
				return "{\"evil\": true}";
			// stub src file (in root where we are unlikely to find a .jshintrc)
			if (path.endsWith("/file.js"))
				return "eval('a=2');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("/file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}

	@Test(groups = { "group" })
	public void testGroupNoHomeDir() {
		CliWrapper cli = setUpGroupCli();

		String prevEnv = System.getProperty("user.home");

		// Remove all home dirs from env.
		System.setProperty("user.home", "");

		cli.stubCwd(() -> System.getProperty("user.dir"));
		String localRc = IOUtils.getPathUtils().normalize(System.getProperty("user.dir") + "/.jshintrc");

		cli.stubExists(path -> {
			if (path.equals(localRc))
				return true;
			if (path.endsWith("file.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			// stub rc file
			if (path.equals(localRc))
				return "{\"evil\": true}";
			// stub src file
			if (path.endsWith("file.js"))
				return "eval('a=2');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found

		// Restore environemnt
		System.setProperty("user.home", prevEnv);
	}

	@Test(groups = { "group" })
	public void testGroupOneLevelRcLookup() {
		CliWrapper cli = setUpGroupCli();

		String srcDir = System.getProperty("user.dir") + "/../src/";
		String parentRc = IOUtils.getPathUtils().join(srcDir, ".jshintrc");

		String cliDir = IOUtils.getPathUtils().join(srcDir, "cli/");
		cli.stubCwd(() -> cliDir);

		cli.stubExists(path -> {
			if (path.equals(parentRc))
				return true;
			if (path.endsWith("file.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			// stub rc file
			if (path.equals(parentRc))
				return "{\"evil\": true}";
			// stub src file
			if (path.endsWith("file.js"))
				return "eval('a=2');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}

	@Test(groups = { "group" })
	public void testGroupTargetRelativeRcLookup() {
		CliWrapper cli = setUpGroupCli();

		// working from outside the project
		cli.stubCwd(() -> System.getProperty("user.home"));
		String projectRc = IOUtils.getPathUtils().normalize(System.getProperty("user.dir") + "/.jshintrc");
		String srcFile = System.getProperty("user.dir") + "/sub/file.js";

		cli.stubExists(path -> {
			if (path.equals(projectRc))
				return true;
			if (path.endsWith("file.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			// stub rc file
			if (path.equals(projectRc))
				return "{\"evil\": true}";
			// stub src file
			if (path.endsWith("file.js"))
				return "eval('a=2');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret(srcFile);
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}

	@Test(groups = { "group" })
	public void testGroupIgnores() {
		CliWrapper cli = setUpGroupCli();

		cli.stubRun();
		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		cli.interpret("file.js", "--exclude=exclude.js");
		assertEquals(cli.getIgnores().get(0), IOUtils.getPathUtils().resolve(dir, "exclude.js"));
		assertEquals(cli.getIgnores().get(1), IOUtils.getPathUtils().resolve(dir, "ignored.js"));
		assertEquals(cli.getIgnores().get(2), IOUtils.getPathUtils().resolve(dir, "another.js"));
	}

	@Test(groups = { "group" })
	public void testGroupIgnoresWithSpecialChars() {
		CliWrapper cli = setUpGroupCli();
		cli.stubCwd(() -> IOUtils.getPathUtils().resolve(System.getProperty("user.dir"), "special++chars"));
		cli.stubExists(path -> {
			if (".".equals(path))
				return true;
			return false;
		});
		cli.stubIsDirectory(path -> {
			if (".".equals(path))
				return true;
			return false;
		});
		cli.stubLs(path -> {
			if (".".equals(path))
				return Collections.emptyList();
			return null;
		});

		cli.interpret(".", "--exclude=exclude1.js");
	}

	@Test(groups = { "group" })
	public void testGroupMultipleIgnores() {
		CliWrapper cli = setUpGroupCli();

		cli.stubRun();
		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		cli.interpret("file.js", "--exclude=foo.js,bar.js");
		assertEquals(cli.getIgnores().get(0), IOUtils.getPathUtils().resolve(dir, "foo.js"));
		assertEquals(cli.getIgnores().get(1), IOUtils.getPathUtils().resolve(dir, "bar.js"));
	}

	// See gh-3187
	@Test(groups = { "group" })
	public void testGroupIgnoreWithDot() {
		CliWrapper cli = setUpGroupCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("file.js"))
				return "This is not Javascript.";
			if (path.endsWith(".jshintignore"))
				return "**/ignored-dir/**";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("file.js"))
				return true;
			if (path.endsWith(".jshintignore"))
				return true;
			return false;
		});

		cli.interpret("ignored-dir/.dot-prefixed/file.js", "ignored-dir/not-dot-prefixed/file.js");
		assertEquals(cli.getExitCode(), 0, "All matching files are ignored, regardless of dot-prefixed directories.");
	}

	@Test(groups = { "group" })
	public void testGroupExcludePath() {
		CliWrapper cli = setUpGroupCli();

		cli.stubRun();
		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		cli.interpret("file.js", "--exclude-path=../examples/.customignore");
		assertEquals(cli.getIgnores().get(0), IOUtils.getPathUtils().resolve(dir, "exclude.js"));
	}

	@Test(groups = { "group" })
	public void testGroupAPIIgnores() throws JSHintException, Cli.ExitException, IOException {
		CliWrapper cli = setUpGroupCli();

		String dir = System.getProperty("user.dir") + "/src/main/";
		cli.stubCwd(() -> dir);
		List<ReporterResult> result = new ArrayList<>();

		Cli.RunOptions opts = new Cli.RunOptions();
		opts.setArgs(new String[] { "../test/resources/fixtures/ignored.js" });
		opts.setCwd(dir + "../test/resources/fixtures");
		opts.setReporter(new JSHintReporter() {
			@Override
			public void generate(List<ReporterResult> results, List<DataSummary> data, String verbose) {
				result.addAll(results);
			}
		});

		cli.run(opts);

		assertTrue(result.size() == 0);
	}

	@Test(groups = { "group" })
	public void testGroupCollectFiles() {
		CliWrapper cli = setUpGroupCli();

		AtomicReference<RunOptions> args = new AtomicReference<>();

		cli.stubGather(opts -> {
			args.set(opts);
			return Collections.emptyList();
		});

		cli.stubExists(path -> {
			return true;
		});

		cli.stubCat(path -> {
			if (path.endsWith("file.js") || path.endsWith("file2.js"))
				return "console.log('Hello');";
			if (Reg.test(Pattern.compile("ignore[\\/\\\\]file\\d\\.js$"), path))
				return "console.log('Hello, ignore me');";
			if (Reg.test(Pattern.compile("ignore[\\/\\\\]dir[\\/\\\\]file\\d\\.js$"), path))
				return "print('Ignore me');";
			if (path.endsWith("node_script"))
				return "console.log('Hello, ignore me');";
			if (path.endsWith(".jshintignore"))
				return IOUtils.getPathUtils().join("ignore", "**");
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js", "file2.js", "node_script", IOUtils.getPathUtils().join("ignore", "file1.js"),
				IOUtils.getPathUtils().join("ignore", "file2.js"),
				IOUtils.getPathUtils().join("ignore", "dir", "file1.js"));

		assertEquals(args.get().getArgs()[0], "file.js");
		assertEquals(args.get().getArgs()[1], "file2.js");
		assertEquals(args.get().getArgs()[2], "node_script");
		assertEquals(args.get().getArgs()[3], IOUtils.getPathUtils().join("ignore", "file1.js"));
		assertEquals(args.get().getArgs()[4], IOUtils.getPathUtils().join("ignore", "file2.js"));
		assertEquals(args.get().getArgs()[5], IOUtils.getPathUtils().join("ignore", "dir", "file1.js"));
		assertEquals(args.get().getIgnores(),
				Arrays.asList(IOUtils.getPathUtils().resolve(IOUtils.getPathUtils().join("ignore", "**"))));

		cli.restoreCat();

		cli.stubIsDirectory(path -> {
			if (path.endsWith("src"))
				return true;
			if (Reg.test(Pattern.compile("src[\\/\\\\]lib$"), path))
				return true;
			return false;
		});

		cli.stubLs(path -> {
			if (path.endsWith("src"))
				return Arrays.asList("lib", "file4.js");
			if (Reg.test(Pattern.compile("src[\\/\\\\]lib$"), path))
				return Arrays.asList("file5.js");
			return null;
		});

		cli.stubCat(path -> {
			if (path.endsWith("file.js") || path.endsWith("file2.js"))
				return "console.log('Hello');";
			if (path.endsWith("file3.json"))
				return "{}";
			if (Reg.test(Pattern.compile("src[\\/\\\\]file4\\.js$"), path))
				return "print('Hello');";
			if (Reg.test(Pattern.compile("src[\\/\\\\]lib[\\/\\\\]file5\\.js$"), path))
				return "print('Hello');";
			if (path.endsWith(".jshintignore"))
				return "";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js", "file2.js", "file3.json", "--extra-ext=json", "src");

		assertEquals(args.get().getArgs().length, 4);
		assertEquals(args.get().getArgs()[0], "file.js");
		assertEquals(args.get().getArgs()[1], "file2.js");
		assertEquals(args.get().getArgs()[2], "file3.json");
		assertEquals(args.get().getArgs()[3], "src");
		assertEquals(args.get().getIgnores(), Collections.emptyList());

		cli.stubCat(path -> {
			if (path.endsWith("file.js") || path.endsWith("file2.js"))
				return "console.log('Hello');";
			if (path.endsWith("file3.json"))
				return "{}";
			if (Reg.test(Pattern.compile("src[\\/\\\\]file4\\.js$"), path))
				return "print('Hello');";
			if (Reg.test(Pattern.compile("src[\\/\\\\]lib[\\/\\\\]file5\\.js$"), path))
				return "print('Hello');";
			if (path.endsWith(".jshintignore"))
				return "";
			if (path.endsWith("reporter.js"))
				return "console.log('Hello');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("examples");

		assertEquals(args.get().getArgs().length, 1);
		assertEquals(args.get().getArgs()[0], "examples");
		assertEquals(args.get().getIgnores().size(), 0);
	}

	@Test(groups = { "group" })
	public void testGroupGatherOptionalParameters() throws IOException {
		CliWrapper cli = setUpGroupCli();

		cli.stubExists(path -> {
			if (path.endsWith("file.js"))
				return true;
			return false;
		});

		Cli.RunOptions opts = new Cli.RunOptions();
		opts.setArgs(new String[] { "file.js" });

		List<String> files = cli.gather(opts);

		assertEquals(files.size(), 1);
		assertEquals(files.get(0), "file.js");
	}

	@Test(groups = { "group" })
	public void testGroupGather() throws IOException {
		CliWrapper cli = setUpGroupCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		Map<Pattern, String> demoFiles = new HashMap<>();
		demoFiles.put(Pattern.compile("file2?\\.js$"), "console.log('Hello');");
		demoFiles.put(Pattern.compile("ignore[\\/\\\\]file\\d\\.js$"), "console.log('Hello, ignore me');");
		demoFiles.put(Pattern.compile("ignore[\\/\\\\]dir[\\/\\\\]file\\d\\.js$"), "print('Ignore me');");
		demoFiles.put(Pattern.compile("node_script$"), "console.log('Hello, ignore me');");

		cli.stubExists(path -> {
			for (Pattern file : demoFiles.keySet())
				if (Reg.test(file, path))
					return true;
			return false;
		});

		cli.stubCat(path -> {
			for (Pattern file : demoFiles.keySet())
				if (Reg.test(file, path))
					return demoFiles.get(file);
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		Cli.RunOptions opts = new Cli.RunOptions();
		opts.setArgs(new String[] { "file.js", "file2.js", "node_script",
				IOUtils.getPathUtils().join("ignore", "file1.js"),
				IOUtils.getPathUtils().join("ignore", "file2.js"),
				IOUtils.getPathUtils().join("ignore", "dir", "file1.js")
		});
		opts.setIgnores(Arrays.asList(IOUtils.getPathUtils().join("ignore", "**")));
		opts.setExtensions("");
		List<String> files = cli.gather(opts);

		assertEquals(files.size(), 3);
		assertEquals(files.get(0), "file.js");
		assertEquals(files.get(1), "file2.js");
		assertEquals(files.get(2), "node_script");

		// demoFiles.put(new String[] {"file2?\\.js$", "console.log('Hello');"});
		// JSHINT_BUG: copy paste
		demoFiles.put(Pattern.compile("file3\\.json$"), "{}");
		demoFiles.put(Pattern.compile("src[\\/\\\\]file4\\.js$"), "print('Hello');");
		demoFiles.put(Pattern.compile("src[\\/\\\\]lib[\\/\\\\]file5\\.js$"), "print('Hello'); ");

		cli.stubExists(path -> {
			for (Pattern file : demoFiles.keySet())
				if (Reg.test(file, path))
					return true;
			if (path.endsWith("src"))
				return true;
			if (Reg.test(Pattern.compile("src[\\/\\\\]lib$"), path))
				return true;
			return false;
		});

		cli.stubIsDirectory(path -> {
			if (path.endsWith("src"))
				return true;
			if (Reg.test(Pattern.compile("src[\\/\\\\]lib$"), path))
				return true;
			return false;
		});

		cli.stubLs(path -> {
			if (path.endsWith("src"))
				return Arrays.asList("lib", "file4.js");
			if (Reg.test(Pattern.compile("src[\\/\\\\]lib$"), path))
				return Arrays.asList("file5.js");
			return null;
		});

		cli.stubCat(path -> {
			for (Pattern file : demoFiles.keySet())
				if (Reg.test(file, path))
					return demoFiles.get(file);
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("file.js", "file2.js", "file3.json", "--extra-ext=json", "src");

		opts = new Cli.RunOptions();
		opts.setArgs(new String[] { "file.js", "file2.js", "file3.json", "src" });
		opts.setExtensions("json");
		opts.setIgnores(Collections.emptyList());
		files = cli.gather(opts);

		assertEquals(files.size(), 5);
		assertEquals(files.get(0), "file.js");
		assertEquals(files.get(1), "file2.js");
		assertEquals(files.get(2), "file3.json");
		assertEquals(files.get(3), IOUtils.getPathUtils().join("src", "lib", "file5.js"));
		assertEquals(files.get(4), IOUtils.getPathUtils().join("src", "file4.js"));

		cli.restoreExists();
		cli.restoreIsDirectory();
		cli.restoreLs();
		cli.restoreCat();
		cli.restoreCwd();

		cli.stubCwd(() -> System.getProperty("user.dir") + "/src/test/resources/");
		cli.stubCat(path -> {
			if (path.endsWith("reporter.js"))
				return "console.log('Hello');";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		opts = new Cli.RunOptions();
		opts.setArgs(new String[] { "examples" });
		opts.setExtensions("json");
		opts.setIgnores(Collections.emptyList());
		files = cli.gather(opts);

		assertEquals(files.size(), 1);
		assertEquals(files.get(0), IOUtils.getPathUtils().join("examples", "reporter.js"));
	}

	@Test(groups = { "group" })
	public void testGroupStatusCode() {
		CliWrapper cli = setUpGroupCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		cli.stubExists(path -> {
			if (path.endsWith("pass.js"))
				return true;
			if (path.endsWith("fail.js"))
				return true;
			return false;
		});

		cli.stubCat(path -> {
			if (path.endsWith("pass.js"))
				return "function test() { return 0; }";
			if (path.endsWith("fail.js"))
				return "console.log('Hello')";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.interpret("pass.js", "--reporter=SimpleReporter");
		assertEquals(cli.getExitCode(), 0);

		cli.interpret("fail.js", "--reporter=SimpleReporter");
		assertEquals(cli.getExitCode(), 2);
	}

	@Test(groups = { "extract" })
	public void testExtractBasic() {
		CliWrapper cli = setUpExtractCli();

		String html = "<html>text<script>var a = 1;</script></html>";
		String text = "hello world";
		String js = "var a = 1;";

		assertEquals(cli.extract(html, "never"), html);
		assertEquals(cli.extract(html, "auto"), js);
		assertEquals(cli.extract(html, "always"), js);

		assertEquals(cli.extract(js, "never"), js);
		assertEquals(cli.extract(js, "auto"), js);
		assertEquals(cli.extract(js, "always"), "");

		assertEquals(cli.extract(text, "never"), text);
		assertEquals(cli.extract(text, "auto"), text);
		assertEquals(cli.extract(text, "always"), "");

		html = StringUtils.join(new String[] {
				"<html>",
				"<script type='text/javascript'>",
				"var a = 1;",
				"</script>",
				"<h1>Hello!</h1>",
				"<script type='text/coffeescript'>",
				"a = 1",
				"</script>",
				"<script>",
				"var b = 1;",
				"</script>",
				"</html>"
		}, "\n");

		js = StringUtils.join(new String[] { "\n", "var a = 1;", "\n\n\n\n\n", "var b = 1;\n" }, "\n");

		assertEquals(cli.extract(html, "auto"), js);
	}

	@Test(groups = { "extract" })
	public void testExtractWithIndent() {
		CliWrapper cli = setUpExtractCli();

		String html = StringUtils.join(new String[] {
				"<html>",
				"<script type='text/javascript'>",
				"  var a = 1;",
				"    var b = 1;",
				"</script>",
				"</html>"
		}, "\n");

		// leading whitespace is removed by amount from first js line
		String js = StringUtils.join(new String[] { "\n", "var a = 1;", "  var b = 1;\n" }, "\n");

		assertEquals(cli.extract(html, "auto"), js);
	}

	@Test(groups = { "extract" })
	public void testExtractWithIndentReportLocation() {
		CliWrapper cli = setUpExtractCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		String html = StringUtils.join(new String[] {
				"<html>",
				"<script type='text/javascript'>",
				"  /* jshint indent: 2*/",
				"  var a = 1;",
				"    var b = 1",
				"</script>",
				"</html>"
		}, "\n");

		cli.stubCat(path -> {
			if (path.endsWith("indent.html"))
				return html;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("indent.html"))
				return true;
			return false;
		});

		cli.interpret("indent.html", "--extract", "always", "--reporter=SimpleReporter");
		assertEquals(cli.getExitCode(), 2);

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 1, "found single error");
		LinterWarning lintError = errors.get(0).getError();
		assertTrue(lintError != null, "have error object");
		assertEquals(lintError.getCode(), "W033", "found missing semicolon warning");
		assertEquals(lintError.getLine(), 5, "misaligned line");
		assertEquals(lintError.getCharacter(), 14, "first misaligned character at column 5");
	}

	@Test(groups = { "extract" })
	public void testExtractWithIndentReportLocationMultipleFragments() {
		CliWrapper cli = setUpExtractCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		String html = StringUtils.join(new String[] {
				"<html>",
				"<script type='text/javascript'>",
				"  /* jshint indent: 2*/",
				"  var a = 1;",
				"    var b = 1", // misindented on purpose
				"</script>",
				"<p>nothing</p>",
				"<script type='text/javascript'>",
				"  /* jshint indent: 2*/",
				"      var a = 1", // misindented on purpose
				"</script>",
				"</html>"
		}, "\n");

		cli.stubCat(path -> {
			if (path.endsWith("indent.html"))
				return html;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("indent.html"))
				return true;
			return false;
		});

		cli.interpret("indent.html", "--extract", "always", "--reporter=SimpleReporter");
		assertEquals(cli.getExitCode(), 2);

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 2, "found two errors");

		assertEquals(errors.get(0).getError().getLine(), 5, "first error line");
		assertEquals(errors.get(0).getError().getCharacter(), 14, "first error column");

		assertEquals(errors.get(1).getError().getLine(), 10, "second error line");
		assertEquals(errors.get(1).getError().getCharacter(), 16, "second error column");
	}

	@Test(groups = { "extract" })
	public void testExtractFirstLine() {
		CliWrapper cli = setUpExtractCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		String html = StringUtils.join(new String[] {
				"<script>",
				"  function a() {",
				"    return 5;",
				"  }",
				"</script>"
		}, "\n");

		cli.stubCat(path -> {
			if (path.endsWith("firstLine.html"))
				return html;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("firstLine.html"))
				return true;
			return false;
		});

		cli.interpret("firstLine.html", "--extract", "always", "--reporter=SimpleReporter");
		assertEquals(cli.getExitCode(), 0);

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 0, "found no errors");

		String js = StringUtils.join(new String[] {
				"",
				"function a() {",
				"  return 5;",
				"}",
				""
		}, "\n");

		assertEquals(cli.extract(html, "auto"), js);
	}

	@Test(groups = { "extract" })
	public void testExtractSameLine() {
		CliWrapper cli = setUpExtractCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		String html = StringUtils.join(new String[] {
				"<script>",
				"  function a() {",
				"    return 5;",
				"  }",
				"</script><script>",
				"  function b() {",
				"    return 'hello world';",
				"  }",
				"</script>"
		}, "\n");

		cli.stubCat(path -> {
			if (path.endsWith("sameLine.html"))
				return html;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("sameLine.html"))
				return true;
			return false;
		});

		cli.interpret("sameLine.html", "--extract", "always", "--reporter=SimpleReporter");
		assertEquals(cli.getExitCode(), 0);

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 0, "found no errors");

		String js = StringUtils.join(new String[] {
				"",
				"function a() {",
				"  return 5;",
				"}",
				"",
				"function b() {",
				"  return 'hello world';",
				"}",
				""
		}, "\n");

		assertEquals(cli.extract(html, "auto"), js);
	}

	@Test(groups = { "extract" })
	public void testExtractMultipleFiles() {
		CliWrapper cli = setUpExtractCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		String html = StringUtils.join(new String[] {
				"<script type='text/javascript'>",
				"  a()",
				"</script>"
		}, "\n");

		cli.stubCat(path -> {
			if (path.endsWith("indent.html"))
				return html;
			if (path.endsWith("another.html"))
				return "\n\n<script>a && a();</script>";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("indent.html"))
				return true;
			if (path.endsWith("another.html"))
				return true;
			return false;
		});

		cli.interpret("indent.html", "another.html", "--extract", "auto", "--reporter=SimpleReporter");
		assertEquals(cli.getExitCode(), 2);

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 2, "found two errors");

		LinterWarning lintError = errors.get(0).getError();
		assertTrue(lintError != null, "have error object");
		assertEquals(lintError.getCode(), "W033", "found missing semicolon warning");
		assertEquals(lintError.getLine(), 2, "misaligned line");
		assertEquals(lintError.getCharacter(), 6, "first misaligned character at column 2");

		lintError = errors.get(1).getError();
		assertTrue(lintError != null, "have error object");
		assertEquals(lintError.getCode(), "W030", "found an expression warning");
		assertEquals(lintError.getLine(), 3, "misaligned line");
		assertEquals(lintError.getCharacter(), 8, "first misaligned character at column 8");
	}

	@Test(groups = { "extract" })
	public void testExtractGH2825() {
		CliWrapper cli = setUpExtractCli();

		String html = StringUtils.join(new String[] {
				"<script>",
				"  var a = 3;",
				"</script>"
		}, "\r\n");

		String js = "\nvar a = 3;\n";

		assertEquals(cli.extract(html, "auto"), js);
	}

	@Test(groups = { "useStdin" })
	public void testUseStdinFilenameOption() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		String jshintrc = "{ \"undef\": true }";

		cli.stubCat(path -> {
			if (Reg.test(Pattern.compile("[/\\\\]fake[/\\\\]\\.jshintrc$"), path))
				return jshintrc;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (Reg.test(Pattern.compile("fake[/\\\\]\\.jshintrc$"), path))
				return true;
			return false;
		});

		cli.interpret("--filename", "fake/fakescript.js", "--reporter=SimpleReporter", "-");

		String[] bad = {
				"function returnUndef() {",
				"  return undefinedVariable;",
				"}",
				"returnUndef();"
		};

		cli.stdinSend(bad);
		cli.stdinEnd();

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 1, "should be a single error.");
		assertEquals(cli.getExitCode(), 2, "status code should be 2 when there is a linting error.");
	}

	@Test(groups = { "useStdin" })
	public void testUseStdinNoFilename() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		cli.interpret("--reporter=SimpleReporter", "-");

		cli.stdinSend("void 0;");
		cli.stdinEnd();

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertTrue(errors.size() == 0, "should not report errors");
		assertEquals(cli.getExitCode(), 0, "status code should be 2 when there is a linting error.");

		cli.interpret("--reporter=SimpleReporter", "-");

		cli.stdinSend("? This is not JavaScript.");
		cli.stdinEnd();

		errors = cli.getTestReporter().getResults();
		assertTrue(errors.size() > 0, "should report some number of errors");
		assertEquals(cli.getExitCode(), 2, "status code should be 2 when there is a linting error.");
	}

	@Test(groups = { "useStdin" })
	public void testUseStdinFilenameOverridesOption() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);

		String jshintrc = "{ \"undef\": false, \"overrides\": { \"**/fake/**\": { \"undef\": true } } }";

		cli.stubCat(path -> {
			if (path.endsWith(".jshintrc"))
				return jshintrc;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (Reg.test(Pattern.compile("fake[/\\\\]\\.jshintrc$"), path))
				return true;
			if (path.endsWith(".jshintrc"))
				return true;
			return false;
		});

		cli.interpret("--filename", "fake/fakescript.js", "--reporter=SimpleReporter", "-");

		String[] bad = {
				"function returnUndef() {",
				"  return undefinedVariable;",
				"}",
				"returnUndef();"
		};

		cli.stdinSend(bad);
		cli.stdinEnd();

		List<ReporterResult> errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 1, "should be a single error.");
		assertEquals(cli.getExitCode(), 2, "status code should be 2 when there is a linting error.");

		cli.interpret("--filename", "fake2/fakescript.js", "--reporter=SimpleReporter", "-");

		cli.stdinSend(bad);
		cli.stdinEnd();

		errors = cli.getTestReporter().getResults();
		assertEquals(errors.size(), 0, "should be no errors.");
		assertEquals(cli.getExitCode(), 0, "status code should be 0 when there is no linting error.");
	}

	@Test(groups = { "useStdin" })
	public void testUseStdinFileNameIgnore() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		cli.stubCwd(() -> dir);
		cli.stubStdout();

		cli.stubCat(path -> {
			if (path.endsWith(".jshintignore"))
				return "ignore-me.js";
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith(".jshintignore"))
				return true;
			return false;
		});

		cli.interpret("--filename", "do-not-ignore-me.js", "-");

		cli.stdinSend("This is not valid JavaScript.");
		cli.stdinEnd();

		assertEquals(cli.getExitCode(), 2, "The input is linted because the specified file name is not ignored.");

		cli.interpret("--filename", "ignore-me.js", "-");

		cli.stdinSend("This is not valid JavaScript.");
		cli.stdinEnd();

		assertEquals(cli.getExitCode(), 0, "The input is not linted because the specified file name is ignored.");

		cli.restoreStdout();
	}

	// Overrides should work when the passed stdin filename is in the current
	// directoy
	@Test(groups = { "useStdin" })
	public void testUseStdinOverridesFromStdin() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		String config = "{ \"asi\": true, \"overrides\": { \"bar.js\": { \"asi\": false } } }";

		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("config.json"))
				return config;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		// Test successful file
		cli.interpret("--filename", "foo.js", "--config", "config.json", "--reporter", "SimpleReporter", "-");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);

		// Test overridden, failed file
		cli.interpret("--filename", "bar.js", "--config", "config.json", "--reporter", "SimpleReporter", "-");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");
	}

	// Override behaviour for implicit relative paths (without a leading ./) should
	// be the same
	// for stdin as for file
	@Test(groups = { "useStdin" })
	public void testUseStdinOverridesMatchesImplicitRelativePathsFromStdin() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		String config = "{ \"asi\": true, \"overrides\": { \"src/bar.js\": { \"asi\": false } } }";

		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("config.json"))
				return config;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		// Test successful file
		cli.interpret("--filename", "src/foo.js", "--config", "config.json", "--reporter", "SimpleReporter", "-");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);

		// Test overridden, failed file
		cli.interpret("--filename", "src/bar.js", "--config", "config.json", "--reporter", "SimpleReporter", "-");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");
	}

	// Override behaviour for explicit relative paths (with a leading ./) should be
	// the same
	// for stdin as for file
	@Test(groups = { "useStdin" })
	public void testUseStdinOverridesMatchesExplicitRelativePathsFromStdin() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		String config = "{ \"asi\": true, \"overrides\": { \"src/bar.js\": { \"asi\": false } } }";

		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("config.json"))
				return config;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("config.json"))
				return true;
			return false;
		});

		cli.restoreExit();

		// Test successful file
		cli.interpret("--filename", "./src/foo.js", "--config", "config.json", "--reporter", "SimpleReporter", "-");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);

		// Test overridden, failed file
		cli.interpret("--filename", "./src/bar.js", "--config", "config.json", "--reporter", "SimpleReporter", "-");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");
	}

	// We should only lint stdin when the special `-` or `/dev/stdin` values are
	// passed, even
	// if the `--filename` parameter is set
	@Test(groups = { "useStdin" })
	public void testUseStdinStdinInputCorrectlyIgnored() {
		CliWrapper cli = setUpUseStdinCli();

		String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		String config = "{ \"asi\": false, \"overrides\": { \"src/foo.js\": { \"asi\": true } } }";

		cli.stubCwd(() -> dir);

		cli.stubCat(path -> {
			if (path.endsWith("foo.js"))
				return "a()";
			if (path.endsWith("config.json"))
				return config;
			throw new UncheckedIOException(new IOException("Method 'cat' is overridden for testing purposes"));
		});

		cli.stubExists(path -> {
			if (path.endsWith("config.json"))
				return true;
			if (path.endsWith("foo.js"))
				return true;
			return false;
		});

		cli.restoreExit();

		// Check that the stdin that is used in this test group actually triggers an
		// error
		cli.interpret("--config", "config.json", "--reporter", "SimpleReporter", "-");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");

		// If the filename is set to something other than stdin, then sending stdin
		// should be ignored
		cli.interpret("--config", "config.json", "--reporter", "SimpleReporter", "./src/foo.js");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);

		// Passing the filename parameter should make no difference
		cli.interpret("--filename", "./src/bar.js", "--config", "config.json", "--reporter", "SimpleReporter",
				"./src/foo.js");
		cli.stdinSend("a()");
		cli.stdinEnd();
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);
	}
}