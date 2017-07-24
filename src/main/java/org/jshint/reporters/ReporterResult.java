package org.jshint.reporters;

import org.jshint.LinterWarning;

public class ReporterResult
{
	private String file;
	private LinterWarning error;
	
	public ReporterResult(String file, LinterWarning err)
	{
		this.file = file;
		this.error = err;
	}
	
	public String getFile()
	{
		return file;
	}

	public LinterWarning getError()
	{
		return error;
	}
}