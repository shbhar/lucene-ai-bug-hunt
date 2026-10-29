# MemoryIndex.addField throws AssertionError under -ea for a KnnFloat16VectorField, because a stale assertion in storeVectorValues predates the FLOAT16 branch

Bug LFV-090 · Area: memory / MemoryIndex vectors · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a KnnFloat16VectorField to a MemoryIndex, through MemoryIndex.addField or MemoryIndex.fromDocument, with Java assertions enabled. You expect it to be stored and readable through getFloat16VectorValues, like a float32 or byte vector field, because MemoryIndex has a FLOAT16 storage branch and a FLOAT16 reader. Instead the first statement of storeVectorValues asserts that the field is a KnnFloatVectorField or a KnnByteVectorField, so the call throws AssertionError before the FLOAT16 branch runs; with assertions disabled the vector is stored and read back exactly.

The test shows that `MemoryIndex#addField` with a `KnnFloat16VectorField` fails under `-ea`, because the first statement of `storeVectorValues` still asserts that the field is a float32 or byte vector field.

## Expected vs actual

Expected the vector to be stored and read back through `getFloat16VectorValues`; actual with `-ea`: `AssertionError` at `MemoryIndex.storeVectorValues(MemoryIndex.java:794)`. With `-da` the test passes (the claim is about `-ea` only).

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexFloat16Vector.java](src/org/apache/lucene/index/memory/TestMemoryIndexFloat16Vector.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexFloat16Vector
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
