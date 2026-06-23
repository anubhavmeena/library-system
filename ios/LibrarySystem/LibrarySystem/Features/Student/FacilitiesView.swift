import SwiftUI

struct FacilitiesView: View {
    private let amenities: [(icon: String, label: String)] = [
        ("wifi",                          "High-Speed WiFi"),
        ("lightbulb.fill",               "24/7 Lighting"),
        ("snowflake",                     "Air Conditioning"),
        ("drop.fill",                     "Drinking Water"),
        ("power",                         "Power Outlets at Every Seat"),
        ("person.3.fill",                 "Reading Rooms"),
        ("lock.shield.fill",             "Secure Locker Storage"),
        ("camera.fill",                  "CCTV Surveillance"),
        ("printer.fill",                 "Printing Facility"),
        ("books.vertical.fill",          "Reference Library"),
        ("figure.walk",                  "Spacious Study Area"),
        ("moon.fill",                     "Quiet Study Hours"),
    ]

    private let shifts: [(name: String, time: String, icon: String)] = [
        ("Morning",  "6:00 AM – 2:00 PM",  "sunrise.fill"),
        ("Evening",  "2:00 PM – 10:00 PM", "sunset.fill"),
        ("Full Day", "6:00 AM – 10:00 PM", "sun.max.fill"),
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 20) {
                        shiftsSection
                        amenitiesSection
                        rulesSection
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Facilities")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private var shiftsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Library Hours").font(.headlineSmall).foregroundColor(.textPrimary)
            ForEach(shifts, id: \.name) { shift in
                AppCard {
                    HStack(spacing: 14) {
                        Image(systemName: shift.icon).font(.system(size: 28)).foregroundColor(.amber)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(shift.name).font(.headlineSmall).foregroundColor(.textPrimary)
                            Text(shift.time).font(.bodySmall).foregroundColor(.textSub)
                        }
                    }
                }
            }
        }
    }

    private var amenitiesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Amenities").font(.headlineSmall).foregroundColor(.textPrimary)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(amenities, id: \.label) { amenity in
                    AppCard {
                        HStack(spacing: 10) {
                            Image(systemName: amenity.icon).foregroundColor(.amber).frame(width: 20)
                            Text(amenity.label).font(.bodySmall).foregroundColor(.textPrimary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
        }
    }

    private var rulesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Rules & Guidelines").font(.headlineSmall).foregroundColor(.textPrimary)
            AppCard {
                VStack(alignment: .leading, spacing: 8) {
                    ruleItem("Maintain complete silence in the study hall at all times")
                    ruleItem("Mobile phones must be kept on silent mode")
                    ruleItem("Food and beverages are not allowed at study seats")
                    ruleItem("Keep your seat and surroundings clean")
                    ruleItem("No marking or writing in library books")
                    ruleItem("Report any technical issues to the front desk")
                    ruleItem("Membership card must be shown at entry")
                    ruleItem("Guests are not permitted inside the study hall")
                }
            }
            Text("⚠\u{FE0F} Violation of rules may result in membership cancellation without refund.")
                .font(.bodySmall).foregroundColor(.redAlert).padding(.top, 4)
        }
    }

    private func ruleItem(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald).font(.bodySmall)
            Text(text).font(.bodySmall).foregroundColor(.textSub)
        }
    }
}
