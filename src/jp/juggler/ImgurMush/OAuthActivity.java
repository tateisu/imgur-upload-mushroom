package jp.juggler.ImgurMush;

import org.json.JSONObject;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import android.content.ContentResolver;
import android.content.Intent;
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

public class OAuthActivity extends ActivityBase {
    private static final String TAG = "OAuthActivity";
    
    public static final String REQUEST_TOKEN_ENDPOINT_URL = "request_token_endpoint_url";
    public static final String ACCESS_TOKEN_ENDPOINT_URL = "access_token_endpoint_url";
    public static final String AUTHORIZATION_WEBSITE_URL = "authorization_website_url";
    public static final String CONSUMER_KEY = "consumer_key";
    public static final String CONSUMER_SECRET = "consumer_secret";
    public static final String CALLBACK = "callback";
    
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_TOKEN_SECRET = "token_secret";
    public static final String EXTRA_ACCOUNT_NAME = "account_name";
    
    public static final String OAUTH_VERIFIER = "oauth_verifier";

    String mRequestTokenEndpointUrl;
    String mAccessTokenEndpointUrl;
    String mAuthorizationWebsiteUrl;
    String mConsumerKey;
    String mConsumerSecret;
    String mCallback;
    String mOAuthVerifier;
    CommonsHttpOAuthProvider mOAuthProvider;
    CommonsHttpOAuthConsumer mOAuthConsumer;
    ContentResolver cr;

    WebView mWebView;
    Button btnSiteTop;
    Button btnOAuthStart;

    private static final boolean validstr(String s){
    	return s!= null && s.length() != 0;
    }
    
    String logout_url = "http://imgur.com/logout";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.act_oauth);
        mWebView = (WebView)findViewById(R.id.webview);
        btnSiteTop = (Button)findViewById(R.id.btnSiteTop);
        btnOAuthStart = (Button)findViewById(R.id.btnOAuthStart);
        
        cr = getContentResolver();
        setResult(RESULT_CANCELED);
    	
        btnSiteTop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mWebView.loadUrl(logout_url);
			}
		});
        btnOAuthStart.setOnClickListener(new OnClickListener() {
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
            OAuthActivity.this.setProgress(newProgress * 100);
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
    	if( (url != null) && (url.startsWith(mCallback)) ){
	        mWebView.stopLoading();
	        mWebView.setVisibility(View.INVISIBLE);
	        if( mOAuthVerifier == null ){
	            Uri uri = Uri.parse(url);
	            mOAuthVerifier = uri.getQueryParameter(OAUTH_VERIFIER);
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
        Intent intent = getIntent();
        mRequestTokenEndpointUrl = intent.getStringExtra(REQUEST_TOKEN_ENDPOINT_URL);
        mAccessTokenEndpointUrl = intent.getStringExtra(ACCESS_TOKEN_ENDPOINT_URL);
        mAuthorizationWebsiteUrl = intent.getStringExtra(AUTHORIZATION_WEBSITE_URL);
        mConsumerKey = intent.getStringExtra(CONSUMER_KEY);
        mConsumerSecret = intent.getStringExtra(CONSUMER_SECRET);
        mCallback = intent.getStringExtra(CALLBACK);
        if( validstr(mRequestTokenEndpointUrl ) 
        &&  validstr(mAccessTokenEndpointUrl  ) 
        &&  validstr(mAuthorizationWebsiteUrl ) 
        &&  validstr(mConsumerKey             )
        &&  validstr(mConsumerSecret          ) 
        &&  validstr(mCallback                )
        ){
        	
            mWebView.setWebViewClient(mWebViewClient);
            mWebView.setWebChromeClient(mWebChromeClient);
            WebSettings setting = mWebView.getSettings();
            setting.setJavaScriptEnabled(true);
            setting.setBuiltInZoomControls(true);
            new Thread(proc_before).start();
        } else {
            finish();
        }
    }

    private Runnable proc_before = new Runnable() {
        @Override
        public void run() {
        	String url = null;
        	for(int nTry=0;nTry<10;++nTry){
	            try {
		            mOAuthConsumer = new CommonsHttpOAuthConsumer( mConsumerKey, mConsumerSecret);
		            mOAuthProvider = new CommonsHttpOAuthProvider( mRequestTokenEndpointUrl, mAccessTokenEndpointUrl, mAuthorizationWebsiteUrl);
	            	url = mOAuthProvider.retrieveRequestToken(mOAuthConsumer, mCallback);
	            	if( validstr(url) ) break;
	            } catch (Throwable ex) {
	            	report_ex(ex);
	            }
        	}
            if( validstr(url)  ) {
            	Log.d(TAG,"auth url="+url);
                mWebView.loadUrl(url);
            } else {
                finish();
            }
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
                	SignedClient client = new SignedClient();
                	client.consumer = new CommonsHttpOAuthConsumer(mConsumerKey, mConsumerSecret);
                	client.consumer.setTokenWithSecret(token, tokenSecret);
                	
                	JSONObject result = client.json_signed_get("http://api.imgur.com/2/account.json");
                	client.error_report(self,result);
                	// 認証されたアカウントを追加する
        			ImgurAccount account = new ImgurAccount();
        			account.name = result.getJSONObject("account").getString("url");
        			account.token = token;
        			account.secret = tokenSecret;
        			account.save(cr);
        			show_toast(Toast.LENGTH_SHORT,R.string.oauth_complete);
                }
            } catch (Throwable ex) {
            	report_ex(ex);
            }
            finish();
        }
    };

}