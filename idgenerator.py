import argparse
import os
import logging
import qrcode
from PIL import Image, ImageDraw, ImageFont

def upload_to_s3(file_path, bucket_name, key):
    # Placeholder for actual upload logic
    return f"https://dummy-s3-url/{bucket_name}/{key}"

def generate_id_card(data, photo_path):
    if not os.path.exists("static"):
        os.makedirs("static")

    print("User Data:", data)

    WIDTH, HEIGHT = 600, 400
    MARGIN = 10

    # Create base card with white background
    card = Image.new('RGB', (WIDTH, HEIGHT), (255, 255, 255))
    draw = ImageDraw.Draw(card)

    # Load fonts
    try:
        title_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 28)
        body_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 20)
    except:
        title_font = body_font = ImageFont.load_default()

    # Draw border
    border_color = (0, 0, 0)
    draw.rectangle([0, 0, WIDTH-1, HEIGHT-1], outline=border_color, width=3)

    # Header background inside border
    header_height = 50
    border_margin = 7  # already defined later; define earlier if not
    draw.rectangle(
        [border_margin, border_margin, WIDTH - border_margin, border_margin + header_height],
        fill=(0, 0, 0)
    )

    # Header text (white, centered inside black box)
    header_text = "TARGET ZONE LIBRARY"
    text_width = draw.textlength(header_text, font=title_font)
    text_x = (WIDTH - text_width) // 2
    text_y = border_margin + (header_height - title_font.size) // 2  # vertical centering
    draw.text((text_x, text_y), header_text, font=title_font, fill=(255, 255, 255))

    # Starting Y position after header
    y_start = 100
    spacing = 37

    # Draw user info
    fields = [
        ("Name", data.get("name", "")),
        ("Father's Name", data.get("father_name", "")),
        ("Age", data.get("age", "")),
        ("Shift", f"{data.get('shift', '')} Hours"),
        ("Phone", data.get("phone", "")),
        ("Paid", f"Rs. {data.get('amount', '')}")
    ]

    for i, (label, value) in enumerate(fields):
        draw.text((20, y_start + i * spacing), f"{label}: {value}", font=body_font, fill=(0, 0, 0))

    # Paste photo (larger)
    try:
        user_img = Image.open(photo_path).resize((130, 130))
        card.paste(user_img, (440, 80))
    except Exception as e:
        logging.warning(f"Failed to load or paste user image: {e}")

    # Generate QR code
    qr_data = "\n".join([f"{k}: {v}" for k, v in data.items()])
    qr = qrcode.make(qr_data).resize((130, 130))
    card.paste(qr, (440, 220))
    
    border_color = (0, 0, 0)
    border_thickness = 5
    border_margin = 7  # margin from the card edge

    draw.rectangle(
        [border_margin, border_margin, WIDTH - border_margin, HEIGHT - border_margin],
        outline=border_color,
        width=border_thickness
    )
    #draw.rectangle([0, 0, WIDTH-1, HEIGHT-1], outline=border_color, width=10)

    # Save and upload
    path = f"static/id_{data.get('phone', 'unknown')}.png"
    card.save(path)

    head, tail = os.path.split(path)
    id_card_s3_path = upload_to_s3(path, 'library-id-cards', tail)

    logging.info(f"ID path is: {id_card_s3_path}")
    return id_card_s3_path

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate a Library ID card")
    parser.add_argument("--name", required=True)
    parser.add_argument("--father_name", required=True)
    parser.add_argument("--age", required=True)
    parser.add_argument("--shift", required=True)
    parser.add_argument("--phone", required=True)
    parser.add_argument("--amount", required=True)
    parser.add_argument("--photo", required=True)

    args = parser.parse_args()

    user_data = {
        "name": args.name,
        "father_name": args.father_name,
        "age": args.age,
        "shift": args.shift,
        "phone": args.phone,
        "amount": args.amount
    }

    generate_id_card(user_data, args.photo)
