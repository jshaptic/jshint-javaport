package org.jshint;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class NameStack
{
	private List<Token> stack;
	
	public NameStack()
	{
		this.stack = new ArrayList<Token>();
	}
	
	public int length()
	{
		return stack.size();
	}
	
	/**
	 * Create a new entry in the stack. Useful for tracking names across
	 * expressions.
	 */
	public void push()
	{
		stack.add(null);
	}
	
	/**
	 * Discard the most recently-created name on the stack.
	 */
	public void pop()
	{
		if (length() > 0)
			stack.remove(length()-1);
	}
	
	/**
	 * Update the most recent name on the top of the stack.
	 *
	 * @param {object} token The token to consider as the source for the most
	 *                       recent name.
	 */
	public void set(Token token)
	{
		stack.set(length()-1, token);
	}
	
	/**
	 * Generate a string representation of the most recent name.
	 *
	 * @returns {string}
	 */
	public String infer()
	{
		Token nameToken = stack.get(length()-1);
		String prefix = "";
		
		// During expected operation, the topmost entry on the stack will only
		// reflect the current function's name when the function is declared without
		// the `function` keyword (i.e. for in-line accessor methods). In other
		// cases, the `function` expression itself will introduce an empty entry on
		// the top of the stack, and this should be ignored.
		if (nameToken == null || nameToken.getType() == TokenType.CLASS)
		{
			nameToken = (stack.size() > length()-2 && length() > 1) ? stack.get(length()-2) : null;
		}
		
		if (nameToken == null)
		{
			return "(empty)";
		}
		
		TokenType type = nameToken.getType();
		
		if (type != TokenType.STRING && type != TokenType.NUMBER && type != TokenType.IDENTIFIER && type != TokenType.DEFAULT)
		{
			return "(expression)";
		}
		
		if (StringUtils.isNotEmpty(nameToken.getAccessorType()))
		{
			prefix = nameToken.getAccessorType() + " ";
		}
		
		return prefix + nameToken.getValue();
	}
}
