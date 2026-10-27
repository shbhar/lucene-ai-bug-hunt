# SegmentOrder.fromSort(numeric sort).reorder(reader) throws NullPointerException when a leaf's getCoreCacheHelper returns null, which LeafReader documents as legal (ParallelCompositeReader over two or more readers, SortingCodecReader or MemoryIndex leaves, content-altering FilterLeafReaders)

Bug LFV-296 · Area: core / index: SegmentOrder (query-time segment re-ordering) and leaf core cache helpers · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call SegmentOrder.fromSort(new Sort(new SortField("f", SortField.Type.LONG, true))).reorder(reader) to visit the segments with the highest f values first, on a reader whose leaves do not support caching, for example a ParallelCompositeReader over two DirectoryReaders with two segments each. You expect a re-ordered view, because reorder accepts any IndexReader and LeafReader#getCoreCacheHelper documents null as a legal return value ('not suited for caching') that other consumers such as LRUQueryCache and QueryBitSetProducer simply skip. Instead reorder throws NullPointerException at SegmentOrder.java:137, where the leaf comparator's memo calls reader.getCoreCacheHelper.getKey without a null check.

## Expected vs actual

- Expected: You expect a re-ordered view, because reorder accepts any IndexReader and LeafReader#getCoreCacheHelper documents null as a legal return value ('not suited for caching') that other consumers such as LRUQueryCache and QueryBitSetProducer simply skip.
- Actual: reorder throws NullPointerException at SegmentOrder.java:137, where the leaf comparator's memo calls reader.getCoreCacheHelper.getKey without a null check.

## Files

- [src/org/apache/lucene/index/TestSegmentOrderNullCoreCacheHelper.java](src/org/apache/lucene/index/TestSegmentOrderNullCoreCacheHelper.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSegmentOrderNullCoreCacheHelper
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
