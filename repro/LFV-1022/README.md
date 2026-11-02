# BpVectorReorderer's in-place command-line reorder commits an empty or partial index over the original when reordering fails

Finding LFV-1022 · Area: misc / index: BpVectorReorderer command line (main -> reorderIndexDirectory, an in-place rewrite through an OpenMode.CREATE IndexWriter and addIndexes) · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-1022.html](../../bugs/LFV-1022.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run BpVectorReorderer's command line (main(path, field), which calls reorderIndexDirectory) to renumber the documents of an existing index in place, and expect that if the reorder fails you get the exception and the index still holds all its documents. Instead, when computing a doc map or addIndexes throws (for example the documented NotEnoughRAMException), the try-with-resources around the OpenMode.CREATE IndexWriter closes it, close() commits by default, and the new commit holds only the segments rewritten before the failure, while the original commit and its files are deleted. A 10-document index ends with 0 documents, and through main() with default options an index created by Lucene 9.x or 10.x, or one with a soft-deletes or parent field, is wiped to 0 documents.

## Expected vs actual

- Expected: The reorder throws `NotEnoughRAMException`, and the index still holds its 1 document.
- Actual: The reorder throws, and the index now holds 0 documents: `expected:<1> but was:<0>`.

## Files

- [src/org/apache/lucene/misc/index/TestBpVectorReorderFailureKeepsIndex.java](src/org/apache/lucene/misc/index/TestBpVectorReorderFailureKeepsIndex.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBpVectorReorderFailureKeepsIndex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBpVectorReorderFailureKeepsIndex.java](TestBpVectorReorderFailureKeepsIndex.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
