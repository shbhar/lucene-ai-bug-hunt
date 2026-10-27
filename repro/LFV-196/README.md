# The phrase matchers add a document position and a query position offset in int: with an offset near 2^31 an exact PhraseQuery or MultiPhraseQuery loops forever inside one document (IndexSearcher#setTimeout does not stop it) and a sloppy one returns false hits

Bug LFV-196 · Area: search / phrase matching (ExactPhraseMatcher, SloppyPhraseMatcher and PhrasePositions position arithmetic; PhraseQuery.Builder and MultiPhraseQuery.Builder accept the positions) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a PhraseQuery with term a at position 0 and term b at position Integer.MAX_VALUE (the builder accepts it, because it only checks that positions are &gt;= 0 and in order) and count it on a one-document index whose text is 'x a b'. You expect 0 hits, because no indexed position can be that far from a. Instead the exact matcher adds a's document position 1 and b's offset Integer.MAX_VALUE in int, the sum wraps to a negative number, both of its advance steps then leave its state unchanged, and IndexSearcher#count never returns, even with a 1 s IndexSearcher#setTimeout; with slop 1 the same kind of overflow makes the sloppy matcher return the document 'b x x a' as a hit.

## Expected vs actual

- Expected: You expect 0 hits, because no indexed position can be that far from a.
- Actual: The exact matcher adds a's document position 1 and b's offset Integer.MAX_VALUE in int, the sum wraps to a negative number, both of its advance steps then leave its state unchanged, and IndexSearcher#count never returns, even with a 1 s IndexSearcher#setTimeout; with slop 1 the same kind of overflow makes the sloppy matcher return the document 'b x x a' as a hit.

## Files

- [src/org/apache/lucene/search/TestPhrasePositionOverflow.java](src/org/apache/lucene/search/TestPhrasePositionOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestPhrasePositionOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
