# SpellChecker.swapSearcher closes the old reader before it installs the new searcher: if that close throws an IOException (injected in the runs), the still-open spell checker keeps the closed reader, so exist and suggestSimilar throw AlreadyClosedException (4.7.1 to 10.6.0, branch_11x and main), and the reader it had just opened is not installed and stays open after close() (since 2.9.2 and 3.0.1)

Finding LFV-1550 · Area: suggest / spell: SpellChecker.swapSearcher (the reader swap at the end of indexDictionary, clearIndex and setSpellIndex) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1550.html](../../bugs/LFV-1550.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a word to a spell index with SpellChecker.indexDictionary; the word is committed, and then, while the spell checker replaces its internal reader, the close of the old reader fails once with an IOException (injected in the runs through a wrapping Directory), so the call throws it. You expect a spell checker that was not closed to answer lookups from the reader it had just opened, and to leave no reader of its own open once you close it (close() is documented to close the spell checker's searcher; no javadoc sentence promises a usable spell checker after a call that threw). Instead swapSearcher closes the old reader before it stores the new one, so the spell checker keeps the closed reader and exist and suggestSimilar throw AlreadyClosedException ('this IndexReader is closed'), and the new reader is lost: after SpellChecker.close() it still has reference count 1 and, in the run, two index files open.

## Expected vs actual

- Expected: `exist("banana")` is true (the word was committed and the new reader was opened), and after `close()` no file of the spell index is open.
- Actual: `AlreadyClosedException: this IndexReader is closed` from `exist`, and after `close()` the test directory reports `cannot close: there are still 2 open files: {_0.cfs=1, _1.cfs=1}`.

## Files

- [src/org/apache/lucene/search/spell/TestSpellCheckerFailedReaderClose.java](src/org/apache/lucene/search/spell/TestSpellCheckerFailedReaderClose.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main ec3b3cca8f10 (a commit of 2026-11-18): reproduced.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with org.apache.lucene.store.AlreadyClosedException: this IndexReader is closed and, as a suppressed exception under it, MockDirectoryWrapper: cannot close: there are still 2 open files.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 5EED0877
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0877 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestSpellCheckerFailedReaderClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSpellCheckerFailedReaderClose.java](TestSpellCheckerFailedReaderClose.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
