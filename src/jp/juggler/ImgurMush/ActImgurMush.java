package jp.juggler.ImgurMush;

import java.io.File;

import jp.juggler.ImgurMush.helper.PreviewLoader;
import jp.juggler.ImgurMush.helper.TextFormat;
import jp.juggler.ImgurMush.helper.UploadTargetManager;
import jp.juggler.ImgurMush.helper.Uploader;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.ClipboardManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ActImgurMush extends BaseActivity {
	static final String TAG = "ImgurMush";
	static final int REQ_FILEPICKER = 2;
	static final int REQ_HISTORY = 3;
	static final int REQ_PREF = 4;
	static final int REQ_ARRANGE= 5;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	initUI();
        init_page();
    }
    
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
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
		switch(requestCode){
		case REQ_FILEPICKER:
			if( detail != null ){
				Uri uri = detail.getData();
				if( uri != null ) file_selected(uri);
			}
			break;
		case REQ_HISTORY:
			if( resultCode == RESULT_OK ) finish_mush(detail.getStringExtra("url"));
			break;
		case REQ_PREF:
			upload_target_manager.reloadAccount();
			break;
		case REQ_ARRANGE:
			if( resultCode ==  RESULT_OK && detail != null){
				String path = detail.getStringExtra(PrefKey.EXTRA_DST_PATH);
				if( path != null ) file_selected(path);
			}
			break;
		}
		super.onActivityResult(requestCode, resultCode, detail);
	}
	
	SparseBooleanArray pressed_key = new SparseBooleanArray();
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if( event.getAction() == KeyEvent.ACTION_DOWN ){
			switch(keyCode){
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
				pressed_key.put(keyCode,true);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if( event.getAction() == KeyEvent.ACTION_UP && pressed_key.get(keyCode) ){
			pressed_key.delete(keyCode);
			switch(keyCode){
			case KeyEvent.KEYCODE_BACK:
				procBackKey();
				return true;
			case KeyEvent.KEYCODE_MENU:
				procMenuKey();
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	protected void  procMenuKey() {
		menu_dialog();
	}


	protected void  procBackKey() {
		finish();
	}

	//////////////////////////////////////////////////////////////

	ImageView preview; 
	TextView tvFileDesc;
	Button btnEdit;
	Button btnUpload;
	Button btnPicker;
	
	UploadTargetManager upload_target_manager;
	Uploader uploader;
	
	boolean init_busy = true;
	
	void initUI(){
		setContentView(R.layout.act_imgur_mush);

        uploader = new Uploader(this,new Uploader.Callback() {
			
			@Override
			public void onStatusChanged(boolean bBusy) {
				if(bBusy){
					btnUpload.setEnabled(false);
				}else{
					btnUpload.setEnabled(true);
				}
				
			}

			@Override
			public void onCancelled() {
				show_toast(Toast.LENGTH_SHORT,getString(R.string.cancel_notice));
				
			}

			@Override
			public void onComplete(String image_url, String page_url) {
				int t = Integer.parseInt(act.pref().getString(PrefKey.KEY_URL_MODE,"0"));
				switch(t){
				default:
				case 0: finish_mush(image_url); return;
				case 1: finish_mush(page_url); return;
				}
			}
		});
        
		preview = (ImageView)findViewById(R.id.preview);
		tvFileDesc = (TextView)findViewById(R.id.tvFileDesc);
		upload_target_manager = new UploadTargetManager(this);
        preview.setVisibility(View.INVISIBLE);
        
		SharedPreferences pref = pref();
		try{
			int i = pref.getInt(PrefKey.KEY_URL_MODE ,0);
			SharedPreferences.Editor e = pref.edit();
			e.putString(PrefKey.KEY_URL_MODE, Integer.toString(i));
			e.commit();
		}catch(ClassCastException ex){
		}

		findViewById(R.id.btnCancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish_mush("");
			}
		});
        
		btnPicker = (Button)findViewById(R.id.btnPicker);
		btnPicker.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				open_file_picker();
			}
		});
        btnEdit= (Button)findViewById(R.id.btnEdit);
        btnEdit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if( btnUpload.isEnabled() ){
					arrange_dialog();
				}
			}
		});
		
		btnUpload = (Button)findViewById(R.id.btnUpload);
		btnUpload.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				upload_start();
			}
		});

		findViewById(R.id.btnSetting).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				menu_dialog();
			}
		});
	}
	
	void init_page(){
		init_busy = true;
		preview_update(null);

		delay_open.run();
	}
	

	void finish_mush(String text){
		Log.d(TAG,"finish_mush text="+text);
		if( text != null && text.length() > 0 ){
			SharedPreferences pref = pref();
			if( pref.getBoolean(PrefKey.KEY_INSERT_SPACE_PRE,false) ) text = " "+text;
			if( pref.getBoolean(PrefKey.KEY_INSERT_SPACE_SUF,true) ) text = text + " ";
		}
		if( is_mushroom() ){
			Intent intent = new Intent();
			intent.putExtra("replace_key", text);
		    setResult(RESULT_OK, intent);
		}else if(text !=null && text.length() > 0 ){
			 ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); 
			 cm.setText(text);
			 Toast.makeText(act,R.string.copy_to_clipboard,Toast.LENGTH_SHORT).show();
		}
	    finish();
	}
	
	boolean is_mushroom(){
		Intent intent = getIntent();
		return ( intent != null && "com.adamrocker.android.simeji.ACTION_INTERCEPT".equals(intent.getAction()) );
	}

	/////////////////////////////////////////////////////////////////////////////

	Runnable delay_open = new Runnable() {
		@Override
		public void run() {
			if(isFinishing()) return;
			int w = preview.getWidth();
			int h = preview.getHeight();
			if( w < 1 || h < 1 ){
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
			if( pref().getBoolean(PrefKey.KEY_AUTO_PICK,false) ) open_file_picker();
		}
	};
	
//	static Pattern reJPEG = Pattern.compile("\\.jp(eg?|g)$",Pattern.CASE_INSENSITIVE);
//	static Pattern rePNG = Pattern.compile("\\.png$",Pattern.CASE_INSENSITIVE);
//	static Pattern reGIF = Pattern.compile("\\.gif$",Pattern.CASE_INSENSITIVE);
	
	String file_path;
	
    void file_selected(Uri uri){
    	file_path =uri_to_path(uri);
    	preview_update(file_path);
	}
    void file_selected(String path){
    	file_path = path;
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
    	Toast.makeText(this,getString(R.string.uri_parse_error,uri.toString()),Toast.LENGTH_LONG);
    	return null;
    }

    void preview_update(final String path){
    	SharedPreferences pref = pref();
    	final boolean measure_only = pref.getBoolean(PrefKey.KEY_DISABLE_PREVIEW,false);
    	final boolean upload_autostart = pref.getBoolean(PrefKey.KEY_AUTO_UPLOAD,false);
    	preview.setVisibility(View.INVISIBLE);
    	if( path == null ){
    		tvFileDesc.setText(getString(R.string.file_not_selected));
    		btnEdit.setVisibility(View.GONE);
        	btnUpload.setEnabled(false);
    		return;
    	}else{
    		btnEdit.setVisibility(View.VISIBLE);
        	btnUpload.setEnabled(true);
        	if( upload_autostart ) upload_start();
    	}
    	
		final File file = new File(path);
		final String size = TextFormat.formatByteSize(file.length());
		final String mtime = TextFormat.formatTime(this,file.lastModified());
		tvFileDesc.setText(String.format(
			"%s\n%s\n%s"
			,size
			,mtime
			,path
		));

    	if( measure_only ){
    		preview.setVisibility(View.VISIBLE);
	    	preview.setImageResource(R.drawable.preview_disabled);
    	}

    	PreviewLoader.load(this,path, measure_only,preview.getWidth(),preview.getHeight(),new PreviewLoader.Callback() {
			@Override
			public void onMeasure(int w, int h) {
				tvFileDesc.setText(String.format(
						"%s %sx%spx\n%s\n%s"
						,size
						,w,h
						,mtime
						,path
					));
			}

			@Override
			public void onLoad(Bitmap bitmap) {
		    	if(bitmap != null ){
			    	preview.setVisibility(View.VISIBLE);
			    	preview.setImageBitmap(bitmap);
		    	}
			}

		});
    }
    
    /////////////////////////////////////////
    
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
			Toast.makeText(this,getText(R.string.picker_missing),Toast.LENGTH_SHORT).show();		
		}
    	Log.d(TAG,"open_file_picker :finish");
    	finish_mush("");
    }
	
    void arrange_dialog(){
		Intent intent = new Intent(ActImgurMush.this,ActArrange.class);
		intent.putExtra(PrefKey.EXTRA_SRC_PATH,file_path);
		startActivityForResult(intent,REQ_ARRANGE);
	}

	public void menu_dialog() {
		dialog_manager.show_dialog(
			new AlertDialog.Builder(this)
			.setCancelable(true)
			.setNegativeButton(R.string.cancel,null)
			.setItems(
				new String[]{
					getString(R.string.about),
					getString(R.string.history),
					getString(R.string.setting),
					
				},new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch(which){
						case 0:
							startActivity(new Intent(act,ActAppInfo.class));
							break;
						case 1:
							startActivityForResult(new Intent(act,ActHistory.class),REQ_HISTORY);
							break;
						case 2:
							startActivityForResult(new Intent(act,ActPref.class),REQ_PREF);
							break;
						}
						
					}
				}
			)
		);
	}
	
	void upload_start(){
		uploader.image_upload( upload_target_manager.getSelectedAccount(),upload_target_manager.getSelectedAlbum() ,file_path);
	}

}