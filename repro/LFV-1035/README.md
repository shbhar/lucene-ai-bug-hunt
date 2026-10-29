# Luke: Search.nextPage after a full last page returns an empty page with offset 0 instead of Optional.empty when the hit total is a lower bound, so with default settings and more than 1,000 hits the Search tab ends on an empty page with 'previous' disabled

Bug LFV-1035 · Area: luke / Search model paging session (SearchImpl.nextPage, SearchResults.of) behind the Luke Search tab · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index in Luke, run a search that matches more than 1,000 documents with the default settings and click 'next' up to the last page; when that page is full (for example 2,000 hits), the shown total is a lower bound ('2000+'), so 'next' stays enabled, and you expect one more click to do nothing, as Search.nextPage's javadoc says ('search results, or empty if there are no more results'). Instead nextPage returns a page with no hits whose offset is 0: the results table empties, the range reads '1 - 0' and 'previous' is disabled, so the earlier pages can only be reached by running the search again. The cause is that nextPage moves its page counter before it knows the page exists and checks for the end only when the total is exact, and SearchResults.of sets the offset only inside its loop over the hits.

## Expected vs actual

- Expected: You expect one more click to do nothing, as Search.nextPage's javadoc says ('search results, or empty if there are no more results').
- Actual: nextPage returns a page with no hits whose offset is 0: the results table empties, the range reads '1 - 0' and 'previous' is disabled, so the earlier pages can only be reached by running the search again.

## Files

- [src/org/apache/lucene/luke/models/search/TestNextPagePastLastPage.java](src/org/apache/lucene/luke/models/search/TestNextPagePastLastPage.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/luke/build/libs/lucene-luke-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.luke.models.search.TestNextPagePastLastPage
```

The module(s) luke also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
