package org.jshint.test.test262;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;

/**
 * <p>
 * Java API for traversing the Test262 test suite.
 * </p>
 * 
 * Based on Node.JS library [test262-stream v1.3.0]
 */
// TODO: extract it as a separate project
// PORT INFO: based on the source file lib/index.js
public class Test262Stream {
	private static final Pattern supportedVersion = Pattern.compile("^[1234]\\.");
	private static final Pattern fixturePattern = Pattern.compile("_FIXTURE\\.[jJ][sS]$");
	private static final String usd = "\"use strict\";\n";
	private static final int usdLength = usd.length();

	private final Path test262Dir;
	private final Path includesDir;
	private final Deque<Path> paths;
	private final boolean omitRuntime;
	private final String acceptVersion;

	// Concurrency
	private final boolean parallel;
	private final int threadNumber;
	private ExecutorService executor;

	// Handlers
	private Consumer<Test262File> testHandler = t -> {
	};
	private Runnable finishHandler = () -> {
	};
	private Consumer<Exception> errorHandler = e -> {
	};

	private Test262Stream(Builder builder) {
		this.parallel = builder.parallel;
		this.threadNumber = builder.threadNumber;
		if (parallel) {
			this.executor = Executors.newFixedThreadPool(threadNumber);
		}

		this.test262Dir = builder.test262Dir;

		if (builder.paths != null) {
			this.paths = builder.paths.stream()
					.map(relativePath -> test262Dir.resolve(relativePath))
					.collect(Collectors.toCollection(ArrayDeque::new));
		} else {
			this.paths = new ArrayDeque<>();
			this.paths.add(test262Dir.resolve("test"));
		}

		this.includesDir = builder.includesDir;
		this.omitRuntime = builder.omitRuntime;
		this.acceptVersion = builder.acceptVersion;
	}

	public Test262Stream onTest(Consumer<Test262File> testHandler) {
		this.testHandler = testHandler != null ? testHandler : t -> {
		};
		return this;
	}

	public Test262Stream onFinish(Runnable finishHandler) {
		this.finishHandler = finishHandler != null ? finishHandler : () -> {
		};
		return this;
	}

	public Test262Stream onError(Consumer<Exception> errorHandler) {
		this.errorHandler = errorHandler != null ? errorHandler : e -> {
		};
		return this;
	}

	public void run() {
		Path packagePath = test262Dir.resolve("package.json");

		try {
			String contents = new String(Files.readAllBytes(packagePath), StandardCharsets.UTF_8);
			JsonValue packageData = Json.parse(contents);
			String version = packageData.asObject().getString("version", "");
			if (!supportedVersion.matcher(version).find() && !acceptVersion.equals(version)) {
				errorHandler.accept(new RuntimeException("Unsupported version of Test262: '" + version + "'"));
				return;
			}
		} catch (NoSuchFileException e) {
			// Ignore this error
		} catch (IOException | ParseException error) {
			errorHandler.accept(error);
			return;
		}

		try {
			traverse();
		} catch (IOException error) {
			errorHandler.accept(error);
			return;
		}

		if (parallel) {
			try {
				executor.shutdown();
				executor.awaitTermination(10, TimeUnit.MINUTES);
				finishHandler.run();
			} catch (InterruptedException e) {
				errorHandler.accept(e);
			}
		} else {
			finishHandler.run();
		}
	}

	private void traverse() throws IOException {
		while (!paths.isEmpty()) {
			Files.walkFileTree(paths.pollFirst(), new FileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
					onFile(path, attrs);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
					errorHandler.accept(e);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	private void onFile(Path path, BasicFileAttributes attrs) {
		if (!attrs.isRegularFile())
			return;
		if (path.getFileName().startsWith("."))
			return;
		if (fixturePattern.matcher(path.toString()).find())
			return;

		compile(path, (tests, err) -> {
			if (err != null) {
				errorHandler.accept(err);
				return;
			}

			for (Test262File test : tests) {
				if (parallel) {
					executor.execute(() -> testHandler.accept(test));
				} else {
					testHandler.accept(test);
				}
			}
		});
	}

	// PORT INFO: this function is based on source file lib/builder.js
	private Test262File builder(Path file, String contents) throws IOException {
		Path includesDir = this.includesDir;

		if (includesDir == null)
			includesDir = test262Dir.resolve("harness");

		final Test262File test = new Test262File(file, contents);

		if (test.getAttrs().isRaw() || omitRuntime) {
			test.setInsertionIndex(test.getAttrs().isRaw() ? -1 : 0);
			return test;
		}

		final List<String> includes = test.getAttrs().getIncludes();

		includes.add("assert.js");
		includes.add("sta.js");

		if (test.getAttrs().isAsync()) {
			includes.add("doneprintHandle.js");
		}

		StringBuilder includeContents = new StringBuilder();
		for (int i = 0; i < includes.size(); i++) {
			Files.lines(includesDir.resolve(includes.get(i))).forEach(includeContents::append);
			includeContents.append("\n");
		}

		test.setContents(includeContents.toString() + test.getContents());
		test.setInsertionIndex(includeContents.length());

		return test;
	}

	// PORT INFO: this function is based on source file lib/compile.js
	private void compile(Path filePath, BiConsumer<List<Test262File>, IOException> done) {
		try {
			String contents = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
			done.accept(createScenarios(builder(test262Dir.relativize(filePath), contents)), null);
		} catch (IOException error) {
			done.accept(Collections.emptyList(), error);
		}
	}

	// PORT INFO: this function is based on source file lib/create-scenarios.js
	private List<Test262File> createScenarios(Test262File test) {
		List<Test262File> scenarios = new ArrayList<>();

		if (!test.getAttrs().isOnlyStrict()) {
			test.setScenario("default");
			scenarios.add(test);
		}

		if (!test.getAttrs().isNoStrict() && !test.getAttrs().isRaw()) {
			Test262File copy = new Test262File(test);
			copy.getAttrs().setDescription(copy.getAttrs().getDescription() + " (Strict Mode)");
			copy.setContents(usd + copy.getContents());
			copy.setInsertionIndex(copy.getInsertionIndex() + usdLength);
			copy.setScenario("strict mode");
			scenarios.add(copy);
		}

		return scenarios;
	}

	public static class Builder {
		private final Path test262Dir;
		private boolean parallel = false;
		private int threadNumber = Runtime.getRuntime().availableProcessors();
		private Path includesDir;
		private List<Path> paths;
		private boolean omitRuntime = false;
		private String acceptVersion = "";

		/**
		 * Stream262 builder constructor
		 * 
		 * @param test262Dir filesystem path to a directory containing Test262
		 */
		public Builder(Path test262Dir) {
			this.test262Dir = test262Dir;
		}

		public Builder parallel() {
			this.parallel = true;
			return this;
		}

		public Builder threadNumber(int threadNumber) {
			this.threadNumber = threadNumber;
			return this;
		}

		/**
		 * Directory from which to load "includes" files (defaults to the appropriate
		 * subdirectory of the provided 'test262Dir')
		 * 
		 * @param includesDir see description
		 * @return current stream builder
		 */
		public Builder includesDir(Path includesDir) {
			this.includesDir = includesDir;
			return this;
		}

		/**
		 * File system paths refining the set of tests that should be produced;
		 * only tests whose source file matches one of these values (in the case of file
		 * paths) or is contained
		 * by one of these paths (in the case of directory paths) will be created;
		 * all paths are interpreted relative to the root of the provided 'test262Dir'
		 * 
		 * @param paths see description
		 * @return current stream builder
		 */
		public Builder paths(List<Path> paths) {
			this.paths = paths;
			return this;
		}

		/**
		 * Flag to disable the insertion of code necessary to execute the test
		 * (e.g. assertion functions and "include" files); defaults to 'false'
		 * 
		 * @return current stream builder
		 */
		public Builder omitRuntime() {
			this.omitRuntime = true;
			return this;
		}

		/**
		 * By default, this stream will emit an error if the provided version of Test262
		 * is not supported;
		 * this behavior may be disabled by providing a value of the expected version.
		 * Use of this option may
		 * cause the stream to emit invalid tests; consider updating the library
		 * instead.
		 * 
		 * @param acceptVersion see description
		 * @return current stream builder
		 */
		public Builder acceptVersion(String acceptVersion) {
			this.acceptVersion = acceptVersion;
			return this;
		}

		/**
		 * Builds new stream of ECMAScript tests
		 * 
		 * @return new stream
		 */
		public Test262Stream build() {
			return new Test262Stream(this);
		}
	}
}