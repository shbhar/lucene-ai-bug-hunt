# MergePolicy.isMerged checks a segment read from disk against the codec instance it was loaded with (the shared Codec.forName instance, with default compound-file settings) instead of the writer's codec, so forceMerge(1) with a LogMergePolicy rewrites an already merged segment after every writer reopen

Bug LFV-572 · Area: index / merge policy compound-file check (MergePolicy.isMerged vs CompoundFormat settings) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give IndexWriter its own codec instance, for example new Lucene104Codec(Mode.BEST_COMPRESSION), turn compound files off on it as MIGRATE.md says (iwc.getCodec.compoundFormat.setShouldUseCompoundFile(false)), use a LogByteSizeMergePolicy and force-merge the index to one segment. You expect a later forceMerge(1) to leave that already merged segment alone, as it does in the same writer session and as 10.x does with the equivalent merge-policy settings. Instead, after the writer is closed and reopened, MergePolicy.isMerged asks the codec instance the segment was read with (the shared Codec.forName instance, whose settings are the defaults), decides the non-compound segment should have been compound, and forceMerge(1) rewrites it (_3 becomes _4); the merge itself uses the writer's codec and writes a non-compound segment again, so the same rewrite happens in every later writer session.

The test shows that with its own codec instance configured for no compound files (MIGRATE.md) and a LogByteSizeMergePolicy, `forceMerge(1)` after a writer reopen rewrites an already merged segment, because `MergePolicy.isMerged` asks the codec instance the segment was read with (the shared `Codec.forName` one, default settings).

## Expected vs actual

Expected the merged segment `_2` to be kept; actual it is rewritten into `_3` (`segment after forceMerge(1) expected:<_2> but was:<_3>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestForceMergeKeepsMergedSegmentAfterReopen.java](src/org/apache/lucene/index/TestForceMergeKeepsMergedSegmentAfterReopen.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestForceMergeKeepsMergedSegmentAfterReopen
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
