# A PostingsEnum reused through SortingCodecReader from a field with payloads onto a field without positions returns the previous field's payload from getPayload() instead of null (SortingPostingsEnum.reset() does not clear the payload buffer; since 9.6.0)

Finding LFV-1319 · Area: core / index: FreqProxTermsWriter.SortingPostingsEnum (the sorted postings view behind SortingCodecReader and index-sorted flush) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1319.html](../../bugs/LFV-1319.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents with a text field p whose token carries the payload 'xyz' and a field q indexed with DOCS_AND_FREQS, wrap the segment with SortingCodecReader.wrap(leaf, sort) using a sort that reorders the documents, read p's postings with PostingsEnum.ALL (getPayload() is 'xyz'), and then pass that enum back as the reuse argument for q's postings with ALL. You expect nextPosition() to return -1 and getPayload() to return null, as PostingsEnum documents ('or null if no payload was indexed') and as the unwrapped segment reader does for the same calls. Instead getPayload() returns 'xyz', because the sorted enum's reset() re-initialises the document and offset state but not its payload buffer, and nextPosition() returns -1 for a field without positions before it reaches the only code that sets or clears that buffer.

## Expected vs actual

- Expected: `nextPosition()` returns -1 and `getPayload()` returns `null`; the test passes.
- Actual: `nextPosition()` returns -1 but `getPayload()` returns `[78 79 7a]`, the payload `xyz` of field `p`: `java.lang.AssertionError: expected null, but was:<[78 79 7a]>`.

## Files

- [src/TestSortingPostingsEnumReusedPayload.java](src/TestSortingPostingsEnumReusedPayload.java) (JUnit test)

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
     execute --select-class TestSortingPostingsEnumReusedPayload
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSortingPostingsEnumReusedPayload.java](TestSortingPostingsEnumReusedPayload.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
