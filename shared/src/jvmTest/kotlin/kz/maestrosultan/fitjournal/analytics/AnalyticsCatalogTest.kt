package kz.maestrosultan.fitjournal.analytics

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kz.maestrosultan.fitjournal.domain.workout.ResultType

/**
 * The catalog is the reporting contract, and GA4 drops what breaks its limits
 * silently — so every limit is enforced here, at test time, not at runtime.
 * The last test keeps `docs/analytics/events.json` byte-identical to the catalog.
 */
class AnalyticsCatalogTest {

    private val nameRegex = Regex("^[a-z][a-z0-9_]*$")
    private val reservedPrefixes = listOf("firebase_", "google_", "ga_")

    /** GA4 / Firebase reserved automatic event names (logEvent rejects them). */
    private val reservedEventNames = setOf(
        "ad_activeview", "ad_click", "ad_exposure", "ad_impression", "ad_query", "ad_reward", "adunit_exposure",
        "app_background", "app_clear_data", "app_exception", "app_remove", "app_store_refund",
        "app_store_subscription_cancel", "app_store_subscription_convert", "app_store_subscription_renew",
        "app_update", "app_upgrade", "dynamic_link_app_open", "dynamic_link_app_update", "dynamic_link_first_open",
        "error", "first_open", "first_visit", "in_app_purchase", "notification_dismiss", "notification_foreground",
        "notification_open", "notification_receive", "os_update", "session_start", "session_start_with_rollout",
        "user_engagement",
    )

    private val reservedUserPropertyNames = setOf(
        "first_open_time", "first_visit_time", "last_deep_link_referrer", "user_id", "first_open_after_install",
    )

    @Test
    fun everyEventSpecRespectsGa4Limits() {
        val events = AnalyticsCatalog.events
        assertTrue(events.size <= Ga4Limits.EVENT_NAMES_MAX)
        assertEquals(events.size, events.map { it.name }.toSet().size, "duplicate event names")
        assertEquals(events, AnalyticsCatalog.samples().map { it.spec }, "catalog order must follow samples()")

        events.forEach { spec ->
            assertTrue(spec.name.length <= Ga4Limits.EVENT_NAME_MAX, "${spec.name}: name too long")
            assertTrue(nameRegex.matches(spec.name), "${spec.name}: bad name")
            assertTrue(spec.params.size <= Ga4Limits.PARAMS_PER_EVENT_MAX, "${spec.name}: too many params")
            assertEquals(spec.params.size, spec.params.map { it.name }.toSet().size, "${spec.name}: duplicate params")
            assertTrue(spec.funnels.all { it.matches(Regex("^F[1-5]$")) }, "${spec.name}: unknown funnel ${spec.funnels}")
            spec.params.forEach { param ->
                val where = "${spec.name}.${param.name}"
                assertTrue(param.name.length <= Ga4Limits.PARAM_NAME_MAX, "$where: name too long")
                assertTrue(nameRegex.matches(param.name), "$where: bad name")
                param.vocab?.forEach { value ->
                    assertEquals(ParamType.STRING, param.type, "$where: only strings carry a vocabulary")
                    assertTrue(value.length <= Ga4Limits.PARAM_VALUE_MAX, "$where: vocab value '$value' too long")
                    assertTrue(nameRegex.matches(value), "$where: vocab value '$value' is not a closed token")
                }
            }
        }
    }

    @Test
    fun everySampleInstanceMatchesItsSpec() {
        val samples = AnalyticsCatalog.samples()
        val cases = AnalyticsEvent::class.java.declaredClasses
            .filter { AnalyticsEvent::class.java.isAssignableFrom(it) }
            .toSet()
        assertEquals(14, cases.size, "the catalog has 14 cases")
        assertEquals(14, samples.size)
        assertEquals(cases, samples.map { it.javaClass }.toSet(), "exactly one sample per case")

        samples.forEach { sample ->
            assertMatchesSpec(sample)
            assertEquals(sample.spec.params.map { it.name }.toSet(), sample.params.keys, "${sample.spec.name}: param keys != spec")
        }

        // Variants the samples don't cover: the false Booleans, absent optionals, every enum value.
        val date = LocalDate(2026, 12, 31)
        val falseFlags = listOf(
            AnalyticsEvent.SignIn(SignInMethod.GOOGLE, isNewUser = false),
            AnalyticsEvent.ExerciseAdded("j", date, 2, "e", 1, ExerciseSource.REPEAT, firstInSlot = false),
        )
        falseFlags.forEach(::assertMatchesSpec)
        assertEquals("false", falseFlags[0].params["is_new_user"])
        assertEquals("false", falseFlags[1].params["first_in_slot"])
        assertEquals("true", (samples.first { it is AnalyticsEvent.SignIn }).params["is_new_user"])
        assertEquals("2026-12-31", falseFlags[1].params["workout_date"])
        assertEquals(2L, falseFlags[1].params["workout_number"])

        val restore = AnalyticsEvent.PurchaseResult(PurchaseOutcome.RESTORED, productId = null, entry = null)
        assertMatchesSpec(restore)
        assertEquals(setOf("outcome"), restore.params.keys, "null optionals are absent, never empty strings")

        val everyEnumValue = ScreenName.entries.map { AnalyticsEvent.ScreenView(it) } +
            Feature.entries.map { AnalyticsEvent.FeatureUsed(it) } +
            PaywallEntry.entries.map { AnalyticsEvent.PaywallRequested(it) } +
            PurchaseOutcome.entries.map { AnalyticsEvent.PurchaseResult(it, "p", PaywallEntry.REPEAT) } +
            SessionEndVia.entries.map { AnalyticsEvent.WorkoutSessionEnd("j", date, 1, 1, it) } +
            ResultType.entries.flatMap { type ->
                SetAction.entries.map { AnalyticsEvent.SetLogged("j", date, 1, "r", "e", 0, type, it) }
            }
        everyEnumValue.forEach(::assertMatchesSpec)
        assertEquals(
            listOf("weight_reps", "distance_duration"),
            ResultType.entries.map { AnalyticsEvent.SetLogged("j", date, 1, "r", "e", 0, it, SetAction.ADD).params["result_type"] },
        )
    }

    /** Keys are declared, required keys present, every value of its declared type and vocabulary. */
    private fun assertMatchesSpec(event: AnalyticsEvent) {
        val spec = event.spec
        val declared = spec.params.associateBy { it.name }
        val optional = if (event is AnalyticsEvent.PurchaseResult) setOf("product_id", "entry") else emptySet()
        assertTrue(declared.keys.containsAll(event.params.keys), "${spec.name}: undeclared ${event.params.keys - declared.keys}")
        assertTrue(event.params.keys.containsAll(declared.keys - optional), "${spec.name}: missing required params")
        event.params.forEach { (key, value) ->
            val param = declared.getValue(key)
            val where = "${spec.name}.$key"
            when (param.type) {
                ParamType.STRING -> {
                    val text = assertIs<String>(value, where)
                    assertTrue(text.length <= Ga4Limits.PARAM_VALUE_MAX, "$where: value too long")
                    param.vocab?.let { assertTrue(text in it, "$where: '$text' not in $it") }
                    if (key == "workout_date") assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(text), where)
                }
                ParamType.INT -> assertIs<Long>(value, where)
                ParamType.DOUBLE -> assertIs<Double>(value, where)
            }
        }
    }

    @Test
    fun noReservedOrPrefixedNames() {
        AnalyticsCatalog.events.forEach { spec ->
            assertTrue(spec.name !in reservedEventNames, "${spec.name} is reserved")
            assertTrue(reservedPrefixes.none { spec.name.startsWith(it) }, "${spec.name} has a reserved prefix")
            spec.params.forEach { param ->
                // exportName is the SDK's own export representation, not a name we choose — exempt.
                assertTrue(reservedPrefixes.none { param.name.startsWith(it) }, "${spec.name}.${param.name} prefixed")
            }
        }
        val exportNames = AnalyticsCatalog.events.flatMap { spec ->
            spec.params.mapNotNull { p -> p.exportName?.let { "${spec.name}.${p.name}->$it" } }
        }
        assertEquals(listOf("screen_view.screen_name->firebase_screen"), exportNames)

        AnalyticsCatalog.userProperties.forEach { spec ->
            val name = spec.property.value
            assertTrue(name !in reservedUserPropertyNames, "$name is reserved")
            assertTrue(reservedPrefixes.none { name.startsWith(it) }, "$name has a reserved prefix")
        }
    }

    @Test
    fun userPropertiesRespectLimits() {
        val specs = AnalyticsCatalog.userProperties
        assertTrue(specs.size <= Ga4Limits.USER_PROPERTIES_MAX)
        assertEquals(UserProperty.entries.toList(), specs.map { it.property }, "one spec per UserProperty, in order")
        assertEquals(
            listOf(UserPropertyScope.ACCOUNT, UserPropertyScope.ACCOUNT, UserPropertyScope.ACCOUNT, UserPropertyScope.PROCESS),
            specs.map { it.property.scope },
        )
        specs.forEach { spec ->
            val name = spec.property.value
            assertTrue(name.length <= Ga4Limits.USER_PROPERTY_NAME_MAX, "$name: name too long")
            assertTrue(nameRegex.matches(name), "$name: bad name")
            spec.vocab?.forEach { value ->
                assertTrue(value.length <= Ga4Limits.USER_PROPERTY_VALUE_MAX, "$name: '$value' too long")
            }
        }
    }

    @Test
    fun renderedDictionaryMatchesCheckedInEventsJson() {
        val rendered = AnalyticsCatalog.renderJson()
        val root = Json.parseToJsonElement(rendered).jsonObject

        // The facts downstream SQL and docs rely on, asserted on the rendering itself.
        val screens = root.getValue("screens").jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("journals", "exercises", "notes", "measurements", "settings", "calculator", "workout_plans"),
            screens.filter { it.getValue("instrumented").jsonPrimitive.content == "false" }
                .map { it.getValue("name").jsonPrimitive.content },
        )
        val params = root.getValue("events").jsonArray.flatMap { event ->
            event.jsonObject.getValue("params").jsonArray.map { event.jsonObject.string("name") to it.jsonObject }
        }
        assertEquals(
            listOf("screen_view" to "firebase_screen"),
            params.filter { "export_name" in it.second }.map { it.first to it.second.string("export_name") },
        )
        listOf("first_in_slot", "is_new_user").forEach { flag ->
            val spec = params.single { it.second.string("name") == flag }.second
            assertEquals("string", spec.string("type"))
            assertEquals(listOf("true", "false"), (spec.getValue("vocab") as JsonArray).map { it.jsonPrimitive.content })
        }
        assertTrue(root.getValue("user_properties").jsonArray.all { "scope" in it.jsonObject })

        val checkedIn: Path = Paths.get(System.getProperty("user.dir"), "..", "docs", "analytics", "events.json").normalize()
        val current = if (Files.exists(checkedIn)) Files.readString(checkedIn) else null
        if (current != rendered) {
            val fresh = Paths.get(System.getProperty("user.dir"), "build", "analytics", "events.json").toAbsolutePath()
            Files.createDirectories(fresh.parent)
            Files.writeString(fresh, rendered)
            fail(
                "$checkedIn is ${if (current == null) "missing" else "stale"}. " +
                    "Rendered copy: $fresh — update with: cp '$fresh' '$checkedIn'",
            )
        }
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
