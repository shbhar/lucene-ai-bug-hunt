# OpenNLPSentenceBreakIterator on text that starts with whitespace: a backward walk with previous returns a non-boundary and then throws ArrayIndexOutOfBoundsException, and following at an offset inside the leading whitespace throws StackOverflowError, because setText keeps the sentence detector's whitespace-trimmed first sentence start while first and next use the text's begin index

Bug LFV-623 · Area: analysis-opennlp / OpenNLPSentenceBreakIterator (public java.text.BreakIterator over OpenNLP sentence spans: setText, previous, following, moveToSentenceAt) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-623.html](../../bugs/LFV-623.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create an OpenNLPSentenceBreakIterator, give it a text that starts with whitespace, for example ' Sentence number 1 has 6 words. Sentence number 2, 5 words.', and walk it backward with last/previous (the reverse loop shown in the BreakIterator javadoc), expecting the forward boundaries [0, 33, 60] in reverse order and then DONE. Instead previous returns 2, which is not a boundary, and the next previous throws ArrayIndexOutOfBoundsException (Index -1); following(0) on a one-sentence text with leading whitespace recurses until StackOverflowError. The OpenNLP sentence detector trims whitespace from its spans and setText stores those starts unchanged, so the first stored sentence start lies after the begin index that first and next treat as the first boundary.

## Expected vs actual

- Expected: The backward walk gives 60, 33, 0, then `DONE`; `following(0)` returns a boundary (32 or 33) or `DONE`.
- Actual: The backward walk gives 60, 33, 2, then `ArrayIndexOutOfBoundsException: Index -1`; `following(0)` on one sentence throws `StackOverflowError` (3 of 3 tests fail with assertions on, 2 of 3 off).

## Files

- [src/org/apache/lucene/analysis/opennlp/TestSentenceBreakIteratorLeadingWhitespace.java](src/org/apache/lucene/analysis/opennlp/TestSentenceBreakIteratorLeadingWhitespace.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.opennlp.TestSentenceBreakIteratorLeadingWhitespace
```

The module(s) analysis-opennlp also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestOpenNLPSentenceBreakIteratorLeadingWhitespace.java](TestOpenNLPSentenceBreakIteratorLeadingWhitespace.java)
- [fix-option-A.diff](fix-option-A.diff)
- [TestSentenceBreakIteratorLeadingWhitespace.java](TestSentenceBreakIteratorLeadingWhitespace.java)
- [TestSentenceBreakIteratorLeadingBlank.java](TestSentenceBreakIteratorLeadingBlank.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
