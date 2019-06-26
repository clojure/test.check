# test.check cheatsheet

So far this only documents functions in the generators namespace.

## Dev utilities, misc

- `(gen/sample g)` — returns 10 smallish samples from `g`
- `(gen/sample g n)` — generates `n` samples from `g`
- `(gen/generate g)` — generates a single moderately sized value from `g`
- `(gen/generate g size)` — generates a value from `g` with the given
  `size` (normally sizes range from 0 to 200)
- `(gen/generator? g)` — checks if `g` is a generator

## Simple Generators

- `(gen/return x)` — A constant generator that always generates `x`
- `gen/boolean` — generates booleans (`true` and `false`)
- `gen/uuid` — generates uniformly random UUIDs, does not shrink
- `(gen/elements coll)` — generates elements from `coll` (which must be non-empty)
- `(gen/shuffle coll)` — generates vectors with the elements of `coll`
  in random orders
- `gen/any` — generates any clojure value
- `gen/any-printable` — generates any printable clojure value
- `gen/any-equatable` — generates any clojure value that can be equal to another
- `gen/any-printable-equatable` — generates any printable clojure value that can be equal to another
- `gen/simple-type` — like `gen/any` but does not generate collections
- `gen/simple-type-printable` — like `gen/any-printable` but does not
- `gen/simple-type-equatable` — like `gen/any-equatable` but does not generate collections
- `gen/simple-type-printable-equatable` — like `gen/any-printable-equatable` but does not
  generate collections

### Numbers

- `gen/nat` — generates small non-negative integers (useful for generating sizes of things)
- `gen/small-integer` — generates small integers, like `gen/nat` but also negative
- `gen/large-integer` — generates a large range of integers
  - variant with options: `(gen/large-integer* {:min x, :max y})`
- `gen/size-bounded-bigint` — generates bigints, up to `2^(6*size)`
- `gen/double` — generates a large range of doubles (w/ infinities & `NaN`)
  - variant with options: `(gen/double* {:min x, :max y, :infinite? true, :NaN? true})`
- `gen/ratio` — generates ratios (sometimes integers) using gen/small-integer
- `gen/big-ratio` — generates ratios (sometimes integers) using gen/size-bounded-bigint
- `gen/byte` — generates a `Byte`
- `gen/choose` — generates *uniformly distributed* integers between two (inclusive) values

### Characters & Strings & Things

- `gen/char` — generates characters
- `gen/char-ascii` — generates printable ASCII characters
- `gen/char-alphanumeric` — generates alphanumeric ASCII characters
- `gen/char-alpha` — generates alphabetic ASCII characters
- `gen/string` — generates a string
- `gen/string-ascii` — generates a string using `gen/char-ascii`
- `gen/string-alphanumeric` — generates a string using `gen/char-alphanumeric`
- `gen/keyword` — generates keywords
- `gen/keyword-ns` — generates namespaced keywords
- `gen/symbol` — generates symbols
- `gen/symbol-ns` — generates namespaced symbols

## Heterogeneous Collections

- `(gen/tuple g1 g2 ...)` — generates vectors `[x1 x2 ...]` where `x1`
  is drawn from `g1`, `x2` from `g2`, etc.
- `(gen/hash-map k1 g1, k2 g2, ...)` — generates maps `{k1 v1, k2 v2, ...}`
  where `v1` is drawn from `g1`, `v2` from `g2`, etc.


## Homogeneous Collections

- `(gen/vector g)` — generates vectors of elements from `g`
  - Variants:
    - `(gen/vector g num-elements)`
    - `(gen/vector g min-elements max-elements)`
- `(gen/list g)` — generates lists of elements from `g`
- `(gen/set g)` — generates sets of elements from `g`
  - Variants:
    - `(gen/set g {:num-elements x, :max-tries 20})`
    - `(gen/set g {:min-elements x, :max-elements y, :max-tries 20})`
- `(gen/map key-gen val-gen)` — generates a map with keys from `key-gen`
  and vals from `val-gen`
  - same opts as `gen/set`u
- `(gen/sorted-set g)` — just like `gen/set`, but generates sorted-sets
- `(gen/vector-distinct g)` — same signature as `gen/set`, but generates
  vectors of distinct elements
- `(gen/list-distinct g)` — same signature as `gen/set`, but generates
  lists of distinct elements
- `(gen/vector-distinct-by key-fn g)` — generates vectors of elements
  where `(apply distinct? (map key-fn the-vector))`
  - same opts as `gen/set`
- `(gen/list-distinct-by key-fn g)` — generates list of elements
  where `(apply distinct? (map key-fn the-list))`
  - same opts as `gen/set`
- `gen/bytes` — generates a byte array

## Combinators

- `(gen/let [x g] y)` — **macro**, like `clojure.core/let`, where
  the right-hand bindings are generators and the left-hand are
  generated values; creates a generator
  - same functionality as `gen/fmap` and `gen/bind`
- `(gen/fmap f g)` — creates a generator that generates `(f x)` for
  `x` generated from `g`
- `(gen/bind g f)` — similar to `gen/fmap`, but where `(f x)` is itself
  a generator and `(gen/bind g f)` generates values from `(f x)`
- `(gen/such-that pred g)` — returns a new generator that generates
  only elements from `g` that match `pred`
  - Variants: `(gen/such-that pred g max-tries)`
- `(gen/one-of [g1 g2 ...])` — generates elements from the given
  generators, picking generators at random
- `(gen/frequency [[2 g1] [7 g2] ...])` — generates elements from the
  given generators, using the given weights to determine the
  probability of picking any particular generator
- `(gen/not-empty g)` — given a generator that generates collections,
  returns a modified generator that never generates empty collections
- `(gen/recursive-gen container-gen scalar-gen)` — generates a tree of
  values, using `container-gen` (which is a function like `gen/list`
  which takes and returns a generator) and `scalar-gen` (a generator
  for the leaf values)

## Sizing & shrinking control

- `(gen/resize n g)` — creates a variant of `g` whose `size` parameter
  is always `n`
- `(gen/scale f g)` — creates a variant of `g` whose `size` parameter
  is `(f size)`
- `(gen/no-shrink g)` — creates a variant of `g` that does not shrink
