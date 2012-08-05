package jp.juggler.ImgurMush.helper;

import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;

public class BaseActivity extends Activity {
	public static final LogCategory log = new LogCategory("Activity");

	public HelperEnvUI env;
	private final SparseBooleanArray pressed_key = new SparseBooleanArray();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		env = new HelperEnvUI(this);
	}

	@Override protected void onDestroy() {
		super.onDestroy();
		env.dialog_manager.onDestroy();
		env.lifecycle_manager.fire_onDestroy();
	}

	@Override protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		env.lifecycle_manager.fire_onNewIntent();
	}

	@Override protected void onStart() {
		super.onStart();
		env.lifecycle_manager.fire_onStart();
	}

	@Override protected void onRestart() {
		super.onRestart();
		env.lifecycle_manager.fire_onRestart();
	}

	@Override protected void onStop() {
		super.onStop();
		env.lifecycle_manager.fire_onStop();
	}

	@Override protected void onResume() {
		super.onResume();
		env.lifecycle_manager.fire_onResume();
	}

	@Override protected void onPause() {
		super.onPause();
		env.lifecycle_manager.fire_onPause();
	}

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
	
}
