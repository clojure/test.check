# Old Confluence Notes

These notes were hastily exported from dev.clojure.org on 2019-04-24.

Formatting was mostly preserved, except that bulleted lists ended up
as preformatted text.

## Main Page

Some old and largely outdated design ideas for test.check.


### API Overhaul Ideas

Responding to TCHECK-99 got me thinking about trying to pull off a
complete redesign of the (mostly generators) API, while maintaining as
much backwards compatibility as possible, at least for a few versions.

I'm going to try to list here the sorts of problems that could be
fixed with such an overhaul that would be hard to fix otherwise.

    Generators
        Convert all generators to be functions; if a default generator is available, it would be obtained by calling the function with 0 args
            Removes confusion such as with the current pair of large-integer and large-integer*
        Get rid of nat, pos-int, s-pos-int, and similar. Replace with a generator called small-integer that takes various options
        rename large-integer to integer, consider whether to allow bigints
        change gen/vector and gen/list APIs to match gen/set
        Maybe allow passing options when calling a generator, so that generators like strings and doubles can be customizable in a different way
            e.g., this way a generator like gen/any could support being customized to never generate NaNs anywhere without having to explicitly code for that
    clojure-test
        change defspec to use clojure.test/is
            can maybe do this w/o breaking changes by putting an alternative to prop/for-all in the clojure-test namespace???
    Properties

### Numerics

The situation here used to be much worse, when all of the integer
generators would only generate numbers up to ~200 by default, and no
double generator existed at all. Now we have gen/large-integer which
does well for large ranges of integers, and gen/double which is also
pretty good. The remaining holes are relatively minor:

    numbers from infinite sets
        a generator for bigints; unclear if this would require bounds or not
        a good generator for ratios; again unclear about bounds, both on abs value and on denominator size
        bigdecimals
    some lower-level utility generators
        a generator for doubles between 0 and 1
        a raw generator of evenly-distributed longs

#### What is a solid approach to generating infinite-domain types?

In particular bigints, ratios, bigdecimals, and maybe even collections.

One idea I had was a generator that always has a non-zero probability of generating any given value, and the size parameter determines the average size of the thing generated (e.g., in bits). I think this means that the size of the output, at least to start, would have a geometric distribution.

#### How should NaNs be handled?

##### Background

gen/double and gen/double* generate NaN, at least by default. This is intentional, since NaN is a useful edge case to test for any code that deals with doubles. However NaN != NaN, which means that any data structure containing a NaN is not equal to itself, which screws up all sorts of things.

This isn't such a problem for gen/double, since users can opt-out of NaNs with gen/double* when appropriate. However, gen/simple-type, gen/simple-type-printable, gen/any, and gen/any-printable are all affected by this in 0.9.0.

##### Possible Approaches

    Modify generators in-place
        Just the four composite generators (gen/simple-type{-printable} and gen/any{-printable})
        gen/double doesn't generate NaN, and gen/double* allows it as opt-in
    Add new generators
        Analogues of the four composite, or some subset (gen/simple-type-no-NaN, gen/any-no-NaN, etc.)
    Something else?
        arguably users can manage this on their own, although excluding NaN from gen/any is not so simple, since a naïve wrapping in gen/such-that won't work

### Test Runner Topics
#### Test Failure Feedback

Currently a test error (i.e., exception caught) gives ample feedback
because the user gets the exception object to inspect, but a mere
failure (falsy return value) doesn't communicate anything other than
whether the result was false or nil. clojure.test goes farther for
this, with a probably-overly-macrocentric approach.

Probably the best approach is to evaluate the result of a property
expression with a protocol, so the current behavior can be maintained
but new implementations can provide more information.

I think reid has already started in this direction on the "feature/result-map" branch

Related: https://github.com/yeller/matcha and this alternate clojure.test integration.

#### Parallel Tests

Requires the immutable RNG for full determinism.

Do we actually need complex coordination of threads or can we do a naive thing?

#### Rerunning a single test easily

#### Running tests for a specific amount of time

Related: test.chuck/times, and a branch that Tom Crayford worked on

### Some ideas from Hypothesis

From this (http://www.drmaciver.com/2015/01/using-multi-armed-bandits-to-satisfy-property-based-tests/) blog post primarily. The library itself is here.

It essentially uses a rather more sophisticated approach to getting more interesting distributions.

Idea for trying this out: have each generator keep track of how many
size parameters it can accept. E.g., gen/int takes one size
parameter. (gen/list gen/int) takes two (one for the size of the list,
one for the size given to gen/int). (gen/tuple g1 g2 g3) takes the sum
of the size-parameter-counts for its args. This breaks down when using
bind and recursive generators, so we might have to fall back on just
passing an infinite number of size parameters, or using the RNG to
determine them, or something like that.

On the other hand, I spoke to David directly about this feature and he
says he regrets including it in hypothesis due to the high complexity
and low value. I didn't ask why he thought it was low value.

### Orchestrating Concurrent (and distributed?) Programs

The point being to test them deterministically, and to test different
possible linearizations to look for concurrency bugs.

### Testing Generators, Regression Tests

Several related topics:

    How do we validate that a generator is likely to generate certain kinds of things?
    How could we collect stats about the sorts of things generated?
    When we find a rare failing test, is there an easy way to ensure that such cases are generated more often?

Need to research other QC impls.

### Generator Syntax Sugar

We ended up adding gen/let for this, but test.chuck/for is still fancier and especially useful for tuples.

### Shrinking Topics

Check other QC impls for all of this.

#### Can we use more specialized generators to minimize the need for bind?

There was an example somewhere of trying to generate a 2d vector,
where doing it without bind seemed impossible but bind caused it to
shrink poorly.

Also see "Stateful Generators" elsewhere on this page.

#### Should shrink-resumption be part of the API?


#### Custom shrinking algorithms?


### Advanced Generators
#### "Stateful" Generators

There are common use cases for being able to generate sequences of
events, where each event can affect what future events are
possible. Perhaps the most natural is modeling a collection of records
with create/update/delete events, where you can only have an update or
delete for a record that already exists (and no updates on a deleted
record).

This can be done pretty easily with a recursive generator that uses
gen/bind at each step, but this comes at the huge expense of almost
entirely thwarting the shrinking process. It would be great to devise
some general way of constructing these sorts of generators that also
shrinks well. This seems easier for special cases like the record-set
case described above, but difficult in the general case.

##### Single Set of Independent Records

A specialized generator that takes as input args similar to
gen/hash-map (i.e., a description of what keys a record has and
generators for each of the keys) and shrinks reasonably well should be
possible to write, especially if there are no constraints beyond the
ordering of the create/update/delete for individual records.

There would be custom low-level generator code that avoids using
gen/bind, paired with custom shrinking code. The shrinking part might
only have to ensure that if a shrink removes a "create" event that it
deletes all subsequent events for that record. There might also have
to be a reified "id" that doesn't shrink or else shrinks all
references to an id in unison.

##### Full Relational Dataset

This would be like the previous example, but would involve multiple
datasets ("tables") and relationships between them ("foreign
keys"). The tricky part would be intelligent handling of foreign
keys. If a record's relative disappears, the shrinking code would
manually do cascading deletes when necessary (or perhaps divert
foreign keys to other records when that makes sense).

##### General Problem


The only natural framing of the general problem that I can think of is a generator such as:

    (defn gen-events
      "Given an init-state and a reduce function for determining the
      current state from a sequence of events, together with a function
      that takes a state and returns a generator of a new event, returns
      a generator of sequences of events."
      [reduce-func init-state state->event-gen]
      ...)

It seems difficult to find a general way to layer smart shrinking on
top of this sort of API, since any change to earlier events could
potentially require arbitrary changes to future events, and I'm not
sure how to build in a way of allowing the user to specify those
changes.

One idea is to take an additional argument, a function that receives
an event that was previously generated and a new state that is the
precursor to that event. The function can choose to leave the event
as-is (if it's still valid in the new state), apply return a function
suitable for use with fmap (if the event can be easily adjusted to the
new state), or signal that the event should be removed altogether. I
think this should be sufficient for implementing the two dataset
examples above, but I'm not sure whether it works for more complex
uses.

### Breaking Changes Fantasy

If we decided to make a large breaking-changes release, what would we include?

    Rename the pos-int/s-pos-int style functions
    Change the arg order for bind? It's awkward that it's opposite fmap
    Change the multi-clause behavior of prop/for-all?
        Currently can't integrate test.chuck/for w/ prop/for-all support without breaking this


### Old/Obsolete Notes

Things that are done or decided against.

#### Immutable RNG

I think we need to do some more performance checks. Ideas for things to look into:

    How does linear generation with java.util.SplittableRandom compare to JUR and IJUSR? Knowing this should give us an idea if the slowdown with IJUSR is more about the algorithm or immutability
        Seems to be entirely about immutability. E.g., JUSR is actually slightly faster with JUR, even after removing concurrency from JUR.
    Can we squeeze any extra performance by using a more specialized API anywhere?
        Like split-n
            This is looking like the most promising
    Can we generate batches of numbers faster than the more general splitting method? If so, can we take advantage of that cleanly in the generators?
        E.g., you could linearly generate 256 longs, put them in an immutable array, and have the RNG reference a range of that array. rand-long returns the first number in the range, splitting returns two objects with the range cut in half. Splitting a singleton range triggers another array creation. I think there are still some subtleties to work out though.
        It's also worth noting that the splittable API involves twice as many allocation as a linear-immutable API (see split-n idea above)
    Is the macro usage subverting inlining?
    Is the long->double->int process taking a while?
    Try using an unrolled pair (ztellman style) for the return value from split

Some code for messing with this stuff is on this branch.

## Generators Reboot

This is a list of problems that can be addressed to some extent by
creating a new generators-2 namespace.

For each problem, there is a proposed solution with and without adding
a new namespace.

Under every "WITHOUT Reboot" section, there is an implicit alternative
to do nothing.

    Many generators have confusing names
        E.g., monadic names (return, fmap, bind), and others (elements, hash-map, one-of)
        WITH Reboot
            Pick better names
        WITHOUT Reboot
            Add aliases and mark the old names as deprecated
    Difference between a var referring to a generator vs a function that returns a generator can be error-prone for users, and leads to a duplication of names like gen/double and gen/double* to allow for options to be passed
        WITH Reboot
            Make every var a function that returns a generator
        WITHOUT Reboot
            Add an IGenerator protocol so that vars like gen/double can be backwards-compatibly changed to functions that can act as generators in a deprecated manner (with optional warnings?)
    Options API is inconsistent
        Some functions take an options map, others have similar options as positional args
        WITH Reboot
            every generator should take an options map, as an optional argument after all of the required args
        WITHOUT Reboot
            Modify signatures with backwards compatible support for the deprecated signatures
    Built-in composite generators like gen/any do not allow customizing the underlying base generators – most notably, you cannot ask gen/any to avoid generating NaNs (but with more options on other base generators this would be a more general problem)
        WITH Reboot
            These generators would be functions and could therefore pass options down to their constituent generators
        WITHOUT Reboot
            Do the "WITHOUT Reboot" steps in the previous two points, and then do the same thing we would do in a reboot
    Integer generators need overhauling
        There are a lot and some are misleadingly named. They could all be collapsed into a single generator with rich options for specifying the distribution
        WITH Reboot
            Make a single gen2/integer
        WITHOUT Reboot
            Make gen2/integer and deprecate all the others
    gen/bind has confusing argument order
        Every other combinator takes the generator as its last argument
        WITHOUT Reboot
            Accept args in either order? This could be ambiguous if we also convert everything to functions but allow the functions to be used as generators
        WITH Reboot
            Change the arg order in the new function
    String generators are pretty basic
        the non-ascii generators use a fixed small range of non-ASCII unicode characters
        WITHOUT Reboot
            Add a new string generator that takes options for distribution, deprecate all the old ones
        WITH Reboot
            Add a new string generator that takes options
    Collection sizing is bad
        Collection generations naïvely result in the expected size of the generated value being N times larger than the expected size from the element generator
        A better situation would perhaps involve
            Collection generators that reduce the `size` used for their elements according to some rule, perhaps based on the expected size of the collection
            Some sort of in-place accounting for how much stuff is being generated as it goes, so that the size can be suppressed further if the value is getting too big, mitigating the risk of the largest 0.001% of values being OOMly large
            Standard options for users to have more control of the above behavior
        WITHOUT Reboot
            Change built-in generators in place to effect the above solution (should this be considered a breaking change, if old calls still work but perhaps generate different distributions?)
            Create new collection generators according to the above solution and deprecate the old ones
        WITH Reboot
            New collection generators according to the above solution

## quickcheck refactoring

This is a hopefully temporary page, tracking evaluation of [Nico's
proposed refactoring](https://clojure.atlassian.net/browse/TCHECK-126)
and any alternatives and related issues.

Organized as (nested) list of questions.

    What problem are we trying to solve?
        A general change to allow an implementation of a variety of useful features (in test.check directly or by users/libraries):
            async tests
            statistics
            time-bounded runs, time-bounded shrinks
            more speculatively, pausing and resuming shrinks
        Note that we shouldn't assume we have to solve all these problems with the same changes, but if a change simultaneously enables all of them, that could be considered evidence in its favor
    How do other quickchecks do any of those things?
        At least
            Hedgehog
            Proper
        Optionally
            Quickcheck
            Hypothesis
    Nico's solution
        My general concern is that it changes the public API, so we have to get it right, and it seems to allow a lot of undefined/strange things, since the user's supplied function could return whatever it wants; e.g., it could return a structure that's inconsistent or sends the algorithm to an unexpected state
        I'm also wondering how it composes; e.g., if the stats library provides one implementation of the state transition function, and the user provides another for limiting shrink time, how do you combine those functions together? does `comp` work? is it commutative?
