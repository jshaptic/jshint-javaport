package org.jshint;

public enum TokenType
{
	NONE("(none)"),
	IDENTIFIER("(identifier)"),
	PUNCTUATOR("(punctuator)"),
	STRING("(string)"),
	NUMBER("(number)"),
	NEGATIVE("(negative)"),
	POSITIVE("(positive)"),
	NEGATIVE_WITH_CONTINUE("(negative-with-continue)"),
	TEMPLATE("(template)"),
	TEMPLATEMIDDLE("(template middle)"),
	TEMPLATETAIL("(template tail)"),
	NOSUBSTTEMPLATE("(no subst template)"),
	REGEXP("(regexp)"),
	ENDLINE("(endline)"),
	END("(end)"),
	
	UNDEFINED("undefined"),
	NULL("null"),
	TRUE("true"),
	FALSE("false"),
	
	ABSTRACT("abstract"),
	ARGUMENTS("arguments"),
	AWAIT("await"),
	BOOLEAN("boolean"),
	BYTE("byte"),
	CASE("case"),
	CATCH("catch"),
	CHAR("char"),
	CLASS("class"),
	DEFAULT("default"),
	DOUBLE("double"),
	ELSE("else"),
	ENUM("enum"),
	EVAL("eval"),
	EXPORT("export"),
	EXTENDS("extends"),
	FINAL("final"),
	FINALLY("finally"),
	FLOAT("float"),
	GOTO("goto"),
	IMPLEMENTS("implements"),
	IMPORT("import"),
	INFINITY("Infinity"),
	INT("int"),
	INTERFACE("interface"),
	LONG("long"),
	NATIVE("native"),
	PACKAGE("package"),
	PRIVATE("private"),
	PROTECTED("protected"),
	PUBLIC("public"),
	SHORT("short"),
	STATIC("static"),
	SUPER("super"),
	SYNCHRONIZED("synchronized"),
	THIS("this"),
	TRANSIENT("transient"),
	VOLATILE("volatile"),
	
	PLAIN("plain"),
	JSLINT("jslint"),
	JSHINT("jshint"),
	GLOBALS("globals"),
	MEMBERS("members"),
	EXPORTED("exported"),
	FALLS_THROUGH("falls through");
	
	private final String value;
	TokenType(String value)
	{
		this.value = value;
	}
	
	@Override
	public String toString()
	{
		return value;
	}
}