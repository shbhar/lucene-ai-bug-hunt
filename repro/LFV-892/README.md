# Flexible query parser: SlopQueryNode and PhraseSlopQueryNode print their int slop through a float, so toQueryString changes every slop above 2^24 (~16777217 prints ~16777216, ~2147483647 prints ~2147483648, which the parser rejects)

Bug LFV-892 · Area: queryparser / flexible parser node serialization (SlopQueryNode and PhraseSlopQueryNode toQueryString/toString) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse "a b"~16777217, or the 'any distance' phrase "a b"~2147483647, with the flexible StandardSyntaxParser, print the node tree with toQueryString, and expect the same slop back, since QueryNode#toQueryString is documented as 'convert to a query string understood by the query parser' and the package documentation says the string 'can be parsed back by the original text parser'. Instead SlopQueryNode prints ~16777216, and ~2147483648 for the second query, which the same parser rejects with NumberFormatException, because getValueString boxes the int slop into a Float and prints its long value, so every slop above 2^24 is rounded to the nearest float. PhraseSlopQueryNode has the same helper (123456789 prints ~123456792), and toString of both nodes uses it too.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: SlopQueryNode prints ~16777216, and ~2147483648 for the second query, which the same parser rejects with NumberFormatException, because getValueString boxes the int slop into a Float and prints its long value, so every slop above 2^24 is rounded to the nearest float.

## Files

- [src/org/apache/lucene/queryparser/flexible/core/nodes/TestSlopNodeToQueryStringRoundTrip.java](src/org/apache/lucene/queryparser/flexible/core/nodes/TestSlopNodeToQueryStringRoundTrip.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.queryparser.flexible.core.nodes.TestSlopNodeToQueryStringRoundTrip
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
