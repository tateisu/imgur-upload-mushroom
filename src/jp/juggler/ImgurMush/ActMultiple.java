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
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ClipboardHelper;
import jp.juggler.ImgurMush.helper.ImageTempDir;
import jp.juggler.ImgurMush.helper.MushroomHelper;
import jp.juggler.ImgurMush.helper.UploadItem;
import jp.juggler.ImgurMush.helper.UploadItemAdapter;
import jp.juggler.ImgurMush.helper.UploadTargetManager;
import jp.juggler.ImgurMush.uploader.UploadJob;
import jp.juggler.ImgurMush.uploader.UploaderUI;
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
				add_item(MushroomHelper.uri_to_path(env,detail.getData()));
			}
			break;
		case REQ_HISTORY:
			if( resultCode == RESULT_OK && detail != null ){
				MushroomHelper.finish_mush(env,true,detail.getStringExtra("url"));
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
					add_item(MushroomHelper.uri_to_path(env,uri));
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
	UploaderUI uploader;
	ListView listview;
	UploadItemAdapter upload_list_adapter;
	
	
	void initUI(){
		setContentView(R.layout.act_multiple);
		
		upload_list_adapter = new UploadItemAdapter(env);
		listview = (ListView)findViewById(R.id.list);
		listview.setAdapter(upload_list_adapter);

		listview.setOnItemClickListener(new OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> arg0, View arg1, final int idx,long arg3) {
				final UploadItem item = (UploadItem)upload_list_adapter.getItem(idx);
				env.dialog_manager.show_dialog(
					new AlertDialog.Builder(act)
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
									updateUploadButtonStatus();
									break;
								}
							}
						}
					)
				);
			}
		});
		
		btnUpload = (Button)findViewById(R.id.btnUpload);

		uploader = new UploaderUI(env,uploader_callback);

		upload_target_manager = new UploadTargetManager(env);



		findViewById(R.id.btnCancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				MushroomHelper.finish_mush(env,false,"");
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
		intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,list);
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
						String path = MushroomHelper.uri_to_path(env,(Uri) p);
						if( path != null ) tmp.add(new UploadItem(env,path));
					}
					upload_list_adapter.replace(tmp);
				}catch(Throwable ex){
					env.report_ex(ex);
				}
			}
		}
		updateUploadButtonStatus();
	}
	
	private void add_item(String path) {
		if( path != null ) upload_list_adapter.add(new UploadItem(env,path));
		updateUploadButtonStatus();
	}

	private void replace_path(int idx, String path) {
		upload_list_adapter.replace_item(idx,new UploadItem(env,path));
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
			env.show_toast(true,R.string.picker_missing);
		}
		log.d("open_file_picker :finish");
		MushroomHelper.finish_mush(env,false,"");
	}
	

	void open_capture(){
		try{
			capture_uri = Uri.fromFile(new File(
				ImageTempDir.getTempDir(act.env)
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
			env.show_toast(true,R.string.capture_missing);
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
		env.dialog_manager.show_dialog(
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
	
	void updateUploadButtonStatus() {
		boolean b = ( upload_list_adapter.getCount() > 0 && !uploader.isBusy());
		btnUpload.setEnabled(b);
		
	}

	UploaderUI.Callback uploader_callback = new UploaderUI.Callback() {
		@Override public void onStatusChanged(boolean bBusy) {
			updateUploadButtonStatus();
		}

		@Override
		public void onAbort(String error_message) {
			env.show_toast(false,error_message);
		}
		@Override
		public void onTextOutput(String text_output) {
			if( text_output.length() > 0 ){
				ClipboardHelper.clipboard_copy(env,text_output,env.getString(R.string.output_to_clipboard));
			}
			finish();
		}
	};
	
	// アップロードを開始する
	void upload_start(){
		// アップロードするアカウントとアルバム
		ImgurAlbum album = upload_target_manager.getSelectedAlbum();
		ImgurAccount account = upload_target_manager.getSelectedAccount();
		// アップロードジョブを登録してアップロード開始
		UploadJob job = new UploadJob(account, album);
		for(int i=0,ie=upload_list_adapter.getCount();i<ie;++i){
			UploadItem item = (UploadItem)upload_list_adapter.getItem(i);
			if(item!=null) job.addFile(item.file.getAbsolutePath());
		}
		if( job.file_list.size() == 0 ) return;
		uploader.upload(job);
	}
}
