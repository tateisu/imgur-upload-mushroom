package jp.juggler.ImgurMush.uploader;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import jp.juggler.ImgurMush.UploadService;
import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;

public class UploaderUI {
	static final LogCategory log = new LogCategory("UploaderUI");

	public interface Callback{
		void onStatusChange(boolean bBusy);

		void onTextOutput(String text_output);

		void onCancelled(String error_message);
		
	}
	


	public UploaderUI(HelperEnvUI env,Callback callback){
		this.env = env;
		this.callback = callback;
		
		// サービスに接続する
		try{
			upload_service = null;
			env.context.startService(new Intent(env.context, UploadService.class));
			env.context.bindService(new Intent(env.context, UploadService.class), mConnection,0);
		}catch(Throwable ex){
			ex.printStackTrace();
		}
		
		//
		env.lifecycle_manager.add(activity_listener);
	}
	
	final LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override public void onDestroy() {
			// サービスとの接続を外す
			try{
				upload_service = null;
				env.context.unbindService(mConnection);
			}catch(Throwable ex){
				log.e("unbind service failed: %s %s",ex.getClass().getSimpleName(),ex.getMessage());
			}
		}

		@Override public void onResume() {
			resume_flag = true;
			callback.onStatusChange(true);
			delay_resume.run();
		}

		@Override public void onPause() {
			resume_flag = false;
			callback.onStatusChange(true);
			save_dialog();
		}
	};
	
	final ServiceConnection mConnection = new ServiceConnection() {
	    @Override
		public void onServiceConnected(ComponentName className, IBinder service_binder) {
	    	log.d("service connected uiThread=%s",env.isUIThread());
	    	upload_service = ((UploadService.LocalBinder)service_binder).getService();
	    }

	    @Override
		public void onServiceDisconnected(ComponentName className) {
	    	log.d("service disconnected uiThread=%s",env.isUIThread());
	    }
	};

	////////////////////////////////////////////

	final HelperEnvUI env;
	final Callback callback;
	UploadService upload_service;
	boolean resume_flag;
	int tracking_job_id = -1;
	ProgressDialog dialog;
	String dialog_last_title;
	String dialog_last_message;
	boolean dialog_last_indeterminate;
	int dialog_last_progress;
	int dialog_last_max;

	final Runnable delay_resume = new Runnable() {
		@Override public void run() {
			// サービスがバインドされるのを待つ
			env.handler.removeCallbacks(delay_resume);
			if( upload_service == null ){
				env.handler.postDelayed(delay_resume,50);
				return;
			}
			// 待ってる間にresume-pauseの外側になってしまった
			if(!resume_flag) return;

			Intent intent = env.act.getIntent();
			// 追跡中のジョブを照合する
			tracking_job_id = intent.getIntExtra( "upload_job_id" ,-1);
			if( upload_service.findJobById(tracking_job_id) == null ) tracking_job_id = -1;
			
			restore_dialog();
		}
	};
	// ダイアログが出てるなら閉じる。ジョブIDは永続化する。
	void save_dialog(){
		if( tracking_job_id != -1 ){
			Intent intent = env.act.getIntent();
			intent.putExtra( "upload_job_id", tracking_job_id );
			env.act.setIntent(intent);
		}
		env.handler.removeCallbacks(progress_checker);
		if(dialog != null) dialog.dismiss();
	}
	
	// ユーザがアップロードボタンを押した
	public boolean upload_start(UploadJob new_job){
		// resume後かつサービスとバインド済みでないと何も起こさない。ユーザが適当に連打してくれるだろう
		if( !resume_flag || upload_service == null  ){
			return false;
		}

		// ジョブを登録
		try{
			tracking_job_id = upload_service.addJob(new_job);
		}catch(Throwable ex){
			ex.printStackTrace();
			env.show_toast(true,ex.getMessage());
			return false;
		}

		// ダイアログを復元
		restore_dialog();
		return true;
	}
	


	// ジョブが実行中ならダイアログを表示する
	void restore_dialog(){
		env.handler.removeCallbacks(progress_checker);
		if( dialog != null ) dialog.dismiss();
		if( tracking_job_id == -1 ){
			callback.onStatusChange(false);
			return;
		}

		callback.onStatusChange(true);
		dialog = new ProgressDialog(env.context);
		dialog_last_title=null;
		dialog_last_message=null;
		dialog_last_indeterminate=true;
		dialog_last_progress=0;
		dialog_last_max=0;
		dialog.setMax(dialog_last_max);
		dialog.setProgress(dialog_last_progress);
		dialog.setIndeterminate(dialog_last_indeterminate);
		dialog.setCancelable(true);
		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override public void onCancel(DialogInterface dialog) {
				upload_service.cancelRequest( tracking_job_id );
			}
		});
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override public void onDismiss(DialogInterface dialog) {
				progress_checker.run();
			}
		});
		 
		env.dialog_manager.show_dialog(dialog);
		progress_checker.run();
	}
	
	Runnable progress_checker = new Runnable() {
		@Override public void run() {
			env.handler.removeCallbacks(progress_checker);
			// 状況のチェック
			if( !resume_flag 
			|| upload_service == null 
			|| tracking_job_id == -1 
			|| dialog == null 
			|| !dialog.isShowing() 
			) return;
			
			// 進捗の確認
			final UploadJob job = upload_service.updateJobProgress(tracking_job_id);

			if( job.aborted.get() ){
				callback.onCancelled(job.getErrorMessage());
				upload_service.expireJob(job.job_id);
				tracking_job_id = -1;
				callback.onStatusChange(false);
				dialog.dismiss();
				return;
			}else if( job.completed.get() ){
				callback.onTextOutput(job.getTextOutput());
				upload_service.expireJob(job.job_id);
				tracking_job_id = -1;
				callback.onStatusChange(false);
				dialog.dismiss();
				return;
			}

			//
			String s;
			s = job.progress_title.get();
			if( s != null && !s.equals(dialog_last_title) ){
				dialog.setTitle(dialog_last_title = s );
			}
			//
			s = job.progress_message.get();
			if( s != null && !s.equals(dialog_last_message) ){
				dialog.setTitle(dialog_last_message = s );
			}
			//
			int max = job.progress_max.get();
			int var = job.progress_var.get();
			if( max >0 && max != dialog_last_max ){
				dialog.setMax(dialog_last_max = max );
			}
			//
			boolean bindeterminate = ( var < 0 || (max > 0 && var >= max));
			if( bindeterminate != dialog_last_indeterminate ){
				dialog.setIndeterminate( dialog_last_indeterminate = bindeterminate );
			}
			if(!bindeterminate){
				if( var > 0 && var != dialog_last_progress ){
					dialog.setProgress(dialog_last_progress = var );
				}
			}

			// 定期的に繰り返す
			env.handler.postDelayed(progress_checker,333);
		}
	};
}
