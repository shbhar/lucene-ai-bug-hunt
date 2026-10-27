# Postings readers count skipped positions in an int, so once the skipped occurrences of one term within a postings block pass Integer.MAX_VALUE, nextPosition returns an earlier document's positions and phrase queries silently miss matches

Bug LFV-185 · Area: codecs / postings reader (Lucene104 BlockPostingsEnum position bookkeeping; also the Lucene101, Lucene103, Lucene99 and Lucene90 readers in backward-codecs) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents that each stay within Lucene's per-document limits but repeat one term about a billion times and run PhraseQuery f:"v t", which only C matches. You expect the postings reader to return C's own positions (101, 102, 103,...) and the query to find C. Instead the reader adds up the frequencies of the documents whose positions it skipped in an int, the sum 2,147,483,696 wraps negative, the skip is not done, and nextPosition returns the first skipped document's positions (1, 2, 3), so the query finds 0 hits with assertions off (with assertions on, an internal assertion fails instead).

## Expected vs actual

- Expected: You expect the postings reader to return C's own positions (101, 102, 103,...) and the query to find C.
- Actual: The reader adds up the frequencies of the documents whose positions it skipped in an int, the sum 2,147,483,696 wraps negative, the skip is not done, and nextPosition returns the first skipped document's positions (1, 2, 3), so the query finds 0 hits with assertions off (with assertions on, an internal assertion fails instead).

## Files

- [src/org/apache/lucene/index/PendingPositionsIntOverflow.java](src/org/apache/lucene/index/PendingPositionsIntOverflow.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.apache.lucene.index.PendingPositionsIntOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
