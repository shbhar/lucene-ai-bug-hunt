# DrillSideways with an ExecutorService reports every failure of its drill-down and drill-sideways searches as RuntimeException(ExecutionException(cause)), while sequential DrillSideways, and IndexSearcher with an executor since 9.8.0, throw the cause itself (for example IndexSearcher.TooManyClauses, ExitableDirectoryReader.ExitingReaderException or the declared IOException)

Bug LFV-636 · Area: facet / DrillSideways concurrent mode (ExecutorService constructor, searchConcurrently) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You construct DrillSideways with an ExecutorService so that its drill-down and drill-sideways searches run concurrently, and one of those searches fails, for example because the base query has more clauses than IndexSearcher's clause limit, an ExitableDirectoryReader timeout fires, or a query or collector throws IOException. You expect the exception the sequential DrillSideways throws for the same search: the class javadoc presents the two modes as alternative implementations that differ in speed and duplicate work, every search overload declares IOException, and IndexSearcher has rethrown task failures as-is since 9.8.0 (GITHUB#12516) precisely so that sequential and concurrent searches throw the same exceptions. Instead DrillSideways.searchConcurrently catches the ExecutionException from Future.get and throws new RuntimeException(e), so the caller receives RuntimeException -&gt; ExecutionException -&gt; the original exception, and catch blocks for TooManyClauses, ExitingReaderException or IOException do not fire.

## Expected vs actual

- Expected: You expect the exception the sequential DrillSideways throws for the same search: the class javadoc presents the two modes as alternative implementations that differ in speed and duplicate work, every search overload declares IOException, and IndexSearcher has rethrown task failures as-is since 9.8.0 (GITHUB#12516) precisely so that sequential and concurrent searches throw the same exceptions.
- Actual: DrillSideways.searchConcurrently catches the ExecutionException from Future.get and throws new RuntimeException(e), so the caller receives RuntimeException -&gt; ExecutionException -&gt; the original exception, and catch blocks for TooManyClauses, ExitingReaderException or IOException do not fire.

## Files

- [src/org/apache/lucene/facet/TestDrillSidewaysConcurrentExceptionType.java](src/org/apache/lucene/facet/TestDrillSidewaysConcurrentExceptionType.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestDrillSidewaysConcurrentExceptionType
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
