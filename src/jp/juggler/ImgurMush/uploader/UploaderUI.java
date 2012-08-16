package jp.juggler.ImgurMush.uploader;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.UploadService;
import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;

public class UploaderUI {
	static final LogCategory log = new LogCategory("UploaderUI");

	public interface Callback{
		void onStatusChanged(boolean bBusy);

		void onTextOutput(String text_output);

		void onAbort(String error_message);
		
	}
	


	public UploaderUI(HelperEnvUI env,Callback callback){
		this.env = env;
		this.callback = callback;
		
		// サービスに接続する
		try{
			upload_service = null;
			ComponentName name = env.context.startService(new Intent(env.act, UploadService.class));
			log.d("start service result=%s",name);
			boolean bBind = env.context.bindService(new Intent(env.act, UploadService.class), mConnection,0);
			log.d("bind service result=%s",bBind);
		}catch(Throwable ex){
			ex.printStackTrace();
		}
		
		PowerManager pm = (PowerManager)env.context.getSystemService(Context.POWER_SERVICE);
		wake_lock = pm.newWakeLock(
			PowerManager.SCREEN_DIM_WAKE_LOCK
			|PowerManager.ON_AFTER_RELEASE
			,this.getClass().getName()
		);
		wake_lock.setReferenceCounted(true);
		
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
			// 追跡中のジョブを照合する
			Intent intent = env.act.getIntent();
			tracking_job_id = intent.getIntExtra( "upload_job_id" ,-1);
			// あとはサービスとのバインドを確認してから
			delay_resume.run();
		}



		@Override public void onPause() {
			resume_flag = false;
			setBusyState(true);
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
	WakeLock wake_lock;
	long dialog_open_time;
	
	final Runnable delay_resume = new Runnable() {
		@Override public void run() {
			setBusyState(true);

			// サービスがバインドされるのを待つ
			env.handler.removeCallbacks(delay_resume);
			if( upload_service == null ){
				//	log.d("not binded yet..");
				env.handler.postDelayed(delay_resume,50);
				return;
			}
			// 待ってる間にresume-pauseの外側になってしまった
			if(!resume_flag) return;

			// ジョブを復元する
			if( tracking_job_id != -1 ){
				if( upload_service.findJobById(tracking_job_id) == null ){
					log.d("seems job %s is cancelled by other task",tracking_job_id);
					env.show_toast(true,R.string.upload_cancel_by_other_task);
					tracking_job_id = -1;
				}else{
					log.d("restore: job id=%s",tracking_job_id);
				}
			}
			// ダイアログを復元する
			restore_dialog();
		}
	};
	// ダイアログが出てるなら閉じる。ジョブIDは永続化する。
	void save_dialog(){
		Intent intent = env.act.getIntent();
		intent.putExtra( "upload_job_id", tracking_job_id );
		env.act.setIntent(intent);
		//
		env.handler.removeCallbacks(progress_checker);
		env.dismiss(dialog);
	}
	
	// ユーザがアップロードボタンを押した
	public boolean upload(UploadJob new_job){
		// resume後かつサービスとバインド済みでないと何も起こさない。ユーザが適当に連打してくれるだろう
		if( !resume_flag || upload_service == null  ){
			return false;
		}

		// ジョブを登録
		try{
			new_job.task_id = env.act.getTaskId();
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
		env.dismiss(dialog);
		if( tracking_job_id == -1 ){
			setBusyState(false);
			final int job_id = upload_service.getOtherTask( env.act.getTaskId() );
			if( job_id != -1 ){
				env.confirm(
					null
					,env.getString(R.string.other_task_detected)
					,false
					,new Runnable() {
						@Override public void run() {
							upload_service.expireJob(job_id,env.getString(R.string.upload_cancel_by_other_task));
							restore_dialog();
						}
					}
					,new Runnable() {
						@Override public void run() {
							env.act.finish();
						}
					}
				);
			}
			return;
		}else{
			setBusyState(true);
			// fall
		}

		dialog = new ProgressDialog(env.context);
		dialog_last_title=" ";
		dialog_last_message=" ";
		dialog_last_indeterminate=true;
		dialog_last_progress=0;
		dialog_last_max=1;
		dialog_open_time = SystemClock.uptimeMillis();
		
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setTitle(dialog_last_title);
		dialog.setMessage(dialog_last_message);
		dialog.setMax(dialog_last_max);
		dialog.setProgress(dialog_last_progress);
		dialog.setIndeterminate(dialog_last_indeterminate);
		dialog.setCancelable(true);
		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override public void onCancel(DialogInterface dialog) {
				String msg = env.getString(R.string.upload_cancelled);
				callback.onAbort(msg);
				upload_service.expireJob(tracking_job_id ,msg);
				tracking_job_id = -1;
				//
			}
		});
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override public void onDismiss(DialogInterface dialog) {
				try{
					wake_lock.release();
					log.d("wake lock released. isHeld=%s",wake_lock.isHeld());
				}catch(Throwable ex){
					ex.printStackTrace();
				}
				
				progress_checker.run();
			}
		});
		//
		wake_lock.acquire();
		log.d("wake lock acquired. isHeld=%s",wake_lock.isHeld());
		//
		env.show_dialog(dialog);
		//
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

			if( job.isAborted() ){
				log.d("job aborted");
				callback.onAbort(job.getErrorMessage());
				upload_service.expireJob(job.job_id,null);
				tracking_job_id = -1;
				setBusyState(false);
				env.dismiss(dialog);
				return;
			}else if( job.completed.get() ){
				log.d("job completed");
				callback.onTextOutput(job.getTextOutput());
				upload_service.expireJob(job.job_id,null);
				tracking_job_id = -1;
				setBusyState(false);
				env.dismiss(dialog);
				return;
			}

			if( SystemClock.uptimeMillis() - dialog_open_time >= 0 ){
				//
				String s;
				s = job.progress_title.get();
				if( s != null && !s.equals(dialog_last_title) ){
					dialog.setTitle(dialog_last_title = s );
				}
				//
				s = job.progress_message.get();
				if( s != null && !s.equals(dialog_last_message) ){
					dialog.setMessage(dialog_last_message = s );
				}
				//
				int max = job.progress_max.get();
				int var = job.progress_var.get();
				
				// 先に IndeterminateをセットしないとProgressの変更が無視されるらしい
				boolean bindeterminate = ( max <=1 );
				if( bindeterminate != dialog_last_indeterminate ){
					dialog.setIndeterminate( dialog_last_indeterminate = bindeterminate );
				}
				if(!bindeterminate){
					if( max >=0 && max != dialog_last_max ){
						dialog.setMax(dialog_last_max = max );
					}
					if( var >=0 && var != dialog_last_progress ){
						dialog.setProgress(dialog_last_progress = var );
					}
				}
			}

			// 定期的に繰り返す
			env.handler.postDelayed(progress_checker,333);
		}
	};

	private boolean bBusy = false;
	private void setBusyState(boolean b) {
		boolean old = bBusy;
		bBusy = b;
		if( b!=old) callback.onStatusChanged(bBusy);
	}
	public boolean isBusy() {
		return bBusy;
	}
}
