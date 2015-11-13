# Changelog

## 0.9.0

0.9.0 contains an assortment of new generators, and is the first
release that requires Clojure 1.7.0 (due to using `cljc` files to unify
the clj & cljs code).

* `gen/let`
* `gen/uuid`
* `gen/double`
* `gen/large-integer`
* `gen/set`
* `gen/sorted-set`
* `gen/vector-distinct`
* `gen/vector-distinct-by`
* `gen/list-distinct`
* `gen/list-distinct-by`
* `gen/map` now takes sizing options, the same as the preceding
  collection generators


## 0.8.2
* Bugfix for [TCHECK-77](http://dev.clojure.org/jira/browse/TCHECK-77),
  which was a regression in the precision of `gen/choose` introduced in
  0.8.1.

## 0.8.1
* Bugfix for [TCHECK-73](http://dev.clojure.org/jira/browse/TCHECK-73),
  in which `gen/int` would sometimes generate doubles.

## 0.8.0
* **Breaking ClojureScript Change**:
  The namespace names have changed:
  - `cljs.*` → `clojure.*`
  - `cljs.test.check.cljs-test` → `clojure.test.check.clojure-test`
* Randomness is now provided by a port of the
  `java.util.SplittableRandom` algorithm, instead of
  `java.util.Random` and `goog.testing.PsuedoRandom`.
* New functions in `clojure.test.check.generators`:
    * `scale`
    * `generate`

## 0.7.0
* Add ClojureScript support, written by @swannodette. More usage can be
  found [in the
  README](https://github.com/clojure/test.check#clojurescript).
* Raise an error if the incorrect arity of `defspec` is used.
* Don't export the following private functions:
    * `make-rng`
    * `not-falsey-or-exception?`
    * `make-gen`
    * `bind-helper`
    * `recursive-helper`
    * `binding-vars`
    * `binding-gens`

## 0.6.2
* Fix regression where floating point numbers weren't allowed to describe
  the number of tests in the defspec macro. Ex: (defspec foo 1e5 ...) now
  works again.
* Allow `gen/shuffle` to work on anything that can be turned into a
  sequence.
* Allow for testing to be cancelled inside the REPL with ctrl-c.
* Fix StackOverflow error that would be caused when generating vector with
  more than about 10k elements.

## 0.6.1
* Fix bug introduced in 0.6.0: The `defspec` macro could only accept map or
  numeric _literals_ as options, instead of a symbol.

## 0.6.0
* Add a `shuffle` generator, which generates permutations of a given
  sequence.
* Rename `alpha-numeric` functions to `alphanumeric`. `char-alpha-numeric`
  and `string-alpha-numeric` are now deprecated in favor of
  `char-alphanumeric` and `string-alphanumeric`. The deprecated versions
  will be removed in a future version of `test.check`.
* Update the `defspec` macro to allow an optional map argument, which
  allows for the setting of `:seed`, `:num-tests` and `:max-size`. Examples
  below:

  ```clojure
  (defspec run-with-map {:num-tests 1} (prop/for-all* [gen/int] (constantly true)))

  (defspec run-with-map {:num-tests 1
                         :seed 1}
    my-prop)
  ```
* Provide better error-messages for the misuse of generator combinators.
  Many of the functions now test that their arguments are of the
  appropriate type, and will throw a runtime error otherwise.
* Print test failures that can be copied directly. For example, print the
  empty string as `""`, instead of a blank value. This is fixed by using
  `prn` instead of `println`.

## 0.5.9
* Better sizing for recursive generators
* Add `gen/recursive-gen` function for writing recursive generators
* Add keyword and symbol generators that may include namespaces
    * `gen/keyword-ns`
    * `gen/symbol-ns`

## 0.5.8
* Limit the number of retries for gen/such-that. A two-arity version is
  provided if you need to retry more than 10 times. This should be a
  code-smell, though.
* Return random seed used on test failure
* Fix keyword generator to conform to reader specs
* Correct documentation mentions of namespaces
* Add more detailed contributing instructions
* Internal: use a record internally for generators. This is meant to help
  convey the fact that generators are opaque
* Extract rose-tree code into a separate namespace

## 0.5.7
* Rename project to clojure.test.check. See README for migrating
from _simple-check_.

## 0.5.6
* Fix `choose` bug introduced in 0.5.4, the upper-bound was not inclusive.

## 0.5.5
* Fix botched release

## 0.5.4
* Fix documentation typos
* Fix defspec default num-tests bug (#52)
* Fix docstring position on `exclude-nth` function (#50)
* Add rose-seq helper function
* Use full Long range in rand-range (#42)
* More useful error-message with `one-of`
* Add `no-shrink` and `shrink-2` combinators
* Fix `interpose-twice-the-length` test (#52)

## 0.5.3
* All dependencies are now dev-dependencies
* Minor doc typo correction

## 0.5.2
* Improve shrinking for sequences
* __BACKWARD_INCOMPATIBILITY__: update API for gen/hash-map,
now mirrors closer the clojure.core API

## 0.5.1
* Remove unused dependency (clj-tuple)
* Add 'any' generator
* Add not-empty generator modifier
* Change one-of to shrink toward earlier generators on failure

## 0.5.0
* Shrinking will only shrink to values that could have been created by the
  generator
* Bugfix with the byte and bytes generator
* Create strings of variable length (instead of always length=size)
* Fix off-by-one error in number of tests reported
* Generate sizes starting at 0, not 1

## 0.4.1
* When a property fails, add the result of the final shrink to the output
  map. This can be found in [:shrunk :result]
* Make pairs respect their size during shrinking (they're just tuples)
* Add a default num-tests to `defspec`

## 0.4.0
* tuple generator now creates tuple types (from `clj-tuple`)
    * __BACKWARD_INCOMPATIBILITY__: `gen/tuple` now takes var-args, instead
    of a vector of arguments. So change:

    ```clojure
    (gen/tuple [gen/int gen/boolean])
    ```

    to

    ```clojure
    (gen/tuple gen/int gen/boolean)
    ```

    Tuples will now retain their size when shrunk.

* add a `ratio` generator
* correctly shrink empty lists
* switch to `codox-md` for documentation

## 0.3.0
* add strictly-positive and strictly-negative integer generators
* allow scientific notation in number of tests parameter
* allow specification of number of elements in vector generator

## 0.2.1
* remove unused `diff` function
* eliminate reflection warnings

## 0.2.0
* added `gen/byte` and `gen/bytes`
* swapped order of args in `gen/such-that`
* swapped order of args in `simple-check.clojure-test/defspec`
* add implicit `do` to `defspec` body

## 0.1.0
* First release
