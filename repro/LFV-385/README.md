# A single indexed token of 715,827,878 or more UTF-16 units closes IndexWriter and drops uncommitted documents instead of being rejected as an immense term

Bug LFV-385 · Area: index / IndexingChain aborting-exception classification x util / UTF-16 to UTF-8 sizing (CharTermAttributeImpl.getBytesRef, BytesRefBuilder.copyChars, UnicodeUtil.maxUTF8Length) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document whose untokenized indexed field (or a TextField analyzed with KeywordAnalyzer) holds one value of 715,827,878 or more characters, for example 'a' repeated 715,827,883 times. You expect what Lucene does for any over-long term: addDocument throws the documented 'immense term' IllegalArgumentException, skips that document and keeps the IndexWriter open with the other buffered documents. Instead the UTF-16 to UTF-8 conversion sizes its buffer at 3 bytes per character before the length check, that size overflows int (ArithmeticException) or exceeds the maximum array length (IllegalArgumentException), and because the conversion runs inside the indexing chain's aborting try, IndexWriter closes itself and rolls back every document added since the last commit.

## Expected vs actual

- Expected: You expect what Lucene does for any over-long term: addDocument throws the documented 'immense term' IllegalArgumentException, skips that document and keeps the IndexWriter open with the other buffered documents.
- Actual: The UTF-16 to UTF-8 conversion sizes its buffer at 3 bytes per character before the length check, that size overflows int (ArithmeticException) or exceeds the maximum array length (IllegalArgumentException), and because the conversion runs inside the indexing chain's aborting try, IndexWriter closes itself and rolls back every document added since the last commit.

## Files

- [src/org/apache/lucene/index/TestHugeTokenClosesIndexWriter.java](src/org/apache/lucene/index/TestHugeTokenClosesIndexWriter.java) (JUnit test)
- [src2/org/apache/lucene/index/TestHugeTokenClosesIndexWriter.java](src2/org/apache/lucene/index/TestHugeTokenClosesIndexWriter.java) (JUnit test)
- [src/org/apache/lucene/index/TestHugeTokenLosesBufferedDocs.java](src/org/apache/lucene/index/TestHugeTokenLosesBufferedDocs.java) (JUnit test)
- [src/org/apache/lucene/index/TestHugeKeywordTokenClosesIndexWriter.java](src/org/apache/lucene/index/TestHugeKeywordTokenClosesIndexWriter.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx5g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHugeTokenClosesIndexWriter
java -da -dsa -Dtests.asserts=false -Xmx5g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHugeTokenClosesIndexWriter

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHugeTokenLosesBufferedDocs \
       --select-class org.apache.lucene.index.TestHugeKeywordTokenClosesIndexWriter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/unicode-conversions/](../../models/unicode-conversions/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
