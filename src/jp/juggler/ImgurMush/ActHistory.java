package jp.juggler.ImgurMush;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.data.ImgurHistory;
import jp.juggler.ImgurMush.helper.AccountAdapter;
import jp.juggler.ImgurMush.helper.AlbumAdapter;
import jp.juggler.ImgurMush.helper.AlbumLoader;
import jp.juggler.ImgurMush.helper.HistoryAdapter;
import jp.juggler.util.LogCategory;
import jp.juggler.util.TextUtil;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

public class ActHistory extends BaseActivity {
	static final LogCategory log = new LogCategory("ActHistory");
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initUI();
		initPage();
	}
	
	ListView listview;
	HistoryAdapter history_adapter;
	AccountAdapter account_adapter;
	AlbumLoader album_loader;
	AlbumAdapter album_adapter;

	Spinner spAccount;
	Spinner spAlbum;
	String strAlbumAll;
	String strAlbumLoading;
	
	void initUI(){
		setContentView(R.layout.act_history);
		
		setResult(RESULT_CANCELED);

		album_loader = new AlbumLoader(act,new AlbumLoader.Callback() {
			@Override
			public void onLoad() {
				onAccountChange(spAccount.getSelectedItemPosition(),-2,"album load");
			}
		});
		
		history_adapter = new HistoryAdapter(this);

		listview = (ListView)findViewById(R.id.list);
		listview.setAdapter(history_adapter);
		listview.setOnItemClickListener(new OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> parent, View view, int idx, long id) {
				final ImgurHistory item = (ImgurHistory)history_adapter.getItem(idx);
				if(item != null){
					act.dialog_manager.show_dialog(
						new AlertDialog.Builder(act)
						.setCancelable(true)
						.setItems(
							act.getResources().getStringArray(R.array.history_context_menu)
							,new DialogInterface.OnClickListener() {
								@Override public void onClick(DialogInterface dialog, int which) {
									switch(which){
									case 0: return_url(item); break;
									case 1: open_browser( item.image ); break; 
									case 2: open_browser( item.page ); break; 
									case 3: delete_dialog( item ); break;
									case 4: item.delete(act.cr); break; 
									}
								}
							}
						)
					);
				}
			}
		});
		
		strAlbumAll = getString(R.string.album_all);
		strAlbumLoading = act.getString(R.string.album_loading);
		
		account_adapter = new AccountAdapter(act,getString(R.string.account_all));
		album_adapter = new AlbumAdapter(this,strAlbumAll);
		
		spAccount = (Spinner)findViewById(R.id.account);
		spAccount.setAdapter(account_adapter);
		spAccount.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,int idx, long arg3) {
				onAccountChange(idx,-2,"account select");
			}
			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				onAccountChange(0,-2,"account non-select");
			}
		});
		
		
		spAlbum = (Spinner)findViewById(R.id.album);
		spAlbum.setAdapter(album_adapter);
		spAlbum.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				onAccountChange(-2,pos,"album select");
			}
			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				onAccountChange(-2,0,"album non-select");
			}
		});
		
		findViewById(R.id.btnClearHistoryAll).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				act.dialog_manager.show_dialog(
						new AlertDialog.Builder(act)
						.setCancelable(true)
						.setNegativeButton(R.string.cancel,null)
						.setTitle(R.string.history_clear_title)
						.setMessage(R.string.history_clear_message)
						.setPositiveButton(R.string.ok,new DialogInterface.OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								final ProgressDialog progress = new ProgressDialog(act);
								progress.setIndeterminate(true);
								progress.setMessage(getString(R.string.please_wait));
								progress.setCancelable(true);
								act.dialog_manager.show_dialog(progress);
								new AsyncTask<Void,Void,String>(){

									@Override
									protected String doInBackground(Void... params) {
										act.cr.delete(ImgurHistory.meta.uri,null,null);
										return null;
									}

									@Override
									protected void onPostExecute(String result) {
										if(isFinishing()) return;
										progress.dismiss();
										Toast.makeText(act,R.string.history_cleared,Toast.LENGTH_SHORT).show();
									}
									
								}.execute();
								
								
							}
						})
				);
				
			}
		});
	}
	
	String lastused_account_name = null;
	String lastused_album_name = null;
	
	void initPage(){
		// 最後に選択したアカウントとアルバム
		SharedPreferences pref = act.pref();
		lastused_account_name = pref.getString(PrefKey.KEY_HISTORY_ACCOUNT,null);
		lastused_album_name   = pref.getString(PrefKey.KEY_HISTORY_ALBUM,null);

		// アカウントは初期化中でもアクセスできると思うので、選択する
		account_adapter.selectByName(spAccount,lastused_account_name );

		// 初期状態のフィルタを設定する
		onAccountChange(-2,-2,"init page");
	}
	
	void onAccountChange(int account_idx,int album_idx,String reason){
		// アカウント指定が-1なら選択位置を補う
		if( account_idx == -2) account_idx = spAccount.getSelectedItemPosition();
		// 選択中のアカウントを参照する
		ImgurAccount account = (ImgurAccount)account_adapter.getItem(account_idx);
		if( account == null ){
			// アカウントが選択されていない場合
			
			if( album_idx == -2 ) album_adapter.clear(strAlbumAll);
			history_adapter.clearFilter();
			return;
		}
		
		// アカウント別の
		Iterable<ImgurAlbum> list = album_loader.findAlbumList(account.name);
		if( list == null ){
			log.d("missing album info: %s",reason);
			if( album_idx == -2 ) album_adapter.clear(strAlbumLoading);
			history_adapter.setFilter(account,null);
			return;
		}
		
		if( album_idx == -2 ){
			// アルバムをロードしなおす
			album_adapter.replace(list,strAlbumAll);
			// 最後にアップロードしたアルバムを選択する？
			if( account.name.equals( lastused_account_name ) ){
				lastused_account_name = null;
				final int idx = album_adapter.findByName(lastused_album_name);
				if(idx >= 0){
					act.ui_handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							spAlbum.setSelection(idx);
						}
					},60);
					return;
				}
			}
			act.ui_handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					spAlbum.setSelection(0);
				}
			},60);
		}else{
			ImgurAlbum album = (ImgurAlbum)album_adapter.getItem(album_idx);
			history_adapter.setFilter(account,album);
		}
	}
	
	///////////////////////////////////////////

	
	

	void return_url(ImgurHistory item){
		int type = Integer.parseInt(pref().getString("URL_mode","0"));
		Intent intent = new Intent();
		switch(type){
		default:
		case 0:
			intent.putExtra("url",item.image);
			break;
		case 1:
			intent.putExtra("url",item.page);
			break;
		}
		setResult(RESULT_OK,intent);
		finish();
	}

	void open_browser(final String url){
		ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing()) return;
				startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
			}
		});
	}
	
	void delete_dialog(final ImgurHistory item){
		dialog_manager.show_dialog(
			new AlertDialog.Builder(this)
			.setCancelable(true)
			.setTitle(item.page)
			.setMessage(R.string.history_delete_confirm)
			.setNegativeButton(R.string.cancel,null)
			.setPositiveButton(R.string.ok,new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					new Thread(){

						@Override
						public void run() {
							Pattern reDeleteHash = Pattern.compile("([^/]+)$");
							Matcher m = reDeleteHash.matcher(item.delete);
							if( !m.find() ){
								act.show_toast(Toast.LENGTH_LONG,act.getString(R.string.delete_hash_missing));
								return;
							}
							String hash = m.group(1);
							String url = "http://api.imgur.com/2/delete/"+hash+"?_format=json";
							for(int nTry=0;nTry<10;++nTry){
								try{
									HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
									conn.connect();
									int rcode = conn.getResponseCode();
									if( rcode == 200 || rcode==302){
										InputStream in = conn.getInputStream();
										try{
											int capa = conn.getContentLength();
											if( capa < 4096) capa = 4096;
											ByteArrayOutputStream bao = new ByteArrayOutputStream( capa );
											byte[] tmp = new byte[4096];
											for(;;){
												int delta = in.read(tmp,0,tmp.length);
												if( delta <= 0 ) break;
												bao.write(tmp,0,delta);
											}
											byte[] data = bao.toByteArray();
											String res = TextUtil.decodeUTF8(data);
											if( -1 != res.indexOf("\"Success\"") ){
												item.delete(act.cr);
											}else{
												act.show_toast(Toast.LENGTH_LONG,res);
											}
										}finally{
											in.close();
										}
										return;
									}
									log.d("http error %d",rcode);
									if( rcode >= 400 ){
										act.show_toast(Toast.LENGTH_LONG,String.format("HTTP error %d",rcode));
										// 400 が来たら履歴から削除する
										if( rcode == 400 ){
											item.delete(act.cr);
										}
										break;
									}
								}catch(Throwable ex){
									act.report_ex(ex);
								}
							}
						}
					}.start();
				}
			})
		);
	}
}
