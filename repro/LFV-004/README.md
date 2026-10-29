# Merge instances re-open segment files by name, so merging an old reader after a name reuse can copy another segment's data

Bug LFV-004 · Area: codecs / merge instances · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-004.html](../../bugs/LFV-004.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep an old NRT reader open, roll back writers until a later writer reuses its segment name, and then merge that reader, for example with addIndexes(CodecReader...). You expect the merge to copy the old reader's own stored fields and vectors. Instead its merge instance opens the file again by name and copies the newer segment's data, silently (CheckIndex on the target is clean) or, for compound files of another layout, with a loud exception.

The test shows that addIndexes(CodecReader) of a stale NRT reader on MMapDirectory with ADVISE_BY_CONTEXT copies the stored fields of a later segment that reused the name _0, because the merge instance re-opens the file by name.

## Expected vs actual

Expected: the copy has the reader's own id "old". Actual: org.junit.ComparisonFailure: expected:&lt;[old]&gt; but was:&lt;[new]&gt; (main 645d4fe, -ea and -da). 10.5.2 passes.

## Files

- [src/org/apache/lucene/index/TestMergeInstanceReopenByName.java](src/org/apache/lucene/index/TestMergeInstanceReopenByName.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestMergeInstanceReopenByName
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
