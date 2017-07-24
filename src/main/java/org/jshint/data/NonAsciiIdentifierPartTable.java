package org.jshint.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class NonAsciiIdentifierPartTable
{
	private static String str = null;
	static
	{
		try
		{
			str = new String(Files.readAllBytes(Paths.get("src/main/resources/non-ascii-identifier-part-only.txt")));
		}
		catch (IOException e)
		{
			str = "";
		}
	}
	
	private static List<Integer> arr = new ArrayList<Integer>();
	static
	{
		String[] strarr = str.split(",");
		for (int i = 0; i < strarr.length; i++)
		{
			arr.add(Integer.parseInt(strarr[i], 10));
		}
	}
	
	public static int indexOf(int code)
	{
		return arr.indexOf(code);
	}
}
