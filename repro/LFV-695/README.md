# Since 10.0.0 IndexRearranger keeps only the analyzer from the IndexWriterConfig it is given, so the rearranged index loses the configured codec and index sort, OpenMode.CREATE is ignored, and an index with a soft-deletes or parent field cannot be rearranged

Bug LFV-695 · Area: misc / index tools (IndexRearranger.execute: writer configs of the rearrange pass and the apply-deletes pass) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You rearrange an index with IndexRearranger and pass an IndexWriterConfig that sets, for example, a codec, an index sort, OpenMode.CREATE or a soft-deletes field, and expect the rearranged index to be written and opened with those settings, as it was from 8.9.0 through 9.12.3. Instead, since 10.0.0 execute builds both of its writers with new IndexWriterConfig(config.getAnalyzer) and sets only NoMergePolicy, so the output silently uses the default codec and has no index sort, a second run with OpenMode.CREATE appends and fails with IllegalStateException ('Document selector... has matched more than 1 segments'), and an input with a soft-deletes or parent field fails with IllegalArgumentException ('soft-deletes field is not configured in IWC', 'this IndexWriter has no parent document field configured').

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Since 10.0.0 execute builds both of its writers with new IndexWriterConfig(config.getAnalyzer) and sets only NoMergePolicy, so the output silently uses the default codec and has no index sort, a second run with OpenMode.CREATE appends and fails with IllegalStateException ('Document selector... has matched more than 1 segments'), and an input with a soft-deletes or parent field fails with IllegalArgumentException ('soft-deletes field is not configured in IWC', 'this IndexWriter has no parent document field configured').

## Files

- [src/org/apache/lucene/misc/index/TestIndexRearrangerIgnoresWriterConfig.java](src/org/apache/lucene/misc/index/TestIndexRearrangerIgnoresWriterConfig.java) (JUnit test)
- [src/org/apache/lucene/misc/index/TestIndexRearrangerIgnoresWriterConfigOnFailurePath.java](src/org/apache/lucene/misc/index/TestIndexRearrangerIgnoresWriterConfigOnFailurePath.java) (JUnit test)
- [src/org/apache/lucene/misc/index/TestIndexRearrangerWriterConfig9x.java](src/org/apache/lucene/misc/index/TestIndexRearrangerWriterConfig9x.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestIndexRearrangerIgnoresWriterConfig

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestIndexRearrangerIgnoresWriterConfigOnFailurePath \
       --select-class org.apache.lucene.misc.index.TestIndexRearrangerWriterConfig9x
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
