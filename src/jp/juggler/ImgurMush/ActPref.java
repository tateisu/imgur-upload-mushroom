package jp.juggler.ImgurMush;

import java.io.File;

import jp.juggler.ImgurMush.helper.ImageTempDir;
import jp.juggler.util.DialogManager;
import jp.juggler.util.LogCategory;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

public class ActPref  extends PreferenceActivity{
	static final LogCategory log = new LogCategory("ActPref");
	ActPref act = this;
	DialogManager dialog_manager;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		dialog_manager = new DialogManager(this);
		final SharedPreferences pref = BaseActivity.getPref(this);

		File image_dir = ImageTempDir.getTempDir(act,pref,new Handler());
		if( image_dir != null){
			log.d("image_dir =%s",image_dir.getAbsolutePath());
		}
		
		addPreferencesFromResource(R.xml.pref);

		if( image_dir != null){
//			findPreference(PrefKey.KEY_TEMP_DIR).setDefaultValue(image_dir.getAbsolutePath());
		}

		
		findPreference("pref_add_account").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				// OAuthActivityを起動する
				Intent intent = new Intent(act, ActOAuth.class);
			    intent.putExtra(ActOAuth.REQUEST_TOKEN_ENDPOINT_URL, Config.REQUEST_TOKEN_ENDPOINT_URL);
			    intent.putExtra(ActOAuth.ACCESS_TOKEN_ENDPOINT_URL, Config.ACCESS_TOKEN_ENDPOINT_URL);
			    intent.putExtra(ActOAuth.AUTHORIZATION_WEBSITE_URL, Config.AUTHORIZATION_WEBSITE_URL);
			    intent.putExtra(ActOAuth.CONSUMER_KEY, Config.CONSUMER_KEY);
			    intent.putExtra(ActOAuth.CONSUMER_SECRET, Config.CONSUMER_SECRET);
			    intent.putExtra(ActOAuth.CALLBACK, Config.CALLBACK);
			    startActivity(intent);
				return false;
			}
		});
		

	}
}
