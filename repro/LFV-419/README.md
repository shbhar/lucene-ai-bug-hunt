# SortedDocValues#ordinalRangeIntoBitSet's default implementation, called after advanceExact(d) returned false, reads ordValue for doc d, which has no value, so it marks d using a neighbouring document's ordinal (or throws EOFException)

Bug LFV-419 · Area: index / SortedDocValues bulk ordinal range (default implementation; Lucene90 sparse SORTED iterator) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

On a default-codec index with docs {0: 'a', 1: no value, 2: 'b'} in a SORTED doc-values field, you call advanceExact(1) on the field's SortedDocValues (it returns false) and then ordinalRangeIntoBitSet(1, 3, ord(a), ord(a), bits, 0) on the same iterator. You expect no bit, because neither doc 1 nor doc 2 has the value 'a', and that is what the per-document advanceExact/ordValue evaluation the javadoc describes returns. Instead the default starts at docID == 1, calls ordValue there (which the API calls illegal after a false advanceExact), gets doc 0's ordinal and marks doc 1; with {0: no value, 1: 'a', 2: 'b'} and advanceExact(0), the same call throws RuntimeException(EOFException).

## Expected vs actual

- Expected: You expect no bit, because neither doc 1 nor doc 2 has the value 'a', and that is what the per-document advanceExact/ordValue evaluation the javadoc describes returns.
- Actual: The default starts at docID == 1, calls ordValue there (which the API calls illegal after a false advanceExact), gets doc 0's ordinal and marks doc 1; with {0: no value, 1: 'a', 2: 'b'} and advanceExact(0), the same call throws RuntimeException(EOFException).

## Files

- [src/org/apache/lucene/index/TestSortedOrdinalRangeAfterAdvanceExact.java](src/org/apache/lucene/index/TestSortedOrdinalRangeAfterAdvanceExact.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestSortedOrdinalRangeAfterAdvanceExact
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/dv-norms-cursor-coherence/](../../models/dv-norms-cursor-coherence/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
