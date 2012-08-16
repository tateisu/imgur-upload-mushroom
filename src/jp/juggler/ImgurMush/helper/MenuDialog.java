package jp.juggler.ImgurMush.helper;

import jp.juggler.ImgurMush.ActAppInfo;
import jp.juggler.ImgurMush.ActHistory;
import jp.juggler.ImgurMush.ActPref;
import jp.juggler.ImgurMush.R;
import jp.juggler.util.HelperEnvUI;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

public class MenuDialog {

	public static final int REQ_FILEPICKER = 2;
	public static final int REQ_HISTORY = 3;
	public static final int REQ_PREF = 4;
	public static final int REQ_ARRANGE= 5;
	public static final int REQ_APPINFO = 6;
	public static final int REQ_CAPTURE = 7;
	
	public static final int FILE_FROM_PICK =1;
	public static final int FILE_FROM_EDIT =2;
	public static final int FILE_FROM_RESTORE =3;
	
	public static final void menu_dialog(final HelperEnvUI env) {
		env.show_dialog(
			new AlertDialog.Builder(env.context)
			.setCancelable(true)
			.setNegativeButton(R.string.cancel,null)
			.setItems(
				new String[]{
					env.getString(R.string.history),
					env.getString(R.string.setting),
					env.getString(R.string.usage),
					env.getString(R.string.about),
				},new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch(which){
						case 0:
							env.act.startActivityForResult(new Intent(env.context,ActHistory.class),REQ_HISTORY);
							break;
						case 1:
							env.act.startActivityForResult(new Intent(env.context,ActPref.class),REQ_PREF);
							break;
						case 2:
							try{
								Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://juggler.jp/tateisu/android/ImgurMush-usage/"));
								intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK );
								env.act.startActivity(intent);
							}catch(Throwable ex){
								ex.printStackTrace();
							}
							break;
						case 3:
							env.act.startActivityForResult(new Intent(env.context,ActAppInfo.class),REQ_APPINFO);
							break;
						}
					}
				}
			)
		);
	}

}
