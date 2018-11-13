package org.jshint.test.helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jshint.JSHint;
import org.jshint.LinterGlobals;
import org.jshint.LinterOptions;
import org.jshint.LinterWarning;
import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;
import org.testng.Assert;

public class TestHelper extends Assert
{	
	private JSHint jshint = null;
	
	private String name = "";
	private List<LinterWarning> errors = new ArrayList<LinterWarning>();
	private List<ErrorMessage> definedErrors = new ArrayList<ErrorMessage>();
	private List<ErrorMessage> undefinedErrors = new ArrayList<ErrorMessage>();
	private List<ErrorMessage> unthrownErrors = new ArrayList<ErrorMessage>();
	private List<ErrorMessage> wrongLocations = new ArrayList<ErrorMessage>();
	private List<ErrorMessage> duplicateErrors = new ArrayList<ErrorMessage>();
	
	public TestHelper newTest()
	{
		newTest("");
		return this;
	}
	
	public TestHelper newTest(String name)
	{
		this.name = name;
		definedErrors.clear();
		undefinedErrors.clear();
		unthrownErrors.clear();
		wrongLocations.clear();
		duplicateErrors.clear();
		return this;
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
		// no needed to check for pollutions
		
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
		// no needed to check for pollutions
		
		jshint = new JSHint();
		jshint.lint(source, options, globals);
		reportErrors();
	}
		
	public TestHelper addError(int line, int character, String message)
	{
		addError(line, character, "", message);
		return this;
	}
	
	public TestHelper addError(int line, int character, String code, String message)
	{
		boolean alreadyDefined = false;
		for (ErrorMessage err : definedErrors)
		{
			if (!err.message.equals(message))
			{
				continue;
			}
			
			if (err.line != line)
			{
				continue;
			}
			
			if (err.character != character)
			{
				continue;
			}
			
			alreadyDefined = true;
			break;
		}
		
		if (alreadyDefined)
			fail("\n  An expected error with the message '" + message + "' and line number " + line + " has already been defined for this test.");
		
		definedErrors.add(new ErrorMessage(line, character, code, message));
		
		return this;
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
			return new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			fail("\n  Cannot read file: " + filename + "\n  Error: " + e.getMessage());
		}
		
		return "";
	}
	
	private void reportErrors()
	{
		errors = jshint.getErrors();
		undefinedErrors.clear();
		unthrownErrors.clear();
		wrongLocations.clear();
		duplicateErrors.clear();
		
		if (errors.size() == 0 && definedErrors.size() == 0) return;
		
		// filter all thrown errors		
		undefinedErrors = errors.stream()
			.filter(er -> !definedErrors.stream().anyMatch(def -> def.line == er.getLine() && def.character == er.getCharacter()
				&& def.message.equals(er.getReason())))
			.map(ErrorMessage::new)
			.collect(Collectors.toList());
		
		// filter all defined errors
		unthrownErrors = definedErrors.stream()
			.filter(def -> !errors.stream().anyMatch(er -> def.line == er.getLine() && def.character == er.getCharacter()
				&& def.message.equals(er.getReason())))
			.collect(Collectors.toList());
		
		// elements that only differ in location
		for (ErrorMessage er : undefinedErrors)
		{	
			List<String> locations = unthrownErrors.stream().filter(def -> def.message.equals(er.message) && (def.line != er.line || def.character != er.character))
				.map(def -> "{Line " + def.line + ", Char " + def.character + "}")
				.collect(Collectors.toList());
			
			if (locations.size() > 0)
			{
				er.definedIn = locations;
				wrongLocations.add(er);
			}
		}
		
		//JSHINT_BUG: this is not needed since duplication of errors is checked in the addError function
		duplicateErrors = errors.stream()
			.filter(er -> errors.stream().filter(other -> er.getLine() == other.getLine() && er.getCharacter() == other.getCharacter()
				&& er.getReason().equals(other.getReason())).count() > 1)
			.map(ErrorMessage::new)
			.collect(Collectors.toList());
		
		// remove undefined errors, if there is a definition with wrong line number
		undefinedErrors = undefinedErrors.stream()
			.filter(er -> !wrongLocations.stream().anyMatch(def -> def.message.equals(er.message)))
			.collect(Collectors.toList());
		unthrownErrors = unthrownErrors.stream()
			.filter(er -> !wrongLocations.stream().anyMatch(def -> def.message.equals(er.message)))
			.collect(Collectors.toList());
		
		StringBuilder errorDetails = new StringBuilder();
		
		if (unthrownErrors.size() > 0)
		{
			errorDetails.append("\n  Errors defined, but not thrown by JSHint:");
			for (ErrorMessage el : unthrownErrors)
				errorDetails.append("\n    {Line " + el.line + ", Char " + el.character + "} " + el.message);
		}
		
		if (undefinedErrors.size() > 0)
		{
			errorDetails.append("\n  Errors thrown by JSHint, but not defined in test run:");
			for (ErrorMessage el : undefinedErrors)
				errorDetails.append("\n    {Line " + el.line + ", Char " + el.character + "} " + (el.code.isEmpty() ? "" : (el.code + " ")) + el.message);
		}
		
		if (wrongLocations.size() > 0)
		{
			errorDetails.append("\n  Errors with wrong location:");
			for (ErrorMessage el : wrongLocations)
				errorDetails.append("\n    {Line " + el.line + ", Char " + el.character + "} " + el.message + " - Not in line(s) " + el.definedIn);
		}
		
		if (duplicateErrors.size() > 0)
		{
			errorDetails.append("\n  Duplicated errors:");
			for (ErrorMessage el : duplicateErrors)
				errorDetails.append("\n    {Line " + el.line + ", Char " + el.character + "} " + (el.code.isEmpty() ? "" : (el.code + " ")) + el.message);
		}
		
		if (errorDetails.length() > 0)
			fail(errorDetails.insert(0, name.isEmpty() ? "" : "\n TestRun: '" + name + "'").toString());
	}
	
	private static class ErrorMessage
	{
		int line = 0;
		int character = 0;
		String code = "";
		String message = "";
		List<String> definedIn = null;
		
		ErrorMessage(int line, int character, String code, String message)
		{
			this.line = line;
			this.message = message;
			this.character = character;
			this.code = code;
		}
		
		ErrorMessage(LinterWarning er)
		{
			this.line = er.getLine();
			this.character = er.getCharacter();
			this.code = er.getCode();
			this.message = er.getReason();
		}
	}
}