package jp.juggler.ImgurMush.data;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;

import jp.juggler.ImgurMush.R;
import jp.juggler.util.CancelChecker;
import jp.juggler.util.HelperEnv;
import jp.juggler.util.LogCategory;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

public class SignedClient {
	static final LogCategory log = new LogCategory("SignedClient");
	static final boolean dump_request_header = false;

	final HelperEnv eh;
	public CommonsHttpOAuthConsumer consumer;
	public CancelChecker cancel_checker = null;
	
	public SignedClient(HelperEnv eh){
		this.eh = eh;
	}
	public void prepareConsumer(ImgurAccount account,String ck,String cs){
		consumer = new CommonsHttpOAuthConsumer(ck,cs);
		consumer.setTokenWithSecret(account.token,account.secret);
	}
	public void prepareConsumer(String token,String secret,String ck,String cs){
		consumer = new CommonsHttpOAuthConsumer(ck,cs);
		consumer.setTokenWithSecret(token,secret);
	}
	


	private boolean send_request(APIResult result,HttpRequestBase request,String cancel_message){
		byte[] tmp = new byte[1024];
		DefaultHttpClient client=new DefaultHttpClient();

		if(dump_request_header){
			for( Header header : request.getAllHeaders() ){
				log.d(String.format("%s %s",header.getName(), header.getValue() ));
			}
		}

		result.content_bytes = null;
		result.content_utf8 = null;
		result.content_json = null;

		for(int nTry=0;nTry<10;++nTry){
			try{
				if( cancel_checker != null && cancel_checker.isCancelled() ) throw new CancellationException("cancelled.");
				HttpResponse response = client.execute(request);
				result.http_rcode = response.getStatusLine().getStatusCode();
				result.http_status = response.getStatusLine().toString();
				result.http_headers = response.getAllHeaders();
				if( result.http_rcode <0 ) continue;
				InputStream in=response.getEntity().getContent();
				try{
					ByteArrayOutputStream bao = new ByteArrayOutputStream();
					for(;;){
						int delta = in.read(tmp,0,tmp.length);
						if( delta < 0 ) break;
						bao.write(tmp,0,delta);
					}
					if( bao.size() > 0 ){
						result.setErrorHTTP(null);
						result.content_bytes = bao.toByteArray();
						return true;
					}
					log.e("read failed. status="+result.http_status+", url="+request.getURI());
				}finally{
					in.close();
				}
			}catch(CancellationException ex){
				result.setErrorHTTP(cancel_message);
				break;
			}catch(SocketTimeoutException ex){
				result.setErrorHTTP(eh.getString(R.string.net_error_timeout,ex.getMessage()));
				continue;
			}catch(UnknownHostException ex){
				result.setErrorHTTP(eh.getString(R.string.net_error_resolver,ex.getMessage()));
				continue;
			}catch(Throwable ex){
				ex.printStackTrace();
				result.setErrorHTTP(APIResult.format_ex(ex));
				continue;
			}
		}
		return false;
	}
	
	public APIResult json_get(String url,String cancel_message,String account_name){
		APIResult result = new APIResult();
		for( int nTry =0; nTry < 10; ++nTry ){
			try{
				HttpGet request = new HttpGet( url );
				send_request(result,request,cancel_message);
				if( result.parse_json(eh,account_name) ){
					break;
				}else{
					continue;
				}
			}catch(Throwable ex){
				ex.printStackTrace();
				result.setErrorExtra(APIResult.format_ex(ex));
				break;
			}
		}
		String v = result.getError();
		if( v != null ) log.e("json_get failed: %s",v);
		return result;
	}
	
	public APIResult json_signed_get(String url,String cancel_message,String account_name){
		APIResult result = new APIResult();
		for( int nTry =0; nTry < 10; ++nTry ){
			try{
				HttpGet request = new HttpGet( url );
				consumer.sign(request);
				send_request(result,request,cancel_message);
				if( result.parse_json(eh,account_name) ){
					break;
				}else{
					continue;
				}
			}catch(Throwable ex){
				ex.printStackTrace();
				result.setErrorExtra(APIResult.format_ex(ex));
				break;
			}
		}
		String v = result.getError();
		if( v != null ) log.e("json_signed_get failed: %s",v);
		return result;
	}

	public APIResult json_send_request(HttpRequestBase request,String cancel_message,String account_name){
		APIResult result = new APIResult();
		for( int nTry =0; nTry < 10; ++nTry ){
			try{
				send_request(result,request,cancel_message);
				if( result.parse_json(eh,account_name) ){
					break;
				}else{
					continue;
				}
			}catch(Throwable ex){
				ex.printStackTrace();
				result.setErrorExtra(APIResult.format_ex(ex));
				break;
			}
		}
		String v = result.getError();
		if( v != null ) log.e("json_signed_request failed: %s",v);
		return result;
	}





}
