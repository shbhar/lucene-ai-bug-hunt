# ClassicTokenizer, WikipediaTokenizer and HTMLStripCharFilter throw IndexOutOfBoundsException on valid text when a supplementary character (for example an emoji) directly follows an unbroken run that has left exactly one free char in the scan buffer (run: one leading space and 4,095 letters from a StringReader for the two tokenizers): the refill of Lucene's default JFlex skeleton holds the lone first surrogate back and still reports new input (ClassicTokenizer in the releases run from 6.0.0, the other two from 4.10.0, to 10.6.0, and on branch_11x and main; known outside Lucene)

Finding LFV-1552 · Area: analysis / ClassicTokenizer, WikipediaTokenizer and HTMLStripCharFilter (zzRefill of the scanners generated from Lucene's default JFlex skeleton) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize, with ClassicTokenizer at default settings, a valid text made of one space, a run of 4,095 letters, an emoji (U+1F600) and ' b', read from a java.io.StringReader. You expect the one token b: a run longer than the default maximum token length of 255 is skipped, and that is what the same text gives with 4,094 or 4,096 letters. Instead incrementToken() throws IndexOutOfBoundsException, with and without assertions: the scanner's 4,096-char buffer holds the 4,095 letters and has one free slot, the refill reads the first half of the emoji's surrogate pair into it, takes it back out because the pair is incomplete, and still reports new input, so the scanner asks for a character at the end of the buffered text. In the runs WikipediaTokenizer throws on the same text and HTMLStripCharFilter on 16,382 chars of text followed by '&lt;p' and 16,383 chars of that tag before the emoji; IndexWriter.addDocument with ClassicAnalyzer passes the exception on, that document is not indexed and the writer stays usable; StandardTokenizer and UAX29URLEmailTokenizer did not throw on the text. Not new in 11: 10.6.0 fails the same test.

## Expected vs actual

- Expected: You expect the one token b: a run longer than the default maximum token length of 255 is skipped, and that is what the same text gives with 4,094 or 4,096 letters.
- Actual: incrementToken() throws IndexOutOfBoundsException, with and without assertions: the scanner's 4,096-char buffer holds the 4,095 letters and has one free slot, the refill reads the first half of the emoji's surrogate pair into it, takes it back out because the pair is incomplete, and still reports new input, so the scanner asks for a character at the end of the buffered text.

## Files

- [src/org/apache/lucene/analysis/classic/TestClassicTokenizerSupplementaryAfterLongRun.java](src/org/apache/lucene/analysis/classic/TestClassicTokenizerSupplementaryAfterLongRun.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main f6e694652c3d (a commit of 2026-11-19): reproduced.
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

# run 1: assertions on, seed C4EC0881
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C4EC0881 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.classic.TestClassicTokenizerSupplementaryAfterLongRun
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
