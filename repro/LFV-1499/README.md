# KnnVectorValues.iterator() does not say what a second call returns: for a vector field that some documents lack, the default codec's values hand out iterators that all share one position, so a second pass over the same values object silently finds no vectors (AssertionError with assertions on), while a field that every document has gets a new iterator on each call (undocumented API inconsistency since 10.0.0; documentation defect)

Finding LFV-1499 · Area: codecs / vector values (KnnVectorValues.iterator() as read through LeafReader.getFloatVectorValues and getByteVectorValues; the sparse off-heap values of the default codec's flat vector formats and of the backward-codecs readers) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the vectors of a field through LeafReader.getFloatVectorValues (or getByteVectorValues) and go over the same values object twice, each time with a new values.iterator(), for example once to gather statistics and once to read the vectors. Going by the javadoc ('Create an iterator for this instance.') and by what happens when every document has a vector, you expect the second iterator to start before the first document again. Instead, when at least one document of the segment has no vector, the default codec's values hand out iterators that share one position: the second one stands where the first one stopped, so the second pass finds no vectors (or throws AssertionError with assertions enabled); nothing in the public javadoc says so, and you need copy(), or the values from the reader again, for an independent pass.

## Expected vs actual

- Expected: You expect the second iterator to start before the first document again.
- Actual: When at least one document of the segment has no vector, the default codec's values hand out iterators that share one position: the second one stands where the first one stopped, so the second pass finds no vectors (or throws AssertionError with assertions enabled); nothing in the public javadoc says so, and you need copy(), or the values from the reader again, for an independent pass.

## Files

- [src/org/apache/lucene/index/TestSparseVectorValuesSecondIterator.java](src/org/apache/lucene/index/TestSparseVectorValuesSecondIterator.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (2026-11-16): still reproduces.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced.
- What the test does: with the default codec it indexes two documents, the first with a float vector field and the second without it, so that the field is sparse. It takes the field's FloatVectorValues from the only leaf reader, advances a first values.iterator() to document 0, then takes a second values.iterator() from the same object and asserts that this one has not been advanced (docID() is -1). The test fails at that assertion (expected -1, was 0), the same with and without assertions; it uses no randomness.
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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSparseVectorValuesSecondIterator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
