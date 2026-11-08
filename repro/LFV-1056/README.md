# With beamWidth = 1, which the HNSW formats accept, every HnswGraphBuilder sizes its level-0 merge queue min(beamWidth / 2, M * 3) = 0 and throws, so building any HNSW graph fails and a merge closes the IndexWriter (since 10.2.0)

Finding LFV-1056 · Area: core / HNSW graph construction (util/hnsw HnswGraphBuilder constructor; codecs/lucene99 Lucene99HnswVectorsFormat beamWidth range) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1056.html](../../bugs/LFV-1056.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure a vector field with an HNSW format and beamWidth = 1, the smallest value the format and the graph builder accept ('beamWidth must be positive'), index vectors and merge, and you expect it to work as it did up to 10.1.0, only with a poorer graph. Instead every HnswGraphBuilder constructor sizes its level-0 merge candidate queue as Math.min(beamWidth / 2, M * 3), which integer division makes 0, and the heap rejects capacity 0 ('initialCapacity must be &gt; 0 ... got: 0'), so no HNSW graph can be built at all. Small segments still flush without a graph, then the first merge that needs one throws and closes the IndexWriter, and the vector document that would push a segment past the graph threshold is rejected.

## Expected vs actual

- Expected: `forceMerge(1)` builds one segment with an HNSW graph, as with `beamWidth = 2` and as on 10.1.0.
- Actual: `IllegalArgumentException: initialCapacity must be > 0 and < 2147483630; got: 0` at `HnswGraphBuilder.<init>:195`, and the `IndexWriter` closes.

## Files

- [src/org/apache/lucene/codecs/lucene99/TestHnswBeamWidthOneMerge.java](src/org/apache/lucene/codecs/lucene99/TestHnswBeamWidthOneMerge.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene99.TestHnswBeamWidthOneMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHnswBeamWidthOneMerge.java](TestHnswBeamWidthOneMerge.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
