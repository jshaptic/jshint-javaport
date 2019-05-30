package org.jshint;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * The NameStack class is used to approximate function name inference as
 * introduced by ECMAScript 2015. In that edition, the `name` property of
 * function objects is set according to the function's syntactic form. For
 * certain forms, this value depends on values available to the runtime during
 * execution. For example:
 *
 *     var fnName = function() {};
 *
 * In the program code above, the function object's `name` property is set to
 * `"fnName"` during execution.
 *
 * This general "name inference" behavior extends to a number of additional
 * syntactic forms, not all of which can be implemented statically. `NameStack`
 * is a support class representing a "best-effort" attempt to implement the
 * specified behavior in cases where this may be done statically.
 *
 * For more information on this behavior, see the following blog post:
 * https://bocoup.com/blog/whats-in-a-function-name
 */
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
	 * @param token The token to consider as the source for the most recent name.
	 */
	public void set(Token token)
	{
		stack.set(length()-1, token);
	}
	
	/**
	 * Generate a string representation of the most recent name.
	 *
	 * @return string representation of the most recent name
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
		if (nameToken == null || nameToken.getType() == Token.Type.CLASS)
		{
			nameToken = (stack.size() > length()-2 && length() > 1) ? stack.get(length()-2) : null;
		}
		
		if (nameToken == null)
		{
			return "(empty)";
		}
		
		Token.Type type = nameToken.getType();
		
		if (type != Token.Type.STRING && type != Token.Type.NUMBER && type != Token.Type.IDENTIFIER && type != Token.Type.DEFAULT)
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
