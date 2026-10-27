# BengaliNormalizer's khio rule tests U+09BF (vowel sign I) instead of U+09B7 (SSA), so the common conjunct ক্ষ (KA+HASANTA+SSA) is never folded to KHA / KA+KHA as the rule comment and the cited paper say, and the malformed KA+HASANTA+VOWEL SIGN I is folded instead

Bug LFV-667 · Area: analysis-common / Bengali normalizer (BengaliNormalizer.normalize; BengaliNormalizationFilter, BengaliAnalyzer and the 'bengaliNormalization' factory) · Priority: medium · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-667.html](../../bugs/LFV-667.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Bengali text with the stock BengaliAnalyzer or BengaliNormalizationFilter. Its normalizer has a rule labelled 'Khio (Ka + Hoshonto + Murdorno Sh)' and says it implements a cited paper, which folds the conjunct ক্ষ (KA+HASANTA+SSA) to খ (KHA) at the start of a word and to কখ (KA+KHA) elsewhere, so ক্ষমা should normalize to খমা. Instead the rule's condition compares the third character with U+09BF (BENGALI VOWEL SIGN I) instead of U+09B7 (BENGALI LETTER SSA), one hex digit off, so the rule never fires on real text: ক্ষমা becomes ক্সমা and পরীক্ষা becomes পরিক্সা (only the separate 'All sh' rule applies, SSA to SA), while the malformed sequence KA+HASANTA+VOWEL SIGN I is folded instead (ক্িমা becomes খমা), with no error.

## Expected vs actual

- Expected: `ক্ষমা` → `খমা`, `পরীক্ষা` → `পরিকখা`, and `BengaliAnalyzer` gives `ক্ষমা` the term `খম`, like `খমা`.
- Actual: `ক্ষমা` → `ক্সমা`, `পরীক্ষা` → `পরিক্সা`, and the analyzer term is `ক্সম`: 3 of 3 tests fail, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/bn/TestBengaliKhioRule.java](src/org/apache/lucene/analysis/bn/TestBengaliKhioRule.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.bn.TestBengaliKhioRule
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestBengaliKhio.java](TestBengaliKhio.java)
- [TestBengaliKhioRule.java](TestBengaliKhioRule.java)
- [KhioOptionsProbe.java](KhioOptionsProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
