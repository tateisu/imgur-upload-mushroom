package jp.juggler.ImgurMush;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class ActivityBase extends Activity {
	Handler ui_handler;
	ActivityBase self = this;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ui_handler = new Handler();
	}
	
    void show_toast(final int length,final int resid){
    	ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing())return;
				Toast.makeText(self,resid,length).show();
			}
		});
    }
    void show_toast(final int length,final String text){
    	ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing())return;
				Toast.makeText(self,text,length).show();
			}
		});
    }
    
	void report_ex(Throwable ex){
		show_toast(Toast.LENGTH_LONG,String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage()));
		ex.printStackTrace();
	}
	
    SharedPreferences pref(){
    	return PreferenceManager.getDefaultSharedPreferences(self);
    }
	
}
