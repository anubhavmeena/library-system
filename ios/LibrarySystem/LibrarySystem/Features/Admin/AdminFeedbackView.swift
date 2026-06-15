import SwiftUI

struct AdminFeedbackView: View {
    @ObservedObject var vm: AdminViewModel

    @State private var selectedItem: FeedbackItem?
    @State private var respondStatus = "IN_PROGRESS"
    @State private var respondNotes  = ""

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if vm.feedback.isEmpty && !vm.isLoading {
                            Text("No feedback yet").foregroundColor(.textMuted)
                                .frame(maxWidth: .infinity).padding(.top, 60)
                        }
                        ForEach(vm.feedback) { item in
                            feedbackCard(item)
                        }
                    }
                    .padding(16)
                }
                .sheet(item: $selectedItem) { item in
                    respondSheet(item)
                }
            }
            .navigationTitle("Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadFeedback() }
    }

    private func feedbackCard(_ item: FeedbackItem) -> some View {
        AppCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.type.capitalized).font(.labelSmall).foregroundColor(.textMuted)
                        Text(item.subject).font(.labelLarge).foregroundColor(.textPrimary)
                    }
                    Spacer()
                    StatusChip(status: item.status)
                }
                if let name = item.studentName {
                    Text("From: \(name) \(item.studentMobile ?? "")").font(.bodySmall).foregroundColor(.textSub)
                }
                Text(item.description).font(.bodySmall).foregroundColor(.textSub).lineLimit(3)
                if let notes = item.adminNotes, !notes.isEmpty {
                    HStack(alignment: .top, spacing: 6) {
                        Image(systemName: "bubble.left.fill").font(.caption).foregroundColor(.blueSoft)
                        Text(notes).font(.bodySmall).foregroundColor(.textSub)
                    }
                    .padding(8).background(Color.blueFaint).clipShape(RoundedRectangle(cornerRadius: 8))
                }
                HStack {
                    Text(item.createdAt.prefix(10).description).font(.caption).foregroundColor(.textMuted)
                    Spacer()
                    Button("Respond") {
                        respondStatus = item.status
                        respondNotes  = item.adminNotes ?? ""
                        selectedItem  = item
                    }
                    .font(.labelSmall).foregroundColor(.amber)
                }
            }
        }
    }

    private func respondSheet(_ item: FeedbackItem) -> some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 16) {
                    AppCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(item.subject).font(.headlineSmall).foregroundColor(.textPrimary)
                            Text(item.description).font(.bodySmall).foregroundColor(.textSub)
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Update Status").font(.labelMedium).foregroundColor(.textSub)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(["IN_PROGRESS","RESOLVED","CLOSED"], id: \.self) { s in
                                    let selected = respondStatus == s
                                    Button { respondStatus = s } label: {
                                        Text(s.replacingOccurrences(of: "_", with: " "))
                                            .font(.labelSmall)
                                            .foregroundColor(selected ? .navyDeep : .textSub)
                                            .padding(.horizontal, 12).padding(.vertical, 6)
                                            .background(selected ? Color.amber : Color.cardBg)
                                            .clipShape(Capsule())
                                            .overlay(Capsule().stroke(selected ? Color.amber : Color.cardBorder))
                                    }
                                }
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Admin Notes").font(.labelMedium).foregroundColor(.textSub)
                        TextEditor(text: $respondNotes)
                            .font(.bodyMedium).foregroundColor(.textPrimary)
                            .scrollContentBackground(.hidden).background(Color.cardBg)
                            .frame(minHeight: 100).padding(8)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    PrimaryButton("Save", isLoading: vm.isLoading) {
                        vm.updateFeedback(id: item.id, status: respondStatus, adminNotes: respondNotes)
                        selectedItem = nil
                    }
                }
                .padding(16)
            }
            .navigationTitle("Respond")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { selectedItem = nil }.foregroundColor(.amber)
                }
            }
        }
    }
}
