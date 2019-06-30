.PHONY: pages docs

##
## Doc targets
##

docs:
	lein doc

pages: docs
	rm -rf /tmp/reiddraper-simple-check-docs
	mkdir -p /tmp/reiddraper-simple-check-docs
	cp -R doc/ /tmp/reiddraper-simple-check-docs
	git checkout gh-pages
	cp -R /tmp/reiddraper-simple-check-docs/* .
	git add .
	git add -u
	git commit
	git push origin gh-pages
	git checkout master
