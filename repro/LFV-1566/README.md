# The default ICUTokenizer can split the same Japanese text differently later in a JVM than at first (run: 'コン ーング゠' gives [コン, ーング゠], then [コン, ーン, グ゠]), because the ICU4J library it depends on picks the break engine for a run of dictionary characters from state that its word iterators share and that earlier text changes; known upstream (#9269 / LUCENE-8222, ICU-13669)

Finding LFV-1566 · Area: analysis / icu (ICUTokenizer word segmentation of Japanese text; the state that changes is in the ICU4J library the module depends on) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze the same Japanese text twice with the default ICUTokenizer. You expect the same tokens both times. Instead, in a new JVM, the text 'コン ーング゠' gave [コン, ーング゠] the first time and [コン, ーン, グ゠] the second time, with no error, so a text that is indexed early and analyzed again later (for example as a query) can have different terms. The root is not in a Lucene class but in the ICU4J library the module depends on, and a plain ICU word iterator gave two different boundary lists for the same text with no Lucene class in the analysis: ICU4J picks the break engine for a run of dictionary characters from state that its word iterators share, and a character that no engine accepts (here U+30A0) makes its catch-all 'unhandled' engine claim that character's whole script, after which the prolonged sound mark U+30FC no longer goes to the dictionary engine together with the Katakana that follows it (read in the library's source at ICU4J 79.1 and 77.1 and shown by a logged run on both). It is known upstream since 2018 (LUCENE-8222, apache/lucene#9269; the library's ticket ICU-13669 was open when read on 2026-11-20) and is not new in 11.0: the 10.6.0 release fails the same test.

## Expected vs actual

- Expected: You expect the same tokens both times.
- Actual: In a new JVM, the text 'コン ーング゠' gave [コン, ーング゠] the first time and [コン, ーン, グ゠] the second time, with no error, so a text that is indexed early and analyzed again later (for example as a query) can have different terms.

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerSameTextTwice.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerSameTextTwice.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): reproduced.
- The commands need the ICU4J jar that the checkout names: put icu4j-79.1.jar (Maven Central, com.ibm.icu:icu4j; 79.1 is the version of main f2b061406c4b) beside the test jars and add the line CP=$CP:icu4j-79.1.jar before the javac line. Without it the run reports 1 test failed with java.lang.NoClassDefFoundError: com/ibm/icu/util/ULocale, which is not the finding. With it the test fails with java.lang.AssertionError: expected:&lt;[コン, ーング゠]&gt; but was:&lt;[コン, ーン, グ゠]&gt;, with assertions on and off.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerSameTextTwice
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/icutokenizer-japanese-word-segmentation/](../../models/icutokenizer-japanese-word-segmentation/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
