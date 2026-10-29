# ThaiTokenizer with a user dictionary silently drops the vowel or tone marks that follow a dictionary word ending inside a character cluster (with {แม่น}, 'แม่น้ำ' (river) is tokenized as just [แม่น] (accurate)), because the match is accepted at a non-boundary and the remainder, which now starts with a mark, is skipped

Bug LFV-229 · Area: analysis / Thai (ThaiTokenizer#incrementWord user-dictionary loop and re-anchor, GITHUB#16722; ThaiTokenizerFactory 'dictionary') · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-229.html](../../bugs/LFV-229.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure ThaiTokenizer with a user dictionary that contains an ordinary short word such as แม่น ('accurate') or ผล ('result') and expect it to change only where words split. Instead, when the dictionary word is followed in the text by a combining vowel or tone mark, as in แม่น้ำ ('river') or ผลิต ('produce'), the tokenizer cuts the character cluster and throws the mark away: แม่น้ำ becomes the single token แม่น, and ผลิต becomes [ผล, ต]. Because queries are analyzed the same way, a search for แม่น้ำ also matches documents that only contain แม่น, with no error.

The test shows that `ThaiTokenizer` with the user dictionary {แม่น} tokenizes แม่น้ำ ("river") as just [แม่น]: the match ends inside the character cluster, so the tone mark U+0E49 and SARA AM U+0E33 are dropped.

## Expected vs actual

Expected: the tokens together hold all of แม่น้ำ. Actual: `expected:<แม่น[้ำ]> but was:<แม่น[]>` (-ea and -da).

## Files

- [src/org/apache/lucene/analysis/th/TestThaiTokenizerUserDictionaryClusterSplit.java](src/org/apache/lucene/analysis/th/TestThaiTokenizerUserDictionaryClusterSplit.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.th.TestThaiTokenizerUserDictionaryClusterSplit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
