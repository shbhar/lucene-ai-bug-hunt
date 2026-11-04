# TieredMergePolicy.setFloorSegmentMB accepts a floor below one byte (for example 1e-9 MB or Double.MIN_VALUE) and stores it as 0 bytes, so once a segment's deletes-adjusted size is also 0 bytes, findMerges' allowed-segment-count loop never ends and commit() hangs forever while holding the IndexWriter lock

Finding LFV-1328 · Area: index / TieredMergePolicy floor setter (findMerges allowed-segment-count loop, IndexWriter.updatePendingMerges) · Priority: low · Impact: hang · Novelty: new

Finding page: [../../bugs/LFV-1328.html](../../bugs/LFV-1328.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give the default TieredMergePolicy setFloorSegmentMB(1e-9) (the setter rejects only values &lt;= 0), index 10,000 one-keyword documents into one small segment, delete all but one of them and call commit(), expecting it to return, as it does on the same steps, in about 90-150 ms, with a 1-byte (1e-6 MB) or the default 16 MB floor. Instead the setter stored the floor as (long) (1e-9 * 2^20) = 0 bytes (getFloorSegmentMB() reads back 0.0, a value the setter itself rejects) and the segment's deletes-adjusted size truncates to 0 bytes too, so findMerges starts its allowed-segment-count loop with levelSize = 0, where neither exit test can become true and no variable changes. That commit() spins forever at full CPU while holding the IndexWriter lock.

## Expected vs actual

- Expected: `commit()` returns and the test passes (with the fix it does; the test method then takes about 0.3 s).
- Actual: `commit()` is still running after 10 s at `TieredMergePolicy.findMerges(TieredMergePolicy.java:413)`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestTieredMergePolicySubByteFloor.java](src/org/apache/lucene/index/TestTieredMergePolicySubByteFloor.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestTieredMergePolicySubByteFloor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTieredMergePolicySubByteFloor.java](TestTieredMergePolicySubByteFloor.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
