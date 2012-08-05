package jp.juggler.ImgurMush.helper;

import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class MushroomHelper {
	static final LogCategory log = new LogCategory("MushroomHelper");
	
	public static String trim_output_url(HelperEnvUI env,String text){
		SharedPreferences pref = env.pref();
		PrefKey.upgrade_config(pref);
		return pref.getString(PrefKey.KEY_URL_PREFIX,"")+text+pref.getString(PrefKey.KEY_URL_SUFFIX,"");
	}
	

	public static boolean is_mushroom(HelperEnvUI env){
		Intent intent = env.act.getIntent();
		return ( intent != null && "com.adamrocker.android.simeji.ACTION_INTERCEPT".equals(intent.getAction()) );
	}

	
	public static void finish_mush(HelperEnvUI env,boolean bDressURL, String text){
		if( bDressURL && text != null && text.length() > 0 ){
			text = trim_output_url(env,text);
		}
		if( is_mushroom(env) ){
			Intent intent = new Intent();
			intent.putExtra("replace_key", text);
			env.act.setResult(Activity.RESULT_OK, intent);
		}else if(text !=null && text.length() > 0 ){
			ClipboardHelper.clipboard_copy(env,text,env.getString(R.string.output_to_clipboard));
		}
		env.act.finish();
	}

	public static String uri_to_path(HelperEnvUI env, Uri uri){
		if(uri==null) return null;
		if(uri.getScheme().equals("content") ){
			Cursor c = env.cr.query(uri, new String[]{MediaStore.Images.Media.DATA }, null, null, null);
			if( c !=null ){
				try{
					if(c.moveToNext() ) return c.getString(0);
				}finally{
					c.close();
				}
			}
		}else if(uri.getScheme().equals("file") ){
			return uri.getPath();
		}
		log.d("cannot convert uri to path. %s",uri.toString());
		env.show_toast(true,R.string.uri_parse_error,uri.toString());
		return null;
	}
}
