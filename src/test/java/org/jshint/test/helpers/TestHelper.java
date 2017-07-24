package org.jshint.test.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jshint.JSHint;
import org.jshint.LinterGlobals;
import org.jshint.LinterOptions;
import org.jshint.LinterWarning;
import org.jshint.utils.JSHintUtils;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;
import org.testng.Assert;

public class TestHelper extends Assert
{
	private static class ErrorMessage
	{
		private int line = 0;
		private String message = "";
		
		private boolean extras = false;
		private int character = 0;
		private String code = "";
		
		private List<Integer> definedIn = null;
		
		private ErrorMessage(int line, String message)
		{
			this.line = line;
			this.message = message;
		}
		
		private ErrorMessage(int line, int character, String message)
		{
			this.line = line;
			this.message = message;
			this.character = character;
			this.extras = true;
		}
		
		private ErrorMessage(int line, String code, String message)
		{
			this.line = line;
			this.message = message;
			this.code = code;
			this.extras = true;
		}
		
		private ErrorMessage(int line, int character, String code, String message)
		{
			this.line = line;
			this.message = message;
			this.character = character;
			this.code = code;
			this.extras = true;
		}
		
		private ErrorMessage(String code, int line, int character, String message, List<Integer> definedIn)
		{
			this.code = code;
			this.line = line;
			this.character = character;
			this.message = message;
			this.definedIn = definedIn;
		}
	}
	
	private JSHint jshint = null;
	
	private List<ErrorMessage> definedErrors = new ArrayList<ErrorMessage>();
	private List<ErrorMessage> undefinedErrors = new ArrayList<ErrorMessage>();
	private List<ErrorMessage> unthrownErrors = new ArrayList<ErrorMessage>();
	private List<ErrorMessage> wrongLineNumbers = new ArrayList<ErrorMessage>();
	
	public void reset()
	{
		definedErrors.clear();
		undefinedErrors.clear();
		unthrownErrors.clear();
		wrongLineNumbers.clear();
	}
	
	public void test(String source)
	{
		test(source, null, null);
	}
	
	public void test(String source, LinterOptions options)
	{
		test(source, options, null);
	}
	
	public void test(String source, LinterOptions options, LinterGlobals globals)
	{	
		jshint = new JSHint();
		jshint.lint(source, options, globals);
		reportErrors();
	}
	
	public void test(String[] source)
	{
		test(source, null, null);
	}
	
	public void test(String[] source, LinterOptions options)
	{
		test(source, options, null);
	}
	
	public void test(String[] source, LinterOptions options, LinterGlobals globals)
	{	
		jshint = new JSHint();
		jshint.lint(source, options, globals);
		reportErrors();
	}
	
	public void addError(int line, String message)
	{
		definedErrors.add(new ErrorMessage(line, message));
	}
	
	public void addError(int line, int character, String message)
	{
		definedErrors.add(new ErrorMessage(line, character, message));
	}
	
	public void addError(int line, String code, String message)
	{
		definedErrors.add(new ErrorMessage(line, code, message));
	}
	
	public void addError(int line, int character, String code, String message)
	{
		definedErrors.add(new ErrorMessage(line, character, code, message));
	}
	
	public JSHint getJSHint()
	{
		return jshint;
	}
	
	public UniversalContainer getNestedFunctionsLocations()
	{
		return ContainerFactory.createArray(
			ContainerFactory.createObject()
				.set("name", "a")
				.set("param", ContainerFactory.createArray("x", "y"))
				.set("line", 2)
				.set("character", 12)
				.set("last", 4)
				.set("lastcharacter", 5),
			ContainerFactory.createObject()
				.set("name", "b")
				.set("line", 7)
				.set("character", 12)
				.set("last", 12)
				.set("lastcharacter", 2),
			ContainerFactory.createObject()
				.set("name", "c")
				.set("line", 8)
				.set("character", 16)
				.set("last", 10)
				.set("lastcharacter", 6),
			ContainerFactory.createObject()
				.set("var", ContainerFactory.createArray("e"))
				.set("name", "d")
				.set("line", 15)
				.set("character", 12)
				.set("last", 20)
				.set("lastcharacter", 2),
			ContainerFactory.createObject()
				.set("name", "e")
				.set("param", ContainerFactory.createArray("x", "y"))
				.set("line", 16)
				.set("character", 23)
				.set("last", 18)
				.set("lastcharacter", 6),
			ContainerFactory.createObject()
				.set("name", "f")
				.set("line", 23)
				.set("character", 12)
				.set("last", 27)
				.set("lastcharacter", 2),
			ContainerFactory.createObject()
				.set("name", "(empty)")
				.set("line", 24)
				.set("character", 16)
				.set("last", 26)
				.set("lastcharacter", 6),
			ContainerFactory.createObject()
				.set("name", "g")
				.set("line", 30)
				.set("character", 12)
				.set("last", 30)
				.set("lastcharacter", 58),
			ContainerFactory.createObject()
				.set("name", "h")
				.set("line", 30)
				.set("character", 39)
				.set("last", 30)
				.set("lastcharacter", 56),
			ContainerFactory.createObject()
				.set("name", "j")
				.set("line", 34)
				.set("character", 19)
				.set("last", 34)
				.set("lastcharacter", 36),
			ContainerFactory.createObject()
				.set("name", "k")
				.set("line", 35)
				.set("character", 20)
				.set("last", 35)
				.set("lastcharacter", 24),
			ContainerFactory.createObject()
				.set("name", "23")
				.set("line", 36)
				.set("character", 18)
				.set("last", 36)
				.set("lastcharacter", 22),
			ContainerFactory.createObject()
				.set("name", "computedStr")
				.set("line", 37)
				.set("character", 32)
				.set("last", 37)
				.set("lastcharacter", 36),
			ContainerFactory.createObject()
				.set("name", "(expression)")
				.set("line", 38)
				.set("character", 33)
				.set("last", 38)
				.set("lastcharacter", 37),
			ContainerFactory.createObject()
				.set("name", "get getter")
				.set("line", 39)
				.set("character", 16)
				.set("last", 39)
				.set("lastcharacter", 20),
			ContainerFactory.createObject()
				.set("name", "set setter")
				.set("line", 40)
				.set("character", 16)
				.set("last", 40)
				.set("lastcharacter", 20),
			ContainerFactory.createObject()
				.set("name", "(empty)")
				.set("line", 43)
				.set("character", 17)
				.set("last", 43)
				.set("lastcharacter", 21),
			ContainerFactory.createObject()
				.set("name", "(empty)")
				.set("line", 48)
				.set("character", 27)
				.set("last", 48)
				.set("lastcharacter", 31),
			ContainerFactory.createObject()
				.set("name", "(empty)")
				.set("line", 50)
				.set("character", 37)
				.set("last", 50)
				.set("lastcharacter", 41),
			ContainerFactory.createObject()
				.set("name", "(empty)")
				.set("line", 52)
				.set("character", 13)
				.set("last", 52)
				.set("lastcharacter", 53),
			ContainerFactory.createObject()
				.set("name", "l")
				.set("line", 52)
				.set("character", 34)
				.set("last", 52)
				.set("lastcharacter", 38),
			ContainerFactory.createObject()
				.set("name", "(expression)")
				.set("line", 52)
				.set("character", 69)
				.set("last", 52)
				.set("lastcharacter", 73),
			ContainerFactory.createObject()
				.set("name", "viaDot")
				.set("line", 54)
				.set("character", 21)
				.set("last", 54)
				.set("lastcharacter", 25),
			ContainerFactory.createObject()
				.set("name", "viaBracket")
				.set("line", 56)
				.set("character", 28)
				.set("last", 56)
				.set("lastcharacter", 32),
			ContainerFactory.createObject()
				.set("name", "(expression)")
				.set("line", 57)
				.set("character", 37)
				.set("last", 57)
				.set("lastcharacter", 41),
			ContainerFactory.createObject()
				.set("name", "VarDeclClass")
				.set("line", 65)
				.set("character", 15)
				.set("last", 65)
				.set("lastcharacter", 19),
			ContainerFactory.createObject()
				.set("name", "func")
				.set("line", 66)
				.set("character", 15)
				.set("last", 66)
				.set("lastcharacter", 19),
			ContainerFactory.createObject()
				.set("name", "method")
				.set("line", 67)
				.set("character", 10)
				.set("last", 67)
				.set("lastcharacter", 14),
			ContainerFactory.createObject()
				.set("name", "get getter")
				.set("line", 68)
				.set("character", 14)
				.set("last", 68)
				.set("lastcharacter", 18),
			ContainerFactory.createObject()
				.set("name", "set setter")
				.set("line", 69)
				.set("character", 14)
				.set("last", 69)
				.set("lastcharacter", 18),
			ContainerFactory.createObject()
				.set("name", "genMethod")
				.set("line", 70)
				.set("character", 14)
				.set("last", 70)
				.set("lastcharacter", 18),
			ContainerFactory.createObject()
				.set("name", "staticGenMethod")
				.set("line", 71)
				.set("character", 27)
				.set("last", 71)
				.set("lastcharacter", 31),
			ContainerFactory.createObject()
				.set("name", "(empty)")
				.set("line", 74)
				.set("character", 26)
				.set("last", 74)
				.set("lastcharacter", 30),
			ContainerFactory.createObject()
				.set("name", "default")
				.set("line", 76)
				.set("character", 25)
				.set("last", 76)
				.set("lastcharacter", 29)
		);
	}
	
	public String readFile(String filename)
	{
		try
		{
			return JSHintUtils.shell.cat(filename);
		}
		catch (IOException e)
		{
			assertTrue(false, "Cannot read file " + filename);
		}
		
		return "";
	}
	
	private void reportErrors()
	{
		undefinedErrors.clear();
		unthrownErrors.clear();
		wrongLineNumbers.clear();
		
		List<LinterWarning> errors = jshint.getErrors();
		
		if (errors.size() == 0 && definedErrors.size() == 0)
		{
			return;
		}
		
		// filter all thrown errors
		for (LinterWarning er : errors)
		{
			boolean result = false;
			
			for (ErrorMessage def : definedErrors)
			{
				if (def.line != er.getLine() || !def.message.equals(er.getReason()))
					continue;
				
				if (def.extras)
				{
					if (def.character != 0 && er.getCharacter() != 0)
					{
						if (def.character != er.getCharacter())
							continue;
					}
					if (!def.code.equals("") && !er.getCode().equals(""))
					{
						if (!def.code.equals(er.getCode()))
							continue;
					}
				}
				
				result = true;
				break;
			}
			
			if (!result)
			{
				undefinedErrors.add(new ErrorMessage(er.getCode(), er.getLine(), er.getCharacter(), er.getReason(), null));
			}
		}
		
		// filter all defined errors
		for (ErrorMessage def : definedErrors)
		{
			boolean result = false;
			
			for (LinterWarning er : errors)
			{
				if (def.line != er.getLine() || !def.message.equals(er.getReason()))
					continue;
				
				result = true;
				break;
			}
			
			if (!result)
			{
				unthrownErrors.add(new ErrorMessage(def.code, def.line, def.character, def.message, null));
			}
		}
		
		// elements that only differs in line number
		for (ErrorMessage er : undefinedErrors)
		{
			List<Integer> lines = new ArrayList<Integer>();
			
			for (ErrorMessage def : unthrownErrors)
			{
				if (def.line != er.line && def.message.equals(er.message))
				{
					lines.add(def.line);
				}
			}
			
			if (lines.size() > 0)
			{
				wrongLineNumbers.add(new ErrorMessage(er.code, er.line, er.character, er.message, lines));
			}
		}
		
		// remove undefined errors, if there is a definition with wrong line number
		List<ErrorMessage> newUndefinedErrors = new ArrayList<ErrorMessage>();
		for (ErrorMessage er : undefinedErrors)
		{
			boolean result = false;
			
			for (ErrorMessage def : wrongLineNumbers)
			{
				result = (def.message.equals(er.message));
				if (result) break;
			}
			
			if (!result)
			{
				newUndefinedErrors.add(er);
			}
		}	
		undefinedErrors = newUndefinedErrors;
		
		List<ErrorMessage> newUnthrownErrors = new ArrayList<ErrorMessage>();
		for (ErrorMessage er : unthrownErrors)
		{
			boolean result = false;
			
			for (ErrorMessage def : wrongLineNumbers)
			{
				result = (def.message.equals(er.message));
				if (result) break;
			}
			
			if (!result)
			{
				newUnthrownErrors.add(er);
			}
		}	
		unthrownErrors = newUnthrownErrors;
		
		if (undefinedErrors.size() == 0 && unthrownErrors.size() == 0 && wrongLineNumbers.size() == 0)
		{
			return;
		}
		else
		{
			fail(getErrorAssertionMessage());
		}
	}
	
	private String getErrorAssertionMessage()
	{
		String message = "";
		
		int idx = 0;
		for (ErrorMessage el : unthrownErrors)
		{
			if (idx == 0) message += "\nErrors defined, but not thrown by JSHINT:";
			message += "\n (X) {Line " + el.line + ", Char " + el.character + "} " + el.message;
			idx++;
		}
		idx = 0;
		for (ErrorMessage el : undefinedErrors)
		{
			if (idx == 0) message += "\nErrors thrown by JSHINT, but not defined in test run:";
			message += "\n (X) {Line " + el.line + ", Char " + el.character + "} " + (el.code.isEmpty() ? "" : (el.code + " ")) + el.message;
			idx++;
		}
		idx = 0;
		for (ErrorMessage el : wrongLineNumbers)
		{
			if (idx == 0) message += "\nErrors with wrong line number:";
			message += "\n (X) {Line " + el.line + "} " + el.message + " {not in line(s) " + el.definedIn.toString() + "}";
			idx++;
		}
		
		return message;
	}
}
