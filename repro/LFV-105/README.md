# IndexingChain.invertTerm adds 2 to the field length for each single-term (InvertableType.BINARY) value, so untokenized DOCS_AND_FREQS fields with norms get double the norm and a lower BM25 score

Bug LFV-105 · Area: index / indexing chain (single-term BINARY inversion, norms) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-105.html](../../bugs/LFV-105.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one value in an untokenized field whose FieldType has DOCS_AND_FREQS and norms enabled, once through a plain Field (TokenStream inversion) and once through the single-term path (a Field subclass whose invertableType returns BINARY, or an IndexWriter#addBatch BinaryColumn or DictionaryColumn). You expect the same field length and norm, 1 per value, because FieldInvertState#getLength is the 'total number of terms in this field' and a BINARY value is documented as a single value with term frequency one. Instead IndexingChain.invertTerm adds 2 to the length for every value, so the norm is 2 (2k for k values) and the BM25 score of the identical document drops (0.082873434 to 0.058813408 in the repro), while the postings still record freq 1 per value.

## Expected vs actual

- Expected: Both documents get the same norm (1 for one value, 2 for two) and the same BM25 score for the term query.
- Actual: Norms [1, 2] and [2, 4]; BM25 0.082873434 vs 0.058813408 for the identical document.

## Files

- [src/org/apache/lucene/index/TestBinaryInvertableTypeFieldLength.java](src/org/apache/lucene/index/TestBinaryInvertableTypeFieldLength.java) (JUnit test)
- [src/org/apache/lucene/index/TestColumnBatchUntokenizedFieldLength.java](src/org/apache/lucene/index/TestColumnBatchUntokenizedFieldLength.java) (JUnit test)

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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestColumnBatchUntokenizedFieldLength

# run 2: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestBinaryInvertableTypeFieldLength
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestBinaryInvertedFieldNorms.java](TestBinaryInvertedFieldNorms.java)
- [TestColumnBatchUntokenizedNorms.java](TestColumnBatchUntokenizedNorms.java)
- [TestBinaryInvertableTypeFieldLength.java](TestBinaryInvertableTypeFieldLength.java)
- [TestBinaryInvertLengthV1.java](TestBinaryInvertLengthV1.java)
- [TestBinaryInvertLengthConsistency.java](TestBinaryInvertLengthConsistency.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
