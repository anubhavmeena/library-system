import SwiftUI

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
            }
            .navigationTitle("Expenses")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadExpenses(year: selectedYear, month: selectedMonth) }
        .onChange(of: vm.expense) { populateFields() }
    }

    private var periodPicker: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Period").font(.labelMedium).foregroundColor(.textSub)
                HStack(spacing: 12) {
                    Picker("Month", selection: $selectedMonth) {
                        ForEach(1...12, id: \.self) { m in
                            Text(months[m-1]).tag(m)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(.amber)

                    Picker("Year", selection: $selectedYear) {
                        ForEach((2024...2030).reversed(), id: \.self) { y in
                            Text(String(y)).tag(y)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(.amber)

                    Spacer()

                    Button("Load") {
                        vm.loadExpenses(year: selectedYear, month: selectedMonth)
                    }
                    .font(.labelMedium).foregroundColor(.amber)
                }
                .onChange(of: selectedMonth) { _ in vm.loadExpenses(year: selectedYear, month: selectedMonth) }
                .onChange(of: selectedYear)  { _ in vm.loadExpenses(year: selectedYear, month: selectedMonth) }
            }
        }
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
