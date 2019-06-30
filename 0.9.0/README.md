# simple-check

## Build Status

[![Build Status](https://secure.travis-ci.org/reiddraper/simple-check.png)](http://travis-ci.org/reiddraper/simple-check)

A Clojure property-based testing tool inspired by QuickCheck. It has a feature
called 'shrinking', which will reduce failing tests to 'smaller', easier to
comprehend counter-examples. It is mostly likely only usable by the author
at this point, but I'm getting close to a stable API.

## Installation

### Leiningen

```clojure
[reiddraper/simple-check "0.1.0-SNAPSHOT"]
```

### Maven

```xml
<dependency>
  <groupId>reiddraper</groupId>
  <artifactId>simple-check</artifactId>
  <version>0.1.0-SNAPSHOT</version>
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

* __for-all macro__: right now you need to provide a function to `for-all` that
  takes n-arguments. A macro would allow you bind names to generated values,
  and provide an expression that uses those names, rather than a function. For
  example:

  ```clojure
  (for-all [a gen/int
            b gen/int]
    (>= (+ a b) a))
  ```

## License

Copyright © 2013 Reid Draper

Distributed under the Eclipse Public License, the same as Clojure.
