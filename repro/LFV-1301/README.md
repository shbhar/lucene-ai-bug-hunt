# LockVerifyServer: when its 30-second accept timeout fires after some but not all clients have connected, run() throws SocketTimeoutException but the threads it started for the connected clients wait forever on the start latch, so the standalone server process never exits (every release since 4.8.0)

Finding LFV-1301 · Area: core / store: LockVerifyServer (the standalone lock-verification server that LockStressTest clients and VerifyingLockFactory talk to) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1301.html](../../bugs/LFV-1301.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You start LockVerifyServer for two clients (the documented way to test a lock factory with LockStressTest), one client connects and the other never starts. You expect the server to end once its 30-second accept timeout fires, which is why the timeout exists: the code comment says 'give clients at most 30 secs to connect and send bytes', and a maintainer kept it in #12535 so 'the server ends if one of the clients does not start'. Instead run() throws SocketTimeoutException, but the thread it already started for the connected client stays parked forever on the start latch, which is counted down only after every client has connected, so the server JVM never exits and has to be killed.

## Expected vs actual

- Expected: `run()` throws `SocketTimeoutException` after 30 s and the server closes the connected client, so its read returns -1 (as with the fix).
- Actual: `SocketTimeoutException: Read timed out`: the server never lets go, and a `ThreadLeakError` shows its thread parked in `CountDownLatch.await` at `LockVerifyServer.java:73`.

## Files

- [src/org/apache/lucene/store/TestLockVerifyServerAcceptTimeout.java](src/org/apache/lucene/store/TestLockVerifyServerAcceptTimeout.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestLockVerifyServerAcceptTimeout
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLockVerifyServerAcceptTimeout.java](TestLockVerifyServerAcceptTimeout.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
