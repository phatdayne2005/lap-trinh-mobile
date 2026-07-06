import hashlib
import secrets
from datetime import datetime, timedelta, timezone
from typing import Optional

from jose import JWTError, jwt
from passlib.context import CryptContext

from config import SECRET_KEY, ALGORITHM, ACCESS_TOKEN_EXPIRE_MINUTES

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain_password: str, password_hash: str) -> bool:
    return pwd_context.verify(plain_password, password_hash)


def create_access_token(subject: str) -> str:
    """Tạo JWT với 'sub' là id người dùng."""
    expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    payload = {"sub": str(subject), "exp": expire}
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)


def decode_token(token: str) -> Optional[str]:
    """Giải mã token, trả về 'sub' (user id) hoặc None nếu không hợp lệ/hết hạn."""
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return payload.get("sub")
    except JWTError:
        return None


def create_refresh_token() -> str:
    """Sinh refresh token dạng chuỗi ngẫu nhiên (opaque, không phải JWT)."""
    return secrets.token_urlsafe(48)


def hash_refresh_token(token: str) -> str:
    """Băm refresh token để lưu DB. Token entropy cao nên dùng SHA-256 là đủ."""
    return hashlib.sha256(token.encode()).hexdigest()
