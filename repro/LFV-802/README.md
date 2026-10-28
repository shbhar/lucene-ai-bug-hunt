# IndexWriter#tryUpdateDocValue does not validate the doc id: an id outside [0, maxDoc) returns success and silently updates or soft-deletes a different document, or commits a doc-values entry for a nonexistent document that a merge shifts onto other documents

Bug LFV-802 · Area: index / IndexWriter tryUpdateDocValue (tryModifyDocument doc-id validation, doc-values update buffer) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter#tryUpdateDocValue with an NRT reader and a doc id outside [0, reader.maxDoc), for example 8 or 9 on an 8-document reader, 12 on a 10-document reader, or -1 on a 4-document leaf reader. You expect the call to be rejected, as the same reader rejects such an id ('docID must be &gt;= 0 and &lt; maxDoc', IllegalArgumentException), or at least to leave every other document alone. Instead, with assertions disabled (and for the negative leaf-reader id even with them enabled) it returns a sequence number &gt; 0, which the javadoc says means the update succeeded, and writes the value to or soft-deletes a different live document (8 -&gt; doc 0, 9 -&gt; doc 1, -1 -&gt; doc 3), or commits a doc-values entry for nonexistent doc 12 that CheckIndex accepts and that forceMerge turns into every document of the next segment carrying its predecessor's value (b0=777, b1=1000,..., b9=1008; 1009 is lost).

## Expected vs actual

- Expected: You expect the call to be rejected, as the same reader rejects such an id ('docID must be &gt;= 0 and &lt; maxDoc', IllegalArgumentException), or at least to leave every other document alone.
- Actual: With assertions disabled (and for the negative leaf-reader id even with them enabled) it returns a sequence number &gt; 0, which the javadoc says means the update succeeded, and writes the value to or soft-deletes a different live document (8 -&gt; doc 0, 9 -&gt; doc 1, -1 -&gt; doc 3), or commits a doc-values entry for nonexistent doc 12 that CheckIndex accepts and that forceMerge turns into every document of the next segment carrying its predecessor's value (b0=777, b1=1000,..., b9=1008; 1009 is lost).

## Files

- [src/org/apache/lucene/index/TestTryUpdateDocValueOutOfRangeDocId.java](src/org/apache/lucene/index/TestTryUpdateDocValueOutOfRangeDocId.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTryUpdateDocValueOutOfRangeDocId
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
