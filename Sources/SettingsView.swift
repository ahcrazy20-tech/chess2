import SwiftUI

struct SettingsView: View {
    @ObservedObject var stealth: StealthManager
    @AppStorage("engineDepth")       private var engineDepth: Int = 20
    @AppStorage("engineMovetime")    private var engineMovetime: Int = 2000
    @AppStorage("multiPV")           private var multiPV: Int = 3
    @AppStorage("autoHideBackground") private var autoHideBackground: Bool = true
    @AppStorage("autoHideRecording")  private var autoHideRecording: Bool = true
    @AppStorage("autoHideScreenshot") private var autoHideScreenshot: Bool = true
    @AppStorage("useMinimalMode")     private var useMinimalMode: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Engine — Stockfish 16 (local)") {
                    Stepper(value: $engineDepth, in: 10...25) {
                        LabeledContent("Search depth", value: "\(engineDepth)")
                    }
                    Stepper(value: $engineMovetime, in: 500...5000, step: 250) {
                        LabeledContent("Max think time", value: "\(engineMovetime) ms")
                    }
                    Stepper(value: $multiPV, in: 1...5) {
                        LabeledContent("Candidate moves", value: "\(multiPV)")
                    }
                    Text("Higher depth = stronger but slower. Depth 20 ≈ ~3400 Elo.")
                        .font(.caption).foregroundColor(.secondary)
                }

                Section("Stealth — Panic Gestures") {
                    HStack {
                        Image(systemName: "iphone.gen3.radiowaves.left.and.right")
                        Text("Shake device to hide overlay")
                    }
                    HStack {
                        Image(systemName: "hand.tap.fill")
                        Text("3-finger tap to hide overlay")
                    }
                    Text("Both gestures are always active.")
                        .font(.caption).foregroundColor(.secondary)
                }

                Section("Stealth — Auto-hide") {
                    Toggle("When app goes to background", isOn: $autoHideBackground)
                    Toggle("When screen is being recorded/mirrored", isOn: $autoHideRecording)
                    Toggle("When screenshot is taken", isOn: $autoHideScreenshot)
                    Toggle("Use minimal mode (tiny dot)", isOn: $useMinimalMode)
                        .onChange(of: useMinimalMode) { _, new in
                            stealth.minimalMode = new
                        }
                }

                Section("Status") {
                    LabeledContent("Overlay visible", value: stealth.overlayVisible ? "Yes" : "No")
                    LabeledContent("Screen captured", value: stealth.isBeingRecorded ? "Yes" : "No")
                    if !stealth.lastHideReason.isEmpty {
                        LabeledContent("Last hide reason", value: stealth.lastHideReason)
                    }
                    Button("Force show overlay") { stealth.show() }
                    Button("Force hide overlay", role: .destructive) { stealth.hide(reason: "manual") }
                }

                Section("Privacy") {
                    HStack {
                        Image(systemName: "lock.shield.fill").foregroundColor(.green)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Zero external requests").font(.callout.bold())
                            Text("Analysis runs 100% on-device. Nothing sent to any server.")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: "2.0.0 (20)")
                    LabeledContent("Engine", value: "Stockfish 16")
                    LabeledContent("Build type", value: "TrollStore (unsigned)")
                }
            }
            .navigationTitle("Settings")
        }
    }
}
