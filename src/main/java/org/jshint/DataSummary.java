package org.jshint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;

public class DataSummary
{
	private List<Function> functions;
	private UniversalContainer options;
	private List<LinterWarning> errors;
	private boolean json = false;
	private List<ImpliedGlobal> implieds;
	private List<String> urls;
	private Set<String> globals;
	private List<Token> unused;
	private Map<String, Integer> member;
	private String file = "";
	
	DataSummary(UniversalContainer options)
	{
		this.functions = new ArrayList<Function>();
		this.options = ContainerFactory.undefinedContainerIfNull(options);
	}
	
	public List<Function> getFunctions()
	{
		return Collections.unmodifiableList(functions);
	}
	
	protected void addFunction(Function function)
	{
		functions.add(function);
	}

	public UniversalContainer getOptions()
	{
		return options;
	}
	
	public List<LinterWarning> getErrors()
	{
		return errors != null ? Collections.unmodifiableList(errors) : Collections.<LinterWarning>emptyList();
	}
	
	protected void setErrors(List<LinterWarning> errors)
	{
		this.errors = errors;
	}

	public boolean isJson()
	{
		return json;
	}
	
	protected void setJson(boolean json)
	{
		this.json = json;
	}
	
	public List<ImpliedGlobal> getImplieds()
	{
		return implieds != null ? Collections.unmodifiableList(implieds) : Collections.<ImpliedGlobal>emptyList();
	}
	
	protected void setImplieds(List<ImpliedGlobal> implieds)
	{
		this.implieds = implieds;
	}

	public List<String> getUrls()
	{
		return urls != null ? Collections.unmodifiableList(urls) : Collections.<String>emptyList();
	}
	
	protected void setUrls(List<String> urls)
	{
		this.urls = urls;
	}

	public Set<String> getGlobals()
	{
		return globals != null ? Collections.unmodifiableSet(globals) : Collections.<String>emptySet();
	}
	
	protected void setGlobals(Set<String> globals)
	{
		this.globals = globals;
	}

	public List<Token> getUnused()
	{
		return unused != null ? Collections.unmodifiableList(unused) : Collections.<Token>emptyList();
	}
	
	protected void setUnused(List<Token> unused)
	{
		this.unused = unused;
	}

	public Map<String, Integer> getMember()
	{
		return member != null ? Collections.unmodifiableMap(member) : Collections.<String, Integer>emptyMap();
	}
	
	protected void setMember(Map<String, Integer> member)
	{
		this.member = member;
	}

	public String getFile()
	{
		return file;
	}

	protected void setFile(String file)
	{
		this.file = StringUtils.defaultString(file);
	}
	
	public static class Function
	{
		private String name;
		private List<String> param;
		private int line;
		private int character;
		private int last;
		private int lastCharacter;
		private Metrics metrics;
		
		protected Function()
		{
			
		}

		public String getName()
		{
			return name;
		}

		protected void setName(String name)
		{
			this.name = StringUtils.defaultString(name);
		}

		public List<String> getParam()
		{
			return param != null ? Collections.unmodifiableList(param) : Collections.<String>emptyList();
		}

		protected void setParam(List<String> param)
		{
			this.param = param;
		}

		public int getLine()
		{
			return line;
		}

		protected void setLine(int line)
		{
			this.line = line;
		}

		public int getCharacter()
		{
			return character;
		}

		protected void setCharacter(int character)
		{
			this.character = character;
		}

		public int getLast()
		{
			return last;
		}

		protected void setLast(int last)
		{
			this.last = last;
		}

		public int getLastCharacter()
		{
			return lastCharacter;
		}

		protected void setLastCharacter(int lastCharacter)
		{
			this.lastCharacter = lastCharacter;
		}

		public Metrics getMetrics()
		{
			return metrics;
		}

		protected void setMetrics(Metrics metrics)
		{
			this.metrics = metrics;
		}
	}
	
	public static class Metrics
	{
		private int complexity;
		private int parameters;
		private int statements;
		
		public Metrics(int complexity, int parameters, int statements)
		{
			setComplexity(complexity);
			setParameters(parameters);
			setStatements(statements);
		}
		
		public int getComplexity()
		{
			return complexity;
		}

		void setComplexity(int complexity)
		{
			this.complexity = complexity;
		}

		public int getParameters()
		{
			return parameters;
		}

		void setParameters(int parameters)
		{
			this.parameters = parameters;
		}

		public int getStatements()
		{
			return statements;
		}

		void setStatements(int statements)
		{
			this.statements = statements;
		}
		
		@Override
	    public int hashCode()
		{
	        return new HashCodeBuilder(17, 31) // two randomly chosen prime numbers
	            .append(complexity)
	            .append(parameters)
	            .append(statements)
	            .toHashCode();
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof Metrics)) return false;
			if (obj == this) return true;
			
			Metrics other = (Metrics) obj;
			return new EqualsBuilder()
				.append(this.complexity, other.complexity)
				.append(this.parameters, other.parameters)
				.append(this.statements, other.statements)
				.isEquals();
		}
	}
}