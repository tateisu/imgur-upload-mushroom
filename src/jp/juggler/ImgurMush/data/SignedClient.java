package jp.juggler.ImgurMush.data;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.UnknownHostException;

import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.util.CancelChecker;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import android.widget.Toast;

public class SignedClient {
	static final String TAG="SignedClient";
	static final boolean debug = false;
	
	public String last_error =null;
	public String last_status =null;
	public int last_rcode;
	public CommonsHttpOAuthConsumer consumer;
	public CancelChecker cancel_checker = null;
	
	
	public void error_report(final BaseActivity activity,JSONObject result){
		if(result == null){
			activity.show_toast(Toast.LENGTH_LONG,String.format("%s %s",last_status,last_error));
		}else if( result.has("error") ){
			try {
				activity.show_toast(Toast.LENGTH_LONG,result.getJSONObject("error").getString("message"));
			} catch (JSONException e) {
			}
		}
	}
	
	public void prepareConsumer(ImgurAccount account,String ck,String cs){
		consumer = new CommonsHttpOAuthConsumer(ck,cs);
		consumer.setTokenWithSecret(account.token,account.secret);
	}

	public JSONObject json_signed_get(String url){
		String str = null;
		try{
			HttpGet request = new HttpGet( url );
			consumer.sign(request);
			byte[] data = send_request(request);
			if(data!=null){
				str = new String(data,"UTF-8");
				if(debug) Log.d(TAG,str);
				return new JSONObject(str);
			}
		}catch(Throwable ex){
			ex.printStackTrace();
			last_error = ex.getClass().getSimpleName() +":"+ex.getMessage();
			if( str !=null) Log.e(TAG,str);
		}
		return null;
	}

	public JSONObject json_request(HttpRequestBase request){
		try{
			byte[] data = send_request(request);
			if(data!=null){
				String str = new String(data,"UTF-8");
				if(debug) Log.d(TAG,str);
				return new JSONObject(str);
			}
		}catch(Throwable ex){
			ex.printStackTrace();
			last_error = ex.getClass().getSimpleName() +":"+ex.getMessage();
		}
		return null;
	}
	
	private byte[] send_request(HttpRequestBase request){
		byte[] tmp = new byte[1024];
		DefaultHttpClient client=new DefaultHttpClient();
		
		for( Header header : request.getAllHeaders() ){
			if(debug) Log.d(TAG,String.format("%s %s",header.getName(), header.getValue() ));
		}
		
		for(int nTry=0;nTry<10;++nTry){
			if( cancel_checker != null && cancel_checker.isCancelled() ) break;
    		try{
	        	HttpResponse response = client.execute(request);
	        	last_rcode = response.getStatusLine().getStatusCode();
	        	last_status = response.getStatusLine().toString();
	        	if( last_rcode <0 ) continue;
	        	InputStream in=response.getEntity().getContent();
	        	try{
	        		ByteArrayOutputStream bao = new ByteArrayOutputStream();
	        		for(;;){
	        			int delta = in.read(tmp,0,tmp.length);
	        			if( delta < 0 ) break;
	        			bao.write(tmp,0,delta);
	        		}
	        		if( bao.size() > 0 ) return bao.toByteArray();
	        		Log.e(TAG,"read failed. status="+response.getStatusLine()+", url="+request.getURI());
	        	}finally{
	        		in.close();
	        	}
    		}catch(UnknownHostException ex){
        		last_error = ex.getClass().getSimpleName() +":"+ex.getMessage();
    			ex.printStackTrace();
    			break;
        	}catch(Throwable ex){
        		last_error = ex.getClass().getSimpleName() +":"+ex.getMessage();
        		ex.printStackTrace();
        	}
    	}
    	return null;
	}
}
