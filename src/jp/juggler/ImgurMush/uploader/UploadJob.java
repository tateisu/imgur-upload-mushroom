package jp.juggler.ImgurMush.uploader;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;

import android.content.SharedPreferences;

public class UploadJob {
	public final AtomicBoolean cancel_request =new AtomicBoolean(false); // キャンセル要求が出た
	public final AtomicBoolean aborted =new AtomicBoolean(false); // 中止された
	public final AtomicBoolean completed =new AtomicBoolean(false); // 処理完了
	
	// 進捗表示用
	public final AtomicReference<String> progress_title = new AtomicReference<String>();
	public final AtomicReference<String> progress_message = new AtomicReference<String>();
	public final AtomicInteger progress_var = new AtomicInteger(-1);
	public final AtomicInteger progress_max = new AtomicInteger(0);

	// 処理結果
	private StringBuffer text_output = new StringBuffer();
	private StringBuffer error_message = new StringBuffer();

	//
	public int job_id = -1;
	public long create_time = System.currentTimeMillis();
	public String account_name;
	public String account_token;
	public String account_secret;
	public String album_id;
	public final ArrayList<UploadUnit> file_list = new ArrayList<UploadUnit>();

	public static class UploadUnit{
		public String src_path;
		public final AtomicReference<String> error_message = new AtomicReference<String>();
		public final AtomicReference<String> text_output = new AtomicReference<String>();
	}

	public synchronized String getErrorMessage() {
		return error_message.toString();
	}
	
	public synchronized void append_error_message(String msg) {
		if( error_message.length() > 0 ) error_message.append("\n");
		error_message.append(msg);
	}
	

	public synchronized String getTextOutput() {
		return text_output.toString();
	}

	public synchronized void append_text_output(SharedPreferences pref, String image_url, String page_url) {
		if( text_output.length() > 0 ) text_output.append("\n");
		int t = Integer.parseInt(pref.getString(PrefKey.KEY_URL_MODE,"0"));
		switch(t){
		default:
		case 0: text_output.append(trim_output_url(pref,image_url)); break;
		case 1: text_output.append(trim_output_url(pref,page_url));break;
		}
	}

	public static String trim_output_url(SharedPreferences pref,String text){
		PrefKey.upgrade_config(pref);
		return pref.getString(PrefKey.KEY_URL_PREFIX,"")+text+pref.getString(PrefKey.KEY_URL_SUFFIX,"");
	}

	static final String KEY_CANCEL_REQUEST="cancel_request";
	static final String KEY_ABORTED  ="aborted";
	static final String KEY_COMPLETED ="completed";
	static final String KEY_PROGRESS_TITLE="progress_title";
	static final String KEY_PROGRESS_MESSAGE="progress_message";
	static final String KEY_PROGRESS_VAR="progress_var";
	static final String KEY_PROGRESS_MAX="progress_max";
	static final String KEY_TEXT_OUTPUT="text_output";
	static final String KEY_ERROR_MESSAGE="error_message";
	static final String KEY_JOB_ID="job_id";
	static final String KEY_ACCOUNT_NAME="account_name";
	static final String KEY_ACCOUNT_TOKEN="account_token";
	static final String KEY_ACOUNT_SECRET="account_secret";
	static final String KEY_ALBUM_ID="album_id";
	static final String KEY_FILE_LIST="file_list";
	static final String KEY_SRC_PATH="src_path";
	static final String KEY_CREATE_TIME="create_time";

	public JSONObject encodeJSON() throws JSONException {
		JSONObject dst = new JSONObject();
		dst.put(KEY_CANCEL_REQUEST,cancel_request.get());
		dst.put(KEY_ABORTED,aborted.get());
		dst.put(KEY_COMPLETED,completed.get());
		
		dst.put(KEY_PROGRESS_TITLE,progress_title.get());
		dst.put(KEY_PROGRESS_MESSAGE,progress_message.get());
		dst.put(KEY_PROGRESS_VAR,progress_var.get());
		dst.put(KEY_PROGRESS_MAX,progress_max.get());

		dst.put(KEY_TEXT_OUTPUT,text_output.toString());
		dst.put(KEY_ERROR_MESSAGE,error_message.toString());

		dst.put(KEY_JOB_ID,job_id);
		dst.put(KEY_CREATE_TIME,create_time);
		dst.put(KEY_ACCOUNT_NAME,account_name);
		dst.put(KEY_ACCOUNT_TOKEN,account_token);
		dst.put(KEY_ACOUNT_SECRET,account_secret);
		dst.put(KEY_ALBUM_ID,album_id);

		JSONArray dst_file_list = new JSONArray();
		for( UploadUnit unit : file_list){
			JSONObject dst_file = new JSONObject();
			dst_file.put(KEY_SRC_PATH,unit.src_path);
			dst_file.put(KEY_TEXT_OUTPUT,unit.text_output.get());
			dst_file.put(KEY_ERROR_MESSAGE,unit.error_message.get());
		}
		dst.put(KEY_FILE_LIST,dst_file_list);

		return dst;
	}

	public UploadJob(JSONObject src) throws JSONException {
		cancel_request.set(src.optBoolean(KEY_CANCEL_REQUEST,false));
		aborted.set(src.optBoolean(KEY_ABORTED,false));
		completed.set(src.optBoolean(KEY_COMPLETED,false));
		
		progress_title.set(src.optString(KEY_PROGRESS_TITLE));
		progress_message.set(src.optString(KEY_PROGRESS_MESSAGE));
		progress_var.set(src.optInt(KEY_PROGRESS_VAR,progress_var.get()));
		progress_max.set(src.optInt(KEY_PROGRESS_MAX,progress_max.get()));
		
		
		text_output = new StringBuffer(src.optString(KEY_TEXT_OUTPUT,""));
		error_message = new StringBuffer(src.optString(KEY_ERROR_MESSAGE,""));

		job_id = src.optInt(KEY_JOB_ID,-job_id);
		create_time = src.optLong(KEY_CREATE_TIME,create_time);
		account_name =src.optString(KEY_ACCOUNT_NAME,account_name);
		account_token =src.optString(KEY_ACCOUNT_TOKEN,account_token);
		account_secret =src.optString(KEY_ACOUNT_SECRET,account_secret);
		album_id =src.optString(KEY_ALBUM_ID,album_id);

		JSONArray src_file_list = src.optJSONArray(KEY_FILE_LIST);
		if( src_file_list != null ){
			for(int i=0,ie=src_file_list.length();i<ie;++i){
				JSONObject src_file = src_file_list.getJSONObject(i);
				UploadUnit unit = new UploadUnit();
				unit.src_path=src_file.optString(KEY_SRC_PATH,unit.src_path);
				unit.text_output.set(src_file.optString(KEY_TEXT_OUTPUT,unit.text_output.get()));
				unit.error_message.set(src_file.optString(KEY_ERROR_MESSAGE,unit.error_message.get()));
				
				file_list.add(unit);
			}
		}
	}
	
	public UploadJob(ImgurAccount account,ImgurAlbum album){
		if( account != null ){
			this.account_name = account.name;
			this.account_token = account.token;
			this.account_secret = account.secret;
		}
		if(album != null ){
			this.album_id = album.album_id;
		}
	}
}
