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
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
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

	SparseBooleanArray pressed_key = new SparseBooleanArray();

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if( event.getAction() == KeyEvent.ACTION_DOWN ){
			switch(keyCode){
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
				pressed_key.put(keyCode,true);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if( event.getAction() == KeyEvent.ACTION_UP && pressed_key.get(keyCode) ){
			pressed_key.delete(keyCode);
			switch(keyCode){
			case KeyEvent.KEYCODE_BACK:
				procBackKey();
				return true;
			case KeyEvent.KEYCODE_MENU:
				procMenuKey();
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}
	
	protected void  procMenuKey() {
	}

	protected void  procBackKey() {
		finish();
	}
	
	//////////////////////////////////////////////////

	public boolean isUIThread(){
		 return Thread.currentThread().equals(getMainLooper().getThread());
	}
	
	public void show_toast(final int length,final int resid,final Object... args){
		if( isUIThread() ){
			Toast.makeText(BaseActivity.this,getString(resid,args),length).show();
			return;
		}
		ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing())return;
				Toast.makeText(BaseActivity.this,getString(resid,args),length).show();
			}
		});
	}
	public void show_toast(final int length,final String text){
		if( isUIThread() ){
			Toast.makeText(BaseActivity.this,text,length).show();
			return;
		}
		ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing())return;
				Toast.makeText(BaseActivity.this,text,length).show();
			}
		});
	}

	public void report_ex(Throwable ex){
		ex.printStackTrace();
		show_toast(Toast.LENGTH_LONG,String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage()));
	}

	public SharedPreferences pref(){
		return getPref(this);
	}

	public static SharedPreferences getPref(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context);
	}
}
