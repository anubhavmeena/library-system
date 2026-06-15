import SwiftUI

extension Color {
    static let navyDeep     = Color(hex: 0x0D1B4B)
    static let navyMid      = Color(hex: 0x1A2A68)
    static let navyLight    = Color(hex: 0x2035A3)
    static let amber        = Color(hex: 0xF59E0B)
    static let amberLight   = Color(hex: 0xFBBF24)
    static let amberFaint   = Color(hex: 0xF59E0B, alpha: 0.15)
    static let emerald      = Color(hex: 0x10B981)
    static let emeraldFaint = Color(hex: 0x10B981, alpha: 0.13)
    static let redAlert     = Color(hex: 0xEF4444)
    static let redFaint     = Color(hex: 0xEF4444, alpha: 0.13)
    static let blueSoft     = Color(hex: 0x3B82F6)
    static let blueFaint    = Color(hex: 0x3B82F6, alpha: 0.13)
    static let textPrimary  = Color(hex: 0xF0F4FF)
    static let textSub      = Color(hex: 0x8AA6F8)
    static let textMuted    = Color(hex: 0x4A5FA8)
    static let cardBg       = Color(hex: 0x1A2A68, alpha: 0.20)
    static let dividerColor = Color(hex: 0x2035A3, alpha: 0.20)
    static let cardBorder   = Color(hex: 0x2035A3, alpha: 0.40)

    init(hex: UInt32, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red:     Double((hex >> 16) & 0xFF) / 255,
            green:   Double((hex >> 8)  & 0xFF) / 255,
            blue:    Double(hex         & 0xFF) / 255,
            opacity: alpha
        )
    }
}
