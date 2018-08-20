# JSHint Java Port

[![Bintray](https://img.shields.io/bintray/v/jshaptic/maven/jshint-javaport.svg?style=flat-square)](https://bintray.com/jshaptic/maven/jshint-javaport/_latestVersion)
[![Travis](https://img.shields.io/travis/jshaptic/jshint-javaport.svg?style=flat-square)](https://travis-ci.org/jshaptic/jshint-javaport)
[![Coveralls](https://img.shields.io/coveralls/jshaptic/jshint-javaport.svg?style=flat-square)](https://coveralls.io/github/jshaptic/jshint-javaport)
[![License](https://img.shields.io/github/license/jshaptic/jshint-javaport.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Just a straight port of a javascript linter JSHint. Almost everything was ported to a native Java code, except regexp validation - it was done using Rhino engine.

## Usage

Code linting with default options:
```java
JSHint jshint = new JSHint();
jshint.lint("var a = 123");
```

Code linting with custom options (for list of all options check JSHint [website](http://jshint.com/docs/options/)):
```java
JSHint jshint = new JSHint();
jshint.lint("var a = 123", new LinterOptions().set("esversion", 6).set("strict", false));
```

Code linting with custom globals:
```java
JSHint jshint = new JSHint();
jshint.lint("var a = 123", new LinterOptions(), new LinterGlobals(true, "JSON", "_"));
```