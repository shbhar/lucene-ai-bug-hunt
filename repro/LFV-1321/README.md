# IntObjectHashMap, LongObjectHashMap and CharObjectHashMap ramBytesUsed() size the reused values() cursor instead of each value, so every entry is charged a flat 256 bytes whatever its value: a map holding an 8 MiB long[] reports 416 bytes, and a null value adds 256 (since 9.11.0)

Finding LFV-1321 · Area: core / util (internal hppc object maps: ramBytesUsed()) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1321.html](../../bugs/LFV-1321.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by Chris Parrinello (chrisparrinello; PR #17000, approved and merged by Alan Woodward; an independent fix, later than our listing) on 2026-11-12 (https://github.com/apache/lucene/pull/17000). Chris Parrinello fixed it in PR #17000 (opened 2026-11-06): sizeOfValues() now sizes value.value instead of the cursor in IntObjectHashMap, LongObjectHashMap and CharObjectHashMap, and each map gets two new tests. On the PR they noted that the HPPC project the code was forked from does not have this bug. Alan Woodward approved the PR on 2026-11-11, merged it into main on 2026-11-12 (milestone 11.0.0) and cherry-picked it to branch_11x the same day. The PR does not cite this list. Not on branch_10_6 (head 890b9f70c23, read 2026-11-14), so 10.6.x keeps the bug. Our minimal repro passes on main 781025d6fe32 and fails on f794ff5a505c, with and without assertions; with the three pre-fix map files put back on 781025d6fe32 it fails again. This site files nothing upstream.

Fixed upstream: [#17000](https://github.com/apache/lucene/pull/17000) (commit [2aec368e588f](https://github.com/apache/lucene/commit/2aec368e588f7908689edacd07631a568597385e)), merged 2026-11-12; also on the 11.0 release branch ([a3533d25f8ca](https://github.com/apache/lucene/commit/a3533d25f8ca)).

## What it shows

You put a value into an IntObjectHashMap (or a LongObjectHashMap or CharObjectHashMap) and call ramBytesUsed(). You expect the estimate to include that value, which is what the method's own sizeOfValues() loop is for: it walks values() and calls RamUsageEstimator.sizeOfObject once per entry. Instead the loop passes the iterator's reused ObjectCursor rather than cursor.value, sizeOfObject does not know that type and returns its 256-byte default, so every entry adds exactly 256 bytes: a map holding an 8 MiB long[] reports 416 bytes, and putting a null value into an empty map raises the estimate from 160 to 416 bytes.

## Expected vs actual

- Expected: `put(1, null)` leaves `ramBytesUsed()` at 160 bytes and the test passes.
- Actual: `java.lang.AssertionError: expected:<160> but was:<416>`: the `null` entry added 256 bytes.

## Files

- [src/org/apache/lucene/internal/hppc/TestIntObjectHashMapRamBytesUsed.java](src/org/apache/lucene/internal/hppc/TestIntObjectHashMapRamBytesUsed.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (a commit of 2026-11-02): reproduced.
- This finding was re-run on Lucene main 1a59f96fc6de on 2026-11-21: it no longer reproduces there.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.internal.hppc.TestIntObjectHashMapRamBytesUsed
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestIntObjectHashMapRamBytesUsed.java](TestIntObjectHashMapRamBytesUsed.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
