# OpenNLPSentenceBreakIterator.next(n) returns DONE instead of the end of the text when the n-th boundary is the last one, so next(1) differs from next and a caller that steps with next(n) until DONE loses the final sentence group

Bug LFV-622 · Area: analysis / opennlp OpenNLPSentenceBreakIterator (java.text.BreakIterator implementation, next(int)) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-622.html](../../bugs/LFV-622.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set a text on an OpenNLPSentenceBreakIterator and call next(n) to jump n sentence boundaries, expecting the n-th boundary, as java.text.BreakIterator#next(int) promises ('Returns the nth boundary from the current boundary') and as n calls of next give. When that boundary is the end of the text, next(n) returns DONE (-1) instead: with the model-free NLPSentenceDetectorOp on 'Whole text is one sentence.', next from first returns 27 but next(1) returns -1, and on the module's three-sentence test text (boundaries 0, 31, 59, 102) next(3) from first returns -1 instead of 102 while next(2) correctly returns 59. The iterator is still moved to the end, so only the returned value is wrong; code that steps with next(n) until DONE loses the final sentence group.

## Expected vs actual

- Expected: `next(1)` from `first` returns 27, like `next`; `next(3)` from `first` returns 102, the end of the text.
- Actual: Both return -1 (`DONE`): 2 of 3 tests fail, with assertions on and off; the controls pass, and `current` is 102 after `next(3)`.

## Files

- [src/org/apache/lucene/analysis/opennlp/TestSentenceBreakIteratorNextN.java](src/org/apache/lucene/analysis/opennlp/TestSentenceBreakIteratorNextN.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/opennlp/build/libs/lucene-analysis-opennlp-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 1880C0DE
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=1880C0DE \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.opennlp.TestSentenceBreakIteratorNextN
```

The module(s) analysis-opennlp also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestOpenNLPSentenceBreakIteratorNextN.java](TestOpenNLPSentenceBreakIteratorNextN.java)
- [test-additions.diff](test-additions.diff)
- [fix-option-A.diff](fix-option-A.diff)
- [TestSentenceBreakIteratorNextN.java](TestSentenceBreakIteratorNextN.java)
- [NextNHistoryProbe.java](NextNHistoryProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
