# UnifiedHighlighter with maxPassages = Integer.MAX_VALUE throws a message-less IllegalArgumentException for every document that has a match (FieldHighlighter sizes its passage queue with Math.min(64, maxPassages + 1), which wraps)

Bug LFV-438 · Area: highlighter / UnifiedHighlighter passage selection (FieldHighlighter.highlightOffsetsEnums passage-queue sizing; maxPassages is never range-checked by UnifiedHighlighter.highlightFieldsAsObjects) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You ask the UnifiedHighlighter for at most Integer.MAX_VALUE passages, meaning 'no limit', and expect the same snippet that 1000 or Integer.MAX_VALUE - 1 gives (or, if the value is not allowed, an IllegalArgumentException that says so). Instead, for every document that has a match, FieldHighlighter.highlightOffsetsEnums sizes its passage queue with Math.min(64, maxPassages + 1); the + 1 wraps to Integer.MIN_VALUE, Math.min returns it, and the java.util.PriorityQueue constructor throws an IllegalArgumentException with no message. With per-field counts such as {1, MAX_VALUE} the field that asked for one passage loses its snippet too, because the whole call throws.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: For every document that has a match, FieldHighlighter.highlightOffsetsEnums sizes its passage queue with Math.min(64, maxPassages + 1); the + 1 wraps to Integer.MIN_VALUE, Math.min returns it, and the java.util.PriorityQueue constructor throws an IllegalArgumentException with no message.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterMaxPassagesMaxValue.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterMaxPassagesMaxValue.java) (JUnit test)
- [src2/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterMaxPassagesMaxValue.java](src2/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterMaxPassagesMaxValue.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterMaxPassagesMaxValue
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterMaxPassagesMaxValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
