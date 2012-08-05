package jp.juggler.ImgurMush;

import java.util.regex.Pattern;

import jp.juggler.ImgurMush.data.APIResult;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.SignedClient;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.util.CancelChecker;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

public class ActOAuth extends BaseActivity {
	static final String KEY_OAUTH_VERIFIER = "oauth_verifier";
	static final String URL_REQUEST_TOKEN = "https://api.imgur.com/oauth/request_token";
	static final String URL_ACCESS_TOKEN = "https://api.imgur.com/oauth/access_token";
	static final String URL_WEB_AUTHORIZATION = "https://api.imgur.com/oauth/authorize";
	static final String URL_LOGOUT = "http://imgur.com/logout";
	static final String URL_CALLBACK = "http://juggler.jp/android/ImgurMush/callback";
	static final Pattern reCertificateError = Pattern.compile("Not trusted server certificate",Pattern.CASE_INSENSITIVE);
	static final Pattern reCapacity = Pattern.compile("Service Unavailable",Pattern.CASE_INSENSITIVE);
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setResult(RESULT_CANCELED);
		
		try{
			CookieSyncManager.createInstance(act);
		}catch(Throwable ex){
			ex.printStackTrace();
		}

		initUI();
		init_page();
	}

	//////////////////////////////////////////////////////////////
	
	final ActOAuth act = this;
	String mOAuthVerifier;
	CommonsHttpOAuthProvider mOAuthProvider;
	CommonsHttpOAuthConsumer mOAuthConsumer;

	WebView mWebView;
	Button btnSiteTop;
	Button btnOAuthStart;
	
	void initUI(){
		requestWindowFeature(Window.FEATURE_PROGRESS);
		setContentView(R.layout.act_oauth);
		
		mWebView = (WebView)findViewById(R.id.webview);
		
		findViewById(R.id.btnSiteTop).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mWebView.loadUrl(URL_LOGOUT);
			}
		});
		findViewById(R.id.btnOAuthStart).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				init_page();
			}
		});
	}
	
	void init_page(){
		// クッキーを破棄
		try{
			CookieManager.getInstance().removeAllCookie();
		}catch(Throwable ex){
			ex.printStackTrace();
		}

		//
		mWebView.setWebViewClient(mWebViewClient);
		mWebView.setWebChromeClient(mWebChromeClient);
		WebSettings setting = mWebView.getSettings();
		setting.setJavaScriptEnabled(true);
		setting.setBuiltInZoomControls(true);
	
		proc_before();
	}
	
	private static final boolean validstr(String s){
		return s!= null && s.length() != 0;
	}

	private WebChromeClient mWebChromeClient = new WebChromeClient() {
		@Override public void onProgressChanged(WebView view, int newProgress) {
			super.onProgressChanged(view, newProgress);
			ActOAuth.this.setProgress(newProgress * 100);
		}
	};

	private WebViewClient mWebViewClient = new WebViewClient() {

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);
		//	log.d("onPageStarted:"+url);
			check_oauth_callback(url);
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
		//	log.d("shouldOverrideUrlLoading:"+url);
			if( check_oauth_callback(url) ) return true;
			return super.shouldOverrideUrlLoading(view, url);
		}

		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
			log.d(String.format("onReceivedError code=%d,desc=%s,url=%s",errorCode,description,failingUrl));
			super.onReceivedError(view, errorCode, description, failingUrl);
		}
	};

	boolean check_oauth_callback(String url){
		if( (url != null) && (url.startsWith(URL_CALLBACK)) ){
			mWebView.stopLoading();
			mWebView.setVisibility(View.INVISIBLE);
			if( mOAuthVerifier == null ){
				Uri uri = Uri.parse(url);
				mOAuthVerifier = uri.getQueryParameter(KEY_OAUTH_VERIFIER);
				if (mOAuthVerifier != null){
					proc_after();
					return true;
				}
			}
			env.finish_with_message("cannot get oAuth verifier");
			return true;
		}
		return false;
	}

	void proc_before(){
		final ProgressDialog dialog = new ProgressDialog(act);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setIndeterminate(true);
		dialog.setMessage(act.getString(R.string.please_wait));
		dialog.setCancelable(true);
		dialog.setMax(0);
		dialog.setProgress(0);
		dialog.show();
		dialog.setOnCancelListener(new OnCancelListener() {
			@Override public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		
		new Thread(){

			CancelChecker cancel_checker = new CancelChecker() {
				@Override public boolean isCancelled() {
					return !dialog.isShowing();
				}
			};

			@Override public void run() {
				try{
					String url = null;
					String last_error = null;
					for(int nTry=0;nTry<10;++nTry){
						if( cancel_checker.isCancelled() ) return;
						try {
							mOAuthConsumer = new CommonsHttpOAuthConsumer( Config.CONSUMER_KEY, Config.CONSUMER_SECRET);
							mOAuthProvider = new CommonsHttpOAuthProvider( URL_REQUEST_TOKEN, URL_ACCESS_TOKEN, URL_WEB_AUTHORIZATION);
							url = mOAuthProvider.retrieveRequestToken(mOAuthConsumer, URL_CALLBACK);

						}catch ( OAuthCommunicationException ex) {
							ex.printStackTrace();
							String msg = ex.getMessage();
							if( reCertificateError.matcher(msg).find() ){
								last_error = act.getString(R.string.oauth_error_server_certificate);
								break;
							}
							if( reCapacity.matcher(msg).find() ){
								last_error = act.getString(R.string.oauth_error_server_unavailable);
								break;
							}
							last_error = ex.getClass().getSimpleName()+": "+ex.getMessage();
						} catch (Throwable ex) {
							ex.printStackTrace();
							last_error = ex.getClass().getSimpleName()+": "+ex.getMessage();
						}
					}
					if( cancel_checker.isCancelled() ) return;
					//
					if( !validstr(url)  ) {
						env.finish_with_message(last_error);
						return;
					}else{
						final String url_ = url;
						env.handler.post(new Runnable() {
							@Override
							public void run() {
								if(act.isFinishing()) return;
								mWebView.loadUrl(url_);
							}
						});
					}
				}catch(Throwable ex) {
					env.report_ex(ex);
				}finally{
					env.handler.post(new Runnable() {
						@Override public void run() {
							dialog.dismiss();
						}
					});
				}
			}
		}.start();
	}

	void proc_after(){
		
		final ProgressDialog dialog = new ProgressDialog(act);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setIndeterminate(true);
		dialog.setMessage(act.getString(R.string.please_wait));
		dialog.setCancelable(true);
		dialog.setMax(0);
		dialog.setProgress(0);
		dialog.show();
		dialog.setOnCancelListener(new OnCancelListener() {
			@Override public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
	
		new Thread(){
			String cancel_message = act.getString(R.string.cancelled);

			CancelChecker cancel_checker = new CancelChecker() {
				@Override public boolean isCancelled() {
					return !dialog.isShowing();
				}
			};
			
			@Override public void run() {
				try {
					mOAuthProvider.retrieveAccessToken(mOAuthConsumer, mOAuthVerifier);
					if( cancel_checker.isCancelled() ) return;
					
					String token = mOAuthConsumer.getToken();
					String tokenSecret = mOAuthConsumer.getTokenSecret();
					if( ! validstr(token) || ! validstr(tokenSecret) ){
						env.finish_with_message(act.getString(R.string.oauth_error_missing_token));
						return;
					}

					// 次はアカウント情報の取得
					env.handler.post(new Runnable() {
						@Override public void run() {
							dialog.setMessage(act.getString(R.string.account_info_querying));
						}
					});
					
					ImgurAccount account = new ImgurAccount();
					account.token = token;
					account.secret = tokenSecret;
					//
					SignedClient client = new SignedClient(act.env);
					client.cancel_checker = cancel_checker;
					client.prepareConsumer(account,Config.CONSUMER_KEY, Config.CONSUMER_SECRET);
					APIResult result = client.json_signed_get("http://api.imgur.com/2/account.json",cancel_message,null);
					if( cancel_checker.isCancelled() ) return;
					//
					if( !result.isError() && result.content_json.isNull("account") ){
						result.setErrorExtra("missing 'account' in response");
					}
					result.save_error(act.env);
					result.show_error(act.env);
					if( result.isError() ) return;
					// 認証されたアカウントを保存する
					account.name = result.content_json.getJSONObject("account").getString("url");
					account.save(env.cr);
					log.d("account configured.");
					env.finish_with_message(act.getString(R.string.account_added));
				}catch(Throwable ex) {
					env.report_ex(ex);
				}finally{
					env.handler.post(new Runnable() {
						@Override public void run() {
							dialog.dismiss();
						}
					});
				}
			}
		}.start();
	}
}
