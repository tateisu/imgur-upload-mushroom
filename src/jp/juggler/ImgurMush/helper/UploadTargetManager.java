package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import jp.juggler.ImgurMush.Config;
import jp.juggler.ImgurMush.DlgAlbumNew;
import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.data.APIResult;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.data.SignedClient;
import jp.juggler.util.CancelChecker;
import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;

public class UploadTargetManager {
	static final LogCategory log = new LogCategory("UploadTargetManager");

	final HelperEnvUI env;
	final AccountAdapter account_adapter;
	final AlbumAdapter album_adapter;
	final AlbumLoader album_loader;
	final View btnAlbumNew;
	final Spinner spAccount;
	final Spinner spAlbum;

	public UploadTargetManager(HelperEnvUI _env){
		this.env = _env;
		env.lifecycle_manager.add(activity_listener);

		spAccount = (Spinner)env.findViewById(R.id.account);
		spAlbum = (Spinner)env.findViewById(R.id.album);
		btnAlbumNew = env.findViewById(R.id.btnAlbumNew);
		account_adapter = new AccountAdapter(env,env.getString(R.string.account_anonymous) );
		album_adapter = new AlbumAdapter(env,env.getString(R.string.album_not_select));
		spAccount.setAdapter(account_adapter);
		spAlbum.setAdapter(album_adapter);

		btnAlbumNew.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				ImgurAccount account = getSelectedAccount();
				if( account == null ){
					env.show_toast(true,R.string.account_not_selected);
				}else{
					DlgAlbumNew.show(env,UploadTargetManager.this,account);
				}
			}
		});
		
		spAccount.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				account_selection_changed(false,pos,"account selected");
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
		album_loader = new AlbumLoader(env,new AlbumLoader.Callback() {
			@Override
			public void onLoad() {
				account_selection_changed(true,spAccount.getSelectedItemPosition(),"album loaded");
				if( reload_dialog != null ){
					reload_dialog.dismiss();
					reload_dialog = null;
				}
			}
		});
		account_adapter.registerDataSetObserver(new DataSetObserver() {
			@Override public void onChanged() {
				super.onChanged();
				account_selection_changed(true,spAccount.getSelectedItemPosition(),"account data changed");
			}
		});

		spAlbum.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				log.d("album selection changed: %d",pos);
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {
				log.d("album selection nothing");
			}
		});
		spAlbum.setOnLongClickListener (new OnLongClickListener () {
			@Override public boolean onLongClick(View v) {
				final ImgurAlbum album = (ImgurAlbum)spAlbum.getSelectedItem();
				env.confirm(
					null
					,env.getString(R.string.album_delete_confirm,album.album_name)
					,true
					,new Runnable() {
						@Override public void run() {
							album_delete(getSelectedAccount(),album);
						}
					}
					,null
				);
				return true;
			}
		});

		selection_init();
	}

	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override
		public void onNewIntent() {
			selection_init();
		}

		@Override
		public void onPause() {
			selection_save();
		}
	};

	/////////////////////////////////////////////////////////////////////////////

	String lastused_account_name = null;
	String lastused_album_name = null;
	String created_album_name = null;

	void selection_init(){
		SharedPreferences pref = env.pref();
		//
		lastused_account_name = pref.getString(PrefKey.KEY_ACCOUNT_LAST_USED,null);
		if( PrefKey.VALUE_ACCOUNT_ANONYMOUS.equals(lastused_account_name) ) lastused_account_name = null;
		//
		if( lastused_account_name == null ){
			lastused_album_name = null;
		}else{
			lastused_album_name = pref.getString("album_name_"+lastused_account_name,null);
		}
		log.d("load selection: account=%s,album=%s",lastused_account_name,lastused_album_name);
		// アカウントは初期化中でもアクセスできるはず…
		account_adapter.selectByName(spAccount,lastused_account_name);
	}

	void selection_save(){
		SharedPreferences.Editor e = env.pref().edit();
		ImgurAlbum album = (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition());
		if( album != null ){
			e.putString(PrefKey.KEY_ACCOUNT_LAST_USED,album.account_name);
			e.putString("album_name_"+album.account_name,album.album_name);
			log.d("save selection: account=%s,album=%s",album.account_name,album.album_name);
		}else{
			ImgurAccount account = (ImgurAccount)account_adapter.getItem( spAccount.getSelectedItemPosition());
			if( account != null ){
				e.putString(PrefKey.KEY_ACCOUNT_LAST_USED,account.name);
				e.remove("album_name_"+account.name);
				log.d("save selection: account=%s,",account.name);
			}else{
				e.remove(PrefKey.KEY_ACCOUNT_LAST_USED);
				log.d("save selection: nanasi");
			}
		}
		e.commit();
	}
	
	AlbumList album_list;

	void account_selection_changed(boolean bAlbumLoaded, int last_account_idx,String desc) {
		ImgurAccount account = (ImgurAccount)account_adapter.getItem(last_account_idx);
		if( account == null ){
			log.d("missing account: %s",desc);
			// アカウントが選択されていない
			album_adapter.clear(env.getString(R.string.album_not_select));
			spAlbum.setEnabled(false);
			btnAlbumNew.setEnabled(false);
			album_list = null;
		}else{
			// アカウントが選択されている

			// アルバム選択肢を読む
			album_list = album_loader.findAlbumList(account.name);
			if( album_list == null ){
				log.d("missing album: %s",desc);
				// アルバム一覧を読めない。ロード中かエラーだろう
				album_adapter.clear(env.getString(R.string.album_loading));
				spAlbum.setEnabled(false);
				btnAlbumNew.setEnabled(false);
			}else{
				btnAlbumNew.setEnabled(true);

				// 直前までの選択。異なるアカウントのものはnullとする
				ImgurAlbum old_selection= (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition());
				if( old_selection != null && ! account.name.equals(old_selection.account_name) ) old_selection = null;

				if( bAlbumLoaded || old_selection ==null  ){

					// アルバム選択肢を設定する
					album_adapter.replace(album_list.iter(),env.getString(R.string.album_not_select));
					spAlbum.setEnabled( album_list.size() > 0);

					if( created_album_name != null ){
						final int idx = album_adapter.findByName(created_album_name);
						if( idx != -1 ){
							spAlbum.setSelection(idx);
							created_album_name = null;
							return;
						}
					}
					
					if( account.name.equals( lastused_account_name ) ){
						// 選択の初期化がまだおわっておらず現在のアカウントとマッチするのなら、アルバムを選択しなおす
						log.d("initialize album selection: %s %s %s",desc,lastused_account_name,lastused_album_name);
						lastused_account_name = null;
						final int idx = album_adapter.findByName(lastused_album_name);
						spAlbum.setSelection(idx < 0 ? 0 : idx);
					}else if( old_selection != null ){
						// アカウントは変化していないが、データが更新されたかもしれない。
						// なるべく以前の選択を維持する
						int idx = album_adapter.findByName(old_selection.album_name);
						spAlbum.setSelection(idx);
					}else{
						// アカウントの選択が変わったので、アルバムの選択をリセットする
						log.d("reset album selection: %s",desc);
						spAlbum.setSelection(0);
					}
					// ただし、 selection changed イベントが発生するのはこれより少し後になる
					// もしくは イベントが発生しない場合もある
				}
			}
		}
	}

	///////////////////////////////////

	public ImgurAccount getSelectedAccount() {
		return (ImgurAccount)account_adapter.getItem(spAccount.getSelectedItemPosition());
	}

	public ImgurAlbum getSelectedAlbum() {
		return (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition());
	}

	public void reload() {
		album_loader.reload();
	}
	
	public interface AlbumCreateCallback{
		void onComplete();
		void onError(String message);
	}

	public void create_album(
		ImgurAccount account
		,final String title
		,final String desc
		,final String privacy
		,final String layout
		,final AlbumCreateCallback callback
	){
		final ProgressDialog dialog = new ProgressDialog(env.context);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setTitle(R.string.album_add);
		dialog.setMessage(env.getString(R.string.please_wait));
		dialog.setIndeterminate(true);
		dialog.setCancelable(true);
		env.dialog_manager.show_dialog(dialog);
		new Thread(){
			@Override public void run() {
				ImgurAccount account = getSelectedAccount();
				APIResult result;
				try{
					String cancel_message = env.getString(R.string.cancelled);
					//
					SignedClient client = new SignedClient(env);
					client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
					client.cancel_checker = new CancelChecker() {
						@Override public boolean isCancelled() {
							return !dialog.isShowing();
						}
					};
					//
					log.d("title=%s desc=%s privacy=%s layout=%s",title,desc,privacy,layout);
					HttpPost request = new HttpPost("http://api.imgur.com/2/account/albums?_format=json");
					ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
					if( desc != null ) params.add(new BasicNameValuePair("description",desc));
					if( layout != null ) params.add(new BasicNameValuePair("layout",layout));
					if( privacy != null ) params.add(new BasicNameValuePair("privacy",privacy));
					if( title != null ) params.add(new BasicNameValuePair("title",title));
					// そのままエンコードすると 空白が + になってしまい、oAuthの署名に適合しない
					String contents = URLEncodedUtils.format(params, HTTP.UTF_8);
					contents = Pattern.compile("\\+").matcher(contents).replaceAll("%20");
					StringEntity entity = new StringEntity(contents, HTTP.UTF_8);
					entity.setContentType(URLEncodedUtils.CONTENT_TYPE);
					request.setEntity(entity);
					//
					client.consumer.sign(request);
					result = client.json_send_request(request,cancel_message,account.name);

				}catch(Throwable ex){
					result = new APIResult(env,ex);
				}
				result.save_error(env);
				if( result.content_json != null ){
					log.d("json=%s",result.content_json);
				}
				final String error = result.getError();
				env.handler.post(new Runnable() {
					@Override
					public void run() {
						if(env.isFinishing())return;
						//
						if( error != null ){
							callback.onError(error);
						}else{
							created_album_name = title;
							callback.onComplete();
							reload_with_dialog();
						}
						dialog.dismiss();
					}
				});
			}
		}.start();
	}
	private void album_delete(final ImgurAccount selectedAccount,final ImgurAlbum album) {
		final ProgressDialog dialog = new ProgressDialog(env.context);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setTitle(env.getString(R.string.album_delete,album.album_name));
		dialog.setMessage(env.getString(R.string.please_wait));
		dialog.setIndeterminate(true);
		dialog.setCancelable(true);
		env.dialog_manager.show_dialog(dialog);
		new Thread(){
			@Override public void run() {
				ImgurAccount account = getSelectedAccount();
				APIResult result;
				try{
					String cancel_message = env.getString(R.string.cancelled);
					//
					SignedClient client = new SignedClient(env);
					client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
					client.cancel_checker = new CancelChecker() {
						@Override public boolean isCancelled() {
							return !dialog.isShowing();
						}
					};
					//
					HttpDelete request = new HttpDelete("http://api.imgur.com/2/account/albums/"+ album.album_id +".json");
					client.consumer.sign(request);
					result = client.json_send_request(request,cancel_message,account.name);
				}catch(Throwable ex){
					result = new APIResult(env,ex);
				}
				result.save_error(env);
				final String error = result.getError();
				env.handler.post(new Runnable() {
					@Override
					public void run() {
						if(env.isFinishing())return;
						//
						if( error != null ){
							env.show_toast(true,error);
						}else{
							reload_with_dialog();
						}
						dialog.dismiss();
					}
				});
			}
		}.start();
		
	}
	
	ProgressDialog reload_dialog;
	void reload_with_dialog(){
		final ProgressDialog dialog = new ProgressDialog(env.context);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setMessage(env.getString(R.string.album_loading_wait));
		dialog.setIndeterminate(true);
		dialog.setCancelable(true);
		reload_dialog = dialog;
		env.dialog_manager.show_dialog(dialog);
		//
		reload();
	}
}

