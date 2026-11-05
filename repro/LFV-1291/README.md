# VerifyingLockFactory.obtainLock takes the inner lock first and then runs the acquire handshake with the lock verify server; when that handshake fails (the server is gone, replies with a wrong byte, or the write fails) obtainLock throws without releasing the inner lock and returns no Lock the caller could close, so the lock name stays held (every release since 5.3.0)

Finding LFV-1291 · Area: core / store: VerifyingLockFactory (the lock-verification wrapper that LockStressTest drives against a LockVerifyServer) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1291.html](../../bugs/LFV-1291.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a lock factory in VerifyingLockFactory (the wrapper LockStressTest uses to check a lock implementation against a LockVerifyServer) and call obtainLock while the connection to the verify server is broken, for example because the server has closed it or answers with an unexpected byte. You expect obtainLock either to return a Lock you can close or to throw and leave the lock free, as LockFactory.obtainLock's contract and the LUCENE-6508 CHANGES entry ('returns a Lock that can be released (or fails with exception)') say. Instead it obtains the inner lock, then throws from the handshake in the CheckedLock constructor without closing that lock and without returning anything to close, so the same name can no longer be obtained: SingleInstanceLockFactory says 'lock instance already obtained', NativeFSLockFactory says 'Lock held by this virtual machine', and a SimpleFS lock file is left on disk.

## Expected vs actual

- Expected: After `obtainLock` throws, `inner.obtainLock(dir, "test.lock")` succeeds: the lock is free again.
- Actual: `LockObtainFailedException: lock instance already obtained: (... lockName=test.lock)` from `SingleInstanceLockFactory.java:39`.

## Files

- [src/org/apache/lucene/store/TestVerifyingLockFailedHandshake.java](src/org/apache/lucene/store/TestVerifyingLockFailedHandshake.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestVerifyingLockFailedHandshake
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestVerifyingLockFailedHandshake.java](TestVerifyingLockFailedHandshake.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
