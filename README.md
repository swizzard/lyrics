# Lyrics Corpus

Ultimately, a tokenized corpus of song lyrics. Currently, some tools to scrape
[MetroLyrics](http://www.metrolyrics.com) and store the results with
[Neo4J](http://neo4j.com/).

## Usage

Make sure you have a working internet connection and an accessible Neo4J
server. Authentication is handled using
[environ](https://github.com/weavejester/environ) (which is a pretty great
thing), so make sure you have a valid `neo4j-url` value set in your
`profiles.clj`. Then `lein run`.

## License

Copyright © 2015 sam raker

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
