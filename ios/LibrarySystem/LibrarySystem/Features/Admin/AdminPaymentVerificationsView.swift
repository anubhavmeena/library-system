import SwiftUI

// Mirrors android/.../AdminPaymentVerificationsScreen.kt — list + review
// pattern borrowed from AdminFeedbackView, screenshot tap-to-preview
// borrowed from AdminGalleryView's photoDetailSheet.
struct AdminPaymentVerificationsView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var previewClaim: PaymentClaim?
    @State private var confirmClaim: PaymentClaim?
    @State private var confirmStatus = "VERIFIED"

    private let baseURL = "https://targetzone.co.in"

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if vm.paymentClaims.isEmpty && !vm.isLoading {
                            Text("Nothing to review").foregroundColor(.textMuted)
                                .frame(maxWidth: .infinity).padding(.top, 60)
                        }
                        ForEach(vm.paymentClaims) { claim in
                            claimCard(claim)
                        }
                    }
                    .padding(16)
                }
                .sheet(item: $previewClaim) { claim in
                    previewSheet(claim)
                }
            }
            .navigationTitle("Verify Payments")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadPaymentClaims() }
        .alert(confirmStatus == "VERIFIED" ? "Verify Payment" : "Reject Claim",
               isPresented: Binding(get: { confirmClaim != nil }, set: { if !$0 { confirmClaim = nil } })) {
            Button("Cancel", role: .cancel) { confirmClaim = nil }
            Button(confirmStatus == "VERIFIED" ? "Verify" : "Reject", role: confirmStatus == "VERIFIED" ? .none : .destructive) {
                if let claim = confirmClaim {
                    vm.reviewPaymentClaim(id: claim.id, status: confirmStatus)
                }
                confirmClaim = nil
            }
        } message: {
            if let claim = confirmClaim {
                Text(confirmStatus == "VERIFIED"
                     ? "This will mark the claim verified and automatically clear \(claim.studentName)'s \(claim.claimType == "DUES" ? "grace dues" : "pending fee")."
                     : "This will mark the claim rejected. No amount will be cleared.")
            }
        }
    }

    private func claimCard(_ claim: PaymentClaim) -> some View {
        AppCard {
            HStack(alignment: .top, spacing: 12) {
                Button { previewClaim = claim } label: {
                    screenshotThumb(claim)
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 4) {
                    Text(claim.studentName).font(.labelLarge).foregroundColor(.textPrimary)
                    if let mobile = claim.studentMobile {
                        Text(mobile).font(.bodySmall).foregroundColor(.textSub)
                    }
                    claimTypeBadge(claim.claimType)
                    Text("₹\(String(format: "%.2f", claim.amountClaimed))")
                        .font(.headlineSmall).foregroundColor(.textPrimary)
                    Text(claim.createdAt.prefix(16).description).font(.caption).foregroundColor(.textMuted)
                }
                Spacer()
            }

            if claim.status == "PENDING" {
                HStack(spacing: 10) {
                    Button {
                        confirmStatus = "REJECTED"
                        confirmClaim = claim
                    } label: {
                        Text("Reject")
                            .font(.labelMedium).foregroundColor(.redAlert)
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                            .background(Color.redFaint)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.redAlert.opacity(0.4)))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)

                    Button {
                        confirmStatus = "VERIFIED"
                        confirmClaim = claim
                    } label: {
                        Text("Verify")
                            .font(.labelMedium).foregroundColor(.emerald)
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                            .background(Color.emeraldFaint)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.emerald.opacity(0.4)))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 10)
            }
        }
    }

    private func screenshotThumb(_ claim: PaymentClaim) -> some View {
        Group {
            if let url = URL(string: baseURL + claim.screenshotUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img): img.resizable().scaledToFill()
                    default: Color.navyLight
                    }
                }
            } else {
                Color.navyLight
            }
        }
        .frame(width: 64, height: 64)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
    }

    private func claimTypeBadge(_ claimType: String) -> some View {
        Text(claimType == "DUES" ? "Grace Dues" : "Pending Fee")
            .font(.labelSmall).foregroundColor(.blueSoft)
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(Color.blueFaint).clipShape(Capsule())
    }

    private func previewSheet(_ claim: PaymentClaim) -> some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                if let url = URL(string: baseURL + claim.screenshotUrl) {
                    AsyncImage(url: url) { phase in
                        if case .success(let img) = phase {
                            img.resizable().scaledToFit()
                        } else {
                            Color.navyMid
                        }
                    }
                    .padding(16)
                }
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { previewClaim = nil }.foregroundColor(.white)
                }
            }
        }
    }
}
