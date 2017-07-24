package org.jshint.test.helpers;

import java.util.List;

import org.jshint.reporters.JSHintReporter;
import org.jshint.reporters.ReporterResult;

public interface TestReporter extends JSHintReporter
{
	public List<ReporterResult> getResults();
	public boolean isCalledOnce();
}
