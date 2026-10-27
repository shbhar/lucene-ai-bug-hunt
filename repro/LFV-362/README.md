# BloomFilteringPostingsFormat's default filter silently stops filtering for segments of 226,279,579 docs or more: FuzzySet.createOptimalSet computes 2 * setSize in int, which wraps negative and yields a 64-bit filter with zero hash functions that answers MAYBE for every key (released since 9.6.0)

Bug LFV-362 · Area: codecs / bloom (BloomFilteringPostingsFormat, DefaultBloomFilterFactory, FuzzySet sizing) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a primary key into a field that uses BloomFilteringPostingsFormat with the default filter factory, whose javadoc promises a filter targeting about 10% false positives sized from the segment's doc count, and you expect lookups of absent keys to be rejected quickly in every segment. For a segment of 226,279,579 docs or more, FuzzySet.createOptimalSet's int expression 2 * setSize wraps negative, the size lookup falls back to its smallest size (63) and the hash count rounds to 0, so the filter has 64 bits, never sets one and answers MAYBE for every key, and the saturation check meant to retire useless filters sees 0% saturation and saves it. Results stay correct, but every absent-key lookup on that segment goes to the terms dictionary: the repro's filter rejects 0 of 1,000 absent keys, while one doc fewer gives a 2^30-bit filter with 3 hash functions that rejected 2,000 of 2,000 in an independent test.

## Expected vs actual

- Expected: You expect lookups of absent keys to be rejected quickly in every segment.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/codecs/bloom/TestBloomFilterLargeSegment.java](src/org/apache/lucene/codecs/bloom/TestBloomFilterLargeSegment.java) (JUnit test)
- [src/org/apache/lucene/codecs/bloom/TestOptimalSetLargeSegment.java](src/org/apache/lucene/codecs/bloom/TestOptimalSetLargeSegment.java) (JUnit test)
- [src/FarmScaleBloom.java](src/FarmScaleBloom.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.codecs.bloom.TestBloomFilterLargeSegment

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.bloom.TestOptimalSetLargeSegment
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" FarmScaleBloom
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
