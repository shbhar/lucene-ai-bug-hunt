# AI-assisted bug hunt in Apache Lucene

A dated snapshot (2026-10-29) of AI-assisted work: defects found in [Apache Lucene](https://github.com/apache/lucene). Every bug the hunt found and confirmed went through AI-run checks: an independent repro, an intended-behaviour check and a prior-report search. This is an independent list: per the Apache Lucene project’s AI policy, nothing here is filed in Lucene unless a person files it by hand.

Browse the table: open `index.html` (published with GitHub Pages).

## What is in this snapshot

- 963 registered bugs: 715 new, for which the automated prior-report search found no earlier report (a keyword search; private reports are not visible), and 248 already known upstream (others reported or discussed the same bug; tracked, but not counted as new). Where the search found only a partial overlap, the bug's novelty note names the closest item; the owner decides whether it counts as known.
- 2 of them are withheld pending security review: they appear in the table and in every count, but only with their id, module, priority tier, novelty (new or known upstream) and the line "Details withheld pending security review".
- Priority tiers: 9 high, 317 medium, 629 low, 8 not yet ranked.
- 344 public bug pages (`bugs/`), 961 repro directories (`repro/`) and 117 units of formal models and scripts (`models/`).
- The full table as data: `data/bugs.csv` and `data/bugs.json`.

## How the bugs were found

The hunt used AI models (Anthropic Claude Opus and OpenAI GPT-5.6) with formal methods (TLA+/TLC model checking, Alloy, Z3/cvc5 SMT solving, Dafny proofs and bounded model checking) alongside fuzzing, model-based, property-based and differential testing, and static analysis. Of the 713 new bugs the hunt found that are shown in full, a formal tool's own output gave the first signal for 44; a semi-formal check (a check of the real jar against exact semantics over a bounded input space, exhaustive or for some harnesses randomly sampled, or a hypothesis formed while modelling that a tool then witnessed) for 414; code reading or other testing for 223; and a testing baseline for 32. Every bug ends in a minimal executable test that fails on Lucene main while the bug is present; 3 tests of the earlier study assert the current behaviour instead (they pass while the bug is present), and their repro pages say so.

## Lucene version

Tested against Lucene main ea57360912ed (2026-10-24) unless a repro says otherwise. On 2026-10-26, 470 of the repros were set up for a rerun on main a9779ef3c193. 468 ran and still show the bug (466 unchanged, 2 with changed behaviour: LFV-160, LFV-462, one of them with an adapted repro); 2 (LFV-003, LFV-069) could not be run, because the rerun host had too little memory for them. None had been fixed upstream. The repros of the other bugs shown in full were last run on these commits: 396 on a9779ef3c193, 95 on ea57360912ed, 2 without a recorded commit; each repro page says where its repro was run.

## Reproduce a bug

Each `repro/<id>/` directory holds the minimal test sources, what they show, expected against actual, and a recipe: JDK 25, a Lucene checkout at the tested commit built with `./gradlew --no-daemon assemble`, and the JUnit Platform console launcher, whose vintage engine runs `LuceneTestCase` tests. A failing test is the bug reproducing; a rerun with the same seed replays the same random choices.

## How to help

- If you know the code: pick a bug, check it, and say what you found in an issue in [this repository](https://github.com/shbhar/lucene-ai-bug-hunt/issues) ("real", "intended", "duplicate of ...", or a better fix).
- Please do not open issues or pull requests in apache/lucene about these findings unless a Lucene committer agrees one should be filed. Following Lucene's AI policy, upstream issues and pull requests are written by a person, in their own words.
- The 2 withheld rows rated security-sensitive (in scope of Lucene's SECURITY.md) go privately to security@lucene.apache.org first.
- If you believe any finding has security implications, report it privately to security@lucene.apache.org rather than in a public issue.

## Layout

| Path | Content |
|---|---|
| `index.html` | the bug table and the method notes |
| `bugs/<id>.html`, `assets/<id>/` | public bug pages and their diagrams |
| `repro/<id>/` | minimal repro sources, README and run recipe |
| `models/<unit>/` | formal models and scripts of the hunt units that worked on a public bug |
| `data/bugs.csv`, `data/bugs.json` | the table as data (public fields only) |

## License

Apache License 2.0 (see `LICENSE`). Apache Lucene and Apache are trademarks of The Apache Software Foundation; this project is independent and not endorsed by it. See `NOTICE`.
