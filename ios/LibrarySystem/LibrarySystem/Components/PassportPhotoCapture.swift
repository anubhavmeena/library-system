import SwiftUI
import UIKit

// Captures a raw photo from the camera (no built-in editing — cropping is handled by
// PassportCropView below, since this app has no crop library and Apple's own editor only
// offers a square crop, not a passport-photo aspect ratio).
struct CameraCaptureView: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraCaptureView
        init(_ parent: CameraCaptureView) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController,
                                    didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.onCapture(image)
            } else {
                parent.onCancel()
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCancel()
        }
    }
}

let passportAspect: CGFloat = 3.5 / 4.5   // ISO passport-photo width:height
private let cropWindowWidth: CGFloat = 260

// A custom pinch-to-zoom / drag-to-pan cropper fixed to a caller-chosen aspect ratio
// (defaults to the ISO passport-photo ratio). No crop library exists in this app, and
// Apple's built-in UIImagePickerController editor only offers a square crop — this
// view fills that gap. Pass `aspect: 1` for a square crop (e.g. a circular avatar).
struct PassportCropView: View {
    let aspect: CGFloat
    let onDone: (UIImage) -> Void
    let onCancel: () -> Void
    private var cropWindowHeight: CGFloat { cropWindowWidth / aspect }

    init(image: UIImage, aspect: CGFloat = passportAspect,
         onDone: @escaping (UIImage) -> Void, onCancel: @escaping () -> Void) {
        self.aspect = aspect
        self.onDone = onDone
        self.onCancel = onCancel

        let normalized = image.normalizedOrientation()
        self.normalizedImage = normalized

        let size: CGSize
        if let cg = normalized.cgImage {
            size = CGSize(width: CGFloat(cg.width), height: CGFloat(cg.height))
        } else {
            size = normalized.size
        }
        self.imageSize = size
        self.baseScale = max(cropWindowWidth / size.width, cropWindowWidth / aspect / size.height)
    }

    // Computed once in init rather than as computed properties re-deriving from `image`
    // on every access — normalizedOrientation() does a full UIGraphicsImageRenderer redraw,
    // and re-running that on every read (imageSize/baseScale/displaySize/clampedOffset were
    // all re-deriving it) during high-frequency pinch/drag gesture callbacks pegged the main
    // thread hard enough to freeze the gesture and eventually get the app killed by the
    // watchdog. Precomputing these once turns the gesture-time math back into cheap arithmetic.
    private let normalizedImage: UIImage
    private let imageSize: CGSize
    private let baseScale: CGFloat

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    private var effectiveScale: CGFloat { baseScale * scale }

    private var displaySize: CGSize {
        CGSize(width: imageSize.width * effectiveScale, height: imageSize.height * effectiveScale)
    }

    private var maxOffsetX: CGFloat { max(0, (displaySize.width - cropWindowWidth) / 2) }
    private var maxOffsetY: CGFloat { max(0, (displaySize.height - cropWindowHeight) / 2) }

    private var clampedOffset: CGSize {
        CGSize(width: min(max(offset.width, -maxOffsetX), maxOffsetX),
               height: min(max(offset.height, -maxOffsetY), maxOffsetY))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.navyDeep.ignoresSafeArea()

                VStack(spacing: 24) {
                    Spacer()

                    ZStack {
                        ZStack {
                            Image(uiImage: normalizedImage)
                                .resizable()
                                .frame(width: displaySize.width, height: displaySize.height)
                                .offset(clampedOffset)
                        }
                        .frame(width: cropWindowWidth, height: cropWindowHeight)
                        .clipped()
                        .contentShape(Rectangle())
                        .gesture(SimultaneousGesture(magnificationGesture, dragGesture))

                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Color.amber, lineWidth: 2)
                            .frame(width: cropWindowWidth, height: cropWindowHeight)
                            .allowsHitTesting(false)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 4))

                    Text("Pinch to zoom, drag to reposition")
                        .font(.bodySmall).foregroundColor(.textMuted)

                    Spacer()

                    HStack(spacing: 12) {
                        OutlineButton("Cancel") { onCancel() }
                        PrimaryButton("Use Photo") { onDone(croppedImage()) }
                    }
                    .padding(.horizontal, 24)
                }
                .padding(.vertical, 16)
            }
            .navigationTitle("Crop Photo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.navyMid, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                offset = CGSize(width: lastOffset.width + value.translation.width,
                                 height: lastOffset.height + value.translation.height)
            }
            .onEnded { _ in
                lastOffset = clampedOffset
                offset = lastOffset
            }
    }

    private var magnificationGesture: some Gesture {
        MagnificationGesture()
            .onChanged { value in
                scale = min(max(lastScale * value, 1), 5)
            }
            .onEnded { _ in
                lastScale = scale
                // Re-clamp the offset since a changed scale can shrink the valid drag range.
                offset = clampedOffset
                lastOffset = offset
            }
    }

    private func croppedImage() -> UIImage {
        guard let cgImage = normalizedImage.cgImage else { return normalizedImage }

        let visibleWidthPx  = cropWindowWidth  / effectiveScale
        let visibleHeightPx = cropWindowHeight / effectiveScale

        let originXPx = (imageSize.width  - visibleWidthPx)  / 2 - clampedOffset.width  / effectiveScale
        let originYPx = (imageSize.height - visibleHeightPx) / 2 - clampedOffset.height / effectiveScale

        let clampedOriginX = min(max(originXPx, 0), imageSize.width  - visibleWidthPx)
        let clampedOriginY = min(max(originYPx, 0), imageSize.height - visibleHeightPx)

        let cropRect = CGRect(x: clampedOriginX, y: clampedOriginY,
                               width: visibleWidthPx, height: visibleHeightPx)

        guard let cropped = cgImage.cropping(to: cropRect) else { return normalizedImage }
        return UIImage(cgImage: cropped)
    }
}

private extension UIImage {
    // Camera photos carry orientation metadata rather than pixel-rotated data; redrawing
    // through UIGraphicsImageRenderer bakes that rotation into the pixel buffer so later
    // cgImage-based cropping lines up with what's visually displayed. `format.scale = 1`
    // keeps the resulting cgImage's pixel dimensions exactly equal to `size` in points,
    // regardless of the source image's scale factor.
    func normalizedOrientation() -> UIImage {
        if imageOrientation == .up { return self }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
