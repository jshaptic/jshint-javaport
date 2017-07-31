package org.jshint;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class InternalSource
{
	private String id;
	private Token elem;
	private Token token;
	private String code;
	
	InternalSource(String id, Token elem, Token token, String code)
	{
		setId(id);
		setElem(elem);
		setToken(token);
		setCode(code);
	}

	public String getId()
	{
		return id;
	}
	
	void setId(String id)
	{
		this.id = StringUtils.defaultString(id);
	}

	public Token getElem()
	{
		return elem;
	}
	
	void setElem(Token elem)
	{
		this.elem = elem;
	}
	
	public Token getToken()
	{
		return token;
	}
	
	void setToken(Token token)
	{
		this.token = token;
	}

	public String getCode()
	{
		return code;
	}
	
	void setCode(String code)
	{
		this.code = StringUtils.defaultString(code);
	}
	
	@Override
    public int hashCode()
	{
        return new HashCodeBuilder(17, 31) // two randomly chosen prime numbers
            .append(id)
            .append(elem)
            .append(token)
            .append(code)
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
			.append(this.token, other.token)
			.append(this.code, other.code)
			.isEquals();
	}
}