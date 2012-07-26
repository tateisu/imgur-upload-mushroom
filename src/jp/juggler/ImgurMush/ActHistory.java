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
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.HistoryAdapter;
import jp.juggler.util.LogCategory;
import jp.juggler.util.TextUtil;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
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
	
	final ActHistory act = this;
	
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
		save_last_selection();
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
	
	static final int ACCOUNT_NOCHANGE = -2;
	static final int ALBUM_DONTCARE = -2;
	
	void initUI(){
		setContentView(R.layout.act_history);
		
		setResult(RESULT_CANCELED);


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
			@Override public void onItemSelected(AdapterView<?> arg0, View arg1,int idx, long arg3) {
				onAccountChange(false,idx,ALBUM_DONTCARE,"account select");
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
		album_loader = new AlbumLoader(act,new AlbumLoader.Callback() {
			@Override public void onLoad() {
				onAccountChange(true,ACCOUNT_NOCHANGE,ALBUM_DONTCARE,"album load");
			}
		});
		account_adapter.registerDataSetObserver(new DataSetObserver() {
			@Override public void onChanged() {
				onAccountChange(true,ACCOUNT_NOCHANGE,ALBUM_DONTCARE,"account dataset changed");
			}
		});
		
		spAlbum = (Spinner)findViewById(R.id.album);
		spAlbum.setAdapter(album_adapter);
		spAlbum.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				onAccountChange(false,ACCOUNT_NOCHANGE,pos,"album select");
			}
			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
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
						@Override public void onClick(DialogInterface dialog, int which) {
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
	boolean init_complete = false;
	
	void initPage(){
		// 最後に選択したアカウントとアルバム
		SharedPreferences pref = act.pref();
		lastused_account_name = pref.getString(PrefKey.KEY_HISTORY_ACCOUNT,null);
		lastused_album_name   = pref.getString(PrefKey.KEY_HISTORY_ALBUM,null);
		init_complete = false;
		
		// アカウントは初期化中でもアクセスできると思うので、選択する
		account_adapter.selectByName(spAccount,lastused_account_name );

		// 初期状態のフィルタを設定する
		onAccountChange(true,ACCOUNT_NOCHANGE,ALBUM_DONTCARE,"init page");
	}
	
	void save_last_selection(){
		// ロード中状態ならセーブしない
		if( !init_complete ) return;
		//
		SharedPreferences.Editor e = act.pref().edit();
		ImgurAlbum album = (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition());
		if( album != null ){
			e.putString(PrefKey.KEY_HISTORY_ACCOUNT,album.account_name);
			e.putString(PrefKey.KEY_HISTORY_ALBUM,album.album_name);
		}else{
			ImgurAccount account = (ImgurAccount)account_adapter.getItem( spAccount.getSelectedItemPosition());
			if( account != null ){
				e.putString(PrefKey.KEY_HISTORY_ACCOUNT,account.name);
				e.remove(PrefKey.KEY_HISTORY_ALBUM);
			}else{
				e.remove(PrefKey.KEY_HISTORY_ACCOUNT);
				e.remove(PrefKey.KEY_HISTORY_ALBUM);
			}
		}
		e.commit();
	}
	
	static final String ACCOUNT_NOT_SELECT = "<>ACCOUNT_NOT_SELECT";
	
	void onAccountChange(boolean bLoadEvent,int account_idx,int album_idx,String desc){
		// アカウント指定が-1なら選択位置を補う
		if( account_idx == ACCOUNT_NOCHANGE) account_idx = spAccount.getSelectedItemPosition();
		
		// 選択中のアカウントを参照する
		ImgurAccount account = (ImgurAccount)account_adapter.getItem(account_idx);
		if( account == null ){
			// アカウントが選択されていない場合
			if( album_idx == ALBUM_DONTCARE ){
				album_adapter.clear(strAlbumAll);
				history_adapter.clearFilter();
				spAlbum.setEnabled(false);
			}

			// アカウント選択なしなら、この状態で初期化完了とみなす
			if( lastused_account_name == null ){
				init_complete = true;
			}
		}else{
			// アカウントが選択されている場合
			
			// アカウント別のアルバム一覧を取得するが、まだロード中かもしれない
			Iterable<ImgurAlbum> list = album_loader.findAlbumList(account.name);
			if( list == null ){
				// ロード中やエラー状態ではアルバム一覧を読めない
				if( album_idx == ALBUM_DONTCARE ){
					album_adapter.clear(strAlbumLoading);
					history_adapter.setFilter(account,null);
					spAlbum.setEnabled(false);
				}
			}else{
				// アルバムを読めたら初期化フェーズは終わったとみなす
				init_complete = true;

				if( album_idx != ALBUM_DONTCARE ){
					// アルバム選択肢が明示されている場合はフィルタの更新のみ行う
					ImgurAlbum album = (ImgurAlbum)album_adapter.getItem(album_idx);
					history_adapter.setFilter(account,album);
					
					// アルバム選択肢の変更だけの場合はリストの再設定は行わない。行うとループが発生する
					return;
				}else{
					// アルバム選択肢が明示されていない場合、アカウントの選択状態が更新されたかアルバム一覧のロード完了だろう

					// 直前までのアルバムの選択。ただしアカウントが異なる場合はnull扱い
					ImgurAlbum old_selection = (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition());
					if( old_selection != null && !account.name.equals(old_selection.account_name) ) old_selection = null;
				
					if( bLoadEvent || old_selection == null ){

						// アルバム選択肢を設定する
						album_adapter.replace(list,strAlbumAll);
						spAlbum.setEnabled( album_adapter.getCount() > 1 );

						// 保存された初期選択がまだ適用されておらず
						// 適用が可能(アカウントが一致する）なら一度だけ選択を行う
						if( account.name.equals(lastused_account_name) ){
							lastused_account_name = null;
							album_idx = album_adapter.findByName(lastused_album_name);
						}else if( old_selection != null ){
							// アカウントは変化していないが、データが更新されたかもしれない。
							// なるべく以前の選択を維持する
							album_idx = album_adapter.findByName(old_selection.album_name);
						}else{
							// アカウントの選択が変わったので、アルバムの選択をリセットする
							album_idx = 0;
						}

						// アルバムを選択する
						spAlbum.setSelection(album_idx);
						// ただし、 selection changed イベントが発生するのはこれより少し後になるし
						// アカウントだけを変更した場合は selection changed イベントはこの直後には発生しない

						// このタイミングでフィルタを更新しておく
						ImgurAlbum album = (ImgurAlbum)album_adapter.getItem(album_idx);
						history_adapter.setFilter(account,album);
					}
				}
			}
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
				@Override public void onClick(DialogInterface dialog, int which) {
					// 作業中表示
					final ProgressDialog progress = new ProgressDialog(act);
					progress.setIndeterminate(true);
					progress.setMessage(getString(R.string.please_wait));
					progress.setCancelable(true);
					act.dialog_manager.show_dialog(progress);
					// 別スレッドで実行
					final Thread t = new Thread(){
						boolean isCancelled(){
							return !progress.isShowing();
						}
						@Override public void run() {
							try{
								Pattern reDeleteHash = Pattern.compile("([^/]+)$");
								Matcher m = reDeleteHash.matcher(item.delete);
								if( !m.find() ){
									act.show_toast(Toast.LENGTH_LONG,act.getString(R.string.delete_hash_missing));
									return;
								}
								String hash = m.group(1);
								String url = "http://api.imgur.com/2/delete/"+hash+"?_format=json";
								for(int nTry=0;nTry<10;++nTry){
									if(isCancelled()) break;
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
							}finally{
								progress.dismiss();
							}
						}
					};
					progress.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							progress.dismiss();
							t.interrupt();
						}
					});
					t.start();
				}
			})
		);
	}
}
