# FSTCompiler silently writes a corrupt FST when a fixed-length-arc node would expand to 2^31 bytes or more

Bug LFV-007 · Area: util / FST · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-007.html](../../bugs/LFV-007.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an FST in which one node has many arcs and at least one large output, so that its fixed-length arc area numArcs*maxBytesPerArc reaches 2^31 bytes. You expect a correct FST or an error. Instead, with assertions off, compile returns an FST whose node header was written over the unexpanded arc list: lookups through that node throw, and enumeration can return a key that was never added.

## Expected vs actual

- Expected: A correct FST, with all 2,048 `Util.get` calls returning their outputs (as with a 1,048,569-byte value or `allowFixedLengthArcs(false)`), or an error.
- Actual: With assertions off, `compile` returns normally and `Util.get` throws `IndexOutOfBoundsException: Index -32704 out of bounds for length 33`; with assertions on, an `AssertionError` while building.

## Files

- [src/TestFstWideNodeExpandedSizeWrap.java](src/TestFstWideNodeExpandedSizeWrap.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 7 test(s) run, 5 failed (testByte2ContinuousRoot_productWraps, testByte2DeepNode_onlyKeysUnderBadNodeBreak, testByte2SparseRoot_firstEntryIsBogusWithoutException, testByte2SparseRoot_productExactly2pow31, testByte2SparseRoot_savedAndReloaded).
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- This repro reads private Lucene state by reflection (setAccessible), so it depends on internals that may change.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=DEADBEEF -Dtests.method=testByte2* \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestFstWideNodeExpandedSizeWrap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [5b-fst-test.patch](5b-fst-test.patch)
- [FstWideNodeProbe.java](FstWideNodeProbe.java)

Formal models and scripts this bug's part of the hunt used: [../../models/study-fst-verification/](../../models/study-fst-verification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
