# Closing a lock from VerifyingLockFactory a second time throws AlreadyClosedException 'Lock instance already released' instead of doing nothing, because CheckedLock.close() keeps no closed state and re-runs ensureValid() on the inner lock it already released, although Lock is Closeable and every other core Lock ignores a second close (every release since 5.3.0)

Finding LFV-1293 · Area: core / store: VerifyingLockFactory (CheckedLock.close, the lock wrapper used by LockStressTest) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1293.html](../../bugs/LFV-1293.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a lock factory in VerifyingLockFactory (the wrapper LockFactory's javadoc recommends for testing a lock implementation), obtain a lock and close it twice. You expect the second close() to do nothing, as Closeable promises, as Lock.close() inherits through {@inheritDoc}, and as every other core Lock does. Instead the second close() calls ensureValid() on the inner lock that the first close already released and throws AlreadyClosedException 'Lock instance already released'.

## Expected vs actual

- Expected: The second `lock.close()` returns normally and does nothing.
- Actual: `AlreadyClosedException: Lock instance already released: ...SingleInstanceLock@...: test.lock` from `CheckedLock.close` (`VerifyingLockFactory.java:57`).

## Files

- [src/org/apache/lucene/store/TestVerifyingLockDoubleClose.java](src/org/apache/lucene/store/TestVerifyingLockDoubleClose.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestVerifyingLockDoubleClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestVerifyingLockDoubleClose.java](TestVerifyingLockDoubleClose.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
