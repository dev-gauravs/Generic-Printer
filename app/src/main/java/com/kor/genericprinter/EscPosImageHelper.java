package com.kor.genericprinter;

import android.graphics.Bitmap;

public class EscPosImageHelper {

    public static byte[] convertBitmap(Bitmap bitmap) {
        return convertBitmap(bitmap, 384);
    }

    public static byte[] convertBitmap(Bitmap bitmap, int widthPx) {
        return EscPosImage.INSTANCE.toRasterBytes(bitmap, widthPx, 160);
    }
}
