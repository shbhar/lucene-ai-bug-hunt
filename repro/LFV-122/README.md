# SortedNumericSortField's class javadoc on main says missing values sort first or last via SortField.STRING_FIRST / STRING_LAST, but a numeric sort cannot use these string sentinels: the constructor accepts them, then every search or index sort with them throws ClassCastException

Bug LFV-122 · Area: search / sorting (SortedNumericSortField missing values; javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You follow the SortedNumericSortField class javadoc on main ('Like sorting by string, this also supports sorting missing values as first or last, via SortField#STRING_FIRST or SortField#STRING_LAST') and build new SortedNumericSortField("f", SortField.Type.LONG, false, SortedNumericSelector.Type.MIN, SortField.STRING_LAST). You expect documents without a value to sort last, or, if the sentinels are not supported, the constructor to reject them. Instead the constructor accepts the sentinel, and IndexSearcher.search with that sort, or IndexWriterConfig.setIndexSort with it, throws ClassCastException 'SortField$2 cannot be cast to class java.lang.Long', because a numeric sort needs a missing value of its own numeric type (for example Long.MAX_VALUE to sort missing documents last), as MIGRATE.md and the upstream tests show.

The test shows (javadoc defect) that the `SortedNumericSortField` class javadoc says missing values can sort last via `SortField.STRING_LAST`, but a LONG sort built with it fails at search time.

## Expected vs actual

Expected: the search sorts the document that has no value (or the constructor rejects the sentinel). Actual: the constructor accepts it and `IndexSearcher.search` throws `ClassCastException` (`SortField$2` cannot be cast to `Long`).

## Files

- [src/org/apache/lucene/search/TestSortedNumericSortFieldStringLast.java](src/org/apache/lucene/search/TestSortedNumericSortFieldStringLast.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSortedNumericSortFieldStringLast
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
