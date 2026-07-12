package com.example.mappin.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Nén ảnh trước khi upload: downscale cạnh dài về tối đa MAX_DIMENSION, xoay theo
 * EXIF (ảnh chụp dọc hay bị nằm ngang) rồi nén JPEG. Giúp ảnh gửi lên còn vài trăm KB
 * thay vì vài MB, tránh chậm/tốn 4G và tránh OOM khi nạp ảnh gốc độ phân giải cao.
 */
public final class ImageUtils {

    private static final int MAX_DIMENSION = 1600;   // cạnh dài tối đa (px)
    private static final int JPEG_QUALITY = 80;

    private ImageUtils() {}

    /**
     * Đọc ảnh từ Uri -> byte[] JPEG đã nén, hoặc null nếu lỗi.
     * Nên gọi ở luồng nền vì decode ảnh lớn có thể chậm.
     */
    public static byte[] compress(Context context, Uri uri) {
        try {
            // 1) Đọc kích thước gốc mà KHÔNG nạp bitmap vào RAM
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                BitmapFactory.decodeStream(is, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            // 2) inSampleSize (bậc 2) để giảm tải bộ nhớ khi decode ảnh lớn
            bounds.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION);
            bounds.inJustDecodeBounds = false;

            Bitmap bitmap;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                bitmap = BitmapFactory.decodeStream(is, null, bounds);
            }
            if (bitmap == null) return null;

            // 3) Scale chính xác về đúng cạnh dài tối đa (inSampleSize chỉ giảm theo bậc 2)
            bitmap = scaleDown(bitmap, MAX_DIMENSION);

            // 4) Xoay theo EXIF cho đúng chiều
            bitmap = applyExifRotation(context, uri, bitmap);

            // 5) Nén JPEG
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            bitmap.recycle();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(int width, int height, int maxDim) {
        int sample = 1;
        int longer = Math.max(width, height);
        // Giữ dư gấp đôi maxDim để bước scaleDown ở sau cho ra ảnh nét, không vỡ
        while (longer / sample > maxDim * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleDown(Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longer = Math.max(w, h);
        if (longer <= maxDim) return src;
        float ratio = (float) maxDim / longer;
        int nw = Math.max(1, Math.round(w * ratio));
        int nh = Math.max(1, Math.round(h * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);
        if (scaled != src) src.recycle();
        return scaled;
    }

    private static Bitmap applyExifRotation(Context context, Uri uri, Bitmap bitmap) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return bitmap;
            ExifInterface exif = new ExifInterface(is);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix m = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:       m.postRotate(90);  break;
                case ExifInterface.ORIENTATION_ROTATE_180:      m.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270:      m.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:   m.postScale(1, -1); break;
                default: return bitmap;   // ORIENTATION_NORMAL / không rõ -> giữ nguyên
            }
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception e) {
            return bitmap;   // không đọc được EXIF -> giữ nguyên ảnh
        }
    }
}
