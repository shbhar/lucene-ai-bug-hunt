# IndexingChain.invertTerm adds 2 to the field length for each single-term (InvertableType.BINARY) value, so untokenized DOCS_AND_FREQS fields with norms get double the norm and a lower BM25 score

Bug LFV-105 · Area: index / indexing chain (single-term BINARY inversion, norms) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-105.html](../../bugs/LFV-105.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one value in an untokenized field whose FieldType has DOCS_AND_FREQS and norms enabled, once through a plain Field (TokenStream inversion) and once through the single-term path (a Field subclass whose invertableType returns BINARY, or an IndexWriter#addBatch BinaryColumn or DictionaryColumn). You expect the same field length and norm, 1 per value, because FieldInvertState#getLength is the 'total number of terms in this field' and a BINARY value is documented as a single value with term frequency one. Instead IndexingChain.invertTerm adds 2 to the length for every value, so the norm is 2 (2k for k values) and the BM25 score of the identical document drops (0.082873434 to 0.058813408 in the repro), while the postings still record freq 1 per value.

The test shows that one value of an untokenized `DOCS_AND_FREQS` field with norms, indexed through a `Field` whose `invertableType()` is `BINARY`, gets field length 2 in `IndexingChain.invertTerm`, so its BM25 norm is 2.

## Expected vs actual

Expected norm 1 (one term, as `FieldInvertState#getLength` and the `InvertableType.BINARY` javadoc say); actual 2 (`expected:<1> but was:<2>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestBinaryInvertableTypeNorm.java](src/org/apache/lucene/index/TestBinaryInvertableTypeNorm.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestBinaryInvertableTypeNorm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
