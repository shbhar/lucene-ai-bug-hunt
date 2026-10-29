# ICUTokenizer with cjkAsWords=false silently drops the Japanese iteration mark 々 (U+3005), as in 人々, 時々 and 様々, and the other Han-script letters that are not Ideographic (U+303B, U+16FE3), because Default.rbbi gives them rule status 0

Bug LFV-653 · Area: analysis / ICU (DefaultICUTokenizerConfig break rules Default.rbbi / Default.brk, used for CJK text when cjkAsWords=false; ICUTokenizerFactory 'icu' with cjkAsWords="false") · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-653.html](../../bugs/LFV-653.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build ICUTokenizer with cjkAsWords=false, whose javadoc promises that text 'will be segmented according to UAX#29 defaults', and index Japanese text such as 人々 ('people'), 時々 ('sometimes') or 様々な人々; you expect 々, a Word_Break=ALetter letter under UAX #29, to end up in a token, as it does with StandardTokenizer, with the default cjkAsWords=true mode and with Lucene 4.6.1. Instead Default.rbbi takes every Han-script character out of its letter rule and gives a status only to Ideographic characters, and 々 is Han but not Ideographic, so its segment gets rule status 0 and ICUTokenizer skips it like punctuation: 人々 -&gt; [人], 様々な人々 -&gt; [様, な, 人], and with CJKBigramFilter 人々 becomes the unigram 人. Nothing throws; the character is simply missing from the index.

The test shows that `ICUTokenizer` with `new DefaultICUTokenizerConfig(false, true)` (cjkAsWords=false) drops the Japanese iteration mark 々 (U+3005, a UAX #29 letter) from 人々, because Default.rbbi gives its segment rule status 0.

## Expected vs actual

Expected tokens `[人, 々]` (as StandardTokenizer gives); actual `[人]` (`expected:<[人, 々]> but was:<[人]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerIterationMark.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerIterationMark.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerIterationMark
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
