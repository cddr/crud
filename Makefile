.PHONY: pages docs

##
## Doc targets
##

docs:
	lein marg

pages: docs
	cd docs && git checkout gh-pages
	cd docs && git add .
	cd docs && git commit -am "new documentation push."
	cd docs && git push -u origin gh-pages
