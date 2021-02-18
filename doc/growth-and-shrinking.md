# Growth and Shrinking

Sizing in test.check seems simple at first glance, but there are
subtleties that are important to understand to ensure that your tests
are covering what you expect them to, and that failing cases can
shrink in an effective way.

It's useful to keep in mind that the way test.check controls the
"size" of the data it generates is entirely different from how it
shrinks failing examples, and we'll cover both of these processes
below.

## Growth

### The `size` Parameter

Internally, a generator cannot produce a value without specifying what
`size` the value should be. This is what allows test.check to start a
test run by trying very simple values first, and gradually trying
larger and larger values. The meaning of `size` depends on the
generator; some generators ignore it altogether.

You can see how `size` affects different generators by experimenting
with the `gen/generate` function, which takes an optional `size`
argument:

``` clojure
(defn sizing-sample
  [g]
  (into {}
   (for [size [0 5 25 200]]
     [size
      (repeatedly 5 #(gen/generate g size))])))

;; with gen/nat, the integer is roughly proportional to the `size`
(sizing-sample gen/nat)
;; => {0   (0 0 0 0 0),
;;     5   (4 1 3 3 5),
;;     25  (12 8 24 25 22),
;;     200 (63 143 31 199 7)}

;; with gen/large-integer, the integer can be much larger
(sizing-sample gen/large-integer)
;; => {0   (-1 0 -1 -1 -1),
;;     5   (1 6 7 4 2),
;;     25  (-1 55798 23198 -11 6124159),
;;     200 (8371567737
;;          -393642130983883
;;          -56826587109
;;          114071285698586153
;;          5723723802814291)}

;; a collection generator grows the collection size and the
;; size of its elements
(dissoc (sizing-sample (gen/vector gen/nat)) 200)
;; => {0  ([] [] [] [] []),
;;     5  ([1 0 4 3 4] [3] [] [2 0] [2 2 4]),
;;     25 ([8 10 0 16 7 7 2 19 16 10]
;;         [16 8 0 18 11 23 11 7 1 19 10 4 0 23 17 2 17 12 1 20]
;;         [15 19 7 6 4]
;;         [6 5 14 12 19 12 7 13 17 10 16 6 9 1]
;;         [19 16 5 22 2 5 10 3 6 7 22 19 21 10 4 22 23 5 9 21 19 16 2])}

;; unless we fix the size of the collection
(sizing-sample (gen/vector gen/large-integer 3))
;; => {0 ([-1 -1 -1] [-1 0 -1] [-1 0 -1] [0 0 -1] [0 0 -1]),
;;     5 ([-10 -1 -1] [4 -14 15] [2 2 -1] [-3 -7 2] [-2 0 0]),
;;     25 ([-4417 32 189]
;;         [12886 576 -2]
;;         [0 -2 -89799]
;;         [108 -250318 -1218212]
;;         [-10 -27 -5]),
;;     200 ([-8526639064861 -44 2311]
;;          [819670069072907 -4481451104804003250 -81]
;;          [-1985273 781374 -480118376]
;;          [-2038 2593 -5355]
;;          [143974988 4 209260326382094708])}

;; gen/uuid completely ignores `size`
(sizing-sample gen/uuid)
;; => {0 (#uuid "29ec3e6f-e35c-466f-b9d5-fa27e043743d"
;;        #uuid "7bb1c53d-0b12-4be0-a2c5-b7d6a406f64f"
;;        #uuid "8f07cab1-4e3d-4bd1-a699-6d653b353588"
;;        #uuid "b2e65dcb-fad1-4f1e-afe6-5645d1759a9d"
;;        #uuid "83d9ca17-cc07-4515-bf22-625e1e537943"),
;;     5 (#uuid "f1a35527-128a-4cda-b9ba-d0fec4255674"
;;        #uuid "f7a7f621-5e84-4d09-b3e3-849bebfea048"
;;        #uuid "d92aa9f9-7be6-4e02-80a7-ab89d9074b48"
;;        #uuid "c5f24f29-1472-454a-9171-34a2d74074b1"
;;        #uuid "c14ac2f6-a31a-4c0b-8e50-273feac6dbda"),
;;     25 (#uuid "008a8dcb-11b1-41cc-87b7-5b7b1b704e8e"
;;         #uuid "f89c245a-7667-4d2f-9a88-b33c92ad09ca"
;;         #uuid "dc209d21-cfbc-4e3e-a338-7a8b146084b4"
;;         #uuid "c9585173-a4f5-4f3b-9a98-7eecc4801007"
;;         #uuid "b10196fb-9cdb-4148-8d99-dea80be6d7fa"),
;;     200 (#uuid "b9a70100-4b02-404b-9de4-611ad5a2cefe"
;;          #uuid "32ca9f24-3248-476a-ab9f-d58c9aa5df9c"
;;          #uuid "9de09d09-0121-4ffe-b7a7-37cb95191bcb"
;;          #uuid "bcaeeaf8-4225-40d9-a2b4-7ba2c417a3f0"
;;          #uuid "6153d4e4-038c-4e73-be0c-2394b3078e25")}
```

### How `size` changes over a test run

`clojure.test.check/quick-check` generates the input for the first
trial using `size=0`, for the second trial it uses `size=1`, and
continues incrementing until the 200th trial with `size=199`, after
which it starts over. In general it uses `(cycle (range 200))`.

Test.check starts with small sizes so that it will catch easy bugs
quickly without needing to generate very large input and then shrink
it, and so that edge cases produced by small sizes have a good chance
of being caught. Custom generators that ignore the `size` parameter
are thwarting this feature.

Also see the warning about small test counts below.

### Controlling `size`

Custom generators can use and modify `size` in several different ways.

#### `gen/sized`

`gen/sized` is essentially a facility for "reading" the size as you
create a generator.

``` clojure
(def g
  (gen/sized
   (fn [size]
    (gen/let [x gen/large-integer]
      (format "I generated %d using size=%d!" x size)))))

(gen/sample g)
;; => ("I generated -1 using size=0!"
;;     "I generated 0 using size=1!"
;;     "I generated -1 using size=2!"
;;     "I generated 0 using size=3!"
;;     "I generated -1 using size=4!"
;;     "I generated 2 using size=5!"
;;     "I generated 0 using size=6!"
;;     "I generated -2 using size=7!"
;;     "I generated 12 using size=8!"
;;     "I generated -2 using size=9!")
```

#### `gen/resize`

`gen/resize` lets you pin a generator to a particular `size`

``` clojure
(def g
  (gen/sized
   (fn [size]
    (gen/let [x (gen/resize 100 gen/large-integer)]
      (format "I generated %d, even though size=%d!" x size)))))

(gen/sample g)
;; => ("I generated 2047953455, even though size=0!"
;;     "I generated -126726750629, even though size=1!"
;;     "I generated 50066179923, even though size=2!"
;;     "I generated 2078170872141134, even though size=3!"
;;     "I generated 678227175, even though size=4!"
;;     "I generated 3858768648, even though size=5!"
;;     "I generated -23231577, even though size=6!"
;;     "I generated 4, even though size=7!"
;;     "I generated 3503438568408, even though size=8!"
;;     "I generated 186422559275, even though size=9!")
```

#### `gen/scale`

`gen/scale` is a convenient way to modify the `size` that a generator
sees (which you could do more tediously by combining `gen/sized` and
`gen/resize`).

``` clojure
(def gen-small-vectors-of-large-numbers
  (gen/scale #(max 0 (Math/log %))
             (gen/vector (gen/scale #(* % 100) gen/large-integer))))

(gen/sample gen-small-vectors-of-large-numbers 20)
;; => ([]
;;     []
;;     []
;;     [234236101]
;;     [34663197938259]
;;     [-15]
;;     [87]
;;     []
;;     []
;;     [-5310368659078251]
;;     [-8403929563691 126041240]
;;     []
;;     []
;;     []
;;     []
;;     [-84306261785]
;;     [35060841580649472 45255404980]
;;     []
;;     [61658595345 277549824780866555]
;;     [])
```

### Gotchas

#### Integer generators

Test.check originally contained six integer generators which are
all variants of the same thing:

``` clojure
(gen/sample (gen/tuple gen/nat       gen/int
                       gen/pos-int   gen/neg-int
                       gen/s-pos-int gen/s-neg-int))

;; => ([0 0 0 0 1 -1]
;;     [1 1 0 0 1 -2]
;;     [0 2 0 -1 2 -2]
;;     [0 0 2 -2 2 -3]
;;     [3 -2 2 -2 5 -2]
;;     [3 -4 3 -2 5 -2]
;;     [0 0 1 -2 5 -4]
;;     [6 5 3 -6 2 -4]
;;     [1 -2 8 -5 4 -5]
;;     [4 -6 2 -9 8 -2])
```

Besides the confusing names, the big gotcha is that the range of these
generators is is more or less strictly bounded by `size`, and so any
use of them will by default not test numbers bigger than `200`, which
is unacceptable coverage for a lot of
applications. `gen/large-integer` should avoid this issue. Most of the
small integer generators have been deprecated, with the exception of
`gen/nat` and the new-and-less-confusingly-named `gen/small-integer`.

#### Small Test Count

Due to the use of `(cycle (range 200))` as the `size` progression
during a test run (described above), tests that use less than 200
trials will not be exposed to the normal range of sizes, and in
particular tests that run less than ~10 trials will be getting very
poor coverage.

If you don't want to run very many trials for some reason, you can
mitigate this with `gen/scale`; e.g.:

``` clojure
;; uses sizes 0,20,40,60,80,100,120,140,160,180
(tc/quick-check 10
 (prop/for-all [x (gen/scale #(* 20 %) g)]
   (f x)))
```

#### `gen/sample`

`gen/sample` starts with very small sizes in the same way that the
`quick-check` function does. This can be misleading to users who don't
expect that and take the first ten results from `gen/sample` to be
representative of the distribution of a generator. Using `gen/generate`
with an explicit `size` argument can be a better way of learning about
the distribution of a generator.

#### Collection composition

_See [TCHECK-106](https://clojure.atlassian.net/browse/TCHECK-106)_

test.check's collection generators by default select a size for the
generated collection that is proportional to the `size` parameter.

This generally works well enough, but when creating generators of
nested collections it can lead to Very Large output, in the worst
case exhausting available memory.

``` clojure
(defn max-size
  [colls]
  (->> colls (map flatten) (map count) (apply max)))

(-> gen/nat
    (gen/vector)
    (gen/vector)
    (gen/sample 200)
    (max-size))
;; => 4747

(-> gen/nat
    (gen/vector)
    (gen/vector)
    (gen/vector)
    (gen/sample 200)
    (max-size))
;; => 195635
```

This can be mitigated with strategic resizing.

## Shrinking

Despite the conceptual similarity, the shrinking algorithm has nothing
to do with the `size` parameter. `size` affects the distribution of a
random process, while shrinking is entirely deterministic and based on
the properties of the basic generators and the combinators.

### Gotchas

#### Unnecessary `bind`

_See [TCHECK-112](http://dev.clojure.org/jira/browse/TCHECK-112)_

`gen/bind` (and multi-clause uses of `gen/let`) is a powerful
combinator that allows you to combine generators in "phases", where
the later generators can make use of values generated in earlier
generators. This can be very useful, but it also is difficult to
shrink in a general way (see the details in the jira ticket linked
above for an example of this).

This means that if you care about the effectiveness of shrinking, it
can be worth taking care not to use `gen/bind` where you don't have
to.

For example, say you wanted to generate a collection of an even number
of integers. You might think to do this by first generating an even
number for the length, and then using that with `gen/vector`:

``` clojure
(def gen-an-even-number-of-integers
  (gen/let [even-number (gen/fmap #(* 2 %) gen/nat)]
    (gen/vector gen/large-integer even-number))
  ;; or, rewritten without gen/let:
  #_
  (gen/bind (gen/fmap #(* 2 %) gen/nat)
            (fn [even-number]
              (gen/vector gen/large-integer even-number))))

(def gen-strictly-increasing-integers
  (gen/bind gen/nat #(gen-strictly-increasing-integers* % 0)))

(gen/sample gen-an-even-number-of-integers)
;; => ([]
;;     [-1 -1]
;;     []
;;     []
;;     [-1 -4 0 -1 -2 2]
;;     [0 2]
;;     [6 -1 1 4 8 30 -2 0 21 -2 -1 0]
;;     [2 -2]
;;     [0 -1 7 -33 9 -49 14 14 1 1 -1 0 1 -2]
;;     [0 2 -1 -9])
```

It looks okay, but we can see a problem when shrinking

``` clojure
(tc/quick-check
 10000
 (prop/for-all [xs gen-an-even-number-of-integers]
   (not-any? #{42} xs)))

;; => {:result false,
;;     :seed 1482063539636,
;;     :failing-size 176,
;;     :num-tests 177,
;;     :fail [[-92431438766962 63530 -164135493216497125 -3270829858185774102
;;             -260351529 -59352395648111 -4 -17469 -31636041044035
;;             7336711261875630 -1636343167264 -20912505735276 -23753842660
;;             13368897139830488 -1 -250220724 24370059524 -8266208340
;;             949778971431 -2233935110 -10 -226980 -166150097914784515
;;             1446375390291034 17977873032 -306481593932634684 2 321887
;;             1535082621176844 24757631603 -15034747392805020 -248163661633
;;             -2272021814312959965 -1045247795284 177163345 13467
;;             -355036687887336641 -4098005768175 -8055 -6317647 133903089
;;             3881 42630713210694061 -2673915744452669 421802903098966
;;             -34741965 1630280301 231213827 858102836152006 5282
;;             -269037059 -4985695680423 -187884359879 -68958514179
;;             1356369075861 -1 5701573467 -9 3993 -66360585914444
;;             1796329244719094 -9139976096708138 -11216 908965 17156900
;;             5559124946 13403 -2345413999 42 1 -76248253307297 222887742816784
;;             1274360 -68929 1 -213900 -122103507959521 2767011893757957
;;             -3626024977 84758031 461767131016 -122390014709033 -1052250928741535
;;             1 383 -575550 -8793837628976134 -540423902910181208
;;             7896218 -49725987 -68869268253 -470133169 -7407245227931
;;             -2266127667584039 -60700760 7759 14242030181 -565807123157122480
;;             -21599378358624 1000368132 -1 109045164 23447410579428773
;;             1966123182 949341425 16444393 60598 340542 -187842543295
;;             3676708478 -236529145197024202 -791408585920527 -3452127625272781
;;             -132208027103 25 -17500698053396417 -3375613232 -88206409961854
;;             -3368 7 -179071081209 16894761949763 -132946664 -30990191248478947
;;             402283570687771 29732288327985 -6211 -885340544041821
;;             -2134764587 -16103 518432298883356507 -30801 -311015444053486
;;             -52408941698 -2282761018048237612 438556242]],
;;     :shrunk {:total-nodes-visited 97,
;;              :depth 72,
;;              :result false,
;;              :smallest [[0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
;;                          0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
;;                          0 0 0 0 0 0 0 0 0 0 0 0 42 0]]}}
```

Test.check wasn't able to shrink the collection to the optimal size (2
elements) because of the structure of the generators (though it did
manage to shrink from 136 to 70 elements). `gen/vector` is not able to
remove elements from the vector when shrinking because it was called
with a fixed size. So the only way to shrink the size of the vector is
to shrink the value from `(gen/fmap #(* 2 %) gen/nat)`. This is one of
the things that `gen/bind` tries, but when it shrinks the
`even-number`, it has no choice but to create an entirely new
generator from `gen-vector` and generate a fresh value that's
unrelated to the original failing collection. This fresh value is
highly unlikely to also fail, and so that part of the shrinking
algorithm is unlikely to be very fruitful (though sometimes it works,
like in the example above where we were lucky to reduce the size from
136 to 70).

Sometimes there are natural ways to write a generator that do not use
`gen/bind`. For example, in this case we could use `gen/vector`
without a fixed size, and modify it with `gen/fmap` to ensure it has
an even number of elements:

``` clojure
(def gen-an-even-number-of-integers
  (gen/let [xs (gen/vector gen/large-integer)]
    (cond-> xs (odd? (count xs)) pop))
  ;; or, rewritten without gen/let:
  #_
  (gen/fmap (fn [xs] (cond-> xs (odd? (count xs)) pop))
    (gen/vector gen/large-integer)))

(gen/sample gen-an-even-number-of-integers)
;; => ([]
;;     []
;;     []
;;     [0 0]
;;     [-6 0]
;;     [0 6]
;;     [22 -2 -2 0]
;;     []
;;     [0 -2 15 95]
;;     [0 -7 -205 -9])

;; => {:result false,
;;     :seed 1482064393801,
;;     :failing-size 160,
;;     :num-tests 161,
;;     :fail [[-20 3758 -174 2908907278 7767028 6628657334113049 -7409556399
;;             -3379156667294 -473722 760549137 -7137938397056 124401939
;;             1590227088 174 482329 4972338 -53955167617312 -237816
;;             1 -13159175 2 1911311087865 -2675112025 -391133804902
;;             -1444282617174675 1477509406066 138075 -3555024567808
;;             0 -26579022516 0 5182 -82958251 -1287 -35417824257454314
;;             -129794819488 42 1642761942897 975833887255494324 701657767868417
;;             3940940 2 458 -1337864380187855428 -6716451 23621121
;;             1 -826778832808 -2 -137892 6996928807632 -1 -506146269826334582
;;             -23783 -419873644169 3928808977969 0 -3595621317791
;;             -66706208260298 -13099314 10721686280827793 -50904466
;;             -6134528453735 24779423757 -43 1042490490 134213823314 -29]],
;;     :shrunk {:total-nodes-visited 115, :depth 68, :result false, :smallest [[42 0]]}}
```
