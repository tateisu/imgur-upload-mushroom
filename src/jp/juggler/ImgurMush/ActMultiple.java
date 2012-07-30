package jp.juggler.ImgurMush;

import java.io.File;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ClipboardHelper;
import jp.juggler.ImgurMush.helper.ImageTempDir;
import jp.juggler.ImgurMush.helper.UploadItem;
import jp.juggler.ImgurMush.helper.UploadItemAdapter;
import jp.juggler.ImgurMush.helper.UploadTargetManager;
import jp.juggler.ImgurMush.helper.Uploader;
import jp.juggler.util.LogCategory;

public class ActMultiple extends BaseActivity{
	static final LogCategory log = new LogCategory("ActMultiple");
	
	static final int REQ_FILEPICKER = 2;
	static final int REQ_HISTORY = 3;
	static final int REQ_PREF = 4;
	static final int REQ_ARRANGE= 5;
	static final int REQ_APPINFO = 6;
	static final int REQ_CAPTURE = 7;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initUI();
		initPage();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		initPage();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		save_status();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent detail) {
		log.d("onActivityResult req=%s res=%sdetail=%s",requestCode,resultCode,detail);
		switch(requestCode){
		case REQ_FILEPICKER:
			if( resultCode == RESULT_OK && detail != null ){
				add_item(uploader.uri_to_path(detail.getData()));
			}
			break;
		case REQ_HISTORY:
			if( resultCode == RESULT_OK && detail != null ){
				uploader.finish_mush(detail.getStringExtra("url"));
			}
			break;
		case REQ_PREF:
			upload_target_manager.reload();
			break;
		case REQ_ARRANGE:
			if( resultCode ==  RESULT_OK && detail != null){
				String path = detail.getStringExtra(PrefKey.EXTRA_DST_PATH);
				replace_path(last_edit_index,path);
			}
			break;
		case REQ_APPINFO:
			break;
		case REQ_CAPTURE:
			if(resultCode == RESULT_OK ){
				Uri uri = (detail==null ? null : detail.getData());
				if( uri == null ) uri = capture_uri;
				if( uri == null ){
					log.e("cannot get capture uri");
				}else{
					log.d("capture uri = %s", uri);
					add_item(uploader.uri_to_path(uri));
				}
			}
			break;
		}
		super.onActivityResult(requestCode, resultCode, detail);
	}
	
	/////////////////////////////////////////////////////

	final ActMultiple act = this;
	Button btnUpload;
	UploadTargetManager upload_target_manager;
	Uploader uploader;
	ListView listview;
	UploadItemAdapter upload_list_adapter;
	
	
	void initUI(){
		setContentView(R.layout.act_multiple);
		
		upload_list_adapter = new UploadItemAdapter(this);
		listview = (ListView)findViewById(R.id.list);
		listview.setAdapter(upload_list_adapter);

		listview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, final int idx,long arg3) {
				final UploadItem item = (UploadItem)upload_list_adapter.getItem(idx);
				act.dialog_manager.show_dialog(new AlertDialog.Builder(act)
					.setCancelable(true)
					.setItems(
						new String[]{
							getString(R.string.edit),
							getString(R.string.selection_remove),
						}
						,new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								switch(which){
								case 0:
									open_editor(idx,item);
									break;
								case 1:
									upload_list_adapter.remove(idx);
									break;
								}
							}
						}
					)
				);
				// TODO 自動生成されたメソッド・スタブ
				
			}
		});
		
//		preview = (ImageView)findViewById(R.id.preview);
//		tvFileDesc = (TextView)findViewById(R.id.tvFileDesc);

//		btnEdit= (Button)findViewById(R.id.btnEdit);
		btnUpload = (Button)findViewById(R.id.btnUpload);

		uploader = new Uploader(this,uploader_callback);

		upload_target_manager = new UploadTargetManager(this);



		findViewById(R.id.btnCancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				uploader.finish_mush("");
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
		
		findViewById(R.id.btnCapture).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				open_capture();
			}
		});

//		btnEdit.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				if( btnUpload.isEnabled() ){
//					open_editor();
//				}
//			}
//		});

		btnUpload.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				upload_start();
			}
		});

	}
	
	Uri capture_uri;
	int last_edit_index;
	
	void save_status(){
		log.d("save_status");
		Intent intent = getIntent();
		// リストアフラグ設定
		intent.putExtra( PrefKey.EXTRA_IS_STATUS_SAVE , true );
		// 最後にカメラ画面を呼び出した際に生成したURL
		if(capture_uri != null) intent.putExtra( PrefKey.EXTRA_CAPTURE_URI, capture_uri.toString() );
		
		intent.putExtra( PrefKey.EXTRA_LAST_EDIT_INDEX, last_edit_index );
		
		// 選択中のファイルの一覧
		ArrayList<Parcelable> list = new ArrayList<Parcelable>();
		int n = upload_list_adapter.getCount();
		for(int i=0;i<n;++i){
			UploadItem item = (UploadItem)upload_list_adapter.getItem(i);
			if( item != null ) list.add( Uri.fromFile(item.file));
		}
		intent.getExtras().putParcelableArrayList(Intent.EXTRA_STREAM,list);
		intent.setAction(Intent.ACTION_SEND_MULTIPLE);
		setIntent(intent);
	}
	
	void initPage(){
		Intent intent = getIntent();
		if( intent != null ){
			
			// カメラ画面を呼び出した際のURIを復旧
			String v = intent.getStringExtra(PrefKey.EXTRA_CAPTURE_URI);
			if( v != null ) this.capture_uri = Uri.parse(v);
			
			
			String action = intent.getAction();
			if( Intent.ACTION_SEND_MULTIPLE.equals(action) ){
				try{
					ArrayList<UploadItem> tmp = new ArrayList<UploadItem>();
					for( Parcelable p : intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ){
						String path = uploader.uri_to_path((Uri) p);
						if( path != null ) tmp.add(new UploadItem(act,path));
					}
					upload_list_adapter.replace(tmp);
				}catch(Throwable ex){
					report_ex(ex);
				}
			}
		}
	}
	
	private void add_item(String path) {
		if( path != null ) upload_list_adapter.add(new UploadItem(act,path));
	}

	private void replace_path(int idx, String path) {
		upload_list_adapter.replace_item(idx,new UploadItem(act,path));
	}
	
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
			show_toast(Toast.LENGTH_LONG,R.string.picker_missing);
		}
		log.d("open_file_picker :finish");
		uploader.finish_mush("");
	}
	

	void open_capture(){
		try{
			capture_uri = Uri.fromFile(new File(
					ImageTempDir.getTempDir(act,act.pref(),act.ui_handler)
					,String.format("capture-%s",System.currentTimeMillis())
			));
		}catch(Throwable ex){
			ex.printStackTrace();
			return;
		}
		try{
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT,capture_uri);
			startActivityForResult(intent,REQ_CAPTURE);
			return;
		}catch(ActivityNotFoundException ex ){
			show_toast(Toast.LENGTH_LONG,R.string.capture_missing);
		}
	}
	
	// 画像加工画面を開く
	void open_editor(int index,UploadItem item){
		last_edit_index = index;
		Intent intent = new Intent(act,ActArrange.class);
		intent.putExtra(PrefKey.EXTRA_SRC_PATH,item.file.getAbsolutePath());
		startActivityForResult(intent,REQ_ARRANGE);
	}

	public void menu_dialog() {
		dialog_manager.show_dialog(
			new AlertDialog.Builder(this)
			.setCancelable(true)
			.setNegativeButton(R.string.cancel,null)
			.setItems(
				new String[]{
					getString(R.string.history),
					getString(R.string.setting),
					getString(R.string.about),
				},new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch(which){
						case 0:
							startActivityForResult(new Intent(act,ActHistory.class),REQ_HISTORY);
							break;
						case 1:
							startActivityForResult(new Intent(act,ActPref.class),REQ_PREF);
							break;
						case 2:
							startActivityForResult(new Intent(act,ActAppInfo.class),REQ_APPINFO);
							break;
						}
					}
				}
			)
		);
	}

	boolean upload_cancelled = false;
	StringBuilder sb_image_url;
	int upload_next = 0;
	int upload_complete = 0;
	
	Uploader.Callback uploader_callback = new Uploader.Callback() {
		
		@Override public void onCancelled() {
			upload_cancelled = true;
			show_toast(Toast.LENGTH_SHORT,getString(R.string.cancel_notice));
		}

		@Override public void onStatusChanged(boolean bBusy) {
			if(!bBusy) upload_next();
		}

		@Override public void onComplete(String image_url, String page_url) {
			int t = Integer.parseInt(act.pref().getString(PrefKey.KEY_URL_MODE,"0"));
			if( sb_image_url.length() > 0 ) sb_image_url.append("\n");
			switch(t){
			default:
			case 0: sb_image_url.append(uploader.trim_output_url(image_url)); break;
			case 1: sb_image_url.append(uploader.trim_output_url(page_url));break;
			}
			++upload_complete;
		}
		// XXX: アップロード中に画面を回転させると、アップロードがキャンセルされる…
	};
	
	// アップロードを開始する
	void upload_start(){
		if(isFinishing()) return;

		// 画像が選択されていない
		if( upload_list_adapter.getCount() <= 0 ) return;

		btnUpload.setEnabled(false);
		upload_cancelled = false;
		sb_image_url = new StringBuilder();
		upload_next = 0;
		upload_complete = 0;
		
		upload_next();
	}
	void upload_next(){
		if( upload_next != upload_complete ){
			upload_cancelled = true;
		}
		UploadItem item = (UploadItem)upload_list_adapter.getItem(upload_next);
		if( item == null || upload_cancelled ){
			if( sb_image_url.length() > 0 ){
				ClipboardHelper.clipboard_copy(act,sb_image_url.toString(),act.getString(R.string.output_to_clipboard));
			}
			btnUpload.setEnabled(true);
			if(!upload_cancelled) finish();
			return;
		}
		ImgurAlbum album = upload_target_manager.getSelectedAlbum();
		ImgurAccount account = upload_target_manager.getSelectedAccount();
		log.d("upload to account=%s,album_id=%s",(account==null?"OFF":"ON"),(album==null?"OFF":"ON"));
		++upload_next;
		uploader.image_upload(
			account
			,(album==null?null : album.album_id)
			,item.file.getAbsolutePath()
		);
	}
}
