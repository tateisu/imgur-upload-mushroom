package jp.juggler.util;

import jp.juggler.ImgurMush.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
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

	public final boolean isFinishing() {
		return act.isFinishing();
	}
	
	public final View findViewById(int id){
		return act.findViewById(id);
	}
	
	public void dismiss(final Dialog d){
		if(!isUIThread()){
			handler.post(new Runnable() {
				@Override public void run() {
					dismiss(d);
				}
			});
		}
		dialog_manager.dismiss(d);
	}
	public void show_dialog(final Dialog d){
		if(!isUIThread()){
			handler.post(new Runnable() {
				@Override public void run() {
					show_dialog(d);
				}
			});
		}
		dialog_manager.show_dialog(d);
	}
	public void show_dialog(final AlertDialog.Builder builder){
		if(!isUIThread()){
			handler.post(new Runnable() {
				@Override public void run() {
					show_dialog(builder);
				}
			});
		}
		dialog_manager.show_dialog(builder.create());
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
			show_dialog(dialog);
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	public void confirm(String title,String message,boolean bCancellable, final Runnable proc_ok,final Runnable proc_cancel){
		AlertDialog.Builder b = new AlertDialog.Builder(context)
		.setCancelable(true)
		.setCancelable(bCancellable)
		.setOnCancelListener(new OnCancelListener() {
			@Override public void onCancel(DialogInterface dialog) {
				if(proc_cancel!=null) proc_cancel.run();
			}
		})
		.setNegativeButton(R.string.cancel,new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				if(proc_cancel!=null) proc_cancel.run();
			}
		})
		.setPositiveButton(R.string.ok,new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				if(proc_ok!=null) proc_ok.run();
			}
		});
		if(title != null ) b.setTitle(title);
		if(message != null)b.setMessage(message);
		show_dialog(b);
	}
	

}
