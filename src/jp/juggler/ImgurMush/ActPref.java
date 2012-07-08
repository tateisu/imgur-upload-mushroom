package jp.juggler.ImgurMush;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class ActPref  extends PreferenceActivity{
	ActPref self = this;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		

		
		addPreferencesFromResource(R.xml.pref);
		findPreference("pref_add_account").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				// OAuthActivityを起動する
				Intent intent = new Intent(self, OAuthActivity.class);
			    intent.putExtra(OAuthActivity.REQUEST_TOKEN_ENDPOINT_URL, Config.REQUEST_TOKEN_ENDPOINT_URL);
			    intent.putExtra(OAuthActivity.ACCESS_TOKEN_ENDPOINT_URL, Config.ACCESS_TOKEN_ENDPOINT_URL);
			    intent.putExtra(OAuthActivity.AUTHORIZATION_WEBSITE_URL, Config.AUTHORIZATION_WEBSITE_URL);
			    intent.putExtra(OAuthActivity.CONSUMER_KEY, Config.CONSUMER_KEY);
			    intent.putExtra(OAuthActivity.CONSUMER_SECRET, Config.CONSUMER_SECRET);
			    intent.putExtra(OAuthActivity.CALLBACK, Config.CALLBACK);
			    startActivity(intent);
				return false;
			}
		});
		
		findPreference("pref_history_clear").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				new AlertDialog.Builder(self)
				.setCancelable(true)
				.setTitle(R.string.pref_history_clear_title)
				.setMessage(R.string.pref_history_clear_message)
				.setNegativeButton(R.string.btnCancel,new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				})
				.setPositiveButton(R.string.ok,new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog progress = new ProgressDialog(self);
						progress.setIndeterminate(true);
						progress.setMessage(getString(R.string.please_wait));
						progress.setCancelable(true);
						progress.show();
						new AsyncTask<Void,Void,String>(){

							@Override
							protected String doInBackground(Void... params) {
								getContentResolver().delete(ImgurHistory.meta.uri,null,null);
								return null;
							}

							@Override
							protected void onPostExecute(String result) {
								if(isFinishing()) return;
								progress.dismiss();
								Toast.makeText(self,R.string.history_cleared,Toast.LENGTH_SHORT).show();
							}
							
						}.execute();
					}
				})
				.show();
				return true;
			}
		});
	}
}
