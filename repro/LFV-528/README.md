# ShingleFilter with minShingleSize &gt;= 3 ends the token stream early when a hole (for example a removed stop word) is near the end, so the last word(s) are never output as unigrams and are missing from the index ('a cup of tea' loses 'tea'; 'the cat' yields no token at all)

Bug LFV-528 · Area: analysis-common / shingle (ShingleFilter, ShingleFilterFactory, ShingleAnalyzerWrapper) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-528.html](../../bugs/LFV-528.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'a cup of tea' with WhitespaceTokenizer, StopFilter{a, of, the} and ShingleFilter(min 3, max 3) with unigrams on (the default), and expect every real word to be indexed as a unigram, because the javadoc says unigrams 'are always output, regardless of whether any shingles are available' and that the unigram option 'is independent of the min shingle size'. Instead, when the window starts with the filler left by the removed stop word, incrementToken skips the all-filler unigram, finds fewer than minShingleSize positions left for a shingle and returns false, which ends the stream although 'tea' is still in its window: 'tea' is never indexed and TermQuery(tea) finds 0 documents. With min 4 up to two trailing words are lost, a value such as 'the cat' yields no token at all, and nothing throws, with assertions on or off.

## Expected vs actual

- Expected: Every word as a unigram: `tea`@3 for "a cup of tea" (min 3), `two`@2 and `three`@3 for "one of two three" (min 4), `cat`@1 for "the cat"; `TermQuery(title:tea)` finds 1 document.
- Actual: No `tea`, `two`, `three` or `cat` ("the cat" yields no token at all) and 0 hits for `title:tea`: 4 of 5 tests fail, assertions on and off.

## Files

- [src/org/apache/lucene/analysis/shingle/TestShingleFilterTrailingHoleUnigrams.java](src/org/apache/lucene/analysis/shingle/TestShingleFilterTrailingHoleUnigrams.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.shingle.TestShingleFilterTrailingHoleUnigrams
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestShingleFilterHoleNearEnd.java](TestShingleFilterHoleNearEnd.java)
- [shingle-skip-empty-position.diff](shingle-skip-empty-position.diff)
- [ShingleHoleProbe.java](ShingleHoleProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
