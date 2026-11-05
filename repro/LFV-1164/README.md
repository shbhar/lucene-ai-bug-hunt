# PackedInts' int[] -&gt; long[] encoder sign-extends the low part of a value that crosses a 64-bit block, so at 35, 37-39, 41-47 or 49-63 bits per value an int with its top bit set (for example -1, which the encoder reads as 4294967295 everywhere else) gets extra high bits and decodes too large; latent, no Lucene code encodes int[] this way (since 4.0.0)

Finding LFV-1164 · Area: util / packed (PackedInts bulk encoders: BulkOperationPacked.encode(int[] -> long[])) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1164.html](../../bugs/LFV-1164.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You get the PACKED encoder for 35 bits per value from PackedInts.getEncoder, encode one iteration of 64 ints that are all 0 except values[53] = -1 into long blocks, and decode the blocks back to long[]. You expect value 53 to come back as 4294967295, because the int[] encoder reads each int as its unsigned 32-bit value (it does so on its other branches, and the upstream TestPackedInts oracle defines an int that way), and that is what you get at any position that does not straddle two blocks and at 33, 34, 36, 40, 48 or 64 bits per value. Instead it comes back as 17179869183 (2^34 - 1): value 53 straddles blocks 28 and 29, and BulkOperationPacked.java:209 masks its 34-bit low part out of the sign-extended int instead of the zero-extended one, so two extra bits are set.

## Expected vs actual

- Expected: `decoded[53]` is 4294967295 (the unsigned value of `-1`) and the test passes.
- Actual: `java.lang.AssertionError: expected:<4294967295> but was:<17179869183>`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/packed/TestPackedIntEncodeSignExtension.java](src/org/apache/lucene/util/packed/TestPackedIntEncodeSignExtension.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.packed.TestPackedIntEncodeSignExtension
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPackedIntEncodeSignExtension.java](TestPackedIntEncodeSignExtension.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
