package jp.juggler.ImgurMush.helper;

import android.os.Build;
import android.widget.Toast;

public class ClipboardHelper {
	
	// UIスレッドから呼び出すこと
	public static void clipboard_copy(BaseActivity act,String text,String ok_text) {
		String last_error;
		try{
			ClipboardHelper10.copyText(act,text);
			act.show_toast(Toast.LENGTH_SHORT,ok_text);
			return;
		}catch(Throwable ex){
			ex.printStackTrace();
			last_error = ex.getClass().getSimpleName()+":"+ex.getMessage();
		}
		if( Build.VERSION.SDK_INT >=11 ){
			try{
				ClipboardHelper11.copyText(act,text);
				act.show_toast(Toast.LENGTH_SHORT,ok_text);
				return;
			}catch(Throwable ex){
				ex.printStackTrace();
				last_error = ex.getClass().getSimpleName()+":"+ex.getMessage();
			}
		}
		act.show_toast(Toast.LENGTH_LONG,last_error);
	}
}
