package org.jshint;

import org.apache.commons.lang3.StringUtils;
import org.jshint.utils.JSHintModule;
import org.jshint.utils.EventContext;
import com.github.jshaptic.js4j.UniversalContainer;

public class Style implements JSHintModule
{
	@Override
	public void execute(final JSHint linter)
	{
		// Check for properties named __proto__. This special property was
		// deprecated and then re-introduced for ES6.
		linter.on("Identifier", new LexerEventListener()
		{
			@Override
			public void accept(EventContext data) throws JSHintException
			{
				if (linter.getOption("proto").test())
				{
					return;
				}
				
				if (data.getName().equals("__proto__"))
				{
					linter.warn("W103", data.getLine(), data.getCharacter(), new String[]{data.getName(), "6"});
				}
			}
		});
		
		// Check for properties named __iterator__. This is a special property
		// available only in browsers with JavaScript 1.7 implementation, but
		// it is deprecated for ES6
		
		linter.on("Identifier", new LexerEventListener()
		{
			@Override
			public void accept(EventContext data) throws JSHintException
			{
				if (linter.getOption("iterator").test())
				{
					return;
				}
				
				if (data.getName().equals("__iterator__"))
				{
					linter.warn("W103", data.getLine(), data.getCharacter(), new String[]{data.getName()});
				}
			}
		});
		
		// Check that all identifiers are using camelCase notation.
		// Exceptions: names like MY_VAR and _myVar.
		linter.on("Identifier", new LexerEventListener()
		{
			@Override
			public void accept(EventContext data) throws JSHintException
			{
				if (!linter.getOption("camelcase").test())
				{
					return;
				}
				
				// PORT INFO: replace and match regexps were moved to Reg class
				if (Reg.trimUnderscores(data.getName()).indexOf("_") > -1 && !Reg.isUppercaseIdentifier(data.getName()))
				{
					linter.warn("W106", data.getLine(), data.getCharacter(), new String[]{data.getName()});
				}
			}
		});
		
		// Enforce consistency in style of quoting.
		linter.on("String", new LexerEventListener()
		{
			@Override
			public void accept(EventContext data) throws JSHintException
			{
				UniversalContainer quotmark = linter.getOption("quotmark");
				String code = "";
				
				if (!quotmark.test())
				{
					return;
				}
				
				// If quotmark is enabled, return if this is a template literal.
				if (quotmark.equals("single") && !data.getQuote().equals("'"))
				{
					code = "W109";
				}
				
				// If quotmark is set to 'double' warn about all single-quotes.
				if (quotmark.equals("double") && !data.getQuote().equals("\""))
				{
					code = "W108";
				}
				
				// If quotmark is set to true, remember the first quotation style
				// and then warn about all others.
				if (quotmark.equals(true))
				{
					if (StringUtils.isEmpty(linter.getCache("quotmark")))
					{
						linter.setCache("quotmark", String.valueOf(data.getQuote()));
					}
					
					if (!linter.getCache("quotmark").equals(String.valueOf(data.getQuote())))
					{
						code = "W110";
					}
				}
				
				if (!code.isEmpty())
				{
					linter.warn(code, data.getLine(), data.getCharacter()	, new String[]{});
				}
			}
		});
		
		linter.on("Number", new LexerEventListener()
		{
			@Override
			public void accept(EventContext data) throws JSHintException
			{
				if (data.getValue().charAt(0) == '.')
				{
					// Warn about a leading decimal point.
					linter.warn("W008", data.getLine(), data.getCharacter(), new String[]{data.getValue()});
				}
				
				if (data.getValue().substring(data.getValue().length()-1).equals("."))
				{
					// Warn about a trailing decimal point.
					linter.warn("W047", data.getLine(), data.getCharacter(), new String[]{data.getValue()});
				}
				
				if (data.getValue().startsWith("00")) // PORT INFO: test regexp /^00+/ was replaced with startsWith method
				{
					// Multiple leading zeroes.
					linter.warn("W046", data.getLine(), data.getCharacter(), new String[]{data.getValue()});
				}
			}
		});
		
		// Warn about script URLs.
		
		linter.on("String", new LexerEventListener()
		{
			@Override
			public void accept(EventContext data) throws JSHintException
			{
				if (linter.getOption("scripturl").test())
				{
					return;
				}
				
				if (Reg.isJavascriptUrl(data.getValue())) // JSHINT_BUG: javascriptURL pattern can be used here
				{
					linter.warn("W107", data.getLine(), data.getCharacter(), new String[]{});
				}
			}
		});
	}
}