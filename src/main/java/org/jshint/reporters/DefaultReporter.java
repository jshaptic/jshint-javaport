package org.jshint.reporters;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jshint.DataSummary;
import org.jshint.LinterWarning;

public class DefaultReporter implements JSHintReporter
{	
	@Override
	public void generate(List<ReporterResult> results, List<DataSummary> data, String verbose)
	{
		int len = results.size();
		StringBuilder str = new StringBuilder();
		String prevfile = null;
		
		for (ReporterResult result : results)
		{
			String file = result.getFile();
			LinterWarning error = result.getError();
			
			if (StringUtils.isNotEmpty(prevfile) && !prevfile.equals(file))
			{
				str.append("\n");
			}
			prevfile = file;
			
			str.append(file + ": line " + error.getLine() + ", col " + error.getCharacter() + ", " + error.getReason());
			
			if (StringUtils.isNotEmpty(verbose))
			{
				str.append(" (" + error.getCode() + ")");
			}
			
			str.append("\n");
		}
		
		if (str.length() > 0)
		{
			System.out.println(str + "\n" + len + " error" + (len == 1 ? "" : "s"));
		}
	}
}