# ByteBuffersDirectory.rename ends with a leftover unconditional files.remove(source), so a file that another thread creates (createOutput) or renames onto the source name while the rename runs is silently deleted, although that thread's call returned normally

Bug LFV-836 · Area: store / ByteBuffersDirectory (rename vs. a concurrent createOutput or rename onto the source name) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You share one ByteBuffersDirectory between two threads: one renames file 'a' to a new name while the other creates a new 'a' (createOutput, write, close) or renames a temp file onto 'a', and you expect the new 'a' to stay until someone deletes or renames it, as it does on FSDirectory. Instead, now and then the new file vanishes: rename has already moved its own entry with the checked files.remove(source, file) and then runs a leftover unconditional files.remove(source), which can only remove a different entry, the one the other thread has just put under 'a'. Neither call reports an error; afterwards listAll lacks the file and openInput throws NoSuchFileException (stress tests lose between 1 and a few hundred files per 100,000 rounds on every run).

## Expected vs actual

- Expected: You expect the new 'a' to stay until someone deletes or renames it, as it does on FSDirectory.
- Actual: Now and then the new file vanishes: rename has already moved its own entry with the checked files.remove(source, file) and then runs a leftover unconditional files.remove(source), which can only remove a different entry, the one the other thread has just put under 'a'.

## Files

- [src/org/apache/lucene/store/TestByteBuffersDirectoryRenameRace.java](src/org/apache/lucene/store/TestByteBuffersDirectoryRenameRace.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.store.TestByteBuffersDirectoryRenameRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/bytebuffers-output-block-expansion-recycler-and-view-aliasing/](../../models/bytebuffers-output-block-expansion-recycler-and-view-aliasing/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
