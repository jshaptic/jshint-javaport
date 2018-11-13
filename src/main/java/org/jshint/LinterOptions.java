package org.jshint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

public class LinterOptions implements Iterable<String>
{
	private Map<String, InnerOption> options;
	private Map<String, Boolean> predefineds;
	private Map<String, Boolean> exporteds;
	private List<Delimiter> ignoreDelimiters;
	
	public LinterOptions()
	{
		
	}
	
	protected LinterOptions(UniversalContainer config)
	{
		loadConfig(config);
	}
	
	protected LinterOptions(LinterOptions original)
	{
		if (original != null)
		{
			initOptions(original.options);
			initPredefinedTable(original.predefineds);
			initExportedTable(original.exporteds);
			initIgnoredDelimitersList(original.ignoreDelimiters);
		}
	}
	
	public LinterOptions addExporteds(String... exporteds)
	{
		initExportedTable();
		
		if (exporteds != null)
		{
			for (String e : exporteds)
			{
				this.exporteds.put(e, true);
			}
		}
		
		return this;
	}
	
	public LinterOptions addExporteds(List<String> exporteds)
	{
		initExportedTable();
		
		if (exporteds != null)
		{
			for (String e : exporteds)
			{
				this.exporteds.put(e, true);
			}
		}
		
		return this;
	}
	
	public LinterOptions addIgnoreDelimiter(String start, String end)
	{
		initIgnoredDelimitersList();
		
		ignoreDelimiters.add(new Delimiter(start, end));
		
		return this;
	}
	
	public LinterOptions addPredefined(String predefined, boolean value)
	{
		initPredefinedTable();
		
		this.predefineds.put(predefined, value);
		
		return this;
	}
	
	public LinterOptions addPredefineds(String... predefineds)
	{
		initPredefinedTable();
		
		if (predefineds != null)
		{
			for (String p : predefineds)
			{
				this.predefineds.put(p, false);
			}
		}
		
		return this;
	}
	
	public LinterOptions addPredefineds(Map<String, Boolean> predefineds)
	{
		initPredefinedTable();
		
		if (predefineds != null)
		{
			this.predefineds.putAll(predefineds);
		}
		
		return this;
	}
	
	public boolean getAsBoolean(String name)
	{
		return options != null && options.containsKey(name) ? options.get(name).value.asBoolean() : false;
	}
	
	public int getAsInteger(String name)
	{
		return options != null && options.containsKey(name) ? options.get(name).value.asInt() : 0;
	}
	
	public String getAsString(String name)
	{
		return options != null && options.containsKey(name) ? options.get(name).value.asString() : "";
	}
	
	public Map<String, Boolean> getExporteds()
	{
		return exporteds != null ? Collections.unmodifiableMap(exporteds) : Collections.<String, Boolean>emptyMap();
	}
	
	public Map<String, Boolean> getPredefineds()
	{
		return predefineds != null ? Collections.unmodifiableMap(predefineds) : Collections.<String, Boolean>emptyMap();
	}
	
	public boolean hasOption(String name)
	{
		return options != null && options.containsKey(name) && !options.get(name).hidden;
	}
	
	@Override
	public Iterator<String> iterator()
	{
		if (options == null)
			return Collections.emptyIterator();
		
		return new Iterator<String>()
			{
				private List<String> keys = new ArrayList<String>(options.keySet());
				private int index = -1;
			
				@Override
				public boolean hasNext()
				{
					int i = index + 1;
					
					while (true)
					{
						if (i >= keys.size())
							return false;
						
						if (!options.get(keys.get(i)).hidden)
							return true;
						
						i++;
					}
				}

				@Override
				public String next()
				{
					index++;
					
					while (true)
					{
						if (index >= keys.size())
							return null;
						
						if (!options.get(keys.get(index)).hidden)
							return keys.get(index);
						
						index++;
					}
				}

				@Override
				public void remove()
				{
					throw new UnsupportedOperationException();
				}
			};
	}
	
	public void loadConfig(UniversalContainer config)
	{
		UniversalContainer o = ContainerFactory.undefinedContainerIfNull(config);

		if (o.test())
		{
			// parse predefiends
			for (String item : convertToList(o, "predef"))
			{
				addPredefined(item, o.get("predef").asBoolean(item));
			}
			
			// parse exporteds
			addExporteds(convertToList(o, "exported"));
			
			// parse normal options
			for (String optionKey: o.keys())
			{
				if (optionKey.equals("predef") || optionKey.equals("exported"))
					continue;
				
				saveOption(optionKey, o.get(optionKey));
			}
			
			// parse delimiter
			if (o.test("ignoreDelimiters"))
			{
				if (o.isArray("ignoreDelimiters"))
				{
					for (UniversalContainer pair : o.get("ignoreDelimiters"))
					{
						addIgnoreDelimiter(pair.asString("start"), pair.asString("end"));
					}
				}
				else
				{
					addIgnoreDelimiter(o.get("ignoreDelimiters").asString("start"), o.get("ignoreDelimiters").asString("end"));
				}
			}
		}
	}
	
	public void remove(String name)
	{
		options.remove(name);
	}
	
	public void removeExported(String name)
	{
		exporteds.remove(name);
	}
	
	public void removeIgnoreDelimiter(String start, String end)
	{
		for (Iterator<Delimiter> i = ignoreDelimiters.iterator(); i.hasNext();)
		{
			Delimiter d = i.next();
			if (d.start.equals(start) && d.end.equals(end))
			{
				i.remove();
				break;
			}
		}
	}
	
	public void removePredefined(String name)
	{
		predefineds.remove(name);
	}
	
	public LinterOptions set(String name, boolean value)
	{
		return saveOption(name, value);
	}
	
	public LinterOptions set(String name, int value)
	{
		return saveOption(name, value);
	}
	
	public LinterOptions set(String name, String value)
	{
		return saveOption(name, value);
	}
	
	public LinterOptions setPredefineds(String... predefineds)
	{
		initPredefinedTable();
		this.predefineds.clear();
		
		return addPredefineds(predefineds);
	}
	
	public LinterOptions setPredefineds(Map<String, Boolean> predefineds)
	{
		initPredefinedTable(predefineds);
		this.predefineds.clear();
		
		return addPredefineds(predefineds);
	}
	
	public LinterOptions setExporteds(String... exporteds)
	{
		initExportedTable();
		this.exporteds.clear();
		
		return addExporteds(exporteds);
	}
	
	protected String cleanSource(String source)
	{
		String result = StringUtils.defaultString(source);
		
		if (ignoreDelimiters != null)
		{
			for (Delimiter delimiterPair : ignoreDelimiters)
			{
				if (StringUtils.isEmpty(delimiterPair.start) || StringUtils.isEmpty(delimiterPair.end))
					continue;
				
				String reIgnoreStr = escapeRegex(delimiterPair.start) +
									 "[\\s\\S]*?" + 
									 escapeRegex(delimiterPair.end);
				
				Pattern reIgnore = Pattern.compile(reIgnoreStr, Pattern.CASE_INSENSITIVE);
				
				result = Reg.replaceAll(reIgnore, result, (str, groups) -> str.replaceAll(".", " "));
			}
		}
		
		return result;
	}
	
	private List<String> convertToList(UniversalContainer container, String name)
	{
		List<String> result = Collections.emptyList();
		
		if (container.test(name))
		{
			if (container.isArray(name))
			{
				result = container.get(name).asList(String.class);
			}
			else
			{
				result = Collections.unmodifiableList(new ArrayList<String>(container.get(name).keys()));
			}
		}
		
		return result;
	}
	
	private String escapeRegex(String str)
	{
		return str.replaceAll("([-\\/\\\\^$*+?.()|\\[\\]{}])", "\\\\$1");
	}
	
	protected UniversalContainer extractIgnoredOptions()
	{
		UniversalContainer ignoredOptions = ContainerFactory.createObject();
		
		if (options != null)
		{
			for (String key : options.keySet())
			{
				InnerOption o = options.get(key);
				if (o.ignored)
				{
					ignoredOptions.set(o.name, o.value);
				}
			}
		}
		
		return ignoredOptions;
	}
	
	protected UniversalContainer extractNormalOptions()
	{
		UniversalContainer normalOptions = ContainerFactory.createObject();
		
		if (options != null)
		{
			for (String key : options.keySet())
			{
				InnerOption o = options.get(key);
				if (!o.ignored)
				{
					normalOptions.set(o.name, o.value);
				}
			}
		}
		
		return normalOptions;
	}
	
	private void initOptions()
	{
		if (options == null)
		{
			options = new HashMap<String, InnerOption>();
		}
	}
	
	private void initOptions(Map<String, InnerOption> source)
	{
		if (source != null)
		{
			options = new HashMap<String, InnerOption>(source);
		}
	}
	
	private void initPredefinedTable()
	{
		if (predefineds == null)
		{
			predefineds = new HashMap<String, Boolean>();
		}
	}
	
	private void initPredefinedTable(Map<String, Boolean> source)
	{
		if (source != null)
		{
			predefineds = new HashMap<String, Boolean>(source);
		}
	}
	
	private void initExportedTable()
	{
		if (exporteds == null)
		{
			exporteds = new HashMap<String, Boolean>();
		}
	}
	
	private void initExportedTable(Map<String, Boolean> source)
	{
		if (source != null)
		{
			exporteds = new HashMap<String, Boolean>(source);
		}
	}
	
	private void initIgnoredDelimitersList()
	{
		if (ignoreDelimiters == null)
		{
			ignoreDelimiters = new ArrayList<Delimiter>();
		}
	}
	
	private void initIgnoredDelimitersList(List<Delimiter> source)
	{
		if (source != null)
		{
			ignoreDelimiters = new ArrayList<Delimiter>(source);
		}
	}
	
	protected void readExporteds(Map<String, Boolean> exporteds)
	{
		if (this.exporteds == null)
			return;
		
		for (String item : this.exporteds.keySet())
		{
			exporteds.put(item, true);
		}
	}
	
	protected void readPredefineds(Map<String, Boolean> predefineds, Set<String> blacklist)
	{
		if (this.predefineds == null)
			return;
		
		for (String item : this.predefineds.keySet())
		{
			if (item.startsWith("-"))
			{
				String slice = item.substring(1);
				blacklist.add(slice);
				// remove from predefined if there
				predefineds.remove(slice);
			}
			else
			{
				predefineds.put(item, this.predefineds.get(item));
			}
		}
	}
	
	private LinterOptions saveOption(String name, Object value)
	{
		initOptions();
		
		boolean isIgnored = false;
		if (name.startsWith("-W"))
		{
			String code = name.substring(2);
			if (code.length() == 3)
			{
				isIgnored = true;
				for (char c : code.toCharArray())
				{
					switch (c)
					{
			        case '0':
			        case '1':
			        case '2':
			        case '3':
			        case '4':
			        case '5':
			        case '6':
			        case '7':
			        case '8':
			        case '9':
			            continue;
					}
					isIgnored = false;
					break;
				}
			}
		}
		
		if (isIgnored)
		{
			options.put(name, new InnerOption(name.substring(1), new UniversalContainer(true), true, false));
		}
		else
		{
			options.put(name, new InnerOption(name, new UniversalContainer(value), false, false));
		}
		
		return this;
	}
	
	private static class InnerOption
	{
		private String name;
		private UniversalContainer value;
		private boolean ignored;
		private boolean hidden;
		
		public InnerOption(String name, UniversalContainer value, boolean ignored, boolean hidden)
		{
			this.name = name;
			this.value = value;
			this.ignored = ignored;
			this.hidden = hidden;
		}
	}
	
	private static class Delimiter
	{
		private String start;
		private String end;
		
		public Delimiter(String s, String e)
		{
			start = s;
			end = e;
		}
	}
}