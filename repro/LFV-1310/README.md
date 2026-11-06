# IndexWriter's 'BD' InfoStream over-reports the deletes and updates of a buffered-updates packet that is resolved again after a concurrent merge commits, because FrozenBufferedUpdates.apply returns the packet's running total instead of this call's count (8 reported for 4 deleted documents)

Finding LFV-1310 · Area: index / IndexWriter buffered deletes and updates resolution (forceApply re-resolve loop, InfoStream diagnostics) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1310.html](../../bugs/LFV-1310.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable IndexWriter's InfoStream and delete documents while a merge is running; when that merge commits while the delete packet is being resolved, IndexWriter resolves the packet a second time against the merged segment. You expect the 'BD' log to report the documents the packet newly deleted, 4 in the test, and the second pass's own phase line does report '0 new deletions'. Instead the second pass's 'done inner apply' line repeats the first pass's 4 and the final 'done apply del packet ... N new deletes/updates' line reports 8, because FrozenBufferedUpdates.apply returns the packet's running total and IndexWriter.forceApply adds the returned values up; the index itself is correct (4 documents deleted), only the logged counts are wrong.

## Expected vs actual

- Expected: The final `BD` line reports `4 new deletes/updates`, the 4 documents the delete removed.
- Actual: `done apply del packet (...) to 3 segments; 8 new deletes/updates; ...; 2 iters due to concurrent merges`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestReResolvedPacketDeleteCount.java](src/org/apache/lucene/index/TestReResolvedPacketDeleteCount.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestReResolvedPacketDeleteCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestReResolvedPacketDeleteCount.java](TestReResolvedPacketDeleteCount.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
