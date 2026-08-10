---
name: project_phaseA_domain_fixes
description: Phase A of a 4-phase fix batch — domain/data layer only, later phase redoes UI
metadata:
  type: project
---

Phase A of a 4-phase fix batch landed on 2026-08-08: domain/DistractorGenerator.kt (new,
`distractors(example, random)`), QuizSubMode gained a third value `FIXED_LEVEL` (default
quizSubMode changed from SHUFFLE to PROGRESSION), and Stats.kt/StatsRepository.kt got
`LevelStats` + `observeLevelStats(operation)` plus `WeakSpot.MIN_ATTEMPTS_TO_FLAG` lowered 3→1
with a new unbucketed whole-operation weak spot added alongside the existing range/level-bucketed
ones in `observeWeakSpots()`.

**Why:** Explicit phase boundary set by the user — UI (`ui/` package) and `strings.xml` are
off-limits in phase A except for one narrowly-scoped exception (see below). A later phase (B?)
is expected to redo the UI to actually use `distractors()`, `LevelStats`, and `FIXED_LEVEL`
(currently `FIXED_LEVEL` has no label/UI beyond generic `.entries` iteration, and
`observeLevelStats` has no caller yet).

**How to apply:** When picking up follow-on UI work in this app, check whether it's meant to
consume these already-built domain primitives before adding new ones. One string-file exception
was made in phase A: `quiz_sub_mode_fixed_level` was added to all three strings.xml (values/
values-uk/values-ru) because leaving `EnumLabels.kt`'s `QuizSubMode.labelRes()` non-exhaustive
would not compile — that was the *only* string-file touch allowed in phase A. Don't infer from
this that strings.xml is generally fair game in "domain-only" phases; it isn't unless a compile
error forces it.
