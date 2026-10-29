# Nori's DictionaryBuilder rejects a left context id of 8192 or more but silently accepts such a right context id, which does not round-trip through the compiled format, so the compiled dictionary returns a wrapped right id (changing segmentation) or makes KoreanTokenizer throw IndexOutOfBoundsException

Bug LFV-710 · Area: analysis / nori system dictionary builder (DictionaryBuilder, TokenInfoDictionaryEntryWriter; custom system dictionaries) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-710.html](../../bugs/LFV-710.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile a custom nori system dictionary with DictionaryBuilder from MeCab-format sources in which a lexicon row (or an unk.def row) has right context id 16385, with a matrix.def that declares 16386 right ids, and expect the row either to be rejected, as a left id of 8192 is ('leftId &gt;= 8192'), or to read back as 16385. Instead the builder accepts it and packs the id into a 14-bit field without any check (TokenInfoDictionaryEntryWriter.java:137), so Dictionary.getRightId returns 1 and KoreanTokenizer uses the connection costs of right id 1: in the repro '가나다' is split [가나, 다] instead of [가, 나다]. A right id in [8192, 16383] decodes to about 1.07 billion, and tokenizing any text that contains the word throws IndexOutOfBoundsException from ConnectionCosts.get.

## Expected vs actual

- Expected: Each out-of-range right id is rejected at build time, or reads back as written: 16385 stays 16385, and `가나다` is split `[가, 나다]`.
- Actual: 4 of 6 tests fail: 16385 reads back as 1 and `가나다` becomes `[가나, 다]`; -1 reads back as 1073741823; right id 8192 makes tokenizing throw `IndexOutOfBoundsException`.

## Files

- [src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderRightIdLimit.java](src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderRightIdLimit.java) (JUnit test)
- [src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderRightIdLimit9x.java](src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderRightIdLimit9x.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.dict.TestNoriBuilderRightIdLimit

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.dict.TestNoriBuilderRightIdLimit9x
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDictionaryBuilderRightId.java](TestDictionaryBuilderRightId.java)
- [fix-with-test-optionA.diff](fix-with-test-optionA.diff)
- [TestNoriBuilderRightIdLimit.java](TestNoriBuilderRightIdLimit.java)
- [TestNoriRightIdRange.java](TestNoriRightIdRange.java)
- [RightIdBandsProbe.java](RightIdBandsProbe.java)

Formal models and scripts this bug's part of the hunt used: [../../models/cjk-compiled-dictionary-source-round-trip/](../../models/cjk-compiled-dictionary-source-round-trip/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
