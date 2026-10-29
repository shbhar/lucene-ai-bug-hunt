# SearcherTaxonomyManager refresh calls t2.addSuppressed(t2), so when the taxonomy reopen fails and closing the just-reopened index reader also fails, maybeRefresh throws IllegalArgumentException ('Self-suppression not permitted') and the taxonomy failure is lost

Bug LFV-264 · Area: facet / taxonomy (SearcherTaxonomyManager.refreshIfNeeded exception path) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use SearcherTaxonomyManager, and one refresh hits two I/O failures at once: reopening the taxonomy fails, and closing the index reader that was just reopened also fails. You expect maybeRefresh to throw the taxonomy failure as an IOException, its documented contract, with the close failure attached as a suppressed exception. Instead it throws IllegalArgumentException('Self-suppression not permitted'), because the cleanup code calls t2.addSuppressed(t2) where t1.addSuppressed(t2) was meant, so the taxonomy failure is lost entirely and callers that catch IOException do not see it.

The test shows that when one SearcherTaxonomyManager refresh fails twice (the taxonomy reopen fails, then closing the just-reopened index reader fails), maybeRefreshBlocking throws the wrong exception because the cleanup calls `t2.addSuppressed(t2)`.

## Expected vs actual

Expected the taxonomy's `IndexNotFoundException` (as upstream testExceptionDuringRefresh asserts); actual: `IllegalArgumentException: Self-suppression not permitted`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerDoubleFailure.java](src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerDoubleFailure.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestSearcherTaxonomyManagerDoubleFailure
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/nrt-reference-managers/](../../models/nrt-reference-managers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
