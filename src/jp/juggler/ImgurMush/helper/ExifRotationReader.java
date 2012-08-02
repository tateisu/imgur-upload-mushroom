package jp.juggler.ImgurMush.helper;

import android.media.ExifInterface;

public class ExifRotationReader {

	public static int read_rotation(String file_path) {
		try{
			ExifInterface exifInterface = new ExifInterface(file_path);
			int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
			switch(orientation){
			default:
			case ExifInterface.ORIENTATION_UNDEFINED: return -1;
			case ExifInterface.ORIENTATION_NORMAL: return 0;
			case ExifInterface.ORIENTATION_ROTATE_90: return 1;
			case ExifInterface.ORIENTATION_ROTATE_180: return 2;
			case ExifInterface.ORIENTATION_ROTATE_270: return 3;
			}
		}catch(Throwable ex){
			ex.printStackTrace();
			return -1;
		}
	}

}
