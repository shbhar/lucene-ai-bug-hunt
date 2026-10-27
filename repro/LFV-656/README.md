# Kuromoji UserDictionary loads entries whose whitespace makes the stored segment lengths disagree with the surface form (a half-width space inside the surface, a tab between segments, a space after a comma), so JapaneseTokenizer later emits a term that starts with a space and drops the entry's last character, throws ArrayIndexOutOfBoundsException, or emits an empty token

Bug LFV-656 · Area: analysis / kuromoji user dictionary (ja.dict.UserDictionary entry validation; USER-token expansion in ViterbiNBest and JapaneseTokenizer.incrementToken) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add the entry 'ソニー ミュージック,ソニー ミュージック,ソニー ミュージック,カスタム名詞' (a name written with a half-width space) to a Kuromoji user dictionary and expect it either to be rejected when the dictionary loads, as LUCENE-8933 promises for segmentations that do not match the surface form ('This change avoids unexpected runtime exceptions or behaviours', MIGRATE.md), or to give tokens that cover ソニー ミュージック. Instead it loads, and JapaneseTokenizer emits ソニー[0,3) and ' ミュージッ'[3,9): one term starts with a space and the final ク is in no token, because the load-time check compares the two columns with all whitespace removed, while the stored segment lengths come from splitting the segmentation on ' ' only and the dictionary key keeps its whitespace. Two related entry shapes also load: a tab between segments (日本&lt;TAB&gt;経済 新聞) makes incrementToken throw ArrayIndexOutOfBoundsException 'arraycopy: last source index 7 out of bounds for char[6]', the exact LUCENE-8933 symptom, and a space after each comma gives an empty token [0,0) (an AssertionError with -ea).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It loads, and JapaneseTokenizer emits ソニー[0,3) and ' ミュージッ'[3,9): one term starts with a space and the final ク is in no token, because the load-time check compares the two columns with all whitespace removed, while the stored segment lengths come from splitting the segmentation on ' ' only and the dictionary key keeps its whitespace.

## Files

- [src/org/apache/lucene/analysis/ja/TestUserDictionaryWhitespaceSegments.java](src/org/apache/lucene/analysis/ja/TestUserDictionaryWhitespaceSegments.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestUserDictionaryWhitespaceSegments
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
