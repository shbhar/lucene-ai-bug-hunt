# BPIndexReorderer assumes every segment ends with a parent document: once a segment ends with child documents that have no parent after them, each BP reordering merge in which that segment comes last throws and closes the IndexWriter

Bug LFV-417 · Area: misc / BP merge-time reordering (BPIndexReorderer with document blocks) · Priority: medium · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-417.html](../../bugs/LFV-417.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index document blocks with IndexWriterConfig#setParentField, merge through BPReorderingMergePolicy with a BPIndexReorderer, and a segment ends up ending with child documents that have no parent after them, for example because you deleted or updated only the parent of its last block and a merge dropped it, or because IndexWriter rejected a block at one of its children (with the documented IllegalArgumentException) just before a flush. You expect later merges to work: IndexWriter accepted every call, CheckIndex calls the index clean and a plain merge of the same segment succeeds. Instead BPIndexReorderer, which assumes the merged view ends with a parent, runs past the end of its arrays in the next reordering merge in which that segment comes last (ArrayIndexOutOfBoundsException with assertions off, AssertionError with them on), and IndexWriter closes with a tragic exception, loses uncommitted changes and fails the same way after every reopen.

The test shows that with setParentField and BPReorderingMergePolicy(BPIndexReorderer), a block that IndexWriter rejects at a child (immense term) as the last block of a segment makes the next forceMerge(1) fail.

## Expected vs actual

Expected: forceMerge(1) succeeds; actual: it throws (AssertionError at BPIndexReorderer.java:329 with -ea, ArrayIndexOutOfBoundsException "Index 65 out of bounds for length 65" at:377 with -da) and closes the writer.

## Files

- [src/org/apache/lucene/misc/index/TestBPReorderAfterRejectedChild.java](src/org/apache/lucene/misc/index/TestBPReorderAfterRejectedChild.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 41700001
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=41700001 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBPReorderAfterRejectedChild
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
