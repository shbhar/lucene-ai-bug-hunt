# ExitableDirectoryReader turns off filtered HNSW search on 9.x-format vector segments, so a filtered kNN query ignores the KnnSearchStrategy.Hnsw threshold even when the timeout never fires

Bug LFV-423 · Area: search / filtered kNN (ExitableDirectoryReader with the 9.x HNSW vectors readers in backward-codecs) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

On main you open an index whose vector segment was written by Lucene 9.8.0 (Lucene95 HNSW format), wrap the reader in ExitableDirectoryReader with a timeout that never fires, and run a KnnFloatVectorQuery (k=10) with a TermQuery filter that matches about 15% of the documents and the default search strategy. You expect the same graph walk and the same hits as on the unwrapped reader, because the wrapper only adds timeout checks and the documented threshold (60%; 0 never, 100 always) says the filtered HNSW search runs. Instead the wrapper hands the 9.x reader an anonymous Bits instead of the filter's BitSet, the reader therefore sizes the filter as 0 and never uses filtered search (even KnnSearchStrategy.Hnsw(100) returns exactly the Hnsw(0) hits), and 3 of the top 10 documents differ; every hit is still a valid, live filter match.

## Expected vs actual

- Expected: You expect the same graph walk and the same hits as on the unwrapped reader, because the wrapper only adds timeout checks and the documented threshold (60%; 0 never, 100 always) says the filtered HNSW search runs.
- Actual: The wrapper hands the 9.x reader an anonymous Bits instead of the filter's BitSet, the reader therefore sizes the filter as 0 and never uses filtered search (even KnnSearchStrategy.Hnsw(100) returns exactly the Hnsw(0) hits), and 3 of the top 10 documents differ; every hit is still a valid, live filter match.

## Files

- [src/org/apache/lucene/index/TestExitableFilteredKnnOldHnswFormat.java](src/org/apache/lucene/index/TestExitableFilteredKnnOldHnswFormat.java) (JUnit test)
- [fixture-src/MakeOldHnswIndex.java](fixture-src/MakeOldHnswIndex.java) (writes the fixture the test reads, as Run it says; not compiled with the test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- Before compiling, copy Lucene95RWHnswVectorsFormat.java and Lucene95HnswVectorsWriter.java, unchanged classes of Lucene's own test sources that the release jars do not contain, from lucene/backward-codecs/src/test/org/apache/lucene/backward_codecs/lucene95/ of the checkout into src/org/apache/lucene/backward_codecs/lucene95/ here.
- Fixture (optional): testIndexWrittenByLucene980 also reads hnsw-9.8.0.zip, an index of 5,000 16-dimensional vectors written by Lucene 9.8.0, from its package on the class path, and is skipped without it. This site ships no binary index files, so the recipe first makes it with fixture-src/MakeOldHnswIndex.java and lucene-core-9.8.0.jar (Maven Central) and puts it in src/, which the run puts on the class path.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

# optional fixture hnsw-9.8.0.zip, written by the Lucene 9.8.0 release: lucene-core-9.8.0.jar (Maven Central)
javac -d fixture-classes -cp lucene-core-9.8.0.jar fixture-src/MakeOldHnswIndex.java
java -cp fixture-classes:lucene-core-9.8.0.jar MakeOldHnswIndex fixture-index
(cd fixture-index && rm -f write.lock && zip -q -X ../src/org/apache/lucene/index/hnsw-9.8.0.zip *)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestExitableFilteredKnnOldHnswFormat
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

2 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
