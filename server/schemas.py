from datetime import datetime
from typing import Optional

from pydantic import BaseModel, EmailStr, Field


# ---------- User ----------
class UserCreate(BaseModel):
    email: EmailStr
    full_name: str = Field(min_length=1, max_length=100)
    password: str = Field(min_length=6, max_length=72)  # bcrypt giới hạn 72 byte


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class UserOut(BaseModel):
    id: int
    email: str
    full_name: str
    created_at: datetime

    class Config:
        from_attributes = True  # cho phép trả về trực tiếp từ ORM object


class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: UserOut


class RefreshRequest(BaseModel):
    refresh_token: str


# ---------- SavedPlace ----------
class PlaceCreate(BaseModel):
    place_name: str = Field(min_length=1, max_length=255)
    address: Optional[str] = Field(default=None, max_length=500)
    category: Optional[str] = Field(default=None, max_length=50)
    rating: Optional[int] = Field(default=None, ge=1, le=5)
    note: Optional[str] = None
    image_url: Optional[str] = Field(default=None, max_length=500)
    map_url: Optional[str] = Field(default=None, max_length=1000)
    latitude: Optional[float] = None
    longitude: Optional[float] = None


class PlaceUpdate(BaseModel):
    place_name: Optional[str] = Field(default=None, min_length=1, max_length=255)
    address: Optional[str] = Field(default=None, max_length=500)
    category: Optional[str] = Field(default=None, max_length=50)
    rating: Optional[int] = Field(default=None, ge=1, le=5)
    note: Optional[str] = None
    map_url: Optional[str] = Field(default=None, max_length=1000)
    image_url: Optional[str] = Field(default=None, max_length=500)
    latitude: Optional[float] = None
    longitude: Optional[float] = None


class PlaceOut(BaseModel):
    id: int
    place_name: str
    address: Optional[str] = None
    category: Optional[str] = None
    rating: Optional[int] = None
    note: Optional[str] = None
    image_url: Optional[str] = None
    map_url: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    created_at: datetime

    class Config:
        from_attributes = True
