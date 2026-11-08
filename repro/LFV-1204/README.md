# FSTCompiler.Builder#directAddressingMaxOversizingFactor is documented so that any negative factor disables direct addressing, but a very negative one (Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, or about -1e8 and below) saturates the int cost arithmetic in shouldExpandNodeWithDirectAddressing and turns direct addressing on, so five sparse BYTE4 keys give a 33.5 MB FST instead of 34 bytes (since 8.4.0)

Finding LFV-1204 · Area: util / FST (FSTCompiler fixed-length node encoding: binary search vs direct addressing) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1204.html](../../bugs/LFV-1204.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an FST with FSTCompiler.Builder and set directAddressingMaxOversizingFactor(Float.NEGATIVE_INFINITY), because its javadoc says a negative value disables direct addressing and only binary-search nodes will be created. You expect the root node of five BYTE4 keys with labels 0, 1, 2, 3 and 2^28 to be a binary-search node and the FST to take 34 bytes, as it does with -1 or the default. Instead (int) (sizeForBinarySearch * factor) saturates to Integer.MIN_VALUE, the int subtraction for expansionCost wraps to a negative value, the node is written with direct addressing and the FST takes 33,554,447 bytes, almost all of them presence bits for the 2^28 label range; every key is still found.

## Expected vs actual

- Expected: The root node is a binary-search node (`nodeFlags()` 32) and the FST takes 24 bytes, as with `-1`.
- Actual: `AssertionError: FST size 131085 expected:<32> but was:<64>`: a direct-addressing root node, with assertions on and off.

## Files

- [src/org/apache/lucene/util/fst/TestNegativeOversizingFactor.java](src/org/apache/lucene/util/fst/TestNegativeOversizingFactor.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions on, seed 5EED0001
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=5EED0001 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestNegativeOversizingFactor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNegativeOversizingFactor.java](TestNegativeOversizingFactor.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
