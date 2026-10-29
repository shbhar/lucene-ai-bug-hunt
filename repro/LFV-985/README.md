# TSTLookup recurses once per ternary-tree level in add/build, store, load and ramBytesUsed, so a deep tree (short keys added in sorted order, or one suggestion thousands of characters long) throws StackOverflowError, often only when the lookup is stored or loaded (every release since 3.3.0)

Bug LFV-985 · Area: suggest / ternary search tree suggester (TSTLookup add, build, store, load and ramBytesUsed; TSTAutocomplete.insert; TernaryTreeNode.sizeInBytes) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add the 20,992 CJK Unified Ideographs to a TSTLookup in code-point order, or build one from a single suggestion of 20,000 characters, and expect to store it, load it back and ask its size, as Lookup.store and Lookup.load promise (they document failure only as a false return or an IOException) and as WFSTCompletionLookup does with the same 20,000-character suggestion. Instead add, get and lookup work, but store, load and ramBytesUsed throw StackOverflowError on the JVM's default thread stack, and for the long suggestion build itself overflows, because TSTAutocomplete.insert, TSTLookup.writeRecursively and readRecursively and TernaryTreeNode.sizeInBytes each use one Java stack frame per tree level. Adding keys in sorted order turns each level's lo/hi links into a chain (that sorted insertion unbalances this tree was noted by its author, Ankul Garg, on SOLR-1316 in 2009, as a speed concern), so the tree gets about as deep as the number of distinct first characters, and every character of a key adds one more level even in build.

The test shows that a `TSTLookup` filled with `add()` in sorted order (the 20,992 CJK Unified Ideographs U+4E00..U+9FFF, one character each, on a thread with a fixed 2 MiB stack) answers `lookup()` but cannot be stored.

## Expected vs actual

Expected: `store()` returns true (Lookup#store documents only false or IOException). Actual: StackOverflowError in the recursive `TSTLookup.writeRecursively` (one frame per tree level; the sorted adds make a ~21,000-level chain). The threshold depends on the JIT state; 12 of 12 fresh JVMs failed on this host.

## Files

- [src/org/apache/lucene/search/suggest/tst/TestTSTLookupSortedAddStore.java](src/org/apache/lucene/search/suggest/tst/TestTSTLookupSortedAddStore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.tst.TestTSTLookupSortedAddStore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
