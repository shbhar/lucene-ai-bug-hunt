# Flexible query parser: QueryParserHelper.setQueryNodeProcessor(null) throws NullPointerException, although its javadoc says null is allowed and means no query-node processing (StandardQueryParser and PrecedenceQueryParser inherit the setter; every release since 2.9.0)

Bug LFV-968 · Area: queryparser / flexible core (QueryParserHelper; inherited by StandardQueryParser and PrecedenceQueryParser) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call setQueryNodeProcessor(null) on a StandardQueryParser, a PrecedenceQueryParser or any QueryParserHelper to switch off the optional query-processing phase, as the method's javadoc allows ('The argument can be null, which means that no processor will be used to process the query node tree'). You expect the parser to go on parsing without processing, as it does when the null processor is passed to the QueryParserHelper constructor. Instead the setter always throws NullPointerException, because it stores the argument and then calls setQueryConfigHandler on it without the null check that the constructor, setQueryConfigHandler and parse all have.

The test shows that `StandardQueryParser#setQueryNodeProcessor(null)` (inherited from `QueryParserHelper`, whose javadoc says the argument can be null and means no query-node processing) throws `NullPointerException`.

## Expected vs actual

Expected the call to return and `parse("Foo", "f")` to give the unprocessed `TermQuery` `f:Foo`; actual `NullPointerException` at `QueryParserHelper.setQueryNodeProcessor` (QueryParserHelper.java:102), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestSetNullQueryNodeProcessor.java](src/org/apache/lucene/queryparser/flexible/standard/TestSetNullQueryNodeProcessor.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestSetNullQueryNodeProcessor
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
