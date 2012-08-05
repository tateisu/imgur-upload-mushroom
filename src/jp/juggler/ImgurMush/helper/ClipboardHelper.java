package jp.juggler.ImgurMush.helper;

import jp.juggler.util.HelperEnv;
import android.os.Build;

public class ClipboardHelper {
	
	// UIスレッドから呼び出すこと
	public static void clipboard_copy(HelperEnv eh,String text,String ok_text) {
		String last_error;
		try{
			ClipboardHelper10.copyText(eh.context,text);
			eh.show_toast(false,ok_text);
			return;
		}catch(Throwable ex){
			ex.printStackTrace();
			last_error = ex.getClass().getSimpleName()+":"+ex.getMessage();
		}
		if( Build.VERSION.SDK_INT >=11 ){
			try{
				ClipboardHelper11.copyText(eh.context,text);
				eh.show_toast(false,ok_text);
				return;
			}catch(Throwable ex){
				ex.printStackTrace();
				last_error = ex.getClass().getSimpleName()+":"+ex.getMessage();
			}
		}
		eh.show_toast(true,last_error);
	}
}
