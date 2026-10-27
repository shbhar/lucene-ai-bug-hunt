# RescoreTopNQuery lets deleted documents take top-n slots, and IndexSearcher#count counts them

Bug LFV-087 · Area: search / rescoring · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-087.html](../../bugs/LFV-087.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap an ordinary query, for example a TermQuery, in RescoreTopNQuery to re-score its matches with a DoubleValuesSource and keep the best n, on an index where some matching documents are deleted but not yet merged away. You expect the n best live matches, and IndexSearcher#count to count only live documents. Instead a deleted document whose rescore value ranks in the top n takes one of the n slots, so the search returns fewer than n hits (n=2: [2] instead of [2, 1]) while IndexSearcher#count still reports 2; the deleted document itself is never returned.

## Expected vs actual

- Expected: best match deleted, n=2: hits `[2, 1]` and count 2; the only match deleted: no hits and count 0.
- Actual: hits `[2]` with count 2; with n=10 the hits `[1, 0]` are right but the count is 3; the only match deleted gives no hits and count 1.

## Files

- [src/org/apache/lucene/search/TestRescoreTopNDeletedDocs.java](src/org/apache/lucene/search/TestRescoreTopNDeletedDocs.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestRescoreTopNDeletedDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestRescoreTopNQueryDeletedDocs.java](TestRescoreTopNQueryDeletedDocs.java)
- [rescoretopn-live-docs.diff](rescoretopn-live-docs.diff)
- [TestRescoreTopNQueryEmptyInnerProbe.java](TestRescoreTopNQueryEmptyInnerProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
