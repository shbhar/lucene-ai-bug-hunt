# Lucene103 block tree never sets Stats.indexNumBytes, so Terms.getStats and CheckIndex -verbose print 'index trie: 0 bytes' for every field

Bug LFV-162 · Area: codecs / block-tree terms dictionary (Lucene103 Stats, CheckIndex verbose statistics) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run CheckIndex -verbose at level 2 or more (or call Terms.getStats) on an index written with the default codec and expect each field's block-tree statistics to say how many bytes its terms index takes, as the field's javadoc 'Byte size of the index' and the printed label 'index trie: N bytes' promise. Since 10.3.0 the Lucene103 block tree never assigns Stats.indexNumBytes, so every field prints 'index trie: 0 bytes', even a 20,000-term field with 607 blocks in a segment whose.tip file is 2,473 bytes. Nothing else reads the number, so search and indexing are unaffected, and the older Lucene90 block tree had already stopped reporting the real size (a constant such as 56 bytes since 8.6.0), so this is a small diagnostic defect, not a regression of a working metric.

The test shows that the Lucene103 block tree never sets `Stats.indexNumBytes` ("Byte size of the index"): `Terms.getStats()` of a one-term `StringField` under the default codec reports 0 (`TestStatsIndexNumBytes#testIndexNumBytesIsSet`).

## Expected vs actual

Expected: the field's trie bytes in the.tip file, &gt; 0. Actual: `expected indexNumBytes > 0 but was 0`, the same with -ea and -da.

## Files

- [src/org/apache/lucene/codecs/lucene103/blocktree/TestStatsIndexNumBytes.java](src/org/apache/lucene/codecs/lucene103/blocktree/TestStatsIndexNumBytes.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene103.blocktree.TestStatsIndexNumBytes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
