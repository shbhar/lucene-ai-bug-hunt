# ByteBuffersDirectory.rename throws IllegalStateException without removing the dest it has already published, so two concurrent renames of one source leave the file listed under both new names

Finding LFV-1158 · Area: store / ByteBuffersDirectory (rename failure path: two concurrent renames of one source) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1158.html](../../bugs/LFV-1158.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Two threads call rename on one ByteBuffersDirectory with the same source and different destinations, rename('a', 'x') and rename('a', 'y'); you expect one call to win and the other to fail and leave nothing behind, which is what happens when the calls run one after the other (the second throws NoSuchFileException) and on FSDirectory. Instead both calls first publish the file under their own dest, then only one of them can remove 'a', and the loser throws IllegalStateException('File was unexpectedly replaced: a') without withdrawing its dest. Afterwards listAll() shows the same file as both 'x' and 'y', permanently, although one of the two renames reported failure.

## Expected vs actual

- Expected: `rename("a", "y")` throws and leaves nothing behind: `listAll()` is `[x]`.
- Actual: It throws, but `y` stays bound to the file: `listAll()` is `[x, y]`.

## Files

- [src/org/apache/lucene/store/TestByteBuffersDirectoryFailedRenameLeavesDest.java](src/org/apache/lucene/store/TestByteBuffersDirectoryFailedRenameLeavesDest.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- This repro reads private Lucene state by reflection (setAccessible), so it depends on internals that may change.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestByteBuffersDirectoryFailedRenameLeavesDest
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestByteBuffersDirectoryFailedRenameLeavesDest.java](TestByteBuffersDirectoryFailedRenameLeavesDest.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
