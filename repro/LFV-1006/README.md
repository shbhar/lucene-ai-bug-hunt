# A hunspell Dictionary load that fails with the offline sorting of the Dictionary(Directory,...) constructors leaves its temp IndexOutput open (regression since 9.9.0, #12834): through HunspellStemFilterFactory every failed inform keeps a file descriptor on the deleted temp file until GC, and with a caller-supplied tempDir the temp file also stays behind

Bug LFV-1006 · Area: analysis-common / hunspell Dictionary loading (SortingStrategy.offline, HunspellStemFilterFactory) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a hunspell Dictionary with a temp Directory (or through HunspellStemFilterFactory) and the load fails while the.dic entries are being written to the offline-sort temp file, for example because the.dic InputStream throws an IOException, which the constructor documents, or a write to the temp directory fails; you expect the failed constructor to close and delete the temp file it created, as it does on success and at the neighbouring steps. Instead Dictionary calls sortingStrategy.start, which opens the temp IndexOutput, and then mergeDictionaries(...) with no failure handling, so the output stays open: MockDirectoryWrapper refuses to close ('there are still 1 open files: {dictionary_dat_0.tmp=1}'), and 10 failed HunspellStemFilterFactory.inform calls leave 10 descriptors open on deleted hunspell_dat_0.tmp files until GC. The open handle is a regression from #12834 (9.9.0), which moved the entry writer out of its try-with-resources; the closed temp file that the direct constructor leaves in a caller-supplied tempDir is older (6.0.0).

The test shows (LFV-1006, regression since 9.9.0) that a hunspell `Dictionary(Directory,...)` load whose.dic stream throws IOException leaves the offline-sort temp IndexOutput open in tempDir.

## Expected vs actual

Expected: after the failed constructor, the temp directory closes cleanly; actual (-ea and -da): `MockDirectoryWrapper: cannot close: there are still 1 open files: {dict_dat_0.tmp=1}`, opened at `SortingStrategy$1.start` from `Dictionary.<init>:267`.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestDictionaryFailedLoadClosesTempOutput.java](src/org/apache/lucene/analysis/hunspell/TestDictionaryFailedLoadClosesTempOutput.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestDictionaryFailedLoadClosesTempOutput
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/analysis-loaders-release-streams-and-temp-files-on-failure/](../../models/analysis-loaders-release-streams-and-temp-files-on-failure/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
