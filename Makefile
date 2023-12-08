#! make

build:
	docker build -t vvc .
	docker run -v $$(pwd)/.m2:/root/.m2 -v $$(pwd)/target:/tmp/target --rm -it vvc mvn package

test:
	java -jar target/GexfVosViewerJson-1.0-jar-with-dependencies.jar -f example.json

.PHONY: build test
