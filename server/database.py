from sqlalchemy import create_engine, Column, Integer, String, Float, ForeignKey, DateTime, Text, Boolean, text
from sqlalchemy.engine import make_url, URL
from sqlalchemy.orm import declarative_base, sessionmaker, relationship
import datetime

from config import DATABASE_URL

# pool_pre_ping giúp tránh lỗi "MySQL server has gone away" khi kết nối để lâu.
engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    # MySQL yêu cầu VARCHAR phải có độ dài -> luôn khai báo length cho String.
    # Email là trường đăng nhập chính (bắt buộc + unique).
    email = Column(String(255), unique=True, index=True, nullable=False)
    full_name = Column(String(100), nullable=False)  # tên hiển thị
    password_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    places = relationship("SavedPlace", back_populates="owner", cascade="all, delete-orphan")
    refresh_tokens = relationship("RefreshToken", back_populates="user", cascade="all, delete-orphan")


class SavedPlace(Base):
    __tablename__ = "saved_places"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    place_name = Column(String(255), nullable=False)
    address = Column(String(500), nullable=True)
    # Các trường khớp với form thêm địa điểm trên app Android.
    category = Column(String(50), nullable=True)       # an_uong / khach_san / ca_phe / khac
    rating = Column(Integer, nullable=True)            # 1..5
    note = Column(Text, nullable=True)
    image_url = Column(String(500), nullable=True)     # đường dẫn ảnh (bổ sung upload sau)
    map_url = Column(String(1000), nullable=True)      # link Google Maps (share/dán từ app)
    # Toạ độ để nullable vì form hiện chưa lấy vị trí; thêm sau khi tích hợp bản đồ.
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    owner = relationship("User", back_populates="places")


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    # Chỉ lưu SHA-256 của refresh token, không lưu token gốc (lộ DB vẫn an toàn).
    token_hash = Column(String(64), unique=True, index=True, nullable=False)
    expires_at = Column(DateTime, nullable=False)
    revoked = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    user = relationship("User", back_populates="refresh_tokens")


def get_db():
    """Dependency của FastAPI: mở session cho mỗi request rồi đóng lại."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def _ensure_database_exists():
    """Tự tạo database (schema) nếu chưa có — kết nối tới server MySQL mà không chọn DB."""
    url = make_url(DATABASE_URL)
    db_name = url.database
    # Dựng lại URL KHÔNG kèm database (URL.set(database=None) không bỏ được vì None bị bỏ qua).
    server_url = URL.create(
        drivername=url.drivername,
        username=url.username,
        password=url.password,
        host=url.host,
        port=url.port,
        query=url.query,
    )
    # AUTOCOMMIT để lệnh CREATE DATABASE được ghi ngay.
    server_engine = create_engine(server_url, isolation_level="AUTOCOMMIT")
    with server_engine.connect() as conn:
        conn.execute(text(
            f"CREATE DATABASE IF NOT EXISTS `{db_name}` "
            f"CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        ))
    server_engine.dispose()


def init_db():
    # 1) Tạo database nếu chưa có, 2) tạo các bảng còn thiếu.
    _ensure_database_exists()
    Base.metadata.create_all(bind=engine)
