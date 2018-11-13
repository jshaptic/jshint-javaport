# JSHint Java Port

[![Bintray](https://img.shields.io/bintray/v/jshaptic/maven/jshint-javaport.svg?style=flat-square)](https://bintray.com/jshaptic/maven/jshint-javaport/_latestVersion)
[![Travis](https://img.shields.io/travis/jshaptic/jshint-javaport.svg?style=flat-square)](https://travis-ci.org/jshaptic/jshint-javaport)
[![Coveralls](https://img.shields.io/coveralls/jshaptic/jshint-javaport.svg?style=flat-square)](https://coveralls.io/github/jshaptic/jshint-javaport)
[![License](https://img.shields.io/github/license/jshaptic/jshint-javaport.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Just a straight port of a javascript linter JSHint. Almost everything is ported to a native Java code, except regexps validation - it is done using Java internal Nashorn engine.

## Usage

Code linting with default options:
```java
JSHint jshint = new JSHint();
jshint.lint("var a = 123");
System.out.println(jshint.generateSummary());
```

Code linting with custom options (for list of all options check JSHint [website](http://jshint.com/docs/options/)):
```java
JSHint jshint = new JSHint();
jshint.lint("var a = 123", new LinterOptions().set("esversion", 6).set("asi", true));
System.out.println(jshint.generateSummary());
```

Code linting with custom globals:
```java
JSHint jshint = new JSHint();
jshint.lint("var a = test(source)", new LinterOptions(), new LinterGlobals(true, "test", "source"));
System.out.println(jshint.generateSummary());
```

Reading linting summary:
```java
JSHint jshint = new JSHint();
jshint.lint("var a = test(source)");
DataSummary report = jshint.generateSummary();

// List of all warnings and errors
report.getErrors();

// List of all methods, objects, variables etc, which are used in the code, but defined anywhere
report.getImplieds();

// List of all unused variables
report.getUnused();
```