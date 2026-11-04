# VectorUtil.l2normalize silently returns the zero vector instead of a unit vector when the float32 sum of squares of a finite non-zero vector overflows, for example 64 components of 1e19 (since 9.8.0; a single component above about 1.8e19 already did so since 9.0.0)

Finding LFV-1336 · Area: util / VectorUtil (l2normalize; default and Panama vectorization providers) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1336.html](../../bugs/LFV-1336.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call VectorUtil.l2normalize on a finite, non-zero float vector with large components, for example 64 components of 1e19 (each square, 1e38, and the norm, 8e19, are finite floats). You expect the unit vector, 64 components of 0.125, because the javadoc says it 'Modifies the argument to be unit length, dividing by its l2-norm' and throws only for zero vectors. Instead it returns normally and leaves 64 zeros: the squared norm is summed in float32 and overflows to +Infinity, only an exactly-zero norm is checked, and every component is divided by Infinity.

## Expected vs actual

- Expected: `v` becomes the unit vector `{0.5, 0.5, 0.5, 0.5}` (Lucene 9.7.0 gives exactly this).
- Actual: `v` becomes `{0.0, 0.0, 0.0, 0.0}`: `expected:<0.5> but was:<0.0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/TestL2NormalizeSquaredNormOverflow.java](src/org/apache/lucene/util/TestL2NormalizeSquaredNormOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestL2NormalizeSquaredNormOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestL2NormalizeSquaredNormOverflow.java](TestL2NormalizeSquaredNormOverflow.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
