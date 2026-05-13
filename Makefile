clean:
	./mvnw clean

update:
	npm update
	mkdir -p ./src/main/resources/static/css/
	mkdir -p ./src/main/resources/static/js/
	cp -rf ./node_modules/hyperscript.org/dist/_hyperscript.min.js ./src/main/resources/static/js/
	cp -rf ./node_modules/htmx.org/dist/htmx.min.js ./src/main/resources/static/js/

install: clean
	npm install

build: install update
	npm run css-build
	./mvnw package

check-mvn-updates:
	./mvnw versions:display-dependency-updates
