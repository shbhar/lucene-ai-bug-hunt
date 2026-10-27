# FSDirectory: a pending-delete retry running on one thread can delete a file that another thread has just re-created (createOutput) or renamed onto the same name, so a file that was written and closed successfully silently disappears

Bug LFV-331 · Area: store / FSDirectory pending deletes (delete retry vs. the createOutput/rename revive, thread safety) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

On a file system that refuses to delete open files (Windows, or a virus scanner), you delete a file that a reader still holds open, so FSDirectory keeps it as a pending delete; after the reader closes, one thread re-creates that name with createOutput (or renames another file onto it), writes and closes it, while another thread makes any mutating call on the same directory, for example sync of an unrelated file. You expect the new file to stay, like any file that was created, closed and never deleted. Instead the other thread's pending-delete retry, which copied the pending set before the revive, deletes the new file: listAll no longer lists it and openInput fails (on main under the test framework's WindowsFS: 'x was created and closed successfully but is gone: [other]').

## Expected vs actual

- Expected: You expect the new file to stay, like any file that was created, closed and never deleted.
- Actual: The other thread's pending-delete retry, which copied the pending set before the revive, deletes the new file: listAll no longer lists it and openInput fails (on main under the test framework's WindowsFS: 'x was created and closed successfully but is gone: [other]').

## Files

- [src/org/apache/lucene/store/TestFSDirectoryPendingDeleteRetryRace.java](src/org/apache/lucene/store/TestFSDirectoryPendingDeleteRetryRace.java) (JUnit test)

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
     execute --select-class org.apache.lucene.store.TestFSDirectoryPendingDeleteRetryRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
