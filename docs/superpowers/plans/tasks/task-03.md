### Task 3: KMP quota strings in four locales

**Goal:** Add the four localized meter strings as Compose resources, with correct ru/uk plural categories.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/composeResources/values/strings.xml`
- Modify `Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml`
- Modify `Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml`
- Modify `Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml`

**Steps:**

Append inside the existing `<resources>` element of each file. Do not reorder or touch existing entries. Both plurals are `%1$d`-only and are read with the count passed twice (`pluralStringResource(res, n, n)`), so no positional-argument mismatch is possible.

1. `values/strings.xml` (en):
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">%1$d free workout left</item>
        <item quantity="other">%1$d free workouts left</item>
    </plurals>
    <!-- NOTE: bare apostrophe on purpose. Compose Resources stores raw XML
     text and does NOT process Android's \' escape, so "You\'ve" would
     ship a literal backslash to users. -->
<plurals name="quota_exhausted_title">
        <item quantity="one">You've used your %1$d free workout</item>
        <item quantity="other">You've used your %1$d free workouts</item>
    </plurals>
    <string name="quota_exhausted_subtitle">Your history stays yours. Go Pro to log new workouts.</string>
    <string name="quota_upgrade_cta">Upgrade</string>
```

2. `values-de/strings.xml`:
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">%1$d kostenloses Workout übrig</item>
        <item quantity="other">%1$d kostenlose Workouts übrig</item>
    </plurals>
    <plurals name="quota_exhausted_title">
        <item quantity="one">Du hast dein %1$d kostenloses Workout verbraucht</item>
        <item quantity="other">Du hast deine %1$d kostenlosen Workouts verbraucht</item>
    </plurals>
    <string name="quota_exhausted_subtitle">Dein Verlauf bleibt dir erhalten. Hol dir Pro, um neue Workouts zu speichern.</string>
    <string name="quota_upgrade_cta">Upgrade</string>
```

3. `values-ru/strings.xml`:
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">осталась %1$d бесплатная тренировка</item>
        <item quantity="few">осталось %1$d бесплатные тренировки</item>
        <item quantity="many">осталось %1$d бесплатных тренировок</item>
        <item quantity="other">осталось %1$d бесплатных тренировок</item>
    </plurals>
    <plurals name="quota_exhausted_title">
        <item quantity="one">Вы использовали %1$d бесплатную тренировку</item>
        <item quantity="few">Вы использовали %1$d бесплатные тренировки</item>
        <item quantity="many">Вы использовали %1$d бесплатных тренировок</item>
        <item quantity="other">Вы использовали %1$d бесплатных тренировок</item>
    </plurals>
    <string name="quota_exhausted_subtitle">История остаётся вашей. Оформите Pro, чтобы записывать новые тренировки.</string>
    <string name="quota_upgrade_cta">Оформить</string>
```

4. `values-uk/strings.xml`:
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">залишилося %1$d безкоштовне тренування</item>
        <item quantity="few">залишилося %1$d безкоштовні тренування</item>
        <item quantity="many">залишилося %1$d безкоштовних тренувань</item>
        <item quantity="other">залишилося %1$d безкоштовних тренувань</item>
    </plurals>
    <plurals name="quota_exhausted_title">
        <item quantity="one">Ви використали %1$d безкоштовне тренування</item>
        <item quantity="few">Ви використали %1$d безкоштовні тренування</item>
        <item quantity="many">Ви використали %1$d безкоштовних тренувань</item>
        <item quantity="other">Ви використали %1$d безкоштовних тренувань</item>
    </plurals>
    <string name="quota_exhausted_subtitle">Історія залишається вашою. Оформіть Pro, щоб записувати нові тренування.</string>
    <string name="quota_upgrade_cta">Оформити</string>
```

**Acceptance Criteria:**
- All four files contain `quota_workouts_left`, `quota_exhausted_title`, `quota_exhausted_subtitle`, `quota_upgrade_cta`.
- ru and uk carry `one`/`few`/`many`/`other`; en and de carry `one`/`other`.
- Every XML file remains well-formed; no existing entry modified or reordered.
- `:shared:assemble` generates all four `Res.*` accessors.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/composeResources/values/strings.xml","Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml","Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml","Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["All four files contain the four new resource names","ru and uk carry one/few/many/other; en and de carry one/other","Every XML file well-formed; no existing entry modified or reordered",":shared:assemble generates Res.plurals.quota_workouts_left, Res.plurals.quota_exhausted_title, Res.string.quota_exhausted_subtitle, Res.string.quota_upgrade_cta"]}
```

---

