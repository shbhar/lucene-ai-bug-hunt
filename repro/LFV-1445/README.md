# KnnVectorsReader.search's javadoc still says acceptDocs may be null to allow every document, but since the parameter became an AcceptDocs in 10.3.0 the readers dereference it, so a caller who follows the javadoc gets a NullPointerException instead of an unfiltered search; documentation defect, javadoc-only fix; since 10.3.0

Finding LFV-1445 · Area: core / codecs: kNN vectors reader SPI (KnnVectorsReader.search javadoc of acceptDocs) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1445.html](../../bugs/LFV-1445.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take the default codec's vectors reader through the public codec SPI (a FilterCodec whose KnnVectorsFormat keeps the reader that fieldsReader opens) and call search("v", query, collector, null), because the @param acceptDocs of every KnnVectorsReader.search overload says '{@link Bits} that represents the allowed documents to match, or {@code null} if they are all allowed to match'. You expect an unfiltered search that collects the segment's one document. Instead Lucene99HnswVectorsReader.search throws NullPointerException ('Cannot invoke "org.apache.lucene.search.AcceptDocs.cost()" because "acceptDocs" is null'): since #15011 (10.3.0) the parameter is an AcceptDocs that the readers dereference and 'all documents' is AcceptDocs.fromLiveDocs(null, maxDoc), but the javadoc still describes the old nullable Bits.

## Expected vs actual

- Expected: Per the javadoc, an unfiltered search: the collector holds the one document (1 hit).
- Actual: `NullPointerException: Cannot invoke "org.apache.lucene.search.AcceptDocs.cost()" because "acceptDocs" is null` at `Lucene99HnswVectorsReader.search`.

## Files

- [src/org/apache/lucene/codecs/TestKnnVectorsReaderSearchNullAcceptDocs.java](src/org/apache/lucene/codecs/TestKnnVectorsReaderSearchNullAcceptDocs.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.TestKnnVectorsReaderSearchNullAcceptDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestKnnVectorsReaderSearchNullAcceptDocs.java](TestKnnVectorsReaderSearchNullAcceptDocs.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
