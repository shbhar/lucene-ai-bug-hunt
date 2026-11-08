# DocIdSetBuilder.build() on an empty builder with maxDoc 0 throws IllegalArgumentException 'maxValue must be non-negative (got: -1)' instead of returning the empty set, because the sparse path always evaluates PackedInts.bitsRequired(maxDoc - 1), so FacetsCollector fails on a zero-document leaf (regression in 6.1.0)

Finding LFV-1215 · Area: core / util (DocIdSetBuilder sparse build; reached from facet / FacetsCollector) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1215.html](../../bugs/LFV-1215.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a DocIdSetBuilder for an empty doc-ID range (new DocIdSetBuilder(0), which FacetsCollector does for every leaf as new DocIdSetBuilder(context.reader().maxDoc())), add nothing and call build(), and expect the empty set, as the same class already returns for maxDoc 0 on its dense path and for every maxDoc &gt;= 1. Instead build() throws IllegalArgumentException 'maxValue must be non-negative (got: -1)', because its sparse branch always computes the radix sort width as PackedInts.bitsRequired(maxDoc - 1) = bitsRequired(-1), even though there is nothing to sort. So faceting over a reader that contains a zero-document leaf (for example an empty ParallelLeafReader) fails instead of counting the other leaves; Lucene 6.0.x returned the empty set.

## Expected vs actual

- Expected: `build()` returns the empty set: the first `nextDoc()` is `NO_MORE_DOCS`.
- Actual: `IllegalArgumentException: maxValue must be non-negative (got: -1)` at `PackedInts.bitsRequired(PackedInts.java:805)` &lt;- `DocIdSetBuilder.build(DocIdSetBuilder.java:322)`.

## Files

- [src/org/apache/lucene/util/TestDocIdSetBuilderZeroMaxDocBuild.java](src/org/apache/lucene/util/TestDocIdSetBuilderZeroMaxDocBuild.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestDocIdSetBuilderZeroMaxDocBuild
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDocIdSetBuilderZeroMaxDocBuild.java](TestDocIdSetBuilderZeroMaxDocBuild.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
