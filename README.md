# simple-check

## Build Status

[![Build Status](https://secure.travis-ci.org/reiddraper/simple-check.png)](http://travis-ci.org/reiddraper/simple-check)

A Clojure property-based testing tool inspired by QuickCheck. It has a feature
called 'shrinking', which will reduce failing tests to 'smaller', easier to
comprehend counter-examples. It is mostly likely only usable by the author
at this point. Will break without notice.

## Usage

```clojure
(require '[simple-check.core :as sc])

;; a passing test
(let [i (sc/gen-int 100)
      v (sc/gen-vec i 100)]
   (sc/quick-check 100 #(= % (reverse (reverse %))) v))
;; {:result true, :num-tests 100}

;; a failing test
(let [i (sc/gen-int 100)]
  (sc/quick-check 100 #(> (+ %1 %2) %) i i))
;; {:result false,
;;  :num-tests 25,
;;  :fail [11 0],
;;  :shrunk {:total-nodes-visited 9, :depth 4, :smallest [0 0]}}
```

See more examples in [core_test.clj](test/simple_check/core_test.clj).

## License

Copyright © 2013 Reid Draper

Distributed under the Eclipse Public License, the same as Clojure.
