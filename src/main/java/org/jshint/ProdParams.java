package org.jshint;

/**
 * This module defines a set of enum-like values intended for use as bit
 * "flags" during parsing. The ECMAScript grammar defines a number of such
 * "production parameters" to control how certain forms are parsed in context.
 * JSHint implements additional parameters to facilitate detection of lint
 * warnings.
 *
 * An equivalent implementation which described the context in terms of a
 * "lookup table" object would be more idiomatic for a JavaScript project like
 * JSHint. However, because the number of contexts scales with the number of
 * expressions in the input program, this would have non-negligible impact on
 * the process's memory footprint.
 */
final class ProdParams
{
	private ProdParams() {}
	
	/**
	 * Enabled when parsing expressions within ES2015 "export" declarations,
	 * allowing otherwise-unreferenced bindings to be considered "used".
	 */
	static final int EXPORT = 1;
	
	/**
	 * Enabled when parsing expressions within the head of `for` statements,
	 * allowing to distinguish between `for-in` and "C-style" `for` statements.
	 */
	static final int NOIN = 2;
	
	/**
	 * Enabled when the expression begins the statement, allowing the parser to
	 * correctly select between the null denotation ("nud") and first null
	 * denotation ("fud") parsing strategy.
	 */
	static final int INITIAL = 4;
	
	static final int PRE_ASYNC = 8;
	
	static final int ASYNC = 16;
	
	/**
	 * Enabled when any exception thrown by the expression will be caught by a
	 * TryStatement.
	 */
	static final int TRY_CLAUSE = 32;
}