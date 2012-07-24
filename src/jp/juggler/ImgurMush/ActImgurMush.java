package jp.juggler.ImgurMush;

import java.io.File;

import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.helper.PreviewLoader;
import jp.juggler.ImgurMush.helper.TextFormat;
import jp.juggler.ImgurMush.helper.UploadTargetManager;
import jp.juggler.ImgurMush.helper.Uploader;
import jp.juggler.util.LogCategory;

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
	static final LogCategory log = new LogCategory("ActImgurMush");
	
	static final String TAG = "ImgurMush";
	static final int REQ_FILEPICKER = 2;
	static final int REQ_HISTORY = 3;
	static final int REQ_PREF = 4;
	static final int REQ_ARRANGE= 5;
	static final int REQ_APPINFO = 6;
	
	static final int FILE_FROM_PICK =1;
	static final int FILE_FROM_EDIT =2;
	
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
			if( resultCode == RESULT_OK && detail != null ){
				file_selected(FILE_FROM_PICK,detail.getData());
			}
			break;
		case REQ_HISTORY:
			if( resultCode == RESULT_OK && detail != null ){
				finish_mush(detail.getStringExtra("url"));
			}
			break;
		case REQ_PREF:
			upload_target_manager.reload();
			upload_autostart = act.pref().getBoolean(PrefKey.KEY_AUTO_UPLOAD,false);
			editor_autostart = act.pref().getBoolean(PrefKey.KEY_AUTO_EDIT,false);
			break;
		case REQ_ARRANGE:
			if( resultCode ==  RESULT_OK && detail != null){
				String path = detail.getStringExtra(PrefKey.EXTRA_DST_PATH);
				if( path != null ) file_selected(FILE_FROM_EDIT,path);
			}
			break;
		case REQ_APPINFO:
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
				upload_autostart = false;
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
				if(!bUploadButtonPressed){
					bUploadButtonPressed = true;
					btnUpload.setEnabled(false);
					upload_starter.run();
				}
			}
		});

		findViewById(R.id.btnSetting).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				menu_dialog();
			}
		});
	}
	
	boolean upload_autostart = false;
	boolean editor_autostart = false;

	void init_page(){
		init_busy = true;
		file_selected(0,(String)null);
		delay_open.run();
		//
		upload_autostart = act.pref().getBoolean(PrefKey.KEY_AUTO_UPLOAD,false);
		editor_autostart = act.pref().getBoolean(PrefKey.KEY_AUTO_EDIT,false);
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
					file_selected(FILE_FROM_PICK,uri);
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

    void file_selected(int mode,Uri uri){
    	String path = uri_to_path(uri);
    	if( path != null ) file_selected(mode,path);
	}

    void file_selected(int mode,String _path){
    	file_path = _path;

    	SharedPreferences pref = pref();
    	final boolean measure_only = pref.getBoolean(PrefKey.KEY_DISABLE_PREVIEW,false);
    	preview.setVisibility(View.INVISIBLE);
    	if( file_path == null ){
    		tvFileDesc.setText(getString(R.string.file_not_selected));
    		btnEdit.setVisibility(View.GONE);
        	btnUpload.setEnabled(false);
    		return;
    	}else{
    		btnEdit.setVisibility(View.VISIBLE);
        	btnUpload.setEnabled(true);
        	
        	if( editor_autostart && mode == FILE_FROM_PICK ){
        		arrange_dialog();
        	}else{
            	upload_starter.run();
        	}
    	}
    	
		final File file = new File(file_path);
		final String size = TextFormat.formatByteSize(file.length());
		final String mtime = TextFormat.formatTime(this,file.lastModified());
		tvFileDesc.setText(String.format(
			"%s\n%s\n%s"
			,size
			,mtime
			,file_path
		));

    	if( measure_only ){
    		preview.setVisibility(View.VISIBLE);
	    	preview.setImageResource(R.drawable.preview_disabled);
    	}

    	PreviewLoader.load(this,file_path, measure_only,preview.getWidth(),preview.getHeight(),new PreviewLoader.Callback() {
			@Override
			public void onMeasure(int w, int h) {
				tvFileDesc.setText(String.format(
						"%s %sx%spx\n%s\n%s"
						,size
						,w,h
						,mtime
						,file_path
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
							startActivityForResult(new Intent(act,ActAppInfo.class),REQ_APPINFO);
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
	
	boolean bUploadButtonPressed = false;
	
	Runnable upload_starter = new Runnable() {
		@Override public void run() {
			if(isFinishing()) return;
			ui_handler.removeCallbacks(upload_starter);
		//	upload_autostart = false;
			
			// 画像が選択されていない
			if( file_path == null ) return;
			
			// 開始する指示が出されていない
			if(!bUploadButtonPressed && !upload_autostart) return;

			// アップロード先が初期化されていない
			if( upload_target_manager.isLoading() ){
				ui_handler.postDelayed(upload_starter,333);
				return;
			}
			
			// アップロードを開始する
			bUploadButtonPressed = false;
			btnUpload.setEnabled(false);
			ImgurAlbum album = upload_target_manager.getSelectedAlbum();
			ImgurAccount account = upload_target_manager.getSelectedAccount(); 
	    	log.d("upload to account=%s,album_id=%s",(account==null?"OFF":"ON"),(album==null?"OFF":"ON"));
			uploader.image_upload( 
					account
					,(album==null?null : album.album_id)
					,file_path
			);
		}
	};

}