# test.check

[simple-check](https://github.com/reiddraper/simple-check) is currently being
migrated here, renamed as _test.check_. If you'd like to try _test.check_, [add
the sonatype repository to your
project](http://dev.clojure.org/display/community/Maven+Settings+and+Repositories),
and then use the _test.check_ release:
`[org.clojure/test.check "0.5.7-SNAPSHOT"]`.

## Migrating from simple-check

In order to migrate from _simple-check_ to _test.check_, you'll need to do two
things:

* Update project.clj

    In your `project.clj` replace `[reiddraper/simple-check "0.5.6"]` with
    `[org.clojure/test.check "0.5.7]` (note: your version numbers may be
    different).

* Update namespace declarations

    Update your namespaces: `simple-check.core` becomes `clojure.test.check` (note
    the dropping of 'core'). Everything else you can simply replace `simple-check`
    with `clojure.test.check`. Let's make it easy:

    ```shell
    find test -name "*.clj" | xargs sed -i '' \
    -e 's/simple-check.core/clojure.test.check/' \
    -e 's/simple-check/clojure.test.check/'
    ```

    Be careful with the above snippet: only run it in a directory under version
    control. Further, review what it updates, as it will replace all strings with
    'simple-check'.

### Version numbers

_test.check_ version numbers start where _simple-check_ left off: 0.5.7.

## Contributing

_test.check_ [uses Jira](http://dev.clojure.org/jira/browse/TCHECK) for bugs,
enhancements and contributions. Further, pull-requests will _not_ be accepted.
Contributors must have a [signed Clojure CA](http://clojure.org/contributing).

## License

Copyright © 2014 Rich Hickey, Reid Draper and contributors

Distributed under the Eclipse Public License, the same as Clojure.
