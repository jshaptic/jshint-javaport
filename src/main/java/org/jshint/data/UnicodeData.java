package org.jshint.data;

public class UnicodeData
{
	private UnicodeData() {}
	
	public static boolean[] identifierStartTable = new boolean[128];
	static
	{
		for (int i = 0; i < 128; i++)
		{
			identifierStartTable[i] =
					i == 36 ||				// $
					i >= 65 && i <= 90 ||	// A-Z
					i == 95 ||				// _
					i >= 97 && i <= 122;	// a-z
		}
	}
	
	public static boolean[] identifierPartTable = new boolean[128];
	static
	{
		for (int i = 0; i < 128; i++)
		{
			identifierPartTable[i] =
					identifierStartTable[i] ||	// $, _, A-Z, a-z
					i >= 48 && i <= 57;			// 0-9
		}
	}
}
