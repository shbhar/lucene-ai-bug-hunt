# The merge-time postings enum that the default FieldsConsumer.merge hands to a postings format's write() (MappingMultiPostingsEnum) returns -1 from docID() after nextDoc() has returned NO_MORE_DOCS, and, when it is reused after a partly read term, that term's document before the first nextDoc() (latent: Lucene's own postings writers use nextDoc()'s return value)

Finding LFV-1303 · Area: index / postings merge (MappedMultiFields / MappingMultiPostingsEnum handed to FieldsConsumer.write) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1303.html](../../bugs/LFV-1303.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A custom postings format that keeps the default merge receives, in FieldsConsumer.write, a merged view of the segments' postings; it walks a term's documents with nextDoc() until NO_MORE_DOCS and then reads docID(), expecting NO_MORE_DOCS as DocIdSetIterator documents. Instead the merge-time enum (MappingMultiPostingsEnum) returns -1, the 'not started yet' value, because docID() is derived from its current sub-enum, which is null once the merge of that term is exhausted; when the enum is reused for the next term after a partly read one, docID() also reports the old term's document instead of -1 until the first nextDoc(). Lucene's own postings writers use nextDoc()'s return value and are not affected; helpers that start from docID(), such as FixedBitSet.or, throw at merge on a term whose documents are all deleted.

## Expected vs actual

- Expected: `docID()` after the drain is `NO_MORE_DOCS` (2147483647).
- Actual: `docID()` is -1: `AssertionError: expected:<2147483647> but was:<-1>`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestMergePostingsDocIdAfterExhaustion.java](src/org/apache/lucene/index/TestMergePostingsDocIdAfterExhaustion.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMergePostingsDocIdAfterExhaustion
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMergePostingsDocIdAfterExhaustion.java](TestMergePostingsDocIdAfterExhaustion.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
