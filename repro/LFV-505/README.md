# Default UnifiedHighlighter (WEIGHT_MATCHES) returns no highlights for a block-join hit when the join's inner query uses the highlighted field, because Weight#matches runs on an overlay reader whose highlighted field holds only the hit document

Bug LFV-505 · Area: highlighter / UnifiedHighlighter WEIGHT_MATCHES path (FieldOffsetStrategy, OverlaySingleDocTermsLeafReader) with block-join queries · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index parent/child blocks whose parents and children share a text field, search with +text:shirt +ToParentBlockJoinQuery(+text:small #type:child), and highlight 'text' with a default UnifiedHighlighter. You expect 'red &lt;b&gt;shirt&lt;/b&gt; sale' and 'blue &lt;b&gt;shirt&lt;/b&gt;', which is what withWeightMatches(false) returns and what Weight#matches on the searched index reports. Instead you get 'red shirt sale' and 'blue shirt' with no highlight, because the highlighter evaluates Weight#matches on OverlaySingleDocTermsLeafReader, where the highlighted field holds only the hit document, so the child query matches no child, the required join clause does not match, and the whole query's Matches is null.

## Expected vs actual

- Expected: You expect 'red &lt;b&gt;shirt&lt;/b&gt; sale' and 'blue &lt;b&gt;shirt&lt;/b&gt;', which is what withWeightMatches(false) returns and what Weight#matches on the searched index reports.
- Actual: You get 'red shirt sale' and 'blue shirt' with no highlight, because the highlighter evaluates Weight#matches on OverlaySingleDocTermsLeafReader, where the highlighted field holds only the hit document, so the child query matches no child, the required join clause does not match, and the whole query's Matches is null.

## Files

- [src/org/apache/lucene/search/join/TestUnifiedHighlighterBlockJoinSameField.java](src/org/apache/lucene/search/join/TestUnifiedHighlighterBlockJoinSameField.java) (JUnit test)
- [src2/org/apache/lucene/search/join/TestUnifiedHighlighterBlockJoinSameField.java](src2/org/apache/lucene/search/join/TestUnifiedHighlighterBlockJoinSameField.java) (JUnit test)

## Run it

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestUnifiedHighlighterBlockJoinSameField
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestUnifiedHighlighterBlockJoinSameField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
