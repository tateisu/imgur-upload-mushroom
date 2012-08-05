package jp.juggler.util;

import jp.juggler.ImgurMush.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.view.LayoutInflater;
import android.view.View;

public class HelperEnvUI extends HelperEnv{
	public final Activity act;
	public final LifeCycleManager lifecycle_manager = new LifeCycleManager();
	public final LayoutInflater inflater;
	public final DialogManager dialog_manager;

	public HelperEnvUI(Activity act) {
		super(act);
		this.act = act;
		inflater = (LayoutInflater)act.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
		dialog_manager = new DialogManager(act);
	}

	public final void finish_with_message(final String msg){
		if( !isUIThread() ){
			handler.post(new Runnable() {
				@Override public void run() {
					finish_with_message(msg);
				}
			});
			return;
		}
		try{
			if(act.isFinishing()) return;
			//
			Dialog dialog =  new AlertDialog.Builder(context)
			.setCancelable(true)
			.setNegativeButton(R.string.close,null)
			.setMessage(msg)
			.create();
			//
			dialog.setOnDismissListener(new OnDismissListener() {
				@Override public void onDismiss(DialogInterface dialog) {
					act.finish();
				}
			});
			//
			dialog_manager.show_dialog(dialog);
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	public final boolean isFinishing() {
		return act.isFinishing();
	}
	
	public final View findViewById(int id){
		return act.findViewById(id);
	}
}
