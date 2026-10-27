# Flexible query parser: QueryNode#toQueryString prints field names without escaping, so nodes for escaped field names such as 'my field' or 'a:b' print strings that parse to a different query or do not parse

Bug LFV-356 · Area: queryparser / flexible parser node serialization (QueryNode#toQueryString of FieldQueryNode, QuotedFieldQueryNode, FuzzyQueryNode, AbstractRangeQueryNode and the other field-printing nodes) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The standard flexible-parser grammar accepts escaped field names on purpose ('my\ field:x' is a query on the field 'my field', 'a\:b:x' on 'a:b'), and QueryNode#toQueryString is documented to return 'a query string understood by the query parser' that 'can be parsed back by the original text parser'. Every toQueryString that prints a field appends the name raw (only the term goes through the escaper), so the node of 'my\ field:x' prints 'my field:x', which parses as df:my OR field:x, and the node of 'a\:b:x' prints 'a:b:x', which is a syntax error. The same happens for phrase and fuzzy nodes, for range nodes on main (after the #7865 range fix), and for hand-built nodes such as new FieldQueryNode("my field", "x", 0, 0).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestToQueryStringFieldNames.java](src/org/apache/lucene/queryparser/flexible/standard/TestToQueryStringFieldNames.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestToQueryStringFieldNames
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/queryparser-flexible-simple-xml/](../../models/queryparser-flexible-simple-xml/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
