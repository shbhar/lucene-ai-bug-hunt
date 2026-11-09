# MatchesUtils.disjunction and the Matches of a BooleanQuery or DisjunctionMaxQuery throw IllegalArgumentException('Failed to retrieve term offset') instead of the declared IOException when a sub-iterator without positions fails to read an offset, because DisjunctionMatchesIterator's PriorityQueue comparator wraps it (since 9.11.1)

Finding LFV-1424 · Area: core / search: Matches API (DisjunctionMatchesIterator, used by MatchesUtils.disjunction and by the Matches of BooleanQuery and DisjunctionMaxQuery) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1424.html](../../bugs/LFV-1424.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge two match iterators that report no positions (startPosition() == -1, which MatchesIterator documents as 'positions are not available') with MatchesUtils.disjunction, or ask Weight#matches of a BooleanQuery or DisjunctionMaxQuery for such matches, and one iterator's startOffset() throws IOException. You expect that IOException: startOffset()/endOffset(), next(), MatchesUtils.disjunction, Matches.getMatches and Weight.matches all declare it, and 9.11.0 delivers it. Instead you get java.lang.IllegalArgumentException: Failed to retrieve term offset (with the IOException as its cause), because DisjunctionMatchesIterator orders such iterators by offset in a PriorityQueue comparator lambda, which cannot throw a checked exception and rethrows it as IllegalArgumentException (DisjunctionMatchesIterator.java:202), both while the iterator is built and in next().

## Expected vs actual

- Expected: `MatchesUtils.disjunction(...)` throws the sub-iterator's `java.io.IOException: offset read failed`.
- Actual: It throws `java.lang.IllegalArgumentException: Failed to retrieve term offset`, from `DisjunctionMatchesIterator.java:202`.

## Files

- [src/org/apache/lucene/search/TestDisjunctionMatchesOffsetIOException.java](src/org/apache/lucene/search/TestDisjunctionMatchesOffsetIOException.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestDisjunctionMatchesOffsetIOException
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDisjunctionMatchesOffsetIOException.java](TestDisjunctionMatchesOffsetIOException.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
