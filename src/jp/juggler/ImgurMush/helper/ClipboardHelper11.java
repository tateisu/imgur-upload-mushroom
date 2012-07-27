package jp.juggler.ImgurMush.helper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public class ClipboardHelper11 {
	public void copyText(Context context,String text){
		ClipData cd = ClipData.newPlainText(text,text);
		ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		cm.setPrimaryClip(cd);
	}
}
