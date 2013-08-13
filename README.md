# simple-check

## Build Status

[![Build Status](https://secure.travis-ci.org/reiddraper/simple-check.png)](http://travis-ci.org/reiddraper/simple-check)

_simple-check_ is a Clojure property-based testing tool inspired by QuickCheck.
The core idea of _simple-check_ (and QuickCheck) is that instead of enumerating
expected input and output for unit tests, you write properties about your
function that should hold true for all inputs. For example, for all lists L,
the count of L should equal the count of the reverse of L. Furthermore,
reversing the list twice should equal the original list.

To write _simple-check_ tests, you'll do two things: use and create generators
that generate random input for your function, and test that your function
behaves well under these input. When a property fails, by returning something
false or nil, _simple-check_ will try and find 'smaller' input for which the
test still fails. This feature is called shrinking. You can find [API
documentation here](reiddraper.github.io/simple-check), and some example usage
[below](https://github.com/reiddraper/simple-check#usage).

## Installation

### Leiningen

```clojure
[reiddraper/simple-check "0.2.1"]
```

### Maven

```xml
<dependency>
  <groupId>reiddraper</groupId>
  <artifactId>simple-check</artifactId>
  <version>0.2.1</version>
</dependency>
```

## Usage

```clojure
(require '[simple-check.core :as sc])
(require '[simple-check.generators :as gen])
(require '[simple-check.properties :as prop])

;; a passing test
(sc/quick-check 100
  (prop/for-all [(gen/vector gen/int)]
                #(= % (reverse (reverse %)))))
;; {:result true, :num-tests 100 :seed 1371257283560}

;; a failing test
(sc/quick-check 100
  (prop/for-all [gen/int gen/int]
                #(> (+ %1 %2) %)))
;; {:result false,
;;  :failing-size 4,
;;  :num-tests 3,
;;  :fail [-2 -4],
;;  :shrunk {:total-nodes-visited 6, depth 3, :smallest [0 0]}}
```

See more examples in [`core_test.clj`](test/simple_check/core_test.clj).

## TODO

* __Nested properties__ allow you to write properties that depend on values
  generated in an outer property. For example:

  ```clojure
  (for-all [(gen/vector gen/int)]
    (fn [v]
      (for-all [(gen/elements v)]
        (fn [e] (some #{e} v)))))
  ```

## License

Copyright © 2013 Reid Draper

Distributed under the Eclipse Public License, the same as Clojure.
