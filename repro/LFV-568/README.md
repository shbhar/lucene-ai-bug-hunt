# After IndexWriter.getConfig.setMergePolicy(...) on a live writer, a merge that the previous policy registered but that has not started yet takes the new policy's compound-file decision, although the setMergePolicy javadoc says merges already registered by the previous policy are not affected

Bug LFV-568 · Area: index / IndexWriter merge execution (live merge-policy change, compound-file decision) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.getConfig.setMergePolicy(newPolicy) while a merge that the old policy selected is registered but has not started, and expect what the javadoc says: 'Any merges in flight or any merges already registered by the previous MergePolicy are not affected.' Instead IndexWriter.merge reads config.getMergePolicy only when the merge starts running and uses that policy for the merged segment's compound-file decision, so the queued merge is written the way the new policy would write it. In the repro a 66,000-document merge registered by LogDocMergePolicy becomes a compound file after a switch to LogByteSizeMergePolicy (without the switch it does not), and on main a switch from the default TieredMergePolicy to NoMergePolicy turns a queued merge's compound segment into a non-compound one.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: IndexWriter.merge reads config.getMergePolicy only when the merge starts running and uses that policy for the merged segment's compound-file decision, so the queued merge is written the way the new policy would write it.

## Files

- [src/org/apache/lucene/index/TestLiveMergePolicyChangeNoCFSRatio.java](src/org/apache/lucene/index/TestLiveMergePolicyChangeNoCFSRatio.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestLiveMergePolicyChangeNoCFSRatio
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
