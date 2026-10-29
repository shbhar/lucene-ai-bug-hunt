# MultiDocValues numeric, binary and norms views throw NullPointerException from advance after advanceExact landed on a segment without the field

Bug LFV-136 · Area: index / MultiDocValues merged numeric, binary and norms doc-values views (also the merged reader passed to MergePolicy.OneMerge#reorder) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read a merged doc-values view over an index with several segments (MultiDocValues.getNumericValues, getBinaryValues or getNormValues, or the merged reader that IndexWriter hands to a custom OneMerge#reorder), call advanceExact(0), which returns false because doc 0's segment has no values for the field, and then call advance(1). You expect advance(1) to return the next document that has a value (doc 2 in the repro), as the sorted-numeric view of the same index and the per-segment codec iterators do. Instead it throws NullPointerException ('this.currentValues is null'): advanceExact stored a null per-segment iterator for the segment without the field, and advance assumes that iterator is non-null as long as the target stays in the same segment.

The test shows that on the MultiDocValues numeric view over 2 segments, advance(1) right after advanceExact(0) returned false (doc 0's segment has no values for the field) throws NullPointerException.

## Expected vs actual

Expected: advance(1) returns 2, the next doc with a value. Actual (-ea and -da): NullPointerException ("this.currentValues" is null), reported as an AssertionError.

## Files

- [src/org/apache/lucene/index/TestMultiDocValuesAdvanceAfterAdvanceExact.java](src/org/apache/lucene/index/TestMultiDocValuesAdvanceAfterAdvanceExact.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestMultiDocValuesAdvanceAfterAdvanceExact
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
