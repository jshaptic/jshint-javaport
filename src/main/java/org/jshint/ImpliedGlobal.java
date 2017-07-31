package org.jshint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ImpliedGlobal
{
	private String name;
	private List<Integer> lines;
	
	public ImpliedGlobal(String name, Integer... lines)
	{
		setName(name);
		setLines(lines);
	}
	
	public String getName()
	{
		return name;
	}
	
	private void setName(String name)
	{
		this.name = StringUtils.defaultString(name);
	}
	
	public List<Integer> getLines()
	{
		return Collections.unmodifiableList(lines);
	}
	
	private void setLines(Integer... lines)
	{
		this.lines = lines != null ? new ArrayList<Integer>(Arrays.asList(lines)) : new ArrayList<Integer>();
	}
	
	void addLine(int line)
	{
		lines.add(line);
	}
	
	@Override
    public int hashCode()
	{
        return new HashCodeBuilder(17, 31) // two randomly chosen prime numbers
            .append(name)
            .append(lines)
            .toHashCode();
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof ImpliedGlobal)) return false;
		if (obj == this) return true;
		
		ImpliedGlobal other = (ImpliedGlobal) obj;
		return new EqualsBuilder()
			.append(this.name, other.name)
			.append(this.lines, other.lines)
			.isEquals();
	}
}