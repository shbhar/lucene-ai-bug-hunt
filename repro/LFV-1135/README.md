# IndexWriter accepts a custom term frequency of 0 without any check: the frequency goes into the postings and the committed segment fails CheckIndex ('freq 0 is out of bounds'), or the commit throws and closes the writer

Finding LFV-1135 · Area: index / IndexingChain inversion with custom term frequencies (TermFrequencyAttribute, FreqProxTermsWriterPerField) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1135.html](../../bugs/LFV-1135.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document through a TokenStream whose TermFrequencyAttribute is your own implementation (plugged in with AttributeSource.addAttributeImpl or an AttributeFactory) and that reports a frequency of 0 for a token, on a field indexed with DOCS_AND_FREQS. You expect IndexWriter to reject that document with an IllegalArgumentException, as it does for a negative position increment, backwards offsets or too many tokens, or at least never to write an index that its own CheckIndex calls broken. Instead the 0 goes into the postings unchecked: with assertions off addDocument and commit succeed and CheckIndex then reports 'term [66 6f 6f]: doc 0: freq 0 is out of bounds' and 1 broken segment, and if the term's only document has frequency 0, the commit instead throws 'cannot write negative vLong (got: -1)', which closes the writer and loses every uncommitted document.

## Expected vs actual

- Expected: `addDocument` rejects the frequency-0 document with `IllegalArgumentException`, or the committed index passes CheckIndex.
- Actual: `RuntimeException: CheckIndex failed` (`term [66 6f 6f]: doc 0: freq 0 is out of bounds`) with assertions off; `AssertionError` at `FreqProxTermsWriterPerField.addTerm:143` with them on.

## Files

- [src/org/apache/lucene/index/TestCustomTermFreqBelowOne.java](src/org/apache/lucene/index/TestCustomTermFreqBelowOne.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCustomTermFreqBelowOne
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCustomTermFreqBelowOne.java](TestCustomTermFreqBelowOne.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
