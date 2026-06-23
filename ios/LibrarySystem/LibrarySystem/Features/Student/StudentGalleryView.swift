import SwiftUI

struct StudentGalleryView: View {
    @ObservedObject var vm: StudentViewModel
    @State private var selected: GalleryPhoto?

    private let baseURL = "https://targetzone.co.in"
    private let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                Group {
                    if vm.galleryPhotos.isEmpty {
                        emptyState
                    } else {
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 3) {
                                ForEach(vm.galleryPhotos) { photo in
                                    photoTile(photo)
                                }
                            }
                        }
                    }
                }
                .sheet(item: $selected) { photo in
                    photoDetail(photo)
                }
            }
            .navigationTitle("Gallery")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { vm.loadGallery() }
    }

    private func photoTile(_ photo: GalleryPhoto) -> some View {
        Button { selected = photo } label: {
            if let url = URL(string: baseURL + photo.url) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFill()
                            .frame(minWidth: 0, maxWidth: .infinity)
                            .aspectRatio(1, contentMode: .fill)
                            .clipped()
                    case .failure:
                        placeholderTile
                    default:
                        placeholderTile
                            .overlay(ProgressView().tint(.amber))
                    }
                }
            } else {
                placeholderTile
            }
        }
    }

    private var placeholderTile: some View {
        Color.navyMid
            .aspectRatio(1, contentMode: .fill)
            .overlay(Image(systemName: "photo").foregroundColor(.textMuted))
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 44)).foregroundColor(.textMuted)
            Text("No Photos Yet").font(.headlineSmall).foregroundColor(.textPrimary)
            Text("Gallery photos will appear here").font(.bodySmall).foregroundColor(.textSub)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func photoDetail(_ photo: GalleryPhoto) -> some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 0) {
                if let url = URL(string: baseURL + photo.url) {
                    AsyncImage(url: url) { phase in
                        if case .success(let img) = phase {
                            img.resizable().scaledToFit()
                        } else {
                            Color.navyMid
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                if let caption = photo.caption, !caption.isEmpty {
                    Text(caption)
                        .font(.bodyMedium).foregroundColor(.white)
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.black.opacity(0.7))
                }
            }
        }
    }
}
