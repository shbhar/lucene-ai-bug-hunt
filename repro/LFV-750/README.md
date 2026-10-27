# SweetSpotSimilarity.hyperbolicTf returns max instead of about min for frequencies far below xoffset, because its NaN fallback is right only for the Math.pow overflow above the midpoint, so a hyperbolic-tf subclass is not monotone in freq and ranks a document with one occurrence above one with hundreds

Bug LFV-750 · Area: misc / similarities (SweetSpotSimilarity hyperbolicTf helper) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass SweetSpotSimilarity so that tf returns hyperbolicTf(freq), as its javadoc suggests, call setHyperbolicTfFactors(0, 2, 10, 400) and search, expecting the documented curve min+(max-min)/2*(tanh((freq-xoffset)*ln(base))+1): a tf near min (0) for frequencies far below the midpoint 400, rising to max (2) above it. Instead hyperbolicTf returns the maximum 2.0 for every frequency from 1 to 91 and drops to 0.0 at 92, so a document that contains the term once outranks an equally long one that contains it 400 times. Far below the midpoint Math.pow(base, xoffset - freq) overflows to Infinity (once xoffset - freq exceeds about 308 at base 10, about 2705 at the default base 1.3), the ratio becomes NaN, and the guard 'return Float.isNaN(result) ? max: result', which is right only for the overflow above the midpoint, returns max.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: hyperbolicTf returns the maximum 2.0 for every frequency from 1 to 91 and drops to 0.0 at 92, so a document that contains the term once outranks an equally long one that contains it 400 times.

## Files

- [src/org/apache/lucene/misc/TestSweetSpotHyperbolicTfOverflow.java](src/org/apache/lucene/misc/TestSweetSpotHyperbolicTfOverflow.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.TestSweetSpotHyperbolicTfOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
