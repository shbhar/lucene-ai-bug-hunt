# NativeFSLockFactory tracks the write locks this JVM holds by real-path string instead of by file, so obtaining write.lock through a second path to the same file (a hard link, or a bind mount of the index directory) opens and closes a second channel on it, which silently drops the holder's OS lock while its ensureValid keeps passing and lets an IndexWriter in another process open and commit to the same index

Bug LFV-343 · Area: store / NativeFSLockFactory (write.lock: in-JVM LOCK_HELD guard and cross-process mutual exclusion) · Priority: low · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

In one JVM you hold an index's write.lock (an open IndexWriter on directory A) and then, in the same JVM, open the same lock file through another real path: a bind mount of A at a second path, or a hard-link copy of A's files that includes write.lock, opened with IndexWriter, CheckIndex or addIndexes(Directory...). You expect that second open to fail with LockObtainFailedException while the first writer keeps its lock, so that a writer in another process on A is refused, as the IndexWriter javadoc promises. Instead the second open gets past NativeFSLockFactory's in-JVM check (LOCK_HELD compares toRealPath strings, which differ), opens a second FileChannel, gets OverlappingFileLockException and closes that channel, which on Linux drops the process's lock on the file; the first lock still passes ensureValid, a writer in another process then opens A and commits, and the first writer's next delete-only commit replaces that commit (on main: 'expected:&lt;REFUSED&gt; but was:&lt;OBTAINED&gt;' and 'the other process's acknowledged commit (doc 'child') was replaced by this writer's commit').

## Expected vs actual

- Expected: You expect that second open to fail with LockObtainFailedException while the first writer keeps its lock, so that a writer in another process on A is refused, as the IndexWriter javadoc promises.
- Actual: The second open gets past NativeFSLockFactory's in-JVM check (LOCK_HELD compares toRealPath strings, which differ), opens a second FileChannel, gets OverlappingFileLockException and closes that channel, which on Linux drops the process's lock on the file; the first lock still passes ensureValid, a writer in another process then opens A and commits, and the first writer's next delete-only commit replaces that commit (on main: 'expected:&lt;REFUSED&gt; but was:&lt;OBTAINED&gt;' and 'the other process's acknowledged commit (doc 'child') was replaced by this writer's commit').

## Files

- [src/org/apache/lucene/store/TestNativeFSLockAliasRelease.java](src/org/apache/lucene/store/TestNativeFSLockAliasRelease.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestNativeFSLockAliasRelease
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/locks-pending-deletes/](../../models/locks-pending-deletes/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
