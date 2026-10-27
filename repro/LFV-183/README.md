# Since 9.12.0 the default postings writer stores the byte length of a level-1 block's competitive impacts in a signed short, so with a custom Similarity whose norms do not fit in a byte, a term with 8,192 or more postings in one segment (4,096 before 10.4) can need more than 32,767 bytes: flush fails an assertion with -ea, and with -da the segment is written but that term's postings then throw on every read or are read back wrong

Bug LFV-183 · Area: codecs / postings writer and reader (Lucene104 level-1 skip data, ImpactLength; the same field in the Lucene912, Lucene101 and Lucene103 formats) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index with the default codec and a custom Similarity whose norm is the field length (or another value that does not fit in a byte), and one term appears in 8,192 documents of a segment with growing frequencies, for example through Lucene's DelimitedTermFrequencyTokenFilter. You expect the term's postings to read back exactly as indexed. Instead the writer stores the length of the level-1 skip block's impact list, which here exceeds 32,767 bytes, in a signed short: with assertions on, flush fails; with assertions off, the segment is written and every read of the term throws 'numBytes must be &gt;= 0', or, with wider norms, decodes wrong data (doc 0 with freq 1,602,871,807 instead of 1,000, and most documents missing).

## Expected vs actual

- Expected: You expect the term's postings to read back exactly as indexed.
- Actual: The writer stores the length of the level-1 skip block's impact list, which here exceeds 32,767 bytes, in a signed short: with assertions on, flush fails; with assertions off, the segment is written and every read of the term throws 'numBytes must be &gt;= 0', or, with wider norms, decodes wrong data (doc 0 with freq 1,602,871,807 instead of 1,000, and most documents missing).

## Files

- [src/org/apache/lucene/index/TestLevel1ImpactsLengthOverflow.java](src/org/apache/lucene/index/TestLevel1ImpactsLengthOverflow.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestLevel1ImpactsLengthOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/postings-lucene104/](../../models/postings-lucene104/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
