package org.jshint;

import org.apache.commons.lang3.StringUtils;

public class JSHintException extends RuntimeException
{
	private static final long serialVersionUID = -2398758215833728845L;
	
	private LinterWarning warning;
	private String message;
	
	public JSHintException(LinterWarning warning, String message)
	{
		setWarning(warning);
		setMessage(message);
	}

	public LinterWarning getWarning()
	{
		return warning;
	}

	void setWarning(LinterWarning warning)
	{
		this.warning = warning;
	}

	public String getMessage()
	{
		return message;
	}

	void setMessage(String message)
	{
		this.message = StringUtils.defaultString(message);
	}
}