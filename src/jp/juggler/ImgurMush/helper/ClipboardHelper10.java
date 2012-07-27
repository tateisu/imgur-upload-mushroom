package jp.juggler.ImgurMush.helper;

import android.content.Context;

public class ClipboardHelper10 {
	@SuppressWarnings("deprecation")
	public
	static void copyText(Context context,String text){
		android.text.ClipboardManager cm = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		cm.setText(text);
	}
}
