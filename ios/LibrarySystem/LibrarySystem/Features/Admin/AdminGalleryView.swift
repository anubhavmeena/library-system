import SwiftUI
import PhotosUI

struct AdminGalleryView: View {
    @State private var photos:       [GalleryPhoto] = []
    @State private var isLoading     = false
    @State private var error:        String?
    @State private var selected:     GalleryPhoto?
    @State private var showUpload    = false
    @State private var photoItem:    PhotosPickerItem?
    @State private var captionText   = ""
    @State private var uploading     = false

    private let baseURL = "https://targetzone.co.in"
    private let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()
                VStack(spacing: 0) {
                    if showUpload { uploadPanel }
                    photoGrid
                }
                .sheet(item: $selected) { photo in
                    photoDetailSheet(photo)
                }
            }
            .navigationTitle("Gallery")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { withAnimation { showUpload.toggle() } } label: {
                        Image(systemName: showUpload ? "xmark" : "plus")
                            .foregroundColor(.amber)
                    }
                }
            }
        }
        .onAppear { loadPhotos() }
    }

    private var uploadPanel: some View {
        VStack(spacing: 12) {
            PhotosPicker(selection: $photoItem, matching: .images) {
                HStack {
                    Image(systemName: "photo.badge.plus").foregroundColor(.amber)
                    Text(photoItem == nil ? "Select Photo" : "Photo selected")
                        .font(.labelMedium).foregroundColor(.textPrimary)
                }
                .frame(maxWidth: .infinity).padding(12)
                .background(Color.amberFaint)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.amber.opacity(0.5)))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            AppTextField(label: "Caption (optional)", text: $captionText,
                         placeholder: "Add a caption...", leadingIcon: "text.bubble")

            if let err = error { ErrorBanner(message: err) }

            PrimaryButton("Upload Photo", isLoading: uploading) { uploadPhoto() }
        }
        .padding(16)
        .background(Color.navyMid.opacity(0.3))
    }

    private var photoGrid: some View {
        Group {
            if isLoading && photos.isEmpty {
                LoadingView()
            } else if photos.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 44)).foregroundColor(.textMuted)
                    Text("No photos yet").font(.headlineSmall).foregroundColor(.textPrimary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 3) {
                        ForEach(photos) { photo in
                            Button { selected = photo } label: {
                                photoTile(photo)
                            }
                        }
                    }
                }
            }
        }
    }

    private func photoTile(_ photo: GalleryPhoto) -> some View {
        Group {
            if let url = URL(string: baseURL + photo.url) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFill()
                    default:
                        Color.navyMid.overlay(ProgressView().tint(.amber))
                    }
                }
            } else {
                Color.navyMid.overlay(Image(systemName: "photo").foregroundColor(.textMuted))
            }
        }
        .frame(minWidth: 0, maxWidth: .infinity)
        .aspectRatio(1, contentMode: .fill)
        .clipped()
    }

    private func photoDetailSheet(_ photo: GalleryPhoto) -> some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                VStack(spacing: 0) {
                    if let url = URL(string: baseURL + photo.url) {
                        AsyncImage(url: url) { phase in
                            if case .success(let img) = phase {
                                img.resizable().scaledToFit()
                            } else { Color.navyMid }
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
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { selected = nil }
                        .foregroundColor(.white)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(role: .destructive) {
                        deletePhoto(photo)
                        selected = nil
                    } label: {
                        Image(systemName: "trash").foregroundColor(.redAlert)
                    }
                }
            }
        }
    }

    private func loadPhotos() {
        isLoading = true
        Task {
            do { photos = try await GalleryRepository.shared.getAll() }
            catch { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    private func uploadPhoto() {
        guard let item = photoItem else { return }
        uploading = true; error = nil
        Task {
            do {
                guard let data = try await item.loadTransferable(type: Data.self) else {
                    error = "Could not load image data"; uploading = false; return
                }
                let photo = try await GalleryRepository.shared.upload(
                    imageData: data, caption: captionText.isEmpty ? nil : captionText)
                photos.insert(photo, at: 0)
                captionText = ""; photoItem = nil
                withAnimation { showUpload = false }
            } catch {
                self.error = error.localizedDescription
            }
            uploading = false
        }
    }

    private func deletePhoto(_ photo: GalleryPhoto) {
        Task {
            do {
                try await GalleryRepository.shared.delete(id: photo.id)
                photos.removeAll { $0.id == photo.id }
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
