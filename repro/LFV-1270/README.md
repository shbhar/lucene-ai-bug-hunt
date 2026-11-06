# A STRING sort on a field whose terms index has fewer terms than its SORTED doc values fails with a false message ('Terms have more than 1 unique terms while doc values have exactly 1 terms' when the terms index has 1 term and the doc values 3), because TermOrdValComparator prints the loop ordinal for both counts (since 9.4.0)

Finding LFV-1270 · Area: core / search: sort comparators (TermOrdValComparator, postings-based pruning of STRING sorts) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1270.html](../../bugs/LFV-1270.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three documents whose field f holds the term 'a' in the terms index (StringField) but the values c, a and b in its SORTED doc values, which breaks SortField's documented requirement that both hold the same data, and run a STRING sort on f (top 1, totalHitsThreshold 1) over all documents. You expect the documented reaction, an IllegalStateException, with a message that describes the mismatch: the terms index has 1 term and the doc values have 3. Instead the message says 'Terms have more than 1 unique terms while doc values have exactly 1 terms', because PostingsBasedCompetitiveState.init prints the loop ordinal for both numbers when terms.next() runs out; the exception type and the failure itself are right, only the diagnostic is false.

## Expected vs actual

- Expected: The intended `IllegalStateException`, with a message saying the terms index ran out of terms before the doc values (1 term against 3).
- Actual: `IllegalStateException: Terms have more than 1 unique terms while doc values have exactly 1 terms`, with assertions on and off on main.

## Files

- [src/org/apache/lucene/search/TestTermOrdValMismatchMessage.java](src/org/apache/lucene/search/TestTermOrdValMismatchMessage.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.search.TestTermOrdValMismatchMessage
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTermOrdValMismatchMessage.java](TestTermOrdValMismatchMessage.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
