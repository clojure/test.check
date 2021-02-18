# Development

_(i.e., working on test.check itself)_

## Links

* [GitHub project](https://github.com/clojure/test.check)
* [Bug Tracker](https://clojure.atlassian.net/browse/TCHECK)
* [Continuous Integration](https://build.clojure.org/job/test.check/)
* [Compatibility Test Matrix](https://build.clojure.org/job/test.check-test-matrix/)

## Tests

test.check runs in both jvm-clojure and clojurescript, so testing
comprehensively requires several steps:

* Run `lein test` to run the JVM tests (requires [Leiningen](https://leiningen.org))
* Run `lein cljsbuild once` to run the ClojureScript tests (also requires [node.js](https://nodejs.org))
* To run the same tests in a web browser, open (after running the above command)
  `test-runners/run_tests_dev.html` and `test-runners/run_tests_adv.html` and watch the
  javascript console for output
* Run `script/test-self-host` to run the self-hosted ClojureScript tests (also requires [node.js](https://nodejs.org))
