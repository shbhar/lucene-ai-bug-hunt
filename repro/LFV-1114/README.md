# ParallelCompositeReader picks a field's owner separately in each segment, so when two parallel indexes share a field, the later index's values show through in segments where the earlier index has no document with that field, and hits change after a merge

Finding LFV-1114 · Area: core / index (ParallelCompositeReader.prepareLeafReaders: per-leaf field ownership through ParallelLeafReader's 'first reader having a given field wins' rule) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1114.html](../../bugs/LFV-1114.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a ParallelCompositeReader over two indexes that hold the same 3 documents in the same two segments ([0, 1] and [2]); the first index has field t only on doc 0 (t=new), the second has t=old on every document, and you search t:old. You expect no hits, because the javadoc says matches come from the first index added that has the field (and docs 1 and 2 are identical in both indexes, so they must at least be treated alike). You get doc 2 but not doc 1, with no error: the reader chooses each field's owner separately in every segment, and segment [2] of the first index has no document with t, so the second index's t shows through there; after forceMerge(1) of both indexes the same search returns nothing.

## Expected vs actual

- Expected: No hits: the first index owns `t` and has no `t=old`.
- Actual: `java.lang.AssertionError: expected:<[]> but was:<[2]>`: doc 2 matches, the identical doc 1 does not.

## Files

- [src/org/apache/lucene/index/TestParallelCompositeReaderFieldOwner.java](src/org/apache/lucene/index/TestParallelCompositeReaderFieldOwner.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
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
     execute --select-class org.apache.lucene.index.TestParallelCompositeReaderFieldOwner
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestParallelCompositeReaderFieldOwner.java](TestParallelCompositeReaderFieldOwner.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
