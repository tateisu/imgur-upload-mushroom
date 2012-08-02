package jp.juggler.ImgurMush.data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.regex.Pattern;

import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ImageTempDir;
import jp.juggler.util.LogCategory;
import jp.juggler.util.TextUtil;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.text.Html;

public class APIResult {
	static final LogCategory log = new LogCategory("APIResult");

	public static final String SAVEKEY_HTTP_RCODE = "http_rcode";
	public static final String SAVEKEY_HTTP_STATUS = "http_status";
	public static final String SAVEKEY_HTTP_HEADER = "http_headers";
	//
	public static final String SAVEKEY_ERROR_HTTP = "error_http";
	public static final String SAVEKEY_ERROR_PARSE = "error_parse";
	public static final String SAVEKEY_ERROR_EXTRA = "error_extra";
	//
	public static final String SAVEKEY_CONTENT_JSON = "content_json";
	//
	public static final byte[] RESPONSE_META_START = TextUtil.encodeUTF8("<<%=%Imgur Error Response Start%=%>>");
	public static final byte[] RESPONSE_META_END   = TextUtil.encodeUTF8("<<%=%Imgur Error Response End%=%>>");
	public static final byte[] RESPONSE_CONTENT_START = TextUtil.encodeUTF8("<<%=%Imgur Error Response Content Start%=%>>");
	public static final byte[] RESPONSE_CONTENT_END   = TextUtil.encodeUTF8("<<%=%Imgur Error Response Content End%=%>>");

	static final Pattern reHTMLHead = Pattern.compile("<head[^>]*>.*?</head[^>]*>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
	static final Pattern reWhiteSpace = Pattern.compile("[\\x00-\\x20\\x7f\\xa0]{2,}");
	static final Pattern reSQLSelect = Pattern.compile("SELECT.+FROM.+WHERE.+AND.+",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
	static final Pattern reObjectReplacementCharacter = Pattern.compile("\uFFFC");
	static final Pattern reOverCapacity = Pattern.compile("Sorry\\! We\\'re busy running around.+",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
	static final Pattern reNginx = Pattern.compile("\\bnginx\\s*\\z",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
	static final Pattern reHTMLRetry = Pattern.compile("gateway|over capacity|405 not allowed",Pattern.CASE_INSENSITIVE);
	static final Pattern reMessageRetry = Pattern.compile("server has gone away",Pattern.CASE_INSENSITIVE);
	
	public byte[]     content_bytes;
	public String     content_utf8;
	public JSONObject content_json;

	public int      http_rcode;
	public String   http_status;
	public Header[] http_headers;

	private String error_http;
	private String error_parse;
	private String error_extra;
	
	public APIResult() {
	}
	

	public void setErrorExtra(String str) {
		error_extra = str;
	}
	public void setErrorHTTP(String str) {
		error_http = str;
	}

	public String getError() {
		return error_extra != null ? error_extra
			 : error_parse != null ? error_parse
			 : error_http;
	}
	
	public boolean isError() {
		return null != getError();
	}

	// レスポンスの内容を確認して、可能ならjsonデコードする。Imgur独特のエラーに多少は対応する。
	// リトライ不要ならtrue、リトライ必要ならfalseを返す
	boolean parse_json(BaseActivity act,String account_name){
		// ヘッダ中の Rate-Limit をアカウント別に保存する
		if( account_name != null && http_headers != null ){
			String limit = null;
			String remain = null;
			String reset = null;
			for( Header h : http_headers ){
				try{
					String k = h.getName().toLowerCase();
					String v = h.getValue().trim();
					if( v.length() == 0 ) continue;
					if( k.equals("x-ratelimit-limit") ){
						limit = v;
					}else if( k.equals("x-ratelimit-remaining") ){
						remain = v;
					}else if( k.equals("x-ratelimit-reset") ){
						reset = v;
					}
				}catch(Throwable ex){
				}
			}
			if( limit  != null
			&&  remain != null
			&&  reset  != null
			&&  account_name != null
			){
				try{
					JSONObject entry = new JSONObject();
					entry.put("limit",limit);
					entry.put("remain",remain);
					entry.put("reset",reset);
					entry.put("when",System.currentTimeMillis());
					//
					SharedPreferences pref =act.pref();
					String old_v = pref.getString(PrefKey.KEY_RATE_LIMIT_MAP,null);
					JSONObject map = (old_v != null ? new JSONObject(old_v) : new JSONObject() );
					map.put(account_name,entry);
					SharedPreferences.Editor e = pref.edit();
					e.putString(PrefKey.KEY_RATE_LIMIT_MAP,map.toString());
					e.commit();
				}catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
			// 長い副作用だった
		}

		error_parse = null;

		if( content_bytes == null && content_utf8==null && content_json==null ){
			if( error_http != null ){
				if( http_status != null ){
					error_parse = String.format("%s(%s)",error_http,http_status);
				}
			}
			if( http_rcode < 400 || http_rcode >= 500 ) return false;
			return true;
		}

		if( content_bytes != null ){
			content_utf8 = null;
			content_json = null;

			// parse UTF-8
			try{
				content_utf8 = TextUtil.decodeUTF8(content_bytes);
			}catch(Throwable ex){
				error_parse = format_ex(ex);
				ex.printStackTrace();
				return true;
			}
		}
		
		if( content_utf8 != null ){
			content_json = null;

			// parse json
			try{
				content_json = new JSONObject(content_utf8);
			}catch(Throwable ex){
				ex.printStackTrace();
				//
				try{
					// jsonではないレスポンスはHTMLであることが多い。適当に整形する
					String text = content_utf8;
					text = reHTMLHead.matcher(text).replaceAll(" ");
					text = Html.fromHtml(text).toString();
					text = reObjectReplacementCharacter.matcher(text).replaceAll(" ");
					text = reOverCapacity.matcher(text).replaceAll(" ");
					text = reNginx.matcher(text).replaceAll(" ");
					text = reWhiteSpace.matcher(text).replaceAll("\n").trim();
					error_parse = text;
					
					// エラー内容によってはリトライ可能
					if( reHTMLRetry.matcher(error_parse).find() ) return false;

				}catch(Throwable ex2){
					ex2.printStackTrace();
					error_parse = String.format("decode failed.\n%s",content_utf8);
				}
				//
				return true;
			}
		}
		
		if( content_json != null ){
			// レスポンスにエラーメッセージが含まれるかもしれない
			try{
				error_parse = content_json.getJSONObject("error").getString("message");
				
				if( error_parse != null ){
					// エラーメッセージはナマのSQLとかなんかキモいのがありうる。適当に整形する
					error_parse = reWhiteSpace.matcher(error_parse).replaceAll("\n").trim();
					error_parse = reSQLSelect.matcher(error_parse).replaceAll("");
				
					// エラーの種類によってはリトライをかける
					if( reMessageRetry.matcher(error_parse).find() ) return false;
				}
			}catch(JSONException ex){
				// エラーメッセージが含まれない場合ここを通る。正常ケース
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
		
		// OK?
		return true;
	}
	
	// エラーがあればユーザに表示する。
	// content_error はerr がnullの際に補完される
	public boolean show_error(BaseActivity act){
		String v = getError();
		if( v==null ) return false;
		act.show_toast(true,v);
		return true;
	}
	
	// エラーレスポンスの詳細をSDカードに保存する
	// content_error はerr がnullの際に補完される
	public boolean save_error(BaseActivity act){
		if( !isError() ) return false;
		if( act.pref().getBoolean(PrefKey.KEY_SAVE_ERROR_DETAIL,false) ){
			try {
				JSONObject o = new JSONObject();
				//
				o.put(SAVEKEY_HTTP_RCODE,http_rcode);
				o.put(SAVEKEY_HTTP_STATUS,http_status);
				if( http_headers != null ){
					JSONArray dst = new JSONArray();
					for( Header src : http_headers ){
						if( ignore_http_header.contains( src.getName().toLowerCase() ) ) continue;
						dst.put( String.format("%s: %s",src.getName(), src.getValue() ) );
					}
					o.put(SAVEKEY_HTTP_HEADER,dst);
				}
				//
				if( error_http != null ) o.put(SAVEKEY_ERROR_HTTP,error_http);
				if( error_parse!= null ) o.put(SAVEKEY_ERROR_PARSE,error_parse);
				if( error_extra != null ) o.put(SAVEKEY_ERROR_EXTRA,error_extra);
				//
				if( content_json != null ) o.put(SAVEKEY_CONTENT_JSON,content_json);
	
				// 保存する
				File dir = ImageTempDir.getTempDir(act,act.pref(),act.ui_handler);
				if( dir != null ){
					File file = new File(dir,formatLogFile(System.currentTimeMillis()));
					try{
						FileOutputStream fo = new FileOutputStream(file,true);
						try{
							fo.write(0x0a);
							fo.write( RESPONSE_META_START );
							fo.write(0x0a);
							fo.write( TextUtil.encodeUTF8( o.toString(1) ));
							fo.write(0x0a);
							fo.write( RESPONSE_META_END );
							fo.write(0x0a);
							if( content_bytes != null ){
								fo.write( RESPONSE_CONTENT_START );
								fo.write( 0x0a );
								fo.write( content_bytes );
								fo.write( 0x0a );
								fo.write( RESPONSE_CONTENT_END );
								fo.write( 0x0a );
							}
						}finally{
							fo.close();
						}
					}catch (Throwable ex) {
					}
				}
			}catch(JSONException ex){
			}
		}
		return true;
	}

	// 保存するログファイルの名前を決める
	static SimpleDateFormat time_format = new SimpleDateFormat("'log'yyyyMMdd-HHmmss-SSS'.txt'");
	static final String formatLogFile(long t ){
		TimeZone tz = TimeZone.getDefault();
		//
		Calendar c = GregorianCalendar.getInstance(tz);
		c.setTimeInMillis(t);
		//
		time_format.setTimeZone(tz);
		String s = time_format.format( c.getTime() );
		//
		return s;
	}

	//////////////////////////////////////////////////////
	// 検証機能用

	
	public APIResult(JSONObject src) throws JSONException {

		if( src.has(SAVEKEY_HTTP_RCODE) ) this.http_rcode = src.getInt(SAVEKEY_HTTP_RCODE);
		if( src.has(SAVEKEY_HTTP_STATUS) ) this.http_status = src.getString(SAVEKEY_HTTP_STATUS);
		if( src.has(SAVEKEY_HTTP_HEADER) ){
			JSONArray src_list = src.getJSONArray(SAVEKEY_HTTP_HEADER);
			int src_list_size = src_list.length();
			http_headers = new Header[src_list_size];
			for(int i=0;i<src_list_size;++i){
				String line = src_list.getString(i);
				int delm = line.indexOf(": ");
				if( delm == -1 ){
					http_headers[i] = new BasicHeader("?",line);
				}else{
					http_headers[i] = new BasicHeader(
						line.substring(0,delm),
						line.substring(delm+2)
					);
				}
			}
		}

		//
		if( src.has(SAVEKEY_ERROR_HTTP ) ) this.error_http  = src.getString(SAVEKEY_ERROR_HTTP );
		if( src.has(SAVEKEY_ERROR_PARSE) ) this.error_parse = src.getString(SAVEKEY_ERROR_PARSE);
		if( src.has(SAVEKEY_ERROR_EXTRA) ) this.error_extra = src.getString(SAVEKEY_ERROR_EXTRA);
		//
		if( src.has(SAVEKEY_CONTENT_JSON) ) this.content_json  = src.getJSONObject(SAVEKEY_CONTENT_JSON);
	}

	public void attatchContent(byte[] src, int start, int end) {
		int length = end-start;
		if( length >= 0){
			content_bytes = new byte[length];
			System.arraycopy( src,start,content_bytes,0,length);
		}
	}
	
	static HashSet<String> ignore_http_header;
	
	static{
		ignore_http_header = new HashSet<String>();
		ignore_http_header.add("access-control-allow-origin");
		ignore_http_header.add("cache-control");
		ignore_http_header.add("date");
		ignore_http_header.add("expires");
		ignore_http_header.add("pragma");
		ignore_http_header.add("server");
		ignore_http_header.add("set-cookie");
		ignore_http_header.add("vary");
		ignore_http_header.add("connection");
	}
	  
	public void test_log(BaseActivity act,StringBuilder sb) {
		
		parse_json(act,null);

		if( error_http  !=null) sb.append("\n# Error (HTTP):" + error_http);
		if( error_parse !=null) sb.append("\n# Error (parse):" + error_parse);
		if( error_extra !=null) sb.append("\n# Error (Extra):" + error_extra);
		//
		if( http_rcode != 0 ) sb.append("\n# HTTP Response code:" +http_rcode);
		if( http_status != null ) sb.append("\n# HTTP Status:" +http_status);
		if( http_headers != null){
			for( Header h : http_headers){
				if( ignore_http_header.contains( h.getName().toLowerCase() ) ) continue;
				sb.append("\n# Header: "+ h.getName()+ ": "+ h.getValue());
			}
		}
		if( content_json != null ){
			sb.append("\n# Content (json):\n");
			try {
				sb.append(content_json.toString(1));
			} catch (JSONException ex) {
				ex.printStackTrace();
			}
		}else if( content_utf8 != null ){
			sb.append("\n# Content (utf8):\n");
			sb.append(content_utf8.trim());
		}else if( content_bytes !=null ){
			sb.append("\n# Content (byte):\n");
			sb.append(String.format("length=%s",content_bytes.length));
		}
	}

	public static final String format_ex(Throwable ex){
		return ex.getClass().getSimpleName() +": "+ex.getMessage();
	}
	
	static final boolean byte_match(byte[] src,int src_length,int src_offset,byte[] key,int key_length){
		if( src_length - src_offset < key_length ) return false;
		for(int i=0;i<key_length;++i){
			if( src[src_offset +i] != key[i] ) return false;
		}
		return true;
	}
	
	static final int byte_indexOf(byte[] src,int src_length,int src_offset,byte[] key,int key_length){
		int last = src_length - key_length;
		for(int i=src_offset;i <= last; ++i ){
			if( byte_match(src,src_length,i,key,key_length ) ) return i;
		}
		return -1;
	}

	public static ArrayList<String> scanErrorLog(BaseActivity act) {
		ArrayList<String> error_list = new ArrayList<String>();
		File dir = ImageTempDir.getTempDir(act,act.pref(),act.ui_handler);
		if( dir != null ){
			String[] list = dir.list(new FilenameFilter() {
				Pattern reErrorFile = Pattern.compile("^log.+\\.txt$",Pattern.CASE_INSENSITIVE);
				
				@Override
				public boolean accept(File dir, String filename) {
					return reErrorFile.matcher(filename).find();
				}
			});
			Arrays.sort(list);
			for(int list_idx=list.length-1;list_idx>=0;--list_idx){
				String entry = list[list_idx];
				
				
				//
				File file = new File(dir,entry);
				try{
					byte[] data;
					FileInputStream in = new FileInputStream(file);
					try{
						int capa = in.available();
						ByteArrayOutputStream bao = new ByteArrayOutputStream( capa > 4096? capa: 4096 );
						byte[] tmp = new byte[4096];
						for(;;){
							int delta = in.read(tmp);
							if(delta == -1 ) break;
							bao.write(tmp,0,delta);
						}
						data = bao.toByteArray();
					}finally{
						in.close();
					}
					APIResult result = null;
					int src_len = data.length;
					int keylen_meta_start = APIResult.RESPONSE_META_START.length;
					int keylen_meta_end = APIResult.RESPONSE_META_END.length;
					int keylen_content_start = APIResult.RESPONSE_CONTENT_START.length;
					int keylen_content_end = APIResult.RESPONSE_CONTENT_END.length;
					for(int i=0;i<src_len;){
						if( byte_match(data,src_len,i,APIResult.RESPONSE_META_START,keylen_meta_start) ){
							if( result != null ){
								StringBuilder sb = new StringBuilder();
								sb.append("## "+entry);
								result.test_log(act,sb);
								error_list.add(sb.toString());
								//
								result = null;
								if( error_list.size() >= 200 ) break;
							}
							int start = i + keylen_meta_start;
							int end = byte_indexOf( data,src_len,start, APIResult.RESPONSE_META_END, keylen_meta_end);
							if( end == -1 ) end = src_len;
							String utf8 = new String(data,start,end-start,"UTF-8");
							result = new APIResult(new JSONObject(utf8));
							i= end + keylen_meta_end;
						}else if( byte_match(data,src_len,i,APIResult.RESPONSE_CONTENT_START,keylen_content_start) ){
							int start = i + keylen_content_start;
							int end = byte_indexOf( data,src_len,start, APIResult.RESPONSE_CONTENT_END, keylen_content_end);
							if( end == -1 ) end = src_len;
							if( result != null ) result.attatchContent( data,start,end );
							i= end + keylen_content_end;
						}else{
							++i;
						}
					}
					if( result != null ){
						StringBuilder sb = new StringBuilder();
						sb.append("## "+entry);
						result.test_log(act,sb);
						error_list.add(sb.toString());
					}
				}catch(Throwable ex){
					ex.printStackTrace();
				}
			}
		}
		return error_list;
	}


	public static StringBuilder dumpRateLimit(BaseActivity act) {
		StringBuilder sb = new StringBuilder();
		
		String caption_account = act.getString(R.string.account);
		String caption_ratelimit_checkdate = act.getString(R.string.ratelimit_checkdate);
		String caption_ratelimit_reset = act.getString(R.string.ratelimit_reset);
		String caption_ratelimit_remain_limit = act.getString(R.string.ratelimit_remain_limit);
		
		try{
			SharedPreferences pref =act.pref();
			String old_v = pref.getString(PrefKey.KEY_RATE_LIMIT_MAP,null);
			JSONObject map = (old_v != null ? new JSONObject(old_v) : new JSONObject() );
			//
			@SuppressWarnings("unchecked")
			Iterator<String> keys = map.keys();
			while( keys.hasNext() ){
				String account_name = keys.next();
				try{
					JSONObject entry = map.getJSONObject(account_name);
					int limit = Integer.parseInt(entry.getString("limit"),10);
					int remain = Integer.parseInt(entry.getString("remain"),10);
					long reset = 1000 * Long.parseLong(entry.getString("reset"),10);
					long when = entry.getLong("when");
					
					if( PrefKey.RATELIMIT_ANONYMOUS.equals(account_name) ) account_name = act.getString(R.string.account_anonymous);
					
					if( sb.length() > 0 ) sb.append("\n");
					sb.append(String.format("\n%s: %s",caption_account,account_name ));
					sb.append(String.format("\n%s: %s/%s",caption_ratelimit_remain_limit,remain,limit ));
					sb.append(String.format("\n%s: %s",caption_ratelimit_checkdate,ImgurHistory.formatTimeLong(when) ));
					sb.append(String.format("\n%s: %s",caption_ratelimit_reset,ImgurHistory.formatTimeLong(reset) ));
				}catch(Throwable ex){
				}
			}
		}catch (Throwable ex) {
			ex.printStackTrace();
		}
		return sb;
	}



}
