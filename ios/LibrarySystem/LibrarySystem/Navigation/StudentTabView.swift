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

            FacilitiesView()
                .tabItem { Label("More",       systemImage: "info.circle.fill") }
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
}
