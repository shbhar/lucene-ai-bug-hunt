# DocBaseBitSetIterator over an empty FixedBitSet with a non-zero docBase throws ArrayIndexOutOfBoundsException (AssertionError with -ea) from its first nextDoc() instead of returning NO_MORE_DOCS, because advance() calls nextSetBit(0) on the zero-length set (latent: no Lucene code builds that shape; since 9.1.0)

Finding LFV-1312 · Area: util / bit-set doc ID iterators (DocBaseBitSetIterator.advance) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1312.html](../../bugs/LFV-1312.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a DocBaseBitSetIterator over an empty bit set with a non-zero doc base, for example new DocBaseBitSetIterator(new FixedBitSet(0), 0, 64), the shape you get for a 64-aligned window of documents that turned out to have no matches. You expect its first nextDoc() to return NO_MORE_DOCS, as the same class does with docBase 0 and as BitSetIterator, which its javadoc says it is 'like', does for an empty set. Instead nextDoc() throws ArrayIndexOutOfBoundsException 'Index 0 out of bounds for length 0' (AssertionError 'index=0, numBits=0' with -ea), because advance() returns early only when target &gt;= bits.length() + docBase and otherwise calls bits.nextSetBit(0) on the zero-length set.

## Expected vs actual

- Expected: The first `nextDoc()` returns `NO_MORE_DOCS` (2147483647) and the test passes.
- Actual: `java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0` from `FixedBitSet.nextSetBit` (`AssertionError: index=0, numBits=0` with assertions on).

## Files

- [src/org/apache/lucene/util/TestDocBaseBitSetIteratorEmptyBits.java](src/org/apache/lucene/util/TestDocBaseBitSetIteratorEmptyBits.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestDocBaseBitSetIteratorEmptyBits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDocBaseBitSetIteratorEmptyBits.java](TestDocBaseBitSetIteratorEmptyBits.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
