import java.util.List;

import org.jshint.DataSummary;
import org.jshint.reporters.ReporterResult;
import org.jshint.test.helpers.TestReporter;

public class SimpleReporter implements TestReporter
{
	private List<ReporterResult> results;
	private int callCounter = 0;
	
	@Override
	public void generate(List<ReporterResult> results, List<DataSummary> data, String verbose)
	{
		this.results = results;
		this.callCounter++;
	}
	
	@Override
	public List<ReporterResult> getResults()
	{
		return results;
	}
	
	@Override
	public boolean isCalledOnce()
	{
		return this.callCounter == 1;
	}
}
