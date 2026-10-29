# MultiPhraseQuery.Builder does not validate an empty Term: it throws ArrayIndexOutOfBoundsException at the first position but accepts one at later positions, and toString of the accepted query then throws, which also breaks explain and the 'indexed without position data' error

Bug LFV-963 · Area: core / search (MultiPhraseQuery.Builder input validation and MultiPhraseQuery.toString) · Priority: unranked · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a phrase with MultiPhraseQuery.Builder and pass an empty Term for one position, for example the terms of a prefix expansion that found nothing (the class javadoc's 'microsoft app*' recipe); add(Term) documents the array as a disjunction, so you expect an empty position to match nothing, or the Builder to reject it with IllegalArgumentException. At the first position Builder.add throws ArrayIndexOutOfBoundsException (it reads terms[0].field, MultiPhraseQuery.java:127), while at any later position the array is accepted and search correctly returns 0 hits, but toString throws ArrayIndexOutOfBoundsException (terms[0].text,:345). So logging the query fails, IndexSearcher.explain throws for a BooleanQuery or IndriAndQuery that holds it (BooleanWeight.java:99 and:103, IndriAndWeight.java:88), and on a field indexed without positions the documented IllegalStateException becomes that exception, because its message prints the query (:255-262).

## Expected vs actual

- Expected: You expect an empty position to match nothing, or the Builder to reject it with IllegalArgumentException.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestMultiPhraseQueryEmptyPosition.java](src/org/apache/lucene/search/TestMultiPhraseQueryEmptyPosition.java) (JUnit test)
- [src/MpqEmptyPositionMain.java](src/MpqEmptyPositionMain.java) (main() class)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestMultiPhraseQueryEmptyPosition

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" MpqEmptyPositionMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
