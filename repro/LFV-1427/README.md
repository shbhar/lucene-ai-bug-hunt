# TermGroupFacetCollectorManager ends the facetPrefix range at facetPrefix + UnicodeUtil.BIG_TERM (ten 0xFF bytes), so grouped faceting leaves out binary facet values that have ten or more 0xFF bytes right after the prefix, and a single-valued facet value equal to that end key drops every facet value of the segment; since 4.0.0

Finding LFV-1427 · Area: grouping / grouped faceting (TermGroupFacetCollectorManager: facetPrefix range end = facetPrefix + UnicodeUtil.BIG_TERM) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1427.html](../../bugs/LFV-1427.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents whose SortedDocValuesField facet values are "pa" and "p" followed by ten 0xFF bytes, and count grouped facets with TermGroupFacetCollectorManager and facetPrefix "p". You expect both values, since both start with "p" and the javadoc says 'only include facet entries with this prefix'. Instead the result is empty (0 entries, total count 0; with -ea an AssertionError): the collector ends the prefix range at "p" + UnicodeUtil.BIG_TERM, a sentinel that is only guaranteed to be bigger than UTF-8 terms, and because one facet value equals that end key the end ordinal turns negative and every document of the segment is skipped.

## Expected vs actual

- Expected: 2 facet entries, `pa` and `p` + ten 0xFF bytes, count 1 each.
- Actual: 0 entries (`expected:<2> but was:<0>`); with assertions on, an `AssertionError` at `TermGroupFacetCollectorManager.java:210`.

## Files

- [src/org/apache/lucene/search/grouping/TestGroupFacetPrefixBinaryValues.java](src/org/apache/lucene/search/grouping/TestGroupFacetPrefixBinaryValues.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupFacetPrefixBinaryValues
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGroupFacetPrefixBinaryValues.java](TestGroupFacetPrefixBinaryValues.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
