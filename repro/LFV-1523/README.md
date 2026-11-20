# Hunspell accepts a word made of a CIRCUMFIX prefix without its circumfix suffix (spell("prestem") is true) while it rejects the suffix alone: the pair is compared only while a suffix is removed; every release from 8.9.0 to 10.6.0, branch_11x and main; known outside Lucene

Finding LFV-1523 · Area: analysis / hunspell (Stemmer affix removal, the CIRCUMFIX flag: Hunspell.spell, getRoots, suggest, HunspellStemFilter) · Priority: low · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary that declares 'CIRCUMFIX X', a prefix rule 'pre' and a suffix rule 'suf' that both carry the flag X, and the entry stem/PS, and call Hunspell.spell. You expect 'prestemsuf' to be a word and each half alone, 'prestem' and 'stemsuf', to be rejected, because the two rules are one affix around the word: Lucene's own comment at the check says the pair is checked 'and vice versa', and the format's manual says the same. Instead spell("prestem") returns true (and the stemmer and HunspellStemFilter reduce 'prestem' to 'stem') while 'stemsuf' is rejected: Stemmer.needsAnotherAffix compares the flags of the prefix and the suffix only while a suffix is being removed, so a prefix that carries the flag and is followed by no suffix is never checked. Not new in 11: every release since 8.9.0 does it, and 4.8.0 to 8.8.2 rejected the lone prefix.

## Expected vs actual

- Expected: You expect 'prestemsuf' to be a word and each half alone, 'prestem' and 'stemsuf', to be rejected, because the two rules are one affix around the word: Lucene's own comment at the check says the pair is checked 'and vice versa', and the format's manual says the same.
- Actual: spell("prestem") returns true (and the stemmer and HunspellStemFilter reduce 'prestem' to 'stem') while 'stemsuf' is rejected: Stemmer.needsAnotherAffix compares the flags of the prefix and the suffix only while a suffix is being removed, so a prefix that carries the flag and is followed by no suffix is never checked.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestCircumfixPrefixAlone.java](src/org/apache/lucene/analysis/hunspell/TestCircumfixPrefixAlone.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 9d2b5a15427a (a commit of 2026-11-16): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on the 11.0 release branch (branch_11x) and on Lucene main, with and without assertions; the finding's record lists every release from 8.9.0 on. Known outside Lucene only: the issue tracker of the reference implementation of the format (Hunspell) has an issue about the same answer on the same dictionary shape (issue 1060, closed in 2026 by a change of the reference implementation); no Lucene issue, pull request or list thread was found that describes it. What the test does: it loads a dictionary with the line CIRCUMFIX X, a prefix rule 'pre' and a suffix rule 'suf' that both carry the flag X, and the one entry stem/PS, and asks Hunspell.spell about three words: 'prestemsuf' (a word, as expected), 'stemsuf' (not a word, as expected) and 'prestem', which is expected not to be a word. The third assertion fails with java.lang.AssertionError: the prefix without its suffix: spell("prestem") returned true. The dictionary and the words are fixed text; the test needs no index. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the four 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-analysis-common) in place of the four $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/analysis/common.
- This finding was re-run on Lucene main f2b061406c4b on 2026-11-19: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: the prefix without its suffix (the two assertions before it pass).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestCircumfixPrefixAlone
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
