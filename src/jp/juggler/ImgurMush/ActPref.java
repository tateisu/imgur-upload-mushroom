package jp.juggler.ImgurMush;

import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ImageTempDir;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

public class ActPref  extends PreferenceActivity{
	
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ImageTempDir.getTempDir(this,BaseActivity.getPref(this),new Handler());
		
		addPreferencesFromResource(R.xml.pref);

		findPreference("pref_add_account").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
			    startActivity(new Intent(ActPref.this, ActOAuth.class));
				return false;
			}
		});
	}
}
