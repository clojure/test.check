# simple-check

## Build Status

[![Build Status](https://secure.travis-ci.org/reiddraper/simple-check.png)](http://travis-ci.org/reiddraper/simple-check)

A Clojure property-based testing tool inspired by QuickCheck. It has a feature
called 'shrinking', which will reduce failing tests to 'smaller', easier to
comprehend counter-examples. It is mostly likely only usable by the author
at this point. Will break without notice.

## Installation

### Leiningen

```
[reiddraper/simple-check "0.1.0-SNAPSHOT"]
```

### Maven

```
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

;; a passing test
(let [v (gen/vector gen/int)]
   (sc/quick-check 100 #(= % (reverse (reverse %))) [v]))
;; {:result true, :num-tests 100 :seed 1371257283560}

;; a failing test
(sc/quick-check 100 #(> (+ %1 %2) %) [gen/int gen/int])
;; {:result false,
;;  :failing-size 4,
;;  :num-tests 3,
;;  :fail [-2 -4],
;;  :shrunk {:total-nodes-visited 6, depth 3, :smallest [0 0]}}
```

See more examples in [`core_test.clj`](test/simple_check/core_test.clj).

## License

Copyright © 2013 Reid Draper

Distributed under the Eclipse Public License, the same as Clojure.
