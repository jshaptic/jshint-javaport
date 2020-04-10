package org.jshint.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NonAsciiIdentifierStartTable
{
	private NonAsciiIdentifierStartTable() {};
	
	private static final List<Integer> arr;
	
	static
	{
		try (
			InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("non-ascii-identifier-start.txt");
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		)
		{
			String str = reader.readLine();
			arr = Arrays.stream(str.split(",")).map(code -> Integer.parseInt(code, 10)).collect(Collectors.toList());
		}
		catch (IOException e)
		{
			throw new RuntimeException("Cannot load resource file!");
		}
	}
	
	public static int indexOf(int code)
	{
		return arr.indexOf(code);
	}
}