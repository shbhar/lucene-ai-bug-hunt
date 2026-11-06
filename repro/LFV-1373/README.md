# During a doc-values update rewrite, the iterator IndexWriter hands to a DocValuesConsumer (ReadersAndUpdates.MergedDocValues) throws from intoBitSet when the destination bit set covers every returned doc but ends before upTo, because it bulk-loads on-disk docs whose values the update removed, contrary to the DocIdSetIterator#intoBitSet contract (latent: Lucene's own consumer always passes a full 65536-doc window; since 10.3.0, #14552)

Finding LFV-1373 · Area: index / doc-values update write path (ReadersAndUpdates.MergedDocValues.intoBitSet, the iterator handed to a DocValuesConsumer) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1373.html](../../bugs/LFV-1373.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A custom doc-values codec, while IndexWriter rewrites a numeric column after a doc-values update, positions the iterator it is given on its first doc and calls intoBitSet(NO_MORE_DOCS, bits, 0) with a bit set just large enough for the docs that iterator returns; in the repro a 65-doc segment has values in docs 0 and 64 and the update removes doc 64's value, so the iterator returns only doc 0 and a 1-bit destination should get bit 0, because the javadoc says intoBitSet must behave exactly like the loop 'for (doc = docID(); doc &lt; upTo; doc = nextDoc()) bitSet.set(doc - offset)'. Instead ReadersAndUpdates.MergedDocValues.intoBitSet first bulk-loads every on-disk doc below upTo, including the removed doc 64, into a scratch bit set of the destination's size, so FixedBitSet.set(64) fails (AssertionError 'index=64, numBits=1' with -ea, ArrayIndexOutOfBoundsException 'Index 1 out of bounds for length 1' with -da) and IndexWriter.commit() throws. Upstream declared this call shape legal when it fixed the same failure in BitSetIterator (#14142) and IndexedDISI (#14922); Lucene's own consumer always passes a full window, so only custom codecs hit it.

## Expected vs actual

- Expected: Bit 0 is set (cardinality 1) and the commit succeeds.
- Actual: `AssertionError: index=64, numBits=1` from `FixedBitSet.set` (assertions off: `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`), and `commit()` throws.

## Files

- [src/org/apache/lucene/index/TestDocValuesUpdateIntoBitSet.java](src/org/apache/lucene/index/TestDocValuesUpdateIntoBitSet.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDocValuesUpdateIntoBitSet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDocValuesUpdateIntoBitSet.java](TestDocValuesUpdateIntoBitSet.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
