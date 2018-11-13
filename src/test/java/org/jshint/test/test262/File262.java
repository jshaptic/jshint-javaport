package org.jshint.test.test262;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class File262
{
	private Path file;
	private String contents;
	private Attrs attrs;
	private String copyright;
	
	private int insertionIndex;
	private String scenario;
	
	public File262(Path file, String contents)
	{
		this.file = file;
		this.contents = contents;
	}
	
	public File262(File262 orig)
	{
		this.file = orig.file;
		this.contents = orig.contents;
		
		if (orig.attrs != null)
		{
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
	
	public Path getFile()
	{
		return file;
	}

	public void setFile(Path file)
	{
		this.file = file;
	}

	public String getContents()
	{
		return contents;
	}

	public void setContents(String contents)
	{
		this.contents = contents;
	}

	public Attrs getAttrs()
	{
		return attrs;
	}

	public void setAttrs(Attrs attrs)
	{
		this.attrs = attrs;
	}

	public String getCopyright()
	{
		return copyright;
	}

	public void setCopyright(String copyright)
	{
		this.copyright = copyright;
	}

	protected int getInsertionIndex()
	{
		return insertionIndex;
	}

	protected void setInsertionIndex(int insertionIndex)
	{
		this.insertionIndex = insertionIndex;
	}

	protected String getScenario()
	{
		return scenario;
	}

	protected void setScenario(String scenario)
	{
		this.scenario = scenario;
	}
	
	private static String getAsString(Object value)
	{
		return value != null ? String.valueOf(value) : "";
	}
	
	@SuppressWarnings({ "unused", "unchecked" })
	private static List<String> getAsStringList(Object value)
	{
		return value != null ? (List<String>)value : Collections.emptyList();
	}

	public static class Attrs
	{
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
		
		public Attrs()
		{
			
		}
		
		@SuppressWarnings("unchecked")
		Attrs(Map<String, Object> yaml)
		{
			this.description = getAsString(yaml.get("description"));
			this.info = getAsString(yaml.get("info"));
			
			if (yaml.containsKey("negative"))
			{
				Map<String, String> negative = (Map<String, String>)yaml.get("negative");
				this.negativeType = negative.getOrDefault("type", "");
				this.negativePhase = negative.getOrDefault("phase", "");
			}
			
			this.esid = getAsString(yaml.get("esid"));
			this.includes = getAsStringList(yaml.get("includes"));
			this.timeout = (int)yaml.getOrDefault("timeout", -1);
			this.author = getAsString(yaml.get("author"));
			this.flags = getAsStringList(yaml.get("flags"));
			this.features = getAsStringList(yaml.get("features"));
		}
		
		public String getDescription()
		{
			return description;
		}

		public void setDescription(String description)
		{
			this.description = description;
		}

		public String getInfo()
		{
			return info;
		}

		public void setInfo(String info)
		{
			this.info = info;
		}

		public String getNegativeType()
		{
			return negativeType;
		}

		public void setNegativeType(String negativeType)
		{
			this.negativeType = negativeType;
		}

		public String getNegativePhase()
		{
			return negativePhase;
		}

		public void setNegativePhase(String negativePhase)
		{
			this.negativePhase = negativePhase;
		}

		public String getEsid()
		{
			return esid;
		}

		public void setEsid(String esid)
		{
			this.esid = esid;
		}

		public List<String> getIncludes()
		{
			return includes;
		}
		
		public void setIncludes(List<String> includes)
		{
			this.includes = includes;
		}

		public int getTimeout()
		{
			return timeout;
		}

		public void setTimeout(int timeout)
		{
			this.timeout = timeout;
		}

		public String getAuthor()
		{
			return author;
		}

		public void setAuthor(String author)
		{
			this.author = author;
		}

		public List<String> getFlags()
		{
			return flags;
		}

		public void setFlags(List<String> flags)
		{
			this.flags = flags;
		}

		public List<String> getFeatures()
		{
			return features;
		}

		public void setFeatures(List<String> features)
		{
			this.features = features;
		}
		
		public boolean isOnlyStrict()
		{
			return flags.contains("onlyStrict");
		}
		
		public boolean isNoStrict()
		{
			return flags.contains("noStrict");
		}
		
		public boolean isModule()
		{
			return flags.contains("module");
		}

		public boolean isRaw()
		{
			return flags.contains("raw");
		}
		
		public boolean isAsync()
		{
			return flags.contains("async");
		}
		
		public boolean isGenerated()
		{
			return flags.contains("generated");
		}
		
		public boolean isCanBlockIsFalse()
		{
			return flags.contains("CanBlockIsFalse");
		}
		
		public boolean isCanBlockIsTrue()
		{
			return flags.contains("CanBlockIsTrue");
		}
	}
}