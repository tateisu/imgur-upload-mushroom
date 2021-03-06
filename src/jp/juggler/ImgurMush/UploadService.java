/*
	アップロードを行うサービス。
	
	ユースケース1
		アップロード開始
		ユーザはそのまま待ってる
		端末が回転、Activityが再生成される
		Activity#onPause時に現在のジョブのIDを保存
		サービスはそのまま生きてる
		Activity#onResume時(実際にはサービスのバインドの遅延がある）
		Activityは再度サービスに接続してジョブIDからジョブを再取得、そのまま進捗を表示する
		アップロード完了、サービスはjobの状態を更新する
		UIは進捗確認の際に完了を検出、終了処理を行う
		
	ユースケース2
		アップロード開始
		ユーザは端末を手動スクリーンオフ
		サービスはそのまま動く(1.6ではすぐにkillされる恐れあり)
		アップロード中はWiFiLockとPartial Wake lock ,startForegroundをとっておく
		アップロード完了、サービスはjobの状態を更新する
		サービスはjobを永続化する
		取得したロックは破棄する
		サービスとActivityのプロセスは破棄される
		Activityが再生成された際にジョブのIDをリストア
		サービスは起動時にjobの状態をリストア
		Activityは進捗確認を正常に行える
		UIは進捗確認の際に完了を検出、終了処理を行う
	
	ユースケース3 (複数のアップロードジョブ)
		異常な量の画像をアップロード開始
		ホームキーを押して再度画像選択アプリを開く。
		この時画像選択アプリのスタックがどうなるかは外部依存。
		画層選択アプリ / Imgurマッシュ / 画層選択アプリ / Imgurマッシュ /
		という深いネストになる可能性がある
		また、マルチタスクなので別のタスクからImgurマッシュを開く場合もある。
		- タスクA 画層選択アプリ / Imgurマッシュ
		- タスクB ファイル選択アプリ  / Imgurマッシュ
		- タスクC Imgurマッシュ
		
		で、内部的には複数のアップロードジョブを処理することは可能だが、
		完了したジョブをいつ破棄してよいかが曖昧になる。
		そこで他のタスクでアップロードが処理中の場合にはダイアログを出して
		前のタスクを中断するか、自分がアップロードを諦めるか選べるようにした。
		前のタスクを中断するとそこでアップロードジョブはキャンセルされて中止されて破棄される
		中断時点でのジョブ要素への参照は残るが、サービス側が管理しているリストからは除かれる
		
	ユースケース4 キャンセルしてダイアログを閉じた後
		ダイアログをキャンセル
		ダイアログは閉じられる
		進捗チェックも行われなくなる
		サービスはその後でジョブを中止する
		中止されたジョブのデータがサービスに溜まる
*/

package jp.juggler.ImgurMush;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import jp.juggler.ImgurMush.data.APIResult;
import jp.juggler.ImgurMush.data.ImgurHistory;
import jp.juggler.ImgurMush.data.ProgressHTTPEntity;
import jp.juggler.ImgurMush.data.SignedClient;
import jp.juggler.ImgurMush.data.StreamSigner;
import jp.juggler.ImgurMush.uploader.UploadJob;
import jp.juggler.ImgurMush.uploader.UploadJob.UploadUnit;
import jp.juggler.util.CancelChecker;
import jp.juggler.util.HelperEnv;
import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.SparseArray;

public class UploadService extends Service{
	static final LogCategory log = new LogCategory("UploadService");

	@Override public void onCreate() {
		log.d("onCreate");
		super.onCreate();

		env = new HelperEnv(this);

		initCompatibleMethod();
		
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		wake_lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"imgur uploader");
		
		WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		wifi_lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL,"imgur uploader");
		
		cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);

		// ワーカースレッドから設定されたコールバックをキューから除去
		env.handler.removeCallbacks(lock_enable);
		env.handler.removeCallbacks(lock_disable);
		env.handler.removeCallbacks(joblist_save);
		
		joblist_load();

		upload_thread = new UploadThread();
		upload_thread.start();
	}

	@Override
	public void onDestroy() {
		log.d("onDestroy");
		assert_ui_thread();
		super.onDestroy();
		
		// アップロード用スレッドを停止
		upload_thread.joinLoop(log,"upload_thread");

		// ワーカースレッドから設定されたコールバックをキューから除去
		env.handler.removeCallbacks(lock_enable);
		env.handler.removeCallbacks(lock_disable);
		env.handler.removeCallbacks(joblist_save);
		
		// lockを除去して、データのセーブも行う
		lock_disable.run();
	}

	@Override
	public IBinder onBind(Intent intent) {
		log.d("onBind");
		assert_ui_thread();
		bind_flag = true; // 1つでもクライアントがbindしたらtrue
		return binder_instance;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		log.d("onUnbind");
		//	全てのクライアントがunbindした
		bind_flag = false;
		checkExit();
		return false;
	}

	//////////////////////////////////////

	private NotificationManager mNM;
	private Method mSetForeground;
	private Method mStartForeground;
	private Method mStopForeground;

	void initCompatibleMethod(){
		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		try {
			mStartForeground = getClass().getMethod("startForeground",int.class,Notification.class);
			mStopForeground = getClass().getMethod("stopForeground",boolean.class);
		}catch(Throwable ex){
			mStartForeground = mStopForeground = null;
			try {
				mSetForeground = getClass().getMethod("setForeground",boolean.class);
			} catch (Throwable ex2){
				ex.printStackTrace();
				ex2.printStackTrace();
			}
		}
	}

	void startForegroundCompat(int id, Notification notification) {
		try {
			if (mStartForeground != null) {
				mStartForeground.invoke(this,id,notification);
			}else{
				mSetForeground.invoke(this,true);
				mNM.notify(id, notification);
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	void stopForegroundCompat(int id) {
		try{
			if (mStopForeground != null) {
				mStopForeground.invoke(this,true);
			}else{
				mNM.cancel(id);
				mSetForeground.invoke(this,false);
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}
	
	//////////////////////////////////////
	// 同一プロセス内でのバインド

	private final IBinder binder_instance = new LocalBinder();
	public class LocalBinder extends Binder{
		public UploadService getService(){
			return UploadService.this;
		}
	}


	HelperEnv env;
	
	public boolean isUIThread(){
		 return Thread.currentThread().equals(getMainLooper().getThread());
	}
	
	void assert_ui_thread(){
		if( !isUIThread() ) throw new RuntimeException("call from incorrect thread");
	}
	
	ConnectivityManager cm;
	boolean check_connection_state(){
		try{
			NetworkInfo info = cm.getActiveNetworkInfo();
			return info != null && info.isConnected();
		}catch(Throwable ex){
			ex.printStackTrace();
			return false;
		}
	}
	
	PowerManager.WakeLock wake_lock;
	WifiManager.WifiLock wifi_lock;
	
	static final int notification_id = 1;
	boolean is_foreground = false;
	boolean bind_flag = false;
	
	void checkExit(){
		log.d("bind=%s,foreground=%s",bind_flag,is_foreground);
		if(!bind_flag && !is_foreground ){
			log.d("not bind,not foreground, stop self...");
			stopSelf();
		}
	}
	
	Runnable lock_enable = new Runnable() {
		
		@SuppressWarnings("deprecation")
		@Override
		public void run() {
			//
			try{
				if( !wake_lock.isHeld() ){
					wake_lock.acquire();
					log.d("wake_lock acquire. isHeld=%s",wake_lock.isHeld());
				}
			}catch (Throwable ex) {
				ex.printStackTrace();
			}
			//
			try{
				if( !wifi_lock.isHeld() ){
					wifi_lock.acquire();
					log.d("wifi_lock acquire. isHeld=%s",wifi_lock.isHeld());
				}
			}catch (Throwable ex) {
				ex.printStackTrace();
			}
			//
			if( !is_foreground){
				is_foreground = true;
				PendingIntent pi = PendingIntent.getService(env.context,0,new Intent(env.context,UploadService.class),0);
				Notification notification = new Notification(R.drawable.icon,env.getString(R.string.upload_notification_ticker),System.currentTimeMillis());
				notification.setLatestEventInfo(env.context,env.getString(R.string.app_name),env.getString(R.string.upload_notification_message),pi);
				notification.flags |= Notification.FLAG_ONGOING_EVENT;
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ){
					notification.icon = R.drawable.icon_status_v4;
				}
				startForegroundCompat(notification_id,notification);
			}
			//
			joblist_save.run();
		}
	};
	Runnable lock_disable = new Runnable() {
		@Override public void run() {
			joblist_save.run();
			//
			try{
				while( wake_lock.isHeld() ){
					wake_lock.release();
					log.d("wake_lock release. isHeld=%s",wake_lock.isHeld());
				}
			}catch (Throwable ex) {
				ex.printStackTrace();
			}
			//
			try{
				while( wifi_lock.isHeld() ){
					wifi_lock.release();
					log.d("wifi_lock release. isHeld=%s",wifi_lock.isHeld());
				}
			}catch (Throwable ex) {
				ex.printStackTrace();
			}
			//
			if( is_foreground ){
				is_foreground = false;
				stopForegroundCompat(notification_id);
			}
		}
	};

	
	////////////////////////////////////////
	// ジョブの管理 UIスレッドから呼ばれる？
	
	final SparseArray<UploadJob> job_list = new SparseArray<UploadJob>();
	final Random random = new Random();

	// IDから
	public UploadJob findJobById(int id) {
		synchronized (job_list) {
			return job_list.get(id);
		}
	}

	// サービスにジョブを登録してアップロードを開始させる
	// 登録されたジョブにはidが発行される
	public int addJob(UploadJob job) {
		log.d("addJob");
		assert_ui_thread();

		// フォアグラウンド化
		lock_enable.run();
		
		// ジョブの進捗表示を初期化
		job.progress_title.set(env.getString(R.string.upload_pending_title));
		job.progress_message.set(env.getString(R.string.upload_pending_message));
		job.progress_var.set(0);
		job.progress_max.set(1);
		
		log.d("job add: account=%s,album_id=%s",(job.account_name==null?"OFF":"ON"),(job.album_id==null?"OFF":"ON"));
		
		// リストにジョブを追加
		synchronized(job_list){
			for(int id=1;;++id){
				if( findJobById(id) != null ) continue;
				job.job_id = id;
				break;
			}
			job_list.put(job.job_id,job);
			joblist_save.run();
		}
		// アップロード開始
		upload_thread.notifyEx();
		return job.job_id;
	}

	// ジョブをキャンセルする
	public void cancelRequest(int job_id) {
		cancelRequest(findJobById(job_id));
	}
	public void cancelRequest(UploadJob job) {
		log.d("cancelJob");
		assert_ui_thread();

		if( job == null ) return;
		job.cancel_request.set(true);
		synchronized (job_list) {
			if( attach_job_id != job.job_id ){
				job.append_error_message( env.getString(R.string.upload_cancelled));
			}
			joblist_save.run();
		}
		upload_thread.notifyEx();
	}

	// ユーザへの結果通知が終わった際に呼ばれる。ジョブはもう参照されないので削除してよい。
	public void expireJob(int job_id,String msg) {
		log.d("expireJob id=%s,msg=%s",job_id,msg);
		assert_ui_thread();
		synchronized (job_list) {
			UploadJob job = findJobById(job_id);
			job.cancel_request.set(true);
			if( msg != null ) job.append_error_message(msg);
			job_list.delete(job_id);
			joblist_save.run();
		}
		upload_thread.notifyEx();
	}
	
	// ジョブの進捗情報を更新する
	public UploadJob updateJobProgress(int job_id) {
		assert_ui_thread();
		return findJobById(job_id);
	}

	
	UploadThread upload_thread;
	int attach_job_id = -1;
	
	class UploadThread extends WorkerBase {
		AtomicBoolean bCancelled = new AtomicBoolean(false);
		
		@Override
		public void cancel() {
			bCancelled.set(true);
			notifyEx();
		}

		@Override
		public void run() {
			log.d("uploader thread start.");
			while(!bCancelled.get()){

				UploadJob job = null;
				int job_count;
				synchronized (job_list) {
					job_count = job_list.size();
					for(int i=0;i<job_count;++i){
						UploadJob it = job_list.valueAt(i);
						if( it.completed.get() || it.isAborted() ){
							continue;
						}else if( it.cancel_request.get() ){
							// キャンセルされたがまだ中止されていないジョブは単に中止させる
							it.append_error_message(env.getString(R.string.upload_cancelled));
							continue;
						}else if(job == null){
							job = it;
						}
					}
					attach_job_id = (job==null ? -1 : job.job_id);
				}
				
				if( job == null ){
					log.d("no jobs to upload. sleep. // joblist size=%s",job_count);
					env.handler.post(new Runnable() {
						@Override public void run() {
							lock_disable.run();
						}
					});
					waitEx();
					continue;
				}
				
				env.handler.post(new Runnable() {
					@Override public void run() {
						lock_enable.run();
					}
				});
				UploadUnit file = null;
				int file_count =job.file_list.size();
				int complete_count=0;
				int error_count =0;
				for( UploadUnit it : job.file_list ){
					if( it.error_message.get() != null ){
						++ error_count;
					}else if(it.text_output.get() != null ){
						++ complete_count;
					}else if( file == null ){
						file = it;
					}
				}
				if( file == null ){
					log.d("job %s is completed.",job.job_id);
					job.completed.set(true);
					continue;
				}
				log.d("upload job id=%s,count=%s // file complete=%s,error=%s,total=%s"
					,job.job_id
					,job_count
					,complete_count
					,error_count
					,file_count
				);

				String title = (file_count==1
					? env.getString(R.string.upload)
					: env.getString(R.string.upload_multi_title,(1+complete_count+error_count),file_count )
				);

				APIResult result = upload_one(job,file,title);

				synchronized(job_list){
					if(result ==null ){
						result = new APIResult(env.getString(R.string.upload_cancelled));
					}
					if( !result.isError() ){
						try{
							// resultをみてitemの状態を更新
							if( result.content_json.has("upload") ){
								JSONObject links=result.content_json.getJSONObject("upload").getJSONObject("links");
								save_history(links,job.account_name,job.album_id );
								file.setTextOutput(env.pref(),links.getString("original") , links.getString("imgur_page") );
							}else if( result.content_json.has("images") ){
								JSONObject links=result.content_json.getJSONObject("images").getJSONObject("links");
								save_history(links,job.account_name,job.album_id );
								file.setTextOutput(env.pref(),links.getString("original") , links.getString("imgur_page") );
							}
						}catch (Throwable ex) {
							result.setErrorExtra(env.format_ex(ex));
						}
					}
					String errmsg = result.getError();
					if( errmsg != null ){
						++error_count;
						file.error_message.set( errmsg );
						job.append_error_message( errmsg );
					}else{
						++complete_count;
						if( complete_count >= file_count ){
							job.completed.set(true);
						}
					}
				}
			}
			log.d("uploader thread end.");
		}
		


		APIResult upload_one(final UploadJob job,final UploadUnit item,String title){
			
			job.progress_title.set(title);
			job.progress_var.set(0);
			job.progress_max.set(1);

			// 入力ファイルのアクセス権を確認
			File infile = new File(item.src_path);
			if( !infile.isFile() ||!infile.canRead() || infile.length() <= 0L ){
				log.d("cannot access to src file");
				return new APIResult(env.getString(R.string.file_access_error,item.src_path));
			}
			
			final CancelChecker cancel_checker = new CancelChecker() {
				@Override public boolean isCancelled() {
					if( bCancelled.get() ){
						log.d("thread was cancelled.");
						return true;
					}else if( job.cancel_request.get() ){
						log.d("job was cancelled.");
						return true;
					}
					return false;
				}
			};
			//
			final AtomicBoolean progress_busy = new AtomicBoolean(true);
			final AtomicInteger progress_value_old = new AtomicInteger(-1);
			final AtomicInteger progress_try_count = new AtomicInteger(1);
			final AtomicReference<String> str_upload_progress = new AtomicReference<String>(env.getString(R.string.upload_progress));
			final String str_wait_response = env.getString(R.string.upload_wait_response);
			//
			final ProgressHTTPEntity.ProgressListener progress_listener = new ProgressHTTPEntity.ProgressListener() {
				@Override public void onProgress(long _v, long _size) {
					if(progress_busy.get()) return;
					int var = (int)_v;
					int size = (int)_size;
					//
					job.progress_var.set(var);
					job.progress_max.set(size);
					
					// リトライ検出
					if( var < progress_value_old.get() ){
						log.d("retry detected? %d < %d",var,progress_value_old.get());
						str_upload_progress.set( env.getString(R.string.upload_progress2,progress_try_count.incrementAndGet()));
					}
					progress_value_old.set(var);
					// メッセージ更新
					if( size > 0 && var==size ){
						job.progress_message.set(str_wait_response);
					}else{
						job.progress_message.set(str_upload_progress.get());
					}
				}
			};

			job.progress_message.set(env.getString(R.string.upload_network_waiting));
			while(!check_connection_state() ){
				if( cancel_checker.isCancelled() ){
					log.d("cancelled at check connection state");
					return null;
				}
				waitEx(111);
			}
			if( cancel_checker.isCancelled() ){
				log.d("cancelled at check connection state(2)");
				return null;
			}
	
			try{
				// oAuthのPercentEscapeルールだと、Base64した方が小さい
				final boolean is_base64 = true;
				final String cancel_message = env.getString(R.string.upload_cancelled);

				SignedClient client = new SignedClient(env);
				StreamSigner signer = new StreamSigner();
				client.cancel_checker = cancel_checker;
				signer.cancel_checker = cancel_checker;
				signer.addParam(true,"type",(is_base64 ?"base64" : "file") );
				signer.addParam(true,"image",infile,is_base64);

				APIResult result;

				if( job.account_name == null ){
					signer.addParam(true,"key",Config.IMGUR_API_KEY);
					HttpPost request = new HttpPost("http://api.imgur.com/2/upload.json");
					
					job.progress_message.set( env.getString(R.string.upload_progress_sizecheck) );
					ProgressHTTPEntity entity = new ProgressHTTPEntity(signer.createPostEntity(),progress_listener);
					request.setEntity(entity);

					if( cancel_checker.isCancelled() ){
						log.d("cancelled at upload as nanasi(1)");
						return null;
					}
					progress_busy.set(false);
					result = client.json_send_request(request,cancel_message,PrefKey.RATELIMIT_ANONYMOUS );
					result.save_error(env);
				}else{
					HttpPost request = new HttpPost("http://api.imgur.com/2/account/images.json");
					signer.addParam(false,"oauth_token", job.account_token);
					signer.addParam(false,"oauth_consumer_key", Config.CONSUMER_KEY );
					signer.addParam(false,"oauth_version","1.0");
					signer.addParam(false,"oauth_signature_method","HMAC-SHA1");
					signer.addParam(false,"oauth_timestamp",Long.toString(System.currentTimeMillis() / 1000L));
					signer.addParam(false,"oauth_nonce",Long.toString(new Random().nextLong()));

					job.progress_message.set( env.getString(R.string.upload_progress_digest));
					signer.sign_header(  request,signer.hmac_sha1(Config.CONSUMER_SECRET,job.account_secret,"POST",request.getURI().toString()));

					if( cancel_checker.isCancelled() ){
						log.d("cancelled at upload as account user(1)");
						return null;
					}
					job.progress_message.set( env.getString(R.string.upload_progress_sizecheck));
					ProgressHTTPEntity entity = new ProgressHTTPEntity(signer.createPostEntity(),progress_listener);
					request.setEntity(entity);

					if( cancel_checker.isCancelled() ){
						log.d("cancelled at upload as account user(2)");
						return null;
					}
					progress_busy.set(false);
					result = client.json_send_request(request,cancel_message,job.account_name);
					result.save_error(env);

					// 画像をアルバムに追加する
					if( ! result.isError() && job.album_id != null ){
						try{
							if( cancel_checker.isCancelled() ){
								log.d("cancelled at album post (1)");
								return null;
							}
							JSONObject image = result.content_json.getJSONObject("images").getJSONObject("image");
							request = new HttpPost("http://api.imgur.com/2/account/albums/"+ job.album_id +".json");
							request.setHeader("Content-Type", "application/x-www-form-urlencoded");
							ArrayList<NameValuePair> nameValuePair = new ArrayList<NameValuePair>();
							nameValuePair.add(new BasicNameValuePair("add_images",image.getString("hash")));
							request.setEntity(new UrlEncodedFormEntity(nameValuePair));
							client.prepareConsumer(job.account_token,job.account_secret,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
							client.consumer.sign(request);
							APIResult r2 = client.json_send_request(request,cancel_message,job.account_name);
							r2.save_error(env);
							if( r2.isError() ) result = r2;
						}catch(Throwable ex){
							log.d("error at album post");
							return new APIResult(env,ex);
						}
					}
				}
				if( cancel_checker.isCancelled() ){
					log.d("cancelled at end of upload");
					return null;
				}
				return result;
			}catch(Throwable ex){
				log.d("error at upload");
				return new APIResult(env,ex);
			}
		}
	}
	
	////////////////////////////////////////////////////////////////////////
	// ジョブデータの永続化

	private Runnable joblist_save = new Runnable(){
		@Override public void run() {
			try{
				synchronized(job_list){
					JSONArray array = new JSONArray();
					for(int i=0,ie=job_list.size();i<ie;++i){
						array.put(job_list.valueAt(i).encodeJSON());
					}
					String data = array.toString();
					
					SharedPreferences.Editor e = env.pref().edit();
					e.putString(PrefKey.KEY_UPLOAD_JOB_LIST,data);
					e.commit();
				}
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
	};

	static final long job_expire = 1000L * 86400 * 5;
	private void joblist_load() {
		try{
			synchronized(job_list){
				long now = System.currentTimeMillis();
				JSONArray array = new JSONArray(env.pref().getString(PrefKey.KEY_UPLOAD_JOB_LIST,"[]"));
				for(int i=0,ie=array.length();i<ie;++i){
					UploadJob job = new UploadJob(array.getJSONObject(i));
	
					// 中止or完了したジョブは一定期間でexpireする
					if( now - job.create_time > job_expire
					&& (job.isAborted() || job.completed.get() )
					){
						continue;
					}
	
					job_list.put(job.job_id,job);
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}
	

		
	private void save_history(JSONObject links, String account_name,String album_id) {
		try{
			ImgurHistory item = new ImgurHistory();
			item.image = links.getString("original");
			item.page = links.getString("imgur_page");
			item.delete = links.getString("delete_page");
			item.square = links.getString("small_square");
			item.upload_time = System.currentTimeMillis();
			item.account_name = (account_name==null ? null : account_name );
			item.album_id = album_id;
			item.save(env.cr);
		}catch(Throwable ex){
			env.report_ex(ex);
		}
	}

	public int getOtherTask( int ignore_task_id ){
		synchronized(job_list){
			if( job_list.size() > 0 ) return job_list.keyAt(0);
			return -1;
		}
	}


}
