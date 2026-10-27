# XML query parser: a &lt;Clause&gt; without the occurs attribute, which LuceneCoreQuery.dtd and BooleanQueryBuilder's own code default to SHOULD, is rejected with ParserException 'Invalid value for "occurs" attribute of clause:' (regression since 5.1.0, LUCENE-6270)

Bug LFV-361 · Area: queryparser / xml (BooleanQueryBuilder) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give the XML query parser (CoreParser.parse) a &lt;BooleanQuery&gt; whose &lt;Clause&gt; has no occurs attribute and expect an optional (SHOULD) clause, because LuceneCoreQuery.dtd declares occurs with the default "should", BooleanQueryBuilder.getOccursValue has its own 'attribute absent -&gt; SHOULD' branch, and 5.0.0 and earlier parsed it that way. Instead the whole parse fails with ParserException 'Invalid value for "occurs" attribute of clause:' (nothing after the colon): org.w3c.dom.Element.getAttribute returns an empty string, never null, for an absent attribute, so the 'occs == null' default branch never runs; an explicit occurs="" is rejected the same way. Explicit should, must, mustNot and filter values work as before, and writing occurs="should" is a workaround.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The whole parse fails with ParserException 'Invalid value for "occurs" attribute of clause:' (nothing after the colon): org.w3c.dom.Element.getAttribute returns an empty string, never null, for an absent attribute, so the 'occs == null' default branch never runs; an explicit occurs="" is rejected the same way.

## Files

- [src/org/apache/lucene/queryparser/xml/TestClauseDefaultOccurs.java](src/org/apache/lucene/queryparser/xml/TestClauseDefaultOccurs.java) (JUnit test)
- [src/ClauseDefaultOccursProbe.java](src/ClauseDefaultOccursProbe.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.queryparser.xml.TestClauseDefaultOccurs

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ClauseDefaultOccursProbe
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
