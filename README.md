# test.check

_test.check_ is a Clojure property-based testing tool inspired by QuickCheck.
The core idea of _test.check_ is that instead of enumerating expected input
and output for unit tests, you write properties about your function that should
hold true for all inputs. This lets you write concise, powerful tests.

_test.check_ used to be called
[_simple-check_](https://github.com/reiddraper/simple-check).

## Releases and Dependency Information

### Leiningen

```clojure
[org.clojure/test.check "0.5.7"]
```

### Maven

```xml
<dependency>
  <groupId>org.clojure</groupId>
  <artifactId>test.check</artifactId>
  <version>0.5.7</version>
</dependency>
```

If you'd like to try a SNAPSHOT version, [add the sonatype repository to your
project](http://dev.clojure.org/display/community/Maven+Settings+and+Repositories).

### Version numbers

_test.check_ version numbers start where _simple-check_ left off: 0.5.7.


## Documentation

  * [Generator writing guide](doc/intro.md)
  * Examples (some of these may refer to simple-check):
    * [core.matrix](https://github.com/mikera/core.matrix/blob/c45ee6b551a50a509e668f46a1ae52ade2c52a82/src/test/clojure/clojure/core/matrix/properties.clj)
    * [byte-streams](https://github.com/ztellman/byte-streams/blob/b5f50a20c6237ae4e45046f72367ad658090c591/test/byte_streams_simple_check.clj)
    * [byte-transforms](https://github.com/ztellman/byte-transforms/blob/c5b9613eebac722447593530531b9aa7976a0592/test/byte_transforms_simple_check.clj)
    * [collection-check](https://github.com/ztellman/collection-check)
  * Blog posts and videos (some of these may refer to simple-check):
    * [Powerful Testing with test.check - Clojure/West](https://www.youtube.com/watch?v=JMhNINPo__g) -- [Slides](https://speakerdeck.com/reiddraper/powerful-testing-with-test-dot-check)
    * [Check your work - 8th Light](http://blog.8thlight.com/connor-mendenhall/2013/10/31/check-your-work.html)
    * [Writing simple-check - Reid Draper](http://reiddraper.com/writing-simple-check/)
    * [Generative testing in Clojure - Youtube](https://www.youtube.com/watch?v=u0TkAw8QqrQ)
    * [Using simple-check with Expectations - Curtis Gagliardi](http://curtis.io/posts/2013-12-28-using-simple-check-with-expectations.html)

## Migrating from simple-check

In order to migrate from _simple-check_ to _test.check_, you'll need to do two
things:

* Update project.clj

    In your `project.clj` replace `[reiddraper/simple-check "0.5.6"]` with
    `[org.clojure/test.check "0.5.7"]` (note: your version numbers may be
    different).

* Update namespace declarations

    Update your namespaces: `simple-check.core` becomes `clojure.test.check` (note
    the dropping of 'core'). Everything else you can simply replace `simple-check`
    with `clojure.test.check`. Let's make it easy:

    ```shell
    find test -name '*.clj' -print0 | xargs -0 sed -i.bak \
    -e 's/simple-check.core/clojure.test.check/' \
    -e 's/simple-check/clojure.test.check/'
    ```

    Review the updates.

## Examples

Let's say we're testing a sort function. We want want to check that that our
sort function is idempotent, that is, applying sort twice should be
equivalent to applying it once: `(= (sort a) (sort (sort a)))`. Let's write a
quick test to make sure this is the case:

```clojure
(require '[clojure.test.check :as tc])
(require '[clojure.test.check.generators :as gen])
(require '[clojure.test.check.properties :as prop])

(def sort-idempotent-prop
  (prop/for-all [v (gen/vector gen/int)]
    (= (sort v) (sort (sort v)))))

(tc/quick-check 100 sort-idempotent-prop)
;; => {:result true, :num-tests 100, :seed 1382488326530}
```

In prose, this test reads: for all vectors of integers, `v`, sorting `v` is
equal to sorting `v` twice.

What happens if our test fails? _test.check_ will try and find 'smaller'
input that still fails. This process is called shrinking. Let's see it in
action:

```clojure
(def prop-sorted-first-less-than-last
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
    (let [s (sort v)]
      (< (first s) (last s)))))

(tc/quick-check 100 prop-sorted-first-less-than-last)
;; => {:result false, :failing-size 0, :num-tests 1, :fail [[3]],
       :shrunk {:total-nodes-visited 5, :depth 2, :result false,
                :smallest [[0]]}}
```

This test claims that the first element of a sorted vector should be less-than
the last. Of course, this isn't true: the test fails with input `[3]`, which
gets shrunk down to `[0]`, as seen in the output above. As your test functions
require more sophisticated input, shrinking becomes critical to being able
to understand exactly why a random test failed. To see how powerful shrinking
is, let's come up with a contrived example: a function that fails if its
passed a sequence that contains the number 42:

```clojure
(def prop-no-42
  (prop/for-all [v (gen/vector gen/int)]
    (not (some #{42} v))))

(tc/quick-check 100 prop-no-42)
;; => {:result false,
       :failing-size 45,
       :num-tests 46,
       :fail [[10 1 28 40 11 -33 42 -42 39 -13 13 -44 -36 11 27 -42 4 21 -39]],
       :shrunk {:total-nodes-visited 38,
                :depth 18,
                :result false,
                :smallest [[42]]}}
```

We see that the test failed on a rather large vector, as seen in the `:fail`
key. But then _test.check_ was able to shrink the input down to `[42]`, as
seen in the keys `[:shrunk :smallest]`.

To learn more, check out the [documentation](#documentation) links.

### `clojure.test` Integration

There is a macro called `defspec` that allows you to succinctly write
properties that run under the `clojure.test` runner, for example:

```clojure
(defspec first-element-is-min-after-sorting ;; the name of the test
         100 ;; the number of iterations for test.check to test
         (prop/for-all [v (such-that not-empty (gen/vector gen/int))]
           (= (apply min v)
              (first (sorted v)))))
```

## Release Notes

Release notes for each version are available in
[`CHANGELOG.markdown`](CHANGELOG.markdown). Remember that prior to version
0.5.7, _test.check_ was called _simple-check_.

## See also...

### Other implementations

- [QC for Haskell](http://hackage.haskell.org/package/QuickCheck)
- [The significantly more advanced QC for
  Erlang](http://www.quviq.com/index.html)

### Papers

- [QuickCheck: A Lightweight Tool for Random Testing of Haskell
  Programs](http://www.eecs.northwestern.edu/~robby/courses/395-495-2009-fall/quick.pdf)

## Contributing

We can not accept pull requests. Please see [CONTRIBUTING.md](CONTRIBUTING.md)
for details.

## License

Copyright © 2014 Rich Hickey, Reid Draper and contributors

Distributed under the Eclipse Public License, the same as Clojure.
