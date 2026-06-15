import SwiftUI

struct FeedbackView: View {
    @ObservedObject var vm: StudentViewModel

    @State private var type        = "FEEDBACK"
    @State private var subject     = ""
    @State private var description = ""
    @State private var showForm    = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {
                        if showForm { submitForm }
                        historySection
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(showForm ? "Cancel" : "New") { showForm.toggle() }
                        .foregroundColor(.amber)
                }
            }
        }
        .onAppear { vm.loadFeedback() }
    }

    private var submitForm: some View {
        AppCard(accentColor: .amber) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Submit Feedback").font(.headlineSmall).foregroundColor(.textPrimary)

                // Type toggle
                HStack(spacing: 10) {
                    ForEach(["FEEDBACK", "COMPLAINT"], id: \.self) { t in
                        Button {
                            type = t
                        } label: {
                            Text(t.capitalized)
                                .font(.labelMedium)
                                .foregroundColor(type == t ? .navyDeep : .textSub)
                                .padding(.horizontal, 16).padding(.vertical, 8)
                                .background(type == t ? Color.amber : Color.cardBg)
                                .clipShape(Capsule())
                                .overlay(Capsule().stroke(type == t ? Color.amber : Color.cardBorder))
                        }
                    }
                }

                AppTextField(label: "Subject", text: $subject, placeholder: "Brief title")

                VStack(alignment: .leading, spacing: 4) {
                    Text("Description").font(.labelMedium).foregroundColor(.textSub)
                    TextEditor(text: $description)
                        .font(.bodyMedium)
                        .foregroundColor(.textPrimary)
                        .scrollContentBackground(.hidden)
                        .background(Color.cardBg)
                        .frame(minHeight: 100)
                        .padding(8)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }

                if let err = vm.error { ErrorBanner(message: err) }

                PrimaryButton("Submit", isLoading: vm.isLoading) {
                    guard !subject.isEmpty, !description.isEmpty else { return }
                    vm.submitFeedback(type: type, subject: subject, description: description)
                    subject = ""; description = ""; showForm = false
                }
            }
        }
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your Submissions").font(.headlineSmall).foregroundColor(.textPrimary)

            if vm.myFeedback.isEmpty {
                Text("No submissions yet").font(.bodySmall).foregroundColor(.textMuted)
            } else {
                ForEach(vm.myFeedback) { item in
                    AppCard {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(item.type.capitalized)
                                    .font(.labelSmall)
                                    .foregroundColor(.textMuted)
                                Spacer()
                                StatusChip(status: item.status)
                            }
                            Text(item.subject).font(.labelLarge).foregroundColor(.textPrimary)
                            Text(item.description).font(.bodySmall).foregroundColor(.textSub)
                                .lineLimit(3)
                            if let notes = item.adminNotes, !notes.isEmpty {
                                HStack(alignment: .top, spacing: 6) {
                                    Image(systemName: "bubble.left.fill").font(.caption).foregroundColor(.blueSoft)
                                    Text(notes).font(.bodySmall).foregroundColor(.textSub)
                                }
                                .padding(8)
                                .background(Color.blueFaint)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            Text(item.createdAt).font(.caption).foregroundColor(.textMuted)
                        }
                    }
                }
            }
        }
    }
}
