package org.jshint.data;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NonAsciiIdentifierPartTable
{
	private NonAsciiIdentifierPartTable() {}
	
	private static final List<Integer> arr;
	
	static
	{
		try
		{
			String str = new String(Files.readAllBytes(Paths.get(ClassLoader.getSystemClassLoader().getResource("non-ascii-identifier-part-only.txt").toURI())));
			arr = Arrays.stream(str.split(",")).map(code -> Integer.parseInt(code, 10)).collect(Collectors.toList());
		}
		catch (IOException | URISyntaxException e)
		{
			throw new RuntimeException("Cannot load resource file!");
		}
	}
	
	public static int indexOf(int code)
	{
		return arr.indexOf(code);
	}
}