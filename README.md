# test.check

_test.check_ is a Clojure property-based testing tool inspired by QuickCheck.
The core idea of _test.check_ is that instead of enumerating expected input
and output for unit tests, you write properties about your function that should
hold true for all inputs. This lets you write concise, powerful tests.

* Release Info
  * [Latest Releases](#latest-releases)
  * [Changelog](CHANGELOG.markdown)
* [Introduction](doc/intro.md)
* Basic Docs
  * [API Docs](http://clojure.github.io/test.check/)
  * [Cheatsheet](doc/cheatsheet.md)
  * [Generator Examples](doc/generator-examples.md)
  * [Migrating from SimpleCheck](doc/migrating-from-simple-check.md)
  * Useful Libraries
    * [test.chuck](https://github.com/gfredericks/test.chuck)
    * [collection-check](https://github.com/ztellman/collection-check)
    * [herbert](https://github.com/miner/herbert)
  * Examples (some of these may refer to simple-check):
    * [core.matrix](https://github.com/mikera/core.matrix/blob/c45ee6b551a50a509e668f46a1ae52ade2c52a82/src/test/clojure/clojure/core/matrix/properties.clj)
    * [byte-streams](https://github.com/ztellman/byte-streams/blob/b5f50a20c6237ae4e45046f72367ad658090c591/test/byte_streams_simple_check.clj)
    * [byte-transforms](https://github.com/ztellman/byte-transforms/blob/c5b9613eebac722447593530531b9aa7976a0592/test/byte_transforms_simple_check.clj)
    * [collection-check](https://github.com/ztellman/collection-check)
  * Blog posts and videos (some of these may refer to simple-check):
    * [Powerful Testing with test.check - Clojure/West 2014](https://www.youtube.com/watch?v=JMhNINPo__g) -- [Slides](https://speakerdeck.com/reiddraper/powerful-testing-with-test-dot-check)
    * [Building test.check Generators - Clojure/Conj 2017](https://www.youtube.com/watch?v=F4VZPxLZUdA) - [Slides](https://gfredericks.com/speaking/2017-10-12-generators.pdf)
    * [Check your work - 8th Light](http://blog.8thlight.com/connor-mendenhall/2013/10/31/check-your-work.html)
    * [Writing simple-check - Reid Draper](http://reiddraper.com/writing-simple-check/)
    * [Generative testing in Clojure - Youtube](https://www.youtube.com/watch?v=u0TkAw8QqrQ)
* Advanced Docs
  * [Growth and Shrinking](doc/growth-and-shrinking.md)
  * Other Implementations
    * [QC for Haskell](http://hackage.haskell.org/package/QuickCheck)
    * [The significantly more advanced QC for Erlang](http://www.quviq.com/index.html)
  * Papers
    * [QuickCheck: A Lightweight Tool for Random Testing of Haskell
  Programs](http://www.eecs.northwestern.edu/~robby/courses/395-495-2009-fall/quick.pdf)
* Developer Docs
  * [Contributing](CONTRIBUTING.md)
  * [Developer Information](doc/development.md)
* [Miscellaneous](#miscellaneous)

## Latest Releases

* Release notes for each version are available in [`CHANGELOG.markdown`](CHANGELOG.markdown)
  * Remember that prior to version 0.5.7, _test.check_ was called _simple-check_
* As of version `0.9.0`, test.check requires Clojure >= `1.7.0`
* Please note a [breaking change for ClojureScript](https://github.com/clojure/test.check/blob/master/CHANGELOG.markdown#080)
  in the `0.8.*` releases.

### Latest Version

#### Leiningen

```clojure
[org.clojure/test.check "0.10.0"]
```

#### Maven

```xml
<dependency>
  <groupId>org.clojure</groupId>
  <artifactId>test.check</artifactId>
  <version>0.10.0</version>
</dependency>
```

### Stable Version

#### Leiningen

```clojure
[org.clojure/test.check "0.10.0"]
```

#### Maven

```xml
<dependency>
  <groupId>org.clojure</groupId>
  <artifactId>test.check</artifactId>
  <version>0.10.0</version>
</dependency>
```

If you'd like to try a SNAPSHOT version, [add the sonatype repository to your
project](https://clojure.org/community/downloads#_using_clojure_and_contrib_snapshot_releases).

## Miscellaneous

### YourKit

![YourKit](http://www.yourkit.com/images/yklogo.png)

YourKit is kindly supporting test.check and other open source projects with its
full-featured Java Profiler.  YourKit, LLC is the creator of innovative and
intelligent tools for profiling Java and .NET applications. Take a look at
YourKit's leading software products:

* <a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
* <a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>

### License

Copyright © 2014 Rich Hickey, Reid Draper and contributors

Distributed under the Eclipse Public License, the same as Clojure.
