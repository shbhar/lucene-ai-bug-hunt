# On Lucene 9.8.0 to 9.12.3, a kNN query without a filter on a segment written by Lucene 9.1.0 whose vector field some documents lack returns each hit's graph ordinal as its document id, so a hit behind the first document without a vector is returned as another document (in the test, a document that has no vector) or, when that document is deleted, not at all; released 9.x line only: every 10.x release, branch_11x and main are right

Finding LFV-1507 · Area: backward-codecs / Lucene91 HNSW vectors reader (kNN search on segments written by Lucene 9.1.0) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1507.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1507.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

Fixed upstream: [#13779](https://github.com/apache/lucene/pull/13779) (commit [6053e1e31378](https://github.com/apache/lucene/commit/6053e1e31378378f6d310a05ea6d7dcdfc45f48b)), merged 2024-09-28; also on branch_11x (the same commit) and branch_10_6 (the same commit). On the released 9.x line only (9.8.0 to 9.12.3): main already had the fix when the finding was listed.

## What it shows

You open an index that still holds a segment written by Lucene 9.1.0 in which only some documents have a vector (in the test: two documents, only document 1 has one) and run KnnFloatVectorQuery for that vector with k = 1. You expect document 1, the only document that has a vector. Instead, on Lucene 9.8.0 to 9.12.3 the query returns document 0, which has no vector, with the score of the right vector and no error: the reader of that old format reports the vector's position in the list of vectors as the document number, so with another layout a hit can also be a document that holds a different vector. Lucene 9.7.0 and earlier, every 10.x release, the 11.0 branch and main return document 1. It is the same kind of defect as the known one of apache/lucene#13910, in another reader: #13910 describes the Lucene90 reader only (segments written by 9.0.0; LFV-1505 on this site), and no upstream report of this one, in the Lucene91 reader (segments written by 9.1.0), was found.

## Expected vs actual

- Expected: The hit is document 1, the only document that has a vector (as on 9.7.0, 10.6.0, `branch_11x` and main).
- Actual: On 9.8.0 and 9.12.3 the hit is document 0, which has no vector: `java.lang.AssertionError: expected:<1> but was:<0>` (line 44).

## Files

- [src/UPSTREAM-TEST-WRITER-ORIGIN.txt](src/UPSTREAM-TEST-WRITER-ORIGIN.txt)
- [src/org/apache/lucene/backward_codecs/lucene91/TestLucene91SparseVectorFieldKnn.java](src/org/apache/lucene/backward_codecs/lucene91/TestLucene91SparseVectorFieldKnn.java) (JUnit test)

## Run it

- Tested on Lucene 9.12.3 (f965e930673c) (a commit of 2025-09-24): reproduced, with assertions on and off, on the 9.12.3 release jars, which are built from this commit (the tag releases/lucene/9.12.3).
- This test fails only on the released 9.x line, 9.8.0 to 9.12.3. It passes on every 10.x release (each was run without assertions, 10.0.0 and 10.6.0 also with them) and, with and without assertions, on Lucene main 781025d6fe32 (2026-11-13) and on branch_11x daa54f2e0cd3; a build of the current main or of the 11.0 branch is therefore the wrong target. The repro is one file, TestLucene91SparseVectorFieldKnn.java. To write its segment it needs six classes of Lucene's own test sources, its test-side writer of the Lucene 9.1 format, which no release jar contains and which this folder does not ship. First, before compiling, copy these six files unchanged from a Lucene source tree at the tag releases/lucene/9.12.3 (the tags of 9.11.0 to 9.12.2 hold the same six files) into this folder: Lucene90RWPostingsFormat.java and Lucene90RWSegmentInfoFormat.java from lucene/backward-codecs/src/test/org/apache/lucene/backward_codecs/lucene90/ into src/org/apache/lucene/backward_codecs/lucene90/, and Lucene91HnswGraphBuilder.java, Lucene91HnswVectorsWriter.java, Lucene91RWCodec.java and Lucene91RWHnswVectorsFormat.java from lucene/backward-codecs/src/test/org/apache/lucene/backward_codecs/lucene91/ into src/org/apache/lucene/backward_codecs/lucene91/ (src/UPSTREAM-TEST-WRITER-ORIGIN.txt lists the six with the git command that prints each; it describes this folder as it is after the copy). Then run the test on the 9.12.3 release jars: put lucene-core-9.12.3.jar, lucene-codecs-9.12.3.jar, lucene-test-framework-9.12.3.jar and lucene-backward-codecs-9.12.3.jar (Maven Central, org.apache.lucene) into this folder, next to the four test jars that the recipe names; the recipe below then runs as it is printed, with JDK 25. The six files of the 9.12.3 tag compile only against the 9.11.0 to 9.12.3 release jars: to run the test on another release from 9.5.0 on, on branch_11x or on main, compile the unchanged test file next to that build's own copy of the test-side writer (lucene/backward-codecs/src/test), because those sources differ between releases; the 9.8.0 failure and the passes on 9.7.0, on the 10.x releases, on branch_11x and on main were run that way. Before 9.5.0 the test file does not compile either (KnnFloatVectorField and KnnFloatVectorQuery came with 9.5.0): on 9.1.0 to 9.4.2 it was run with those two class names adapted, on 9.1.0 with that release's own codec as the writer (finding page, History, Versions tested). What the test does: it writes two documents with that writer, document 0 without a vector and document 1 with the vector [1, 0], opens the index with the default reader and searcher and runs KnnFloatVectorQuery for [1, 0] with k = 1. It expects document 1 and fails with 'expected:&lt;1&gt; but was:&lt;0&gt;'. There is no binary index fixture, and the test uses no randomness. The Gradle alternative at the end of this page needs a checkout of the tag releases/lucene/9.12.3 (or of branch_9_12) and a JDK that the 9.x build accepts; that route was not run for this page.
- About the recipe: its first six lines (the checkout comment, the LUCENE and V line and the four CP lines that name $LUCENE) are the lines every repro page of this site prints for a build of a checkout. They do not apply to this test: the build of the 9.x line (Gradle 8.10) predates JDK 25, and no build of the 9.12.3 tag has the version of the V line. The TESTJARS and CP lines after the comment in the middle of the recipe set the class path again, to the four release jars and the four test jars, so the six lines have no effect and can be left out.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: the test passed, as recorded before (a control run).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=9.12.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

# This test runs on the 9.12.3 release jars, not on a build of a checkout: the six lines above that name a
# checkout, $LUCENE and $V do not apply to it (no build of the 9.12.3 tag has that version). The two lines below
# set the class path again: the four release jars in this folder, then the four test jars named above.
TESTJARS=${CP##*-$V.jar:}
CP=lucene-core-9.12.3.jar:lucene-codecs-9.12.3.jar:lucene-test-framework-9.12.3.jar:lucene-backward-codecs-9.12.3.jar:$TESTJARS

# The javac line below also needs the six test sources of the 9.12.3 tag in src/, which this folder does not ship:
# copy them there first, as the note above the recipe says.

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 0E15E505
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=0E15E505 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_codecs.lucene91.TestLucene91SparseVectorFieldKnn
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestLucene91SparseVectorFieldKnn.java](TestLucene91SparseVectorFieldKnn.java)

6 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
