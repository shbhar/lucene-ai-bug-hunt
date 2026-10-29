# MappingCharFilter.reset rewinds the input but keeps the offset corrections recorded on the first pass, so after a reset and re-read correctOffset (and the token offsets a Tokenizer derives from it) are wrong, or BaseCharFilter's ordering assertion fires

Bug LFV-338 · Area: analysis / char filters (MappingCharFilter, BaseCharFilter offset correction) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read a MappingCharFilter to the end, call reset to rewind it (MappingCharFilter overrides reset for this, and upstream TestMappingCharFilter.testReaderReset re-reads after it), read it again and expect correctOffset to map the second pass's output back to the input exactly as on the first pass. Instead reset rewinds the input and the filter's read state but not the offset-correction map it inherits from BaseCharFilter, so the second pass appends new corrections computed from the first pass's last cumulative diff: for the map a-&gt;xy over "a", correctOffset(2) returns 0 instead of 1, and a WhitespaceTokenizer over the re-read filter for "a b" gives xy[0,0] b[1,2] instead of xy[0,1] b[2,3]. For deleting or shrinking maps, with -ea the second read trips BaseCharFilter's 'Offset... is less than the last recorded offset' assertion, and with -da it returns offsets past the end of the input (b-&gt;'' over "ba": correctOffset(1) = 3 for a 2-character input).

The test shows that `MappingCharFilter.reset()` keeps the offset corrections from the first read, so `correctOffset()` is wrong after a reset and a second read (map `a`-&gt;`xy` over `"a"`).

## Expected vs actual

Expected `correctOffset(2)` = 1 (end of the input) after the re-read; actual: 0 (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/charfilter/TestMappingCharFilterResetOffsets.java](src/org/apache/lucene/analysis/charfilter/TestMappingCharFilterResetOffsets.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.charfilter.TestMappingCharFilterResetOffsets
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
