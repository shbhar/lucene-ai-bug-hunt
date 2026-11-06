# Block tree Stats.toString prints the compressed suffix bytes per block as the average on the 'term suffix bytes before compression' line (Terms.getStats(), CheckIndex -verbose)

Finding LFV-1228 · Area: codecs / block-tree terms dictionary (Lucene103 and backward-codecs Lucene90 Stats, CheckIndex verbose statistics) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1228.html](../../bugs/LFV-1228.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Terms.getStats() (or run CheckIndex -verbose at -level 2 or more) on a default-codec index and read the block tree line 'N term suffix bytes before compression (A suffix-bytes/block)', expecting A to be N divided by the block count, as every other average in that output divides its own line's total. Since 8.5.0, A is the compressed (on-disk) suffix bytes divided by the block count, the figure that belongs to the next line: a 2,000-term field with 80 blocks prints '34376 term suffix bytes before compression (128.9 suffix-bytes/block)', where 34376 / 80 = 429.7 and 128.9 = 10314 compressed bytes / 80. Only this one diagnostic figure is wrong; the totals, the compression ratio, the public Stats fields, indexing and search are unaffected.

## Expected vs actual

- Expected: `30 term suffix bytes before compression (30.0 suffix-bytes/block)`: one block, so the average is the total.
- Actual: `30 term suffix bytes before compression (24.0 suffix-bytes/block)`, next to `24 compressed term suffix bytes`; assertions on and off.

## Files

- [src/org/apache/lucene/codecs/lucene103/blocktree/TestStatsSuffixBytesPerBlock.java](src/org/apache/lucene/codecs/lucene103/blocktree/TestStatsSuffixBytesPerBlock.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.codecs.lucene103.blocktree.TestStatsSuffixBytesPerBlock
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestStatsSuffixBytesPerBlock.java](TestStatsSuffixBytesPerBlock.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
