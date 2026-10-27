# StandardAnalyzer and the other JFlex-generated scanners (StandardTokenizer, UAX29URLEmailTokenizer, ClassicTokenizer, WikipediaTokenizer, HTMLStripCharFilter) silently index no tokens for a text value that ends in an unpaired high surrogate, such as a string cut inside an emoji; values longer than the scan buffer lose or split their last buffer window

Bug LFV-158 · Area: analysis / StandardTokenizer and the other JFlex-generated scanners (zzRefill in both Lucene JFlex skeletons) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-158.html](../../bugs/LFV-158.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a text field with StandardAnalyzer at default settings, and the value ends in an unpaired high surrogate, for example 'hello world' followed by U+D83D, which is what cutting a Java String inside an emoji, or decoding the JSON escape \ud83d, produces. You expect hello and world to be indexed and end to report the input length as the final offset, as happens when the same lone surrogate sits mid-text and as Lucene 7.6.0 did. Instead the scanner's refill reads the whole text, looks one char ahead for the missing low surrogate, hits end of input and reports 'no new input', so the scanner ends the stream without scanning what it just read: no tokens, final offset 0, and the document does not match title:hello; values longer than the 255-char buffer lose or split their last window instead.

## Expected vs actual

- Expected: `[hello, world]` with final offsets 12 and 13, and `title:hello` matches 2 documents, as on 7.6.0.
- Actual: `` with final offset 0 for both values, and `title:hello` matches 1 document (main with -ea and -da, 10.5.2, 7.7.0).

## Files

- [src/org/apache/lucene/analysis/standard/TestStandardTokenizerTrailingHighSurrogate.java](src/org/apache/lucene/analysis/standard/TestStandardTokenizerTrailingHighSurrogate.java) (JUnit test)
- [src/org/apache/lucene/analysis/classic/TestJFlexScannersTrailingHighSurrogate.java](src/org/apache/lucene/analysis/classic/TestJFlexScannersTrailingHighSurrogate.java) (JUnit test)
- [src/trailhs/TrailingHighSurrogateMain.java](src/trailhs/TrailingHighSurrogateMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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

# run 1: assertions on, seed 28CA5EF0
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=28CA5EF0 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.standard.TestStandardTokenizerTrailingHighSurrogate \
       --select-class org.apache.lucene.analysis.classic.TestJFlexScannersTrailingHighSurrogate

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" trailhs.TrailingHighSurrogateMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TrailingHighSurrogate.java](TrailingHighSurrogate.java)
- [TestStandardAnalyzerTrailingHighSurrogate.java](TestStandardAnalyzerTrailingHighSurrogate.java)
- [TestTrailingHighSurrogateJFlexScanners.java](TestTrailingHighSurrogateJFlexScanners.java)
- [fix-with-tests.diff](fix-with-tests.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
