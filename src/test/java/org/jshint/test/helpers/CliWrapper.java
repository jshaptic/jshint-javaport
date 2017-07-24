package org.jshint.test.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jshint.Cli;
import org.jshint.JSHintException;
import org.jshint.reporters.JSHintReporter;
import org.jshint.utils.JSHintUtils;
import com.github.jshaptic.js4j.UniversalContainer;

public class CliWrapper extends Cli
{
	private List<String> out = new ArrayList<String>();
	private boolean isStdin = false;
	private int exitCode = 0;
	
	private RunOptions opts = null;
	private String[] args = null;
	private String lines = "";
	
	private boolean stubExit = false;
	private boolean stubRun = false;
	
	@Override
	protected void error(String msg)
	{
		out.add(msg);
	}
	
	@Override
	public void exit(int code) throws ExitException
	{
		exitCode = code;
		if (!stubExit)
		{
			throw new ExitException(code);
		}
	}
	
	@Override
	public boolean run(RunOptions opts) throws JSHintException, ExitException, IOException
	{
		this.opts = opts;
		if (stubRun)
		{
			return true;
		}
		return super.run(opts);
	}
	
	@Override
	public int interpret(String... args)
	{
		isStdin = (args.length > 0 && (args[args.length-1].equals("-") || args[args.length-1].equals("/dev/stdin")));
		
		if (isStdin)
		{
			this.args = args;
			exitCode = 0;
		}
		else
		{
			return super.interpret(args);
		}
		
		return exitCode;
	}
	
	public void end()
	{
		if (isStdin)
		{
			JSHintUtils.cli = new JSHintUtils.CliUtils()
			{
				@Override
				public String readFromStdin()
				{
					return lines;
				}
			};
			
			super.interpret(args);
		}
	}
	
	public void send(String... lines)
	{
		if (isStdin)
		{
			for (String l : lines) this.lines += l + "\n";
		}
    }
	
	public List<String> getErrorMessages()
	{
		return out;
	}
	
	public int getExitCode()
	{
		return exitCode;
	}
	
	public void restore()
	{
		opts = null;
		isStdin = false;
		args = null;
		lines = "";
		out = new ArrayList<String>();
		stubExit = false;
		stubRun = false;
	}
	
	public String[] getArgs()
	{
		return opts != null ? opts.getArgs() : null;
	}
	
	public UniversalContainer getConfig()
	{
		return opts != null ? opts.getConfig() : null;
	}
	
	public JSHintReporter getReporter()
	{
		return opts != null ? opts.getReporter() : null;
	}
	
	public TestReporter getTestReporter()
	{
		JSHintReporter reporter = getReporter();
		return reporter instanceof TestReporter ? (TestReporter) reporter : null;
	}
	
	public List<String> getIgnores()
	{
		return opts != null ? opts.getIgnores() : null;
	}
	
	public String getExtensions()
	{
		return opts != null ? opts.getExtensions() : "";
	}
	
	public void toggleExit(boolean run)
	{
		stubExit = !run;
	}
	
	public void toggleRun(boolean run)
	{
		stubRun = !run;
	}
}