package org.jshint.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jshint.Token;

public class EventContext
{
	private String code = "";
	private String value = "";
	private String quote = "";
	private String name = "";
	private String raw_name = "";
	
	private int line = 0;
	private int character = 0;
	private int from = 0;
	private int startLine = 0;
	private int startChar = 0;
	private int base = 0;
	
	private boolean isProperty = false;
	private boolean isMalformed = false;
	
	private Token token = null;
	private SubstitutionMap data = null;
	
	public EventContext()
	{
		
	}

	public String getCode()
	{
		return code;
	}

	public void setCode(String code)
	{
		this.code = StringUtils.defaultString(code);
	}

	public String getValue()
	{
		return value;
	}

	public void setValue(String value)
	{
		this.value = StringUtils.defaultString(value);
	}

	public String getQuote()
	{
		return quote;
	}

	public void setQuote(String quote)
	{
		this.quote = quote;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = StringUtils.defaultString(name);
	}

	public String getRawName()
	{
		return raw_name;
	}

	public void setRawName(String rawName)
	{
		this.raw_name = StringUtils.defaultString(rawName);
	}

	public int getLine()
	{
		return line;
	}

	public void setLine(int line)
	{
		this.line = line;
	}

	public int getCharacter()
	{
		return character;
	}

	public void setCharacter(int character)
	{
		this.character = character;
	}

	public int getFrom()
	{
		return from;
	}

	public void setFrom(int from)
	{
		this.from = from;
	}

	public int getStartLine()
	{
		return startLine;
	}

	public void setStartLine(int startLine)
	{
		this.startLine = startLine;
	}

	public int getStartChar()
	{
		return startChar;
	}

	public void setStartChar(int startChar)
	{
		this.startChar = startChar;
	}

	public int getBase()
	{
		return base;
	}

	public void setBase(int base)
	{
		this.base = base;
	}

	public boolean isProperty()
	{
		return isProperty;
	}

	public void setProperty(boolean isProperty)
	{
		this.isProperty = isProperty;
	}

	public boolean isMalformed()
	{
		return isMalformed;
	}

	public void setMalformed(boolean isMalformed)
	{
		this.isMalformed = isMalformed;
	}

	public Token getToken()
	{
		return token;
	}

	public void setToken(Token token)
	{
		this.token = token;
	}

	public String[] getData()
	{
		return data != null ? data.toArray() : null;
	}

	public void setData(String... data)
	{
		this.data = new SubstitutionMap(data);
	}
	
	@Override
    public int hashCode()
	{
        return new HashCodeBuilder(17, 31) // two randomly chosen prime numbers
            .append(code)
            .append(value)
            .append(quote)
            .append(name)
            .append(raw_name)
            .append(line)
            .append(character)
            .append(from)
            .append(startLine)
            .append(startChar)
            .append(base)
            .append(isProperty)
            .append(isMalformed)
            .append(token)
            .toHashCode();
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof EventContext)) return false;
		if (obj == this) return true;
		
		EventContext other = (EventContext) obj;
		return new EqualsBuilder()
			.append(this.code, other.code)
			.append(this.value, other.value)
			.append(this.quote, other.quote)
			.append(this.name, other.name)
			.append(this.raw_name, other.raw_name)
			.append(this.line, other.line)
			.append(this.character, other.character)
			.append(this.from, other.from)
			.append(this.startLine, other.startLine)
			.append(this.startChar, other.startChar)
			.append(this.base, other.base)
			.append(this.isProperty, other.isProperty)
			.append(this.isMalformed, other.isMalformed)
			.append(this.token, other.token)
			.isEquals();
	}
}