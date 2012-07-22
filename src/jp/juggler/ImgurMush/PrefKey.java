package jp.juggler.ImgurMush;

public class PrefKey {
	private PrefKey(){} // disable create instance

	// v3まで
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
	
	
	
	// v4以降
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

}
