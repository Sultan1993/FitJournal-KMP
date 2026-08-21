### Task 13: Android four Remote Config keys and defaults

**Goal:** Declare the four quota/placement Remote Config keys and their bundled defaults.

**Files:**
- Modify `Android/common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt`
- Modify `Android/common/remoteconfig/src/main/res/xml/remote_config_defaults.xml`

**Steps:**

*No failing-test step: four constants and four XML defaults. There is no logic to assert; compilation and Task 28's matrix are the real checks.*

1. In `RemoteConfigKey.kt`, append inside the `object`:
```kotlin
    // Free-workout-day quota (usage-metered reverse trial).
    const val FREE_WORKOUT_QUOTA = "free_workout_quota"

    // ISO-8601 instant: the moment metering was ACTIVATED. Workout days whose
    // earliest record was created at-or-after this count against the quota.
    // NEVER backdate this in the console — doing so retroactively charges days
    // logged before it. The 9999 default means "metering off".
    const val FREE_WORKOUT_QUOTA_STARTED_AT = "free_workout_quota_started_at"

    // Superwall placements. PAYWALL_PLACEMENT is the onboarding/launch-gate
    // paywall; PAYWALL_PLACEMENT_QUOTA is the in-app quota paywall. The single
    // server-side switch for swapping in a no-trial campaign with no app release.
    const val PAYWALL_PLACEMENT = "paywall_placement"
    const val PAYWALL_PLACEMENT_QUOTA = "paywall_placement_quota"
```

2. In `remote_config_defaults.xml`, append inside `<defaultsMap>`, matching the existing `<entry>` style:
```xml
    <entry>
        <key>free_workout_quota</key>
        <value>10</value>
    </entry>

    <!--
        Far-future sentinel = metering OFF. Set this to the ACTIVATION instant
        (current UTC time, rounded to the minute) in the Firebase console when
        turning metering on. Never backdate it: days logged before the cutoff are
        free forever, and moving the cutoff backwards retroactively charges them.
        WorkoutQuotaGate returns Unlimited whenever the effective cutoff is in the
        future, which is what makes this sentinel mean "off" rather than "0 used".
    -->
    <entry>
        <key>free_workout_quota_started_at</key>
        <value>9999-01-01T00:00:00Z</value>
    </entry>

    <entry>
        <key>paywall_placement</key>
        <value>paywall_final</value>
    </entry>

    <entry>
        <key>paywall_placement_quota</key>
        <value>paywall_final</value>
    </entry>
```

3. Change nothing else; do not reorder existing entries.

**Acceptance Criteria:**
- All four constants exist with exactly the key strings in the header contract table.
- All four `<entry>` blocks exist with defaults `10`, `9999-01-01T00:00:00Z`, `paywall_final`, `paywall_final`.
- XML remains well-formed; no existing entry modified.
- `:common:remoteconfig:compileDebugKotlin` succeeds (module-scoped per build-rule B2).

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :common:remoteconfig:compileDebugKotlin && test $(grep -c 'free_workout_quota\|paywall_placement' common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt) -eq 4 && test $(grep -c '<key>free_workout_quota</key>\|<key>free_workout_quota_started_at</key>\|<key>paywall_placement</key>\|<key>paywall_placement_quota</key>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml) -eq 4 && grep -q '<value>9999-01-01T00:00:00Z</value>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml`

```json:metadata
{"files":["Android/common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt","Android/common/remoteconfig/src/main/res/xml/remote_config_defaults.xml"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :common:remoteconfig:compileDebugKotlin && test $(grep -c 'free_workout_quota\\|paywall_placement' common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt) -eq 4 && test $(grep -c '<key>free_workout_quota</key>\\|<key>free_workout_quota_started_at</key>\\|<key>paywall_placement</key>\\|<key>paywall_placement_quota</key>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml) -eq 4 && grep -q '<value>9999-01-01T00:00:00Z</value>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml","acceptanceCriteria":["Four constants exist with exactly the pinned key strings","Four XML entries exist with defaults 10, 9999-01-01T00:00:00Z, paywall_final, paywall_final","XML well-formed; no existing entry modified or reordered",":common:remoteconfig:compileDebugKotlin succeeds"],"blockedBy":[12]}
```

---

