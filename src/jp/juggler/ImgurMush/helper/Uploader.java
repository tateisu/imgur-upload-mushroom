package jp.juggler.ImgurMush.helper;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import jp.juggler.ImgurMush.Config;
import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurHistory;
import jp.juggler.ImgurMush.data.ProgressHTTPEntity;
import jp.juggler.ImgurMush.data.SignedClient;
import jp.juggler.ImgurMush.data.APIResult;
import jp.juggler.ImgurMush.data.StreamSigner;
import jp.juggler.util.CancelChecker;
import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LogCategory;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class Uploader {
	static final LogCategory log = new LogCategory("Uploader");
	final HelperEnvUI env;
	final Callback callback;

	public interface Callback{
		void onStatusChanged(boolean bBusy);
		void onCancelled();
		void onComplete(String image_url,String page_url);
	}

	public Uploader(HelperEnvUI env,Callback callback){
		this.env =env;
		this.callback = callback;
		
		PowerManager pm = (PowerManager)env.context.getSystemService(Context.POWER_SERVICE);
		wake_lock = pm.newWakeLock(
			PowerManager.SCREEN_DIM_WAKE_LOCK
			|PowerManager.ON_AFTER_RELEASE
			,"ImgurMushroom"
		);
		wake_lock.setReferenceCounted(true);
	}

	ProgressDialog progress_dialog;
	WakeLock wake_lock;
	
	public void image_upload(final ImgurAccount account,final String album_id,final String file_path,String title){

		callback.onStatusChanged(true);
		progress_busy = true;
		
	
		//
		final ProgressDialog dialog = progress_dialog = new ProgressDialog(env.context);
		dialog.setTitle(title);
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setMessage(env.getString(R.string.upload_progress));
		dialog.setCancelable(true);
		dialog.setMax(0);
		dialog.setProgress(0);
		dialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				callback.onCancelled();
			}
		});
		dialog.setOnDismissListener(new OnDismissListener() {
			
			@Override
			public void onDismiss(DialogInterface dialog) {
				try{
					wake_lock.release();
					log.d("wake lock released. isHeld=%s",wake_lock.isHeld());
				}catch(Throwable ex){
					ex.printStackTrace();
				}
			}
		});
		//
		wake_lock.acquire();
		log.d("wake lock acquired. isHeld=%s",wake_lock.isHeld());

		env.dialog_manager.show_dialog(dialog);

		final CancelChecker cancel_checker = new CancelChecker() {
			@Override
			public boolean isCancelled() {
				return ! dialog.isShowing();
			}
		};

		new AsyncTask<Void,Void,APIResult>(){
			@Override
			protected APIResult doInBackground(Void... params) {
				File infile = new File(file_path);
				long infile_size = infile.length();
				if( infile_size >= 10* 1024 * 1024 ){
					env.show_toast(false,R.string.too_large_data);
					return null;
				}
				final boolean is_base64 = true; // oAuthのPercentEscapeルールだと、Base64した方が小さい
				try{
					SignedClient client = new SignedClient(env);
					StreamSigner signer = new StreamSigner();
					client.cancel_checker = cancel_checker;
					signer.cancel_checker = cancel_checker;
					signer.addParam(true,"type",(is_base64 ?"base64" : "file") );
					signer.addParam(true,"image",infile,is_base64);

					APIResult result;
					String cancel_message = env.getString(R.string.upload_cancelled);
					
					if( account==null ){
						signer.addParam(true,"key",Config.IMGUR_API_KEY);
						HttpPost request = new HttpPost("http://api.imgur.com/2/upload.json");

						progress_set_pre(R.string.upload_progress_sizecheck);
						ProgressHTTPEntity entity = new ProgressHTTPEntity(signer.createPostEntity(),progress_listener);
						request.setEntity(entity);

						if( cancel_checker.isCancelled() ) return null;
						progress_reset_retry( entity.getContentLength() );
						result = client.json_send_request(request,cancel_message,PrefKey.RATELIMIT_ANONYMOUS );
						result.save_error(env);
						result.show_error(env);
					}else{
						HttpPost request = new HttpPost("http://api.imgur.com/2/account/images.json");
						signer.addParam(false,"oauth_token", account.token);
						signer.addParam(false,"oauth_consumer_key", Config.CONSUMER_KEY );
						signer.addParam(false,"oauth_version","1.0");
						signer.addParam(false,"oauth_signature_method","HMAC-SHA1");
						signer.addParam(false,"oauth_timestamp",Long.toString(System.currentTimeMillis() / 1000L));
						signer.addParam(false,"oauth_nonce",Long.toString(new Random().nextLong()));

						progress_set_pre(R.string.upload_progress_digest);
						signer.sign_header(  request,signer.hmac_sha1(Config.CONSUMER_SECRET,account.secret,"POST",request.getURI().toString()));

						if( cancel_checker.isCancelled() ) return null;
						progress_set_pre(R.string.upload_progress_sizecheck);
						ProgressHTTPEntity entity = new ProgressHTTPEntity(signer.createPostEntity(),progress_listener);
						request.setEntity(entity);

						if( cancel_checker.isCancelled() ) return null;
						progress_reset_retry( entity.getContentLength() );
						result = client.json_send_request(request,cancel_message,account.name);
						result.save_error(env);
						result.show_error(env);

						// 画像をアルバムに追加する
						if( cancel_checker.isCancelled() ) return null;
						if( ! result.isError() && album_id != null ){
							try{
								JSONObject image = result.content_json.getJSONObject("images").getJSONObject("image");
								request = new HttpPost("http://api.imgur.com/2/account/albums/"+ album_id +".json");
								request.setHeader("Content-Type", "application/x-www-form-urlencoded");
								ArrayList<NameValuePair> nameValuePair = new ArrayList<NameValuePair>();
								nameValuePair.add(new BasicNameValuePair("add_images",image.getString("hash")));
								request.setEntity(new UrlEncodedFormEntity(nameValuePair));
								client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
								client.consumer.sign(request);
								APIResult r2 = client.json_send_request(request,cancel_message,account.name);
								r2.save_error(env);
								r2.show_error(env);
							}catch(Throwable ex){
								env.report_ex(ex);
							}
						}
					}
					if( cancel_checker.isCancelled() ) return null;
					return result;
				}catch(Throwable ex){
					env.report_ex(ex);
				}
				return null;
			}

			@Override
			protected void onPostExecute(APIResult result) {
				if(env.isFinishing()) return;
				dialog.dismiss();
				try{
					if( result != null && !result.isError() ){
						if( result.content_json.has("upload") ){
							JSONObject links=result.content_json.getJSONObject("upload").getJSONObject("links");
							save_history(links,account,album_id );
							callback.onComplete(links.getString("original"),links.getString("imgur_page"));
						}
						if( result.content_json.has("images") ){
							JSONObject links=result.content_json.getJSONObject("images").getJSONObject("links");
							save_history(links,account,album_id );
							callback.onComplete(links.getString("original"),links.getString("imgur_page"));
						}
					}
				}catch(Throwable ex){
					env.report_ex(ex);
				}
				callback.onStatusChanged(false);
			}
		}.execute();
	}



	void save_history(JSONObject links,ImgurAccount account,String album_id){
		try{
			ImgurHistory item = new ImgurHistory();
			item.image = links.getString("original");
			item.page = links.getString("imgur_page");
			item.delete = links.getString("delete_page");
			item.square = links.getString("small_square");
			item.upload_time = System.currentTimeMillis();
			item.account_name = (account==null ? null : account.name );
			item.album_id = album_id;
			item.save(env.cr);
		}catch(Throwable ex){
			env.report_ex(ex);
		}
	}

	boolean progress_busy = false;
	AtomicInteger progress_value = new AtomicInteger(0);
	AtomicInteger progress_max= new AtomicInteger(0);
	AtomicInteger progress_last_message_id = new AtomicInteger(0);
	int progress_try_count;
	String progress_last_msg;


	
	void progress_set_pre(final int text_id) {
		env.handler.post(new Runnable() {
			@Override
			public void run() {
				if(env.isFinishing() ) return;
				progress_dialog.setMessage(env.getString(text_id));
				progress_dialog.setIndeterminate(true);
			}
		});
	}
	
	private void progress_reset_retry(long size) {
		progress_busy = false;
		env.handler.post(new Runnable() {
			@Override
			public void run() {
				if(env.isFinishing() ) return;
				progress_last_message_id.set(0);
				progress_try_count = 1;
				progress_dialog.setProgress(0);
				progress_dialog.setIndeterminate(false);
			}
		});
	}
	
	ProgressHTTPEntity.ProgressListener progress_listener = new ProgressHTTPEntity.ProgressListener() {
		@Override
		public void onProgress(long v, long size) {
			if(progress_busy) return;
			progress_set( v, size );
		}
	};

	void progress_set(long v,long max){
		progress_value.set( (int)v );
		progress_max.set( (int)max );
		env.handler.postDelayed(progress_runnable,100);
	}
	
	Runnable progress_runnable = new Runnable() {
		@Override
		public void run() {
			env.handler.removeCallbacks(progress_runnable);
			if(env.isFinishing()) return;
			
			int old_v = progress_dialog.getProgress();
			int v = progress_value.get();
			int max = progress_max.get();
			progress_dialog.setMax(max);
			progress_dialog.setProgress(v);
			//
			if( v < old_v ){
				log.d("retry detected? %d < %d",v,old_v);
				++progress_try_count;
			}
			//
			String msg = null;
			if(max > 0 && v==max){
				msg = env.getString(R.string.upload_wait_response);
			}else if( progress_try_count > 1 ){
				msg = env.getString(R.string.upload_progress2,progress_try_count);
			}else{
				msg = env.getString(R.string.upload_progress);
			}
			if( ! msg.equals(progress_last_msg) ){
				progress_last_msg = msg;
				progress_dialog.setMessage(msg);
			}
		}
	};




	//////////////////////////////////////////////////////////////////

//	public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
//	static final int tmp_size = 1024;
//	static final byte[] write_tmp = new byte[tmp_size];
//	static int write_tmp_p =0;
//	static final void write_tmp_byte(FileOutputStream out,byte b) throws IOException{
//		if( write_tmp_p >= write_tmp.length ){
//			out.write(write_tmp);
//			write_tmp_p =0;
//		}
//		write_tmp[write_tmp_p++] = b;
//	}
//	static void write_escape(FileOutputStream out,byte[] data) throws IOException{
//		if(data==null || data.length==0) return;
//		for(int i=0,ie=data.length;i<ie;++i){
//			byte c = data[i];
//			if( c == (byte)'&' ){
//				write_tmp_byte(out,(byte)'%');
//				write_tmp_byte(out,(byte)'2');
//				write_tmp_byte(out,(byte)'6');
//				continue;
//			}
//			if( c == (byte)'=' ){
//				write_tmp_byte(out,(byte)'%');
//				write_tmp_byte(out,(byte)'3');
//				write_tmp_byte(out,(byte)'d');
//				continue;
//			}
//			if( c == (byte)'+' ){
//				write_tmp_byte(out,(byte)'%');
//				write_tmp_byte(out,(byte)'2');
//				write_tmp_byte(out,(byte)'b');
//				continue;
//			}
//			if( c == (byte)'%' ){
//				write_tmp_byte(out,(byte)'%');
//				write_tmp_byte(out,(byte)'2');
//				write_tmp_byte(out,(byte)'5');
//				continue;
//			}
//			write_tmp_byte(out,c);
//		}
//		if( write_tmp_p > 0 ){
//			out.write(write_tmp,0,write_tmp_p);
//			write_tmp_p = 0;
//		}
//	}


}
