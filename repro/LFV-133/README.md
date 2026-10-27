# (Latent) FixedBitSet.approximateCardinality returns a negative value for bit sets of 2^31-63 bits or more, because its scale factor 64 * numWords overflows int

Bug LFV-133 · Area: util / FixedBitSet (approximateCardinality) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a FixedBitSet of 2^31-63 to 2^31-1 bits (for example new FixedBitSet(Integer.MAX_VALUE), a legal length: the class javadoc points to LongBitSet only for more than 2.1B bits), set some bits and call approximateCardinality; you expect a non-negative estimate of the number of set bits, as BitSet documents ('an approximation of the cardinality of this set'). Instead it returns a negative number (-2147418112 with all 2^31-1 bits set, -64 with a single set bit), because the scale factor (interval / rangeLength) * numWords / maxWord is computed in int and its product 64 * numWords wraps to Integer.MIN_VALUE once the set has 2^25 words; new BitDocIdSet(bits) then throws IllegalArgumentException 'cost must be &gt;= 0'. No Lucene code path builds a bit set that long (doc-ID sets stop at IndexWriter.MAX_DOCS = 2^31-129 bits), so only external code that uses FixedBitSet directly over another int domain is affected.

## Expected vs actual

- Expected: You expect a non-negative estimate of the number of set bits, as BitSet documents ('an approximation of the cardinality of this set').
- Actual: It returns a negative number (-2147418112 with all 2^31-1 bits set, -64 with a single set bit), because the scale factor (interval / rangeLength) * numWords / maxWord is computed in int and its product 64 * numWords wraps to Integer.MIN_VALUE once the set has 2^25 words; new BitDocIdSet(bits) then throws IllegalArgumentException 'cost must be &gt;= 0'.

## Files

- [src/org/apache/lucene/util/TestFixedBitSetApproximateCardinalityLarge.java](src/org/apache/lucene/util/TestFixedBitSetApproximateCardinalityLarge.java) (JUnit test)

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
java -ea -esa -Xmx3g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestFixedBitSetApproximateCardinalityLarge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/bitsets-livedocs-acceptdocs/](../../models/bitsets-livedocs-acceptdocs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
