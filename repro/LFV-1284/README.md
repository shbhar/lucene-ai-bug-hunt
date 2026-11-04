# StandardTokenizerImpl.setBufferSize growing the buffer after yypushback to the buffer start loses a held-back high surrogate, so the next refill reads new input over it: a supplementary character silently disappears and the next offsets shift by one (UAX29URLEmailTokenizerImpl the same; since 4.10.0)

Finding LFV-1284 · Area: analysis / StandardTokenizerImpl (generated JFlex scanner: setBufferSize and the zzRefill high-surrogate hold-back) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1284.html](../../bugs/LFV-1284.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You drive StandardTokenizerImpl directly on "ab" + U+1F600 + "cd" with setBufferSize(3), read the first token "ab", push it back with yypushback(yylength()) and grow the buffer with setBufferSize(8). You expect the scan to go on with ab@0, U+1F600@2, cd@4, as it does with only the pushback or only the growth. Instead it returns ab@0, cd@3: the first refill held the emoji's high surrogate back in the last slot of the 3-char buffer (zzFinalHighSurrogate), the growth keeps that hold-back, and because the pushback left the scan at buffer index 0 the next refill skips the shift that would fold the held char back in and reads new input over it, so the emoji is lost and the next offsets are one short (on longer input a later buffer shift folds in one stale NUL char, which is dropped but can split a word, and the offsets after it are right again).

## Expected vs actual

- Expected: `[ab@0, 😀@2, cd@4]`: the pushed-back `ab`, then the emoji, then `cd`.
- Actual: `[ab@0, cd@3]`: the emoji is missing and `cd`'s offset is one short, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/standard/TestStandardTokenizerImplGrowAfterPushback.java](src/org/apache/lucene/analysis/standard/TestStandardTokenizerImplGrowAfterPushback.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.standard.TestStandardTokenizerImplGrowAfterPushback
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestStandardTokenizerImplGrowAfterPushback.java](TestStandardTokenizerImplGrowAfterPushback.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
