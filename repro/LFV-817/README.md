# TermStates#get hands a lazily looked-up TermState to the other partitions of the same segment without safe publication, so under intra-segment concurrency a partition can read docFreq or file pointers still at 0 and a non-scoring term or phrase query silently misses matches (executed on aarch64)

Bug LFV-817 · Area: search / intra-segment concurrency (index / TermStates lazy per-leaf lookup) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with an IndexSearcher that splits one segment into several partitions (the documented intra-segment concurrency) and count a non-scoring PhraseQuery, expecting the same count as a sequential search (500). Instead, on aarch64 about once per 10^5 to 10^6 searches the count is 437, 438 or 375 with no error: all partitions share one lazily filled TermStates, and TermStates#get stores each freshly cloned TermState into a plain array slot that the other partitions read with plain loads, so a partition can see the object before its fields (docFreq 0, sometimes a zero docStartFP or posStartFP) and gets an empty or garbage postings list for its doc range. Publishing the slot with a release store and reading it with acquire loads removes the failure in every test (0 in 1.2M end-to-end searches, 0 in 122.9M root-cause lookups).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: On aarch64 about once per 10^5 to 10^6 searches the count is 437, 438 or 375 with no error: all partitions share one lazily filled TermStates, and TermStates#get stores each freshly cloned TermState into a plain array slot that the other partitions read with plain loads, so a partition can see the object before its fields (docFreq 0, sometimes a zero docStartFP or posStartFP) and gets an empty or garbage postings list for its doc range.

## Files

- [src/org/apache/lucene/search/TestPartitionedPhraseCountLazyTermStates.java](src/org/apache/lucene/search/TestPartitionedPhraseCountLazyTermStates.java) (JUnit test)
- [src/org/apache/lucene/index/TestTermStatesLazyPublication.java](src/org/apache/lucene/index/TestTermStatesLazyPublication.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dh292.searches=300000 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPartitionedPhraseCountLazyTermStates

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTermStatesLazyPublication
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
