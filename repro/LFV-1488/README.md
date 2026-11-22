# CheckIndex's index-sort check calls nextDoc() on the parent-field iterator again after it returned NO_MORE_DOCS when an index-sorted segment with document blocks holds no parent document, so with SimpleTextCodec it reports a segment that the default codec reports clean as broken ('Index Sort test failed'); with the default codec nothing is visible (every release since 9.10.0)

Finding LFV-1488 · Area: index / CheckIndex (testSort, the index-sort check of segments with document blocks); witness codec: codecs / SimpleText doc values · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1488.html](../../bugs/LFV-1488.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write an index with SimpleTextCodec, an index sort and a parent field, add one block (a child and its parent), delete the parent by a term that only the parent carries, force-merge and run CheckIndex with integrity checks (level 2). You expect the index to be reported clean, as CheckIndex reports it when the same steps run with the default codec. Instead CheckIndex prints 'test: index sort..........ERROR [Index -2 out of bounds for length 1]' and counts one broken segment: the merged segment holds no parent document, so the check's first nextDoc() on the parent field already returns NO_MORE_DOCS, its loop calls nextDoc() once more, which the iterator's javadoc tells callers not to do, and SimpleText's iterator answers that call with a read at a negative file position.

## Expected vs actual

- Expected: `CheckIndex` reports the index clean (`test: index sort..........OK`), as it does for the same steps with the default codec.
- Actual: `test: index sort..........ERROR [Index -2 out of bounds for length 1]`, 1 broken segment, `Status.clean` is false.

## Files

- [src/org/apache/lucene/index/TestCheckIndexSortedSegmentWithoutParent.java](src/org/apache/lucene/index/TestCheckIndexSortedSegmentWithoutParent.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it writes one block (an empty child document and its parent) with SimpleTextCodec, an index sort and a parent field, deletes the parent by its id term, force-merges to one segment and runs CheckIndex at Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS (level 2). It asserts that the status CheckIndex returns is clean, and it fails with CheckIndex's whole report as the message; the line 'test: index sort..........ERROR [Index -2 out of bounds for length 1]' in that report is the finding. SimpleTextCodec is in Lucene's codecs module, so the test needs the lucene-codecs jar on the class path, as in the recipe below. The control with the default codec is not part of this test.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexSortedSegmentWithoutParent
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestCheckIndexSortedSegmentWithoutParent.java](TestCheckIndexSortedSegmentWithoutParent.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
