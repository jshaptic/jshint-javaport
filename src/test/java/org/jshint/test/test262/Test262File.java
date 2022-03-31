package org.jshint.test.test262;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.scanner.ScannerException;

// PORT INFO: based on the source file lib/test-file.js
public class Test262File {
	private static final Pattern copyrightRegex = Pattern.compile("^(?:(?:\\/\\/.*)*) Copyright.*");
	private static final String yamlStart = "/*---";
	private static final String yamlEnd = "---*/";

	/**
	 * filename
	 */
	private Path file;

	/**
	 * test code
	 */
	private String contents;

	/**
	 * parsed, normalized attributes
	 */
	private Attrs attrs;

	/**
	 * copyright message
	 */
	private String copyright;

	private int insertionIndex;
	private String scenario;

	public Test262File(Path file, String contents) {
		Objects.requireNonNull(file);
		Objects.requireNonNull(contents);

		this.file = file;
		this.contents = contents;
		this.attrs = extractAttrs(this);
		this.copyright = extractCopyright(this);
		this.scenario = null;
		this.insertionIndex = 0;
	}

	public Test262File(Test262File orig) {
		this.file = orig.file;
		this.contents = orig.contents;

		if (orig.attrs != null) {
			this.attrs = new Attrs();
			this.attrs.description = orig.attrs.description;
			this.attrs.info = orig.attrs.info;
			this.attrs.negativeType = orig.attrs.negativeType;
			this.attrs.negativePhase = orig.attrs.negativePhase;
			this.attrs.esid = orig.attrs.esid;
			this.attrs.includes = orig.attrs.includes;
			this.attrs.timeout = orig.attrs.timeout;
			this.attrs.author = orig.attrs.author;
			this.attrs.flags = orig.attrs.flags;
			this.attrs.features = orig.attrs.features;
		}

		this.copyright = orig.copyright;
		this.insertionIndex = orig.insertionIndex;
		this.scenario = orig.scenario;
	}

	/**
	 * Extract copyright message
	 *
	 * @param file file object
	 * @return the copyright string extracted from contents
	 */
	private static String extractCopyright(Test262File file) {
		String[] lines = file.getContents().split("\r|\n|\u2028|\u2029", -1);

		for (String line : lines) {
			// The very first line may be a hashbang, so look at
			// all lines until reaching a line that looks close
			// enough to copyright
			Matcher m = copyrightRegex.matcher(line);
			if (m.find() && m.group(0) != null) {
				return m.group(0);
			}
		}

		return "";
	}

	/**
	 * Extract YAML frontmatter from a test262 test
	 * 
	 * @param text text of test file
	 * @return the YAML frontmatter or empty string if none
	 */
	private static String extractYAML(Test262File file) {
		int start = file.getContents().indexOf(yamlStart);

		if (start > -1) {
			return file.getContents().substring(start + 5, file.getContents().indexOf(yamlEnd));
		}

		return "";
	}

	/**
	 * Extract and parse frontmatter from a test
	 * 
	 * @param file file object
	 * @return normalized attributes
	 */
	private static Attrs loadAttrs(Test262File file) {
		String extracted = extractYAML(file);

		if (!extracted.isEmpty()) {
			try {
				return new Attrs(new Yaml(new SafeConstructor()).load(extracted));
			} catch (ScannerException e) {
				throw new RuntimeException(
						"Error loading frontmatter from file " + file.getFile() + "\n" + e.getMessage());
			}
		}

		return new Attrs();
	}

	/**
	 * Normalize attributes; ensure that flags, includes exist
	 * 
	 * @param file file object
	 * @return normalized attributes
	 */
	private static Attrs extractAttrs(Test262File file) {
		Attrs attrs = loadAttrs(file);
		if (attrs.getFlags() == null)
			attrs.setFlags(Collections.emptyList());
		if (attrs.getIncludes() == null)
			attrs.setIncludes(Collections.emptyList());
		return attrs;
	}

	public Path getFile() {
		return file;
	}

	public void setFile(Path file) {
		this.file = file;
	}

	public String getContents() {
		return contents;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

	public Attrs getAttrs() {
		return attrs;
	}

	public void setAttrs(Attrs attrs) {
		this.attrs = attrs;
	}

	public String getCopyright() {
		return copyright;
	}

	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}

	protected int getInsertionIndex() {
		return insertionIndex;
	}

	protected void setInsertionIndex(int insertionIndex) {
		this.insertionIndex = insertionIndex;
	}

	protected String getScenario() {
		return scenario;
	}

	protected void setScenario(String scenario) {
		this.scenario = scenario;
	}

	private static String getAsString(Object value) {
		return value != null ? String.valueOf(value) : "";
	}

	public static class Attrs {
		private String description;
		private String info;

		private String negativeType;
		private String negativePhase;

		private String esid;
		private List<String> includes;
		private int timeout;
		private String author;
		private List<String> flags;
		private List<String> features;

		public Attrs() {

		}

		@SuppressWarnings("unchecked")
		private Attrs(Map<String, Object> yaml) {
			this.description = getAsString(yaml.get("description"));
			this.info = getAsString(yaml.get("info"));

			if (yaml.containsKey("negative")) {
				Map<String, String> negative = (Map<String, String>) yaml.get("negative");
				this.negativeType = negative.getOrDefault("type", "");
				this.negativePhase = negative.getOrDefault("phase", "");
			}

			this.esid = getAsString(yaml.get("esid"));
			this.includes = getAsStringList(yaml.get("includes"));
			this.timeout = (int) yaml.getOrDefault("timeout", -1);
			this.author = getAsString(yaml.get("author"));
			this.flags = getAsStringList(yaml.get("flags"));
			this.features = getAsStringList(yaml.get("features"));
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getInfo() {
			return info;
		}

		public void setInfo(String info) {
			this.info = info;
		}

		public String getNegativeType() {
			return negativeType;
		}

		public void setNegativeType(String negativeType) {
			this.negativeType = negativeType;
		}

		public String getNegativePhase() {
			return negativePhase;
		}

		public void setNegativePhase(String negativePhase) {
			this.negativePhase = negativePhase;
		}

		public String getEsid() {
			return esid;
		}

		public void setEsid(String esid) {
			this.esid = esid;
		}

		public List<String> getIncludes() {
			return includes;
		}

		public void setIncludes(List<String> includes) {
			this.includes = includes;
		}

		public int getTimeout() {
			return timeout;
		}

		public void setTimeout(int timeout) {
			this.timeout = timeout;
		}

		public String getAuthor() {
			return author;
		}

		public void setAuthor(String author) {
			this.author = author;
		}

		public List<String> getFlags() {
			return flags;
		}

		public void setFlags(List<String> flags) {
			this.flags = flags;
		}

		public List<String> getFeatures() {
			return features;
		}

		public void setFeatures(List<String> features) {
			this.features = features;
		}

		public boolean isOnlyStrict() {
			return flags.contains("onlyStrict");
		}

		public boolean isNoStrict() {
			return flags.contains("noStrict");
		}

		public boolean isModule() {
			return flags.contains("module");
		}

		public boolean isRaw() {
			return flags.contains("raw");
		}

		public boolean isAsync() {
			return flags.contains("async");
		}

		public boolean isGenerated() {
			return flags.contains("generated");
		}

		public boolean isCanBlockIsFalse() {
			return flags.contains("CanBlockIsFalse");
		}

		public boolean isCanBlockIsTrue() {
			return flags.contains("CanBlockIsTrue");
		}

		@SuppressWarnings("unchecked")
		private static List<String> getAsStringList(Object value) {
			return value != null ? (List<String>) value : Collections.emptyList();
		}
	}
}