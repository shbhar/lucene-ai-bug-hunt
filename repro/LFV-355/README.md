# Flexible query parser: AbstractRangeQueryNode.toQueryString does not keep the 11.0 'can be parsed back' promise for quoted bounds that contain a space, ']' or '}' or for open bounds in processed trees, and an open point bound makes it throw IllegalArgumentException, which escapes StandardQueryParser.parse at the clause limit

Bug LFV-355 · Area: queryparser / flexible (AbstractRangeQueryNode.toQueryString, PointQueryNode.getTermEscaped, the StandardSyntaxParser range lexer, the open-bound forms of OpenRangeQueryNodeProcessor and PointRangeQueryNodeProcessor, BooleanQueryNodeBuilder) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a range such as f:["a b" TO c] or f:[a TO *] with the flexible StandardQueryParser, call toQueryString on the range node and parse the result again, as the 11.0 CHANGES entry for #7865 says you can. Instead a bound with a space, ']' or '}' prints backslash-escaped (f:[a\ b TO c]), which the range lexer rejects even for the syntax parser's own node; a processed open term bound prints as nothing (f:[a TO ]); and a processed open point bound (i:[1 TO *] with a PointsConfig) makes toQueryString and toString throw IllegalArgumentException 'Cannot format given Object as a Number'. Because BooleanQueryNodeBuilder builds its too-many-clauses message with toQueryString, a query over the clause limit that contains such an open point range makes StandardQueryParser.parse throw that IllegalArgumentException instead of the documented QueryNodeException, in every release since 4.0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: A bound with a space, ']' or '}' prints backslash-escaped (f:[a\ b TO c]), which the range lexer rejects even for the syntax parser's own node; a processed open term bound prints as nothing (f:[a TO ]); and a processed open point bound (i:[1 TO *] with a PointsConfig) makes toQueryString and toString throw IllegalArgumentException 'Cannot format given Object as a Number'.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/nodes/TestRangeNodeToQueryStringRoundTrip.java](src/org/apache/lucene/queryparser/flexible/standard/nodes/TestRangeNodeToQueryStringRoundTrip.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
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
     execute --select-class org.apache.lucene.queryparser.flexible.standard.nodes.TestRangeNodeToQueryStringRoundTrip
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
