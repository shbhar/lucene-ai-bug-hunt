# Nori UserDictionary picks a user noun's right context id (last syllable with a final consonant, without one, or not Hangul) from the last character of the whole entry line instead of the noun, so a trailing space or an inline comment ('학교 # memo') changes how the word after the noun is analysed

Bug LFV-654 · Area: analysis / nori UserDictionary (right context id of user nouns, used by the Viterbi connection costs) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-654.html](../../bugs/LFV-654.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a nori user dictionary whose line for the noun 학교 ('school') ends with a space, or carries an inline comment as in '학교 # memo' (the parser deletes '#' to the end of the line but keeps the space before it), and expect the same analysis as with the plain line '학교', because the parser splits the line on whitespace and gets the same noun with no segmentation. Instead UserDictionary.java:94 takes the noun's right context id, which tells the tokenizer whether the noun's last syllable ends in a consonant, from the last character of the whole line, here the space, so the noun gets the generic id and the word after it is analysed differently: on 9.12.3 '학교이' gives 이/VCP with '학교' but 이/J with '학교 ', and KoreanAnalyzer turns '학교가방을 샀다' into [학교][가방][사] with '학교' but [학교][방][사] with '학교 ', losing the term 가방 ('bag'). The two line forms disagree in every release from 7.4.0 to 10.5.2 and on main (main: '학교이' gives 이/JKS vs 이/VCP, '사람이나' gives [사람] vs [사람][이]), and a shorter segmentation such as '세종시 세종' takes the id from 종 (final consonant) instead of 시 (none) in the same way.

## Expected vs actual

- Expected: `사람 ` and `사람 # memo` keep 사람's right id 3535, and `KoreanAnalyzer` indexes `사람이나` as `[사람]` with every line form.
- Actual: Right id 3533 for `사람 ` and `사람 # memo`, and `KoreanAnalyzer` indexes `사람이나` as `[사람][이]`: 3 of 5 tests fail, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/ko/TestUserDictionaryRightIdLastChar.java](src/org/apache/lucene/analysis/ko/TestUserDictionaryRightIdLastChar.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.TestUserDictionaryRightIdLastChar
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestUserDictionaryLineFormat.java](TestUserDictionaryLineFormat.java)
- [Lfv654Probe.java](Lfv654Probe.java)
- [TestUserDictionaryRightIdLastChar.java](TestUserDictionaryRightIdLastChar.java)
- [TestUserDictParticle.java](TestUserDictParticle.java)
- [TestUserDictionaryRightIdLastCharHistory.java](TestUserDictionaryRightIdLastCharHistory.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
