# UnifiedHighlighter throws MaxBytesLengthExceededException when a TermInSetQuery's toString is longer than 32766 bytes (for example 886 UUID-like ids), because PassageScorer groups match labels in a BytesRefHash

Bug LFV-804 · Area: highlighter / UnifiedHighlighter passage scoring (PassageScorer match labels) · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a TermInSetQuery of 886 UUID-shaped ids (or 4,000 short terms) on a field indexed with offsets, get the hit, and call the default UnifiedHighlighter's highlight on that field, expecting the snippet with the matching id in &lt;b&gt;, as the equivalent ConstantScoreQuery over a SHOULD BooleanQuery of the same ids gives. Instead highlight throws BytesRefHash$MaxBytesLengthExceededException 'bytes can be at most 32766 in length; got 32786' from PassageScorer.score. For a multi-term set query the highlighter labels each match with the query's whole toString, which lists every term, and PassageScorer groups those labels in a BytesRefHash, which rejects entries over 32766 bytes; 885 ids (a 32,749-byte toString) still work.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: highlight throws BytesRefHash$MaxBytesLengthExceededException 'bytes can be at most 32766 in length; got 32786' from PassageScorer.score.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterLongMatchLabel.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterLongMatchLabel.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterLongMatchLabel
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
