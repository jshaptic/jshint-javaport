package org.jshint.test.helpers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jshint.Cli;
import org.jshint.JSHintException;
import org.jshint.reporters.JSHintReporter;
import org.jshint.utils.IOUtils;
import com.github.jshaptic.js4j.UniversalContainer;

public class CliWrapper extends Cli {
	private static PrintStream SYSTEM_OUT_ORIG = System.out;
	private static PrintStream SYSTEM_OUT_STUB = new PrintStream(new OutputStream() {
		@Override
		public void write(int b) {
		}
	});

	private String[] args = null;
	private RunOptions opts = null;
	private String stdinLines = "";
	private List<String> errors = new ArrayList<>();
	private int exitCode = 0;

	// Cli Stubs
	private boolean stdinStub = false;
	private boolean runStub = false;
	private Function<RunOptions, List<String>> gatherStub = null;
	private boolean exitStub = false;

	// Utils Stubs
	private Function<String, String> catStub = null;
	private Supplier<String> cwdStub = null;
	private Function<String, List<String>> lsStub = null;
	private Predicate<String> existsStub = null;
	private Predicate<String> isDirectoryStub = null;

	public CliWrapper() {
		IOUtils.PathUtils path = new IOUtils.PathUtils() {
			@Override
			public String cwd() {
				return cwdStub == null ? super.cwd() : cwdStub.get();
			}
		};

		IOUtils.ShellUtils shell = new IOUtils.ShellUtils(path) {
			@Override
			public String cat(String path) throws IOException {
				try {
					return catStub == null ? super.cat(path) : catStub.apply(path);
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			}

			@Override
			public List<String> ls(String path) {
				return lsStub == null ? super.ls(path) : lsStub.apply(path);
			}

			@Override
			public boolean exists(String path) {
				return existsStub == null ? super.exists(path) : existsStub.test(path);
			}

			@Override
			public boolean isDirectory(String path) {
				return isDirectoryStub == null ? super.isDirectory(path) : isDirectoryStub.test(path);
			}
		};

		IOUtils.CliUtils cli = new IOUtils.CliUtils() {
			@Override
			public String readFromStdin() {
				return stdinLines;
			}

			@Override
			public void error(String message) {
				errors.add(message);
			}
		};

		setShellUtils(shell);
		setPathUtils(path);
		setCliUtils(cli);
	}

	@Override
	public void exit(int code) throws ExitException {
		this.exitCode = code;

		if (!exitStub)
			super.exit(code);
	}

	@Override
	public List<String> gather(RunOptions opts) throws IOException {
		return gatherStub == null ? super.gather(opts) : gatherStub.apply(opts);
	}

	@Override
	public boolean run(RunOptions opts) throws JSHintException, ExitException, IOException {
		this.opts = opts;

		if (!runStub)
			return super.run(opts);

		return true;
	}

	@Override
	public int interpret(String... args) {
		this.args = args;
		this.exitCode = 0;

		if (!stdinStub)
			return super.interpret(args);

		return exitCode;
	}

	public void stdinSend(String... lines) {
		if (stdinStub) {
			for (String l : lines)
				stdinLines += l + "\n";
		}
	}

	public void stdinEnd() {
		if (stdinStub) {
			super.interpret(args);
			stdinLines = "";
		}
	}

	public void stubStdin() {
		this.stdinStub = true;
	}

	public void stubStdout() {
		System.setOut(SYSTEM_OUT_STUB);
	}

	public void stubRun() {
		this.runStub = true;
	}

	public void stubGather(Function<RunOptions, List<String>> gatherStub) {
		this.gatherStub = gatherStub;
	}

	public void stubExit() {
		this.exitStub = true;
	}

	public void stubCat(Function<String, String> catStub) {
		this.catStub = catStub;
	}

	public void stubCwd(Supplier<String> cwdStub) {
		this.cwdStub = cwdStub;
	}

	public void stubLs(Function<String, List<String>> lsStub) {
		this.lsStub = lsStub;
	}

	public void stubExists(Predicate<String> existsStub) {
		this.existsStub = existsStub;
	}

	public void stubIsDirectory(Predicate<String> isDirectoryStub) {
		this.isDirectoryStub = isDirectoryStub;
	}

	public void restoreStdin() {
		this.stdinStub = false;
	}

	public void restoreStdout() {
		System.setOut(SYSTEM_OUT_ORIG);
	}

	public void restoreRun() {
		this.runStub = false;
	}

	public void restoreGather() {
		this.gatherStub = null;
	}

	public void restoreExit() {
		this.exitStub = false;
	}

	public void restoreCat() {
		this.catStub = null;
	}

	public void restoreCwd() {
		this.cwdStub = null;
	}

	public void restoreLs() {
		this.lsStub = null;
	}

	public void restoreExists() {
		this.existsStub = null;
	}

	public void restoreIsDirectory() {
		this.isDirectoryStub = null;
	}

	public List<String> getErrorMessages() {
		return errors;
	}

	public int getExitCode() {
		return exitCode;
	}

	public UniversalContainer getConfig() {
		return opts != null ? opts.getConfig() : null;
	}

	public JSHintReporter getReporter() {
		return opts != null ? opts.getReporter() : null;
	}

	public TestReporter getTestReporter() {
		JSHintReporter reporter = getReporter();
		return reporter instanceof TestReporter ? (TestReporter) reporter : null;
	}

	public List<String> getIgnores() {
		return opts != null ? opts.getIgnores() : null;
	}

	public String getExtensions() {
		return opts != null ? opts.getExtensions() : null;
	}
}