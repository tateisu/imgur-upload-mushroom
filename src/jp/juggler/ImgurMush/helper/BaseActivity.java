package jp.juggler.ImgurMush.helper;

import jp.juggler.util.DialogManager;
import jp.juggler.util.LifeCycleManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.widget.Toast;

public class BaseActivity extends Activity {

	public Handler ui_handler;
	public LayoutInflater inflater;
	public ContentResolver cr;
	public LifeCycleManager lifecycle_manager = new LifeCycleManager();
	public DialogManager dialog_manager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ui_handler = new Handler();
        inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        cr =getContentResolver();
        dialog_manager = new DialogManager(this);
	}
	
	//////////////////////////////////////////////////

	@Override protected void onDestroy() {
		super.onDestroy();
		dialog_manager.onDestroy();
		lifecycle_manager.fire_onDestroy();
	}

	@Override protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		lifecycle_manager.fire_onNewIntent();
	}

	@Override protected void onStart() {
		super.onStart();
		lifecycle_manager.fire_onStart();
	}

	@Override protected void onRestart() {
		super.onRestart();
		lifecycle_manager.fire_onRestart();
	}

	@Override protected void onStop() {
		super.onStop();
		lifecycle_manager.fire_onStop();
	}

	@Override protected void onResume() {
		super.onResume();
		lifecycle_manager.fire_onResume();
	}

	@Override protected void onPause() {
		super.onPause();
		lifecycle_manager.fire_onPause();
	}
	
	//////////////////////////////////////////////////
	
	public void show_toast(final int length,final int resid){
    	ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing())return;
				Toast.makeText(BaseActivity.this,resid,length).show();
			}
		});
    }
    public void show_toast(final int length,final String text){
    	ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing())return;
				Toast.makeText(BaseActivity.this,text,length).show();
			}
		});
    }
    
    public void report_ex(Throwable ex){
		show_toast(Toast.LENGTH_LONG,String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage()));
		ex.printStackTrace();
	}
	
    public SharedPreferences pref(){
    	return getPref(this);
    }

    public static SharedPreferences getPref(Context context){
    	return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
