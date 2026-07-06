import SwiftUI
import UIKit

extension View {
    // Tap anywhere outside a focused text field to dismiss the keyboard, matching
    // standard iOS behavior (Mail, Messages, Safari) — SwiftUI doesn't do this by
    // default. Pair with .scrollDismissesKeyboard(.interactively) on any ScrollView
    // in the same screen so dragging the keyboard down works too.
    func dismissKeyboardOnTap() -> some View {
        simultaneousGesture(TapGesture().onEnded {
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        })
    }
}
