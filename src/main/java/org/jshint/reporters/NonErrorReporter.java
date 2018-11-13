package org.jshint.reporters;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jshint.DataSummary;
import org.jshint.ImpliedGlobal;
import org.jshint.LinterWarning;
import org.jshint.Token;

public class NonErrorReporter implements JSHintReporter
{
	@Override
	public void generate(List<ReporterResult> results, List<DataSummary> data, String verbose)
	{
		int len = results.size();
		StringBuilder str = new StringBuilder();
		
		for (ReporterResult result : results)
		{
			String file = result.getFile();
			LinterWarning error = result.getError();
			str.append(file + ": line " + error.getLine() + ", col " + error.getCharacter() + ", " + error.getReason());
			
			// Add the error code if the --verbose option is set
			if (StringUtils.isNotEmpty(verbose))
			{
				str.append(" (" + error.getCode() + ")");
			}
			
			str.append("\n");
		}
		
		str.append(len > 0 ? ("\n" + len + " error" + (len == 1 ? "" : "s")) : "");
		
		for (DataSummary d : data)
		{
			String file = d.getFile();
			List<ImpliedGlobal> globals = d.getImplieds();
			List<Token> unuseds = d.getUnused();
			
			if (globals.size() > 0 || unuseds.size() > 0)
			{
				str.append("\n\n" + file  + " :\n");
			}
			
			if (globals.size() > 0)
			{
				str.append("\tImplied globals:\n");
				for (ImpliedGlobal global : globals)
				{
					str.append("\t\t" + global.getName()  + ": " + global.getLines() + "\n");
				}
			}
			
			if (unuseds.size() > 0)
			{
				str.append("\tUnused Variables:\n\t\t");
				for (Token unused : unuseds)
				{
					str.append(unused.getName() + "(" + unused.getLine() + "), ");
				}
			}
		}
		
		if (str.length() > 0)
		{
			System.out.println(str + "\n");
		}
	}
}