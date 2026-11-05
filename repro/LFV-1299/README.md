# PatienceKnnVectorQuery treats saturation thresholds 0.0 and -0.0 as equal in equals() (IEEE ==) but hashes the double's bits in hashCode(), so two equal queries get hash codes 2^31 apart and hash sets, maps and composite queries that hold them treat them as different queries (every release since 10.2.0)

Finding LFV-1299 · Area: core / search: PatienceKnnVectorQuery (equals and hashCode contract) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1299.html](../../bugs/LFV-1299.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap the same kNN query twice with PatienceKnnVectorQuery.fromFloatQuery, once with saturation threshold 0.0 and once with -0.0. equals() returns true (it compares the thresholds with ==, under which 0.0 == -0.0), so by the Object#hashCode contract, which Query's javadoc asks subclasses to honour so that QueryCache works, the two hash codes must be equal. Instead they differ by exactly 2^31 (1050736867 and -1096746781 in the repro), because hashCode() is Objects.hash(super.hashCode(), saturationThreshold, patience, delegate), which hashes the double's bits, so a HashSet holding one query does not find the other, while search results stay the same.

## Expected vs actual

- Expected: `a.equals(b)` is true, so `a.hashCode()` equals `b.hashCode()`.
- Actual: `AssertionError: expected:<1050736867> but was:<-1096746781>`: equal queries whose hash codes are 2^31 apart.

## Files

- [src/org/apache/lucene/search/TestPatienceKnnSignedZeroThreshold.java](src/org/apache/lucene/search/TestPatienceKnnSignedZeroThreshold.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPatienceKnnSignedZeroThreshold
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPatienceKnnSignedZeroThreshold.java](TestPatienceKnnSignedZeroThreshold.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
