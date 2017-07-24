package org.jshint.reporters;

import java.util.List;

import org.jshint.DataSummary;

public interface JSHintReporter
{
	public void generate(List<ReporterResult> results, List<DataSummary> data, String verbose);
}
