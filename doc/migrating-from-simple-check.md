# Migrating from simple-check

_test.check_ used to be called
[_simple-check_](https://github.com/reiddraper/simple-check).

In order to migrate from _simple-check_ to _test.check_, you'll need to do two
things:

* Update project.clj

    In your `project.clj` replace `[reiddraper/simple-check "0.5.6"]` with
    `[org.clojure/test.check "0.6.2"]` (note: your version numbers may be
    different).

* Update namespace declarations

    Update your namespaces: `simple-check.core` becomes `clojure.test.check` (note
    the dropping of 'core'). For everything else you can simply replace `simple-check`
    with `clojure.test.check`. Let's make it easy:

    ```shell
    find test -name '*.clj' -print0 | xargs -0 sed -i.bak \
    -e 's/simple-check.core/clojure.test.check/' \
    -e 's/simple-check/clojure.test.check/'
    ```

    Review the updates.
