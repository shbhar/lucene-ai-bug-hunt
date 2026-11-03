# SleepingLockWrapper's constructor accepts pollInterval 0 (it rejects only negative values: 'pollInterval must be a non-negative number'), and then every obtainLock on the wrapper, including the write-lock acquisition in IndexWriter's constructor, throws ArithmeticException '/ by zero' from 'lockWaitTimeout / pollInterval' instead of the constructor rejecting 0 up front (since 5.3.0)

Finding LFV-1191 · Area: core / store: SleepingLockWrapper (constructor pollInterval check, obtainLock) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1191.html](../../bugs/LFV-1191.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a directory in new SleepingLockWrapper(dir, 1000, 0), with a pollInterval the constructor accepts (it rejects only negative values, with the message 'pollInterval must be a non-negative number'), and ask it for a lock that nobody holds. You expect either that lock or an IllegalArgumentException from the constructor. Instead every obtainLock, including the one in IndexWriter's constructor, throws java.lang.ArithmeticException '/ by zero' from 'lockWaitTimeout / pollInterval' before the wrapped directory is even asked for the lock.

## Expected vs actual

- Expected: The constructor rejects `pollInterval` 0 with an `IllegalArgumentException` (the recommended fix), or else the free lock is returned.
- Actual: `expected IllegalArgumentException but got java.lang.ArithmeticException: / by zero` at `SleepingLockWrapper.java:81`, with assertions on and off.

## Files

- [src/org/apache/lucene/store/TestSleepingLockWrapperZeroPoll.java](src/org/apache/lucene/store/TestSleepingLockWrapperZeroPoll.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestSleepingLockWrapperZeroPoll
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSleepingLockWrapperZeroPoll.java](TestSleepingLockWrapperZeroPoll.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
