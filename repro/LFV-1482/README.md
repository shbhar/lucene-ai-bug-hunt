# SharedTermsUniformSplit (experimental postings format): a merge that includes a segment without postings throws NullPointerException in STUniformSplitTermsWriter.merge and closes the IndexWriter when the format is the codec's own postings format (9.1.0 to 10.6.0 and main; a regression of LUCENE-10291); with the usual per-field wiring only a partitioned merge with an empty doc range (main and branch_11x) or, on 9.2.0 to 9.12.3, an index created by 8.x reaches it

Finding LFV-1482 · Area: codecs / SharedTermsUniformSplit postings format (lucene-codecs, experimental): STUniformSplitTermsWriter.merge on a merge input that has no postings producer · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1482.html](../../bugs/LFV-1482.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You make SharedTermsUniformSplit the postings format of your codec (a FilterCodec whose postingsFormat() returns STUniformSplitPostingsFormat), index one document with an indexed field and one document with only a doc-values field into two segments and call forceMerge(1). You expect one segment that holds both documents, as with every other postings format and as on 9.0.0. Instead the merge throws NullPointerException ('Cannot invoke "org.apache.lucene.codecs.FieldsProducer.terms(String)" because "fieldsProducer" is null') in STUniformSplitTermsWriter.merge: since 9.1.0 a segment without postings has no postings producer, the generic merge skips such a segment, and this format's own merge never checks for it. IndexWriter treats the exception as a tragic event and closes itself, so uncommitted changes are lost and a new writer that runs the same merge fails the same way (two more sessions were tried); the committed index stays readable and nothing wrong is written.

## Expected vs actual

- Expected: `forceMerge(1)` returns: 1 segment, `docFreq(id:a)` = 1.
- Actual: `forceMerge(1)` throws `NullPointerException` (`"fieldsProducer" is null`) at `STUniformSplitTermsWriter.merge(STUniformSplitTermsWriter.java:297)` on main `781025d6fe32` (line 296 on main `f2b061406c4b`).

## Files

- [src/org/apache/lucene/codecs/uniformsplit/sharedterms/TestSTUniformSplitMergeSegmentWithoutPostings.java](src/org/apache/lucene/codecs/uniformsplit/sharedterms/TestSTUniformSplitMergeSegmentWithoutPostings.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it makes STUniformSplitPostingsFormat the postings format of an anonymous FilterCodec, so it needs no META-INF/services entry, flushes one document with an indexed field and one with only a doc-values field, and calls forceMerge(1) under the serial merge scheduler; the NullPointerException from the merge reaches the test thread. The per-field wiring paths named in the title are not part of this test.
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.uniformsplit.sharedterms.TestSTUniformSplitMergeSegmentWithoutPostings
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSTUniformSplitMergeSegmentWithoutPostings.java](TestSTUniformSplitMergeSegmentWithoutPostings.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
