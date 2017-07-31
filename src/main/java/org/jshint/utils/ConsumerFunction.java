package org.jshint.utils;

import org.jshint.JSHintException;

public interface ConsumerFunction
{
	public void accept() throws JSHintException;
}