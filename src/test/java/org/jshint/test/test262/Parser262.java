package org.jshint.test.test262;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.scanner.ScannerException;

/** This class will allow you to parse test262 test files into their component pieces, for further use and manipulation.<p>
 * 
 * Based on Node.JS library [test262-parser v2.0.7]
 */
public class Parser262
{
	private static final Pattern copyrightRegex = Pattern.compile("^(?:(?:\\/\\/.*\\n)*)");
	private static final String yamlStart = "/*---";
	private static final String yamlEnd = "---*/";
	
	/**
	 * Extract copyright message
	 *
	 * @param  file file object
	 * @return the copyright string extracted from contents
	 */
	private String extractCopyright(File262 file)
	{
		Matcher m = copyrightRegex.matcher(file.getContents());
		return m.find() ? m.group(0) : "";
	}
	
	/**
	 * Extract YAML frontmatter from a test262 test
	 * 
	 * @param  text text of test file
	 * @return the YAML frontmatter or empty string if none
	 */
	public String extractYAML(String text)
	{
		int start = text.indexOf(yamlStart);
		int end;
		
		if (start > -1)
		{
			end = text.indexOf(yamlEnd);
			return text.substring(start + 5, end);
		}
		
		return "";
	}
	
	/**
	 * Extract test body 
	 *
	 * @param file file object
	 * @return the test body (after all frontmatter)
	 */
	private String extractBody(File262 file)
	{
		String text = file.getContents();
		int start = text.indexOf(yamlEnd);
		
		if (start > -1)
		{
			return text.substring(start + 5);
		}
		
		return text;
	}
	
	/**
	 * Extract and parse frontmatter from a test
	 * 
	 * @param  file file object
	 * @return normalized attributes
	 */
	private File262.Attrs loadAttrs(File262 file)
	{
		String y = extractYAML(file.getContents());
		
		if (!y.isEmpty())
		{
			try
			{
				Yaml yaml = new Yaml(new SafeConstructor());
				return new File262.Attrs(yaml.load(y));
			}
			catch (ScannerException e)
			{
				throw new RuntimeException("Error loading frontmatter from file " + file.getFile() + "\n" + e.getMessage());
			}
		}
		
		return new File262.Attrs();
	}
	
	/**
	 * Normalize attributes; ensure that flags, includes exist
	 * 
	 * @param attrs raw, unnormalized attributes
	 * @return normalized attributes
	 */
	private File262.Attrs normalizeAttrs(File262.Attrs attrs)
	{
		if (attrs.getFlags() == null) attrs.setFlags(Collections.emptyList());
		if (attrs.getIncludes() == null) attrs.setIncludes(Collections.emptyList());
		return attrs;
	}
	
	/**
	 * Parse a test file: <p>
	 *  - identify and parse frontmatter <br>
	 *  - set up normalized attributes
	 *
	 * @param file file object (only name, contents expected)
	 * @return file object with attrs, async added
	 */
	public File262 parseFile(File262 file)
	{
		file.setAttrs(normalizeAttrs(loadAttrs(file)));
		
		file.setCopyright(extractCopyright(file));
		
		file.setContents(extractBody(file));
		
		return file;
	}
}