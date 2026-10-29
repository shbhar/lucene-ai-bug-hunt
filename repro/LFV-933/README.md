# Hunspell Dictionary fails to load with 'Too many collisions' when its entry count is a multiple of 961, because WordStorage takes the raw String.hashCode modulo the table size (for example the en_US dictionary plus a 300-word custom list)

Bug LFV-933 · Area: analysis-common / hunspell WordStorage (dictionary word hash table) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary, for example the stock en_US dictionary plus a custom list of 300 words through HunspellStemFilterFactory's documented dictionary="en_US.dic,custom.dic" pattern, and expect it to load as it does with 299 or 301 custom words. Instead the Dictionary constructor throws RuntimeException 'Too many collisions. Try a larger Dictionary#hashFactor (now 1.0). If this doesn't help, please report this to dev@lucene.apache.org', so the analyzer cannot be built. WordStorage puts every word into slot Math.abs(String.hashCode % tableSize) with tableSize = entry count x hashFactor (default 1.0); when that size is a multiple of 961 = 31*31, the slot of a short word depends almost only on its last two letters (31^2 divides every earlier term of the unmixed radix-31 hash), so short words with a common ending (six-letter '-er' words in en_US) pile into a few slots and one exceeds the hard limit of 20 entries.

The test shows that a Hunspell `Dictionary` with 961 = 31*31 entries cannot load: `WordStorage` takes `String.hashCode() % 961`, which for short words keeps only the last two letters, so 21 short "-ing" words fill one slot past the 20-entry limit.

## Expected vs actual

Expected: 940 three-letter words plus 21 "-ing" words (no flags, default hashFactor) load; actual: `RuntimeException: Too many collisions. Try a larger Dictionary#hashFactor (now 1.0)`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestDictionary961Entries.java](src/org/apache/lucene/analysis/hunspell/TestDictionary961Entries.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.hunspell.TestDictionary961Entries
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hunspell-set-encoding-names-vs-c-hunspell-reference/](../../models/hunspell-set-encoding-names-vs-c-hunspell-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
