# QueryNodeProcessorPipeline's listIterator/subList views, and replaceAll / Collections.replaceAll built on them, put processors on the pipeline without its QueryConfigHandler, so the flexible query parser silently skips point, boost and default phrase slop handling or throws NullPointerException

Bug LFV-966 · Area: queryparser / flexible core: QueryNodeProcessorPipeline (java.util.List views and default methods) · Priority: unranked · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-966.html](../../bugs/LFV-966.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You customise a flexible query parser pipeline, for example StandardQueryParser's, by inserting or replacing a processor through the pipeline's java.util.List API: pipeline.listIterator then add or set, subList(..).set, pipeline.replaceAll or Collections.replaceAll. You expect the processor to receive the pipeline's QueryConfigHandler, as it does with add(k, p) or set(k, p), because the class says it 'takes care of setting this QueryConfigHandler on all processor on pipeline'. Instead those paths write through the private ArrayList's own views and leave the processor with a null handler, so a replaced PointQueryNodeProcessor turns n:5 into a TermQuery that finds 0 of 1 matching documents, a replaced BoostQueryNodeProcessor drops field boosts, and most other stock processors throw NullPointerException at parse.

## Expected vs actual

- Expected: `n:5` parses to `n:[5 TO 5]` and finds the document, `f:foo` keeps its boost as `(f:foo)^2.0`, and the inserted processor carries the pipeline's config.
- Actual: `n:5` is a `TermQuery` with 0 hits, `f:foo` loses its boost through `replaceAll` and `subList`, and the inserted processor's config is `null`: 4 of 5 tests fail.

## Files

- [src/org/apache/lucene/queryparser/flexible/core/processors/TestPipelineListViewsConfigHandler.java](src/org/apache/lucene/queryparser/flexible/core/processors/TestPipelineListViewsConfigHandler.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.core.processors.TestPipelineListViewsConfigHandler
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestPipelineListViews.java](TestPipelineListViews.java)
- [TestPipelineListViewsConfigHandler.java](TestPipelineListViewsConfigHandler.java)
- [TestPipelineViewEditsV1.java](TestPipelineViewEditsV1.java)
- [PipelineInsertAfterMain.java](PipelineInsertAfterMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
