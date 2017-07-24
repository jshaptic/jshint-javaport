package org.jshint;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class InternalSource
{
	private String id;
	private Token elem;
	private String value;
	
	InternalSource(String id, Token elem, String value)
	{
		setId(id);
		setToken(elem);
		setValue(value);
	}

	public String getId()
	{
		return id;
	}
	
	void setId(String id)
	{
		this.id = StringUtils.defaultString(id);
	}

	public Token getToken()
	{
		return elem;
	}
	
	void setToken(Token elem)
	{
		this.elem = elem;
	}

	public String getValue()
	{
		return value;
	}
	
	void setValue(String value)
	{
		this.value = StringUtils.defaultString(value);
	}
	
	@Override
    public int hashCode()
	{
        return new HashCodeBuilder(17, 31) // two randomly chosen prime numbers
            .append(id)
            .append(elem)
            .append(value)
            .toHashCode();
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof InternalSource)) return false;
		if (obj == this) return true;
		
		InternalSource other = (InternalSource) obj;
		return new EqualsBuilder()
			.append(this.id, other.id)
			.append(this.elem, other.elem)
			.append(this.value, other.value)
			.isEquals();
	}
}