# ICUTokenizer with cjkAsWords=false can divide a Katakana run with ICU's CJK dictionary since 9.11.0 (デジタルカメラ gives デジタル and カメラ; 9.10.0 gave one token), although the option's javadoc says the text is then segmented according to UAX#29 defaults: the ICU 74.2 upgrade (#13239) added Katakana to the CJK set of Default.rbbi and that set to its dictionary trigger set

Finding LFV-1568 · Area: analysis / ICU (ICUTokenizer with cjkAsWords=false: the break rules Default.rbbi / Default.brk of DefaultICUTokenizerConfig; ICUTokenizerFactory 'icu' with cjkAsWords="false") · Priority: low · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build ICUTokenizer with new DefaultICUTokenizerConfig(false, true), whose constructor javadoc says of cjkAsWords=false that text 'will be segmented according to UAX#29 defaults' (the cjkAsWords attribute of ICUTokenizerFactory builds the same config), and analyze the Japanese word デジタルカメラ ('digital camera', seven Katakana letters); you expect one token, which is what UAX #29 gives for adjacent Katakana letters (rule WB13), what StandardTokenizer gives and what Lucene 9.10.0 gave. Instead, on each release from 9.11.0 to 10.6.0, on branch_11x and on main you get two tokens, デジタル and カメラ, with no error. The ICU 74.2 upgrade (#13239) remade Default.rbbi from ICU's rule text, in which Katakana is part of the CJK set and that set is part of the set that triggers ICU's dictionary break engines, so the Katakana run that rule 13 keeps whole is handed to ICU's CJK dictionary engine and divided (logged for this word on ICU4J 79.1 and 77.1), although the file's own header says it is modified 'to avoid CJK dictionary break'. Not every Katakana run is divided (テキストファイル and カタカナ stay whole), the default mode (cjkAsWords=true) gave the same tokens before and after for the eight texts run, and it is not new in 11.0: the 10.6.0 release behaves the same.

## Expected vs actual

- Expected: You expect one token, which is what UAX #29 gives for adjacent Katakana letters (rule WB13), what StandardTokenizer gives and what Lucene 9.10.0 gave.
- Actual: On each release from 9.11.0 to 10.6.0, on branch_11x and on main you get two tokens, デジタル and カメラ, with no error.

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerKatakanaWithoutCjkAsWords.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerKatakanaWithoutCjkAsWords.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): reproduced.
- The test needs ICU4J, the third-party runtime jar of the analysis-icu module, which the block does not list: add the line CP=$CP:icu4j-79.1.jar (Maven Central, com.ibm.icu) before the javac line. Without that jar the one test still fails, with java.lang.NoClassDefFoundError: com/ibm/icu/util/ULocale, which is not the finding. Run on 2026-11-20 with the jar, with assertions on and off, on a build of main f2b061406c4b and of branch_11x 75eea467ac52: 1 test found, 1 failed, with org.junit.ComparisonFailure: term 0 expected:&lt;デジタル[カメラ]&gt; but was:&lt;デジタル[]&gt;. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with org.junit.ComparisonFailure: term 0 expected:&lt;デジタル[カメラ]&gt; but was:&lt;デジタル[]&gt; (the first token is デジタル, where the test expects the whole word as one token).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 1CEF1257CA11
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=1CEF1257CA11 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerKatakanaWithoutCjkAsWords
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
