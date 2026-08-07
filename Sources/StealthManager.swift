import SwiftUI
import UIKit
import Combine

/// Central manager for all stealth features:
/// - Shake gesture → hide overlay
/// - 3-finger tap → hide overlay
/// - Screen recording detection → auto-hide
/// - Screenshot detection → flash-hide
/// - App backgrounding → hide
@MainActor
final class StealthManager: ObservableObject {

    @Published var overlayVisible: Bool = true
    @Published var minimalMode: Bool = false      // tiny dot instead of full overlay
    @Published var isBeingRecorded: Bool = false
    @Published var lastHideReason: String = ""

    private var cancellables = Set<AnyCancellable>()

    init() {
        setupScreenCaptureDetection()
        setupBackgroundDetection()
        setupScreenshotDetection()
    }

    // MARK: - Public API

    func toggleOverlay() {
        overlayVisible.toggle()
        lastHideReason = overlayVisible ? "" : "manual"
    }

    func hide(reason: String) {
        overlayVisible = false
        lastHideReason = reason
    }

    func show() {
        overlayVisible = true
        lastHideReason = ""
    }

    func toggleMinimalMode() {
        minimalMode.toggle()
    }

    // MARK: - Screen recording / mirroring detection

    private func setupScreenCaptureDetection() {
        NotificationCenter.default.publisher(for: UIScreen.capturedDidChangeNotification)
            .sink { [weak self] _ in
                guard let self = self else { return }
                let captured = UIScreen.main.isCaptured
                self.isBeingRecorded = captured
                if captured {
                    self.hide(reason: "screen recording detected")
                }
            }
            .store(in: &cancellables)

        // Initial check
        isBeingRecorded = UIScreen.main.isCaptured
        if isBeingRecorded { hide(reason: "screen recording") }
    }

    // MARK: - App background detection

    private func setupBackgroundDetection() {
        NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)
            .sink { [weak self] _ in
                self?.hide(reason: "app backgrounded")
            }
            .store(in: &cancellables)
    }

    // MARK: - Screenshot detection

    private func setupScreenshotDetection() {
        NotificationCenter.default.publisher(for: UIApplication.userDidTakeScreenshotNotification)
            .sink { [weak self] _ in
                self?.hide(reason: "screenshot taken")
            }
            .store(in: &cancellables)
    }
}

// MARK: - Shake gesture support

extension UIWindow {
    open override func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        if motion == .motionShake {
            NotificationCenter.default.post(name: .stealthPanic, object: nil)
        }
        super.motionEnded(motion, with: event)
    }
}

extension Notification.Name {
    static let stealthPanic = Notification.Name("StealthPanicGesture")
}

// MARK: - 3-finger tap modifier

struct ThreeFingerPanicModifier: ViewModifier {
    let action: () -> Void

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(
                SpatialTapGesture(count: 1)
                    .onEnded { _ in }
            )
            .overlay(
                ThreeFingerCatcher(action: action)
                    .allowsHitTesting(true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .opacity(0.001)  // invisible but receives touches
            )
    }
}

private struct ThreeFingerCatcher: UIViewRepresentable {
    let action: () -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let tap = UITapGestureRecognizer(target: context.coordinator,
                                         action: #selector(Coordinator.tapped))
        tap.numberOfTouchesRequired = 3
        tap.numberOfTapsRequired = 1
        view.addGestureRecognizer(tap)
        view.isUserInteractionEnabled = true
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    final class Coordinator: NSObject {
        let action: () -> Void
        init(action: @escaping () -> Void) { self.action = action }
        @objc func tapped() { action() }
    }
}

extension View {
    func onThreeFingerPanic(_ action: @escaping () -> Void) -> some View {
        modifier(ThreeFingerPanicModifier(action: action))
    }
}
