# StandardTokenizer.setMaxTokenLength lowered between two incrementToken() calls shrinks the scanner buffer under the scanner's read positions, so the next incrementToken() throws ArrayIndexOutOfBoundsException or IndexOutOfBoundsException instead of continuing (UAX29URLEmailTokenizer the same; since 4.10.0, LUCENE-5897)

Finding LFV-1202 · Area: analysis / StandardTokenizer (JFlex scanner buffer resize: StandardTokenizer.setMaxTokenLength, StandardTokenizerImpl.setBufferSize) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1202.html](../../bugs/LFV-1202.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize 'hello' with a StandardTokenizer, read its only token, and then call setMaxTokenLength(2), a value inside the documented range [1, 1M], which the setter accepts. You expect the next incrementToken() to return false, because no input is left (it does without the call, or when the length is raised). Instead it throws ArrayIndexOutOfBoundsException 'arraycopy: last source index 5 out of bounds for char[2]' from StandardTokenizerImpl.zzRefill: the setter also resizes the scanner's buffer, and setBufferSize copies only min(old, new) chars but keeps the scanner's read positions, which now point past the end of the smaller buffer.

## Expected vs actual

- Expected: The second `incrementToken()` returns false: no text is left.
- Actual: `ArrayIndexOutOfBoundsException: arraycopy: last source index 5 out of bounds for char[2]` at `StandardTokenizerImpl.zzRefill(StandardTokenizerImpl.java:759)`.

## Files

- [src/org/apache/lucene/analysis/standard/TestStandardTokenizerMaxTokenLengthMidStream.java](src/org/apache/lucene/analysis/standard/TestStandardTokenizerMaxTokenLengthMidStream.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.standard.TestStandardTokenizerMaxTokenLengthMidStream
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestStandardTokenizerMaxTokenLengthMidStream.java](TestStandardTokenizerMaxTokenLengthMidStream.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
