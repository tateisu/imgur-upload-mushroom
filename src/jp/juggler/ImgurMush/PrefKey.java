package jp.juggler.ImgurMush;

import android.content.SharedPreferences;

public class PrefKey {
	private PrefKey(){} // disable create instance

	// v1.xまで
	public static final String KEY_AUTO_UPLOAD = "cbAutoStart";
	public static final String KEY_AUTO_PICK = "cbAutoPick";
	public static final String KEY_DISABLE_PREVIEW = "cbDisablePreview";
	public static final String KEY_INSERT_SPACE_PRE = "cbInsertSpacePref";
	public static final String KEY_INSERT_SPACE_SUF = "cbInsertSpaceSuff";
	public static final String KEY_URL_MODE = "URL_mode";

	public static final String KEY_DEFAULT_ACCOUNT = "pref_default_account";
	public static final String KEY_ACCOUNT_LAST_USED = "account_name";


	public static final String VALUE_LAST_USED = "<>lastused";
	public static final String VALUE_ACCOUNT_ANONYMOUS = "<>anonymous";



	// v2.x
	public static final String KEY_LAST_RESIZE_MODE = "resize_preset_mode";
	public static final String KEY_LAST_RESIZE_VALUE = "resize_preset_value";
	public static final String KEY_TEMP_DIR="image_output_dir";
	public static final String KEY_JPEG_QUALITY = "jpeg_quality";

	public static final String EXTRA_SRC_PATH="src_path";
	public static final String EXTRA_DST_PATH="dst_path";

	public static final String EXTRA_RESIZE_PRESET_MODE = "mode";
	public static final String EXTRA_RESIZE_PRESET_VALUE = "value";

	public static final String KEY_HISTORY_ACCOUNT = "history_account";
	public static final String KEY_HISTORY_ALBUM   = "history_album";
	public static final String KEY_ALBUM_CACHE_COUNT = "album_cache_count";
	public static final String KEY_ALBUM_CACHE_ACCOUNT_NAME = "album_cache_account_name";
	public static final String KEY_ALBUM_CACHE_ALBUM_LIST = "album_cache_album_list";
	public static final String KEY_AUTO_EDIT = "edit_autostart";
	public static final String EXTRA_IS_STATUS_SAVE = "is_status_save";
	public static final String EXTRA_EDIT_ROTATE = "edit_rotate";
	public static final String EXTRA_CROP_L = "edit_crop_l";
	public static final String EXTRA_CROP_R = "edit_crop_r";
	public static final String EXTRA_CROP_T = "edit_crop_t";
	public static final String EXTRA_CROP_B = "edit_crop_b";
	public static final String EXTRA_CAPTURE_URI = "capture_uri";
	
	public static final String KEY_URL_PREFIX = "text_output_prefix";
	public static final String KEY_URL_SUFFIX = "text_output_suffix";
	public static final String EXTRA_LAST_EDIT_INDEX = "last_edit_index";
	
	public static void upgrade_config(SharedPreferences pref) {
		SharedPreferences.Editor e = pref.edit();
		boolean bChanged = false;
		
		// KEY_URL_PREFIX
		String v = pref.getString(KEY_URL_PREFIX,null);
		if( v == null ){
			boolean b = pref.getBoolean(KEY_INSERT_SPACE_PRE,false);
			e.putString(KEY_URL_PREFIX,(b?" ":""));
			bChanged = true;
		}
		// KEY_URL_SUFFIX
		v = pref.getString(KEY_URL_SUFFIX,null);
		if( v == null ){
			boolean b = pref.getBoolean(KEY_INSERT_SPACE_SUF,true);
			e.putString(KEY_URL_SUFFIX,(b?" ":""));
			bChanged = true;
		}

		
		//
		if(bChanged) e.commit();
	}

}
