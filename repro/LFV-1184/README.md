# MultiLevelSkipListWriter.bufferSkip throws ArrayIndexOutOfBoundsException for a one-level skip writer (maxSkipLevels = 1) at the first df that is a multiple of skipInterval * skipMultiplier (regression since 9.9.0, #12653)

Finding LFV-1184 · Area: core / codecs: MultiLevelSkipListWriter (multi-level skip lists for postings; @lucene.experimental) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1184.html](../../bugs/LFV-1184.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a postings format whose skip writer extends MultiLevelSkipListWriter with maxSkipLevels = 1 (a single-level skip list) and call bufferSkip every skipInterval documents. You expect every entry to go to level 0, because the writer reports numberOfSkipLevels = 1 and allocates one level buffer, and 9.8.0 does exactly that. Instead, at the first df that is a multiple of skipInterval * skipMultiplier, a shortcut added in 9.9.0 (#12653) raises the level count to 2 without checking that cap, and bufferSkip throws ArrayIndexOutOfBoundsException 'Index 1 out of bounds for length 1'; Lucene's own postings formats use 4 or 10 levels and are not affected.

## Expected vs actual

- Expected: Both entries go to level 0: `writeSkipData` sees levels [0, 0], as on 9.8.0.
- Actual: `bufferSkip(8)` throws `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` at `MultiLevelSkipListWriter.java:147`, assertions on and off.

## Files

- [src/org/apache/lucene/codecs/TestSingleLevelSkipWriter.java](src/org/apache/lucene/codecs/TestSingleLevelSkipWriter.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.TestSingleLevelSkipWriter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSingleLevelSkipWriter.java](TestSingleLevelSkipWriter.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
