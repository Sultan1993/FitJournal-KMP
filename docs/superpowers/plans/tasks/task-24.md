### Task 24: iOS declinable paywall and reusable quota presenter

**Goal:** Make the paywall dismissable with a one-shot finish, let the coordinator proceed on decline, and expose one reusable non-private presenter for the in-app quota surface.

**Files:**
- Modify `iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift`
- Modify `iOS/FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift`
- Modify `iOS/FitJournal/Configuration/ConfigurationCoordinator.swift`

*`SuperwallPaywallContract.swift` is deliberately NOT in this list: the one-shot guard is a private `hasFinished` flag inside the ViewModel, and the contract's existing `ViewAction.finish` / `Event.closePaywall` cases are reused unchanged.*

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: SDK callback wiring, no iOS test target; matrix M21/M22 are the real checks.*

1. **`SuperwallPaywallViewModel`** — make finishing one-shot. With Non-Gated feature gating, **both** the `feature` block and `onDismiss` fire, so `consume(.finish)` arrives twice. Add `private var hasFinished = false` and guard the `.finish` handler:
```swift
        case .finish:
            guard !hasFinished else { return }
            hasFinished = true
            event.value = .closePaywall
```

2. **`SubscriptionPaywallViewController`** — configurable placement (defaulting to the onboarding key) plus a presentation handler. The pinned iOS SDK (4.15.3) exposes `register()` with optional `params` and optional `handler`, so the `register(placement:handler:feature:)` form below is valid — **do not pass `params:`**:
```swift
    private let placement: String

    init(viewModel: SuperwallPaywallViewModel,
         placement: String = FirebaseRemoteConfig.getString(key: .paywallPlacement)) {
        self.viewModel = viewModel
        self.placement = placement
        super.init(nibName: nil, bundle: nil)
    }
```
```swift
    private func setupPaywall() {
        loadingIndicator?.removeFromSuperview()
        loadingIndicator = nil

        // Idempotent finish: the ViewModel's hasFinished guard absorbs the double
        // call that Non-Gated feature gating produces (feature block AND onDismiss).
        let finish: () -> Void = { [weak self] in self?.viewModel.consume(.finish) }

        let handler = PaywallPresentationHandler()
        // Declined / purchased / restored — every dismissal continues.
        handler.onDismiss { _, _ in finish() }
        // Holdout, no audience match, or the placement isn't in a campaign:
        // nothing to show, so do not strand the user on a blank screen.
        handler.onSkip { _ in finish() }
        handler.onError { _ in finish() }

        Superwall.shared.register(placement: placement, handler: handler) { finish() }
    }
```

3. **Self-dismiss when there is no delegate.** In `setupObservers`, change the `.closePaywall` branch to:
```swift
            case .closePaywall:
                if let delegate = vc.delegate {
                    delegate.paywallDidFinish(vc)
                } else {
                    // Modally presented in-app quota paywall — no coordinator is
                    // involved, so it dismisses itself.
                    vc.dismiss(animated: true)
                }
```

4. **The delegate rename.** `paywallDidFinishWithSuccess(_:)` no longer describes what happens — a decline also finishes. Rename it to `paywallDidFinish(_:)` in the protocol here, and update the single conformance in `ConfigurationCoordinator.swift`:
```swift
extension ConfigurationCoordinator: SuperwallPaywallControllerDelegate {

    /// Declined AND purchased both land here — the onboarding paywall is
    /// declinable now, so finishing means "continue into the app", not
    /// "purchased".
    func paywallDidFinish(_ vc: SuperwallPaywallViewController) {
        startMigration()
    }
}
```
   The `configurationDidFinish(_:shouldShowPaywall:)` push path stays exactly as it is: a non-entitled user still SEES the onboarding paywall (Day-0 purchase intent is the point) — only declining now proceeds instead of dead-ending.

5. **Add the reusable presenter** at **top level** of `SubscriptionPaywallViewController.swift`, so Tasks 25 and 26 both call one non-private entry point:
```swift
/// Presents the in-app quota paywall modally from any presenting view controller.
/// Used by the shared Workout screen's ShowPaywall effect and by the
/// repeat-workout / add-to-date entry points. Sets no delegate on purpose: the
/// controller dismisses itself when `delegate == nil`, so nothing needs to be
/// routed through a coordinator — no navigation state changes and nothing is
/// pushed, so there is nothing for a coordinator to coordinate.
@MainActor
func presentQuotaPaywall(from presenter: UIViewController) {
    let vc = SuperwallPaywallViewController(
        viewModel: SuperwallPaywallViewModel(),
        placement: FirebaseRemoteConfig.getString(key: .paywallPlacementQuota)
    )
    vc.modalPresentationStyle = .fullScreen
    presenter.present(vc, animated: true)
}
```

6. Do not change `EventKit.logEvent(.paywallPage)` or the loading indicator, and do not edit `SuperwallPaywallContract.swift`.

7. **Dashboard prerequisite (record it in the completion note):** the onboarding placement's Feature Gating must be **Non Gated**, and the paywall template must carry a visible dismissal element ("Continue with the free version").

**Acceptance Criteria:**
- Finishing is one-shot: a `hasFinished` guard exists in the ViewModel and `closePaywall` is emitted at most once per presentation.
- All three callbacks — `onDismiss`, `onSkip`, `onError` — are present and route to the same `finish` closure.
- The onboarding placement comes from `FirebaseKey.paywallPlacement`; `presentQuotaPaywall(from:)` uses `FirebaseKey.paywallPlacementQuota`. No `"paywall_final"` literal remains in the file.
- `paywallDidFinishWithSuccess` is renamed to `paywallDidFinish`; the old name appears nowhere in the repo; the coordinator proceeds on decline as well as purchase.
- `.closePaywall` self-dismisses when `delegate == nil`.
- `presentQuotaPaywall(from:)` is declared at top level, `@MainActor`, not `private`.
- `SuperwallPaywallContract.swift` is unmodified.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && V=FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift && M=FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift && rg -q '^@MainActor\nfunc presentQuotaPaywall\(from presenter: UIViewController\)' -U $V && test $(grep -c 'handler.onDismiss\|handler.onSkip\|handler.onError' $V) -eq 3 && grep -q 'Superwall.shared.register(placement: placement, handler: handler)' $V && rg -U 'if let delegate = vc.delegate \{[\s\S]*?vc.dismiss\(animated: true\)' $V && grep -q 'key: .paywallPlacementQuota' $V && grep -q 'key: .paywallPlacement)' $V && ! grep -q 'paywall_final' $V && grep -q 'private var hasFinished = false' $M && grep -q 'hasFinished = true' $M && ! rg -q 'paywallDidFinishWithSuccess' FitJournal && git diff --quiet -- FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallContract.swift`

```json:metadata
{"files":["iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift","iOS/FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift","iOS/FitJournal/Configuration/ConfigurationCoordinator.swift"],"modelTier":"frontier","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && V=FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift && M=FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift && rg -q '^@MainActor\\nfunc presentQuotaPaywall\\(from presenter: UIViewController\\)' -U $V && test $(grep -c 'handler.onDismiss\\|handler.onSkip\\|handler.onError' $V) -eq 3 && grep -q 'Superwall.shared.register(placement: placement, handler: handler)' $V && rg -U 'if let delegate = vc.delegate \\{[\\s\\S]*?vc.dismiss\\(animated: true\\)' $V && grep -q 'key: .paywallPlacementQuota' $V && grep -q 'key: .paywallPlacement)' $V && ! grep -q 'paywall_final' $V && grep -q 'private var hasFinished = false' $M && grep -q 'hasFinished = true' $M && ! rg -q 'paywallDidFinishWithSuccess' FitJournal && git diff --quiet -- FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallContract.swift","acceptanceCriteria":["hasFinished guard exists in the ViewModel; closePaywall emitted at most once per presentation","All three callbacks onDismiss, onSkip, onError present and routed to the same finish closure","Onboarding placement from FirebaseKey.paywallPlacement; presentQuotaPaywall uses paywallPlacementQuota; no paywall_final literal remains","paywallDidFinishWithSuccess renamed to paywallDidFinish and absent repo-wide; coordinator proceeds on decline as well as purchase",".closePaywall self-dismisses when delegate == nil","presentQuotaPaywall(from:) declared at top level, @MainActor, not private","SuperwallPaywallContract.swift unmodified","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[21]}
```

---

