package com.example.mappin.util;

/**
 * Tiện ích xử lý chuỗi địa chỉ.
 */
public final class AddressUtils {

    private AddressUtils() {}

    /**
     * Bỏ mã bưu chính (zip code) ở cuối địa chỉ mà Google Maps hay trả kèm.
     * Ví dụ: "36/2 Nguyễn Gia Trí, Thạnh Mỹ Tây, Hồ Chí Minh 700000"
     *     -> "36/2 Nguyễn Gia Trí, Thạnh Mỹ Tây, Hồ Chí Minh"
     * Giữ nguyên nếu cuối địa chỉ không phải dãy số (vd "..., MA 01970, USA").
     */
    public static String stripPostalCode(String address) {
        if (address == null) return null;
        // Bỏ nhóm 4-6 chữ số đứng ở CUỐI chuỗi (kèm dấu phẩy/khoảng trắng thừa)
        String cleaned = address.replaceAll("[,\\s]*\\b\\d{4,6}\\b[,\\s]*$", "").trim();
        // Dọn dấu phẩy/space còn sót ở cuối
        cleaned = cleaned.replaceAll("[,\\s]+$", "").trim();
        return cleaned.isEmpty() ? address : cleaned;
    }
}
