.PHONY: pages docs

##
## Doc targets
##

docs:
	lein doc

pages: docs
	rm -rf /tmp/org.clojure-test.check-docs
	mkdir -p /tmp/org.clojure-test.check-docs
	cp -R doc/ /tmp/org.clojure-test.check-docs
	git checkout gh-pages
	cp -R /tmp/org.clojure-test.check-docs/* .
	git add .
	git add -u
	git commit
	git push origin gh-pages
	git checkout master
