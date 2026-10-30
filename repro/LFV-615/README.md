# IndexUpgrader's command line and default constructors use a bare IndexWriterConfig, so upgrading a sorted index silently and permanently removes its index sort (the application's sorted IndexWriter then refuses to open the index), and an index with a parent field or a soft-deletes field cannot be upgraded at all

Bug LFV-615 · Area: core index / IndexUpgrader (command line and default-config upgrade path); IndexWriter index-sort validation · Priority: medium · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's documented upgrade tool (java... org.apache.lucene.index.IndexUpgrader indexDir, or new IndexUpgrader(dir).upgrade) on an index that has an index sort, for example Lucene's own backward-compatibility fixture sorted.9.10.0.zip, and expect only the segment format to change. Instead the tool rewrites the old segments with a writer whose config has no sort, so the new segment records indexSort=null: all 50 documents survive, but the sort is gone for good, and the application's IndexWriter configured with the original sort, which opened the index before, now throws 'cannot change previous indexSort=null... to new indexSort=&lt;long: "dateDV"&gt;!'. On an index with a parent field or a soft-deletes field, the same default tool throws IllegalArgumentException and cannot upgrade the index at all.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The tool rewrites the old segments with a writer whose config has no sort, so the new segment records indexSort=null: all 50 documents survive, but the sort is gone for good, and the application's IndexWriter configured with the original sort, which opened the index before, now throws 'cannot change previous indexSort=null... to new indexSort=&lt;long: "dateDV"&gt;!'.

## Files

- [src/org/apache/lucene/backward_index/TestIndexUpgraderKeepsIndexSort.java](src/org/apache/lucene/backward_index/TestIndexUpgraderKeepsIndexSort.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- Fixtures: the test reads sorted.9.10.0.zip and sorted.10.5.2.zip, test resources of Lucene's backward-codecs module (lucene/backward-codecs/src/test/org/apache/lucene/backward_index/ in the checkout), so the run puts $LUCENE/lucene/backward-codecs/src/test on the class path; without them every test is skipped.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$LUCENE/lucene/backward-codecs/src/test:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_index.TestIndexUpgraderKeepsIndexSort
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
