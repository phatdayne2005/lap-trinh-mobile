import os
import shutil
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from typing import List, Tuple

from fastapi import FastAPI, Depends, HTTPException, status, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session

import schemas
from config import REFRESH_TOKEN_EXPIRE_DAYS
from database import get_db, init_db, User, SavedPlace, RefreshToken
from security import (
    hash_password,
    verify_password,
    create_access_token,
    decode_token,
    create_refresh_token,
    hash_refresh_token,
)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Chạy khi server khởi động: tạo database + bảng nếu chưa có.
    init_db()
    yield
    # (nếu cần dọn dẹp khi tắt server thì đặt ở đây)


app = FastAPI(title="Mappin API", version="1.0", lifespan=lifespan)

# Cho phép mọi origin khi test local. Khi lên VPS nên giới hạn lại.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

bearer_scheme = HTTPBearer()

# Thư mục lưu ảnh upload + phục vụ file tĩnh tại /uploads
UPLOAD_DIR = "uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")


def _issue_tokens(user: User, db: Session) -> Tuple[str, str]:
    """Cấp cặp access token (JWT) + refresh token (lưu hash vào DB)."""
    access = create_access_token(user.id)
    raw_refresh = create_refresh_token()
    db.add(RefreshToken(
        user_id=user.id,
        token_hash=hash_refresh_token(raw_refresh),
        expires_at=datetime.utcnow() + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS),
    ))
    db.commit()
    return access, raw_refresh


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    """Lấy user từ JWT trong header Authorization: Bearer <token>."""
    user_id = decode_token(credentials.credentials)
    if user_id is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn")
    user = db.query(User).filter(User.id == int(user_id)).first()
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Người dùng không tồn tại")
    return user


# ---------------- Auth ----------------
@app.post("/auth/register", response_model=schemas.Token, status_code=status.HTTP_201_CREATED)
def register(payload: schemas.UserCreate, db: Session = Depends(get_db)):
    if db.query(User).filter(User.email == payload.email).first():
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Email đã được sử dụng")

    user = User(
        email=payload.email,
        full_name=payload.full_name,
        password_hash=hash_password(payload.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    access, refresh_token = _issue_tokens(user, db)
    return schemas.Token(access_token=access, refresh_token=refresh_token, user=user)


@app.post("/auth/login", response_model=schemas.Token)
def login(payload: schemas.UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == payload.email).first()
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Sai email hoặc mật khẩu")

    access, refresh_token = _issue_tokens(user, db)
    return schemas.Token(access_token=access, refresh_token=refresh_token, user=user)


@app.post("/auth/refresh", response_model=schemas.Token)
def refresh(payload: schemas.RefreshRequest, db: Session = Depends(get_db)):
    """Đổi refresh token lấy cặp token mới (xoay vòng: token cũ bị thu hồi)."""
    rt = (
        db.query(RefreshToken)
        .filter(RefreshToken.token_hash == hash_refresh_token(payload.refresh_token))
        .first()
    )
    if rt is None or rt.revoked or rt.expires_at < datetime.utcnow():
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn")

    user = db.query(User).filter(User.id == rt.user_id).first()
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Người dùng không tồn tại")

    # Rotation: thu hồi token cũ rồi cấp cặp mới.
    rt.revoked = True
    access, refresh_token = _issue_tokens(user, db)
    return schemas.Token(access_token=access, refresh_token=refresh_token, user=user)


@app.post("/auth/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(payload: schemas.RefreshRequest, db: Session = Depends(get_db)):
    """Thu hồi refresh token (đăng xuất)."""
    rt = (
        db.query(RefreshToken)
        .filter(RefreshToken.token_hash == hash_refresh_token(payload.refresh_token))
        .first()
    )
    if rt and not rt.revoked:
        rt.revoked = True
        db.commit()


@app.get("/auth/me", response_model=schemas.UserOut)
def me(current_user: User = Depends(get_current_user)):
    return current_user


# ---------------- Saved places ----------------
@app.get("/places", response_model=List[schemas.PlaceOut])
def list_places(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return (
        db.query(SavedPlace)
        .filter(SavedPlace.user_id == current_user.id)
        .order_by(SavedPlace.created_at.desc())
        .all()
    )


@app.post("/places", response_model=schemas.PlaceOut, status_code=status.HTTP_201_CREATED)
def create_place(
    payload: schemas.PlaceCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    place = SavedPlace(user_id=current_user.id, **payload.model_dump())
    db.add(place)
    db.commit()
    db.refresh(place)
    return place


@app.put("/places/{place_id}", response_model=schemas.PlaceOut)
def update_place(
    place_id: int,
    payload: schemas.PlaceUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    place = _get_owned_place(place_id, current_user, db)
    # Chỉ cập nhật các trường được gửi lên (exclude_unset)
    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(place, key, value)
    db.commit()
    db.refresh(place)
    return place


@app.post("/places/{place_id}/photo", response_model=schemas.PlaceOut)
def upload_photo(
    place_id: int,
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    place = _get_owned_place(place_id, current_user, db)
    ext = os.path.splitext(file.filename or "")[1] or ".jpg"
    filename = f"{uuid.uuid4().hex}{ext}"
    with open(os.path.join(UPLOAD_DIR, filename), "wb") as f:
        shutil.copyfileobj(file.file, f)
    place.image_url = f"/uploads/{filename}"
    db.commit()
    db.refresh(place)
    return place


@app.get("/places/{place_id}", response_model=schemas.PlaceOut)
def get_place(
    place_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    place = _get_owned_place(place_id, current_user, db)
    return place


@app.delete("/places/{place_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_place(
    place_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    place = _get_owned_place(place_id, current_user, db)
    db.delete(place)
    db.commit()


def _get_owned_place(place_id: int, user: User, db: Session) -> SavedPlace:
    place = db.query(SavedPlace).filter(SavedPlace.id == place_id).first()
    if place is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Không tìm thấy địa điểm")
    if place.user_id != user.id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Không có quyền với địa điểm này")
    return place


@app.get("/")
def root():
    return {"status": "ok", "service": "Mappin API", "docs": "/docs"}


# Cho phép chạy trực tiếp bằng: python main.py
if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
