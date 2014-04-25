# Generator Examples

The following examples assume you have the following namespace alias:

```clojure
(require '[clojure.test.check.generators :as gen])
```

For the most part, these are in order of simplest to most complex. They also
skip over some of the built-in, basic generators.

## Integers 5 through 9, inclusive

```clojure
(def five-through-nine (gen/choose 5 9))
(gen/sample five-through-nine)
;; => (6 5 9 5 7 7 6 9 7 9)
```

## A random element from a vector

```clojure
(def languages (gen/elements ["clojure" "haskell" "erlang" "scala" "python"]))
(gen/sample languages)
;; => ("clojure" "scala" "clojure" "haskell" "clojure" "erlang" "erlang"
;; =>  "erlang" "haskell" "python")
```

## An integer or nil

```clojure
(def int-or-nil (gen/one-of [gen/int (gen/return nil)]))
(gen/sample int-or-nil)
;; => (nil 0 -2 nil nil 3 nil nil 4 2)
```

## An integer 90% of the time, nil 10%

```clojure
(def mostly-ints (gen/frequency [[9 gen/int] [1 (gen/return nil)]]))
(gen/sample mostly-ints)
;; => (0 -1 nil 0 -2 0 6 -6 8 7)
```

## Even, positive integers

```clojure
(def even-and-positive (gen/fmap #(* 2 %) gen/pos-int))
(gen/sample even-and-positive 20)
;; => (0 0 2 0 8 6 4 12 4 18 10 0 8 2 16 16 6 4 10 4)
```

## Powers of two

```clojure
;; generate exponents with gen/s-pos-int (strictly positive integers),
;; and then apply the lambda to them
(def powers-of-two (gen/fmap #(int (Math/pow 2 %)) gen/s-pos-int))
(gen/sample powers-of-two)
;; => (2 2 8 16 16 64 16 2 4 4)
```

## Sorted seq of integers

```clojure
;; apply the sort function to each generated vector
(def sorted-vec (gen/fmap sort (gen/vector gen/int)))
(gen/sample sorted-vec)
;; => (() (-1) (-2 -2) (-1 2 3) (-1 2 4) (-3 2 3 3 4) (1)
;; => (-4 0 1 3 4 6) (-5 -4 -1 0 2 8) (1))
```

## An integer and a boolean

```clojure
(def int-and-boolean (gen/tuple gen/int gen/boolean))
(gen/sample int-and-boolean)
;; => ([0 false] [0 true] [0 true] [3 true] [-3 false]
;; =>  [0 true] [4 true] [0 true] [-2 true] [-9 false])
```

## Any number but 5

```clojure
(def anything-but-five (gen/such-that #(not= % 5) gen/int))
(gen/sample anything-but-five)
;; => (0 0 -2 1 -3 1 -4 7 -1 6)
```

It's important to note that `such-that` should only be used for predicates that
are _very_ likely to match. For example, you should _not_ use `such-that` to
filter out random vectors that are not sorted, as is this is exceedingly
unlikely to happen randomly. If you want sorted vectors, just sort them using
`gen/fmap` and `sort`.

## A vector and a random element from it

```clojure
(def vector-and-elem (gen/bind (gen/not-empty (gen/vector gen/int))
                               #(gen/tuple (gen/return %) (gen/elements %))))
(gen/sample vector-and-elem)
;; =>([[-1] -1]
;; => [[0] 0]
;; => [[-1 -1] -1]
;; => [[2 0 -2] 2]
;; => [[0 1 1] 0]
;; => [[-2 -3 -1 1] -1]
;; => [[-1 2 -5] -5]
;; => [[5 -7 -3 7] 5]
;; => [[-1 2 2] 2]
;; => [[-8 7 -3 -2 -6] -3])
```

`gen/bind` and `gen/fmap` are similar: they're both binary functions that take
a generator and a function as arguments (though their argument order is
reversed). They differ in what the provided function's return value should be.
The function provided to `gen/fmap` should return a _value_. We saw that
earlier when we used `gen/fmap` to sort a vector. `sort` returns a normal
value. The function provided to `gen/bind` should return a _generator_. Notice
how above we're providing a function that returns a `gen/tuple` generator? The
decision of which to use depends on whether you want to simply transform the
_value_ of a generator (sort it, multiply it by two, etc.), or create an
entirely new generator out of it.

---

Go [back](intro.md) to the intro.
