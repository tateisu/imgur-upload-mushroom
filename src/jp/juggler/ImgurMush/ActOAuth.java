package jp.juggler.ImgurMush;

import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.SignedClient;
import jp.juggler.ImgurMush.helper.BaseActivity;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

public class ActOAuth extends BaseActivity {
	static final String TAG = "OAuthActivity";
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
        	Log.d(TAG,"onPageStarted:"+url);
        	check_oauth_callback(url);
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
         	Log.d(TAG,"shouldOverrideUrlLoading:"+url);
        	if( check_oauth_callback(url) ) return true;
			return super.shouldOverrideUrlLoading(view, url);
		}

		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
			Log.d(TAG,String.format("onReceivedError code=%d,desc=%s,url=%s",errorCode,description,failingUrl));
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

        mWebView.setWebViewClient(mWebViewClient);
        mWebView.setWebChromeClient(mWebChromeClient);
        WebSettings setting = mWebView.getSettings();
        setting.setJavaScriptEnabled(true);
        setting.setBuiltInZoomControls(true);
        new Thread(proc_before).start();
    }

    private Runnable proc_before = new Runnable() {
        @Override
        public void run() {
        	String url = null;
        	for(int nTry=0;nTry<10;++nTry){
	            try {
		            mOAuthConsumer = new CommonsHttpOAuthConsumer( Config.CONSUMER_KEY, Config.CONSUMER_SECRET);
		            mOAuthProvider = new CommonsHttpOAuthProvider( URL_REQUEST_TOKEN, URL_ACCESS_TOKEN, URL_WEB_AUTHORIZATION);
	            	url = mOAuthProvider.retrieveRequestToken(mOAuthConsumer, URL_CALLBACK);
	            	if( validstr(url) ) break;
	            } catch (Throwable ex) {
	            	report_ex(ex);
	            }
        	}
            if( !validstr(url)  ) {
            	finish();
            	return;
            }
        	Log.d(TAG,"auth url="+url);
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
        			SignedClient client = new SignedClient();
        			client.prepareConsumer(account,Config.CONSUMER_KEY, Config.CONSUMER_SECRET);
        			SignedClient.JSONResult result = client.json_signed_get(act,"http://api.imgur.com/2/account.json");
        			if( result.err != null ){
    					act.show_toast(Toast.LENGTH_LONG,result.err);
    					return;
    				}
    				if( result.json.isNull("account") ){
    					act.show_toast(Toast.LENGTH_LONG,String.format("missing 'account' in response: %s",result.str));
    					return;
    				}
    				// 認証されたアカウントを保存する
		        	account.name = result.json.getJSONObject("account").getString("url");
		        	account.save(act.cr);
		        	show_toast(Toast.LENGTH_SHORT,R.string.account_added);
                }
            	finish();
            } catch (Throwable ex) {
            	report_ex(ex);
            }
        }
    };

}