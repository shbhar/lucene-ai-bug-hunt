# Postings docIDRunEnd returns Integer.MIN_VALUE for dense blocks after a term's last level-1 skip block when doc IDs are within 8,192 (4,096 before 10.4) of 2^31, so CheckIndex at level 2 or higher reports a healthy segment of about 2^31 documents as corrupt

Bug LFV-170 · Area: codecs / postings reader (Lucene104 BlockPostingsEnum#docIDRunEnd, also the Lucene101 and Lucene103 readers); index / CheckIndex · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You have one segment of about 2^31 documents (within about 8,000 docs of IndexWriter.MAX_DOCS) with a term whose postings are dense near the end of the doc-ID space, for example a term that every document has, and you call docIDRunEnd on its postings or run CheckIndex at level 2 or higher. You expect docIDRunEnd to return a doc ID greater than the current one, as its javadoc promises, and CheckIndex to pass. Instead, in the dense blocks after the term's last full 8,192-doc level-1 skip block (4,096 docs in 10.2-10.3), the default postings reader returns Integer.MIN_VALUE and CheckIndex reports the healthy segment as corrupt ('Run end -2147483648 is &lt;= doc ID 2147475456'); query results stay correct, and the repro writes the postings through the public codec API rather than indexing 2^31 documents with IndexWriter.

The test shows that Lucene104 postings docIDRunEnd() on a dense 256-doc block that follows the term's last level-1 block, with doc IDs within 8,192 of 2^31, returns NO_MORE_DOCS + 1 = Integer.MIN_VALUE. The test is codec level: it writes 8,448 doc IDs with the postings writer.

## Expected vs actual

Expected: docIDRunEnd() &gt; docID() = 2147475456. Actual (-ea and -da): -2147483648.

## Files

- [src/org/apache/lucene/codecs/lucene104/TestDocIDRunEndNearMaxDoc.java](src/org/apache/lucene/codecs/lucene104/TestDocIDRunEndNearMaxDoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene104.TestDocIDRunEndNearMaxDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/postings-lucene104/](../../models/postings-lucene104/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
