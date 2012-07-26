package jp.juggler.ImgurMush.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import jp.juggler.ImgurMush.Config;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurHistory;
import jp.juggler.ImgurMush.data.ProgressHTTPEntity;
import jp.juggler.ImgurMush.data.SignedClient;
import jp.juggler.ImgurMush.data.StreamSigner;
import jp.juggler.util.CancelChecker;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.widget.Toast;

public class Uploader {
	final BaseActivity act;
	final Callback callback;

	public interface Callback{
		void onStatusChanged(boolean bBusy);
		void onCancelled();
		void onComplete(String image_url,String page_url);
	}
	
	public Uploader(BaseActivity act,Callback callback){
		this.act =act;
		this.callback = callback;

	}
	
    ProgressDialog progress_dialog;
    
    public void image_upload(final ImgurAccount account,final String album_id,final String file_path){

		callback.onStatusChanged(true);

		final ProgressDialog dialog = progress_dialog = new ProgressDialog(act);
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setMessage(act.getString(R.string.upload_progress));
		dialog.setCancelable(true);
    	dialog.setMax(0);
    	dialog.setProgress(0);
    	dialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				callback.onCancelled();
			}
		});
    	act.dialog_manager.show_dialog(dialog);
    	
    	final CancelChecker cancel_checker = new CancelChecker() {
			@Override
			public boolean isCancelled() {
				return ! dialog.isShowing();
			}
		}; 
    	
		new AsyncTask<Void,Void,JSONObject>(){
			@Override
			protected JSONObject doInBackground(Void... params) {
				File infile = new File(file_path);
				long infile_size = infile.length();
				if( infile_size >= 10* 1024 * 1024 ){
	    			act.show_toast(Toast.LENGTH_SHORT,R.string.too_large_data);
	    			return null;
	    		}
				final boolean is_base64 = true; // oAuthのPercentEscapeルールだと、Base64した方が小さい
				try{
					SignedClient client = new SignedClient();
		    		StreamSigner signer = new StreamSigner();
		    		client.cancel_checker = cancel_checker;
		    		signer.cancel_checker = cancel_checker;
	    			signer.addParam(true,"type",(is_base64 ?"base64" : "file") );
	    			signer.addParam(true,"image",infile,is_base64);

		    		if( account==null ){
		    			signer.addParam(true,"key","83003560cf25f217ba03ccfcbf603471");
		    			HttpPost request = new HttpPost("http://api.imgur.com/2/upload.json");
		    			
		    			act.ui_handler.post(new Runnable() {
							@Override
							public void run() {
								if(act.isFinishing() ) return;
								dialog.setMessage(act.getString(R.string.upload_progress_sizecheck));
								dialog.setIndeterminate(true);
							}
						});
			    		request.setEntity(new ProgressHTTPEntity(signer.createPostEntity(),progress_listener));
		    			if( cancel_checker.isCancelled() ) return null; 

			    		progress_busy = false;
			    		act.ui_handler.post(new Runnable() {
							@Override
							public void run() {
								if(act.isFinishing() ) return;
								dialog.setMessage(act.getString(R.string.upload_progress));
								dialog.setIndeterminate(false);
							}
						});
			    		JSONObject result = client.json_request(request);
		    			if( cancel_checker.isCancelled() ) return null; 
		    			
			    		client.error_report(act,result);
			    		return result;
			    	}else{
			    		HttpPost request = new HttpPost("http://api.imgur.com/2/account/images.json");
			    		signer.addParam(false,"oauth_token", account.token);
			    		signer.addParam(false,"oauth_consumer_key", Config.CONSUMER_KEY );
			    		signer.addParam(false,"oauth_version","1.0");
			    		signer.addParam(false,"oauth_signature_method","HMAC-SHA1");
			    		signer.addParam(false,"oauth_timestamp",Long.toString(System.currentTimeMillis() / 1000L));
			    		signer.addParam(false,"oauth_nonce",Long.toString(new Random().nextLong()));
			    		
			    		act.ui_handler.post(new Runnable() {
							@Override
							public void run() {
								dialog.setMessage(act.getString(R.string.upload_progress_digest));
								dialog.setIndeterminate(true);
							}
						});
			    		signer.sign_header(  request,signer.hmac_sha1(Config.CONSUMER_SECRET,account.secret,"POST",request.getURI().toString()));
			    		if( cancel_checker.isCancelled() ) return null; 
			    		
			    		act.ui_handler.post(new Runnable() {
							@Override
							public void run() {
								dialog.setMessage(act.getString(R.string.upload_progress_sizecheck));
								dialog.setIndeterminate(true);
							}
						});
			    		request.setEntity(new ProgressHTTPEntity(signer.createPostEntity(),progress_listener));
			    		if( cancel_checker.isCancelled() ) return null; 

			    		progress_busy = false;
			    		act.ui_handler.post(new Runnable() {
							@Override
							public void run() {
								dialog.setMessage(act.getString(R.string.upload_progress));
								dialog.setIndeterminate(false);
							}
						});
			    		JSONObject result = client.json_request(request); 
			    		if( cancel_checker.isCancelled() ) return null; 

			    		client.error_report(act,result);
			    		
			    		if( result != null && album_id != null ){
			    			try{
			    				JSONObject image = result.getJSONObject("images").getJSONObject("image");
				    			request = new HttpPost("http://api.imgur.com/2/account/albums/"+ album_id +".json");
					    		request.setHeader("Content-Type", "application/x-www-form-urlencoded");
					    		ArrayList<NameValuePair> nameValuePair = new ArrayList<NameValuePair>();
					    		nameValuePair.add(new BasicNameValuePair("add_images",image.getString("hash")));
					    		request.setEntity(new UrlEncodedFormEntity(nameValuePair));
			    				client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
					    		client.consumer.sign(request);
					    		JSONObject r2 = client.json_request(request);
					    		client.error_report(act,r2);
			    			}catch(Throwable ex){
			    				act.report_ex(ex);
			    			}
			    		}
			    		
			    		return result;
			    	}
				}catch(Throwable ex){
					act.report_ex(ex);
				}
				return null;
			}

			@Override
			protected void onPostExecute(JSONObject result) {
				if(act.isFinishing()) return;
				dialog.dismiss();
				callback.onStatusChanged(false);
				try{
					if(result != null ){
						if( result.has("upload") ){
							JSONObject links=result.getJSONObject("upload").getJSONObject("links");
							save_history(links,account,album_id );
							callback.onComplete(links.getString("original"),links.getString("imgur_page"));
						}
						if( result.has("images") ){
							JSONObject links=result.getJSONObject("images").getJSONObject("links");
							save_history(links,account,album_id );
							callback.onComplete(links.getString("original"),links.getString("imgur_page"));
						}
					}
				}catch(Throwable ex){
					act.report_ex(ex);
				}
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
	    	item.save(act.cr);
    	}catch(Throwable ex){
    		act.report_ex(ex);
    	}
    }
    
    boolean progress_busy = false;
    AtomicInteger progress_value = new AtomicInteger(0);
    AtomicInteger progress_max= new AtomicInteger(0);
    Runnable progress_runnable = new Runnable() {
		@Override
		public void run() {
			act.ui_handler.removeCallbacks(progress_runnable);
			if(act.isFinishing()) return;
			int v = progress_value.get();
			int max = progress_max.get();
			progress_dialog.setMax(max);
			progress_dialog.setProgress(v);
			if(max > 0 && v==max){
				progress_dialog.setMessage(act.getString(R.string.upload_wait_response));
			}
		}
	};

	ProgressHTTPEntity.ProgressListener progress_listener = new ProgressHTTPEntity.ProgressListener() {
		@Override
		public void onProgress(long v, long size) {
	    	if(progress_busy) return;
	    	progress_max.set( (int)size );
	    	progress_value.set( (int)v );
	    	act.ui_handler.postDelayed(progress_runnable,100);
		}
	};
	
	
	
    //////////////////////////////////////////////////////////////////

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    static final int tmp_size = 1024;
    static final byte[] write_tmp = new byte[tmp_size];
    static int write_tmp_p =0;
    static final void write_tmp_byte(FileOutputStream out,byte b) throws IOException{
    	if( write_tmp_p >= write_tmp.length ){
    		out.write(write_tmp);
    		write_tmp_p =0;
    	}
    	write_tmp[write_tmp_p++] = b;
    }
    static void write_escape(FileOutputStream out,byte[] data) throws IOException{
    	if(data==null || data.length==0) return;
    	for(int i=0,ie=data.length;i<ie;++i){
    		byte c = data[i];
    		if( c == (byte)'&' ){
    			write_tmp_byte(out,(byte)'%');
    			write_tmp_byte(out,(byte)'2');
    			write_tmp_byte(out,(byte)'6');
    			continue;
    		}
    		if( c == (byte)'=' ){
    			write_tmp_byte(out,(byte)'%');
    			write_tmp_byte(out,(byte)'3');
    			write_tmp_byte(out,(byte)'d');
    			continue;
    		}
    		if( c == (byte)'+' ){
    			write_tmp_byte(out,(byte)'%');
    			write_tmp_byte(out,(byte)'2');
    			write_tmp_byte(out,(byte)'b');
    			continue;
    		}
    		if( c == (byte)'%' ){
    			write_tmp_byte(out,(byte)'%');
    			write_tmp_byte(out,(byte)'2');
    			write_tmp_byte(out,(byte)'5');
    			continue;
    		}
    		write_tmp_byte(out,c);
    	}
    	if( write_tmp_p > 0 ){
    		out.write(write_tmp,0,write_tmp_p);
    		write_tmp_p = 0;
    	}
    }
    
    
}
