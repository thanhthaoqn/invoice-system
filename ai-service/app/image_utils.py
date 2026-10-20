import io
import logging
from PIL import Image, ImageOps, ImageEnhance

logger = logging.getLogger(__name__)

def preprocess_image(image_bytes: bytes) -> bytes:
    """
    Tiền xử lý ảnh hóa đơn trước khi đưa vào OCR / VLM:
    - Sửa góc xoay theo EXIF (auto-orientation / deskew cơ bản).
    - Chuẩn hóa kích thước (resize nếu quá lớn để tối ưu thời gian xử lý và giữ nguyên chi tiết).
    - Cân bằng độ tương phản (contrast enhancement).
    - Khử nhiễu và làm rõ nét văn bản.
    """
    try:
        image = Image.open(io.BytesIO(image_bytes))
        
        # 1. Deskew theo EXIF orientation (xoay đúng chiều ảnh chụp điện thoại)
        image = ImageOps.exif_transpose(image)
        
        # Chuyển đổi màu sang RGB nếu là RGBA, P hoặc CMYK
        if image.mode != "RGB":
            image = image.convert("RGB")
            
        # 2. Chuẩn hóa kích thước: nếu cạnh lớn nhất > 2400px thì resize tỉ lệ
        max_dim = 2400
        w, h = image.size
        if max(w, h) > max_dim:
            scale = max_dim / float(max(w, h))
            new_size = (int(w * scale), int(h * scale))
            image = image.resize(new_size, Image.Resampling.LANCZOS)
            
        # 3. Tăng cường độ tương phản văn bản hóa đơn
        enhancer = ImageEnhance.Contrast(image)
        image = enhancer.enhance(1.25)
        
        # 4. Tăng độ sắc nét nhẹ cho nét chữ
        sharpness = ImageEnhance.Sharpness(image)
        image = sharpness.enhance(1.15)
        
        output_buffer = io.BytesIO()
        image.save(output_buffer, format="JPEG", quality=92, optimize=True)
        return output_buffer.getvalue()
    except Exception as e:
        logger.warning(f"Image preprocessing warning: {e}. Returning original bytes.")
        return image_bytes
