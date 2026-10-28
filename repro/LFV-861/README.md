# Classic Highlighter's SimpleSpanFragmenter.start does not reset waitForPos, so a Highlighter reused for the next text (or for the same text again) keeps suppressing fragment boundaries after a phrase match that ended the previous text: one oversized fragment, then one-word fragments and a different best fragment, depending on what was highlighted before

Bug LFV-861 · Area: highlighter / classic Highlighter fragmenting (SimpleSpanFragmenter.start, state carried across texts) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reuse one classic Highlighter with a QueryScorer and a SimpleSpanFragmenter for several texts (Highlighter keeps one fragmenter and calls its start, documented as 'Initializes the Fragmenter', for every text) and expect each text to be cut into the same fragments as with a fresh Highlighter. Instead start resets the position and fragment count but not waitForPos, the position the fragmenter is still waiting for, so when a phrase match ended the previous text no boundary is allowed in the next text until it reaches that old position: with the phrase 'search library' at the end of the first text and fragment size 20, the next text starts with one 70-character fragment 'Highlighting picks short fragments of text around each match so that a', then one-word fragments ', reader', ', of', ', the', where a fresh Highlighter gives 'Highlighting picks', ' short fragments of', ' text around each', and getBestFragment also differs. Hits and scores are unaffected; only the snippet layout changes, and it depends on what the same Highlighter highlighted before.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: start resets the position and fragment count but not waitForPos, the position the fragmenter is still waiting for, so when a phrase match ended the previous text no boundary is allowed in the next text until it reaches that old position: with the phrase 'search library' at the end of the first text and fragment size 20, the next text starts with one 70-character fragment 'Highlighting picks short fragments of text around each match so that a', then one-word fragments ', reader', ', of', ', the', where a fresh Highlighter gives 'Highlighting picks', ' short fragments of', ' text around each', and getBestFragment also differs.

## Files

- [src/org/apache/lucene/search/highlight/TestSimpleSpanFragmenterReuse.java](src/org/apache/lucene/search/highlight/TestSimpleSpanFragmenterReuse.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.highlight.TestSimpleSpanFragmenterReuse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
