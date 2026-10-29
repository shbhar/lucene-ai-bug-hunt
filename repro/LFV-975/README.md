# Flexible query parser: BoostQueryNode#toQueryString prints a boost below 0.001 (or above 2^63) in E-notation, e.g. ab^0.0009 prints ab^9.0E-4, which StandardSyntaxParser cannot parse back

Bug LFV-975 · Area: queryparser / flexible parser node serialization (BoostQueryNode toQueryString/toString) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse ab^0.0009 with the flexible StandardSyntaxParser, print the node tree with toQueryString, and expect text that parses back to the same boost, since QueryNode#toQueryString is documented as 'convert to a query string understood by the query parser' and the package documentation says the string 'can be parsed back by the original text parser'. Instead BoostQueryNode prints f:ab^9.0E-4, because its getValueString falls back to Float.toString, which uses E-notation below 0.001 and above 2^63 (ab^10000000000000000000 prints ab^1.0E19). The grammar's NUMBER token has no exponent, so re-parsing the printed text throws ParseException; the same happens to a per-field boost such as setFieldsBoost(Map.of("title", 0.0005f)) in the processed tree (title:lucene^5.0E-4).

The test shows that the flexible `StandardSyntaxParser` parses `ab^0.0009`, and `BoostQueryNode#toQueryString` prints it as `f:ab^9.0E-4` (Float.toString E-notation), which the same parser cannot parse back.

## Expected vs actual

Expected text that re-parses to a `BoostQueryNode` with boost 0.0009; actual: `f:ab^9.0E-4`, whose re-parse throws `INVALID_SYNTAX_CANNOT_PARSE`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queryparser/flexible/core/nodes/TestBoostQueryNodeToQueryString.java](src/org/apache/lucene/queryparser/flexible/core/nodes/TestBoostQueryNodeToQueryString.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.flexible.core.nodes.TestBoostQueryNodeToQueryString
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
