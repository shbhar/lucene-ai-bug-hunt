# Luke: Optimize and Add document open an IndexWriter without the index's sort, so on a sorted index they silently write unsorted segments and the application's IndexWriter configured with that sort then refuses to open the index

Bug LFV-1042 · Area: luke / IndexTools write operations (IndexToolsImpl.optimize and addDocument through IndexUtils.createWriter) behind Tools > Optimize and Documents > Add document; IndexWriter index-sort validation · Priority: low · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a sorted index (written with IndexWriterConfig.setIndexSort) in Luke with the default options and run Tools &gt; Optimize or Documents &gt; Add document, and you expect a force merge or one more document, with the index's sort unchanged. Instead Luke opens its IndexWriter with a config that has no index sort, which IndexWriter accepts on a sorted index, so the merged or flushed segment records indexSort=null and Luke reports success. Afterwards the application's own IndexWriter, configured with the same sort, refuses to open the index: IllegalArgumentException 'cannot change previous indexSort=null (from segment=_2(11.0.0):c2) to new indexSort=&lt;long: "ts"&gt;'.

## Expected vs actual

- Expected: You expect a force merge or one more document, with the index's sort unchanged.
- Actual: Luke opens its IndexWriter with a config that has no index sort, which IndexWriter accepts on a sorted index, so the merged or flushed segment records indexSort=null and Luke reports success.

## Files

- [src/org/apache/lucene/luke/models/tools/TestIndexToolsKeepsIndexSort.java](src/org/apache/lucene/luke/models/tools/TestIndexToolsKeepsIndexSort.java) (JUnit test)

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
     execute --select-class org.apache.lucene.luke.models.tools.TestIndexToolsKeepsIndexSort
```

The module(s) luke also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
