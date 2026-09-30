clean:
	./mvnw clean

install:
	npm install

assets: install
	npm run build

build: assets
	./mvnw package

check-mvn-updates:
	./mvnw versions:display-dependency-updates

check-npm-updates:
	npm outdated

.PHONY: clean install assets build check-mvn-updates check-npm-updates
