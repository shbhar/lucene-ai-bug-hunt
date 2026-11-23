# If IndexWriter's InfoStream throws when the constructor's failure handler logs its message ('init: hit exception on init; releasing write lock'), the failed new IndexWriter leaves the directory's write lock held and the caller has no writer to close: the handler logs before it releases the lock (run with one throw at that message on an open that fails by itself on the 10.6.0, 10.5.2, 9.12.3 and 9.1.0 releases, on branch_11x and on main, and with a main() of the same shape on nine older releases from 2.9.3 and 3.0.2 on; needs an InfoStream that throws at that message)

Finding LFV-1559 · Area: index / IndexWriter constructor (the failure handler: the log statement in front of the release of the write lock) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You turn on IndexWriter's InfoStream (its diagnostic log, off by default) and open a writer in a way that fails, for example with OpenMode.APPEND on a directory that holds no index, and the log call throws an exception at the message the constructor logs about that failure ('init: hit exception on init; releasing write lock'). You expect the failed constructor to have released the directory's write lock, as Lucene's change notes state since LUCENE-2476 ('If any exception is hit init'ing IW, release the write lock') and as that message itself says; the constructor's javadoc is silent on it. Instead the lock stays held and there is no writer object to close: obtaining write.lock, or opening another IndexWriter on that directory in the same JVM, fails with LockObtainFailedException, and the caller gets the log call's exception instead of the open's own (run on 10.6.0, branch_11x and main); in the run on an existing index its files were unchanged and its document was readable.

## Expected vs actual

- Expected: You expect the failed constructor to have released the directory's write lock, as Lucene's change notes state since LUCENE-2476 ('If any exception is hit init'ing IW, release the write lock') and as that message itself says; the constructor's javadoc is silent on it.
- Actual: The lock stays held and there is no writer object to close: obtaining write.lock, or opening another IndexWriter on that directory in the same JVM, fails with LockObtainFailedException, and the caller gets the log call's exception instead of the open's own (run on 10.6.0, branch_11x and main); in the run on an existing index its files were unchanged and its document was readable.

## Files

- [src/org/apache/lucene/index/TestFailedOpenReleasesWriteLock.java](src/org/apache/lucene/index/TestFailedOpenReleasesWriteLock.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test and 1 failure, org.apache.lucene.store.LockObtainFailedException for write.lock, thrown by the test's obtainLock call after the constructor has thrown.
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

# run 1: assertions on, seed 5EED0898
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0898 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFailedOpenReleasesWriteLock
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
