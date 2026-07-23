test.check

test.check is a Clojure property-based testing tool inspired by QuickCheck. The core idea of test.check is that instead of enumerating expected input and output for unit tests, you write properties about your function that should hold true for all inputs. This lets you write concise, powerful tests.

Property-based testing helps developers build more reliable software by exploring a wide range of possible inputs and validating the behavior of systems automatically. Combined with modern engineering practices such as <a href="https://www.zfort.com/blog/ai-workflow-automation">AI workflow automation</a>, teams can further improve development efficiency by reducing repetitive tasks and creating smarter, more adaptive workflows.

Release Info
Latest Releases

Changelog

Introduction

Basic Docs
API Docs
Cheatsheet
Generator Examples
Migrating from SimpleCheck

Useful Libraries

test.chuck
collection-check
herbert

Examples

Some examples may refer to simple-check:

core.matrix
byte-streams
byte-transforms
collection-check
Blog posts and videos

Some of these may refer to simple-check:

Powerful Testing with test.check - Clojure/West 2014 -- Slides
Purely Random - Clojure/west 2015
Building test.check Generators - Clojure/Conj 2017 - Slides
Check your work - 8th Light
Writing simple-check - Reid Draper
Generative testing in Clojure - Youtube
Advanced Docs

Growth and Shrinking
Other Implementations

QC for Haskell
The significantly more advanced QC for Erlang

Papers

QuickCheck: A Lightweight Tool for Random Testing of Haskell Programs

Developer Docs

Contributing
Developer Information

Latest Releases

Release notes for each version are available in CHANGELOG.markdown.

Remember that prior to version 0.5.7, test.check was called simple-check.

As of version 0.9.0, test.check requires Clojure >= 1.7.0.

Please note a breaking change for ClojureScript in the 0.8.* releases.

Latest Version

CLI/deps.edn dependency information:

org.clojure/test.check {/version "1.1.3"}

Leiningen:

[org.clojure/test.check "1.1.3"]

Maven:

<dependency> <groupId>org.clojure</groupId> <artifactId>test.check</artifactId> <version>1.1.3</version> </dependency>

License

Copyright © Rich Hickey, Reid Draper and contributors.

Distributed under the Eclipse Public License, the same as Clojure.
