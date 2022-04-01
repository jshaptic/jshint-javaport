# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.13.3-0] - 2022-04-01

### Changed

- Ported all changes from JSHint v2.13.3

## [2.10.1-4] - 2022-01-14

### Changed

- GH-3: Refactored State object to be non-static (and thus thread safe), thanks to Jarrah Watson (abonstu)
- Moved project from Travis to Github Actions
- Replaced inhouse minimatch implementation with external library minimatch-javaport
- Upgraded dependency libraries and tooling to the latest versions

## [2.10.1-3] - 2020-05-10

### Fixed

- GH-4: Avoided using default Binding for ScriptEngine, thanks to Jarrah Watson (abonstu)

## [2.10.1-2] - 2020-04-19

### Fixed

- GH-2: Used context ClassLoader to load resources, thanks to Jarrah Watson (abonstu)

## [2.10.1-1] - 2019-06-10

### Fixed

- GH-1: Fixed packaging issue with resources

## [2.10.1-0] - 2019-05-30

### Changed

- Ported all changes from JSHint v2.10.1

## [2.9.7-0] - 2018-12-17

### Changed

- Ported all changes from JSHint v2.9.7

## [2.9.6-0] - 2018-11-13

### Changed

- Ported all changes from JSHint v2.9.6

## [2.9.5-3] - 2018-08-20

### Changed

- Upgraded dependency libraries and tooling

## [2.9.5-2] - 2017-08-01

### Changed

- Modified versioning logic, updated TODOs and other markers

## [2.9.5-0] - 2017-07-31

### Changed

- Ported all changes from JSHint v2.9.5

## [2.9.3-0] - 2017-07-24

### Added

- First implementation of a Java Port
