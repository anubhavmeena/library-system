import SwiftUI
import UIKit

struct AdminExpensesView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var selectedYear  = Calendar.current.component(.year,  from: Date())
    @State private var selectedMonth = Calendar.current.component(.month, from: Date())

    @State private var waterTankerQty    = ""
    @State private var waterTankerPrice  = ""
    @State private var electricityBill   = ""
    @State private var internetBill      = ""
    @State private var miscItems: [(desc: String, amt: String)] = []

    private let months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        periodPicker
                        currentExpenseSummary
                        editSection

                        if let err = vm.error { ErrorBanner(message: err) }
                        if let msg = vm.successMsg {
                            HStack { Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                                Text(msg).foregroundColor(.textPrimary) }
                        }

                        PrimaryButton("Save Expenses", isLoading: vm.isLoading) {
                            save()
                        }
                    }
                    .padding(16)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .simultaneousGesture(TapGesture().onEnded { dismissKeyboard() })
            .navigationTitle("Expenses")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadExpenses(year: selectedYear, month: selectedMonth) }
        .onChange(of: vm.expense) { _ in populateFields() }
    }

    // Prev/next month arrows around a "Month YYYY" label, matching the web
    // app's calendar-header style rather than two separate wheel pickers.
    private var periodPicker: some View {
        AppCard {
            HStack {
                Button { changeMonth(by: -1) } label: {
                    Image(systemName: "chevron.left")
                        .font(.headlineSmall).foregroundColor(.amber)
                        .frame(width: 36, height: 36)
                        .background(Color.amberFaint)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)

                Spacer()

                Text("\(months[selectedMonth - 1]) \(String(selectedYear))")
                    .font(.headlineSmall).foregroundColor(.textPrimary)

                Spacer()

                Button { changeMonth(by: 1) } label: {
                    Image(systemName: "chevron.right")
                        .font(.headlineSmall).foregroundColor(.amber)
                        .frame(width: 36, height: 36)
                        .background(Color.amberFaint)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func changeMonth(by delta: Int) {
        var newMonth = selectedMonth + delta
        var newYear  = selectedYear
        if newMonth < 1  { newMonth = 12; newYear -= 1 }
        if newMonth > 12 { newMonth = 1;  newYear += 1 }
        guard (2024...2030).contains(newYear) else { return }
        selectedMonth = newMonth
        selectedYear  = newYear
        vm.loadExpenses(year: selectedYear, month: selectedMonth)
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    private var currentExpenseSummary: some View {
        let e = vm.expense
        guard e.totalExpense > 0 else { return AnyView(EmptyView()) }
        return AnyView(
            AppCard(accentColor: .blueSoft) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Total: ₹\(String(format: "%.0f", e.totalExpense))")
                        .font(.headlineMedium).foregroundColor(.textPrimary)
                    Divider().background(Color.dividerColor)
                    if e.waterTankerQty > 0 {
                        InfoRow(label: "Water (\(e.waterTankerQty) tankers)",
                                value: "₹\(String(format: "%.0f", e.waterTankerPrice))")
                    }
                    if e.electricityBill > 0 {
                        InfoRow(label: "Electricity", value: "₹\(String(format: "%.0f", e.electricityBill))")
                    }
                    if e.internetBill > 0 {
                        InfoRow(label: "Internet", value: "₹\(String(format: "%.0f", e.internetBill))")
                    }
                    if let items = e.miscItems, !items.isEmpty {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                            InfoRow(label: item.description, value: "₹\(String(format: "%.0f", item.amount))")
                        }
                    }
                }
            }
        )
    }

    private var editSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Edit Expenses").font(.headlineSmall).foregroundColor(.textPrimary)

            HStack(spacing: 12) {
                AppTextField(label: "Water Tankers (qty)", text: $waterTankerQty,
                             placeholder: "0", leadingIcon: "drop")
                AppTextField(label: "Total Water Cost (₹)", text: $waterTankerPrice,
                             placeholder: "0", leadingIcon: "indianrupeesign")
            }

            AppTextField(label: "Electricity Bill (₹)", text: $electricityBill,
                         placeholder: "0", leadingIcon: "bolt")

            AppTextField(label: "Internet Bill (₹)", text: $internetBill,
                         placeholder: "0", leadingIcon: "wifi")

            miscSection
        }
    }

    private var miscSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Miscellaneous Items").font(.labelMedium).foregroundColor(.textSub)
                Spacer()
                Button {
                    miscItems.append((desc: "", amt: ""))
                } label: {
                    Image(systemName: "plus.circle.fill").foregroundColor(.amber)
                }
            }

            ForEach(miscItems.indices, id: \.self) { i in
                HStack(spacing: 8) {
                    AppTextField(label: "", text: Binding(
                        get: { miscItems[i].desc },
                        set: { miscItems[i].desc = $0 }
                    ), placeholder: "Description", leadingIcon: "text.bubble")

                    AppTextField(label: "", text: Binding(
                        get: { miscItems[i].amt },
                        set: { miscItems[i].amt = $0 }
                    ), placeholder: "₹", leadingIcon: "indianrupeesign")
                    .frame(width: 100)

                    Button {
                        miscItems.remove(at: i)
                    } label: {
                        Image(systemName: "minus.circle.fill").foregroundColor(.redAlert)
                    }
                }
            }
        }
    }

    private func populateFields() {
        let e = vm.expense
        waterTankerQty   = e.waterTankerQty > 0   ? "\(e.waterTankerQty)"  : ""
        waterTankerPrice = e.waterTankerPrice > 0  ? String(format: "%.0f", e.waterTankerPrice) : ""
        electricityBill  = e.electricityBill > 0   ? String(format: "%.0f", e.electricityBill)  : ""
        internetBill     = e.internetBill > 0       ? String(format: "%.0f", e.internetBill)     : ""
        miscItems = (e.miscItems ?? []).map { (desc: $0.description, amt: String(format: "%.0f", $0.amount)) }
    }

    private func save() {
        let misc = miscItems
            .filter { !$0.desc.isEmpty && !$0.amt.isEmpty }
            .compactMap { item -> MiscItemRequest? in
                guard let amt = Double(item.amt) else { return nil }
                return MiscItemRequest(description: item.desc, amount: amt)
            }
        vm.saveExpenses(
            year: selectedYear, month: selectedMonth,
            waterTankerQty: Int(waterTankerQty) ?? 0,
            waterTankerPrice: Double(waterTankerPrice) ?? 0,
            electricityBill: Double(electricityBill) ?? 0,
            internetBill: Double(internetBill) ?? 0,
            miscItems: misc
        )
    }
}
