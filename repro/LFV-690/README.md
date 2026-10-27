# DiversifiedTopDocsCollector and its manager put every document without a key into key 0's group, so a keyless document uses up key 0's maxHitsPerKey quota and real key-0 hits are silently dropped from the top hits

Bug LFV-690 · Area: misc / search: DiversifiedTopDocsCollector and DiversifiedTopDocsCollectorManager (per-key diversity cap) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-690.html](../../bugs/LFV-690.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass DiversifiedTopDocsCollector to get the top hits with at most maxHitsPerKey hits per source, and some matching documents have no value in the key field; you expect such a document not to use up the quota of any real source. Instead insert gives it key 0 whenever getKeys.advanceExact returns false (DiversifiedTopDocsCollector.java:146-151), and the manager's reduce counts by that same key (DiversifiedTopDocsCollectorManager.java:77-89), so with one keyless and one key-0 document, numHits 2 and maxHitsPerKey 1, only one hit comes back. With ordinal keys, the pattern of upstream's own test, key 0 is the first value in sort order, so a song without an artist takes the slot of the alphabetically first artist.

## Expected vs actual

- Expected: `[no-artist, abba, zappa]` and `[no-seller, seller-0]`: every document comes back, as it does with seller 1 instead of seller 0.
- Actual: `[no-artist, zappa]` and `[no-seller]`: ABBA's song and seller 0's product are dropped, `totalHits` is still 3 and 2, and 2 of 4 tests fail.

## Files

- [src/org/apache/lucene/misc/search/TestDiversifiedMissingKey.java](src/org/apache/lucene/misc/search/TestDiversifiedMissingKey.java) (JUnit test)

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

## Files shown on the bug page

- [TestDiversifiedTopDocsCollectorMissingKey.java](TestDiversifiedTopDocsCollectorMissingKey.java)
- [TestDiversifiedMissingKey.java](TestDiversifiedMissingKey.java)
- [TestDiversifiedNoKeyCollector.java](TestDiversifiedNoKeyCollector.java)
- [TestDiversifiedNoKeyManager.java](TestDiversifiedNoKeyManager.java)
- [TestDiversifiedMissingKeyIntent.java](TestDiversifiedMissingKeyIntent.java)
- [TestDiversifiedMissingKeyManager.java](TestDiversifiedMissingKeyManager.java)
- [HistMissingKeyIterator.java](HistMissingKeyIterator.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
