# PlainTextDictionary(Path) opens the dictionary file in its constructor and has no close(), so a SpellChecker.indexDictionary or Lookup.build that fails before reading it to the end leaves the file open until garbage collection (the Path constructor since 5.0.0; the File constructor before it since 1.9)

Finding LFV-1496 · Area: suggest / spell: PlainTextDictionary (a word-list file as a Dictionary) under SpellChecker.indexDictionary and Lookup.build(Dictionary): release of the dictionary file when the build fails · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1496.html](../../bugs/LFV-1496.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a spell index from a word-list file with spellChecker.indexDictionary(new PlainTextDictionary(path), ...), and the call fails, for example with LockObtainFailedException because another writer holds the spell index. You expect that nothing of the failed call stays open once you have closed the SpellChecker and its Directory: the library opened the dictionary file itself and you hold no handle on it. Instead the file stays open, one file descriptor per failed call, until the garbage collector reclaims the abandoned dictionary object, because PlainTextDictionary has no close() and closes its reader only when the iteration reaches the end of the file or a read fails; the same happens when Lookup.build(Dictionary) fails early.

## Expected vs actual

- Expected: `indexDictionary` throws `LockObtainFailedException` and leaves no handle on `dict.txt`, so `leakFS.onClose()` returns.
- Actual: `leakFS.onClose()` throws `file handle leaks: [InputStream(.../dict.txt)]` (line 49), with assertions on and off.

## Files

- [src/org/apache/lucene/search/spell/TestPlainTextDictionaryPathLeak.java](src/org/apache/lucene/search/spell/TestPlainTextDictionaryPathLeak.java) (JUnit test)

## Run it

- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it writes a one-word dictionary file under a LeakFS (the test framework's file system wrapper that tracks open files), opens a SpellChecker and then an IndexWriter on the same Directory, so that the writer holds the write lock, and expects spellChecker.indexDictionary(new PlainTextDictionary(dictionaryFile), ...) to throw LockObtainFailedException, which it does. No fault is injected. After the SpellChecker, the writer and the Directory are closed, it asks the LeakFS whether a file is still open. The test fails with 'file handle leaks: [InputStream(.../dict.txt)]', the same with and without assertions. Beside core the recipe needs the suggest and analysis-common jars, which it lists. The writer that holds the lock is the unnamed variable _ (Java 22 or later; the recipe uses JDK 25), so the same file also compiles in a Lucene checkout, which builds with -Xlint:try -Werror. The file that is not exported is the earlier form of this test, with the lock holder as a named variable.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestPlainTextDictionaryPathLeak
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestPlainTextDictionaryPathLeak.java](TestPlainTextDictionaryPathLeak.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
