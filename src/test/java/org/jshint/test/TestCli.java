package org.jshint.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jshint.Cli;
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
import org.jshint.utils.JSHintUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

public class TestCli extends Assert
{
	@BeforeClass
	private void setupBeforeClass()
	{
		JSHintUtils.reset();
	}
	
	@BeforeGroups(groups = {"config"})
	public void setUpConfig()
	{	
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file\\.js$", path)) return "var a = function () {}; a();";
				if (Reg.test("file1\\.json$", path)) return "wat";
				if (Reg.test("file2\\.json$", path)) return "{\"node\":true,\"globals\":{\"foo\":true,\"bar\":true}}";
				if (Reg.test("file4\\.json$", path)) return "{\"extends\":\"file3.json\"}";
				if (Reg.test("file5\\.json$", path)) return "{\"extends\":\"file2.json\"}";
				if (Reg.test("file6\\.json$", path)) return "{\"extends\":\"file2.json\",\"node\":false}";
				if (Reg.test("file7\\.json$", path)) return "{\"extends\":\"file2.json\",\"globals\":{\"bar\":false,\"baz\":true}}";
				if (Reg.test("file8\\.json$", path)) return "{\"extends\":\"file7.json\",\"overrides\":{\"file.js\":{\"globals\":{\"foo\":true,\"bar\":true}}}}";
				if (Reg.test("file9\\.json$", path)) return "{\"extends\":\"file8.json\",\"overrides\":{\"file.js\":{\"globals\":{\"baz\":true,\"bar\":false}}}}";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("file\\.js$", path)) return true;
				if (Reg.test("file1\\.json$", path)) return true;
				if (Reg.test("file2\\.json$", path)) return true;
				if (Reg.test("file3\\.json$", path)) return false;
				if (Reg.test("file4\\.json$", path)) return true;
				if (Reg.test("file5\\.json$", path)) return true;
				if (Reg.test("file6\\.json$", path)) return true;
				return false;
			}
		};
	}
	
	@Test(groups = {"group", "config"})
	public void testGroupConfigNormal()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		cli.toggleRun(false);
		
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
	
	@Test(groups = {"group", "config"})
	public void testGroupConfigFailure()
	{
		CliWrapper cli = new CliWrapper();
		String msg = "";
		
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
	
	@Test(groups = {"group"})
	public void testGroupPrereq()
	{	
		CliWrapper cli = new CliWrapper();
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file\\.js$", path)) return "a();";
				if (Reg.test("prereq.js$", path)) return "var a = 1;";
				if (Reg.test("config.json$", path)) return "{\"undef\":true,\"prereq\":[\"prereq.js\", \"prereq2.js\"]}";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("file\\.js$", path)) return true;
				if (Reg.test("prereq.js$", path)) return true;
				if (Reg.test("config.json$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js", "--config", "config.json");
		assertNotEquals(cli.getExitCode(), 2, "ProcessExit");
	}
	
	// CLI prereqs
	@Test(groups = {"group"})
	public void testGroupPrereqCLIOption()
	{
		CliWrapper cli = new CliWrapper();
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file\\.js$", path)) return "a();";
				if (Reg.test("prereq.js$", path)) return "var a = 1;";
				if (Reg.test("config.json$", path)) return "{\"undef\":true}";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("file\\.js$", path)) return true;
				if (Reg.test("prereq.js$", path)) return true;
				if (Reg.test("config.json$", path)) return true;
				return false;
			}
		};
		
		cli.toggleExit(false);
		cli.interpret("file.js", "--config", "config.json", "--prereq", "prereq.js  , prereq2.js");
		assertNotEquals(cli.getExitCode(), 2, "ProcessExit");
	}
	
	// CLI prereqs should get merged with config prereqs
	@Test(groups = {"group"})
	public void testGroupPrereqBothConfigAndCLIOption()
	{
		CliWrapper cli = new CliWrapper();
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file\\.js$", path)) return "a(); b();";
				if (Reg.test("prereq.js$", path)) return "var a = 1;";
				if (Reg.test("prereq2.js$", path)) return "var b = 2;";
				if (Reg.test("config.json$", path)) return "{\"undef\":true,\"prereq\":[\"prereq.js\"]}";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("file\\.js$", path)) return true;
				if (Reg.test("prereq.js$", path)) return true;
				if (Reg.test("prereq2.js$", path)) return true;
				if (Reg.test("config.json$", path)) return true;
				return false;
			}
		};
		
		cli.toggleExit(false);
		cli.interpret("file.js", "--config", "config.json", "--prereq", "prereq2.js,prereq3.js");
		assertNotEquals(cli.getExitCode(), 2, "ProcessExit");
	}
	
	@Test(groups = {"group"})
	public void testGroupOverrides()
	{
		CliWrapper cli = new CliWrapper();
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		final String config = "{ \"asi\": true, \"overrides\": { \"bar.js\": { \"asi\": false } } }";
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("foo\\.js$", path)) return "a()";
				if (Reg.test("bar\\.js$", path)) return "a()";
				if (Reg.test("config\\.json$", path)) return config;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("foo\\.js$", path)) return true;
				if (Reg.test("bar\\.js$", path)) return true;
				if (Reg.test("config\\.json$", path)) return true;
				return false;
			}
		};
		
		// Test successful file
		cli.interpret("foo.js", "--config", "config.json", "--reporter", "SimpleReporter.java");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 0);
		
		// Test overriden, failed file
		cli.interpret("bar.js", "--config", "config.json", "--reporter", "SimpleReporter.java");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() > 0, "Error was expected but not thrown");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getCode(), "W033");
	}
	
	@Test(groups = {"group"})
	public void testGroupOverridesMatchesRelativePaths()
	{
		CliWrapper cli = new CliWrapper();
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		final String config = "{ \"asi\": true, \"overrides\": { \"src/bar.js\": { \"asi\": false } } }";
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("bar\\.js$", path)) return "a()";
				if (Reg.test("config\\.json$", path)) return config;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("bar\\.js$", path)) return true;
				if (Reg.test("config\\.json$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("./src/bar.js", "--config", "config.json", "--reporter", "SimpleReporter.java");
		assertNotEquals(cli.getExitCode(), 1, "ProcessExit");
		assertTrue(cli.getTestReporter().getResults().size() == 1);
	}
	
	@Test(groups = {"group"})
	public void testGroupReporter()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
		};
		
		// Test failed attempt.
		cli.interpret("file.js", "--reporter", "invalid.java");
		String msg = cli.getErrorMessages().get(0);
		assertEquals(msg.substring(0, 25), "Can't load reporter file:");
		assertEquals(msg.substring(msg.length() - 12), "invalid.java");
		
		// Test successful attempt.
		cli.toggleRun(true);
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file\\.js$", path)) return "func()";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("file\\.js$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js", "--reporter", "SimpleReporter.java");
		assertEquals(cli.getTestReporter().getResults().get(0).getError().getRaw(), "Missing semicolon.");
		assertTrue(cli.getTestReporter().isCalledOnce());
	}
	
	@Test(groups = {"group"})
	public void testGroupJSLintReporter()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		cli.toggleExit(false);
		
		cli.interpret("file.js", "--reporter", "jslint");
		assertEquals(cli.getReporter().getClass(), JslintXmlReporter.class);
		
		cli.interpret("file.js", "--jslint-reporter");
		assertEquals(cli.getReporter().getClass(), JslintXmlReporter.class);
	}
	
	@Test(groups = {"group"})
	public void testGroupCheckStyleReporter()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		cli.toggleExit(false);
		
		cli.interpret("file.js", "--reporter", "checkstyle");
		assertEquals(cli.getReporter().getClass(), CheckstyleReporter.class);
		
		cli.interpret("file.js", "--checkstyle-reporter");
		assertEquals(cli.getReporter().getClass(), CheckstyleReporter.class);
	}
	
	@Test(groups = {"group"})
	public void testGroupShowNonErrors()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		cli.toggleExit(false);
		
		cli.interpret("file.js", "--show-non-errors");
		assertEquals(cli.getReporter().getClass(), NonErrorReporter.class);
	}
	
	@Test(groups = {"group"})
	public void testGroupExtensions()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		cli.toggleExit(false);
		
		cli.interpret("file.js");
		assertEquals(cli.getExtensions(), "");
		
		cli.interpret("file.js", "--extra-ext", ".json");
		assertEquals(cli.getExtensions(), ".json");
	}
	
	@Test(groups = {"group"})
	public void testGroupMalformedNpmFile()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/npm";
		final String localNpm = JSHintUtils.path.normalize(dir + "/package.json");
		final String localRc = JSHintUtils.path.normalize(dir + "/.jshintrc");
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				// stub rc file
				if (path.equals(localRc)) return "{\"evil\": true}";
				// stub npm file
				if (path.equals(localNpm)) return "{"; // malformed package.json
				// stub src file
				if (Reg.test("file\\.js$", path)) return "eval('a=2');";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				// stub rc file
				if (path.equals(localRc)) return true;
				// stub npm file
				if (path.equals(localNpm)) return true;
				// stub src file
				if (Reg.test("file\\.js$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // lint with wrong package.json
	}
	
	@Test(groups = {"group"})
	public void testGroupRcFile()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/npm";
		final String localRc = JSHintUtils.path.normalize(dir + "/.jshintrc");
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				// stub rc file
				if (path.equals(localRc)) return "{\"evil\": true}";
				// stub src file
				if (Reg.test("file\\.js$", path)) return "eval('a=2');";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				// stub rc file
				if (path.equals(localRc)) return true;
				// stub src file
				if (Reg.test("file\\.js$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}
	
	@Test(groups = {"group"})
	public void testGroupHomeRcFile()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String homeRc = JSHintUtils.path.join(System.getProperty("user.home"), ".jshintrc");
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cat(String path) throws IOException
			{
				// stub rc file
				if (path.equals(homeRc)) return "{\"evil\": true}";
				// stub src file (in root where we are unlikely to find a .jshintrc)
				if (Reg.test("file\\.js$", path)) return "eval('a=2');";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				// stub rc file
				if (path.equals(homeRc)) return true;
				// stub src file (in root where we are unlikely to find a .jshintrc)
				if (Reg.test("file\\.js$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("/file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}
	
	@Test(groups = {"group"})
	public void testGroupNoHomeDir()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		String prevEnv = System.getProperty("user.home"); 
		
		// Remove all home dirs from env.
		System.setProperty("user.home", "");
		
		final String localRc = JSHintUtils.path.normalize(System.getProperty("user.dir") + "/.jshintrc");
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return System.getProperty("user.dir");
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				// stub rc file
				if (path.equals(localRc)) return "{\"evil\": true}";
				// stub src file
				if (Reg.test("file\\.js$", path)) return "eval('a=2');";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				// stub rc file
				if (path.equals(localRc)) return true;
				// stub src file
				if (Reg.test("file\\.js$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
		
		// Restore environemnt
		System.setProperty("user.home", prevEnv);
	}
	
	@Test(groups = {"group"})
	public void testGroupOneLevelRcLookup()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String srcDir = System.getProperty("user.dir") + "/src/";
		final String parentRc = JSHintUtils.path.join(srcDir, ".jshintrc");
		final String cliDir = JSHintUtils.path.join(srcDir, "cli/");
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return cliDir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				// stub rc file
				if (path.equals(parentRc)) return "{\"evil\": true}";
				// stub src file
				if (Reg.test("file\\.js$", path)) return "eval('a=2');";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				// stub rc file
				if (path.equals(parentRc)) return true;
				// stub src file
				if (Reg.test("file\\.js$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js");
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}
	
	@Test(groups = {"group"})
	public void testGroupTargetRelativeRcLookup()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String projectRc = JSHintUtils.path.normalize(System.getProperty("user.dir") + "/.jshintrc");
		final String srcFile = System.getProperty("user.dir") + "/sub/file.js";
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				// working from outside the project
				return System.getProperty("user.home");
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				// stub rc file
				if (path.equals(projectRc)) return "{\"evil\": true}";
				// stub src file
				if (path.equals(srcFile)) return "eval('a=2');";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				// stub rc file
				if (path.equals(projectRc)) return true;
				// stub src file
				if (path.equals(srcFile)) return true;
				return false;
			}
		};
		
		cli.interpret(srcFile);
		assertEquals(cli.getExitCode(), 0); // eval allowed = rc file found
	}
	
	@Test(groups = {"group"})
	public void testGroupIgnores()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
		};
		
		cli.interpret("file.js", "--exclude=exclude.js");
		assertEquals(cli.getIgnores().get(0), JSHintUtils.path.resolve(dir, "exclude.js"));
		assertEquals(cli.getIgnores().get(1), JSHintUtils.path.resolve(dir, "ignored.js"));
		assertEquals(cli.getIgnores().get(2), JSHintUtils.path.resolve(dir, "another.js"));
	}
	
	@Test(groups = {"group"})
	public void testGroupIgnoresWithSpecialChars()
	{
		CliWrapper cli = new CliWrapper();
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return JSHintUtils.path.resolve(System.getProperty("user.dir"), "special++chars");
			}
			
			@Override
			public boolean exists(String path)
			{
				if (".".equals(path)) return true;
				return false;
			}
			
			@Override
			public boolean isDirectory(String path)
			{
				if (".".equals(path)) return true;
				return false;
			}
			
			@Override
			public List<String> ls(String path)
			{
				if (".".equals(path)) return new ArrayList<String>();
				return null;
			}
		};
		cli.interpret(".", "--exclude=exclude1.js");
	}
	
	@Test(groups = {"group"})
	public void testGroupMultipleIgnores()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
		};
		
		cli.interpret("file.js", "--exclude=foo.js,bar.js");
		assertEquals(cli.getIgnores().get(0), JSHintUtils.path.resolve(dir, "foo.js"));
		assertEquals(cli.getIgnores().get(1), JSHintUtils.path.resolve(dir, "bar.js"));
	}
	
	@Test(groups = {"group"})
	public void testGroupExcludePath()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleRun(false);
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
		};
		
		cli.interpret("file.js", "--exclude-path=../examples/.customignore");
		assertEquals(cli.getIgnores().get(0), JSHintUtils.path.resolve(dir, "exclude.js"));
	}
	
	@Test(groups = {"group"})
	public void testGroupAPIIgnores() throws JSHintException, Cli.ExitException, IOException
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/main/";
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
		};
		
		Cli.RunOptions opts = new Cli.RunOptions();
		opts.setArgs(new String[]{"../test/resources/fixtures/ignored.js"});
		opts.setCwd(dir + "../test/resources/fixtures");
		opts.setReporter(new JSHintReporter()
		{
			@Override
			public void generate(List<ReporterResult> results, List<DataSummary> data, String verbose)
			{
				assertTrue(results.size() == 0);
			}
		});
		
		cli.run(opts);
	}
	
	@Test(groups = {"group"})
	public void testGroupCollectFiles()
	{
		CliWrapper cli = new CliWrapper()
		{
			String[] args;
			List<String> ignores;
			
			@Override
			public List<String> gather(RunOptions opts)
			{
				args = (opts != null ? opts.getArgs() : null);
				ignores = (opts != null ? opts.getIgnores() : null);
				return Collections.emptyList();
			}
			
			@Override
			public String[] getArgs()
			{
				return args;
			}
			
			@Override
			public List<String> getIgnores()
			{
				return ignores;
			}
		};
		cli.toggleExit(false);
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{	
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file2?\\.js$", path)) return "console.log('Hello');";
				if (Reg.test("ignore[\\/\\\\]file\\d\\.js$", path)) return "console.log('Hello, ignore me');";
				if (Reg.test("ignore[\\/\\\\]dir[\\/\\\\]file\\d\\.js$", path)) return "print('Ignore me');";
				if (Reg.test("node_script$", path)) return "console.log('Hello, ignore me');";
				if (Reg.test("\\.jshintignore$", path)) return JSHintUtils.path.join("ignore", "**");
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test(".*", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js", "file2.js", "node_script", JSHintUtils.path.join("ignore", "file1.js"),
				JSHintUtils.path.join("ignore", "file2.js"), JSHintUtils.path.join("ignore", "dir", "file1.js"));
		
		assertEquals(cli.getArgs()[0], "file.js");
		assertEquals(cli.getArgs()[1], "file2.js");
		assertEquals(cli.getArgs()[2], "node_script");
		assertEquals(cli.getArgs()[3], JSHintUtils.path.join("ignore", "file1.js"));
		assertEquals(cli.getArgs()[4], JSHintUtils.path.join("ignore", "file2.js"));
		assertEquals(cli.getArgs()[5], JSHintUtils.path.join("ignore", "dir", "file1.js"));
		assertEquals(cli.getIgnores(), Arrays.asList(JSHintUtils.path.resolve(JSHintUtils.path.join("ignore", "**"))));
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{	
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file2?\\.js$", path)) return "console.log('Hello');";
				if (Reg.test("file3\\.json$", path)) return "{}";
				if (Reg.test("src[\\/\\\\]file4\\.js$", path)) return "print('Hello');";
				if (Reg.test("src[\\/\\\\]lib[\\/\\\\]file5\\.js$", path)) return "print('Hello');";
				if (Reg.test("\\.jshintignore$", path)) return "";
				throw new IOException();
			}
			
			@Override
			public List<String> ls(String path)
			{
				if (Reg.test("src$", path)) return Arrays.asList("lib", "file4.js");
				if (Reg.test("src[\\/\\\\]lib$", path)) return Arrays.asList("file5.js");
				return null;
			}
			
			@Override
			public boolean isDirectory(String path)
			{
				if (Reg.test("src[\\/\\\\]lib$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js", "file2.js", "file3.json", "--extra-ext=json", "src");
		
		assertEquals(cli.getArgs().length, 4);
		assertEquals(cli.getArgs()[0], "file.js");
		assertEquals(cli.getArgs()[1], "file2.js");
		assertEquals(cli.getArgs()[2], "file3.json");
		assertEquals(cli.getArgs()[3], "src");
		assertEquals(cli.getIgnores(), Collections.emptyList());
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{	
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("file2?\\.js$", path)) return "console.log('Hello');";
				if (Reg.test("file3\\.json$", path)) return "{}";
				if (Reg.test("src[\\/\\\\]file4\\.js$", path)) return "print('Hello');";
				if (Reg.test("src[\\/\\\\]lib[\\/\\\\]file5\\.js$", path)) return "print('Hello');";
				if (Reg.test("\\.jshintignore$", path)) return "";
				if (Reg.test("reporter\\.js$", path)) return "console.log('Hello');";
				throw new IOException();
			}
			
			@Override
			public List<String> ls(String path)
			{
				if (Reg.test("src$", path)) return Arrays.asList("lib", "file4.js");
				if (Reg.test("src[\\/\\\\]lib$", path)) return Arrays.asList("file5.js");
				return null;
			}
			
			@Override
			public boolean isDirectory(String path)
			{
				if (Reg.test("src[\\/\\\\]lib$", path)) return true;
				return false;
			}
		};
		
		cli.restore();
		cli.interpret("examples");
		
		assertEquals(cli.getArgs().length, 1);
		assertEquals(cli.getArgs()[0], "examples");
		assertEquals(cli.getIgnores().size(), 0);
	}
	
	@Test(groups = {"group"})
	public void testGroupGatherOptionalParameters() throws IOException
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("file.js$", path)) return true;
				return false;
			}
		};
		
		Cli.RunOptions opts = new Cli.RunOptions();
		opts.setArgs(new String[]{"file.js"});
		
		List<String> files = cli.gather(opts);
		
		assertEquals(files.size(), 1);
		assertEquals(files.get(0), "file.js");
	}
	
	@Test(groups = {"group"})
	public void testGroupGather() throws IOException
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String[][] demoFiles1 = {
			{"file2?\\.js$", "console.log('Hello');"},
			{"ignore[\\/\\\\]file\\d\\.js$", "console.log('Hello, ignore me');"},
			{"ignore[\\/\\\\]dir[\\/\\\\]file\\d\\.js$", "print('Ignore me');"},
			{"node_script$", "console.log('Hello, ignore me');"}
		};
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				for (String[] file : demoFiles1)
				{
					if (Reg.test(file[0], path)) return file[1];
				}
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				for (String[] file : demoFiles1)
				{
					if (Reg.test(file[0], path)) return true;
				}
				return false;
			}
		};
		
		Cli.RunOptions opts = new Cli.RunOptions();
		opts.setArgs(new String[]{"file.js", "file2.js", "node_script", 
				JSHintUtils.path.join("ignore", "file1.js"),
				JSHintUtils.path.join("ignore", "file2.js"),
				JSHintUtils.path.join("ignore", "dir", "file1.js")
		});
		opts.setIgnores(Arrays.asList(JSHintUtils.path.join("ignore", "**")));
		opts.setExtensions("");
		List<String> files = cli.gather(opts);
		
		assertEquals(files.size(), 3);
		assertEquals(files.get(0), "file.js");
		assertEquals(files.get(1), "file2.js");
		assertEquals(files.get(2), "node_script");
		
		final String[][] demoFiles2 = {
			{"file2?\\.js$", "console.log('Hello');"},
			{"file3\\.json$", "{}"},
			{"src[\\/\\\\]file4\\.js$", "print('Hello');"},
			{"src[\\/\\\\]lib[\\/\\\\]file5\\.js$", "print('Hello'); "}
		};
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				for (String[] file : demoFiles1)
				{
					if (Reg.test(file[0], path)) return file[1];
				}
				for (String[] file : demoFiles2)
				{
					if (Reg.test(file[0], path)) return file[1];
				}
				throw new IOException();
			}
			
			@Override
			public List<String> ls(String path)
			{
				if (Reg.test("src$", path)) return Arrays.asList("lib", "file4.js");
				if (Reg.test("src[\\/\\\\]lib$", path)) return Arrays.asList("file5.js");
				return null;
			}
			
			@Override
			public boolean exists(String path)
			{
				for (String[] file : demoFiles1)
				{
					if (Reg.test(file[0], path)) return true;
				}
				for (String[] file : demoFiles2)
				{
					if (Reg.test(file[0], path)) return true;
				}
				if (Reg.test("src$", path)) return true;
				if (Reg.test("src[\\/\\\\]lib$", path)) return true;
				return false;
			}
			
			@Override
			public boolean isDirectory(String path)
			{
				if (Reg.test("src$", path)) return true;
				if (Reg.test("src[\\/\\\\]lib$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("file.js", "file2.js", "file3.json", "--extra-ext=json", "src");
		
		opts.setArgs(new String[]{"file.js", "file2.js", "file3.json", "src"});
		opts.setExtensions("json");
		opts.setIgnores(new ArrayList<String>());
		files = cli.gather(opts);
		
		assertEquals(files.size(), 5);
		assertEquals(files.get(0), "file.js");
		assertEquals(files.get(1), "file2.js");
		assertEquals(files.get(2), "file3.json");
		assertEquals(files.get(3), JSHintUtils.path.join("src", "lib", "file5.js"));
		assertEquals(files.get(4), JSHintUtils.path.join("src", "file4.js"));
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return System.getProperty("user.dir") + "/src/test/resources/";
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("reporter\\.js$", path)) return "console.log('Hello');";
				throw new IOException();
			}
		};
		
		opts.setArgs(new String[]{"examples"});
		opts.setExtensions("json");
		opts.setIgnores(new ArrayList<String>());
		files = cli.gather(opts);
		
		assertEquals(files.size(), 1);
		assertEquals(files.get(0), JSHintUtils.path.join("examples", "reporter.js"));
	}
	
	@Test(groups = {"group"})
	public void testGroupStatusCode()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("pass\\.js$", path)) return "function test() { return 0; }";
				if (Reg.test("fail\\.js$", path)) return "console.log('Hello')";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("(pass\\.js|fail\\.js)$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("pass.js", "--reporter=SimpleReporter.java");
		assertEquals(cli.getExitCode(), 0);
		
		cli.interpret("fail.js", "--reporter=SimpleReporter.java");
		assertEquals(cli.getExitCode(), 2);
	}
	
	@Test(groups = {"extract"})
	public void testExtractBasic()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
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
		
		html = StringUtils.join(new String[] {"<html>",
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
		
		js = StringUtils.join(new String[] {"\n", "var a = 1;", "\n\n\n\n\n", "var b = 1;\n"}, "\n");
		
		assertEquals(cli.extract(html, "auto"), js);
	}
	
	@Test(groups = {"extract"})
	public void testExtractWithIndent()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		String html = StringUtils.join(new String[] {
			"<html>",
			"<script type='text/javascript'>",
			"  var a = 1;",
			"    var b = 1;",
			"</script>",
			"</html>"
		}, "\n");
		
		// leading whitespace is removed by amount from first js line
		String js = StringUtils.join(new String[] {"\n", "var a = 1;", "  var b = 1;\n"}, "\n");
		
		assertEquals(cli.extract(html, "auto"), js);
	}
	
	@Test(groups = {"extract"})
	public void testExtractWithIndentReportLocation()
	{	
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String html = StringUtils.join(new String[] {
			"<html>",
			"<script type='text/javascript'>",
			"  /* jshint indent: 2*/",
			"  var a = 1;",
			"    var b = 1",
			"</script>",
			"</html>"
		}, "\n");
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("indent\\.html$", path)) return html;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("indent\\.html$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("indent.html", "--extract", "always", "--reporter=SimpleReporter.java");
		
		List<ReporterResult> errors = cli.getTestReporter().getResults();
		
		assertEquals(cli.getExitCode(), 2);
		assertEquals(errors.size(), 1, "found single error");
		LinterWarning lintError = errors.get(0).getError();
		assertTrue(lintError != null, "have error object");
		assertEquals(lintError.getCode(), "W033", "found missing semicolon warning");
		assertEquals(lintError.getLine(), 5, "misaligned line");
		assertEquals(lintError.getCharacter(), 14, "first misaligned character at column 5");
	}
	
	@Test(groups = {"extract"})
	public void testExtractWithIndentReportLocationMultipleFragments()
	{	
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String html = StringUtils.join(new String[] {
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
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("indent\\.html$", path)) return html;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("indent\\.html$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("indent.html", "--extract", "always", "--reporter=SimpleReporter.java");
		
		List<ReporterResult> errors = cli.getTestReporter().getResults();
		
		assertEquals(cli.getExitCode(), 2);
		assertEquals(errors.size(), 2, "found two errors");
		
		assertEquals(errors.get(0).getError().getLine(), 5, "first error line");
		assertEquals(errors.get(0).getError().getCharacter(), 14, "first error column");
		
		assertEquals(errors.get(1).getError().getLine(), 10, "second error line");
		assertEquals(errors.get(1).getError().getCharacter(), 16, "second error column");
	}
	
	@Test(groups = {"extract"})
	public void testExtractFirstLine()
	{	
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String html = StringUtils.join(new String[] {
			"<script>",
			"  function a() {",
			"    return 5;",
			"  }",
			"</script>"
		}, "\n");
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("firstLine\\.html$", path)) return html;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("firstLine\\.html$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("firstLine.html", "--extract", "always", "--reporter=SimpleReporter.java");
		
		List<ReporterResult> errors = cli.getTestReporter().getResults();
		
		assertEquals(cli.getExitCode(), 0);
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
	
	@Test(groups = {"extract"})
	public void testExtractSameLine()
	{	
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String html = StringUtils.join(new String[] {
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
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("sameLine\\.html$", path)) return html;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("sameLine\\.html$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("sameLine.html", "--extract", "always", "--reporter=SimpleReporter.java");
		
		List<ReporterResult> errors = cli.getTestReporter().getResults();
		
		assertEquals(cli.getExitCode(), 0);
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
	
	@Test(groups = {"extract"})
	public void testExtractMultipleFiles()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String html = StringUtils.join(new String[] {
			"<script type='text/javascript'>",
		    "  a()",
		    "</script>"
		}, "\n");
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("indent\\.html$", path)) return html;
				if (Reg.test("another\\.html$", path)) return "\n\n<script>a && a();</script>";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("indent\\.html$", path)) return true;
				if (Reg.test("another\\.html$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("indent.html", "another.html", "--extract", "auto", "--reporter=SimpleReporter.java");
		
		List<ReporterResult> errors = cli.getTestReporter().getResults();
		
		assertEquals(cli.getExitCode(), 2);
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
	
	@Test(groups = {"extract"})
	public void testExtractGH2825()
	{
		CliWrapper cli = new CliWrapper();
		
		String html = StringUtils.join(new String[] {
			"<script>",
		    "  var a = 3;",
		    "</script>"
		}, "\r\n");
		
		String js = "\nvar a = 3;\n";
		
		assertEquals(cli.extract(html, "auto"), js);
	}
	
	@Test(groups = {"useStdin"})
	public void testUseStdinFilenameOption()
	{	
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String jshintrc = "{ \"undef\": true }";
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("[/\\\\]fake[/\\\\]\\.jshintrc$", path)) return jshintrc;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("fake[/\\\\]\\.jshintrc$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("--filename", "fake/fakescript.js", "--reporter=SimpleReporter.java", "-");
		
		String[] bad = {
			"function returnUndef() {",
			"  return undefinedVariable;",
			"}",
			"returnUndef();"
		};
		
		cli.send(bad);
		cli.end();
		
		List<ReporterResult> errors = cli.getTestReporter().getResults();
		
		assertEquals(errors.size(), 1, "should be a single error.");
		assertEquals(cli.getExitCode(), 2, "status code should be 2 when there is a linting error.");
	}
	
	@Test(groups = {"useStdin"})
	public void testUseStdinFilenameOverridesOption()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		
		final String jshintrc = "{ \"undef\": false, \"overrides\": { \"**/fake/**\": { \"undef\": true } } }";
		
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("\\.jshintrc$", path)) return jshintrc;
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("fake[/\\\\]\\.jshintrc$", path)) return true;
				if (Reg.test("\\.jshintrc$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("--filename", "fake/fakescript.js", "--reporter=SimpleReporter.java", "-");
		
		String[] bad = {
			"function returnUndef() {",
			"  return undefinedVariable;",
			"}",
			"returnUndef();"
		};
		
		cli.send(bad);
		cli.end();
		
		List<ReporterResult> errors = cli.getTestReporter().getResults();
		
		assertEquals(errors.size(), 1, "should be a single error.");
		assertEquals(cli.getExitCode(), 2, "status code should be 2 when there is a linting error.");
		
		cli.restore();
		cli.toggleExit(false);
		cli.interpret("--filename", "fake2/fakescript.js", "--reporter=SimpleReporter.java", "-");
		
		cli.send(bad);
		cli.end();
		
		errors = cli.getTestReporter().getResults();
		
		assertEquals(errors.size(), 0, "should be no errors.");
		assertEquals(cli.getExitCode(), 0, "status code should be 0 when there is no linting error.");
	}
	
	@Test(groups = {"useStdin"})
	public void testUseStdinFileNameIgnore()
	{
		CliWrapper cli = new CliWrapper();
		cli.toggleExit(false);
		
		final String dir = System.getProperty("user.dir") + "/src/test/resources/examples/";
		JSHintUtils.shell = new JSHintUtils.ShellUtils()
		{
			@Override
			public String cwd()
			{
				return dir;
			}
			
			@Override
			public String cat(String path) throws IOException
			{
				if (Reg.test("\\.jshintignore$", path)) return "ignore-me.js";
				throw new IOException();
			}
			
			@Override
			public boolean exists(String path)
			{
				if (Reg.test("\\.jshintignore$", path)) return true;
				return false;
			}
		};
		
		cli.interpret("--filename", "do-not-ignore-me.js", "-");
		
		cli.send("This is not valid JavaScript.");
		cli.end();
		
		assertEquals(cli.getExitCode(), 2, "The input is linted because the specified file name is not ignored.");
		
		cli.restore();
		cli.toggleExit(false);
		
		cli.interpret("--filename", "ignore-me.js", "-");
		
		cli.send("This is not valid JavaScript.");
		cli.end();
		
		assertEquals(cli.getExitCode(), 0, "The input is not linted because the specified file name is ignored.");
	}
}