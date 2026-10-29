# UnifiedHighlighter with maxPassages=0 throws NullPointerException for every document that has a match (a per-field 0 makes the whole multi-field call fail)

Bug LFV-265 · Area: highlighter / UnifiedHighlighter passage selection (FieldHighlighter.maybeAddPassage; maxPassages is never range-checked by UnifiedHighlighter.highlightFieldsAsObjects) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call UnifiedHighlighter.highlight(field, query, topDocs, 0), or highlightFields with a per-field count of 0, and expect what the same call already does for a document without a match: a null snippet (or, if 0 is not allowed, an IllegalArgumentException that says so). Instead, for every document that has a match in the field, FieldHighlighter.maybeAddPassage tests passageQueue.size == maxPassages (0 == 0) while the passage queue is still empty and then calls passageQueue.peek.getScore, so a NullPointerException escapes the call. With {title: 0, body: 1} the body field, which asked for one passage, loses its snippet too, because the whole call throws.

The test shows that `UnifiedHighlighter.highlight(field, query, topDocs, 0)` fails with an internal NullPointerException for a document that matches in the field.

## Expected vs actual

Expected a null snippet (or an up-front `IllegalArgumentException`); actual `NullPointerException` because `PriorityQueue.peek()` is null in `FieldHighlighter.maybeAddPassage` (FieldHighlighter.java:212), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/uhighlight/TestZeroMaxPassages.java](src/org/apache/lucene/search/uhighlight/TestZeroMaxPassages.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestZeroMaxPassages
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
