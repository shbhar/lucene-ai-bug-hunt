# TSTLookup.get('') and TSTLookup.lookup('') throw StringIndexOutOfBoundsException instead of returning null or an empty list: TSTAutocomplete.prefixCompletion reads key.charAt(0) before it checks the key length (every release since 3.3.0)

Bug LFV-251 · Area: suggest / ternary search tree suggester (TSTLookup.lookup and get, TSTAutocomplete.prefixCompletion) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a TSTLookup suggester and call get('') or lookup('', false, 5), for example because a user cleared the search box. You expect null from get(''), whose javadoc says it returns null if the key does not exist (the empty key is never stored), and a list, possibly empty, from lookup(''). Instead both throw StringIndexOutOfBoundsException 'Index 0 out of bounds for length 0', because TSTAutocomplete.prefixCompletion reads key.charAt(0) on its first loop step before it checks the key length, and the tree's root node is never null; non-empty keys work normally on the same lookup.

## Expected vs actual

- Expected: You expect null from get(''), whose javadoc says it returns null if the key does not exist (the empty key is never stored), and a list, possibly empty, from lookup('').
- Actual: Both throw StringIndexOutOfBoundsException 'Index 0 out of bounds for length 0', because TSTAutocomplete.prefixCompletion reads key.charAt(0) on its first loop step before it checks the key length, and the tree's root node is never null; non-empty keys work normally on the same lookup.

## Files

- [src/org/apache/lucene/search/suggest/tst/TestTSTLookupEmptyKey.java](src/org/apache/lucene/search/suggest/tst/TestTSTLookupEmptyKey.java) (JUnit test)
- [src2/org/apache/lucene/search/suggest/tst/TestTSTLookupEmptyKey.java](src2/org/apache/lucene/search/suggest/tst/TestTSTLookupEmptyKey.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.tst.TestTSTLookupEmptyKey
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.tst.TestTSTLookupEmptyKey
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
