# A term repeated in one document of a DOCS_AND_CUSTOM_FREQS field closes IndexWriter, instead of skipping the document, when the term is a non-UTF-8 binary term such as the single byte 0xC3: the duplicate-term rejection builds its message with utf8ToString, which throws (since 10.5.0)

Finding LFV-1189 · Area: index / IndexingChain inversion, term-doc (DOCS_AND_CUSTOM_FREQS) duplicate-term rejection (TermsHashPerField.add) x aborting-exception classification · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1189.html](../../bugs/LFV-1189.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index binary terms into a field with IndexOptions.DOCS_AND_CUSTOM_FREQS, which allows each term only once per document, and one document repeats a term that is not valid UTF-8, for example new BytesRef(new byte[] {(byte) 0xC3}) added twice as an untokenized Field. You expect addDocument to reject only that document with the non-aborting IllegalArgumentException 'Document update skipped due to duplicate termdoc term' and the writer to stay open, as happens for an ASCII or valid UTF-8 term and as the upstream test TestCustomTermFreq.testRepeatedTerms asserts. Instead TermsHashPerField.add builds the rejection message with termBytes.utf8ToString(), which throws ArrayIndexOutOfBoundsException for these bytes; the indexing chain treats that as an aborting failure, so IndexWriter closes itself and every uncommitted document is lost.

## Expected vs actual

- Expected: `addDocument` throws `IllegalArgumentException: Document update skipped due to duplicate termdoc term` and the writer stays open.
- Actual: `addDocument` throws `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` from `BytesRef.utf8ToString`, and the writer has closed itself.

## Files

- [src/org/apache/lucene/index/TestRepeatedBinaryTermDocTerm.java](src/org/apache/lucene/index/TestRepeatedBinaryTermDocTerm.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRepeatedBinaryTermDocTerm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRepeatedBinaryTermDocTerm.java](TestRepeatedBinaryTermDocTerm.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
