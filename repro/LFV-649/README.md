# MinHashFilter with hashSetSize &gt;= 2 drops one of the kept lowest hash values when a token repeats while its (hash, bucket) set is full, so the min-hash signature depends on token repeats and order instead of only on the token set ('b a a' keeps 1 of 2 values; 'c a c b' and 'c a b c' give different signatures)

Bug LFV-649 · Area: analysis-common / minhash (MinHashFilter.FixedSizeTreeSet bottom-k set; MinHashFilterFactory 'minHash') · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyse 'b a a' with MinHashFilter(hashCount 1, bucketCount 1, hashSetSize 2) and expect the same two min hashes as for 'b a', because the filter keeps 'the N lowest values' of the tokens' hashes (MinHashFilter.java:38-40; hashSetSize is 'the no. of min hashes to keep') and the lowest values of a token set do not change when a token repeats. Instead, FixedSizeTreeSet.add removes the full set's largest value (pollLast) before TreeSet.add finds that the repeated value is already there, so 'b a a' gives one value; a later new token, however large its hash, then fills the free slot ('c a c b' keeps the hashes of c and b, not the true lowest two, c and a), and the same tokens in another order ('c a b c') give yet another signature. In the javadoc's '1 hash and keep the 100 lowest values' setting with 5-word shingles, a 150-word text read twice has the same shingle set as the text read once but a signature of 99 values that misses one of the 100 lowest.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: FixedSizeTreeSet.add removes the full set's largest value (pollLast) before TreeSet.add finds that the repeated value is already there, so 'b a a' gives one value; a later new token, however large its hash, then fills the free slot ('c a c b' keeps the hashes of c and b, not the true lowest two, c and a), and the same tokens in another order ('c a b c') give yet another signature.

## Files

- [src/org/apache/lucene/analysis/minhash/TestMinHashFilterRepeatedToken.java](src/org/apache/lucene/analysis/minhash/TestMinHashFilterRepeatedToken.java) (JUnit test)
- [src/MinHashRepeatedTokenMain.java](src/MinHashRepeatedTokenMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.minhash.TestMinHashFilterRepeatedToken

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" MinHashRepeatedTokenMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
