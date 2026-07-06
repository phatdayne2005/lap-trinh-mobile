# Mappin Server (FastAPI + MySQL)

API lưu người dùng và các địa điểm yêu thích cho app Mappin.

## Chạy local

### 1. Chuẩn bị database
Bạn đã có MySQL sẵn trên máy. **Không cần tạo database tay** — khi khởi động,
server sẽ tự tạo database `mappin` (nếu chưa có) và toàn bộ bảng.

Chỉ cần chắc chắn tài khoản MySQL trong `.env` có quyền tạo database.
(Nếu muốn tự tạo trước cho chủ động: `CREATE DATABASE mappin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`)

> ⚠️ Server tự tạo **bảng mới** còn thiếu, nhưng KHÔNG tự sửa cột khi bạn đổi model
> (SQLAlchemy không có "auto-update" như JPA). Khi thay đổi cấu trúc bảng đã tồn tại,
> hoặc dùng Alembic để migrate, hoặc (lúc đang dev) DROP bảng cũ để nó tạo lại.

### 2. Cấu hình kết nối
```bash
cp .env.example .env
```
Mở `.env` và sửa `DATABASE_URL` cho khớp user/mật khẩu MySQL của bạn, ví dụ:
```
DATABASE_URL=mysql+pymysql://root:MAT_KHAU_CUA_BAN@localhost:3306/mappin
```

### 3. Cài thư viện & chạy
```bash
python -m venv venv
venv\Scripts\activate        # Windows (PowerShell/CMD)
# source venv/bin/activate   # macOS/Linux

pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

- API chạy tại: http://localhost:8000
- Tài liệu tương tác (thử API ngay trên trình duyệt): http://localhost:8000/docs

> Từ điện thoại/emulator gọi vào máy tính: dùng `http://10.0.2.2:8000` (Android emulator)
> hoặc IP LAN của máy (vd `http://192.168.1.x:8000`).

## Các endpoint

| Method | Path | Mô tả | Cần token |
|--------|------|-------|:---------:|
| POST | `/auth/register` | Đăng ký, trả về access + refresh token | ✗ |
| POST | `/auth/login` | Đăng nhập, trả về access + refresh token | ✗ |
| POST | `/auth/refresh` | Đổi refresh token lấy cặp token mới | ✗ |
| POST | `/auth/logout` | Thu hồi refresh token (đăng xuất) | ✗ |
| GET | `/auth/me` | Thông tin user hiện tại | ✓ |
| GET | `/places` | Danh sách địa điểm đã lưu | ✓ |
| POST | `/places` | Thêm địa điểm | ✓ |
| GET | `/places/{id}` | Chi tiết 1 địa điểm | ✓ |
| DELETE | `/places/{id}` | Xóa địa điểm | ✓ |

Gửi token ở header: `Authorization: Bearer <access_token>`.

### Cơ chế token
- **Access token** (JWT): ngắn hạn (mặc định 30 phút), dùng cho mọi request cần đăng nhập.
- **Refresh token** (chuỗi ngẫu nhiên, lưu hash trong DB): dài hạn (mặc định 30 ngày).
  Khi access token hết hạn, app gọi `POST /auth/refresh` với refresh token để lấy cặp mới.
- **Rotation**: mỗi lần refresh, token cũ bị thu hồi và cấp token mới → chống dùng lại token cũ.
- **Logout / revoke**: `POST /auth/logout` đánh dấu refresh token là đã thu hồi trong DB.

> Luồng đề xuất phía app: lưu cả 2 token; khi gọi API gặp lỗi `401` thì tự động gọi
> `/auth/refresh` một lần rồi thử lại request; nếu refresh cũng `401` thì bắt đăng nhập lại.

### Ví dụ
```bash
# Đăng ký (đăng nhập bằng email; full_name là tên hiển thị)
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@b.com","full_name":"Thanh","password":"123456"}'

# Thêm địa điểm (thay <TOKEN> bằng access_token nhận được)
curl -X POST http://localhost:8000/places \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"place_name":"Quán phở ngon","category":"an_uong","rating":5,"address":"Q1","note":"ngon"}'
```

## Chạy trên VPS (sau này)
- Đặt `SECRET_KEY` ngẫu nhiên và `DATABASE_URL` trỏ tới MySQL trên VPS trong `.env`.
- Bỏ `--reload`, chạy sau Nginx, ví dụ:
  `uvicorn main:app --host 0.0.0.0 --port 8000 --workers 2`
- Giới hạn lại `allow_origins` trong `main.py` thay vì `*`.
