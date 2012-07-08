package jp.juggler.ImgurMush;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import jp.juggler.ImgurMush.ProgressHTTPEntity.ProgressListener;
import jp.juggler.util.CancelChecker;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ImgurMush extends ActivityBase {
	static final String TAG = "ImgurMush";
	static final int REQ_FILEPICKER = 2;
	static final int REQ_HISTORY = 3;
	
	
	Spinner spAccount;
	Spinner spAlbum;
	Button btnPick;
	Button btnUpload;
	Button btnCancel;
	ImageView preview; 
	ContentResolver cr ;
	LayoutInflater inflater;
	
	
	boolean init_busy = true;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        spAccount = (Spinner)findViewById(R.id.account);
        spAlbum = (Spinner)findViewById(R.id.album);
        btnPick = (Button)findViewById(R.id.btnPick);
        btnUpload = (Button)findViewById(R.id.btnUpload);
        btnCancel = (Button)findViewById(R.id.btnCancel);
        preview = (ImageView)findViewById(R.id.preview);
        
        ui_handler = new Handler();
        cr =getContentResolver();
        inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        
        preview.setVisibility(View.INVISIBLE);
        
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		try{
			int i = pref.getInt("URL_mode",0);
			SharedPreferences.Editor e = pref.edit();
			e.putString( "URL_mode" , Integer.toString(i));
			e.commit();
		}catch(ClassCastException ex){
		}
        
        spAccount.setAdapter(account_adapter);
		spAccount.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				Log.d(TAG,"onItemSelected");
				init_album_list( get_account(pos) ,false);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				Log.d(TAG,"onNothingSelected");
				init_album_list( null  ,false);
			}
		});
        
		spAlbum.setAdapter(album_adapter);

		btnCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish_mush("");
			}
		});
		btnPick.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				open_file_picker();
			}
		});
		btnUpload.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				image_upload();
			}
		});
        init_page();
    }
    
    
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(account_cursor!=null ){
			spAccount.setAdapter(null);
			account_cursor.close();
		}
	}


	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		init_page();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent detail) {
		Log.d(TAG,"onActivityResult req="+requestCode+", result="+detail);
		if( requestCode == REQ_FILEPICKER ){
			if( detail != null ){
				Uri uri = detail.getData();
				if( uri != null ) file_selected(uri);
			}
			return;
		}
		if( requestCode ==  REQ_HISTORY ){
			if( resultCode == RESULT_OK ) finish_mush(detail.getStringExtra("url"));
			return;
		}

		super.onActivityResult(requestCode, resultCode, detail);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.option_menu, menu);
	    return true;
	}
	


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    default:
	        return super.onOptionsItemSelected(item);

	    case R.id.menu_setting:
	    	ui_handler.post(new Runnable() {
				@Override
				public void run() {
					if(isFinishing())return;
					startActivity(new Intent(self,ActPref.class));
				}
			});
			return true;
	    case R.id.menu_history:
	    	startActivityForResult(new Intent(self,ActHistory.class),REQ_HISTORY);
	    	return true;
	    }
	}
	
	
	
	///////////////////////////////////////////////////////////////////
	
	@Override
	protected void onPause() {
		super.onPause();
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor e = pref.edit();
	//	e.putBoolean("cbAutoStart",cbAutoStart.isChecked());
	//	e.putInt("URL_mode",Integer.toString(spOutput.getSelectedItemPosition()));
		e.commit();
	}


	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		ImgurAccount a_old = get_account();
		account_reload();
		ImgurAccount a_new = get_account();
		if( a_old == null && a_new == null ){
			return;
		}else if( a_old== null || a_new == null || ! a_old.token.equals(a_new.token) ){
			load_album_selection(pref);
		}
	}


	void init_page(){
		init_busy = true;
		preview_update(null);
		//
		account_cursor = cr.query(ImgurAccount.meta.uri,null,null,null,ImgurAccount.COL_NAME+" asc");
		account_cursor.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				account_adapter.notifyDataSetChanged();
			}
			@Override
			public void onInvalidated() {
				account_adapter.notifyDataSetInvalidated();
			}
		});
		// アカウント選択肢を復帰
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		load_account_selection(pref);
		// 
		init_album_list(get_account(),true);
		
		delay_open.run();
		
	}
	

	void finish_mush(String text){
		Log.d(TAG,"finish_mush text="+text);
		if( text != null && text.length() > 0 ){
			SharedPreferences pref = pref();
			if( pref.getBoolean("cbInsertSpacePref",false) ) text = " "+text;
			if( pref.getBoolean("cbInsertSpaceSuff",true) ) text = text + " ";
		}
		if( is_mushroom() ){
			Intent intent = new Intent();
			intent.putExtra("replace_key", text);
		    setResult(RESULT_OK, intent);
		}else if(text !=null && text.length() > 0 ){
			 ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); 
			 cm.setText(text);
			 Toast.makeText(self,R.string.copy_to_clipboard,Toast.LENGTH_SHORT).show();
		}
	    finish();
	}
	
	boolean is_mushroom(){
		Intent intent = getIntent();
		return ( intent != null && "com.adamrocker.android.simeji.ACTION_INTERCEPT".equals(intent.getAction()) );
	}


	
	/////////////////////////////////////////////////////
	


	
	
	void load_account_selection(SharedPreferences pref){
		String target_account = pref.getString("pref_default_account", "<>lastused");
		if( target_account.equals("<>lastused") ){
			target_account = pref.getString("account_name","<>anonymous");
		}
		if( target_account.equals("<>anonymous") ){
			target_account = null;
		}
		Log.d(TAG,"load_account_selection: "+target_account);
		
		if( account_cursor != null && account_cursor.getCount() > 0 ){
			for(int i=0,ie=account_cursor.getCount();i<=ie;++i){
				if( i==0 ){
					if( target_account ==null ){
						spAccount.setSelection(i);
						break;
					}
				}else{
					ImgurAccount account = get_account(i);
					if( account != null && account.name.equals(target_account) ){
						spAccount.setSelection(i);
						break;
					}
				}
			}
		}
	}

	

	// アカウントリストを更新する
	void account_reload(){
		account_cursor.requery();
		account_adapter.notifyDataSetChanged();
	}
	
	int account_cursor_colidx_id = -1;
	int account_cursor_colidx_name;
	int account_cursor_colidx_token;
	int account_cursor_colidx_secret;

	ImgurAccount get_account(){
		return get_account(spAccount.getSelectedItemPosition());
	}
	ImgurAccount get_account(int position){
		if(position <= 0 || !account_cursor.moveToPosition(position -1)) return null;
		
		if( account_cursor_colidx_id < 0 ){
			account_cursor_colidx_id = account_cursor.getColumnIndex(ImgurAccount.COL_ID);
			account_cursor_colidx_name = account_cursor.getColumnIndex(ImgurAccount.COL_NAME);
			account_cursor_colidx_token = account_cursor.getColumnIndex(ImgurAccount.COL_TOKEN);
			account_cursor_colidx_secret = account_cursor.getColumnIndex(ImgurAccount.COL_SECRET);
		}
		
		ImgurAccount item = new ImgurAccount();
		item.id     = account_cursor.getLong(account_cursor_colidx_id);
		item.name   = account_cursor.getString(account_cursor_colidx_name);
		item.token  = account_cursor.getString(account_cursor_colidx_token);
		item.secret = account_cursor.getString(account_cursor_colidx_secret);
		return item;
		
	}


	
	class AccountViewHolder{
		TextView tvName;
	}
	Cursor account_cursor;
	AccountAdapter account_adapter = new AccountAdapter();
	class AccountAdapter extends BaseAdapter{
		@Override
		public int getCount() {
			return 1+(account_cursor==null? 0 : account_cursor.getCount());
		}

		@Override
		public Object getItem(int arg0) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			return make_view( position,  view,  parent,android.R.layout.simple_spinner_item);
		}

		@Override
		public View getDropDownView(int position, View view,ViewGroup parent) {
			return make_view( position,  view,  parent,android.R.layout.simple_spinner_dropdown_item);
		}
		
		private View make_view(int position, View view, ViewGroup parent,int layout){
			AccountViewHolder holder;
			if(view==null){
				view = inflater.inflate(layout ,null );
				view.setTag( holder = new AccountViewHolder() );
				holder.tvName = (TextView)view.findViewById(android.R.id.text1);
			}else{
				holder = (AccountViewHolder)view.getTag();
			}
			if( position == 0 ){
				holder.tvName.setText(getString(R.string.account_anonymous));
			}else{
				ImgurAccount item = get_account(position);
				if( item == null ){
					holder.tvName.setText("(error)");
				}else{
					holder.tvName.setText(item.name);
				}
			}
			return view;
		}
	}
	
	/////////////////////////////////////////////////////////////////////////////


	void init_album_list( final ImgurAccount account ,boolean bForce){
		if( init_busy && !bForce) return;
		
		if( account == null ){
			Log.d(TAG,"init_album_list: acount is null");
			album_list.clear();
			album_adapter.notifyDataSetChanged();
			init_busy = false;
		}else{
			new AsyncTask<Void,Void,JSONObject>(){
				@Override
				protected JSONObject doInBackground(Void... params) {
					try{
						SignedClient client = new SignedClient();
						client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
						JSONObject result = client.json_signed_get("http://api.imgur.com/2/account/albums.json?count=999");
						if( client.last_rcode != 404 ) client.error_report(self,result);
						return result;
					}catch(Throwable ex){
						report_ex(ex);
					}
					return null;
				}

				@Override
				protected void onPostExecute(JSONObject result) {
					
					if( result != null ){
						try{
							album_list.clear();
							if( result.has("albums") ){
								JSONArray src_list = result.getJSONArray("albums");
								for(int i=0,ie=src_list.length();i<ie;++i){
									album_list.add( src_list.getJSONObject(i) );
								}
							}
							album_adapter.notifyDataSetChanged();
							if( init_busy){
								SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ImgurMush.this);
								load_album_selection(pref);
							}
						}catch(Throwable ex){
							report_ex(ex);
						}
					}
					init_busy = false;
				}
				
				
			}.execute();
			
		}
	}
	
	void load_album_selection(SharedPreferences pref){
		Log.d(TAG,"load_album_selection");
		ImgurAccount account = get_account();
		String key = "album_name" + (account ==null ? "" : ("_" + account.name ) );
		
		String last_text = pref.getString(key,null);
		for(int i=0,ie=album_list.size();i<=ie;++i){
			if( i==0 ){
				if( last_text ==null ){
					spAlbum.setSelection(i);
					break;
				}
			}else{
				try{
					JSONObject item = get_album(i);
					if( item != null && item.getString("title").equals(last_text) ){
						spAlbum.setSelection(i);
						break;
					}
				}catch(JSONException ex){
					ex.printStackTrace();
				}
			}
		}
	}
	
	JSONObject get_album(int pos){
		if( pos <= 0 ) return null;
		return album_list.get(pos-1);
	}
	
	ArrayList<JSONObject> album_list = new ArrayList<JSONObject>();
	AlbumAdapter album_adapter = new AlbumAdapter();
	class AlbumAdapter extends BaseAdapter{
		@Override
		public int getCount() {
			return 1+album_list.size();
		}

		@Override
		public Object getItem(int arg0) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			return make_view( position,  view,  parent,android.R.layout.simple_spinner_item);
		}

		@Override
		public View getDropDownView(int position, View view,ViewGroup parent) {
			return make_view( position,  view,  parent,android.R.layout.simple_spinner_dropdown_item);
		}
		
		private View make_view(int position, View view, ViewGroup parent,int layout){
			AccountViewHolder holder;
			if(view==null){
				view = inflater.inflate(layout ,null );
				view.setTag( holder = new AccountViewHolder() );
				holder.tvName = (TextView)view.findViewById(android.R.id.text1);
			}else{
				holder = (AccountViewHolder)view.getTag();
			}
			if( position == 0 ){
				holder.tvName.setText(getString(R.string.no_album));
			}else{
				try{
					JSONObject item = get_album(position);
					holder.tvName.setText(item.getString("title"));
				}catch(Throwable ex){
					holder.tvName.setText("(error)");
					
				}
			}
			return view;
		}
	}

	/////////////////////////////////////////////////////////////////////////////

	Runnable delay_open = new Runnable() {
		@Override
		public void run() {
			if(isFinishing()) return;
			int w = preview.getWidth();
			int h = preview.getHeight();
			if( w < 16 || h < 16 ){
				ui_handler.postDelayed(delay_open,111);
				return;
			}
			Intent intent = getIntent();
			if( intent != null ){
				Uri uri = intent.getData();
				if(uri == null){
					Bundle extra = intent.getExtras();
					if( extra != null ){
						uri  = (Uri)extra.get(Intent.EXTRA_STREAM);
					}
				}
				if( uri != null ){
					file_selected(uri);
					return;
				}
			}

			//
			if( pref().getBoolean("cbAutoPick",false) ) open_file_picker();

		}
	};
	
	void open_file_picker(){
		try{
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			// open chooser
		//	startActivityForResult(Intent.createChooser(intent,"file picker"),REQ_FILEPICKER);
			startActivityForResult(intent,REQ_FILEPICKER);
			return;
		}catch(ActivityNotFoundException ex ){
			Toast.makeText(this,getText(R.string.no_picker),Toast.LENGTH_SHORT).show();		
		}
    	Log.d(TAG,"open_file_picker :finish");
    	finish_mush("");
    }
	
	static Pattern reJPEG = Pattern.compile("\\.jp(eg?|g)$",Pattern.CASE_INSENSITIVE);
	static Pattern rePNG = Pattern.compile("\\.png$",Pattern.CASE_INSENSITIVE);
	static Pattern reGIF = Pattern.compile("\\.gif$",Pattern.CASE_INSENSITIVE);
	
	String file_path;
	
    void file_selected(Uri uri){
    	file_path =uri_to_path(uri);
    	preview_update(file_path);
	}


    
    String uri_to_path(Uri uri){
    	if(uri==null) return null;
    	Log.d(TAG,"image uri="+uri.toString());
    	if(uri.getScheme().equals("content") ){
			Cursor c = cr.query(uri, new String[]{MediaStore.Images.Media.DATA }, null, null, null);
			if( c !=null ){
				try{
					if(c.moveToNext() ) return c.getString(0);
				}finally{
					c.close();
				}
			}
    	}else if(uri.getScheme().equals("file") ){
    		return uri.getPath();
    	}
    	Toast.makeText(this,"ファイルパスを調べられません\n"+uri.toString(),Toast.LENGTH_LONG);
    	return null;
    }


    void preview_update(String path){
    	preview.setVisibility(View.INVISIBLE);
    	btnUpload.setEnabled(false);
    	if( path == null ) return;

    	btnUpload.setEnabled(true);
    	if( pref().getBoolean("cbAutoStart",false) ) image_upload();

    	if( pref().getBoolean("cbDisablePreview",false) ){
    		preview.setVisibility(View.VISIBLE);
	    	preview.setImageResource(R.drawable.preview_disabled);
	    	return;
    	}

    	try{
			BitmapFactory.Options options = new BitmapFactory.Options();

			// 画像の大きさだけ取得
			options.inJustDecodeBounds = true;
			Bitmap image = BitmapFactory.decodeFile(path, options);

			int w = options.outWidth;
			int h = options.outHeight;
			int max_w = preview.getWidth();
			int max_h = preview.getHeight();
			int shift = 1;
			while( w > max_w || h > max_h ){
				++shift;
				w /= 2;
				h /= 2;
			}
			
			// 今度は画像を読み込む
			options.inJustDecodeBounds = false;
			options.inSampleSize = shift;
			options.inPurgeable = true;
			options.inTargetDensity = 0;
			options.inDensity = 0;
			options.inDither =true;
			options.inScaled = false;
		    image = BitmapFactory.decodeFile(path, options);
		    if( image != null ){
		    	preview.setVisibility(View.VISIBLE);
		    	preview.setImageBitmap(image);
		    	
		    }
		}catch(Throwable ex){
			report_ex(ex);
		}
    }
    
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
    
    
    ProgressDialog progress_dialog;
    
    void image_upload(){
    	final ImgurAccount account = get_account();
    	final JSONObject album = get_album(spAlbum.getSelectedItemPosition()); 
    	String album_key = "album_name" + (account ==null ? "" : ("_" + account.name ) );
    	// 現在の設定を保存する
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor e = pref.edit();
		//
		e.putString("account_name",account==null?null : account.name );
		//
		try{
			e.putString(album_key,album==null?null : album.getString("title"));
		}catch(Throwable ex){
			report_ex(ex);
		}
		//
		
		//
		e.commit();

		btnUpload.setEnabled(false);

		final ProgressDialog dialog = progress_dialog = new ProgressDialog(self);
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setMessage("Uploading...");
		dialog.setCancelable(true);
    	dialog.setMax(0);
    	dialog.setProgress(0);
    	dialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				finish_mush("");
			}
		});
    	dialog.show();
    	
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
	    			show_toast(Toast.LENGTH_SHORT,R.string.too_large_data);
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
		    			
		    			ui_handler.post(new Runnable() {
							@Override
							public void run() {
								if(isFinishing() ) return;
								dialog.setMessage("calculate upload size...");
								dialog.setIndeterminate(true);
							}
						});
			    		request.setEntity(new ProgressHTTPEntity(signer.createPostEntity(),progress_listener));
		    			if( cancel_checker.isCancelled() ) return null; 

			    		progress_busy = false;
			    		ui_handler.post(new Runnable() {
							@Override
							public void run() {
								if(isFinishing() ) return;
								dialog.setMessage("Uploading...");
								dialog.setIndeterminate(false);
							}
						});
			    		JSONObject result = client.json_request(request);
		    			if( cancel_checker.isCancelled() ) return null; 
		    			
			    		client.error_report(self,result);
			    		return result;
			    	}else{
			    		HttpPost request = new HttpPost("http://api.imgur.com/2/account/images.json");
			    		signer.addParam(false,"oauth_token", account.token);
			    		signer.addParam(false,"oauth_consumer_key", Config.CONSUMER_KEY );
			    		signer.addParam(false,"oauth_version","1.0");
			    		signer.addParam(false,"oauth_signature_method","HMAC-SHA1");
			    		signer.addParam(false,"oauth_timestamp",Long.toString(System.currentTimeMillis() / 1000L));
			    		signer.addParam(false,"oauth_nonce",Long.toString(new Random().nextLong()));
			    		
			    		ui_handler.post(new Runnable() {
							@Override
							public void run() {
								dialog.setMessage("calculate digest...");
								dialog.setIndeterminate(true);
							}
						});
			    		signer.sign_header(  request,signer.hmac_sha1(Config.CONSUMER_SECRET,account.secret,"POST",request.getURI().toString()));
			    		if( cancel_checker.isCancelled() ) return null; 
			    		
			    		ui_handler.post(new Runnable() {
							@Override
							public void run() {
								dialog.setMessage("calculate upload size...");
								dialog.setIndeterminate(true);
							}
						});
			    		request.setEntity(new ProgressHTTPEntity(signer.createPostEntity(),progress_listener));
			    		if( cancel_checker.isCancelled() ) return null; 

			    		progress_busy = false;
			    		ui_handler.post(new Runnable() {
							@Override
							public void run() {
								dialog.setMessage("Uploading...");
								dialog.setIndeterminate(false);
							}
						});
			    		JSONObject result = client.json_request(request); 
			    		if( cancel_checker.isCancelled() ) return null; 

			    		client.error_report(self,result);
			    		
			    		if( result != null && album != null ){
			    			try{
			    				JSONObject image = result.getJSONObject("images").getJSONObject("image");
				    			request = new HttpPost("http://api.imgur.com/2/account/albums/"+ album.getString("id")+".json");
					    		request.setHeader("Content-Type", "application/x-www-form-urlencoded");
					    		ArrayList<NameValuePair> nameValuePair = new ArrayList<NameValuePair>();
					    		nameValuePair.add(new BasicNameValuePair("add_images",image.getString("hash")));
					    		request.setEntity(new UrlEncodedFormEntity(nameValuePair));
			    				client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
					    		client.consumer.sign(request);
					    		JSONObject r2 = client.json_request(request);
					    		client.error_report(self,r2);
			    			}catch(Throwable ex){
			    				report_ex(ex);
			    			}
			    		}
			    		
			    		return result;
			    	}
				}catch(Throwable ex){
					report_ex(ex);
				}
				return null;
			}

			@Override
			protected void onPostExecute(JSONObject result) {
				if(isFinishing()) return;
				dialog.dismiss();
				btnUpload.setEnabled(true);
				int t = Integer.parseInt(pref().getString("URL_mode","0"));
				try{
					if(result != null ){

						if( result.has("upload") ){
							JSONObject links=result.getJSONObject("upload").getJSONObject("links");
							save_history(links,account,( album==null?null : album.getString("id") ) );
							
							switch(t){
							default:
							case 0: finish_mush(links.getString("original")); return;
							case 1: finish_mush(links.getString("imgur_page")); return;
							}
						}
						if( result.has("images") ){
							JSONObject links=result.getJSONObject("images").getJSONObject("links");
							save_history(links,account,( album==null?null : album.getString("id") ) );
							switch(t){
							default:
							case 0: finish_mush(links.getString("original")); return;
							case 1: finish_mush(links.getString("imgur_page")); return;
							}
						}
					}
				}catch(Throwable ex){
					report_ex(ex);
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
	    	item.save(cr);
    	}catch(Throwable ex){
    		report_ex(ex);
    	}
    }
    
    boolean progress_busy = false;
    AtomicInteger progress_value = new AtomicInteger(0);
    AtomicInteger progress_max= new AtomicInteger(0);
    Runnable progress_runnable = new Runnable() {
		@Override
		public void run() {
			ui_handler.removeCallbacks(progress_runnable);
			if(isFinishing()) return;
			progress_dialog.setMax(progress_max.get());
			progress_dialog.setProgress(progress_value.get());
		}
	};

	ProgressHTTPEntity.ProgressListener progress_listener = new ProgressListener() {
		@Override
		void onProgress(long v, long size) {
	    	if(progress_busy) return;
	    	progress_max.set( (int)size );
	    	progress_value.set( (int)v );
	    	ui_handler.postDelayed(progress_runnable,333);
		}
	};
}