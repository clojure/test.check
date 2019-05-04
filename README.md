# test.check

_test.check_ is a Clojure property-based testing tool inspired by QuickCheck.
The core idea of _test.check_ is that instead of enumerating expected input
and output for unit tests, you write properties about your function that should
hold true for all inputs. This lets you write concise, powerful tests.

* [Releases and Dependency Information](#releases-and-dependency-information)
* [Documentation](#documentation)
* [Developer Information](#developer-information)
* [See also](#see-also)
* [YourKit](#yourkit)
* [License](#license)

## Releases and Dependency Information

Release notes for each version are available in
[`CHANGELOG.markdown`](CHANGELOG.markdown). Remember that prior to version
0.5.7, _test.check_ was called _simple-check_.

As of version `0.9.0`, test.check requires Clojure >= `1.7.0`.

Please note a
[breaking change for ClojureScript](https://github.com/clojure/test.check/blob/master/CHANGELOG.markdown#080)
in the `0.8.*` releases.

### Latest Version

#### Leiningen

```clojure
[org.clojure/test.check "0.10.0-alpha4"]
```

#### Maven

```xml
<dependency>
  <groupId>org.clojure</groupId>
  <artifactId>test.check</artifactId>
  <version>0.10.0-alpha4</version>
</dependency>
```

### Stable Version

#### Leiningen

```clojure
[org.clojure/test.check "0.9.0"]
```

#### Maven

```xml
<dependency>
  <groupId>org.clojure</groupId>
  <artifactId>test.check</artifactId>
  <version>0.9.0</version>
</dependency>
```

If you'd like to try a SNAPSHOT version, [add the sonatype repository to your
project](http://dev.clojure.org/display/community/Maven+Settings+and+Repositories).

## Documentation

  * [API Docs](http://clojure.github.io/test.check/)
  * [Cheatsheet](https://github.com/clojure/test.check/blob/master/doc/cheatsheet.md)
  * [Generator writing guide](doc/intro.md)
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

## Useful libraries

* [test.chuck](https://github.com/gfredericks/test.chuck)
* [collection-check](https://github.com/ztellman/collection-check)
* [herbert](https://github.com/miner/herbert)

## Examples

Let's say we're testing a sort function. We want to check that that our sort
function is idempotent, that is, applying sort twice should be equivalent to
applying it once: `(= (sort a) (sort (sort a)))`. Let's write a quick test to
make sure this is the case:

```clojure
(require '[clojure.test.check :as tc])
(require '[clojure.test.check.generators :as gen])
(require '[clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])])

(def sort-idempotent-prop
  (prop/for-all [v (gen/vector gen/int)]
    (= (sort v) (sort (sort v)))))

(tc/quick-check 100 sort-idempotent-prop)
;; => {:result true,
;; =>  :pass? true,
;; =>  :num-tests 100,
;; =>  :time-elapsed-ms 28,
;; =>  :seed 1528580707376}
```

In prose, this test reads: for all vectors of integers, `v`, sorting `v` is
equal to sorting `v` twice.

What happens if our test fails? _test.check_ will try and find 'smaller'
inputs that still fail. This process is called shrinking. Let's see it in
action:

```clojure
(def prop-sorted-first-less-than-last
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
    (let [s (sort v)]
      (< (first s) (last s)))))

(tc/quick-check 100 prop-sorted-first-less-than-last)
;; => {:num-tests 5,
;; =>  :seed 1528580863556,
;; =>  :fail [[-3]],
;; =>  :failed-after-ms 1,
;; =>  :result false,
;; =>  :result-data nil,
;; =>  :failing-size 4,
;; =>  :pass? false,
;; =>  :shrunk
;; =>  {:total-nodes-visited 5,
;; =>   :depth 2,
;; =>   :pass? false,
;; =>   :result false,
;; =>   :result-data nil,
;; =>   :time-shrinking-ms 1,
;; =>   :smallest [[0]]}}
```

This test claims that the first element of a sorted vector should be less-than
the last. Of course, this isn't true: the test fails with input `[-3]`, which
gets shrunk down to `[0]`, as seen in the output above. As your test functions
require more sophisticated input, shrinking becomes critical to being able
to understand exactly why a random test failed. To see how powerful shrinking
is, let's come up with a contrived example: a function that fails if it's
passed a sequence that contains the number 42:

```clojure
(def prop-no-42
  (prop/for-all [v (gen/vector gen/int)]
    (not (some #{42} v))))

(tc/quick-check 100 prop-no-42)
;; => {:num-tests 45,
;; =>  :seed 1528580964834,
;; =>  :fail
;; =>  [[-35 -9 -31 12 -30 -40 36 36 25 -2 -31 42 8 31 17 -19 3 -15 44 -1 -8 27 16]],
;; =>  :failed-after-ms 11,
;; =>  :result false,
;; =>  :result-data nil,
;; =>  :failing-size 44,
;; =>  :pass? false,
;; =>  :shrunk
;; =>  {:total-nodes-visited 16,
;; =>   :depth 5,
;; =>   :pass? false,
;; =>   :result false,
;; =>   :result-data nil,
;; =>   :time-shrinking-ms 1,
;; =>   :smallest [[42]]}}
```

We see that the test failed on a rather large vector, as seen in the `:fail`
key. But then _test.check_ was able to shrink the input down to `[42]`, as
seen in the keys `[:shrunk :smallest]`.

To learn more, check out the [documentation](#documentation) links.

### `clojure.test` Integration

The macro `clojure.test.check.clojure-test/defspec` allows you to succinctly
write properties that run under the `clojure.test` runner, for example:

```clojure
(defspec first-element-is-min-after-sorting ;; the name of the test
  100 ;; the number of iterations for test.check to test
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
    (= (apply min v)
       (first (sort v)))))
```

### ClojureScript

ClojureScript support was added in version `0.7.0`.

Integrating with `cljs.test` is via the
`clojure.test.check.clojure-test/defspec` macro, in the same fashion
as integration with `clojure.test` on the jvm.

## Developer Information

We can not accept pull requests. Please see
[CONTRIBUTING.md](CONTRIBUTING.md) for details.

### Links

* [GitHub project](https://github.com/clojure/test.check)
* [Bug Tracker](http://dev.clojure.org/jira/browse/TCHECK)
* [Continuous Integration](http://build.clojure.org/job/test.check/)
* [Compatibility Test Matrix](http://build.clojure.org/job/test.check-test-matrix/)

### Tests

test.check runs in both jvm-clojure and clojurescript, so testing
comprehensively requires several steps:

* Run `lein test` to run the JVM tests (requires [Leiningen](https://leiningen.org))
* Run `lein cljsbuild once` to run the ClojureScript tests (also requires [node.js](https://nodejs.org))
* To run the same tests in a web browser, open (after running the above command)
  `test-runners/run_tests_dev.html` and `test-runners/run_tests_adv.html` and watch the
  javascript console for output
* Run `script/test-self-host` to run the self-hosted ClojureScript tests (also requires [node.js](https://nodejs.org))

## See also

### Other implementations

- [QC for Haskell](http://hackage.haskell.org/package/QuickCheck)
- [The significantly more advanced QC for
  Erlang](http://www.quviq.com/index.html)

### Papers

- [QuickCheck: A Lightweight Tool for Random Testing of Haskell
  Programs](http://www.eecs.northwestern.edu/~robby/courses/395-495-2009-fall/quick.pdf)

### simple-check

_test.check_ used to be called
[_simple-check_](https://github.com/reiddraper/simple-check).

See [migrating from simple-check](doc/migrating-from-simple-check.md).

## YourKit

![YourKit](http://www.yourkit.com/images/yklogo.png)

YourKit is kindly supporting test.check and other open source projects with its
full-featured Java Profiler.  YourKit, LLC is the creator of innovative and
intelligent tools for profiling Java and .NET applications. Take a look at
YourKit's leading software products:

* <a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
* <a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>

## License

Copyright © 2014 Rich Hickey, Reid Draper and contributors

Distributed under the Eclipse Public License, the same as Clojure.
