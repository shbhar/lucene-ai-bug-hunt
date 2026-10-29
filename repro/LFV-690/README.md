# DiversifiedTopDocsCollector and its manager put every document without a key into key 0's group, so a keyless document uses up key 0's maxHitsPerKey quota and real key-0 hits are silently dropped from the top hits

Bug LFV-690 · Area: misc / search: DiversifiedTopDocsCollector and DiversifiedTopDocsCollectorManager (per-key diversity cap) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-690.html](../../bugs/LFV-690.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass DiversifiedTopDocsCollector to get the top hits with at most maxHitsPerKey hits per source, and some matching documents have no value in the key field; you expect such a document not to use up the quota of any real source. Instead insert gives it key 0 whenever getKeys.advanceExact returns false (DiversifiedTopDocsCollector.java:146-151), and the manager's reduce counts by that same key (DiversifiedTopDocsCollectorManager.java:77-89), so with one keyless and one key-0 document, numHits 2 and maxHitsPerKey 1, only one hit comes back. With ordinal keys, the pattern of upstream's own test, key 0 is the first value in sort order, so a song without an artist takes the slot of the alphabetically first artist.

The test shows that DiversifiedTopDocsCollector (through DiversifiedTopDocsCollectorManager) puts a document whose key field has no value into key 0's group, so it uses up key 0's maxHitsPerKey quota.

## Expected vs actual

Expected 2 hits for one keyless document and one seller-0 document with numHits 2 and maxHitsPerKey 1; actual 1 (`hits expected:<2> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/misc/search/TestDiversifiedMissingKey.java](src/org/apache/lucene/misc/search/TestDiversifiedMissingKey.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.search.TestDiversifiedMissingKey
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
