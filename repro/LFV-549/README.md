# SpellChecker.indexDictionary never closes or rolls back its IndexWriter when the build throws, so the spell index stays write-locked and every later indexDictionary, clearIndex or IndexWriter on it in the same JVM fails with LockObtainFailedException until the JVM restarts

Bug LFV-549 · Area: suggest / spell: SpellChecker.indexDictionary (n-gram spell index build, failure cleanup) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a spell index with SpellChecker.indexDictionary and the build throws, for example because the word-list file is not valid UTF-8, has a line longer than 32766 bytes, or its reader hits an I/O error; you fix the input and call indexDictionary or clearIndex again. You expect the retry to work, because the failed call should close or roll back the IndexWriter it opened. Instead indexDictionary closes its writer only on the success path, so the writer, which the caller cannot reach, keeps the spell index's write lock, and every later write to that index in the same JVM throws LockObtainFailedException 'Lock held by this virtual machine' until the JVM restarts.

The test shows that when SpellChecker.indexDictionary fails (its Dictionary throws IOException), it never closes its IndexWriter.

## Expected vs actual

Expected: a retry with a good dictionary succeeds; actual: the retry fails with LockObtainFailedException on write.lock (-ea and -da).

## Files

- [src/org/apache/lucene/search/spell/TestSpellCheckerFailedIndexDictionary.java](src/org/apache/lucene/search/spell/TestSpellCheckerFailedIndexDictionary.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestSpellCheckerFailedIndexDictionary
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
