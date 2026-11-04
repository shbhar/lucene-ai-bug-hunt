# SearcherTaxonomyManager.close() leaves the taxonomy reader open when closing the index reader throws, and a second close() cannot release it (since 4.3.0)

Finding LFV-1324 · Area: facet / taxonomy: SearcherTaxonomyManager (release of a SearcherAndTaxonomy pair in decRef) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a SearcherTaxonomyManager (any constructor, default settings) and close it while closing its index reader fails with an IOException, for example an I/O error as the reader closes its segment files, or a reader-closed listener that throws. You expect close() to report that IOException but still release both readers of the pair, as the already-opened-readers constructor promises ('the incoming readers will be closed when you call close'). Instead decRef releases the index reader first with no try/finally, so the exception skips the taxonomy reader's release: it stays open with refCount 1, and because the manager already counts as closed, a second close() does nothing.

## Expected vs actual

- Expected: You expect close() to report that IOException but still release both readers of the pair, as the already-opened-readers constructor promises ('the incoming readers will be closed when you call close').
- Actual: decRef releases the index reader first with no try/finally, so the exception skips the taxonomy reader's release: it stays open with refCount 1, and because the manager already counts as closed, a second close() does nothing.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCloseFailure.java](src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCloseFailure.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestSearcherTaxonomyManagerCloseFailure
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
