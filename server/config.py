import os

from dotenv import load_dotenv

# Đọc biến môi trường từ file .env (nếu có)
load_dotenv()

# Kết nối MySQL. Định dạng: mysql+pymysql://user:password@host:port/database
# Sửa trong file .env cho khớp MySQL trên máy bạn.
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "mysql+pymysql://root:123456@localhost:3306/mappin",
)

# Khóa bí mật để ký JWT. BẮT BUỘC đổi khi chạy thật (đặt trong .env).
SECRET_KEY = os.getenv("SECRET_KEY", "doi-khoa-nay-khi-len-production-toi-thieu-32-ky-tu")
ALGORITHM = "HS256"
# Access token ngắn hạn (phút). Hết hạn thì dùng refresh token để lấy cái mới.
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", "30"))
# Refresh token dài hạn (ngày).
REFRESH_TOKEN_EXPIRE_DAYS = int(os.getenv("REFRESH_TOKEN_EXPIRE_DAYS", "30"))
