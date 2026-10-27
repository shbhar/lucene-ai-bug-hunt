# MMapDirectory applies a slice's read advice to the wrong bytes when the slice starts inside an mmap chunk and crosses into the next one (the #14109 prefetch fix missed this caller of advise)

Bug LFV-126 · Area: store / MMapDirectory read advice (MemorySegmentIndexInput slices, compound files) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take a slice of an MMapDirectory file with IndexInput#slice(name, offset, length, IOContext) and a context that asks for RANDOM or SEQUENTIAL read advice, as the compound-file reader does for each inner file, and you expect the madvise hint to land on the slice's own pages. When the slice starts inside an mmap chunk and crosses into the next chunk, the hint is applied from the start of that chunk instead: in the repro, a slice of [512 KiB, 1.5 MiB) with 1 MiB chunks puts the RANDOM hint on [0, 1 MiB), so other inner files of a compound file get it, and a short slice that straddles a chunk boundary can get none of its own pages advised. Reads and query results stay correct; only the operating system's readahead and caching behaviour on those pages changes.

## Expected vs actual

- Expected: You expect the madvise hint to land on the slice's own pages.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/store/TestMultiChunkSliceReadAdvice.java](src/org/apache/lucene/store/TestMultiChunkSliceReadAdvice.java) (JUnit test)

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
     execute --select-class org.apache.lucene.store.TestMultiChunkSliceReadAdvice
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
