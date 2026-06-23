import SwiftUI

struct StudentTabView: View {
    @StateObject private var vm = StudentViewModel()
    @State private var showPaymentSuccess = false

    var body: some View {
        TabView {
            DashboardView(vm: vm)
                .tabItem { Label("Home",       systemImage: "house.fill") }

            MembershipView(vm: vm)
                .tabItem { Label("Membership", systemImage: "creditcard.fill") }

            BookingView(vm: vm)
                .tabItem { Label("Book",       systemImage: "chair.lounge.fill") }

            ProfileView(vm: vm)
                .tabItem { Label("Profile",    systemImage: "person.fill") }

            studentMoreTab
                .tabItem { Label("More",       systemImage: "ellipsis.circle.fill") }
        }
        .tint(.amber)
        .toolbarBackground(Color.navyMid, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showPaymentSuccess) {
            PaymentSuccessView(vm: vm)
        }
        .onChange(of: vm.successMsg) { msg in
            if msg == "Payment successful!" {
                showPaymentSuccess = true
                vm.clearSuccess()
            }
        }
    }

    private var studentMoreTab: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 12) {
                        NavigationLink { StudentGalleryView(vm: vm) } label: {
                            moreRow(icon: "photo.fill.on.rectangle.fill", label: "Photo Gallery")
                        }
                        NavigationLink { FeedbackView(vm: vm) } label: {
                            moreRow(icon: "text.bubble.fill", label: "Feedback")
                        }
                        NavigationLink { FacilitiesView() } label: {
                            moreRow(icon: "building.columns.fill", label: "Facilities & Rules")
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("More")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private func moreRow(icon: String, label: String) -> some View {
        AppCard {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.system(size: 22)).foregroundColor(.amber).frame(width: 30)
                Text(label).font(.bodyLarge).foregroundColor(.textPrimary)
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(.textMuted)
            }
        }
    }
}
