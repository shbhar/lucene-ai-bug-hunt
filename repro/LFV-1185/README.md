# SeededKnnVectorQuery built with its public seed-Weight constructor makes IndexSearcher.search throw IllegalArgumentException (or EOFException on a DENSE block) when a seed hit has no vector, because MappedDISI advances the vector iterator to a doc at or before its current one and the entry-point count includes seeds without a vector (since 10.3.0)

Finding LFV-1185 · Area: search / kNN vector queries (SeededKnnVectorQuery seed-to-ordinal mapping: MappedDISI and the entry-point count) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1185.html](../../bugs/LFV-1185.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 2,000 documents with a float vector on the even ones only, build a seed Weight with IndexSearcher.createWeight for a term query that matches doc 1997 (no vector) and doc 1998 (the last vector doc), and search new SeededKnnVectorQuery(new KnnFloatVectorQuery("v", q, 2), seed, seedWeight) for 2 hits. You expect 2 hits, which SeededKnnVectorQuery.fromFloatQuery returns for the same seed, and the class javadoc only says the format may ignore a seed. Instead search throws IllegalArgumentException 'The number of entry points provided is less than the number of entry points requested': the caller's Weight skips the FieldExistsQuery filter that the internal path adds, MappedDISI maps doc 1997 to vector doc 1998 and then calls advance(1998) on an iterator already at 1998 (undefined for a DocIdSetIterator), which runs it off the end, while the entry-point count still counts both seeds.

## Expected vs actual

- Expected: `searcher.search(...)` returns 2 hits.
- Actual: `IllegalArgumentException: The number of entry points provided is less than the number of entry points requested` at `SeededHnswGraphSearcher.fromEntryPoints(SeededHnswGraphSearcher.java:48)`, assertions on and off.

## Files

- [src/org/apache/lucene/search/TestSeededKnnSeedWithoutVector.java](src/org/apache/lucene/search/TestSeededKnnSeedWithoutVector.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSeededKnnSeedWithoutVector
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSeededKnnSeedWithoutVector.java](TestSeededKnnSeedWithoutVector.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
