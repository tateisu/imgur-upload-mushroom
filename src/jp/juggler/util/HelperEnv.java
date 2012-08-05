package jp.juggler.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class HelperEnv {
	
	public final Context context;
	public final Handler handler;
	public final ContentResolver cr;
	public final float density;
	public final Resources resources;

	public HelperEnv(Context context){
		this.context = context;
		this.handler = new Handler();
		this.cr = context.getContentResolver();
		this.resources = context.getResources();
		this.density = this.resources.getDisplayMetrics().density;
	}
	
	public final boolean isUIThread(){
		 return Thread.currentThread().equals(context.getMainLooper().getThread());
	}
	
	public final void show_toast(final boolean bLong,final int resid,final Object... args){
		if( !isUIThread() ){
			handler.post(new Runnable() {
				@Override public void run() {
					show_toast(bLong,resid,args);
				}
			});
			return;
		}
		try{
			Toast.makeText(context,context.getString(resid,args),(bLong?Toast.LENGTH_LONG:Toast.LENGTH_SHORT)).show();
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}
	
	public final void show_toast(final boolean bLong,final String text){
		if( !isUIThread() ){
			handler.post(new Runnable() {
				@Override public void run() {
					show_toast(bLong,text);
				}
			});
			return;
		}
		try{
			Toast.makeText(context,text,(bLong?Toast.LENGTH_LONG:Toast.LENGTH_SHORT)).show();
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}
	
	public final String format_ex(Throwable ex){
		return String.format("%s: %s",ex.getClass().getSimpleName(),ex.getMessage());
	}

	public final void report_ex(Throwable ex){
		ex.printStackTrace();
		show_toast(true,format_ex(ex));
	}

	public final SharedPreferences pref(){
		return getPref(context);
	}

	public static final SharedPreferences getPref(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context);
	}

	public final String getString(int string_id) {
		return resources.getString(string_id);
	}

	public final String getString(int string_id,Object... args) {
		if( args == null || args.length <= 0 ){
			return resources.getString(string_id);
		}else{
			return resources.getString(string_id,args);
		}
	}
}
