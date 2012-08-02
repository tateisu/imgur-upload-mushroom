package jp.juggler.ImgurMush;

import java.util.regex.Pattern;

import jp.juggler.ImgurMush.data.APIResult;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.SignedClient;
import jp.juggler.ImgurMush.helper.BaseActivity;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.CookieManager;
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

	final ActOAuth act = this;
	String mOAuthVerifier;
	CommonsHttpOAuthProvider mOAuthProvider;
	CommonsHttpOAuthConsumer mOAuthConsumer;

	WebView mWebView;
	Button btnSiteTop;
	Button btnOAuthStart;

	private static final boolean validstr(String s){
		return s!= null && s.length() != 0;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_PROGRESS);

		setResult(RESULT_CANCELED);

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
				init();
			}
		});
		init();
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
			log.d("onPageStarted:"+url);
			check_oauth_callback(url);
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			log.d("shouldOverrideUrlLoading:"+url);
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
					new Thread(proc_after).start();
				} else {
					finish();
				}
			}
			return true;
		}
		return false;
	}

	void init(){
		// クッキーを破棄
		CookieManager cMgr = CookieManager.getInstance();
		cMgr.removeAllCookie();
		
		//
		mWebView.setWebViewClient(mWebViewClient);
		mWebView.setWebChromeClient(mWebChromeClient);
		WebSettings setting = mWebView.getSettings();
		setting.setJavaScriptEnabled(true);
		setting.setBuiltInZoomControls(true);
		new Thread(proc_before).start();
	}

	Pattern reCertificateError = Pattern.compile("Not trusted server certificate",Pattern.CASE_INSENSITIVE);
	
	private Runnable proc_before = new Runnable() {
		
		@Override
		public void run() {
			String url = null;
			String last_error = null;
			for(int nTry=0;nTry<10;++nTry){
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
					last_error = ex.getClass().getSimpleName()+": "+ex.getMessage();
				} catch (Throwable ex) {
					ex.printStackTrace();
					last_error = ex.getClass().getSimpleName()+": "+ex.getMessage();
				}
			}
			if( !validstr(url)  ) {
				final String errmsg = last_error;
				act.ui_handler.post(new Runnable() {
					@Override
					public void run() {
						if(act.isFinishing()) return;
						act.dialog_manager.show_dialog(new AlertDialog.Builder(act)
							.setCancelable(true)
							.setOnCancelListener(new OnCancelListener() {
								@Override public void onCancel(DialogInterface dialog) {
									finish();
								}
							})
							.setNegativeButton(R.string.close,new DialogInterface.OnClickListener() {
								@Override public void onClick(DialogInterface dialog, int which) {
									finish();
								}
							})
							.setMessage(errmsg)
						);
					}
				});
				return;
			}
			log.d("auth url=%s",url);
			final String url_ = url;
			act.ui_handler.post(new Runnable() {
				@Override
				public void run() {
					if(act.isFinishing()) return;
					mWebView.loadUrl(url_);
				}
			});
		}
	};

	private Runnable proc_after = new Runnable() {
		@Override
		public void run() {
			String cancel_message = act.getString(R.string.cancelled);
			
			try {
				mOAuthProvider.retrieveAccessToken(mOAuthConsumer, mOAuthVerifier);
				String token = mOAuthConsumer.getToken();
				String tokenSecret = mOAuthConsumer.getTokenSecret();
				if( validstr(token)
				&&  validstr(tokenSecret)
				){
					ImgurAccount account = new ImgurAccount();
					account.token = token;
					account.secret = tokenSecret;
					//
					SignedClient client = new SignedClient(act);
					client.prepareConsumer(account,Config.CONSUMER_KEY, Config.CONSUMER_SECRET);
					APIResult result = client.json_signed_get("http://api.imgur.com/2/account.json",cancel_message,"unknown");
					//
					if( !result.isError()
					&&   result.content_json.isNull("account")
					){
						result.setErrorExtra("missing 'account' in response");
					}
					result.save_error(act);
					result.show_error(act);
					if( result.isError() ) return;
					// 認証されたアカウントを保存する
					account.name = result.content_json.getJSONObject("account").getString("url");
					account.save(act.cr);
					show_toast(false,R.string.account_added);
				}
				finish();
			} catch (Throwable ex) {
				report_ex(ex);
			}
		}
	};

}
