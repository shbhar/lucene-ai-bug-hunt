# ReqExclBulkScorer.WindowBits.applyMask adds offset + bitSet.length in int, so FILTER/MUST + MUST_NOT queries silently drop every match in a 4096-doc window based at doc 2^31-4096 or above (up to the last 3,967 doc IDs of a segment near IndexWriter.MAX_DOCS)

Bug LFV-139 · Area: search / BooleanQuery MUST_NOT bulk scoring (ReqExclBulkScorer) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-139.html](../../bugs/LFV-139.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one segment of IndexWriter.MAX_DOCS (2,147,483,519) documents with default settings, one of which has b:y, and count the matches of +*:* -b:y (MatchAllDocsQuery as FILTER plus a MUST_NOT term query) with a plain IndexSearcher; you expect 2,147,483,518. Instead you get 2,147,479,551: all 3,967 documents of the last window [2,147,479,552, MAX_DOCS) are silently missing, because ReqExclBulkScorer's WindowBits.applyMask computes Math.min(NO_MORE_DOCS, offset + bitSet.length) in int, the sum wraps negative for a 4096-bit window based at 2^31-4096 or above, and the method then clears the whole window. Only segments with more than 2,147,479,552 documents and required clauses denser than 0.3% of maxDoc are affected, and only unreleased code (main and the 10.x / 10.6 branches since #16669) has the bug.

The test shows that `ReqExclBulkScorer` drops every match of a dense required clause in the 4096-doc window based at 2^31-4096 when a `MUST_NOT` clause is present, because `WindowBits.applyMask` adds `offset + bitSet.length()` in `int` (no index built).

## Expected vs actual

Expected 99 hits (the last 100 doc IDs of a `MAX_DOCS` segment minus one excluded); actual 0 (`expected:<99> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestReqExclLastWindow.java](src/org/apache/lucene/search/TestReqExclLastWindow.java) (JUnit test)

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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestReqExclLastWindow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/bitsets-livedocs-acceptdocs/](../../models/bitsets-livedocs-acceptdocs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
