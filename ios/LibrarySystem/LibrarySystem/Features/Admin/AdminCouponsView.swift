import SwiftUI

struct AdminCouponsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var code = ""
    @State private var discountPercent = ""
    @State private var deleteTarget: Coupon?

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        globalToggleCard
                        createCard

                        if let err = vm.error { ErrorBanner(message: err) }
                        if let msg = vm.successMsg {
                            HStack { Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                                Text(msg).foregroundColor(.textPrimary) }
                        }

                        couponList
                    }
                    .padding(16)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .dismissKeyboardOnTap()
            .navigationTitle("Coupons")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadCoupons(); vm.loadAppSettings() }
        .alert("Delete coupon \(deleteTarget?.code ?? "")?", isPresented: Binding(
            get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }
        )) {
            Button("Cancel", role: .cancel) { deleteTarget = nil }
            Button("Delete", role: .destructive) {
                if let target = deleteTarget { vm.deleteCoupon(target) }
                deleteTarget = nil
            }
        } message: {
            Text("This cannot be undone.")
        }
    }

    private var globalToggleCard: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 8) {
                Toggle(isOn: Binding(
                    get: { vm.appSettings.couponsEnabled ?? true },
                    set: { vm.setCouponsEnabled($0) }
                )) {
                    Text("Enable Discount Coupons").font(.labelMedium).foregroundColor(.textPrimary)
                }
                .tint(.amber)
                Text("Turning this off immediately hides all coupons from students and disables discounts, without deleting any coupons.")
                    .font(.caption).foregroundColor(.textMuted)
            }
        }
    }

    private var createCard: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Create Coupon").font(.labelMedium).foregroundColor(.textSub)
                AppTextField(label: "Coupon Code (optional)", text: $code,
                             placeholder: "Leave blank to auto-generate")
                    .onChange(of: code) { code = $0.uppercased() }
                AppTextField(label: "Discount %", text: $discountPercent,
                             placeholder: "e.g. 10", keyboardType: .numberPad)
                PrimaryButton("Create Coupon", isLoading: vm.isLoading) {
                    guard let pct = Int(discountPercent), (1...100).contains(pct) else { return }
                    vm.createCoupon(code: code.isEmpty ? nil : code, discountPercent: pct)
                    code = ""; discountPercent = ""
                }
            }
        }
    }

    private var couponList: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("All Coupons").font(.labelMedium).foregroundColor(.textSub)
            if vm.coupons.isEmpty {
                Text("No coupons created yet.").font(.bodySmall).foregroundColor(.textMuted)
            } else {
                ForEach(vm.coupons, id: \.code) { coupon in
                    AppCard {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(coupon.code).font(.headlineSmall).foregroundColor(.amber)
                                Text("\(coupon.discountPercent)% off").font(.labelSmall).foregroundColor(.textSub)
                            }
                            Spacer()
                            Toggle("", isOn: Binding(
                                get: { coupon.isActive },
                                set: { vm.setCouponActive(coupon, active: $0) }
                            ))
                            .labelsHidden()
                            .tint(.amber)
                            Button { deleteTarget = coupon } label: {
                                Image(systemName: "trash").foregroundColor(.redAlert)
                            }
                        }
                    }
                }
            }
        }
    }
}
