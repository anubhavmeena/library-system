use crate::services::notification::PaymentReceiptInfo;
use printpdf::path::{PaintMode, WindingOrder};
use printpdf::{BuiltinFont, Color, Mm, PdfDocument, Point, Polygon, Rgb};
#[cfg(test)]
use rust_decimal::Decimal;

// A5 landscape-ish receipt, in mm.
const W: f32 = 148.0;
const H: f32 = 105.0;
const MARGIN: f32 = 6.0;
const HEADER_H: f32 = 16.0;

/// Renders the same fields the Java `ReceiptPdfService` prints — invoice
/// number, date, student/plan/seat, an optional Valid Upto line, payment
/// method, and a Paid/Pending amount table. Uses "Rs." rather than the "₹"
/// glyph since the built-in WinAnsi-encoded fonts can't render it.
pub fn build_receipt_pdf(info: &PaymentReceiptInfo) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    let (doc, page1, layer1) = PdfDocument::new("Payment Receipt", Mm(W), Mm(H), "Layer 1");
    let layer = doc.get_page(page1).get_layer(layer1);

    let font = doc.add_builtin_font(BuiltinFont::Helvetica)?;
    let font_bold = doc.add_builtin_font(BuiltinFont::HelveticaBold)?;

    fill_color(&layer, 1.0, 1.0, 1.0);
    filled_rect(&layer, 0.0, 0.0, W, H);

    stroke_color(&layer, 0.0, 0.0, 0.0);
    layer.set_outline_thickness(1.0);
    stroked_rect(&layer, MARGIN, MARGIN, W - 2.0 * MARGIN, H - 2.0 * MARGIN);

    // Navy header bar
    fill_color(&layer, 0.05, 0.15, 0.35);
    filled_rect(&layer, MARGIN, H - MARGIN - HEADER_H, W - 2.0 * MARGIN, HEADER_H);
    fill_color(&layer, 1.0, 1.0, 1.0);
    layer.use_text(
        "TARGET ZONE LIBRARY — PAYMENT RECEIPT",
        12.0,
        Mm(MARGIN + 4.0),
        Mm(H - MARGIN - HEADER_H / 2.0 - 2.0),
        &font_bold,
    );

    let today = chrono::Local::now().date_naive();
    let paid = format!("Rs. {}", info.amount_paid.normalize());
    let pending = format!("Rs. {}", info.amount_pending.normalize());
    let valid_upto = info.valid_upto.map(|d| d.format("%d/%m/%Y").to_string());

    let mut rows: Vec<(&str, String)> = vec![
        ("Invoice No.", info.invoice_id.clone()),
        ("Date", today.format("%d/%m/%Y").to_string()),
        ("Student Name", info.user_name.clone()),
        ("Phone", info.user_mobile.clone().unwrap_or_else(|| "-".into())),
        ("Plan", info.plan_name.clone()),
        ("Seat", info.seat_number.clone().unwrap_or_else(|| "-".into())),
    ];
    if let Some(vu) = valid_upto {
        rows.push(("Valid Upto", vu));
    }
    rows.push(("Payment Method", info.payment_method.clone()));

    let row_h = 6.5;
    let mut y = H - MARGIN - HEADER_H - 8.0;
    let label_x = MARGIN + 4.0;
    let value_x = MARGIN + 42.0;

    for (label, value) in &rows {
        stroke_color(&layer, 0.75, 0.75, 0.75);
        layer.set_outline_thickness(0.3);
        stroked_rect(&layer, MARGIN + 2.0, y - row_h + 2.0, W - 2.0 * MARGIN - 4.0, row_h);

        fill_color(&layer, 0.3, 0.3, 0.3);
        layer.use_text(*label, 8.5, Mm(label_x), Mm(y - row_h + 4.0), &font);
        fill_color(&layer, 0.0, 0.0, 0.0);
        layer.use_text(value.as_str(), 8.5, Mm(value_x), Mm(y - row_h + 4.0), &font_bold);

        y -= row_h;
    }

    // Amount table
    y -= 4.0;
    fill_color(&layer, 0.9, 0.95, 0.9);
    filled_rect(&layer, MARGIN + 2.0, y - row_h + 2.0, (W - 2.0 * MARGIN - 4.0) / 2.0, row_h);
    fill_color(&layer, 0.0, 0.4, 0.0);
    layer.use_text("Amount Paid", 8.5, Mm(label_x), Mm(y - row_h + 4.0), &font);
    layer.use_text(&paid, 10.0, Mm(label_x + 28.0), Mm(y - row_h + 4.0), &font_bold);

    fill_color(&layer, 0.98, 0.92, 0.85);
    filled_rect(&layer, MARGIN + 2.0 + (W - 2.0 * MARGIN - 4.0) / 2.0, y - row_h + 2.0, (W - 2.0 * MARGIN - 4.0) / 2.0, row_h);
    fill_color(&layer, 0.55, 0.25, 0.0);
    layer.use_text("Amount Pending", 8.5, Mm(value_x + 30.0), Mm(y - row_h + 4.0), &font);
    layer.use_text(&pending, 10.0, Mm(value_x + 58.0), Mm(y - row_h + 4.0), &font_bold);

    // Footer
    fill_color(&layer, 0.4, 0.4, 0.4);
    layer.use_text(
        "This is a system-generated receipt. For queries, contact Target Zone Library.",
        6.5,
        Mm(MARGIN + 4.0),
        Mm(MARGIN + 3.0),
        &font,
    );

    Ok(doc.save_to_bytes()?)
}

fn fill_color(layer: &printpdf::PdfLayerReference, r: f32, g: f32, b: f32) {
    layer.set_fill_color(Color::Rgb(Rgb::new(r, g, b, None)));
}

fn stroke_color(layer: &printpdf::PdfLayerReference, r: f32, g: f32, b: f32) {
    layer.set_outline_color(Color::Rgb(Rgb::new(r, g, b, None)));
}

fn filled_rect(layer: &printpdf::PdfLayerReference, x: f32, y: f32, w: f32, h: f32) {
    layer.add_polygon(Polygon {
        rings: vec![vec![
            (Point::new(Mm(x), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y + h)), false),
            (Point::new(Mm(x), Mm(y + h)), false),
        ]],
        mode: PaintMode::Fill,
        winding_order: WindingOrder::NonZero,
    });
}

fn stroked_rect(layer: &printpdf::PdfLayerReference, x: f32, y: f32, w: f32, h: f32) {
    layer.add_polygon(Polygon {
        rings: vec![vec![
            (Point::new(Mm(x), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y + h)), false),
            (Point::new(Mm(x), Mm(y + h)), false),
        ]],
        mode: PaintMode::Stroke,
        winding_order: WindingOrder::NonZero,
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    fn sample_info() -> PaymentReceiptInfo {
        PaymentReceiptInfo {
            user_id: Uuid::new_v4(),
            user_name: "Test Student".into(),
            user_mobile: Some("9876543210".into()),
            user_email: Some("student@example.com".into()),
            invoice_id: "INV-20260101-ABC123".into(),
            amount_paid: Decimal::from(1200),
            amount_pending: Decimal::from(600),
            plan_name: "Morning 30 Days".into(),
            seat_number: Some("A1".into()),
            valid_upto: Some(chrono::Local::now().date_naive() + chrono::Duration::days(30)),
            payment_method: "CASH".into(),
        }
    }

    #[test]
    fn build_receipt_pdf_produces_valid_pdf() {
        let pdf = build_receipt_pdf(&sample_info()).expect("must build a receipt PDF");
        assert!(pdf.starts_with(b"%PDF"), "output must be a real PDF");
        assert!(pdf.len() > 500, "a rendered receipt should be more than a trivially empty PDF");
    }

    #[test]
    fn build_receipt_pdf_without_valid_upto_still_succeeds() {
        let mut info = sample_info();
        info.valid_upto = None;
        let pdf = build_receipt_pdf(&info).expect("must build even without a Valid Upto date");
        assert!(pdf.starts_with(b"%PDF"));
    }
}
