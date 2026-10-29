# The file-format javadoc of the default doc-values format (Lucene90DocValuesFormat) says terms dictionaries are 'written in chunks of 16, with the first value written completely', but since 9.0 they are written in blocks of 64 terms whose other 63 terms are prefix-coded and then LZ4-compressed; it also says table compression is used for fewer than 256 unique values, while exactly 256 are table-compressed (documentation defect; the writer and reader agree)

Bug LFV-974 · Area: codecs / doc values (Lucene90DocValuesFormat class javadoc prose vs Lucene90DocValuesConsumer) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the Lucene90DocValuesFormat javadoc to learn how the default codec stores the terms of SORTED and SORTED_SET doc values, and it says they are written in chunks of 16 terms, each starting with one term written in full while the others share prefixes. Since Lucene 9.0 the writer instead uses blocks of 64 terms: only the first term of each block is stored in full, and the other 63 are prefix-coded and then compressed with LZ4, a step the javadoc never mentions, so in the.dvd file only terms 0, 64, 128,... appear in clear, not 0, 16, 32,.... The same javadoc says a lookup table is used for numeric fields with fewer than 256 unique values, but a field with exactly 256 is also table-compressed; Lucene's own reader matches the writer, so nothing breaks at runtime, only the documentation is wrong.

The test shows that the `Lucene90DocValuesFormat` javadoc says terms dictionaries are "written in chunks of 16, with the first value written completely", but in the `.dvd` file the default codec writes for 17 SORTED terms, only ord 0 is stored in full (the writer uses 64-term blocks and LZ4-compresses the other 63 terms).

## Expected vs actual

Expected (javadoc): ords 0 and 16 are written completely. Actual: `expected ords 0 and 16 written completely, actual: [0]`.

## Files

- [src/org/apache/lucene/codecs/lucene90/TestLucene90DocValuesTermsChunks.java](src/org/apache/lucene/codecs/lucene90/TestLucene90DocValuesTermsChunks.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.TestLucene90DocValuesTermsChunks
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/codec-format-javadoc-grammar-overflow-liv-cfe-extension-table/](../../models/codec-format-javadoc-grammar-overflow-liv-cfe-extension-table/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
