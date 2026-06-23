import SwiftUI

struct AdminInboxView: View {
    @ObservedObject var vm: AdminViewModel
    @State private var selectedSummary: InboxSummary?

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                Group {
                    if vm.isLoading && vm.inboxMessages.isEmpty {
                        LoadingView()
                    } else if vm.inboxMessages.isEmpty {
                        emptyInbox
                    } else {
                        messageList
                    }
                }
                .navigationDestination(item: $selectedSummary) { summary in
                    InboxMessageDetailView(vm: vm, summary: summary)
                }
            }
            .navigationTitle("Inbox")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { vm.loadInbox() } label: {
                        Image(systemName: "arrow.clockwise").foregroundColor(.amber)
                    }
                }
            }
        }
        .onAppear { vm.loadInbox() }
    }

    private var emptyInbox: some View {
        VStack(spacing: 12) {
            Image(systemName: "tray").font(.system(size: 40)).foregroundColor(.textMuted)
            Text("Inbox is empty").font(.headlineSmall).foregroundColor(.textPrimary)
            Text("No messages found").font(.bodySmall).foregroundColor(.textSub)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var messageList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                ForEach(vm.inboxMessages) { summary in
                    Button {
                        selectedSummary = summary
                        vm.loadInboxMessage(summary.messageNumber)
                    } label: {
                        AppCard {
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(summary.isRead ? Color.cardBg : Color.amber)
                                    .frame(width: 8, height: 8)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(summary.subject)
                                        .font(summary.isRead ? .bodyMedium : .labelLarge)
                                        .foregroundColor(.textPrimary)
                                        .lineLimit(1)
                                    Text(summary.from)
                                        .font(.bodySmall).foregroundColor(.textSub)
                                        .lineLimit(1)
                                    Text(summary.date.prefix(16).description)
                                        .font(.labelSmall).foregroundColor(.textMuted)
                                }

                                Spacer()
                                Image(systemName: "chevron.right").foregroundColor(.textMuted)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }
}

struct InboxMessageDetailView: View {
    @ObservedObject var vm: AdminViewModel
    let summary: InboxSummary

    @State private var replyBody    = ""
    @State private var showReply    = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.navyDeep.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let msg = vm.selectedMessage, msg.messageNumber == summary.messageNumber {
                        messageContent(msg)
                    } else {
                        LoadingView().frame(height: 200)
                    }
                }
                .padding(16)
            }
        }
        .navigationTitle("Message")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.navyMid, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button(role: .destructive) {
                        vm.deleteInboxMessage(summary.messageNumber)
                        dismiss()
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").foregroundColor(.amber)
                }
            }
        }
    }

    private func messageContent(_ msg: InboxMessage) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            AppCard(accentColor: .blueSoft) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(msg.subject).font(.headlineSmall).foregroundColor(.textPrimary)
                    InfoRow(label: "From", value: msg.from)
                    InfoRow(label: "Date", value: msg.date.prefix(20).description)
                }
            }

            AppCard {
                Text(msg.body)
                    .font(.bodyMedium).foregroundColor(.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            replySection(msgNumber: msg.messageNumber)

            if let err = vm.error { ErrorBanner(message: err) }
            if let success = vm.successMsg {
                HStack { Image(systemName: "checkmark.circle.fill").foregroundColor(.emerald)
                    Text(success).foregroundColor(.textPrimary) }
            }
        }
    }

    private func replySection(msgNumber: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Reply").font(.labelMedium).foregroundColor(.textSub)
                Spacer()
                Button { withAnimation { showReply.toggle() } } label: {
                    Text(showReply ? "Cancel" : "Compose Reply")
                        .font(.labelSmall).foregroundColor(.amber)
                }
            }

            if showReply {
                TextEditor(text: $replyBody)
                    .font(.bodyMedium).foregroundColor(.textPrimary)
                    .scrollContentBackground(.hidden).background(Color.cardBg)
                    .frame(minHeight: 120).padding(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder))
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                PrimaryButton("Send Reply", isLoading: vm.isLoading) {
                    guard !replyBody.isEmpty else { return }
                    vm.replyToMessage(msgNumber, body: replyBody)
                    replyBody = ""
                    showReply = false
                }
            }
        }
    }
}
