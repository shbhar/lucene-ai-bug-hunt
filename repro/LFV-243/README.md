# DocValuesRewriteMethod's Weight#matches looks the matched terms up in the postings, so on a doc-values-only field it returns null for every hit of newSlowSetQuery or any DOC_VALUES_REWRITE query (highlights are silently lost, and a MUST_NOT clause of this kind fails open in Weight#matches)

Bug LFV-243 · Area: core / search: MultiTermQuery rewrite methods (DocValuesRewriteMethod, DOC_VALUES_REWRITE) and the Matches API (Weight#matches) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep a keyword field as doc values only, filter on it with SortedSetDocValuesField.newSlowSetQuery or a PrefixQuery with DOC_VALUES_REWRITE (DocValuesRewriteMethod says it 'can be used to perform these queries against an unindexed docvalues field'), and expect Weight#matches to return non-null Matches (MATCH_WITH_NO_TERMS) for every hit, as its javadoc promises for DocValues queries and as newSlowExactQuery on the same field does. Instead matches looks the matched terms up in the field's postings, which do not exist, so it returns null for every hit; for +body:fruit #tags:(banana) the UnifiedHighlighter with default settings returns 'a ripe fruit in a basket' with no highlighted term, while the same query with newSlowRangeQuery as the filter highlights '&lt;b&gt;fruit&lt;/b&gt;'. As a MUST_NOT clause the same null makes Weight#matches non-null for documents the clause excludes; hits, counts and scores stay correct.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: matches looks the matched terms up in the field's postings, which do not exist, so it returns null for every hit; for +body:fruit #tags:(banana) the UnifiedHighlighter with default settings returns 'a ripe fruit in a basket' with no highlighted term, while the same query with newSlowRangeQuery as the filter highlights '&lt;b&gt;fruit&lt;/b&gt;'.

## Files

- [src/org/apache/lucene/search/TestDocValuesRewriteMatches.java](src/org/apache/lucene/search/TestDocValuesRewriteMatches.java) (JUnit test)
- [src/org/apache/lucene/search/uhighlight/TestDocValuesSetFilterHighlight.java](src/org/apache/lucene/search/uhighlight/TestDocValuesSetFilterHighlight.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestDocValuesRewriteMatches

# run 2: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestDocValuesSetFilterHighlight
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
