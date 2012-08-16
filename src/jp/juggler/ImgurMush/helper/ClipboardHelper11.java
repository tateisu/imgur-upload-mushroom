package jp.juggler.ImgurMush.helper;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

@TargetApi(11)
public class ClipboardHelper11 {
	public static void copyText(Context context,String text){
		ClipData cd = ClipData.newPlainText(text,text);
		ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		cm.setPrimaryClip(cd);
	}
	// 3.x 以降では getSystemServiceはUIスレッドから呼び出さないといけない場合があるようだ
}
