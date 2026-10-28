# Hunspell getAllWordForms, generateAllSimpleWords and compress return words that spell rejects: the sanity check canStemToOriginal accepts any affix path that strips a form back to the stem text, so with 'NEEDAFFIX X', 'SFX A 0 s/XB', an unrelated 'SFX C 0 s' and foo/A, getAllWordForms("foo") returns foos

Bug LFV-638 · Area: analysis / hunspell (WordFormGenerator: canStemToOriginal, getAllWordForms, generateAllSimpleWords, compress) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-638.html](../../bugs/LFV-638.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary with 'NEEDAFFIX X', 'SFX A 0 s/XB' (so foos needs one more suffix), 'SFX B 0 en' and an unrelated class 'SFX C 0 s', with the entries foo/A and bar/C, and call Hunspell.getAllWordForms("foo"). You expect [foo, foosen]: spell rejects foos (C Hunspell 1.7.2 does too), and the generator's check canStemToOriginal is documented to keep only forms that are 'accepted by the spell-checker and analyzed to be the form of the original dictionary entry'. Instead you get [foo, foos, foosen], because canStemToOriginal accepts as soon as any affix-stripping path gives back the stem text, here class C, which foo does not even have, and ignores which affixes were stripped (WordFormGenerator.java:211); generateAllSimpleWords leaks the same words, and compress([kat, kater, katern]) suggests kat/PQ, which does not accept kater.

## Expected vs actual

- Expected: `getAllWordForms("foo")` is `[foo, foosen]`, no generated form is rejected by `spell`, and `compress` suggests an entry that accepts `foos`.
- Actual: `[foo, foos, foosen]`; needaffix5 plus two lines leaks `foopseudosuf`, `pseudoprefoo`, `pseudoprefoopseudosuf`; `compress` suggests `foo/AB`, which rejects `foos`.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestWordFormsSanityCheckFlags.java](src/org/apache/lucene/analysis/hunspell/TestWordFormsSanityCheckFlags.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestWordFormsSanityCheckFlags
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestGeneratedWordFormsMustSpell.java](TestGeneratedWordFormsMustSpell.java)
- [TestWordFormsSanityCheckFlags.java](TestWordFormsSanityCheckFlags.java)
- [TestGeneratedFormsMustSpell.java](TestGeneratedFormsMustSpell.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
