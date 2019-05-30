package org.jshint.reporters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jshint.DataSummary;
import org.jshint.LinterWarning;

public class JslintXmlReporter implements JSHintReporter
{
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
		Map<String, List<LinterWarning>> files = new HashMap<String, List<LinterWarning>>();
		List<String> out = new ArrayList<String>();
		
		for (ReporterResult result : results)
		{
			String file = StringUtils.removeStart(result.getFile(), "./");
			if (!files.containsKey(file))
			{
				files.put(file, new ArrayList<LinterWarning>());
			}
			
			files.get(file).add(result.getError());
		}
		
		out.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		out.add("<jslint>");
		
		for (String file : files.keySet())
		{
			out.add("\t<file name=\"" + file + "\">");
			for (int i = 0; i < files.get(file).size(); i++)
			{
				LinterWarning issue = files.get(file).get(i);
				out.add("\t\t<issue line=\"" + issue.getLine() +
						"\" char=\"" + issue.getCharacter() +
					"\" reason=\"" + encode(issue.getReason()) +
					"\" evidence=\"" + encode(issue.getEvidence()) +
					(StringUtils.isNotEmpty(issue.getCode()) ? "\" severity=\"" + encode(String.valueOf(issue.getCode().charAt(0))) : "") +
					"\" />");
			}
			out.add("\t</file>");
		}
		
		out.add("</jslint>");
		
		System.out.println(StringUtils.join(out, "\n") + "\n");
	}
}