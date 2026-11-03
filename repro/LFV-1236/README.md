# DisjunctionMaxQuery treats tie-breakers 0.0f and -0.0f as equal in equals() (IEEE ==) but hashes their raw bits in hashCode(), so two equal queries get different hash codes and the query cache, BooleanQuery clause sets and other hash-based structures treat them as different queries (since 1.9)

Finding LFV-1236 · Area: core / search: DisjunctionMaxQuery (equals and hashCode contract) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1236.html](../../bugs/LFV-1236.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build DisjunctionMaxQuery([f:x], 0.0f) and DisjunctionMaxQuery([f:x], -0.0f): equals returns true both ways (the constructor accepts -0.0f, and equalsTo compares the tie-breakers with ==, under which 0.0f == -0.0f), so by the Object#hashCode contract, which Query's javadoc requires so that QueryCache works, the two hash codes must be equal. Instead they differ by exactly 2^31, because hashCode adds Float.floatToIntBits(tieBreakerMultiplier), which is 0x00000000 for 0.0f and 0x80000000 for -0.0f. Hash-based structures therefore see two different queries: the query cache keeps a separate entry, a HashSet holds both and a BooleanQuery with both as FILTER clauses is not de-duplicated, while hits and scores stay the same.

## Expected vs actual

- Expected: `equals` and `hashCode` agree: equal queries have equal hash codes. Today `a.equals(b)` is true, so `a.hashCode()` must equal `b.hashCode()`.
- Actual: `AssertionError: expected:<74785687> but was:<-2072697961>` (through gradle, seed DEADBEEF; other run configurations give other values, always 2^31 apart): equal queries with different hash codes.

## Files

- [src/org/apache/lucene/search/TestDisjunctionMaxQueryNegativeZeroTie.java](src/org/apache/lucene/search/TestDisjunctionMaxQueryNegativeZeroTie.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestDisjunctionMaxQueryNegativeZeroTie
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDisjunctionMaxQueryNegativeZeroTie.java](TestDisjunctionMaxQueryNegativeZeroTie.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
