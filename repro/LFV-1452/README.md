# ThaiRepeatFilter (new in 10.6.0) emits the repeats of a word ending in the Thai repetition mark Maiyamok (U+0E46) before the tokens stacked on that word (a KeywordRepeatFilter copy, a synonym, a TypeAsSynonymFilter type token), so the stacked token moves one position later with a start offset that goes backwards and IndexWriter rejects the document

Finding LFV-1452 · Area: analysis / Thai (ThaiRepeatFilter emission order; a custom chain with a token-stacking filter before thaiRepeat) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1452.html](../../bugs/LFV-1452.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an analyzer whose chain stacks a second token on each word before ThaiRepeatFilter (for example WhitespaceTokenizer &gt; KeywordRepeatFilter &gt; ThaiRepeatFilter) and index the Thai word 'เร็วๆ' ('quickly': the word เร็ว [0,4) followed by the repetition mark Maiyamok [4,5)) in a plain TextField. You expect both copies of เร็ว [0,4) at position 0, the repeat [4,5) at position 1, and the document to be indexed. Instead ThaiRepeatFilter emits the first copy's queued repeat before it reads the stacked copy, so the stream is pos 0 [0,4), pos 1 [4,5), pos 1 [0,4), pos 2 [4,5): the stacked copy moves one position later, its start offset 0 comes after 4, and IndexWriter.addDocument throws IllegalArgumentException 'offsets must not go backwards startOffset=0,endOffset=4,lastStartOffset=4'.

## Expected vs actual

- Expected: `addDocument` indexes the document: `เร็ว` [0,4) twice at position 0, `เร็ว` [4,5) twice at position 1.
- Actual: `IllegalArgumentException: ... offsets must not go backwards startOffset=0,endOffset=4,lastStartOffset=4 for field 'body'`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/th/TestThaiRepeatFilterStackedTokens.java](src/org/apache/lucene/analysis/th/TestThaiRepeatFilterStackedTokens.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.th.TestThaiRepeatFilterStackedTokens
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestThaiRepeatFilterStackedTokens.java](TestThaiRepeatFilterStackedTokens.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
