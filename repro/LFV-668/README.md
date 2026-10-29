# SoraniNormalizer checks for a word-final HEH before it removes trailing directional marks, so a word ending in HEH followed by RLM, LRM or ALM keeps HEH (U+0647) instead of becoming AE (U+06D5) as the class javadoc says

Bug LFV-668 · Area: analysis-common / Sorani normalizer (SoraniNormalizer.normalize; SoraniNormalizationFilter, SoraniAnalyzer and the 'soraniNormalization' factory) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-668.html](../../bugs/LFV-668.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Sorani Kurdish text with the stock SoraniAnalyzer or SoraniNormalizationFilter. The normalizer's javadoc says a word-final HEH (U+0647) is converted to AE (U+06D5) and that formatting characters such as directional controls are removed, so خانه ('house') followed by an invisible right-to-left mark should normalize like خانه itself, to خانە. Instead the HEH rule asks 'is this the last character?' while the trailing mark is still in the buffer, and the mark is deleted only later in the same pass, so خانه+RLM (and +LRM, +ALM) becomes خانه with a plain HEH and no longer matches خانە, with no error.

The test shows that `SoraniNormalizationFilter` leaves a word-final HEH (U+0647) unconverted when an invisible RLM (U+200F) follows it: the RLM is removed, but only after the word-final test has already been made.

## Expected vs actual

Expected the term U+062E U+0627 U+0646 U+06D5 (AE, as without the mark); actual U+062E U+0627 U+0646 U+0647 (-ea and -da).

## Files

- [src/org/apache/lucene/analysis/ckb/TestSoraniFinalHehBeforeRlm.java](src/org/apache/lucene/analysis/ckb/TestSoraniFinalHehBeforeRlm.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ckb.TestSoraniFinalHehBeforeRlm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
