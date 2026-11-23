# A merge that renumbers documents through SortingCodecReader (BPReorderingMergePolicy, or the re-sort recipe via addIndexes) throws ArithmeticException once one BINARY doc-values field of the merged segment holds 2 GiB, whatever the heap, because the sorted view copies the field into one int-addressed buffer; under the merge policy the failed merge closes IndexWriter

Finding LFV-1486 · Area: index / sorted views of BINARY doc values (BinaryDocValuesWriter.BinaryDVs, built by SortingCodecReader for merges that renumber documents: BPReorderingMergePolicy in misc and the addIndexes re-sort recipe) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1486.html](../../bugs/LFV-1486.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents with a BinaryDocValuesField and merge them with a merge policy that reorders documents (BPReorderingMergePolicy), or you sort an existing index the documented way with addIndexes(SortingCodecReader.wrap(reader, sort)); one binary field of the merged segment holds 2 GiB or more in total (for example 262,144 values of 8 KiB). You expect the merge to complete, as it does with one value fewer and for the same segments under a plain merge policy, because Lucene limits only the length of one binary value, not the total size of the field. Instead the merge throws ArithmeticException 'integer overflow' however much heap the JVM has, because the reordered view first copies the whole field into one in-memory buffer whose positions are ints; under the merge policy that exception closes the IndexWriter.

## Expected vs actual

- Expected: `forceMerge(1)` returns and the index is one segment with all 262,144 values.
- Actual: `forceMerge(1)` throws `java.lang.ArithmeticException: integer overflow` from `BinaryDocValuesWriter$BinaryDVs.<init>` (line 287), and the writer is closed.

## Files

- [src/org/apache/lucene/misc/index/TestBPReorderingMergeBinaryDocValues2GiB.java](src/org/apache/lucene/misc/index/TestBPReorderingMergeBinaryDocValues2GiB.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does and needs: it writes 262,144 binary doc values of 8,192 bytes (2 GiB in one field) to a temporary directory and calls forceMerge(1) under BPReorderingMergePolicy with the serial merge scheduler; the merge fails with ArithmeticException: integer overflow. The recipe sets a 4 GB heap through JAVA_TOOL_OPTIONS, as in the recorded runs; with a 2 GB heap the test ends in OutOfMemoryError instead, which is not the finding. It also needs about 2.5 GB of temporary disk space under java.io.tmpdir (the failing runs peaked at 2.15 to 2.54 GB) and ran for about 10 seconds on a fast local disk. A failing run keeps its index: the test framework keeps the files of a failed test (it prints 'NOTE: leaving temporary files on disk at: ...'), so each run leaves about 2.1 GB in that folder; delete it after the run, or point java.io.tmpdir at a disk with room (add -Djava.io.tmpdir=/path/with/room to JAVA_TOOL_OPTIONS). The Gradle alternative at the end of this page needs -Ptests.heapsize=4g as well: with Gradle's default test heap the test ends in OutOfMemoryError, which is not the finding either. The re-sort recipe with addIndexes is not part of this test.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

export JAVA_TOOL_OPTIONS="-Xmx4g"   # the recorded heap: the merge copies the 2 GiB field into the heap before it fails

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=6 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBPReorderingMergeBinaryDocValues2GiB
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestBPReorderingMergeBinaryDocValues2GiB.java](TestBPReorderingMergeBinaryDocValues2GiB.java)

Formal models and scripts this finding's part of the hunt used: [../../models/sortingcodecreader-binary-docvalues-2gib-copy/](../../models/sortingcodecreader-binary-docvalues-2gib-copy/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
