import SwiftUI

struct AdminAppSettingsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var wifiName        = ""
    @State private var wifiPassword    = ""
    @State private var upiId           = ""
    @State private var graceDays       = ""
    @State private var convenienceFee  = ""
    @State private var waterTankerRate = ""
    @State private var couponsEnabled  = true
    @State private var loaded          = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        AppCard {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("WiFi").font(.labelMedium).foregroundColor(.textSub)
                                AppTextField(label: "WiFi Name", text: $wifiName,
                                            placeholder: "Network name", leadingIcon: "wifi")
                                AppTextField(label: "WiFi Password", text: $wifiPassword,
                                            placeholder: "Password", isSecure: true, leadingIcon: "lock")
                            }
                        }

                        AppCard {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Payments").font(.labelMedium).foregroundColor(.textSub)
                                AppTextField(label: "UPI ID (VPA)", text: $upiId,
                                            placeholder: "yourname@upi", leadingIcon: "indianrupeesign.circle")
                                Text("Used to generate UPI QR codes and payment-request links on the Create Membership screen.")
                                    .font(.caption).foregroundColor(.textMuted)
                            }
                        }

                        AppCard {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Grace Period").font(.labelMedium).foregroundColor(.textSub)
                                AppTextField(label: "Grace Days", text: $graceDays,
                                            placeholder: "e.g. 10", keyboardType: .numberPad,
                                            leadingIcon: "hourglass")
                            }
                        }

                        AppCard {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Convenience Fee").font(.labelMedium).foregroundColor(.textSub)
                                AppTextField(label: "Amount (₹)", text: $convenienceFee,
                                            placeholder: "0", keyboardType: .decimalPad,
                                            leadingIcon: "indianrupeesign")
                            }
                        }

                        AppCard {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Water Tanker Rate").font(.labelMedium).foregroundColor(.textSub)
                                AppTextField(label: "Rate (₹)", text: $waterTankerRate,
                                            placeholder: "0", keyboardType: .decimalPad,
                                            leadingIcon: "indianrupeesign")
                            }
                        }

                        AppCard {
                            VStack(alignment: .leading, spacing: 12) {
                                Toggle(isOn: $couponsEnabled) {
                                    Text("Enable Discount Coupons").font(.labelMedium).foregroundColor(.textPrimary)
                                }
                                .tint(.amber)
                                Text("Turning this off immediately hides all coupons from students and disables discounts, without deleting any coupons. Manage coupon codes from Admin → Coupons.")
                                    .font(.caption).foregroundColor(.textMuted)
                            }
                        }

                        if let err = vm.error { ErrorBanner(message: err) }

                        PrimaryButton("Save Settings", isLoading: vm.isLoading) {
                            vm.saveAppSettings(
                                wifiName: wifiName.isEmpty ? nil : wifiName,
                                wifiPassword: wifiPassword.isEmpty ? nil : wifiPassword,
                                upiId: upiId.isEmpty ? nil : upiId,
                                graceDays: Int(graceDays) ?? 0,
                                convenienceFee: Double(convenienceFee) ?? 0,
                                waterTankerRate: Double(waterTankerRate) ?? 0,
                                couponsEnabled: couponsEnabled)
                        }

                        VersionFooter()
                    }
                    .padding(16)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .dismissKeyboardOnTap()
            .navigationTitle("App Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadAppSettings() }
        .onChange(of: vm.appSettingsLoaded) { isLoaded in
            guard isLoaded, !loaded else { return }
            loaded = true
            wifiName = vm.appSettings.wifiName ?? ""
            wifiPassword = vm.appSettings.wifiPassword ?? ""
            upiId = vm.appSettings.upiId ?? ""
            graceDays = vm.appSettings.graceDays.map { "\($0)" } ?? "10"
            convenienceFee = vm.appSettings.convenienceFee.map { String(format: "%.0f", $0) } ?? "0"
            waterTankerRate = vm.appSettings.waterTankerRate.map { String(format: "%.0f", $0) } ?? "0"
            couponsEnabled = vm.appSettings.couponsEnabled ?? true
        }
    }
}
