package org.jshint;

import org.jshint.utils.EventContext;

public interface LexerEventListener
{
	public void accept(EventContext ev) throws JSHintException;
}