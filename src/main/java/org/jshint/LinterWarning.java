package org.jshint;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jshint.utils.SubstitutionMap;

public class LinterWarning
{
	private String id = "";
	private String raw = "";
	private String code = "";
	private String reason = "";
	private String evidence = "";
	private int line = 0;
	private int character = 0;
	private String scope = "";
	private SubstitutionMap substitutions;
	
	LinterWarning()
	{
		
	}
	
	public String getId()
	{
		return id;
	}
	
	void setId(String id)
	{
		this.id = StringUtils.defaultString(id);
	}

	public String getRaw()
	{
		return raw;
	}
	
	void setRaw(String raw)
	{
		this.raw = StringUtils.defaultString(raw);
	}

	public String getCode()
	{
		return code;
	}
	
	void setCode(String code)
	{
		this.code = StringUtils.defaultString(code);
	}
	
	public String getReason()
	{
		return reason;
	}
	
	void setReason(String reason)
	{
		this.reason = StringUtils.defaultString(reason);
	}

	public String getEvidence()
	{
		return evidence;
	}
	
	void setEvidence(String evidence)
	{
		this.evidence = StringUtils.defaultString(evidence);
	}

	public int getLine()
	{
		return line;
	}
	
	void setLine(int line)
	{
		this.line = line;
	}

	public int getCharacter()
	{
		return character;
	}
	
	void setCharacter(int character)
	{
		this.character = character;
	}
	
	void shiftCharacter(int offset)
	{
		character += offset;
	}

	public String getScope()
	{
		return scope;
	}
	
	void setScope(String scope)
	{
		this.scope = StringUtils.defaultString(scope);
	}
	
	String getSubstitution(String name)
	{
		return substitutions != null ? substitutions.get(name) : null;
	}
	
	void setSubstitutions(String... values)
	{
		substitutions = new SubstitutionMap(values);
	}
	
	@Override
    public int hashCode()
	{
        return new HashCodeBuilder(17, 31) // two randomly chosen prime numbers
            .append(id)
            .append(raw)
            .append(code)
            .append(reason)
            .append(evidence)
            .append(line)
            .append(character)
            .append(scope)
            .append(substitutions)
            .toHashCode();
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof LinterWarning)) return false;
		if (obj == this) return true;
		
		LinterWarning other = (LinterWarning) obj;
		return new EqualsBuilder()
			.append(this.id, other.id)
			.append(this.raw, other.raw)
			.append(this.code, other.code)
			.append(this.reason, other.reason)
			.append(this.evidence, other.evidence)
			.append(this.line, other.line)
			.append(this.character, other.character)
			.append(this.scope, other.scope)
			.append(this.substitutions, other.substitutions)
			.isEquals();
	}
}