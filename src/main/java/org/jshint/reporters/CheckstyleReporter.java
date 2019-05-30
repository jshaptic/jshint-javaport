package org.jshint.reporters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jshint.DataSummary;

//Author: Boy Baukema
//http://github.com/relaxnow
public class CheckstyleReporter implements JSHintReporter
{
	private static class Issue
	{
		String severity = "";
		int line = 0;
		int column = 0;
		String message = "";
		String source = "";
		
		public Issue(String severity, int line, int column, String message, String source)
		{
			this.severity = severity;
			this.line = line;
			this.column = column;
			this.message = message;
			this.source = source;
		}
	}
	
	private static final Map<String, String> pairs = new HashMap<String, String>();
	static
	{
		pairs.put("&", "&amp;");
		pairs.put("\"", "&quot;");
		pairs.put("'", "&apos;");
		pairs.put("<", "&lt;");
		pairs.put(">", "&gt;");
	}
	
	private String encode(String s)
	{
		for (String r : pairs.keySet())
		{
			if (StringUtils.isNotEmpty(s))
			{
				s = StringUtils.replace(s, r, pairs.get(r));
			}
		}
		return s != null ? s : "";
	}
	
	@Override
	public void generate(List<ReporterResult> results, List<DataSummary> data, String verbose)
	{	
		Map<String, List<Issue>> files = new HashMap<String, List<Issue>>();
		List<String> out = new ArrayList<String>();
		
		for (ReporterResult result : results)
		{
			// Register the file
			String file = StringUtils.removeStart(result.getFile(), "./");
			if (!files.containsKey(file))
			{
				files.put(file, new ArrayList<Issue>());
			}
			
			// Create the error message
			String errorMessage = result.getError().getReason();
			if (StringUtils.isNotEmpty(verbose))
			{
				errorMessage += " (" + result.getError().getCode() + ")";
			}
			
			String typeNo = result.getError().getCode();
			String severity = "";
			switch (typeNo.charAt(0))
			{
			case 'I':
				severity = "info";
				break;
			case 'W':
				severity = "warning";
				break;
			case 'E':
				severity = "error";
				break;
			}
			
			// Add the error
			files.get(file).add(new Issue(
					severity,
					result.getError().getLine(),
					result.getError().getCharacter(),
					errorMessage,
					"jshint." + result.getError().getCode()
			));
		}
		
		out.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		out.add("<checkstyle version=\"4.3\">");
		
		for (String fileName : files.keySet())
		{
			out.add("\t<file name=\"" + fileName + "\">");
			for (int i = 0; i < files.get(fileName).size(); i++)
			{
				Issue issue = files.get(fileName).get(i);
				out.add(
					"\t\t<error " +
					"line=\"" + issue.line + "\" " +
					"column=\"" + issue.column + "\" " +
					"severity=\"" + issue.severity + "\" " +
					"message=\"" + encode(issue.message) + "\" " +
					"source=\"" + encode(issue.source) + "\" " +
					"/>"
				);
			}
			out.add("\t</file>");
		}
		
		out.add("</checkstyle>");
		
		System.out.println(StringUtils.join(out, "\n"));
	}
}