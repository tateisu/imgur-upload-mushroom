package jp.juggler.ImgurMush;

import java.io.File;

import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ClipboardHelper10;
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
	
	static final int REQ_FILEPICKER = 2;
	static final int REQ_HISTORY = 3;
	static final int REQ_PREF = 4;
	static final int REQ_ARRANGE= 5;
	static final int REQ_APPINFO = 6;
	
	static final int FILE_FROM_PICK =1;
	static final int FILE_FROM_EDIT =2;
	static final int FILE_FROM_RESTORE =3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	log.d("onCreate");
    	super.onCreate(savedInstanceState);
    	initUI();
        init_page();
    }
    
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		init_page();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		save_status();
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

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent detail) {
		log.d("onActivityResult req=%s detail=%s",requestCode,detail);
		switch(requestCode){
		case REQ_FILEPICKER:
			if( resultCode == RESULT_OK && detail != null ){
				String path = uri_to_path(detail.getData());
				if(path != null ) setCurrentFile(FILE_FROM_PICK,path);
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
				if( path != null ) setCurrentFile( FILE_FROM_EDIT,path);
			}
			break;
		case REQ_APPINFO:
			break;
		}
		super.onActivityResult(requestCode, resultCode, detail);
	}
	

	protected void  procMenuKey() {
		menu_dialog();
	}


	protected void  procBackKey() {
		finish();
	}
	
	//////////////////////////////////////////////////////////////

	final ActImgurMush act = this;
	ImageView preview; 
	TextView tvFileDesc;
	Button btnEdit;
	Button btnUpload;

	
	UploadTargetManager upload_target_manager;
	Uploader uploader;
	
	boolean init_busy = true;
	
	void initUI(){
		setContentView(R.layout.act_imgur_mush);

		preview = (ImageView)findViewById(R.id.preview);
		tvFileDesc = (TextView)findViewById(R.id.tvFileDesc);
		
        btnEdit= (Button)findViewById(R.id.btnEdit);
		btnUpload = (Button)findViewById(R.id.btnUpload);
		
        uploader = new Uploader(this,new Uploader.Callback() {
			@Override public void onStatusChanged(boolean bBusy) {
				if(bBusy){
					btnUpload.setEnabled(false);
					// XXX: アップロード中に画面を回転させるとキャンセルされる…
				}else{
					btnUpload.setEnabled(true);
				}
			}

			@Override public void onCancelled() {
				show_toast(Toast.LENGTH_SHORT,getString(R.string.cancel_notice));
			}

			@Override public void onComplete(String image_url, String page_url) {
				int t = Integer.parseInt(act.pref().getString(PrefKey.KEY_URL_MODE,"0"));
				switch(t){
				default:
				case 0: finish_mush(image_url); return;
				case 1: finish_mush(page_url); return;
				}
			}
		});
        
        upload_target_manager = new UploadTargetManager(this);

        // 旧版からの以降に関するデータ型の変更
		try{
			SharedPreferences pref = pref();
			int i = pref.getInt(PrefKey.KEY_URL_MODE ,-1);
			if( i != -1 ){
				SharedPreferences.Editor e = pref.edit();
				e.putString(PrefKey.KEY_URL_MODE, Integer.toString(i));
				e.commit();
			}
		}catch(ClassCastException ex){
		}

		findViewById(R.id.btnCancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish_mush("");
			}
		});

		findViewById(R.id.btnSetting).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				menu_dialog();
			}
		});
        
		findViewById(R.id.btnPicker).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				open_file_picker();
			}
		});
        
        btnEdit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if( btnUpload.isEnabled() ){
					open_editor();
				}
			}
		});
		
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
	}
	
	boolean upload_autostart = false;
	boolean editor_autostart = false;

	void init_page(){
		upload_autostart = act.pref().getBoolean(PrefKey.KEY_AUTO_UPLOAD,false);
		editor_autostart = act.pref().getBoolean(PrefKey.KEY_AUTO_EDIT,false);
		//
		setCurrentFile(0,null);
		//
		init_busy = true;
		
		// 画像アプリの共有インテントなどから起動された場合、インテントの指定から画像を選択する
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
				String path = uri_to_path(uri);
				boolean bRestore = intent.getBooleanExtra(PrefKey.EXTRA_IS_STATUS_SAVE,false);
				if( path != null ) setCurrentFile( (bRestore?FILE_FROM_RESTORE:FILE_FROM_PICK),path);
				return;
			}
		}

		// それ以外の場合、設定されていれば自動的にピッカーを開く
		if( pref().getBoolean(PrefKey.KEY_AUTO_PICK,false) ) open_file_picker();
		
	}
	

	
	boolean is_mushroom(){
		Intent intent = getIntent();
		return ( intent != null && "com.adamrocker.android.simeji.ACTION_INTERCEPT".equals(intent.getAction()) );
	}

	void finish_mush(String text){
		log.d("finish_mush text=%s",text);
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
			clipboard_copy(text);
			
			 
		}
	    finish();
	}
	
	private void clipboard_copy(String text) {
		try{
			ClipboardHelper10.copyText(this,text);
			Toast.makeText(act,R.string.copy_to_clipboard,Toast.LENGTH_SHORT).show();
		}catch(Throwable ex){
			ex.printStackTrace();
			Toast.makeText(act,ex.getClass().getSimpleName()+":"+ex.getMessage(),Toast.LENGTH_SHORT).show();

//			if( Build.VERSION.SDK_INT >=11 ){
//				ClipboardHelper11.copyText(this,text);
//			}

		}
	}


	String file_path;
	int open_type;
	
	void save_status(){
		log.d("save_status");
		Intent intent = getIntent();
		if( file_path != null ) intent.setData( Uri.fromFile(new File(file_path)));
		intent.putExtra( PrefKey.EXTRA_IS_STATUS_SAVE , true );
		setIntent(intent);
	}

	void setCurrentFile(int open_type, String path){
		this.file_path = path;
		this.open_type = open_type;
		delay_open.run();
	}
	
	Runnable delay_open = new Runnable() {
		@Override
		public void run() {
			ui_handler.removeCallbacks(delay_open);

			if(isFinishing()) return;

			preview.setVisibility(View.INVISIBLE);
	    	if( file_path == null ){
	    		tvFileDesc.setText(getString(R.string.file_not_selected));
	    		btnEdit.setVisibility(View.GONE);
	        	btnUpload.setEnabled(false);
	    		return;
	    	}else{
	    		btnEdit.setVisibility(View.VISIBLE);
	        	btnUpload.setEnabled(true);
	    	}
	    	
			// レイアウトが完了してないならもう少し後で実行する
			int w = preview.getWidth();
			int h = preview.getHeight();
			if( w < 1 || h < 1 ){
				ui_handler.postDelayed(delay_open,111);
				return;
			}
			
			if( open_type == FILE_FROM_RESTORE ){
	    		log.d("this is reconstruct. skip autostart..");
	    	}else{
		    	// 画像選択後に自動処理が設定されていれば、それを開始する
		    	if( editor_autostart && open_type == FILE_FROM_PICK ){
		    		open_editor();
		    	}else{
		        	upload_starter.run();
		    	}
	    	}

	    	// 画像の情報をテキスト表示
	    	final File file = new File(file_path);
			final String size = TextFormat.formatByteSize(file.length());
			final String mtime = TextFormat.formatTime(act,file.lastModified());
			tvFileDesc.setText(String.format(
				"%s %s\n%s"
				,size
				,mtime
				,file_path
			));

			// プレビュー無効の設定があるかもしれない
	    	boolean measure_only = pref().getBoolean(PrefKey.KEY_DISABLE_PREVIEW,false);
	    	if( measure_only ){
	    		preview.setVisibility(View.VISIBLE);
		    	preview.setImageResource(R.drawable.preview_disabled);
	    	}

	    	// 測定とプレビューを開始する
	    	PreviewLoader.load(act,file_path, measure_only,preview.getWidth(),preview.getHeight(),new PreviewLoader.Callback() {
				@Override
				public void onMeasure(int w, int h) {
					tvFileDesc.setText(String.format(
							"%s %sx%spx %s\n%s"
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
	};

	// 画像選択画面を開く
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
    	log.d("open_file_picker :finish");
    	finish_mush("");
    }
	
	// 画像加工画面を開く
    void open_editor(){
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
	
	String uri_to_path(Uri uri){
    	if(uri==null) return null;
    	log.d("image uri=%s",uri.toString());
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
    	Toast.makeText(this,getString(R.string.uri_parse_error,uri.toString()),Toast.LENGTH_LONG).show();
    	return null;
    }
}