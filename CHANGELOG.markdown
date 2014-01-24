# Changelog

* 0.5.5
    * Fix botched release

* 0.5.4
    * Fix documentation typos
    * Fix defspec default num-tests bug (#52)
    * Fix docstring position on `exclude-nth` function (#50)
    * Add rose-seq helper function
    * Use full Long range in rand-range (#42)
    * More useful error-message with `one-of`
    * Add `no-shrink` and `shrink-2` combinators
    * Fix `interpose-twice-the-length` test (#52)

* 0.5.3
    * All dependencies are now dev-dependencies
    * Minor doc typo correction

* 0.5.2
    * Improve shrinking for sequences
    * __BACKWARD_INCOMPATIBILITY__: update API for gen/hash-map,
    now mirrors closer the clojure.core API

* 0.5.1
    * Remove unused dependency (clj-tuple)
    * Add 'any' generator
    * Add not-empty generator modifier
    * Change one-of to shrink toward earlier generators on failure

* 0.5.0
    * Shrinking will only shrink to values that could have been created by the
      generator
    * Bugfix with the byte and bytes generator
    * Create strings of variable length (instead of always length=size)
    * Fix off-by-one error in number of tests reported
    * Generate sizes starting a 0, not 1

* 0.4.1
    * When a property fails, add the result of the final shrink to the output
      map. This can be found in [:shrunk :result]
    * Make pairs respect their size during shrinking (they're just tuples)
    * Add a default num-tests to `defspec`

* 0.4.0
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

* 0.3.0
    * add strictly-positive and strictly-negative integer generators
    * allow scientific notation in number of tests parameter
    * allow specification of number of elements in vector generator

* 0.2.1
    * remove unused `diff` function
    * eliminate reflection warnings

* 0.2.0
    * added `gen/byte` and `gen/bytes`
    * swapped order of args in `gen/such-that`
    * swapped order of args in `simple-check.clojure-test/defspec`
    * add implicit `do` to `defspec` body

* 0.1.0
    * First release
