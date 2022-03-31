package org.jshint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

public class LinterOptions implements Iterable<String> {
	private Map<String, InnerOption> table;
	private Map<String, Boolean> predefineds;
	private Map<String, Boolean> globals;
	private Map<String, Boolean> exporteds;
	private Map<String, Boolean> unstables;
	private List<Delimiter> ignoreDelimiters;

	public LinterOptions() {

	}

	protected LinterOptions(UniversalContainer config) {
		loadConfig(config);
	}

	protected LinterOptions(LinterOptions original) {
		if (original != null) {
			initMainTable(original.table);
			initPredefineds(original.predefineds);
			initGlobals(original.globals);
			initExporteds(original.exporteds);
			initUnstables(original.unstables);
			initIgnoredDelimiters(original.ignoreDelimiters);
		}
	}

	// API FOR MAIN OPTIONS TABLE

	private void initMainTable() {
		if (table == null) {
			table = new HashMap<>();
		}
	}

	private void initMainTable(Map<String, InnerOption> table) {
		if (table != null) {
			this.table = new HashMap<>(table);
		}
	}

	public LinterOptions set(String name, boolean value) {
		return setOption(name, value);
	}

	public LinterOptions set(String name, int value) {
		return setOption(name, value);
	}

	public LinterOptions set(String name, String value) {
		return setOption(name, value);
	}

	private LinterOptions setOption(String name, Object value) {
		initMainTable();

		if (name.startsWith("-W") && name.substring(2).length() == 3 && StringUtils.isNumeric(name.substring(2))) {
			table.put(name, new InnerOption(name.substring(1), new UniversalContainer(true), true, false));
		} else {
			table.put(name, new InnerOption(name, new UniversalContainer(value), false, false));
		}

		return this;
	}

	public LinterOptions remove(String name) {
		if (table != null) {
			table.remove(name);
		}

		return this;
	}

	protected UniversalContainer getOptions(State state, boolean ignored) {
		UniversalContainer options = state.getOption();

		if (table != null) {
			for (String key : table.keySet()) {
				InnerOption o = table.get(key);
				if (o.ignored == ignored) {
					options.set(o.name, o.value);
				}
			}
		}

		return options;
	}

	public boolean getAsBoolean(String name) {
		return table != null && table.containsKey(name) ? table.get(name).value.asBoolean() : false;
	}

	public int getAsInteger(String name) {
		return table != null && table.containsKey(name) ? table.get(name).value.asInt() : 0;
	}

	public String getAsString(String name) {
		return table != null && table.containsKey(name) ? table.get(name).value.asString() : "";
	}

	public boolean hasOption(String name) {
		return table != null && table.containsKey(name) && !table.get(name).hidden;
	}

	@Override
	public Iterator<String> iterator() {
		if (table == null)
			return Collections.emptyIterator();

		return new Iterator<String>() {

			private List<String> keys = new ArrayList<>(table.keySet());
			private int index = -1;

			@Override
			public boolean hasNext() {
				int i = index + 1;

				while (true) {
					if (i >= keys.size())
						return false;

					if (!table.get(keys.get(i)).hidden)
						return true;

					i++;
				}
			}

			@Override
			public String next() {
				index++;

				while (true) {
					if (index >= keys.size())
						return null;

					if (!table.get(keys.get(index)).hidden)
						return keys.get(index);

					index++;
				}
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	// API FOR PREDEFINEDS

	private void initPredefineds() {
		if (predefineds == null) {
			predefineds = new HashMap<>();
		}
	}

	private void initPredefineds(Map<String, Boolean> predefineds) {
		if (predefineds != null) {
			this.predefineds = new HashMap<>(predefineds);
		}
	}

	public LinterOptions setPredefineds(String... predefineds) {
		initPredefineds();
		this.predefineds.clear();

		return addPredefineds(predefineds);
	}

	public LinterOptions addPredefined(String name, boolean value) {
		initPredefineds();
		predefineds.put(name, value);

		return this;
	}

	public LinterOptions addPredefineds(String... predefineds) {
		initPredefineds();

		if (predefineds != null) {
			for (String p : predefineds) {
				this.predefineds.put(p, false);
			}
		}

		return this;
	}

	public LinterOptions removePredefined(String name) {
		if (predefineds != null) {
			predefineds.remove(name);
		}

		return this;
	}

	protected void readPredefineds(Map<String, Boolean> predefineds, Set<String> blacklist) {
		updatePredefinedsAndBlacklist(this.predefineds, predefineds, blacklist);
	}

	public Map<String, Boolean> getPredefineds() {
		return predefineds != null ? Collections.unmodifiableMap(predefineds) : Collections.<String, Boolean>emptyMap();
	}

	// API FOR GLOBALS

	private void initGlobals() {
		if (globals == null) {
			globals = new HashMap<>();
		}
	}

	private void initGlobals(Map<String, Boolean> globals) {
		if (globals != null) {
			this.globals = new HashMap<>(globals);
		}
	}

	public LinterOptions setGlobals(String... globals) {
		initGlobals();
		this.globals.clear();

		return addGlobals(globals);
	}

	public LinterOptions addGlobal(String name, boolean value) {
		initGlobals();
		globals.put(name, value);

		return this;
	}

	public LinterOptions addGlobals(String... globals) {
		initGlobals();

		if (globals != null) {
			for (String p : globals) {
				this.globals.put(p, false);
			}
		}

		return this;
	}

	public LinterOptions removeGlobal(String name) {
		if (globals != null) {
			globals.remove(name);
		}

		return this;
	}

	protected void readGlobals(Map<String, Boolean> predefineds, Set<String> blacklist) {
		updatePredefinedsAndBlacklist(this.globals, predefineds, blacklist);
	}

	public Map<String, Boolean> getGlobals() {
		return globals != null ? Collections.unmodifiableMap(globals) : Collections.<String, Boolean>emptyMap();
	}

	// API FOR EXPORTEDS

	private void initExporteds() {
		if (exporteds == null) {
			exporteds = new HashMap<>();
		}
	}

	private void initExporteds(Map<String, Boolean> exporteds) {
		if (exporteds != null) {
			this.exporteds = new HashMap<>(exporteds);
		}
	}

	public LinterOptions setExporteds(String... exporteds) {
		initExporteds();
		this.exporteds.clear();

		return addExporteds(exporteds);
	}

	public LinterOptions addExporteds(String... exporteds) {
		initExporteds();

		if (exporteds != null) {
			for (String e : exporteds) {
				this.exporteds.put(e, true);
			}
		}

		return this;
	}

	public LinterOptions addExporteds(List<String> exporteds) {
		initExporteds();

		if (exporteds != null) {
			for (String e : exporteds) {
				this.exporteds.put(e, true);
			}
		}

		return this;
	}

	public LinterOptions removeExported(String name) {
		if (exporteds != null) {
			exporteds.remove(name);
		}

		return this;
	}

	protected void readExporteds(Map<String, Boolean> exporteds) {
		if (this.exporteds == null)
			return;

		for (String item : this.exporteds.keySet()) {
			exporteds.put(item, true);
		}
	}

	public List<String> getExporteds() {
		return exporteds != null ? Collections.unmodifiableList(new ArrayList<>(exporteds.keySet()))
				: Collections.<String>emptyList();
	}

	// API FOR UNSTABLES

	private void initUnstables() {
		if (unstables == null) {
			unstables = new HashMap<>();
		}
	}

	private void initUnstables(Map<String, Boolean> unstables) {
		if (unstables != null) {
			this.unstables = new HashMap<>(unstables);
		}
	}

	public LinterOptions setUnstables(String... unstables) {
		initUnstables();
		this.unstables.clear();

		return addUnstables(unstables);
	}

	public LinterOptions addUnstables(String... unstables) {
		initUnstables();

		if (unstables != null) {
			for (String e : unstables) {
				this.unstables.put(e, true);
			}
		}

		return this;
	}

	public LinterOptions addUnstables(List<String> unstables) {
		initUnstables();

		if (unstables != null) {
			for (String e : unstables) {
				this.unstables.put(e, true);
			}
		}

		return this;
	}

	public LinterOptions removeUnstable(String name) {
		if (unstables != null) {
			unstables.remove(name);
		}

		return this;
	}

	public List<String> getUnstables() {
		return unstables != null ? Collections.unmodifiableList(new ArrayList<>(unstables.keySet()))
				: Collections.<String>emptyList();
	}

	// API FOR IGNORE DELIMITERS

	public static class Delimiter {

		private String start;
		private String end;

		public Delimiter(String start, String end) {
			this.start = start;
			this.end = end;
		}

		public String getStart() {
			return start;
		}

		public String getEnd() {
			return end;
		}
	}

	private void initIgnoredDelimiters() {
		if (ignoreDelimiters == null) {
			ignoreDelimiters = new ArrayList<>();
		}
	}

	private void initIgnoredDelimiters(List<Delimiter> ignoreDelimiters) {
		if (ignoreDelimiters != null) {
			this.ignoreDelimiters = new ArrayList<>(ignoreDelimiters);
		}
	}

	public LinterOptions addIgnoreDelimiter(String start, String end) {
		initIgnoredDelimiters();
		ignoreDelimiters.add(new Delimiter(start, end));

		return this;
	}

	public LinterOptions removeIgnoreDelimiter(String start, String end) {
		for (Iterator<Delimiter> i = ignoreDelimiters.iterator(); i.hasNext();) {
			Delimiter d = i.next();
			if (d.start.equals(start) && d.end.equals(end)) {
				i.remove();
				break;
			}
		}

		return this;
	}

	public List<Delimiter> getIgnoreDelimiters() {
		return ignoreDelimiters != null ? Collections.unmodifiableList(ignoreDelimiters)
				: Collections.<Delimiter>emptyList();
	}

	public void loadConfig(UniversalContainer config) {
		UniversalContainer o = ContainerFactory.undefinedContainerIfNull(config);

		if (o.test()) {
			// parse predefiends
			for (String item : convertToList(o, "predef")) {
				addPredefined(item, o.get("predef").asBoolean(item));
			}

			// parse exporteds
			addExporteds(convertToList(o, "exported"));

			// parse unstables
			addUnstables(convertToList(o, "unstable"));

			// parse normal options
			for (String optionKey : o.keys()) {
				if (optionKey.equals("predef") || optionKey.equals("exported"))
					continue;

				setOption(optionKey, o.get(optionKey));
			}

			// parse delimiter
			if (o.test("ignoreDelimiters")) {
				if (o.isArray("ignoreDelimiters")) {
					for (UniversalContainer pair : o.get("ignoreDelimiters")) {
						addIgnoreDelimiter(pair.asString("start"), pair.asString("end"));
					}
				} else {
					addIgnoreDelimiter(o.get("ignoreDelimiters").asString("start"),
							o.get("ignoreDelimiters").asString("end"));
				}
			}
		}
	}

	private List<String> convertToList(UniversalContainer container, String name) {
		List<String> result = Collections.emptyList();

		if (container.test(name)) {
			if (container.isArray(name)) {
				result = container.get(name).asList(String.class);
			} else {
				result = new ArrayList<>(container.get(name).keys());
			}
		}

		return result;
	}

	private void updatePredefinedsAndBlacklist(Map<String, Boolean> dict, Map<String, Boolean> predefineds,
			Set<String> blacklist) {
		if (dict == null)
			return;

		for (String item : dict.keySet()) {
			if (item.startsWith("-")) {
				String slice = item.substring(1);
				blacklist.add(slice);
				// remove from predefined if there
				predefineds.remove(slice);
			} else {
				predefineds.put(item, dict.get(item));
			}
		}
	}

	private static class InnerOption {

		private String name;
		private UniversalContainer value;
		private boolean ignored;
		private boolean hidden;

		public InnerOption(String name, UniversalContainer value, boolean ignored, boolean hidden) {
			this.name = name;
			this.value = value;
			this.ignored = ignored;
			this.hidden = hidden;
		}
	}
}