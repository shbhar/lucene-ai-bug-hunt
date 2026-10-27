# FieldExistsQuery#count undercounts documents whose norms-free text field analyzes to no tokens but still has a doc value, so IndexSearcher#count is too low

Bug LFV-086 · Area: search / FieldExistsQuery count · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a text field with norms turned off together with a doc-values field of the same name, some document's text analyzes to no tokens (for example an empty string), and you call IndexSearcher#count(new FieldExistsQuery(field)). You expect the count to equal the number of documents the query returns, because the query matches every document that has a doc value. Instead, on a segment without deletions, Weight#count returns Terms#getDocCount, which counts only documents with at least one term, so the count is 1 for 2 hits, or 0 for 1 hit when no document in the segment has a term.

## Expected vs actual

- Expected: You expect the count to equal the number of documents the query returns, because the query matches every document that has a doc value.
- Actual: On a segment without deletions, Weight#count returns Terms#getDocCount, which counts only documents with at least one term, so the count is 1 for 2 hits, or 0 for 1 hit when no document in the segment has a term.

## Files

- [src/org/apache/lucene/search/TestFieldExistsCountZeroTokenDocs.java](src/org/apache/lucene/search/TestFieldExistsCountZeroTokenDocs.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestFieldExistsCountZeroTokenDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/count-totalhits/](../../models/count-totalhits/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
