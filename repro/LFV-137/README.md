# FixedBitSet.ensureCapacity computes the grown length as int arr.length &lt;&lt; 6, which wraps negative once the requested bit reaches about 1.91 billion, so DocsWithFieldSet iterates no documents and a flushed segment silently drops every value of its sparse doc-values and norms fields

Bug LFV-137 · Area: util / FixedBitSet growth (ensureCapacity) and index / DocsWithFieldSet (doc-values and norms flush) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index into one segment until doc IDs pass about 1.91 billion (legal: IndexWriter.MAX_DOCS is 2^31-129) with a doc-values or norms field that only some documents have, then commit; you expect every indexed value to be in the segment. Instead, when the field's DocsWithFieldSet grows its FixedBitSet to the new doc ID, FixedBitSet.ensureCapacity computes the new length as arr.length &lt;&lt; 6 in int arithmetic, which wraps negative once the backing array has 2^25 words, so the set's iterator returns no document although cardinality counts them, and the flushed segment has no document with a value for that field (with -da; with -ea addDocument throws ArrayIndexOutOfBoundsException for that document). The same call on a small set, ensureCapacity(new FixedBitSet(64), 2_000_000_000), returns a set with length = -2,044,967,232; the twin LongBitSet.ensureCapacity had the identical overflow and was fixed in 2015 (LUCENE-6409), but FixedBitSet was not.

## Expected vs actual

- Expected: You expect every indexed value to be in the segment.
- Actual: When the field's DocsWithFieldSet grows its FixedBitSet to the new doc ID, FixedBitSet.ensureCapacity computes the new length as arr.length &lt;&lt; 6 in int arithmetic, which wraps negative once the backing array has 2^25 words, so the set's iterator returns no document although cardinality counts them, and the flushed segment has no document with a value for that field (with -da; with -ea addDocument throws ArrayIndexOutOfBoundsException for that document).

## Files

- [src/org/apache/lucene/util/TestFixedBitSetEnsureCapacityLarge.java](src/org/apache/lucene/util/TestFixedBitSetEnsureCapacityLarge.java) (JUnit test)
- [src/org/apache/lucene/index/TestDocsWithFieldSetLargeDocIds.java](src/org/apache/lucene/index/TestDocsWithFieldSetLargeDocIds.java) (JUnit test)
- [src/org/apache/lucene/index/TestSparseDocValuesBeyond1_91BillionDocs.java](src/org/apache/lucene/index/TestSparseDocValuesBeyond1_91BillionDocs.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
java -ea -esa -Xmx3g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestFixedBitSetEnsureCapacityLarge \
       --select-class org.apache.lucene.index.TestDocsWithFieldSetLargeDocIds

# run 2: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx3g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSparseDocValuesBeyond1_91BillionDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/bitsets-livedocs-acceptdocs/](../../models/bitsets-livedocs-acceptdocs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
