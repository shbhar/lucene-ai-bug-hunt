# BloomFilteringPostingsFormat's default filter silently stops filtering for segments of 226,279,579 docs or more: FuzzySet.createOptimalSet computes 2 * setSize in int, which wraps negative and yields a 64-bit filter with zero hash functions that answers MAYBE for every key (released since 9.6.0)

Finding LFV-362 · Area: codecs / bloom (BloomFilteringPostingsFormat, DefaultBloomFilterFactory, FuzzySet sizing) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by Greg Miller (gsmiller; PR #17019, approved by goankur on 2026-11-25, merged and cherry-picked to branch_11x by its author on 2026-11-25) on 2026-11-25 (https://github.com/apache/lucene/pull/17019). Greg Miller (gsmiller) fixed it in PR #17019 (opened 2026-11-11): FuzzySet.createOptimalSet caps the set at 2^30 bits and keeps at least one hash function, and createSetBasedOnMaxMemory multiplies in long. goankur approved the PR on 2026-11-25; its author merged it into main the same day as 8506598d7a9 and cherry-picked it to branch_11x the same day (c83d24b7b44). Not on branch_10_6 (head 451a69c1d4a, 2026-11-20) when read on 2026-11-25. From our runs of 2026-11-25, each with and without assertions: the minimal repro passes on main 8506598d7a98 and on branch_11x c83d24b7b448 and 691f4adb27f4; on the commits before them (main 1640b5c394de, branch_11x 8a72db074350) and on the 10.6.0 release it fails; with FuzzySet.java from before the fix put back on main 8506598d7a98 it fails again. Below 226,279,579 docs the default factory's sizing is unchanged. This site files nothing upstream.

Fixed upstream: [#17019](https://github.com/apache/lucene/pull/17019) (commit [8506598d7a98](https://github.com/apache/lucene/commit/8506598d7a98d8d5336972d9d970e588500e2619)), merged 2026-11-25; also on branch_11x ([c83d24b7b448](https://github.com/apache/lucene/commit/c83d24b7b448)).

## What it shows

You index a primary key into a field that uses BloomFilteringPostingsFormat with the default filter factory, whose javadoc promises a filter targeting about 10% false positives sized from the segment's doc count, and you expect lookups of absent keys to be rejected quickly in every segment. For a segment of 226,279,579 docs or more, FuzzySet.createOptimalSet's int expression 2 * setSize wraps negative, the size lookup falls back to its smallest size (63) and the hash count rounds to 0, so the filter has 64 bits, never sets one and answers MAYBE for every key, and the saturation check meant to retire useless filters sees 0% saturation and saves it. Results stay correct, but every absent-key lookup on that segment goes to the terms dictionary: the repro's filter rejects 0 of 1,000 absent keys, while one doc fewer gives a 2^30-bit filter with 3 hash functions that rejected 2,000 of 2,000 in an independent test.

## Expected vs actual

- Expected: You expect lookups of absent keys to be rejected quickly in every segment.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/codecs/bloom/TestBloomFilterLargeSegment.java](src/org/apache/lucene/codecs/bloom/TestBloomFilterLargeSegment.java) (JUnit test)
- [src/org/apache/lucene/codecs/bloom/TestOptimalSetLargeSegment.java](src/org/apache/lucene/codecs/bloom/TestOptimalSetLargeSegment.java) (JUnit test)
- [src/FarmScaleBloom.java](src/FarmScaleBloom.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- Upstream fixed this finding on 2026-11-25: #17019 was merged into main as 8506598d7a98 and cherry-picked to branch_11x as c83d24b7b448 (see Fixed upstream above). On builds with the fix both test classes pass: run on a build of main 8506598d7a98 (set V=12.0.0-SNAPSHOT) and of branch_11x 55ef56a69344 (V as printed), each class reports 2 tests found and 2 successful. To see the finding, run the commands on a build without the fix: on builds of branch_11x 8a72db074350 (V as printed) and of main 1640b5c394de (V=12.0.0-SNAPSHOT), the commits before the cherry-pick and before the fix, each class reports 2 tests found and 1 failed, with a message that ends 'rejected 0/1000; filter=FuzzySet(k=0, bits=0/64)'. Without a build: put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar and lucene-test-framework-10.6.0.jar (Maven Central, org.apache.lucene) in place of the three $LUCENE jars and set V=10.6.0; the 10.6.0 release fails the same two tests. Each of these runs, made on 2026-11-25, gave the same result with and without assertions. The last command starts FarmScaleBloom without its three arguments (a directory, the number of documents and the number of threads); it is the end-to-end program (it indexes that many documents with one unique id each, calls forceMerge(1), prints each segment's filter file size and exits 1 when a segment of 226,279,579 docs or more has a filter file under 1 KiB); with its arguments it was not run on 2026-11-25.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: it no longer reproduces there; in the commands below, V=12.0.0-SNAPSHOT on a build of main 82cf7465bb8f.
- How to read the outcome: a failing test is the finding reproducing (its message says how many of 1,000 absent keys the filter rejected); FarmScaleBloom, started as printed without its three arguments, stops with ArrayIndexOutOfBoundsException at the first statement of its main method, with the fix or without, and that is not the finding.
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
